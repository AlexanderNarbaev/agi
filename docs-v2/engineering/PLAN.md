# PLAN

Что реализовано и что осталось (synth-only).

## W32 Wave (Sep 11-12 2026) — HDC × BitLinear hybrid brain implementation

**Trigger:** Universal autonomous directive после formalization W31 doctrine.

**Achievements (all VERIFIED):**
- 15 brain classes в `io.matrix.neuron/` (RUN 437-451)
- 22 test files added (brain tests + 7 legacy test batches)
- 258 W31 + 14 W32 unit/integration tests, 0 failures
- 8 performance benchmarks
- 6 design-документов (DESIGN-54..59)
- 1 ADR (ADR-2026-09-12-001)
- 1 cross-disciplinary research doctrine
- 18 hypotheses (H-051..H-068)
- 1 README + 1 arXiv paper draft
- ADR integration with existing MPDT (HierarchicalBrain)

**Capability Levels (DESIGN-58 v2) — все достигнуты кроме L4 (partial):**
- L0 Fabric: ✅ DONE
- L1 Pavlov + Sokolov: ✅ DONE
- L2 Spelke core knowledge: ✅ DONE
- L3 Cross-modal: ✅ DONE
- L4 Piaget sensorimotor: 🟡 PARTIAL (только NCA, без full sensorimotor loop)
- L5 Symbol grounding: ✅ DONE
- L6 Compositional: ✅ DONE

**Performance (CPU-only):**
- HdcEncoding.hamming: 37.9M ops/sec
- HdcBinding.bind: 21.4M ops/sec
- BitLinear.forward (64→64): 47K ops/sec
- 24× memory reduction vs dense embeddings

**Следующие шаги:**
- L4 sensorimotor loop completion (Piaget A-not-B with motor babble)
- Integration с реальным BitNet 3B+ для L5 production validation
- Native-image build с workaround для OOM (HammingNative C extension уже работает)
- arXiv preprint submission на базе draft в `docs-v2/research/W31-ARXIV-PAPER-DRAFT.md`
- Edge-AI startup pitch на базе позиционирования

**29 commits в W32 wave, HEAD `192d592b`, всё запушено в origin/main.**

## W31 Cross-Disciplinary Wave (Sep 11 2026) — направление следующих 6 месяцев

**Trigger:** пользователь дал директиву формализовать cross-disciplinary research как AGENTS-правила и архитектурные документы. Запущена wave 31.

**Результаты wave:**
- Документация ядра: `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` (доктрина, META-R1..R5).
- 6 design-документов: `DESIGN-54` (HDC × BitLinear × Boolean brain), `DESIGN-55` (Russian/Asian cybernetics integration), `DESIGN-56` (fuzzy bit continuous relaxation), `DESIGN-57` (Nyaya 4-state logic), `DESIGN-58` (capability levels roadmap), `DESIGN-59` (NCA brain).
- Hypotheses H-051..H-068 в `HYPOTHESES-NEW.md`.

**Стратегия следующих 6 месяцев:**

Capability Levels (DESIGN-58) — measurable developmental milestones:
- **Level 0**: Fabric (78 классов + brain) — DONE.
- **Level 1**: Pavlov operant conditioning (Sokolov 1963, Spitz 1957) — 1-2 недели.
- **Level 2**: Spelke 4-set core knowledge — 3-5 недель.
- **Level 3**: Cross-modal HDC paired (Mithen 1996) — 6-8 недель.
- **Level 4**: Piaget sensorimotor stage (Bernstein 1947 levels) — 9-12 недель.
- **Level 5**: Symbol grounding (Bloom 2000, Pinker 2007) — 13-18 недель.
- **Level 6**: Compositional reasoning (Tomasello 2003) — 19-26 недель.

RUN plan:
- 437-442 (1-2 недели): HDC core classes.
- 443-444 (3-4 недели): Pavlov + Spelke.
- 445-446 (4-6 недель): Cross-modal + NCA.
- 447-448 (6-8 недель): HDC-as-LLM integration.
- 449-450 (8-10 недель): Synthetic grammar + Sokolov habituation.

Compute reality:
- $0 на текущей машине (32GB RAM, no GPU): Levels 1-3 achievable соло.
- $300/мес Lambda Labs: full experimental cycle.
- $30K AWS spot: publication-grade validation.

Edge-AI brain on CPU, not AGI. Это нишевая позиция без прямых конкурентов.

## Реализовано в коде ()

### Ядро (всё работает, тесты зелёные)
- BIR-компилятор + 3 формы (TT, CLAUSESET, BDD) + JvmSimd/Fpga-бэкенды.
- 37 сайтов мигрированы на BIR; INV-1 source-scan страж в CI.
- Продюсеры: TsetlinTrainer (этап B FR-B1/B2), WisardProducer (H-010 accepted), MpdtGaProducer (baseline).
- ELSP-федерация: ElspChannel (Ed25519) + ElspChannelMlDsa (ML-DSA, JEP 497 native).
- Curriculum: devloop 12 классов (CompetenceAssessor, CurriculumEngine ZPD, MaturityGateKeeper MA-0..5).
- Lifecycle: CauldronProtocol, FnlGate (SHADOW→PROMOTED), ConsolidationCycle, PlanRunner Hoare, PlanPreprocessor AC-3.
- RicCI-топология знаний (ktopo).
- BirClassifier, Distiller, OnnxActivationTeacher (onnxruntime 1.29.0).
- CRD SignalModule/TaskCell в operator.
- **Algorithm library** (RUN 419-436): 78+ pure-function classes — bandits, BloomFilter, PageRank, Dijkstra, KdTree, RLE, Levenshtein, BellmanFord, FloydWarshall, NaiveBayes, KMeans, BoyerMoore, Sort, LinearRegression, LogisticRegression, TfIdf, XxHash, MultiLayerPerceptron, TokenBucket, HyperLogLog, CascadeFilter, MinHash, ReservoirSampler, SuffixArray, Trie, DynamicProgramming, BigArithmetic, Csv.
- **Native-image C-extension**: `HammingNative` через Project Panama FFM (`libtruthy_hamming.so`).

### Эксперименты (реальные цифры)
- **H-010 accepted** (EXP-010, 9 прогонов, median ×242, 9/9 точность).
- **H-002/H-003 refuted-toy** (EXP-002/003, GA быстрее и точнее на синтетике).
- **EXP-009B/C**: дистиллят BIR ×149 быстрее ORT-CPU на синтетическом FFN, fidelity.999; GPU-нога (RTX 5070 Ti): батч 0.02 мс, per-call 17.25 µs; **MATRIX BIR ×276 быстрее GPU на точечных вызовах**.
- **JMH-гейт Batch\*** выполнен: 32–69M ops/s, решение «оставить как есть».
- **Native build**: analysis phase complete (~30K types reachable), final C-link exhausts Mandrel container 7.85GB heap. Workaround: HammingNative C-extension через Project Panama FFM.

## Стек (актуальный)
Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Kafka-clients 4.3.1 · Testcontainers 1.21.3 · ML-DSA postquantum (JEP 497). См. [STANDARDS-MATRIX.md](STANDARDS-MATRIX.md).

## BLOCKED-EXT / отложено

| Блокер | Что нужно |
|---|---|
| DJL/ONNX экспорт **реального** LLM-среза (.onnx FFN Qwen-0.6B и т.п.) | python-тулчейн + веса; инфраструктура (OnnxActivationTeacher) уже готова |
| Доменные корпуса для полного EXP-002/003/009 вердикта | данные (удалены по директиве; могут быть восстановлены из git-истории) |
| **Energy-метрики** для гейта H-009 | wattmeter или модельное энергопотребление |
| Audio-events этап 3 | приоритизация DESIGN-06 |
| Квантовый код FR-D3 (BIR-to-MPS) | квантовый субстрат |
| FPGA-синтез | yosys/nextpnr |
| Полные цепи Ханселя (DESIGN-09 v2) | research wave |
| Native-image full build | Mandrel container >=10GB heap memory (W31 Phase X workaround sufficient) |

## Следующее (минимально-ценностные шаги)

1. **W31 RUN 437-450** (см. секцию выше): HDC+BitLinear+Boolean brain до Capability Level 3.
2. TLA+-спек-кандидаты (см. [architecture/FORMAL-CONTRACTS.md](../architecture/FORMAL-CONTRACTS.md)):
   - **RECON-W39**: спе-каталог заменён реестром
     [`formal/spec-implementation-map.yml`](../../formal/spec-implementation-map.yml)
     (машинно-читаемый, проверяется автоматически). Для каждой спецификации
     зафиксированы Java-исходники, production-вызывающие, тесты, свойства и
     **явный статус соответствия**. Валидатор
     `matrix-quality/.../SpecImplementationMapTest.java` не допускает статус
     `PRODUCTION_PATH_VERIFIED` без названного production-вызывающего.
   - **RECON-W39**: harness мутаций `scripts/mutation-harness.py` — гейт
     проверяется на способность краснеть. Отчёт:
     [`engineering/MUTATION-REPORT.md`](MUTATION-REPORT.md).
   - **RECON-W39**: перечень рисков перепроверен и переведён в статусы —
     [`engineering/RISK-REGISTER.md`](RISK-REGISTER.md).
   - `BRC-Step` (закрывает пробел `reasoning/`),
   - `ConjugateBudgeter-DP`,
   - `Memory-M4-Causal`,
   - `MCTS-LATS-Visit`.
3. ~~SDD-свип: спеки для топ-`needs-spec` (`reasoning/BrcChain`, `mediator/`, `hades/`, `memory/`, `rag/`).~~ ✅ **RUN 13 done**: SPEC-008/009/010/011/012.
4. Production-domain прогоны EXP-010/002/003 на восстановленных корпусах.
5. CUDA-нога EXP-009 через `onnxruntime_gpu` (Java) при доступности cuDNN-тулчейна.
6. Research-only Python: `scripts/bench_gpu_vs_bir.py` v2 с батчем (полные пороги).
7. **W31 publication prep**: arXiv preprint "Boolean+HDC+BitNet hybrid brain on CPU" after Level 3 demo.

## Wave W87-W91 — Multi-timestep integration metrics + Φ_linGauss + PhiID (Sep 14 2026)
- **W87** ConsciousBrain `appendTrajectory` + circular `long[8]` buffer + multi-timestep Φ/ΦR/ΦF/C_N/tickling. MultiTimestepIntegrationTest 4/4 PASS.
- **W88** Multi-timestep noise-floor re-validation. W88MultiTimestepNoiseFloorTest 5/5 PASS. **H-081 partially refuted** (multi-timestep inverts W76 signal-vs-noise claim).
- **W89** Φ_linGauss closed-form via covariance ln-determinant (Barrett-Seth 2011). PhiLinGaussTest 9/9 PASS. O(2^N · N³) for N ≤ 16.
- **W90** PhiID 4-atom decomposition (Mediano-Seth-Barrett 2020) — no external JIDT dep. PhiIdTest 9/9 PASS.
- **W91** Final synthesis report `docs-v2/research/W87-W91-FINAL-SYNTHESIS-REPORT.md` (610 lines). H-078..H-084 added. INDEX.md + WAL.md CHECKPOINT 32 updated.
- 27 new tests, 0 failures. All 4 commits pushed to `origin/main`.

## W92+ Future work (see §8 of W87-W91 synthesis report)
- W76 hypothesis revalidation with corrected noise-floor (autocorrelated-random vs iid-random)
- Φ_linGauss + PhiID integration into ConsciousBrain (one-line change, defer to W92)
- PhiID for discrete/binary systems (Icard-Finn-Mediano formulas)
- Cross-disciplinary R-B (cybernetic) R-C (Soviet/Asian) R-D (learning) R-E (substrate) R-F (creativity) research waves for integration metrics
- JNI/FFM acceleration of bipartitionMi / logDeterminant (~100× speedup)
---

## Wave W42 — Recon: локальная обучающая система (01–02 Nov 2026)

**Директива владельца:** RECON-W42 MASTER DEVELOPMENT MISSION · 2026-11-01.
**Ветка:** `develop` · **HEAD на 2026-11-02:** `6ea07101` (проверен через
`git ls-remote origin develop`, не по памяти) · **старт W42:** `0296e83e`.

> Файл `PLAN.md` не обновлялся с сентября 2026 (заканчивался на W92), а
> процедура сессии в `AGENTS.md` читает его, чтобы найти текущую волну.
> Поэтому раздел добавлен: без него следующая сессия не находит волну.

### Что сделано

| Волна | Что | Отчёт |
|---|---|---|
| W42.0 | Разведка реальности: 5 артефактов, инструмент бенчмарка, базовая линия | `W42-*.md` |
| W42.1 | Ядро обучения: машина состояний, планировщик, проверка запроса, абстиненция 0.400 → 0.700 | `W42.1-LEARNING-CORE-REPORT.md` |
| W42.2 | Приобретение знаний: TXT/MD/HTML/PDF/EPUB/DOCX/CSV, источник, слои промоушена, реестр корпуса | `W42.2-UNIVERSAL-ACQUISITION-REPORT.md` |
| W42.3 | Диалог: локализованный текстовый слой, лексикон, опечатки, ограниченный контекст, 168 кейсов | `W42.3-DIALOG-INTELLIGENCE-REPORT.md` |
| W42.4 | Обучение и оценка: отбор упражнений, отложенное удержание, композитор представлений | `W42.4-LEARNING-AND-REPRESENTATION-REPORT.md` |
| W42.5 | Когнитивная интеграция: детектор ложной посылки, консолидация, error-driven отбор | `W42.5-COGNITIVE-INTEGRATION-REPORT.md` |
| W42.6 | Local Alpha v0.3: 12 шагов по HTTP, переживает `SIGKILL` | `W42.6-LOCAL-ALPHA-V0.3.md` |
| W42.7 | Открытые пункты: версии источников, Source Browser, Knowledge Graph, Experiment Dashboard, сохранённый диалог | `RECON-W42-BACKLOG.md` §W42.7 |
| W42.9 | Асимметрия свёртки, ложная посылка по-русски, три независимых аудита | этот файл, §W42.9 |

### Где стоит волна сейчас

**Первый практический результат миссии показан на живом приложении:**
концепция, приобретённая из настоящего документа, объясняется собственными
знаниями системы, отвечается на четырёх уровнях, система знает свои
пределы, отвечает на ложную посылку **на русском и на английском**,
практикуется, оценивается независимо и переживает принудительный
перезапуск.

### Измерения на 2026-11-02

| Что | Значение |
|---|---|
| Целевые тесты (5 пакетов) | 1483 теста, 0 падений (после трёх независимых аудитов) |
| Формальный гейт | 14/14, exit 0 |
| Гейт качества | exit 0 (263 кандидата при принятой базе 270) |
| Бенчмарк W42 v1 | 34 пункта: точность 1.000, абстиненция 0.900, пересечение с BM25 0.610 |
| Бенчмарк W42 v3 | 137 пунктов, ru+en, с holdout-расщеплением |

### Следующее действие

1. **Зафиксировать аудиты.** Три независимых проверки (`W42-VERIFICATION-*.md`)
   нашли 93 расхождения в документации и 16 неверных утверждений в
   отчётах. Основные — дубли строк статуса, числа мутационного гейта
   (27/25/2 → 32/30/2), счётчик формального гейта (12/13/14 → 14),
   «девять теневых классов» → шесть, статус `ModelToMatrix`.
2. **Закрыть R32** — кросс-язычное сопоставление области действия: нужен
   собственный дизайн и собственные тесты, глобальная правка ломает три
   зелёных гейта (измерено).
3. **Оценить старт приложения.** Признанное значение 1.571 s — из волны
   W42.0; повторное измерение на упакованном JAR даёт иную картину
   (в старте доминирует обучение цепочки) — требуется отдельная запись.
