# W48 — Snapshot restore compatibility

**Wave:** W48 · **Task:** W48-01 · **Priority:** P0
**Commits:** `4084538a` (the contract and the refusal), `33d199e2` (the size bound and the retention calendar)
**Baseline:** `eca23bfd` · **Document written:** 2026-11-06

---

## 1. The defect this closes

A restore of a workspace snapshot is a **destructive** operation: it erases the target
workspace and writes the payload back. The erasure set is larger than the payload of an
old snapshot.

| the wave | the erasure set | what the payload could carry |
|---|---|---|
| before W47 | the knowledge tables | the knowledge tables |
| W47, OD-025 | + the observation family | + the observation family (payload version 2) |
| W47, OD-036 | + the eight learning tables | the learning family — **only in a payload written after OD-036** |

A snapshot written **before OD-036** carries no learning rows. Restoring it into a
workspace that holds learning rows deletes those rows and cannot bring them back — and
nothing says so afterwards, because the restore *succeeds*. That is silent loss of the
learner's history and of the adjudication record.

W47 documented this hazard. **Documentation is not a safety mechanism**, and the
W48 directive asks for an explicit, typed compatibility check.

---

## 2. The payload formats

The payload names its own version: `KnowledgeStore.SNAPSHOT_PAYLOAD_VERSION = 2`.

| the payload version | what it carries | written by | readable by this build |
|---|---|---|---|
| **1** | the typed knowledge content only: concepts, sources, claims, evidence, representations, prerequisites, revisions, derivations, contradictions, goals | W47 before OD-036 | **yes** — with the compatibility check below |
| **2** | the knowledge content + the rows of the observation family + the rows of the learning family | W47 after OD-036, and W48 | **yes** |
| **> 2** | unknown to this build | a future build | **no — refused** |

The reader derives the **coverage** of a payload from the payload's own table list
rather than from its version number, because the version is the *build's* statement and
the table list is the *payload's* statement:

`SnapshotCoverage.of(tables)` (`matrix-core/src/main/java/io/matrix/knowledge/SnapshotCoverage.java`)

| the coverage | the payload carries | a restore would destroy what it cannot bring back |
|---|---|---|
| `KNOWLEDGE_ONLY` | the knowledge tables only | the observation history **and** the learning state |
| `OBSERVATION_HISTORY` | + the observation family | the learning state |
| `FULL` | + the complete learning family | nothing |
| `UNKNOWN` | a build this one cannot describe | everything this build cannot describe |

The families are named explicitly in the same file:

- the **observation family** — `epistemic_case`, `observation_occurrence`,
  `observation_disposition`, `observation_adjudication`;
- the **learning family** — `learning_transition`, `learning_error`,
  `assessment_outcome`, `assessment_item`, `learning_state`, `dialogue_turn`,
  `asked_question`, `hebbian_binding`.

A family counts as *carried* when the payload holds an **entry for every table** of the
family. The entry may be empty: "the table was there and it had no rows" is a fact about
the snapshot; "the table is not in the payload at all" is a fact about the build that
wrote it.

---

## 3. The failure semantics

### 3.1 `INCOMPLETE_LEGACY_SNAPSHOT` — the payload cannot cover what the restore would erase

`KnowledgeStore.IncompatibleSnapshotException`, identifier **`INCOMPLETE_LEGACY_SNAPSHOT`**.

The check runs **before the transaction and before the write lock**:

```java
// matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:1551
SnapshotCoverage coverage = coverageOf(payload.tables());
if (coverage != SnapshotCoverage.FULL) {
    ...
    boolean losesObservations = coverage != SnapshotCoverage.OBSERVATION_HISTORY && observationRows > 0;
    boolean losesLearning = coverage != SnapshotCoverage.FULL && learningRows > 0;
    if (losesObservations || losesLearning) {
        throw new IncompatibleSnapshotException(snapshotId, coverage, missing, detail);
    }
}
```

The refusal is **conditional on the actual loss**:

| the payload | the target holds | the outcome |
|---|---|---|
| legacy (v1) | learning rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| legacy (v1) | observation rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| v2 without the learning family | learning rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| legacy (v1) | **nothing** of the missing families | **ALLOWED**, and the snapshot says what it does not carry |
| complete (v2 with the learning family) | anything | **ALLOWED** |

The database is **byte-identical** before and after a refusal: the check precedes the
transaction, the write lock and every write.

### 3.2 The other refusals — unchanged by W48, and still fail-closed

| the condition | the error | where |
|---|---|---|
| a payload from a **newer build** | refused with the payload's version named | `KnowledgeStore.java:1936` — `decoded.version() > SNAPSHOT_PAYLOAD_VERSION` |
| a **corrupt** payload | refused | the payload decoder |
| a **failure during** the restore | rolled back — no partial replacement | `connection.rollback()` in the catch of the restore transaction |
| a payload over the **size bound** | refused with `SNAPSHOT_PAYLOAD_TOO_LARGE` | W48-07, §6 |

Nothing is ever truncated, skipped or reconstructed to make a restore succeed. A missing
family is never treated as an empty family.

### 3.3 The transaction order

The invariant: **a workspace replacement may not commit unless a complete recoverable
snapshot of the data it destroys exists.**

```
1. read the payload
2. validate the typed knowledge content
3. the compatibility check            <-- refuses HERE, before anything destructive
4. the size bound                      <-- refuses HERE, before the write
5. BEGIN; lock the workspace
6. write the snapshot of the data that is about to be destroyed
7. delete the workspace
8. insert the typed knowledge content
9. insert the untyped rows the payload carries
10. apply the safe retention
11. COMMIT
```

Steps 3 and 4 are outside the transaction on purpose: a refusal there must leave the
database exactly as it was, and a transaction that rolls back is a stronger guarantee
than a transaction that was never started.

---

## 4. The inspection contract

`WorkspaceSnapshot.empty()` used to answer "does this snapshot hold **knowledge
content**?" — the eight learning tables were not among its counts. A snapshot that held
**only** learning rows therefore reported `empty() == true` while the replacement that
took it had destroyed the learner's history. That is an ambiguous and unsafe inspection
contract: a reader would conclude that nothing was at risk, and something was.

It now answers "does this snapshot hold **anything a replacement would destroy**?":

```java
public boolean empty() {
    return conceptCount == 0 && claimCount == 0 && ... && adjudicationCount == 0
        && untypedCounts.values().stream().allMatch(count -> count == 0);
}
```

and the snapshot carries two new facts:

- `coverage()` — the `SnapshotCoverage` of the payload, so a reader can see that a
  snapshot does **not** carry the learning family;
- `untypedCounts()` — the row count of every untyped table the payload carries.

A reader of the older shape is not broken by the newer one: the coverage of a snapshot
that predates W48 is derived as `KNOWLEDGE_ONLY`, the honest answer for a payload that
cannot be known to carry more.

---

## 5. The tests

`matrix-core/src/test/java/io/matrix/knowledge/SnapshotRestoreCompatibilityTest.java` —
**12 tests**, on **real temporary SQLite databases**, not on mocked payloads.

| # | the test | the directive's requirement |
|---|---|---|
| 1 | `aLegacyPayloadIsRefusedWhenTheTargetHoldsLearningRows` | legacy payload + target with learning rows → refused |
| 2 | `aVersionTwoPayloadWithoutTheLearningFamilyIsRefused` | the same, for a v2 payload that lacks the learning family |
| 3 | `aLegacyPayloadRestoresATargetThatHoldsNoneOfTheMissingRows` | legacy payload + target without the affected rows → the documented behaviour |
| 4 | `aLegacyPayloadDoesNotRestoreAnObservationHistoryItDoesNotCarry` | the observation family is protected the same way |
| 5 | `aCompletePayloadRestoresAllEightLearningTables` | a new complete payload restores every learning table |
| 6 | `theRestoredObservationHistoryStaysReferentiallyConsistent` | cases, occurrences, dispositions, adjudications and evidence stay consistent |
| 7 | `aPayloadFromANewerBuildIsRefused` | unknown future payload version → fail closed |
| 8 | `aCorruptPayloadIsRefused` | corrupt payload → fail closed |
| 9 | `aFailureDuringTheRestoreLeavesNoPartialReplacement` | a failure during the transactional restore leaves no partial replacement |
| 10 | `aRestoreDoesNotTouchAnotherWorkspace` | the restore does not cross the workspace boundary |
| 11 | `aSnapshotThatHoldsOnlyLearningRowsIsNotEmpty` | the inspection contract: a destructive snapshot is not reported as empty |
| 12 | `aLegacySnapshotSaysWhatItDoesNotCarry` | the coverage is exposed, not inferred |

Tests 1, 2, 4, 7, 8 and 9 also assert that the **fingerprint of the whole database is
identical before and after the refusal** — the refusal leaves no trace.

---

## 6. The size bound and the retention calendar (W48-07)

The measured payload of the owner's workspace is **103 075 bytes** (knowledge 57 789 /
observation 171 / learning 45 066). The fixture distribution is min 3 312, median
28 681, p95/p99/max 301 136 bytes. The default bound is therefore **8 MiB** — 81× the
measured payload — and it is configurable through the system property
`matrix.knowledge.snapshot.max-payload-bytes`.

A payload over the bound is refused **before the write**, with the typed error
`SNAPSHOT_PAYLOAD_TOO_LARGE` naming the limit and the actual size; the workspace is not
erased, no partial snapshot is produced and the database is unchanged. **Truncation is
never a size policy.**

The three-tier calendar (daily 7 / weekly 4 / monthly 12, per workspace, in UTC) applies
to the `workspace_snapshot` **rows**; it never touches the knowledge model, the
observations, the adjudication history, the learning rows or the evidence. The full
record is in `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md`.

---

## 7. The restore drill (W48-08)

`scripts/restore-drill.sh` repeats the drill against a **copy**, and the measured result
is in `docs-v2/operations/RECOVERY-RUNBOOK.md` §9. The drill's own copy method is
`VACUUM INTO` over a read-only JDBC URI, because the live file's WAL holds the schema
of v17–v19 while the main file is still the v16 checkpoint — a plain `cp` would silently
roll the database back three schema versions while still reporting
`integrity_check = ok`.

The drill restores the **database**, not a payload: the live `workspace_snapshot` table
holds **0 rows**, so the payload restore was not exercised by the drill and is not
claimed by it.

---

## 8. Known limitations

1. **The payload restore was not exercised by the drill.** The live `workspace_snapshot`
   table holds 0 rows. The payload path is covered by the 12 unit tests on temporary
   databases, not by an end-to-end drill against the owner's data.
2. **`coverage()` of a snapshot written before W48 is `KNOWLEDGE_ONLY` by derivation**,
   not by measurement: the older shape does not record its table list. It is the
   conservative answer — a reader is told the payload carries less than it might.
3. **The compatibility check counts the rows of the target**, not the rows the payload
   would have carried. A legacy payload restored into a target that holds learning rows
   is refused even if the erased learning rows would have been identical to what the
   payload lacks. The check is conservative on purpose.
4. **The observation family's completeness is checked per table**, not per column: a
   payload that carries the four observation tables with their rows is accepted. The
   referential consistency of the restored rows is verified by the store's own
   insert path and by test 6.
5. **The size bound is a byte count of the serialized payload.** Compression is not
   used: the bound must be understandable and verifiable in bytes, and a compressed
   payload would need its own version.
6. **The native-image and GPU paths do not touch the snapshot contract** — the
   compatibility check is in the JVM store and is exercised only there.
