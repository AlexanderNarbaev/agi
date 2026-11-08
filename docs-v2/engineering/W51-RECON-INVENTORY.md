# W51 — RECON: inventory of the cognitive/runtime subsystems

**Wave:** W51 · **Branch:** `develop` @ `a098f283` · **Method:** read-only (`grep`/`glob`/`read`; no file modified except this report).

**Scope note.** "Production call path" here means *something under `*/src/main/java` reaches the class*. Two
serving applications exist and they are not merged: **Chain A** = `matrix-core` (Quarkus, the
epistemic/workbench application) and **Chain B** = `matrix-api-gateway` + `matrix-brain-runtime` (a
hand-written JDK `HttpServer` brain runtime). They share a classpath, not a call — see §1.

---

## 1. Chain A / Chain B vs. `io.matrix.chain` — three different things with one name

**1a. "Chain A"/"Chain B" are a serving-application boundary, not a package.** Named in code by
`matrix-core/src/main/java/io/matrix/api/CanonicalEntryPoint.java`:

| Line | Declaration |
|---|---|
| `:89-93` | `EPISTEMIC_APPLICATION` = Chain A ("the epistemic and workbench application"); `BRAIN_RUNTIME_APPLICATION` = Chain B ("the brain runtime") |
| `:96-103` | Chain A entry point: `io.matrix.knowledge.LearningWorkbenchResource` on `/v1/workbench/**`, cycle `io.matrix.cognitive.CognitiveKernel` |
| `:104-111` | Chain A's **second** serving path: `io.matrix.api.OpenAIChatResource` on `/v1/chat/completions`, cycle `io.matrix.reasoning.BrainLoopService` |
| `:112-119` | Chain B entry point: `io.matrix.api.MinimalHttpServer` on `POST /v1/analyze`, cycle `io.matrix.brain.runtime.TrueMindCycle` |
| `:120-129` | W46.0 correction: `io.matrix.brain.BirBrainCycle` lives in `matrix-core` but is a **Chain B** stage |

**Runtime bridge A↔B: none, by decision and by gate.** `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md:44-48`
records the owner's decision ("No bridge, no implicit write path, no automatic trust, no new network
surface"). The isolation is *proved*, not asserted: `scripts/w45-chain-isolation-check.sh`,
`scripts/w45-chain-isolation-selftest.sh` (negative control), `scripts/w45-chain-isolation-reachability.py`.

**1b. `io.matrix.chain` (DESIGN-21) is a third, unrelated thing** — a registry of boolean-model chains with
trigger rules, all under `matrix-core/src/main/java/io/matrix/chain/`:

- `ChainRegistry.java` — process-wide singleton (`getInstance()`, :33); `register`/`unregister`/`addRule`/
  `evaluateTriggers`/`activate`; `activate` depth cap 5.
- `ChainDescriptor.java` — record: `ChainId`, name, `BooleanChainRunner`, `dependsOn`, priority.
  `ChainId.java` — `UUID` + short name. `TriggerPredicate.java` — functional interface over `ChainEnrichedOutput`.
- `TriggerRule.java` — predicate + target + priority; `frozenShutoffDefault` (:40) is a **placeholder returning
  `false`**. `StandardPredicates.java` — novelty / consolidation / high-arousal / low-magnitude;
  `frozenShutoff()` (:62) **always returns `false`**.
- `MultiChainEnsemble.java` — consensus over N `BooleanChainRunner`s: `BYZANTINE` (strict majority per bit),
  `WEIGHTED` (magnitude-weighted, threshold 0.5), `DEBATE` (**falls back to BYZANTINE**, :140).

**Wired in production? NO.** `grep -rn "import io.matrix.chain" */src/main/java` returns only the package's
own seven files — zero production callers. Tests only: `matrix-core/src/test/java/io/matrix/research/`
`Exp341ChainRegistryTest.java`, `Exp347MultiChainConsensusTest.java`, `Exp359FinalIntegrationTest.java`,
`Exp385MasterIntegrationTest.java` + `matrix-core/src/experimental/test/.../Exp418GrandMasterIntegrationTest.java`.

**What it actually does today:** a self-contained rule registry + bit-voting ensemble over
`io.matrix.imports.BooleanChainRunner` objects, with two of its safety predicates hard-wired to `false`
and one of its three strategies unimplemented.

---

## 2. `CognitiveKernel` and `TrueMindCycle` — the two real cognitive cycles

### 2a. `io.matrix.cognitive.CognitiveKernel` (Chain A)

- **Primary:** `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java` (1005 lines).
  Public entry point: `process(CognitiveSignal)` **:287** and `process(CognitiveSignal, RequestProgressListener)`
  **:303**; both `synchronized` ("one cycle at a time per kernel", javadoc :268-282).
- **Wired in production: YES.**
  - `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:252` — `new CognitiveKernel(...)`;
    the service is `@ApplicationScoped` (:51).
  - `matrix-core/src/main/java/io/matrix/cognitive/CognitiveResource.java:80` — `service.observe(...)` from
    `POST /v1/cognitive/observe` (`CognitiveResource.java:34`, `:72-74`).
  - `CognitiveService.java:141` — `new CognitiveStore(Paths.get(workbench.databasePath()))`: the cognitive
    store opens the **same SQLite file** as the epistemic store.
- **What it does:** one 10-step cycle on a 4-input lamp micro-world — PERCEIVE (`ChannelClassifier`), RESTORE
  (rebuild the session from stored CASES), PREDICT, SELECT (algorithm), PROBE (sandbox env), OBSERVE (write case
  + evidence), ERROR, LEARN (promote procedure), REGULATE (drive state), ANSWER. Budget
  `DEFAULT_CYCLE_BUDGET_MILLIS = 250` (:96). **Honest caveat, from the code itself:** `promoteIfValidated` (:815)
  can never promote in the lamp world — the input domain is exhausted once the model settles, so the "validated
  fast path" is permanently an *in-sample derived shortcut* (comment :806-812).
- **Tests:** `matrix-core/src/test/java/io/matrix/cognitive/` — 12 classes (`CognitiveKernelTest`,
  `CognitiveConcurrencyTest`, `CognitiveCycleBudgetTest`, `CognitiveContradictionDurabilityTest`,
  `CognitiveRegulatoryPersistenceTest`, `CognitiveRequestLifecycleTest`, `CognitiveResourceTest`,
  `CognitiveServiceIdentityTest`, `CognitiveStoreIdentityTest`, `CognitiveStoreMigrationTest`,
  `CognitiveWriterLockTest`, `IdempotencyRegistryTest`) + `knowledge/ObservationLegacyBackfillTest.java`.

### 2b. `io.matrix.brain.runtime.TrueMindCycle` (Chain B)

- **Primary:** `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java` (501 lines).
  Entry point `think(String)` **:155**, `think(String, String)` **:169** (dual form: transliterated retrieval
  input + original text). Constructors :104, :109, :121.
- **Wired in production: YES** —
  `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:74` (field
  `private final TrueMindCycle mindCycle`), `:129-130` (construction, `Random(42L)`), `:332`
  (`mindCycle.think(input, original)`); `matrix-api-gateway/.../MinimalHttpServer.java` owns it.
- **What it does:** reflex-gate → harmful-intent gate → text signal → salience → `BirBrainCycle` → safety monitor.
- **Tests:** `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/{TrueMindCycleIntegrationTest,
  HarmfulIntentProductionWiringTest, TmpGateProbeTest}.java` +
  `matrix-api-gateway/src/test/java/io/matrix/api/CanonicalEntryPointChainBTest.java`.

### 2c. `io.matrix.brain.runtime.MindCycle` — the *legacy* cycle

- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindCycle.java` (327 lines), entry point
  `think(String, String)`. **Wired in production: NO.**
  `grep -rn "new MindCycle(" */src/main/java` → one hit only:
  `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindRuntimeCli.java:45` (a CLI).
  `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/CognitiveConductorGuardTest.java:100-108`
  actively **forbids** any production source from constructing it.
- `ProductionBrainClient.java:325-330` still *says* "primary path is the cognitive conductor MindCycle" — a
  **stale comment**; the field is `TrueMindCycle`. Do not trust the comment.

---

## 3. LearningWorkbench — the epistemic application

**Primary classes** (all `matrix-core/src/main/java/io/matrix/knowledge/`): `LearningWorkbenchResource.java` —
`@Path("/v1/workbench")` (:51), **58** HTTP method annotations (`grep -cE "^\s*@(GET|POST|PUT|DELETE)"` → 58;
`docs-v2/engineering/W42-*` still says 52 — stale); UI at `src/main/resources/META-INF/resources/workbench/index.html`.
`KnowledgeWorkbench.java` (2264 lines) — the domain facade. `KnowledgeStore.java` (4905 lines) — the SQLite store.
`LearningCycleResource.java` — `@Path("/v1/learning")` (:31).

**Wired in production: YES.** `matrix-core/src/main/java/io/matrix/MatrixApplication.java` is the only
`@QuarkusMain`; JAX-RS resources are auto-mounted by the Quarkus plugin (matrix-core is the only module that
applies it).

**The flow, with evidence (acquisition → claim → retrieval → answering → revision):**

1. **Acquisition** — `io/matrix/knowledge/acquisition/AcquisitionCycle.java:74` `run(workbench, ws, question, max)`
   scores the bundled **classpath corpus** against the question's uncovered terms
   (`RESEARCH_POOL = "research-pool/plate-tectonics"` :39; `MAXIMUM_DOCUMENTS = 3` :42). Entry point
   `LearningCycleResource.java:110`. **No network**: the pool ships inside the jar (`BOUNDARY` :45).
2. **Claim** — `AcquisitionCycle.java:106` → `KnowledgeWorkbench.java:763-782` →
   `acquisition/DocumentImporter.java:218` → `acquisition/ClaimExtractor.java` proposes claims →
   `KnowledgeStore.applyChanges` (transactional). `DocumentExtractorRegistry` handles PDF/DOCX/EPUB/HTML/CSV/TXT.
3. **Retrieval** — `KnowledgeWorkbench.java:401-402` builds `new KnowledgeRetriever(snapshot, …)`;
   `KnowledgeRetriever` (729 lines) scores claims by coverage. Also `KnowledgeGapDetector.java:32`,
   `PremiseAssessment.java:282`, `ReasoningEngine.java:115`.
4. **Answering** — `LearningWorkbenchResource.java:479-480` (`POST /workspaces/{id}/ask`) →
   `KnowledgeWorkbench.reason` (`:2008-2010`) → `ReasoningEngine` → `ContradictionDetector` (`:2034`).
   A question the workspace cannot cover returns a refusal naming the uncovered terms.
5. **Revision** — `AcquisitionCycle.java:174` `acquireAndRevise` → `ModelRevisionDecider.decide` →
   `KnowledgeRefiner.refine` → `store.applyChanges` (`AcquisitionCycle.java:207-208`). Manual refinement:
   `KnowledgeWorkbench.java:1086` `acceptRefinement` / `:1138` `rejectRefinement`.

**What it actually does today:** a local, single-file-SQLite, source-cited Q&A/teaching/assessment
application over **bundled** corpora (photosynthesis pack, plate-tectonics corpus); no live web retrieval.

**Tests:** `matrix-core/src/test/java/io/matrix/knowledge/` — 19 classes (`AcquisitionCycleTest`,
`CorpusManagerTest`, `CorpusVersionsTest`, `CorpusFoundationTest`, `KnowledgeRetrieverTest`,
`ReasoningEngineTest`, `LogicDomainTest`, `KnowledgeRefinerTest`, `CounterexampleCycleTest`,
`KnowledgeStoreTest`, `KnowledgeImporterTest`, `WorkspaceSnapshotTest`, `KnowledgeConsolidatorTest`,
`ErrorIntegratorTest`, `SchemaMigrationTest`, `ErrorDrivenDecayWiringTest`, `SnapshotRestoreEndpointTest`,
`WorkspaceLoadEndpointTest`, `KnowledgeClockInjectionTest`) + `knowledge/acquisition/*`.

**Docs:** `docs-v2/engineering/LEARNING-WORKBENCH.md` (406 lines) — accurate on the procedure; it quotes no
endpoint count, so it does not drift.

---

## 4. BIR (`io.matrix.bir`) — the Boolean Intermediate Representation

All under `matrix-core/src/main/java/io/matrix/bir/` (25 files + 4 converters):
- `Bir.java:18` — `public sealed interface Bir permits BirForm`: `inputBits()`, `outputBits()`, `form()`,
  `provenance()`, `fidelity()`, `contentHash()` (SHA3-256). `BirForm.java:20` — `sealed abstract class BirForm
  permits TtForm, ClauseSetForm, BddForm`.
- **Three inference forms:** `TtForm` (truth table, canonical semantics, SIMD eval), `ClauseSetForm` (Tsetlin
  clauses, unbounded arity, learnable), `BddForm` (canonical → exact equivalence).
- `BooleanRuntime.java:15` `evaluate(Bir, long[])→long[]`; `:25` `evaluateBatch`; `:35` `equivalent(Bir,Bir)` —
  the whole "runtime contract": any form executes without the caller knowing which.
- `BirRegistry.java:70` `register(id, bir, name, phi, lineageHash)`; also `getByHash`, `listAll`, `addListener`.
  In-memory `ConcurrentHashMap`; `BirRegistryPersistence` is the on-disk variant.
- Also `BirCompiler`, `BirAvroCodec`, `BirClassifier`, `BirMetrics`, `BirLimits` (`DEFAULT_MAX_LITERALS = 4096`,
  overridable by `matrix.bir.max-literals` / `MATRIX_BIR_MAX_LITERALS`), `KMaxEnforcer`, `LineageLedger`,
  `PureBirGenerator`, `DecisionTreeAdapter`, `TruthTableAdapter`, `SubstrateBackend`/`JvmSimdBackend`/`FpgaBackend`,
  `convert/{TtToBdd,BddToTt,ClauseSetToTt,TtToClauseSet}Converter`.

**Wired in production: YES — split across the two chains.**
- Chain B: `matrix-brain-runtime/.../{BirRegistryBridge, BirKnowledgeBase, DistillationPipeline,
  DistillationMerge, RuleInductionEngine, RealSleepScheduler, SuperAdditivityStudy, SymbolicSimplifier}.java`;
  plus `matrix-core/.../brain/BirBrainCycle.java` (reached from Chain B via `TrueMindCycle.java:149`).
- Chain A: `matrix-core/.../{agent/AgentBrainService, api/OpenAIChatResource, distill/Distiller,
  model/ModelRegistry, cluster/NeuronClusterActor, imports/*, memory/PersistentHierarchicalMemory,
  explainability/ExplanationGenerator, monotone/MonotoneDecoder, pilot/minecraft/LearnedMinecraftPilot}.java`.

**What it actually does today:** a clean, well-tested boolean-function IR with three interconvertible forms
and a substrate-agnostic evaluator — a *representation layer*, not an inference engine (no learning of its
own beyond the Tsetlin producer).

**Tests:** `matrix-core/src/test/java/io/matrix/bir/` — 20 classes (`BirTest`, `BirInvariantsTest`,
`BirPropertiesTest`, `BirBooleanAlgebraTest`, `BirCompilerMutationTest`, `BirRegistryTest`,
`BirRegistryPersistenceTest`, `BirAvroCodecTest`/`IT`, `LineageLedgerTest`, `KMaxEnforcerTest`, `convert/*Test`,
`DecisionTreeAdapterTest`, `TruthTableAdapterTest`, `PureBirGeneratorTest`, `JvmSimdBackendTest`,
`ClauseValueEqualityTest`, `Inv1SourceGuardTest`) + JMH `src/jmh/java/io/matrix/bir/BirEvaluateBenchmark.java`.

---

## 5. HDC — hyperdimensional computing

**Primary classes** — `matrix-core/src/main/java/io/matrix/neuron/HdcBrain.java` (10 000-bit HDC + binding table;
`:141-143` uses `HdcBinding.sequence/bind`), `HdcEncoding.java` (word/dim constants, `similarity`), `HdcBinding.java`
(bind/unbind), `HdcConditioning.java` (stimulus-from-template), `HdcAsLlmPreprocessor.java`;
`matrix-core/src/main/java/io/matrix/advanced/SparseHDC.java` (sparse variant);
`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/HdcVector.java` (packed-word Jaccard kernel, used by
`PersistentHdcStore.java:161-167`), `PersistentHdcStore.java` (NDJSON-persisted), `SparseHdcStore.java`,
`stages/HdcRetrievalStage.java`.

**Wired in production: PARTIALLY.**
- YES (Chain B): `HdcBrain` via `BirBrainCycle.java:96` (`new HdcBrain(10000, rng)`), reached from
  `TrueMindCycle.java:149`; `CodebookMemory` at `BirBrainCycle.java:101`, queried at `:182`;
  `PersistentHdcStore` at `ProductionBrainClient.java:81,129`; `HdcEncoding` at
  `matrix-core/.../cognitive/ErrorDrivenLearner.java:98,149,158` and `KnowledgeStore.java:2790`.
- NO: `io.matrix.advanced.SparseHDC` — **zero** callers in `*/src/main/java` (only
  `matrix-core/src/test/java/io/matrix/advanced/SparseHDCTest.java`).
- NO (production): `HdcRetrievalStage` — its only constructor call is
  `matrix-brain-runtime/.../MindCycle.java:100`, and `MindCycle` is not constructed in production (§2c).
  `HdcAsLlmPreprocessor` — no main callers.

**What it actually does today:** a bit-vector encoder + binding table used as a *retrieval* mechanism
(codebook lookup by cosine/Hamming similarity, floor `MIN_SIMILARITY = 0.15` at `BirBrainCycle.java:72`),
plus a second, unconnected HDC stack in `matrix-brain-runtime` (`HdcVector`/`PersistentHdcStore`) that only
the dead `MindCycle` path uses.

**Tests:** `matrix-core/src/test/java/io/matrix/neuron/{HdcBrainTest,HdcBindingTest,HdcConditioningTest,
HdcEncodingTest,HdcAsLlmPreprocessorTest}.java` + `advanced/SparseHDCTest.java`;
`matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/{HdcVectorTest,SparseHdcStoreTest,
PersistentHdcStoreIntegrationTest,HdcProjectionProbeTest}.java` + `.../stages/{HdcRetrievalStageTest,
HdcNodeServingPathTest}.java`; JMH `src/jmh/java/io/matrix/perf/HdcRetrievalBenchmark.java`.

---

## 6. Tsetlin

**Primary classes** — `matrix-core/src/main/java/io/matrix/tsetlin/`:
- `TsetlinTrainer.java` — canonical Granmo Tsetlin machine: ±1 clause pairs, `p=(T±vote)/2T` asymmetric soft
  feedback, Type Ia/Ib/II updates, decision function distilled exactly into BIR.
  - `DEFAULT_S = 4.0` (**:49**) with the measured table at :31-37 (parity-3..parity-6 × s=4..64).
  - `recommendS(int informativeBits)` (**:67**) — `2^k * 4`; explicitly labelled *"a lower bound fitted to
    measured behaviour, not a derivation"* (:60-63). `countInformativeBits` (**:84**).
  - `feedbackT = Math.max(1, nClauses / 4)` (**:135**) — the vote-saturation threshold (magic number).
  - `trainBatch(long[][], boolean[], int epochs)` (**:302**), `trainStep` (:269), `predict` (:313).
  - `toDecisionClauseSet` (:372) / `toDecisionBir` (:381) — exact distillation into BIR.
- `TsetlinAutomaton.java` — the per-literal automaton. `matrix-core/src/main/java/io/matrix/tsetlin/WisardProducer.java`
  — a WiSARD baseline (claimed ≥10× faster within 2 p.p. of Tsetlin).
  `matrix-core/src/experimental/java/io/matrix/experimental/tsetlin/AdvancedTsetlinMachine.java` —
  **experimental source set**, not in `src/main`.

**Wired in production: YES (Chain B), partially.**
- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:88` —
  `new TsetlinTrainer(16, 64, 200, new Random(seed))` (inputBits=16, clauses=64, states=200); invoked from
  `RealSleepScheduler.java:175` (sleep-phase induction).
- `matrix-core/src/main/java/io/matrix/knowledge/AcceptanceModel.java:94` — `new TsetlinTrainer(...)` in **Chain A**.
- `matrix-core/src/main/java/io/matrix/agent/AgentBrainService.java:16` imports it.
- Reading the learned function: `matrix-brain-runtime/.../graph/ClauseExplorer.java:72`, `graph/ClauseGibbsSampler.java:22`.

**What it actually does today:** a seeded, offline learner that turns boolean training rows into a
`ClauseSetForm`/BIR decision function; it runs during **sleep consolidation**, never in the request path.

**Tests:** `matrix-core/src/test/java/io/matrix/tsetlin/{TsetlinTest,TsetlinGranmoReferenceTest,TsetlinExportPropertyTest}.java`;
`matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/{ProductionTsetlinLearningTest,TsetlinFiringConsistencyTest,
TsetlinEncodingProbeTest}.java` + `.../stages/TsetlinStageSimulacrumTest.java` +
`.../graph/TsetlinReinforcementStrengthTest.java`; `matrix-core/src/experimental/test/.../Exp414AdvancedTsetlinTest.java`;
JMH `matrix-core/src/experimental/jmh/.../TsetlinClauseBenchmark.java`.

---

## 7. MPDT — and every `.c`/`.h` file in the repo

**What MPDT is, per the repo's own docs:** `docs-v2/algorithms/MPDT-GA.md`, `formal/MPDTNeuron.tla`,
`formal/MPDTNeuron.cfg`, `matrix-core/src/main/resources/avro/mpdt_neuron.avsc` — a *McCulloch-Pitts
Decision-Table* neuron (a truth table in flash / an Avro record), plus a GA variant evolving DNF clause sets.

**Java implementations (2, and they are different things):**
- `matrix-core/src/main/java/io/matrix/evolution/MpdtGaProducer.java` — **a genetic algorithm over DNF clauses**
  (pos/neg masks packed into one long, arity ≤ 20, elitism + tournament + crossover, MDL pressure λ=0.1,
  seeded `Random`). It is *not* a decision-tree neuron. Wired in production (Chain B):
  `matrix-brain-runtime/.../RuleInductionEngine.java:89` (`new MpdtGaProducer(16, 64, 100, seed)`), dispatched
  at `:251`; `RealSleepScheduler.java:175` names it in the dream notes.
  Tests: `matrix-core/src/test/java/io/matrix/evolution/{Exp002ComparisonTest, Exp002Exp003ProtocolTest}.java`.
- `matrix-core/src/main/java/io/matrix/neuron/MpdtHdcBridge.java` — hybrid MPDT+HDC decision/remember.
  **Not wired in production**: the only references are `matrix-core/src/test/java/io/matrix/neuron/MpdtHdcBridgeTest.java`.

**C implementation — YES, it exists, and it is tiny:**
- `matrix-micro/src/mpdt_neuron.h` (~120 lines) — `MPDTNeuron {uint8_t k; const uint32_t *table;}`;
  `MPDT_K_MAX 20` (:38); `static inline bool mpdt_evaluate(const MPDTNeuron*, uint32_t inputs)` (:68-73) — a
  two-bit-operation flash lookup. **`mpdt_evaluate_bool` (:85-88) is a STUB**: it ignores its varargs and
  always evaluates `mpdt_evaluate(neuron, 0)`.
- `matrix-micro/src/mpdt_neuron.c` (**16 lines**) — implements **only** `mpdt_describe` (a `snprintf`).
- `matrix-micro/tests/test_mpdt.c` — C unit test; `matrix-micro/examples/xor_example.c` — usage example;
  `matrix-fpga/test_mpdt_fpga.py` — an FPGA-side check for the same table;
  `matrix-micro/tools/{ldn2h.py, test_ldn2h.c, test_sample.ldn}` — `.ldn` → C header generator.

**Every MATRIX-owned `.c`/`.h` in the repo** (`.venv/` torch/triton headers and `matrix-web-ui/node_modules` excluded):

| File | What it is |
|---|---|
| `matrix-core/src/main/c/libtruthy/truthy.c` | native "truthy" evaluation helper |
| `matrix-core/src/main/c/libtruthy/hamming.c` | native Hamming-distance helper |
| `matrix-micro/src/mpdt_neuron.c` / `.h` | MPDT flash truth-table neuron (above) |
| `matrix-micro/examples/xor_example.c` | XOR neuron usage example |
| `matrix-micro/tests/test_mpdt.c` | C unit test for the above |
| `matrix-micro/tools/test_ldn2h.c` | C test for the `.ldn`→header generator |

**Build status:** `settings.gradle:19-21` has `// include 'matrix-fpga'`, `// include 'matrix-micro'`,
`// include 'matrix-ros2'` **commented out** — the C code is **not in the Gradle build** and is not compiled
or tested by the Java CI.

**What it actually does today:** a 16-line C file that formats a string, plus a header of inline functions
(one of which is a stub); plus a Java GA that happens to be named MPDT and does run in the sleep path. The
"MPDT neuron" as a decision tree exists only in TLA/Avro/docs.

---

## 8. Memory systems, Connectome, and the SQLite-backed stores

**Memory packages**
- `matrix-core/src/main/java/io/matrix/memory/` — 11 classes: `HierarchicalMemory` (tiered L0-L3),
  `MemoryHierarchy`, `MemoryHierarchyTier`, `ConsolidationCycle` (TR+REM over `MemoryEntry`), `PersistentMemory`
  (Avro/JSON file), `PersistentHierarchicalMemory` (`@ApplicationScoped`, JSONL flush), `SqliteMemoryBackend`,
  `RecurrentSdm`, `SdmReader`, `query/{EntityQuery,SemanticQuery}`.
- `matrix-brain-runtime/.../EpisodicLog.java` (append-only NDJSON episodic tier), `PersistentHdcStore.java`,
  `SparseHdcStore.java`, `GraphMemoryIndex.java`, `PersistentMind.java` (three tiers: EPISODIC NDJSON /
  SEMANTIC SQLite / PROCEDURAL BIR clauses — javadoc :19-35).
- `io/matrix/knowledge/` also holds the *epistemic* memory (claims, cases, evidence, learning state) inside
  `KnowledgeStore`. **No class is literally named** `EpisodicMemory`/`SemanticMemory`/`ProceduralMemory`/
  `WorkingMemory`/`WorldStateMemory`/`MetaMemory` — the tier names are vocabulary inside
  `PersistentMind`/`HierarchicalMemory`.
- **Duplicate-name hazard:** two unrelated `ConsolidationCycle` classes — `io/matrix/memory/ConsolidationCycle.java`
  (memory TR/REM) and `io/matrix/lifecycle/ConsolidationCycle.java` (route-backlog drain). The production one is
  the *lifecycle* variant: `matrix-api-gateway/.../MinimalHttpServer.java:270`.

**Connectome**
- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/Connectome.java` — a weighted directed graph
  with `propagate`, `prune`, `markPruned`, `restore`; used by `HdcNode.java:79`,
  `SleepConsolidator.java:72-76`, `ReplayPhase.java:67-75`.
- **Wired in production? NO.** Every constructor call site is inside `matrix-brain-runtime/src/test/`
  (`ConnectomeIntegrationTest`, `ConnectomePropagationTest`, `ConnectomePruneSafetyTest`);
  `SleepConsolidator`/`ReplayPhase` are themselves only reachable from `MindCycle`-era code.

**DB files and their single writer** (single-writer rule = `WriterLock`, one writer *process* per file):

| DB / file | Written by | Chain | Notes |
|---|---|---|---|
| `data/knowledge/workbench.sqlite` (default `matrix.knowledge.path`) | `io.matrix.knowledge.KnowledgeStore` (`KnowledgeStore.java:364` `jdbc:sqlite:`) **and** `io.matrix.cognitive.CognitiveStore` (`CognitiveStore.java:146`) — **same file, two connections** | A | `CognitiveService.java:141` opens the cognitive store on `workbench.databasePath()`. `WriterLock` is *per-process* (`WriterLock.java:126-131`), so the two stores join one lock. |
| `<db>.writer.lock` next to it | `io.matrix.cognitive.WriterLock` (`WriterLock.java:68` `LOCK_SUFFIX`) | A | pid + start-instant + instance-token record; refuses a second writer *process*. |
| `data/mind/mind.sqlite` (`matrix.mind.dir` / `MATRIX_MIND_DIR`) | `io.matrix.memory.SqliteMemoryBackend` (`SqliteMemoryBackend.java:40`) via `PersistentMind` | B | `MinimalHttpServer.java:199` |
| `data/mind/episodic.ndjson` | `io.matrix.brain.runtime.EpisodicLog` | B | `MinimalHttpServer.java:181` |
| `data/mind/hdc_kb.ndjson` | `PersistentHdcStore` | B | `MinimalHttpServer.java:148` |
| `data/mind/bir.ndjson` | `io.matrix.brain.runtime.BirKnowledgeBase` (+ `BirRegistryPersistence`) | B | `MinimalHttpServer.java:238` |
| `data/hierarchical_memory.jsonl` (`matrix.memory.persistence.path`) | `io.matrix.memory.PersistentHierarchicalMemory` (`@ApplicationScoped`, flush every `matrix.memory.flush-seconds`, default 30) | A | |
| `data/knowledge/arm-*.sqlite` | `LearningWorkbenchResource.java:738` | A | experimental arms |
| `*.ldn` JSON | `io.matrix.snapshot.SnapshotStore` | A | cluster snapshots |

**Single-writer rule, precisely:** `WriterLock.acquire` (`WriterLock.java:157-170`) takes an OS `FileLock` on
`<db>.writer.lock` plus a written owner record; a second *process* is refused with
`CognitiveWriterConflictException`; a second store *in the same JVM* joins the lock (`:126-131`).
`CognitiveStore.java:1749` and `:1817` are the two fence points.

**Tests:** `matrix-core/src/test/java/io/matrix/memory/` — 11 classes (`PersistentMemoryTest`,
`PersistentHierarchicalMemoryTest`, `SqliteMemoryBackendTest`, `HierarchicalMemoryTest`,
`HierarchicalMemoryEvictionTest`, `MemoryHierarchyTest`, `MemoryHierarchyTierTest`, `ConsolidationCycleTest`,
`TombstoneCompactionTest`, `EpisodeSeparationTest`, `Exp164MemoryPersistenceTest`);
`matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/{EpisodicLogPersistenceTest, EpisodicLogAuditChainTest,
PersistentHdcStoreIntegrationTest, SparseHdcStoreTest}.java`.

---

## 9. Goals / attention / regulation / budget / autonomy

| Subsystem | Primary class | In production? | Caller evidence | Tests |
|---|---|---|---|---|
| Goals (Chain B) | `matrix-core/src/main/java/io/matrix/goals/GoalTracker.java` (in-memory list of `Goal{id,description,Status,priority}`) | **YES** | `matrix-brain-runtime/.../AutonomyLoop.java:36`; `matrix-api-gateway/.../MinimalHttpServer.java:1590,1628,1644` | `matrix-core/src/test/java/io/matrix/goals/{GoalTrackerTest,Exp216GoalTrackerTest}.java` |
| Goals (Chain A) | `io/matrix/knowledge/GoalManager.java` + `KnowledgeStore.goals()` | **YES** | `KnowledgeStore.java:3501-3502` (`goalsWithSubtasks`) → `GoalManager.java:162-163` → `KnowledgeWorkbench.java:2051` → `LearningWorkbenchResource.java:672` | `matrix-core/src/test/java/io/matrix/knowledge/` (goal persistence) |
| Drives | `matrix-core/src/main/java/io/matrix/cognitive/DriveSignal.java` (`Dimension`, `Effect`) | **YES** | persisted by `CognitiveStore.java:2065,2080-2093`; read at `CognitiveKernel.java:390-391` | `matrix-core/src/test/java/io/matrix/cognitive/neuromodulation/*` |
| Regulator | `matrix-core/src/main/java/io/matrix/cognitive/KernelRegulator.java:24` (implements `CognitiveKernel.RegulatoryBridge`) → `neuromodulation/RegulationController.java:53`, `DriveDynamics.java` | **YES** | `CognitiveService.java:258` `new KernelRegulator()`; kernel calls `regulator.bias/evolve` at `CognitiveKernel.java:390,642` | `CognitiveRegulatoryPersistenceTest`, `neuromodulation/{RegulationControllerTest, DriveDynamicsTest, RegulationEffectMeasurementTest, RegulationEvolveTest, RegulationSafetySurfaceTest, RegulatoryEventTest, ModelUnsettledEventTest}` |
| Selector | `matrix-core/src/main/java/io/matrix/cognitive/KernelSelector.java:20` (implements `CognitiveKernel.AlgorithmSelectorBridge`) | **YES** | `CognitiveService.java:257` `new KernelSelector(registry, ledger)`; kernel calls `selector.select` at `CognitiveKernel.java:410` | `matrix-core/src/test/java/io/matrix/cognitive/algorithms/AlgorithmSelectorTest.java` |
| Attention | `matrix-core/src/main/java/io/matrix/consciousness/AttentionRouter.java`; `matrix-brain-runtime/.../graph/NeuroModulatorHub.java` | **YES** | `BirBrainCycle.java:56,96`; `consciousness/BrainLoopService.java:35,44`; `TrueMindCycle.java:98,128` (`NeuroModulatorHub.productionDefault()`) | `AttentionRouterTest`, `brain/runtime/graph/AttentionSafetyFloorTest.java` |
| Saliency | `matrix-core/src/main/java/io/matrix/perception/SaliencyEngine.java` | **YES** | `TrueMindCycle.java:148,243`; `BirBrainCycle.java:55` | `matrix-core/src/test/java/io/matrix/perception/SaliencyEngineTest.java` |
| Budget | `matrix-core/src/main/java/io/matrix/budgeter/{ConjugateBudgeter,ConjugateBudgeterMulti}.java` | **YES (Chain B)** | `matrix-brain-runtime/.../AutonomyLoop.java:58` → `io/matrix/lifecycle/ImpulseScheduler.java:63,68,107` | `budgeter/{ConjugateBudgeterTest, ConjugateBudgeterStepTest, ConjugateBudgeterTraceConformanceTest, ConjugateBudgeterVsGreedyTest}` |
| Budget (homeostat) | `matrix-core/src/main/java/io/matrix/budgeter/BudgeterHomeostat.java` | **NO** — zero references outside `matrix-core/src/test/java/io/matrix/budgeter/BudgeterHomeostatTest.java` | — | `BudgeterHomeostatTest` only |
| Autonomy / impulse | `matrix-core/src/main/java/io/matrix/autonomy/{AutonomyEngine,ImpulseProposal,ImpulseProposalConsumer,BoundedImpulseProposalStore,LearningLoop,SelfImprovingEngine}.java` | **YES (Chain B)** | `matrix-brain-runtime/.../AutonomyLoop.java:33,61-62`; `matrix-api-gateway/.../llm_quarantine/{BrainProduction,AutonomyEngineLauncher,BrainIntegrated,SelfImprovingEngineLauncher}.java` | `autonomy/{AutonomyEngineTest, ImpulseProposalConsumerTest}` — the latter asserts the consumer **cannot** reach Chain A's store (:221-246) |

---

## 10. Plugin / extension mechanism — **there is none**

Evidence (all searches over `*/src/main/java`):
- `ServiceLoader` / `META-INF/services` / `java.util.ServiceLoader` — **0 hits**.
- `WASM` / `WASI` — **0 hits** (the only regex hits are the substring `whatWasMeasured`).
- Quarkus extension machinery (`io.quarkus.extension`, `SyntheticBeanBuildItem`, `ConfigSource`, `@ConfigRoot`) —
  **0 hits**; only one `@ConfigMapping` (`io/matrix/ethics/guardrail/GuardrailConfig.java:15`).
- `plugin` in main → 4 hits, all unrelated: a Gradle-plugin remark, an Avro remark, an explicit **denial** in
  `CognitiveKernel.java:35` ("it does the work of one cycle, and … *no plugin mechanism*"), and Minecraft/Bukkit refs.
- `capability` in main → `io/matrix/cognitive/algorithms/AlgorithmCapability.java` (a *descriptor* of an
  algorithm), `io/matrix/knowledge/Capability{Evaluator,State}.java` (learning-state dimensions),
  `io/matrix/federation/liquid/CapabilityConsensusEngine.java`. No provider/registry abstraction.
- `sandbox` in main → `io/matrix/api/SandboxResource.java` — an HTTP endpoint for boolean-model inference
  (imports `BooleanChainRunner`, `BooleanChainProducer`, `ModelRegistry`), **not** a code sandbox for
  third-party extensions.

**The three things that look like a plugin system but are not:**
1. `matrix-spigot/src/main/java/io/matrix/spigot/MatrixPlugin.java:42` — a **Bukkit/Spigot** plugin (Minecraft
   adapter): an *outbound* client of MATRIX, not an extension point *into* MATRIX.
2. `matrix-core/src/main/java/io/matrix/mcp/MatrixMcpServer.java` — an MCP server exposing MATRIX capabilities
   as MCP tools/resources; its own javadoc (:42) says *"This is a protocol stub … no full MCP transport."*
3. `io.matrix.ethics.guardrail.*` / `io.matrix.guardrail.GuardrailEngine` — a hard-coded interceptor chain, not
   a user-extensible registry.

**Conclusion:** MATRIX has **no plugin/SPI/WASM extension mechanism**; extension happens by editing the source
and recompiling. The nearest thing to a runtime extension registry is
`io.matrix.cognitive.algorithms.AlgorithmRegistry` (§12) and `io.matrix.chain.ChainRegistry` (§1b) — both
in-process singletons with no discovery, isolation, lifecycle or trust model.

---

## 11. Persistence / recovery / snapshot / fencing

**Fencing (single-writer, W45.1/W45.2)** — all `matrix-core/src/main/java/io/matrix/cognitive/`:
- `WriterLock.java` — OS `FileLock` + owner record (`pid`, `startedAt`, `token`, `database`, `claimedAt`);
  `acquire` (:157), `lockFileFor` (:144); `close()` deletes the file on a clean shutdown.
- `OwnershipToken.java` — `classify(owner, mine)` → `MINE` / `LIVE_OTHER` / `DEAD`.
- `CognitiveStore.java:1749` and `:1817` — the two **fence points**: "a process that lost the request may not
  write its own [completion]" / "the completion only counts when the writer is still the owner".
- **No lease service, no multi-writer support** — deliberate:
  `docs-v2/engineering/W45.2-OWNER-DECISIONS.md:174-175` ("It does not add leases, a fencing service, or
  multi-writer support").

**Recovery** (all `matrix-core/src/main/java/io/matrix/cognitive/`):
- `RequestExecutionState.java` — `CLAIMED`, `IN_PROGRESS`, `COMPLETED`, `FAILED_NO_EFFECT`,
  `INTERRUPTED_NO_EFFECT`, `INTERRUPTED_PARTIAL`, `INDETERMINATE`. Re-executable only from
  `FAILED_NO_EFFECT` / `INTERRUPTED_NO_EFFECT`.
- `RequestCheckpoint.java` — durable phase markers (`CLAIMED`, `EPISODE_OPENED`, `PREDICTION_WRITTEN`,
  `EXPERIMENT_EXECUTED`, `OBSERVATION_COMMITTED`, `REGULATION_PERSISTED`, `EPISODE_CLOSED`,
  `OUTCOME_PERSISTED`, `IDEMPOTENCY_COMPLETED`); each carries an `irreversible` flag —
  `OBSERVATION_COMMITTED` is the point of no return.
- `IdempotencyRegistry.java` — `claim` (:217), `recover` (:284), `recordRecovery` (:414), `unfinished` (:424),
  `history` (:434). `CognitiveService.java:207` `recoverInterruptedRequests()`, invoked once at startup (:270).

**Snapshots:** epistemic `io/matrix/knowledge/{KnowledgeSnapshot,WorkspaceSnapshot,SnapshotCoverage,
IncompatibleSnapshotException}.java` (transactional payload bound `DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES = 8 MiB`,
`KnowledgeStore.java:174`); cluster `io/matrix/snapshot/{ClusterSnapshot,SnapshotStore}.java` (`.ldn` JSON) +
`io/matrix/security/SnapshotSigner.java`; brain `io/matrix/brain/runtime/{EpisodicLog,BirKnowledgeBase,
PersistentHdcStore,PersistentMind}.java`, `graph/SleepConsolidator.java`, `RealSleepScheduler.java`;
journals `io/matrix/events/{EventJournal,KafkaEventJournal,R2dbcEventJournal,InMemoryEventJournal,
BatchKafkaJournal}.java`, `io/matrix/audit/{HashChain,FrozenFNLHashChain}.java`, `AuditChain.java`.

**Wired in production:** YES for the Chain A snapshot/recovery machinery (`CognitiveService`,
`LearningWorkbenchResource`), YES for `EventJournal`/`KafkaEventJournal` (`MatrixApplication.java` `@Produces`),
YES for Chain B's NDJSON stores (`MinimalHttpServer.java:148-238`). `io/matrix/snapshot/SnapshotStore` is
reachable only from CLI (`HadesDemoCommand.java:69`, `PipelineCommand.java:140`) and
`api/MatrixResource.java:183,211`.

**Tests:** `matrix-core/src/test/java/io/matrix/cognitive/{CognitiveWriterLockTest,IdempotencyRegistryTest,
CognitiveRequestLifecycleTest,CognitiveCycleBudgetTest}.java`; `knowledge/{WorkspaceSnapshotTest,
SnapshotRestoreEndpointTest,SchemaMigrationTest}.java`; `audit/{HashChainTest,HashChainIntegrityTest,
FrozenFNLHashChainTest}.java`; `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/{EpisodicLogPersistenceTest,
AuditChainLicenseFederationTest,EpisodicLogAuditChainTest}.java`.

---

## 12. Method selection

**There is no class named `MethodSelector`.** The mechanism is **algorithm selection**, in
`matrix-core/src/main/java/io/matrix/cognitive/algorithms/` (34 files):

- **`AlgorithmSelector.java:53` — the selector.** `decide(request)` (:158), `decide(request, costWeight)` (:169),
  `select(...)` (:291, :302). Four rules: admit only inside declared preconditions; score from *measured* data only
  (declared accuracy + `CostLedger` cost); nothing admitted ⇒ refusal naming the blocking precondition; budget
  honoured before score. Deterministic (no clock, no randomness). Tie-break: highest score, then the
  lexicographically smallest method id.
- `AlgorithmRegistry.java` — the catalogue of `AlgorithmMethod`s, built by `Portfolio.defaultRegistry()`
  (`Portfolio.java:34`). `AlgorithmMethod.java` — the method interface (`execute(payload, budget)`).
- `AlgorithmCapability.java` — declared preconditions, cost model, accuracy evidence, `VerificationStrategy` (:111).
  `AlgorithmDecision.java` — the decision record (selected id, status, reason, rejected list).
- `CostLedger.java` — the measured cost store; two cost bases (`MEASURED` vs `DECLARED`), never mixed.
- `ProcedureStore.java` — **not used in `main/`**, only `matrix-core/src/test/java/io/matrix/cognitive/algorithms/ProcedureStoreTest.java`.
- Implementations: `MicroWorldReferenceMethod`, `MicroWorldSettledModelMethod`, `Dijkstra`/`BellmanFord`/
  `BoundedUniformCost` shortest-path, `BloomFilter`/`Trie`/`LinearScan` membership, `EntailmentMethod`,
  `IntegerArithmeticMethod`, `ProcedureMethod`.

**Wired in production: YES (Chain A).** `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:248`
`Portfolio.defaultRegistry()`, `:251` `new CognitiveSolver(new AlgorithmSelector(registry, ledger), ledger)`,
`:257` `new KernelSelector(registry, ledger)`. The kernel asks at `CognitiveKernel.java:410-412`
(`selector.select(new ProblemRequest(FAMILY_MICRO_WORLD, …, budget), bias.costWeightMultiplier())`).
`CognitiveResource.java:131` `service.solver().solveGraph(...)` serves `POST /v1/cognitive/solve`.

**The "strategy" vocabulary** here is `AlgorithmCapability.VerificationStrategy` (how a method's result is
verified) — *not* a runtime strategy selector. The only runtime strategy enums in the repo are
`MultiChainEnsemble.Strategy` (§1b; two of three effectively distinct) and `TsetlinTrainer.InitStrategy`.
**Other, unrelated "selection" surfaces:** `io/matrix/knowledge/ExerciseSelector.java` (what to practise next —
`KnowledgeWorkbench.java:655` `nextExercise`), `io/matrix/mcts/` (MCTS planner).

**Tests:** `matrix-core/src/test/java/io/matrix/cognitive/algorithms/{AlgorithmSelectorTest,AlgorithmRegistryTest,
ShortestPathMethodsTest,MembershipLookupTest,IntegerArithmeticMethodTest,EntailmentMethodTest,
BoundedUniformCostSearchMethodTest,CostLedgerTest,ProcedureStoreTest,PortfolioBenchmarkTest}.java`.

---

## Classification table

| Subsystem | Primary class path | Wired in production? | Test path | Classification suggestion |
|---|---|---|---|---|
| 1. Chain A / Chain B (boundary) | `matrix-core/src/main/java/io/matrix/api/CanonicalEntryPoint.java` | Yes (declaration + gate `scripts/w45-chain-isolation-check.sh`) | `matrix-core/src/test/java/io/matrix/api/CanonicalEntryPointTest.java`, `matrix-api-gateway/src/test/java/io/matrix/api/CanonicalEntryPointChainBTest.java` | **KEEP** (the isolation is the owner's decision; keep the proof running) |
| 1b. `io.matrix.chain` (registry+ensemble) | `matrix-core/src/main/java/io/matrix/chain/ChainRegistry.java`, `MultiChainEnsemble.java` | **No** — 0 production callers | `matrix-core/src/test/java/io/matrix/research/Exp341ChainRegistryTest.java`, `Exp347MultiChainConsensusTest.java`, `Exp359FinalIntegrationTest.java`, `Exp385MasterIntegrationTest.java` | **ARCHIVE** (or ADAPT into `AlgorithmRegistry`); two predicates hard-wired `false`, DEBATE unimplemented |
| 2a. `CognitiveKernel` | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java` | **Yes** — `CognitiveService.java:252`, `CognitiveResource.java:80` | `matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelTest.java` + 11 siblings | **KEEP** (the one genuinely epistemic cycle); the procedure-promotion path is dead by construction — needs an explicit domain |
| 2b. `TrueMindCycle` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java` | **Yes** — `ProductionBrainClient.java:129,332` | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/TrueMindCycleIntegrationTest.java` | **KEEP** (Chain B conductor) |
| 2c. `MindCycle` (legacy) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindCycle.java` | **No** (CLI only) | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/MindCycleIntegrationTest.java` | **ARCHIVE** (a guard test already forbids production use) |
| 3. LearningWorkbench | `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java` + `KnowledgeWorkbench.java` | **Yes** — `@Path("/v1/workbench")`, 58 endpoints | `matrix-core/src/test/java/io/matrix/knowledge/**` | **KEEP** (the only fully wired learning application) |
| 3b. `io.matrix.knowledge.acquisition` | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/AcquisitionCycle.java` | **Yes** — `LearningCycleResource.java:110,175` | `matrix-core/src/test/java/io/matrix/knowledge/acquisition/AcquisitionCycleTest.java` | **ADAPT** — it only ever reads *bundled* corpora, never the network; the name over-promises |
| 4. BIR | `matrix-core/src/main/java/io/matrix/bir/Bir.java`, `BirForm.java`, `BooleanRuntime.java`, `BirRegistry.java` | **Yes**, both chains | `matrix-core/src/test/java/io/matrix/bir/**` (20 classes) | **KEEP** — the healthiest subsystem found; best candidate as the shared substrate of an eventual A↔B bridge |
| 5. HDC | `matrix-core/src/main/java/io/matrix/neuron/HdcBrain.java`; `matrix-brain-runtime/.../HdcVector.java`, `PersistentHdcStore.java` | **Partly** — `HdcBrain`/`PersistentHdcStore` yes; `SparseHDC`, `HdcAsLlmPreprocessor`, `HdcRetrievalStage` **no** | `matrix-core/src/test/java/io/matrix/neuron/Hdc*Test.java`; `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/Hdc*Test.java` | **INTEGRATE** (two disconnected HDC stacks) or ARCHIVE the orphans |
| 6. Tsetlin | `matrix-core/src/main/java/io/matrix/tsetlin/TsetlinTrainer.java` | **Yes** (sleep path + `AcceptanceModel`) | `matrix-core/src/test/java/io/matrix/tsetlin/**` | **KEEP** (measured, documented, off the request path) |
| 7. MPDT | `matrix-core/src/main/java/io/matrix/evolution/MpdtGaProducer.java`; `matrix-micro/src/mpdt_neuron.{c,h}` | GA **yes** (sleep path); `MpdtHdcBridge` **no**; C code **not in the build** (`settings.gradle:20` commented out) | `matrix-core/src/test/java/io/matrix/evolution/Exp002ComparisonTest.java`; `matrix-core/src/test/java/io/matrix/neuron/MpdtHdcBridgeTest.java`; `matrix-micro/tests/test_mpdt.c` | **REWRITE CANDIDATE** — the name says "decision tree neuron", the code says "GA over DNF clauses"; `mpdt_evaluate_bool` is a stub |
| 8a. Memory (core) | `matrix-core/src/main/java/io/matrix/memory/*` (11 classes) | **Partly** — `PersistentHierarchicalMemory` yes (CDI); `RecurrentSdm`, `SdmReader`, `MemoryHierarchy`, `PersistentMemory` (only via `BrainLoopMemoryAdapter`) **no** | `matrix-core/src/test/java/io/matrix/memory/**` | **ADAPT / ARCHIVE** the orphans; keep `PersistentHierarchicalMemory` |
| 8b. Memory (brain runtime) | `matrix-brain-runtime/.../PersistentMind.java`, `EpisodicLog.java`, `PersistentHdcStore.java` | **Yes** — `MinimalHttpServer.java:181,199` | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/EpisodicLog*Test.java`, `PersistentHdcStoreIntegrationTest.java` | **KEEP** |
| 8c. Connectome | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/Connectome.java` | **No** — test-only | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/graph/Connectome*Test.java` | **ARCHIVE** (or INTEGRATE into `SleepConsolidator`) |
| 9. Goals / attention / regulation | `io/matrix/goals/GoalTracker.java`, `io/matrix/cognitive/{KernelRegulator,KernelSelector,DriveSignal}.java`, `io/matrix/consciousness/AttentionRouter.java`, `io/matrix/perception/SaliencyEngine.java`, `io/matrix/budgeter/ConjugateBudgeter.java`, `io/matrix/autonomy/*` | **Yes** (all except `BudgeterHomeostat`) | `matrix-core/src/test/java/io/matrix/{goals,autonomy,budgeter,perception,consciousness}/**`, `cognitive/neuromodulation/**` | **KEEP** the wired ones; **ARCHIVE** `BudgeterHomeostat` (0 main refs) |
| 10. Plugin / extension mechanism | — (does not exist) | **No** | — | **ADAPT** — build a real registry; today the only extension is recompilation |
| 11. Persistence / recovery / fencing | `io/matrix/cognitive/{WriterLock,OwnershipToken,RequestExecutionState,RequestCheckpoint,IdempotencyRegistry}.java` | **Yes** | `matrix-core/src/test/java/io/matrix/cognitive/{CognitiveWriterLockTest,IdempotencyRegistryTest,CognitiveRequestLifecycleTest}.java` | **KEEP** — the most carefully reasoned subsystem in the repo |
| 12. Method selection | `matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmSelector.java` | **Yes** — `CognitiveService.java:248-258`, `CognitiveKernel.java:410` | `matrix-core/src/test/java/io/matrix/cognitive/algorithms/AlgorithmSelectorTest.java` | **KEEP** — but the name is "algorithm", not "method"; `ProcedureStore` is orphaned (ARCHIVE) |

---

## Unconfirmed / caveats

- **Unconfirmed:** whether `matrix-micro`'s C code is exercised by any CI job. `settings.gradle:19-21` comments
  out `matrix-micro`; I found no CI workflow reference to `matrix-micro` in the files read.
- **Unconfirmed:** the exact number of reachable-from-production classes per chain — the W45.2 reachability
  analyser (`scripts/w45-chain-isolation-reachability.py`) computes this, but I did not execute it.
- **Not measured:** no test suite was run; all statements above are static-analysis claims about reachability.
- **Stale in-repo claims found while reading:** `ProductionBrainClient.java:325` calls `TrueMindCycle`
  "MindCycle"; `LearningWorkbenchResource` has 58 HTTP methods while `docs-v2/engineering/W42-*` says 52;
  `docs-v2/engineering/W42-VERIFICATION-INTEGRATION.md:85` cites `LearningWorkbenchResource.java:342` for the
  goals endpoint — the real line is **672**.
