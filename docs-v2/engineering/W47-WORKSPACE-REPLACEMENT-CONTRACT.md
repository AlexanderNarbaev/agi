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
`KnowledgeStore.ERASED_TABLES` (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:172`).
A list that all three read is the only way for them to agree.

| the table | the workspace owns it | it points at | the deletion | the evidence |
|---|---|---|---|---|
| `observation_occurrence` | yes (`workspace_id`) | the case, a source, an evidence row | **DELETE** | `KnowledgeStore.java:740` (v18), `:174` |
| `observation_adjudication` | yes | the case, a revision | **DELETE** | `KnowledgeStore.java:748` (v18), `:175` |
| `observation_disposition` | yes | the case | **DELETE** | `KnowledgeStore.java:787` (v17), `:176` |
| `evidence` | yes | a claim, a source, a case | DELETE | `KnowledgeStore.java:300`, `:177` |
| `revision` | yes | a concept, a claim, evidence ids | DELETE | `KnowledgeStore.java:338`, `:178` |
| `derivation_premise` | through its derivation | a derivation, a claim | DELETE (before the derivation) | `KnowledgeStore.java:1056`, `:179` |
| `derivation` | yes | a claim | DELETE | `KnowledgeStore.java:1051`, `:181` |
| `contradiction` | yes | two claims | DELETE | `KnowledgeStore.java:1029`, `:182` |
| `prerequisite` | yes | two concepts | DELETE | `KnowledgeStore.java:329`, `:183` |
| `representation` | yes | a concept | DELETE | `KnowledgeStore.java:311`, `:184` |
| `learning_goal_subtask` | through its goal | a goal | DELETE (before the goal) | `KnowledgeStore.java:1013`, `:185` |
| `learning_goal` | yes | — | DELETE | `KnowledgeStore.java:1005`, `:186` |
| `claim` | yes | a concept, a source | DELETE | `KnowledgeStore.java:285`, `:187` |
| `epistemic_case` | yes | a concept, a source | **DELETE** | `KnowledgeStore.java:816` (v16), `:188` |
| `source` | yes | — | DELETE | `KnowledgeStore.java:273`, `:189` |
| `concept` | yes | — | DELETE | `KnowledgeStore.java:264`, `:190` |
| `workspace` | the row itself | — | DELETE | `KnowledgeStore.java:257` |
| `workspace_snapshot` | yes | — | **KEEP** — it is the recoverable copy | `KnowledgeStore.java:706` |
| `hebbian_binding`, `assessment_item`, `learning_error`, `learning_state`, `learning_transition`, `assessment_outcome`, `dialogue_turn`, `asked_question` | yes | a concept (mostly) | **KEEP** — see §5 | `KnowledgeStore.java:841, 860, 928, 979, 986, 1129, 906, 1105` |

**The order is the reference order.** A row that points at another row goes first, so that
no reference dangles while the rows are being removed.

### 2.1 The hole the emptiness check had

`isWorkspaceEmpty` counted **ten** tables — the knowledge ones — and asked nothing of the
observation tables. A workspace that held an epistemic case and nothing else therefore
counted as **empty**: the replacement would have erased the case and its history
**without asking**, because the caller had been told that there was nothing there.

The check now reads the same declaration the deletion reads
(`KnowledgeStore.java:1477`). A refusal that depends on a second list of tables is a
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

The learner's own history — the assessment items, the learning errors, the learning
state, the transitions, the outcomes, the dialogue turns and the asked questions — is
**kept**. It belongs to the learner rather than to the workspace's knowledge, and the
W45.2 contract says so: *"the assessment items, the asked questions … are not in the
snapshot, because they are not at risk from a replacement"*
(`WorkspaceSnapshot.java:15-19`).

**The consequence, stated plainly:** those rows point at concepts, and the concepts are
erased. A replaced workspace therefore keeps learning records whose `concept_id` names a
concept that is not there. This is a **bounded residual**, not a new defect: the learning
tables were never in the erasure set, so the reference was already dangling before this
wave.

**The owner's decision OD-025 covers the epistemic class**, and this wave implements
exactly that class. Whether the learning state goes with the knowledge is a **deletion
semantics** decision that belongs to the owner, and it is asked in the W47 questionnaire
rather than decided here.

---

## 6. The snapshot

A snapshot is the only recoverable copy of what a replacement destroyed. Therefore it must
carry **everything the replacement erases** — that is the W45.2 promise
(`W45.2-STATUS.md:99-101`).

The typed knowledge content (the concepts, the sources, the claims, the evidence, the
representations, the prerequisites, the revisions, the derivations, the contradictions and
the goals) was already carried. **The observation rows are not typed content** — they are
rows of a database — so the payload was extended.

**The payload is versioned** (`KnowledgeStore.SNAPSHOT_PAYLOAD_VERSION = 2`):

| the version | the payload | who wrote it |
|---|---|---|
| 1 | the bare knowledge content | every build before this wave |
| 2 | the knowledge content **and** the observation rows, table by table, captured verbatim | this wave |

A version 1 payload is **still readable** — a snapshot taken before this wave restores
exactly as it did before (`WorkspaceReplacementContractTest.aSnapshotTakenBeforeThisWaveStillRestores`).
A payload that names a version this build does not know is **refused**: a snapshot is the
only copy, and reading it as something else would restore the wrong thing quietly.

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

1. **The learning state's references stay dangling** (§5) — a bounded residual, and an
   owner question.
2. **The snapshot's payload is still unbounded** — the W45.2 residual. The W47 retention
   task (TASK-W46-17) is the other half of that answer.
3. **The store's migration moves the live database from v16 to v19 when it is next
   opened.** It is additive and re-runnable, but it happens on the owner's machine and
   not on this one's.
4. **The HTTP surface of the dry run is exercised through the resource method, not
   through a live server** — the repository has no `@QuarkusTest` harness, and the
   endpoint test drives the resource the way the HTTP layer does.
