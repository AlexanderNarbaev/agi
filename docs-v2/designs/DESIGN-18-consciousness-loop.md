# DESIGN-18 — Consciousness Loop (петля сознания)

**Статус: normative** · пересмотр 2026-08-26 (brain wave v1) · changelog 2026-08-26 — brain wave v1.

## Что

Полный цикл: perception → attention → deliberation → gate → action → consolidation → subconscious → prediction-error → attention. Включает меры «бдительности» (arousal, saliency weights) и маршрутизацию working-memory (M2 ↔ M0/M1). Реализует [SPEC-006](../specifications/SPEC-006-consciousness-deliberation.md) и связывает [SPEC-007](../specifications/SPEC-007-subconscious.md) через `Impulse → AttentionRouter`.

## Цикл

```
        ┌──────► (subconscious → prediction-error) ──┐
        │                                              │
        ▼                                              │
   perception ──► attention(merge) ──► deliberation ──► gate ──► action
                     ▲   ▲                                   │
                     │   └── top-down (Impulse, SPEC-007)   │
                     └────── bottom-up (SaliencyEvent)     │
                                                            ▼
                                                    consolidation
                                                    (TR/REM, DESIGN-19)
```

Каждый шаг — детерминированная транзакция, журналируется в `x-matrix-trace` ([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).

## Компоненты

| Шаг | Класс(ы) | Бюджет | INV |
|---|---|---|---|
| perception | `signals/SignalModule` registry ([SPEC-004](../specifications/SPEC-004-perception.md)) | tokens | K_MAX=20 (BIR) |
| attention.merge | `consciousness/AttentionRouter` ([SPEC-006](../specifications/SPEC-006-consciousness-deliberation.md)) | O(impulses + saliency) | det |
| deliberation | `consciousness/DeliberationEngine` (BRC + MCTS/LATS) | tokens/wallMs/birEvals | det, AC-3 fail-fast |
| gate | `consciousness/ActionGate` (FROZEN-FNL) | O(1) | CONSTITUTION IV |
| action | `PlanRunner` ([DESIGN-17](../designs/DESIGN-17-action-arena.md)) | per-step budget | Hoare pre/post |
| consolidation | `lifecycle/ConsolidationCycle` + `subconscious/*` | batch | det |
| prediction-error | `subconscious/PredictionModel` | per-cycle | det (seed) |

## Бдительность (arousal & saliency)

- `arousal ∈ [0,1]`: текущий «уровень бдительности». Повышается при росте prediction-error, этических срабатываниях, novelty-сигнале. Снижается при стационарности цикла.
- `saliency_weights` — таблица весов по каналам восприятия (text/image/audio), обновляется через `ImpulseGenerator.IntegrityImpulse` (online).
- `arousal` управляет `DeliberationEngine.budget` (выше arousal → больше deliberation budget); см. H-046 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Монотонность `arousal` при strictly-increasing prediction-error stream — H-050 (HYPOTHESES-NEW).

## Working-memory routing (M0 ↔ M1 ↔ M2)

- `M0` (scratch) — in-process `BitSet` в текущем `TaskCell`; живёт только в бюджете ячейки ([DESIGN-05](../designs/DESIGN-05-memory.md)).
- `M1` (episodic) — append-only журнал эпизодов; promotion в M2 при consolidation (TR-phase).
- `M2` (semantic) — двухстадийный поиск: coarse (activation score) → fine (точный матч по схеме). Routing: `memory/HierarchicalMemory.recall(query, budget)`.
- На каждом цикле сознания: M0 → M1 (commit episode), M2 → M0 (load focus items).

## Цикл подсознания

Подсознательный `TaskCell` ([DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)) работает параллельно ([DESIGN-19](../designs/DESIGN-19-subconscious-consolidator.md)):
- TR-phase каждые N циклов сознания (N настраивается; default 10).
- REM-phase — после TR; prediction-error > δ → M3 candidate через `FnlGate` SHADOW.
- Impulse generation — непрерывно, с приоритетом.

Цикл подсознания никогда не блокирует цикл сознания: budget изолирован.

## Latency budget split

| Стадия | Целевой p99 (proposed H-047) |
|---|---|
| perception | < 5 ms |
| attention.merge | < 2 ms |
| deliberation | < 50 ms |
| gate | < 1 ms |
| action | < 10 ms (per step) |

Эти числа — proposed targets для EXP-grade измерений ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md) H-047, H-042). До замеров они не являются фактическими характеристиками системы.

## Связь с существующим

- `reasoning/BrcChain` ([DESIGN-03](../designs/DESIGN-03-pipeline.md)) — делиберация.
- `mcts/MctsTree`, `LatsNode` — поиск.
- `actions/PlanPreprocessor` — AC-3 ([DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md)).
- `ethics/frozen/FROZENFNLGuardian` — gate (CONSTITUTION IV).
- `lifecycle/ConsolidationCycle` + `subconscious/*` ([SPEC-007](../specifications/SPEC-007-subconscious.md)) — фоновый слой.
- `memory/HierarchicalMemory` ([DESIGN-05](../designs/DESIGN-05-memory.md)) — M0/M1/M2.
- `PlanRunner` ([DESIGN-17](../designs/DESIGN-17-action-arena.md)) — моторный слой.

## Тесты

- `ConsciousnessLoopIntegrationTest` — end-to-end один цикл с фиксированным seed.
- `ArousalDynamicsTest` — монотонность `arousal` при нарастающем prediction-error (jqwik).
- `WorkingMemoryRoutingTest` — детерминизм M2-recall; M0 → M1 commit; M1 → M2 promotion.

## Метрики / гейты

- На 2026-08-26 не измерено; см. H-046, H-047, H-050 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Emergence of behavior stability под повторными циклами — H-048.

## Отложено

- Полная интеграция с `brain/Viewpoint` ensemble ([DESIGN-02](../designs/DESIGN-02-viewpoint.md)).
- Online calibration arousal (через TsetlinTrainer offline) — research wave.
- Cross-pillar latency split — proposed H-047.
- Federated arousal sync (cross-instance) — BLOCKED-EXT.

См. [architecture/MODULES.md](../architecture/MODULES.md), [research/HYPOTHESES.md](../research/HYPOTHESES.md).
