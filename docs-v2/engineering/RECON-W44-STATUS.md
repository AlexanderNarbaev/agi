# RECON — W44 status

**Directive:** W44 Cognitive Kernel Foundation · 2026-11-02
**Repository:** `AlexanderNarbaev/agi` · branch `develop`
**HEAD at the start of the wave:** `99d35f25` (`WAL: The scenario begins with a claim that names its scope`)
**Documents:** `W44-CAPABILITY-MAP.md` · `W44-BENCHMARK.md` · `RECON-W44-BACKLOG.md` ·
`ADR-W44-01-cognitive-kernel.md` · `docs-v2/architecture/MATRIX-COGNITIVE-*.md`

---

## 1. The starting state, verified

| Fact | Value | How it was checked |
|---|---|---|
| HEAD | `99d35f25d254e9d98722a270e6ad859c341a82e7` | `git rev-parse HEAD` |
| Branch | `develop` | `git branch --show-current` |
| Owner's uncommitted work | `.gitignore`, `.opencode/todo.md`, `.opencode/w37-plan.md`, `AGENTS.md`, `opencode.json`, the deleted `opencode.json.bak`, and the untracked `.codex/`, `.veai/`, `.opencode/goals/`, `.opencode/opencode-swarm.json`, `.opencode/tmp/` | `git status --porcelain` |
| The build needs | `JAVA_HOME=/home/alexandr-narbaev/jdk25` | `./gradlew` fails without a compiler otherwise |
| Host | 32 threads, 59 GiB RAM, an RTX 5070 Ti Laptop GPU is present | `nproc`, `free`, `nvidia-smi -L` |
| Baseline test scope | `matrix-brain-runtime` **914** tests, 0 failures, 2 skipped; `matrix-core` (the W43 targeted scope: `knowledge`, `memory`, `brain`, `api`, `rag`) **1518** tests, 0 failures | run before any change |
| The module's real size | **8284** `@Test` methods across 1179 files — the 1518 are a SUBSET | the build prints this in `generateBuildInfo` |
| Quality gate before the wave | `literal_candidates=271` accepted; the measured count was at the baseline | `scripts/quality-gate.sh --report` |

**The owner's changes were not touched.** Every file this wave created is new, and the three
existing files it changed (`KnowledgeStore.java`, `KnowledgeWorkbench.java`,
`workbench/index.html`) are the W43 epistemic core and the workbench UI.

---

## 2. What the reconnaissance found

### 2.1 Two serving chains that do not touch

The epistemic core (`matrix-core`, Quarkus, `POST /v1/workbench/**`) and the brain runtime
(`matrix-api-gateway` → `ProductionBrainClient` → `TrueMindCycle`, `POST /v1/analyze`) share no
call. The JAX-RS resources of `matrix-api-gateway` are never mounted, because the module applies
no Quarkus plugin.

**W44 adds to the first chain.** The kernel is in `matrix-core`, served by the same process, and
it writes into the same SQLite file.

### 2.2 Four subsystems that are built, tested and unreachable

| Subsystem | Evidence |
|---|---|
| the connectome (`Connectome`, `Synapse`, `CognitiveNode`, `ReplayPhase`, `SleepConsolidator`, `ClauseExplorer`) | 159 tests, mutation-verified, **zero** production callers (`grep` over `*/src/main` is empty for `ReplayPhase`, `SleepConsolidator`, `ClauseExplorer`) |
| the impulse and attention path (`Impulse`, `AttentionRouter`) | `AttentionRouter.merge` is called with an **empty** impulse list in every production caller |
| the biochemical island (`BiochemicalNetwork`, `BiochemicalOrchestrator`, `KineticModulator`, `CognitiveModulationBridge`) | reachable only from `IntegratedFederation`, which has no production caller; every coefficient is hand-picked with no cited source; `CognitiveModulationBridge` reads two modulator ids that are never registered, so it always reads its `0.5` fallback |
| `NeuroModulatorHub` | reachable, but the attention that drives the retrieval floor is pinned at `NEUTRAL_ATTENTION = 0.0`, so the floor never moves |

### 2.3 Three gaps that the wave had to close

| Gap | Evidence |
|---|---|
| **No precondition vocabulary attached to any algorithm** | `Dijkstra`'s class javadoc says "non-negative weights" and the code never checks `e.weight()`. The three existing shapes (`ActionRegistry`'s string list, `BrcStepContract`'s predicates, `KMaxEnforcer`'s limits) are unrelated to algorithms. |
| **No selection policy that is reachable** | `CognitiveRouter` is the only precondition-driven router and it is DEAD CODE (its only consumer, `InteractiveChatUI`, has no production caller); it returns a MODE, not a method, and its preconditions are modulator levels rather than properties of the input. |
| **The `Case` model was planned and never built** | `ADR-W43-01` §4.5 describes it, `RECON-W43-STATUS.md` records it as `PLANNED` pending the owner's schema-16 decision, and there is no `Case.java` and no `if (version == 15)` migration branch. |

### 2.4 A risk the wave found but did NOT change

`POST /v1/workbench/workspaces/{id}/load` replaces a workspace wholesale, and
`KnowledgeStore.deleteWorkspace` deletes `contradiction`, `evidence`, `derivation` and
`revision` for that workspace (`KnowledgeStore.java:700-726`). **The knowledge consolidator
itself only MARKS** — it never deletes. The reload path is the one that erases contradiction
history, silently. W44 did not change it: it is a W43 behaviour with W43 tests, and changing it
would be a separate decision. It is recorded in `RECON-W44-BACKLOG.md` as an open risk.

---

## 3. What W44 built, and where each piece is reachable from

| Piece | Reachable from | State |
|---|---|---|
| the cognitive kernel | `POST /v1/cognitive/observe`, and the workbench's "Cognitive" tab | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| the case model (schema 16) | `KnowledgeWorkbench.recordObservation` → the workbench's Epistemic panel | IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED |
| the algorithm portfolio and the selector | `POST /v1/cognitive/solve` and the kernel's SELECT phase | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| the regulatory layer | the kernel's SELECT and ACT phases | IMPLEMENTED, TESTED, REACHABLE, MEASURED |
| the micro-world | the kernel's RESTORE, PREDICT, ACT and LEARN phases | IMPLEMENTED, TESTED, DEMONSTRATED |
| the bounded internal task | generated by the kernel on a prediction error | IMPLEMENTED, TESTED, REACHABLE |
| the cognitive trace | `GET /v1/cognitive/trace/{episodeId}` and the workbench panel | IMPLEMENTED, DEMONSTRATED |

**Measured:** 256 tests in `io.matrix.cognitive.*`, 0 failures. The per-package coverage is in
`W44-BENCHMARK.md` §1.

---

## 4. The Definition of Done, item by item

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | A verified map of the current cognitive, neuromodulatory and goal-related implementations | **MET** | `W44-CAPABILITY-MAP.md` §2 — 25 implementations with states and callers |
| 2 | The production serving path and the experimental paths are distinguished | **MET** | `W44-CAPABILITY-MAP.md` §1 — the two chains, and the unmounted JAX-RS resources |
| 3 | The architecture defines consistent signal, perception, world-state, prediction, drive, goal, algorithm and resource contracts | **MET** | `ADR-W44-01` §2.2 and `io.matrix.cognitive.*` |
| 4 | No duplicate knowledge store or competing task hierarchy | **MET** | `CognitiveTask` is a bounded internal unit with its own store; it never masquerades as a `LearningGoal`; the cases live in the W43 store |
| 5 | At least one working algorithm portfolio is reachable from a real cognitive task | **MET** | `POST /v1/cognitive/solve` and the kernel's SELECT phase; `CognitiveSolverTest` |
| 6 | Algorithm selection respects preconditions | **MET** | the selector admits only inside the declared preconditions, and the record names the missing one; `ShortestPathMethodsTest`, `MembershipLookupTest` |
| 7 | The selector measures quality and cost rather than relying on arbitrary names or hard-coded preference | **MET** | `score = accuracy − costWeight × relativeCost`, cost from the ledger; `W44-BENCHMARK.md` §2 |
| 8 | At least one repeated validated task uses a cheaper reusable procedure | **MET** | the promoted `micro-world-settled-model` procedure, validated against the reference on held-out cases |
| 9 | The reusable procedure has an applicability guard and a reference fallback | **MET** | `requiredFacts = {MODEL_SETTLED}`, `referenceAlgorithmId = micro-world-hypothesis-enumeration` |
| 10 | At least one neuromodulatory signal changes real downstream behaviour in a controlled and measured way | **MET** | the raised uncertainty changes the kernel from "no confirmation" to "a confirmation requested"; `W44-BENCHMARK.md` §3 |
| 11 | Neuromodulation cannot override safety | **MET** | `RegulationBias` has four fields and no effect can express a gate; the reflection test fails if a fifth is added |
| 12 | The system generates at least one bounded internal task from a real cognitive event | **MET** | a prediction error generates a task with a budget, a stop condition and an allow-list of one action |
| 13 | The micro-world includes hidden rules or incomplete information | **MET** | the rule is private to `LampEnvironment`; the hypothesis space starts as all 16 functions |
| 14 | The system can form competing hypotheses and select a discriminating experiment | **MET** | survivors 16 → 8 → 4 → 2 → 1; the gain is computed, 0.500 at the maximum |
| 15 | A counterexample changes the model in a traceable way | **MET** | the case, the evidence, the error with provenance and the bounded task; the model is NOT rewritten |
| 16 | The system can distinguish a prediction from an observation | **MET** | `Prediction` has no "confirmed" field; the comparison is a separate `ErrorSignal` |
| 17 | The core learning scenario survives forced termination and restart | **PARTIAL — see the note** | the restart is asserted in-process over the same file (`theObservationBecomesACaseThatSurvivesARestart`); a `kill -9` script was NOT written in this wave |
| 18 | The benchmark includes cases that must be answered, refused and clarified | **MET** | refused: the malformed signal and the negative cycle; answered: the graph and the micro-world; clarification: the assignment that cannot be parsed is refused with the reason |
| 19 | Results are reproducible and separately reported for each task family | **MET** | `W44-BENCHMARK.md` §2-§6, one section per family |
| 20 | Documentation reflects actual implementation rather than architectural aspiration | **MET** | every document separates what is implemented from what is deferred, and the deferred list is explicit |

### 4.1 The one criterion that is only partially met, and why

**Criterion 17** asks for the scenario to survive FORCED termination. What the wave proves is
that the committed state survives a restart over the same file, asserted in-process. The W43 wave
proved the stronger property with `scripts/w43-counterexample-journey.sh` (a real `kill -9` of
the application's own JVM). W44 did not write the equivalent script.

Why: the wave spent its budget on the mechanism, and the script needs a packaged Quarkus run.
It is the first item in `RECON-W44-BACKLOG.md`, and it is the first thing a reviewer should ask
for. The durability configuration that makes it possible is already in place —
`PRAGMA journal_mode=WAL` and `PRAGMA synchronous=FULL` in both stores, which is what the W43
journey relies on.

---

## 5. The negative findings

These are the results that cost something and are worth as much as the rest.

1. **The GPU path is not provable.** `OrtEnvironment.getAvailableProviders()` is never called
   anywhere in the repository, `OnnxRuntimeAdapter.activeProviders()` is hard-coded to
   `{"CPUExecutionProvider"}`, and `GpuTaskExecutor` echoes its input. The hardware is present;
   the software path is not observable. The wave therefore claims no GPU result.
2. **The popular index/scan answer is wrong at this scale.** Measured: the index is 2.2×
   cheaper per query but repays its build only after 288 queries, and the corpus asks 64 — so
   the scan is 2.9× cheaper end to end, and the selector picks the index anyway because the
   build cost is the caller's assertion.
3. **The selector does not amortise a preprocessing cost.** It compares per-call costs and
   leaves the amortisation to the caller's precondition. Honest, but it is a limitation and it
   is on the list.
4. **Every neuromodulatory constant in the existing subsystems is hand-picked.** Ten synergy and
   antagonism coefficients, four sets of production and decay values, five mood thresholds — no
   cited source for any of them. The wave did not revive them and did not copy their style.
5. **The five-class memory taxonomy exists in prose only.** There is no code for
   working/episodic/semantic/procedural/meta memory as such. W44 implemented the two classes
   the kernel actually needs — cases as episodic memory, procedures as procedural memory — and
   left the other three as PLANNED rather than inventing a hierarchy.
6. **There are three definitions of consolidation and five taxonomies of memory in the
   repository**, all in `matrix-core`, none reconciled.
7. **The micro-world's information gain cannot exceed 0.5**, because the outcome is two-valued.
   The metric is a comparable fraction, not a probability, and it understates the achievable
   gain.
8. **The reference method is O(2^v · m).** At 16 candidates that is cheap; what happens at 4096
   is an open research question, not a measurement.
9. **The full `matrix-core` suite was not run** — 8284 tests, and the owner rule D-017 reserves
   the full run.

---

## 6. What W45 should do, and why

Every item below is a proposal that follows from a MEASURED result in this wave, and each one
names the measurement that motivates it. The full agenda with the experiments is in
`docs-v2/research/MATRIX-COGNITIVE-RESEARCH-AGENDA.md`.

| # | Proposal | Motivated by |
|---|---|---|
| 1 | Write `scripts/w44-cognitive-journey.sh` — the real `kill -9` and restart, in the shape of the W43 journey | criterion 17 is only partially met |
| 2 | Measure how close the deterministic selector is to the best achievable choice, and publish the gap | the selector is a baseline, not a policy (§2 of the benchmark) |
| 3 | Amortise a preprocessing cost in the selection, or state the break-even in the decision | the index/scan measurement (§2.1) |
| 4 | Cap the number of simultaneously maintained hypotheses and measure the effect of the cap | the hypothesis set is currently unbounded |
| 5 | Give the drives credit assignment: a drive may bias admission only when its source is a recorded error | a drive that shifts behaviour is never shown to have improved anything |
| 6 | Make a promotion reversible: a live disagreement demotes the procedure back to its stored reference | a promoted procedure is currently never demoted |
| 7 | Move the declared worst-case bound into admission, not only the measured cost | a ledger cost is one sample from one regime |
| 8 | Price the safety guards: the fraction of correct refusals against the fraction of wrong ones | an unmeasured guard is an unpriced guard |
| 9 | Build a SECOND domain, so that "the kernel generalises" stops being an empty phrase | the only domain is the micro-world |
| 10 | Do not revive the inert biochemistry island without an executable decision procedure and a cited coefficient | ten hand-picked coefficients with no source |
