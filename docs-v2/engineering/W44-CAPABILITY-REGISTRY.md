# W44 — Capability registry


**Status:** `SUPERSEDED` (2026-11-04) → **`docs-v2/architecture/CAPABILITY-REGISTRY.yml`**.
This registry is the W44 audit's base and uses a different vocabulary from the architecture
registry. It also carries two labels that the bytecode has since disproved: it marks
`io.matrix.api.MinimalHttpServer` as *inactive-but-tested* (the class has a `main` at
`MinimalHttpServer.java:1674`), and its cycle map names five cycles where the tree has six.
**The document is kept as the audit's evidence and is not rewritten.**

**Audit:** RECON-W44.0 · 2026-11-02 · branch `develop` · HEAD `81f08478`
**Evidence:** `W44-CLASS-INVENTORY.csv` (source scan) + `W44-CALL-GRAPH.csv` (bytecode,
`javap -p -c`) · generator `scripts/w44-audit-inventory.py` (read-only)

**States** (a capability may hold several):

| state | meaning | decided by |
|---|---|---|
| IMPLEMENTED | a production type exists and its body does what its name says | source scan |
| TESTED | ≥1 test class references the type | `W44-TEST-MAP.csv` |
| SERVED | a path of production calls leads from a CDI bean / JAX-RS resource / `main` to the type | bytecode graph |
| ISLAND | production callers exist, but none of them is served | bytecode graph |
| INACTIVE | no production caller; tested | bytecode graph |
| DORMANT | no production caller, no test reference | bytecode graph |
| MEASURED | a measured number exists, with its denominator, in a document that states the method | document cross-check |
| DEMONSTRATED | observed working in a real run of the packaged application | the W44 end-to-end run |
| EXPERIMENTAL | marked experimental by its own module or by the `llm_quarantine` package | source/document |
| PLANNED | planned in a document; no implementation | document cross-check |

**The population** (definitions in `RECON-W44-ARCHAEOLOGY.md` §1):

| | count |
|---|---|
| production types in the 14 built modules | 2413 |
| served | **860** |
| island | 619 |
| inactive-but-tested | 482 |
| dormant | 452 |
| entry points (types the runtime can instantiate) | **211** |
| — CDI beans | 96 |
| — JAX-RS resources | 36 (of which the gateway's 8 are **never mounted**) |
| — `main` methods (CLI) | 79 |

---

## 1. Cognitive execution

| capability | type | states | evidence |
|---|---|---|---|
| the brain cycle | `io.matrix.brain.runtime.TrueMindCycle` | IMPLEMENTED, TESTED, SERVED | 3 production callers; reached from `ProductionBrainClient.cycle`, `MindRuntimeCli.main`, `ActuationStage.handle` |
| the mind cycle | `io.matrix.brain.runtime.MindCycle` | IMPLEMENTED, TESTED, SERVED | 1 caller (`MindRuntimeCli`) |
| the BIR cycle | `io.matrix.brain.BirBrainCycle` | IMPLEMENTED, TESTED, SERVED | 3 callers; produces `BrainCycle.CycleResult` (15 callers) |
| the consciousness loop | `io.matrix.reasoning.ConsciousnessLoop` | IMPLEMENTED, TESTED, SERVED | 1 caller |
| the brain loop service | `io.matrix.consciousness.BrainLoopService` | IMPLEMENTED, TESTED, SERVED | **25 production callers, 57 test references — the most connected class in the repository** |
| the reasoning brain loop | `io.matrix.reasoning.BrainLoopService` | IMPLEMENTED, TESTED, SERVED, event observer | 1 caller + an observer entry point |
| the brain loop V2 | `io.matrix.consciousness.BrainLoopServiceV2` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers, 1 test reference |
| the brain loop topology | `io.matrix.consciousness.BrainLoopTopology` | IMPLEMENTED, TESTED, **INACTIVE** | 0, 1 |
| the conscious brain | `io.matrix.neuron.ConsciousBrain` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers, **19** test references |
| adaptive compute | `io.matrix.consciousness.CognitiveAdaptiveCompute` | IMPLEMENTED, TESTED, **ISLAND** | 1 caller, and that caller is not served |
| the epistemic workbench | `io.matrix.knowledge.KnowledgeWorkbench` | IMPLEMENTED, TESTED, SERVED, DEMONSTRATED | 14 callers; a CDI bean |
| the gateway facade | `io.matrix.api.resource.AnalyzeResource` | IMPLEMENTED, TESTED, **declared but unmounted** | the module applies no Quarkus plugin; it would call `StubBrainCycle` |
| the gateway's real entry | `io.matrix.api.MinimalHttpServer` | IMPLEMENTED, TESTED, SERVED | `main`; calls `ProductionBrainClient` → `TrueMindCycle` |
| the stub cycle | `io.matrix.api.brain.StubBrainCycle` | IMPLEMENTED, TESTED, SERVED, **EXPERIMENTAL** | returns a canned answer for "hello"; keyword matching |
| the cognitive kernel (W44) | `io.matrix.cognitive.CognitiveKernel` | IMPLEMENTED, TESTED, SERVED, DEMONSTRATED | 2 callers; `POST /v1/cognitive/**` |

**Verdict: five coexisting cycles, one of which is a stub.** The ADR for "one serving
conductor" is not satisfied by the code.

---

## 2. Perception and signal processing

| capability | type | states | evidence |
|---|---|---|---|
| text encoding | `io.matrix.perception.TextEncoder` | IMPLEMENTED, TESTED, SERVED | — |
| the unified vector | `io.matrix.multimodal.UnifiedRepresentation` | IMPLEMENTED, TESTED, SERVED | a container of embeddings |
| cross-modal alignment | `io.matrix.multimodal.CrossModalAligner` | IMPLEMENTED, TESTED, SERVED | aligns vectors, not meanings |
| saliency | `io.matrix.perception.SaliencyEngine` | IMPLEMENTED, TESTED, SERVED | — |
| sensor packets | `io.matrix.signals.SensorPacket` | IMPLEMENTED, TESTED, SERVED | — |
| audio signal module | `io.matrix.signals.AudioSignalModule` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| the vision brain | `io.matrix.perception.BrainVision` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers — the only class that might preserve pixel content |
| the operator's signal spec | `io.matrix.operator.SignalModuleSpec` | IMPLEMENTED, TESTED, **INACTIVE** | — |

**The named `SignalModule` / `SignalModuleRegistry` / `SignalRegistry` /
`TextSignalModule` / `ImageSignalModule` family does not exist** in any built module. The
audit found `AudioSignalModule` and `SignalModuleSpec` and nothing else of that shape.

**Information loss, located.** The irreversible step is the embedding: `TextEncoder` and
`TextEmbedder` produce vectors; `CrossModalAligner` aligns vectors; nothing on the serving
path maps a vector back to a statement. What a vector does not carry is lost before any
cognitive process runs.

---

## 3. Neural and computational substrate

| family | representative types | exact / approximate | trainable | served |
|---|---|---|---|---|
| HDC | `HdcBrain` (8 callers), `HdcEncoding`, `HdcBinding`, `SparseHDC` | approximate (hashed) | yes | yes |
| BIR | `io.matrix.bir.Bir`, `BirClassifier`, `BooleanRuntime`, `TruthTableAdapter`, `ClauseSetForm`, `BddForm` | exact | yes | yes |
| Tsetlin | `io.matrix.tsetlin.*`, `WisardProducer` | exact (learning automaton) | yes | yes |
| MPDT | `io.matrix.evolution.MpdtGaProducer`, `io.matrix.neuron.MpdtHdcBridge` | learned | yes | yes |
| Hebbian | `io.matrix.neuron.HebbianUpdater`, `io.matrix.knowledge.HebbianBindings` | learned | yes | yes |
| SDM | `io.matrix.memory.Pattern.Sdm`, `SdmReader` | learned | yes | **dormant** |
| BitLinear / BitNet | `io.matrix.imports.BitLinearTrainer`, `BitLinearGpu`, `io.matrix.distill.BitNetEncoder`, `io.matrix.neuron.BitNetAutoregressive` | learned | yes | yes |
| — the `BitLinear` class itself | `io.matrix.neuron.BitLinear` | learned | yes | **ISLAND** — 2 callers, both inside `io.matrix.neuron` |
| graph algorithms | `io.matrix.neuron.Dijkstra`, `BellmanFord`, `Trie`, `BloomFilter` | exact / probabilistic | n/a | **only** via the W44 portfolio |
| converters | `TtToClauseSetConverter`, `TtToBddConverter`, `BddToTtConverter` | exact | n/a | mixed — `TtToBddConverter` is INACTIVE |

**`io.matrix.neuron` holds 181 production types.** Four of them (`Dijkstra`, `BellmanFord`, `Trie`,
`BloomFilter`) are reachable through the W44 portfolio wrapper, and `Dijkstra` has exactly
**one** production caller: `DijkstraShortestPathMethod`. The library is not a portfolio;
it is a library that the portfolio now borrows four items from.

---

## 4. Prediction and self-model

| capability | type | states | what it actually computes |
|---|---|---|---|
| predictive coding | `io.matrix.neuron.PredictiveCoder` | IMPLEMENTED, TESTED, SERVED | its own reconstruction error |
| the self model | `io.matrix.neuron.SelfModel` | IMPLEMENTED, TESTED, SERVED | the system's own state |
| free energy (neuron) | `io.matrix.neuron.FreeEnergyEvaluator` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| free energy (runtime) | `io.matrix.brain.runtime.FreeEnergyEstimator` | IMPLEMENTED, TESTED, SERVED | a divergence score |
| variational free energy | `io.matrix.consciousness.VariationalFreeEnergy` | IMPLEMENTED, TESTED, SERVED | — |
| Wu-wei policy | `io.matrix.neuron.WuWeiPolicy` | IMPLEMENTED, TESTED, SERVED | — |
| active inference | `io.matrix.embodied.ActiveInferenceEngine` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| embodied NCA | `io.matrix.neuron.EmbodiedNcaCortex`, `SensorimotorLoop`, `SensorimotorCycle` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| the self-prediction model | `io.matrix.consciousness.PredictionModel` | IMPLEMENTED, TESTED, **SERVED** | 5 callers (`BrainLoopService`, `BirBrainCycle`, the quarantined LLM service). **Its weight matrix is a fixed identity** (`Arrays.fill(weights, 1.0)`), and it predicts the *sum* of the state vector, so its error term can only see a wrong magnitude, never a wrong direction |
| `io.matrix.neuron.FreeEnergyLoss` | IMPLEMENTED, TESTED, **ISLAND** | 1 caller, not served |
| `io.matrix.neuron.PragmaticTest` | IMPLEMENTED, TESTED, **ISLAND** | — |

**No production class predicts the effect of the system's next action on an external
state.** Every prediction on a serving path is a reconstruction, a similarity search or a
divergence score.

---

## 5. Neuromodulation and regulation

| capability | type | states | evidence |
|---|---|---|---|
| the brain hub | `io.matrix.brain.runtime.graph.NeuroModulatorHub` | IMPLEMENTED, TESTED, SERVED | 3 production callers, inside `TrueMindCycle` |
| the chemistry network | `io.matrix.federation.liquid.biochemistry.BiochemicalNetwork` | IMPLEMENTED, TESTED, **ISLAND** | 4 callers, all inside the island |
| the chemistry orchestrator | `io.matrix.federation.liquid.biochemistry.BiochemicalOrchestrator` | IMPLEMENTED, TESTED, **ISLAND** | — |
| the kinetic modulator | `io.matrix.federation.liquid.KineticModulator` | IMPLEMENTED, TESTED, **ISLAND** | — |
| the modulation bridge | `io.matrix.federation.integration.CognitiveModulationBridge` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| the mediator | `io.matrix.federation.integration.BiochemicalMediator` | IMPLEMENTED, TESTED, SERVED | 1 caller |
| the dynamic registry | `io.matrix.federation.registry.DynamicModulatorRegistry` | IMPLEMENTED, TESTED, SERVED | — |
| neurotransmitters | `io.matrix.neuron.Neurotransmitter` | IMPLEMENTED, TESTED, SERVED | dopamine/serotonin/norepinephrine/acetylcholine and the rest |
| arousal | `io.matrix.consciousness.ArousalDynamics` **and** `io.matrix.reasoning.ArousalDynamics` | IMPLEMENTED, TESTED, SERVED | **two classes, same name, two packages, both served** |
| impulses | `io.matrix.consciousness.Impulse` | IMPLEMENTED, TESTED, SERVED | 1 caller: `AttentionRouter.merge` — nothing else produces an impulse |
| attention routing | `io.matrix.consciousness.AttentionRouter` | IMPLEMENTED, TESTED, SERVED | 5 callers: `BrainLoopService`, `BirBrainCycle`, `LlmBrainLoopService` |
| the instance mediator | `io.matrix.mediator.InstanceMediator` | IMPLEMENTED, TESTED, SERVED | 6 callers |
| the driver state | `io.matrix.mediator.DriverState` | IMPLEMENTED, TESTED, SERVED | 6 callers |
| the impulse scheduler | `io.matrix.lifecycle.ImpulseScheduler` | IMPLEMENTED, TESTED, SERVED | — |
| the W44 regulator | `io.matrix.cognitive.neuromodulation.RegulationController` | IMPLEMENTED, TESTED, SERVED, **MEASURED** | the only modulator layer with a measured effect |

**Every candidate signal the directive names exists as a type.** What the audit shows is
that the *plumbing* is duplicated in four places and that **no modulator has an
outcome-level measurement**: no experiment in the repository compares a decision taken
with and without a modulator.

---

## 6. Goals, autonomy and action

| capability | type | states | evidence |
|---|---|---|---|
| the goal manager | `io.matrix.knowledge.GoalManager` | IMPLEMENTED, TESTED, SERVED | `KnowledgeWorkbench` calls 6 of its methods — **it corrects the earlier "benchmarks only" claim** |
| the goal tracker | `io.matrix.goals.GoalTracker` | IMPLEMENTED, TESTED, SERVED | — |
| the agent loop | `io.matrix.agent.AgentLoop` | IMPLEMENTED, TESTED, SERVED | — |
| the ReAct loop | `io.matrix.agent.react.ReActAgentLoop` | IMPLEMENTED, TESTED, SERVED | 1 caller |
| the autonomy engine | `io.matrix.autonomy.AutonomyEngine` | IMPLEMENTED, TESTED, SERVED | 5 callers; its launcher lives in `llm_quarantine` |
| self-improvement | `io.matrix.autonomy.SelfImprovingEngine` | IMPLEMENTED, TESTED, SERVED | — |
| the autonomy loop | `io.matrix.brain.runtime.AutonomyLoop` | IMPLEMENTED, TESTED, SERVED | — |
| the task cell | `io.matrix.lifecycle.TaskCell`, `TaskCellV2` | IMPLEMENTED, TESTED, SERVED | 2 callers |
| the FNL gate | `io.matrix.lifecycle.FnlGate` | IMPLEMENTED, TESTED, **INACTIVE** | `FnlGateV2` is served — the gate was replaced and the original kept |
| the action arena | `io.matrix.actions.ActionArena`, `ActionRegistry`, `PlanRunner` | IMPLEMENTED, TESTED, SERVED | `ActionArena` 2 callers |
| the action gate | `io.matrix.consciousness.ActionGate` | IMPLEMENTED, TESTED, SERVED | — |
| the task scheduler | `io.matrix.mediator.scheduler.TaskScheduler` | IMPLEMENTED, TESTED, SERVED | — |
| the conjugate budgeter | `io.matrix.budgeter.ConjugateBudgeter` | IMPLEMENTED, TESTED, SERVED, formally specified | 6 callers; `formal/ConjugateBudgeterDP.tla` |
| HADES | `io.matrix.hades.*` | IMPLEMENTED, TESTED, **ISLAND** | the CLI demo is the only caller |
| the W44 task | `io.matrix.cognitive.CognitiveTask` | IMPLEMENTED, TESTED, SERVED | a bounded internal task, deliberately not a `LearningGoal` |

**Four goal models and no reconciler.** The knowledge-store goal tables, `GoalManager`,
`GoalTracker`, the task-cell model and the W44 `CognitiveTask` all coexist.

---

## 7. Memory and learning

| capability | type | states | evidence |
|---|---|---|---|
| the knowledge store | `io.matrix.knowledge.KnowledgeStore` | IMPLEMENTED, TESTED, SERVED, MEASURED | 12 production callers; SQLite schema 16; WAL + `synchronous=FULL` |
| the workbench | `io.matrix.knowledge.KnowledgeWorkbench` | IMPLEMENTED, TESTED, SERVED, DEMONSTRATED | 14 callers |
| the retriever | `io.matrix.knowledge.KnowledgeRetriever` | IMPLEMENTED, TESTED, SERVED | 5 callers |
| hierarchical memory | `io.matrix.memory.HierarchicalMemory` | IMPLEMENTED, TESTED, SERVED | in-memory |
| persistent hierarchical memory | `io.matrix.memory.PersistentHierarchicalMemory` | IMPLEMENTED, TESTED, SERVED | — |
| the HDC store | `io.matrix.brain.runtime.PersistentHdcStore` | IMPLEMENTED, TESTED, SERVED | 9 callers |
| the episodic log | `io.matrix.brain.runtime.EpisodicLog` | IMPLEMENTED, TESTED, SERVED | 3 callers |
| the consolidator | `io.matrix.knowledge.KnowledgeConsolidator` | IMPLEMENTED, TESTED, SERVED | marks; never deletes |
| two-stage consolidation | `io.matrix.neuron.TwoStageConsolidator` | IMPLEMENTED, TESTED, SERVED | — |
| the dream replayer | `io.matrix.noosphere.DreamReplayer` | IMPLEMENTED, TESTED, SERVED | — |
| error-driven learning | `io.matrix.cognitive.ErrorDrivenLearner` | IMPLEMENTED, TESTED, SERVED | — |
| sleep consolidation | `io.matrix.brain.runtime.graph.SleepConsolidator` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| the connectome | `io.matrix.brain.runtime.graph.Connectome` | IMPLEMENTED, TESTED, **ISLAND** | 2 callers, both inside the closed cluster |
| the replay phase | `io.matrix.brain.runtime.graph.ReplayPhase` | IMPLEMENTED, TESTED, **INACTIVE** | — |
| the SDM | `io.matrix.memory.Pattern.Sdm`, `SdmReader` | IMPLEMENTED, TESTED, **DORMANT** | no callers at all |

**Two writers of persistent state are on a serving path**: the knowledge consolidator and
the W44 cognitive store. Everything else that persists either persists for the brain
runtime (HDC store, episodic log) or is unreachable.

---

## 8. Federation and distributed cognition

| capability | type | states | evidence |
|---|---|---|---|
| the noosphere | `io.matrix.noosphere.*` (36 types) | IMPLEMENTED, TESTED, SERVED | `NoosphereResource` is a REST entry point |
| consensus | `io.matrix.consensus.ConsensusEngine` | IMPLEMENTED, TESTED, SERVED | 4 callers |
| capability consensus | `io.matrix.federation.liquid.CapabilityConsensusEngine` | IMPLEMENTED, TESTED, SERVED | — |
| the ELSP channel | `io.matrix.federation.ElspChannel` | IMPLEMENTED, TESTED, SERVED | — |
| the stigmergy protocol | `io.matrix.federation.liquid.biochemistry.StigmergyProtocol` | IMPLEMENTED, TESTED, SERVED | — |
| the CRDT | `io.matrix.noosphere.CausalCrdt` | IMPLEMENTED, TESTED, **INACTIVE** | 0 production callers |
| the mesh | `io.matrix.noosphere.MeshFederation` | IMPLEMENTED, TESTED, **INACTIVE** | 0 |
| the epoch-aware causal set | `io.matrix.noosphere.EpochAwareCausalSet` | IMPLEMENTED, TESTED, **INACTIVE** | 0 |
| the orchestrator | `io.matrix.federation.orchestration.IntegratedFederation` | IMPLEMENTED, TESTED, **ISLAND** | 4 callers: `AdversarialAttackSuite`, `EmergentBehaviorMonitor`, `FederatedLearningEngine`, `SwarmScaleExperiment` |
| the GPU task executor | `io.matrix.federation.gpu.GpuTaskExecutor` | IMPLEMENTED, TESTED, SERVED, **must not be counted** | it echoes its input |

**Which components are real distributed mechanisms?** The ELSP channel, the consensus
engines and the stigmergy protocol are real and served. The CRDT, the mesh and the causal
set are implemented and unreachable. The orchestrator is reachable only from experiment
harnesses — and it is the root that keeps the whole biochemistry island alive.

---

## 9. Integration and measurement

| capability | type | states | evidence |
|---|---|---|---|
| the connectome | `io.matrix.brain.runtime.graph.Connectome` | IMPLEMENTED, TESTED, **ISLAND** | 159 tests, 0 served |
| the cognitive node | `io.matrix.brain.runtime.graph.CognitiveNode` | IMPLEMENTED, TESTED, SERVED | — |
| the synapse | `io.matrix.brain.runtime.graph.Synapse` | IMPLEMENTED, TESTED, SERVED | — |
| the replay phase | `io.matrix.brain.runtime.graph.ReplayPhase` | IMPLEMENTED, TESTED, **INACTIVE** | — |
| integration metrics | `io.matrix.consciousness.IntegrationMetrics` | IMPLEMENTED, TESTED, **ISLAND** | 3 callers: `CrossLevelPhi`, `InterAgentPhi` and `ConsciousBrain` — and `ConsciousBrain` has 0 callers |
| extended metrics | `io.matrix.consciousness.ExtendedIntegrationMetrics` | IMPLEMENTED, TESTED, **ISLAND** | 3 callers: two profile builders and `ConsciousBrain` |
| Φ_ID | `io.matrix.consciousness.PhiId` | IMPLEMENTED, TESTED, **ISLAND** | **exactly one caller, and it is the inactive `ConsciousBrain`** |
| inter-agent Φ | `io.matrix.consciousness.InterAgentPhi` | IMPLEMENTED, TESTED, **ISLAND** | 1 caller (`InterAgentPhiSnapshot`) |
| the genesis profile | `io.matrix.consciousness.CognitiveGenesisProfile` | IMPLEMENTED, TESTED, **ISLAND** | 41 production callers — all inside the same unreachable cluster |
| Φ_linGauss | — | **ABSENT** | no type with that name exists |

**What the metrics measure.** `IntegrationMetrics` counts graph structure; `PhiId`
computes an information-integral estimate over a partition; `InterAgentPhi` compares
agents. **None of them takes a cognitive quantity as an input**, and none is correlated
with any behavioural measure in the repository. They measure what their formulas measure,
and the formulas are about graphs.

**And the whole cluster is unreachable.** The island's root is
`io.matrix.neuron.ConsciousBrain` — 0 production callers, 19 test references. `PhiId`'s
only caller is that class. `io.matrix.consciousness` holds **297 types of which 11 are
served**: `ActionGate`, `ArousalDynamics`, `AttentionRouter`, `BrainLoopService`,
`PredictionModel`, `Impulse` and their nested types. Everything else — the metrics, the
profile builders, `CrossLevelPhi`, `BrainLoopServiceV2`, `BrainLoopTopology` — hangs off
the inactive root.

---

## 10. The registry in one table

The table counts the types the directive names **per family**, resolved to their real
FQNs; "named types found" excludes the names that do not exist in any built module.

| family | named types found | SERVED | ISLAND | INACTIVE | DORMANT |
|---|---|---|---|---|---|
| cognitive execution | 16 | 7 | 4 | 4 | 0 |
| perception and signal processing | 17 | 9 | 3 | 3 | 0 |
| neural and computational substrate | 23 | 9 | 1 | 3 | 0 |
| prediction and self-model | 14 | **1** | 5 | 7 | 0 |
| neuromodulation and regulation | 20 | 9 | 9 | 1 | 0 |
| goals, autonomy and action | 27 | 9 | 6 | 7 | 0 |
| memory and learning | 22 | 12 | 7 | 2 | 0 |
| federation and distributed cognition | 10 | **1** | 3 | 4 | 0 |
| integration and metrics | 14 | **0** | 9 | 4 | 0 |

**The integration-and-measurement family is entirely unserved (0 of 14).** The
prediction-and-self-model family has exactly one served type, and that type is a
self-prediction with a fixed identity weight matrix. The repository's two most brain-like
families are its two least served, and the one served prediction model cannot detect a
wrong direction — only a wrong magnitude.

(The counts here are derived from `W44-SUBSYSTEM-EVIDENCE.md`, which the script
`scripts/w44-audit-subsystems.py` regenerates from the inventory; they are not
hand-maintained.)
