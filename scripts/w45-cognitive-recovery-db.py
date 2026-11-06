#!/usr/bin/env python3
"""W45.1 — reads the durable state of a cognitive request out of the SQLite store.

The harness needs NUMBERS, not a claim about them: how many episodes a request
opened, how many cases and how many OCCURRENCES it committed, what the
idempotency row says about its lifecycle, and what the append-only history of
the request explains. This reader prints all of that as JSON, and one field of
it on request.

The store is opened in QUERY-ONLY mode rather than read-only: a SQLite database
in WAL mode needs the -shm file, and a read-only connection cannot create it.
`PRAGMA query_only=1` refuses every write while still letting the WAL machinery
work, so the reader never touches the data.

Usage:
    w45-cognitive-recovery-db.py <database> [<idempotency-key>] [--field <path>]

The field path is dotted: `idempotency.state`, `episodes_of_request`,
`request_history.0.state`. Without --field the whole snapshot is printed.
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys

# The tables the journey counts. A table that the store has not created yet
# counts as None ("not there"), which is different from zero ("there and empty").
TABLES = (
    ("episodes", "cognitive_episode"),
    ("predictions", "cognitive_prediction"),
    ("errors", "cognitive_error"),
    ("events", "cognitive_event"),
    ("cases", "epistemic_case"),
    ("occurrences", "observation_occurrence"),
    ("evidence", "evidence"),
    ("sources", "source"),
    ("request_events", "cognitive_request_event"),
)

IDEMPOTENCY_COLUMNS = (
    "key", "episode_id", "signal_id", "state", "checkpoint", "attempts", "request_id",
    "owner_pid", "failure", "recovery", "created_at", "completed_at",
)


def open_store(path: str) -> sqlite3.Connection | None:
    """Opens the store for reading only. Returns None when the file is not there."""
    if not os.path.exists(path):
        return None
    conn = sqlite3.connect(path, timeout=15)
    conn.execute("PRAGMA busy_timeout=15000")
    conn.execute("PRAGMA query_only=1")
    return conn


def snapshot(db: str, key: str | None) -> dict:
    """The durable state of the store, and of one request when a key is given."""
    out: dict = {"database": db, "database_exists": os.path.exists(db)}
    conn = open_store(db)
    if conn is None:
        out["note"] = "the database file does not exist yet"
        return out
    try:
        names = {row[0] for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        for label, table in TABLES:
            # A table the store has not created yet is NOT a count of zero: it
            # is a table that is not there, and the two mean different things.
            out[label] = (conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
                          if table in names else "absent")
        if not key:
            return out

        row = conn.execute(
            "SELECT key, episode_id, signal_id, state, checkpoint, attempts, request_id, "
            "owner_pid, failure, recovery, created_at, completed_at "
            "FROM cognitive_idempotency WHERE key = ?", (key,)).fetchone()
        if row is None:
            out["idempotency"] = "no-row"
            out["request_history"] = []
            return out
        record = dict(zip(IDEMPOTENCY_COLUMNS, row))
        out["idempotency"] = record

        episode = record["episode_id"]
        if episode:
            out["episodes_of_request"] = conn.execute(
                "SELECT COUNT(*) FROM cognitive_episode WHERE id = ?", (episode,)).fetchone()[0]
            out["predictions_of_request"] = conn.execute(
                "SELECT COUNT(*) FROM cognitive_prediction WHERE episode_id = ?",
                (episode,)).fetchone()[0]
            closed = conn.execute(
                "SELECT closed_at FROM cognitive_episode WHERE id = ?", (episode,)).fetchone()
            out["episode_closed_at"] = closed[0] if closed else None
            if "observation_occurrence" in names:
                out["occurrences_of_request"] = conn.execute(
                    "SELECT COUNT(*) FROM observation_occurrence WHERE episode_id = ?",
                    (episode,)).fetchone()[0]
                out["case_ids_of_request"] = [r[0] for r in conn.execute(
                    "SELECT DISTINCT case_id FROM observation_occurrence WHERE episode_id = ? "
                    "ORDER BY case_id", (episode,))]
        out["request_history"] = [
            {"state": r[0], "checkpoint": r[1], "detail": r[2], "occurred_at": r[3]}
            for r in conn.execute(
                "SELECT state, checkpoint, detail, occurred_at FROM cognitive_request_event "
                "WHERE key = ? ORDER BY occurred_at, rowid", (key,))]
    finally:
        conn.close()
    return out


def at(data, path: str):
    """The value at a dotted path of the snapshot."""
    current = data
    for part in path.split("."):
        if isinstance(current, dict):
            current = current.get(part)
        elif isinstance(current, list):
            try:
                current = current[int(part)]
            except (ValueError, IndexError):
                return ""
        else:
            return ""
    return current


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    db = argv[1]
    key = argv[2] if len(argv) > 2 and not argv[2].startswith("--") else None
    field = None
    if "--field" in argv:
        field = argv[argv.index("--field") + 1]
    data = snapshot(db, key)
    if field is None:
        print(json.dumps(data, indent=2, ensure_ascii=False))
        return 0
    value = at(data, field)
    if isinstance(value, (dict, list)):
        print(json.dumps(value, indent=2, ensure_ascii=False))
    elif value is None:
        print("")
    else:
        print(value)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
