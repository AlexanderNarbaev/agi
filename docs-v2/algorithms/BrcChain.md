# BRC Chain (послойное булеvo рассуждение)

**Статус: normative** · changelog 2026-08-26 — brain wave v3 algorithms.

## Что

Императивный каркас многошагового булева рассуждения поверх BIR (SPEC-002): цепочка `BrcStep` применяет `NeuronLayer` к `BitSet`-вектору, проверяет сходимость по Хэммингу и останавливается. Источник: `matrix-core/src/main/java/io/matrix/reasoning/BrcChain.java` (плюс `BrcStep`, `BrcState`). Соответствует DESIGN-03 (Deliberation) и SPEC-000 (developmental loop).

## Контракт шага

Каждый `BrcStep` несёт `NeuronLayer`, имя и `convergenceThreshold`:

- `requiredInputWidth() = layer.outputWidth() * layer.k()` — слой ожидает упакованный вход.
- `padInput`: при недостаточной ширине вход периодически копируется (`i % srcLen`); при избыточной — обрезается до `requiredWidth`.
- `apply(state)` возвращает новый `BrcState` (immutable): `output = layer.evaluate(padded)`; флаг `converged = hammingDistance(prev, output) <= threshold` (для `threshold == 0` — точное равенство через `BitSet.equals`).

## Главный цикл

```
BrcChain.evaluate(input, vectorWidth):
  state = BrcState(input, vectorWidth)
  for step in steps:
    if maxSteps > 0 and stepCount >= maxSteps: break
    state = step.apply(state)
    stepCount += 1
    if earlyStopping and state.isConverged(): break
  return state
```

`evaluateDetailed` дополнительно накапливает состояния после каждого шага (для трассировки). `Builder` требует ≥1 шага; `maxSteps` клампится через `Math.max(0, _)`. Пустой список шагов на `build()` даёт `IllegalStateException`.

## Валидация выхода

С версии 3.24 — опциональный `SchemaDescriptor outputSchema`. После `evaluate(...)` вызывается `validateOutput(result)`:

- `outputSchema == null` ⇒ trivially `true`;
- иначе по каждой позиции `i ∈ [0, min(vector.length(), schema.k()))` валидируется `schema.validateOutput(bit, i)`. Нарушение ⇒ `SchemaViolationException` (если schema strict).

Это закрывает пробел `architecture/FORMAL-CONTRACTS.md` «BRC-Step contract — needs-spec» в части shape-инварианта на выходе цепи.

## Иммутабельность и детерминизм

`steps = List.copyOf(...)` — добавление/удаление шагов после `build()` невозможно. `BrcState` создаёт новый объект на каждом `next(...)`, включая копию `history`. Один и тот же `BitSet` на входе даёт идентичную последовательность состояний — пригодно для hash-chain аудита (см. одноимённый документ серии brain wave v3).

## Метрики / гейты

- Read-only: `stepCount()`, `maxSteps()`, `isEarlyStopping()`, `steps()`, `outputSchema()`.
- Юнит `BrcChainTest`: exact converge (`threshold=0`) vs approximate (`threshold=k`); respect `maxSteps`; `outputSchema` strict-mode бросает; `Builder.build()` пустой ⇒ `IllegalStateException`.
- Производительность линейна по числу шагов; на каждом шаге — `O(vectorWidth / 64)` `BitSet`-операций плюс `O(NeuronLayer.evaluate)` (см. SPEC-002 BIR-инварианты).

## Открытые вопросы

- Полная TLA+-спека `BRC-Step` (Hoare-триплет на шаге) — в `FORMAL-CONTRACTS.md` значится как next-format-contract; в этой серии не реализовано.
- Расширение `outputSchema` для небулевых слотов (int, enum) — отложено.

Next: см. файл ConversationProtocol.md в той же папке для следующей темы.
