# MCTS + LATS (Value/Reflection-augmented tree search)

## Что

Monte Carlo Tree Search поверх `DecisionTree`-состояний, расширенный LATS-режимом (arXiv:2310.04406): value-function вместо random simulation, self-reflection на провалившихся ветках, PUCT-селекция. Источник: `matrix-core/src/main/java/io/matrix/mcts/{MctsTree,MctsNode,LatsNode,LatsReflector,LatsValueFunction}.java`. Соответствует SPEC-002 (BIR) и DESIGN-03 (Deliberation).

## Классический MCTS

Четыре фазы на каждой итерации `runSearch(iterations)`:

1. **Selection**: от корня к листу по UCB1 (`bestChild(c)` с эксплорацией `C = √2`, см. `MctsNode.EXPLORATION_CONSTANT`).
2. **Expansion**: `expand(node)` создаёт нового потомка через `nextUntriedAction()` → `action.apply(state, rng, k)`.
3. **Simulation**: `simulate(node)` — `simulationDepth` случайных `MctsAction` через `rng.nextInt(actions.size())`, финальный reward = `rewardFunction.applyAsDouble(current)`.
4. **Backpropagation**: `backpropagate(node, reward)` — `update(reward)` по цепочке предков до корня.

Результат: `root.mostVisitedChild().action()`.

## LATS-расширения

В LATS-режиме (`latsMode == true`) три модификации:

1. **Value-function вместо simulation.** В `runSearch`: `reward = valueFunction.evaluate(expanded.state())`, затем `latsChild.setValueScore(reward)`. Интерфейс `LatsValueFunction<T>` — `@FunctionalInterface` с `evaluate(state) → [0, 1]`. Готовые реализации:
 - `RewardFunctionAdapter` — оборачивает `ToDoubleFunction<DecisionTree>`.
 - `HeuristicValueFunction(k, rng)` — структурный скор (`0.4·structural + 0.4·diversity − 0.2·complexity`); `diversity = 1 − 2·|ratio − 0.5|`, где `ratio = truthTable.cardinality() / 2^k`.
 - `CompositeValueFunction(functions, weights)` — взвешенная сумма (проверяет `Σw ≈ 1.0`).

2. **Prior + PUCT.** При `expand` создаётся `LatsNode` с `prior = clamp(valueFunction.evaluate(newState), 0.01, 1.0)`. `LatsNode.puct(c)`:

```
PUCT = meanReward + c · prior · √parentVisits / (1 + visitCount)
```

`visitCount == 0 ⇒ Double.MAX_VALUE` — гарантирует первое посещение. `bestLatsChild(c)` выбирает argmax.

3. **Self-reflection.** Каждые `reflectionEveryN` итераций `reflectOnWeakBranches()` обходит `root.children()` и для каждого `LatsNode` с `meanReward() < failureThreshold && visitCount() > 0` вызывает `reflector.reflect(latsChild, latsRoot)`. Дефолт `failureThreshold = 0.3`, `reflectionEveryN = 5`.

## LatsReflector

`reflect(node, parent)`:

- Если `reward < failureThreshold && visitCount() > 0` ⇒ `setStatus(FAILURE)` + `generateFailureReflection(...)`.
- Иначе при `visitCount() ≥ 3` ⇒ `setStatus(SUCCESS)` + `generateSuccessReflection(...)`.

Текст рефлексии собирается из трёх источников:

- custom `BiFunction<DecisionTree parent, DecisionTree current, String>` (если задан);
- `structuralAnalysis(tree)` — `depth/nodes/leaves/splits` через `TreeWalker`; эвристики "deep — consider pruning", "large — may be overfitting", "trivial — single leaf";
- последние ≤3 предковых рефлексий через `node.ancestorReflections()`.

Рефлексии хранятся в `HierarchicalMemory` на уровне `L1_PATTERN` с тегами `{tag, TAG_STRUCTURAL, "lats"}` и доменом `"mcts-lats"`. `retrieveRelevantReflections(query, limit)` и `retrieveRecentFailures(limit)` — извлечение для подсказки будущего поиска.

## LatsNode

Расширяет `MctsNode` пятью полями: `reflection` (String), `reflectionGenerated` (bool), `status` (`PENDING/SUCCESS/FAILURE/PRUNED`), `valueScore`, `prior`. Конструктор наследует `ancestorReflections` от родительской цепочки. `fullPathReflections()` возвращает список от корня к этому узлу включительно. `isExplorable()` = `status ∉ {PRUNED, FAILURE}` — гейт для селекции.

`setValueScore`/`setPrior` валидируют `[0, 1]` (иначе `IllegalArgumentException`).

## Детерминизм и стохастика

`Random rng` инжектируется — для воспроизводимости прогонов фиксируется seed. `simulationDepth`, `explorationConstant`, `failureThreshold`, `reflectionEveryN` — все параметры. `Builder.build()` требует `rootState/rng/rewardFunction`; `latsMode(valueFunction)` включает LATS и требует value-функцию.

`exportJson()` рекурсивно сериализует дерево в JSON (`mode`, `explorationConstant`, `simulationDepth`, плюс `action/visits/totalReward/meanReward/depth/valueScore/prior/status/hasReflection/reflection/children`).

## Метрики / гейты

- Юнит `MctsTreeTest` / `LatsNodeTest` / `LatsReflectorTest` / `LatsValueFunctionTest`: классический UCB1 выбор; LATS PUCT сходимость к `meanReward`; reflection при failure; composite weights должны суммироваться к 1.0.
- Контракт `MCTS-LATS-Visit` (конвергенция к α-Root) — значится как next-format-contract в `FORMAL-CONTRACTS.md`; полная TLC-проверка отложена.
- EXP-009 latency BIR (62 нс/eval CPU, ×149 vs ORT-CPU) — замерено для дистиллированного артефакта; MCTS-накладные расходы отдельно не замерены.

## Открытые вопросы

- Параллельный `runSearch` (multi-leaf selection, virtual threads) — отложено.
- Калибровка `prior` через настоящую `LatsValueFunction` поверх brain-а (сейчас `HeuristicValueFunction` для тестов) — отдельная задача.
- Merkle-hash дерева для аудита MCTS-прогонов (через hash-chain) — отложено.
