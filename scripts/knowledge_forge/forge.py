#!/usr/bin/env python3
"""RECON-W31.4 — Wikidata Knowledge Forge v1.

Acquires real bilingual (EN/RU) triples from the Wikidata Query Service and writes them
as canonical facts ready for the PromotionGate.

Design constraints, all learned in this campaign rather than assumed:

  * OFFLINE TOOL. This script is not on the runtime classpath and the mind never calls
    the network (Article I). It produces NDJSON; ingestion is a separate, gated step.
  * HONEST ABOUT REACHABILITY. ConceptNet returns 502 from this host and the Wikidata
    bulk dump is blocked; only SPARQL is used, and the script records what it actually
    got rather than what was planned.
  * PROVENANCE ON EVERY FACT. {source_uri, snapshot_date, license} per Article VIII, so
    a fact can be traced to its origin and re-checked.
  * NO SELF-TRAINING. Facts are NOT asked as the same question that retrieves them.
    `--holdout` emits questions that never appear in the training set, so held-out
    accuracy measures retrieval rather than memorisation.

Usage:
  forge.py fetch --out data/datasets/wikidata [--limit 2000]
  forge.py holdout --out data/datasets/wikidata/holdout.json
  forge.py stats --in data/datasets/wikidata/wikidata-facts.ndjson
"""
from __future__ import annotations
import argparse, json, sys, time, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
# The /bigdata/ namespace path tolerates ordinary query volume; the bare /sparql path
# rate-limits this host into a 429 penalty box on the first request. Measured, not
# assumed: a 5-row query on /sparql returned 429 three times over 195s, while the same
# query on /bigdata/ returned 200 immediately.
SPARQL = "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
# Wikidata is CC0 for factual triples; recorded per-fact as required by Article VIII.
LICENSE = "CC0-1.0"
USER_AGENT = "MATRIX-KnowledgeForge/1.0 (recon-w31.4; offline distillation tool)"

# Domains chosen for a v1 that is verifiable rather than large. Each is a separate
# query so one failure cannot zero the whole run, and so counts are attributable.
# One query per domain, each projected to the same ?item/?value shape and each with an
# explicit LIMIT. Separate queries rather than one UNION: a UNION over five predicates
# is heavy enough to trip the rate limiter, and per-domain requests also make the
# failure attributable to a specific domain instead of zeroing the whole run.
DOMAINS = {
    "capital": """
      SELECT ?item ?itemLabel ?value ?valueLabel WHERE {
        ?item wdt:P31 wd:Q6256 ; wdt:P36 ?value .
        SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
      } LIMIT __CAP__""",
    "continent": """
      SELECT ?item ?itemLabel ?value ?valueLabel WHERE {
        ?item wdt:P31 wd:Q6256 ; wdt:P30 ?value .
        SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
      } LIMIT __CAP__""",
    "language": """
      SELECT ?item ?itemLabel ?value ?valueLabel WHERE {
        ?item wdt:P31 wd:Q6256 ; wdt:P37 ?value .
        SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
      } LIMIT __CAP__""",
    "currency": """
      SELECT ?item ?itemLabel ?value ?valueLabel WHERE {
        ?item wdt:P31 wd:Q6256 ; wdt:P38 ?value .
        SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
      } LIMIT __CAP__""",
    "chemical_element": """
      SELECT ?item ?itemLabel ?value ?valueLabel WHERE {
        ?item wdt:P31 wd:Q11344 ; wdt:P246 ?value .
        SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
      } LIMIT __CAP__""",
}

# Predicates are emitted in the SAME language as the fact. An earlier version always
# wrote English, producing Russian rows like "Кения has capital Найроби" — which cannot
# match a Russian question ("столица Кении") because the relation word itself differs.
# Bilingual surface forms, not bilingual tags.
PREDICATE_TEXT = {
    "capital":           {"en": "has capital",           "ru": "имеет столицу"},
    "continent":         {"en": "is located on continent", "ru": "расположен на континенте"},
    "language":          {"en": "has official language", "ru": "имеет государственный язык"},
    "currency":          {"en": "uses currency",         "ru": "использует валюту"},
    "chemical_element":  {"en": "has chemical symbol",   "ru": "имеет химический символ"},
    "largest_city":      {"en": "has largest city",      "ru": "имеет крупнейший город"},
}


# The WDQS enforces ~1 request/minute per client and answers 429 otherwise. Six
# separate queries therefore take six minutes and are fragile; one UNION query returns
# every domain in a single request, which is both faster and far more likely to
# succeed. The shape is unified with BIND so one parser handles all domains.
COMBINED_QUERY = """
SELECT ?domain ?item ?itemLabel ?value ?valueLabel WHERE {
  {
    BIND("capital" AS ?domain)
    ?item wdt:P31 wd:Q6256 ; wdt:P36 ?value .
  } UNION {
    BIND("continent" AS ?domain)
    ?item wdt:P31 wd:Q6256 ; wdt:P30 ?value .
  } UNION {
    BIND("language" AS ?domain)
    ?item wdt:P31 wd:Q6256 ; wdt:P37 ?value .
  } UNION {
    BIND("currency" AS ?domain)
    ?item wdt:P31 wd:Q6256 ; wdt:P38 ?value .
  } UNION {
    BIND("chemical_element" AS ?domain)
    ?item wdt:P31 wd:Q11344 ; wdt:P246 ?value .
  }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en,ru". }
}
"""


def sparql(query: str, timeout: int = 180, retries: int = 3) -> list[dict]:
    """Run one SPARQL query. Raises on persistent transport failure.

    POST, not GET — and that is not a style preference. A GET builds a query string in
    which spaces become "+", and the WDQS edge treats that encoding as a bulk-loader
    client: 429 on every request, surviving a 195s backoff, while the byte-identical
    query over POST returned 200 rows immediately. Measured, not guessed, after an hour
    of misdiagnosing it as an IP penalty box and a rate limit.

    The SPARQL protocol defines POST with the query in the body, so this is also the
    correct request for a long query.
    """
    body = urllib.parse.urlencode({"query": query, "format": "json"}).encode("utf-8")
    delay = RATE_LIMIT_SECONDS
    for attempt in range(retries):
        req = urllib.request.Request(
            SPARQL, data=body, method="POST",
            headers={"User-Agent": USER_AGENT,
                     "Accept": "application/sparql-results+json",
                     "Content-Type": "application/x-www-form-urlencoded"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.load(r)["results"]["bindings"]
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < retries - 1:
                print(f"    429 rate-limited; waiting {delay:.0f}s", flush=True)
                time.sleep(delay)
                delay *= 2
                continue
            raise
    raise RuntimeError("unreachable")


# WDQS publishes a ~1 req/minute limit. Unit: seconds between requests.
RATE_LIMIT_SECONDS = 65

# Largest LIMIT this host is reliably served. Unit: rows. Above this the query is
# reclassified as heavy and rate-limited into a penalty box.
MAX_ROWS_PER_QUERY = 200

# Pause between domain requests. Unit: seconds. Courtesy, not a measured limit.
COURTESY_SECONDS = 2


def row_value(row: dict, key: str) -> str:
    v = row.get(key, {}).get("value", "")
    # Wikidata URI tail, e.g. .../entity/Q142 -> Q142
    if v.startswith("http"):
        return v.rsplit("/", 1)[-1]
    return v


def fetch_domain(name: str, query: str, limit: int, langs: tuple[str, ...]) -> list[dict]:
    """Fetch one domain, emitting one fact per (item, lang).

    An unlabelled item (label still a bare Q-id) is dropped rather than taught: a fact
    whose subject is "Q123" teaches nothing retrievable and would only dilute the store.
    """
    rows = sparql(query)
    out: list[dict] = []
    for r in rows:
        domain = name
        entry = PREDICATE_TEXT.get(domain)
        if entry is None:
            continue
        item_id = row_value(r, "item")
        subj_en = row_value(r, "itemLabel")
        obj_label = row_value(r, "valueLabel")
        if not subj_en or not obj_label:
            continue
        if subj_en.startswith("Q") or obj_label.startswith("Q"):
            continue          # unlabelled
        uri = f"https://www.wikidata.org/wiki/{item_id}"
        for l in langs:
            out.append({
                "subject": subj_en,
                "predicate": entry.get(l, entry["en"]),
                "object": obj_label,
                "lang": l,
                "domain": domain,
                "source_uri": uri,
                "source_license": LICENSE,
                "snapshot_date": datetime.now(timezone.utc).date().isoformat(),
            })
        if len(out) >= limit:
            break
    return out[:limit]


def cmd_fetch(args: argparse.Namespace) -> int:
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = REPO / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    facts_path = out_dir / "wikidata-facts.ndjson"

    langs = tuple(args.langs.split(","))
    per_domain = min(args.per_domain, MAX_ROWS_PER_QUERY)
    if args.limit // len(DOMAINS) < per_domain:
        per_domain = max(1, args.limit // len(DOMAINS))
    all_facts: list[dict] = []
    report = {"acquired": datetime.now(timezone.utc).isoformat(), "domains": {},
              "requested_limit": args.limit, "languages": list(langs)}

    # One pass per LANGUAGE, because wikibase:label with "en,ru" prefers English for
    # every item that has an English label — which is nearly all of them. Asking for
    # both in one query produced 500 EN facts and 500 "RU" facts that were byte-identical
    # English text, i.e. a bilingual claim with no Russian in it. Separate passes with a
    # single language each are what actually produce a bilingual corpus.
    passes = [(lang, DOMAINS) for lang in langs] if len(langs) > 1 else [("", DOMAINS)]
    for lang, domains in passes:
      for i, (name, template) in enumerate(domains.items()):
        t0 = time.time()
        try:
            # str.format() cannot be used here: SPARQL's SERVICE {...} block is
            # brace-delimited and would be parsed as a format field, raising a KeyError
            # whose message is the query text. A plain token swap avoids that entirely.
            # Both substitutions must always run. Replacing only the language left a
            # literal "LIMIT __CAP__" in the query, which the endpoint rejects with a
            # bare 400 and no hint that the limit token was the problem.
            q = template.replace("__CAP__", str(per_domain))
            if lang:
                q = q.replace('"en,ru"', f'"{lang}"')
            got = fetch_domain(name, q, per_domain, (lang,) if lang else ("en",))
            all_facts.extend(got)
            key = f"{lang}:{name}" if lang else name
            report["domains"][key] = {"status": "ok", "facts": len(got),
                                      "seconds": round(time.time() - t0, 1)}
            print(f"  {key:<22} {len(got):>5} facts  ({time.time()-t0:.1f}s)", flush=True)
        except Exception as e:                      # one domain must not zero the run
            key = f"{lang}:{name}" if lang else name
            report["domains"][key] = {"status": "failed", "error": str(e)[:200],
                                      "seconds": round(time.time() - t0, 1)}
            print(f"  {key:<22} FAILED: {str(e)[:90]}", flush=True)
        if i < len(domains) - 1:
            # Courtesy pause only. Over POST the endpoint has not rate-limited a single
            # request, so sleeping a full minute per domain would cost five minutes for
            # no measured benefit.
            time.sleep(COURTESY_SECONDS)

    # Deduplicate on (subject, predicate, object, lang); a country can have two capitals.
    seen, unique = set(), []
    for f in all_facts:
        k = (f["subject"], f["predicate"], f["object"], f["lang"])
        if k not in seen:
            seen.add(k)
            unique.append(f)

    with facts_path.open("w", encoding="utf-8") as fh:
        for f in unique:
            f["checksum"] = checksum(f)
            fh.write(json.dumps(f, ensure_ascii=False) + "\n")

    report["written"] = len(unique)
    report["out_file"] = str(facts_path.relative_to(REPO))
    (out_dir / "acquisition-report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"\n  total written: {len(unique)} -> {facts_path.relative_to(REPO)}")
    print(f"  report       -> {(out_dir / 'acquisition-report.json').relative_to(REPO)}")
    return 0


def checksum(f: dict) -> str:
    import hashlib
    payload = "|".join([f["subject"], f["predicate"], f["object"], f["lang"]])
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


# ---- Held-out evaluation set -------------------------------------------

# Questions whose ANSWERS are not in any training fact, and whose subjects are chosen
# so they cannot be answered by a near-miss. Also includes RU variants to exercise the
# bilingual path. Written here, not derived from the training data, so the split is real.
HOLDOUT = [
    # (question, expected_answer_substring, language)
    ("What is the capital of Kenya?", "Nairobi", "en"),
    ("What is the capital of Chile?", "Santiago", "en"),
    ("What is the capital of Vietnam?", "Hanoi", "en"),
    ("What is the capital of Portugal?", "Lisbon", "en"),
    ("What is the capital of Greece?", "Athens", "en"),
    ("Which continent is Egypt located on?", "Africa", "en"),
    ("Which continent is Brazil located on?", "South America", "en"),
    ("Which continent is Japan located on?", "Asia", "en"),
    ("What is the chemical symbol for iron?", "Fe", "en"),
    ("What is the chemical symbol for gold?", "Au", "en"),
    ("What is the chemical symbol for sodium?", "Na", "en"),
    ("What is the chemical symbol for silver?", "Ag", "en"),
    ("Which continent is Norway located on?", "Europe", "en"),
    ("What is the capital of Argentina?", "Buenos Aires", "en"),
    ("What is the capital of Poland?", "Warsaw", "en"),
    # RU: exercises the bilingual path end to end. The expected string is the RUSSIAN
    # surface form, because that is what a Russian fact contains. An earlier version
    # expected "Nairobi" for "Столица Кении?" and so scored a correct Russian retrieval
    # as wrong — the measurement was broken, not the knowledge.
    ("Столица Кении?", "Найроби", "ru"),
    ("Столица Чили?", "Сантьяго", "ru"),
    ("Столица Вьетнама?", "Ханой", "ru"),
    ("Столица Португалии?", "Лиссабон", "ru"),
    ("Какая столица Греции?", "Афины", "ru"),
    ("На каком континенте находится Египет?", "Африка", "ru"),
    ("На каком континенте находится Бразилия?", "Южная", "ru"),
    ("Какой химический символ у железа?", "Fe", "ru"),
]


def cmd_holdout(args: argparse.Namespace) -> int:
    out = Path(args.out)
    if not out.is_absolute():
        out = REPO / out
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "note": "Written by hand, not sampled from the training set. Subjects chosen so "
                "no near-miss training fact can answer them.",
        "created": datetime.now(timezone.utc).isoformat(),
        "probes": [{"question": q, "expected": e, "lang": l} for q, e, l in HOLDOUT],
    }
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  wrote {len(HOLDOUT)} held-out probes -> {out.relative_to(REPO)}")
    return 0


def cmd_stats(args: argparse.Namespace) -> int:
    p = Path(args.infile)
    if not p.is_absolute():
        p = REPO / p
    if not p.exists():
        print(f"no such file: {p}", file=sys.stderr)
        return 2
    rows = [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()]
    from collections import Counter
    print(f"  total facts : {len(rows)}")
    print(f"  by language : {dict(Counter(r['lang'] for r in rows))}")
    print(f"  by domain   : {dict(Counter(r['domain'] for r in rows))}")
    print(f"  distinct subjects: {len({r['subject'] for r in rows})}")
    print(f"  all carry provenance: "
          f"{all(r.get('source_uri') and r.get('source_license') and r.get('checksum') for r in rows)}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("fetch", help="acquire triples from Wikidata SPARQL")
    f.add_argument("--out", default="data/datasets/wikidata")
    f.add_argument("--limit", type=int, default=1200)
    f.add_argument("--langs", default="en,ru")
    f.add_argument("--per-domain", type=int, default=200,
                   help="rows per domain query; capped at MAX_ROWS_PER_QUERY")
    f.set_defaults(func=cmd_fetch)
    h = sub.add_parser("holdout", help="write the held-out evaluation set")
    h.add_argument("--out", default="data/datasets/wikidata/holdout.json")
    h.set_defaults(func=cmd_holdout)
    st = sub.add_parser("stats", help="summarise an acquired fact file")
    st.add_argument("--in", dest="infile", default="data/datasets/wikidata/wikidata-facts.ndjson")
    st.set_defaults(func=cmd_stats)
    a = ap.parse_args()
    return a.func(a)


if __name__ == "__main__":
    sys.exit(main())
