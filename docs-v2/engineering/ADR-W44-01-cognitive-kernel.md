# ADR-W44-01 — The cognitive kernel: contracts, placement and schema

**Status:** accepted · 2026-11-02 · wave W44 · supersedes nothing
**Scope clarified 2026-11-04 by `ADR-W45.2-01`**: the canonical entry point is named per
serving application, and the five cycles are kept rather than merged. The content below is
the historical decision and is not rewritten.
**Author:** the coordinator · **Decider:** the repository owner (the W44 directive itself)
**Relates to:** `ADR-W43-01-epistemic-model.md` (the epistemic model this wave extends),
`ADR-2026-10-26-002-single-cognitive-conductor.md` (one conductor per serving path)

---

## 1. The problem this decision solves

W43 left MATRIX with a good epistemic core and no cognitive kernel.

The epistemic core is real: `KnowledgeWorkbench`, `ReasoningEngine`, `EntailmentChecker`,
`KnowledgeRetriever`, the `Source`/`Claim`/`Evidence`/`Derivation` model, the counterexample
cycle and the revision history are all implemented, tested, reachable from a user request
through `POST /v1/workbench/...`, and persisted in SQLite (`KnowledgeStore`, schema v15).

What is missing is the layer the W44 directive actually asks for: a system that **perceives a
signal, maintains a model of the world, forms competing hypotheses, predicts, chooses an
experiment, compares prediction with outcome, revises its model, chooses a computational method
by measured preconditions and cost, and reuses a validated cheaper procedure when one exists**.

W43 also left three specific debts that this decision must not inherit:

1. **`Case` was planned and never implemented.** `ADR-W43-01` §4.5 describes a first-class
   `Case`; `RECON-W43-STATUS.md:98` records it as `PLANNED`, pending a schema-16 migration that
   was the owner's decision. There is no `Case.java` in the repository.
2. **Two serving chains that do not touch.** The Quarkus epistemic core (`matrix-core`) and the
   brain runtime (`matrix-api-gateway` → `ProductionBrainClient` → `TrueMindCycle`) are separate
   systems with no call between them. A second application inside MATRIX would be the third.
3. **Every hormone-like subsystem is unreachable.** `BiochemicalNetwork`,
   `BiochemicalOrchestrator`, `KineticModulator`, `CognitiveModulationBridge`, `Impulse` and
   `AttentionRouter` have no production caller, and every coefficient in them is hand-picked
   with no cited source.

The decision this ADR records is therefore threefold: **where the kernel lives**, **what
contracts it speaks**, and **what the schema gains**.

---

## 2. Decision

### 2.1 Placement: `matrix-core`, package `io.matrix.cognitive`

The kernel is implemented in `matrix-core/src/main/java/io/matrix/cognitive/`, because that is
the only module in the repository that is simultaneously (a) the Quarkus application
(`matrix-core/build.gradle:5` is the only `io.quarkus` plugin in the tree), (b) the owner of the
epistemic store and its SQLite file, and (c) the owner of the workbench HTTP surface and the
static workbench UI where the cognitive trace has to be visible.

Rejected:

- **`matrix-brain-runtime`** — it owns the serving pipeline (`TrueMindCycle`) but has no Quarkus
  plugin, no HTTP surface and no access to `sqlite-jdbc` (matrix-core declares it with
  `implementation`, which is not exposed transitively). A kernel there could not be exposed, not
  persisted in the epistemic store, and not shown in the workbench.
- **A new module** — a fifteenth Gradle module for one wave of work would be a second
  application and a second build entry point, which the W44 directive forbids explicitly.
- **`matrix-core` under `io.matrix.knowledge`** — the kernel is not knowledge management; the
  epistemic model is one of its consumers, not its container.

### 2.2 The contracts

The W44 directive lists seventeen semantic concepts and says explicitly that they are *not* an
instruction to create a Java class per name. The mapping that follows is the decision:

| Directive concept | Implementation | Decision |
|---|---|---|
| `Signal` | `io.matrix.cognitive.CognitiveSignal` | **NEW** — the raw payload with its channel and provenance; format only, never meaning |
| `Percept` | `io.matrix.cognitive.Percept` | **NEW** — a candidate interpretation, possibly malformed, possibly several |
| `Representation` | `io.matrix.knowledge.Representation` | **REUSE** — already exists, already persisted |
| `WorldState` | `io.matrix.knowledge.KnowledgeSnapshot` + `Claim`/`Concept`/`Evidence` | **REUSE** — no second world model |
| `Hypothesis` | `io.matrix.knowledge.Claim` with `ClaimStatus.HYPOTHESIS` / `CANDIDATE` | **REUSE** — W43 already models a hypothesis as a claim with a warrant; a second hypothesis class would be a second epistemic vocabulary |
| `Prediction` | `io.matrix.cognitive.Prediction` | **NEW** — a prediction is a temporal expectation, not a proposition; it has no place in the claim model |
| `Evidence` | `io.matrix.knowledge.Evidence` | **REUSE** |
| `ActionProposal` | `io.matrix.cognitive.microworld.Experiment` (W44.4) | **NEW, sandbox-scoped** — the only action type in W44 is an experiment inside the micro-world |
| `Outcome` | `io.matrix.cognitive.microworld.Observation` + `io.matrix.knowledge.Case` | **NEW/REUSE** |
| `ErrorSignal` | `io.matrix.cognitive.ErrorSignal` | **NEW** — an explicit prediction error with provenance and a disposition |
| `MemoryEpisode` | `io.matrix.cognitive.CognitiveEpisode` | **NEW** — the kernel's own episode log |
| `DriveSignal` | `io.matrix.cognitive.DriveSignal` | **NEW** — a typed regulatory dimension with a bounded level and ONE allowed effect |
| `Goal` | `io.matrix.knowledge.LearningGoal` | **REUSE** for learning goals; internal cognitive tasks are `io.matrix.cognitive.CognitiveTask`, which is NOT a second learning-goal hierarchy and does not compete with `GoalManager` |
| `AlgorithmCapability` | `io.matrix.cognitive.algorithms.AlgorithmCapability` | **NEW** |
| `ComputeBudget` | `io.matrix.cognitive.algorithms.ComputeBudget` | **NEW** — the existing `io.matrix.budgeter` package has no serving-path caller and models a different thing (agent budgets, not per-computation limits) |
| `AlgorithmDecision` | `io.matrix.cognitive.algorithms.AlgorithmDecision` | **NEW** |
| `LearningTrace` | `io.matrix.cognitive.LearningTrace` | **NEW** — the W44 scenario record; the brain runtime's `BrcStep` trace belongs to the other serving chain and is not reachable from Quarkus |

The rule behind the table: **a new class is justified only when no existing class can carry the
contract without changing its meaning.** Every "REUSE" row is a class that already exists, is
already tested, and already persists.

### 2.3 Event model

Events are `io.matrix.cognitive.CognitiveEvent` records with a type, a schema version, a
correlation id and an immutable payload. They are produced in-process and appended to the
cognitive store when the episode requires replay. They are **not** distributed messages: the
W44 directive says local in-process calls are preferable until independent lifecycles justify
transport, and no subsystem in W44 has an independent lifecycle.

Ordering is explicit (a monotonically increasing sequence number per episode), so a replay
reconstructs the same order. The trace is append-only: a step that changes the understanding of
an earlier one adds a step, it does not edit one. This is the rule the epistemic core already
follows for revisions, and reusing it means a reader learns one convention.

### 2.4 Persistence: schema 16 adds `case`; the cognitive state gets its own store

**`KnowledgeStore` gains schema version 16** with one new table:

```sql
CREATE TABLE case (
    id            TEXT PRIMARY KEY,
    workspace_id  TEXT NOT NULL,
    concept_id    TEXT,
    statement     TEXT NOT NULL,
    conditions    TEXT NOT NULL,   -- JSON: the conditions the observation was made under
    outcome       TEXT NOT NULL,   -- JSON: what was observed
    source_id     TEXT,
    origin        TEXT NOT NULL,   -- OBSERVED | REPORTED | DERIVED | ADVERSARIAL
    observed_at   TEXT NOT NULL
)
```

and one additive column, `evidence.case_id TEXT`, so an evidence link can point at the case that
produced it.

This is the `Case` model that `ADR-W43-01` §4.5 planned and that `RECON-W43-STATUS.md` recorded
as `PLANNED` pending an owner decision. **The W44 directive is that decision**: it requires the
case model to be reconciled with the actual implementation, and an observation that carries its
conditions is exactly what the micro-world needs in order to distinguish a prediction from an
observation. The migration is additive: no existing table changes shape, so no existing query
can break, and the migration chain keeps the property that a fresh database is built by the same
code that upgrades an old one.

**The cognitive state (episodes, predictions, errors, procedures, tasks, drives) is persisted by
`io.matrix.cognitive.CognitiveStore`**, a new store that opens the SAME SQLite file with its own
connection, following the precedent of `io.matrix.memory.SqliteMemoryBackend`.

Rejected: extending `KnowledgeStore` with the cognitive tables. It would have forced
`io.matrix.knowledge` to import `io.matrix.cognitive` records, creating a package cycle, and it
would have put the kernel's transient state into the W43 class that 1489 of the repository's
tests already exercise. The cost of the decision is that a write spanning both stores is not
atomic; it is handled by ordering (cognitive state first) and by deterministic identifiers, so
an interrupted write is idempotent on replay. That cost is stated here rather than discovered
later.

### 2.5 The kernel is a kernel, not a framework

The kernel is a single class, `io.matrix.cognitive.CognitiveKernel`, with one method that
processes one signal and returns one outcome plus one `LearningTrace`. It has no plugin
discovery, no service loader, no reflection and no generic bus. The W44 directive warns against
"a broad framework with no actual consumer"; the kernel's consumer is the micro-world scenario
and the workbench endpoint, and both are delivered in the same wave.

---

## 3. Alternatives rejected

| Alternative | Why rejected |
|---|---|
| Revive `BiochemicalNetwork` / `CognitiveModulationBridge` / `AttentionRouter` | They are unreachable islands with hand-picked constants. Reviving them would import unmeasured coefficients into a decision path, which is the opposite of the W44 §11.3 requirement that interactions be empirically constrained. They stay INACTIVE and are documented as such. |
| Add `DriveSignal` as a field on `NeuroModulatorHub` | That class lives in `matrix-brain-runtime`, which the kernel cannot depend on, and it carries the retrieval floor whose measured behaviour must not change. The kernel's drives are a separate, typed, bounded vocabulary. |
| A second goal hierarchy next to `GoalManager` | Explicitly forbidden by the W44 directive. Internal cognitive tasks are a distinct, bounded record type with their own store, and they never masquerade as learning goals. |
| A second knowledge store | Forbidden, and unnecessary: the kernel reuses `Claim`, `Evidence`, `Case` and the W43 store. |
| A distributed event bus | No subsystem in W44 has an independent lifecycle, so a bus would be transport for its own sake. |
| JPA / Flyway | The repository has no JPA entities and no migration tool; the epistemic store is raw JDBC with an inline migration chain, and the kernel follows the pattern that exists rather than introducing a second one. |

---

## 4. Consequences

**Accepted costs.**

- `matrix-core` grows a new package that counts towards the 82% METHOD coverage gate
  (`matrix-core/build.gradle:271-283`). The new code is therefore required to be covered, and
  the wave's own measurement reports its per-package coverage.
- Two SQLite connections may be open on the knowledge file at once. WAL mode serialises the
  writes; the wave's restart test measures the result rather than assuming it.

**Accepted benefits.**

- The kernel can be reached from a user request and from the workbench, because it lives in the
  module that owns both.
- The `Case` model that W43 planned becomes real, and the micro-world's observations acquire the
  conditions they were made under.
- Every new concept has exactly one home, and the W43 epistemic model is extended rather than
  duplicated.

---

## 5. Points that remain open for the owner

1. **Schema 16 is accepted by this ADR** because the W44 directive requires the case model to be
   reconciled with the implementation. If the owner prefers the case model to stay a `Source`
   plus `Evidence` pair, the migration is revertible: the table is additive and nothing else
   depends on it.
2. **The cognitive store's separate connection** is a deliberate cost, not an oversight
   (§2.4). A single-connection design would require `KnowledgeStore` to expose its JDBC
   connection, which this ADR rejects.
3. **The kernel does not merge the two serving chains.** The Quarkus epistemic core and the
   `MinimalHttpServer` brain runtime remain separate systems. Bringing them together is a
   separate decision with its own ADR; W44 does not attempt it.
