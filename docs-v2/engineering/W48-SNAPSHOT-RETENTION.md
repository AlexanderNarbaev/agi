# W48-07 — the snapshot size bound and the snapshot retention

**Status:** ACTIVE
**Date:** 2026-11-06
**The owner decision:** OD-039 · **The wave:** W48-07 · **The question:** Q-W47-01

---

## 1. What this document is

Two bounds on one table, and the measurements that justify them.

The table is `workspace_snapshot`: the recoverable copy of what a destructive workspace
replacement destroyed. Every replacement adds a row, and until this wave nothing ever took
one away. The payload of a row had no bound either: the store would serialise whatever the
workspace held and write it, however large it grew.

The owner's decision, OD-039:

> The snapshot's payload gets a SIZE BOUND, and the snapshot rows inside the database get the
> same three-tier calendar that the backups have.

This document says what was measured, what the bound is and why, what the tiers are and how
they behave, what the refusals say, and what is **not** covered.

**Every number below is the output of a command that is quoted next to it.** A number without
a command is not evidence.

---

## 2. What was measured

### 2.1 The database, read without being touched

The owner's live database is `data/knowledge/workbench.sqlite`. It was **read only**, through
SQLite's own read-only mode, which cannot migrate the file, cannot write to it and cannot
create a journal beside it:

```bash
$ python3 -c "import sqlite3; c = sqlite3.connect('file:data/knowledge/workbench.sqlite?mode=ro', uri=True); \
    print('schema:', c.execute(\"select value from schema_meta where key='version'\").fetchone()[0]); \
    [print('workspace:', r) for r in c.execute('select id, name, created_at from workspace')]; \
    print('snapshot rows:', c.execute('select count(*) from workspace_snapshot').fetchone()[0])"
schema: 19
workspace: ('photosynthesis', 'Photosynthesis, respiration and plant gas exchange', '2026-11-02T15:08:00.280327044Z')
snapshot rows: 0
```

**The table holds no row at all.** It was added by the schema-version-19 migration, in W45.2,
and the count is zero. The size of a snapshot therefore cannot be read from the live file — it
has to be **produced**, on a copy, with the store's own code.

### 2.2 The payload of the owner's one workspace

The copy is the live file plus its write-ahead log, and the snapshot is taken by the store
itself — not by a re-implementation of the serialiser:

```bash
$ mkdir -p /tmp/w48-snap/live
$ cp data/knowledge/workbench.sqlite       /tmp/w48-snap/live/workbench.sqlite
$ cp data/knowledge/workbench.sqlite-wal   /tmp/w48-snap/live/workbench.sqlite-wal
$ cp data/knowledge/workbench.sqlite-shm   /tmp/w48-snap/live/workbench.sqlite-shm
$ export JAVA_HOME=/home/alexandr-narbaev/jdk25
$ CP="matrix-core/build/classes/java/main:$(ls matrix-core/build/quarkus-app/lib/main/*.jar | tr '\n' ':')"
$ $JAVA_HOME/bin/jshell --class-path "$CP" -s - <<'EOF'
import io.matrix.knowledge.*;
import java.nio.file.*;
var store = new KnowledgeStore(Path.of("/tmp/w48-snap/live/workbench.sqlite"));
var taken = store.takeWorkspaceSnapshot("photosynthesis", WorkspaceSnapshot.Kind.MANUAL, "2026-11-06T00:00:00Z");
System.out.println("the snapshot: " + taken.id() + " coverage=" + taken.coverage() + " concepts=" + taken.conceptCount() + " claims=" + taken.claimCount() + " evidence=" + taken.evidenceCount() + " representations=" + taken.representationCount() + " sources=" + taken.sourceCount() + " prerequisites=" + taken.prerequisiteCount() + " revisions=" + taken.revisionCount());
System.out.println("the untyped counts: " + taken.untypedCounts());
EOF
the snapshot: snap-photosynthesis-1 coverage=FULL concepts=13 claims=24 evidence=25 representations=19 sources=5 prerequisites=5 revisions=1
the untyped counts: {observation_occurrence=0, observation_adjudication=0, observation_disposition=0, learning_transition=0, learning_error=0, assessment_outcome=0, assessment_item=64, learning_state=0, dialogue_turn=35, asked_question=5, hebbian_binding=0, epistemic_case=0}
```

The size of that payload is measured by the retention tool of this wave, in its
`--report-sizes` mode, which reads the database **read-only**:

```bash
$ python3 scripts/snapshot-retention.py --database /tmp/w48-snap/live/workbench.sqlite --report-sizes
the database: /tmp/w48-snap/live/workbench.sqlite
the snapshots it holds: 1
the workspaces: photosynthesis
  the payload size, in bytes:
    min:    103075
    median: 103075.0
    p95:    103075
    p99:    103075
    max:    103075
  the parts, summed over every snapshot, in bytes:
    the typed knowledge content: 57789
    the observation rows:       171
    the learning rows:          45066
  every snapshot:
    snap-photosynthesis-1  workspace=photosynthesis  bytes=103075  knowledge=57789  observation=171  learning=45066
```

| the part of the payload | the bytes | the rows |
|---|---|---|
| the typed knowledge content | 57,789 | 13 concepts, 5 sources, 24 claims, 25 evidence links, 19 representations, 5 prerequisites, 1 revision, 0 derivations, 0 contradictions, 0 goals |
| the observation rows (`epistemic_case`, `observation_occurrence`, `observation_disposition`, `observation_adjudication`) | 171 | **0** — the four tables are captured empty, and the empty capture is what makes the coverage `FULL` |
| the learning rows (the eight tables of the learning family) | 45,066 | 104 — 64 assessment items, 35 dialogue turns, 5 asked questions, and 0 of the other five |
| **the payload, in UTF-8 bytes** | **103,075** | 103,019 characters; the difference is 56 — the Cyrillic characters of the content, each of which takes two bytes instead of one |

**The coverage is `FULL`** — the payload carries the knowledge content, the observation family
and the learning family. That is the shape of every snapshot this build writes, and it is what
makes the snapshot a *complete* recoverable copy (`matrix-core/src/main/java/io/matrix/knowledge/SnapshotCoverage.java`).

### 2.3 The distribution, over a fixture of growing workspaces

One workspace is one sample, and one sample is not a distribution. The distribution was
therefore measured over a **fixture**: eight workspaces whose content grows by a factor of
two each time, built with the store's own API (the same `Concept`, `Source`, `Claim`,
`Evidence`, `Representation` and `AssessmentItem` records the store writes) and snapshotted by
the store itself. The fixture is a *fixture*: it is not the owner's data, and it is labelled
as such everywhere below.

```bash
$ python3 scripts/snapshot-retention.py --database /tmp/w48-snap/fixture.sqlite --report-sizes
the database: /tmp/w48-snap/fixture.sqlite
the snapshots it holds: 8
the workspaces: w1, w128, w16, w2, w32, w4, w64, w8
  the payload size, in bytes:
    min:    3312
    median: 28681.0
    p95:    301136
    p99:    301136
    max:    301136
  the parts, summed over every snapshot, in bytes:
    the typed knowledge content: 528138
    the observation rows:       1368
    the learning rows:          72492
  every snapshot:
    snap-w1-1  workspace=w1  bytes=3312  knowledge=2284  observation=171  learning=808
    snap-w128-1  workspace=w128  bytes=301136  knowledge=265874  observation=171  learning=35042
    snap-w16-1  workspace=w16  bytes=38034  knowledge=33036  observation=171  learning=4778
    snap-w2-1  workspace=w2  bytes=5600  knowledge=4312  observation=171  learning=1068
    snap-w32-1  workspace=w32  bytes=75218  knowledge=65964  observation=171  learning=9034
    snap-w4-1  workspace=w4  bytes=10176  knowledge=8368  observation=171  learning=1588
    snap-w64-1  workspace=w64  bytes=149586  knowledge=131820  observation=171  learning=17546
    snap-w8-1  workspace=w8  bytes=19328  knowledge=16480  observation=171  learning=2628
```

The nine samples — the owner's workspace and the eight fixture workspaces — together:

```bash
$ python3 - <<'PY'
from statistics import median
sizes = [103075, 3312, 5600, 10176, 19328, 38034, 75218, 149586, 301136]
sizes.sort()
rank = lambda p: sizes[max(1, min(len(sizes), -(-p * len(sizes) // 100))) - 1]
print("n:", len(sizes), "min:", min(sizes), "median:", median(sizes),
      "p95:", rank(95), "p99:", rank(99), "max:", max(sizes))
PY
n: 9 min: 3312 median: 38034 p95: 301136 p99: 301136 max: 301136
```

| | the bytes |
|---|---|
| the minimum | 3,312 |
| the median | 38,034 |
| the p95 | 301,136 |
| the p99 | 301,136 |
| the maximum | 301,136 |
| the owner's own workspace | 103,075 |

**The distribution is nearly linear in the content.** The knowledge part grows with the number
of concepts, claims, evidence links and representations; the observation part is a constant
171 bytes for an empty capture; the learning part grows with the number of assessment items
and dialogue turns.

---

## 3. The bound

### 3.1 What it is

| | |
|---|---|
| the default | **8,388,608 bytes** (8 MiB) |
| the property | `matrix.knowledge.snapshot.max-payload-bytes` |
| the constant | `KnowledgeStore.DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES` |
| the measure | the UTF-8 byte length of the serialised payload |

### 3.2 Why 8 MiB

| the reason | the number |
|---|---|
| it is **81 times** the payload the owner's own workspace produces | 8,388,608 ÷ 103,075 = 81.4 |
| it is **27 times** the largest payload measured anywhere | 8,388,608 ÷ 301,136 = 27.9 |
| the owner's workspace is 0.098 MiB of payload; the bound is 8 MiB | 103,075 bytes = 0.0983 MiB |
| a bound that a normal workspace can reach is a bound that only fires on a runaway | the largest measured payload is 0.2872 MiB, and the bound is 27.9 times that |

The bound is a **refusal**, and never a trim. The reasoning is in the owner's own terms: a
snapshot is the only recoverable copy of what a replacement destroyed. A payload that is cut
short is not a smaller snapshot; it is a **corrupt** one, and a restore of it brings back a
workspace that has silently lost rows. So the payload is measured, and a payload over the
bound is refused with a message that says what exceeded which bound — and the transaction that
called it rolls back, so the database is exactly as it was.

### 3.3 How it is configured

The bound is read **once**, when the store is opened, from the system property
`matrix.knowledge.snapshot.max-payload-bytes`:

```bash
$ java -Dmatrix.knowledge.snapshot.max-payload-bytes=16777216 …   # 16 MiB
```

A property that is not a positive number is **refused at start-up** rather than ignored: a
bound that is silently ignored is a bound that does not exist, and a limit that cannot be read
is a limit that cannot be trusted. A caller that needs another bound may pass it to the
constructor `new KnowledgeStore(databaseFile, maxSnapshotPayloadBytes)` — that is how the
tests of §5 prove the behaviour without changing the state of the whole process.

**The application property route is not used.** The store is constructed by
`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java`, and wiring a Quarkus
`@ConfigProperty` into it would change a file outside this wave's write scope. The system
property is the configuration this wave delivers; the application property is a follow-up.

---

## 4. The three-tier retention

The tool is `scripts/snapshot-retention.py`. It applies the tiers **per workspace**, in UTC,
and the tiers are the ones the backups already have (OD-035).

| the tier | the rule | the window |
|---|---|---|
| daily | every snapshot of the last seven days | the OPENED interval `(now − 7 days, now]` |
| weekly | the **EARLIEST** snapshot of its ISO week | the last four ISO weeks |
| monthly | the **EARLIEST** snapshot of its calendar month | the last twelve calendar months |

A tier point is not a row the operator has to name: it is derived from the snapshot's own
`created_at`. One point per week and one per month, always, whatever days the owner happens
to replace a workspace on — and the earliest, so that the point is the beginning of the period
it stands for. The rules are the same ones `scripts/backup-retention.py` applies to the backup
files, and the tool is written in the same shape, so that the two calendars cannot drift.

### 4.1 The rules, in the order they are applied

1. a snapshot of the last seven days is kept;
2. the earliest snapshot of each ISO week is kept, for the last four weeks;
3. the earliest snapshot of each calendar month is kept, for the last twelve months;
4. every other snapshot **expires**;
5. the latest **valid** snapshot of a workspace is kept whatever the tiers say;
6. a **corrupt** snapshot is never deleted, and is never counted as a recoverable copy;
7. a snapshot whose timestamp cannot be read is never deleted either.

### 4.2 The four states of a snapshot

| the state | what it is | what the pass does with it |
|---|---|---|
| `complete` | the payload carries the knowledge content, the observation family **and** the learning family — the whole of `SnapshotCoverage.FULL` | it is a recoverable copy; the tiers decide |
| `legacy` | the payload is readable but carries less: a version-1 payload, or a version-2 payload without the learning family | it is a recoverable copy, but not a **complete** one; the tiers decide |
| `corrupt` | the payload is not a snapshot payload at all | **never deleted**, never counted as recoverable |
| `undated` | the `created_at` cannot be read, so no tier can be decided | **never deleted** |

### 4.3 The dry run is the default

```bash
$ python3 scripts/snapshot-retention.py --database data/knowledge/workbench.sqlite
the database: data/knowledge/workbench.sqlite
the mode: dry run — nothing was deleted
  retained: 0
  expired:  0
  corrupt:  0
  undated:  0
```

The dry run reads the database **read-only** (`file:…?mode=ro`), so it cannot migrate the
file, cannot write to it and cannot create a journal beside it. The deletion happens only
when the owner asks for it:

```bash
$ python3 scripts/snapshot-retention.py --database data/knowledge/workbench.sqlite --apply
```

`--apply` also refuses to run when another process holds the database's writer lock: the
contract is one writer process per database file (OD-004), and a pruning that raced the
application would write into a database the application is using.

### 4.4 The audit

Every decision is written to the audit log, one JSON object per line, **before** anything is
deleted — a decision that is not recorded must not be acted upon. The default path is
`logs/snapshot-retention-audit.log`; `--audit-log` names another one.

```json
{"at": "2026-11-06T12:00:00+00:00", "dryRun": true, "database": "…", "snapshot": "snap-p-9", "workspace": "photosynthesis", "createdAt": "2026-10-28T12:00:00Z", "kind": "LOAD", "state": "complete", "payloadBytes": 103075, "tier": "weekly", "decision": "retained", "reason": "it is the first snapshot of its week and the week is one of the last 4"}
```

The line names the snapshot, the workspace, the tier, the state, the size of the payload in
bytes, the decision and the reason for it. A pruning that cannot be explained afterwards is a
pruning that must not happen.

### 4.5 What the pass does on a realistic database

Eleven snapshots of one workspace plus one corrupt one and one stale one, on a copy of the
owner's database:

```bash
$ python3 scripts/snapshot-retention.py --database /tmp/w48-snap/apply/knowledge.sqlite --now 2026-11-06T12:00:00Z
the database: /tmp/w48-snap/apply/knowledge.sqlite
the mode: dry run — nothing was deleted
  retained: 10
    snap-p-9  [weekly]  it is the first snapshot of its week and the week is one of the last 4
    snap-p-6  [daily]  it is one of the last 7 days
    snap-p-5  [daily]  it is one of the last 7 days
    snap-p-4  [daily]  it is one of the last 7 days
    snap-p-3  [daily]  it is one of the last 7 days
    snap-p-2  [daily]  it is one of the last 7 days
    snap-p-corrupt  [corrupt]  it is corrupt: the payload is not JSON. A corrupt snapshot is never deleted
    snap-p-1  [daily]  it is one of the last 7 days
    snap-photosynthesis-1  [daily]  it is one of the last 7 days
    snap-p-0  [daily]  it is one of the last 7 days
  expired:  3
    snap-p-old  it is outside every retention window
    snap-p-8  it is outside every retention window
    snap-p-7  it is outside every retention window
```

`--apply` then deletes exactly those three and keeps the other ten, including the corrupt one.

---

## 5. The refusal semantics

### 5.1 The payload is over the bound

The store refuses to write the snapshot. The refusal is **typed** and its identifier is
stable:

```java
KnowledgeStore.SnapshotPayloadTooLargeException   // IDENTIFIER = "SNAPSHOT_PAYLOAD_TOO_LARGE"
```

The message says what exceeded which bound, and it says that nothing was written:

> `SNAPSHOT_PAYLOAD_TOO_LARGE`: the snapshot of the workspace 'demo' is 61234 bytes and the
> bound is 512 bytes, so the snapshot exceeds the bound by 60722 bytes. The snapshot was NOT
> written and the database was NOT changed. The payload is never trimmed: raise the bound with
> `-Dmatrix.knowledge.snapshot.max-payload-bytes=<bytes>`, or reduce what the workspace holds.

The refusal happens **before the row is written**, inside the transaction that called it, so
the transaction rolls back. Three properties follow, and all three are proved by the tests:

| the promise | the test |
|---|---|
| the database is unchanged by a refused write | `a refused snapshot leaves the database exactly as it was` — the fingerprint of every table of every row is compared before and after |
| a refused **replacement** destroys nothing | `a refused replacement destroys nothing` — the workspace still holds its concepts, its claims and its evidence |
| a refused **restore** leaves the database unchanged | `a refused restore leaves the database exactly as it was` |
| the payload is never shortened | the refusal carries the FULL size (`payloadBytes()` > the bound), and no row is written at all |

The bound is checked in `writeSnapshot`, which is the single place where a snapshot row is
written (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java`). Every path that
writes a snapshot goes through it: the replacement, the manual snapshot and the restore.

### 5.2 The pass would leave a workspace with no recoverable snapshot

The retention pass **refuses for that workspace** and deletes nothing for it. Two cases:

| the case | the refusal |
|---|---|
| the pass would leave the workspace with no recoverable snapshot at all | "the pass would leave the workspace with no recoverable snapshot, and a policy that deletes the only recovery copy is not a policy" |
| the workspace holds complete snapshots and the pass would leave it with no complete one | "the pass would leave the workspace with no COMPLETE recoverable snapshot, and no replacement may be committed without one" |

---

## 6. The invariant

> **No replacement is committed without a complete recoverable snapshot.**

Two halves, and they are enforced in two places.

**The store.** The snapshot is written in the SAME transaction as the replacement, before the
erasure, so a transaction that rolls back takes the snapshot with it. On top of that,
W48-07 adds the explicit guard `KnowledgeStore.requireCompleteSnapshot`: a replacement whose
snapshot does not carry the whole of `SnapshotCoverage.FULL` is refused with
`KnowledgeStore.IncompleteSnapshotException` (`IDENTIFIER = "SNAPSHOT_NOT_COMPLETE"`), before
the erasure, so the workspace is exactly as it was. The guard is what turns the capture list
into a promise a reader can rely on — and it is what makes the invariant checkable rather
than merely intended.

**The tool.** A pass that would leave a workspace with no recoverable snapshot is refused for
that workspace, and nothing is deleted for it (§5.2).

The tests that prove it:

```bash
$ ./gradlew :matrix-core:test --tests "io.matrix.knowledge.SnapshotRetentionBoundTest" --offline
$ ./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --offline
```

`a replacement leaves a complete recoverable snapshot` and `a snapshot that is not complete is
refused before the erasure` are the store's half; `a pass that would leave no complete
snapshot is refused` and `a workspace with no recoverable snapshot is refused` are the tool's
half.

---

## 7. The gate

The gate is `scripts/quality-gate.sh`, and the check is **SNAPSHOT-RETENTION-1**. It proves
three things, and each of them can fail:

1. **the policy proves itself** — the tool's own `--self-test`: a synthetic database of valid,
   expired, corrupt, undated and legacy snapshots, and every rule of the policy is checked
   against it;
2. **the bound is configured** — the store reads it from the property
   `matrix.knowledge.snapshot.max-payload-bytes` and names its default
   `DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES`; a bound that is hard-coded fails the check;
3. **the dry run is the default** — the gate runs the tool against the owner's database
   **without** `--apply`, and compares the sha256 of the database before and after. A dry run
   that changed a byte of the owner's database fails the check.

```bash
$ bash scripts/quality-gate.sh
…
RETENTION-1 the retention policy holds
SNAPSHOT-RETENTION-1 the snapshot retention policy holds, the bound is configured, and the dry run changes nothing
----------------------------------------------
literal candidates: 271 (shell 78, java 193)
…
PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).
```

The literal-candidate count is **unchanged** at 271: the new shell code and the new Java code
contribute no numeric literal outside a named constant. The gate blocks when the count RISES,
and the accepted baseline is **not** raised to make a new script fit — a script that adds a
literal has to extract it, or the wave has to say so explicitly and own it.

The tool's own self-test:

```bash
$ python3 scripts/snapshot-retention.py --self-test
  ok    a dry run changes nothing  [18 rows after, 18 before]
  …
  the checks: 40, the failures: 0
```

### 7.1 The check can fail

A check that cannot fail is not a check, so each of the four ways the check goes red was
exercised against a deliberately broken copy of the tree, in a temporary directory:

| the broken thing | what the gate said |
|---|---|
| the tool is absent | `SNAPSHOT-RETENTION-1 the snapshot retention tool is absent` |
| the tool's self-test fails | `SNAPSHOT-RETENTION-1 the snapshot retention policy does not hold` |
| the bound is not configurable in the store | `SNAPSHOT-RETENTION-1 the bound on the snapshot payload is not configurable` |
| the dry run changes the database | `SNAPSHOT-RETENTION-1 the dry run changed the owner's database` |
| nothing broken | `SNAPSHOT-RETENTION-1 the snapshot retention policy holds, the bound is configured, and the dry run changes nothing` |

Each of the four failures adds one violation, and the gate exits non-zero.

---

## 8. What is NOT covered

- **The owner's database holds no snapshot today**, so the retention pass has nothing to prune
  there yet. The measurement in §2.2 was therefore taken on a copy, with the store's own code,
  and it is labelled as such.
- **The application property route is not wired** (§3.3): the store is constructed by
  `KnowledgeWorkbench`, which is outside this wave's write scope.
- **The bound applies to the snapshot payload only.** It does not bound the database file, the
  learning tables outside a snapshot, or the workbench's other artefacts.
- **The pass does not restore, and it does not take a snapshot.** Both are the store's work.
- **The pass does not decide what a snapshot is worth.** It counts the rows it holds and
  whether its payload can be parsed; whether a payload is the right content for a restore is
  the store's own compatibility check (`matrix-core/src/main/java/io/matrix/knowledge/IncompatibleSnapshotException.java`).
- **The tiers are applied per workspace**, and the tool does not merge the calendars of
  different workspaces: a workspace with many snapshots keeps up to 23 of them (7 daily, 4
  weekly, 12 monthly), and a workspace with one keeps that one.
- **The pruning is not scheduled.** The tool is run by hand, or by the gate in its dry-run
  mode; nothing puts it on a timer, and the owner's daily backups are a separate policy
  (`scripts/backup-retention.py`, `docs-v2/operations/RECOVERY-RUNBOOK.md`).
- **The restore drill is a different wave.** W48-08 performs it against a copy; this wave
  neither performs nor claims it.

---

## 9. The files this wave changed

| the file | what changed |
|---|---|
| `scripts/snapshot-retention.py` | **new** — the three-tier retention, the dry run, the audit, the refusal of the invariant, the size report, and the self-test |
| `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md` | **new** — this document |
| `scripts/quality-gate.sh` | the check **SNAPSHOT-RETENTION-1** and the header that lists it |
| `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java` | the bound, the two typed refusals, the guard of the invariant, and the constructor that takes the bound |
| `matrix-core/src/test/java/io/matrix/knowledge/SnapshotRetentionBoundTest.java` | **new** — the tests of the bound, of the refusals and of the invariant |

No other file was touched, and **nothing was committed**.

### 9.1 The store, in detail

The change to `KnowledgeStore.java` is small and is four things:

1. `SNAPSHOT_MAX_PAYLOAD_BYTES_PROPERTY` and `DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES` — the
   property and the default;
2. `configuredSnapshotPayloadBound()` and the field `maxSnapshotPayloadBytes` — the bound is
   read once, when the store is opened; the constructor
   `KnowledgeStore(Path, long)` takes it explicitly;
3. the check in `writeSnapshot` — the payload's UTF-8 byte length is compared with the bound
   **before** the row is written, and `SnapshotPayloadTooLargeException` is thrown when it is
   over;
4. `requireCompleteSnapshot` and `IncompleteSnapshotException` — the guard of the invariant,
   called in `replaceWorkspace` between the snapshot and the erasure.

The two exception classes are nested inside `KnowledgeStore`, so that the wave's write scope —
the store file and its tests — is respected without inventing a public type in a file that
this wave may not touch.
