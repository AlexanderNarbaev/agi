# SPEC-006 — Consciousness / Deliberation (центральный процессор)

## Что

Центральный процессор сознания: BRC chain (`reasoning/BrcChain`) + MCTS/LATS поиск (`mcts/MctsTree`, `LatsNode`) + AC-3 preconditions (`actions/PlanPreprocessor`, [DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md)). Top-down внимание от autonomy-impulses (см. [SPEC-007](./SPEC-007-subconscious.md), [DESIGN-19](../designs/DESIGN-19-subconscious-consolidator.md)), bottom-up saliency от perception ([SPEC-004](./SPEC-004-perception.md)). FROZEN этический гейт (`ethics/EthicalFilter` → `StructuralSafetyGuard` → `LieDetector` → `frozen/FROZENFNLGuardian`) обязателен перед каждым action decision.

## Архитектура

```
perception ──► attention(merge) ──► deliberation(BRC + MCTS/LATS)
 │
 ▼
 AC-3 preprocess (DESIGN-15)
 │
 ▼
 FROZEN EthicalGate ◄── CONSTITUTION IV
 │
 ▼
 action (PlanRunner, DESIGN-17)
```

## FR (классы и интерфейсы)

- `consciousness/ConsciousLoop` — оркестратор цикла ([DESIGN-18](../designs/DESIGN-18-consciousness-loop.md)).
- `consciousness/AttentionRouter` — top-down × bottom-up merge; входы: `Set<Impulse>`, `Set<SaliencyEvent>`; выход: `Set<FocusItem>` (отсортировано по mergedScore детерминированно).
- `consciousness/DeliberationEngine` — обёртка `BrcChain` + `MctsTree`/`LatsNode` с конфигурируемым бюджетом.
- `consciousness/ActionGate` — каскад `AdversarialInputFilter` → `EthicalFilter` → `StructuralSafetyGuard` → `LieDetector` → `FROZENFNLGuardian`; возвращает `Verdict{allow, deny, transform}`.
- `consciousness/ConsciousTrace` — append-only журнал решений (`x-matrix-trace`).

## Инварианты

1. **Детерминизм**: `AttentionRouter.route(...)` и `DeliberationEngine.run(...)` дают фиксированный выход на фиксированных входах (CONSTITUTION I).
2. **FROZEN-гейт**: каждый `action` обязан пройти `ActionGate` перед `PlanRunner.execute`; пропуск — INV violation, проверяется unit-тестом.
3. **AC-3 preprocess**: пустой домен → `IllegalStateException("unsatisfiable_preconditions at step=…")` ([DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md)); fast-fail до gate.
4. **K_MAX=20** (CONSTITUTION II): все BRC-шаги компилируются через `bir/BirCompiler` в `TtForm`/`ClauseSetForm`/`BddForm` ([SPEC-002](./SPEC-002-boolean-compute-layer.md)).
5. **Coverage gate ≥82%** (CONSTITUTION V) на новых классах; без отлагательства «потестить позже» ([INVARIANTS](../engineering/INVARIANTS.md)).

## Этический гейт (FROZEN)

`ActionGate` вызывается **всегда** в порядке:
1. `ethics/AdversarialInputFilter` — pre-stage, reject injection.
2. `ethics/EthicalFilter` → `StructuralSafetyGuard` → `LieDetector` — каскад ([DESIGN-03](../designs/DESIGN-03-pipeline.md)).
3. `ethics/frozen/FROZENFNLGuardian` — единственный математически проверяемый носитель четырёх запретов (TLA+ `FrozenEthicalFNL`, [FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).
4. Если `Verdict.deny` — возврат в `ConsciousLoop` с пометкой `ethics_denied`, без `PlanRunner`.

FROZEN-зона (`matrix-core/.../ethics/frozen/**`) — CONSTITUTION III, модификация только через RFC.

## Budgeting

- `DeliberationEngine.run` принимает `Budget{tokens, wallMs, birEvals}` — все три детерминированных счётчика.
- Превышение бюджета → `BudgetExceededException` с типизированной причиной.
- `AttentionRouter` резервирует верхний N% бюджета для top-down импульсов (curiosity/integrity); настраивается.

## Связь с существующим

- `reasoning/BrcChain` ([DESIGN-03](../designs/DESIGN-03-pipeline.md)) — ядро делиберации; формальные свойства — TLA+ `BRC-Step` (next-format-contracts, [FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).
- `mcts/MctsTree`, `LatsNode` — поиск; convergence TLA+ `MCTS-LATS-Visit` отложено.
- `actions/PlanPreprocessor` — AC-3 fast-fail ([DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md)).
- `lifecycle/TaskCell` ([DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)) — носитель одного цикла сознания (бюджет CPU/mem/wall/ttl).
- `mediator/InstanceMediator` — согласование импульсов от подсознания с бюджетером ([DESIGN-02](../designs/DESIGN-02-viewpoint.md)).
- `PlanRunner` ([DESIGN-17](../designs/DESIGN-17-action-arena.md)) — моторный слой после gate.

## Тесты

- `ConsciousLoopTest` — детерминизм одного цикла (фиксированный seed).
- `AttentionRouterTest` — top-down приоритет при равенстве; merge устойчив.
- `ActionGateTest` — каждый из 4 уровней каскада; FROZEN-FNL mock недопустим (TLA+).
- `DeliberationEngineTest` — budget exceeded path; AC-3 fail-fast path.

## Метрики / гейты

- На не измерено; EXP-кандидаты — H-046, H-047 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Per-step JMH: `bir.BooleanRuntime.evaluate` (32–69M ops/s по [DESIGN-14](../designs/DESIGN-14-bir-migration.md)) как baseline per-BRC-step.

## Отложено

- TLA+ `BRC-Step` — next-format-contracts.
- TLA+ `MCTS-LATS-Visit` (convergence to α-Root) — next-format-contracts.
- Полный integration с `subconscious/` ([SPEC-007](./SPEC-007-subconscious.md)) — [DESIGN-18](../designs/DESIGN-18-consciousness-loop.md).

См. [architecture/MODULES.md](../architecture/MODULES.md), [research/HYPOTHESES.md](../research/HYPOTHESES.md), [CONSTITUTION.md](../../CONSTITUTION.md).