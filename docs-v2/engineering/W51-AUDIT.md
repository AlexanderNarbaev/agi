# W51 — Repository audit and subsystem classification

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-08 · **Branch:** `develop`
**Evidence:** the four reconnaissance reports of this wave —
`docs-v2/engineering/W51-RECON-INVENTORY.md`,
`docs-v2/engineering/W51-RECON-W50-DEFECTS.md`,
`docs-v2/engineering/W51-RECON-INFRA-PLUGIN.md`,
`docs-v2/engineering/W51-RECON-ENVIRONMENT.md` — and the measurements taken in this wave.

**The rule of this document.** Every statement about the code is a statement about a class
that exists, with a path. Every statement about the serving of that class is a statement
about a **caller in `src/main/java`**, because "implemented" and "served" are different
facts, and the distance between them is the single most important fact about this
repository.

---

## 1. The state of the repository at the start of the wave

| | |
|---|---|
| the branch | `develop` |
| the head at the audit | `a098f283` — *WAL: W50 — the report records the pull request's CI job by job* |
| `develop` against `main` | **386 commits ahead, 0 behind.** `main` is `7a0ed0cc` (*RECON-W37.5 release*), and `develop` contains it. |
| the release pull request | **#47 is open** (`develop` → `main`). It was prepared by the W50 wave and **not merged**: the merge is the owner's decision. W51 does not merge it either. |
| the last merged wave | **W49** (`main` = `79045c57`, the squash of PR #44) |
| the wave before this one | **W50** — the local evidence-grounded demonstrator: a bounded, licensed corpus, 348 + 324 claims, a four-turn demonstration on the packaged jar, and a measured local-model evaluation. |
| the modules | 14 included in `settings.gradle`, 3 commented out, 2 outside Gradle |
| the serving entry points | 2 — the Quarkus application on 9091 (217 annotated routes after W51) and the brain runtime's JDK `HttpServer` on 8765 (28 served routes) |
| the databases | one canonical SQLite file for chain A (`data/knowledge/workbench.sqlite`, knowledge schema **v19**) and one for chain B (`data/mind/mind.sqlite`). W51 adds a third, **the bridge's own ledger** (`data/bridge/bridge.sqlite`), which holds no knowledge and has its own single writer. |

---

## 2. The classification

| # | the subsystem | the primary class | served? | the classification | the reason |
|---|---|---|---|---|---|
| 1 | **The chain boundary** | `io.matrix.api.CanonicalEntryPoint` | **yes** | **KEEP** | it names, in code, which class is the canonical entry point of which serving application. Two applications, two entry points, stated on purpose. |
| 2 | **The cognitive kernel** | `io.matrix.cognitive.CognitiveKernel` | **yes** — `CognitiveService:252`, `CognitiveResource:80` | **KEEP** | one cycle, ten phases, a durable trace, a durable episode. It is the system's real cognitive loop. |
| 3 | **The true mind** | `io.matrix.brain.runtime.TrueMindCycle` | **yes** — `ProductionBrainClient:129,332` | **KEEP** | chain B's brain, with the reflex gate, the harmful-intent gate and the BIR core. |
| 4 | **The legacy mind** | `io.matrix.brain.runtime.MindCycle` | **no** — CLI only; `CognitiveConductorGuardTest:100-108` forbids production use | **ARCHIVE** | dead. The comment at `ProductionBrainClient:325` still calls it "the primary path" and **that comment is wrong** — the field is `TrueMindCycle`. |
| 5 | **The learning workbench** | `io.matrix.knowledge.KnowledgeWorkbench` (2264 lines) + `KnowledgeStore` (4905) | **yes** — 58 HTTP methods | **KEEP** | the epistemic application: acquisition, retrieval, answering, revision, teaching, assessment, transfer. |
| 6 | **The acquisition** | `io.matrix.knowledge.acquisition.*` | **yes** — `LearningCycleResource:110` | **ADAPT** | it works, but it reads **bundled** corpora only (the plate-tectonics pool is inside the jar). W51 fixes its status, offset and source-identity defects (§4). |
| 7 | **BIR** | `io.matrix.bir.*` (25 classes) | **yes**, from both chains | **KEEP** | the healthiest subsystem in the repository; a shared substrate, not a path to any store. |
| 8 | **HDC** | `io.matrix.neuron.HdcBrain`; `matrix-brain-runtime`'s `HdcVector`, `PersistentHdcStore` | **partly** — `SparseHDC`, `HdcAsLlmPreprocessor` and `HdcRetrievalStage` have **no** production caller | **INTEGRATE** | two disconnected stacks that do the same thing. The chain-B one is served; the chain-A one is an island. |
| 9 | **Tsetlin** | `io.matrix.tsetlin.TsetlinTrainer` | **yes** — the sleep path, `AcceptanceModel:94` | **KEEP** | |
| 10 | **MPDT** | `io.matrix.evolution.MpdtGaProducer`; `matrix-micro/src/mpdt_neuron.c` | **partly** — the Java side is in the sleep path; **the C side is 16 lines with a stubbed `mpdt_evaluate_bool`, and `matrix-micro` is commented out of `settings.gradle`** | **REWRITE CANDIDATE** | the name is a lie in both languages: the Java class is a genetic algorithm over DNF clauses, not a decision-tree neuron, and the C is not a kernel. It must not be presented as a complete implementation. |
| 11 | **Memory** | `io.matrix.memory.*` (11 classes); chain B's `PersistentMind`, `EpisodicLog`, `PersistentHdcStore` | **partly** — only `PersistentHierarchicalMemory` is a CDI bean; `RecurrentSdm`, `SdmReader` and `MemoryHierarchy` are test-only | **ADAPT** (the served ones) / **ARCHIVE** (the orphans) | the six kinds of memory the mandate names are not six classes; they are six roles over the two stores that exist. |
| 12 | **The connectome** | `matrix-brain-runtime`'s `graph.Connectome` | **no** — tests only | **ARCHIVE** or **INTEGRATE** | 159 tests, 0 production callers. |
| 13 | **Goals, attention, regulation, budget** | `io.matrix.goals.GoalTracker`, `KernelRegulator`, `KernelSelector`, `DriveSignal`, `AttentionRouter`, `SaliencyEngine`, `ConjugateBudgeter`, `io.matrix.autonomy.*` | **yes**, except `BudgeterHomeostat` (0 callers) | **KEEP** the wired ones / **ARCHIVE** `BudgeterHomeostat` | the mandate's "do not treat constant neutral values as active attention" is a real defect here: the regulator starts at neutral and only moves on an error signal. |
| 14 | **The plugin / extension mechanism** | — | **there is none** | **BUILT IN THIS WAVE** | no `ServiceLoader`, no `META-INF/services`, no extension point, no classpath scanning, no sandbox-capable dependency in any of the sixteen build files. W51 adds `io.matrix.plugin` — see §5. |
| 15 | **Persistence, recovery, fencing** | `io.matrix.cognitive.{WriterLock,OwnershipToken,RequestExecutionState,RequestCheckpoint,IdempotencyRegistry}` | **yes** | **KEEP** | the fencing is real: an OS file lock, an owner record, an ownership classification, fence points in the store, and a recovery that never steals the work of a live process. **Single writer by design; no lease, no multi-writer.** |
| 16 | **Method selection** | `io.matrix.cognitive.algorithms.AlgorithmSelector` | **yes** — `CognitiveService:248-258`, `CognitiveKernel:410` | **KEEP** | it was already the right thing: a method is admitted only inside its declared preconditions, the score is computed from measured evidence and measured cost, the tie-break is the method id, and nothing admitted means a refusal. `ProcedureStore` has no production caller — **ARCHIVE**. |
| 17 | **`io.matrix.chain`** | `io.matrix.chain.{ChainRegistry,MultiChainEnsemble}` | **no** — 0 production callers | **ARCHIVE** | a third thing that shares a name with the serving chains and is not them: two of its safety predicates are hard-wired to `false` and one of its three strategies is unimplemented. |
| 18 | **The micro-world** | `io.matrix.cognitive.microworld.*` (12 classes) | **yes** — `CognitiveKernel` probes it | **KEEP** | a deterministic environment whose hidden rule is enforced by the language and guarded by a reflective test. It is the environment this wave learns in. |
| 19 | **Other environments** | `io.matrix.simulation.*`, `PilotGridWorld`, `GridWorldBridge`, `io.matrix.embodied.*`, `io.matrix.minecraft.*` | no | **ARCHIVE / DEFER** | `io.matrix.embodied.SensorimotorCycle` fabricates its own observations and its "act" is `rng.nextDouble() < 0.8`; `ActiveInferenceEngine` has no environment at all. A search of `src/main` for `MDP|transitionModel|markov` returns **zero** matches. |

### 2.1 The single most important fact

**The two serving chains did not meet.** Chain B reached **zero** of `io.matrix.cognitive.**`,
**zero** of the store's own classes, and **zero** of the classes that name the epistemic
database. The gate that proved it could go red, and had been seen going red.

That was a decision, not an accident — `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` §2 — and
the same document wrote down the contract a future bridge must satisfy (**BRIDGE-1 … BRIDGE-10**)
and required the gate to be extended to the bridge before the bridge was merged (BRIDGE-10).

**W51 implements that contract.** The bridge is the one crossing, and the gate now checks it
(§6).

---

## 3. What W50 left, measured

The W50 evaluation is 29 cases in `docs-v2/demos/local-learning-loop/EVALUATION-SET.md`. It was
**never machine-readable**, and **no runner was ever committed** — the W50 checkers lived in
`/tmp` and were lost. W51 §6 therefore begins by making both of those things exist.

**Measured on 2026-11-08, offline, against the packaged jar: 12 PASS / 17 FAIL** — identical to
W50's result, per case and per category. Per category: answerable 2/8, unanswerable 3/6
abstentions (2/6 strict), false-premise verdicts 3/5 with 0/5 promotions, multi-claim 1/3,
revision supersessions applied 4/4 where 0/3 were expected.

**All eight of the known defects were still present**, each with a measured instance:

| the defect | where it was | what was measured |
|---|---|---|
| D1 candidate claims enter answers unfiltered | `KnowledgeRetriever:340,386-388`; `Claim.isEstablished()` was called from nowhere | 569 of 571 cited claims were `CANDIDATE` with confidence 0.0; one answer cited a `SUPERSEDED` claim as evidence |
| D2 relevance confused with evidential support | `KnowledgeRetriever:386-388`; confidence never gated eligibility | P-01: 63 claims, 0 of 3 required; A-06 answered from the wrong document |
| D3 answers where it should abstain | `QuestionDemand:102` — `MINIMUM_DEMAND_COVERAGE = 0.0`; `KnowledgeWorkbench:634-637` | U-01, U-04 and U-06 were answered |
| D4 provenance without precise offsets | `acquisition/SourceRecord:65` — the locator defaults to "the whole document"; `CorpusImport:157-158` reported the scope as the span | 0 of 571 cited claims had a source with an exact locator. **Refinement by the W50 report:** the *claim* row does carry a segment locator (571/571), so the honest statement is "the source locator is absent and the claim locator is segment-level, not span-level". |
| D5 status counts empty | `KnowledgeWorkbench.status():2107-2136` reads the `workspace` table; `applyChanges:2428-2503` never writes it | six workspaces held 4080 claims and the `workspace` table held **0 rows**; no per-`ClaimStatus` breakdown existed anywhere |
| D6 sources rewritten in place | `KnowledgeStore.upsertSource:2525` is `INSERT OR REPLACE`; the id is derived from the locator, and the locator of every imported document is the same constant | `s-publication-66bb6e0c` was rewritten from one document to another; five claims pointed at it |
| D7 wrong supersessions | `ModelRevisionDecider.inForce:123` lets a `CANDIDATE` be superseded; `decide:78` picks the max-relatedness claim; the denial branch short-circuits the strength check | R-02, R-04 and R-05 applied 4, 5 and 2 supersessions where 0 were expected |
| D8 lexical heuristics beyond what they can establish | `ModelRevisionDecider.denies:168` + markers; `PremiseAssessment.denied:1116` + markers | F-02 answered `UNRESOLVED` where `CONTRADICTED` was expected; a marker in a pool document turned three cases into revisions against equally strong evidence (0.055 against 0.055) |

**The gates the defects name were absent from the data model too** — no offset column, no
source-version history, no per-status count. That is why the fix is a change to the write
path and not only to the read path.

---

## 4. The CI and technical-debt state, verified

| the issue | the state on 2026-11-08 | the evidence |
|---|---|---|
| **SpotBugs cannot read Java 25 bytecode** | **STILL PRESENT** | the plugin is `com.github.spotbugs` **6.1.7** (`matrix-core/build.gradle:4`) with `toolVersion = '4.9.3'` (`:317`); 4.9.3 bundles ASM 9.7.1, whose maximum class-file major is 68, and the project compiles to 69. The CI step is honest about producing no evidence; the job yields **no static analysis**. |
| **TruffleHog: five unverified PostgreSQL-like findings** | **STILL PRESENT as findings, and they are NOT secrets** | the five strings live in `.veai/skills/**` documentation (`deployment-patterns/SKILL.md:340`, `docker-patterns/SKILL.md:39,162`, `prisma-patterns/SKILL.md:221,224`) and are placeholders — literal `user`/`pass`/`host`/`db`, or the Compose default `postgres:postgres` of a documentation example. There is no `.trufflehog.yml` and no ignore file. **The finding count is unchanged and the exposure is zero.** |
| **ShellCheck** | **STILL PRESENT** | **106 findings across 41 files**, measured with ShellCheck 0.11.0 over the 63 tracked `.sh` files (1 error, 50 warnings, 55 notes; the top codes are SC2086 ×18, SC2155 ×16, SC2034 ×13, SC2012 ×12). The workflow triggers on `main` only, which is why it stayed dark on `develop`. |
| **The native-image job dies of memory exhaustion** | **STILL PRESENT** | `.github/workflows/native.yml:28-34` sets **no memory limit at all**; the only ceiling that reaches native-image is `-J-Xmx8g` (`matrix-core/build.gradle:383`) together with `--gc=epsilon` (`:387`, which collects nothing, so 8 GiB is a hard wall). The recorded failure is `java.lang.OutOfMemoryError: Java heap space` **with `-Xmx8g`**. Two further defects: the job triggers on `main` only, and its verify step globs `matrix-core/build/*-runner` while Quarkus produces `build/native/nativeCompile/matrix-core`. |
| **The magic-literal baseline** | **PRESENT AND CORRECT** | `docs-v2/quality/quality-baseline.txt:1` = `literal_candidates=271`; the measured count is **271** (78 shell + 193 java) and the gate exits 0 — green **at** the baseline. The debt is unchanged, not repaid; the gate blocks on an increase, never on a decrease. **W51 adds zero candidates** (measured: 271 after the wave's code was in place). |

**Nothing was suppressed.** No workflow was edited: `.github/workflows/**` is a frozen zone
under the repository's own `AGENTS.md`, and the W51 mandate says not to weaken a gate to
obtain a green pipeline. The five findings above are reported, not silenced.

---

## 5. What W51 built, and where it lives

| the deliverable | the code | the evidence |
|---|---|---|
| **The canonical cognitive contracts** | `io.matrix.contract` — 23 records: Entity, Relation, Property, WorldState, StateTransition, Event, Observation, Evidence, Belief, Hypothesis, Prediction, Experiment, Action, Outcome, Goal, Task, Skill, Procedure, Algorithm, Plugin, Evaluation, Reflection, plus the envelopes (Command, Event, Response, Correlation), Confidence, Provenance, ValidityInterval, BeliefStatus, Capability | `CanonicalContractsTest` — **33 tests, 0 failures** |
| **The bridge** (BRIDGE-1…10) | `io.matrix.contract.bridge` (the protocol, pure), `io.matrix.bridge` (the chain A service, the sink, the durable journal, the HTTP surface), `io.matrix.brain.runtime.bridge` (the chain B client) | `BridgeServiceTest` — **13 tests, 0 failures**; the gate §6 |
| **The shared runtime** | `io.matrix.runtime.{CognitiveRuntime,RuntimeComponent,MatrixRuntimeComposition}` | `CognitiveRuntimeTest` — **6 tests, 0 failures** |
| **The learning environment** | `io.matrix.learning.env.*` over the existing `io.matrix.cognitive.microworld` | `MicroWorldEpisodeRunnerTest` + `EpisodeComparisonTest` — **13 tests, 0 failures** |
| **The world-state memory** | `io.matrix.cognitive.worldstate.*` — the estimate of a domain, its transitions, and the micro-world's adapter | `WorldStateMemoryTest` — **6 tests, 0 failures**; the kernel consults it before it acts and records the transition in the trace |
| **The integrated cognitive loop** | `io.matrix.cognitive.CognitiveKernel` + the world-state memory + the durable case log | `W51DemonstrationTest` — **5 tests, 0 failures**; the demonstration narrows sixteen hypotheses to one **through the runtime** and reads them back after a restart |
| **The write path's transaction mode** | `KnowledgeStore` and `CognitiveStore` — both open the **same** file, and both wrote DEFERRED transactions | `ObservationDispositionDurabilityTest` — the flake failed about one run in three before the fix and **eight of eight** after it; the wave's whole scope is **1 323 tests, 0 failures** |
| **The method-performance memory** | `CognitiveKernel` + `KernelSelector` + `CostLedger` — the kernel now measures the method it runs and records it | the demonstration's decision record: the first cycle says `DECLARED_COMPLEXITY`, the later ones say `MEASURED` — `W51DemonstrationTest` |
| **The cycle's reflection** | `io.matrix.cognitive.reflection.CycleReflection` — the canonical `Reflection` and `Evaluation` contracts now have a producer | `CycleReflectionTest` — **5 tests, 0 failures**; the cycle's trace carries a `REFLECT` phase and the outcome carries the reflection with its decisions |
| **The meta-memory** | `io.matrix.knowledge.meta.KnowledgeReliability` — a projection over the durable claims and revisions: the reliability of each source and each validation method | `KnowledgeReliabilityTest` — **4 tests, 0 failures**; it closes the last of §4's six memory roles that had no carrier, and it decides nothing about citability — that stays with the claim's own status |
| **The plugin architecture and creator mode** | `io.matrix.plugin.*` — registry, lifecycle, audit, service ownership, declarative sandbox, validator, creator mode | `PluginLifecycleTest` — **16 tests, 0 failures** |
| **The Java/C boundary** | `matrix-core/src/main/c/matrixkernel/`, `io.matrix.nativekernel.*`, `scripts/w51-native-kernel-bench.sh` | `NativeKernel*Test` — **31 tests, 0 failures**; `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md` |
| **The end-to-end demonstration** | `io.matrix.w51.W51Demonstration`, `./gradlew :matrix-core:w51Demonstration` | `docs-v2/engineering/W51-DEMONSTRATION.json` |

---

## 6. The gate, extended

The isolation gate of `W45.2-CHAIN-BOUNDARY.md` keeps its rules **G1**–**G6** unchanged and
gains three:

- **G7** — the shared contract layer is **pure**: no class of `io.matrix.contract.**` that
  chain B can reach may open a database, serve a route or reach a store. A shared class that
  can open a database is a class both processes can call to open one.
- **G8** — the bridge is the **only crossing**: no class may be reachable from chain B *and*
  from chain A's store, and chain B must not reach the chain A bridge server. The crossing
  happens over the network, not inside one class graph.
- **G9** — the chain B bridge client reaches **no** class of the store. The client is compiled
  into `matrix-core`, which chain B depends on, so the property is checked, not assumed.

**The measured run of 2026-11-08: 22 checks, 0 failed** — G1–G6 exactly as before, G7–G9
green.

**And the two new rules became non-trivial.** When G7 was first written, chain B reached
**0** classes of the contract layer, because the bridge client was not yet on any serving
path — the rule was green for the wrong reason. The publisher is now attached to the brain
runtime's own server (`MinimalHttpServer`), and the gate reports **19** contract classes
reachable from chain B, of which **0** can open a database, serve a route or reach a store.
G9 reports that the chain B bridge client **is** reachable (`true`) and that its closure
reaches **0** classes of the store. A rule that cannot fail is not a rule; these two can
now fail, and the control proves it.

**The negative control was extended and re-run: 12 checks, 0 failed.** Two new controls were
added, and both make the gate red:

- **C3** — a class of the shared contract layer that opens a database. The gate names the
  impurity of the contract layer (G7) and **nothing else**: the class reaches the persistence
  stack and not the epistemic store, so every other rule still holds. The control is of G7,
  not of G2.
- **C4** — a class of chain B that reaches the chain A bridge server. The gate names it
  (G8). "The bridge is the only crossing" is a tested statement rather than a promise.

**One definition was corrected during the wave.** The first version of G8 compared chain B's
closure with the store's whole closure and reported 36 "crossings" — the shared substrate
(`io.matrix.bir`, `io.matrix.neuron`, `io.matrix.tsetlin`, `io.matrix.compression`). That was
wrong, and `W45.2-CHAIN-BOUNDARY.md` §4.4 says why: the shared substrate is not part of the
store, and its presence in both closures says nothing about the isolation. G8 now uses the
store's **own** classes, which is the same distinction G2 uses.

---

## 7. What is still disconnected, experimental or archived

| the subsystem | the state | the decision |
|---|---|---|
| `io.matrix.chain` | 0 production callers; two safety predicates hard-wired to `false`; one strategy unimplemented | **ARCHIVE** — the strongest candidate, but the deletion is the owner's call |
| `io.matrix.neuron.{RecurrentSdm,SdmReader,MemoryHierarchy}` | test-only | **ARCHIVE** |
| `io.matrix.budgeter.BudgeterHomeostat` | 0 main references | **ARCHIVE** |
| `io.matrix.cognitive.algorithms.ProcedureStore` | 0 production callers; the procedure the kernel promotes is held by the **cognitive store** instead | **ARCHIVE** — but the *concept* survives, as `io.matrix.contract.Procedure` |
| `io.matrix.advanced.SparseHDC`, `io.matrix.neuron.HdcAsLlmPreprocessor` | test-only | **ADAPT** — the HDC stack has two implementations and one of them serves |
| `io.matrix.neuron.MpdtHdcBridge` | test-only | **REWRITE CANDIDATE** with MPDT |
| `graph.Connectome`, `SleepConsolidator`, `ReplayPhase` | 159 tests, 0 production callers | **INTEGRATE or ARCHIVE** |
| `io.matrix.brain.runtime.MindCycle` | CLI only, forbidden in production by a guard test | **ARCHIVE** |
| `io.matrix.embodied.*` | `SensorimotorCycle`'s action is a coin toss; `ActiveInferenceEngine` has no environment | **ARCHIVE** |
| `io.matrix.simulation.*`, `PilotGridWorld`, `GridWorldBridge` | real but GA fixtures; no prediction or learning loop | **DEFER** |
| `matrix-micro/src/mpdt_neuron.c` | 16 lines, a stubbed evaluator, and the module is commented out of the build | **REWRITE CANDIDATE** |

---

## 8. The honest limits of this audit

1. **The audit is static.** It resolves the calls the compiler emitted. It does not cover
   reflection, JNI, a native image built from a different class set, or a class loaded by
   name at runtime. The W45.2 document says this too; W51 did not make it better.
2. **The test population is unknown.** The full `matrix-core` suite is not run in this
   repository's practice — it needs the owner's approval because of the OOM risk. The
   baseline is a set of **scopes**, each verified on a named commit, and
   `docs-v2/engineering/TEST-CATALOGUE.yml` is the registry.
3. **The W50 evaluation was measured against the packaged jar and a reconstructed runner**,
   because no runner was committed. W51 §6 commits both the machine-readable dataset and the
   runner; the numbers above are the ones that runner produced on 2026-11-08.
4. **The demonstrations of this wave were run in one process**, deliberately. The
   cross-process claim is proved by the gate and by the bridge's own client and server; the
   component claim is proved by the demonstration. Mixing them would have made both weaker.
