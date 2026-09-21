
# Design-DRAFT — SDM-Recurrent (Kanerva)

## Что

Точный SDM/M0-M1 читатель через kanerva-style sparse distributed memory, поверх существующего `memory/SdmReader`. Расширение: рекуррентный readout (1-step прошлое подаётся на вход) для временного контекста.

## Цели

- Латентность чтения M1 ≤ 50 мс (≤ 64 KiB M1 footprint на одну ячейку).
- Точность top-K@5 ≥ базовый Hamming baseline (см. EXP-011 preregistration).
- Deterministic seed.

## Блокеры алгоритма

- ALGORITHM-ATLAS §41..§43 — SDM stabilization theorems.
- ALGORITHM-ATLAS-WAVE7 §41..§43 — recurrence-aware addressing.
- ALGORITHM-ATLAS-WAVE8 §44..§46 — capacity analysis.

## Реализация (набросок)

```
io.matrix.memory (расширение):
 SDMAddresser(hashFamily, sd, q).address(query) → BitSet
 SDMRead(addressSet).topK(5) → List<TraceId>
 RecurrentSDM(previousTraceId, currentQuery) → TopK + carry
```

## Метрики

- Калибровка `q` (MMR / temperature softmax) на офлайн-деме — нужны события (BLOCKED-EXT; см. `protocols/H-007-memory-stack.md`).
- Probe-time-to-onboard ≤ N секунд (cold-start gate).
- Инвариант: одинаковые `(sd, hashFamily, traces)` дают одинаковые читатели.

## Отложено

Реальный эпизодический трафик; BDD-codeset версия SDM.