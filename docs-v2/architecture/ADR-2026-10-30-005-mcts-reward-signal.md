# ADR-2026-10-30-005 — The production MCTS search has no reward signal

**Статус:** accepted · **Дата:** 2026-10-30 · **RECON-W39** · **Область:** `io.matrix.mcts`, `io.matrix.brain.runtime.stages.PlanningStage`

## Контекст

Owner directive §8 (Phase Five) asked for the MCTS/LATS algorithm to be
reviewed against the specification, for the production-caller status to be
verified, and for the algorithm to be benchmarked against alternatives.

The production-caller status was already recorded by the registry
(`formal/spec-implementation-map.yml`): `io.matrix.mcts.MctsTree` **is**
reached from production — `PlanningStage.plan()` → `tree.runSearch(...)` —
while `MCTSPlanner` and `LatsValueFunction` are not reached at all.

The review of the algorithm itself found something the registry could not
state, because the registry records what corresponds to what, not whether
the correspondence is *useful*.

## Находка

`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java:103-105`:

```java
MctsTree tree = new MctsTree(root, random, 4, budget.simulationDepth,
    1.4142135,  // sqrt(2) exploration constant (UCT default)
    (DecisionTree dt) -> 0.5);  // reward function: constant (deterministic)
```

**Награда постоянна.** Каждая симуляция приносит 0.5, независимо от того,
что было симулировано. UCB1 сравнивает дочерние узлы по среднему значению
награды; при постоянной награде средние значения равны, и правило выбора
сводится к **исследовательскому** члену — то есть к порядку обхода. Поиск
не может отличить хорошее действие от плохого, потому что у него нет
сигнала, который их различал бы.

`PlanningStage` — **единственный** production-вызывающий `MctsTree` в
репозитории (проверено: `grep -rn "MctsTree.builder\|new MctsTree" --include="*.java"`
по `matrix-brain-runtime/src/main`, `matrix-api-gateway/src/main`,
`matrix-core/src/main` даёт ровно одно попадание — сам `PlanningStage`).

Следовательно: **действующий в production MCTS-поиск не может улучшить
выбранное действие.** Он детерминирован (свойство, которое действительно
нужно — CONSTITUTION Article I), но не информативен.

### Измерение

JMH-бенчмарк `io.matrix.mcts.MctsAlternativesBenchmark` сравнивает три
варианта на одной задаче и одном бюджете:

| Метод | Время, мкс/оп | Средняя награда (20 задач) |
|---|---|---|
| `greedyOneStep` — один жадный шаг по всем действиям | 0.808 | 3.25 |
| `randomMutations` — те же мутации без правила выбора | 0.787 | 3.75 |
| `mctsUct` — производственный поиск (UCB1 + симуляция + backpropagation) | **18.197** | **2.60** |

Производственный поиск **в 23 раза дороже** жадного шага и на этой задаче
достигает **меньшей** награды. Причина видна в устройстве движка: симуляция
**случайна** (она оценивает случайную мутацию), поэтому даже при
информативной награде сигнал зашумлён; при постоянной награде сигнала нет
вовсе.

Замеры воспроизводимы: фиксированное семя, `--rerun-tasks
-PjmhBenchmark=MctsAlternativesBenchmark`. Побочный тест
`MctsAlternativesComparisonTest` фиксирует правила, которые действительно
верны: поиск воспроизводим при фиксированном семени, финальное действие —
самое посещаемое, бюджет ограничивает работу.

## Решение

### R1. Что MCTS в production делает, записано точно

`docs-v2/architecture/FORMAL-CONTRACTS.md` и реестр теперь говорят: MCTS в
production — это **детерминированный ограниченный стохастический поиск по
мутациям дерева решений** без различающего сигнала. Производственный след
(`engine=MctsTree.runSearch(...,selection=UCB1,latsMode=false)`) не врёт
про правило выбора, но и не врёт про смысл поиска — потому что смысла в
нём нет.

### R2. Никакая способность «MCTS планирует лучше жадного» не заявляется

Ни в одном документе репозитория такого утверждения теперь нет. Реестр
записывает для `MctsLatsVisit` статус `PRODUCTION_PATH_VERIFIED` — это
означает, что путь исполняется, а не то, что он полезен.

### R3. Награда не меняется молча

Замена постоянной награды на информативную — это **изменение того, что
выбирает мозг**, то есть изменение поведения production-пути. Оно требует
решения, которое не принимается в этом раунде:

- что считать целью в контексте стадии планирования (какой измеримый
  признак «хорошего» решения дерева существует в MATRIX?);
- как сохранить детерминизм (CONSTITUTION Article I), если награда зависит
  от состояния;
- как проверить, что изменение улучшает то, что заявлено.

Это отдельная задача, а не правка в этом раунде.

### R4. LATS остаётся выключенным, граница сходимости остаётся UNVERIFIED

Режим LATS в production не включается (`PlanningStage` использует
шестиаргументный конструктор `MctsTree`, который жёстко задаёт
`latsMode = false`), и он остаётся выключенным: включать его без
информативной награды и без замеров — значит менять поведение без
основания.

Утверждение о сходимости MCTS остаётся **UNVERIFIED**: это вероятностное
утверждение об асимптотическом распределении счётчиков посещений, которое
не проверяется конечной моделью, и его первоисточник в этом раунде не
перечитывался.

### R5. Замеры — часть репозитория

Бенчмарк и тест живут в репозитории, а не в отчёте: `./gradlew
:matrix-core:jmh --rerun-tasks -PjmhBenchmark=MctsAlternativesBenchmark`.

## Почему BFS/DFS/A* не являются альтернативами

Директива предлагала сравнить с BFS/DFS/A*. Здесь они неприменимы:
производственный движок **не ищет по графу**. Он выполняет ограниченный
стохастический поиск по **мутациям дерева решений**
(`MctsAction.apply`), а у этого пространства нет перечислимой структуры,
которой можно было бы воспользоваться исчерпывающему или наилучшему
поиску. Сравнивать с BFS на другой задаче — значит не сравнивать ничего.
Поэтому сравнение выполнено с **абляциями** производственного поиска:
правило выбора (UCB1) и бюджет.

## Связанные документы

- `formal/spec-implementation-map.yml` (MctsLatsVisit)
- `docs-v2/engineering/TLA-STATUS.md`
- `docs-v2/engineering/RISK-REGISTER.md`
- `matrix-core/src/jmh/java/io/matrix/mcts/MctsAlternativesBenchmark.java`
- `matrix-core/src/test/java/io/matrix/mcts/MctsAlternativesComparisonTest.java`
- `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java`
