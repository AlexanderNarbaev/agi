# RISK-REGISTER — operational and cognitive risks, revalidated

**RECON-W39** · 2026-10-30 · owner directive §12

Every item below was re-examined against the **current tree**, not against the
previous wave's conclusion. Each verdict cites the file and line that decided
it. Where an item could not be settled from the code, it is marked BLOCKED
rather than guessed at.

Status vocabulary: `FIXED` (the defect was real and is corrected),
`VERIFIED_NOT_A_DEFECT` (the claimed defect does not exist),
`OPEN` (still present), `INTENTIONAL_LIMITATION` (deliberate and documented),
`REQUIRES_RFC` (needs a decision that is not the coordinator's),
`BLOCKED` (could not be determined), `DEFERRED` (out of scope, with a reason).

## Operational

| # | Risk | Status | Evidence |
|---|---|---|---|
| 1 | Stripe webhook replay | **FIXED** | `matrix-billing/.../StripeWebhookHandler.java`: `DEFAULT_TOLERANCE_SECONDS = 300`, a `Clock` is injected (`Clock.systemUTC()` default), and the timestamp is rejected when it is outside the symmetric window or too far in the future. The tolerance cannot be disabled — the constructor rejects a non-positive value. |
| 2 | Billing idempotency | **FIXED** | `matrix-billing/.../CreditLedger.java:41-45` — `creditFromStripe` records the Stripe event id and skips an event that was already processed. |
| 3 | Docker artifact paths and startup | **FIXED** | `deploy/docker/Dockerfile.api-gateway:28` copies `/src/matrix-api-gateway/build/install/matrix-api-gateway/` (the `installDist` layout the build actually produces) and runs `/app/bin/matrix-api-gateway`; `Dockerfile:29` copies the `quarkus-app/` layout and starts `quarkus-run.jar`. Both carry a real readiness `HEALTHCHECK` against `/q/health/ready`. |
| 4 | Native build tasks | **FIXED** | `matrix-api-gateway/build.gradle:10` now states that `:matrix-api-gateway:nativeCompile` does not exist in that module; the CI job uses `installDist`. |
| 5 | CI `continue-on-error` | **FIXED** | `grep -rn continue-on-error .github/workflows/*.yml` returns only the two explanatory comments left by RECON-W38.4 (ci.yml:233, ci.yml:260). No step uses it. |
| 6 | ONNX dependency isolation | **VERIFIED_NOT_A_DEFECT** | `ai.onnxruntime` is imported by `matrix-core/.../api/OnnxRuntimeAdapter.java` only, and that class has **no production caller** — the only references are `OnnxRuntimeAdapterTest`. ONNX is not reachable from a decision path. |
| 7 | CUDA/ONNX native-library symlinks | **FIXED** | `git ls-files -s` contains no mode-120000 entries at all: there are no committed symlinks, so none can point into a developer's virtualenv. |
| 8 | Disabled modules | **INTENTIONAL_LIMITATION** | `settings.gradle:19-23` — `matrix-fpga`, `matrix-micro`, `matrix-ros2` and `matrix-web-ui` are excluded with an explicit comment each ("intentionally NOT included in Gradle"). The exclusion is documented, not accidental. |
| 9 | Unused dependencies | **FIXED** | `matrix-brain-runtime/build.gradle` now declares only `project(':matrix-core')` and `jackson-databind`; the `matrix-audit` and `matrix-observability` declarations that were never imported are gone. |
| 10 | Frontend tooling drift | **FIXED** | `matrix-web-ui/package.json:19` pins `"next": "16.3.5"`; `matrix-web-ui/README.md:21` states **Next.js 16.3.5**. The two agree. |
| 11 | Native-image and CLI claims | **FIXED** | `matrix-core/.../quarkus/runner/ApplicationImpl.java:115-116` — the hard-coded headline numbers ("171+ cognitive measurement classes", "6,700+ test methods across 940+ test files") survive only as a comment recording that they *used to* be printed; the values are measured at build time. |

## Cognitive

| # | Risk | Status | Evidence |
|---|---|---|---|
| 1 | BIR bypass | **VERIFIED_NOT_A_DEFECT** | `neuron/NeuronLayer.java:88` builds the BIR form through `DecisionTreeAdapter.toBir(dt, k)` and `:92` evaluates it with `BooleanRuntime.evaluate`. The same path is used by `api/MatrixResource.java:246` and `cluster/NeuronClusterActor.java:276`. No production evaluation path bypasses the BIR. |
| 2 | Uncontrolled randomness in decision paths | **VERIFIED_NOT_A_DEFECT** | Every production `Random` is seeded: `cauldron/LevinSchedule.java:25` (`new Random(seed)`), `cauldron/CauldronProtocol.java:91` (derived from a parent generator), `tsetlin/WisardProducer.java:31`, and the gateway's brain cycle `api/brain/ProductionBrainClient.java:129-130` uses `new java.util.Random(42L)` — a fixed seed, so the cycle is replayable. The remaining `new Random(...)` sites are generators, simulations, benchmarks and CLI demos. |
| 3 | MindCycle vs TrueMindCycle routing | **OPEN** | The gateway builds `TrueMindCycle` (`ProductionBrainClient.java:129-130`) and the comment says "use TrueMindCycle (real core engines), not the legacy hand-coded MindCycle" — but the legacy `io.matrix.brain.runtime.MindCycle` class is still in the tree and is referenced by stale comments (ProductionBrainClient.java:29, 325, 339, 369) that still call the running cycle "MindCycle". Nothing constructs the legacy class any more, so it is dead code whose documentation points readers at the wrong implementation. **Smallest fix:** delete the legacy class and correct the four comments. |
| 4 | Unbounded `CognitiveProcessor` history | **OPEN** | `consciousness/CognitiveProcessor.java:53,75` — `history` is an `ArrayList` that is only ever appended to, and it is the list the self-attention runs over (`:80`). Nothing trims it. The separate sliding window *is* bounded (`CognitiveSlidingWindow.add` removes the oldest entry past `windowSize`), so the leak is specifically the attention history. **Smallest fix:** trim `history` to a configured maximum, as the sliding window already does. |
| 5 | Unused caches and sliding windows | **VERIFIED_NOT_A_DEFECT** | `consciousness/CognitiveSlidingWindow.java:51-56` — `add` appends and then removes the oldest entry when the window is over `windowSize`; the sinks list is separately capped by `sinkCount`. Both are read: `CognitiveProcessor.java:110` returns the window contents. |
| 6 | Incomplete RAG | **VERIFIED_NOT_A_DEFECT** | Retrieval is bounded by an explicit `topK` at every call site (`rag/BooleanRag.java:76`, `rag/HybridBooleanRag.java:66,226`, `rag/IndexedBooleanRag.java:37`) — truncation is the designed retrieval size, not a silent cut. An empty store is reported as a **miss with a reason** (`brain/runtime/stages/HdcRetrievalStage.java:310-311, 434-437`: `HdcResult.miss("reason=empty-store")`), not as a successful retrieval. |
| 7 | ConsciousBrain integration | **VERIFIED_NOT_A_DEFECT** | `consciousness/CognitiveGenesisProfileBuilder.java:3,23-26` builds a real `CognitiveGenesisProfile` from a `ConsciousBrain.CycleReport` — the brain's cycle output is consumed by the memory layer, so it is not dead code. |
| 8 | PragmaticTest outcome evaluation | **VERIFIED_NOT_A_DEFECT** | `neuron/PragmaticTest.java:9-22,43-61` implements James–Dewey pragmatism: the meaning of a percept is the action that *successfully* follows it, and `trial(...)` updates the meaning strength from the trial's outcome. It evaluates outcomes, not merely the absence of an exception. |
| 9 | Error registration reaching learning | **OPEN** | The mechanism exists — `cognitive/CognitiveErrorStream.java:31` records errors, `cognitive/ErrorDrivenLearner.java:48` applies a decay to a `HebbianUpdater.State` from that stream, and `neuron/ConsciousBrain.java:68` holds the stream — but `ErrorDrivenLearner.applyDecay` has **no caller in any production source**: the recorded errors never reach the learning layer. **Smallest fix:** call `applyDecay` where the brain cycle applies its learning update. |
| 10 | `AgentLoop.checkConvergence()` with multiple tasks | **OPEN** | `agent/AgentLoop.java:428-433` — the check is `scheduler.allTasks().stream().anyMatch(t -> t.status() == Task.Status.COMPLETED)`. With several tasks in flight, completing **one** of them sets the loop's converged flag and ends the iteration, so the remaining tasks are abandoned. **Smallest fix:** converge when the tasks the loop was given are complete, not when any one of them is. |
| 11 | InterAgentPhi interpretation | **VERIFIED_NOT_A_DEFECT** | `consciousness/InterAgentPhi.java:23,33-41` states what the measure is and names every input it is computed from (self-representation, HDC recall similarity and distance, the WuWei no-op flag and excess surprise); `neuron/ConsciousBrain.java:186,249` captures the snapshots and computes it. The meaning is defined at the definition site and used consistently. |
| 12 | 8-D metric proxies | **VERIFIED_NOT_A_DEFECT** | `consciousness/IntegrationMetrics.java:12-17` documents the provenance of each metric, and labels the approximation as one: "C_N (neural complexity): cheap integration proxy", "Phi_binary: exact Phi for N <= 8 binary systems". A proxy is present and is called a proxy. |
| 13 | Greedy Wasserstein vs exact optimal transport | **VERIFIED_NOT_A_DEFECT** | `ktopo/FingerprintDistance.java:4,37-52` — `wasserstein1` accumulates `Σ_i |cumA_i − cumB_i| · binWidth` over the shared histogram grid. That is the CDF-integral formula, which **is** the exact 1-D Wasserstein-1 distance between the two piecewise-constant distributions; nothing is approximated and no greedy assignment is used. |
| 14 | EXP-010 criterion mismatches | **VERIFIED_NOT_A_DEFECT** | `test/.../tsetlin/Exp010ComparisonTest.java:63` — "Tsetlin grid — model selection strictly by TRAIN accuracy", and `:140` asserts the learnability sanity bound `max(r[1], r[2]) > 0.5`. The selection criterion is stated where it is applied, and the assertion checks what the run measured. |

## Added by RECON-W39 — one more, found while reviewing the algorithm

| Risk | Status | Evidence |
|---|---|---|
| The production MCTS search has no reward signal | **OPEN** | `matrix-brain-runtime/.../stages/PlanningStage.java:103-105` builds the tree with the reward function `(DecisionTree dt) -> 0.5` — a constant. Every simulated rollout scores the same, so the UCB1 selection compares equal means and the rule reduces to the exploration term. `PlanningStage` is the **only** production caller of `MctsTree`. The measured comparison (ADR-2026-10-30-005): the production search costs 18.197 us/op against 0.808 us/op for one greedy step, and reaches a *lower* mean reward (2.60 against 3.25) on the benchmark problem. **Fixing it means deciding what the planning stage is optimising** — a design decision, recorded as ADR-2026-10-30-005 R3, not a code change. |

## Added by RECON-W41 — the risks this wave created, named

| Risk | Status | Evidence |
|---|---|---|
| The support score could be read as a probability of being right | **INTENTIONAL_LIMITATION** | `matrix-core/.../knowledge/SupportScore.java` — the record carries `meaning` ("It is NOT a probability that the answer is factually correct, and it has not been calibrated against any held-out set") and `calibrationStatus`, and the answer panel shows both. The name is `supportScore`, not `confidence`, and `Answer.confidence` no longer exists. |
| The teach-back could measure the lesson instead of the learner | **FIXED** | `matrix-core/.../knowledge/TeachBackExperiment.java` — the learner is a `StructuredLearner` that never sees the lesson, the phases use disjoint item sets, and the result reports the legacy containment score and a lexical baseline beside the structured score. The live run on the photosynthesis concept: structured 1.00, legacy 1.00, lexical baseline 0.00, **delayed phase 0/1**. |
| A goal could be lost with the process that was pursuing it | **FIXED** | `matrix-core/.../knowledge/LearningGoal.java`, `GoalManager.java` and `KnowledgeStore.java` (schema v8) — the goal, its subtasks, its spent budget, its failed attempts and its checkpoint are written to SQLite. Verified live: after `kill -9` and a restart the goal, the completed subtask, the budget and the checkpoint were all intact. |
| A goal could report itself complete without the evidence | **FIXED** | `LearningGoal.isComplete` requires every REQUIRED subtask to be COMPLETED **and** every success criterion to have evidence; an optional subtask never blocks; a goal whose recorded state contradicts its own dependency graph is BLOCKED; a spent budget moves the goal to ABANDONED; a circular dependency never yields a false completion. |
| The conflict detector could cry "contradiction" on unrelated claims | **FIXED** | `ContradictionDetector` — two claims contradict only when their terms agree once the negation is taken out. The bound is pinned by a test: the logic domain has exactly two conflicts, and the claims about different variables and about different assignments are not among them. |
| The classifier could read a derivation question as a plain fact | **FIXED** | `ReasoningEngine.classify` folds its keywords the way the tokenizer folds the terms. Live: "Does q follow from p and p implies q?" is classified `DERIVATION`, and the three recorded rules are checked (`DERIVED_VALID`). |
| A formal property that cannot fail could be read as a check | **INTENTIONAL_LIMITATION** | `formal/KnowledgeAxes.tla` — `ConflictsAreKept` is vacuous by construction (the model has no action that removes a conflict, which is the guarantee). The mutation harness records it as a survivor with the finding; the registry carries the gap as `VACUOUS_PROPERTY`. Two other mutants cover the conflicts machinery and are killed. |
| An oversized document could be imported as knowledge | **FIXED** | `matrix-core/.../knowledge/KnowledgeImporter.java` — `MAX_PACKAGE_CHARACTERS = 4 MiB` and `MAX_EXCERPT_CHARACTERS = 64 KiB`; a package or a source excerpt over the limit is refused with a message that says which limit was exceeded. Tested in `KnowledgeImportLimitsTest`. |
| A knowledge document could be executed as if it were an instruction | **VERIFIED_NOT_A_DEFECT** | The knowledge store is not on the LLM path: `grep -rn "KnowledgeStore\|KnowledgeSnapshot" matrix-core/src/main/java/io/matrix/api/*.java matrix-core/src/main/java/io/matrix/rag/*.java` returns nothing. A source's text is stored and reasoned over; it is never placed in a prompt, and the system's own generated answers are written to the episodic memory with the tag `generated-dialogue` and are excluded from the generation context. |
| The engine's reach is two curated domains | **INTENTIONAL_LIMITATION** | `logic.pack.json` (11 concepts, 28 claims, 2 sources, 8 representations, 3 derivations) and `photosynthesis.pack.json` (8/17/4/18/13). The release report states that no result supports a general statement about the system's competence. |
| The history of the cognition pipeline is unbounded | **OPEN** | Inherited from W40, not addressed by this wave — see the summary below. The knowledge workbench's own history (asked questions, outcomes, goals) is bounded by the store, not by a limit. |

## Summary

- Operational: **9 FIXED**, **1 VERIFIED_NOT_A_DEFECT** (ONNX), **1 INTENTIONAL_LIMITATION** (the deliberately excluded modules). No open operational defect.
- Cognitive: **8 VERIFIED_NOT_A_DEFECT**, **4 OPEN** — the legacy `MindCycle` class and its stale comments, the unbounded attention history in `CognitiveProcessor`, the unwired `ErrorDrivenLearner.applyDecay`, and `AgentLoop.checkConvergence()` converging on the first completed task.

The four open items are recorded, not fixed: three of them change runtime behaviour (a convergence rule, a learning path, a memory bound) and the fourth is dead code in a module whose documentation is otherwise the reference for the brain cycle. Each is a separate, small, reviewable change; none of them is a claim the repository currently makes falsely.

**RECON-W41 added** three items to the cognitive register: the unbounded
`CognitiveProcessor` history remains **OPEN** and was not addressed by this
wave; the retention failure of the adversarial teach-back is
**INTENTIONAL_LIMITATION** recorded in the release report (the delayed phase
scores 0/1 and the report says so); and the support score's uncalibrated
state is **INTENTIONAL_LIMITATION** — the score is disclosed as a support
score and is not presented as a probability.
