# MATRIX — DATA AND MEMORY ARCHITECTURE

**Status:** `CANONICAL` · **Date:** 2026-11-04
**What it owns:** the persistence architecture — every database, every table group, every
transaction boundary, and every path that deletes or replaces data.

**The evidence behind it:** `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §4 (the
bytecode-level census of 2026-11-04) and `docs-v2/engineering/RECON-W44-ARCHAEOLOGY.md`. Every
number here was read out of the compiled code, not out of a document that describes it.

**The rule:** *a table belongs to the store that creates it, and a write that crosses two
connections is not one transaction.* The second half of that rule is the one the project spent
W45.0 and W45.1 learning.

---

## 1. The databases

**Two SQLite files in the repository. Three connections.**

| the file | the purpose | the class | the schema |
|---|---|---|---|
| `data/knowledge/workbench.sqlite` | the canonical epistemic and cognitive store | `KnowledgeStore` (v19, 27 tables) + `CognitiveStore` (v4, 12 tables) | **two schemas, one file, two connections** |
| `data/mind/mind.sqlite` | chain B's own mind store | `SqliteMemoryBackend` | chain B's schema |

**There is no second store that duplicates the knowledge store's responsibility.** The
W46.0 audit looked for one; there is none. The rule from the directive — *do not introduce
another store* — holds today.

**The configuration source** of the canonical path is the workbench resource; the mind store's
path is `data/mind/mind.sqlite` by construction
(`SqliteMemoryBackend.java:40`).

---

## 2. The table groups and their owners

### The epistemic store — `KnowledgeStore`, schema **v19**, 27 tables, 26 indexes

| the group | the tables | the owner |
|---|---|---|
| the workspace | `workspace`, `workspace_snapshot` | the workbench; the snapshot is the recovery point of a destructive reload |
| the knowledge core | `concept`, `source`, `claim`, `evidence`, `representation`, `prerequisite`, `revision` | the knowledge model |
| the derivation | `derivation`, `derivation_premise` | the reasoner |
| the contradiction | `contradiction` | the contradiction detector |
| the case identity | `epistemic_case` | the canonical fact (W45.0) |
| the observation | `observation_occurrence` (the act), `observation_disposition` (the projection), `observation_adjudication` (the append-only history) | the W45.0 disposition lifecycle |
| the learning | `learning_goal`, `learning_goal_subtask`, `learning_state`, `learning_transition`, `learning_error` | the learning machinery |
| the dialogue and the assessment | `asked_question`, `assessment_item`, `assessment_outcome`, `dialogue_turn` | the workbench surfaces |
| the association | `hebbian_binding` | the Hebbian layer |
| the meta | `schema_meta` | the migration chain |

### The cognitive store — `CognitiveStore`, schema **v4**, 12 tables, 3 indexes

| the table | the content |
|---|---|
| `cognitive_episode` | the episode — the unit of continuity across a cycle and across a restart |
| `cognitive_prediction` | the prediction an episode made — the unanimous prediction the disposition rule compares against |
| `cognitive_error` | the error the prediction produced |
| `cognitive_event`, `cognitive_request_event` | the append-only event logs |
| `cognitive_procedure`, `cognitive_task`, `cognitive_sequence` | the procedure registry and its ordering |
| `cognitive_regulatory_state`, `cognitive_regulatory_event` | the durable regulatory state |
| `cognitive_idempotency` | the claim, the checkpoint, the owner token, the owner pid, the attempts, the failure, the recovery |
| `cognitive_schema_meta` | the migration chain |

---

## 3. The migration chain

- The knowledge store migrates in a **`while (version < SCHEMA_VERSION)`** loop
  (`KnowledgeStore.java:214`); the cognitive store has the same shape
  (`CognitiveStore.java:97`).
- **The version is written LAST.** An interrupted migration is therefore re-runnable: the
  next start sees the old version and repeats the additive steps.
- **Every migration is additive**: `CREATE TABLE IF NOT EXISTS`, `addColumnIfMissing`. No
  migration drops or rewrites a row.
- The chain: knowledge **v16 → v17 → v18 → v19**; cognitive **v1 → v2 → v3 → v4**.

---

## 4. The transactional boundaries — and the crossing

**Inside one transaction:**

| the operation | the guarantee |
|---|---|
| the workspace snapshot + the workspace replacement | the same transaction, so a replacement cannot happen without a recovery point (W45.2) |
| `recordObservationAtomically` | the case, the occurrence, the disposition and the adjudication are written together (W45.0) |
| the idempotency claim / complete / fail | a request is claimed exactly once (W45.1) |
| the request checkpoint + the request event | the checkpoint and the event log move together (W45.1) |

**Across two connections:** the cognitive kernel writes to **both** stores — the epistemic
state through the workbench (`CognitiveKernel.java:343` `storeForValidation()`, `:552`
`recordObservation(...)`) and the cognitive state through the `CognitiveStore` (`:309`, `:378`,
`:590`, `:596`, `:657`, `:870`, `:917`).

> **The two are not one transaction.** This is the single most important sentence in this
> document. It is why W45.0 made the *epistemic* write atomic and W45.1 made the *request*
> recovery idempotent, instead of pretending that the pair is atomic.

**The durability settings** are the same in both stores: `PRAGMA journal_mode=WAL` and
`PRAGMA synchronous=FULL` (`KnowledgeStore.java:184`, `CognitiveStore.java:169-170`).

---

## 5. The mechanisms that make a write recoverable

| the mechanism | where it lives | what it protects |
|---|---|---|
| the durable observation disposition | `observation_disposition` + `observation_occurrence` + `observation_adjudication` | a contradiction never overwrites an episode; the decision history is append-only |
| the request lifecycle | `cognitive_idempotency` + `cognitive_request_event` | a request survives the death of its process and is never executed twice |
| the ownership fencing | `OwnershipToken(pid, startedAt, token)`; `classify(owner, mine)` → `MINE` / `LIVE_OTHER` / `DEAD` | a dead owner's request is recovered; a live owner's is not |
| the crash recovery | `IdempotencyRegistry.recover` + `CognitiveService.recoverInterruptedRequests` | the recovery write is deliberately unfenced — it acts on behalf of a **provably dead** owner |
| the single-writer guard | `WriterLock` — the OS lock and the owner record | **one writer process per database file** (the owner's decision D4) |
| the migration | the additive chain, the version written last | an interrupted migration is re-runnable |

---

## 6. The deletion and the replacement paths — and the two open gaps

| the path | what it erases | the protection |
|---|---|---|
| `deleteWorkspace` (`KnowledgeStore.java:1534-1540`) | the rows of **twelve** tables: `revision, prerequisite, representation, evidence, derivation, derivation_premise, contradiction, learning_goal, learning_goal_subtask, claim, source, concept` + the workspace row | the HTTP load refuses without `?replace=true`, and the snapshot is taken in the same transaction (W45.2) |
| the cognitive tables | **nothing** erases them — they are append-only by design | — |

### The two gaps

**Gap 1 — the erasure set predates the observation tables.** It erases `evidence` but **not**
`epistemic_case`, `observation_occurrence`, `observation_disposition` or
`observation_adjudication`. **A load therefore leaves a case and its decision history while
erasing the evidence rows the observation wrote.** The status is `UNVERIFIED` in the
capability registry, and the resolution is the owner's decision **D3-c** —
`docs-v2/governance/OPEN-QUESTIONS.md`, **Q-W46-03**.

**Gap 2 — there is no file-level backup.** The SQLite backup API (`BackupManager`,
`VACUUM INTO`) appears **nowhere** in the production sources, and
`docs-v2/operations/RUNBOOK.md` contains no occurrence of backup, restore or snapshot. The
whole-database backup and recovery runbook is a **W46.3 deliverable**
(`docs-v2/operations/RECOVERY-RUNBOOK.md`).

**And the residual of D4** — the writer lock resolves the path with
`toAbsolutePath().normalize()`, not `toRealPath()`, so **two symlinked aliases of the same
file yield two lock files and two writers.** It is the owner's decision, it touches the
database-ownership boundary, and it is recorded as **TASK-W46-4** in the roadmap.

---

## 7. Where the evidence lives

| the claim | the evidence |
|---|---|
| the schema versions and the table counts | `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §4; the constants in `KnowledgeStore.java:90` and `CognitiveStore.java:97` |
| the connections | `KnowledgeStore.java:170`, `CognitiveStore.java:146`, `SqliteMemoryBackend.java:40` |
| the transactional crossings | `CognitiveKernel.java:343, 552` and the six `CognitiveStore` call sites |
| the deletion set | `KnowledgeStore.java:1534-1540` |
| the migration chain | `KnowledgeStore.java:214`, `CognitiveStore.java:97` |
| the capability claims | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` — the rows `W45-ATOMIC`, `W45-SNAPSHOT`, `W45-FAILCLOSED`, `W45-ISOLATION` |
