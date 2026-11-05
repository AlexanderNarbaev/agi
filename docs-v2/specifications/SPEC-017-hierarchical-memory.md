# SPEC-017 — Hierarchical Memory (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.memory`
**Related**: [SPEC-003](./SPEC-003-knowledge-topology.md), [SPEC-007](./SPEC-007-subconscious.md), [SPEC-011](./SPEC-011-memory-hierarchy.md), [DESIGN-05](../designs/DESIGN-05-memory.md), [DESIGN-19](../designs/DESIGN-19-subconscious-consolidator.md), [L6-Memory](../levels/L6-Memory.md), [AR](../architecture/REQUEST-memory-hierarchy.md)

## Что

Спецификация **иерархической памяти** — той части системы, которая хранит то, что было сказано
и то, что было извлечено, и различает их.

**В дереве сейчас три несовместимых таксономии памяти.** Это надо сказать прямо, потому что
любая из них претендует на имя «иерархия памяти»:

| # | таксономия | где объявлена | кто её реализует | состояние |
|---|---|---|---|---|
| **A** | `L0_ARTIFACT → L1_PATTERN → L2_MODULE → L3_QUANTUM → L4_KERNEL` — артефакт, паттерн, модуль, квант, ядро | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55` | `io.matrix.memory.HierarchicalMemory` | **реализована и обслуживается** — это таксономия работающей системы |
| **B** | `L1 → L5`: operating, short-term, long-term, structural, collective — по времени жизни | `docs-v2/levels/L6-Memory.md:17-27` | `io.matrix.memory.MemoryHierarchy` (`matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:29-40`) | реализована, **но недостижима**: production-вызывающих нет |
| **C** | `M0` working, `M1` episodic, `M2` long-term | `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19-25` | `io.matrix.memory.PersistentMemory`, `io.matrix.memory.ConsolidationCycle` | реализована, **но недостижима**: production-вызывающих нет |

Плюс четвёртая, **каноническая по статусу, но не по коду**: [SPEC-011](./SPEC-011-memory-hierarchy.md)
объявляет уровни `L1_HOT / L2_MODULE / L3_ARCHIVE` (`docs-v2/specifications/SPEC-011-memory-hierarchy.md:18`,
`:49-51`) — **таких уровней в коде нет**.

**Эта спецификация нормативна для таксономии A** — той, которую реально обслуживает маршрут
чата. Таксономии B и C описаны здесь как **реализованные, но не подключённые**, с указанием,
что с ними должно произойти (§ «Отложено»). SPEC-011 — **предшественник, который предлагается
заменить этой спецификацией**; его статус меняет координатор, здесь он не редактируется.

Ключевые утверждения:

- **IMPLEMENTED** — обслуживаемая память имеет **пять уровней** с фиксированной базовой
  значимостью. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`.
- **IMPLEMENTED** — запись **эпизодическая помечается тегом** `generated-dialogue`, и поиск
  знания этот тег исключает: то, что было сказано, не становится фактом без процедуры
  доказательства. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:43`;
  `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:329-330`.
- **SERVED** — память обслуживается **обоими подающими приложениями**: chain A в маршруте чата и
  chain B в гейтвейере. Evidence: `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:12`,
  `:87`; `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:260`.
- **IMPLEMENTED** — promotion между уровнями **явный**: вызывающий решает, куда переносить;
  автоматика одна — порог значимости в цикле сна. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:254`;
  `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:65-69`.
- **PROPOSED** — обслуживаемая таксономия A обязана быть **единственной нормативной**; B и C
  обязаны быть либо подключены, либо помечены как недействующие. Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:44`;
  `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19`.

### Уровни обслуживаемой таксономии

| уровень | index | базовая значимость | смысл | evidence |
|---|---|---|---|---|
| `L0_ARTIFACT` | 0 | 0.1 | сырьё: логи, источники, черновики | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:47` |
| `L1_PATTERN` | 1 | 0.3 | подтверждённые связи между понятиями | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:49` |
| `L2_MODULE` | 2 | 0.5 | функциональные единицы знания | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:51` |
| `L3_QUANTUM` | 3 | 0.8 | синтезированное знание высокой уверенности | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:53` |
| `L4_KERNEL` | 4 | 1.0 | семантические домены: стабильны, меняются редко | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:55` |

Значимость — **константы, а не измерения**.

## Мотивация

Память нужна системе не как «база», а как **разделение того, что сказано, и того, что выучено**.
Одна запись в чате и один проверенный факт лежат в одном хранилище и различаются только тегом;
без этого разделения разговор незаметно становится знанием.

### Три исследовательские перспективы (META-R1)

Три перспективы из разных школ, каждая привязана к первоисточнику, который **уже есть в
репозитории**:

| школа | первоисточник в репозитории | что он даёт этой спецификации |
|---|---|---|
| **нейронаука памяти — Э. Тульвинг (1972)** | «Episodic vs semantic memory \| Tulving 1972» — `docs-v2/research/summaries/GLOSSARY.md:65` | **эпизодическое против семантического**: эпизод — журнал того, что было сказано; семантическое — верифицированный пул. Это ровно то разделение, которое несут `EPISODE_TAG` и `search(..., excludedTags)` |
| **разреженная распределённая память — П. Канерва (1988)** | SDM: `docs-v2/research/protocols/H-011-sdm-reader.md:3` (счётчики + радиус Канервы), `:29` (гейт стабильности `std ≤ 0.02` на 5 сидах), `:36` (артефакт `memory/SdmReader`); гипотезы **H-052** и **H-059** — `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:27`, `:34` | адресующая память как **счётчик с радиусом**, а не как список: отсюда требование, чтобы уровни имели **счётчик доступа**, а не только значимость, и чтобы поиск был ранжирован по значимости |
| **собственный preregistered-эксперимент репозитория — H-007** | `docs-v2/research/protocols/H-007-memory-stack.md:1-16` — RECALL@5 ≥ 0.85 на стеке знаний (SDM M1 + Ricci-fingerprint + drift-fingerprint), без LLM-контура | **измеримая цель**: иерархия памяти обязана иметь воспроизводимый гейт на отзыве, а не только декларацию слоёв |

Источник самой модели из пяти уровней — `Research Synthesis 2026-Q3 §3.1`, процитированный в
`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:9-13`.

**UNVERIFIED** — в репозитории **нет** первоисточников по Marr (уровни анализа), по
complementary learning systems (O'Keefe/Beck/Frankel) и по McGaugh (сон-зависимая консолидация).
Следствие: обоснование цикла консолидации в AR опирается на утверждение «гиппокамп→неокортекс»
(`docs-v2/architecture/REQUEST-autonomy-impulses.md:68`), **первоисточника на которое в
репозитории нет**. Сама спецификация ниже этого утверждения не опирается.

## Архитектура

```
                    chain A: POST /v1/chat/completions
                              │  search(query, 3, excludedTags={EPISODE_TAG})
                              │  store(L2_MODULE, answer, …, {EPISODE_TAG})
                              ▼
                    io.matrix.memory.HierarchicalMemory
                     ├── L0_ARTIFACT … L4_KERNEL
                     ├── EPISODE_TAG — «это сказали», а не «это выучили»
                     ├── detectDrift() — расхождение между доменами
                     └── promote(id, level) — перенос по явному решению вызывающего
                              ▲
                              │  entriesAtLevel(L1_PATTERN) → importance ≥ 0.5 → promote(L2_MODULE)
                    io.matrix.sleep.SleepCycle
```

Ключевые утверждения:

- **IMPLEMENTED** — пять уровней, у каждого `index`, `displayName` и `baseImportance`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-69`.
- **IMPLEMENTED** — `EPISODE_TAG = "generated-dialogue"` — тег, который отделяет сказанное от
  выученного; обоснование — RECON-W41 §5.1, процитированное в коде.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:36-43`.
- **IMPLEMENTED** — поиск с исключением тегов: `search(query, limit, excludedTags)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:222`.
- **IMPLEMENTED** — поиск ранжирован **по значимости**; случайности в ранжировании нет.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:230`, `:246` —
  `Comparator.comparingDouble(MemoryEntry::importance).reversed()`.
- **IMPLEMENTED** — promotion по вызову: `promote(id, targetLevel)` возвращает
  `Optional<MemoryEntry>`. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:254`.
- **IMPLEMENTED** — автоматический promotion есть ровно в одном месте: цикл сна переносит
  `L1_PATTERN → L2_MODULE` при значимости `≥ 0.5`. Evidence: `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:65-69`.
- **IMPLEMENTED** — drift-детектор: `detectDrift()` возвращает список `DriftSignal`, у которого
  есть `isSignificant()`. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:270`, `:106-113`.
- **SERVED** — chain A читает память в маршруте чата, **исключая эпизодический тег**, и записывает
  ответ в `L2_MODULE` **с** этим тегом.
  Evidence: `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:325-330`, `:425-432`.
- **SERVED** — chain B обслуживает собственным экземпляром памяти.
  Evidence: `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:260`.
- **SERVED** — цикл сна гейтвейера использует память и цикл консолидации.
  Evidence: `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:260-261`;
  `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:42-43`.
- **IMPLEMENTED** — `io.matrix.lifecycle.ConsolidationCycle` — детерминированное «окно сна»:
  сливает очереди маршрутов ограниченными порциями и возвращает
  `DrainSummary{routesDrained, itemsMigrated}`. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12`,
  `:15`, `:22`, `:59-65`.
- **PROPOSED** — «консолидация» в смысле AR (реплей M1 → сжатие в M2/M3 → забывание сырья) —
  **не реализована**: `lifecycle/ConsolidationCycle` сливает очереди маршрутов и не имеет дела
  ни с реплеем, ни со сжатием, ни с забыванием.
  Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:65` против
  `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12-15`.
- **IMPLEMENTED** — `io.matrix.memory.ConsolidationCycle` — двухстадийная консолидация
  TR (M2→M1) + REM (M1→M0) по DESIGN-19, детерминированная, счётчик частоты без временного
  затухания. Evidence: `matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21`,
  `:26`, `:47`, `:60`.
- **IMPLEMENTED** — `PersistentHierarchicalMemory` — CDI-обёртка, которая восстанавливает и
  периодически сбрасывает иерархию в JSONL, с дебаунсом и периодом
  `matrix.memory.flush-seconds` (по умолчанию 30). Evidence: `matrix-core/src/main/java/io/matrix/memory/PersistentHierarchicalMemory.java:36`,
  `:48`, `:80-89`.
- **IMPLEMENTED** — таксономия B (`MemoryHierarchy`) реализована полностью: L1 — кольцевой буфер
  сигналов, L2 — журнал событий, L3 — снапшоты, L4 — только FROZEN-нейроны, L5 — Noosphere.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:29-40`, `:83`, `:190`.
- **IMPLEMENTED** — таксономия C (`MemoryHierarchyTier` M0/M1/M2) объявлена и используется только
  двумя классами, у которых нет вызывающих. Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19-25`;
  `matrix-core/src/main/java/io/matrix/memory/PersistentMemory.java:42`, `:63`.
- **IMPLEMENTED** — у ядра когнитивного состояния **своя** память: `cognitive_episode`
  (эпизодическая), `cognitive_procedure` (процедурная, версионируемая), `cognitive_event`
  (неизменяемый журнал), `cognitive_task`, `cognitive_prediction`, `cognitive_error`.
  Evidence: `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:21-24`, `:45-62`.
  Остальные три класса пятиклассовой таксономии — **PLANNED**.
  Evidence: `docs-v2/architecture/MATRIX-COGNITIVE-KERNEL.md:182`.

## FR (классы и интерфейсы)

- **IMPLEMENTED** — `io.matrix.memory.HierarchicalMemory` — CDI-безопасный, конструктор без
  параметров (`:142`) и с лимитом (`:135`).
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:28-29`, `:135`, `:142`.
- **IMPLEMENTED** — `enum Level { L0_ARTIFACT, L1_PATTERN, L2_MODULE, L3_QUANTUM, L4_KERNEL }` с
  `index()`, `displayName()`, `baseImportance()`. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-69`.
- **IMPLEMENTED** — `record MemoryEntry` с `withAccessed()` и `withLevel(Level)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:75`, `:88`, `:95`.
- **IMPLEMENTED** — `record DriftSignal` с `isSignificant()`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:106-113`.
- **IMPLEMENTED** — `MemoryEntry store(Level level, String content, String domain, Set<String> tags)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:149`.
- **IMPLEMENTED** — `Optional<MemoryEntry> get(String id)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:193`.
- **IMPLEMENTED** — `List<MemoryEntry> search(String query, int limit)` и
  `List<MemoryEntry> search(String query, int limit, Set<String> excludedTags)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:206`, `:222`.
- **IMPLEMENTED** — `List<MemoryEntry> searchAtLevel(Level level, String query, int limit)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:238`.
- **IMPLEMENTED** — `Optional<MemoryEntry> promote(String id, Level targetLevel)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:254`.
- **IMPLEMENTED** — `List<DriftSignal> detectDrift()`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:270`.
- **IMPLEMENTED** — `List<MemoryEntry> entriesAtLevel(Level level)`,
  `List<MemoryEntry> entriesInDomain(String domain)`, `int size()`,
  `Map<Level,Integer> levelStats()`, `long evictedCount()`, `int listenerFailures()`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:298`, `:309`,
  `:320`, `:327`, `:397`, `:426`.
- **IMPLEMENTED** — `int evictExpired(long maxAgeMs)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:338`.
- **IMPLEMENTED** — `void addStoreListener(Consumer<MemoryEntry>)`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:126`.
- **IMPLEMENTED** — `io.matrix.memory.MemoryHierarchy` — таксономия B: `storeL1`, `recallL1`,
  `checkpointL1ToL2`, `replayL2`, `consolidateL2ToL3`, `loadL3`, `registerL4`, `getL4`,
  `publishL5`, `searchL5`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:83`, `:90`, `:108`,
  `:127`, `:143`, `:168`, `:184`, `:196`, `:212`, `:240`.
- **IMPLEMENTED** — `io.matrix.memory.MemoryHierarchyTier { M0, M1, M2 }` — таксономия C.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19-25`.
- **IMPLEMENTED** — `io.matrix.memory.ConsolidationCycle` с `enum Stage { TR, REM }`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21`, `:23`, `:26`.
- **IMPLEMENTED** — `io.matrix.lifecycle.ConsolidationCycle` с `record DrainSummary(int routesDrained, long itemsMigrated)`.
  Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12`, `:15`.
- **IMPLEMENTED** — `io.matrix.memory.PersistentHierarchicalMemory` — `flushIfDirtyNow()` и
  `flushNow()`. Evidence: `matrix-core/src/main/java/io/matrix/memory/PersistentHierarchicalMemory.java:118`, `:123`.

## Инварианты

1. **IMPLEMENTED** — **I1. Эпизод не становится фактом без процедуры доказательства.** Запись
   помечается `EPISODE_TAG`, а поиск знания этот тег исключает.
   Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:43`;
   `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:329-330`.
2. **IMPLEMENTED** — **I2. Ранжирование поиска детерминировано** — по значимости, без Random и
   без wall-clock. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:230`, `:246`.
3. **IMPLEMENTED** — **I3. Promotion явный** — перенос выполняется по решению вызывающего, а не
   по скрытому правилу внутри хранилища. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:254`;
   `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:69`.
4. **IMPLEMENTED** — **I4. В таксономии B в L4 попадают только FROZEN-нейроны** — иначе
   исключение. Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:190`.
5. **PROPOSED** — **I5. Обслуживаемая таксономия одна.** **Код этому инварианту не удовлетворяет:**
   в дереве три реализованные таксономии (A, B, C), и документ `SPEC-011` объявляет четвёртую
   (`docs-v2/specifications/SPEC-011-memory-hierarchy.md:18`).
6. **PROPOSED** — **I6. Обёртка персистентности и обслуживаемое хранилище — один и тот же объект.**
   **Код этому инварианту не удовлетворяет:** `PersistentHierarchicalMemory` — CDI-безопасный
   бин, который никто не инъецирует, а `HierarchicalMemory`, который инъецируется в маршрут
   чата, — **другой** экземпляр.
   Evidence: `matrix-core/src/main/java/io/matrix/memory/PersistentHierarchicalMemory.java:35-36`;
   `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:87`.
7. **PROPOSED** — **I7. Вытеснение по возрасту не зависит от wall-clock в пути решения.**
   **Код этому инварианту не удовлетворяет:** `evictExpired` сравнивает `System.currentTimeMillis()`
   с порогом, а записи получают `createdAt` из wall-clock.
   Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:339`, `:91`, `:154`.
   Это не нарушение CONSTITUTION I буквально (wall-clock не в *решении о действии*, а в
   обслуживании хранилища), но решение о вытеснении — решение, и оно недетерминировано.
8. **PROPOSED** — **I8. Консолидация памяти не смешана с консолидацией очередей.** Сегодня это
   разные классы с разными именами, и оба называются `ConsolidationCycle`; ни один из них не
   выполняет заявленное AR «реплей → сжатие → забывание».
   Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12-15`;
   `matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21`;
   `docs-v2/architecture/REQUEST-autonomy-impulses.md:65`.

## Метрики / гейты

- **IMPLEMENTED** — наблюдаемость хранилища: `size()`, `levelStats()`, `evictedCount()`,
  `listenerFailures()`. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:320`,
  `:327`, `:397`, `:426`.
- **IMPLEMENTED** — сигнал расхождения доменов: `detectDrift()` и `DriftSignal.isSignificant()`.
  Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:270`, `:113`.
- **PROPOSED** — гейт отзыва **RECALL@5 ≥ 0.85** по preregistered-протоколу **H-007** (стек знаний:
  SDM M1 + Ricci-fingerprint curriculum-ordering + drift-fingerprint, без LLM-контура).
  Evidence: `docs-v2/research/protocols/H-007-memory-stack.md:1-16`. **Золотой набор владельца и
  сам бенчмарк не зафиксированы** — это указано в самом протоколе
  (`docs-v2/research/protocols/H-007-memory-stack.md:5-7`).
- **PROPOSED** — гейт стабильности SDM по радиусу Канервы: `std ≤ 0.02` на 5 сидах, отклонение при
  `std > 0.05`. Evidence: `docs-v2/research/protocols/H-011-sdm-reader.md:29`.
- **PROPOSED** — базовые значимости уровней и порог promotion `0.5` должны быть выведены из
  замера. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:47-55`;
  `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:68` — сегодня это константы без
  provenance-метки.
- **PROPOSED** — гейт «эпизод не растворяется в факте»: доля записей, помеченных
  `EPISODE_TAG`, и не попавших в выдачу знания, должна быть измерима. Evidence: теста на это
  нет; ближайший — `matrix-core/src/test/java/io/matrix/memory/EpisodeSeparationTest.java`.

## Реализация в коде

| класс | пакет | статус | доказательство |
|---|---|---|---|
| `HierarchicalMemory` | `io.matrix.memory` | **IMPLEMENTED**, **SERVED** (chain A и chain B) | `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:29`; `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java:87`; `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:260` |
| `SleepCycle` | `io.matrix.sleep` | **IMPLEMENTED**, **REACHABLE** | `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:29`, `:65-69` |
| `RealSleepScheduler` | `io.matrix.brain.runtime` | **IMPLEMENTED**, **SERVED** (chain B) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:42-43`, `:58` |
| `PersistentHierarchicalMemory` | `io.matrix.memory` | **IMPLEMENTED**, **UNREACHABLE** — CDI-безопасный бин, который **никто не инъецирует** | `matrix-core/src/main/java/io/matrix/memory/PersistentHierarchicalMemory.java:35-36`, `:80-89` |
| `MemoryHierarchy` (таксономия B) | `io.matrix.memory` | **IMPLEMENTED**, **UNREACHABLE** — production-вызывающих нет | `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:44` |
| `MemoryHierarchyTier` (таксономия C) | `io.matrix.memory` | **IMPLEMENTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19`; `matrix-core/src/main/java/io/matrix/memory/PersistentMemory.java:42` |
| `ConsolidationCycle` (память) | `io.matrix.memory` | **IMPLEMENTED**, **TESTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21` |
| `ConsolidationCycle` (жизненный цикл) | `io.matrix.lifecycle` | **IMPLEMENTED**, **TESTED**, **SERVED** (chain B) | `matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12`; `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:261` |
| `SqliteMemoryBackend` | `io.matrix.memory` | **IMPLEMENTED**, **TESTED** | `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:93` |
| `SdmReader`, `RecurrentSdm` | `io.matrix.memory` | **IMPLEMENTED** | `matrix-core/src/main/java/io/matrix/memory/SdmReader.java`; `docs-v2/research/protocols/H-011-sdm-reader.md:36` |
| `CognitiveStore` | `io.matrix.cognitive` | **IMPLEMENTED**, **SERVED** (chain A) | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:19-24`; `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:258` |
| `KnowledgeStore` (семантическая память) | `io.matrix.knowledge` | **IMPLEMENTED**, **SERVED** (chain A) | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java`; `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:50` |
| `HierarchicalBrain`, `TwoStageConsolidator`, `CodebookMemory` | `io.matrix.neuron` | **IMPLEMENTED**, **SERVED** (chain B, через `BirBrainCycle`) | `matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java:61`, `:101`, `:182` |

## Связь с существующим

- **[SPEC-011](./SPEC-011-memory-hierarchy.md) — предшественник, который предлагается заменить
  этой спецификацией.** Он **нормативен** (`docs-v2/specifications/SPEC-011-memory-hierarchy.md:3`)
  и **противоречит коду** в следующем:
  - объявляет уровни `L1_HOT / L2_MODULE / L3_ARCHIVE` (`:18`, `:49-51`) — **таких уровней нет**;
    реальный перечень — `L0_ARTIFACT…L4_KERNEL` (`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`);
  - описывает `MemoryHierarchy` как «утилитарный класс для работы с уровнями»
    (`:38-39`) — на деле это отдельное пятиуровневое хранилище сигналов и знаний
    (`matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:29-40`);
  - перечисляет методы `recall(MemoryEntry)`, `expire(olderThan)`, `drift()` (`:34-37`) — **ни
    одного из них в коде нет**; реальные — `search` (`:206`), `evictExpired(long)` (`:338`),
    `detectDrift()` (`:270`); параметр третьего у `store` — `domain`, а не `source` (`:33`);
  - объявляет promotion «L3→L2 при accessCount > 5, L2→L1 при accessCount > 20» и demotion по
    TTL (`:53-54`) — **в коде нет ни этих порогов, ни demotion**; единственная автоматика —
    `importance ≥ 0.5` в `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:68`;
  - утверждает, что `lifecycle/ConsolidationCycle` выполняет promotion/demotion в составе
    drain-summary (`:122-123`) — **неверно**: этот класс сливает очереди маршрутов
    (`matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12-15`);
  - называет тест `DriftSignalTest` (6) и «Total 62 теста» (`:127-129`) — **`DriftSignalTest` в
    дереве нет** (проверено поиском по всему репозиторию), и число 62 относится к состоянию,
    которое не воспроизводится;
  - утверждает, что `search` ранжирован «by importance+recency» (`:35`) — **в коде только
    значимость** (`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:230`).
  **Предлагаемый преемник — SPEC-017 (этот документ).** Статус SPEC-011 меняет координатор;
  **здесь файл не редактируется**.
- **[DESIGN-05](../designs/DESIGN-05-memory.md)** — нормативен (`:3`) и описывает **другую**
  таксономию — capability-таксономию M0…M4: рабочая, эпизодическая, семантическая,
  процедурная, коллективная (`:7`). Она **не совпадает** ни с A, ни с B, ни с C. Список
  реализации в нём (`:11-13`) называет `memory/HierarchicalMemory`, `MemoryHierarchy`, `SdmReader` —
  то есть три класса с тремя разными таксономиями как одну реализацию. **Эта спецификация
  разделяет их**: A обслуживается, B и C — нет.
- **[L6-Memory](../levels/L6-Memory.md)** — нормативен (`:3`) и задаёт таксономию B по времени
  жизни (`:14-22`). Она остаётся нормой для **уровня L6 как такового**; вопрос о том, какой
  класс её реализует, решается здесь: `MemoryHierarchy` реализован, но **не подключён**.
- **[AR](../architecture/REQUEST-memory-hierarchy.md)** — директива, которую эта спецификация
  делает нормативной. Её таблица слоёв (`:10-24`) добавляет к DESIGN-05 три слоя (`m0`, `M5`,
  `M5+`) — **этих слоёв в коде нет**. Её строка 113 называет формальную спеку «placeholder» —
  **эта спецификация и есть та спека**.
- **[SPEC-003](./SPEC-003-knowledge-topology.md)** — Ricci-анализ, drift-fingerprint,
  curriculum-ordering: источник fingerprint'ов, по которым работает детектор расхождения.
- **[SPEC-007](./SPEC-007-subconscious.md)** — описывает фоновые TR/REM-фазы и promotion-гейты
  H-040 (`:45-47`, `:53`); в дереве нет ни класса `subconscious/`, ни `DreamReplay`, ни
  `ImpulseGenerator` (проверено поиском). Сама **H-040 объявлена** —
  `docs-v2/research/HYPOTHESES-NEW.md:13`, статус `proposed`, гейт «promotion precision ≥0.9
  (synthetic-scope)» — но её указатель на реализацию ведёт на TR/REM-фазы SPEC-007, которых в
  дереве нет; ближайшая существующая реализация TR/REM — `io.matrix.memory.ConsolidationCycle`
  (`matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21`), и она **не
  подключена**.
- **Трассировка** — `docs-v2/paradigm/TRACEABILITY.md:52` уже оценивает предмет как
  **«🟡 partial»**: `L6 — Memory hierarchy | DESIGN-05 | memory/HierarchicalMemory`. Эта спецификация
  не меняет оценку — она её уточняет.
- **Реестр возможностей** — `docs-v2/architecture/CAPABILITY-REGISTRY.yml` упоминает
  `io.matrix.memory.HierarchicalMemory` как зависимость цикла chain B (`:1192`), но отдельной
  строки про память не содержит. Строку должен завести координатор; здесь она не правится.

## Связь с тестами

**Test scope (точно) — без Gradle-прогона, если не разрешён отдельно:**

```bash
./gradlew :matrix-core:test --tests "io.matrix.memory.*"
./gradlew :matrix-core:test --tests "io.matrix.sleep.*"
```

Классы тестов, покрывающие предмет:

| класс | что покрывает |
|---|---|
| `matrix-core/src/test/java/io/matrix/memory/HierarchicalMemoryTest.java` | обслуживаемая таксономия A: store, search, promote, уровни |
| `matrix-core/src/test/java/io/matrix/memory/HierarchicalMemoryEvictionTest.java` | вытеснение и `evictedCount()` |
| `matrix-core/src/test/java/io/matrix/memory/EpisodeSeparationTest.java` | разделение «сказано» и «выучено» (I1) |
| `matrix-core/src/test/java/io/matrix/memory/MemoryHierarchyTest.java` | таксономия B |
| `matrix-core/src/test/java/io/matrix/memory/MemoryHierarchyTierTest.java` | таксономия C |
| `matrix-core/src/test/java/io/matrix/memory/ConsolidationCycleTest.java` | TR/REM-консолидация (таксономия C) |
| `matrix-core/src/test/java/io/matrix/lifecycle/ConsolidationCycleTest.java` | окно сна (слив очередей) |
| `matrix-core/src/test/java/io/matrix/memory/PersistentHierarchicalMemoryTest.java` | JSONL-персистентность |
| `matrix-core/src/test/java/io/matrix/memory/SqliteMemoryBackendTest.java` | SQLite-бэкенд |
| `matrix-core/src/test/java/io/matrix/memory/PersistentMemoryTest.java` | таксономия C, хранилище |
| `matrix-core/src/test/java/io/matrix/memory/TombstoneCompactionTest.java` | уплотнение tombstone'ов |

**Чего в тестах нет:** теста на `DriftSignalTest`, который SPEC-011:127 называет (класс в дереве
отсутствует); теста на I6 (обёртка и обслуживаемое хранилище — разные объекты); гейта
RECALL@5 из H-007 (золотой набор не зафиксирован, `docs-v2/research/protocols/H-007-memory-stack.md:5-7`).

## Отложено

- **PROPOSED** — **статус SPEC-011** должен стать `SUPERSEDED` с указанием SPEC-017 как
  преемника; правку делает координатор. Evidence: `docs-v2/specifications/SPEC-011-memory-hierarchy.md:3`.
- **PROPOSED** — таксономия **B** (`MemoryHierarchy`): подключить к обслуживаемому пути или
  пометить как недействующую. Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchy.java:44`.
- **PROPOSED** — таксономия **C** (`MemoryHierarchyTier`, `PersistentMemory`,
  `memory/ConsolidationCycle`): то же. Evidence: `matrix-core/src/main/java/io/matrix/memory/MemoryHierarchyTier.java:19`.
- **PROPOSED** — **I6**: инъецировать `PersistentHierarchicalMemory` в обслуживаемое хранилище
  либо убрать бин. Evidence: `matrix-core/src/main/java/io/matrix/memory/PersistentHierarchicalMemory.java:35-36`.
- **PROPOSED** — **I7**: вынести решение о вытеснении из wall-clock или явно зафиксировать
  исключение из CONSTITUTION I. Evidence: `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:339`.
- **PROPOSED** — **I8**: переименовать один из двух `ConsolidationCycle` так, чтобы имя
  соответствовало поведению, и реализовать заявленное AR «реплей → сжатие → забывание».
  Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:65`.
- **PROPOSED** — слои `m0`, `M5`, `M5+` из AR в коде отсутствуют.
  Evidence: `docs-v2/architecture/REQUEST-memory-hierarchy.md:10-24`.
- **PROPOSED** — TLA+-спецификация `Memory-M4-Causal` (formalization promotion/demotion с
  инвариантами capacity bounds и reachability).
  Evidence: `docs-v2/specifications/SPEC-011-memory-hierarchy.md:131-135`;
  `docs-v2/designs/DESIGN-05-memory.md:19-20`.
- **PROPOSED** — золотой набор и бенчмарк RECALL@5 для H-007.
  Evidence: `docs-v2/research/protocols/H-007-memory-stack.md:5-7`.
- **PROPOSED** — первоисточники по Marr, complementary learning systems и McGaugh: **UNVERIFIED**,
  в репозитории их нет; обоснование цикла консолидации опирается на неподкреплённое утверждение
  «гиппокамп→неокортекс». Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:68`.
- **PROPOSED** — reconciliation capability-таксономии M0…M4 из DESIGN-05 с таксономией A: это
  **две разные оси** (роль памяти против степени зрелости), и документ должен сказать это явно.
  Evidence: `docs-v2/designs/DESIGN-05-memory.md:7` против
  `matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`.
