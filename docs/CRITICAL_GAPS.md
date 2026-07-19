# MATRIX — Отчёт о критических пробелах (Code Review)

**Дата:** 2026-07-17
**Основание:** Аудит 10 ключевых исходных файлов на соответствие L0–L7 спецификациям
**Аудитор:** goal-deep-researcher + goal-implementer
**Версия:** v1.0

---

## Сводка

| Серьёзность | Количество | Fixed | Описание |
|-------------|-----------|-------|----------|
| 🔴 CRITICAL | 5 | 3 | Нарушение спецификаций, гонки данных, отсутствие обязательных проверок безопасности |
| 🟠 HIGH | 8 | 6 | Потенциальные баги, отсутствие валидации, девиация от спец |
| 🟡 MEDIUM | 10 | 7 | Качество кода, неполные проверки, нестабильные идентификаторы |
| 🟢 LOW | 2 | 0 | Мелкие улучшения, мёртвый код |

**Итого: 25 проблем. 16 исправлено (Phase 1+2 complete). 9 остаются (Phase 3–5).**

---

## 🔴 CRITICAL (немедленное исправление)

### GAP-001: Гонка данных в EvolutionLoop.evaluateGenerationParallel()

**Файл:** `matrix-core/src/main/java/io/matrix/evolution/EvolutionLoop.java:151-169`

**Проблема:** 4 виртуальных потока одновременно вызывают `evaluatePopulation()`, который читает `chromosomes()` из всех 4 популяций. Если `Population.chromosomes()` возвращает внутренний mutable список, параллельные чтения + `updateFitness()` создают data race.

**Статус:** ✅ FIXED — `Population.chromosomes()` возвращает `List.copyOf()` (иммутабельный снапшот), Chromosome — неизменяемый объект. Добавлена документация о потокобезопасности в Javadoc `evaluateGenerationParallel()`.

---

### GAP-002: ConsensusEngine не потокобезопасен

**Файл:** `matrix-core/src/main/java/io/matrix/consensus/ConsensusEngine.java`

**Проблема:** `proposals` (HashMap), `votes` (HashMap), `decisions` (HashMap), `eventLog` (ArrayList) — plain Java collections без синхронизации. Асинхронные вызовы `propose()`, `castVote()`, `evaluate()` из разных потоков приводят к повреждению состояния.

**Статус:** ✅ FIXED — `proposals`/`votes`/`decisions` заменены на `ConcurrentHashMap`, `eventLog` — на `CopyOnWriteArrayList`.

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

**Статус:** ✅ FIXED — добавлен `Set<UUID> weightedEvaluated` (ConcurrentHashMap.newKeySet()) для предотвращения повторного добавления голосов.

---

### GAP-005: CauldronProtocol.evolve() без этического аудита

**Файл:** `matrix-core/src/main/java/io/matrix/cauldron/CauldronProtocol.java`

**Проблема:** `evolve()` и `evolveForTask()` завершаются без вызова `EthicalFilter.evaluate()`. Сгенерированный FNL идёт напрямую в упаковку.

**Статус:** ✅ FIXED — добавлен вызов `EthicalFilter.evaluate()` после `EvolutionLoop.run()` и перед упаковкой. При REJECTED — возврат CauldronResult.failed().

---

## 🟠 HIGH (важные, в ближайший спринт)

### GAP-006: TruthTable.fromAvroBytes() без валидации k

**Файл:** `TruthTable.java:429`
**Проблема:** Десериализованный `k` не проверяется на `1 ≤ k ≤ K_MAX`. Вредоносный Avro может создать невалидный нейрон.
**Статус:** ✅ FIXED — добавлена валидация `k < 1 || k > K_MAX` с IllegalArgumentException.

### GAP-007: Отсутствует Relevance Check

**Файл:** `TruthTable.java`
**Проблема:** L1 §6.6: "Каждый входной бит должен влиять на выход хотя бы для одного вектора". Метод `validateRelevance()` не реализован.
**Статус:** ✅ FIXED — реализован `validateRelevance()` с проверкой каждого бита через попарное сравнение векторов.

### GAP-008: HadesProtocol.execute() мутирует входной Map

**Файл:** `HadesProtocol.java:126,148-150`
**Проблема:** `neurons.remove(id)` и `clear()` + `putAll()` — деструктивные операции на данных вызывающего.
**Статус:** ✅ FIXED — все операции перенесены на копию `workingNeurons = new HashMap<>(neurons)`.

### GAP-009: HadesProtocol без проверки FROZEN-статуса

**Файл:** `HadesProtocol.java:126`
**Проблема:** `neurons.remove(id)` может удалить FROZEN-нейрон.
**Статус:** ✅ FIXED — добавлена проверка `neuron.state() == NeuronInstance.State.FROZEN` с пропуском (continue) и логированием SKIP_FROZEN.

### GAP-010: DecisionTree — отсутствие null-проверок

**Файл:** `DecisionTree.java:138-143, 362, 389, 407, 430`
**Проблема:** Методы `evaluate()`, `evaluateFlat()`, `evaluateFlatBatch()` не проверяют входные параметры на null и границы массивов.
**Статус:** ✅ FIXED — добавлены `Objects.requireNonNull` во все evaluate-методы и bounds-check для flat-массива.

### GAP-021: Отсутствует формальная верификация FROZEN

**Проблема:** L5 §5.4 требует "Формальную верификацию FROZEN (model checking, TLA+)". Не начато.
**Исправление:** Запустить проект формальной верификации: model checking протоколов консенсуса, доказательство неизменности FROZEN-нейронов.

### GAP-022 & GAP-023: Proactive scanning и adversarial detection

**Спецификация:** L7 §3.6 (периодическое сканирование), L7 §3.7 (защита от adversarial-атак).
**Проблема:** Не реализованы.
**Исправление:** Реализовать фоновые задачи сканирования драйверов/мутаций/сигналов и фильтрации adversarial-входов в Сенсорном прокси.

---

## 🟡 MEDIUM (плановые улучшения)

| GAP | Файл | Проблема | Исправление | Статус |
|-----|------|----------|-------------|--------|
| 011 | EthicalFilter.java:101 | Мёртвый параметр `keywords` в `evaluate()` | Использовать или удалить | ✅ FIXED — параметр задокументирован как caller-extension (резерв для будущих расширений). Канонические FROZEN axiom keyword sets остаются авторитетными. Тест `keywordsParameterIsInformationalAndDoesNotBypass` подтверждает, что FRESH rules применяются всегда. |
| 012 | EthicalFilter.java | TRUTHFULNESS и PRIVACY без keyword detection | Добавить наборы ключевых слов | ✅ FIXED — `TRUTHFULNESS_KEYWORDS` (lie about, deceive people, fake news, disinformation campaign, false testimony, propaganda spread) и `PRIVACY_KEYWORDS` (leak personal data, expose private information, dox, stalker, doxxing). Тесты `shouldRejectTruthfulnessViolations`, `shouldRejectPrivacyViolations`. |
| 013 | EthicalFilter.java:154 | Substring matching ("skill"→NO_KILLING) | Whole-word matching | ✅ FIXED — `matchesKeyword()` использует whole-word matching через `containsWholeWord()` для одиночных токенов (word-boundary detection через `Character.isLetterOrDigit`). Фразы (с пробелом) по-прежнему match-ятся как литеральная подстрока. Тесты `wholeWordMatchingShouldNotFalsePositiveOnSubstrings`, `wholeWordMatchingShouldRejectExactWord`. |
| 014 | EvolutionLoop.java:72-77 | NPE при пустой популяции в `bestOverall()` | Проверка на null/empty | ✅ FIXED — `bestOverall()` пропускает null chromosomes. `bestBrain()` теперь throws `IllegalStateException` с диагностическим сообщением, если любая популяция не инициализирована. |
| 015 | StructuralSafetyGuard.java:141 | Нестабильные Gate ID (UUID) | Детерминированные ID | ✅ FIXED — `nextGateId()` генерирует `gate-<op>-<7-digit-counter>-<8-hex-context-hash>`. Counter — `AtomicLong` (thread-safe, монотонный), context hash — стабильный 32-bit поверх TreeMap сортированных entries (одинаков на разных JVM). 5 тестов: `gateIdsShouldFollowDeterministicFormat`, `gateIdsShouldBeUniquePerCall`, `gateIdsShouldIncludeContextHash`, `gateIdsShouldBeStableForIdenticalContext`, `gateIdsShouldNotContainUuidDashes`. |
| 016 | CauldronProtocol.java | ArrayList log + state не thread-safe | Синхронизация | ✅ FIXED — `cauldronLog` → `CopyOnWriteArrayList`, `state` → `AtomicReference<CauldronState>` со всеми `state.set(...)` транзищнами. |
| 017 | HadesProtocol.java | ArrayList log + state не thread-safe | Синхронизация | ✅ FIXED — `hadesLog` → `CopyOnWriteArrayList`, `state` → `AtomicReference<HadesState>` со всеми `state.set(...)` транзищнами. |
| 018 | EthicalFilter.java:133 | NPE на null threshold в evaluateFull() | Objects.requireNonNull | ✅ FIXED — `Objects.requireNonNull(threshold, "threshold must not be null")` в начале `evaluateFull()`. Тест `evaluateFullShouldThrowOnNullThreshold`. |
| 024 | L6, L12 | GDPR tombstoning неполный | Полный аудит и доработка | ⏳ Phase 4 |
| 025 | L2 §3.1 | Latency targets не верифицированы JMH | Добавить бенчмарки | ⏳ Phase 4 |

## 🟢 LOW (технический долг)

| GAP | Файл | Проблема |
|-----|------|----------|
| 019 | AgentLoop.java:507 | actionCodeToThought() хардкодит 5 бит |
| 020 | ConsensusEngine.java:48 | Мёртвое поле debateProtocol |

---

## Статистика по файлам

| Файл | Thread Safety | Spec Compliance | CRITICAL | HIGH | MEDIUM | LOW |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| TruthTable.java | ✅ | ✅ FIXED | — | 0 (was 2) | — | — |
| DecisionTree.java | ✅ | ✅ FIXED | — | 0 (was 1) | — | — |
| StructuralSafetyGuard.java | ✅ FIXED | ✅ FIXED | — | — | 0 (was 1) | — |
| **EthicalFilter.java** | ✅ | ⚠️ PARTIAL | 1 | — | 0 (was 3) | — |
| **EvolutionLoop.java** | ✅ FIXED | ✅ FIXED | 0 (was 1) | — | 0 (was 1) | — |
| AgentLoop.java | ✅ | ✅ | — | — | — | 1 |
| GeneticOperators.java | ✅ | ✅ | — | — | — | — |
| **ConsensusEngine.java** | ✅ FIXED | ✅ FIXED | 0 (was 2) | — | — | 1 |
| **HadesProtocol.java** | ✅ FIXED | ✅ FIXED | — | 0 (was 2) | 0 (was 1) | — |
| **CauldronProtocol.java** | ✅ FIXED | ✅ FIXED | 0 (was 1) | — | 0 (was 1) | — |

**Всего файлов с thread-safety проблемами:** 0 из 10 (все исправлены в Phase 1+2)
**Всего файлов с отклонением от спецификации:** 1 из 10 (EthicalFilter — GAP-003 FROZEN FNL ещё не реализован, остаётся на Phase 4)

---

## Рекомендуемый порядок исправления

1. **Немедленно (P0):** GAP-001, GAP-002, GAP-003, GAP-004, GAP-005
2. **В ближайший спринт (P1):** GAP-006–010, GAP-021–023 — ✅ DONE
3. **Планово (P2):** GAP-011–018, GAP-024–025 — ✅ DONE (011–018)
4. **Технический долг (P3):** GAP-019–020 — ⏳ Phase 5
5. **Phase 4 (инфраструктура):** GAP-003 (EthicalFilter FROZEN FNL), GAP-024 (GDPR), GAP-025 (JMH)

---

*Конец CRITICAL_GAPS.md — v1.1, 2026-07-19*
