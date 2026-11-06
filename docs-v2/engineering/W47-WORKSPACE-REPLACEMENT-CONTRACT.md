# W47 — the workspace replacement contract

**Status:** `CANONICAL` · **Date:** 2026-11-05 · **Task:** TASK-W46-16
**The decisions:** OD-025, OD-026, OD-027 · **The wave:** W47

---

## 1. What this document is

The contract of the destructive workspace replacement: what it erases, what it asks
before it erases, what it leaves behind, and how a caller finds out what is at stake
before it says yes.

It exists because the W45.2 wave left a defect it could not close by itself. The W45.2
status says so in as many words (`docs-v2/engineering/W45.2-STATUS.md:149`):

> the erasure set of a workspace replacement erases the `evidence` table but not
> `epistemic_case`, `observation_disposition`, `observation_occurrence` or
> `observation_adjudication` — the set predates the observation tables. A load therefore
> leaves a case and a disposition whose evidence rows are gone. **Taking the observation
> tables out of the erasure set is the smaller fix, and it is not made here: it changes
> what a workspace replacement means, which is the owner's call.**

The owner's call was made on 2026-11-05: **OD-025** — the case goes with its decision
history and with its evidence. This document is what that decision became in code.

---

## 2. The erasure matrix

One declaration drives the deletion, the preview and the emptiness check:
`KnowledgeStore.ERASED_TABLES` (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:179`).
A list that all three read is the only way for them to agree.

| the table | the workspace owns it | it points at | the deletion | the evidence |
|---|---|---|---|---|
| `observation_occurrence` | yes (`workspace_id`) | the case, a source, an evidence row | **DELETE** | `KnowledgeStore.java:863` (v18), `:180` |
| `observation_adjudication` | yes | the case, a revision | **DELETE** | `KnowledgeStore.java:871` (v18), `:181` |
| `observation_disposition` | yes | the case | **DELETE** | `KnowledgeStore.java:910` (v17), `:182` |
| `evidence` | yes | a claim, a source, a case | DELETE | `KnowledgeStore.java:423`, `:183` |
| `revision` | yes | a concept, a claim, evidence ids | DELETE | `KnowledgeStore.java:461`, `:184` |
| `derivation_premise` | through its derivation | a derivation, a claim | DELETE (before the derivation) | `KnowledgeStore.java:1179`, `:185` |
| `derivation` | yes | a claim | DELETE | `KnowledgeStore.java:1174`, `:187` |
| `contradiction` | yes | two claims | DELETE | `KnowledgeStore.java:1152`, `:188` |
| `prerequisite` | yes | two concepts | DELETE | `KnowledgeStore.java:452`, `:189` |
| `representation` | yes | a concept | DELETE | `KnowledgeStore.java:434`, `:190` |
| `learning_goal_subtask` | through its goal | a goal | DELETE (before the goal) | `KnowledgeStore.java:1136`, `:191` |
| `learning_goal` | yes | — | DELETE | `KnowledgeStore.java:1128`, `:193` |
| **`learning_transition`** | **yes (`workspace_id`)** | a concept, an outcome | **DELETE (before the outcome)** | `KnowledgeStore.java:1109`, `:204` |
| **`learning_error`** | **yes** | a concept, an item | **DELETE (before the item)** | `KnowledgeStore.java:1051`, `:205` |
| **`assessment_outcome`** | **yes** | a concept, an item | **DELETE (before the item)** | `KnowledgeStore.java:1252`, `:206` |
| **`assessment_item`** | **yes** | a concept | **DELETE** | `KnowledgeStore.java:983`, `:207` |
| **`learning_state`** | **yes** | a concept | **DELETE** | `KnowledgeStore.java:1102`, `:208` |
| **`dialogue_turn`** | **yes** | a concept, a claim (as CSV lists) | **DELETE** | `KnowledgeStore.java:1029`, `:209` |
| **`asked_question`** | **yes** | — | **DELETE** | `KnowledgeStore.java:1228`, `:210` |
| **`hebbian_binding`** | **yes** | a concept | **DELETE** | `KnowledgeStore.java:964`, `:211` |
| `claim` | yes | a concept, a source | DELETE | `KnowledgeStore.java:408`, `:212` |
| `epistemic_case` | yes | a concept, a source | **DELETE** | `KnowledgeStore.java:939` (v16), `:213` |
| `source` | yes | — | DELETE | `KnowledgeStore.java:396`, `:214` |
| `concept` | yes | — | DELETE | `KnowledgeStore.java:387`, `:215` |
| `workspace` | the row itself | — | DELETE | `KnowledgeStore.java:380` |
| `workspace_snapshot`, `schema_meta` | yes | — | **KEEP** — the recoverable copy and the schema's own version | `KnowledgeStore.java:829`, `:324` |
| the `cognitive_*` tables | no — another store, over the same file | — | **KEEP** — see §5 | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:201` |
| the rows of every other workspace | yes | — | **KEEP** — a replacement is a decision about ONE workspace | the `WHERE workspace_id = ?` rule of every erased table |

**The order is the reference order.** A row that points at another row goes first, so that
no reference dangles while the rows are being removed. The eight learning tables follow the
same rule: a transition points at an outcome, an error and an outcome point at an item, and
all five of the concept-pointing tables point at a concept — so the referring rows are
deleted before the rows they point at, and before the concept they point at.

### 2.1 The hole the emptiness check had

`isWorkspaceEmpty` counted **ten** tables — the knowledge ones — and asked nothing of the
observation tables or the learning tables. A workspace that held an epistemic case and
nothing else therefore counted as **empty**: the replacement would have erased the case and
its history **without asking**, because the caller had been told that there was nothing
there. The same was true of a workspace that held only a learning state.

The check now reads the same declaration the deletion reads
(`KnowledgeStore.java:1538`). A refusal that depends on a second list of tables is a
refusal that can be wrong, and this one was wrong in the direction of destroying history.

---

## 3. The consent

**OD-026** — the consent can be given in two forms, and they are the same decision:

| the form | how it is sent | what it means |
|---|---|---|
| the query parameter | `?replace=true` | the caller means the replacement |
| the body field | `{"replaceExisting": true}` | the caller means the replacement |

Either one grants it (`LearningWorkbenchResource.java:196`). A body field that is not a
boolean is **refused**, not guessed at: a consent that is read loosely is not a consent
(`LearningWorkbenchResource.java:236`).

**A request without a consent still fails closed**: HTTP 409, nothing deleted, and the
refusal names what to send (`LearningWorkbenchResource.java:207`).

---

## 4. The preview

**OD-027** — `?dryRun=true` answers one question: what would a replacement erase?

The answer is the count of every erased table, in the order the tables are erased, and
the identifiers of the epistemic cases that would go with them
(`ReplacementPreview.java`, `KnowledgeStore.previewWorkspaceReplacement`).

**The preview writes nothing**: no row, no snapshot, no revision, no counter. The
contract test proves it with a fingerprint of the whole database — every row of every
table, in a stable order — taken before and after
(`WorkspaceReplacementContractTest.aPreviewReportsWhatWouldBeErasedAndChangesNothing`).

**The preview and the deletion read the same declaration.** A preview that counted
different tables would be a second opinion about the operation rather than its account of
itself.

**A dry run and a consent at once are refused** (HTTP 400): a caller that means both has
not said which of the two it means (`LearningWorkbenchResource.java:200`).

---

## 5. What is NOT erased, and why it matters

**The learning state goes with the knowledge.** The owner's answer of 2026-11-05, asked
through the W47 questionnaire, settles the deletion semantics: the assessment items, the
learning errors, the learning states, the transitions, the outcomes, the dialogue turns, the
asked questions and the Hebbian bindings are in the erasure set, exactly as the epistemic
case and its history are. A replaced workspace now holds **no** learning row that points at
a concept that is not there.

**What is left is what a workspace replacement never was: a decision about ONE workspace.**

| what is not erased | why | the evidence |
|---|---|---|
| the rows of every other workspace | a replacement is a decision about one workspace, not about the database | the `WHERE workspace_id = ?` rule of every erased table (`KnowledgeStore.java:180-215`) |
| the snapshot table | it is the recoverable copy of what was destroyed | `KnowledgeStore.java:829` |
| the schema's own version row | the schema is not the workspace's content | `KnowledgeStore.java:324` |
| the `cognitive_*` tables | they are **another store** over the same file — the cognitive store, which the epistemic replacement does not write | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:201` |
| the rest of the database | a replacement erases a workspace's rows; it is not a backup, a restore point or a database-wide operation | `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md` |

**The consequence, stated plainly.** The dangling reference that the previous version of
this contract recorded as a bounded residual is gone: a learning row and the concept it
describes now have the same fate, so a replaced workspace holds neither, and the snapshot
carries both — the rows of the eight learning tables verbatim, table by table, with the
very rule that selects them for deletion (`KnowledgeStore.java:1803`).

**What the counts do not cover.** The snapshot's record counts the knowledge content and
the four observation tables; it does not count the eight learning tables. A snapshot can
therefore hold learning rows and still report every count as zero
(`WorkspaceSnapshot.java:31-35`). The rows are in the payload, and the payload is what a
restore reads — so the honest way to ask what a snapshot is worth is to look at the rows it
holds, not at the counts.

---

## 6. The snapshot

A snapshot is the only recoverable copy of what a replacement destroyed. Therefore it must
carry **everything the replacement erases** — that is the W45.2 promise
(`W45.2-STATUS.md:99-101`).

The typed knowledge content (the concepts, the sources, the claims, the evidence, the
representations, the prerequisites, the revisions, the derivations, the contradictions and
the goals) was already carried. **The observation rows are not typed content** — they are
rows of a database — so the payload was extended. The **eight learning rows** are not typed
content either, and they are captured by the same generic rule: a table and a `WHERE`
clause. A table joins the capture by being named in `ERASED_TABLES` without the typed
snapshot carrying it, so the learning tables joined it without a line of code of their own
(`KnowledgeStore.java:1803`).

**The payload is versioned** (`KnowledgeStore.SNAPSHOT_PAYLOAD_VERSION = 2`):

| the version | the payload | who wrote it |
|---|---|---|
| 1 | the bare knowledge content | every build before this wave |
| 2 | the knowledge content **and** the rows the typed content does not carry — the observation rows and the learning rows, table by table, captured verbatim | this wave |

A version 1 payload is **still readable** — a snapshot taken before this wave restores
exactly as it did before (`WorkspaceReplacementContractTest.aSnapshotTakenBeforeThisWaveStillRestores`).
A payload that names a version this build does not know is **refused**: a snapshot is the
only copy, and reading it as something else would restore the wrong thing quietly.

**What an older version-2 payload does not hold.** The payload version did not change,
because the *shape* of the payload did not: it is still the typed content plus a list of
tables of rows. A version-2 payload taken **before** this decision therefore holds the
observation rows but **not** the learning rows — it was written by a build whose erasure set
did not erase them. Restoring such a snapshot brings back the knowledge and the case
history, and leaves the learning state of the workspace as it was at the moment of the
restore. The rows are in the payload of every snapshot taken after this decision.

**The capture and the deletion use the same rules.** The rows are captured with the very
`WHERE` clause that selects them for deletion, in the same transaction, before anything
is deleted.

**The counts are in the record** (`WorkspaceSnapshot.caseCount`, `occurrenceCount`,
`dispositionCount`, `adjudicationCount`), so a reader can see what a snapshot is worth
before restoring it.

---

## 7. The in-process door

`KnowledgeWorkbench.loadBundledPackage(String)` used to replace whatever was there,
without asking. It is the door through which a workspace's history could be destroyed
quietly, and the HTTP door was the only one that asked.

**It asks now** (`KnowledgeWorkbench.java:128-142`): it refuses to replace a workspace
that holds something, exactly as the HTTP door does.

The callers do not break, because the refusal is about a workspace that **holds
something**: the pre-W45.2 callers load a package into a workspace that holds nothing,
which is what a first load is. The one caller that loaded the same package three times
was a test that wanted **idempotence** — and idempotence is not the contract: the
contract is that a replacement is a decision, and a decision is made by the caller
(`KnowledgeWorkbenchTest.reloadingIsIdempotent` now says so).

**The internal callers that legitimately replace say so**: the retention experiment
(`RetentionExperiment.java:111-116`) sets up its own workspace and asks for the
replacement.

---

## 8. The verification

| the scope | the command | the classes | the executions | the failures | the errors | the skipped |
|---|---|---|---|---|---|---|
| the knowledge, the cognitive kernel and the API | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.cognitive.*" --tests "io.matrix.api.*" --rerun-tasks` | **196** | **1561** | **0** | **0** | **0** |

**The positive tests** (`WorkspaceReplacementContractTest`, `WorkspaceLoadEndpointTest`):
the emptiness hole · the erasure of the case, its history and its evidence · the absence
of a dangling reference · the other workspace untouched · the snapshot carrying the case
history · the restore bringing it back · the old payload still restoring · the preview
counting and changing nothing · the preview of an empty workspace · the refusal without a
consent · the in-process door asking · the two consent forms · both consents together ·
a body without the consent field · a consent that is not a boolean · the dry run · the dry
run with a consent.

**The negative controls**: the refusal happens **before** anything is deleted
(`aReplacementWithoutTheConsentIsRefused` asserts the case is still there after the
refusal); the dry run's fingerprint is **byte-identical** before and after; the v1
payload restores; the migration fixture is a **real** v18 database — the W47 capture reads
the observation tables, and a fixture that leaves them out is a broken database rather
than an old one (`WorkspaceSnapshotMigrationTest`).

**Not run**: the full `matrix-core` suite (the owner's agreement is required), the
native-image tests (GraalVM is absent on this host), the live database under `data/` (the
directive forbids opening it: the store's migration would move it from v16 to v19).

---

## 9. What changed

| the file | the change |
|---|---|
| `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java` | the erasure declaration, the versioned payload, the capture and the restore of the observation rows, the preview, the emptiness check |
| `matrix-core/src/main/java/io/matrix/knowledge/WorkspaceSnapshot.java` | the four observation counts, and the promise that the snapshot carries what the replacement erases |
| `matrix-core/src/main/java/io/matrix/knowledge/ReplacementPreview.java` | **new** — what a replacement would erase |
| `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java` | the in-process door asks; the preview is exposed |
| `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java` | the two consents, the dry run, the refusal of a conflicting request |
| `matrix-core/src/main/java/io/matrix/knowledge/RetentionExperiment.java` | the experiment says that it means the replacement |
| `matrix-core/src/test/java/io/matrix/knowledge/WorkspaceReplacementContractTest.java` | **new** — the contract |
| `matrix-core/src/test/java/io/matrix/knowledge/WorkspaceLoadEndpointTest.java` | the two consents and the dry run |
| `matrix-core/src/test/java/io/matrix/knowledge/KnowledgeWorkbenchTest.java` | the reload states the real contract |
| `matrix-core/src/test/java/io/matrix/knowledge/WorkspaceSnapshotMigrationTest.java` | the fixture is a real v18 database |

**The schema did not change.** The tables that the erasure set names have been there
since v16–v18; what changed is which of them a replacement touches.

---

## 10. The limits of this contract

1. **The snapshot's counts do not cover the eight learning tables** (§5, §6) — a snapshot can
   hold learning rows and report every count as zero. The rows are in the payload; the
   counts are not the measure of the learning state.
2. **A version-2 payload taken before this decision does not hold the learning rows** (§6) —
   it was written by a build whose erasure set did not erase them. Restoring it leaves the
   learning state of the workspace as it was at the moment of the restore.
3. **The snapshot's payload is still unbounded** — the W45.2 residual. The W47 retention
   task (TASK-W46-17) is the other half of that answer.
4. **The store's migration moves the live database from v16 to v19 when it is next
   opened.** It is additive and re-runnable, but it happens on the owner's machine and
   not on this one's.
5. **The HTTP surface of the dry run is exercised through the resource method, not
   through a live server** — the repository has no `@QuarkusTest` harness, and the
   endpoint test drives the resource the way the HTTP layer does.
