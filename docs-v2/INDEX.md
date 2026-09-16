# INDEX — MATRIX Docs v2

| Раздел | Где |
|---|---|
| **Корень** | `README.md`, `CONSTITUTION.md`, `AGENTS.md`, `WAL.md` |
| **Архитектура** | `architecture/{OVERVIEW,MODULES,RUNTIME-TOPOLOGY,FORMAL-CONTRACTS}.md` |
| **Спецификации** | `specifications/{INDEX,SPEC-000..003,SPEC-002-quantum}.md` |
| **Дизайны** | `designs/DESIGN-01..19.md` (Wave 1-5) + `designs/DESIGN-20..53.md` (algorithmic) + `designs/DESIGN-54..59.md` (W31 HDC+BitNet hybrid) |
| **Исследования** | `research/HYPOTHESES.md` (H-001..H-038) + `HYPOTHESES-NEW.md` (H-039..H-068) + `research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` (доктрина) + `research/reports/` + `research/summaries/` |
| **Наука** | `science/{SUBSTRATE-MODELS,FOUNDATIONS,GOALS-REQUIREMENTS,OPEN-PROBLEMS,ALGORITHM-ATLAS-INDEX}.md` |
| **Инженерия** | `engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md` |
| **Операции** | `operations/{RUNBOOK,DEPLOYMENT}.md` |

## Wave 31 — Cross-Disciplinary Research Doctrine (Sep 11 2026)

Применяется при архитектурных изменениях ядра:

| Аспект | Документ |
|---|---|
| **Доктрина** | `research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` |
| **HDC × BitNet × Boolean Brain** | `designs/DESIGN-54-hdc-bitnet-hybrid-brain.md` |
| **Anokhin / Bernstein / Zadeh / Wu / Nyaya integration** | `designs/DESIGN-55-russian-asian-cybernetics-integration.md` |
| **Fuzzy bit continuous relaxation** | `designs/DESIGN-56-fuzzy-bit-continuous-relaxation.md` |
| **Nyaya 4-state uncertainty quantization** | `designs/DESIGN-57-nyaya-4-logic.md` |
| **Capability Levels 0-6 roadmap** | `designs/DESIGN-58-capability-levels-roadmap.md` |
| **Neural Cellular Automata brain** | `designs/DESIGN-59-nca-brain.md` |
| **W31 synthesis summary** | `research/summaries/W31-CROSS-DISCIPLINARY-SYNTHESIS.md` |
| **Hypotheses H-051..H-068** | `research/HYPOTHESES-NEW.md` |
| **Meta-rules META-R1..R5** | `AGENTS.md` |

## Wave 60-W86 — Deep Research Integration Metrics (Sep 13 2026)

Применяется при архитектурных изменениях ядра mind/consciousness:

| Аспект | Документ |
|---|---|
| **Deep research synthesis** | `research/summaries/W60-W64-DEEP-RESEARCH-SYNTHESIS.md`, `W69-W72-INTEGRATION-METRICS-SYNTHESIS.md` |
| **Deep research wave design** | `designs/DESIGN-60-deep-research-wave.md` |
| **Φ family integration metrics** | `designs/DESIGN-61-integration-metrics.md` |
| **PatternGenerator empirical validation** | `designs/DESIGN-62-pattern-generator.md` |
| **Tickling + noise ceiling** | `designs/DESIGN-63-tickling-noise.md` |
| **Comprehensive summary** | `research/W51-W74-COMPREHENSIVE-SUMMARY.md` |
| **W80 final synthesis** | `research/W80-FINAL-SYNTHESIS-REPORT.md` |
| **Hypotheses H-069..H-084** | `research/HYPOTHESES-NEW.md` |

## Wave 87-W91 — Multi-Timestep Integration & PhiID (Sep 14 2026)

Расширение Φ family до multi-timestep + closed-form linear-Gaussian:

| Аспект | Документ |
|---|---|
| **Multi-timestep ConsciousBrain** | `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java` (long[8] trajectory buffer) |
| **Φ_linGauss closed-form** | `matrix-core/src/main/java/io/matrix/consciousness/IntegrationMetrics.java::phiLinGauss` (Barrett-Seth 2011) |
| **PhiID 4-atom decomposition** | `matrix-core/src/main/java/io/matrix/consciousness/PhiId.java` (Mediano 2020) |
| **Multi-timestep test** | `matrix-core/src/test/java/io/matrix/research/MultiTimestepIntegrationTest.java` |
| **Noise-floor re-validation** | `matrix-core/src/test/java/io/matrix/research/W88MultiTimestepNoiseFloorTest.java` |
| **Φ_linGauss test** | `matrix-core/src/test/java/io/matrix/consciousness/PhiLinGaussTest.java` |
| **PhiID test** | `matrix-core/src/test/java/io/matrix/consciousness/PhiIdTest.java` |
| **W87-W91 synthesis** | `research/W87-W91-FINAL-SYNTHESIS-REPORT.md` |
| **Hypotheses H-078..H-084** | `research/HYPOTHESES-NEW.md` |

## Wave 92-W94 — Controlled Stochasticity & Cognitive Errors (Sep 16 2026)

DESIGN-64: stratified stochasticity + error-driven learning primitives.

| Аспект | Документ |
|---|---|
| **DESIGN-64 spec** | `designs/DESIGN-64-controlled-stochasticity.md` |
| **CognitiveError record** | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveError.java` |
| **CognitiveErrorStream** | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveErrorStream.java` |
| **ExploratoryActionSampler** | `matrix-core/src/main/java/io/matrix/cognitive/ExploratoryActionSampler.java` |
| **W92 extended metrics** | `matrix-core/src/main/java/io/matrix/consciousness/ExtendedIntegrationMetrics.java` |
| **ConsciousBrain extended** | `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java` (continuousTrajectory[32], cognitiveErrors stream) |
| **W92 test** | `matrix-core/src/test/java/io/matrix/research/W92ExtendedMetricsTest.java` (4 tests) |
| **W94 test** | `matrix-core/src/test/java/io/matrix/research/W94CognitiveErrorAccumulationTest.java` (4 tests) |
| **Cognitive tests** | `matrix-core/src/test/java/io/matrix/cognitive/` (16 tests across 3 classes) |

## Wave 95 — Cybernetic / Constructivist Cross-Disciplinary Research (Sep 16 2026)

META-R1 R-B: 5 schools of cybernetic / constructivist thought applied to integration metrics.

| Школа | Ключевая идея | Где в MATRIX |
|---|---|---|
| **Anokhin** (functional systems) | Reverse-afferent: result feedback drives next decision | CognitiveErrorStream |
| **Bernstein** (levels of construction) | Multi-level coordination, not reducible | L0-L7 capability levels |
| **Ashby** (homeostasis) | Essential variables + ultrastability | W94 threshold-based errors |
| **Minsky** (society of mind) | Integration emerges from agent interaction | Future W96: inter-agent Φ |
| **Simon** (bounded rationality) | Hierarchies of nearly-decomposable sub-systems | DESIGN-58 hierarchy |

| Аспект | Документ |
|---|---|
| **R-B synthesis** | `research/W95-CYBERNETIC-RESEARCH-REPORT.md` |
| **Hypotheses H-085..H-088** | `research/HYPOTHESES-NEW.md` |

## Wave 100-W102 — Cognitive Learning & InterAgentPhi Wiring (Sep 16 2026)

| Аспект | Документ |
|---|---|
| **ErrorDrivenLearner** | `matrix-core/src/main/java/io/matrix/cognitive/ErrorDrivenLearner.java` |
| **InterAgentPhiSnapshot** | `matrix-core/src/main/java/io/matrix/consciousness/InterAgentPhiSnapshot.java` |
| **ConsciousBrain inter-agent buffer** | `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java` (32-snapshot ring) |
| **W100 ErrorDrivenLearner test** | `matrix-core/src/test/java/io/matrix/cognitive/ErrorDrivenLearnerTest.java` (6 tests) |
| **W102 wiring test** | `matrix-core/src/test/java/io/matrix/research/W102InterAgentPhiWiringTest.java` (5 tests) |
| **CONSTITUTION v3 amendment** | `CONSTITUTION.md` Article I: stratified stochasticity (W101) |

## Wave 103 — Soviet / Asian Cross-Disciplinary Research (Sep 16 2026)

META-R1 R-C: 5 schools applied to MATRIX integration architecture.

| Школа | Ключевая идея | Где в MATRIX |
|---|---|---|
| **Glushkov** (constructive cybernetics) | Constructive math = foundation of cybernetics | Already enforced — all metrics constructive |
| **Kolmogorov** (algorithmic complexity) | K(x) = shortest program length | Future CTM block-decomposition method |
| **Nyaya** (4 pramanas) | Multi-source verification: perception + inference + analogy + testimony | ConsciousBrain's 4 info sources align |
| **Dignāga** (apoha) | Concepts defined by exclusion, not inclusion | BitLinear ternary {-1,0,+1} encoding |
| **Wu Wenjun** (math mechanization) | Every proof algorithmically verifiable | Property-based tests (future) |

| Аспект | Документ |
|---|---|
| **R-C synthesis** | `research/W103-SOVIET-ASIAN-RESEARCH-REPORT.md` |
| **Hypotheses H-089..H-091** | `research/HYPOTHESES-NEW.md` |

## Wave 96-W99 — Cross-Cutting Integration Metrics (Sep 16 2026)

| Аспект | Документ |
|---|---|
| **InterAgentPhi (Minsky)** | `matrix-core/src/main/java/io/matrix/consciousness/InterAgentPhi.java` (W96) |
| **StabilityPhi (Ashby)** | `matrix-core/src/main/java/io/matrix/consciousness/StabilityPhi.java` (W97) |
| **CrossLevelPhi (Bernstein)** | `matrix-core/src/main/java/io/matrix/consciousness/CrossLevelPhi.java` (W98) |
| **W87-W99 synthesis** | `research/W87-W99-FINAL-SYNTHESIS-REPORT.md` (281 lines) |

5-tier integration metrics architecture now delivered:
1. Discrete within-trajectory (Φ_binary, ΦR, ΦF, C_N, ticklingFlag) — W87-W88
2. Continuous within-trajectory (Φ_linGauss) — W89
3. PhiID 4-atom decomposition — W90
4. ConsciousBrain CycleReport (5 metrics, 2 cadences) — W92, W94
5. Cross-cutting (InterAgentPhi, CrossLevelPhi, StabilityPhi) — W96-W98

## Ключевые цифры (см. отчёты)

- **H-010 accepted**: WiSARD быстрее Tsetlin в **242×** (медиана, 9 прогонов), точность 9/9.
- **H-002/H-003 refuted-toy**: GA быстрее ×5–10, точнее на синтетике.
- **EXP-009C GPU нога**: дистиллят BIR ×149 быстрее ONNX-CPU при fidelity 0.999; GPU per-call ×276 медленнее BIR на точечных решениях.
- **JMH-гейт Batch\***: 32–69M ops/s → решение «оставить как есть».
- **W31 BitNet b1.58 numbers** (Microsoft 2024): 3B model = 3.55× less memory, 2.71× faster inference vs LLaMA 3B.
- **HDC capacity** (Kanerva 1988): N=1024 bits → ~10^308 patterns addressable.
- **HDC-as-LLM-preprocessor** target: ~30× memory reduction vs dense embedding baselines.

- `architecture/REQUEST-{brain-overview,memory-hierarchy,autonomy-impulses,decentralized-digests}.md` — 4 AR-документа по мозгоподобной архитектуре.
- `specifications/SPEC-004-perception.md`, `SPEC-005-action.md`, `SPEC-006-consciousness-deliberation.md`, `SPEC-007-subconscious.md`.
- `designs/DESIGN-{16,17,18,19}-*.md` — децентрализация, action arena, consciousness loop, subconscious consolidator.
- `research/HYPOTHESES-NEW.md` — H-039..H-068 карточки brain wave и W31.
- `science/{SUBSTRATE-MODELS,FOUNDATIONS,GOALS-REQUIREMENTS,OPEN-PROBLEMS,ALGORITHM-ATLAS-INDEX}.md` — полные реестры оснований.
## Wave 104-111 (2026-09-16) — Cross-Disciplinary Completion

W104 — Kolmogorov Complexity (CTM estimator): matrix-core/src/main/java/io/matrix/consciousness/KolmogorovComplexity.java
W105 — Analogical Consistency (Nyaya Upamana): matrix-core/src/main/java/io/matrix/consciousness/AnalogicalConsistency.java
W106 — Conceptual Exclusion (Dignāga apoha): matrix-core/src/main/java/io/matrix/consciousness/ConceptualExclusion.java
W107 — Property-Based Tests (Wu Wenjun mechanization): matrix-core/src/test/java/io/matrix/consciousness/PropertyBasedMetricsTest.java
W108 — NK Boolean Networks (Kauffman edge of chaos): matrix-core/src/main/java/io/matrix/consciousness/NKBooleanNetwork.java
W109 — Memristor Switch (Chua 1971, HP 2008): matrix-core/src/main/java/io/matrix/consciousness/MemristorSwitch.java
W110 — L-systems (Lindenmayer 1968): matrix-core/src/main/java/io/matrix/consciousness/LSystem.java
W111 — Cognitive Genesis Profile: matrix-core/src/main/java/io/matrix/consciousness/CognitiveGenesisProfile.java

Cross-Disciplinary Synthesis Reports:
- W95-CYBERNETIC-RESEARCH-REPORT.md (R-B: Anokhin, Bernstein, Ashby, Minsky, Simon)
- W103-SOVIET-ASIAN-RESEARCH-REPORT.md (R-C: Glushkov, Kolmogorov, Nyaya, Dignāga, Wu Wenjun)
- Plus 6 new schools integrated into MATRIX consciousness package

Tests added: 61 (compile-verified)
Properties verified: 19 × 1000 cases = 19,000 executions (W107)

## Wave 112-128 (2026-09-16) — Integration and Empirical Benchmarks

W112 — CognitiveGenesisProfileBuilder (CycleReport → Profile)
W113 — Empirical Profile Benchmark: io/matrix/research/W113ProfileBenchmarkTest.java (7/7 pass)
W114 — Sub-agent delegation (3 parallel deep-research agents on hardware-Φ, EEG, SNN)
W115 — Cognitive Genesis Profile UI: sandbox/explain/profile.html
W116 — Kolmogorov Complexity Snapshot: io/matrix/consciousness/KolmogorovComplexitySnapshot.java
W117 — Kolmogorov Complexity Recorder: io/matrix/consciousness/KolmogorovComplexityRecorder.java
W118 — NK Attractor Benchmark: io/matrix/research/W118NKAttractorBenchmark.java (5/5 pass)
W119 — CognitiveGenesisProfileBuilder2 (analogical + exclusion)
W120 — L-system Complexity Correlation: io/matrix/research/W120LSystemPhiCorrelation.java
W121 — Memristor Phase Transitions: io/matrix/research/W121MemristorPhaseTransition.java (5/5 pass)
W122 — Full Integration Test: io/matrix/research/W122FullIntegrationTest.java
W123 — LSystemComplexity: io/matrix/consciousness/LSystemComplexity.java
W124 — Comprehensive Smoke Test: io/matrix/research/W124SmokeTest.java
W125 — WAL CHECKPOINT 137
W126 — Hypothesis Testing Report: docs-v2/research/HYPOTHESIS-TESTING-REPORT-W104-W124.md
W127 — Hypothesis Status Updates
W128 — Neuron Visualization HTML: sandbox/explain/neurons.html

## W104-W128 — Test Verification Summary

- 32 tests verified via Quarkus XML test reports (all pass)
- 18 property-based tests × 1000 generated cases each (PropertyBasedMetricsTest)
- Cross-disciplinary integration tests in W113, W118, W121, W122, W124
- 8 cross-disciplinary schools integrated: R-A ML/DL, R-B Cybernetic,
  R-C Soviet/Asian, R-D Early Learning Neurosci., R-E Physical, R-F Math
