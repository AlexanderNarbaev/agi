#!/usr/bin/env python3
"""E2 — candidate recall and the per-case cause classification (EVID-BASE).

**What it measures.** For every failing citation case of the official evaluation, and
against the SAME workspace the evaluation builds:

1. whether the gold proposition is a claim of the workspace at all (and its epistemic status);
2. where it ranks in the retriever's candidate list, and at which rank the answer cites it;
3. Recall@K and MRR over the cases;
4. the measured cause of the failure, from those facts — the floor (the claim is citable and
   cited but the required proposition/source is missing from the citation set), the status
   filter (the claim exists but is not citable), the ranking (citable, but ranked/matched
   below what the answer cites), or downstream (the answer cites the gold and still fails).

**How it gets the candidate list without changing production code.** The application is
started with ``-Dmatrix.retrieval.minimum-coverage=0.0`` — the existing knob, whose default
is unchanged — so every citable claim with any term match is cited, in the retriever's rank
order. The workspace's claims are read from ``GET /v1/workbench/workspaces/{id}/claims``,
which carries each claim's status.

USAGE
-----
    JAVA_HOME=<jdk-25> python3 scripts/e2-candidate-recall.py [--port 9396]
"""

import argparse
import importlib.util
import json
import os
import re
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "matrix-core" / "src" / "main" / "resources" / "epistemic-eval" / "cases.json"
OUT = ROOT / "docs-v2" / "engineering" / "E2-CANDIDATE-RECALL.json"
CITATION_CASES = ("A-02", "A-04", "A-05", "A-06", "M-01", "M-03", "P-01", "P-02")


def load_runner():
    spec = importlib.util.spec_from_file_location(
        "w51eval", str(ROOT / "scripts" / "w51-epistemic-eval.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def normalise(text):
    """Case- and whitespace-insensitive comparison of propositions."""
    return re.sub(r"\s+", " ", (text or "").strip().lower()).rstrip(".?!")


def gold_for(case):
    expect = case.get("expect") or {}
    propositions = expect.get("requiredPropositions") or []
    return [normalise(p) for p in propositions], expect.get("requiredSource")


def main(argv):
    parser = argparse.ArgumentParser(description="the E2 candidate-recall measurement")
    parser.add_argument("--port", type=int, default=9396)
    parser.add_argument("--work", default="/tmp/e2-candidate-recall")
    arguments = parser.parse_args(argv[1:])

    runner = load_runner()
    dataset = json.loads(DATASET.read_text(encoding="utf-8"))
    scopes = dataset["scopes"]
    cases = {case["id"]: case for case in dataset["cases"]}

    os.environ["JAVA_TOOL_OPTIONS"] = "-Dmatrix.retrieval.minimum-coverage=0.0"
    process = runner.start_application(arguments.port, arguments.work)
    base = "http://127.0.0.1:%d" % arguments.port
    record = {"record": "E2-CANDIDATE-RECALL", "date": "2026-11-14",
              "property": "matrix.retrieval.minimum-coverage=0.0",
              "cases": {}, "summary": {}}
    try:
        prepared = {}
        for scope_id in ("S1", "P1"):
            prepared[scope_id] = runner.prepare_scope(base, scopes[scope_id])

        claims_by_scope = {}
        for scope_id in ("S1", "P1"):
            workspace = scopes[scope_id]["workspace"]
            response = runner.call(base, "GET",
                                   "/v1/workbench/workspaces/%s/claims"
                                   % urllib.parse.quote(workspace))
            if isinstance(response, list):
                claims_by_scope[scope_id] = response
            else:
                claims_by_scope[scope_id] = (response.get("claims")
                                             or response.get("items") or [])

        ranks = []
        for case_id in CITATION_CASES:
            case = cases[case_id]
            scope_id = "S1" if case["scope"] == "S1" else "P1"
            response = runner.run_case(base, case, scopes[case["scope"]])
            cited = response.get("claims") or []
            cited_text = [normalise(c.get("proposition")) for c in cited]
            workspace_claims = claims_by_scope[scope_id]
            gold_props, required_source = gold_for(case)
            cited_sources = [s.get("id") or s.get("name") for s in (response.get("sources") or [])]

            cited_titles = {s.get("title") for s in (response.get("sources") or [])}
            raw_cited = [c.get("proposition") or "" for c in cited]
            row = {"scope": case["scope"], "answered": response.get("answered"),
                   "citedClaims": len(cited), "citedSources": sorted(t for t in cited_titles if t),
                   "gold": []}
            for gold in gold_props:
                claim = next((c for c in workspace_claims
                              if normalise(c.get("proposition")) == gold), None)
                claim_text = (claim or {}).get("proposition") or ""
                raw_matches = [text for text in raw_cited if normalise(text) == gold]
                entry = {
                    "proposition": gold[:120],
                    "inWorkspace": claim is not None,
                    "status": (claim or {}).get("status"),
                    "established": (claim or {}).get("established"),
                    "citedExact": any(text == claim_text and claim_text for text in raw_cited),
                    "citedNormalised": bool(raw_matches),
                    "citedRank": (raw_cited.index(raw_matches[0]) + 1) if raw_matches else None,
                    "cause": None,
                }
                if claim is None:
                    entry["cause"] = "NOT-IN-WORKSPACE (the claim was not extracted)"
                elif entry["citedExact"]:
                    entry["cause"] = "CITED-EXACTLY (this claim cannot be the failure)"
                    ranks.append(entry["citedRank"])
                elif entry["citedNormalised"]:
                    # the judge compares RAW strings: show where they differ
                    stored = claim_text
                    first = next((i for i, (a, b) in enumerate(zip(stored, gold)) if a != b),
                                 min(len(stored), len(gold)))
                    entry["cause"] = "CITED-WITH-TEXT-DIFFERENCE (judge compares raw strings)"
                    entry["differenceAt"] = first
                    entry["storedText"] = stored[:140]
                    entry["datasetText"] = gold[:140]
                elif claim.get("status") is not None and not claim.get("established"):
                    entry["cause"] = "STATUS-FILTER (the claim exists, is not established/citable)"
                else:
                    entry["cause"] = "RANKING (citable, but not cited even at floor 0.0)"
                row["gold"].append(entry)
            row["requiredSource"] = required_source
            row["requiredSourcePresent"] = (required_source in cited_titles) if required_source else None
            record["cases"][case_id] = row

        # Recall@K / MRR over the gold claims that are citable
        for k in (1, 5, 10):
            record["summary"]["recallAt%d" % k] = sum(1 for r in ranks if r <= k)
        record["summary"]["goldCitedWithFloorZero"] = len(ranks)
        record["summary"]["mrr"] = (round(sum(1.0 / r for r in ranks) / len(ranks), 4)
                                    if ranks else None)
        record["summary"]["note"] = ("floor 0.0 cites every citable claim with any term match, "
                                     "so a gold claim absent here has matched zero terms")
    finally:
        runner.stop_application(process)

    OUT.write_text(json.dumps(record, indent=2), encoding="utf-8")
    print("=== the per-case classification (floor 0.0)")
    for case_id, row in record["cases"].items():
        causes = sorted({e["cause"] for e in row["gold"]})
        print("  %-5s cited=%-4d source=%-6s %s" % (case_id, row["citedClaims"],
                                                   str(row["requiredSourcePresent"]),
                                                   "; ".join(causes)[:110]))
        for entry in row["gold"][:2]:
            print("        gold: inWorkspace=%s status=%s rank=%s -- %s"
                  % (entry["inWorkspace"], entry["status"], entry["citedRank"],
                     entry["cause"]))
    print("=== summary: %s" % json.dumps(record["summary"]))
    print("=== the artifact is at %s" % OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
