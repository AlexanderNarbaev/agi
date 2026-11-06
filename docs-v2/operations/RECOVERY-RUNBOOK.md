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

## 5. The restore test — owed, and now paid (§9)

**A backup procedure that has never been restored from is a procedure, not a capability.**

**What exists:** the workspace snapshot is **tested** — `WorkspaceSnapshotTest` (12 tests),
`WorkspaceSnapshotMigrationTest` (4) and `WorkspaceLoadEndpointTest` (7), all executed in
W45.2 and W46.1.

**What did not exist:** an executed end-to-end restore of the **whole database** from a
`VACUUM INTO` backup. That test is **TASK-W46-6** in `docs-v2/governance/ROADMAP.md`: it must
run against an **isolated temporary database** and an **isolated application process** — never
against the owner's production database.

**What it is now:** §9 records the drill of 2026-11-06, which performed that restore against
an isolated copy of the live database, measured every stage, and left the original's digest
unchanged. The task it discharges is **TASK-W46-13** of the same roadmap.

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

---

## 9. The restore drill — performed, against a copy (W48-08, 2026-11-06)

**The honest statement first:** *the restore has been performed — against a copy.* The
procedure of §2 and §3 was executed end to end on 2026-11-06 against an isolated copy of
`data/knowledge/workbench.sqlite`; every stage printed its measurements; and the original's
digest was the same before and after. What was **not** performed: a restore *over* the
original — a restore still happens only on the owner's explicit command (OD-029, §8) — and a
restore of a **workspace payload**. The live `workspace_snapshot` table holds no rows, so
there was no payload to restore. The two classes that decide whether a payload restore is
safe, `matrix-core/src/main/java/io/matrix/knowledge/SnapshotCoverage.java` and
`matrix-core/src/main/java/io/matrix/knowledge/IncompatibleSnapshotException.java`, guard
that *other* restore and were therefore **not** exercised by this drill; §9.8 says what that
costs.

**The task it discharges.** **TASK-W46-13** of `docs-v2/governance/ROADMAP.md`: "a backup
procedure that has never been restored from is a procedure, not a capability". Its
acceptance criteria — a `VACUUM INTO` backup of an **isolated temporary database**, restored
and verified, with the `integrity_check` `ok`, the schema version the expected one, the row
counts matching, and the restored database opening under the writer lock without a
migration — are met, and the measurements are below.

---

### 9.1 The tool

`scripts/restore-drill.sh` — the whole drill, repeatable, and it works on a copy.

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25

# what it would do; nothing is written
bash scripts/restore-drill.sh --dry-run

# the drill; it exits 0 only when every stage held
bash scripts/restore-drill.sh
```

| the option | what it does |
|---|---|
| `--dry-run` | prints the stages and the paths, writes nothing, exits 0 |
| `--original PATH` | the live database to drill against (default `data/knowledge/workbench.sqlite`) |
| `--workdir PATH` | where the copies live (default: a fresh directory under the temp root) |
| `--keep` | keeps the working directory, with every measurement in it, after the drill |

The drill exits **non-zero** when any stage fails, and it fails loudly rather than quietly:
the comparison of two measurements names every difference it found, so a drill that reports
a verdict is a drill whose verdict can be checked.

### 9.2 The three rules that make the drill safe

1. **The original is opened READ-ONLY and never through MATRIX.** The measurement engine
   opens it through the `mode=ro` URI of the SQLite driver, and a read-only connection
   cannot checkpoint the write-ahead log into the database file — so the drill cannot
   change a byte of the original even by accident. The application is never pointed at it
   either: the store migrates on open, and a migration of the owner's live database during
   a drill is exactly the damage the drill exists to prevent. That is not a hypothesis:
   `docs-v2/engineering/W48-BASELINE.md` §3 records the W47 wave migrating the live
   database from v16 to v19 because a gate opened it.
2. **The digest is proved, not asserted.** The `sha256` of the original, of its
   write-ahead log and of its shared-memory file are taken before the drill and after it,
   and the drill fails when they differ.
3. **The copy is made by SQLite's own mechanism.** The copy is produced with `VACUUM INTO`
   over a read-only connection — the same statement §2 prescribes, run through the JDBC
   driver because the `sqlite3` command-line tool is **not installed** on this machine
   (`command -v sqlite3` → nothing). The drill does not assume what a naive copy loses:
   it measures it (§9.4).

### 9.3 Stage A — the fidelity of the copy

The copy is the `VACUUM INTO` image of the live database; the original is measured
read-only, and the two are compared.

| the measurement | the original (read-only) | the copy (`VACUUM INTO`) | the same? |
|---|---|---|---|
| the file's `sha256` | `bb8c05700be48bf122118a37d4736d3bc143beae6368ca38123397a53ad2619b` | `9a9f36a4322cbc2711f55fcb364d3a60573eb4102ba54adebc58a9e9b5346847` | the copy is a *new* file, so its digest differs — that is what a copy is |
| the size in bytes | 462 848 | 507 904 | the copy holds the write-ahead log's pages |
| `PRAGMA integrity_check` | `ok` | `ok` | ✅ |
| the schema version (`schema_meta.version`) | **19** | **19** | ✅ |
| the number of tables | **34** | **34** | ✅ |
| the total number of rows | **199** | **199** | ✅ |
| the schema digest (the digest of `sqlite_master`) | `02aac93a3d176c53a5e2185e02608c12b0b0e2e13752b3cf8ab14d6b498e0335` | `02aac93a3d176c53a5e2185e02608c12b0b0e2e13752b3cf8ab14d6b498e0335` | ✅ |
| the journal mode | `wal` | `delete` | **by design**: the copy is a self-contained image with no write-ahead log beside it; the store sets the mode back to `wal` when it opens the copy |

**The row counts and the per-table fingerprints.** Every table of the live database, and
the fingerprint of each — the digest of the table's rows in a canonical encoding, every
cell typed and every row sorted, so that two databases with the same counts and the same
fingerprints hold the same content whatever order they return the rows in:

| the table | the rows | the fingerprint (the original and the copy) |
|---|---|---|
| `assessment_item` | 64 | `479e1f0bac6cace8a804a80675e0de7349e4a85324ffd3981316224efd4fd540` |
| `dialogue_turn` | 35 | `446dab63b262ebe7a88ad129000fb1e1c53b283ff2a654e14de3172812e4e23a` |
| `evidence` | 25 | `27922477c0d86b8327c238d2308f311d83309dafbd7b59977efd46578020f9c6` |
| `claim` | 24 | `e51d8fd5820b51aecfedcb49533e432f055e77436794065a9c49600b5af6e375` |
| `representation` | 19 | `3c5d9c17ce493758f4c98be124fa5ab48062768e72a0aa108a9e55ae92673c5a` |
| `concept` | 13 | `985fe74e8adb3c96077bd776df4ad86170e33939bf76cd97b51cfa3115fba879` |
| `asked_question` | 5 | `90ff822e50f62b65df7256052db26daff48b5440bc5928dcb67688bd12c31c33` |
| `prerequisite` | 5 | `652d29c5c5727efaf20cf21f8da06b477d767b60477c48e2372c4d37071a0334` |
| `source` | 5 | `07222c5859cd5b65aab3b2d316f0a7e083f1ae8e6d36c4e745e41a2c7e0c286d` |
| `cognitive_schema_meta` | 1 | `3e030d0a941fc519d3ba1126d2a6269b5c9d25bc7ab077070feee9a6ed9c69cc` |
| `revision` | 1 | `81275baee4837f36146bf9e29de67026d85a576f575c1b9d0d027e98eaf68b37` |
| `schema_meta` | 1 | `2fc1681284a3081c90c98fd5c3165ed7619e7ef3a029d0ad3e78fd006c7f3a8f` |
| `workspace` | 1 | `2ca3750964f8df0916f80e008f288105adc7aa362846b2983aabd87d60f97693` |
| the other twenty-one tables | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (the digest of no rows) |

**The comparison.** `{"identical": true, "differences": []}` — the two measurements agree
on the integrity check, the schema version, the table list, the row count of every table,
the fingerprint of every table and the schema digest.

### 9.4 The hazard, measured

The drill also copies the main file the way a `cp` would, and measures that copy rather
than assuming what it loses.

| the measurement | the naive copy (`cp` of the main file) | the `VACUUM INTO` copy |
|---|---|---|
| `PRAGMA integrity_check` | `ok` | `ok` |
| **the schema version** | **16** | **19** |
| **the number of tables** | **30** | **34** |
| the tables it does not have | `observation_disposition`, `observation_occurrence`, `observation_adjudication`, `workspace_snapshot` | — |
| the total number of rows | 199 | 199 |
| the schema digest | `de5084519eb1f769b18d00b3d91caa992e28d99ca32d4492bcc0f0c770c02228` | `02aac93a3d176c53a5e2185e02608c12b0b0e2e13752b3cf8ab14d6b498e0335` |

**This is the whole point of the backup API, measured on the owner's own database.** The
live database's write-ahead log holds the **schema** that the migrations v17, v18 and v19
added — the W47 wave's chain ran them against the live database on 2026-11-05
(`docs-v2/engineering/W48-BASELINE.md` §3) — and the database file itself is still the
checkpoint of 2026-11-02, at v16. A `cp` of the main file therefore yields a database that
*opens* and *looks* intact — the integrity check says `ok` and the row counts are the same
— and that is **three schema versions behind**, with the tables the migrations added
missing. A restore from that copy would silently roll the database back to v16 and lose the
schema history; the store would then migrate it forward again, and the backfilled
observation history would be a backfill, not a record. The `VACUUM INTO` copy is the v19
image.

**The honest limit of that measurement:** the write-ahead log of *this* database holds
schema and not rows, so the row counts of the two copies are equal here. The mechanism is
the same one `BackupInstructionTest` records on a store that is written to while it is
open — the rows that were written last are in the log and not in the file — and the
drill's own test asserts it on a database the test writes to (§9.9).

### 9.5 Stage B — the copy under the current code

The copy is opened by the application itself (`KnowledgeWorkbench`, the facade the HTTP
layer uses), and the store's own migration chain, integrity check, writer lock and
shutdown are what are measured.

| the measurement | the copy at v19 | the pre-v17 image of the copy |
|---|---|---|
| the schema version before the open | 19 | **16** |
| the schema version after the open | 19 | **19** |
| the migration that ran | none — the copy is already at the current version | **v16 → v17 → v18 → v19** |
| `PRAGMA integrity_check` after the migration | `ok` | `ok` |
| the dangling references | **0** | **0** |
| the application started | yes | yes |
| the application shut down cleanly | yes — and the writer's lock file was gone afterwards | yes — and the writer's lock file was gone afterwards |
| a second writer while the first holds the lock | **refused** (exit code 3) | **refused** (exit code 3) |
| the result against the copy's own record | identical | **identical** — no differences |

**The migration path, exercised on the drill's own data.** The copy is at v19, so the
migration chain has nothing to do with it. The **pre-v17 image** of the copy is therefore
*derived* — the tables v17, v18 and v19 added (`observation_disposition`,
`observation_occurrence`, `observation_adjudication`, `workspace_snapshot`) are dropped and
the version is set back to 16 — and the current code is asked to migrate it forward. The
migrated image then holds **exactly** what the copy holds: the same 34 tables, the same 199
rows, the same fingerprint for every table, the same schema digest
(`02aac93a3d176c53a5e2185e02608c12b0b0e2e13752b3cf8ab14d6b498e0335`), and zero dangling
references. The derived image is a **fixture** and the drill says so; the snapshot itself is
never downgraded.

**The dangling references.** The schema declares no foreign keys, so nothing in the
database would ever report a row that points at a row that is not there. The drill asks the
question itself, over thirty-three columns that name another row's identifier — from
`evidence.claim_id` to `learning_transition.outcome_id` — and found **none**.

**The writer lock.** The contract is one writer *process* per database file; a second
store of the same process joins the lock, so the refusal can only be observed from a second
process. The drill forks one, and the refusal is what comes back:

```
the cognitive database /…/stage-b.sqlite is held by another writer: process 268334 started
at 2026-11-06T06:11:17.630Z (instance 95b2518a-…); the operating system reports that
another writer holds the lock file; … ONE WRITER PROCESS PER COGNITIVE DATABASE FILE is the
contract of this store, and a second writer would make the recovery of an interrupted
request unsafe
```

### 9.6 Stage C — the restore

The restore was performed **into an isolated target**, exactly as §3 writes it, with the
damaged file kept as evidence.

```bash
# the target is damaged on purpose
cp  "$SNAPSHOT" "$RESTORE_TARGET"
printf 'DAMAGED' | dd of="$RESTORE_TARGET" bs=1 seek=0 conv=notrunc status=none

# the restore of §3, into the isolated target and never over the original
mv  "$RESTORE_TARGET" "$RESTORE_TARGET.damaged-$STAMP"
cp  "$SNAPSHOT" "$RESTORE_TARGET"
rm -f "$RESTORE_TARGET-wal" "$RESTORE_TARGET-shm"
```

| the measurement of the restored database | the result |
|---|---|
| `PRAGMA integrity_check` | `ok` |
| the schema version | **19** — the expected one, and **no migration ran** |
| the row counts, the fingerprints and the schema digest against the copy's own record | **identical** — no differences |
| the dangling references | 0 |
| the application started and shut down cleanly | yes |
| a second writer while the first holds the lock | refused |

**So the answer to the question this runbook exists for is yes: this snapshot can be
restored.** The restore reproduces the copy exactly, and the restored database opens under
the writer lock without a migration — the acceptance criterion of **TASK-W46-13** and of
§5.

### 9.7 The digest of the original, before and after

```
before: bb8c05700be48bf122118a37d4736d3bc143beae6368ca38123397a53ad2619b  data/knowledge/workbench.sqlite
after : bb8c05700be48bf122118a37d4736d3bc143beae6368ca38123397a53ad2619b  data/knowledge/workbench.sqlite
```

The write-ahead log and the shared-memory file were digested too, and none of the three
changed:

| the file | the digest before | the digest after |
|---|---|---|
| `data/knowledge/workbench.sqlite` | `bb8c05700be48bf122118a37d4736d3bc143beae6368ca38123397a53ad2619b` | `bb8c05700be48bf122118a37d4736d3bc143beae6368ca38123397a53ad2619b` |
| `data/knowledge/workbench.sqlite-wal` | `3f9de415b223f60b29bed9af7d0f684a5f13a2c4ff1ec6310fb418fbb4c7035c` | `3f9de415b223f60b29bed9af7d0f684a5f13a2c4ff1ec6310fb418fbb4c7035c` |
| `data/knowledge/workbench.sqlite-shm` | `b5a3dc6086f868d0ca3312abb8193248f4b127fc38ad6fad76c03af2d8fc0f62` | `b5a3dc6086f868d0ca3312abb8193248f4b127fc38ad6fad76c03af2d8fc0f62` |

The drill itself checks this and exits non-zero when it does not hold.

### 9.8 What the drill does NOT prove

- **It does not restore over the original.** The drill restores into an isolated target.
  A restore over the owner's live database is still the owner's explicit command (OD-029).
- **It does not exercise the payload restore.** The live `workspace_snapshot` table holds
  **no rows**, so there was no payload to restore, and the classes that decide whether a
  payload restore is safe — `SnapshotCoverage` and `IncompatibleSnapshotException`, in
  `matrix-core/src/main/java/io/matrix/knowledge/` — were not exercised by this drill. They
  are covered by `SnapshotRestoreCompatibilityTest`, which is a different restore and was
  not re-run here.
- **It does not prove that a backup taken *during* a migration is restorable.** The
  migration writes the version last, so such a backup is a backup of the pre-migration
  schema; §2 says so, and the drill does not change that.
- **It does not prove the restore of the *other* database.** `data/mind/mind.sqlite` is
  chain B's store, it has its own schema, and the drill did not touch it.
- **It does not measure the cost of a restore on the owner's hardware.** The drill
  measured correctness, not time; nothing in the drill's output is a performance claim.
- **It does not replace the full suite.** The owner's one approved full run (OD-041) is
  spent; the drill ran one targeted test class only.

### 9.9 The engine, and the test that holds it

The measurements are made by `matrix-core/src/test/java/io/matrix/knowledge/RestoreDrillProbe.java`
— the current code itself, because the migration, the dangling references, the writer lock
and the clean shutdown can only be measured by the code that implements them. The drill
compiles it, drives it, and reads its verdicts.

The engine is tested where it can be falsified, on databases the test builds itself:
`matrix-core/src/test/java/io/matrix/knowledge/RestoreDrillProbeTest.java`, four tests, run
in isolation and **never against the owner's database**.

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew :matrix-core:test --tests "io.matrix.knowledge.RestoreDrillProbeTest"
```

| the test | what it proves |
|---|---|
| the backup holds the rows the write-ahead log holds, and a plain copy does not | the copy is WAL-safe — on a database that is written to while it is open |
| the current code migrates the pre-v17 image forward and holds what it held | the migration path is real, on data the test wrote |
| the second writer is refused, and the lock file is gone after the shutdown | the writer lock, from a forked process |
| the fingerprint changes when a row changes | the fingerprint is worth comparing |

**The evidence.** The drill's output is the evidence, and the drill writes every
measurement into its working directory — the measurement of the original, the measurement
of the copy, the measurement of the naive copy, the three open reports and the comparison
of the restored database against the copy's own record. `--keep` keeps that directory.
