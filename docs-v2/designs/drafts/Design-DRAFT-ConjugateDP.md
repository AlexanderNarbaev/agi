
# Design-DRAFT — Conjugate Budgeter (DP + conjugate gradient/dual)

## Что

Расширить `budgeter/ConjugateBudgeter` (0/1-DP на gcd-единицах + shadow price λ) под асимптотически оптимальный многопериодный алгоритм через двойственные оценки. Основа — ТЛА-доказательство bounded-shadow-price (next-format-contract) и экспериментальная валидация на синтетических workloads.

## Цели

- H-021 (EXP-021): превосходство над greedy по ≤3% throughput за 100 поколений × 64 tasks.
- Shadow price λ ∈ [λ_lo, λ_hi] монотонно с накопленным объёмом.

## Блокеры

- ALGORITHM-ATLAS §33..§34 — ленивая двойственная оптимизация.
- ALGORITHM-ATLAS-WAVE5 §33..§34 — теорема Понтрягина для бэкетирования.
- TLA+-спека `ConjugateBudgeter-DP` (next-format-contracts).

## Реализация (набросок)

```
io.matrix.budgeter:
 record SchedulePlan(rows, horizon, kappaFn) — Lagrangian relaxation
 ConjugateBudgeter.step(epoch: int, observedΛ: double): BudgeterState
 boundedShadowPrice(): boolean — invariant test
```

## Метрики

- Ошибка LP-релаксации ≤ 5% от оффлайн-оптимума на синтетике.
- Shadow price trajectory: variance ≤ 10% от теоретической нормы.

## Отложено

Real workload калибровка.