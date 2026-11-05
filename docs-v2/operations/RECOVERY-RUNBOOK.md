# MATRIX — RECOVERY RUNBOOK

**Status:** `ACTIVE` · **Date:** 2026-11-04
**What it owns:** how to back up the databases, how to restore them, and what the recovery
guarantees actually are.

**The honest statement first:** *there is no file-level backup in the production code.* The
SQLite backup API (`BackupManager`, `VACUUM INTO`) appears **nowhere** in the production
sources. What exists is the **workspace snapshot** of W45.2 — a *logical* export of a
workspace, not a copy of the file. **This runbook closes that gap with a procedure, not with
code**, and it says which of the two it is.

---

## 1. What there is to protect

| the file | the content | the size class | the loss |
|---|---|---|---|
| `data/knowledge/workbench.sqlite` | the epistemic store (v19, 27 tables) **and** the cognitive store (v4, 12 tables) | the project's state | the knowledge, the episodes, the dispositions, the request history |
| `data/mind/mind.sqlite` | chain B's mind store | small | chain B's conversations |
| `data/knowledge/workbench.sqlite-wal` and `-shm` | the write-ahead log | transient | **must not be copied separately** — the backup must be taken with the WAL active |

**One file, two schemas, two connections** — see
`docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`.

---

## 2. The backup

### The rule

> **Never copy a live SQLite file with `cp` while the WAL is active.** The copy may catch the
> file between the WAL checkpoint and the commit, and the result is a database that opens and
> is missing its last transactions.

### The procedure — `VACUUM INTO`

`VACUUM INTO` is the SQLite-native way to take a consistent copy of a live database: it
produces a **new, complete, self-contained** file while the source stays open and writable.

```bash
# the canonical database — the backup must land OUTSIDE the data directory
export JAVA_HOME=/home/alexandr-narbaev/jdk25
sqlite3 data/knowledge/workbench.sqlite \
  "VACUUM INTO '${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"

# chain B's mind store
sqlite3 data/mind/mind.sqlite \
  "VACUUM INTO '${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/mind-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"
```

**The backup directory is outside the repository entirely** — not merely outside `data/`. A
backup inside the tree it protects is not a backup, and the retention script
(`scripts/backup-retention.py`) **refuses** a backup root that resolves inside the
repository, so a runbook that put it there would contradict the tool that prunes it.

The owner's decision OD-029 puts it outside the repository and says how long the backups
live: see §8.

### The procedure — the SQLite CLI's `.backup`

If `VACUUM INTO` is unavailable (SQLite < 3.27), use the CLI's backup command, which takes
the same consistent snapshot:

```bash
sqlite3 data/knowledge/workbench.sqlite ".backup '${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"
```

### The procedure — the live application

**The safest backup is one taken while the writer is not running.** The `WriterLock` (the
owner's decision D4) is a lifetime-held OS lock: **if the application holds it, another
process cannot open the database as a writer.** So:

```bash
# stop the application, then
sqlite3 data/knowledge/workbench.sqlite "VACUUM INTO '${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite'"
# and start it again
```

### Verifying a backup — **do not skip this**

A backup that was not verified is a hope.

```bash
# 1. it opens
sqlite3 "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" "PRAGMA integrity_check;"     # → "ok"

# 2. it has the tables
sqlite3 "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" \
  "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"

# 3. the schema version is the expected one
sqlite3 "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" "SELECT version FROM schema_meta;"

# 4. the row counts are plausible
sqlite3 "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" "SELECT count(*) FROM epistemic_case;"
sqlite3 "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" "SELECT count(*) FROM cognitive_episode;"
```

**The version is written last by the migration chain**, so a backup taken during a migration
carries the *old* version and the next start re-runs the additive steps. That is the design,
not a fault — but it means **a backup taken during a migration is a backup of the pre-migration
schema**, and the restore must run the migration again.

---

## 3. The restore

```bash
# 1. stop the application — the writer lock must be free
# 2. keep the damaged file, do not delete it
mv data/knowledge/workbench.sqlite data/knowledge/workbench.sqlite.damaged-<stamp>
cp "${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}/workbench-<stamp>.sqlite" data/knowledge/workbench.sqlite

# 3. the WAL of the damaged file must not come back
rm -f data/knowledge/workbench.sqlite-wal data/knowledge/workbench.sqlite-shm

# 4. verify
sqlite3 data/knowledge/workbench.sqlite "PRAGMA integrity_check;"
sqlite3 data/knowledge/workbench.sqlite "SELECT version FROM schema_meta;"

# 5. start the application — the migration chain runs if the version is behind
```

**Step 3 is not optional.** A stale `-wal` file next to a restored database is replayed by
SQLite on the next open, and it can undo the restore.

**The damaged file is kept.** It is evidence: a defect that destroyed data is worth
investigating, and the directive forbids deleting evidence.

---

## 4. What recovery is NOT

| the claim | the truth |
|---|---|
| "the workspace snapshot is a backup" | it is a **logical export of a workspace** — the rows, not the file. It does not restore the file, the indexes, the WAL, or the other 26 tables. |
| "the copy is a backup" | a copy of a live WAL database is not. Use `VACUUM INTO` or `.backup`. |
| "the request recovery is a restore" | it is not. `IdempotencyRegistry.recover` resumes an **interrupted request**; it does not restore a lost database. |
| "the runbook had a backup section" | it did not. The W46.0 audit found **no occurrence** of backup, restore or snapshot in `docs-v2/operations/RUNBOOK.md`. This document is that section. |

---

## 5. The restore test — the part that is still owed

**A backup procedure that has never been restored from is a procedure, not a capability.**

**What exists:** the workspace snapshot is **tested** — `WorkspaceSnapshotTest` (12 tests),
`WorkspaceSnapshotMigrationTest` (4) and `WorkspaceLoadEndpointTest` (7), all executed in
W45.2 and W46.1.

**What does not exist:** an executed end-to-end restore of the **whole database** from a
`VACUUM INTO` backup. That test is **TASK-W46-6** in `docs-v2/governance/ROADMAP.md`: it must
run against an **isolated temporary database** and an **isolated application process** — never
against the owner's production database.

**The acceptance criterion is written down in advance:** the restore must produce a database
whose `PRAGMA integrity_check` is `ok`, whose schema version is the expected one, and whose
row counts match the backup's; and the restored database must open under the writer lock
without a migration.

---

## 6. The open questions this runbook does not close

| the question | where |
|---|---|
| the writer lock resolves the path with `normalize()`, not `realPath()`, so two symlinked aliases of the same file yield two locks and two writers | **TASK-W46-4** — the owner's decision, it touches the database-ownership boundary |
| the writer lock is meaningless on a network filesystem; the supported topology is a local disk | the owner's decision D4: *"the supported local topology"* — the runbook assumes a local disk |
| ~~how long the backups are kept, where they live, and who may restore them~~ | **CLOSED** — Q-W46-13, the owner's decision OD-029 of 2026-11-05. §8 is the answer and `scripts/backup-retention.py` implements it. A restore still happens only on the owner's explicit command. |

---

## 7. The commands, in one block

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
# OUTSIDE the repository — the retention script refuses a root inside it.
BACKUP_DIR="${MATRIX_BACKUP_ROOT:-$HOME/backups/matrix}"; mkdir -p "$BACKUP_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

# the backup
sqlite3 data/knowledge/workbench.sqlite "VACUUM INTO '$BACKUP_DIR/workbench-$STAMP.sqlite'"
sqlite3 data/mind/mind.sqlite        "VACUUM INTO '$BACKUP_DIR/mind-$STAMP.sqlite'"

# the verification
sqlite3 "$BACKUP_DIR/workbench-$STAMP.sqlite" "PRAGMA integrity_check;"
sqlite3 "$BACKUP_DIR/workbench-$STAMP.sqlite" "SELECT version FROM schema_meta;"

# the restore (the application must be stopped)
mv data/knowledge/workbench.sqlite "data/knowledge/workbench.sqlite.damaged-$STAMP"
cp "$BACKUP_DIR/workbench-$STAMP.sqlite" data/knowledge/workbench.sqlite
rm -f data/knowledge/workbench.sqlite-wal data/knowledge/workbench.sqlite-shm
sqlite3 data/knowledge/workbench.sqlite "PRAGMA integrity_check;"
```

---

## 8. The retention — how many backups are kept, and who may restore them

**The owner's decision OD-029 (2026-11-05).** The backups live outside the repository, the
daily backups are kept for seven days, one weekly point per week for four weeks and one
monthly point per month for twelve months, and **a restore happens only on the owner's
explicit command**. Nothing in this repository restores anything by itself.

**Where a tier point comes from.** A tier point is not a file the operator has to name: it
is derived from the backup's own timestamp, so the policy works on whatever this runbook
produced.

| the tier | what it keeps | how the point is chosen |
|---|---|---|
| daily | every backup of the last **7** days | the window is the opened interval (now − 7 days, now] |
| weekly | one point per week, for the last **4** weeks | the **earliest** backup of each ISO week |
| monthly | one point per month, for the last **12** months | the **earliest** backup of each calendar month |

One point per week and one per month, always, whatever days the operator happens to back up
on — and the earliest, so that the point stands for the beginning of the period it covers.

**The tool.** `scripts/backup-retention.py`.

```bash
# what would be pruned, and why — nothing is deleted
python3 scripts/backup-retention.py --root "$MATRIX_BACKUP_ROOT" --dry-run

# prune
python3 scripts/backup-retention.py --root "$MATRIX_BACKUP_ROOT"

# prove every rule, on a synthetic tree
python3 scripts/backup-retention.py --self-test
```

**What it refuses to do.**

- it refuses a backup root that resolves **inside the repository**, or that overlaps a live
  database's path;
- it never touches a live database;
- it never deletes a file it cannot prove is a valid backup — an empty, truncated or
  corrupt file is reported and **left alone**, because a failed backup is evidence;
- it never deletes the latest valid copy, and it never prunes down to no valid copy;
- it never restores anything.

**What it writes.** One JSON line per decision in `retention-audit.log` inside the backup
root: the file, its tier, its stamp, the decision and the reason. A pruning that cannot be
explained afterwards must not happen, so the audit is written **before** anything is
deleted.

