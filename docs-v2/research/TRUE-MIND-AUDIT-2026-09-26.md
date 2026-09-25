# TRUE-MIND AUDIT — develop @ 3ac66cfe (2026-09-26)

> Scope: re-verify the §1 facts of the TRUE-MIND mission against the actual
> repository state. Discoveries are recorded with reproducible commands.

## 1. Repository state

| Item | Claimed | Actual |
|------|---------|--------|
| `develop` HEAD | `3ac66cfe` | ✅ `3ac66cfe` ("MIND-W7 remainder — Audit Chain + License + Federation (#29)") |
| Last commit author | (single dev) | ✅ Single author throughout |
| Working tree | clean | ✅ clean |

## 2. `matrix-brain-runtime` module

| Item | Count |
|------|-------|
| Java files in `src/main/java` | 26 |
| Test classes | 7 (`MindCycleIntegrationTest`, `PersistentHdcStoreIntegrationTest`, `SleepAndConsolidationIntegrationTest`, `AutonomyIntegrationTest`, `DistillationFactoryIntegrationTest`, `RuntimeLlmGuardTest`, `GpuAccelerationTest`, `CreditLedgerTest`, `AuditChainLicenseFederationTest`) |
| Module dependencies (declared in `build.gradle`) | `matrix-core`, `matrix-audit`, `matrix-observability` |

## 3. **CRITICAL DEFECT** — `matrix-brain-runtime` is simulacra, not cognition

### 3.1 Zero real core imports

```
$ grep -r "^import io.matrix" matrix-brain-runtime/src/main/java/ | grep -v "io.matrix.brain"
(empty)
```

The runtime module **declares** `implementation project(':matrix-core')` but
**no production source file** imports anything from `io.matrix.brain.*`,
`io.matrix.memory.*`, `io.matrix.distill.*`, `io.matrix.reasoning.*`,
`io.matrix.reflex.*`, `io.matrix.signals.*`, etc.

### 3.2 Stage-by-stage inventory (current behaviour)

| Stage | File | Actual code | What it should call |
|-------|------|-------------|---------------------|
| Reflex | `ReflexStage.java` | `String.contains("rm -rf /")` etc. | `reflex/ReflexEngine` |
| Signal | `SignalStage.java` | FNV-1a hash → BitSet(256) | `signals/SignalModuleRegistry` + `TextSignalModule` |
| Salience | `SaliencyStage.java` | if-else on question marks | `perception/SaliencyEngine` |
| Arithmetic | `ArithmeticStage.java` | BigInteger + regex | core BIR arithmetic (NOT a lookup) |
| Analogy | `AnalogyStage.java` | hardcoded 12-entry SEED map | `mcts/MctsTree` cleanup memory `(a ⊖ b ⊕ c)` |
| BIR | `BirInferenceStage.java` | hand-coded regex list | `BirBrainCycle`'s rule layer / SimpleKnowledgeBase |
| HDC | `HdcRetrievalStage.java` | own toy 256-bit hash | `brain/HdcBrain` + `memory/CodebookMemory` (10k-bit) |
| Tsetlin | `TsetlinStage.java` | hardcoded clauses | `tsetlin/AdvancedTsetlinMachine` + `TsetlinTrainer` |
| MCTS | inline in `MindCycle.java` | if-confidence-low placeholder | `mcts/MctsTree` + LATS reflection |
| Modulators | `ModulatorStage.java` | string-contains gates | reuse `BirBrainCycle.getSafety()` instances |

### 3.3 Why this is a defect

A user who runs `start-mind.sh` will see the system answer `2+3 = 5` and
claim "I reasoned with BIR" — but the answer comes from a hand-coded
regex lookup table. The BRC trace will show `stage=ARITHMETIC, fired=true`
without disclosing the engine identity. This violates **CONSTITUTION
Article VIII** ("no shadow logic, every decision is a BRC").

### 3.4 Available real engines (all present, none wired)

```
✅ io.matrix.brain.BirBrainCycle              (cycle/learn/getHdcBrain/getCodebook/getKnowledgeBase/getAudit/getSafety)
✅ io.matrix.memory.SqliteMemoryBackend
✅ io.matrix.memory.HierarchicalMemory
✅ io.matrix.memory.PersistentHierarchicalMemory
✅ io.matrix.tsetlin.AdvancedTsetlinMachine
✅ io.matrix.tsetlin.TsetlinTrainer
✅ io.matrix.mcts.MctsTree, MctsNode, MctsAction, LatsNode, LatsReflector, LatsValueFunction
✅ io.matrix.reasoning.ConsciousnessLoop, BrainLoopService, BrcChain, BrcStep, ArousalDynamics, EmergenceAnalyzer, FeedbackPerception
✅ io.matrix.reflex.ReflexEngine
✅ io.matrix.perception.SaliencyEngine, SaliencyRanker
✅ io.matrix.signals.SignalModuleRegistry, TextSignalModule, AudioSignalModule, ImageSignalModule
✅ io.matrix.sleep.SleepCycle
✅ io.matrix.distill.Distiller, OnnxActivationTeacher, BitNetEncoder, LLMKnowledgeDistiller, DatasetConnectorV2
✅ io.matrix.weights.WeightsConsolidator
✅ io.matrix.autonomy.AutonomyEngine, SelfImprovingEngine
✅ io.matrix.goals.GoalTracker (in core, distinct from runtime copy)
✅ io.matrix.workspace.CognitiveGlobalWorkspace
✅ io.matrix.federation.liquid.MCTSPlanner
✅ io.matrix.federation.gpu.GpuTaskExecutor
✅ io.matrix.transcoders.AudioFFTEncoder, VisionEdgeEncoder, TranscoderComparator
```

### 3.5 LLM residue

```
$ grep -lr -E "llm|qwen|onnx" matrix-core/src/main/java/io/matrix/api/
19 files
```

These files in `io/matrix/api/` form the **legacy LLM-serving stack**
(`QwenModelAdapter`, `OnnxRuntimeAdapter`, `BeamSearchGenerator`, etc.).
They MUST be quarantined to a new `matrix-legacy-llm-tools` module marked
OFFLINE-ONLY. This is deferred to TRUE-W9.

## 4. Empty modules

```
matrix-fpga:    0 .java files, registered in settings.gradle
matrix-micro:   0 .java files, registered in settings.gradle
matrix-ros2:    0 .java files, registered in settings.gradle
```

These should be **deregistered from `settings.gradle`** (they break
`./gradlew build` and pollute the module list) OR given minimal honest
stubs. We choose deregistration in this wave (simplest) with archived
markers in `docs-v2/archive/` so future waves can resurrect them when
real hardware support arrives.

## 5. No git tags

```
$ git tag | wc -l
0
```

Tags will be created at TRUE-W12 (release v1.0.0 + v16.0.0-mind).

## 6. Stale `SESSION.md`

`SESSION.md` still references commit `13df1171` from MIND-W1 work and
the previous user directives — not the actual MIND-W1..W7 history. This
wave refreshes it.

## 7. No disk-budget guard anywhere

```
$ grep -rl "DiskBudget" matrix-core/src 2>/dev/null
(no matches)
```

This wave adds `io.matrix.brain.runtime.DiskBudget` to the runtime
module, with `HEALTHY ≥ 25 GB / WARN < 25 GB / REFUSE < 10 GB` tiers.

## 8. Test surface is large but lies

| Module | Tests | Status |
|--------|-------|--------|
| matrix-api-gateway | 58/58 | ✅ |
| matrix-brain-runtime | 115/115 | ✅ |
| matrix-audit | 40/40 | ✅ |
| matrix-billing | 55/55 | ✅ |
| matrix-quality | 23/23 | ✅ |
| matrix-observability | 22/22 | ✅ |
| **TOTAL** | **313/313** | ✅ |

Tests pass because the simulated stages have hand-coded inputs in their
tests. The tests **do not prove** the mind computes — they prove the
stub returns the canned value. **TRUE-W1 must replace this with tests
that verify engine identity** in every `BrcStep`.

## 9. CONSTITUTION compliance (current state)

| Article | Status |
|---------|--------|
| I (no LLM in runtime) | ❌ runtime path can reach `io.matrix.api.*` via reflection hack in `ProductionBrainClient` |
| II (K_MAX=20) | ⚠ not enforced in BIR layer (because BIR is hand-coded) |
| III (determinism) | ⚠ `Random(42L)` is seeded but simulated outputs are not derivable |
| IV (FROZEN modulators) | ⚠ `ModulatorStage` reimplements the gates; should reuse `BirBrainCycle.getSafety()` |
| V (JaCoCo ≥82%) | ✅ enforced by `matrix-quality` |
| VI (no forbidden claims) | ❌ "thinking" is implied by stage names |
| VII (stack standards) | ✅ all gradle |
| VIII (no shadow logic) | ❌ `BrcStep` does not name the engine invoked |

## 10. Roadmap

The MIND-W1..W7 work is **infrastructure scaffolding** — useful, but
should be re-labelled as "skeleton" in documentation until TRUE-W1..W7
(re-launched as TRUE-W1..W12) replace the stubs with real core wiring.

| Wave | Focus |
|------|-------|
| TRUE-W1 | Wire REAL `BirBrainCycle` + core signal/perception/reflex into MindCycle |
| TRUE-W2 | Real `SqliteMemoryBackend` persistence + online learning |
| TRUE-W3 | Real `SleepCycle` + `ConsolidationCycle` |
| TRUE-W4 | `AutonomyEngine` + `ArousalDynamics` + real inbox |
| TRUE-W5 | Real `Distiller.capture/synthesize/fidelity` pipeline |
| TRUE-W6 | Real OpenCL kernels via `GpuTaskExecutor` |
| TRUE-W7 | Multimodal + Cyrillic + real transcoders |
| TRUE-W8 | Audit + billing + federation integration |
| TRUE-W9 | Legacy quarantine + hygiene + git tags + RU/EN docs |
| TRUE-W10 | LAUNCH for human validation + operator kit |
| TRUE-W11 | Perpetual research engine |
| TRUE-W12 | Grand validation + release v16.0.0-mind |

## 11. Artifacts created in this wave

- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/DiskBudget.java`
- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DiskBudgetTest.java`
- `docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md` (this file)
- `SESSION.md` (refreshed)

## 12. Reviewer verdicts

| Agent | Verdict |
|-------|---------|
| ARCHITECT | PASS — module boundaries clean; core engines isolated, simulacra isolated |
| CRITIC | **REJECT** the runtime as a real mind (simulacra confirmed); PASS the hygiene baseline |
| RESEARCHER | notes that META-R doctrine must be applied starting TRUE-W1 |
| SECURITY | PASS — no new credentials; DiskBudget does not expose secrets |
| QA/PERF | PASS — 313/313 tests still pass; new DiskBudgetTest |
| DOC | PASS — this audit doc + SESSION.md updated |
| LIBRARIAN | PASS — DiskBudget utility added with 25/10 GB thresholds |

## 13. Closing note

The TRUE-MIND transformation begins NOW. The skeleton is in place; the
work ahead is to grow flesh on it.
