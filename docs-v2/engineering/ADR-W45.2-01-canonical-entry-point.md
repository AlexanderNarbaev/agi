# ADR-W45.2-01 — The canonical entry point of each serving application

**Status:** accepted · 2026-11-04 · wave W45.2 · **owner decision** (the questionnaire of
2026-11-04)
**Decider:** the repository owner
**Relates to:** `ADR-W44-01-cognitive-kernel.md` (the kernel's placement),
`ADR-2026-10-26-002-single-cognitive-conductor.md` (one conductor, scoped to the brain
runtime), `RECON-W44-ARCHAEOLOGY.md` (the bytecode evidence)
**Enforced by:** `io.matrix.api.CanonicalEntryPoint` and
`io.matrix.api.CanonicalEntryPointTest`, `io.matrix.api.CanonicalEntryPointChainBTest`

---

## 1. The problem this decision solves

The repository carried **two ADRs that disagreed about scope**, and no statement of the
architecture that the compiled code actually has.

`ADR-2026-10-26-002` decided that "`TrueMindCycle` is the one cognitive conductor for every
serving path". Its scope line says `matrix-brain-runtime`, `matrix-api-gateway` — the brain
runtime. It demoted `MindCycle` to a legacy reference pipeline and made the decision
mechanically checkable with `CognitiveConductorGuardTest`, which asserts that no production
source constructs `MindCycle`.

`ADR-W44-01` then wrote about "the cognitive kernel" and related itself to that ADR. Read
together, the two documents describe one conductor for the whole system. **The bytecode says
otherwise.** RECON-W44.0 found:

- **five** cycles that coexist, and **none delegates to the others**:
  `BrainLoopService` (25 production callers, all inside `io.matrix.consciousness`),
  `TrueMindCycle` (3), `BirBrainCycle` (3), `MindCycle` (1), `ConsciousnessLoop` (1);
- **two serving chains that do not meet**: the Quarkus epistemic application
  (`/v1/workbench/**`, `/v1/cognitive/**`) and the hand-written brain runtime
  (`MinimalHttpServer` → `ProductionBrainClient` → `TrueMindCycle`);
- a **third** cluster — the measurement cluster of `io.matrix.neuron.ConsciousBrain` — with
  **zero** production callers, on which the metrics, the profile builders and
  `BrainLoopServiceV2` hang.

A reader had no way to tell which of the two was true, and the answer "it depends which
application you are in" was nowhere written down.

## 2. The decision

**The canonical entry point is named per serving application. The cycles are kept, with their
distinct responsibilities. Nothing is merged, and no global conductor is declared.**

### 2.1 The serving applications and their canonical entry points

| the application | the canonical entry point | the routes | the cycles it runs |
|---|---|---|---|
| **the epistemic and workbench application** (`matrix-core`, Quarkus) | `io.matrix.knowledge.LearningWorkbenchResource` | `/v1/workbench/**` | `io.matrix.cognitive.CognitiveKernel` — the W44 learning loop |
| | `io.matrix.api.OpenAIChatResource` | `/v1/chat/completions` | `io.matrix.reasoning.BrainLoopService` — the nine-stage loop |
| **the brain runtime** (`matrix-brain-runtime`, behind the hand-written server) | `io.matrix.api.MinimalHttpServer` | `POST /v1/analyze` | `io.matrix.brain.runtime.TrueMindCycle` |

**The epistemic application runs TWO loops.** That is not a wart to be smoothed over — it is
the fact the archaeology found, and naming only the cognitive kernel would repeat the ADR's
original error in the opposite direction.

### 2.2 The specialists, retained

| the loop | what it is |
|---|---|
| `io.matrix.brain.runtime.TrueMindCycle` | the conductor of the brain runtime; it already delegates to `BirBrainCycle` through its `BirInferenceStage` |
| `io.matrix.brain.BirBrainCycle` | the BIR/HDC/Tsetlin/MCTS portfolio; a method of the kernel's portfolio and a stage of the runtime's conductor |
| `io.matrix.reasoning.ConsciousnessLoop` | the loop `io.matrix.reasoning.BrainLoopService` drives; one caller |
| `io.matrix.consciousness.BrainLoopService` | the RUN 158 loop. Its 25 production callers are all inside `io.matrix.consciousness` — a package of 297 types of which **11** are served. The caller count measures the package, not a serving path |
| `io.matrix.brain.runtime.MindCycle` | the legacy reference pipeline of `ADR-2026-10-26-002`, retained for differential testing and for the MCTS stage |

### 2.3 The research cluster, not served

`io.matrix.neuron.ConsciousBrain` and the cluster that hangs off it are **inactive-but-tested**
with **zero** production callers. They are research and simulation, not a serving path, and
they are not revived by this decision.

## 3. What this decision explicitly does NOT do

- **It does not merge the cycles.** No refactor of 297 types, of which 11 are served.
- **It does not declare a global conductor.** `TrueMindCycle` is the conductor **of the brain
  runtime**, which is what `ADR-2026-10-26-002` already scoped it to; it is not the conductor
  of the epistemic application, whose learning loop is the `CognitiveKernel`.
- **It does not create a sixth cycle, a kernel, a bus or a framework.**
- **It does not join the two chains.** That is Decision 2 of the same wave, and the answer
  there is isolation and fail-closed.

## 4. Why the naming is in code and not only in this document

A naming that lives in prose drifts. The declaration is therefore a class,
`io.matrix.api.CanonicalEntryPoint`, and it is checked:

- `CanonicalEntryPointTest` (matrix-core) asserts that there are two serving applications,
  that each names an entry point and its routes, that **no cycle is the cycle of two serving
  applications**, that the specialists and the research cluster are classified as such, and
  that every declared class this module can resolve **does** resolve — so a typo cannot hide.
- `CanonicalEntryPointChainBTest` (matrix-api-gateway) asserts the chain B side against the
  gateway's own code: the declaration names `MinimalHttpServer` and `TrueMindCycle`, the
  `ProductionBrainClient` really holds a `TrueMindCycle`, and the entry point really has a
  `main`.

**The classes are named by NAME, not by reference.** Chain B's entry point lives in
`matrix-api-gateway` and its cycle in `matrix-brain-runtime`, while the declaration lives in
`matrix-core`, which both depend on. A direct reference would make `matrix-core` depend on
the gateway — the wrong way round.

## 5. What it costs

| the cost | how it is bounded |
|---|---|
| a sixth place where the architecture is written down | the declaration is a **single** class, and the tests keep it true; the ADRs point at it rather than repeating it |
| the declaration can drift from the code | the tests resolve every class they can and the chain B test checks the wiring; the chain isolation gate proves the two chains do not meet |
| the ADRs still exist and still disagree about scope | this ADR supersedes the scope claims of both and says exactly which sentence is now the truth |
| the epistemic application runs two loops, which is harder to explain than one | it is what the code does. A single-loop claim would be a false claim, and the archaeology's whole point was that the documents describe a brain while the call graph describes a pipeline |

## 6. Consequences

### Accepted

- The architecture now has one statement of the canonical entry point per serving
  application, and it is mechanically checked.
- `ADR-2026-10-26-002` keeps its scope — the brain runtime — and its guard; this ADR widens
  the statement to the whole repository and names the epistemic application explicitly.
- The specialist loops keep their distinct responsibilities and their tests.

### Recorded as a capability gap (not fixed by this decision)

- **`MCTSPlanner` has no production caller.** The serving conductor has no MCTS stage, so
  multi-step planning is unwired in production. `ADR-2026-10-26-002` already recorded this.
- **The measurement cluster cannot run.** `PhiId` and its siblings hang off
  `ConsciousBrain`, which has zero production callers. This decision does not revive it.
- **The two applications do not meet**, and by Decision 2 of this wave they are not to.

## 7. References

- `RECON-W44-ARCHAEOLOGY.md` — the bytecode evidence, §1 and §2.
- `W44-COGNITIVE-CYCLE-MAP.md` — the five cycles and the two chains.
- `docs-v2/engineering/W45-OWNER-DECISIONS.md` — the decision as it was presented, with the
  options and the evidence.
- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/CognitiveConductorGuardTest.java`
  — the existing guard this decision extends rather than replaces.
