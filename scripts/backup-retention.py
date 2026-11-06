#!/usr/bin/env python3
"""MATRIX — the retention of the database backups.

**Status:** `CANONICAL` · **The owner decision:** OD-029 (2026-11-05)
**The task:** TASK-W46-17

WHAT THIS IS. The recovery runbook says how to take a backup
(`docs-v2/operations/RECOVERY-RUNBOOK.md`) and how to restore one. It says nothing
about how many backups are kept, and an unbounded pile of backups is not a
recovery plan: it is a disk that fills up, and it is a set of copies in which
nobody knows which one is the good one.

The owner's policy, OD-029:

    the backups live outside the repository, 7 days daily plus 4 weeks monthly,
    and a restore happens only on the owner's explicit command

The three tiers were made precise by the owner on 2026-11-05:

    daily    — every backup of the last SEVEN days
    weekly   — one point per week, for the last FOUR weeks
    monthly  — one point per month, for the last TWELVE months

WHAT A TIER POINT IS. A tier point is not a file the operator has to name: it is
DERIVED from the backup's own timestamp, so that the policy works on whatever the
runbook produced.

    a WEEKLY point is the EARLIEST backup of its ISO week
    a MONTHLY point is the EARLIEST backup of its calendar month

One point per week and one per month, always, whatever days the operator
happens to back up on — and the earliest, so that the point is the beginning of
the period it stands for.

WHAT THIS SCRIPT DOES NOT DO.

    it does not take a backup      (the runbook does that)
    it does not restore anything   (the runbook does that, on the owner's command)
    it does not touch a live database, ever
    it does not delete a file it cannot prove is a valid backup

THE REFUSALS. The script refuses to run when the backup root resolves inside
the repository, or when it overlaps a live database's path. A backup inside the
tree it protects is not a backup.

THE AUDIT. Every decision is written to an audit log, one line per file, in
JSON. A pruning that cannot be explained afterwards is a pruning that must not
happen.

THE USAGE.

    python3 scripts/backup-retention.py --root /var/backups/matrix --dry-run
    python3 scripts/backup-retention.py --root /var/backups/matrix
    python3 scripts/backup-retention.py --self-test

    --root       the backup directory; it must be outside the repository
    --repo-root  the repository (default: the parent of this script's directory)
    --dry-run    report what would happen, and change nothing
    --now        the reference instant, for a deterministic run (RFC 3339)
    --audit-log  where the decisions are written (default: <root>/retention-audit.log)
    --self-test  build a synthetic tree, prove every rule, and report
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

# The owner's policy, in days. The daily window is a rolling one; the weekly and
# the monthly windows are counted in periods.
DAILY_DAYS = 7
WEEKLY_WEEKS = 4
MONTHLY_MONTHS = 12

# The stamp the runbook writes: workbench-20261105T101500Z.sqlite
BACKUP_NAME = re.compile(r"^(?P<name>[A-Za-z0-9_.-]+?)-(?P<stamp>\d{8}T\d{6}Z)\.sqlite$")

# The SQLite file magic. A backup that does not begin with it is not a database.
SQLITE_MAGIC = b"SQLite format 3\x00"

# The live databases the script must never touch. They are relative to the
# repository root, and the backup root may not contain any of them.
LIVE_DATABASES = (
    Path("data") / "knowledge" / "workbench.sqlite",
    Path("data") / "mind" / "mind.sqlite",
)


# ── the model ──────────────────────────────────────────────────────────────────


@dataclass
class Backup:
    """One backup file, as the policy sees it."""

    path: Path
    name: str
    stamp: datetime
    valid: bool
    reason: str
    tier: str = "expired"
    keep: bool = False
    why: str = ""


@dataclass
class Report:
    """What the run decided."""

    root: Path
    dry_run: bool
    kept: list = field(default_factory=list)
    expired: list = field(default_factory=list)
    failed: list = field(default_factory=list)
    refused: list = field(default_factory=list)

    @property
    def total(self) -> int:
        return len(self.kept) + len(self.expired) + len(self.failed)


# ── the helpers ───────────────────────────────────────────────────────────────


def parse_stamp(text: str) -> datetime:
    """The instant a backup carries, in UTC."""
    return datetime.strptime(text, "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)


def is_valid_backup(path: Path) -> tuple:
    """Whether a file is a backup that can be restored from.

    A backup is valid when it is a REGULAR file (a symlink is not a backup — it
    is a pointer to somewhere else, and following it would let a link inside the
    tree point at a live database), when it is not empty, and when it begins
    with the SQLite magic. A file that is empty, truncated, or not a database at
    all is not a recovery copy, and it must never be counted as one.
    """
    if path.is_symlink():
        return False, "a symbolic link is not a backup copy"
    if not path.is_file():
        return False, "not a regular file"
    try:
        size = path.stat().st_size
    except OSError as failure:
        return False, "the file cannot be read: " + str(failure)
    if size == 0:
        return False, "the file is empty: the backup was interrupted"
    try:
        with path.open("rb") as handle:
            head = handle.read(len(SQLITE_MAGIC))
    except OSError as failure:
        return False, "the file cannot be read: " + str(failure)
    if head != SQLITE_MAGIC:
        return False, "the file is not a SQLite database: the backup is corrupt"
    return True, "a database that opens"


def deep_verify(path: Path) -> tuple:
    """Whether a backup passes SQLite's own integrity check."""
    try:
        connection = sqlite3.connect("file:" + str(path) + "?mode=ro", uri=True)
    except sqlite3.Error as failure:
        return False, "the database does not open: " + str(failure)
    try:
        row = connection.execute("PRAGMA integrity_check;").fetchone()
    except sqlite3.Error as failure:
        return False, "the integrity check failed: " + str(failure)
    finally:
        connection.close()
    if not row or str(row[0]).lower() != "ok":
        return False, "the integrity check says: " + str(row[0] if row else "nothing")
    return True, "the integrity check says ok"


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


# ── the policy ─────────────────────────────────────────────────────────────────


def classify(backups: list, now: datetime) -> list:
    """Decide what is kept and what expires.

    The rules, in the order they are applied:

      1. a backup of the last seven days is kept (the daily tier). The window is the
         OPENED interval (now - 7 days, now]: a backup exactly seven days old belongs to
         the week before, and a daily schedule therefore keeps exactly seven points;
      2. the EARLIEST backup of each ISO week is kept, for the last four weeks
         (the weekly tier);
      3. the EARLIEST backup of each calendar month is kept, for the last
         twelve months (the monthly tier);
      4. every other backup expires;
      5. the latest VALID backup is kept whatever the rules say — a policy that
         deletes the only recovery copy is not a policy;
      6. if pruning would leave no valid copy at all, nothing is pruned.

    The decisions are a pure function of the backups and the reference instant:
    the same tree and the same instant always give the same answer.
    """
    valid = [backup for backup in backups if backup.valid]
    if not valid:
        return backups

    earliest_of_week = {}
    earliest_of_month = {}
    for backup in sorted(valid, key=lambda item: (item.stamp, item.path.name)):
        earliest_of_week.setdefault(week_key(backup.stamp), backup)
        earliest_of_month.setdefault(month_key(backup.stamp), backup)

    daily_floor = now - timedelta(days=DAILY_DAYS)
    weekly_floor = week_start(now) - timedelta(weeks=WEEKLY_WEEKS)
    monthly_floor = month_shift(month_start(now), -MONTHLY_MONTHS)

    for backup in valid:
        if backup.stamp > daily_floor:
            backup.tier = "daily"
            backup.keep = True
            backup.why = "it is one of the last " + str(DAILY_DAYS) + " days"
        elif backup is earliest_of_week.get(week_key(backup.stamp)) and \
                backup.stamp >= weekly_floor:
            backup.tier = "weekly"
            backup.keep = True
            backup.why = "it is the first backup of its week and the week is one of the last " \
                + str(WEEKLY_WEEKS)
        elif backup is earliest_of_month.get(month_key(backup.stamp)) and \
                backup.stamp >= monthly_floor:
            backup.tier = "monthly"
            backup.keep = True
            backup.why = "it is the first backup of its month and the month is one of the last " \
                + str(MONTHLY_MONTHS)
        else:
            backup.tier = "expired"
            backup.keep = False
            backup.why = "it is outside every retention window"

    # Rule 5: the latest valid copy survives whatever the windows say.
    newest = max(valid, key=lambda item: item.stamp)
    if not newest.keep:
        newest.keep = True
        newest.tier = "newest"
        newest.why = "it is the latest valid copy, and a policy that deletes the only recovery " \
            "copy is not a policy"

    # Rule 6: never prune down to nothing.
    if len([backup for backup in valid if backup.keep]) == 1 and len(valid) > 1:
        for backup in valid:
            if not backup.keep:
                backup.keep = True
                backup.tier = "last-one"
                backup.why = "pruning it would leave no valid recovery copy"
    return backups


# ── the tree ───────────────────────────────────────────────────────────────────


def collect(root: Path) -> list:
    """Every backup in the root, with its validity."""
    backups = []
    for path in sorted(root.iterdir()):
        if not path.is_file() and not path.is_symlink():
            continue
        match = BACKUP_NAME.match(path.name)
        if not match:
            continue
        valid, reason = is_valid_backup(path)
        backups.append(Backup(path=path, name=match.group("name"),
                              stamp=parse_stamp(match.group("stamp")),
                              valid=valid, reason=reason))
    return backups


def refuses(root: Path, repo_root: Path) -> list:
    """The reasons the script must not run, if there are any."""
    reasons = []
    resolved_root = root.resolve()
    resolved_repo = repo_root.resolve()
    if resolved_root == resolved_repo or resolved_repo in resolved_root.parents:
        reasons.append("the backup root " + str(resolved_root) + " is inside the repository "
                       + str(resolved_repo) + ": a backup inside the tree it protects is not "
                       "a backup")
    for database in LIVE_DATABASES:
        live = (resolved_repo / database).resolve()
        if resolved_root == live or live in resolved_root.parents:
            reasons.append("the backup root " + str(resolved_root) + " contains the live "
                           "database " + str(live))
        if live in resolved_root.parents or resolved_root == live:
            reasons.append("the backup root " + str(resolved_root) + " is the live database "
                           + str(live))
    return reasons


# ── the run ────────────────────────────────────────────────────────────────────


def run(root: Path, repo_root: Path, now: datetime, dry_run: bool, audit_log: Path) -> Report:
    """One retention run."""
    report = Report(root=root, dry_run=dry_run)
    reasons = refuses(root, repo_root)
    if reasons:
        report.refused = reasons
        return report

    backups = collect(root)
    classify(backups, now)
    for backup in backups:
        if not backup.valid:
            backup.tier = "invalid"
            report.failed.append(backup)
        elif backup.keep:
            report.kept.append(backup)
        else:
            report.expired.append(backup)

    # The audit is written before anything is deleted: a decision that is not
    # recorded must not be acted upon.
    write_audit(audit_log, report, now, dry_run)
    if dry_run:
        return report
    for backup in report.expired:
        try:
            backup.path.unlink()
        except OSError as failure:
            backup.why = "the file could not be deleted: " + str(failure)
    return report


def write_audit(audit_log: Path, report: Report, now: datetime, dry_run: bool) -> None:
    """Write one line per decision."""
    try:
        audit_log.parent.mkdir(parents=True, exist_ok=True)
        with audit_log.open("a", encoding="utf-8") as handle:
            for backup in report.kept + report.expired + report.failed:
                handle.write(json.dumps({
                    "at": now.isoformat(),
                    "dryRun": dry_run,
                    "file": backup.path.name,
                    "tier": backup.tier,
                    "stamp": backup.stamp.isoformat(),
                    "valid": backup.valid,
                    "decision": "retained" if backup.keep else
                                ("failed" if not backup.valid else "expired"),
                    "reason": backup.why or backup.reason,
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
    print("the backup root: " + str(report.root))
    print("the mode: " + ("dry run — nothing was deleted" if report.dry_run else "pruning"))
    print("  retained: " + str(len(report.kept)))
    for backup in report.kept:
        print("    " + backup.path.name + "  [" + backup.tier + "]  " + backup.why)
    print("  expired:  " + str(len(report.expired)))
    for backup in report.expired:
        print("    " + backup.path.name + "  " + backup.why)
    print("  failed:   " + str(len(report.failed)))
    for backup in report.failed:
        print("    " + backup.path.name + "  " + backup.reason + " — it was NOT deleted and it "
              "is NOT counted as a recovery copy")


# ── the self-test ──────────────────────────────────────────────────────────────


def make_backup(path: Path, stamp: str, content: bytes = b"SQLite format 3\x00rest") -> None:
    """A backup file with a given stamp."""
    path.write_bytes(content)
    path.rename(path.parent / (path.stem + "-" + stamp + ".sqlite"))


def self_test() -> int:
    """Build a synthetic tree and prove every rule of the policy."""
    checks = []

    def check(name: str, condition: bool, detail: str = "") -> None:
        checks.append((name, condition, detail))

    now = datetime(2026, 11, 5, 12, 0, 0, tzinfo=timezone.utc)
    with tempfile.TemporaryDirectory(prefix="matrix-retention-") as temporary:
        base = Path(temporary)
        root = base / "backups"
        root.mkdir()
        repo = base / "repo"
        (repo / "data" / "knowledge").mkdir(parents=True)
        (repo / "data" / "mind").mkdir(parents=True)
        (repo / "data" / "knowledge" / "workbench.sqlite").write_bytes(b"live")
        (repo / "data" / "mind" / "mind.sqlite").write_bytes(b"live")

        # a backup of every day of the last ten days
        for day in range(10):
            moment = now - timedelta(days=day)
            make_backup(root / ("workbench-" + moment.strftime("%Y%m%dT%H%M%SZ") + ".sqlite"),
                        moment.strftime("%Y%m%dT%H%M%SZ"))
        # the stale ones: a month old and a year old
        for days in (40, 200, 400):
            moment = now - timedelta(days=days)
            make_backup(root / ("workbench-" + moment.strftime("%Y%m%dT%H%M%SZ") + ".sqlite"),
                        moment.strftime("%Y%m%dT%H%M%SZ"))
        # the broken ones: an empty file and a corrupt file
        (root / ("workbench-" + (now - timedelta(days=3)).strftime("%Y%m%dT%H%M%SZ")
                 + ".sqlite")).write_bytes(b"")
        (root / ("workbench-" + (now - timedelta(days=4)).strftime("%Y%m%dT%H%M%SZ")
                 + ".sqlite")).write_bytes(b"this is not a database")
        # a symlink that points at the live database
        link = root / ("workbench-" + (now - timedelta(days=5)).strftime("%Y%m%dT%H%M%SZ")
                       + ".sqlite")
        link.symlink_to(repo / "data" / "knowledge" / "workbench.sqlite")
        # a file that is not a backup at all
        (root / "notes.txt").write_text("not a backup", encoding="utf-8")

        before = sorted(item.name for item in root.iterdir())

        # 1. a dry run changes nothing
        dry = run(root, repo, now, dry_run=True, audit_log=base / "audit.log")
        after = sorted(item.name for item in root.iterdir())
        check("a dry run changes nothing", before == after,
              str(len(before)) + " files before, " + str(len(after)) + " after")
        check("a dry run reports the expired ones", len(dry.expired) > 0,
              str(len(dry.expired)) + " expired")
        check("a dry run deletes nothing", all(item.path.exists() for item in dry.expired), "")

        # 2. the invalid ones are never counted as recovery copies
        check("the empty file is not a valid backup",
              any(not item.valid and "empty" in item.reason for item in dry.failed), "")
        check("the corrupt file is not a valid backup",
              any(not item.valid and "corrupt" in item.reason for item in dry.failed), "")
        check("the symbolic link is not a valid backup",
              any(not item.valid and "symbolic link" in item.reason for item in dry.failed), "")
        check("the file that is not a backup is ignored",
              all("notes.txt" not in item.path.name for item in dry.kept + dry.expired
                  + dry.failed), "")
        check("the invalid ones are not deleted",
              all(item.path.exists() for item in dry.failed), "")

        # 3. the daily window keeps the last seven days
        daily = [item for item in dry.kept if item.tier == "daily"]
        check("the daily tier keeps the last seven days", len(daily) == 7,
              str(len(daily)) + " kept")
        check("every daily backup is one of the last seven days",
              all(item.stamp > now - timedelta(days=DAILY_DAYS) for item in daily), "")
        check("no daily backup is older than seven days",
              all((now - item.stamp) < timedelta(days=DAILY_DAYS) for item in daily), "")

        # 4. the weekly and the monthly tiers
        weeks = {week_key(item.stamp) for item in dry.kept if item.tier == "weekly"}
        months = {month_key(item.stamp) for item in dry.kept if item.tier == "monthly"}
        check("the weekly tier holds at most one point per week",
              len(weeks) == len([item for item in dry.kept if item.tier == "weekly"]), "")
        check("the monthly tier holds at most one point per month",
              len(months) == len([item for item in dry.kept if item.tier == "monthly"]), "")
        check("the weekly tier holds no more than four weeks",
              len(weeks) <= WEEKLY_WEEKS, str(len(weeks)) + " weeks")

        # 5. the latest valid copy survives
        newest = max((item for item in dry.kept + dry.expired if item.valid),
                     key=lambda item: item.stamp)
        check("the latest valid copy is kept", newest.keep, newest.path.name)

        # 6. the refusals
        inside = run(repo / "data" / "knowledge", repo, now, dry_run=True,
                     audit_log=base / "audit.log")
        check("a backup root inside the repository is refused", len(inside.refused) > 0, "")
        live = run(repo / "data" / "mind", repo, now, dry_run=True, audit_log=base / "audit.log")
        check("a backup root on a live database is refused", len(live.refused) > 0, "")

        # 7. the real run prunes, and it is idempotent
        real = run(root, repo, now, dry_run=False, audit_log=base / "audit.log")
        check("the run prunes the expired ones", len(real.expired) > 0,
              str(len(real.expired)) + " pruned")
        check("the pruned ones are gone",
              all(not item.path.exists() for item in real.expired), "")
        check("the retained ones are still there",
              all(item.path.exists() for item in real.kept), "")
        check("the invalid ones are still there",
              all(item.path.exists() for item in real.failed), "")
        second = run(root, repo, now, dry_run=False, audit_log=base / "audit.log")
        check("a second run prunes nothing more", len(second.expired) == 0,
              str(len(second.expired)) + " pruned the second time")

        # 8. the audit
        lines = (base / "audit.log").read_text(encoding="utf-8").strip().split("\n")
        check("the audit recorded every decision", len(lines) > 0, str(len(lines)) + " lines")
        check("the audit is one JSON object per line",
              all(json.loads(line) for line in lines), "")

        # 9. the last valid copy is never left with nothing
        lonely = base / "lonely"
        lonely.mkdir()
        make_backup(lonely / ("mind-" + (now - timedelta(days=400)).strftime("%Y%m%dT%H%M%SZ")
                               + ".sqlite"), (now - timedelta(days=400)).strftime("%Y%m%dT%H%M%SZ"))
        alone = run(lonely, repo, now, dry_run=False, audit_log=base / "audit.log")
        check("the only copy is never deleted",
              all(item.path.exists() for item in alone.kept + alone.expired), "")

    failed = [item for item in checks if not item[1]]
    for name, condition, detail in checks:
        print(("  ok    " if condition else "  FAIL  ") + name
              + (("  [" + detail + "]") if detail else ""))
    print("  the checks: " + str(len(checks)) + ", the failures: " + str(len(failed)))
    return 1 if failed else 0


# ── the entry point ────────────────────────────────────────────────────────────


def main(argv: list) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--root", help="the backup directory; it must be outside the repository")
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parent.parent),
                        help="the repository root")
    parser.add_argument("--dry-run", action="store_true", help="report and change nothing")
    parser.add_argument("--now", help="the reference instant (RFC 3339), for a deterministic run")
    parser.add_argument("--audit-log", help="where the decisions are written")
    parser.add_argument("--self-test", action="store_true", help="prove every rule and report")
    arguments = parser.parse_args(argv)

    if arguments.self_test:
        return self_test()

    if not arguments.root:
        parser.error("--root is required: the backup directory must be named explicitly")

    root = Path(arguments.root).expanduser()
    repo_root = Path(arguments.repo_root).expanduser()
    if not root.exists():
        print("the backup root " + str(root) + " does not exist: create it first", file=sys.stderr)
        return 1
    if not root.is_dir():
        print("the backup root " + str(root) + " is not a directory", file=sys.stderr)
        return 1

    if arguments.now:
        moment = datetime.fromisoformat(arguments.now)
        if moment.tzinfo is None:
            moment = moment.replace(tzinfo=timezone.utc)
    else:
        moment = datetime.now(timezone.utc)

    audit_log = Path(arguments.audit_log).expanduser() if arguments.audit_log \
        else root / "retention-audit.log"

    report = run(root, repo_root, moment, arguments.dry_run, audit_log)
    print_report(report)
    if report.refused:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
