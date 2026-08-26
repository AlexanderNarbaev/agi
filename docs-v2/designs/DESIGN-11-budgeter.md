# DESIGN-11 — Сопряжённый бюджетер (ConjugateBudgeter)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Два режима распределения энергии цикла Каулдрона:
- **CONJUGATE** — точный 0/1-DP на gcd-уменьшенных единицах (`units = cap / gcd_of_costs`, girded H_MAX=250k). Цель = max Σ vᵢxᵢ subject Σ cᵢxᵢ ≤ E. Теневые цены λ = V(U) − V(U−1).
- **FALLBACK_LEVIN_PROPORTIONAL** — при `envelope < min_cost` или крупной сетке (units > H_MAX) — fractional proportional пропуск, deferred к Levin-базе.

## Реализация

`budgeter/ConjugateBudgeter`:
- `Mode` enum, `Row` record(id, value, cost), `Allocation` record(mode, boolean[] selected, objective, spentEnvelope, shadowPrice).
- `allocate(rows, envelope)` → детерминированно.
- Тесты: 11 зелёных (`ConjugateBudgeterTest`): optimality, fallback, determinism, spent ≤ envelope, shadow price finite.

## Метрики / гейты

H-021 — preliminary DP-strict baseline реализован; лестница Понтрягина shadow-price через конечные разности DP. Полный JMH + EXP-021 запланированы.

## Отложено

- ТЛЕ-дифферинцирование shadow-price для нестационарных envelopes (когда V(want) скачет).
- Связь с квантованием RowState.
