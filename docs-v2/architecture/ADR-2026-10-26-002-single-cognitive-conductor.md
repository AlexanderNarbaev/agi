# ADR-2026-10-26-002 — One cognitive conductor: `TrueMindCycle` is the serving path

**Status:** Accepted
**Date:** 2026-10-26
**Deciders:** repository owner (via MASTER ENGINEERING DIRECTIVE v2, §13)
**Applies to:** `matrix-brain-runtime`, `matrix-api-gateway`
**Supersedes:** the implicit arrangement described by the `MIND-W1` and
`TRUE-W1` comments, which contradicted each other

---

## 1. Context

`matrix-brain-runtime` contains **two** classes that both claim to be *the*
cognitive conductor, and the repository asserted two incompatible things about
them:

| Class | Pipeline | What the code says about it |
|---|---|---|
| `MindCycle` (`MIND-W1`) | 10 stages: REFLEX → SIGNAL → SALIENCE → ARITHMETIC → ANALOGY → BIR_RULES → HDC_MEMORY → TSETLIN → **MCTS** → MODULATORS, each delegating to a wrapper in `io.matrix.brain.runtime.stages` | its own comment: *"MindCycle is the pipeline the gateway actually serves (TrueMindCycle is the offline/audit variant)"* |
| `TrueMindCycle` (`TRUE-W1`) | 12 stage markers: REFLEX → harmful-intent → SIGNAL → SALIENCE → ARITHMETIC → ANALOGY → RELATIONAL → BILINGUAL → BIR → HDC → TSETLIN → MODULATORS, calling the **real core engines** (`ReflexEngine`, `SaliencyEngine`, `BirBrainCycle`, `SafetyMonitor`, `NeuroModulatorHub`, `CodebookMemory`) directly | `ProductionBrainClient`: *"use TrueMindCycle (real core engines), **not** the legacy hand-coded MindCycle"* |

Both claims cannot be true. Measured against the working tree at the time of
this ADR:

- `ProductionBrainClient` — the gateway's brain client, the serving path —
  instantiates **`TrueMindCycle`** (`new TrueMindCycle(new Random(42L), …)`).
  The `MindCycle` comment was therefore **exactly backwards**.
- The only production source that constructed `MindCycle` was
  `MindRuntimeCli`, the developer REPL wired as the module's `application`
  main class. Everything else that touched `MindCycle` was a test.
- The `io.matrix.brain.runtime.stages` wrapper classes are **shared**
  infrastructure, not `MindCycle`-private: `TrueMindCycle`, `ContentSimilarity`,
  `PersistentHdcStore`, `EngineCallRegistry` and `HdcNode` all use them.
  Retiring `MindCycle` does not orphan them.

A reader of the repository had no way to tell which claim was true, and the
two pipelines had drifted: `TrueMindCycle` had gained harmful-intent
classification, relational reasoning and bilingual lookup, while `MindCycle`
retained the MCTS planning stage that `TrueMindCycle` never had.

## 2. Decision

**`TrueMindCycle` is the one cognitive conductor for every serving path.**
`MindCycle` is demoted to a **legacy reference pipeline**.

Concretely:

1. **Serving paths use `TrueMindCycle`.** The gateway already did. This ADR
   makes it the stated rule rather than an accident of history.

2. **`MindCycle` is `@Deprecated`** and documented as a legacy reference
   pipeline. It is **retained**, not deleted, because it is still the only
   place the MCTS planning stage is exercised and because the differential
   tests compare the two pipelines. It must not be constructed by a serving
   path.

3. **The developer CLI defaults to the serving conductor.**
   `MindRuntimeCli` now runs `TrueMindCycle`, so a developer reproducing a
   serving behaviour sees the serving behaviour. The legacy pipeline remains
   reachable behind an explicit `--legacy` flag, so the opt-in is visible
   rather than ambient.

4. **The decision is mechanically enforced, not merely documented.**
   `CognitiveConductorGuardTest` (new, in the house style of
   `RuntimeLlmGuardTest`) asserts that:
   - no production source constructs `MindCycle`, except `MindRuntimeCli`;
   - `MindRuntimeCli` defaults to `TrueMindCycle`;
   - `ProductionBrainClient` constructs `TrueMindCycle` and never
     `MindCycle`.

   The guard was **proved non-vacuous**: with a deliberate
   `new MindCycle()` in a production source it fails, and it passes when the
   violation is removed. (This matters: `RuntimeLlmGuardTest` once passed
   while scanning nothing at all, because a relative path did not resolve
   from a Gradle test working directory.)

5. **The contradictory comment was corrected**, not left to confuse the next
   reader.

## 3. Alternatives considered and rejected

| Alternative | Why rejected |
|---|---|
| **Delete `MindCycle` and its tests outright** | It would destroy research value the directive requires be preserved: the differential pipeline, the MCTS stage's only exercise, and a large test surface that still passes. Deletion is not needed to end the ambiguity — labelling and enforcement are. |
| **Merge the two pipelines into one class** | The two have genuinely diverged (different engines, different stage sets, different failure semantics). A merge is a redesign, and the directive explicitly forbids launching a broad redesign before critical defects are repaired. It would also be unreviewable in one change. |
| **Leave both in place, documented only** | This is the state that produced the contradiction in the first place. Two files asserted opposite facts about the serving path, and nothing would have caught a third claim. Documentation without enforcement is what this ADR replaces. |
| **Make `MindCycle` the serving conductor** | `TrueMindCycle` calls the real core engines directly and has the harmful-intent gate, relational and bilingual stages. Switching the serving path to `MindCycle` would remove safety-relevant behaviour. |

## 4. Consequences

### Accepted

- The serving path has exactly one conductor, and the repository says so
  consistently.
- The ambiguity is closed by a failing-on-violation CI guard, not by a
  comment.
- `MindCycle` and its tests survive intact for differential testing.

### Recorded as a capability gap (not fixed by this ADR)

**Multi-step planning is not wired into the serving conductor.** `MindCycle`
stage 9 is the only place `MCTSPlanner` (`matrix-core`) would be exercised,
and `MCTSPlanner` has **zero production callers** — only its own unit test.
Since the serving path is `TrueMindCycle`, which has no MCTS stage, MCTS
planning is currently unwired in production. This was already true before
this ADR (the gateway served `TrueMindCycle`); the ADR makes it explicit
rather than accidental. Wiring `MCTSPlanner` into `TrueMindCycle` is a
separate change with its own evidence requirements.

### Follow-up

- `MCTSPlanner` has no production caller — see above.
- `io.matrix.brain.runtime.stages` wrappers are shared by two pipelines and
  several standalone services; if `MindCycle` is ever removed, the wrappers
  stay (they are used by `ContentSimilarity`, `PersistentHdcStore`,
  `EngineCallRegistry` and `HdcNode`).

## 5. Evidence

- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/CognitiveConductorGuardTest.java`
  — 3 tests, all passing; proved to fail on a deliberate violation.
- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindRuntimeCli.java`
  — now defaults to `TrueMindCycle`; `--legacy` selects the old pipeline.
- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindCycle.java`
  — `@Deprecated`, with the ADR reference and the corrected comment.
- `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java`
  — unchanged; the guard now asserts what it already did.
- `:matrix-brain-runtime:test` — 116 suites / 909 tests green before and after
  the change.

## 6. References

- Owner directive v2 §13 (resolve the competing `MindCycle` and
  `TrueMindCycle` implementations into a single coherent architecture with an
  ADR).
- `docs-v2/engineering/TLA-STATUS.md` — the formal-specification status
  produced in the same session.
- `SESSION.md` — RECON-W38 session journal (incident D8 and the section
  records).
