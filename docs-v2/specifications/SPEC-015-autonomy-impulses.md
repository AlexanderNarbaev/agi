# SPEC-015 — Autonomy Impulses (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.lifecycle`
**Related**: [SPEC-005](./SPEC-005-action.md), [SPEC-007](./SPEC-007-subconscious.md), [SPEC-009](./SPEC-009-mediator-hierarchy.md), [DESIGN-11](../designs/DESIGN-11-budgeter.md), [DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md), [AR](../architecture/REQUEST-autonomy-impulses.md)

> **W48 note — 2026-11-06.** This document describes the **scheduler**, and the scheduler is
> **not** an executor. The distinction below is stated here because the rest of the document
> depends on it, and because the wave found it worth a document of its own:
>
> - **`FIRED` means ACCEPTED, not CONSUMED.** `ImpulseScheduler.fire(...)`
>   (`matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59`) returns a
>   `FireRecord` (`:33`). A `FIRED` outcome means the impulse **passed the FROZEN gate and
>   the conjugate budget was allocated to it** — nothing more. It does **not** mean that
>   work was dispatched, consumed, executed or completed.
> - **There is no downstream consumer.** The only two production call sites of
>   `scheduler.fire(...)` are `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:141`
>   and `matrix-core/src/main/java/io/matrix/autonomy/SelfImprovingEngine.java:60`. The
>   `FireRecord` is **read** in exactly one place: `AutonomyEngine.fire(...)` logs a
>   **refusal** and **drops** a `FIRED` record (`:141-145`). The `AutonomyEngine` **records
>   and logs** outcomes — it executes nothing.
> - **The curiosity cycle is not a completed autonomous work system.** It decides that it may
>   want to do something, pays for the wanting, and writes the decision in a log. There is
>   no queue, no proposal and no executor.
>
> The ADR that would close the gap is **APPROVED by the owner on 2026-11-06 — OD-045,
> option A — and it is NOT IMPLEMENTED**:
> `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` <!-- no-id: the ADR declares this identifier in its own heading; this line references the document, it does not define it -->. **No consumer
> exists**, the implementation is the **first task of the next wave**, and nothing in this
> specification may be read as describing a consumer that is already there.

## Что

Спецификация **автономных импульсов** — фоновых единиц работы, которые система может запустить
**без внешнего запроса пользователя**, если позволяет бюджет и не запрещено FROZEN-гейтом.

В дереве есть **две несогласованные версии этого предмета**, и эта спецификация выбирает одну:

| | версия A — реализация | версия B — спецификация [SPEC-007](./SPEC-007-subconscious.md) |
|---|---|---|
| где живёт | `io.matrix.lifecycle`, `io.matrix.autonomy` | `io.matrix.subconscious` |
| четыре импульса | `CURIOSITY`, `CONSOLIDATION`, `INTEGRITY_CHECK`, `SHARE_DIGEST` — **enum** | `CuriosityImpulse`, `IntegrityImpulse`, `ShareImpulse`, `ConsolidationImpulse` — **четыре класса** |
| конвейер | `ImpulseScheduler.fire` → `ConjugateBudgeter` + `EthicalFilter` | `Impulse` → `AttentionRouter` → `ActionGate` |

**Эта спецификация нормативна для версии A.** Версия B описывает классы, которых в дереве нет
(§ «Связь с существующим»); её предмет — фоновый слой в целом, а не импульсы.

Ключевые утверждения:

- **IMPLEMENTED** — набор импульсов **закрыт и состоит ровно из четырёх**: любопытство, консолидация, проверка целостности, передача дайджеста. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/AutonomyImpulse.java:11-19`.
- **IMPLEMENTED** — запуск импульса проходит через **бюджетер** и **FROZEN-гейт** и возвращает наблюдаемый исход. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59-98`.
- **IMPLEMENTED** — сам Budgeter и FROZEN-гейт живут в других классах, спецификация импульсов их только вызывает. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:87-95` (бюджет), `:75-79` (этика).
- **PROPOSED** — импульс **не является привилегией**: он получает ровно те же гейты, что и действие по запросу пользователя. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:94-99` (директива), `docs-v2/specifications/SPEC-005-action.md:49-62` (сам гейт живёт в другой спецификации — см. § «Связь с существующим»).
- **PROPOSED** — никаких «свободы воли», «воли» или «желаний»: формулировки запрещены CONSTITUTION VI. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:119-121`.
- **[W48 note — 2026-11-06] IMPLEMENTED — `FIRED` means ACCEPTED by the scheduler, not
  EXECUTED.** `fire()` returns a `FireRecord`; the `FIRED` outcome means the impulse passed
  the FROZEN gate and the budget was allocated to it — **not** that work was dispatched,
  consumed, executed or completed. There is no consumer: the `FireRecord` is read in exactly
  one place (`AutonomyEngine.java:141-145`), where a refusal is logged and a `FIRED` record
  is dropped. Evidence:
  `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59`;
  `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:141-145`. **See the W48
  note at the top of this document.**

### Таблица четырёх импульсов

| импульс | что означает | стоимость | ценность | evidence |
|---|---|---|---|---|
| `CURIOSITY` | активное исследование недо-освоенных областей наблюдения | 100 | 100.0 | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:105`, `:114` |
| `CONSOLIDATION` | консолидация памяти: реплей + дайджест | 50 | 80.0 | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:106`, `:115` |
| `INTEGRITY_CHECK` | проверка целостности: хеш-чекпоинт против текущего состояния | 10 | 60.0 | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:107`, `:116` |
| `SHARE_DIGEST` | передача дайджеста M3→M4 | 5 | 40.0 | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:108`, `:117` |

Стоимость и ценность — **константы, а не измерения**; они задают порядок конкуренции при
малом бюджете (дешёвые выигрывают), но не выведены ни из одного замера.

## Мотивация

Импульс — это способ сделать фоновую работу **видимой и подотчётной**: без него фоновая
деятельность либо не существует, либо существует вследую. Спецификация фиксирует не
«метафору самостоятельности», а **набор из четырёх записей, у каждой из которых есть
стоимость, ценность, гейт и наблюдаемый исход**.

> **[W48 note — 2026-11-06] What is visible today is the DECISION, not the work.** The
> motivation above is the design intent, and it is not yet a fact on the ground: the
> observable outcome of a fired impulse today is the scheduler's `FireRecord`, and nothing
> consumes it. **The curiosity cycle is not a completed autonomous work system** — it is a
> cycle that decides, pays for and records the wish to work, and then has nowhere to put it.
> The proposal that would give it somewhere to put it is in § «Отложено».

### Три исследовательские перспективы (META-R1)

Три перспективы из разных школ, каждая привязана к первоисточнику, который **уже есть в
репозитории**:

| школа | первоисточник в репозитории | что он даёт этой спецификации |
|---|---|---|
| **R-B кибернетика — П. К. Анохин** | «Теория функциональных систем» (1935-1974): `docs-v2/research/W95-CYBERNETIC-RESEARCH-REPORT.md:29` (§2.1), `:46` (обратная афферентация), `:153` (таблица «школа → механизм»); библиография `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:110`; гипотеза **H-054** — `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:29` | импульс — это **исполнительная сторона** петли «результат → обратная связь»: результат цикла сравнивается с прогнозом, и расхождение порождает следующий выстрел, а не новое намерение. Отсюда — требование, чтобы у импульса был **измеримый** исход |
| **R-B кибернетика — У. Р. Эшби** | «Introduction to Cybernetics» (1956): `docs-v2/research/W95-CYBERNETIC-RESEARCH-REPORT.md:21`, `:25`, `:75` (§2.3), `:155`; библиография `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:112` | **requisite variety** и ультрастабильность: набор допустимых реакций ограничен, потому что ограничен бюджет. Отсюда — закрытый набор из четырёх импульсов и бюджет как гомеостат |
| **R-B computational cognition — О. Мински** | Society of Mind: `docs-v2/research/W95-CYBERNETIC-RESEARCH-REPORT.md:25`, `:103` (§2.4), `:153` | импульс — это **задача субагента**, а не «намерение системы»: у неё есть стоимость и она конкурирует с остальными задачами. Отсюда — `Row(id, value, cost)` и то, что дефицит бюджета означает задержку, а не голод |

Собственный preregistered-эксперимент репозитория, который измеряет точность импульса
любопытства: **H-039** — `docs-v2/research/protocols/H-039.md:1-12` (порог `θ_c ∈ {1,2,3,4}`,
Precision@top-K ≥ 0.7 при recall ≥ 0.5, synthetic-scope).

**UNVERIFIED** — первоисточника по «intrinsic motivation» в репозитории **нет**; формулировка
AR (`docs-v2/architecture/REQUEST-autonomy-impulses.md:5`) на него опирается, но не ссылается.

Отдельно: **H-046 объявлена** — `docs-v2/research/HYPOTHESES-NEW.md:19`, статус
`accepted (synthetic-scope, RUN 16)`, измеренный результат EXP-MATRIX.15: accuracy 0.915,
precision 1.000, recall 0.742 (n=200). Но её **указатель на реализацию неверен**: гипотеза
отсылает к `SPEC-007` и его классу `ImpulseGenerator` — **класса в дереве нет** (см.
§ «Связь с существующим»). Код, который её цитирует, —
`matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:66` — реализует не
`ImpulseGenerator`, а allow-list из четырёх значений `io.matrix.lifecycle.AutonomyImpulse`.

## Архитектура

```
 trigger (idle / drift / explicit)
        │
        ▼
 io.matrix.autonomy.AutonomyEngine        — три расписания: 30 s / 120 s / 300 s
        │  .fire(impulse, envelope, context)
        ▼
 io.matrix.lifecycle.ImpulseScheduler
        │
        ├── ethics.frozenViolatedAxiom(name) != null  ──► REJECTED_ETHICS
        │
        ├── cost > envelope                            ──► REJECTED_BUDGET
        │
        └── ConjugateBudgeter.allocate([Row(name, value, cost)], envelope)
                 │
                 ├── mode != CONJUGATE или строка не выбрана ──► REJECTED_BUDGET
                 └── иначе ──► FIRED { envelopeSpent, rowsAllocated = 1 }
```

Ключевые утверждения:

- **IMPLEMENTED** — три расписания автономного цикла: `idleCycle` каждые 30 с, `curiosityCycle` каждые 120 с, `integrityCycle` каждые 300 с. Evidence: `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:39`, `:41`, `:42`.
- **IMPLEMENTED** — цикл любопытства выстреливает `CURIOSITY` **безусловно**, цикл проверки целостности — `INTEGRITY_CHECK`. Evidence: `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:75`, `:82`.
- **IMPLEMENTED** — гейт FROZEN стоит **до** бюджета: запрещённый импульс отклоняется, даже если бюджет его выдерживает. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:75-79`.
- **IMPLEMENTED** — бюджетный конвейер: одна строка `Row(имя, ценность, стоимость)`, `ConjugateBudgeter.allocate`, отбор только в режиме `CONJUGATE`. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:87-95`.
- **IMPLEMENTED** — набор импульсов **закрыт allow-list'ом**: только четыре значения enum могут быть выстреляны; комментарий в коде фиксирует, что это закрывает дыру, найденную в H-046. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:63-74`.
- **IMPLEMENTED** — решение о запуске **не использует ни Random, ни wall-clock**: `ImpulseScheduler` не содержит ни того, ни другого (проверено grep по файлу). Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59-98`.
- **[W48 note — 2026-11-06] IMPLEMENTED — the `FIRED` outcome is NOT consumed by anything.**
  `fire()` returns the `FireRecord` to its caller, and the **only** place in the tree that
  reads one is `AutonomyEngine.fire(...)`: it logs a **refusal** and **drops** a `FIRED`
  record. There is no dispatcher, no queue, no proposal store and no executor. The
  `AutonomyEngine` **records and logs** outcomes — it does not perform the work an impulse
  names. Evidence: `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:140-146`;
  the two production call sites of `scheduler.fire(...)` are
  `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:141` and
  `matrix-core/src/main/java/io/matrix/autonomy/SelfImprovingEngine.java:60`.
- **PROPOSED** — дефицит бюджета означает **задержку, а не голод**: любой импульс может быть отложен, но не потерян. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:84-90` — в коде отложенного импульса нет, есть только `REJECTED_BUDGET` (`matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:83-86`).
- **PROPOSED** — каждый импульс обязан писать в `audit/HashChain` с trigger-id. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:99` — **в `ImpulseScheduler` обращения к журналу аудита нет**.
- **PROPOSED** — полный FROZEN-конвейер `EthicalFilter → StructuralSafetyGuard → LieDetector → HashChain`. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:94-99` — реализован только первый шаг (`matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:75-79`).
- **PROPOSED** — таблица триггеров AR (idle ≥ τ, drift > κ, локальный M2 Δ > порог) нигде не закодирована: код знает только расписания. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:13-18` против `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:39-42`.

## FR (классы и интерфейсы)

- **IMPLEMENTED** — `io.matrix.lifecycle.AutonomyImpulse` — enum из четырёх значений, без состояния. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/AutonomyImpulse.java:11-19`.
- **IMPLEMENTED** — `io.matrix.lifecycle.ImpulseScheduler(ConjugateBudgeter, EthicalFilter)` — конструктор без состояния, кроме счётчиков. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:48-51`.
- **IMPLEMENTED** — `FireRecord fire(AutonomyImpulse impulse, long envelope, Map<String,Object> context)` — единственная операция запуска. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59`.
- **IMPLEMENTED** — `ImpulseOutcome { FIRED, REJECTED_BUDGET, REJECTED_ETHICS, REJECTED_UNKNOWN }`. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:25-30`.
- **IMPLEMENTED** — `FireRecord(AutonomyImpulse impulse, ImpulseOutcome outcome, long envelopeSpent, int rowsAllocated)` — record с проверкой не-null. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:33-41`.
- **IMPLEMENTED** — `long totalFires()` и `long totalRejections()` — наблюдаемость. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:121-122`.
- **IMPLEMENTED** — `io.matrix.autonomy.AutonomyEngine(BrainCycle, ImpulseScheduler)`; `start()`, `stop()`, `getCycleCount()`, `getTotalReflections()`, `getLastReflection()`, `isRunning()`. Evidence: `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:32-36`, `:38-46`, `:86-92`.
- **IMPLEMENTED** — `io.matrix.autonomy.SelfImprovingEngine(LearningLoop, ImpulseScheduler)` — второй потребитель планировщика; выстреливает `CURIOSITY`. Evidence: `matrix-core/src/main/java/io/matrix/autonomy/SelfImprovingEngine.java:31`, `:60`.
- **IMPLEMENTED** — `ImpulseScheduler` **не должен быть null** ни на одном пути, который может выстрелить. **W47 (OD-038) закрыл дефект:** цикл гейтвейера строит настоящий планировщик (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:56-58`: `new ImpulseScheduler(new ConjugateBudgeter(), new EthicalFilter())`), а конструктор двигателя отвергает null (`matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:54`: `Objects.requireNonNull(scheduler, "scheduler")`). До этого был `new AutonomyEngine(brain, null)`, и `scheduler.fire(...)` вызывался безусловно — то есть через 120 с после старта гейтвейера путь достигал `NullPointerException`, а `ScheduledExecutorService` ОТМЕНЯЕТ задачу, которая бросила: любопытство умирало навсегда. Evidence: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:56-58`; `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:54`; тест `matrix-core/src/test/java/io/matrix/autonomy/AutonomyEngineTest.java`.
- **PROPOSED** — `AutonomyImpulse` должен быть **единственным** типом импульса в системе. Сегодня их два: `io.matrix.consciousness.Impulse` (record с `Source`, `priority`, `weight`, `target`) и `io.matrix.lifecycle.AutonomyImpulse` (enum). Evidence: `matrix-core/src/main/java/io/matrix/consciousness/Impulse.java:18`; `matrix-core/src/main/java/io/matrix/lifecycle/AutonomyImpulse.java:11`.

## Инварианты

1. **IMPLEMENTED** — **I1. Ни один импульс не обходит FROZEN-гейт.** Гейт стоит первым и отклоняет до бюджета. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:75-79`.
2. **IMPLEMENTED** — **I2. Ни один импульс не обходит бюджет.** Стоимость больше конверта отклоняется до вызова бюджетера; внутри бюджетера отбирается только одна строка. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:83-86`, `:87-95`.
3. **IMPLEMENTED** — **I3. Набор импульсов закрыт.** Выстрелить может только одно из четырёх значений enum. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:63-74`.
4. **IMPLEMENTED** — **I4. Решение о запуске детерминировано.** Ни Random, ни wall-clock в пути решения. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:59-98` (в файле нет ни `Random`, ни `currentTimeMillis`).
5. **IMPLEMENTED** — **I5. Импульс не создаёт нового эффекта.** Один выстрел — одна строка бюджетера — одна запись в журнале исходов. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:87-97`.
6. **IMPLEMENTED** — **I6. Планировщик не null ни на одном пути, который может выстрелить.** **W47 (OD-038) закрыл дефект** — см. § «FR». Evidence: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:56-58`; `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:54`.
7. **PROPOSED** — **I7. Каждый выстрел пишется в `audit/HashChain`.** **Код этому инварианту не удовлетворяет.** Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:99`; в `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java` обращения к журналу аудита нет.
8. **PROPOSED** — **I8. Отклонённый по бюджету импульс откладывается, а не теряется.** **Код этому инварианту не удовлетворяет** — есть только `REJECTED_BUDGET` без очереди на повтор. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:83-86`.

## Метрики / гейты

- **IMPLEMENTED** — счётчики `totalFires` и `totalRejections` — единственная текущая наблюдаемость запуска импульсов. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:121-122`.
- **IMPLEMENTED** — исход каждого выстрела наблюдаем: `FIRED`, `REJECTED_BUDGET`, `REJECTED_ETHICS`, `REJECTED_UNKNOWN`. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:25-30`.
- **PROPOSED** — гейт точности импульса любопытства по preregistered-протоколу **H-039**: Precision@top-K ≥ 0.7 при recall ≥ 0.5, sweep `θ_c ∈ {1,2,3,4}`, synthetic-scope. Evidence: `docs-v2/research/protocols/H-039.md:1-12`. **Гейт не подключён к production-пути.**
- **PROPOSED** — стоимость и ценность импульсов должны быть выведены из замера, а не назначены константой. Evidence: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:104-119` — сегодня это константы без provenance-метки.
- **PROPOSED** — гейт «импульс не хуже, чем его отсутствие»: ни один замер в репозитории не сравнивает эффект импульса с эффектом его не-выстрела. Evidence: `docs-v2/research/protocols/H-039.md:1-12` — протокол измеряет точность триггера, а не выигрыш от выстрела.

## Реализация в коде

| класс | пакет | статус | доказательство |
|---|---|---|---|
| `AutonomyImpulse` | `io.matrix.lifecycle` | **IMPLEMENTED** | `matrix-core/src/main/java/io/matrix/lifecycle/AutonomyImpulse.java:11-19` |
| `ImpulseScheduler` | `io.matrix.lifecycle` | **IMPLEMENTED**, **TESTED** | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:22`; тест `matrix-core/src/test/java/io/matrix/lifecycle/ImpulseSchedulerTest.java` |
| `AutonomyEngine` | `io.matrix.autonomy` | **IMPLEMENTED**, **REACHABLE**, **TESTED** (дефект I6 закрыт в W47) | `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:21`; вызывается из `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:38`; тест `matrix-core/src/test/java/io/matrix/autonomy/AutonomyEngineTest.java` |
| `SelfImprovingEngine` | `io.matrix.autonomy` | **IMPLEMENTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/autonomy/SelfImprovingEngine.java:31`; единственный создатель — `matrix-api-gateway/src/main/java/io/matrix/api/llm_quarantine/SelfImprovingEngineLauncher.java:42` |
| `AutonomyLoop` | `io.matrix.brain.runtime` | **IMPLEMENTED**, **SERVED** (chain B, production-режим) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:37`; создаётся и запускается в `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:208`, `:210` |
| `GoalTracker` | `io.matrix.goals` | **IMPLEMENTED**, **SERVED** (chain B) | `matrix-core/src/main/java/io/matrix/goals/GoalTracker.java:14`, `:51`; маршруты `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:395-396`, `:1563-1569`, `:1598-1609` |
| `GoalManager` | `io.matrix.knowledge` | **IMPLEMENTED**, **SERVED** (chain A) | `matrix-core/src/main/java/io/matrix/knowledge/GoalManager.java:26`; маршрут `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:627-629` → `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1994` |
| `InstanceMediator` + `DriverState` | `io.matrix.mediator` | **IMPLEMENTED**, **TESTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/mediator/InstanceMediator.java:23`; `matrix-core/src/main/java/io/matrix/mediator/DriverState.java:12`; создаётся только в `matrix-core/src/main/java/io/matrix/SystemDemo.java` |
| `ClusterMediator` + `LobeMediator` | `io.matrix.mediator.hierarchy` | **IMPLEMENTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/mediator/hierarchy/`; только `matrix-core/src/main/java/io/matrix/SystemDemo.java:24-25` |
| `Impulse` (второй тип) | `io.matrix.consciousness` | **IMPLEMENTED**, **TESTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/consciousness/Impulse.java:18`; `AttentionRouter.merge` вызывается с **пустым** списком импульсов в `matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java:162`, `matrix-core/src/main/java/io/matrix/consciousness/BrainLoopService.java:85`, `matrix-api-gateway/src/main/java/io/matrix/api/llm_quarantine/LlmBrainLoopService.java:120` |
| `AttentionRouter` | `io.matrix.consciousness` | **IMPLEMENTED**, **TESTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/consciousness/AttentionRouter.java:21`, `:29`, `:54`; единственный вызов с непустым списком — `matrix-core/src/main/java/io/matrix/consciousness/BrainLoopServiceV2.java:68`, у которого **нет** production-вызывающего |
| `ShareImpulseFirer` | `io.matrix.federation` | **IMPLEMENTED**, **TESTED**, **UNREACHABLE** | `matrix-core/src/main/java/io/matrix/federation/ShareImpulseFirer.java:22` — файл сам объявляет себя автономной синтетической реализацией (`:12-21`) |
| `NeuroModulatorHub` | `io.matrix.brain.runtime.graph` | **IMPLEMENTED**, **SERVED** (chain B) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/NeuroModulatorHub.java:31`; через `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:128` |
| `RegulationController` | `io.matrix.cognitive.neuromodulation` | **IMPLEMENTED**, **TESTED**, **SERVED** (chain A) | `matrix-core/src/main/java/io/matrix/cognitive/neuromodulation/RegulationController.java`; `matrix-core/src/main/java/io/matrix/cognitive/KernelRegulator.java:27`, `:35`; `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:258`; маршруты `matrix-core/src/main/java/io/matrix/cognitive/CognitiveResource.java:34`, `:73`, `:125` |

**Ни один из четырёх импульсов сегодня не SERVED.** `ImpulseScheduler` создаётся только в
карантинном пакете `matrix-api-gateway/src/main/java/io/matrix/api/llm_quarantine/` — строки
`:53`, `:35`, `:57`, `:42` четырёх файлов; ни один из них не смонтирован в маршрутизатор
(`matrix-core/src/main/java/io/matrix/api/CanonicalEntryPoint.java:123` фиксирует, что
`BrainHttpServer` из этого пакета **не смонтирован**).

> **[W48 correction — 2026-11-06] The paragraph above is the PRE-W47 state and is no longer
> true. The scheduler IS built on a served path — and still nothing consumes what it
> accepts.**
>
> | | then (pre-W47) | now (verified 2026-11-06) |
> |---|---|---|
> | where the `ImpulseScheduler` is built | only in the unmounted quarantine package | **also** in chain B's served loop: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:57` — `new ImpulseScheduler(new ConjugateBudgeter(), new EthicalFilter())` — and the engine is built with it at `:58` |
> | is that loop served | no | **yes** — the gateway creates and starts it in production mode: `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:208`, `:210` (in stub mode it is deliberately skipped, `:214`) |
> | are the drives reached | no — the null scheduler killed the curiosity cycle and the integrity cycle never ran | **yes** — `CURIOSITY` every 120 s and `INTEGRITY_CHECK` every 300 s go through the real scheduler (`matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:75`, `:82`) |
> | is the accepted impulse consumed | — | **NO.** The `FireRecord` is read in one place only (`AutonomyEngine.java:141-145`), where a refusal is logged and a `FIRED` record is dropped. **The impulse is ACCEPTED; nothing CONSUMES it.** |
>
> **So the honest sentence is not "no impulse is served" and not "the curiosity cycle
> performs work". It is: the drives are expressed and paid for on a served path, and the
> result of that expression is a decision in a log — not a unit of autonomous work.**

## Связь с существующим

- **[SPEC-005](./SPEC-005-action.md)** — **гейт автономных импульсов живёт сейчас именно там**, в
  § «Гейты автономных импульсов» (`docs-v2/specifications/SPEC-005-action.md:49-62`), и там же
  определено, что автономный импульс обязан пройти `FrozenEthicalFNL` + `StructuralSafetyGuard` +
  `FROZENFNLGuardian` (`docs-v2/specifications/SPEC-005-action.md:30`). **Этот гейт по предмету
  принадлежит SPEC-015**; до переноса он остаётся нормативным в SPEC-005. Перенос — работа
  координатора, эта спецификация его не выполняет.
- **[SPEC-007](./SPEC-007-subconscious.md)** — **спецификация фонового слоя, версия B.** Она
  называет четыре класса импульсов `CuriosityImpulse`, `IntegrityImpulse`, `ShareImpulse`,
  `ConsolidationImpulse` в пакете `subconscious/` (`docs-v2/specifications/SPEC-007-subconscious.md:32-33`)
  и конвейер `Impulse → AttentionRouter → ActionGate` (`:5`, `:20-23`). **Ни пакета
  `io.matrix.subconscious`, ни этих четырёх классов, ни тестов `SubconsciousDaemonTest`,
  `DreamReplayTest`, `ImpulseGeneratorTest`, `SubconsciousBypassGuardTest` в дереве нет** —
  проверено поиском по всему репозиторию. Существует только `PredictionModel`, но он лежит в
  `io.matrix.consciousness` (`matrix-core/src/main/java/io/matrix/consciousness/PredictionModel.java`),
  а не в `subconscious/`. **Предмет расходится**: SPEC-007 описывает фон целиком (предсказание
  мира, dream-replay, TR/REM, gossip), а не импульсы; имена в ней не совпадают с
  реализацией (`CURIOSITY / CONSOLIDATION / INTEGRITY_CHECK / SHARE_DIGEST`).
  **Предлагаемый преемник для предмета «импульсы» — SPEC-015 (этот документ); сам SPEC-007
  не редактируется здесь** — его статус меняет координатор.
- **[SPEC-009](./SPEC-009-mediator-hierarchy.md)** — иерархия медиатора и `GoldenRatioAllocator`
  (`docs-v2/specifications/SPEC-009-mediator-hierarchy.md:30`, `:47`, `:62`) — это **драйверы**
  (`D_curiosity` и подобные), из которых SPEC-005:30 выводит автономные импульсы. Классы
  медиатора сегодня **UNREACHABLE** (см. § «Реализация в коде»).
- **[DESIGN-11](../designs/DESIGN-11-budgeter.md)** — нормативная спецификация `ConjugateBudgeter`,
  к которому SPEC-015 обращается за бюджетом.
- **[DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)** — FNL-реестр и TaskCell: карантин новых
  артефактов, из которого подсознание (по SPEC-007:52) может писать только SHADOW.
- **[AR](../architecture/REQUEST-autonomy-impulses.md)** — директива, которую эта спецификация
  делает нормативной. Именно её строка 5 носила имя `SPEC-008-autonomy-impulses` с пометкой
  «спецификации этого предмета не существует» — **эта спецификация закрывает ту ссылку**.
- **Реестр возможностей** — `docs-v2/architecture/CAPABILITY-REGISTRY.yml` не содержит строки про
  автономные импульсы; ближайшая по теме — `W44-GOALMGR` (`docs-v2/architecture/CAPABILITY-REGISTRY.yml:760`),
  которая объявляет `GoalManager` **SERVED** через chain A. Строку про импульсы должен завести
  координатор; этот документ его не правит.

## Связь с тестами

**Test scope (точно) — без Gradle-прогона, если не разрешён отдельно:**

```bash
./gradlew :matrix-core:test --tests "io.matrix.lifecycle.*"
```

Классы тестов, покрывающие предмет:

| класс | что покрывает |
|---|---|
| `matrix-core/src/test/java/io/matrix/lifecycle/ImpulseSchedulerTest.java` | выстрел, отклонение по бюджету, отклонение по этике, закрытость набора |
| `matrix-core/src/test/java/io/matrix/goals/GoalTrackerTest.java` | `GoalTracker` — цели автономного цикла (chain B) |
| `matrix-core/src/test/java/io/matrix/goals/Exp216GoalTrackerTest.java` | тот же класс под экспериментальным протоколом |
| `matrix-core/src/test/java/io/matrix/knowledge/GoalPersistenceTest.java` | `GoalManager` — персистентность целей (chain A) |
| `matrix-core/src/test/java/io/matrix/consciousness/AttentionRouterTest.java` | слияние импульса и saliency — второй, неиспользуемый тип импульса |
| `matrix-core/src/test/java/io/matrix/consciousness/ImpulseTest.java` | второй, неиспользуемый тип импульса |
| `matrix-core/src/test/java/io/matrix/mediator/DriverStateTest.java` | гомеостаз драйверов — источник импульсов по замыслу |
| `matrix-core/src/test/java/io/matrix/mediator/InstanceMediatorTest.java` | цикл медиатора |
| `matrix-api-gateway/src/test/java/io/matrix/api/AutonomyLoopWiringTest.java` | проводка `AutonomyLoop` в гейтвейере — **покрытия дефекта I6 в нём нет** |

**Чего в тестах нет:** ни один тест не проверяет, что `ImpulseScheduler` не равен `null` на пути
`AutonomyLoop` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:38`),
и ни один не проверяет запись выстрела в журнал аудита.

## Отложено

- **[W48 note — 2026-11-06] APPROVED, NOT IMPLEMENTED — the impulse consumer, and the ADR
  that scopes it.** The subject is the gap between **ACCEPTED** and **CONSUMED**: what should
  happen to an impulse the scheduler accepted. The ADR is
  `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` <!-- no-id: the ADR declares this identifier in its own heading; this line references the document, it does not define it --> —
  **approved by the owner on 2026-11-06 (OD-045, option A), NOT IMPLEMENTED.** Its scope is
  a proposal-creating consumer: it turns an accepted impulse into a bounded internal
  proposal, records it inside Chain B, and produces **no external effect**. **No code of
  that ADR exists**, and this specification does not treat it as implemented. **Until the
  next wave writes it, the truth is unchanged: the scheduler accepts impulses, nothing
  consumes them.**

- **IMPLEMENTED** — дефект **I6** (null-планировщик в `AutonomyLoop`) **исправлен в W47** по решению
  владельца OD-038: цикл строит настоящий планировщик, а конструктор двигателя отвергает null.
  Evidence: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/AutonomyLoop.java:56-58`;
  `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:54`.
- **PROPOSED** — запись выстрела в `audit/HashChain` (**I7**). Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:99`.
- **PROPOSED** — очередь отложенных импульсов (**I8**). Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:84-90`.
- **PROPOSED** — шаги FROZEN-конвейера `StructuralSafetyGuard` и `LieDetector` в пути импульса.
  Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:94-99`.
- **PROPOSED** — таблица триггеров (idle, drift, локальный M2 Δ) в коде.
  Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:13-18`.
- **PROPOSED** — TLA+-контракт `BRC-Step` для конвейера импульса. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:115`.
- **PROPOSED** — семантические constraint-predicate-плагины для AC-3 в импульсах. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:115`.
- **PROPOSED** — **H-014** <!-- no-id: the register marks H-014 "a family prefix, not an identifier" (Q-W46-19) --> не является идентификатором: реестр помечает его как семейный
  префикс, а не как объявленную гипотезу. Evidence: `docs-v2/research/HYPOTHESES.md:36`.
- **PROPOSED** — указатель реализации у **H-046** ведёт на несуществующий класс
  `ImpulseGenerator` из SPEC-007; его надо перенаправить на
  `io.matrix.lifecycle.ImpulseScheduler`. Evidence: `docs-v2/research/HYPOTHESES-NEW.md:19`;
  `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:66`.
- **PROPOSED** — сведение двух типов импульса (`io.matrix.consciousness.Impulse` и
  `io.matrix.lifecycle.AutonomyImpulse`) к одному. Evidence: `matrix-core/src/main/java/io/matrix/consciousness/Impulse.java:18`.
- **PROPOSED** — первоисточник по «intrinsic motivation» — сейчас **UNVERIFIED**, в репозитории его
  нет. Evidence: `docs-v2/architecture/REQUEST-autonomy-impulses.md:5`.
