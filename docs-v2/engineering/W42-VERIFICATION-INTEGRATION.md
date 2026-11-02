# W42 — Verification of the Integration Matrix and the Mission's Acceptance Targets

**Audit date:** 2026-11-02 · **repository:** `/home/alexandr-narbaev/Projects/agi`
**Branch:** `develop` · **HEAD at audit start:** `6ea07101` ("WAL: The false-premise detector exists, it does not work in Russian")
**Auditor:** `integrator` (read-only; the only file written is this one)

---

## 0. Method, and what was actually executed

The mission forbids claiming integration without a verified production path, and
forbids invented test results. So every row below is backed by a command that
was run or a `file:line` that was read. The commands are listed once here and
referenced by name afterwards.

| # | command / action | result |
|---|---|---|
| C1 | `python3 scripts/integration-census.py` (the matrix's own census) | used as a cross-check only — see C2 |
| C2 | `python3 /tmp/census_all.py <Class…>` — a census over **all 14** `*/src/main/java` trees + `pilots/*/src/main/java` (1 339 production `.java` files, 1 329 test files), counting **production mentions, `new <Class>(` constructions (qualified names included), production call files, and test files** — each component's own file excluded | the primary evidence for every "integrated / disconnected" verdict below |
| C3 | `grep -rn "<name>" --include="*.java" */src/main/java pilots/*/src/main/java` | method-level caller census for the rows that name a method |
| C4 | `python3` + `json` parse of the three benchmark files | exact item counts (see §2) |
| C5 | `scripts/formal-gate.sh` (`JAVA_HOME=/home/alexandr-narbaev/jdk25`) | **14 passed, 0 failed**, exit 0 |
| C6 | `scripts/quality-gate.sh` | **263 candidates vs. accepted baseline 270 → PASS, exit 0** |
| C7 | `python3 scripts/mutation-harness.py` | **32 mutants, 30 KILLED, 2 SURVIVED, exit 0** |
| C8 | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.benchmark.*" --tests "io.matrix.knowledge.RetentionExperiment*" --tests "io.matrix.knowledge.TeachBackExperiment*" --offline` | **exit 0**; 28 tests, 0 failures |
| C9 | `./gradlew :matrix-core:quarkusRun --offline` (live app) → `scripts/local-alpha-checklist.sh` (12 steps) | **every assertion held, exit 0** |
| C10 | `kill -9 <pid>` of the app's own JVM → restart → `scripts/local-alpha-restart.sh` | **every assertion held, exit 0** (13 concepts / 19 representations / 24 claims survive) |
| C11 | a `/tmp` Java harness against the built `matrix-core` classes (`BenchmarkRunner`, `TypoBenchmarkRunner`, `DialogueCase`) | the numbers in §2 |
| C12 | `curl` against the live app on `127.0.0.1:9091` | the live provenance, metrics and experiment readings |

**Two limits of the audit, stated up front.**

1. **The working tree moved under the audit.** The owner had uncommitted
   modifications to `PremiseChecker.java`, and later to `KnowledgeRetriever.java`
   and `TextProfiles.java`. Every measurement below is therefore reported
   twice where it matters: once at **pure HEAD** (the owner's `PremiseChecker`
   change compiled into a separate directory and prepended to the classpath)
   and once against the **working tree**. The HEAD-only reading is the one
   that is attributable to `6ea07101`. **The owner committed those changes
   during the audit — HEAD is now `c49ae2c7` ("WAL: The journey is re-verified
   on the live path after this wave's changes"). Every measurement in this
   report was taken *before* that commit; re-running them at `c49ae2c7` is the
   obvious next step and is not claimed here.**
2. **The matrix's own census script is narrower than the mission requires.**
   `scripts/integration-census.py:65-72` lists only six source roots
   (`matrix-core`, `matrix-brain-runtime`, `matrix-api-gateway`, `matrix-audit`,
   `matrix-ethics` — which does not exist — and `matrix-core/src/main/resources`).
   It omits `matrix-billing`, `matrix-observability`, `matrix-operator`,
   `matrix-quality`, `matrix-sdk-java`, `matrix-spigot`, `matrix-tools-distill`
   and the three `pilots/*` modules. **Every verdict below was therefore
   produced with C2, not with the census.** The census is a screen, not a
   verdict, and it is a screen with holes.

---

## 1. MATRIX ROW VERIFICATION

One row per matrix entry. `claimed` is the status cell of
`docs-v2/engineering/W42-INTEGRATION-MATRIX.md`; `verified` is what the code
supports. **★** marks a row whose status is wrong.

### 1.1 §2 — Knowledge and reasoning (`io.matrix.knowledge`, product A)

| # | matrix row | claimed | verified | evidence |
|---|---|---|---|---|
| 1 | `KnowledgeStore` (SQLite, v8) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:90` `store = new KnowledgeStore(Paths.get(databasePath));` — 1 production construction (C2) |
| 2 | `KnowledgeImporter` (curated pack) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `KnowledgeWorkbench.java:106` `new KnowledgeImporter().importResource(packFor(workspaceId))` (C2) |
| 3 | `KnowledgeRetriever` (IDF coverage) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** (line stale) | constructions at `KnowledgeWorkbench.java:229`, `KnowledgeGapDetector.java:32`, `ReasoningEngine.java:115`, `KnowledgeConsolidator.java:231` (C2) |
| 4 | `ReasoningEngine` (§6.1 pipeline) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** (line stale) | `KnowledgeWorkbench.java:1454` `new ReasoningEngine().reason(snapshot, question, level)` — the matrix cites `:566`, which is now a javadoc line |
| 5 | `EntailmentChecker` | `INTEGRATED` | **CONFIRMED** | `ReasoningEngine.java:80` `this(new EntailmentChecker(), new ContradictionDetector())` (C2) |
| 6 | `ContradictionDetector` | `INTEGRATED` | **CONFIRMED** | `ReasoningEngine.java:80` and `KnowledgeWorkbench.java:1478` `new ContradictionDetector().detect(snapshot)` (C2) |
| 7 | `Derivation` / `DerivationStatus` | `INTEGRATED` | **CONFIRMED** | the derivation is now **written**: `KnowledgeWorkbench.java:1473` `store().recordDerivation(new Derivation(...))` — see row 21 |
| 8 | `SupportScore` (decomposed, disclosed) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** (line stale) | constructed at `KnowledgeRetriever.java:509`; the matrix cites `:216`, which today is a comment about the pinned W41 score |
| 9 | `LearningGoal` / `GoalManager` | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `KnowledgeWorkbench.java:1496` `new GoalManager(store()).goalsWithSubtasks(workspaceId)`; `GoalManager.java:52` constructs the goal (C2) |
| 10 | `AssessmentItem` / `Dataset` / `Outcome` | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED, with a naming caveat** | `AssessmentItem` constructed at `KnowledgeStore.java:1564`, `AssessmentDataset.java:127`, `AssessmentGenerator.java:175` (C2). **There is no class named `Dataset` in `io.matrix.knowledge`** — the real types are `AssessmentDataset` (`AssessmentDataset.java:127`) and `Outcome` (the assessment outcome, `KnowledgeStore.java`); the matrix row names a class that does not exist |
| 11 | `LearningStateEvaluator` / `CapabilityEvaluator` | `INTEGRATED` | **CONFIRMED** | `KnowledgeWorkbench.java:1080` `new LearningStateEvaluator(dataset.items())`; `KnowledgeWorkbench.java:1167` `CapabilityEvaluator.evaluate(...)` (C2) |
| 12 | `AcceptanceModel` (Tsetlin+WiSARD) | `INTEGRATED` | **CONFIRMED** | `KnowledgeWorkbench.java:1201` `new AcceptanceModel(dataset.items(), new Random(42))`; the WiSARD producer at `AcceptanceModel.java:151` `new WisardProducer(...)` (C2) |
| 13 | `TeachingComposer` | `INTEGRATED` | **CONFIRMED** | `KnowledgeWorkbench.java:1111` `new TeachingComposer(snapshot, dataset(workspaceId))` (C2) |
| 14 | `TeachBackEvaluator` (legacy) | `INTEGRATED` | **CONFIRMED** | `KnowledgeWorkbench.java:1443` `new TeachBackEvaluator().run(...)` (C2) |
| 15 | `TeachBackExperiment` (rebuilt) | `INTEGRATED` — *"no UI caller"* | **CONFIRMED (status), the "no UI caller" still holds** | `KnowledgeWorkbench.java:1546` `TeachBackExperiment.run(...)` (C2); the matrix's UI-caller census (C2 on `index.html`) confirms no UI reference to `/teach-back` |
| 16 | `StructuredLearner`, `Calibration` | `INTEGRATED` | **CONFIRMED, transitively** | `TeachBackExperiment.java:102` `new StructuredLearner(List.of(), snapshot.evidence())`; `TeachBackExperiment.java:131` `new Calibration.Forecast(...)` — reachable only through row 15 (C2) |
| 17 | `TextFeatures` (tokenizer, stopwords, fold, IDF) | `INTEGRATED` **— and untested** | **★ WRONG → `INTEGRATED` + `TESTED`** | **C2 finds 4 test files mentioning the class**: `matrix-core/src/test/java/io/matrix/knowledge/TextFeaturesTest.java`, `MultilingualTextTest.java`, `TermsDiagTest.java`, `AssessmentGeneratorTest.java`. The claim *"zero tests anywhere in the repository"* is **false at HEAD**. The English-only part of the claim is also superseded: the Russian stopwords, the case-ending fold and the language detection landed in W42.3 (`TextProfiles`, `BilingualLexicon`) |
| 18 | `QuestionSimilarity` | `DISCONNECTED` | **CONFIRMED** | C2: **0** production files mention the class outside its own file; 1 test file only (C2) |
| 19 | `KnowledgeGapDetector.missingPrerequisites` | `DISCONNECTED` — *"no callers"* | **★ WRONG → `INTEGRATED`** | **C3:** `matrix-core/src/main/java/io/matrix/knowledge/ExerciseSelector.java:196` `if (!detector.missingPrerequisites(concept.id()).isEmpty())` and `matrix-core/src/main/java/io/matrix/knowledge/LearningPlanner.java:136` `if (gaps.missingPrerequisites(conceptId).isEmpty()`. Both owners are live: `ExerciseSelector` is constructed at `KnowledgeWorkbench.java:659,675` and `LearningPlanner` at `KnowledgeWorkbench.java:1061` |
| 20 | `KnowledgeStore.goals(workspaceId)` | `DISCONNECTED` — *"no callers at all"* | **★ WRONG → `INTEGRATED`** | **C3:** `KnowledgeStore.java:2080` `List<LearningGoal> goals = goals(workspaceId);` inside `goalsWithSubtasks`, which is called at `KnowledgeStore.java:2079-2080`, `GoalManager.java:139,150,218`, `KnowledgeWorkbench.java:1060,1496,1612` — and `LearningWorkbenchResource.java:342` `return workbench.goals(workspaceId);` is the REST path |
| 21 | `KnowledgeStore.recordContradiction` | `DISCONNECTED` — *"the contradiction table is always empty ⇒ GET /contradictions always returns []"* | **★ WRONG → `INTEGRATED`** | **C3:** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1479` `store().recordContradiction(conflict);` — a production call site. **The derived claim is false**: the contradiction table is no longer always empty |
| 22 | `KnowledgeStore.recordDerivation` | `DISCONNECTED` — *"test-only ⇒ stored derivations stay UNDECIDED"* | **★ WRONG → `INTEGRATED`** | **C3:** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1473` `store().recordDerivation(new Derivation(derivation.id(), derivation.workspaceId(), …));` — a production call site. **The derived claim is false**: the derivation's status is now computed *and* written |
| 23 | `GoalManager.failAttempt` | `DISCONNECTED` | **CONFIRMED** | C3: the only production hit is the declaration `GoalManager.java:104`; 2 test hits; no REST endpoint |
| 24 | `AcceptanceModel.distilledRule()` | `DISCONNECTED` | **CONFIRMED** | C3: only the declaration `AcceptanceModel.java:251`; 2 test hits |
| 25 | `LearningStage.of/atLeast`, `MasteryCriteria.exploratory`, `LearningGoal.remainingSubtasks` | `DISCONNECTED` | **★ PARTLY WRONG → `INTEGRATED`** | C3: `LearningStage.of` (`LearningStage.java:45`), `LearningStage.atLeast` (`:57`) and `MasteryCriteria.exploratory` (`MasteryCriteria.java:32`) have **no production caller** — those two are confirmed `DISCONNECTED`. **But `LearningGoal.remainingSubtasks` is called at `matrix-core/src/main/java/io/matrix/knowledge/LearningPlanner.java:151` `List<LearningGoal.Subtask> open = goal.remainingSubtasks();`**, and `LearningPlanner` is constructed in production at `KnowledgeWorkbench.java:1061`. The method's own javadoc at `LearningPlanner.java:20` still claims it is *"written and never called"* — that comment is stale too |
| 26 | `LearningDimension.RETENTION/TEACHING/PROCEDURE/SYNTHESIS` | `UNVERIFIED` | **CONFIRMED** | the live `/metrics` endpoint (C12) reports only `RECALL`, `CALIBRATION`, `RETENTION` for `c-photosynthesis-4d4bb673` — `TEACHING`, `PROCEDURE` and `SYNTHESIS` appear nowhere in the output |
| 27 | `AssessmentItem.difficulty` | `DISCONNECTED` — *"stored, never read by any evaluator"* | **CONFIRMED as stated** | C3: written at `KnowledgeStore.java:1542`, read back at `KnowledgeStore.java:1568`, surfaced at `LearningWorkbenchResource.java:835` `item.put("difficulty", selection.item().difficulty());`. It is read and served, but **no evaluator consumes it** — the matrix's narrow claim stands |
| 28 | `KnowledgePack.notes` | `DISCONNECTED` — *"curation provenance is dropped at import"* | **CONFIRMED** | `KnowledgePack.java:27-28` declares the field; **C3 finds zero reads of it in `KnowledgeImporter.java`** (no `notes` match in the importer at all) |
| 29 | `SimpleKnowledgeBase` (in-memory .md/.txt) | `PRODUCTION_PATH_VERIFIED` — brain path | **CONFIRMED** | 13 production constructions (C2), e.g. `matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java:83`, `matrix-core/src/main/java/io/matrix/learning/ConversationLearner.java:107` |
| 30 | `KnowledgeGraphStore` (in-memory graph) | `TESTED` — `DISCONNECTED` | **CONFIRMED** | 1 production construction at `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeGraphProjector.java:54`; 9 test files (C2). It is not on the `KnowledgeStore` path |

### 1.2 §3 — Cognition and memory

| # | matrix row | claimed | verified | evidence |
|---|---|---|---|---|
| 31 | `HdcEncoding` (encode, hamming, similarity) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | 11 production call files (C2): `CodebookMemory.java:79`, `HdcBrain.java:133`, `HdcBinding.java:48`, `KnowledgeStore.java:1402`, `QuestionSimilarity.java:84` and others |
| 32 | `CodebookMemory` (10 000-entry LRU) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java:101` `new CodebookMemory(10000)` (C2) |
| 33 | `HdcBrain.forward/learn` | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `BirBrainCycle.java:100` `new HdcBrain(10000, rng)` (C2) |
| 34 | `HebbianUpdater` accumulator | `INTEGRATED` but **inert** | **CONFIRMED** | `HdcBrain.java:174` writes it; the matrix's claim that nothing in production reads it still holds for the *accumulator*; the knowledge domain's own copy is a different class (`io.matrix.knowledge.HebbianBindings`) and IS read — see row 39 |
| 35 | `HdcBinding.bind/unbind` | `DISCONNECTED` | **CONFIRMED (with a note)** | `HdcBrain.bindRecord` has **no production caller** (C3) — correct. The operators are, however, called from two `src/main` *experiment* classes — `matrix-core/src/main/java/io/matrix/neuron/SyntheticGrammarExperiment.java:184` and `matrix-core/src/main/java/io/matrix/neuron/CrossModalPaired.java:85,104,128` — which are not reachable from any entry point. The verdict `DISCONNECTED` is right; the matrix's *"no callers"* wording is not literally true |
| 36 | `ConsciousBrain` | `DISCONNECTED` | **CONFIRMED** | C2: **0** production constructions outside its own file; 24 test files; the single production reference `CognitiveGenesisProfileBuilder.java:11` is an import of the nested record, as the matrix says |
| 37 | `CognitiveProcessor` | `DISCONNECTED` | **CONFIRMED** | 1 production construction, `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java:444` — inside the disconnected `ConsciousBrain` (C2) |
| 38 | `CognitiveAttention` / `ProfileKVCache` / `CognitiveSlidingWindow` | `DISCONNECTED` | **CONFIRMED** | `CognitiveProcessor.java:80,62,63` — the only production constructions, inside the disconnected `ConsciousBrain` (C2) |
| 39 | `HebbianBindings` (the knowledge domain's) | `INTEGRATED` — decay consumed by the retrieval | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java:591` `HebbianUpdater.State fresh = HebbianBindings.stateFor(snapshot, claim.conceptId());` and `KnowledgeWorkbench.java:806` (C2) |
| 40 | `CognitiveErrorStream` | `INTEGRATED` | **CONFIRMED** | constructed at `matrix-core/src/main/java/io/matrix/knowledge/HebbianBindings.java:333` — and `HebbianBindings` is on the `KnowledgeRetriever` path (row 39) (C2) |
| 41 | `ErrorDrivenLearner.applyDecay` | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:994` `io.matrix.cognitive.ErrorDrivenLearner.applyDecay(state, stream, …)` (C3) |
| 42 | `FreeEnergyLoss` / `ConsciousBrain.freeEnergy` | `DISCONNECTED` | **CONFIRMED** | 1 production call file, `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java:500` — inside the disconnected `ConsciousBrain` (C2) |
| 43 | `TwoStageConsolidator` | `DISCONNECTED` | **CONFIRMED** | 1 production call file, `ConsciousBrain.java:91` — inside the disconnected `ConsciousBrain` (C2) |
| 44 | `HierarchicalMemory` L0 eviction | `INTEGRATED` but **ineffective** | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/brain/DefaultBrainPipeline.java:152` writes `HierarchicalMemory.Level.L2_MODULE`; `HierarchicalMemory.java:184` calls the private `evictLowestLevel()` and `:376` defines it — only L0 is evicted (C3) |
| 45 | `HierarchicalMemory.evictExpired` | `DISCONNECTED` | **CONFIRMED** | C3: only the declaration `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:338`; 2 test hits; 0 production callers |
| 46 | `PersistentHdcStore.vectors` | `PRODUCTION_PATH_VERIFIED` — **unbounded** | **CONFIRMED** (line stale) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentHdcStore.java:40` `private final Map<String, BitSet> vectors = new ConcurrentHashMap<>();` — no bound; the accessor is at `:136`. The matrix cites `:41` |
| 47 | `TombstoneService` (5 backends) | `INTEGRATED` in the privacy path, `DISCONNECTED` from memory | **CONFIRMED** | constructions at `matrix-core/src/main/java/io/matrix/api/BotEthicsResource.java:69`, `AuditLogResource.java:59`, `CascadeResource.java:52` (C2); no sleep/memory-path caller |
| 48 | `SqliteMemoryBackend.compact` | `PRODUCTION_PATH_VERIFIED` — **a hard DELETE** | **CONFIRMED** (line stale) | `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:189` `public int compact(double maxImportance, long maxAgeMs)` — the matrix cites `:156` |

### 1.3 §4 — Agents and planning

| # | matrix row | claimed | verified | evidence |
|---|---|---|---|---|
| 49 | `TrueMindCycle` (10-stage BRC) | `PRODUCTION_PATH_VERIFIED` (product B) | **CONFIRMED** | `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:129` `new TrueMindCycle(new java.util.Random(42L), hdcStore)` (C2) |
| 50 | `PlanningStage` | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:304` `new ArithmeticStage(new io.matrix.brain.runtime.stages.PlanningStage()).tryEvaluate(input, trace);` (C2) |
| 51 | `MctsTree` | `PRODUCTION_PATH_VERIFIED` — **degenerate** | **CONFIRMED** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java:103` `MctsTree tree = new MctsTree(root, random, 4, budget.simulationDepth, …)` (C2) |
| 52 | `BirBrainCycle` | `PRODUCTION_PATH_VERIFIED` — **trace only** | **CONFIRMED** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:149` `this.brain = new BirBrainCycle(rng);` (C2) |
| 53 | Runtime BRC trace (`io.matrix.brain.runtime.BrcStep`) | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED** | 12 production call files (C2): `TrueMindCycle.java:175`, `SaliencyStage.java:51`, `PlanningStage.java:188`, `ReflexStage.java:30`, `SignalStage.java:41`, `HdcRetrievalStage.java:309`, `TsetlinStage.java:58`, `ModulatorStage.java:75`, `ArithmeticStage.java:78`, `AnalogyStage.java:76` and others |
| 54 | `AgentLoop` | `DISCONNECTED` | **CONFIRMED** | C2: **0** production constructions outside its own file |
| 55 | `ReActAgentLoop` | `DISCONNECTED` | **CONFIRMED** | C2: exactly 1 production construction — `matrix-core/src/main/java/io/matrix/agent/AgentLoop.java:160` `return new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory, …)` — inside the unreachable `AgentLoop`. The two construct each other and neither is reached |
| 56 | `TaskScheduler` | `DISCONNECTED` | **CONFIRMED** | C2: 0 production constructions; 14 test files |
| 57 | `MultiAgentLoop` | `DISCONNECTED` | **CONFIRMED** | C2: **0** production mentions outside its own file; 2 test files |
| 58 | `MCTSPlanner` | `DISCONNECTED` | **CONFIRMED** | C2: 0 production constructions; 2 test files |
| 59 | `WeightedVoting` | `DISCONNECTED` | **CONFIRMED** | C2: 1 production construction — `matrix-core/src/main/java/io/matrix/consensus/ConsensusEngine.java:60` — inside the `ConsensusEngine`, whose only live owner is the CLI self-check (row 60) |
| 60 | `ConsensusEngine` | `INTEGRATED` in a CLI self-check only | **CONFIRMED** | C2: 4 production constructions — `matrix-core/src/main/java/io/matrix/SystemDemo.java:122`, `noosphere/GlobalMediator.java:54`, `consensus/ConsensusBenchmark.java:201`, `agent/MultiAgentLoop.java:321`. `GlobalMediator` is constructed **only in a test** (`GlobalMediatorTest.java:18`) and `MultiAgentLoop` is itself disconnected, so the CLI self-check is indeed the only live owner |
| 61 | Core BRC chain (`io.matrix.reasoning.BrcChain`) | `DISCONNECTED` | **CONFIRMED** | `matrix-core/src/main/java/io/matrix/reasoning/BrainLoopService.java:97` `BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));` — an empty step list (C2) |
| 62 | `GpuKernelEngine` | `DISCONNECTED` | **CONFIRMED** | C2: **0** production mentions outside its own file; 2 test files |
| 63 | `ModelToMatrix` | `INTEGRATED` (transitively, W42.9) — *"used by the live distillation ledger"* | **★ WRONG → `DISCONNECTED`** (already corrected by the Lead) | C2: **0** production files mention the class outside its own file; the only construction anywhere is in a test — `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DistillationFactoryIntegrationTest.java:23` `private static final ModelToMatrix PIPELINE = new ModelToMatrix();`. **The dependency runs the other way**: `DistillationLedger` is a passive NDJSON sink, and `ModelToMatrix` writes *into* it (`ModelToMatrix.java:149` `ledger.record(new DistillationLedger.Entry(…))`). The live `TrueDistillationFactory.java:148` records into the ledger **without** `ModelToMatrix` |
| 64 | `DistillationLedger` (brain-runtime) | `INTEGRATED` (product B) | **CONFIRMED** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueDistillationFactory.java:148` `ledger.record(new DistillationLedger.Entry(…))` (C2) |
| 65 | `CreditLedger` (brain-runtime), `LicenseManager` | `DISCONNECTED` | **CONFIRMED** | C2: the **brain-runtime's** `CreditLedger` and `LicenseManager` have 0 production mentions outside their own files (the `matrix-billing` `CreditLedger` at `BillingModule.java:31` is a different class, as the matrix says) |
| 66 | `AuditChain` (brain-runtime) | `INTEGRATED` (W42.9) | **CONFIRMED** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EpisodicLog.java:181` `this(logPath, new AuditChain());` and `RealAuditService.java:20` `new AuditChain()` (C2) |
| 67 | `FederationRegistry` (brain-runtime, the shadow) | `DISCONNECTED` | **CONFIRMED** | C2: the brain-runtime copy has 0 constructions outside its own file. The gateway's own `FederationRegistry` is constructed at `matrix-api-gateway/src/main/java/io/matrix/api/resource/FederateResource.java:34` — a different class, as the matrix says |
| 68 | `SleepConsolidator` (brain-runtime) | `DISCONNECTED` — mechanism integrated | **CONFIRMED** | C2: **0** production mentions outside its own file; 3 test files |
| 69 | `RealGpuKernelEngine`, `RealSleepScheduler`, `RealInboxWatcher`, `io.matrix.goals.GoalTracker` | `PRODUCTION_PATH_VERIFIED` (product B) | **CONFIRMED** | `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:277` `new io.matrix.brain.runtime.RealGpuKernelEngine(gpuEnabled)`, `:258` `new io.matrix.brain.runtime.RealSleepScheduler(…)`, `:286` `new io.matrix.brain.runtime.RealInboxWatcher(…)`; `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:41` `this.goals = new GoalTracker();` (C2) |

### 1.4 §5 — The application surface (product A)

| # | matrix row | claimed | verified | evidence |
|---|---|---|---|---|
| 70 | REST surface | `PRODUCTION_PATH_VERIFIED` — *"36 endpoints"* | **★ STALE COUNT → 52 endpoints** | `grep -cE "@(GET\|POST\|PUT\|DELETE)" matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java` → **52**; `grep -c "@Path("` → 53 (the class root + 52). The matrix's "36" is out of date. Status unchanged; **the evidence number is wrong** |
| 71 | UI | `PRODUCTION_PATH_VERIFIED` — *"9 tabs, 10 buttons"* | **★ STALE COUNTS → 13 tabs, 26 buttons** | `grep -c "data-tab=" matrix-core/src/main/resources/META-INF/resources/workbench/index.html` → **13** (`dashboard, ask, reasoning, knowledge, corpus, graph, experiments, assess, teach, progress, sessions, goals, persistence`); `grep -c "<button"` → **26**. The matrix's own comment in the code (`index.html:422-424`) still says *"three of the nine tabs"* — the code comment is stale too |
| 72 | UI ↔ REST | `INTEGRATED` — *"10 of 36 endpoints have no UI caller"* | **★ STALE COUNT → 15 of 52** | A path-by-path comparison of the 52 `@Path` values in `LearningWorkbenchResource.java` against the 38 endpoint strings in `index.html` leaves **15 with no UI reference**: `/evidence, /goals/resumable, /dialogue/classify, /retention-experiment, /typo-benchmark, /validate, /integrate-errors, /exercises, /gaps, /outcomes, /export, /transfer, /learned-rule, /acceptance-model, /teach-back`. The matrix's two named examples still hold (`/goals/resumable` uncalled; `/teach-back-experiment` no longer exists — it is `/teach-back` now) |
| 73 | Dashboard first paint | `DISCONNECTED` — *"index.html:938 calls only loadStatus()"* | **CONFIRMED (line stale → 1311)** | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html:1311` is the last statement of the script and is exactly `loadStatus();`. `loadMetrics()` (`:441`) and `loadRules()`/`loadKnowledge()` are called only from the tab handler (`:425-437`) or the workspace-change handler (`:497-501`). The metrics and revisions boxes stay empty on first paint |
| 74 | Persistence | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED, live** | C10: `kill -9` of the app's own JVM → restart → `scripts/local-alpha-restart.sh` → *"the concepts survive the kill"*, *"the imported account is still there"*, *"the imported representation survives"*, *"the claims survive"* — 13 concepts, 19 representations, 24 claims. Exit 0 |
| 75 | **Backup instruction** | *"VERIFIED_NOT_A_DEFECT is FALSE — this is a data-loss defect"*, `KnowledgeWorkbench:727-728` | **★ WRONG → the defect is FIXED** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1652-1661` now writes `backupState` = *"none: the store is a single SQLite database in WAL mode, so a backup is THREE files, not one. Copy workbench.sqlite, workbench.sqlite-wal and workbench.sqlite-shm together…"* and `backupFiles` = the three names. The live UI renders it (`index.html:510`). The WAL on this machine is real: `matrix-core/data/knowledge/workbench.sqlite` = 487 424 B, `workbench.sqlite-wal` = 2 871 672 B — **5.9×**. **Finding F8 and the matrix row are both stale** |
| 76 | Local autonomy | `PRODUCTION_PATH_VERIFIED` | **CONFIRMED with a caveat** | The app started and served the full 12-step checklist with no PostgreSQL, no Redis, no Kafka and no API key (C9). **But** `matrix-core/src/main/resources/application.properties` still declares `redis.uri=${REDIS_URI:redis://localhost:6379}` and `quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_ENDPOINT:http://localhost:4317}`, and the live log shows a Kafka producer retrying `localhost:9092` in a loop. Nothing is *required*; the app starts and works — but the process is not silent |
| 77 | Configuration surface | `PARTIALLY` — *"50 matrix.* keys in code; 3 in application.properties"* | **★ STALE COUNTS → 45 keys; 7 in application.properties** | `grep -rhoE 'name\s*=\s*"matrix\.[a-zA-Z0-9_.-]+"' --include="*.java" matrix-core/src/main/java \| sort -u \| wc -l` → **45**; `grep -c "^matrix\." matrix-core/src/main/resources/application.properties` → **7**. The misspelling is fixed. `PARTIALLY` is still the right status |
| 78 | **Auto-trainer (not a matrix row — recorded because it decides acceptance row 1)** | — | **the startup blocker** | `matrix-core/src/main/java/io/matrix/imports/AutoTrainer.java:44` `@ConfigProperty(name = "matrix.auto-train.enabled", defaultValue = "true")` and `:56` `void onStart(@Observes StartupEvent ev) { if (!enabled) return; … }` — the training runs **synchronously on the `StartupEvent`** and there is **no `matrix.auto-train.*` key in `application.properties`** (grep: no match), so the default path is the blocking one |

### 1.5 The mission's §13.1 families — the per-class production-caller census

The mission's §13.1 names HDC, BIR, Tsetlin, WiSARD, RAG, MCTS and the
attention family. The W42 backlog (item 5.1) records *"most have a test count
and no baseline"*. This census is the countable half of that claim — it is
**not** a baseline, and it does not measure any quality of these subsystems.
All figures from C2.

| family / class | production files mentioning | production constructions | production call files | test files | verdict |
|---|---|---|---|---|---|
| **HDC** `HdcEncoding` | 19 | 0 (static utility) | 11 | 20 | **live** — `CodebookMemory.java:79`, `HdcBrain.java:133`, `KnowledgeStore.java:1402` |
| **HDC** `HdcBrain` | 16 | 2 | 5 | 14 | **live in the brain path** — `BirBrainCycle.java:100`; second construction `ConsciousBrain.java:90` is inside the disconnected `ConsciousBrain` |
| **HDC** `HdcBinding` | 3 | 0 | 3 | 6 | **DISCONNECTED from a product path** (only experiment classes call it) |
| **BIR** `BirBrainCycle` | 9 | 2 | 1 | 5 | **live, trace only** — `TrueMindCycle.java:149` |
| **BIR** `BirRegistry` | 10 | 2 | 1 | 16 | **live** — `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:230` `new io.matrix.bir.BirRegistry(), birPath` |
| **BIR** `BirClassifier` | 1 | 1 | 0 | **0** | **test-free and narrow** — only `matrix-core/src/main/java/io/matrix/pilot/minecraft/LearnedMinecraftPilot.java:30` |
| **Tsetlin** `TsetlinTrainer` | 10 | 2 | 1 | 18 | **live in the runtime** — `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:88` |
| **Tsetlin** `TsetlinAutomaton` | 1 | 1 | 0 | 2 | reachable only through `TsetlinTrainer.java:146` |
| **WiSARD** `WisardProducer` | 1 | 1 | 0 | 2 | **live but through one caller** — `matrix-core/src/main/java/io/matrix/knowledge/AcceptanceModel.java:151` (itself reached from `KnowledgeWorkbench.java:1201`) |
| **RAG** `BooleanRag` / `HybridBooleanRag` | 3 / 1 | 0 / 0 | 1 / 1 | 9 / 5 | **only from `matrix-core/src/main/java/io/matrix/rag/BooleanRagBenchmark.java:75,85`** — a benchmark, not a workflow |
| **RAG** `IndexedBooleanRag` | **0** | 0 | 0 | **0** | **EXISTS and untested** — no mention anywhere |
| **RAG** `CognitiveRAG` | 1 | 0 | 0 | 3 | **no production caller** — not even a construction |
| **MCTS** `MctsTree` | 4 | 1 | 1 | 11 | **live** — `PlanningStage.java:103`; degenerate (constant reward), as the matrix says |
| **MCTS** `MCTSPlanner` | 2 | 0 | 0 | 2 | **DISCONNECTED** |
| **MCTS** `LatsReflector` | 3 | 1 | 1 | 4 | **live** — `PlanningStage.java:115` |
| **attention** `CognitiveAttention` | 1 | 0 | 1 | 3 | **DISCONNECTED** — only `CognitiveProcessor.java:80`, inside the disconnected `ConsciousBrain` |
| **attention** `AttentionRouter` | 10 | 5 | 0 | 2 | **live** — `matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java:96`, `consciousness/BrainLoopService.java:44`, `BrainLoopServiceV2.java:38` |
| **attention** `CognitiveGroupedQueryAttention` | 1 | 1 | 0 | 4 | **DISCONNECTED** — only `ConsciousBrain.java:483` |
| **attention** `CognitiveLatentAttention` | 1 | 1 | 0 | 3 | constructed at `matrix-core/src/main/java/io/quarkus/runner/ApplicationImpl.java:217` — **a bootstrap/`main` class, not a workflow** |
| **attention** `CognitiveMultiResolutionAttention` | 1 | 1 | 0 | 2 | constructed at `matrix-core/src/main/java/io/quarkus/runner/ApplicationImpl.java:224` — same |
| **attention** `CognitiveFlashAttention` / `CognitiveLinearAttention` / `CognitiveSparseAttention` | **0** | 0 | 0 | 1 / 1 / 3 | **no production mention at all** |

**What this table says.** Of the seven §13.1 families, **HDC, BIR and MCTS have
a production path**; **Tsetlin has one in the brain runtime only**;
**WiSARD has exactly one caller** (inside `AcceptanceModel`); **RAG is a
benchmark, not a workflow**; and **the attention family is split** —
`AttentionRouter` is live, `CognitiveAttention` and four of the six variants
are not, and three of the six have **no production mention and no test at all
outside one test file**. No **quality baseline** was measured for any of them;
this table is a wiring census only, and the backlog's *"most have a test count
and no baseline"* stands for every row of it.

---

## 2. MISSION ACCEPTANCE TABLE

The mission's Part XIII acceptance table, reproduced. The mission's own §14
table is `docs-v2/engineering/RECON-W42-BENCHMARKS.md:428-447` (18 rows); the
row *"no unresolved documentation contradiction"* is the nineteenth, taken from
the mission's §13 requirement that the report must not hide what it does not
cover.

| # | acceptance row | verdict | evidence |
|---|---|---|---|
| 1 | application startup under 5 s | **PARTIALLY MEASURED — and CONTRADICTED between two runs** | **I measured** (C9): `matrix-core 1.0.0 on JVM (powered by Quarkus 3.38.3) started in 2.038s. Listening on: http://127.0.0.1:9091` (`/tmp/quarkus.log`, 16:42:15) and `started in 1.981s` (`/tmp/quarkus2.log`, 16:45:05) — both **under 5 s**, and the port served the whole 12-step checklist and the post-kill restart check. **The Lead measured** `started in 19.018s` with `-Dmatrix.auto-train.enabled=false`, and the port **not up after 180 s** with the default configuration. **The mechanism is in the code**: `AutoTrainer.java:44` `matrix.auto-train.enabled` defaults to **`true`**, `:56` `void onStart(@Observes StartupEvent ev)` runs the training **synchronously on the startup event**, and **no `matrix.auto-train.*` key exists in `application.properties`** — so the default path blocks the port. My two sub-5 s readings were taken with an already-populated `matrix-core/data/knowledge/`. **The row is not a plain MEASURED**: startup-under-5 s holds only for a pre-trained store, and the default first-run path is not pinned |
| 2 | no mandatory external service | **MEASURED** | The app started and served the full 12-step acceptance checklist (C9, exit 0) with no PostgreSQL, no Redis, no Kafka, no OTLP collector and no API key; the store is one SQLite file. Caveat recorded at row 76: `redis.uri` and the OTLP endpoint are still declared, and a Kafka producer retries `localhost:9092` in the background |
| 3 | Russian and English | **MEASURED** | C11 against the built classes: `w42-benchmark-v1` — `byLanguage={en=Group[items=28, answerable=19, correct=19, refusedCorrectly=8], ru=Group[items=6, answerable=5, correct=5, refusedCorrectly=1]}`; the CROSS_LINGUAL category is **4/4**; the live `/experiments` endpoint (C12) reports the same. The dialogue corpus is 133 EN + 35 RU (C4) |
| 4 | import TXT, MD, HTML, PDF, EPUB | **MEASURED** | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentFormat.java:62-80` resolves `.md/.markdown`, `.txt/.text`, `.html/.htm/.xhtml`, `.pdf`, `.epub`, plus `.docx` (`:77`) and `.csv` (`:80`); `DocumentExtractorRegistry.java:28-32` dispatches all seven. **The pipeline reads SEVEN formats, not the eight the §14 table claims** — `RECON-W42-BENCHMARKS.md:433` says *"the mission's §5.1 names eight formats and the pipeline reads all of them"*, which is false on both halves: `RECON-W42-BACKLOG.md:51` names **five** (TXT, Markdown, HTML, PDF, EPUB) and the pipeline reads **seven** |
| 5 | persistence 100 % after a forced termination | **MEASURED, live** | C10: `kill -9` of the app's own JVM → restart → `scripts/local-alpha-restart.sh` → *"the concepts survive the kill" / "the imported account is still there" / "the imported representation survives" / "the claims survive"*, 13 concepts, 19 representations, 24 claims. Exit 0 |
| 6 | import integrity atomic | **MEASURED** | Two independent instruments. (a) The formal gate: the mutant `KnowledgeStore-rollback-leaves-the-claims` is **KILLED** — *"TLC reported a violated invariant: ImportIsAtomic"* (C7). (b) The live checklist step 2 (ACQUIRE) asserts the import path, and step 10 (CONSOLIDATE) asserts *"the claims before: 24 \| after: 24"* with the idempotence flag true (C9) |
| 7 | provenance 100 % of promoted claims | **MEASURED** | Live `/metrics` (C12): `"evidenceBackedShare": 1.0` with `"claims": 24, "claimsWithEvidence": 24, "evidenceLinks": 25, "sources": 5`. The §14 table's absolute numbers (*"17 claims, 17 with evidence, 18 evidence links, 4 sources"*) are the pre-import state — the **share 1.000** is the claim, and it holds |
| 8 | abstention on unsupported questions | **MEASURED (0.900) with a named residual** | C11: `unanswerableCount=10, refusedCorrectly=9` → **abstention quality 0.900**; the live `/experiments` endpoint reports `"theAbstentionQuality": 0.9`. The one failure is `en-unanswer-2` *"Who invented propositional logic?"* — answered, support 0.49, and the runner itself annotates it *"the workspace answered an item it cannot support"*. **The §14 table's claim that this is "NOT a fabrication" is a judgement, not a measurement**; the measurement is 9/10 |
| 9 | ≥ 100 reproducible multilingual conversation cases | **MEASURED (168)** | C4 by JSON parse: `w42-dialogue-cases-v1.json` → `cases` array length **168**, 133 `en` + 35 `ru`, 22 categories. C11: `DialogueCase.run(...)` → `count=168, actAgreements=168` (**1.000**), `refusalsExpected=4, refusalsCorrect=2`. **BUT the answerability number in the §14 table is stale**: I measure `inScope=61, answerabilityAgreements=59` → **0.967**, not the documented **0.934** (which corresponds to 57/61) |
| 10 | ≥ 95 % intent preservation on a synthetic typo benchmark | **MEASURED (1.000, 24 of 24)** | The benchmark exists: `matrix-core/src/main/java/io/matrix/knowledge/benchmark/TypoBenchmarkGenerator.java` (seed 42), `TypoBenchmarkRunner.java`, `matrix-core/src/test/java/io/matrix/knowledge/benchmark/TypoBenchmarkTest.java`. **C11, run directly:** `TYPO cases=24 preserved=24 share=1.0 failures=0 seed=42`. C8 confirms the test passes. The §14 table's *"1.000 — 24 of 24"* is **exact** |
| 11 | retrieval measured against BM25 | **MEASURED — but the documented NUMBER is stale** | The baseline exists: `matrix-core/src/main/java/io/matrix/knowledge/benchmark/Bm25Index.java` (Robertson & Zaragoza 2009, `ln((N−df+0.5)/(df+0.5)+1)`, `k1=1.2`, `b=0.75`). **C11 at pure HEAD:** `meanBm25Overlap=0.600473137973138`. **C12 on the live server:** `"theBm25Overlap": 0.5997689079925922`. The §14 table claims **0.603** (`RECON-W42-BENCHMARKS.md:440`) and the W42.7 section (`:285`) claims 0.594. **Neither reproduces at `6ea07101`.** The row is *measured*; the *number* in the report is wrong |
| 12 | independent pre / post / delayed / transfer learning evaluation | **MEASURED, over a synthetic learner** | `matrix-core/src/main/java/io/matrix/knowledge/TeachBackExperiment.java:110` `for (String phase : List.of("pre", "post", "delayed", "transfer"))` over **disjoint** item sets (`items.stream().filter(item -> phase.equals(item.variant()))`). C8: `TeachBackExperimentTest` — 8 tests, 0 failures, including *"the phases are disjoint sets of items"* and *"the calibration of the stated confidence is measured"*. **Caveat carried forward:** the learner is a scripted rule and the sample is 7 probes; the §14 table itself records that *"transfer to a human learner"* is not measured |
| 13 | retention measured over a no-consolidation baseline | **MEASURED** | The baseline exists and is a **two-arm** experiment: `matrix-core/src/main/java/io/matrix/knowledge/RetentionExperiment.java:48-50` `enum Policy { BASELINE_ORDER, CONSOLIDATION_SELECTED }`, each arm on **its own** workspace (`:100-110`). **Live, in the checklist (C9, step 11):** *"the baseline: the sessions 10 \| the retention EXPLORATION \| the evidence 2"* vs *"the consolidation: the sessions 10 \| the retention PRACTICE \| the evidence 3"*. C8: `RetentionExperimentTest` — 5 tests, 0 failures, incl. *"the consolidation arm reaches a higher retention than the baseline"*. The §14 table's *"EXPLORATION/2 against PRACTICE/3"* is exact. **The instrument measures the selection policy under a scripted learner, not a human** |
| 14 | multi-step goal planning + recovery | **MEASURED, live** | The planner is wired: `KnowledgeWorkbench.java:1061` `plans.add(new LearningPlanner(snapshot, goal).plan())`, exposed at `LearningWorkbenchResource.java:863-864` `@Path("/workspaces/{id}/plan")`. **Live checklist steps 7 and 12 (C9):** *"the system says what to practise and why"* — *"the reason: the learner has a verified error about this concept"*, the item, the concept and the dimension are all named; and after the `kill -9` (C10) *"the imported account is still there"*. C8: `RetentionExperimentTest` and the goal persistence tests pass |
| 15 | bounded memory under long sessions | **MEASURED** | `matrix-core/src/main/java/io/matrix/knowledge/DialogueContext.java:37` `public static final int MAXIMUM_TURNS = 32;` enforced at `:105-106` `while (turns.size() > MAXIMUM_TURNS) { turns.remove(0); }`. The bound is declared **and** enforced in code |
| 16 | formal verification with no vacuous critical properties | **PARTIALLY MEASURED — one critical property IS vacuous** | C5: `scripts/formal-gate.sh` → **14 passed, 0 failed**, exit 0. C7: `python3 scripts/mutation-harness.py` → **32 mutants, 30 KILLED, 2 SURVIVED**, exit 0. **The two survivors are named in the harness's own findings:** `Consensus-evaluate-without-quorum` (the properties do not pin the quorum) and `KnowledgeAxes-conflict-forgets-what-it-recorded`, whose finding states plainly: *"The property ConflictsAreKept is **vacuous by construction**: the model has no action that removes a conflict, which is exactly the guarantee the specification makes."* So the *mission's* row — as worded *"no vacuous critical property"* — is **not met**; what is met is the weaker *"no UNEXPLAINED vacuous property"*, because the hole is recorded. **Two further notes:** (a) the §14 table claims *"27 mutants, 25 killed, 2 survived"* — the manifest now holds **32** (the LearningStateMachine and PromotionStage mutants are new, and their two specs are **untracked working-tree files**); (b) the §14 table claims *"12/12"* specifications — there are now **14** |
| 17 | mutation testing kills all critical mutants | **PARTIALLY MEASURED (30 of 32)** | C7: **32 mutants, 30 KILLED, 2 SURVIVED, 0 INVALID, 0 DRIFT**, exit 0. The two survivors are the ones above. The §14 table's *"25 of 27, 2 justified"* is **stale on both numbers** (30 of 32) |
| 18 | reproducible local build | **NOT MEASURED by this audit — the claim is not reproducible from the tree** | The §14 table claims *"two clean builds produce 2 413 byte-identical class files (the hash 8ea0474b…) and the same jar (9b17cf2e…)"*. **I did not run two clean builds** (a clean build of this repository is expensive and the mission's §16 forbids inventing the number). What I *can* say from the code: `matrix-core/build.gradle` pins Java 25, Gradle 9.6.0, the Quarkus BOM, SQLite 3.43.0.0, jsoup 1.17.2, PDFBox 3.0.3 — and the build-info file is written by hand, sorted and timestamp-free, which is what the §14 table says makes it reproducible. **The claim is asserted in prose; this audit does not confirm it, and the §14 table itself concedes the build's fragility is "not yet pinned"** |
| 19 | no unresolved documentation contradiction | **CONTRADICTED — at least eight are open** | Enumerated in §4 below. The most load-bearing: the BM25 overlap (0.603 vs. 0.6005 measured); the formal gate's specification count (12 vs. 14 measured); the mutation counts (27/25 vs. 32/30 measured); the benchmark's category table (`RECON-W42-BENCHMARKS.md:19-30`) sums to **37** while the file holds **34** and the measured table at `:51-62` sums to 34; the matrix's cited line numbers are stale in **≥ 12** rows; `w42-benchmark-v2-candidate.json` carries `benchmark.id = "w42-benchmark-v1"`, `version = 1` — a v2 file that identifies itself as v1 |

### 2.1 The benchmark item counts — exact, parsed, not grepped

`grep -o '"id"'` overcounts, because `id` appears in nested structures. The
counts below come from **parsing the JSON with `python3` and counting the item
array** (C4).

| file | item array | **count** | composition |
|---|---|---|---|
| `matrix-core/src/main/resources/benchmark/w42-benchmark-v1.json` | `items` | **34** | en 28 / ru 6; 12 categories: FACTUAL 6, MULTI_HOP 3, CONTRADICTORY 1, AMBIGUOUS 2, MISSPELLED 4, PARAPHRASE 2, CROSS_LINGUAL 4, UNANSWERABLE 4, FALSE_PREMISE 2, CAUSAL 2, TRANSFER 2, ADVERSARIAL 2 |
| `matrix-core/src/main/resources/benchmark/w42-benchmark-v2-candidate.json` | `items` | **65** | en 34 / ru 31; FACTUAL 9, MULTI_HOP 5, CONTRADICTORY 1, AMBIGUOUS 2, MISSPELLED 10, PARAPHRASE 6, CROSS_LINGUAL 10, UNANSWERABLE 10, FALSE_PREMISE 4, CAUSAL 4, TRANSFER 2, ADVERSARIAL 2. **Its metadata block still says `benchmark.id = "w42-benchmark-v1", version = 1`** |
| `matrix-core/src/main/resources/benchmark/w42-dialogue-cases-v1.json` | `cases` | **168** | en 133 / ru 35; 22 categories |

**C11, run on the v2 candidate:** `V2 items=65 answerable=47 answeredCorrectly=47
unanswerable=18 refusedCorrectly=16 meanBm25Overlap=0.545622466116834` —
accuracy **1.000**, abstention **0.889**, BM25 overlap **0.546**. The v2
candidate is **not mentioned anywhere** in `RECON-W42-BENCHMARKS.md` or
`W42-INTEGRATION-MATRIX.md`, and the file that carries it is named
`*candidate*` — so the mission's *"the benchmark grows"* item is **provisioned
but not adopted**.

---

## 3. CORRECTIONS REQUIRED

Status values in `docs-v2/engineering/W42-INTEGRATION-MATRIX.md` that are
wrong, with the corrected value.

| # | matrix row | wrong status | corrected status | the one-line reason |
|---|---|---|---|---|
| C1 | `TextFeatures` | `INTEGRATED` **— and untested** ("zero tests anywhere in the repository") | `INTEGRATED` **and `TESTED`** | `TextFeaturesTest.java`, `MultilingualTextTest.java`, `TermsDiagTest.java`, `AssessmentGeneratorTest.java` — 4 test files (C2) |
| C2 | `KnowledgeGapDetector.missingPrerequisites` | `DISCONNECTED` | `INTEGRATED` | `ExerciseSelector.java:196` and `LearningPlanner.java:136` (C3) |
| C3 | `LearningGoal.remainingSubtasks` | `DISCONNECTED` | `INTEGRATED` | `LearningPlanner.java:151`; `LearningPlanner` built at `KnowledgeWorkbench.java:1061` (C3) |
| C4 | `KnowledgeStore.recordContradiction` | `DISCONNECTED` (+ "the contradiction table is always empty ⇒ `GET /contradictions` always returns `[]`") | `INTEGRATED` — **and the derived claim is false** | `KnowledgeWorkbench.java:1479` (C3) |
| C5 | `KnowledgeStore.recordDerivation` | `DISCONNECTED` (+ "stored derivations stay `UNDECIDED`") | `INTEGRATED` — **and the derived claim is false** | `KnowledgeWorkbench.java:1473` (C3) |
| C6 | `KnowledgeStore.goals(workspaceId)` | `DISCONNECTED` ("no callers at all") | `INTEGRATED` | `KnowledgeStore.java:2080` → `GoalManager.java:139,150,218` → `KnowledgeWorkbench.java:1060,1496,1612` → `LearningWorkbenchResource.java:342` (C3) |
| C7 | `ModelToMatrix` | `INTEGRATED` (transitively) | `DISCONNECTED` | 0 production mentions outside its own file; the only construction is in `DistillationFactoryIntegrationTest.java:23`. The ledger is a sink, not a caller (C2) |
| C8 | **Backup instruction** | *"this is a data-loss defect"* | **FIXED — no longer a defect** | `KnowledgeWorkbench.java:1652-1661` names all three WAL files; the live UI renders it (C12). **Finding F8 is closed** |
| C9 | REST surface | *"36 endpoints"* | **52 endpoints** | `grep -cE "@(GET\|POST\|PUT\|DELETE)" LearningWorkbenchResource.java` → 52 |
| C10 | UI | *"9 tabs, 10 buttons"* | **13 tabs, 26 buttons** | `grep -c "data-tab="` → 13; `grep -c "<button"` → 26 |
| C11 | UI ↔ REST | *"10 of 36 endpoints have no UI caller"* | **15 of 52** | path-by-path comparison of the 52 `@Path` values against the 38 UI endpoint strings |
| C12 | Configuration surface | *"50 matrix.* keys … 3 in application.properties"* | **45 keys … 7 in application.properties** (status `PARTIALLY` unchanged) | the two greps in row 77 |
| C13 | `Dashboard first paint` | `DISCONNECTED`, evidence `index.html:938` | `DISCONNECTED` **unchanged**, evidence `index.html:1311` | the last statement of the script is `loadStatus();` |

**Also required (outside the status vocabulary — evidence corrections):**

- **E1.** `RECON-W42-BENCHMARKS.md:440` — the BM25 overlap **0.603** must become
  **0.6005** at `6ea07101` (0.5998 on the live store). And `:285`'s 0.594 is
  stale in the other direction.
- **E2.** `RECON-W42-BENCHMARKS.md:446` and `:124` — the mutation counts
  **27/25** must become **32/30**, and the formal-gate specification count
  **12** must become **14**.
- **E3.** `RECON-W42-BENCHMARKS.md:433` — *"the mission's §5.1 names eight
  formats and the pipeline reads all of them"* is wrong twice: the backlog
  (`RECON-W42-BACKLOG.md:51`) names **five**, and the pipeline reads **seven**.
- **E4.** `RECON-W42-BENCHMARKS.md:19-30` — the category table sums to **37**;
  the file holds **34**. The measured table at `:51-62` is the correct one.
- **E5.** `RECON-W42-BENCHMARKS.md:475` — the dialogue answerability **0.934**
  must become **0.967** (59 of 61 in scope).
- **E6.** `w42-benchmark-v2-candidate.json` — its `benchmark` block must carry
  its own id/version; it currently claims to be `w42-benchmark-v1` v1.
- **E7.** `matrix-core/src/main/resources/META-INF/resources/workbench/index.html:422-424`
  — the comment *"three of the nine tabs"* is stale (13 tabs).
- **E8.** `matrix-core/src/main/java/io/matrix/knowledge/LearningPlanner.java:20`
  — the comment *"written and never called"* about `remainingSubtasks()` is
  stale.
- **E9.** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1050-1051`
  — the comment claiming `remainingSubtasks` / `blockedSubtasks` /
  `missingPrerequisites` were *"written and never called"* is stale.
- **E10.** `scripts/quality-gate.sh` — `VIOLATIONS` is incremented at lines
  173, 187 and 217 and **never read**. The FROZEN-1 failures it reports
  (`.github/` 785 diff lines, `AGENTS.md` 14 diff lines vs. `b144563e`) **do
  not fail the gate**. A gate that cannot go red on the rule it prints is not a
  gate; the FROZEN-1 rule must be wired into the exit decision.
- **E11.** `scripts/integration-census.py:65-72` — the source-root list must
  cover all `*/src/main/java` trees (it currently misses `matrix-billing`,
  `matrix-observability`, `matrix-operator`, `matrix-quality`,
  `matrix-sdk-java`, `matrix-spigot`, `matrix-tools-distill` and `pilots/*`),
  and it points at a `matrix-ethics` module that does not exist.

---

## 4. MEASUREMENT GAPS

Acceptance criteria with **no measurement at all** — as distinct from a
measurement whose number is stale.

| # | acceptance row | state | what is missing |
|---|---|---|---|
| **G1** | **reproducible local build** (row 18) | **NOT MEASURED** | The two-clean-build comparison was **not re-run** by this audit. The claim rests on a prose assertion in `RECON-W42-BENCHMARKS.md:447` with two hashes (a class-file hash `8ea0474b…` and a jar hash `9b17cf2e…`) that **no script in the repository produces**. There is no `scripts/reproducible-build.sh`. **Nothing in the tree can regenerate or refute those two hashes.** The §14 table itself concedes the fragility is "not yet pinned" |
| **G2** | **the §13.1 families have no quality baseline** (not one of the eighteen rows, but the mission's §13.1 requirement and backlog item 5.1) | **NOT MEASURED** | The census in §1.5 counts wiring. **No family has a baseline**: no HDC capacity/accuracy curve, no BIR rule-quality measurement, no Tsetlin learning-curve, no WiSARD generalisation number, no RAG recall/precision against a corpus, no MCTS search-quality measure, no attention-family ablation. `BooleanRag`/`HybridBooleanRag` have only a benchmark caller. The backlog's *"most have a test count and no baseline"* stands for **all of them** |
| **G3** | **the startup time under the DEFAULT configuration** (row 1, the untested half) | **NOT MEASURED** | The Lead's run (19.018 s; the port not up after 180 s) and my two runs (2.038 s / 1.981 s, port up) disagree, and the difference is the auto-trainer acting on a **pre-trained vs. an empty** corpus. **The first-run startup on an empty store is not pinned by any test or script**, and `matrix.auto-train.*` is absent from `application.properties` |
| **G4** | **the invalid-signature defence** (§13.2, reported in `RECON-W42-BENCHMARKS.md:345` as `NOT MEASURED`) | **NOT MEASURED** | The knowledge packages carry **no signature**, so there is nothing to verify. Recorded honestly by the wave itself; carried forward here because it is a gap, not a pass |
| **G5** | **transfer to a human learner** (row 12) | **NOT MEASURED** | Every learning measurement in §2 is over a **scripted rule learner**. `TeachBackExperiment` and `RetentionExperiment` both say so in their own javadoc. No human has been taught by this system |
| **G6** | **the `RECON-W42-BENCHMARKS.md` §1.1 baseline table (0.583 / 0.400 / 0.593)** | **STALE, not re-measurable** | Those are the W42.0 figures. The current build is at 1.000 / 0.900 / 0.6005. The chain W42.0 → W42.1 → W42.3 → W42.5 → W42.7 → W42.9 is recorded in the document but **each link is a separate run with no committed artefact**; only the current numbers reproduce |
| **G7** | **the v2 candidate benchmark's adoption** | **NOT MEASURED as a gate** | `w42-benchmark-v2-candidate.json` (65 items) has no recorded run in any report, is not named in the matrix, and carries v1's metadata. It is provisioned but neither adopted nor rejected |
| **G8** | **the full repository test suite** | **NOT RE-RUN** | `RECON-W42-BENCHMARKS.md:123` records 164 classes / 1 473 tests for the *targeted* suites only and states the full suite "has still not been re-run" because of the OOM risk. This audit ran only the targeted suites (C8). The claim "1 119 tests must remain green" in `settings.gradle` has no current evidence behind it |

---

## 5. What the matrix gets right

A verification that only lists defects is not a verification. Three of the
matrix's strongest claims were confirmed by live measurement, not by reading:

1. **The matrix's core thesis holds.** 21 of the 30 knowledge rows and 8 of the
   19 agent rows are disconnected from a product path. Every one of them was
   re-verified by construction count (C2), not by mention. **A tested class
   with zero production callers is disconnected for the product's purposes** —
   `ConsciousBrain` (0 constructions, 24 test files), `MultiAgentLoop`,
   `GpuKernelEngine`, `SleepConsolidator`, `IndexedBooleanRag` (0 mentions
   anywhere), `MCTSPlanner`, `QuestionSimilarity`.
2. **The matrix's own warning about its census is correct and, as shown in
   §0.2, understated** — the census omits eight modules, and the two rows it
   missed in W42.7 (`ErrorDrivenLearner`, `CognitiveErrorStream`) are exactly
   the kind of stale row this audit found four more of.
3. **The live product is real.** The 12-step Local Alpha checklist passes with
   **every** assertion holding (C9, exit 0), and the state survives a
   `kill -9` (C10, exit 0). That is a stronger claim than "the class exists",
   and it holds.

---

*Report produced by a read-only audit. The only file written is this one. No
repository file, git object, or working-tree modification was touched by the
auditor; the two `kill -9` events were against the auditor's own
`quarkusRun` instance, and the runtime store it wrote
(`matrix-core/data/knowledge/`) is covered by `.gitignore:77` (`data/*`).*
