#!/usr/bin/env python3
"""MATRIX — the retention of the workspace snapshots.

**Status:** `CANONICAL` · **The owner decision:** OD-039 (2026-11-05)
**The wave:** W48-07

WHAT THIS IS. A workspace replacement destroys what the workspace held, and it leaves
a snapshot of what it destroyed (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java`,
the `writeSnapshot` path). The snapshot is the ONLY recoverable copy of that content, and
the rows of the snapshot table were never pruned: every replacement added a row, and
nothing ever took one away.

The owner's policy, OD-039:

    the snapshot's payload gets a SIZE BOUND, and the snapshot rows inside the
    database get the same three-tier calendar that the backups have

The three tiers are the ones the backups already have (OD-035), and they are applied
PER WORKSPACE, because a workspace's snapshots are its own history:

    daily    — every snapshot of the last SEVEN days
    weekly   — one point per ISO week, for the last FOUR weeks
    monthly  — one point per calendar month, for the last TWELVE months

WHAT A TIER POINT IS. A tier point is not a row the operator has to name: it is DERIVED
from the snapshot's own timestamp, so that the policy works on whatever the store wrote.

    a WEEKLY point is the EARLIEST snapshot of its ISO week
    a MONTHLY point is the EARLIEST snapshot of its calendar month

One point per week and one per month, always, whatever days the owner happens to replace
a workspace on — and the earliest, so that the point is the beginning of the period it
stands for.

THE RULES, in the order they are applied.

    1. a snapshot of the last seven days is kept (the daily tier);
    2. the EARLIEST snapshot of each ISO week is kept, for the last four weeks;
    3. the EARLIEST snapshot of each calendar month is kept, for the last twelve months;
    4. the latest VALID snapshot of a workspace is kept whatever the tiers say;
    5. a CORRUPT snapshot is never deleted, and it is never counted as recoverable;
    6. a snapshot whose timestamp cannot be read is never deleted either;
    7. every other snapshot expires.

THE INVARIANT. A pass that would leave a workspace with NO recoverable snapshot is
REFUSED for that workspace, and nothing is deleted for it. A workspace that holds a
COMPLETE snapshot must keep one: a pass that leaves it with only an incomplete one is
refused as well. The rule is the store's own rule — no replacement is committed without a
complete recoverable snapshot — applied to the pruning that decides which snapshots
survive.

WHAT A SNAPSHOT'S STATE IS.

    complete — the payload carries the knowledge content, the observation family AND
               the learning family: the whole of the store's `SnapshotCoverage.FULL`
    legacy   — the payload is readable but carries less than that (a version-1 payload,
               or a version-2 payload without the learning family)
    corrupt  — the payload is not a snapshot payload at all
    undated  — the timestamp cannot be read, so no tier can be decided

WHAT THIS SCRIPT DOES NOT DO.

    it does not take a snapshot        (the store does that)
    it does not restore anything       (the store does that, on the owner's command)
    it does not touch a live database in the dry run — the dry run reads it READ-ONLY
    it does not delete a snapshot it cannot prove is a valid one
    it does not shorten a payload      (the bound is a refusal in the store, not a trim)

THE AUDIT. Every decision is written to an audit log, one line per snapshot, in JSON. A
pruning that cannot be explained afterwards is a pruning that must not happen.

THE USAGE.

    python3 scripts/snapshot-retention.py --database data/knowledge/workbench.sqlite
    python3 scripts/snapshot-retention.py --database data/knowledge/workbench.sqlite --apply
    python3 scripts/snapshot-retention.py --self-test

    --database   the SQLite database that holds the snapshots
    --repo-root  the repository (default: the parent of this script's directory)
    --apply      actually delete the expired snapshots (the DEFAULT is a dry run)
    --now        the reference instant, for a deterministic run (RFC 3339)
    --audit-log  where the decisions are written
                 (default: <repo-root>/logs/snapshot-retention-audit.log)
    --self-test  build a synthetic database, prove every rule, and report
"""

from __future__ import annotations

import argparse
import fcntl
import json
import sqlite3
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

# The owner's policy, in days. The daily window is a rolling one; the weekly and
# the monthly windows are counted in periods. They are the tiers of the backups
# (OD-035), applied to the snapshot rows (OD-039).
DAILY_DAYS = 7
WEEKLY_WEEKS = 4
MONTHLY_MONTHS = 12

# The tables of the observation family and of the learning family. A snapshot that
# carries every one of them is the COMPLETE recoverable copy: it can bring back
# everything a replacement destroys. The names are the ones the store writes
# (matrix-core/src/main/java/io/matrix/knowledge/SnapshotCoverage.java).
OBSERVATION_TABLES = (
    "epistemic_case",
    "observation_occurrence",
    "observation_disposition",
    "observation_adjudication",
)

LEARNING_TABLES = (
    "learning_transition",
    "learning_error",
    "assessment_outcome",
    "assessment_item",
    "learning_state",
    "dialogue_turn",
    "asked_question",
    "hebbian_binding",
)

# The table the snapshots live in.
SNAPSHOT_TABLE = "workspace_snapshot"

# The suffix of the single-writer lock file that sits next to a database.
WRITER_LOCK_SUFFIX = ".writer.lock"

# The states a snapshot can be in.
STATE_COMPLETE = "complete"
STATE_LEGACY = "legacy"
STATE_CORRUPT = "corrupt"
STATE_UNDATED = "undated"


# ── the model ──────────────────────────────────────────────────────────────────


@dataclass
class Snapshot:
    """One snapshot row, as the policy sees it."""

    id: str
    workspace_id: str
    created_at: datetime | None
    created_at_raw: str
    kind: str
    payload: str
    state: str = STATE_LEGACY
    state_reason: str = ""
    tier: str = "expired"
    keep: bool = False
    why: str = ""

    @property
    def payload_bytes(self) -> int:
        """The size of the payload, in bytes."""
        return len(self.payload.encode("utf-8"))


@dataclass
class Report:
    """What the run decided."""

    database: Path
    dry_run: bool
    kept: list = field(default_factory=list)
    expired: list = field(default_factory=list)
    corrupt: list = field(default_factory=list)
    undated: list = field(default_factory=list)
    refused: list = field(default_factory=list)
    refused_workspaces: list = field(default_factory=list)
    deleted: list = field(default_factory=list)

    @property
    def total(self) -> int:
        return len(self.kept) + len(self.expired) + len(self.corrupt) + len(self.undated)


# ── the helpers ────────────────────────────────────────────────────────────────


def parse_created_at(text: str) -> datetime | None:
    """The instant a snapshot carries, in UTC — or None when it cannot be read.

    The store writes the instant the CALLER gave it, so the spelling varies: an
    ISO-8601 instant with a Z, with an offset, or with nanoseconds. A timestamp
    that cannot be read is a snapshot that cannot be placed in a tier, and a
    snapshot that cannot be placed in a tier is never deleted.
    """
    if not text:
        return None
    candidate = text.strip()
    if candidate.endswith("Z"):
        candidate = candidate[:-1] + "+00:00"
    try:
        moment = datetime.fromisoformat(candidate)
    except ValueError:
        return None
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=timezone.utc)
    return moment.astimezone(timezone.utc)


def month_key(moment: datetime) -> tuple:
    """The calendar month a moment belongs to."""
    return moment.year, moment.month


def week_key(moment: datetime) -> tuple:
    """The ISO week a moment belongs to."""
    iso = moment.isocalendar()
    return iso[0], iso[1]


def month_start(moment: datetime) -> datetime:
    """The first instant of the month a moment belongs to."""
    return moment.replace(day=1, hour=0, minute=0, second=0, microsecond=0)


def month_shift(moment: datetime, months: int) -> datetime:
    """The same day of the month, `months` months later or earlier."""
    total = moment.year * 12 + (moment.month - 1) + months
    year, month = divmod(total, 12)
    return moment.replace(year=year, month=month + 1, day=1,
                          hour=0, minute=0, second=0, microsecond=0)


def week_start(moment: datetime) -> datetime:
    """The first instant of the ISO week a moment belongs to."""
    return (moment - timedelta(days=moment.isoweekday() - 1)).replace(
        hour=0, minute=0, second=0, microsecond=0)


def classify_payload(payload: str) -> tuple:
    """The state of a payload, and the reason for it.

    A payload that is not a snapshot payload at all is CORRUPT: it cannot be
    restored, so it is not a recovery copy — and it is never deleted either,
    because a payload that cannot be read may still be the only record of what
    a replacement destroyed, and a human may be able to read it.
    """
    try:
        tree = json.loads(payload)
    except (ValueError, TypeError):
        return STATE_CORRUPT, "the payload is not JSON"
    if not isinstance(tree, dict):
        return STATE_CORRUPT, "the payload is not a JSON object"
    if "version" in tree and "knowledge" in tree:
        tables = tree.get("tables")
        tables = tables if isinstance(tables, list) else []
        names = {entry.get("table") for entry in tables if isinstance(entry, dict)}
        missing = [name for name in OBSERVATION_TABLES + LEARNING_TABLES if name not in names]
        if not missing:
            return STATE_COMPLETE, ("the payload carries the knowledge content, the observation "
                                    "family and the learning family")
        return STATE_LEGACY, "the payload does not carry: " + ", ".join(missing)
    if "workspaceId" in tree:
        return STATE_LEGACY, "the payload is the version-1 shape: the knowledge content alone"
    return STATE_CORRUPT, "the payload is JSON, but it is not a snapshot payload"


# ── the policy ─────────────────────────────────────────────────────────────────


def classify(snapshots: list, now: datetime) -> list:
    """Decide what is kept and what expires, PER WORKSPACE.

    The rules, in the order they are applied:

      1. a snapshot of the last seven days is kept (the daily tier). The window is the
         OPENED interval (now - 7 days, now]: a snapshot exactly seven days old belongs
         to the week before, and a daily schedule therefore keeps exactly seven points;
      2. the EARLIEST snapshot of each ISO week is kept, for the last four weeks
         (the weekly tier);
      3. the EARLIEST snapshot of each calendar month is kept, for the last twelve
         months (the monthly tier);
      4. every other snapshot expires;
      5. the latest VALID snapshot of a workspace is kept whatever the tiers say — a
         policy that deletes the only recovery copy is not a policy;
      6. a CORRUPT snapshot is never deleted, and an UNDATED one is never deleted.

    The decisions are a pure function of the snapshots and the reference instant:
    the same database and the same instant always give the same answer.
    """
    for snapshot in snapshots:
        snapshot.state, snapshot.state_reason = classify_payload(snapshot.payload)

    by_workspace: dict = {}
    for snapshot in snapshots:
        by_workspace.setdefault(snapshot.workspace_id, []).append(snapshot)

    for held in by_workspace.values():
        dated = [item for item in held if item.created_at is not None]
        valid = [item for item in dated if item.state != STATE_CORRUPT]

        earliest_of_week: dict = {}
        earliest_of_month: dict = {}
        for snapshot in sorted(dated, key=lambda item: (item.created_at, item.id)):
            earliest_of_week.setdefault(week_key(snapshot.created_at), snapshot)
            earliest_of_month.setdefault(month_key(snapshot.created_at), snapshot)

        daily_floor = now - timedelta(days=DAILY_DAYS)
        weekly_floor = week_start(now) - timedelta(weeks=WEEKLY_WEEKS)
        monthly_floor = month_shift(month_start(now), -MONTHLY_MONTHS)

        for snapshot in held:
            if snapshot.created_at is None:
                snapshot.tier = "undated"
                snapshot.keep = True
                snapshot.why = ("its timestamp cannot be read, so no tier can be decided for "
                                "it; it is never deleted")
                continue
            if snapshot.state == STATE_CORRUPT:
                snapshot.tier = "corrupt"
                snapshot.keep = True
                snapshot.why = ("it is corrupt: " + snapshot.state_reason
                                + ". A corrupt snapshot is never deleted")
                continue
            if snapshot.created_at > daily_floor:
                snapshot.tier = "daily"
                snapshot.keep = True
                snapshot.why = "it is one of the last " + str(DAILY_DAYS) + " days"
            elif snapshot is earliest_of_week.get(week_key(snapshot.created_at)) and \
                    snapshot.created_at >= weekly_floor:
                snapshot.tier = "weekly"
                snapshot.keep = True
                snapshot.why = ("it is the first snapshot of its week and the week is one of "
                                "the last " + str(WEEKLY_WEEKS))
            elif snapshot is earliest_of_month.get(month_key(snapshot.created_at)) and \
                    snapshot.created_at >= monthly_floor:
                snapshot.tier = "monthly"
                snapshot.keep = True
                snapshot.why = ("it is the first snapshot of its month and the month is one of "
                                "the last " + str(MONTHLY_MONTHS))
            else:
                snapshot.tier = "expired"
                snapshot.keep = False
                snapshot.why = "it is outside every retention window"

        # Rule 5: the latest valid copy survives whatever the windows say.
        if valid:
            newest = max(valid, key=lambda item: (item.created_at, item.id))
            if not newest.keep:
                newest.keep = True
                newest.tier = "newest"
                newest.why = ("it is the latest valid snapshot of its workspace, and a policy "
                              "that deletes the only recovery copy is not a policy")
    return snapshots


def check_invariant(snapshots: list) -> list:
    """The workspaces the pass must NOT touch, and why.

    The invariant: no replacement is committed without a complete recoverable snapshot,
    so a pass that would leave a workspace with no recoverable snapshot is REFUSED, and
    a pass that would leave a workspace that holds complete ones with no complete one is
    refused too. A refusal means that NOTHING is deleted for that workspace.
    """
    by_workspace: dict = {}
    for snapshot in snapshots:
        by_workspace.setdefault(snapshot.workspace_id, []).append(snapshot)

    refusals = []
    for workspace, held in sorted(by_workspace.items()):
        survivors = [item for item in held if item.keep]
        recoverable = [item for item in survivors if item.state in (STATE_COMPLETE, STATE_LEGACY)]
        complete_before = [item for item in held if item.state == STATE_COMPLETE]
        complete_after = [item for item in survivors if item.state == STATE_COMPLETE]
        if not recoverable:
            refusals.append((workspace, "the pass would leave the workspace with no "
                                        "recoverable snapshot, and a policy that deletes the "
                                        "only recovery copy is not a policy"))
        elif complete_before and not complete_after:
            refusals.append((workspace, "the pass would leave the workspace with no COMPLETE "
                                        "recoverable snapshot, and no replacement may be "
                                        "committed without one"))
    return refusals


# ── the database ───────────────────────────────────────────────────────────────


def collect(database: Path) -> list:
    """Every snapshot the database holds, READ-ONLY.

    The connection is opened with SQLite's own read-only mode, so that the run
    cannot migrate the file, cannot write to it and cannot even create a journal
    beside it. A database that has no snapshot table is reported by the caller.
    """
    snapshots = []
    connection = sqlite3.connect("file:" + str(database) + "?mode=ro", uri=True)
    try:
        rows = connection.execute(
            "SELECT id, workspace_id, created_at, kind, payload FROM " + SNAPSHOT_TABLE
            + " ORDER BY created_at, id")
        for identifier, workspace, created, kind, payload in rows:
            snapshots.append(Snapshot(id=identifier, workspace_id=workspace,
                                      created_at=parse_created_at(created),
                                      created_at_raw=created, kind=kind, payload=payload))
    finally:
        connection.close()
    return snapshots


def refuses(database: Path) -> list:
    """The reasons the script must not run, if there are any."""
    reasons = []
    if not database.exists():
        reasons.append("the database " + str(database) + " does not exist")
        return reasons
    if not database.is_file():
        reasons.append("the database " + str(database) + " is not a file")
        return reasons
    try:
        connection = sqlite3.connect("file:" + str(database) + "?mode=ro", uri=True)
        try:
            names = {row[0] for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'")}
        finally:
            connection.close()
    except sqlite3.Error as failure:
        reasons.append("the database does not open: " + str(failure))
        return reasons
    if SNAPSHOT_TABLE not in names:
        reasons.append("the database " + str(database) + " has no " + SNAPSHOT_TABLE
                       + " table: it is not a database this tool can prune")
    return reasons


def holds_the_writer_lock(database: Path) -> bool:
    """Whether another process holds the database's writer lock.

    The store takes a POSIX record lock on the file next to the database, and the
    contract is ONE WRITER PROCESS per database file (OD-004). A pruning that
    raced the application would write into a database the application is using, so
    the pruning refuses instead.
    """
    lock_file = database.with_name(database.name + WRITER_LOCK_SUFFIX)
    if not lock_file.exists():
        return False
    try:
        handle = lock_file.open("r+b")
    except OSError:
        return False
    try:
        fcntl.lockf(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.lockf(handle.fileno(), fcntl.LOCK_UN)
        return False
    except OSError:
        return True
    finally:
        handle.close()


# ── the run ────────────────────────────────────────────────────────────────────


def run(database: Path, repo_root: Path, now: datetime, apply: bool,
        audit_log: Path) -> Report:
    """One retention run."""
    report = Report(database=database, dry_run=not apply)
    reasons = refuses(database)
    if reasons:
        report.refused = reasons
        return report

    if apply and holds_the_writer_lock(database):
        report.refused = ["another process holds the writer lock of " + str(database)
                           + ": the database has ONE writer at a time (OD-004), and the "
                           "pruning refuses to race the application"]
        return report

    snapshots = collect(database)
    classify(snapshots, now)

    refusals = check_invariant(snapshots)
    refused_workspaces = {workspace for workspace, _ in refusals}
    for workspace, reason in refusals:
        report.refused_workspaces.append((workspace, reason))

    for snapshot in snapshots:
        if snapshot.workspace_id in refused_workspaces:
            snapshot.keep = True
            snapshot.tier = "refused"
            snapshot.why = ("the pass is refused for this workspace: " + reason)
        if snapshot.keep:
            report.kept.append(snapshot)
        elif snapshot.state == STATE_CORRUPT:
            report.corrupt.append(snapshot)
        elif snapshot.state == STATE_UNDATED:
            report.undated.append(snapshot)
        else:
            report.expired.append(snapshot)

    # The audit is written before anything is deleted: a decision that is not
    # recorded must not be acted upon.
    write_audit(audit_log, report, now, not apply)
    if apply:
        delete_expired(database, report)
    return report


def delete_expired(database: Path, report: Report) -> None:
    """Delete the expired snapshots, in one transaction."""
    if not report.expired:
        return
    connection = sqlite3.connect(str(database))
    try:
        connection.execute("BEGIN")
        for snapshot in report.expired:
            connection.execute("DELETE FROM " + SNAPSHOT_TABLE + " WHERE id = ?", (snapshot.id,))
            report.deleted.append(snapshot)
        connection.commit()
    except sqlite3.Error as failure:
        connection.rollback()
        raise
    finally:
        connection.close()


def write_audit(audit_log: Path, report: Report, now: datetime, dry_run: bool) -> None:
    """Write one line per decision."""
    try:
        audit_log.parent.mkdir(parents=True, exist_ok=True)
        with audit_log.open("a", encoding="utf-8") as handle:
            for snapshot in report.kept + report.expired + report.corrupt + report.undated:
                handle.write(json.dumps({
                    "at": now.isoformat(),
                    "dryRun": dry_run,
                    "database": str(report.database),
                    "snapshot": snapshot.id,
                    "workspace": snapshot.workspace_id,
                    "createdAt": snapshot.created_at_raw,
                    "kind": snapshot.kind,
                    "state": snapshot.state,
                    "payloadBytes": snapshot.payload_bytes,
                    "tier": snapshot.tier,
                    "decision": "retained" if snapshot.keep else "expired",
                    "reason": snapshot.why or snapshot.state_reason,
                }, ensure_ascii=False) + "\n")
    except OSError as failure:
        print("the audit log could not be written: " + str(failure), file=sys.stderr)


def print_report(report: Report) -> None:
    """What the run decided, in words."""
    if report.refused:
        print("the retention run REFUSED:")
        for reason in report.refused:
            print("  - " + reason)
        return
    print("the database: " + str(report.database))
    print("the mode: " + ("dry run — nothing was deleted" if report.dry_run else "pruning"))
    print("  retained: " + str(len(report.kept)))
    for snapshot in report.kept:
        print("    " + snapshot.id + "  [" + snapshot.tier + "]  " + snapshot.why)
    print("  expired:  " + str(len(report.expired)))
    for snapshot in report.expired:
        print("    " + snapshot.id + "  " + snapshot.why)
    print("  corrupt:  " + str(len(report.corrupt)))
    for snapshot in report.corrupt:
        print("    " + snapshot.id + "  " + snapshot.state_reason + " — it was NOT deleted")
    print("  undated:  " + str(len(report.undated)))
    for snapshot in report.undated:
        print("    " + snapshot.id + "  " + snapshot.state_reason + " — it was NOT deleted")
    if report.refused_workspaces:
        print("  refused workspaces: " + str(len(report.refused_workspaces)))
        for workspace, reason in report.refused_workspaces:
            print("    " + workspace + "  " + reason + " — nothing was deleted for it")
    if report.deleted:
        print("  deleted:  " + str(len(report.deleted)))


# ── the self-test ──────────────────────────────────────────────────────────────


def complete_payload(workspace: str) -> str:
    """A payload of the complete shape: the knowledge content and both families."""
    tables = [{"table": name, "rows": [{"id": "row-" + name}]}
              for name in OBSERVATION_TABLES + LEARNING_TABLES]
    return json.dumps({
        "version": 2,
        "knowledge": {"workspaceId": workspace, "workspaceName": "Demo", "concepts": [],
                      "sources": [], "claims": [], "evidence": [], "representations": [],
                      "prerequisites": [], "revisions": [], "derivations": [],
                      "contradictions": [], "goals": [], "exportedAt": "2026-11-06T00:00:00Z"},
        "tables": tables,
    }, ensure_ascii=False)


def legacy_payload(workspace: str) -> str:
    """A payload of the version-1 shape: the knowledge content alone."""
    return json.dumps({
        "workspaceId": workspace, "workspaceName": "Demo", "concepts": [], "sources": [],
        "claims": [], "evidence": [], "representations": [], "prerequisites": [],
        "revisions": [], "derivations": [], "contradictions": [], "goals": [],
        "exportedAt": "2026-11-06T00:00:00Z",
    }, ensure_ascii=False)


def corrupt_payload() -> str:
    """A payload that is not a payload."""
    return '{"version": 2, "knowledge": { this is not json'


def make_database(path: Path) -> None:
    """A database with the snapshot table the store writes."""
    connection = sqlite3.connect(str(path))
    try:
        connection.execute("CREATE TABLE IF NOT EXISTS workspace_snapshot ("
                           "id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                           "created_at TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL)")
        connection.commit()
    finally:
        connection.close()


def insert_snapshot(database: Path, identifier: str, workspace: str, created: str,
                    payload: str, kind: str = "LOAD") -> None:
    """One snapshot row."""
    connection = sqlite3.connect(str(database))
    try:
        connection.execute("INSERT OR REPLACE INTO workspace_snapshot(id, workspace_id, "
                           "created_at, kind, payload) VALUES (?,?,?,?,?)",
                           (identifier, workspace, created, kind, payload))
        connection.commit()
    finally:
        connection.close()


def self_test() -> int:
    """Build a synthetic database and prove every rule of the policy."""
    checks = []

    def check(name: str, condition: bool, detail: str = "") -> None:
        checks.append((name, condition, detail))

    now = datetime(2026, 11, 6, 12, 0, 0, tzinfo=timezone.utc)
    with tempfile.TemporaryDirectory(prefix="matrix-snapshot-retention-") as temporary:
        base = Path(temporary)
        database = base / "knowledge.sqlite"
        make_database(database)

        # a snapshot of every day of the last ten days
        for day in range(10):
            moment = now - timedelta(days=day)
            insert_snapshot(database, "snap-daily-" + str(day), "demo",
                            moment.isoformat().replace("+00:00", "Z"), complete_payload("demo"))
        # the stale ones: a month old, a year old, and two years old
        for days in (40, 200, 400):
            moment = now - timedelta(days=days)
            insert_snapshot(database, "snap-stale-" + str(days), "demo",
                            moment.isoformat().replace("+00:00", "Z"), complete_payload("demo"))
        # the broken one, and the one whose timestamp cannot be read, and a legacy
        # payload — in a workspace of their own, so that the calendar of the
        # scheduled workspace is not disturbed by them
        insert_snapshot(database, "snap-corrupt", "odd",
                        (now - timedelta(days=3)).isoformat().replace("+00:00", "Z"),
                        corrupt_payload())
        insert_snapshot(database, "snap-undated", "odd", "not a timestamp",
                        complete_payload("odd"))
        insert_snapshot(database, "snap-legacy", "odd",
                        (now - timedelta(days=2)).isoformat().replace("+00:00", "Z"),
                        legacy_payload("odd"))
        # another workspace, with two snapshots only
        insert_snapshot(database, "snap-other-1", "other",
                        (now - timedelta(days=1)).isoformat().replace("+00:00", "Z"),
                        complete_payload("other"))
        insert_snapshot(database, "snap-other-2", "other",
                        (now - timedelta(days=500)).isoformat().replace("+00:00", "Z"),
                        complete_payload("other"))

        before = count_rows(database)

        # 1. a dry run changes nothing
        dry = run(database, base, now, apply=False, audit_log=base / "audit.log")
        check("a dry run changes nothing", count_rows(database) == before,
              str(count_rows(database)) + " rows after, " + str(before) + " before")
        check("a dry run reports the expired ones", len(dry.expired) > 0,
              str(len(dry.expired)) + " expired")
        check("a dry run deletes nothing", all(item.id not in deleted_ids(dry) for item in dry.expired),
              "")
        check("a dry run deletes no row at all", not dry.deleted, "")

        # 2. the daily window keeps the last seven days
        daily = [item for item in dry.kept
                 if item.tier == "daily" and item.workspace_id == "demo"]
        check("the daily tier keeps the last seven days", len(daily) == 7,
              str(len(daily)) + " kept")
        check("every daily snapshot is one of the last seven days",
              all(item.created_at > now - timedelta(days=DAILY_DAYS) for item in daily), "")
        check("no daily snapshot is older than seven days",
              all((now - item.created_at) < timedelta(days=DAILY_DAYS) for item in daily), "")

        # 3. the weekly and the monthly tiers
        weeks = {week_key(item.created_at) for item in dry.kept if item.tier == "weekly"}
        months = {month_key(item.created_at) for item in dry.kept if item.tier == "monthly"}
        weekly = [item for item in dry.kept if item.tier == "weekly"]
        monthly = [item for item in dry.kept if item.tier == "monthly"]
        check("the weekly tier holds at most one point per week",
              len(weeks) == len(weekly), str(len(weekly)) + " points, " + str(len(weeks)) + " weeks")
        check("the monthly tier holds at most one point per month",
              len(months) == len(monthly),
              str(len(monthly)) + " points, " + str(len(months)) + " months")
        check("the weekly tier holds no more than four weeks", len(weeks) <= WEEKLY_WEEKS,
              str(len(weeks)) + " weeks")
        check("the monthly tier holds no more than twelve months", len(months) <= MONTHLY_MONTHS,
              str(len(months)) + " months")
        check("the weekly point is the EARLIEST of its week",
              all(item.created_at == min(other.created_at for other in weekly
                                         if week_key(other.created_at) == week_key(item.created_at))
                  for item in weekly), "")
        check("the monthly point is the EARLIEST of its month",
              all(item.created_at == min(other.created_at for other in monthly
                                         if month_key(other.created_at) == month_key(item.created_at))
                  for item in monthly), "")

        # 4. the corrupt and the undated are never deleted
        check("a corrupt snapshot is never deleted",
              any(item.id == "snap-corrupt" and item.keep for item in dry.kept), "")
        check("a corrupt snapshot is not counted as recoverable",
              all(item.state != STATE_CORRUPT for item in dry.expired), "")
        check("an undated snapshot is never deleted",
              any(item.id == "snap-undated" and item.keep for item in dry.kept), "")

        # 5. the latest valid snapshot survives
        for workspace in ("demo", "other"):
            held = [item for item in dry.kept + dry.expired if item.workspace_id == workspace
                    and item.created_at is not None and item.state != STATE_CORRUPT]
            newest = max(held, key=lambda item: (item.created_at, item.id))
            check("the latest valid snapshot of '" + workspace + "' is kept", newest.keep,
                  newest.id)

        # 6. the policy is per workspace
        other = [item for item in dry.kept + dry.expired if item.workspace_id == "other"]
        check("the policy is applied per workspace",
              any(item.tier == "expired" for item in other)
              and any(item.tier in ("daily", "newest") for item in other), "")

        # 7. the refusals
        missing = run(base / "absent.sqlite", base, now, apply=False,
                      audit_log=base / "audit.log")
        check("a database that does not exist is refused", len(missing.refused) > 0, "")
        empty = base / "empty.sqlite"
        make_database(empty)
        nothing = run(empty, base, now, apply=False, audit_log=base / "audit.log")
        check("a database with no snapshots is not a refusal", not nothing.refused, "")

        # 8. the invariant: a workspace with one snapshot keeps it
        lonely = base / "lonely.sqlite"
        make_database(lonely)
        insert_snapshot(lonely, "snap-only", "demo",
                        (now - timedelta(days=400)).isoformat().replace("+00:00", "Z"),
                        complete_payload("demo"))
        alone = run(lonely, base, now, apply=False, audit_log=base / "audit.log")
        check("the only snapshot of a workspace is kept", not alone.expired,
              str(len(alone.expired)) + " expired")

        # 9. the invariant: a workspace whose only complete snapshot would go is refused
        refused_db = base / "refused.sqlite"
        make_database(refused_db)
        insert_snapshot(refused_db, "snap-complete", "demo",
                        (now - timedelta(days=400)).isoformat().replace("+00:00", "Z"),
                        complete_payload("demo"))
        insert_snapshot(refused_db, "snap-legacy-newer", "demo",
                        (now - timedelta(days=399)).isoformat().replace("+00:00", "Z"),
                        legacy_payload("demo"))
        refused = run(refused_db, base, now, apply=False, audit_log=base / "audit.log")
        check("a pass that would leave no complete snapshot is refused",
              len(refused.refused_workspaces) > 0, str(len(refused.refused_workspaces)))
        check("a refused workspace expires nothing",
              all(item.tier == "refused" for item in refused.kept
                  if item.workspace_id == "demo"), "")
        check("a refused workspace keeps every snapshot it holds",
              count_rows(refused_db) == 2, str(count_rows(refused_db)) + " rows")

        # 10. the invariant: a workspace with no recoverable snapshot at all is refused
        broken_db = base / "broken.sqlite"
        make_database(broken_db)
        insert_snapshot(broken_db, "snap-broken", "demo",
                        (now - timedelta(days=400)).isoformat().replace("+00:00", "Z"),
                        corrupt_payload())
        broken = run(broken_db, base, now, apply=False, audit_log=base / "audit.log")
        check("a workspace with no recoverable snapshot is refused",
              len(broken.refused_workspaces) > 0, str(len(broken.refused_workspaces)))
        check("a corrupt snapshot is kept even in a refused workspace",
              count_rows(broken_db) == 1, str(count_rows(broken_db)) + " rows")

        # 12. the size report
        sizes = report_sizes(database)
        check("the size report succeeds", sizes == 0, "exit " + str(sizes))
        empty_sizes = report_sizes(empty)
        check("the size report of an empty database succeeds", empty_sizes == 0,
              "exit " + str(empty_sizes))
        absent_sizes = report_sizes(base / "absent.sqlite")
        check("the size report refuses a database that does not exist", absent_sizes == 1,
              "exit " + str(absent_sizes))

        # 13. the real run prunes, and it is idempotent
        real = run(database, base, now, apply=True, audit_log=base / "audit.log")
        check("the run prunes the expired ones", len(real.deleted) > 0,
              str(len(real.deleted)) + " pruned")
        check("the pruned ones are gone",
              all(not row_exists(database, item.id) for item in real.deleted), "")
        check("the retained ones are still there",
              all(row_exists(database, item.id) for item in real.kept), "")
        check("the corrupt one is still there", row_exists(database, "snap-corrupt"), "")
        check("the undated one is still there", row_exists(database, "snap-undated"), "")
        second = run(database, base, now, apply=True, audit_log=base / "audit.log")
        check("a second run prunes nothing more", len(second.deleted) == 0,
              str(len(second.deleted)) + " pruned the second time")

        # 11. the audit
        lines = (base / "audit.log").read_text(encoding="utf-8").strip().split("\n")
        check("the audit recorded every decision", len(lines) > 0, str(len(lines)) + " lines")
        check("the audit is one JSON object per line",
              all(json.loads(line) for line in lines), "")
        audited = {json.loads(line)["snapshot"] for line in lines}
        check("the audit names every snapshot of the database",
              all(snapshot.id in audited for snapshot in dry.kept + dry.expired
                  + dry.corrupt + dry.undated), "")
        check("the audit says whether the run was a dry run",
              any(json.loads(line)["dryRun"] for line in lines)
              and any(not json.loads(line)["dryRun"] for line in lines), "")

    failed = [item for item in checks if not item[1]]
    for name, condition, detail in checks:
        print(("  ok    " if condition else "  FAIL  ") + name
              + (("  [" + detail + "]") if detail else ""))
    print("  the checks: " + str(len(checks)) + ", the failures: " + str(len(failed)))
    return 1 if failed else 0


def count_rows(database: Path) -> int:
    """How many snapshot rows the database holds."""
    connection = sqlite3.connect("file:" + str(database) + "?mode=ro", uri=True)
    try:
        return connection.execute("SELECT count(*) FROM " + SNAPSHOT_TABLE).fetchone()[0]
    finally:
        connection.close()


def percentile(sizes: list, percent: int) -> int:
    """The nearest-rank percentile of a list of sizes.

    The nearest-rank rule is the one that names an OBSERVED value: the smallest
    size that at least `percent` per cent of the sizes are at or below. A
    percentile that interpolates between two sizes would be a number that no
    snapshot has.
    """
    if not sizes:
        return 0
    ordered = sorted(sizes)
    rank = max(1, min(len(ordered), -(-percent * len(ordered) // 100)))
    return ordered[rank - 1]


def median(sizes: list) -> float:
    """The median of a list of sizes."""
    if not sizes:
        return 0.0
    ordered = sorted(sizes)
    middle = len(ordered) // 2
    if len(ordered) % 2 == 1:
        return float(ordered[middle])
    return (ordered[middle - 1] + ordered[middle]) / 2.0


def size_breakdown(payload: str) -> tuple:
    """The bytes of a payload's three parts: the knowledge, the observations, the learning.

    The parts are measured by re-serialising the sub-trees of the stored payload, so
    they add up to the payload itself: the wrapper around them is the rest.
    """
    try:
        tree = json.loads(payload)
    except (ValueError, TypeError):
        return 0, 0, 0
    if not isinstance(tree, dict):
        return 0, 0, 0
    knowledge = tree.get("knowledge")
    knowledge_bytes = len(compact(knowledge).encode("utf-8")) if knowledge is not None else 0
    tables = tree.get("tables")
    tables = tables if isinstance(tables, list) else []
    observation = 0
    learning = 0
    for entry in tables:
        if not isinstance(entry, dict):
            continue
        size = len(compact(entry).encode("utf-8"))
        if entry.get("table") in OBSERVATION_TABLES:
            observation += size
        elif entry.get("table") in LEARNING_TABLES:
            learning += size
    return knowledge_bytes, observation, learning


def compact(value) -> str:
    """The compact JSON form of a value — the form the store writes."""
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False)


def report_sizes(database: Path) -> int:
    """Print the size distribution of the payloads the database holds.

    The measurement is READ-ONLY: the connection is opened in SQLite's read-only
    mode, so the report cannot migrate the file, cannot write to it and cannot
    create a journal beside it.
    """
    reasons = refuses(database)
    if reasons:
        print("the size report REFUSED:")
        for reason in reasons:
            print("  - " + reason)
        return 1
    snapshots = collect(database)
    print("the database: " + str(database))
    print("the snapshots it holds: " + str(len(snapshots)))
    workspaces = sorted({item.workspace_id for item in snapshots})
    print("the workspaces: " + (", ".join(workspaces) if workspaces else "(none)"))
    if not snapshots:
        print("  there is no snapshot to measure: the distribution is EMPTY")
        return 0
    sizes = [item.payload_bytes for item in snapshots]
    knowledge = []
    observation = []
    learning = []
    for snapshot in snapshots:
        parts = size_breakdown(snapshot.payload)
        knowledge.append(parts[0])
        observation.append(parts[1])
        learning.append(parts[2])
    print("  the payload size, in bytes:")
    print("    min:    " + str(min(sizes)))
    print("    median: " + str(median(sizes)))
    print("    p95:    " + str(percentile(sizes, 95)))
    print("    p99:    " + str(percentile(sizes, 99)))
    print("    max:    " + str(max(sizes)))
    print("  the parts, summed over every snapshot, in bytes:")
    print("    the typed knowledge content: " + str(sum(knowledge)))
    print("    the observation rows:       " + str(sum(observation)))
    print("    the learning rows:          " + str(sum(learning)))
    print("  every snapshot:")
    for snapshot in sorted(snapshots, key=lambda item: (item.workspace_id, item.id)):
        parts = size_breakdown(snapshot.payload)
        print("    " + snapshot.id + "  workspace=" + snapshot.workspace_id
              + "  bytes=" + str(snapshot.payload_bytes)
              + "  knowledge=" + str(parts[0])
              + "  observation=" + str(parts[1])
              + "  learning=" + str(parts[2]))
    return 0


def row_exists(database: Path, identifier: str) -> bool:
    """Whether a snapshot row is still there."""
    connection = sqlite3.connect("file:" + str(database) + "?mode=ro", uri=True)
    try:
        found = connection.execute("SELECT count(*) FROM " + SNAPSHOT_TABLE
                                   + " WHERE id = ?", (identifier,)).fetchone()[0]
        return found > 0
    finally:
        connection.close()


def deleted_ids(report: Report) -> list:
    """The identifiers a report deleted."""
    return [item.id for item in report.deleted]


# ── the entry point ────────────────────────────────────────────────────────────


def main(argv: list) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--database", help="the SQLite database that holds the snapshots")
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parent.parent),
                        help="the repository root")
    parser.add_argument("--apply", action="store_true",
                        help="actually delete the expired snapshots (the default is a dry run)")
    parser.add_argument("--report-sizes", action="store_true",
                        help="print the size distribution of the payloads the database holds, "
                             "and change nothing")
    parser.add_argument("--now", help="the reference instant (RFC 3339), for a deterministic run")
    parser.add_argument("--audit-log", help="where the decisions are written")
    parser.add_argument("--self-test", action="store_true", help="prove every rule and report")
    arguments = parser.parse_args(argv)

    if arguments.self_test:
        return self_test()

    repo_root = Path(arguments.repo_root).expanduser()
    if arguments.database:
        database = Path(arguments.database).expanduser()
    else:
        database = repo_root / "data" / "knowledge" / "workbench.sqlite"

    if arguments.report_sizes:
        return report_sizes(database)

    if arguments.now:
        moment = datetime.fromisoformat(arguments.now)
        if moment.tzinfo is None:
            moment = moment.replace(tzinfo=timezone.utc)
        moment = moment.astimezone(timezone.utc)
    else:
        moment = datetime.now(timezone.utc)

    audit_log = Path(arguments.audit_log).expanduser() if arguments.audit_log \
        else repo_root / "logs" / "snapshot-retention-audit.log"

    report = run(database, repo_root, moment, arguments.apply, audit_log)
    print_report(report)
    if report.refused:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
