# DESIGN-73 — Consolidation Cycle (консолидация памяти)

**Статус: normative** · пересмотр (v2 rebuild).

**Package**: `io.matrix.sleep` · `io.matrix.lifecycle` · `io.matrix.knowledge` ·
`io.matrix.memory` · `io.matrix.brain.runtime`
**Related**: [SPEC-016](../specifications/SPEC-016-decentralized-digests.md),
[SPEC-007](../specifications/SPEC-007-subconscious.md),
[SPEC-011](../specifications/SPEC-011-memory-hierarchy.md),
[DESIGN-05](./DESIGN-05-memory.md), [DESIGN-07](./DESIGN-07-lifecycle.md),
[DESIGN-08](./DESIGN-08-federation.md), [DESIGN-19](./DESIGN-19-subconscious-consolidator.md)

**Why this document exists.** The series was renumbered and the design of this subject was
lost; the directive `DESIGN-07-consolidation-cycle` was repaired by subject onto
`DESIGN-07-lifecycle` (`docs-v2/engineering/W46.2-STATUS.md:57-58`) — но сам `DESIGN-07`
(21 строк) не описывает ни одного из девяти классов, которые носят это имя. Владелец
решил (OD-031, `docs-v2/governance/DECISION-REGISTER.md:71`): предмет **пишется, а не
сопоставляется**. Реализация — TASK-W46-19 (`docs-v2/governance/ROADMAP.md:99`).

**Словарь статусов документа** (задан волной): `DESIGNED` · `IMPLEMENTED` · `INTEGRATED` ·
`REACHABLE` · `SERVED` · `TESTED` · `BENCHMARKED` · `OPERATIONAL` · `PROPOSED`.
**Класс без вызывающего никогда не называется `SERVED`.** Каждое утверждение несёт
ссылку `path:line`.

---

## Что

Под консолидацией в этом репозитории называются **девять разных классов**, и только
три из них имеют отношение к памяти в смысле документа. Смешивать их — главный
источник путаницы, поэтому здесь сначала names, потом механизмы.

| # | класс | пакет | что это на самом деле | статус |
|---|---|---|---|---|
| 1 | `ConsolidationCycle` (72 строки) | `io.matrix.lifecycle` | **Слив очередей маршрутов.** Не память: `drain(route, batchSize)` двигает «items» из бэклога маршрута. Javadoc: *«the deterministic «sleep» window of DESIGN-07 §4: background route backlogs are drained in bounded batches»* (`matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:7-11`) | `IMPLEMENTED`, `REACHABLE`, `SERVED` |
| 2 | `ConsolidationCycle` (105 строк) | `io.matrix.memory` | **Промоушен по ярусам памяти** M2→M1→M0 по счётчику обращений. Только в памяти, без persistence | `IMPLEMENTED`, `TESTED`, **НЕ `REACHABLE`** |
| 3 | `SleepCycle` (103 строки) | `io.matrix.sleep` | **Окно сна**: слив + промоушен + выпуск дайджеста. Это то, что сервится | `IMPLEMENTED`, `REACHABLE`, `SERVED`, `TESTED` |
| 4 | `SubconsciousConsolidator` (134 строки) | `io.matrix.lifecycle` | **TR/REM-цикл как запись хешей** в `Anonymizer`. Ничего не переносит | `IMPLEMENTED`, `TESTED`, **НЕ `REACHABLE`** |
| 5 | `KnowledgeConsolidator` (475 строк) | `io.matrix.knowledge` | **Ограниченное обратимое забывание** диалоговой памяти. Единственный цикл, который пишет в базу | `IMPLEMENTED`, `REACHABLE`, `SERVED`, `TESTED` |
| 6 | `SleepConsolidator` (381 строка) | `io.matrix.brain.runtime.graph` | **Фаза прунинга** графа-связей во сне | `IMPLEMENTED`, `TESTED`, **НЕ `REACHABLE`** |
| 7 | `ReplayPhase` (159 строк) | `io.matrix.brain.runtime.graph` | **Реплей эпизодов** по коннектому | `IMPLEMENTED`, `TESTED`, **НЕ `REACHABLE`** |
| 8 | `HippocampalReplayScheduler` (48 строк) | `io.matrix.brain.runtime` | **Очередь реплея** с вытеснением | `IMPLEMENTED`, **НЕ `REACHABLE`** |
| 9 | `SleepScheduler`-обёртка `RealSleepScheduler` (273 строки) | `io.matrix.brain.runtime` | **Планировщик окна сна**, оборачивающий `SleepCycle` | `IMPLEMENTED`, `REACHABLE`, `SERVED` |

**Ключевой факт, который этот документ фиксирует: имя «consolidation cycle» означает
три разных механизма в трёх разных слоях, и только один из них трогает базу данных.**

- **Слой 1 — слив (route drain).** `io.matrix.lifecycle.ConsolidationCycle`. Служит
  девятистадийному циклу `io.matrix.reasoning.BrainLoopService`
  (`matrix-core/src/main/java/io/matrix/reasoning/BrainLoopService.java:99`, импорт
  `io.matrix.lifecycle.ConsolidationCycle` `:5`) и гейтвею при старте
  (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:262`).
- **Слой 2 — окно сна.** `io.matrix.sleep.SleepCycle`. Служит через
  `RealSleepScheduler` → `POST /v1/sleep`.
- **Слой 3 — забывание.** `io.matrix.knowledge.KnowledgeConsolidator`. Служит через
  `/v1/workbench/workspaces/{id}/consolidate`.
- **Острова** (production-вызывающих нет, измерено поиском по `matrix-core/src/main`,
  `matrix-brain-runtime/src/main`, `matrix-api-gateway/src/main`): `io.matrix.memory.
  ConsolidationCycle` (единственный вызывающий — `io.matrix.consciousness.
  BrainLoopMemoryAdapter:38`, измерительный контур), `SubconsciousConsolidator`,
  `SleepConsolidator`, `ReplayPhase`, `HippocampalReplayScheduler`.

**Про `io.matrix.noosphere.DreamReplayer`** (58 строк) — единственный класс реплея с
production-ссылками, но обе ссылки ведут в **тест-хелпер, лежащий в дереве main**:
`matrix-core/src/main/java/io/matrix/neuron/DreamReplayerTestHelper.java:10`. Сам
`DreamReplayer` — перевзвешивание величин (`replay` `:25`, константы `RECENT_BOOST` /
`OLD_DECAY` `:17-18`), а не реплей эпизодов. Статус: `IMPLEMENTED`, **НЕ `REACHABLE`**.

---

## Архитектура

```
                    ┌──────────────────────────────────────────────┐
chain B (mind)      │  POST /v1/sleep   MinimalHttpServer:394      │
                    │        │                                     │
                    │        ▼ triggerNow()                        │
                    │  RealSleepScheduler:39  → :103               │
                    │        │                                     │
                    │        ▼ runOnce()                           │
                    │  SleepCycle:27 → :50                         │
                    │    1│ drain("sleep-cycle", 16)      :57      │
                    │    2│ promote L1_PATTERN → L2_MODULE :64-73  │
                    │    3│ digest = identityHash+wallClock :79-81 │
                    │        ▼                                     │
                    │  lifecycle.ConsolidationCycle:12             │
                    │  Anonymizer:14   (счётчик, не выпуск)       │
                    └──────────────────────────────────────────────┘

                    ┌──────────────────────────────────────────────┐
chain A (epistemic) │  GET/POST /v1/workbench/workspaces/{id}/    │
                    │      consolidate     LearningWorkbenchResource:659│
                    │        ▼                                     │
                    │  KnowledgeConsolidator:41 → consolidate :189 │
                    │    • 6 правил находят кандидатов    :90-107  │
                    │    • бюджет ограничивает маркировку  :44-56 │
                    │    • МАРКИРОВКА, НЕ УДАЛЕНИЕ         :237    │
                    │        ▼                                     │
                    │  KnowledgeWorkbench.forgetQuestion:1196      │
                    │        ▼                                     │
                    │  KnowledgeStore.forgetAskedQuestion:2500     │
                    │    UPDATE asked_question SET forgotten_at…  │
                    └──────────────────────────────────────────────┘

                    ┌──────────────────────────────────────────────┐
острова (нет        │  io.matrix.memory.ConsolidationCycle (TR/REM)│
production-вызовов) │  SubconsciousConsolidator (TR/REM → хеши)    │
                    │  graph.SleepConsolidator / graph.ReplayPhase │
                    │  HippocampalReplayScheduler                 │
                    └──────────────────────────────────────────────┘
```

**Маршруты.** `POST /v1/sleep` — chain B, в перечне **28 обслуживаемых маршрутов**
(`docs-v2/architecture/RUNTIME-TOPOLOGY.md:60-64`), обработчик
`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:1398`.
`/v1/workbench` — chain A, **57 маршрутов** (`RUNTIME-TOPOLOGY.md:33`); консолидация
живёт на `LearningWorkbenchResource.java:659`, вызов —
`LearningWorkbenchResource.java:669`.

---

## Фазы

### Фаза 1 — слив (bounded drain)

`io.matrix.lifecycle.ConsolidationCycle` (`matrix-core/src/main/java/io/matrix/lifecycle/ConsolidationCycle.java:12`).
`open(Map<String,Integer> initialBacklogs)` (`:22`) регистрирует бэклоги по маршрутам и
**валидирует всё до мутации состояния** — «no dirty open on failure» (`:26-32`);
повторный `open` бросает `IllegalStateException("cycle_already_open")` (`:23-25`).
`drain(route, batchSize)` (`:44`) двигает `min(pending, batchSize)` элементов и
накапливает `migratedTotal` (`:51-55`); отрицательный `batchSize` отвергается (`:48-50`).
`close()` (`:59`) возвращает `DrainSummary(routesDrained, itemsMigrated)` (`:15`) и
считает маршрутом «выдренным» тот, у которого бэклог обнулился (`:64`).
**Детерминизм: ни стенного времени, ни случайности — размер батча задаёт вызывающий**
(`ConsolidationCycle.java:9-10`). **Status: `IMPLEMENTED`, `SERVED`, `TESTED`.**

### Фаза 2 — промоушен (promotion)

`io.matrix.sleep.SleepCycle.runOnce()` (`matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:50`),
шаг 2 (`:62-74`): берутся записи уровня `L1_PATTERN`
(`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`, где
`L1_PATTERN(1, "Pattern", 0.3)` и `L2_MODULE(2, "Module", 0.5)`), и запись с
`importance >= 0.5` промоутится в `L2_MODULE` (`SleepCycle.java:68-71`).
Уровни памяти — `HierarchicalMemory.Level` (`HierarchicalMemory.java:45`).
**Status: `IMPLEMENTED`, `SERVED` (через окно сна), `TESTED` — частично: теста на
промоушен через рестарт нет.**

### Фаза 3 — маркировка забывания (bounded forgetting)

`io.matrix.knowledge.KnowledgeConsolidator`
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeConsolidator.java:41`).
`consolidate(workbench, workspaceId, now, budget)` (`:189`) — **метод, который выполняет
консолидацию базы знаний**. Шесть правил называют кандидатов (перечисление `Kind`,
`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeConsolidator.java:94-106`): след
(`:95`), однократный спрос (`:97`), избыточность (`:99`), помехи (`:101`), закрытие
пробела (`:103`), опровержение (`:105`). Бюджет ограничивает, сколько записей один прогон может забыть
(`Budget(maxForgotten, ageWindowMs)` `:44`, `defaults()` `:56`). Живая фаза —
**`:237` «PHASE: THE MARK. A forgotten record is marked, never deleted.»**
Запись маркируется через `workbench.forgetQuestion(workspaceId, id, kind, reason, nowMs)`
(`:244`) → `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1196` →
`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:2500`.
**Status: `IMPLEMENTED`, `SERVED`, `TESTED`.**

### Фаза 4 — выпуск дайджеста

`SleepCycle.runOnce()`, шаг 3 (`SleepCycle.java:76-84`): если есть `Anonymizer` и память,
считается «contentHash» и он записывается в счётчик (`:79-81`).
Полная спецификация выпуска — [SPEC-016](../specifications/SPEC-016-decentralized-digests.md).
**Status: `IMPLEMENTED`, но дефектно — см. § Инварианты, инвариант 6.**

### Фаза 5 — TR/REM (остров)

`io.matrix.memory.ConsolidationCycle` (`matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:21`).
`tick()` (`:42`) = `tr()` (`:48`) + `rem()` (`:61`). TR: M2 → M1. REM: записи M1 с
`accessCount >= 1` → M0 (`:63-67`). Хранение — три `ArrayList` (`:28-30`), **persistence
нет**. Javadoc класса: *«RUN 162 — Consolidation cycle (TR + REM)»* (`:7`).
`io.matrix.lifecycle.SubconsciousConsolidator` (`matrix-core/src/main/java/io/matrix/lifecycle/SubconsciousConsolidator.java:34`)
реализует TR/REM **как запись хешей**: TR-фаза — явный no-op в коде (`:87-89`: *«TR
phase: passive consolidation (no-op for the minimal impl, the digest is recorded for
outbound gossip)»*), REM-фаза — хеш каждой записи чекпоинта (`:95-99`).
**Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE` — острова.**

---

## Инварианты

1. **Забывание маркирует, а не удаляет.** SQL консолидации
   (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:2500`):
   `UPDATE asked_question SET forgotten_at = ?, forgotten_kind = ?, forgotten_reason = ? WHERE workspace_id = ? AND id = ? AND forgotten_at IS NULL`.
   Ни одного `DELETE` в пути консолидации нет; запись остаётся в базе с меткой.
   **Status: `IMPLEMENTED`, `SERVED`.**
2. **Идемпотентность по записи.** Последний предикат `AND forgotten_at IS NULL` делает
   повторный прогон по той же записи no-op; сам консолидатор фиксирует это как
   «the run is idempotent PER RECORD: a record that was forgotten is never forgotten
   again» (`KnowledgeConsolidator.java:283`). Отчёт отдаёт **фактически помеченные**
   записи, а не кандидатов (`:322-325`). **Status: `IMPLEMENTED`, `TESTED`.**
3. **Консолидация не трогает утверждения.** `KnowledgeConsolidator` работает по
   диалоговой памяти и не пишет в `claim`, `evidence`, `derivation`, `contradiction`;
   кандидаты выводятся из `asked_question` и снапшота (`KnowledgeConsolidator.java:338`,
   `:344`). **Status: `IMPLEMENTED`.**
4. **Тот же закон для иерархической памяти.** `SqliteMemoryBackend.compact(...)`
   (`matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:189-191`) пишет
   `UPDATE memory SET forgotten_at = ?, forgotten_reason = ?` — **маркировка**;
   `tombstones()` (`:213`) читает помеченные, `restore(id)` (`:231`) снимает метку.
   **Status: `IMPLEMENTED`.**
5. **Слив детерминирован.** Ни стенного времени, ни случайности в
   `io.matrix.lifecycle.ConsolidationCycle` (`ConsolidationCycle.java:9-10,44-56`).
   **Status: `IMPLEMENTED`, `TESTED`.**
6. **Дефект окна сна: дайджест — это `identityHash + wallClock`.**
   `matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:79-81`:
   ```java
   String contentHash = Integer.toHexString(memory.hashCode())
           + "-" + System.currentTimeMillis();
   anonymizer.recordContribution(contentHash, "sleep-cycle");
   ```
   Это не хеш по содержимому, и он **нов на каждом цикле**. Счётчик `Anonymizer` растёт
   по хешу (`matrix-core/src/main/java/io/matrix/federation/Anonymizer.java:26`), поэтому
   счётчик такого хеша навсегда равен 1, `isAnonymous` (`Anonymizer.java:30`) возвращает
   `false`, и `DecentralizedDigestPipeline.emitDigests()` никогда не поставит такому
   дайджесту `shared = true`
   (`matrix-core/src/main/java/io/matrix/federation/DecentralizedDigestPipeline.java:76`).
   Вдобавок `System.currentTimeMillis()` стоит на пути решения, а `AGENTS.md` запрещает
   wall-clock в рантайм-пути решений без явного RFC-мандата.
   **Status: `IMPLEMENTED` (как есть) — дефект зафиксирован, код не исправлен.**
7. **Второй дефект смежного класса: `Anonymizer.recordContribution` игнорирует
   `nodeId`.** `matrix-core/src/main/java/io/matrix/federation/Anonymizer.java:25-27`
   использует только `contentHash`, тогда как javadoc обещает *«at least k **distinct
   nodes**»* (`Anonymizer.java:9-11`). К-анонимный контракт, который класс заявляет, не
   тот, что он реализует. **Status: `PROPOSED` (k узлов) против `IMPLEMENTED` (k
   вкладов) — дефект зафиксирован, код не исправлен.**

---

## Связь с существующим

- **[SPEC-016](../specifications/SPEC-016-decentralized-digests.md)** — спецификация
  выпуска дайджеста; фаза 4 этого дизайна.
- **[SPEC-007](../specifications/SPEC-007-subconscious.md)** — родительская спека
  подсознательного слоя. **Осторожно:** она называет `subconscious/SubconsciousDaemon`,
  `subconscious/PredictionModel`, `subconscious/DreamReplay`, `subconscious/ImpulseGenerator`,
  `subconscious/Impulse` (`SPEC-007-subconscious.md:28-33`) — **пакета `subconscious/` в
  репозитории нет**; существующий `PredictionModel` лежит в
  `matrix-core/src/main/java/io/matrix/consciousness/PredictionModel.java`. Статус: те
  классы **`PROPOSED`**, не `IMPLEMENTED`.
- **[DESIGN-19](./DESIGN-19-subconscious-consolidator.md)** — ближайший дизайн: TR/REM +
  gossip. **Осторожно:** его TR-фаза обещает *«promotion в M2 (semantic)»* (`:47`), а
  REM-фаза — *«offline replay через `subconscious/PredictionModel`»* (`:54`); **ни того,
  ни другого в коде нет** (см. фазу 5). Статус: **`PROPOSED`**.
- **[DESIGN-05](./DESIGN-05-memory.md)** — пять слоёв M0…M4 и их физика (`docs-v2/designs/DESIGN-05-memory.md:7`).
- **[DESIGN-07](./DESIGN-07-lifecycle.md)** — жизненный цикл элементов; **21 строка**,
  механизмы не описывает. Статус: `DESIGNED`.
- **[DESIGN-08](./DESIGN-08-federation.md)** — ELSP, ML-DSA v2, k-анонимность (`docs-v2/designs/DESIGN-08-federation.md:7-16`).
- **[SPEC-011](../specifications/SPEC-011-memory-hierarchy.md)** — иерархическая память.
  **Осторожно:** спека перечисляет уровни `L1_HOT / L2_MODULE / L3_ARCHIVE`
  (`SPEC-011-memory-hierarchy.md:18`) и говорит, что `SqliteMemoryBackend` хранит записи
  **в трёх таблицах** `memory_l1 / memory_l2 / memory_l3` (`SPEC-011-memory-hierarchy.md:111`), тогда как код
  имеет **пять** уровней `L0_ARTIFACT…L4_KERNEL`
  (`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`) и **одну**
  таблицу `memory` (`matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:43`).
  Это расхождение — существующий дрейф чужой спеки; здесь оно **зафиксировано**, но не
  исправлено (§ Отложено).
- **[architecture/RUNTIME-TOPOLOGY.md](../architecture/RUNTIME-TOPOLOGY.md)** — канонический
  документ о том, что реально работает; 28 маршрутов chain B (`:60-64`), 210 маршрутов
  chain A (`:38`).
- **[architecture/DATA-AND-MEMORY-ARCHITECTURE.md](../architecture/DATA-AND-MEMORY-ARCHITECTURE.md)**
  — канонический документ о базах и транзакционных границах.
- `io.matrix.reasoning.BrainLoopService` — обслуживаемый девятистадийный цикл
  (`matrix-core/src/main/java/io/matrix/reasoning/BrainLoopService.java:99`).
- `io.matrix.consciousness.BrainLoopMemoryAdapter` — измерительный контур
  (`matrix-core/src/main/java/io/matrix/consciousness/BrainLoopMemoryAdapter.java:38`).

---

## Тесты

| тест | файл | `@Test` | статус |
|---|---|---|---|
| `ConsolidationCycleTest` (lifecycle) | `matrix-core/src/test/java/io/matrix/lifecycle/ConsolidationCycleTest.java` | 3 | `TESTED` |
| `ConsolidationCycleTest` (memory) | `matrix-core/src/test/java/io/matrix/memory/ConsolidationCycleTest.java` | 9 | `TESTED` |
| `SleepCycleTest` | `matrix-core/src/test/java/io/matrix/sleep/SleepCycleTest.java` | 3 | `TESTED` |
| `KnowledgeConsolidatorTest` | `matrix-core/src/test/java/io/matrix/knowledge/KnowledgeConsolidatorTest.java` | 10 | `TESTED` |
| `SubconsciousConsolidatorTest` | `matrix-core/src/test/java/io/matrix/lifecycle/SubconsciousConsolidatorTest.java` | 6 | `TESTED` |
| `SleepConsolidatorTest`, `SleepConsolidatorCrossValidationTest`, `SleepConsolidatorAnnealTest` | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/graph/` | — | `TESTED` (остров) |
| `ReplayPhaseTest` | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/graph/ReplayPhaseTest.java` | — | `TESTED` (остров) |

**Честно о покрытии.** Счётчик `@Test` — это число аннотаций в файле, а не число
исполненных тестов: параметризованный метод считается один раз. **Тестов нет:**
на восстановление промоушена после рестарта; на то, что k-порог дайджеста вообще
открывается; на `HippocampalReplayScheduler`; на `DreamReplayer` (его «тест-хелпер»
лежит в дереве `main`: `matrix-core/src/main/java/io/matrix/neuron/DreamReplayerTestHelper.java:10`).
Классы `M3PromotionGateTest` и `ImpulseGenerationTest`, названные в
`docs-v2/designs/DESIGN-19-subconscious-consolidator.md:106-107`, **не существуют**;
`SubconsciousBypassGuardTest`, названный в `SPEC-007-subconscious.md:64`, — **тоже**.

---

## Метрики / гейты

- **H-120** — *«Сон»-консолидация не является безусловным благом: при достаточном объёме
  данных она может ухудшать результат, поэтому утверждение о консолидации должно быть
  обусловлено измерением* (`docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:383`;
  открыта в `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §2). **Это гейт
  всего раздела: утверждение о консолидации без замера не пишется.**
- **Гейт консолидации знаний** — отчёт `POST /v1/workbench/workspaces/{id}/consolidate`
  отдаёт `examined`, `candidates`, `forgotten`, `idempotent`, `invariants` и список
  **фактически помеченных** записей с основанием
  (`matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:752-756`).
  **Status: `IMPLEMENTED`, `SERVED`.**
- **Единственный замер в репозитории** — `docs-v2/research/SLEEP-CONSOLIDATION-STUDY-W650.md`
  (**16 строк**): No Sleep 0.514 → 0.514, With Sleep 0.514 → 0.613, retention 78.0 %,
  forgetting −19.4 %, memory 78 (`:5-8`). **Метода, корпуса, seed'а и первоисточника в
  документе нет** — только заголовок и таблица. **Status: `BENCHMARKED` — но без
  воспроизводимой методики; гейтом это не является.**
- **Per-phase latency** — измерима через JMH-инфраструктуру
  (`docs-v2/research/PROTOCOL.md`); замера фаз консолидации в репозитории нет.
  **Status: `PROPOSED`.**
- **TLA+-контракт консолидации не существует.** В `formal/spec-implementation-map.yml`
  14 записей; консолидации среди них нет. Статус: `PROPOSED`.

---

## Исследовательские перспективы (META-R1)

Консолидация памяти — архитектурное решение ядра, поэтому на него распространяется
META-R1: не менее трёх действительно разных перспектив, каждая с **первоисточником,
уже записанным в репозитории**.

| перспектива | школа | первоисточник | где он в репозитории | что он говорит этому дизайну | статус |
|---|---|---|---|---|---|
| **R-D нейронаука раннего обучения** — сон как активный процесс переноса, а не как покой | Spelke / Spitz-Sokolov / Kauffman | **Rasch, B. & Born, J. (2013), «About sleep's role in memory», *Physiological Reviews* 93(2), DOI `10.1152/physrev.00032.2012`** | `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:346` | консолидация — **активная системная операция**, а не «слив очереди»; фаза промоушена (фаза 2) не должна быть пустой | `PROPOSED` (источник доступен, применение не записано) |
| **R-B кибернетика / computational cognition** — остановка как условие переноса, порог как правило решения | Anokhin / Bernstein / Ashby / Minsky | **Schaul, Quan, Antonoglou & Silver (2015), «Prioritized Experience Replay», arXiv `1511.05952`**; Tadros et al. (2022), sleep-like unsupervised replay | `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:218` | реплей должен быть **приоритетным и отборным**, а не полным прогоном; это противопоставляет текущую фазу REM, которая перебирает всё (фаза 5) | `PROPOSED` |
| **R-E биология вычислений / нейронаука** — генеративный реплей и переиспользование в сне | Kauffman / Spelke | **Schwartenbeck, Baram, Liu et al. (2023), «Generative replay underlies compositional inference…», *Cell* 186(22), DOI `10.1016/j.cell.2023.09.004`**; **Otte et al. (2013), *Detecting violations of temporal regularities in waking and sleeping two-month-old infants*, *Biological Psychology* 92(2), DOI `10.1016/j.biopsycho.2012.09.009`**; **Friedrich, Wilhelm, Born & Friederici (2015), *Generalization of word meanings during infant sleep*, *Nature Communications* 6:6004, DOI `10.1038/ncomms7004`** | `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:344`, `:345`, `:343` | консолидация **переформулирует**, а не только переносит: обобщение во сне — измеряемый эффект, а не побочный | `PROPOSED` |

**META-R3 — по этому предмету первоисточники есть.** Все пять перечисленных источников
уже записаны в `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` (`:218`, `:343-346`).
Это **не** относится к предмету дайджестов — там первоисточников в репозитории нет
вовсе (см. [SPEC-016](../specifications/SPEC-016-decentralized-digests.md), § Исследовательские
перспективы).

---

## Отложено

Ничего из перечисленного **не исправлено** в этой волне: она пишет документы, а не код.

- **Дефект окна сна** — дайджест есть `identityHash + wallClock`
  (`SleepCycle.java:79-81`); k-порог для него не открывается; wall-clock стоит на пути
  решения. **Не исправлено.**
- **Дефект `Anonymizer.recordContribution`** — `nodeId` игнорируется
  (`Anonymizer.java:25-27`), k считает вклады, а не узлы; требуется решение владельца,
  от которого зависит смысл спецификации выпуска. **Не исправлено.**
- **Острова остаются островами.** `io.matrix.memory.ConsolidationCycle`,
  `SubconsciousConsolidator`, `SleepConsolidator`, `ReplayPhase`,
  `HippocampalReplayScheduler` имеют 0 production-вызывающих. **Не исправлено.**
- **Расхождение `io.matrix.memory.ConsolidationCycle` с её же документацией.** Javadoc
  `tr()` обещает *«for any M2 entry that has an incoming request key»*
  (`matrix-core/src/main/java/io/matrix/memory/ConsolidationCycle.java:47`), а код
  переносит **каждую** запись M2 безусловно (`:48-58`). **Не исправлено.**
- **TR/REM-фазы из `SPEC-007` и `DESIGN-19` не реализованы.** Пакет `subconscious/`
  не существует; TR-фаза `SubconsciousConsolidator` — явный no-op
  (`SubconsciousConsolidator.java:87-89`). **Не реализовано.**
- **Сторож `SleepConsolidator`** — аннотация верна только для рёбер, вошедших в
  `CONNECTED` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/SleepConsolidator.java:251-268`).
  **Не исправлено.**
- **`DreamReplayer` доступен только через тест-хелпер в дереве `main`**
  (`matrix-core/src/main/java/io/matrix/neuron/DreamReplayerTestHelper.java:10`).
  **Не исправлено.**
- **Расхождение `SPEC-011-memory-hierarchy.md`** — пять уровней против трёх
  (`:30` против `HierarchicalMemory.java:45-55`) и одна таблица `memory` против трёх
  `memory_l1/l2/l3` (`:124-126` против `SqliteMemoryBackend.java:43`); пять тестовых
  файлов названы с неверными числами или не существуют (`SPEC-011-memory-hierarchy.md:128-129`). Чужая спека — не в моей зоне
  записи. **Не исправлено.**
- **Классы `M3PromotionGateTest`, `ImpulseGenerationTest`, `SubconsciousBypassGuardTest`**
  не существуют, хотя названы в `DESIGN-19:105-106` и `SPEC-007:41`. **Не исправлено.**
- **TLA+-контракт консолидации** не существует (`formal/spec-implementation-map.yml`).
  **Не реализовано.**
- **Замер консолидации без методики** — `docs-v2/research/SLEEP-CONSOLIDATION-STUDY-W650.md`
  (16 строк). **Не переделан.**

---

См. [architecture/MODULES.md](../architecture/MODULES.md),
[architecture/RUNTIME-TOPOLOGY.md](../architecture/RUNTIME-TOPOLOGY.md),
[architecture/DATA-AND-MEMORY-ARCHITECTURE.md](../architecture/DATA-AND-MEMORY-ARCHITECTURE.md),
[research/HYPOTHESES-NEW.md](../research/HYPOTHESES-NEW.md),
[research/RESEARCH-INDEX.md](../research/RESEARCH-INDEX.md),
[research/NEGATIVE-RESULTS-INDEX.md](../research/NEGATIVE-RESULTS-INDEX.md).
