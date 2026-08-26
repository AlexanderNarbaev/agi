**Статус: normative · draft** · пересмотр 2026-08-26 (brain wave v5 design drafts).

# Design-DRAFT — BrcChain Primitives

## Что

Формализация atomic preserved-step контракта для `reasoning/BrcChain`. Каждый шаг — тройка Pre/Effect/Post с монотонной предпосылкой и гарантией эквивалентного результата (т.е. композиция шагов ≈ одна суперцепочка).

## Блокеры

- TLA+-спек `BRC-Step` (next-format-contracts в `architecture/FORMAL-CONTRACTS.md`).
- ALGORITHM-ATLAS §38..§40 — chain preservation theorems.
- ALGORITHM-ATLAS-WAVE6 §38..§40 — provable-preservation proof sketches.

## Реализация (набросок)

```
io.matrix.reasoning:
  record BrcStep<S>(Predicate<Map<String,S>> pre, Effect<Map<String,S>> effect,
                    Predicate<Map<String,S>> post)
  BrcChain.compose(left, right): BrcChain  // preserves endpoints up to unused vars
  BrcChain.run(initialState): Stream<StepTrace>
```

## Метрики / Гейты EXP-019

- Пост-условия выполняются на simulated branches (deterministic random actions).
- Композиция двух цепочек ≤ вторая по late-indexу ⇒ бенчмарк-гейт (+10% cushion).
- Параллельная композиция (fork) эквивалентна последовательной до явного join.

## Отложено

Реальные агентные workload'ы; satisfies-полная логика (PRISMA-эквивалент) отложен.
