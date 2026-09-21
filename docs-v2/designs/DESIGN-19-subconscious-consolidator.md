# DESIGN-19 — Subconscious Consolidator (консолидатор подсознания)

## Что

Консолидатор подсознания: TR/REM фазы + Gossip (M3→M4 quorum) + генерация импульсов (curiosity/integrity/share/consolidation). Описывает, как именно работает `subconscious/SubconsciousDaemon` из [SPEC-007](../specifications/SPEC-007-subconscious.md). Связывает [DESIGN-07](../designs/DESIGN-07-lifecycle.md) (базовая sleep-функция) и [DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md) (FnlGate карантин M3).

## Архитектура

```
 ┌───────────────────────────────────────┐
 M1 episodes ─►│ SubconsciousDaemon (TaskCell) │
 │ │
 │ ┌─────────┐ ┌─────────┐ │
 │ │ TR-phase│──►│REM-phase│ │
 │ │ M1→M2 │ │ replay │ │
 │ └─────────┘ └────┬────┘ │
 │ │ prediction-err │
 │ ▼ │
 │ ┌──────────────┐ │
 │ │ M3 candidate │ │
 │ │ (FnlGate │ │
 │ │ SHADOW) │ │
 │ └──────┬───────┘ │
 │ │ IntegrityCheck │
 │ ▼ │
 │ ┌──────────────┐ │
 │ │ Gossip M3→M4 │ │
 │ │ (quorum R/W) │ │
 │ └──────┬───────┘ │
 │ │ │
 │ ▼ │
 │ ┌──────────────┐ │
 │ │ ImpulseGen │ │
 │ │ (4 classes) │ │
 │ └──────────────┘ │
 └───────────────────────────────────────┘
 │
 ▼
 consciousness/AttentionRouter (SPEC-006)
```

## Фазы

### TR (transfer) phase

- Вход: партия эпизодов M1 (`memory/HierarchicalMemory.query(episodes, window)`).
- Действие: кластеризация эпизодов по схеме `ioSchema`; promotion в M2 (semantic) при превышении порога консолидации (H-040, [HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Lineage: каждый M2-артефакт получает hash-chain в `audit/HashChain` через `LineageLedger` ([DESIGN-14](../designs/DESIGN-14-bir-migration.md)).
- Бюджет: tokens (по числу episodes), wallMs (per-batch).

### REM (replay) phase

- Вход: эпизоды M1 (выборка по детерминированному seed).
- Действие: offline replay через `subconscious/PredictionModel`; фиксация prediction-error.
- Если `predictionError > δ` (настраивается): эпизод помечается для M3 promotion (SHADOW).
- Если `predictionError ≤ δ`: эпизод может быть забыт (M1 → garbage).
- Бюджет: tokens (per-episode eval).

### Integrity + Gossip

- После REM: M3 candidates проходят `IntegrityCheck` (детерминированный: BDD-эквивалентность с lineage, см. [DESIGN-13](../designs/DESIGN-13-action-registry.md), [DESIGN-14](../designs/DESIGN-14-bir-migration.md)).
- Готовые M3-артефакты отправляются в `noosphere/MeshFederation` для gossip M3→M4 (quorum R/W, [DESIGN-08](../designs/DESIGN-08-federation.md)).
- Gossip неблокирующий; результат (accepted/rejected) приходит как `Impulse(Share)` следующего цикла.
- Анонимизация: k-anonymous + DP-noise через `federation/Anonymizer` (H-043).

### Impulse generation

- `CuriosityImpulse`: prediction-error > θ_c (high surprise); приоритет по confidence.
- `IntegrityImpulse`: M3 candidate failed integrity check; требует human/operator review (slow path).
- `ShareImpulse`: M3 quorum принят; предлагает conscious loop распространить через `api/*` (только через gate, [SPEC-006](../specifications/SPEC-006-consciousness-deliberation.md)).
- `ConsolidationImpulse`: фоновая загрузка; низкий приоритет; arousal-low режим.

## Цикл (псевдокод)

```
open CycleWindow
 read M1 backlog
 if backlog >= TR_THRESHOLD:
 run TR-phase(M1 → M2)
 for episode in sample(M1, REM_BATCH):
 run REM-phase(episode) // fills M3 candidates
 for candidate in M3 candidates:
 run IntegrityCheck(candidate)
 if pass: enqueue(Gossip, candidate)
 read Gossip results
 for result in Gossip results:
 enqueue(Impulse.Share or Impulse.Integrity)
 emit Impulses to conscious/AttentionRouter
close CycleWindow (DrainSummary)
```

## Связь с существующим

- **[DESIGN-07](../designs/DESIGN-07-lifecycle.md)** (`lifecycle/ConsolidationCycle`) — базовая sleep-функциональность; расширяется TR/REM фазами.
- **[DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)** (`lifecycle/FnlGate`, `TaskCell`) — карантин M3 кандидатов; подсознание пишет только SHADOW.
- **[DESIGN-08](../designs/DESIGN-08-federation.md)** (`noosphere/MeshFederation`) — gossip M3→M4; quorum R/W (TLA+ `Memory-M4-Causal` отложено).
- **[DESIGN-05](../designs/DESIGN-05-memory.md)** (`memory/HierarchicalMemory`) — M1/M2/M3 storage.
- **[DESIGN-11](../designs/DESIGN-11-budgeter.md)** (`budgeter/ConjugateBudgeter`) — бюджет TR/REM фаз (только через квоту, не вытесняет conscious).
- **[DESIGN-18](../designs/DESIGN-18-consciousness-loop.md)** — место интеграции в общий цикл.
- **[SPEC-007](../specifications/SPEC-007-subconscious.md)** — родительская спека.

## Тесты

- `SubconsciousConsolidatorTest` — TR → REM → Integrity → Gossip pipeline; детерминизм.
- `ConsolidationCycleTest` — window open/close; `DrainSummary` корректен.
- `M3PromotionGateTest` — promotion criteria (H-040 gate) unit + jqwik.
- `ImpulseGenerationTest` — четыре класса импульсов; sealed exhaust; приоритет det.
- `SubconsciousBypassGuardTest` — source-scan страж: подсознание НЕ вызывает `api/*` напрямую.

## Метрики / гейты

- Все EXP-кандидаты — H-039…H-050 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Per-phase latency: TR/REM/Gossip измеримы через JMH-инфраструктуру ([PROTOCOL](../research/PROTOCOL.md)).
- Dream-replay vs online retention F1 — H-041.

## Отложено

- Federated dream-replay (coordinated cross-instance REM) — BLOCKED-EXT.
- Hierarchical impulse merging (multi-driver hierarchy) — research wave.
- TLA+ `ConsolidationCycle` — next-format-contracts ([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).

См. [architecture/MODULES.md](../architecture/MODULES.md), [research/HYPOTHESES.md](../research/HYPOTHESES.md).