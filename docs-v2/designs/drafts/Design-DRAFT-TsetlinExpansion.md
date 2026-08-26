**Статус: normative · draft** · пересмотр 2026-08-26 (brain wave v5 design drafts).

# Design-DRAFT — Tsetlin Expansion (TypeI/II, multi-clause tuning)

## Что

Расширение текущего TsetlinTrainer под будущие контролируемые эксперименты (H-016): полные цепи Ханселя, multi-label, weighted-счётчики с Γ(t)-tempered шумом.

## Цели (draft)

- Расширить `TsetlinTrainer` под configurable `s ∈ ℝ` (дробное) + температура `τ(t)` на эпоху `t`.
- Поддержка per-clause γ-усиления (тонкая настройка по литералам).
- API для выгрузки `TsetlinMachineState` (сериализуемый снимок с Σ-clause состояниями).
- Совместимость с текущим `toDecisionClauseSet` (ничего не сломать).

## Блокеры алгоритма

- ALGORITHM-ATLAS §25..§26 — теоретические границы для fractional `s`.
- ALGORITHM-ATLAS-WAVE4B §26..§28 — multi-clause stabilization proof.
- ALGORITHM-ATLAS-WAVE5B §35..§37 — Γ(t) tempering.

## Реализация (набросок)

```
io.matrix.tsetlin (расширение):
  TsetlinTrainer(inputBits, clauses, states, rng, initStrategy, tauSchedule)
  TsetlinMachineState.export() → byte[]; TsetlinTrainer.fromState(byte[])
  gammaTemperature(t): double[] | ndarray — задаётся вызывающим
```

## Метрики

- Эквивалентность классического (τ=1, s=4) результата на тех же входах.
- Качество vs скорость vs плотность артефакта (литералов на вход).
- Гейт EXP-019: accuracy ≥ базовый TsetlinTrainer ±2 п.п. при ≥10× меньших состояниях.

## Отложено

Полная TLA+-спека контракта fractional s; BLOCKED-EXT на реальные LLM-данные.
