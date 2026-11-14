#!/usr/bin/env python3
"""W53 — read chain B's durable task inbox.

The demonstration uses this to show what the peer's own durable record says after the
replay: one row per request id, and a row whose updated timestamp equals its creation
timestamp — which is what "the task was not executed a second time" looks like from the
outside. The file is chain B's own; the demonstration reads it, it never writes it.
"""

import json
import os
import sqlite3
import sys

DEFAULT_PATH = os.path.join("data", "mind", "tasks.sqlite")


def rows(path):
    """The rows of the inbox, or an error when the file is not there."""
    if not os.path.exists(path):
        return {"error": "the inbox file is not where it was expected: " + path}
    connection = sqlite3.connect(path)
    try:
        found = list(connection.execute(
            "SELECT request_id, task_id, state, effect_committed, created_at, "
            "updated_at, stage_timings_json "
            "FROM remote_task ORDER BY created_at"))
    finally:
        connection.close()
    return [{
        "requestId": row[0],
        "taskId": row[1],
        "state": row[2],
        "effectCommitted": bool(row[3]),
        "createdAt": row[4],
        "updatedAt": row[5],
        "stageTimings": stages(row[6]),
    } for row in found]


def stages(raw):
    """The stages the peer measured for one row, or None when it measured none."""
    if not raw:
        return None
    try:
        return json.loads(raw)
    except ValueError:
        return None


if __name__ == "__main__":
    print(json.dumps(rows(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PATH), indent=2))
