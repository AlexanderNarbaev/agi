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
  "VACUUM INTO '$(pwd)/backup/workbench-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"

# chain B's mind store
sqlite3 data/mind/mind.sqlite \
  "VACUUM INTO '$(pwd)/backup/mind-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"
```

**The backup directory is outside the repository's `data/`** — a backup inside the tree it
protects is not a backup.

### The procedure — the SQLite CLI's `.backup`

If `VACUUM INTO` is unavailable (SQLite < 3.27), use the CLI's backup command, which takes
the same consistent snapshot:

```bash
sqlite3 data/knowledge/workbench.sqlite ".backup '$(pwd)/backup/workbench-$(date -u +%Y%m%dT%H%M%SZ).sqlite'"
```

### The procedure — the live application

**The safest backup is one taken while the writer is not running.** The `WriterLock` (the
owner's decision D4) is a lifetime-held OS lock: **if the application holds it, another
process cannot open the database as a writer.** So:

```bash
# stop the application, then
sqlite3 data/knowledge/workbench.sqlite "VACUUM INTO '$(pwd)/backup/workbench-<stamp>.sqlite'"
# and start it again
```

### Verifying a backup — **do not skip this**

A backup that was not verified is a hope.

```bash
# 1. it opens
sqlite3 backup/workbench-<stamp>.sqlite "PRAGMA integrity_check;"     # → "ok"

# 2. it has the tables
sqlite3 backup/workbench-<stamp>.sqlite \
  "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"

# 3. the schema version is the expected one
sqlite3 backup/workbench-<stamp>.sqlite "SELECT version FROM schema_meta;"

# 4. the row counts are plausible
sqlite3 backup/workbench-<stamp>.sqlite "SELECT count(*) FROM epistemic_case;"
sqlite3 backup/workbench-<stamp>.sqlite "SELECT count(*) FROM cognitive_episode;"
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
cp backup/workbench-<stamp>.sqlite data/knowledge/workbench.sqlite

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
| how long the backups are kept, where they live, and who may restore them | **Q-W46-13** — the retention policy, the owner's decision |

---

## 7. The commands, in one block

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
BACKUP_DIR="$(pwd)/backup"; mkdir -p "$BACKUP_DIR"
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
