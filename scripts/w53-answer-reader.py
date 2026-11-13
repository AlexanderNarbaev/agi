#!/usr/bin/env python3
"""W53 — read one answer of the distributed path into a benchmark sample.

The benchmark runs the tasks concurrently, so each task writes its own answer file
and this reader turns that file into one line of the sample stream. The reader is
a script of its own because a heredoc inside a shell loop that runs in parallel is
harder to read than a file.
"""

import json
import sys


def sample(answer_path, index):
    """One sample: what the disposition was, and how long each stage took."""
    try:
        with open(answer_path, encoding="utf-8") as handle:
            answer = json.load(handle)
    except (OSError, ValueError):
        answer = {}
    return {
        "index": int(index),
        "disposition": answer.get("disposition"),
        "state": answer.get("state"),
        "timingsNanos": answer.get("timingsNanos") or {},
    }


if __name__ == "__main__":
    print(json.dumps(sample(sys.argv[1], sys.argv[2])))
