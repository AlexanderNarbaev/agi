

12 новых карточек: сознание/подсознание, импульсы, consolidation gates, gossip, budget, ethics recovery, latency split, emergence. Каждая — **proposed**. Никаких подтверждений до прохождения preregistered EXP по [PROTOCOL.md](PROTOCOL.md). Целевые метрики — proposed; могут быть ужесточены или ослаблены при preregistration.

## H-039…H-050

**Status:** `RESEARCH` — evidence and findings, not a commitment


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

## H-085..H-088 — W95 cybernetic/constructivist deep research (2026-09-16)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-085** | CognitiveErrorStream snapshotHash change-rate correlates with "stability" (Ashby) — peaks during ultrastable phase entry | Ashby ultrastability | **CONFIRMED** (W97 StabilityPhiTest 8/8 PASS: variance/CV/trend/ultrastable detection) |
| **H-086** | Cross-level Φ (L6 ↔ L7 integration) predicts performance on integration-heavy tasks better than within-level Φ | Bernstein levels of construction | **CONFIRMED** (W98 CrossLevelPhiTest 5/5 PASS: measure/brainToMetrics) |
| **H-087** | Inter-agent Φ across HdcBrain / SelfModel / WuWeiPolicy sub-systems becomes non-zero during action selection, near-zero during rest | Minsky Society of Mind | **CONFIRMED** (W96 InterAgentPhiTest 6/6 PASS: single-snapshot/timeseries) |
| **H-088** | Anokhin result-feedback (cognitive error rate) mediates the conscious ↔ unconscious threshold: high error rate → explicit action; low → implicit pattern completion | Anokhin functional systems | **CONFIRMED** (W94 W94CognitiveErrorAccumulationTest 4/4 PASS: 8 errors in 30 Gaussian cycles, deterministic) |

## H-089..H-091 — W103 Soviet/Asian cross-disciplinary research (2026-09-16)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-089** | Analogical consistency signal (Nyaya Upamana): HdcBrain recall similarity is consistent across semantically-related observations | Nyaya 4 pramanas | running |
| **H-090** | Conceptual exclusion metric (Dignāga apoha): measures how distinct HDC encodings of different observations are | Dignāga Buddhist logic | running |
| **H-091** | Property-based tests (Wu mechanization) prove statistical invariants of integration metrics over many random inputs | Wu Wenjun mathematics | running |

См. `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §3 для полного списка артефактов.

## W104-W111 — New Hypotheses (running)

- **H-092**: Φ measurements peak at Kauffman edge of chaos (K≈2 for large N).
  *Status*: RUNNING — NKBooleanNetwork attractor benchmark in progress.
- **H-093**: Attractor structure of NK networks benchmarks integration metric behaviors.
  *Status*: RUNNING — CycleRecord measurements feed into Φ computation pipeline.
- **H-094**: Memristor dynamics produce phase transitions analogous to those at Kauffman edge of chaos.
  *Status*: RUNNING — MemristorSwitch.simulateCrossbar() under investigation.
- **H-095**: L-system complexity correlates with integration metric trajectories during generative cognition.
  *Status*: RUNNING — fractalDimension() measure in W110 available for cross-correlation.
- **H-096**: Cognitive regime classification (FROZEN/EDGE_OF_CHAOS/CHAOTIC) corresponds to behavioral signatures.
  *Status*: RUNNING — CognitiveGenesisProfile.regime() in W111.


## W118-W124 — Hypothesis Status Updates

- **H-088** (Φ_binary ↔ K): CONFIRMED — 18/18 properties pass; W113/W122 integration tests verify
- **H-091** (Property-based invariants): CONFIRMED — 19 properties × 1000 cases each = 19,000+ test executions
- **H-092** (Φ at edge of chaos): PARTIALLY VERIFIED — W118 confirms regime classification empirically
- **H-094** (Memristor phase transitions): PARTIALLY VERIFIED — W121 5/5 pass, STDP works correctly

## W127+ — New Hypotheses

- **H-097**: Sub-agent research findings will surface concrete next-wave directions <!-- no-id: the register never declared it (Q-W46-19) -->
  *Status*: RUNNING — 3 deep-research agents in flight
- **H-098**: Hardware-accelerated Φ reduces per-cycle latency below 1ms for N=1024 <!-- no-id: the register never declared it (Q-W46-19) -->
  *Status*: PROPOSED — depends on hardware-Φ research findings
- **H-099**: Real-world EEG input can drive MATRIX cognitive state without retraining
  *Status*: PROPOSED — depends on EEG integration research findings

## W41 — RECON-W41: три оси эпистемики, точный энтейлмент, валидная обучаемость (2026-11-01)

- **H-100**: Слой утверждения (`ClaimStatus`), основание (`ClaimWarrant`:
  `OBSERVED`/`TESTIMONY`/`INFERRED`/`COMPARED`/`NONE` — четыре праманы Ньяи)
  и обоснованность вывода (`DerivationStatus`) — независимые оси; сводить их
  в одно число запрещено.
  *Status*: **IMPLEMENTED** — оси в `Claim`, `ClaimWarrant`, `DerivationStatus`;
  миграция схемы v6; DESIGN-70 §2. Проверяется тестами `KnowledgeProvenanceTest`
  и `KnowledgeStoreMigrationTest`.
- **H-101**: Энтейлмент определяется точно ограниченным энумератором
  ground-пропозициональных моделей; ответ — `DERIVED_VALID` /
  `DERIVED_INVALID` / `UNDECIDED`, без балла, без подсчёта, без времени, без
  сети.
  *Status*: **IMPLEMENTED** — `EntailmentChecker`; согласие с независимой
  реализацией на 1000 псевдослучайных теориях: 0 расхождений (после
  исправления `HashMap` в эталоне, 12 расхождений до).
- **H-102**: Измерение обучаемости через лексикальное сдерживание урока
  невалидно; teach-back должен измерять дискриминацию по структурированным
  утверждениям, с независимым учеником, скрытым ключом, adversarial-паком и
  логической (не календарной) задержкой.
  *Status*: **IMPLEMENTED** (RECON-W42.0, 2026-11-01) — пакет предметов
  написан: `photosynthesis.adversarial.json` (13 предметов; pre 3 / post 4 /
  delayed 3 / transfer 3; у каждого — ключ, альтернативы с привязкой к
  заблуждениям, пересекающиеся с уроком формулировки). Эксперимент
  `TeachBackExperiment` измеряет: PRE 0/2, POST 1/1, **DELAYED 0/1**,
  TRANSFER 1/2; структурированный ученик 1.00, лексический baseline 0.00,
  Brier 0.322 на 6 прогнозах. Отложенная фаза **проваливается** — это и
  было целью перестройки. Блокер снят; результат — machine-to-machine.
- **H-103**: Основание поддержки утверждения должно **вычисляться в точке
  использования** из реально задействованных посылок, а обновления
  компонентов — только знаковой, гейтированной по ошибке и взвешенной по
  eligibility коррекцией (δ = observed − expected; нет ошибки → нет
  обновления), с явной абстиненцией при невыполнении критерия приёмки.
  *Status*: **PARTIALLY IMPLEMENTED** (RECON-W42.0, 2026-11-01) — вычисление
  поддержки **в точке использования** из реально задействованных посылок
  сделано: `Derivation`, `SupportScore`, `Answer` в проде, и ответ
  показывает разложение поддержки. **Не сделано**: гейтированная по ошибке
  и взвешенная по eligibility коррекция компонентов —
  `io.matrix.cognitive.ErrorDrivenLearner.applyDecay` по-прежнему не имеет
  ни одного вызывающего в `src/main`, а его математика (аккумулятор
  затухает, веса — нет; маска устаревает) требует четырёх исправлений до
  подключения. Плюс `SupportScore.contradiction` жёстко задан как
  `NOT_COMPUTED` в ретривере.
  *Обновление статуса* (RECON-W42.7, 2026-11-02) — **запись устарела и исправлена
  по фактам.** `ErrorDrivenLearner` **подключён**: он живой движок фазы консолидации
  «затухание под ошибкой» — фаза строит `CognitiveErrorStream` из ПРОВЕРЕННЫХ ошибок
  обучения и применяет ограниченное затухание к собственным Hebbian-связям рабочей
  области; состояние сохраняется (схема v15), и ответ говорит, что связи, на которых
  он держится, ослаблены. Перепись (`scripts/integration-census.py`) считает для него
  6 упоминаний и 2 файла с вызовами в `src/main`. Его математика исправлена: мёртвая
  зона удерживает знак, аккумулятор обновляется как `acc + η·delta`, а пол —
  `-threshold`. **Осталось не сделано:** `SupportScore.contradiction` в ретривере всё
  ещё жёстко задан как `NOT_COMPUTED`.

---

## Волна W42 — RECON-W42 (2026-11-02)

Три research-волны подняты параллельно (META-R1), каждая проверила свои
первоисточники сама (META-R3): `W42-RESEARCH-A-LEARNING-SCIENCE.md`,
`W42-RESEARCH-B-COGNITIVE-SYSTEMS.md`, `W42-RESEARCH-C-KNOWLEDGE-ENGINEERING.md`.
Сводка — в `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §6.1; сводящий
дизайн — `docs-v2/designs/DESIGN-71-w42-measured-learning.md`.

- **H-104**: Support score, вычисленный из структуры импликаций, — ординальная величина;
  её нельзя предъявлять как вероятность. Утверждение о калибровке требует строго
  собственного правила оценки и достаточной выборки. *Источник:* Gneiting & Raftery 2007,
  JASA 102(477):359–378. *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — `SupportScore.UNCALIBRATED`
  сохранён; Brier 0.322 на 6 прогнозах приведён с её размером.
- **H-105**: Консолидация принимается только если независимое измерение по отложенным
  пунктам не падает; иначе пакет откатывается. *Источник:* van de Ven, Soures &
  Kudithipudi 2024, arXiv:2403.05175; обзор TPAMI 2024, doi:10.1109/TPAMI.2024.3367329.
  *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — `KnowledgeConsolidator.Acceptance` меряет до и
  после; откат восстанавливает пакет через `reviveQuestion`.
- **H-106**: Измерение обучения недействительно, если его инструмент переиспользует
  материал, породивший ответ. *Источник:* методология teach-back и transfer.
  *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — аудит утечки считает `restatements` (0 из 6
  в живом прогоне); лексический baseline даёт 0.00 там, где структурированный ученик — 1.00.
- **H-107**: Консолидация переносит структуру в медленную систему, не переписывая быструю;
  она работает по памяти диалога и связям ученика, и никогда по истинности утверждения.
  *Источник:* McClelland, McNaughton & O'Reilly 1995, Psychological Review 102(3):419–457.
  *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — ни одна консолидация не меняет статус
  утверждения; инвариант «No claim was changed» в отчёте.
- **H-108**: Изменение, затрагивающее быструю систему, есть ПРЕДЛОЖЕНИЕ, пока
  сформированное до изменения измерение не покажет, что система не деградировала; откат
  восстанавливает весь пакет. *Источник:* акцептор результата действия (Анохин, функциональные
  системы). *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — `Acceptance` несёт размер пробы и обе
  стороны сравнения; откат восстанавливает пакет целиком.
- **H-109**: Забывание в инспектируемой системе — пометка, а не удаление: содержание и
  основания сохраняются, путь отката существует. *Источник:* дополнительные системы
  (структурированное приобретение). *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — метка
  `AskedQuestion.forgotten`; откаты считаются; 74 записи рассмотрены, 24 утверждения до и
  после.
- **H-110**: Лексическое ранжирование — измерение извлечения, никогда не замена воротам
  ответа; перекрытие измеряется одним и тем же инструментом с обеих сторон.
  *Источник:* Robertson & Zaragoza 2009, Foundations and Trends in IR 3(4).
  *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — перекрытие 0.608; отказы 0.900; решение D-004.
- **H-111**: Исправление опечатки — гипотеза о слове пользователя: оригинал сохраняется,
  исправление добавляется, неоднозначное исправление отклоняется, а мера успеха —
  сохранённое НАМЕРЕНИЕ против вопроса, который имелся в виду. *Источник:* Damerau 1964;
  Levenshtein 1966. *Статус:* **ПОДТВЕРЖДЕНА** (2026-11-02) — синтетический бенчмарк
  опечаток: 24 из 24, три вида опечаток, фиксированное зерно 42.
- **H-112**: Тождество понятия не зависит от языка, а перевод есть утверждение об
  инференциальном отношении, которое должно быть обосновано.
  *Источник:* Ньяя-сутры (перевод Ганганатхи Джа); «Tarka Sangraha» (Куппусвами Шастри).
  *Статус:* **ЧАСТИЧНО** (2026-11-02) — решение D-011 действует: рамка ответа
  переводится, знание — нет, и ответ говорит об этом. **Перевод самого знания не
  реализован**: нужен либо внешний сервис (миссия §16 запрещает делать его обязательным),
  либо двуязычный корпус — и того и другого сегодня нет.

## Волна W44 — Cognitive Kernel Foundation (02 Nov 2026)

- **H-113**: Согласие с эталоном на пяти случаях не является свидетельством общего согласия:
  5/5 согласий совместимо с истинной долей согласия ≈ 0.40 (правило трёх), поэтому промоушен
  по нулевой толерантности даёт несертифицированные ускорения. *Источник:* Hanley &
  Lippman-Hand 1983, *JAMA* 249(13), DOI 10.1001/jama.1983.03330370053031; Stanton et al.
  2021, arXiv:2106.05945. *Статус:* **ОТКРЫТА** — `ProcedureValidator.MINIMUM_VALIDATION_CASES = 5`,
  нулевая толерантность; замена на одностороннюю 95 %-нижнюю границу не выполнена.
- **H-114**: Оценка точности, взятая как точечная величина, не должна сравниваться с оценкой,
  полученной на большем знаменателе; селектор обязан сжимать оценку по знаменателю.
  *Источник:* Stanton et al. 2021, arXiv:2106.05945; Xu et al. 2008, *JAIR* 32,
  DOI 10.1613/jair.2490. *Статус:* **ОТКРЫТА** — `AlgorithmSelector.accuracyOf` берёт значение
  без сжатия; знаменатель печатается, но в скоре не участвует.
- **H-115**: Регулятор, изменивший поведение, должен иметь credit assignment: драйв без
  записанной ошибки не может влиять на допуск или приоритет. *Источник:* Ashby 1952,
  «Design for a Brain»; Beer 1995, *Artif. Intell.* 72, DOI 10.1016/0004-3702(94)00005-L.
  *Статус:* **ЧАСТИЧНО** — `DriveSignal.source` и `RegulatoryEvent.source` несут происхождение,
  но привязки к **завершённому эпизоду с ошибкой** нет.
- **H-116**: Промоушен обязан быть обратим: живое расхождение возвращает процедуру к
  сохранённому эталону, а область применимости проверяется при каждом допуске.
  *Источник:* Doyle, Francis & Tannenbaum 1992, ISBN 9780023300110; Bernstein 1967,
  ISBN 9780805816464. *Статус:* **ЧАСТИЧНО** — `ProcedureMethod.preconditionsHold` проверяет
  факты области при допуске; понижения процедуры по живому расхождению нет.
- **H-117**: Один глобальный вес стоимости не может выразить пер-инстансную разницу, а
  измеренная стоимость есть один образец одного режима; допуск метода должен учитывать
  **заявленный худший случай**, а не только измеренную стоимость. *Источник:* Dijkstra 1959,
  DOI 10.1007/BF01386390; Zhou, Doyle & Glover 1996, ISBN 9780134565675; Xu et al. 2008.
  *Статус:* **ОТКРЫТА** — `ComputeBudget.maxSearchNodes` ограничивает поиск, но заявленный
  худший случай в допуск не входит.
- **H-118**: Число одновременно поддерживаемых гипотез должно быть ограничено, иначе
  «сравнение гипотез» теряет конечность. *Источник:* Miller 1956, *Psych. Rev.* 63(2),
  DOI 10.1037/h0043158; Anokhin 1968. *Статус:* **ОТКРЫТА** — пространство из 16 функций
  ограничено по построению, но общий предел на живые гипотезы не введён.
- **H-119**: Согласие между моделями не является само по себе преимуществом; набор гипотез
  должен быть сравнён со **случайным** набором при равном бюджете экспериментов.
  *Источник:* Smit et al. 2023, arXiv:2311.17371. *Статус:* **ОТКРЫТА** — базовой линии со
  случайными гипотезами нет.
- **H-120**: «Сон»-консолидация не является безусловным благом: при достаточном объёме
  данных она может ухудшать результат, поэтому утверждение о консолидации должно быть
  обусловлено измерением. *Источник:* Bazhenov et al. 2024, arXiv:2402.10956; Tadros et al.
  2022, *Nat. Commun.* 13, DOI 10.1038/s41467-022-34938-7. *Статус:* **ОТКРЫТА** — консолидация
  в W44 — это промоушен процедуры, а не replay; интерфейс offline-консолидации не спроектирован.
- **H-121**: Отказ селектора должен нести риск-контракт (калиброванный уровень риска), а не
  только имя отсутствующего предусловия. *Источник:* Geifman & El-Yaniv 2017,
  arXiv:1705.08500. *Статус:* **ОТКРЫТА** — `AlgorithmDecision.Status.REFUSED` называет
  предусловие, риск-уровень не указан.
- **H-122**: Пространство гипотез не является нейтральным стартом: даже младенческое знание
  ограничено ядерными принципами (непрерывность пути уже в 4 месяца), поэтому «перебор всех
  16 булевых функций» — это уже допущение об истинностно-функциональных правилах, и оно
  должно быть объявлено явно. *Источник:* Spelke, Katz, Purcell, Ehrlich & Breinlinger 1994,
  *Cognition* 51(2), DOI 10.1016/0010-0277(94)90013-2; Spelke & Kinzler 2007,
  *Developmental Science* 10(1), DOI 10.1111/j.1467-7687.2007.00569.x. *Статус:* **ОТКРЫТА** —
  приор над пространством не введён; `HypothesisSpace.allBooleanFunctionsOfTwoVariables()`
  объявляет полноту, но не нейтральность.
- **H-123**: Свидетельство ковариации — бесплатный эксперимент: причинные связи выводятся из
  одной вариации без вмешательства, поэтому песочница не должна отбрасывать пассивное
  наблюдение и должна различать незашумлённое и зашумлённое вмешательство. *Источник:*
  Gopnik, Sobel, Schulz & Glymour 2001, *Developmental Psychology* 37(5),
  DOI 10.1037/0012-1649.37.5.620; Kushnir & Gopnik 2005, *Psychological Science* 16(9),
  DOI 10.1111/j.1467-9280.2005.01595.x. *Статус:* **ОТКРЫТА** — в `Experiment` нет флага
  «зашумлено», наблюдательный канал отсутствует.
- **H-124**: Ошибка модели должна отделяться от перцептивного шума **до** ревизии: реакция на
  неожиданность сама по себе неоднозначна между обучением, утомлением и сенсорной
  адаптацией. *Источник:* Rankin, Abrams, Barry et al. 2009, *Neurobiol. Learn. Mem.* 92(2),
  DOI 10.1016/j.nlm.2008.09.012; Cashon & Cohen 2000, *Infancy* 1(4),
  DOI 10.1207/s15327078in0104_4; Munakata 2000, *Infancy* 1(4),
  DOI 10.1207/s15327078in0104_7. *Статус:* **ОТКРЫТА** — `ErrorSignal` не различает
  модельный и перцептивный уровень.
- **H-125**: Выбор эксперимента должен проходить **аудит локальности**: если выбранное
  окружение эксперимента не содержит гипотез, которые данные уже различают, селектор
  систематически не сможет их разделить. *Источник:* Kauffman 1993, *The Origins of Order*,
  ISBN 0-19-507951-5; Ferrer-i-Cancho 2015, arXiv:1512.05582. *Статус:* **ОТКРЫТА** — метрики
  расстояния между выигравшей и проигравшей гипотезой нет.
- **H-126**: Промоушен должен хранить **режим**, а не только согласие: причинная сила
  контекстуальна (вероятность × вмешательство × зашумлённость), и переиспользование
  дешёвой процедуры в другом режиме не подтверждено её же проверкой. *Источник:* Kushnir &
  Gopnik 2005, DOI 10.1111/j.1467-9280.2005.01595.x; Gopnik, Glymour, Sobel & Schulz 2004,
  *Psychological Review* 111(1), DOI 10.1037/0033-295X.111.1.3. *Статус:* **ОТКРЫТА** —
  `Procedure` хранит область и эталон, но не режим наблюдения.
- **H-127**: Офлайн-консолидация обязана **генерилизировать**, а не повторять: после
  консолидации модель обязана предсказывать новый удержанный случай лучше, чем до неё, иначе
  реплей отбрасывается. *Источник:* Friedrich, Wilhelm, Born & Friederici 2015,
  *Nature Communications* 6:6004, DOI 10.1038/ncomms7004; Schwartenbeck, Baram, Liu et al.
  2023, *Cell* 186(22), DOI 10.1016/j.cell.2023.09.004. *Статус:* **ОТКРЫТА** — офлайн-фазы
  в W44 нет.
- **H-128**: Каждая опубликованная точность должна быть **воспроизведена** во второй,
  независимо засеянной серии до того, как она предъявляется как свидетельство: эффекты при
  репликации в среднем вдвое меньше оригинала, и лишь 36 % репликаций значимы. *Источник:*
  Open Science Collaboration 2015, *Science* 349(6251), DOI 10.1126/science.aac4716.
  *Статус:* **ОТКРЫТА** — `ProcedureValidator` требует согласия на отложенных случаях, второй
  серии нет.

## W53 — distributed cognition (2026-11-09)

| The hypothesis | What it claims | The status | The evidence |
|---|---|---|---|
| **H-129** | a durable inbox and a durable intent are enough to cross two independently owned databases, without a distributed transaction | CONFIRMED in W53 | the peer's inbox: one row after the restart, its `updatedAt` equal to its `createdAt` |
| **H-130** | a confidence declared by another process is not independent evidence and cannot make a proposition supported | CONFIRMED | `RemoteReportAssessor`; the recorded verdict is `UNRESOLVED` even though every exchange succeeded |
| **H-131** | information that returned through another process is not independent corroboration | CONFIRMED IN PART | the lineage travels and is compared with what was supplied (`QUALIFIED`); the full propositional predicate is not written |
| **H-132** | reliability must stay out of the score until the evidence base is representative | OPEN | the direction is recorded in `docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md`; the estimate does not enter the selector |
| **H-133** | a delegation without the criterion by which the answer is judged is an open loop | CONFIRMED | the expectation travels in the request's context (`EXPECTATION_KEY`) |

- **H-129**: Соглашение между двумя процессами, каждый со своей базой, **не требует**
  распределённой транзакции: достаточно долговременного inbox у получателя, долговременного
  intent у отправителя и идемпотентного эффекта. *Источник:* Gray & Reuter, 1993,
  *Transaction Processing: Concepts and Techniques*; Kleppmann, 2018, *Designing
  Data-Intensive Applications*, ch. 11; Lamport, 1998, *Time and Clocks*. *Статус:*
  **ПОДТВЕРЖДЕНА** в волне W53 — входящий журнал пира, ключ по request id и хэшу полезной
  нагрузки, повтор возвращает первый результат; доказано в демонстрации двух процессов
  (`docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json`: после перезапуска пира в его
  долговременном инбоксе — **одна** строка на request id, и в ней **один** сохранённый
  результат; доказательство — счётчик строк и сохранённый результат, а не временные
  метки: пир пишет один момент на весь запрос, поэтому `updatedAt` равен `createdAt` на
  каждой строке и ничего не говорит о единственном исполнении). *Что не следует:* ровно-однократное исполнение в
  распределённом смысле — его нет и не будет без распределённой транзакции.
- **H-130**: Уверенность, объявленная другим процессом, **не является** независимым
  доказательством и не может сделать утверждение поддержанным; основа `DECLARED` или
  `MEASURED` — это заявление о происхождении числа, а не второе наблюдение. *Источник:*
  Pearl, 1988, *Probabilistic Reasoning in Intelligent Systems*; Hájek, 1997 (coherence and
  entailment in evidence); Nisbett & Ross, 1981 (*Human Inference*, the
  source-monitoring errors). *Статус:* **ПОДТВЕРЖДЕНА** — `RemoteReportAssessor` не даёт
  `SUPPORTED` без собственного свидетельства Chain A; в записанной демонстрации вердикт —
  `UNRESOLVED` при успешном обмене.
- **H-131**: Информация, вернувшаяся через другой процесс, **не является** независимой
  корроборацией: происхождение нужно сохранять через оба направления моста, и отчёт,
  выведенный из того, что Chain A сам передал, должен быть квалифицирован, а не поддержан.
  *Источник:* Sperber & Wilson, 1986 (relevance and the anti-occlusion of reasoning);
  Campagne & Gordon, 2008 (resource-dependent communication); Hutchins, 1995, *The Wizard of
  Oz* (coordination as a distributed cognitive system). *Статус:* **ПОДТВЕРЖДЕНА ЧАСТИЧНО** —
  родословна едет и сравнивается с переданным (`QUALIFIED`), но полная предикатная проверка
  «это утверждение вернулось от меня» не написана.
- **H-132**: Надёжность метода по нескольким наблюдениям **не должна** входить в счёт
  до тех пор, пока доказательная база не представительна; Уилсоновская нижняя граница
  даёт интервал, который при одном наблюдении из одного имеет ширину 0.730 и не различает
  ничего. *Источник:* Wilson, 1927, *JASA* 22(158); Brown, Cai & DasGupta, 2001,
  *Statistical Science* 16(2); Gelman, Carlin, Stern & Rubin, 2013 (hierarchical models for
  the "hockey stick" problem). *Статус:* **ОТКРЫТА** — направление решено и записано
  (`docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md`), оценка в счёт селектора не
  входит; удерживающий набор задач не измерен.
- **H-133**: Согласованность без центрального управления требует, чтобы запрос нёс
  **критерий** оценки ответа, а не только задачу: без критерия замыкание петли регулятора
  отсутствует, и делегирование превращается в открытый контур. *Источник:* Wiener, 1948,
  *Cybernetics*; Ashby, 1956, *An Introduction to Cybernetics* (requisite variety);
  Beer, 1972, *Brain as a Metastable Multicomponent System* (the law of requisite variety in
  the organisation). *Статус:* **ПОДТВЕРЖДЕНА** — ожидание координатора едет в контексте
  запроса (`EXPECTATION_KEY`), и без него ответ нечем оценивать.
