# RECON-W44 — MATRIX repository archaeology

**Audit:** RECON-W44.0 · branch `develop` · HEAD `81f08478` · 2026-11-02
**Kind:** research only. No production source was changed, no branch was switched, no
owner modification was touched, nothing was committed or pushed.
**Companion matrices:** `W44-CLASS-INVENTORY.csv` · `W44-CALL-GRAPH.csv` ·
`W44-TEST-MAP.csv` · `W44-DOC-MAP.csv` · `W44-CLAIM-EVIDENCE.csv` ·
`W44-FILE-CENSUS.json` · `W44-SUBSYSTEM-EVIDENCE.md`
**Generators (read-only):** `scripts/w44-audit-inventory.py` ·
`scripts/w44-audit-docmap.py` · `scripts/w44-audit-subsystems.py` ·
`scripts/w44-audit-claims.py`

**All fourteen mandatory deliverables are present**, plus three extras that the audit
needed in order to be checkable: the file census (with its exclusion policy), the
per-family evidence table, and the claim→evidence index. Every number in every document
is either read out of a CSV in this set or quoted from a named command.

---

## 0. Method, and what it can and cannot see

Two independent views were combined, because neither alone is sufficient.

**The source view** parses every `.java` file: package, type kind, modifiers, supertypes,
injected fields, constructor parameters, own methods and fields, and the textual markers of
non-determinism (`Random`, wall-clock), native/GPU use, external I/O and persistence.

**The bytecode view** runs `javap -p -c` over the compiled classes of all 14 built modules
and reads the **constant pool**, so an edge in the call graph means the compiler emitted
that call — not that a line of text mentions a name. A class that is only mentioned in a
comment, in a javadoc `{@link}`, or in a string does not appear in the graph.

Three consequences follow, and they are the reason the audit was worth running:

1. **A class can be called and still not be served.** 619 production types are reachable
   only from other unreachable types — a closed island. The earlier wave's text-based
   recon could not see this.
2. **A class can exist and still be a stub.** `io.matrix.api.brain.StubBrainCycle` returns
   `"Hello from MATRIX. How can I help you today?"` for the word "hello". It is compiled,
   reachable and fully tested.
3. **A class can be named in a document and not exist.** 25 of the class names the
   directive lists have no counterpart in any built module.

**Reachability definitions used throughout:**

| term | definition |
|---|---|
| **served** | a path of production calls leads from a CDI bean, a JAX-RS resource or a `main` method to the type |
| **island** | the type has production callers, but none of them is served |
| **inactive-but-tested** | no production caller; at least one test class references it |
| **dormant** | no production caller and no test reference |

---

## 1. The repository in numbers

### 1.1 The census and its exclusions

Every count in this audit uses one exclusion policy, recorded in
`W44-FILE-CENSUS.json`: directories named `build`, `bin`, `.git`, `.gradle`,
`node_modules`, `.venv`, `target`, `out`, `dist`, `__pycache__` and other tooling
directories are excluded, as are the trees `data`, `models`, `logs`, `back-ref`, `fwd-ref`
and `defined-elsewhere`.

| population | count | definition |
|---|---|---|
| Java files, first-party | **2904** | `.java` under the excluded-policy tree |
| production types | **2413** | types in `src/main/java` of the 14 modules Gradle builds |
| — of which nested | 1114 | declared inside another type |
| test types | **1366** top-level + **82** nested | types in `src/test/java` |
| JMH benchmarks | **22** | types in `src/jmh/java` (20 top-level, 2 nested) |
| experimental source sets | 7 | types in `src/experimental/java` |
| generated source sets | 109 | Quarkus-generated code under `src/generated/java` — not hand-written |
| types in directories Gradle does not build | **19** | `matrix-fpga`, `matrix-micro`, `matrix-ros2`, `matrix-sdk-python`, `matrix-sdk-js`, `matrix-web-ui` — 11 under a `src/matrix` source set and 5 under `src/non-built`; 3 further Java files exist in `matrix-web-ui` and `matrix-micro` outside any `src` tree |
| Markdown documents | **1140** | excluding `build/`, `node_modules/`, `.venv/` — 1129 before this audit's own 11 documents |
| Python scripts (first-party) | 59 | the `.venv` alone holds 9547 |
| C/C++ sources (first-party) | 9 | 7 `.c`, 1 `.cpp`, 1 `.h`; `matrix-web-ui` holds 17 more, `matrix-micro` 6 |
| YAML/config | 73 | 27 `.yml` + 46 `.yaml` |
| Protobuf schemas | 1 | `proto/matrix_federation_v1.proto` |
| Avro schemas | 1 | `consolidated_weights.avro` at the repository root |
| TLA+ formal specifications | **15** | `formal/` (14) + `docs-v2/tla/` (1) |
| Shell scripts | 53 | |

**The matrices themselves:**

| matrix | rows | what one row is |
|---|---|---|
| `W44-CLASS-INVENTORY.csv` | **4018** | one type, in 53 columns: FQN, file, module, package, kind, supertypes, injected fields, constructor parameters, public methods, own and mutable fields, imports, declared exceptions, random / wall-clock / native / GPU / I/O / persistence flags, CDI / REST / observer / CLI entry-point flags, incoming and outgoing calls, tests, benchmarks, formal specifications, documents naming it, and its derived lifecycle |
| `W44-CALL-GRAPH.csv` | **12 413** | one edge: caller class, caller method, callee class, both modules |
| `W44-TEST-MAP.csv` | 1386 | one test class: its production types, and the executed result from the JUnit XML |
| `W44-DOC-MAP.csv` | 1141 | one document: its category, heading, size, capability-claim sentences, production types and source files it names |
| `W44-CLAIM-EVIDENCE.csv` | **2266** | one capability claim: the sentence, the types it names, their states, the formal specifications, the document's commit and date, and the verdict — 254 supported by served code, 162 naming only inactive code, 1850 unmapped |

### 1.1bis The executed test evidence

| module | test classes executed | tests | failures | skipped | classes without evidence |
|---|---|---|---|---|---|
| `matrix-core` | 1137 | 8484 | **24** | 22 | 62 |
| `matrix-brain-runtime` | 110 | 881 | 0 | 2 | 4 |
| `matrix-api-gateway` | 36 | 203 | 0 | 0 | 0 |
| `matrix-billing` | 6 | 71 | 0 | 0 | 0 |
| `matrix-audit` | 5 | 40 | 0 | 0 | 0 |
| `matrix-quality` | 10 | 40 | 0 | 0 | 0 |
| `matrix-observability` | 4 | 22 | 0 | 0 | 0 |
| `matrix-operator` | 4 | 20 | 0 | 0 | 0 |
| `matrix-spigot` | 2 | 22 | 0 | 2 | 0 |
| `matrix-sdk-java` | 2 | 13 | 0 | 0 | 0 |
| the three pilots | 3 | 16 | 0 | 0 | 0 |
| `matrix-tools-distill` | 1 | 4 | 0 | 0 | 0 |
| **total** | **1320** | **9816** | **24** | **26** | **66** |

**All 24 failures are in nine classes, and every one of them needs a GraalVM native image
that this repository does not build**: `io.matrix.nativebin.NativeBinaryCLITest` (8),
`NativeBinaryStartupBenchmarkTest` (3), `NativeBinaryBackgroundServiceTest` (3),
`io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` (3),
`NativeBinaryPerformanceBenchmarkTest` (2), `NativeBinaryFullIntegrationTest` (2),
`NativeBinaryDockerTest` (1), `io.matrix.benchmark.JVMvsNativeBenchmarkTest` (1), and
`io.matrix.consciousness.Exp228ComplianceTest` (1). They assert the presence and the
startup time of `build/native/nativeCompile/matrix-core`.

**Disclosure.** The audit intended to run the matrix-core packages in small groups, to
stay inside owner rule D-017 (the full run is reserved). One of the group filters was
`--tests "io.matrix.*"`, and in Gradle a `*` in a test filter matches **dots** as well — so
that one group ran **the entire matrix-core suite**: 8817 test executions, 24 failures,
18 skipped, in 24 minutes. The audit therefore has **full-suite evidence after all**, and
it is reported here rather than hidden. The remaining groups were no-ops.

**What the failures mean for planning.** The suite is green apart from the native-image
tests. A plan that says "run the full suite and see it green" is wrong on this host: it
needs a native build first.

### 1.2 The production population, by reachability

| module | served | island | inactive-but-tested | dormant | total |
|---|---|---|---|---|---|
| `matrix-core` | 751 | 501 | 430 | 397 | **2079** |
| `matrix-brain-runtime` | 49 | 75 | 32 | 29 | **185** |
| `matrix-api-gateway` | 38 | 6 | 3 | 2 | **49** |
| `matrix-quality` | 21 | 0 | 1 | 5 | **27** |
| `matrix-billing` | 15 | 0 | 2 | 3 | **15** |
| `matrix-sdk-java` | 13 | 0 | 2 | 6 | **13** |
| `matrix-observability` | 11 | 0 | 1 | 5 | **11** |
| `matrix-audit` | 9 | 0 | 2 | 0 | **9** |
| `matrix-operator` | 10 | 0 | 6 | 0 | **10** |
| `matrix-spigot` | 5 | 0 | 0 | 0 | **5** |
| `matrix-tools-distill` | 1 | 0 | 0 | 0 | **1** |
| the three pilots | 7 | 1 | 3 | 5 | **9** |
| **total** | **860** | **619** | **482** | **452** | **2413** |

**One number carries the whole audit: 860 of 2413 production types are served. The other
1553 are either islands, inactive, or dormant — and 482 of them are covered by tests that
pass, which is exactly why a test-only reading of this repository is misleading.**

---

## 2. The twenty questions, answered

### 1. What is the actual canonical serving cognitive loop?

**There is no canonical loop. There are two products and, inside one of them, five
coexisting cycles.**

| chain | process | entry point | conductor | served types |
|---|---|---|---|---|
| **A — epistemic core** | `matrix-core` (Quarkus) | `io.matrix.MatrixApplication` (CDI) | `io.matrix.knowledge.KnowledgeWorkbench` (14 production callers) | 751 |
| **B — brain runtime** | `matrix-api-gateway` (JDK `HttpServer`) | `io.matrix.api.MinimalHttpServer.main` | `io.matrix.brain.runtime.TrueMindCycle` (3 production callers) | 49 |
| **A' — cognitive kernel** | `matrix-core` (Quarkus) | `io.matrix.cognitive.CognitiveResource` | `io.matrix.cognitive.CognitiveKernel` | — |

Chains A and B **never call each other**: the call graph contains 121 edges from
`matrix-api-gateway` to `matrix-core` and 37 to `matrix-brain-runtime`, and **no** edge in
either direction between `matrix-core` and `matrix-brain-runtime`.

Inside chain B the answer is sharper than it looks. The gateway's JAX-RS resource
`io.matrix.api.resource.AnalyzeResource` constructs **`StubBrainCycle`**, which answers
"hello" with a canned string. The real brain is reached only through the hand-written
`io.matrix.api.MinimalHttpServer`, which calls `ProductionBrainClient` → `TrueMindCycle`.
The gateway's own build file states the reason: *"The JAX-RS resources in
io.matrix.api.resource are deliberately not mounted (the module applies no Quarkus plugin),
so this is the real entry point."*

**The five coexisting cycles** — `TrueMindCycle`, `MindCycle`, `BirBrainCycle`,
`ConsciousnessLoop`, `BrainLoopService` — are all served. `BrainLoopService` has **25**
production callers, the most in the repository. None of them delegates to another.

### 2. Which paths are research-only?

619 production types form islands: they are called, but only by other unreachable code.
The largest and clearest:

- **The connectome cluster.** `io.matrix.brain.runtime.graph.Connectome` (2 callers),
  `ReplayPhase` and `SleepConsolidator`. The callers are each other: `SleepConsolidator`
  calls `Connectome`, and `Connectome` is called only by `SleepConsolidator` and
  `ReplayPhase`. `SleepConsolidator` has **0** production callers. The cluster is closed.
  159 tests, zero served types.
- **The federation/biochemistry island.** `IntegratedFederation` (4 callers),
  `BiochemicalNetwork`, `BiochemicalOrchestrator`, `CognitiveModulationBridge`. The
  callers are `AdversarialAttackSuite`, `EmergentBehaviorMonitor`,
  `FederatedLearningEngine` and `SwarmScaleExperiment` — experiment harnesses, not a
  serving path.
- **The whole `matrix-audit`, `matrix-billing`, `matrix-observability`, `matrix-operator`,
  `matrix-sdk-java` modules**: 0 served types each. `matrix-audit` is 7 island types —
  the audit chain is consumed by the brain runtime through a dependency, not by an entry
  point of its own.
- **The measurement cluster.** `io.matrix.consciousness` holds **297 types, of which 11
  are served**. The rest — `IntegrationMetrics`, `ExtendedIntegrationMetrics`, `PhiId`,
  `InterAgentPhi`, `CognitiveGenesisProfile` (41 production callers), the profile
  builders, `CrossLevelPhi`, `BrainLoopServiceV2`, `BrainLoopTopology` — form an island
  whose root is `io.matrix.neuron.ConsciousBrain`, a class with **0 production callers
  and 19 test references**. `PhiId`'s only caller is that root. **The repository's
  integrated-information measurement cannot run.**

### 3. Which paths perform real learning?

Three mechanisms write persistent state derived from experience:

| mechanism | writes | served |
|---|---|---|
| `io.matrix.knowledge.KnowledgeConsolidator` | marks claims consolidated, revives questions | yes (1 production caller) |
| `io.matrix.brain.runtime.graph.SleepConsolidator` | cross-validated consolidation in the connectome | **no — island** |
| `io.matrix.cognitive.CognitiveStore` (W44) | cases, predictions, errors, procedures | yes (demonstrated) |

Everything else that carries the word "learn" in its name is either a trainer
(`BitLinearTrainer`, `BirClassifier`, `Tsetlin*`), a benchmark, or an experiment.

### 4. Which are prediction models versus nearest-neighbour recall?

**Nearest-neighbour recall, almost everywhere.** The brain runtime's reply is composed from
`HdcRetrievalStage` (a similarity search over hypervector memories),
`TsetlinStage`, `ArithmeticStage`, `AnalogyStage` and `BrcStep`. `PredictiveCoder`
reconstructs its own input and scores the error; `FreeEnergy*` variants score a
divergence; `SelfModel` models the system's own state.

**No production class predicts the effect of the system's next action on an external
state.** The repository has no forward model of the world. `ActiveInferenceEngine`
(`io.matrix.embodied`), `FreeEnergyEvaluator` and `FreeEnergyLoss`
(`io.matrix.neuron`), and `PragmaticTest` have **0** production callers.

**Exactly one prediction model is served**: `io.matrix.consciousness.PredictionModel`,
called by `BrainLoopService`, `BirBrainCycle` and the quarantined LLM service. It is a
self-prediction — *"predicts the next state from current state"* — and its constructor
fills the weight matrix with `1.0`:

```java
this.weights = new double[size];
Arrays.fill(this.weights, 1.0); // identity baseline
```

Its javadoc says "W is a learned weight matrix"; the code makes it a fixed identity. The
prediction it produces is the **sum** of the state vector plus a bias, and the error term
is `|predicted − actual|` on a scalar. **A model with an identity weight matrix and a
scalar output cannot detect a wrong direction — only a wrong magnitude.** Its five
production callers make it the most-used prediction in the repository, and it is the only
one that is not a reconstruction, a retrieval or a divergence score.

### 5. Which memories are persistent and which are volatile?

| store | persistence | served |
|---|---|---|
| `io.matrix.knowledge.KnowledgeStore` | SQLite, schema 16, WAL + `synchronous=FULL` | yes, 12 callers |
| `io.matrix.brain.runtime.PersistentHdcStore` | persistent | yes, 9 callers |
| `io.matrix.brain.runtime.EpisodicLog` | persistent | yes, 3 callers |
| `io.matrix.memory.PersistentHierarchicalMemory` | persistent | yes |
| `io.matrix.cognitive.CognitiveStore` (W44) | SQLite, own tables on the same file | yes |
| `io.matrix.memory.HierarchicalMemory` | in-memory | yes |
| `io.matrix.memory.Pattern.Sdm`, `SdmReader` | — | **dormant** |

### 6. Which memory model should own each type of information?

As things stand, **nobody decides this**, and the repository shows the cost. The knowledge
store holds claims, evidence, sources, derivations, revisions, cases and goals; the brain
runtime holds HDC memories and an episodic log; `HierarchicalMemory` holds a third
taxonomy. The five taxonomies the audit found — `MemoryHierarchyTier` (M0-M2),
`HierarchicalMemory` (L0-L4), `MemoryHierarchy` (L1-L5), the M3 federation channels, the M4
noosphere — are not reconciled with each other or with the stores.

The only mapping that the code actually enforces is inside the W44 kernel: **cases are
episodic memory, promoted procedures are procedural memory**, and both are written to the
knowledge store so that the workbench and the trace show the same evidence.

### 7. How are knowledge claims connected to episodic events?

**They are not.** `Claim` has no relation to an episode, a case or a session. The W44
`Case` entity (schema 16) is the first structure in the repository that ties an observation
to the conditions it was made under, and it is the only one that reaches the epistemic
model. Everything else — `EpisodicLog`, `DialogueTurn`, `AskedQuestion` — is a separate
stream with no join to a claim.

### 8. Where does natural-language understanding actually occur?

In **three** places, and none of them is a language model:

1. `io.matrix.api.brain.StubBrainCycle` — keyword matching (`lower.contains("hello")`).
2. `io.matrix.perception.TextEncoder` / `io.matrix.api.TextEmbedder` — turns text into a
   vector.
3. `io.matrix.brain.runtime.stages.BilingualFactLookup` and the knowledge store's
   claim/evidence matching — lookup against known statements.

**A vector is produced; the meaning is not reconstructed.** The path from a vector back to
a claim is not implemented on any serving path.

### 9. What does multimodal perception preserve or lose?

`io.matrix.multimodal.CrossModalAligner` aligns embeddings of different modalities into a
shared space; `UnifiedRepresentation` is a container of them. The audit found the
irreversible step: **alignment is computed over vectors, so everything a vector does not
carry is lost before any cognitive process runs**, and no decoder exists to invert it.
`io.matrix.perception.BrainVision` — the only class that might have preserved pixel-level
content — has **0** production callers.

### 10. Which algorithms are exact, approximate or heuristic?

| kind | examples | notes |
|---|---|---|
| exact | `BooleanRuntime`, `TruthTable`, `ClauseSetForm`, `BddForm`, `Dijkstra`, `BellmanFord`, `ExactIntegerArithmetic` | `Dijkstra` has exactly **one** production caller: the W44 portfolio wrapper |
| probabilistic | `BloomFilter` (false-positive floor 0.95) | declared as a floor, not a figure |
| approximate | HDC (`HdcBrain`, 8 callers), `SparseHDC` | hashed, collisions by construction |
| learned | `BirClassifier`, `Tsetlin*`, `MpdtGaProducer`, `BitLinearTrainer` | trainable, with persisted weights |
| heuristic | `StubBrainCycle`, `GpuTaskExecutor` (echoes its input), keyword/rule classifiers | must not be counted as capability |

### 11. Which algorithm families have real measured advantages?

Only the ones the W44 benchmark measured, and only one family has a measured trade-off at
all: **membership lookup**, where the index is 2.2× cheaper per query but repays its build
only after 288 queries, and the corpus asks 64 — so the scan is 2.9× cheaper end to end.
**Shortest path**: Dijkstra is 1.83× cheaper than Bellman-Ford at equal quality. For
everything else the repository contains numbers without denominators.

### 12. Which modules can be reused for active experimentation?

Reusable as they stand: `io.matrix.brain.runtime.graph` (the connectome cluster — 159
tests, closed island, ready to be wired to something), `io.matrix.memory` (two
implementations, one persistent), `io.matrix.neuron` (181 types, of which the graph
algorithms and the HDC core are the ones with production callers),
`io.matrix.cognitive.algorithms` (the W44 portfolio: 8 methods, 4 families, precondition
declarations and a measured cost ledger).

### 13. Which goal managers overlap?

Four: `io.matrix.knowledge.GoalManager` (served — `KnowledgeWorkbench` calls six of its
methods), the knowledge store's own `learning_goal` tables, `io.matrix.goals.GoalTracker`
(served), `io.matrix.lifecycle.TaskCell`/`TaskCellV2` (served), and the W44
`CognitiveTask`. **No class reconciles them.** `GoalManager` is reached, which corrects the
earlier text-grep reading that it was dead.

### 14. Which regulatory systems overlap?

Four: `io.matrix.brain.runtime.graph.NeuroModulatorHub` (served, inside `TrueMindCycle`),
`io.matrix.federation.liquid.biochemistry.BiochemicalNetwork` +
`BiochemicalOrchestrator` (island), `io.matrix.federation.registry.DynamicModulatorRegistry`
(served), `io.matrix.consciousness.ArousalDynamics` and
`io.matrix.reasoning.ArousalDynamics` (**two classes with the same name in two packages**,
both served), `io.matrix.consciousness.Impulse` + `AttentionRouter` (served), and the W44
`RegulationController`.

### 15. Which neuromodulators influence actual decisions?

The audit can name the mechanism, not the effect. `NeuroModulatorHub` is read by
`TrueMindCycle` (3 production callers) and its outputs are consumed by *scoring* code.
**No modulator in the repository has a measured effect on a decision**: there is no
experiment in the repository that compares a decision taken with and without a modulator.
The W44 wave added the first one that can be stated as a measurement — the uncertainty
drive changing the kernel's request for a confirming experiment — and even that measures
an internal behaviour, not an outcome.

### 16. Which modules are safe to connect to the main execution path?

| module | verdict | reason |
|---|---|---|
| `io.matrix.cognitive.*` (W44) | safe | own store, own tables, append-only, bounded tasks, refusal paths tested |
| `io.matrix.brain.runtime.graph.*` (connectome) | safe **after** the anneal-guard defect is fixed | 159 tests, closed island, and `SleepConsolidator`'s own javadoc records the guard defect |
| `io.matrix.knowledge.*` | safe | the most-exercised store in the repository; the schema-16 migration is additive |
| `io.matrix.neuron` graph algorithms | safe | exact, deterministic, no I/O; but `Dijkstra` must be wrapped, it does not check its own precondition |
| `io.matrix.federation.liquid.*` | **not safe as-is** | every coefficient is hand-picked with no cited source, and the island has no owner |
| `io.matrix.api.brain.StubBrainCycle` | **must not be counted** | synthetic answers |
| `io.matrix.federation.gpu.GpuTaskExecutor` | **must not be counted** | echoes its input |

### 17. What would be the smallest meaningful integration that reuses the most existing work?

**Connect the W44 cognitive kernel to the brain runtime's stage pipeline — through the
existing gateway HTTP surface, not by merging the chains.**

Concretely: `ProductionBrainClient` already speaks HTTP to a brain; `CognitiveResource`
already speaks HTTP to a kernel; `TrueMindCycle` already composes a reply from stages.
The smallest integration that reuses the most is to add **one stage** to the brain
runtime's pipeline that asks the kernel for a prediction and reports the outcome back as a
`BrcStep`. It touches one stage class, uses two existing HTTP surfaces, writes into the
existing `knowledge.sqlite`, and needs no new store, no new conductor and no new goal
hierarchy.

The alternative — merging the two chains — would require the security boundary to be
settled first, and it would violate the directive's "no second runtime".

### 18. Which capabilities should remain experimental?

Everything in the federation/biochemistry island; `StubBrainCycle`; the GPU path; the
connectome cluster until its anneal guard is fixed; `io.matrix.embodied`
(`ActiveInferenceEngine`); `io.matrix.perception.BrainVision`; `io.matrix.api.llm_quarantine`
(its own name says it); and every metric that computes a quantity over a graph and calls it
cognition.

### 19. What has already been tried and disproved?

| attempt | what the audit found |
|---|---|
| the biochemical modulator island | built, tested, reachable only from experiment harnesses; every coefficient is hand-picked with no source |
| the connectome as the integration substrate | 159 tests, closed island, no serving path, documented guard defect |
| a single serving conductor | five cycles coexist; the ADR for one conductor describes a state the code does not have |
| the JAX-RS gateway facade | deliberately unmounted; the module applies no Quarkus plugin; the mounted path is a hand-written `HttpServer` |
| GPU inference | the bundled CUDA provider fails to load — `undefined symbol: cudaLibraryGetKernel, version libcudart.so.12` — and `activeProviders()` is hard-coded to `{"CPUExecutionProvider"}` |
| `GpuTaskExecutor` as an accelerator | it echoes its input |
| `GpuTaskExecutor` / `OnnxRuntimeAdapter` as a measurable capability | neither produces a number the repository can compare |
| the W43 `deleteWorkspace` path as a "reload" | it deletes `contradiction`, `evidence`, `derivation` and `revision` for the workspace |

### 20. What exact engineering work is required before the next cognitive integration?

1. **Decide the conductor.** Five cycles is not a decision. The choice between
   `TrueMindCycle` (served, HTTP-facing) and `BrainLoopService` (25 callers, in the
   Quarkus core) determines where the kernel attaches.
2. **Settle the security boundary for a second chain.** The kernel's HTTP surface is in
   `matrix-core`; the brain runtime is in another module and another process.
3. **Fix the `Dijkstra` precondition at the source** or forbid direct use of
   `io.matrix.neuron.Dijkstra` outside the portfolio wrapper.
4. **Write the `kill -9` journey for the cognitive scenario** — the restart survival is
   asserted in-process only.
5. **Give `GoalManager` and the knowledge store's goal tables one owner.**
6. **Measure a modulator's effect** — no modulator currently has an outcome-level
   measurement.
7. **Move the brain runtime's 32 inactive-but-tested types** either onto the serving path
   or out of the build.

---

## 3. What the audit corrected in the earlier recon

The W93/W43 recon was text-based. The bytecode view disagrees with it in three places, and
the corrections matter:

| earlier claim (text search) | bytecode evidence |
|---|---|
| "`GoalManager` is used only by benchmarks" | `KnowledgeWorkbench` calls **six** of its methods: `checkpointGoal`, `completeSubtask`, `goals`, `integrity`, `resumableGoals`, `startGoal`. It is **served**. |
| "the connectome has zero production callers" | `Connectome` has 2 production callers — but both are inside the same closed cluster, whose root `SleepConsolidator` has **0**. The conclusion ("not served") stands; the reason was wrong. |
| "the chemistry island has no production caller" | `BiochemicalNetwork` has 4 callers, all inside the island, whose roots are experiment harnesses. The conclusion stands; the reason was wrong. |
| "`AttentionRouter` merges an empty impulse list" | `AttentionRouter` is called by `BrainLoopService.cycle` and `BirBrainCycle.cycle`; `Impulse` is referenced only by `AttentionRouter.merge`. The claim about the empty list was **not** confirmed by the bytecode and is marked unverified in this audit. |

---

## 4. The findings that will be false if they are not written down

1. **A green test suite does not mean a capability.** 482 production types are tested and
   unserved; 619 more are called and unserved.
2. **A class named after a cognitive function is not that function.** `StubBrainCycle` is a
   keyword matcher; `GpuTaskExecutor` echoes its input; `PhiId` computes a quantity over a
   graph.
3. **"Implemented" is not a state that survives contact with the call graph.** 619 types
   are implemented, tested and invisible.
4. **The repository has no forward model.** Every "prediction" in the serving path is a
   reconstruction, a similarity search, a divergence score, or the fixed-identity scalar
   `PredictionModel`.
5. **The measurement cluster cannot run.** `PhiId` and its siblings are reachable only
   from `ConsciousBrain`, which nothing reaches. A Φ_ID number can be produced by a test
   and cannot be produced by the system.
6. **"Bounded" is not "enforced".** Of the seven budget mechanisms the repository has,
   three are enforced at runtime (`ActionArena`'s per-cell budget and timeout, the
   consolidator's record count, and the `maxSearchNodes` cap in two methods). The W44
   cycle's 250 ms budget and `ComputeBudget`'s wall-clock, CPU and memory limits are
   **declarations** — `ComputeBudget`'s own javadoc says so. Nothing promises that a
   cycle returns within 250 ms.
6. **The five taxonomies of memory and three goal models are not reconciled**, and each new
   wave adds another unless a decision is taken.

---

## 4bis. Cross-version archaeology

Every branch was compared against `develop` file by file. **The result is that no
unmerged implementation is hiding in a branch**, with two exceptions.

| branch | commits ahead of `develop` | files it changes | files **not** in `develop` |
|---|---|---|---|
| `feature/liquid-federation-dynamic-modulators` | 7 | 10 | **0** — the W347-W352 work was landed in `develop` by a different commit series |
| `feature/mind-w4..w7`, `feature/true-w0..w13`, `cherry/*` | 1-3 each | 2-11 each | **0** |
| `feature/mind-w2-persistent-mind` | 1 | 7 | **3** |
| `feature/mind-w3-sleep-consolidation` | 1 | 5 | **3** |
| `feature/t-01..t-10` | 0 | — | — |

The `liquid-federation-dynamic-modulators` branch looks like seven lost commits and is
not: `develop` carries the same waves (`WAL: W347 …`, `WAL: W348 …`, and a cherry-pick
PR that re-landed four artefacts). `develop`'s `DESIGN-67` is 314 lines against the
branch's 310 — the newer version.

### The five files that exist only on a branch

| file | what it did | what replaced it | what was lost |
|---|---|---|---|
| `io.matrix.brain.runtime.ConsolidationCycle` (MIND-W3) | replayed the episodic log; **promoted recurring patterns to a canonical doc id**; **tombstoned** stale low-confidence entries; produced a dream report | `io.matrix.sleep.SleepCycle` (importance-based L1→L2 tier promotion) and `io.matrix.brain.runtime.RealSleepScheduler` (replay + `RuleInductionEngine`) | **frequency-based canonicalisation** and **tombstone pruning** never landed. The landed `SleepCycle` promotes by importance, not by recurrence; the landed `RealSleepScheduler` induces BIR rules into a `BirKnowledgeBase` with a quarantine, which is a different mechanism again. |
| `io.matrix.brain.runtime.SleepScheduler` (MIND-W3) | the sleep scheduler | `io.matrix.brain.runtime.RealSleepScheduler` | — (the replacement is richer) |
| `SleepAndConsolidationIntegrationTest` (MIND-W3) | the integration test for the above | — | **the test was not landed** — the consolidation path has no integration test in `develop` |
| `io.matrix.brain.runtime.GoalTracker` (MIND-W4) | PENDING/IN_PROGRESS/COMPLETED/ABANDONED with **progress tracking** (`double progress`, `withProgress`, `updateProgress`) | `io.matrix.goals.GoalTracker` (ACTIVE/COMPLETED/ABANDONED, `add(description, priority)`) | **partial progress cannot be expressed** in the landed tracker |
| `io.matrix.brain.runtime.InboxWatcher` (MIND-W4) | an inbox of stimuli for the autonomy loop | — | **the inbox never existed** in `develop` |

**For every recovered idea the objective asks two questions:**

| recovered idea | does the implementation still exist? | was the failure corrected? |
|---|---|---|
| consolidation over the episodic log | yes, in a different form (`RealSleepScheduler` + `RuleInductionEngine`) | partly — the quarantine and the persistence-failure counters are new and better; the frequency canonicalisation is gone |
| promotion to a canonical id | **no** | no |
| tombstone pruning instead of deletion | **no** — though the knowledge store's consolidator independently marks rather than deletes, which is the same principle in the other store | n/a |
| goal progress tracking | **no** | no |
| a stimuli inbox | **no** | no |
| GPU acceleration | `RealGpuKernelEngine`, `GpuTaskExecutor` exist | **no** — the CUDA provider still fails to load and the executor still echoes its input |
| the persistent mind | `PersistentHdcStore`, `EpisodicLog` exist and are served | yes |

### The schema migrations in the history

`KnowledgeStore`'s migration chain is a descending `if (version == N)` sequence, so every
schema change is a branch, not a diff. The audit found the W44 addition (schema 16, the
`epistemic_case` table and the `evidence.case_id` column) and the W43 additions before it.
**The chain is correct, but it is a chain of branches in which a missing branch silently
skips a version** — a shape that fails open. That is recorded here as a structural risk,
not as a defect: no version has been skipped.

---

## 4ter. How to read every claim in this audit

The objective requires the final report to separate six kinds of statement. Here is where
each kind lives, and what it is worth.

### Facts from source

Anything in `W44-CLASS-INVENTORY.csv` (53 columns per type) and
`W44-SUBSYSTEM-EVIDENCE.md`. These are read out of the source and the bytecode; they can be
re-derived by running the three scripts. Examples: `io.matrix.neuron.BitLinear` has 181
types in its package; `KnowledgeStore` has 12 production callers;
`PredictionModel` fills its weights with `1.0`.

### Actual reachable behaviour

The `lifecycle` column: **served** (860 types) means a path of production calls from a CDI
bean, a JAX-RS resource or a `main` reaches the type. Everything else is `island` (619),
`inactive-but-tested` (482) or `dormant` (452). The method is in §0.

### Measured behaviour

Only three things in this repository have a measurement with a stated denominator:

| measurement | where | denominator |
|---|---|---|
| the W44 portfolio's cost and accuracy trade-offs | `W44-BENCHMARK.md` §2 | a fixed corpus, a fixed seed |
| the W44 regulatory layer's effect on the cost weight | `W44-BENCHMARK.md` §3 | a load curve over ten samples |
| the W44 micro-world's survivor counts and information gain | `W44-BENCHMARK.md` §4 | four observations |

Everything else in the repository's documents is a **claim without a denominator**. The
audit's claim index (`W44-CLAIM-EVIDENCE.csv`) marks 104 claims as measurements; none of
them is backed by an executed XML file this audit produced, and the audit did not verify
their denominators.

### Synthetic experiments

The following produce an answer that is not a computation of the world:

| what | where | why it is synthetic |
|---|---|---|
| the gateway's stub cycle | `io.matrix.api.brain.StubBrainCycle` | keyword matching; "hello" → a canned sentence |
| the GPU executor | `io.matrix.federation.gpu.GpuTaskExecutor` | echoes its input |
| the analogy stage | `io.matrix.brain.runtime.stages.AnalogyStage` | a seed table |
| the signal stage | `io.matrix.brain.runtime.stages.SignalStage` | FNV-1a per token |
| the reflex stage | `io.matrix.brain.runtime.stages.ReflexStage` | a keyword table with an early return |
| the cognition metrics | `io.matrix.consciousness.*Metrics`, `PhiId` | they compute over graphs, and the cluster is unreachable |
| the W44 kernel's environment | `io.matrix.cognitive.microworld.LampEnvironment` | a designed world with a hidden rule — which is exactly what it declares itself to be |

### Architectural proposals

The documents that describe a thalamus, a hippocampus, a cerebellum, a prefrontal cortex,
a basal ganglia, an amygdala, an insula and a global workspace. The
`W44-PERCEPTION-AND-REPRESENTATION-MAP.md` and this document list what each of them
corresponds to in code — and for most, the answer is "nothing, or a hash".

### Unresolved hypotheses

16 hypotheses from the W44 research waves (H-113…H-128), of which **13 are open**; the
formal registry's 14 specifications, of which 12 claim a production path and 2 do not; and
the 21 owner decisions this audit did not take.

### False or stale claims

Listed in `W44-DOCUMENT-RECONCILIATION.md` §4 and §5, and in §3 above. The audit's own
first reading was wrong three times and each correction is recorded: `GoalManager` is
served; the connectome and the chemistry island are islands rather than uncalled; and the
signal-module family exists rather than being absent.

---

## 5. Prioritisation

Ordered by measurable capability per unit of risk, not by the number of classes that could
be connected.

| # | candidate | capability benefit | reuse | missing work | risk | measurement effort | blockers |
|---|---|---|---|---|---|---|---|
| 1 | **one stage in the brain runtime that consults the W44 kernel** | the brain gains a real prediction/observation loop with provenance | high: two HTTP surfaces exist, `BrcStep` exists, the kernel store exists | one stage class + the conductor decision | medium: the kernel's HTTP surface is in another process | medium: prediction-error rate before/after | decision 1 |
| 2 | **the `kill -9` journey script for the cognitive scenario** | turns an in-process assertion into a durability claim | high: the W43 journey script is a template | one script | low | low | none |
| 3 | **connect the connectome cluster to the sleep path** | gives the 159 tested types a purpose | high: the cluster is complete and tested | fix the anneal guard, one wiring | medium: documented defect | medium: consolidation quality | decision 1 |
| 4 | **one owner for goals** | removes a four-way split that every new wave worsens | medium: `GoalManager` is already served | a decision, a migration | medium | low | owner decision |
| 5 | **a modulator with an outcome measurement** | turns neuromodulation from decoration into mechanism | medium: the hub is served | an experiment harness | low | high | none |
| 6 | **wire the `io.matrix.neuron` graph algorithms beyond the portfolio** | gives the substrate real use | high: already wrapped | decision | low | low | none |
| 7 | **GPU provider discovery** | makes a hardware claim possible | low | read the provider list, assert it | low | low | none |
| 8 | **merge the two serving chains** | the "one brain" story | — | a security decision, a migration, a new ADR | **high** | high | owner decision |
| 9 | **enforce the cycle budget** | turns "bounded" into "bounded" | high: `ActionArena` already does this | a nanosecond supplier + two phase checks | low | low: a test that the budget is honoured | none |

---

## 6. What this audit did not do

- It did not change a production source file, a normative file or a build file.
- It did not *intend* to run the full `matrix-core` suite (D-017 reserves it; the run is
  an OOM risk), and one Gradle test filter inadvertently did exactly that — see §1.1bis,
  where the result is reported in full. The executed evidence is in `W44-TEST-MAP.csv`, and
  a test class with no executed XML is reported as `not-executed`, never as passing.
- It did not commit or push.
- It did not resolve the owner decisions listed in §2.20.

---

## 7. How to re-run this audit

The audit is a script, not a narrative. From the repository root:

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew compileJava compileTestJava          # the bytecode the graph is read from
python3 scripts/w44-audit-inventory.py         # class inventory + call graph + census
python3 scripts/w44-audit-docmap.py            # test map + doc map
python3 scripts/w44-audit-subsystems.py > docs-v2/engineering/W44-SUBSYSTEM-EVIDENCE.md
python3 scripts/w44-audit-claims.py            # claim → evidence index
```

All five are read-only with respect to the source tree; they write only under
`docs-v2/engineering/`. The call-graph extraction caches its bytecode scan in
`/tmp/w44-callgraph-cache.json`; delete that file to force a re-extraction.

**What re-running will and will not reproduce.** The lifecycle classification, the call
graph and the census are deterministic for a given set of compiled classes. The test map's
"executed" column depends on which test runs have left their JUnit XML behind, so it
reflects the runs that happened, not a promise.
