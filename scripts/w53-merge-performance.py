#!/usr/bin/env python3
"""W53 — the two workloads of the performance report, merged reproducibly.

The mandate asks for two workloads (§10.2): a normal single-task path and a concurrent
path with several tasks in flight. They are measured in two runs, because the cold start
belongs to the first one and must not be hidden inside the second. The report therefore
has to hold both, and it has to be reproducible: a reader must be able to run the two
commands and get the same document.

This script merges the two measurements into the canonical report:

    scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json
    scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4 --report /tmp/concurrent.json
    scripts/w53-merge-performance.py /tmp/single.json /tmp/concurrent.json \
        docs-v2/engineering/W53-PERFORMANCE.json

Each run keeps its own section, named by its concurrency, and the document states which
command produced which section. Nothing is averaged between the runs: the two workloads
answer different questions, and a single number for both would answer neither.
"""

import json
import sys


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: w53-merge-performance.py <first.json> <second.json> <report.json>",
              file=sys.stderr)
        return 2
    first_path, second_path, report_path = sys.argv[1:4]

    with open(first_path, encoding="utf-8") as handle:
        first = json.load(handle)
    with open(second_path, encoding="utf-8") as handle:
        second = json.load(handle)

    runs = []
    for document in (first, second):
        concurrency = document.get("workload", {}).get("concurrency")
        section = "concurrentRun" if concurrency and concurrency > 1 else "sequentialRun"
        runs.append((section, document))

    sections = {section: document for section, document in runs}
    if len(sections) != len(runs):
        print("the two measurements have the same concurrency; the report cannot hold both "
              "under one name", file=sys.stderr)
        return 1

    base = runs[0][1]
    document = dict(base)
    document.update(sections)
    # The replay path belongs to the run that measured it, not to the document: it lives
    # inside each run's own section, so a reader cannot take the sequential run's replay
    # for the concurrent one.
    document.pop("replayPath", None)
    document.pop("replaySamples", None)
    document.pop("processMetricsReplayPass", None)
    document["schema"] = "matrix.w53.distributed-performance/v2"
    document["workloads"] = {
        section: {
            "command": f"bash scripts/w53-distributed-benchmark.sh --tasks "
                       f"{run.get('workload', {}).get('tasks')} --concurrency "
                       f"{run.get('workload', {}).get('concurrency')} --report "
                       f"<this run>.json",
            "tasks": run.get("workload", {}).get("tasks"),
            "concurrency": run.get("workload", {}).get("concurrency"),
            "runStamp": run.get("workload", {}).get("runStamp"),
        }
        for section, run in sections.items()
    }
    document["whatThisReportDoesNotClaim"] = list(
        document.get("whatThisReportDoesNotClaim", [])) + [
        "it does not average the two workloads into one number: they answer different "
        "questions, and each keeps its own section",
    ]

    with open(report_path, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(f"[w53-merge] the report is written to {report_path} with the sections: "
          + ", ".join(sorted(sections)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
