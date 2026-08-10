# MATRIX REBUILD SYNTHESIS
# Синтез целевой архитектуры (docs/matrix-rebuild) и текущей кодовой базы

**Ветка:** docs/matrix-rebuild
**Дата:** 2026-08-08
**Статус:** Рабочий синтез для полной пересборки

---

## 1. Целевая архитектура (из удалённой ветки)

### 1.1. Позиционирование

**MATRIX — детерминированное нейро-символическое ядро верификации и безопасного исполнения для ИИ-систем.** Не чат-бот, не AGI-декларация. Каждое решение — проверяемая булева цепочка.

### 1.2. Три продуктовые линии

| Линия | Назначение | Ключевые компоненты |
|-------|-----------|---------------------|
| **MATRIX Guardrail** | Слой верификации для LLM-систем: LLM генерирует план → булево ядро + SAT/BDD проверяет соответствие аксиомам → StructuralSafetyGuard решает | BIR, Verifier, EthicalFilter, StructuralSafetyGuard |
| **MATRIX Safety Core** | Детерминированный контроллер safety-critical ниш (SCADA): наносекундные решения TT-форм, полная аудируемость, FPGA-компиляция | BIR TT-формы, FpgaBackend, JvmSimdBackend |
| **MATRIX Knowledge Stack** | Булев слой точной верификации и нормативного поиска поверх источников знаний | HierarchicalMemory, KnowledgeIndex, BDD-эквивалентность |

### 1.3. Ключевой принцип

**Всё выше Boolean Compute Layer зависит только от контракта `evaluate(BIR, bits) → bits` и верификационных сервисов.** Замена субстрата (JVM→FPGA→квантовый) = новый backend; замена механизма обучения = новый producer.

### 1.4. SPEC-документы (нормативные)

| SPEC | Название | Что определяет |
|------|----------|----------------|
| SPEC-000 | Developmental Loop | MA-уровни зрелости, competence gates, scenario specs, scaffolding fade |
| SPEC-001 | Weight Conversion | LLM → булева дистилляция (веса → BIR-артефакты) |
| SPEC-002 | Boolean Compute Layer | BIR: три формы (TT/CLAUSESET/BDD), компилятор, верификатор, backends |
| SPEC-003 | Knowledge Topology | Граф знаний, Ricci flow, семантические сообщества, drift fingerprint |

### 1.5. DESIGN-документы (13 штук)

| DESIGN | Тема | Ключевые компоненты |
|--------|------|---------------------|
| DESIGN-01 | Вычислительная единица | Гибридный нейрон: TT + CLAUSESET + BDD в одной обёртке |
| DESIGN-02 | Композиция | Кластеры, сети, точки зрения, профессионалы |
| DESIGN-03 | Контур запроса | PERCEPTION → DELIBERATION → RENDERING, Avro-контракты |
| DESIGN-04 | Обучение | TsetlinTrainer (первичный), Distiller, GATopologySearch, ThreeFactorRule |
| DESIGN-05 | Память | M0-M4 слои, консолидация («сон»), tombstone, журнал событий |
| DESIGN-06 | Модули сигналов | SignalModule контракт, реестр, медиа-линейки |
| DESIGN-07 | Жизненный цикл | Cauldron, TaskCell, FNL, побуждающая активность, сон-цикл, K8s Operator |
| DESIGN-08 | Федерация | Подписи, k-анонимность, ECC-кандидаты, MitM-стойкость |
| DESIGN-09 | MonotoneDecoder | Producer монотонных CLAUSESET (Хансель/Кабулов-Норматов) |
| DESIGN-10 | Binary Reservoir | Бинарный резервуар как динамическая память |
| DESIGN-11 | Budgeter-Homeostat | Сопряжённый бюджетер рядов Cauldron, гомеостат коридоров |
| DESIGN-12 | FNL + TaskCell | Карантин свежих элементов, эфемерные задачные инстансы |
| DESIGN-13 | Action Registry | Реестр действий с ioSchema (контракты Хоара / PDDL) |

### 1.6. BIR — Boolean Intermediate Representation (keystone)

| Форма | Арность | Сильная сторона | Слабость | Роль |
|-------|---------|-----------------|----------|------|
| **TT** (таблица истинности) | k ≤ 20 | каноническая семантика, наносекундный SIMD-eval, LUT-маппинг | память 2^k | эталон семантики; FPGA |
| **CLAUSESET** (клаузы Цетлина) | не ограничена | сжатие разреженных функций, встроенное обучение, интерпретируемые правила | не канонична | **первичная форма обучения и хранения знаний** |
| **BDD** (решающая диаграмма) | практически не ограничена | канонична → точная проверка эквивалентности | взрыв на отдельных классах | **верификация и аудит** |

---

## 2. Текущая кодовая база (что есть сегодня)

### 2.1. Что реализовано и работает (verified end-to-end)

| Компонент | Пакет | Соответствие целевой архитектуре |
|-----------|-------|----------------------------------|
| TruthTable | `io.matrix.neuron` | BIR TT-форма (адаптер нужен) |
| DecisionTree | `io.matrix.neuron` | BIR TT-форма (адаптер нужен) |
| HierarchicalBrain | `io.matrix.neuron` | Multi-layer BIR composition (частично) |
| MultiBrainEnsemble | `io.matrix.neuron` | Multi-model ensemble (аналог producer pool) |
| NeuralTextGenerator | `io.matrix.neuron` | 3-layer hierarchy (encoder→compression→output) — аналог BRC-цепочки |
| NeuralMemoryResponse | `io.matrix.neuron` | Corpus retrieval (не целевой путь; целевой — генерация) |
| EthicalFilter | `io.matrix.ethics` | L7 этика (совместимо) |
| HierarchicalMemory | `io.matrix.memory` | L6 память (in-memory, нужен SQLite/RocksDB) |
| BrainPipeline (3-block) | `io.matrix.brain` | DESIGN-03 pipeline (частично) |
| LongHorizonPlanner | `io.matrix.agent` | DESIGN-03 §3 BRC (частично) |
| SubAgent | `io.matrix.agent` | DESIGN-12 TaskCell (частично) |
| ToolsResource | `io.matrix.tools` | DESIGN-13 Action Registry (частично) |
| Event Sourcing | `io.matrix.events` | L6 EventJournal (Kafka/Avro) |
| ConversationRecorder | `io.matrix.chat` | L6 журнал разговоров |
| ChatDrivenTrainer | `io.matrix.chat` | L7 самообучение (частично) |
| OpenAI-compatible API | `io.matrix.api` | Внешний интерфейс (не целевой) |
| Grafana dashboards | K8s | L10 мониторинг |
| MinIO | K8s | L6 S3-хранилище |
| Pretrained models (6) | `models/pretrained/` | Временное состояние (SPEC-001 заменит) |

### 2.2. Что частично реализовано (есть, но не по спеке)

| Компонент | Проблема | Что нужно |
|-----------|----------|-----------|
| BIR модуль | Не существует | Создать `matrix-bir` модуль с TT/CLAUSESET/BDD формами, компилятором, верификатором |
| TsetlinTrainer | Не существует | Создать `matrix-tsetlin` модуль с автоматами Цетлина (SPEC-002 этап B) |
| BDD | Не существует | Собственный Java BDD (unique-table + computed-cache) |
| Multi-modal | Stubs | FeatureExtractors возвращают `[image:512feats]` — нужен реальный декодинг |
| World model persistence | In-memory | HierarchicalMemory нужен SQLite/RocksDB backend |
| Sub-agent sandbox | Частичный | Нужна process isolation для SubAgent |
| Long-horizon planning execution | Текстовые шаги | Нужно wire tools into planner steps |
| Coverage measurement | Blocked | Quarkus native-image фильтрует jacoco agent |
| Sequential HF training | Script ready | sequential-train.sh существует, не интегрирован в CI |
| Sub-agent memory write-back | Отсутствует | SubAgent результаты не сохраняются в HierarchicalMemory |
| Multi-instance mesh | Частичный | Kafka event journal есть, но нет live multi-instance communication |
| FROZEN neuron audit | Частичный | Ethical filter работает, но нет автоматической верификации FROZEN neurons |

### 2.3. Что отсутствует полностью (нужно создать)

| Компонент | Документ | Описание |
|-----------|----------|----------|
| **matrix-bir** | SPEC-002 | BIR ядро: TT/CLAUSESET/BDD формы, компилятор, верификатор |
| **matrix-tsetlin** | SPEC-002 этап B | TsetlinTrainer: автоматы Цетлина, клаузы, feedback типов I/II |
| **matrix-bdd** | SPEC-002 этап A | Собственный Java BDD (unique-table + computed-cache) |
| **Developmental Loop** | SPEC-000 | MA-уровни, CompetenceAssessor, CurriculumEngine, ScaffoldingManager, MaturityGateKeeper |
| **Weight Conversion** | SPEC-001 | LLM → булева дистилляция: Distiller, ActivationCapture, TruthTableSynthesis |
| **Knowledge Topology** | SPEC-003 | Ricci flow, семантические сообщества, drift fingerprint |
| **SignalModule реестр** | DESIGN-06 | Модули входящих/исходящих сигналов с контрактом и версионированием |
| **Action Registry** | DESIGN-13 | Реестр действий с ioSchema, контракты Хоара |
| **FNL карантин** | DESIGN-12 | Карантин свежих элементов, откат |
| **TaskCell lifecycle** | DESIGN-12 | Эфемерные задачные инстансы с полным контекстом |
| **Cauldron protocol** | DESIGN-07 | Самосоздание элементов с бюджетами, карантином, откатом |
| **Sleep cycle** | DESIGN-07 §6 | Разроутинг кластеров на время консолидации |
| **K8s Operator** | DESIGN-07 §7 | Декларативное создание/масштабирование элементов |
| **Federation** | DESIGN-08 | Подписи, k-анонимность, ECC-кандидаты |
| **Binary Reservoir** | DESIGN-10 | Бинарный резервуар как динамическая память |
| **Budgeter-Homeostat** | DESIGN-11 | Сопряжённый бюджетер рядов Cauldron |
| **MonotoneDecoder** | DESIGN-09 | Producer монотонных CLAUSESET |

---

## 3. Gap-анализ: текущее vs целевое

### 3.1. Критические расхождения

| Аспект | Текущее | Целевое | Gap |
|--------|---------|---------|-----|
| **Основной формат** | TruthTable/DecisionTree (TT-only) | BIR: TT + CLAUSESET + BDD | Нужен BIR модуль с 3 формами и компилятором |
| **Обучение** | Genetic Algorithm (EvolutionLoop) | TsetlinTrainer (первичный) + Distiller + GATopologySearch | Нужен TsetlinTrainer как primary producer |
| **Генерация текста** | Corpus retrieval (NeuralMemoryResponse) | Генерация через BIR-цепочки (BRC) | Нужно обучить BIR-цепочки на корпусе |
| **Верификация** | Нет | BDD-эквивалентность, SAT-доказательства | Нужен BDD пакет |
| **Память** | HierarchicalMemory (in-memory) | M0-M4 слои с persistence | Нужен backend (SQLite/RocksDB) |
| **Планирование** | LongHorizonPlanner (текстовые шаги) | BRC-цепочки с witness | Нужно wire tools into steps + witness |
| **Субагенты** | SubAgent (direct call) | TaskCell (эфемерный, sandboxed) | Нужна process isolation + lifecycle |
| **Мультимодальность** | Stubs (length-only summaries) | SignalModule контракт с реальным декодингом | Нужен реальный image/audio parsing |
| **Федерация** | Нет | DESIGN-08: подписи, k-анонимность, ECC | Нужен federation layer |
| **Developmental loop** | Нет | SPEC-000: MA-уровни, competence gates | Нужен CompetenceAssessor + CurriculumEngine |
| **Weight conversion** | Статические pretrained модели | SPEC-001: LLM → булева дистилляция | Нужен Distiller |
| **Knowledge topology** | Нет | SPEC-003: Ricci flow, семантические сообщества | Нужен graph analysis |

### 3.2. Что можно переиспользовать (strangler-fig подход)

| Существующий компонент | Как переиспользовать |
|------------------------|----------------------|
| TruthTable/DecisionTree | Завернуть в BIR как TT-формы (адаптер, не переписывание) |
| EthicalFilter | Сохранить как L7 этика, интегрировать с BIR Verifier |
| HierarchicalMemory | Добавить SQLite/RocksDB backend, сохранить API |
| EventJournal | Сохранить, интегрировать с BIR provenance |
| BrainPipeline | Сохранить как DESIGN-03 pipeline, заменить внутренности на BIR |
| LongHorizonPlanner | Сохранить как BRC-цепочка orchestrator |
| SubAgent | Сохранить как TaskCell, добавить sandboxing |
| ToolsResource | Сохранить как Action Registry, добавить ioSchema |
| ChatDrivenTrainer | Сохранить как self-improvement loop, заменить на TsetlinTrainer |
| Grafana dashboards | Сохранить, добавить BIR-метрики |
| MinIO | Сохранить как S3-хранилище для snapshots |

---

## 4. План пересборки (phased)

### Phase 0: BIR Foundation (keystone, 2 недели)

**Цель:** Создать `matrix-bir` модуль с тремя формами и компилятором.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| Avro-схема BIR | `matrix-bir/src/main/avro/bir.avdl` | Схема валидна |
| TT-форма | `matrix-bir/.../TtForm.java` | jqwik: eval(TT) = eval(TruthTable) |
| CLAUSESET-форма | `matrix-bir/.../ClauseSetForm.java` | jqwik: eval(CLAUSESET) = голосование TM |
| BDD-форма | `matrix-bir/.../BddForm.java` | jqwik: каноничность, eval(BDD) = eval(TT) k≤16 |
| Компилятор TT⇄BDD | `matrix-bir/.../Compiler.java` | Точная конверсия |
| Компилятор TT→CLAUSESET | `matrix-bir/.../Compiler.java` | Эспрессо-минимизация |
| Верификатор BDD-экв. | `matrix-bir/.../Verifier.java` | Точная проверка |
| BooleanRuntime | `matrix-bir/.../BooleanRuntime.java` | `evaluate(BIR, bits)→bits` детерминизм |
| Адаптер TruthTable→BIR | `matrix-bir/.../TruthTableAdapter.java` | Обратная совместимость |
| JMH бенчмарк | `matrix-bir/.../EvaluateBenchmark.java` | нс/вызов по формам |

**Критерий:** Весь существующий функционал ядра исполняется через BIR без регрессии тестов; JMH-отклонение latency ≤ 10%.

### Phase 1: TsetlinTrainer (2 недели)

**Цель:** Создать `matrix-tsetlin` модуль как первичный producer.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| Автоматы Цетлина | `matrix-tsetlin/.../TsetlinAutomaton.java` | 2N состояний, монотонность feedback |
| Клаузы | `matrix-tsetlin/.../Clause.java` | Голосование, типы I/II feedback |
| Экспорт в CLAUSESET | `matrix-tsetlin/.../ClauseSetExporter.java` | Property: eval(CLAUSESET) = голосование |
| Интеграция с BIR | `matrix-tsetlin/.../TsetlinProducer.java` | Producer interface |
| EXP-002 сравнение | `matrix-tsetlin/.../Exp002Comparison.java` | Точность, нс/решение, интерпретируемость |

**Критерий:** EXP-002 accepted: Tsetlin-автоматы vs BNN vs MPDT-ГА на идентичных бинаризованных входах.

### Phase 2: Developmental Loop (2 недели)

**Цель:** SPEC-000 — MA-уровни зрелости, competence gates, scaffolding.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| Avro-схемы | `matrix-devloop/.../ScenarioSpec.avdl` | ScenarioSpec, CompetenceReport, MaturityTransition |
| CompetenceAssessor | `matrix-devloop/.../CompetenceAssessor.java` | Батарейный набор (XOR→GridWorld→крафт-граф) |
| CurriculumEngine | `matrix-devloop/.../CurriculumEngine.java` | LP-выбор, конфиг ЗБР |
| FeedbackComposer | `matrix-devloop/.../FeedbackComposer.java` | BRC-контрпримеры |
| ScaffoldingManager | `matrix-devloop/.../ScaffoldingManager.java` | DSL расписаний затухания |
| MaturityGateKeeper | `matrix-devloop/.../MaturityGateKeeper.java` | MA переходы, drift-понижение, Event Sourcing |
| ArchUnit INV-2 | `matrix-devloop/.../Inv2Rule.java` | Sandbox enforcement |
| Интеграция AgentLoop | `matrix-devloop/.../AgentLoopIntegration.java` | Curriculum как источник целей |

**Критерий:** Сценарии §2 воспроизводятся в интеграционных тестах; INV-1..4 покрыты тестами включая мутационный тест.

### Phase 3: Weight Conversion (1 неделя)

**Цель:** SPEC-001 — LLM → булева дистилляция.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| Distiller | `matrix-distill/.../Distiller.java` | LLM → активации → BIR |
| ActivationCapture | `matrix-distill/.../ActivationCapture.java` | Захват активаций из LLM |
| TruthTableSynthesis | `matrix-distill/.../TruthTableSynthesis.java` | Активации → TT |
| Интеграция с BIR | `matrix-distill/.../DistillProducer.java` | Producer interface |

**Критерий:** Distilled BIR-артефакты воспроизводят LLM-поведение на тестовом корпусе с fidelity ≥ 0.9.

### Phase 4: Knowledge Topology (1 неделя)

**Цель:** SPEC-003 — граф знаний, Ricci flow, семантические сообщества.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| KnowledgeGraph | `matrix-ktopo/.../KnowledgeGraph.java` | Узлы, рёбра, provenance |
| RicciFlow | `matrix-ktopo/.../RicciFlow.java` | Кривизна Олливье-Риччи, итеративный поток |
| CommunityDetection | `matrix-ktopo/.../CommunityDetection.java` | Семантические сообщества |
| DriftFingerprint | `matrix-ktopo/.../DriftFingerprint.java` | Распределение кривизны как подпись |
| Интеграция с HierarchicalMemory | `matrix-ktopo/.../MemoryGraphLink.java` | Граф знаний поверх памяти |

**Критерий:** Ricci flow выравнивает кривизну на тестовом графе; fingerprint детектирует drift.

### Phase 5: Signal Modules + Action Registry (1 неделя)

**Цель:** DESIGN-06 + DESIGN-13 — реестр модулей сигналов и действий.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| SignalModule контракт | `matrix-signals/.../SignalModule.java` | Интерфейс, версионирование |
| SignalModule реестр | `matrix-signals/.../SignalModuleRegistry.java` | Регистрация, discovery |
| TextSignalModule | `matrix-signals/.../TextSignalModule.java` | Текст → биты |
| ImageSignalModule | `matrix-signals/.../ImageSignalModule.java` | Image → биты (real decoding) |
| AudioSignalModule | `matrix-signals/.../AudioSignalModule.java` | Audio → биты (real decoding) |
| ActionRegistry | `matrix-actions/.../ActionRegistry.java` | ioSchema, контракты Хоара |
| PDDL-совместимость | `matrix-actions/.../PddlMapper.java` | Доменный слой |

**Критерий:** SignalModule для текста, image, audio работают с реальным декодингом; ActionRegistry поддерживает ioSchema.

### Phase 6: Lifecycle (Cauldron + TaskCell + Sleep) (1 неделя)

**Цель:** DESIGN-07 — жизненный цикл элементов.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| CauldronProtocol | `matrix-lifecycle/.../CauldronProtocol.java` | Бюджеты, карантин, откат |
| TaskCell | `matrix-lifecycle/.../TaskCell.java` | Эфемерные инстансы, полный контекст |
| FNL карантин | `matrix-lifecycle/.../FnlQuarantine.java` | Карантин свежих элементов |
| SleepCycle | `matrix-lifecycle/.../SleepCycle.java` | Разроутинг кластеров на консолидацию |
| K8s Operator | `matrix-operator/.../MatrixOperator.java` | Декларативное создание элементов |

**Критерий:** Сценарные тесты рождения/отката; тест жизненного цикла TaskCell.

### Phase 7: Federation (1 неделя)

**Цель:** DESIGN-08 — федерация знаний.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| ArtifactSigner | `matrix-federation/.../ArtifactSigner.java` | Ed25519 подписи |
| Anonymizer | `matrix-federation/.../Anonymizer.java` | k-анонимность (k=100) |
| ImportGate | `matrix-federation/.../ImportGate.java` | Φ-гейт импорта |
| NoosphereRegistry | `matrix-federation/.../NoosphereRegistry.java` | Глобальный реестр |

**Критерий:** MitM-сценарии покрыты тестами; аудит анонимизации.

### Phase 8: Integration + Polish (1 неделя)

**Цель:** Интегрировать все модули, убрать legacy, достичь 82% coverage.

| Задача | Файлы | Проверка |
|--------|-------|----------|
| Legacy deprecation | `io.matrix.neuron.*` → deprecated | Обратная совместимость через адаптеры |
| Coverage 82% | `build.gradle` | jacocoTestReport ≥ 82% METHOD |
| Integration tests | `matrix-integration/.../FullPipelineTest.java` | End-to-end через BIR |
| Documentation | `docs/` | API docs, deployment guide |

**Критерий:** Все тесты проходят; coverage ≥ 82%; API документирован.

---

## 5. Миграционная стратегия (strangler-fig)

```
Текущее состояние          Переходное              Целевое
─────────────────          ──────────              ───────
TruthTable ──────────────► BIR TT-adapter ────────► BIR TT
DecisionTree ─────────────► BIR TT-adapter ────────► BIR TT
EvolutionLoop ────────────► TsetlinTrainer ───────► TsetlinTrainer (primary)
NeuralMemoryResponse ─────► BIR-chain generator ──► BIR-chain generator
LongHorizonPlanner ───────► BRC-orchestrator ─────► BRC-orchestrator
SubAgent ─────────────────► TaskCell ─────────────► TaskCell
ToolsResource ────────────► ActionRegistry ───────► ActionRegistry
HierarchicalMemory ───────► HierarchicalMemory+DB ► HierarchicalMemory+DB
ChatDrivenTrainer ────────► SelfImprovementLoop ──► SelfImprovementLoop
```

**Принцип:** Старый API deprecated, но работает. Новый код пишется через BIR. Старый код заворачивается в адаптеры.

---

## 6. Что делает систему "настоящей" (vs текущее состояние)

| Свойство | Текущее (v3.59.3) | Целевое (BIR-архитектура) |
|----------|-------------------|---------------------------|
| **Формат знаний** | TruthTable/DecisionTree | BIR: TT + CLAUSESET + BDD |
| **Верификация** | Нет | BDD-эквивалентность (точная), SAT-доказательства |
| **Обучение** | Genetic Algorithm | TsetlinTrainer (первичный), Distiller, GATopologySearch |
| **Генерация** | Corpus retrieval | BIR-цепочки с witness |
| **Интерпретируемость** | Частичная (DecisionTree) | Полная (BIR-цепочки + CLAUSESET правила) |
| **Воспроизводимость** | Детерминизм (TT) | Бит-в-бит (BIR + Event Sourcing) |
| **Энергоэффективность** | SIMD TT-eval | TT + CLAUSESET + BDD + FPGA path |
| **Субстратная независимость** | JVM only | JVM → native → FPGA → quantum |
| **Developmental loop** | Нет | MA-уровни, competence gates, scaffolding |
| **Federation** | Нет | Подписи, k-анонимность, Noosphere |
| **Action registry** | Нет | ioSchema, контракты Хоара |
| **Coverage** | ~44% (некоторые классы) | ≥82% METHOD (все классы) |

---

## 7. Риски и митигация

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| BIR модуль слишком сложен | Средняя | Начать с TT-адаптера, добавить BDD позже |
| TsetlinTrainer медленнее ГА | Средняя | EXP-002 сравнение до выбора primary producer |
| BDD взрыв на больших функциях | Высокая | Использовать BDD только для верификации k≤20 |
| Legacy код не мигрируется | Средняя | Strangler-fig: адаптеры, не переписывание |
| Coverage не достигает 82% | Высокая | Фокус на новых модулях, legacy excluded |
| Sequential HF training не стабилен | Средняя | sequential-train.sh с checkpointing |

---

## 8. Критерии успеха пересборки

1. **BIR-ядро работает:** `evaluate(BIR, bits)→bits` для всех трёх форм, детерминизм, JMH ≤ 10% отклонение.
2. **TsetlinTrainer — primary producer:** EXP-002 accepted, точность ≥ базовой линии.
3. **Developmental loop:** MA-0→MA-1→MA-2 переходы работают, INV-1..4 покрыты.
4. **Chat через BIR:** Ответы генерируются через BIR-цепочки, не corpus retrieval.
5. **Coverage ≥82%:** jacocoTestReport METHOD ≥ 82%.
6. **Все 13 DESIGN-документов:** реализованы и покрыты тестами.
7. **Все 4 SPEC-документа:** реализованы и верифицированы.

---

**End of REBUILD_SYNTHESIS.md**
