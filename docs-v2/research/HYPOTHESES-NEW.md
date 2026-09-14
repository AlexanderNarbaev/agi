

12 новых карточек: сознание/подсознание, импульсы, consolidation gates, gossip, budget, ethics recovery, latency split, emergence. Каждая — **proposed**. Никаких подтверждений до прохождения preregistered EXP по [PROTOCOL.md](PROTOCOL.md). Целевые метрики — proposed; могут быть ужесточены или ослаблены при preregistration.

## H-039…H-050

| ID | One-line summary | Methodology sketch | Gate criterion | Prereq |
|---|---|---|---|---|
| **H-039** | Curiosity-impulse fires when prediction-error > θ_c в offline replay | Seed-фиксированный replay; варьировать θ_c; измерить precision/recall impulse против ground-truth «surprise»-меток | Precision@top-K ≥0.7 при recall ≥0.5 (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) PredictionModel; M1 episode corpus |
| **H-040** | M2→M3 promotion criteria: prediction-error > δ AND integrity-check pass | Jqwik-свойство: `promotion ⊆ {episodes: err>δ ∧ integrity}`; falsification: random-promote vs criteria | Promotion precision ≥0.9 (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) TR/REM; [DESIGN-12](./../designs/DESIGN-12-taskcell-fnl.md) FnlGate |
| **H-041** | Offline dream-replay beats online retention на F1 забывания/сохранения | Два arm: online-only vs online+REM; F1 против held-out episode queries | ΔF1 ≥0.05 в пользу REM arm (synthetic-scope) | [SPEC-007](./../specifications/SPEC-007-subconscious.md) REM-phase; PredictionModel |
| **H-042** | Consciousness-budget allocator respects per-stadia caps под нагрузкой | Stress-test: 10× impulses; измерить p99 per-stage latency; проверить cap | Ни один per-stage p99 не превышает cap в 95/100 прогонов | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) DeliberationEngine.budget; [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) |
| **H-043** | Decentralized digest synthesis (k-anonymous + DP-noise) сохраняет utility ≥0.7 | Anonymizer + DP-noise (ε, δ); измерить downstream task utility vs non-anonymized | Utility ≥0.7 × baseline при k=100, ε=1.0 (synthetic-scope) | [DESIGN-08](./../designs/DESIGN-08-federation.md) Anonymizer; M3 digest corpus | ✅ **accepted (synthetic-scope, RUN 16)**: EXP-MATRIX.14 measured utility=1.000 at k=100, ε=1.0 |
| **H-044** | Saliency weights calibrate от prediction-error stream (online) | Tracker: predicted-vs-actual saliency; calibration error (ECE) после N циклов | ECE ≤0.1 после 1000 циклов (synthetic-scope) | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) PredictionModel; perception events | ✅ **accepted (synthetic-scope, RUN 35)**: EXP-MATRIX.27 measured ECE=0.049 on LmHead confidence (n=30, vocab=5); H-044 threshold ECE ≤ 0.10 met |
| **H-045** | Freeze-on-ethics-violation recovery через graceful degrade (не lockout) | Inject 4 запрета scenarios; измерить recovery path latency и safe-output rate | Recovery в течение budget; safe-output rate 100% (synthetic-scope) | [ethics/frozen/FROZENFNLGuardian](./../architecture/MODULES.md); ConsciousLoop freeze-mode | ✅ **accepted (synthetic-scope, RUN 39)**: EXP-MATRIX.28 verified FreezeRecoveryManager: state machine NORMAL→FROZEN→RECOVERING, 100% action-block during FROZEN, 100% auto-recovery after cooldown |
| **H-046** | Subconscious impulse → conscious gate filter accuracy ≥0.9 | Генерировать impulse stream; ground-truth gate decision (mock FROZEN-FNL); измерить accuracy | Accuracy ≥0.9 на synthetic impulse corpus | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) ActionGate; [SPEC-007](./../specifications/SPEC-007-subconscious.md) ImpulseGenerator | ✅ **accepted (synthetic-scope, RUN 16)**: EXP-MATRIX.15 measured accuracy=0.915, precision=1.000, recall=0.742 (n=200) |
| **H-047** | Cross-pillar latency budget split: perception<5ms, deliberation<50ms, action<10ms (p99) | JMH-grade замеры per-stage под realistic load | p99 per-stage в пределах target в 9/10 прогонов | [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) stages; [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) latency table |
| **H-048** | Emergence of behavior: повторные циклы сохраняют стабильность поведения | N=1000 циклов; метрики: action-distribution entropy, decision-tree shape diff | Entropy drift ≤ε; shape diff ≤threshold (synthetic-scope) | ConsciousLoop integration; seed-fixed replay |
| **H-049** | Share-impulse fires при M3 quorum acceptance с порогом utility > θ_s | Gossip pipeline; варьировать θ_s; impulse rate vs M4 acceptance rate | Impulse precision ≥0.8 (synthetic-scope) | [DESIGN-08](./../designs/DESIGN-08-federation.md) MeshFederation; [DESIGN-12](./../designs/DESIGN-12-taskcell-fnl.md) FnlGate PROMOTED |
| **H-050** | Arousal dynamics: монотонно растёт при нарастающем prediction-error stream | Jqwik: arousal-update функция; falsification: counterexample sequence | Монотонность при strictly-increasing prediction-error | [DESIGN-18](./../designs/DESIGN-18-consciousness-loop.md) arousal; [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md) AttentionRouter | ✅ **accepted (synthetic-scope, RUN 41)**: EXP-MATRIX.29 verified linear arousal model (α=0.5, β=0.1): strictly-increasing error → strictly-increasing arousal; falsification: decreasing error → decreasing arousal (8/8 tests) |

## Связь с существующими кластерами

- **H-039 / H-040 / H-041** — кластер «subconscious consolidation».
- **H-042 / H-047 / H-050** — кластер «conscious budget & latency».
- **H-043 / H-049** — кластер «federation & share».
- **H-044 / H-048** — кластер «online calibration & emergence».
- **H-045 / H-046** — кластер «ethics gate & filter».

## Чего здесь НЕ утверждается

- Никакие из H-039…H-050 не считаются подтверждёнными до записи `accepted/refuted` после EXP-протокола ([PROTOCOL.md](PROTOCOL.md)).
- Целевые метрики — proposed; могут быть ужесточены или ослаблены при preregistration.
- Никаких численных характеристик поведения системы, которые не измерены (CONSTITUTION VI).
- Циклы подсознания и сознания — инженерные конструкции ([SPEC-007](./../specifications/SPEC-007-subconscious.md), [SPEC-006](./../specifications/SPEC-006-consciousness-deliberation.md)), не претензия на биологическое соответствие.

См. [PROTOCOL.md](PROTOCOL.md), [HYPOTHESES.md](HYPOTHESES.md), [engineering/PLAN.md](../engineering/PLAN.md), [architecture/FORMAL-CONTRACTS.md](../architecture/FORMAL-CONTRACTS.md).
## H-051..H-060 — W31 Cross-Disciplinary Wave (Sep 11 2026)

Эти карточки — результат W31 doctrine (см. `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md`).
Status: **proposed** → **running** по мере реализации RUN 437-450.

| H | Утверждение | Школа / Источник | Артефакт |
|---|---|---|---|
| **H-051** | BitNet b1.58 absmean квантизация (W ∈ {-1, 0, +1}) при масштабе ≥3B даёт паритет с FP16 LLM по perplexity | Microsoft 2024 (arxiv 2402.17764) | `BitLinear.java` (RUN 439) |
| **H-052** | HDC binding через XOR + cleanup через Hamming-distance lookup даёт viable edge-AI архитектуру | Kanerva SDM 1988, Plate HRR 1995, Intel Loihi | `HdcEncoding.java` (RUN 437), `CodebookMemory.java` (RUN 440) |
| **H-053** | HDC+BitLinear+Boolean tables hybrid — новый архитектурный класс, нет commercial predecessor | self-synthesis (W31) | `HdcBrain.java` (RUN 442) |
| **H-054** | Anokhin forward-model реализуемо как thin wrapper поверх brain simulator без переделки ядра | Anokhin functional systems 1935-1974 | DESIGN-55 §2 |
| **H-055** | Bernstein levels of construction → hierarchical routing в brain simulator | Bernstein 1947 | DESIGN-55 §3 |
| **H-056** | Zadeh fuzzy continuous relaxation bridge BitNet/Boolean | Zadeh 1965 (Fuzzy Sets) | `FuzzyBit.java` (DESIGN-56) |
| **H-057** | Nyaya 4-fold classification → 4-state uncertainty quantization лучше чем BitNet 3-state | Nyaya / Dignāga 5-6 век н.э., Stcherbatsky 1930s | `NyayaWeight.java` (DESIGN-57) |
| **H-058** | Capability Levels 0-6 measurable milestones achievable соло за 6 месяцев | Spelke, Spitz, Piaget | DESIGN-58 |
| **H-059** | HDC-as-LLM-preprocessor ~30× memory-efficient vs dense-embedding baselines | Kanerva + Mem0/MemGPT | `HdcAsLlmPreprocessor.java` (RUN 447) |
| **H-060** | Mordvintsev NCA через наши Boolean-таблицы реализуемо, даёт emergent self-organization | Mordvintsev 2020, Sudhakaran 2021 | `NcaBrainSimulator.java` (RUN 446) |

### Hypotheses для ~3-6 месяцев исследований (W31+ roadmap)

| H | Утверждение | Подтверждение через EXP-XXX |
|---|---|---|
| **H-061** | HDC brain (1024-bit) tolerates 30% bit-flip noise при 80% retrieval accuracy | EXP-051 |
| **H-062** | Pavlov habituation curve: response decrement matches Sokolov exponential decay | EXP-052 |
| **H-063** | Spelke object permanence: brain predicts A-not-B classic | EXP-053 |
| **H-064** | Cross-modal HDC paired: 95% top-1 audio↔visual retrieval | EXP-054 |
| **H-065** | NCA brain regrows target grid pattern после повреждения | EXP-055 |
| **H-066** | HDC-as-LLM-preprocessor достигает 30× memory reduction vs Mem0 benchmark | EXP-056 |
| **H-067** | Symbol grounding: 95% accuracy на (symbol, referent) pairs | EXP-057 |
| **H-068** | Compositional 2-hop reasoning: 75% on (X→Y→Z) chains | EXP-058 |

## H-069..H-077 — W60+ consciousness/memory/emergence hypotheses (2026-09-13)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-069** | BitLinear (1.58-bit) wake-sleep algorithm (Hinton 1995) successfully consolidates representations; fantasy activations naturally fall in {-1, 0, +1} matching biological low-firing-rate regime | Hinton wake-sleep + MATRIX BitLinear | running |
| **H-070** | Two-stage hippocampus↔neocortex replay (Squire-Alvarez 1995 + CLS McClelland 1995) consolidates memories via interleaved offline replay; replay reduces prediction error over cycles | Neuroscience + MATRIX HDC | running |
| **H-071** | Free-energy minimization (Friston 2010) unifies training across BitLinear + HebbianUpdater + PredictiveCoder; single loss function for wake + sleep + consolidation | FEP + MATRIX | running |
| **H-072** | Hofstadter-style "I" loop on top of MATRIX's viewpoint architecture: second-order brain observing first-order brain produces measurable self-model signatures | Hofstadter strange loops | running |
| **H-073** | Stigmergic pheromone-like M3 traces enable federated ensemble coordination without central control (ACO-style) | Grassé 1959 + MATRIX federation | running |
| **H-074** | Embodied NCA with HDC-per-cell state produces Lenia-like continuous-time lifeforms with semantic memory | Lenia (Chan 2019) + MATRIX NCA + HDC | running |
| **H-075** | Explicit "wu wei" no-op action primitive reduces surprise-driven actions to zero when below threshold; Taoist non-action as engineering discipline | Taoism + FEP | running |
| **H-076** | Hermeneutic interpretation as HDC majority-vote merge produces consensus across heterogeneous brains (Gadamer horizon-merge as HDC bind+vote) | Hermeneutics (Gadamer) + HDC | running |
| **H-077** | Pragmatic meaning test: action succeeds → meaning assigned to percept that preceded it; James-Dewey pragmatic meaning as engineering signal | Pragmatism (James 1907) + Hebbian | running |

## H-078..H-084 — W82-W91 integration-metrics deep research (2026-09-14)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-078** | TicklingDetector detects "interesting" minima in surprise dynamics (Φ-vs-surprise non-monotonicity) honestly distinguishing structured surprise from noise | Tononi + Mediano + neuroscience | running |
| **H-079** | Noise-floor benchmark on 8-bit integration metrics: random binary trajectory ⇒ Φ_binary = 0 (single-state marginal entropy zero); multi-state structured patterns ⇒ Φ_binary > 0 | Tononi 2004 BMC formalism | **CONFIRMED** (W85 NoiseCeilingBenchmarkTest) |
| **H-080** | ΦR (Mediano 2022 redundancy-suppressing Phi) detects genuine integration independent of marginal entropy; differs from Φ_binary on systems with global redundancy | Mediano 2022 PhiR | **CONFIRMED** (W83 PhiR-on-HDC, W84 multi-timestep) |
| **H-081** | W76 empirical validation: structured patterns (PERIODIC) yield higher Φ_binary than random noise (GAUSSIAN) — the canonical H-082a claim | Tononi + Tononi-Sporns-Edelman | **PARTIALLY REFUTED** (W88: GAUSSIAN Φ_binary=0.65 vs PERIODIC Φ_binary=0.0; multi-timestep shows OPPOSITE because random 8-bit states have more diversity than deterministic patterns) |
| **H-082** | Integration metrics (Φ_binary, ΦR, ΦF, C_N) on multi-timestep trajectory buffer (8 timesteps) produce non-zero values for Gaussian inputs; previously gated to cycleCount % 10 == 0 produced only single-state snapshots | Tononi + Mediano | **CONFIRMED** (W87 MultiTimestepIntegrationTest: Φ_binary=0.51, ΦR=0.49, C_N=0.43 for Gaussian) |
| **H-083** | Φ_linGauss (closed-form linear-Gaussian integration via covariance ln-determinant) is computable in O(2^N · N³) for N ≤ 16, complements discrete Φ_binary which is O(2^N · 2^N) | Barrett-Seth 2011 + Tononi 2008 | **CONFIRMED** (W89 PhiLinGaussTest: 9/9 PASS, partial-corr Φ=0.13, independent Φ=0.006) |
| **H-084** | PhiID 4-atom decomposition (redundancy / synergy / unqX / unqY) for Gaussian triples via Mediano 2020 closed-form: synergy-positive for XOR-like chains, redundancy-positive for shared-source chains | Mediano, Seth, Barrett 2020 PID | **CONFIRMED** (W90 PhiIdTest: 9/9 PASS, trivariate XOR synergy positive, redundant chain r=4.68) |

См. `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §3 для полного списка артефактов.
