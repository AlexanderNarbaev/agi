# SPEC-008 — Boolean Reasoning Chain (BRC) (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.reasoning`
**Related**: [SPEC-006](./SPEC-006-consciousness-deliberation.md), [DESIGN-03](../designs/DESIGN-03-pipeline.md), [FORMAL-CONTRACTS BRC-Step](../architecture/FORMAL-CONTRACTS.md)

## Что

`BrcChain` — нормативное ядро делиберации MATRIX: цепочка
`BrcStep`, каждый из которых применяет `NeuronLayer` к boolean-вектору
и проверяет конвергенцию. Точка входа для сознательной петли
([SPEC-006](./SPEC-006-consciousness-deliberation.md), [DESIGN-18](../designs/DESIGN-18-consciousness-loop.md))
и для одного шага MCTS/LATS-развёртки.

## Архитектура

```
BrcChain {
  steps: List<BrcStep>
  maxSteps: int             (0 = unlimited)
  earlyStopping: boolean
  outputSchema: SchemaDescriptor?
}

BrcStep {
  layer: NeuronLayer
  name: String
  convergenceThreshold: int (Hamming distance ≤ threshold → стоп)
}

BrcState {
  vector: BitSet
  stepIndex: int
  history: List<BitSet>
  converged: boolean
}
```

`BrcChain.evaluate(input, width)` применяет шаги последовательно.
`BrcChain.evaluateDetailed(input, width)` возвращает всю траекторию.

## FR (классы и интерфейсы)

- `reasoning/BrcChain` — неизменяемая (`List.copyOf`) цепочка шагов с
  конструктором `(steps, maxSteps, earlyStopping, outputSchema)`.
- `reasoning/BrcStep` — неизменяемый шаг, конструктор
  `(NeuronLayer, name, convergenceThreshold)`. Метод `apply(state)`
  возвращает новый `BrcState` (immutable).
- `reasoning/BrcState` — immutable состояние; конструктор `(input, width)`
  для начального состояния; package-private `next(vector, stepIndex, history, converged)`
  для переходов.
- `reasoning/FeedbackPerception` — `Supplier<BitSet>`-обёртка для
  feedback-loop: `lastAction()` записывает последний `BitSet`-decision,
  `get()` отдаёт его на следующем тике, чтобы сеть увидела свой выход.

## Инварианты

1. **Иммутабельность**: `BrcChain.steps` — `List.copyOf`; добавление
   шагов после конструктора невозможно. После сборки цепочка
   потокобезопасна для concurrent `evaluate`.
2. **Детерминизм (CONSTITUTION I)**: `BrcChain.evaluate(input, width)`
   — чистая функция; same input → same output, same history.
3. **Конвергенция**: при `outputSchema != null` цепочка останавливается
   на первом шаге, чей `BrcState.converged == true`.
4. **K_MAX=20 (CONSTITUTION II)**: каждый `NeuronLayer` компилируется
   через `bir/BirCompiler` в один из `TtForm`/`ClauseSetForm`/`BddForm`
   ([SPEC-002](./SPEC-002-boolean-compute-layer.md)). Размер таблицы
   истинности ≤ 2^K_MAX = 1 048 576 строк.
5. **Контракт слоёв**: `BrcStep.layer.outputWidth() * layer.k()` —
   это ширина, требуемая следующим шагом (`requiredInputWidth`).
   Слой должен принимать BitSet ровно этой длины; иначе
   `IllegalArgumentException("input_width_mismatch")`.

## FeedbackPerception

Особая форма perception ([SPEC-004](./SPEC-004-perception.md)),
используемая `BrainLoopService` ([RUN 12](../../.opencode/r12-plan.md))
и `ConsciousnessLoop` для self-feedback:

```
FeedbackPerception implements Supplier<BitSet> {
  get(): BitSet              // возвращает текущее восприятие
  lastAction(action: BitSet) // записывает выход loop-а для следующего тика
}
```

`ConsciousnessLoop.tick()` автоматически вызывает `lastAction(lastDecision)`
если `perception instanceof FeedbackPerception`. Это замыкает
attention-loop без внешнего состояния.

## Связь с существующим

- `brain/BrainPipeline` ([DESIGN-13](../designs/DESIGN-13-brain-pipeline.md))
  использует `BrcChain` как deliberative backbone.
- `mcts/MctsTree` — каждый MCTS-rollout использует короткую
  `BrcChain` (1-3 шага) для эмуляции действия.
- `lifecycle/ConsolidationCycle` ([DESIGN-07](../designs/DESIGN-07-consolidation-cycle.md))
  дренирует backlogs после `BrcChain.evaluate` в `ConsciousnessLoop`.
- `mediator/InstanceMediator` ([SPEC-009](./SPEC-009-mediator-hierarchy.md))
  согласует `BrcState.vector` с импульсами от подсознания
  ([SPEC-007](./SPEC-007-subconscious.md)).
- `agent/LongHorizonPlanner` использует `BrcChain.evaluateDetailed`
  для trace-back многошаговых планов.

## FR-уровень coverage

Покрытие `BrcChain`/`BrcStep`/`BrcState` ≥82% (CONSTITUTION V) —
тесты в `BrcChainTest` (24 теста), `BrcStepTest` (9 тестов),
`BrcStateTest` (7 тестов). Coverage gate в CI.

## TLA+ формализация

Спецификация `BRC-Step` ([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md))
формализует один шаг как state-transition: type-check, apply,
convergence-check. Next: 4 TLA+ specs (RUN 14).
