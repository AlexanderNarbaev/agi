#!/usr/bin/env python3
"""E2 — the retrieval-floor sweep (EVID-BASE).

**What it measures.** The official epistemic evaluation (``scripts/w51-epistemic-eval.py``)
at several values of the retrieval floor, which the production code reads from the system
property ``matrix.retrieval.minimum-coverage`` (``KnowledgeRetriever.configuredMinimumCoverage``).

**Why the sweep is a fair experiment.** The floor is the only variable: the runner, the
dataset, the corpus, the application and the machine are unchanged between the runs. The
JVM inherits the property through ``JAVA_TOOL_OPTIONS``, so the official runner is not
modified and its report path is separate for every run.

**What it writes.** One machine report per floor under ``docs-v2/engineering/`` and a
comparison artifact ``E2-FLOOR-SWEEP.json`` holding, for every case and every floor, the
verdict and the first problem — so a reader can see exactly which case moved and why.

USAGE
-----
    JAVA_HOME=<jdk-25> python3 scripts/e2-retrieval-experiment.py [--floors 0.25,0.10,0.0]
"""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RUNNER = ROOT / "scripts" / "w51-epistemic-eval.py"
REPORT_DIR = ROOT / "docs-v2" / "engineering"
PROPERTY = "matrix.retrieval.minimum-coverage"
CITATION_PROPERTY = "matrix.retrieval.citation-coverage"


def run_one(floor: str, report: Path, force: bool, mode: str) -> dict:
    """Runs the official evaluation with the floor configured, and reads its report.

    The official runner exits 1 whenever any case fails (that is its own
    contract), so a non-zero exit is NOT a failed experiment: the experiment
    failed only when the report is missing.
    """
    if report.is_file() and not force:
        print("=== floor %s -> reusing %s" % (floor, report.name), flush=True)
        result = json.loads(report.read_text(encoding="utf-8"))
        result["_exitCode"] = "reused"
        return result
    environment = dict(os.environ)
    if mode == "cite":
        # the answer floor stays at its default; only the citation floor moves
        environment["JAVA_TOOL_OPTIONS"] = "-D%s=%s" % (CITATION_PROPERTY, floor)
    else:
        environment["JAVA_TOOL_OPTIONS"] = "-D%s=%s" % (PROPERTY, floor)
    command = [sys.executable, str(RUNNER), "--report", str(report)]
    print("=== floor %s -> %s" % (floor, report.name), flush=True)
    completed = subprocess.run(command, cwd=str(ROOT), env=environment,
                               capture_output=True, text=True)
    if not report.is_file():
        tail = (completed.stdout or "")[-1500:] + (completed.stderr or "")[-1500:]
        raise SystemExit("the runner produced no report at floor %s (exit %d):\n%s"
                         % (floor, completed.returncode, tail))
    result = json.loads(report.read_text(encoding="utf-8"))
    result["_exitCode"] = completed.returncode
    return result


def first_problem(result: dict) -> str:
    problems = result.get("problems") or []
    if not problems:
        return ""
    problem = problems[0]
    if isinstance(problem, dict):
        return str(problem.get("problem", ""))[:200]
    return str(problem)[:200]


def main(argv):
    parser = argparse.ArgumentParser(description="the E2 retrieval-floor sweep")
    parser.add_argument("--floors", default="0.25,0.0")
    parser.add_argument("--force", action="store_true",
                        help="re-run a floor even when its report exists")
    parser.add_argument("--mode", choices=("answer", "cite"), default="answer",
                        help="which floor the values are: the answer floor or the citation floor")
    arguments = parser.parse_args(argv[1:])
    floors = [f.strip() for f in arguments.floors.split(",") if f.strip()]

    reports = {}
    for floor in floors:
        prefix = "E2-CITE" if arguments.mode == "cite" else "E2-FLOOR"
        report = REPORT_DIR / ("%s-%s.json" % (prefix, floor.replace(".", "p")))
        reports[floor] = run_one(floor, report, arguments.force, arguments.mode)

    # the comparison artifact: every case, every floor, verdict + first problem
    case_ids = [r["id"] for r in reports[floors[0]]["results"]]
    comparison = {
        "record": "E2-FLOOR-SWEEP",
        "date": "2026-11-14",
        "property": CITATION_PROPERTY if arguments.mode == "cite" else PROPERTY,
        "floors": floors,
        "dataset": reports[floors[0]]["dataset"],
        "datasetVersion": reports[floors[0]]["datasetVersion"],
        "perFloor": {f: {"passed": reports[f]["passed"], "total": reports[f]["total"],
                         "byCategory": reports[f]["byCategory"]} for f in floors},
        "cases": {},
    }
    for case_id in case_ids:
        row = {"category": None, "verdicts": {}, "problems": {}}
        for floor in floors:
            result = next(r for r in reports[floor]["results"] if r["id"] == case_id)
            row["category"] = result["category"]
            row["verdicts"][floor] = result["verdict"]
            row["problems"][floor] = first_problem(result)
        comparison["cases"][case_id] = row

    out = REPORT_DIR / ("E2-CITE-SWEEP.json" if arguments.mode == "cite"
                       else "E2-FLOOR-SWEEP.json")
    out.write_text(json.dumps(comparison, indent=2), encoding="utf-8")

    print()
    print("=== the sweep")
    for floor in floors:
        print("  floor %-6s %d/%d  %s" % (floor, reports[floor]["passed"],
                                          reports[floor]["total"],
                                          reports[floor]["byCategory"]))
    print()
    moved = [cid for cid, row in comparison["cases"].items()
             if len(set(row["verdicts"].values())) > 1]
    print("  the cases whose verdict moved: %s" % (moved or "none"))
    print("  the comparison is at %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
