# REQUEST-autonomy-impulses — побуждающая активность

**Статус: normative · singleton AR** · changelog `2026-08-26 — brain wave v1`.

## Что это

AR-документ: директивы по внутренним драйверам, производящим действие **без внешнего сигнала**. Это «intrinsic motivation» в терминах vision GOALS-REQUIREMENTS §FR-09, согласованная с NFR-10 (DEGRADED-режим). Конкретика — [SPEC-008-autonomy-impulses](../specifications/SPEC-008-autonomy-impulses.md) (placeholder). Существующие нормативы: [SPEC-000-devloop](../specifications/SPEC-000-developmental-loop.md), [DESIGN-07-lifecycle](../designs/DESIGN-07-lifecycle.md), [DESIGN-11-budgeter](../designs/DESIGN-11-budgeter.md), [DESIGN-12-taskcell-fnl](../designs/DESIGN-12-taskcell-fnl.md).

## Каноническое запрещение

**FROZEN Ethical Core (CONSTITUTION IV) не обходится ни одним импульсом.** Любая активность, порождённая impulse, проходит `ethics/EthicalFilter → StructuralSafetyGuard → LieDetector` так же, как реактивный ответ на пользовательский запрос. Импульсы — **не привилегия**: они подчиняются тем же гейтам, что и пользовательские действия.

## Четыре импульса

| # | Импульс | Триггер | Действие | Бюджет |
|---|---|---|---|---|
| 1 | **curiosity** | ZPD-полоса свободна (SPEC-000) | выбор сценария из `devloop/CurriculumEngine.nextScenario` | devloop-бюджет |
| 2 | **consolidation** | idle ≥ τ_consolidate, route-drainable | sleep-cycle: replay M1 → сжатие в M2/M3 → forgetting сырья | wall-time + CPU |
| 3 | **integrity-check** | T (период) или drift > κ | lineage-аудит, Ricci-fingerprint diff (SPEC-003), эвикция подозрительного | CPU + память |
| 4 | **share-digest** | local M2 Δ > порог | Anonymizer → ELSP → noosphere M5 | network + DP-budget |

Каждый импульс — детерминированный (fixed seed), бюджетированный (`budgeter/ConjugateBudgeter`, DESIGN-11), и регистрируется в `audit/HashChain`.

## Цикл импульса

```
                  ┌──────────────────────────────────────┐
                  │  scheduler tick (deterministic)      │
                  └────────────────┬─────────────────────┘
                                   ▼
                       ┌──────────────────────┐
                       │  collect triggers    │
                       │  (curiosity/cons/    │
                       │   integrity/share)   │
                       └──────────┬───────────┘
                                  ▼
              ┌─────────────────────────────────────┐
              │  ConjugateBudgeter.allocate(rows, E)│
              │  → Allocation(mode, selected[],     │
              │                spent, shadowPrice) │
              └──────────────┬──────────────────────┘
                             ▼
        ┌────────────────────────────────────────────────┐
        │  per selected impulse:                         │
        │   1. emit TaskCell (DESIGN-12) с бюджетом     │
        │   2. action → ethics-gate → FROZEN-check      │
        │   3. исполнение → append audit/HashChain       │
        │   4. на завершении: emit side-effects          │
        └──────────────┬─────────────────────────────────┘
                       ▼
              ┌──────────────────────┐
              │  update world-model  │
              │  emit metrics        │
              └──────────────────────┘
```

## 1. Curiosity (исследование компетенций)

- **Источник**: SPEC-000 `devloop/CompetenceAssessor` (EWMA α=0.3) + `CurriculumEngine.nextScenario(competence, catalog)` → lowest-id scenario в ZPD-полосе.
- **Назначение**: держать систему в режиме непрерывного развития (CONSTITUTION III); избежать «stuck at MA-N» без прогресса.
- **Граница**: curiosity **не выбирает** сценарии за пределами maturity MA-N+1 — это решает `MaturityGateKeeper`.
- **Связь с FR-09** (vision GOALS-REQUIREMENTS): побуждающая активность без вызова пользователя.

## 2. Consolidation (sleep-cycle)

- **Источник**: DESIGN-07 `lifecycle/ConsolidationCycle` + DESIGN-05 §sleep.
- **Назначение**: реплей M1 → MDL-сжатие в M2/M3 → удаление сырья. Φ-гейт (Ванник SRM + Колмогоров/MDL, см. science/FOUNDATIONS.md) — promote только при уменьшении суммарной длины описания.
- **Когда**: idle ≥ τ_consolidate, либо явный сигнал оператора, либо DRIFT-triggered.
- **Маршрут-drain**: на время consolidation кластер снимается с трафика (DESIGN-07 §sleep); восстановление — детерминированное.
- **Связь с vision ARCHITECTURE §3.3** («гиппокамп→неокортекс»).

## 3. Integrity-check (аудит целостности)

- **Источник**: `audit/HashChain` + `ktopo/` (SPEC-003) + lineage-проверка (`bir/LineageLedger`).
- **Триггеры**: (а) периодический T (например, каждый N операций); (б) drift > κ по Ricci-fingerprint (Wasserstein-1 порог); (в) явный запрос оператора.
- **Действия**: обход M2/M3; сравнение hash-цепи; пометка подозрительных узлов; автоматический demote через FNL-гейт при нарушении Φ.
- **Связь с FR-07** (vision): Cauldron-протокол — promote/demote через Φ.

## 4. Share-digest (анонимный вклад в noosphere)

- **Источник**: локальный diff M2 ≥ порога, или явный запрос пользователя «поделиться».
- **Действия**: агрегация → `federation/Anonymizer` (k-anonymous + DP-noise) → ELSP-подпись → gossip в noosphere M5.
- **Граница**: digests **никогда** не содержат идентифицируемых payloads; budget DP-ε ограничен и аудируется.
- **Связь**: [REQUEST-decentralized-digests](REQUEST-decentralized-digests.md).

## Бюджетирование (DESIGN-11)

- Импульсы подаются как `Row(id, value, cost)` в `ConjugateBudgeter.allocate(envelope)`.
- CONJUGATE-mode: точный 0/1-DP, `units = cap / gcd_of_costs`, grid H_MAX=250k.
- FALLBACK_LEVIN_PROPORTIONAL: при `envelope < min_cost` или `units > H_MAX`.
- Shadow-price λ = V(U) − V(U−1) — конечная разность DP; bounded диапазон.
- **Дефицит ресурсов** — FALLBACK; не starvation. Любой импульс может быть отложен, но не потерян.

## FROZEN-защита

Каждый импульс обязан включать в свой pipeline:

1. **EthicalFilter** — negative selection + логический вывод вреда.
2. **StructuralSafetyGuard** — BDD-эквивалентность + SAT/SMT проверка.
3. **LieDetector** — `FrozenEthicalFNL` проверка против известных запретов.
4. **Audit append** — `audit/HashChain` запись с trigger-id.

Любой импульс, обнаруживший нарушение FROZEN, переходит в `integrity-check` режим с немедленным demote всех связанных артефактов. **Нет** сценария, в котором импульс «знает лучше» этики.

## Детерминизм и наблюдаемость

- Tick-scheduler детерминирован (DESIGN-07 §sleep-cycle integration); random/wall-clock вне триггеров.
- Любой импульс emit-ит метрики в `events/KafkaEventJournal` (темизация по trigger-id).
- Side-effects — только через `actions/PlanRunner` (DESIGN-13), с Hoare-триплетом.
- Backpressure: при превышении бюджета — partial execution с audit, не silent drop.

## Открытые задачи

- H-005 (running): DevLoop сокращает solve-time ≥30% — preliminary positive на `LearnedMinecraftPilot`.
- H-014 (proposed): VC-оценка предсказывает размер окна Φ-гейта → планирование consolidation.
- TLA+ `BRC-Step` — закрытие пробела для impulse-pipeline (next-format-contract, FORMAL-CONTRACTS.md).
- Семантические constraint-predicate-плагины для AC-3 в импульсах (DESIGN-15, отложено).

## Чего этот AR НЕ утверждает

- Никаких «свободы воли», «воли», «желаний» — это запрещённые формулировки (CONSTITUTION VI).
- Никаких обещаний, что импульсы «улучшают» систему в абсолютном смысле — только что они **зарегистрированы и бюджетированы**, эффект измеряется через preregistered EXP.
- Никакого автоматического самомодифицирования FROZEN-артефактов (CONSTITUTION IV; требует RFC + консенсус).

Для исторической глубины см. `archive/2026-08-pre-v2/science/science/SUBSTRATE-MODELS.md` §8.1 (МГУА Ивахненко = правило остановки Cauldron) и §9.2 (VC-теория = составной Φ).