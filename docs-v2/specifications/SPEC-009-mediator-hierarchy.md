# SPEC-009 — Mediator Hierarchy (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.mediator`
**Related**: [SPEC-006](./SPEC-006-consciousness-deliberation.md), [DESIGN-02](../designs/DESIGN-02-viewpoint.md)

## Что

`mediator/` — пакет согласования разнородных импульсов
(подсознание, этика, планировщик) с бюджетером ([SPEC-006](./SPEC-006-consciousness-deliberation.md)).
Носитель иерархии целей и распределения ресурсов через
golden-ratio аллокатор.

## Архитектура

```
InstanceMediator            — корневой координатор
 ├── DriverState (per DriverType)
 │    ├── ENERGY, COMPUTE, ATTENTION, ETHICS, EXPLORATION
 │    └── current / target / pressure
 ├── Goal / GoalStatus      — иерархия целей
 ├── Task                   — единица планирования
 ├── GoldenRatioAllocator   — φ-распределение бюджета
 ├── MetaGoalValidator      — проверка целей на этическую допустимость
 └── mediator.hierarchy/    — поддержка многоуровневой вложенности
      ├── MediatorNode
      └── MediatorHierarchy
```

## FR (классы и интерфейсы)

- `mediator/InstanceMediator` — корневой координатор. Конструктор
  `(MediatorConfig, MatrixMetrics?, Random)`. Делегирует в
  `GoldenRatioAllocator` и `MetaGoalValidator`.
- `mediator/DriverState` — состояние одного драйвера (current/target/pressure).
- `mediator/DriverType` — enum: `ENERGY`, `COMPUTE`, `ATTENTION`, `ETHICS`, `EXPLORATION`.
- `mediator/Goal` — цель с приоритетом, дедлайном, parent-ссылкой.
- `mediator/GoalStatus` — `PENDING`, `ACTIVE`, `SUSPENDED`, `DONE`, `FAILED`.
- `mediator/Task` — единица планирования, обёрнутая вокруг `lifecycle/TaskCell`.
- `mediator/GoldenRatioAllocator` — φ-распределение (φ ≈ 1.618) — большой
  драйвер получает φ× малого. Детерминированно, без random.
- `mediator/MetaGoalValidator` — пропускает цели через этический гейт
  ([SPEC-006](./SPEC-006-consciousness-deliberation.md)).
- `mediator/hierarchy/` — поддержка деревьев `InstanceMediator`-ов
  (parent/child relationships).

## Инварианты

1. **Детерминизм (CONSTITUTION I)**: `GoldenRatioAllocator.allocate(drivers, total)`
   — чистая функция. Random допустим только как seed для tie-break
   и не используется в decision path.
2. **ФРОЗЕН-гейт (CONSTITUTION III)**: `MetaGoalValidator` обязан
   пропустить любую цель через `ethics/frozen/FROZENFNLGuardian`
   перед её активацией. `Verdict.deny` → `GoalStatus.SUSPENDED`,
   невозможно перейти в `ACTIVE` без override.
3. **K_MAX=20 (CONSTITUTION II)**: каждый Goal компилируется через
   `bir/BirCompiler` в boolean-форму. Сложность цели ≤ 2^20 строк.
4. **Coverage gate ≥82%** на всех mediator-классах (CONSTITUTION V).
5. **Иерархия**: `MediatorHierarchy` — дерево (не граф). Cycle при
   `addChild` → `IllegalStateException("mediator_cycle")`.

## GoldenRatioAllocator

Распределение `total` бюджета по драйверам с весами `weights`:

```
driver.share = weight / sum(weights)
```

Если веса сильно различаются (|max/min| > φ²), аллокатор
масштабирует больший драйвер на множитель φ, чтобы избежать
монополизации. Алгоритм:

```
factor = 1.0
for each driver (sorted by weight desc):
  share = weight * factor
  if share > total * phi_inverse:
    factor *= phi_inverse
  allocations[driver] = total * (share / sum(share))
```

Полностью детерминирован (CONSTITUTION I).

## Связь с существующим

- `brain/BrainPipeline` ([DESIGN-13](../designs/DESIGN-13-brain-pipeline.md))
  использует `InstanceMediator` как coordination layer.
- `lifecycle/ConsolidationCycle` ([DESIGN-07](../designs/DESIGN-07-consolidation-cycle.md))
  потребляет драйвер `COMPUTE` от медиатора.
- `ethics/EthicalFilter` → `FROZENFNLGuardian` ([SPEC-006](./SPEC-006-consciousness-deliberation.md))
  используется `MetaGoalValidator`.
- `reasoning/BrcChain` ([SPEC-008](./SPEC-008-reasoning-brcchain.md))
  использует `attention` драйвер для top-down bias.

## Связь с тестами

`InstanceMediatorTest` (18 тестов), `GoldenRatioAllocatorTest`
(11 тестов), `MetaGoalValidatorTest` (8 тестов), `MediatorHierarchyTest`
(14 тестов). Total 51 тест, coverage ≥82%.

## TLA+ формализация

`ConjugateBudgeter-DP` ([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)) —
следующий контракт для формализации (RUN 14). Иерархия медиаторов
требует state-машины с parent-link и reachability-check.
