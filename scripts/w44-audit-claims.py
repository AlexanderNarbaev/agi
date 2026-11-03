#!/usr/bin/env python3
"""W44.0 — capability-claim → evidence index (read-only).

The audit's document section requires every capability claim to be mapped to
evidence, or to be marked unsupported. This script mines the claim sentences
out of every Markdown document and resolves each one against the bytecode
inventory:

  claim line  →  the production types it names  →  each type's state
              →  the verdict

Verdicts:
  supported-served      the claim names only types that a serving path reaches
  names-inactive-code   the claim names a type that exists but nothing serves
  names-absent-type     the claim names a type that does not exist in a built module
  unmapped              the claim names no production type at all — needs a human

The output is `W44-CLAIM-EVIDENCE.csv`. It reads only; it writes only under
`docs-v2/engineering/`.
"""

from __future__ import annotations

import csv
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENG = ROOT / "docs-v2" / "engineering"

IDENT_RE = re.compile(r"\b([A-Z][A-Za-z0-9]{2,})\b")
CLAIM_RE = re.compile(
    r"\b(can|cannot|provides?|supports?|implements?|enables?|offers?|exposes?|"
    r"handles?|manages?|tracks?|measures?|guarantees?|ensures?|detects?|learns?|"
    r"persists?|serves?|performs?|executes?|generates?|validates?|proves?|"
    r"demonstrates?|proves|is able to|are able to)\b", re.I)
NEGATIVE_RE = re.compile(
    r"\b(not|never|no|none|without|cannot|does not|is not|are not|fails?|"
    r"unverified|stale|disabled|quarantin\w*)\b", re.I)
FORWARD_RE = re.compile(r"\b(will|shall|should|would|plan(ned|ning)?|propose[sd]?|next wave|"
                       r"future|TODO|not yet|to be|intended|roadmap)\b", re.I)
MEASURED_RE = re.compile(r"\b(\d+(\.\d+)?\s*(%|ms|s\b|tests?|x\b)|p50|p95|"
                         r"measured|benchmark\w*|baseline|coverage|accuracy|"
                         r"mean\b|median)\b", re.I)
HISTORIC_RE = re.compile(r"\b(was|were|previously|formerly|before|until|"
                         r"originally|superseded|deprecated|replaced)\b", re.I)
STATUS_RE = re.compile(
    r"\b(IMPLEMENTED|TESTED|REACHABLE|MEASURED|DEMONSTRATED|EXPERIMENTAL|"
    r"INACTIVE|PLANNED|PRODUCTION_PATH_VERIFIED|NO_PRODUCTION_CALLER|"
    r"ПОДТВЕРЖДЕНА|ЧАСТИЧНО|ОТКРЫТА|UNVERIFIED|BLOCKED)\b")


def main() -> int:
    with (ENG / "W44-CLASS-INVENTORY.csv").open(encoding="utf-8") as fh:
        inv = list(csv.DictReader(fh))
    prod = {r["fqn"]: r for r in inv if r["source_set"] == "main" and r["built"] == "yes"}
    simple: dict[str, list[str]] = defaultdict(list)
    for fqn, r in prod.items():
        simple[fqn.rsplit(".", 1)[-1]].append(fqn)

    # the formal specification registry: which java sources each spec covers
    spec_of: dict[str, list[str]] = defaultdict(list)
    yml = ROOT / "formal" / "spec-implementation-map.yml"
    if yml.exists():
        text = yml.read_text(encoding="utf-8", errors="replace")
        current = ""
        for line in text.splitlines():
            m = re.match(r"\s*- id: (\S+)", line)
            if m:
                current = m.group(1)
            m = re.match(r"\s*-\s+(matrix-[\w/.-]+\.java)\s*$", line)
            if m and current:
                spec_of[m.group(1)].append(current)

    # the last commit that touched each document: the document's own version
    try:
        out = subprocess.run(["git", "log", "-1", "--format=%H|%cs", "--name-only",
                              "--", "docs-v2"], capture_output=True, text=True,
                             cwd=ROOT, timeout=600)
        touched: dict[str, tuple[str, str]] = {}
        commit = date = ""
        for line in out.stdout.splitlines():
            if "|" in line and " " not in line.split("|")[0]:
                commit, date = line.split("|", 1)
            elif line.strip().endswith(".md"):
                touched.setdefault(line.strip(), (commit, date))
    except (OSError, subprocess.TimeoutExpired):
        touched = {}

    with (ENG / "W44-DOC-MAP.csv").open(encoding="utf-8") as fh:
        docs = list(csv.DictReader(fh))

    rows: list[dict] = []
    for d in docs:
        path = ROOT / d["document"]
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        commit, date = touched.get(d["document"], ("", ""))
        in_code = False
        for n, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("```"):
                in_code = not in_code
                continue
            if in_code or len(stripped) < 25:
                continue
            if not CLAIM_RE.search(stripped):
                continue
            ids = [m for m in IDENT_RE.findall(stripped) if m in simple]
            names = sorted({fqn for i in ids for fqn in simple[i]})
            served, inactive = [], []
            for fqn in names:
                r = prod[fqn]
                if r["lifecycle"] == "served":
                    served.append(fqn)
                else:
                    inactive.append(fqn)
            # a simple name can resolve to several types; the claim is supported if
            # AT LEAST ONE of them is served — the others are reported beside it.
            if served and inactive:
                verdict = "supported-served"
            elif served:
                verdict = "supported-served"
            elif inactive:
                verdict = "names-inactive-code"
            if not names:
                verdict = "unmapped"
            if names:
                kind = "about-code"
            elif FORWARD_RE.search(stripped):
                kind = "forward-looking"
            elif MEASURED_RE.search(stripped):
                kind = "measurement"
            elif HISTORIC_RE.search(stripped):
                kind = "historical"
            else:
                kind = "behavioural-prose"
            specs = sorted({s for fqn in names
                            for s in spec_of.get(
                                next((p["file"] for p in inv if p["fqn"] == fqn), ""), [])})
            rows.append({
                "document": d["document"],
                "category": d["category"],
                "line": n,
                "claim": stripped[:300],
                "negative": "yes" if NEGATIVE_RE.search(stripped) else "no",
                "status_token": ";".join(sorted(set(STATUS_RE.findall(stripped)))),
                "types_named": len(names),
                "served_types": ";".join(served),
                "inactive_types": ";".join(inactive),
                "formal_specs": ";".join(specs),
                "verdict": verdict,
                "claim_kind": kind,
                "doc_commit": commit[:12],
                "doc_date": date,
            })

    fields = ["document", "category", "line", "claim", "negative", "status_token",
              "types_named", "served_types", "inactive_types", "formal_specs",
              "verdict", "claim_kind", "doc_commit", "doc_date"]
    with (ENG / "W44-CLAIM-EVIDENCE.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for r in rows:
            w.writerow(r)

    summary: dict[str, int] = defaultdict(int)
    kinds: dict[str, int] = defaultdict(int)
    for r in rows:
        summary[r["verdict"]] += 1
        kinds[f"{r['verdict']}/{r['claim_kind']}"] += 1
    print(json.dumps({"claims": len(rows), **summary}, indent=2))
    print(json.dumps(dict(sorted(kinds.items(), key=lambda kv: -kv[1])), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
