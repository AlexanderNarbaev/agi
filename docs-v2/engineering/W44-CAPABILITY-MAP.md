# W44 — Capability map

**Directive:** W44 Cognitive Kernel Foundation · 2026-11-02
**Repository:** `AlexanderNarbaev/agi` · branch `develop`
**Baseline:** HEAD `99d35f25` · W43 close-out
**Status vocabulary:** `IMPLEMENTED` (code exists) · `TESTED` (a JUnit test exercises it) ·
`REACHABLE` (a production caller reaches it, named with file:line) · `MEASURED` (a benchmark or
report carries numbers) · `DEMONSTRATED` (exercised end to end in a recorded run) ·
`EXPERIMENTAL` (moved out of `src/main`) · `INACTIVE` (implemented, nothing reaches it) ·
`PLANNED` (declared but absent).

A capability may hold several states. The column that matters most is **Reachable**: an
implementation with no production caller is not a capability of the system, however well it is
tested.

---

## 1. The production serving path, as it actually is

There are TWO serving chains in this repository and they do not touch each other. This is the
single most important fact for the wave, and it was verified by reading the caller graph rather
than by reading the documentation.

| Chain | Entry point | Module | Reaches the epistemic core? |
|---|---|---|---|
| **A — the epistemic core** | `io.matrix.MatrixApplication` (`@QuarkusMain`) → `POST /v1/workbench/**` → `LearningWorkbenchResource` → `KnowledgeWorkbench` → `KnowledgeStore` (SQLite) | `matrix-core` (the only module with the Quarkus plugin) | — |
| **B — the brain runtime** | `io.matrix.api.MinimalHttpServer` (JDK `HttpServer`) → `ProductionBrainClient` → `TrueMindCycle` | `matrix-api-gateway` → `matrix-brain-runtime` | **No.** Not one call crosses between the two |

The JAX-RS resources of `matrix-api-gateway` (`AnalyzeResource`, `ExplainResource`,
`AuditResource`, `FederateResource`, `llm_quarantine/BrainQuarkusResource`) are **INACTIVE**:
the module applies no Quarkus plugin, so they are never mounted.

**W44 adds to chain A.** The cognitive kernel lives in `matrix-core`, is served by the same
Quarkus process on `/v1/cognitive/**`, and writes into the same SQLite file as the workbench. It
is not a second application and it is not a second knowledge store.

---

## 2. The twenty-five implementations the directive names

| # | Implementation | Source | Production caller | State |
|---|---|---|---|---|
| 1 | `KnowledgeWorkbench` | `matrix-core/…/knowledge/KnowledgeWorkbench.java` | `LearningWorkbenchResource` → HTTP `/v1/workbench/**` | IMPLEMENTED, TESTED, REACHABLE, MEASURED, DEMONSTRATED |
| 2 | `ReasoningEngine` | `matrix-core/…/knowledge/ReasoningEngine.java` | `KnowledgeWorkbench.reason` → `POST …/reason` | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| 3 | `EntailmentChecker` | `matrix-core/…/knowledge/EntailmentChecker.java` | `ReasoningEngine` → `KnowledgeWorkbench.reason` | IMPLEMENTED, TESTED, REACHABLE |
| 4 | `KnowledgeRetriever` | `matrix-core/…/knowledge/KnowledgeRetriever.java` | `KnowledgeWorkbench.ask` → `POST …/ask` | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| 5 | `Source` / `Claim` / `Evidence` / `Derivation` / `KnowledgeRevision` / `ClaimStatus` | `matrix-core/…/knowledge/*.java` | every `/v1/workbench/**` route | IMPLEMENTED, TESTED, REACHABLE, MEASURED, DEMONSTRATED |
| 6 | Counterexample and refinement (`ClaimAssessor`, `CounterexampleReading`, `KnowledgeRefiner`) | `matrix-core/…/knowledge/` | `POST …/claims/{id}/examples`, `…/refine/accept`, `…/refine/reject` | IMPLEMENTED, TESTED, REACHABLE, MEASURED, DEMONSTRATED |
| 7 | Conversation state (`DialogueContext`) | `matrix-core/…/knowledge/DialogueContext.java` | `KnowledgeWorkbench.ask` → `POST …/ask`; table `dialogue_turn` | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| 8 | Text perception and normalisation (`TextFeatures`, `TypoCorrector`, `EnglishLemma`, `PremiseChecker`) | `matrix-core/…/knowledge/` | `KnowledgeWorkbench.ask` → `POST …/ask` | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| 9 | HDC retrieval | `matrix-brain-runtime/…/stages/HdcRetrievalStage.java` | `TrueMindCycle` → `ProductionBrainClient` → `POST /v1/analyze` (chain B) | IMPLEMENTED, TESTED, REACHABLE, MEASURED — **not reachable from the Quarkus app** |
| 10 | BIR | `matrix-core/…/bir/`, `matrix-brain-runtime/…/stages/BirInferenceStage.java` | `BirBrainCycle` → `TrueMindCycle` → chain B; `BirRegistry` → `POST /v1/bir` | IMPLEMENTED, TESTED, REACHABLE, MEASURED (chain B) |
| 11 | Tsetlin | `matrix-core/…/tsetlin/TsetlinTrainer.java` | `RuleInductionEngine` (chain B); `AcceptanceModel` (chain A) | IMPLEMENTED, TESTED, REACHABLE — per-cycle use was REMOVED from the serving path (`TrueMindCycle.java:274-294`) |
| 12 | WiSARD | `matrix-core/…/tsetlin/WisardProducer.java` | `AcceptanceModel` → `KnowledgeWorkbench.registerLearnedRule` | IMPLEMENTED, TESTED, REACHABLE |
| 13 | MCTS | `matrix-core/…/mcts/MctsTree.java` | `PlanningStage` → `ArithmeticStage` → chain B | IMPLEMENTED, TESTED, REACHABLE, MEASURED (chain B) |
| 14 | Connectome (`Connectome`, `Synapse`, `CognitiveNode`, `ReplayPhase`, `SleepConsolidator`, `ClauseExplorer`) | `matrix-brain-runtime/…/graph/` | **none** — `grep -rn "ReplayPhase\|SleepConsolidator\|ClauseExplorer" */src/main` is empty | IMPLEMENTED, TESTED, **INACTIVE** (159 tests, zero production callers) |
| 15 | `Impulse`, `AttentionRouter` | `matrix-core/…/consciousness/` | `AttentionRouter.merge` is called with an EMPTY impulse list in every production caller | IMPLEMENTED, TESTED, **INACTIVE** |
| 16 | `NeuroModulatorHub` | `matrix-brain-runtime/…/graph/NeuroModulatorHub.java` | `TrueMindCycle` → `HdcRetrievalStage` — and the attention is pinned at `NEUTRAL_ATTENTION = 0.0`, so the floor never moves | IMPLEMENTED, TESTED, REACHABLE, MEASURED (floor only), **INACTIVE** for every other signal |
| 17 | `BiochemicalNetwork` | `matrix-core/…/federation/liquid/biochemistry/` | `IntegratedFederation` → no production caller | IMPLEMENTED, TESTED, EXPERIMENTAL, **INACTIVE** |
| 18 | `KineticModulator` | `matrix-core/…/federation/liquid/` | the same island | IMPLEMENTED, TESTED, EXPERIMENTAL, **INACTIVE** |
| 19 | `BiochemicalOrchestrator` | `matrix-core/…/federation/liquid/biochemistry/` | the same island; every coefficient is hand-picked with no cited source | IMPLEMENTED, TESTED, EXPERIMENTAL, **INACTIVE** |
| 20 | `CognitiveModulationBridge` | `matrix-core/…/federation/integration/` | none; the modulator ids it reads are never registered, so it always reads its `0.5` fallback | IMPLEMENTED, TESTED, **INACTIVE** |
| 21 | `GoalManager` | `matrix-core/…/knowledge/GoalManager.java` | only the benchmark runners — no HTTP route, no bean, no CLI | IMPLEMENTED, TESTED, **INACTIVE** |
| 22 | `GoalTracker` | `matrix-core/…/goals/GoalTracker.java` | `AutonomyLoop` → `POST /v1/goals` (chain B); in-memory only | IMPLEMENTED, TESTED, REACHABLE (chain B) |
| 23 | `InstanceMediator` / `DriverState` | `matrix-core/…/mediator/` | `SystemDemo` and a bot with no callers | IMPLEMENTED, TESTED, **INACTIVE** |
| 24 | Sleep and consolidation | `io.matrix.sleep.SleepCycle` (chain B, `POST /v1/sleep`), `io.matrix.knowledge.KnowledgeConsolidator` (chain A, `POST …/consolidate`), `io.matrix.lifecycle.ConsolidationCycle` (chain B) | three different definitions of consolidation, two of them reachable | IMPLEMENTED, TESTED, REACHABLE (inconsistent with each other) |
| 25 | CPU/GPU algorithm backends | `io.matrix.bir.SubstrateBackend` + `JvmSimdBackend` + `FpgaBackend` (no production implementor), `OnnxRuntimeAdapter` (`activeProviders` hard-coded to CPU), `MatrixNativeMath` (JDK Vector API, the only honest accelerator), `GpuTaskExecutor` (echoes its input) | the ONNX path is reachable; the GPU provider set is NOT observable at runtime | IMPLEMENTED, TESTED, REACHABLE, MEASURED — **GPU execution is not provable today** |

### 2.1 What the map changes

Four findings matter for the wave:

1. **The brain graph is a library, not a subsystem.** 159 tests, mutation-verified, zero
   production callers. W44 does not wire it in, and the reason is recorded in
   `docs-v2/releases/MATRIX-MIND-REPORT-V18.md` §"What this release does NOT claim" and
   re-verified here.
2. **Every neuromodulator is either neutral or unreachable.** The only signal with a measured
   effect is the retrieval floor, and the attention that drives it is pinned at zero. Every
   biochemical coefficient is hand-picked. W44 therefore builds a NEW, bounded, typed regulatory
   layer rather than reviving an island whose constants were never measured.
3. **Precondition metadata did not exist.** Three unrelated shapes (`ActionRegistry`'s string
   list, `BrcStepContract`'s predicates, `KMaxEnforcer`'s hard limits) and none of them attached
   to an algorithm. `Dijkstra` documents "non-negative weights" and never checks them.
   W44.2 introduces the uniform vocabulary.
4. **The `Case` model was planned and never built.** `ADR-W43-01` §4.5 describes it,
   `RECON-W43-STATUS.md` records it as `PLANNED`, and there is no `Case.java` and no schema-16
   migration. W44 implements it (schema 16).

---

## 3. What W44 added

| Capability | Source | Caller | State |
|---|---|---|---|
| Cognitive kernel | `matrix-core/…/cognitive/CognitiveKernel.java` | `CognitiveService` → `POST /v1/cognitive/observe` | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| Signal and percept contracts | `io.matrix.cognitive.CognitiveSignal`, `Percept`, `ChannelClassifier` | the kernel | IMPLEMENTED, TESTED, REACHABLE |
| Prediction / error contracts | `io.matrix.cognitive.Prediction`, `ErrorSignal` | the kernel → `cognitive_prediction`, `cognitive_error` | IMPLEMENTED, TESTED, REACHABLE |
| Case model (schema 16) | `io.matrix.knowledge.Case`, `KnowledgeStore` v16 | `KnowledgeWorkbench.recordObservation` | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| Cognitive store | `io.matrix.cognitive.CognitiveStore` | the kernel | IMPLEMENTED, TESTED, REACHABLE |
| Algorithm capability descriptor | `io.matrix.cognitive.algorithms.AlgorithmCapability` | the selector | IMPLEMENTED, TESTED, REACHABLE |
| Algorithm selector | `io.matrix.cognitive.algorithms.AlgorithmSelector` | `KernelSelector` → the kernel | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| Algorithm portfolio | `io.matrix.cognitive.algorithms.*Method` (arithmetic, entailment, shortest path, bounded search) | the registry | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| Procedure promotion | `io.matrix.cognitive.algorithms.ProcedureStore`, `ProcedureValidator` | the kernel | IMPLEMENTED, TESTED, REACHABLE |
| Reference and fast methods for the micro-world | `io.matrix.cognitive.MicroWorldReferenceMethod`, `MicroWorldSettledModelMethod` | the kernel's registry | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| Typed drives and regulation | `io.matrix.cognitive.DriveSignal`, `io.matrix.cognitive.neuromodulation.*` | `KernelRegulator` → the kernel | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| Cognitive micro-world | `io.matrix.cognitive.microworld.*` | the kernel | IMPLEMENTED, TESTED, DEMONSTRATED |
| Bounded internal task generation | `io.matrix.cognitive.CognitiveTask` | the kernel, on a prediction error | IMPLEMENTED, TESTED, REACHABLE |
| Cognitive trace in the workbench | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html` (the "Cognitive" tab) | the browser | IMPLEMENTED, DEMONSTRATED (the JavaScript is checked with `node --check`) |

---

## 4. What W44 did NOT do, and will not claim

| Not implemented | Why |
|---|---|
| Merging the two serving chains | The brain runtime is a separate application with its own storage (`data/mind/`). Connecting them is a separate decision with its own ADR. |
| Wiring the `graph` package (connectome, replay, sleep) | It has zero production callers and a documented defect in the anneal guard (`SleepConsolidator.java:251-268`). W44 does not make it reachable. |
| Reviving `BiochemicalNetwork` / `CognitiveModulationBridge` / `AttentionRouter` | Unreachable islands with hand-picked coefficients. Reusing them would import unmeasured numbers into a decision path. |
| A learned meta-controller over the portfolio | The W44 directive says: do not build an elaborate learned meta-controller before a deterministic, measurable baseline exists. W44.2 ships the deterministic baseline; the learned policy is W45. |
| GPU execution | `OrtEnvironment.getAvailableProviders()` is never called anywhere in the repository and `OnnxRuntimeAdapter.activeProviders()` is hard-coded to `{"CPUExecutionProvider"}`. No test can currently prove that a CUDA kernel ran. W44 does not claim it. |
| The five-class memory taxonomy (working/episodic/semantic/procedural/meta) as code | It exists in prose only. W44 implements the two classes the kernel actually needs — cases as episodic memory, procedures as procedural memory — and records the rest as PLANNED. |
| A second goal hierarchy | `CognitiveTask` is a bounded unit of internal work with its own store. It never masquerades as a `LearningGoal`, and it does not compete with `GoalManager`. |

---

## 5. The safety boundary, and what W44 changed about it

| Invariant | Where it is enforced | W44's contribution |
|---|---|---|
| A regulator may not weaken a gate | `RegulationBias` has no field that could express it; the reflection test asserts the declared field set | `RegulationBias` is a four-field value object and the test fails if a fifth is added |
| A learned routine may not bypass its preconditions | the selector admits a method only when its declared preconditions hold; the method checks its own | `AlgorithmSelector.decide` records every blocked candidate with the missing precondition |
| A fast path needs a reference fallback | `AlgorithmCapability.fallbackAlgorithmId` | the promoted procedure names the reference, and the reference is used whenever the fast path's domain does not hold |
| Provenance is never erased | `KnowledgeConsolidator` marks, never deletes | W44 writes cases and evidence through the same transactional `applyChanges`; a case id is derived from the observation, so a repeated write updates rather than duplicates |
| A contradicted claim is preserved | `acceptRefinement` supersedes, never deletes | W44's error path writes a case and an evidence link and never rewrites the hypothesis set |
| No autonomous task runs forever | — | `CognitiveTask` carries a budget, a stop condition, a completion condition and an allow-list of actions |

**A finding that is NOT a W44 change but is a real risk:** `POST /v1/workbench/workspaces/{id}/load`
replaces a workspace wholesale, and `KnowledgeStore.deleteWorkspace` deletes `contradiction`,
`evidence`, `derivation` and `revision` for that workspace (`KnowledgeStore.java:700-726`). The
knowledge consolidator itself only MARKS. The reload path is the one that erases contradiction
history, silently. W44 does not touch it — it is a W43 behaviour with W43 tests — but it is
recorded here as an open risk in `RECON-W44-BACKLOG.md`.
