#!/usr/bin/env python3
"""W53 — the workloads of the performance report, merged reproducibly.

The mandate asks for two workloads (§10.2): a normal single-task path and a concurrent
path with several tasks in flight. They are measured in two runs, because the cold start
belongs to the first one and must not be hidden inside the second. The report therefore
has to hold both, and it has to be reproducible: a reader must be able to run the two
commands and get the same document.

A third measurement was added when the report had to answer the bound the coordinator
keeps (§10.1's "queue depth and wait time" and §16's "bounded retry behaviour"): the same
workload with **more tasks in flight than the coordinator's cap**, which measures the
refusal instead of the throughput. It is a workload of its own and it keeps its own
section.

This script merges the measurements into the canonical report:

    scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json
    scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4 --report /tmp/concurrent.json
    scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 8 --report /tmp/over-cap.json
    scripts/w53-merge-performance.py /tmp/single.json /tmp/concurrent.json /tmp/over-cap.json \
        docs-v2/engineering/W53-PERFORMANCE.json

Each run keeps its own section, named by the workload it measured, and the document states
which command produced which section. Nothing is averaged between the runs: the workloads
answer different questions, and a single number for all of them would answer none.
"""

import json
import sys

# The sections, in the order the files are given. The names say what the run measured,
# not how many files were passed: a reader must not have to count the arguments to know
# which number belongs to which workload.
SECTION_NAMES = ("sequentialRun", "concurrentRun", "overCapRun")


def main() -> int:
    if len(sys.argv) < 4 or len(sys.argv) > 5:
        print("usage: w53-merge-performance.py <first.json> <second.json> "
              "[<third.json>] <report.json>", file=sys.stderr)
        return 2
    if len(sys.argv) == 5:
        first_path, second_path, third_path, report_path = sys.argv[1:5]
    else:
        first_path, second_path, report_path = sys.argv[1:4]
        third_path = None

    paths = [first_path, second_path] + ([third_path] if third_path else [])
    documents = []
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            documents.append(json.load(handle))

    runs = []
    for name, document in zip(SECTION_NAMES, documents):
        runs.append((name, document))

    sections = {section: document for section, document in runs}
    if len(sections) != len(runs):
        print("the measurements have the same concurrency; the report cannot hold both "
              "under one name", file=sys.stderr)
        return 1

    # The section names say what the run measured, so the concurrencies must match them:
    # one at a time, then more than one, then more than the second run. A measurement
    # that does not fit its name is a mistake the report must not carry.
    concurrencies = [run.get("workload", {}).get("concurrency") for _, run in runs]
    if any(c is None for c in concurrencies):
        print("a measurement does not state its concurrency; the report cannot name it",
              file=sys.stderr)
        return 1
    if concurrencies[0] != 1 or concurrencies[1] <= 1:
        print("the first measurement must be the single-task path and the second must "
              "have more than one task in flight", file=sys.stderr)
        return 1
    if len(concurrencies) > 2 and concurrencies[2] <= concurrencies[1]:
        print("the third measurement must have more tasks in flight than the second",
              file=sys.stderr)
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
    document["schema"] = "matrix.w53.distributed-performance/v3"
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
        "it does not average the workloads into one number: they answer different "
        "questions, and each keeps its own section",
        "the section overCapRun measures the refusal the coordinator's concurrency cap "
        "makes when more tasks arrive than the cap allows; its throughput is the "
        "throughput of the accepted tasks alone and is not the throughput of the path",
    ]

    with open(report_path, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(f"[w53-merge] the report is written to {report_path} with the sections: "
          + ", ".join(sorted(sections)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
