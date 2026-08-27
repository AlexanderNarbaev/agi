# SPEC-005 — Action Pillar (Motor Layer)

## Что

Моторный слой P-стороны brain-контура [DESIGN-03](../designs/DESIGN-03-pipeline.md) P-E-D pipeline. Преобразует решения BRC/BIR в исполняемые действия; иерархия effectors: low-level primitives → composite actions. Автономные импульсы проходят через `ethics/FROZEN` + structural-safety gate [DESIGN-14](../designs/DESIGN-14-bir-migration.md) (Критерий A: единственная точка исполнения — `BooleanRuntime.evaluate`).

## Мотивация

- Атомарность и контрактность: каждый шаг — Hoare-триплет `P{effect}Q` [DESIGN-13](../designs/DESIGN-13-action-registry.md).
- Детерминированная эволюция контрактов через `VersionedContract` (atomic swap по domainHash + consecutive version).
- AC-3 fast-fail предотвращает запуск неконсистентных планов [DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md).
- Изоляция автономных импульсов: ни одно действие не обходит этический гейт (CONSTITUTION III «Три запрета»).

## Архитектура

### Иерархия effectors

```
low-level primitives ← PlanRunner.Step (single-shot, атомарный)
 ↑
composite actions ← PlanRunner (multi-step, AC-3 validated)
 ↑
autonomous impulses ← FROZEN + StructuralSafetyGuard gate
 ↑
BIR / BRC decisions ← продукт deliberative слоя
```

- **Low-level primitives** — атомарные `Action` (DESIGN-13): запись в storage, отправка сообщения, вычисление значения. Каждый — единичный side-effect.
- **Composite actions** — `PlanRunner` + `PlanPreprocessor` (DESIGN-15): список `PlanStep` + AC-3 проверка доменов перед исполнением.
- **Autonomous impulses** — действия, инициируемые медиатором (драйверы D_curiosity, D_social и т.п.); обязаны пройти `ethics/frozen/FrozenEthicalFNL` + `StructuralSafetyGuard` + `FROZENFNLGuardian`.

### Реестр действий

`actions/ActionRegistry` (production):
- `record Action(name, IoSchema, preconditions, postconditions, executor)`.
- Resolve по имени; `ActionResult` (`success`/`failure`/`preconditionFailed`/`postconditionFailed`).
- Регистрация — через явный `register(Action)`; без ServiceLoader (DESIGN-06 R-семантика).

### Контракт шага плана

`PlanRunner.Step(name, precondition, postcondition, effect)` — атомарное исполнение:
1. Проверка `precondition` → иначе `precondition_violated`.
2. Исполнение `effect` через `Action.execute(input)`.
3. Проверка `postcondition` → иначе `postcondition_violated`.
4. `invariant_violated` если нарушен системный инвариант (K_MAX=20, FROZEN-целостность).

Своп версии: `actions/VersionedContract(name, version, domainHash)` — атомарная замена при совпадении `name+domainHash` и `next.version == version + 1`; иначе `IllegalArgumentException` (DESIGN-13).

### Гейты автономных импульсов

```
impulse → FrozenEthicalFNL ─┐
 ├→ approve → PlanRunner → effector
 FROZENFNLGuardian ─┤
 ├→ reject → log + halt
 StructuralSafetyGuard ─┘
```

- `FrozenEthicalFNL`: FROZEN-проверка шести аксиом (см. `ethics/frozen/`).
- `FROZENFNLGuardian`: гарантирует, что FROZEN-нейроны не мутированы.
- `StructuralSafetyGuard`: проверяет K_MAX=20, целостность контрактов, отсутствие запрещённых side-effect.
- Любой гейт может вернуть `EthicalVerdict.REJECTED` → действие блокируется + запись в audit.

## Метрики / гейты

- Юнит-тесты `PlanRunnerTest`, `PlanPreprocessorTest`: satisfied / failure modes (юнит + jqwik invariant preservation).
- AC-3: fail-fast на пустом домене (`IllegalStateException("unsatisfiable_preconditions at step=name")`).
- Атомарный своп: tampered `domainHash` → `IllegalArgumentException` (jqwik).
- Полный EXP-014 (BDD-бисимуляция версий) — после H-017.

## Реализация в коде

- `actions/{ActionRegistry, PlanRunner, PlanPreprocessor, VersionedContract}` — production.
- `agent/planning/Ac3Solver` — fast-fail ядро (DESIGN-15).
- `ethics/{EthicalFilter, StructuralSafetyGuard, FROZENFNLGuardian, LieDetector}` — гейт-цепочка.
- `audit/HashChain` — append-only журнал решений гейтов.
- Тесты: `actions/*`, `ethics/*`, `agent/planning/*` (юнит + jqwik).

## Отложено

- Семантические predicate-плагины для AC-3 (DESIGN-15).
- BDD-бисимуляция версий контрактов (DESIGN-13).
- Связь FNL-карантина [DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md) с автономными импульсами.
- Hard-real-time планирование для motor-примитивов (HW-bound).
- Двойной карантин IMPORT_M4 ↔ DESIGN-08 federation.

См. также [DESIGN-13](../designs/DESIGN-13-action-registry.md), [DESIGN-14](../designs/DESIGN-14-bir-migration.md), [DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md), [DESIGN-17](../designs/DESIGN-17-action-arena.md), [SPEC-002](./SPEC-002-boolean-compute-layer.md).