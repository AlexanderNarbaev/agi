# MATRIX — Отчёт о критических пробелах (Code Review)

**Дата:** 2026-07-17
**Основание:** Аудит 10 ключевых исходных файлов на соответствие L0–L7 спецификациям
**Аудитор:** goal-deep-researcher + goal-implementer
**Версия:** v1.0

---

## Сводка

| Серьёзность | Количество | Описание |
|-------------|-----------|----------|
| 🔴 CRITICAL | 5 | Нарушение спецификаций, гонки данных, отсутствие обязательных проверок безопасности |
| 🟠 HIGH | 8 | Потенциальные баги, отсутствие валидации, девиация от спец |
| 🟡 MEDIUM | 10 | Качество кода, неполные проверки, нестабильные идентификаторы |
| 🟢 LOW | 2 | Мелкие улучшения, мёртвый код |

**Итого: 25 проблем.** Все требуют исправления.

---

## 🔴 CRITICAL (немедленное исправление)

### GAP-001: Гонка данных в EvolutionLoop.evaluateGenerationParallel()

**Файл:** `matrix-core/src/main/java/io/matrix/evolution/EvolutionLoop.java:151-169`

**Проблема:** 4 виртуальных потока одновременно вызывают `evaluatePopulation()`, который читает `chromosomes()` из всех 4 популяций. Если `Population.chromosomes()` возвращает внутренний mutable список, параллельные чтения + `updateFitness()` создают data race.

**Спецификация:** L2 §1 — "Все взаимодействия асинхронные... без блокирующих вызовов". Детерминированная оценка фитнеса обязательна.

**Исправление:** Синхронизировать доступ к `Population.chromosomes()` через `synchronized` блок или использовать `Collections.unmodifiableList()` + копии для чтения.

---

### GAP-002: ConsensusEngine не потокобезопасен

**Файл:** `matrix-core/src/main/java/io/matrix/consensus/ConsensusEngine.java`

**Проблема:** `proposals` (HashMap), `votes` (HashMap), `decisions` (HashMap), `eventLog` (ArrayList) — plain Java collections без синхронизации. Асинхронные вызовы `propose()`, `castVote()`, `evaluate()` из разных потоков приводят к повреждению состояния.

**Спецификация:** L2 §2 — "Асинхронность: все взаимодействия — обмен сообщениями, без блокирующих вызовов".

**Исправление:** Заменить на `ConcurrentHashMap` для `proposals`/`votes`/`decisions`, `CopyOnWriteArrayList` для `eventLog`.

---

### GAP-003: EthicalFilter не реализован как FROZEN FNL

**Файл:** `matrix-core/src/main/java/io/matrix/ethics/EthicalFilter.java`

**Проблема:** Спецификация L5 §5.5 и L7 §3.1 требуют Этический фильтр как **FROZEN FNL** (сеть MPDT-нейронов с замороженными таблицами истинности). Текущая реализация — строковый matching на Java enum. Это структурное отклонение от ключевого принципа безопасности: "Безопасность через неизменяемость на уровне нейронов, не на уровне Java-кода".

**Спецификация:** L5 §5.1 — "FROZEN flagged neurons must be immutable in code (final classes, unmodifiable collections)"; L7 §3.1 — "Этический фильтр = FROZEN FNL, неизменяемая без внешнего крипто-консенсуса".

**Исправление:** Спроектировать и реализовать Этический фильтр как сеть MPDT-нейронов с FROZEN-состоянием, встроенных в каждый инстанс при инициализации.

---

### GAP-004: Двойной подсчёт голосов в ConsensusEngine.evaluateWeighted()

**Файл:** `matrix-core/src/main/java/io/matrix/consensus/ConsensusEngine.java:287-289`

**Проблема:** Голоса пересчитываются при каждом вызове `evaluateWeighted()` без очистки или дедупликации. Два вызова удваивают эффективное количество голосов.

**Спецификация:** L2 §3.1 — "Функция f детерминирована и верифицируема".

**Исправление:** Добавить дедупликацию голосов по voter ID или очистку после подсчёта.

---

### GAP-005: CauldronProtocol.evolve() без этического аудита

**Файл:** `matrix-core/src/main/java/io/matrix/cauldron/CauldronProtocol.java`

**Проблема:** `evolve()` и `evolveForTask()` завершаются без вызова `EthicalFilter.evaluate()`. Сгенерированный FNL идёт напрямую в упаковку.

**Спецификация:** L5 §4.2 шаг 5: "Новая FNL проходит обязательный этический аудит перед интеграцией". L7 §4.3: "Cauldron-сгенерированные FNL проходят обязательный этический аудит; не прошедшие → перезапуск Cauldron с ограничениями".

**Исправление:** Добавить вызов `EthicalFilter.evaluate()` после `evolve()` и перед `packageResult()`. При REJECTED — перезапустить Cauldron.

---

## 🟠 HIGH (важные, в ближайший спринт)

### GAP-006: TruthTable.fromAvroBytes() без валидации k

**Файл:** `TruthTable.java:429`
**Проблема:** Десериализованный `k` не проверяется на `1 ≤ k ≤ K_MAX`. Вредоносный Avro может создать невалидный нейрон.
**Спецификация:** L1 §6.1 — "k ≤ K_MAX. Violation makes the neuron invalid."
**Исправление:** Добавить `if (k < 1 || k > K_MAX) throw new IllegalArgumentException(...)`.

### GAP-007: Отсутствует Relevance Check

**Файл:** `TruthTable.java`
**Проблема:** L1 §6.6: "Каждый входной бит должен влиять на выход хотя бы для одного вектора". Метод `validateRelevance()` не реализован.
**Исправление:** Реализовать проверку: для каждого входного бита i, ∃ два вектора, различающихся только в бите i, дающие разный выход.

### GAP-008: HadesProtocol.execute() мутирует входной Map

**Файл:** `HadesProtocol.java:126,148-150`
**Проблема:** `neurons.remove(id)` и `clear()` + `putAll()` — деструктивные операции на данных вызывающего.
**Исправление:** Работать с копией: `new HashMap<>(neurons)`.

### GAP-009: HadesProtocol без проверки FROZEN-статуса

**Файл:** `HadesProtocol.java:126`
**Проблема:** `neurons.remove(id)` может удалить FROZEN-нейрон.
**Спецификация:** L5 §5.1 — "FROZEN neurons cannot be... deleted."
**Исправление:** Проверять `neuron.state() == NeuronState.FROZEN` перед удалением.

### GAP-010: DecisionTree — отсутствие null-проверок

**Файл:** `DecisionTree.java:138-143, 362, 389, 407, 430`
**Проблема:** Методы `evaluate()`, `evaluateFlat()`, `evaluateFlatBatch()` не проверяют входные параметры на null и границы массивов.
**Исправление:** Добавить `Objects.requireNonNull(input, "input")` и bounds checks.

### GAP-021: Отсутствует формальная верификация FROZEN

**Проблема:** L5 §5.4 требует "Формальную верификацию FROZEN (model checking, TLA+)". Не начато.
**Исправление:** Запустить проект формальной верификации: model checking протоколов консенсуса, доказательство неизменности FROZEN-нейронов.

### GAP-022 & GAP-023: Proactive scanning и adversarial detection

**Спецификация:** L7 §3.6 (периодическое сканирование), L7 §3.7 (защита от adversarial-атак).
**Проблема:** Не реализованы.
**Исправление:** Реализовать фоновые задачи сканирования драйверов/мутаций/сигналов и фильтрации adversarial-входов в Сенсорном прокси.

---

## 🟡 MEDIUM (плановые улучшения)

| GAP | Файл | Проблема | Исправление |
|-----|------|----------|-------------|
| 011 | EthicalFilter.java:101 | Мёртвый параметр `keywords` в `evaluate()` | Использовать или удалить |
| 012 | EthicalFilter.java | TRUTHFULNESS и PRIVACY без keyword detection | Добавить наборы ключевых слов |
| 013 | EthicalFilter.java:154 | Substring matching ("skill"→NO_KILLING) | Whole-word matching |
| 014 | EvolutionLoop.java:72-77 | NPE при пустой популяции в `bestOverall()` | Проверка на null/empty |
| 015 | StructuralSafetyGuard.java:141 | Нестабильные Gate ID (UUID) | Детерминированные ID |
| 016 | CauldronProtocol.java | ArrayList log + state не thread-safe | Синхронизация |
| 017 | HadesProtocol.java | ArrayList log + state не thread-safe | Синхронизация |
| 018 | EthicalFilter.java:133 | NPE на null threshold в evaluateFull() | Objects.requireNonNull |
| 024 | L6, L12 | GDPR tombstoning неполный | Полный аудит и доработка |
| 025 | L2 §3.1 | Latency targets не верифицированы JMH | Добавить бенчмарки |

## 🟢 LOW (технический долг)

| GAP | Файл | Проблема |
|-----|------|----------|
| 019 | AgentLoop.java:507 | actionCodeToThought() хардкодит 5 бит |
| 020 | ConsensusEngine.java:48 | Мёртвое поле debateProtocol |

---

## Статистика по файлам

| Файл | Thread Safety | Spec Compliance | CRITICAL | HIGH | MEDIUM | LOW |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| TruthTable.java | ✅ | ⚠️ | — | 2 | — | — |
| DecisionTree.java | ✅ | ✅ | — | 1 | — | — |
| StructuralSafetyGuard.java | ✅ | ✅ | — | — | 1 | — |
| **EthicalFilter.java** | ✅ | ❌ FAIL | 1 | — | 3 | — |
| **EvolutionLoop.java** | ❌ FAIL | ⚠️ | 1 | — | 1 | — |
| AgentLoop.java | ✅ | ✅ | — | — | — | 1 |
| GeneticOperators.java | ✅ | ✅ | — | — | — | — |
| **ConsensusEngine.java** | ❌ FAIL | ⚠️ | 2 | — | — | 1 |
| **HadesProtocol.java** | ❌ FAIL | ⚠️ | — | 2 | 1 | — |
| **CauldronProtocol.java** | ❌ FAIL | ❌ FAIL | 1 | — | 1 | — |

**Всего файлов с thread-safety проблемами:** 4 из 10
**Всего файлов с отклонением от спецификации:** 2 из 10

---

## Рекомендуемый порядок исправления

1. **Немедленно (P0):** GAP-001, GAP-002, GAP-003, GAP-004, GAP-005
2. **В ближайший спринт (P1):** GAP-006–010, GAP-021–023
3. **Планово (P2):** GAP-011–018, GAP-024–025
4. **Технический долг (P3):** GAP-019–020

---

*Конец CRITICAL_GAPS.md — v1.0, 2026-07-17*
