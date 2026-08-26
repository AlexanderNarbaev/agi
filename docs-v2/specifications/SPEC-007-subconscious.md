# SPEC-007 — Subconscious (фоновый слой)

**Статус: normative** · пересмотр 2026-08-26 (brain wave v1) · changelog 2026-08-26 — brain wave v1.

## Что

Подсознательный слой: фоновые консолидации (`lifecycle/ConsolidationCycle`), модели предсказания мира, dream-cycles (offline replay), генерация импульсов для autonomy. Не выходит в output напрямую — только через `consciousness/AttentionRouter` → `consciousness/ActionGate` ([SPEC-006](./SPEC-006-consciousness-deliberation.md)). Это инженерная дисциплина: подсознание — отдельный процесс с собственным бюджетом, ничем не «прорывается» в рантайм-контур решений кроме как через гейт сознания.

## Архитектура

```
                  ┌─────────────────────────────────────┐
                  │  subconscious/SubconsciousDaemon    │
                  │  ───────────────────────────────    │
   M1/M2/M3 ──►   │  • PredictionModel (world-model)    │  ──►  Impulse (Set)
                  │  • ConsolidationCycle (TR/REM)      │
                  │  • DreamReplay (offline)            │
                  │  • ImpulseGenerator                 │
                  └─────────────────────────────────────┘
                                       │
                                       ▼
                          consciousness/AttentionRouter (top-down, SPEC-006)
                                       │
                                       ▼
                                   ActionGate (FROZEN)
```

## FR (классы и интерфейсы)

- `subconscious/SubconsciousDaemon` — оркестратор фонового цикла; работает в `lifecycle/TaskCell` с собственным бюджетом.
- `subconscious/PredictionModel` — предикторы на основе BIR-артефактов ([SPEC-002](./SPEC-002-boolean-compute-layer.md)); вход: `M1Episode`, выход: `Prediction{nextState, confidence}`.
- `subconscious/ConsolidationCycle` (расширение `lifecycle/ConsolidationCycle`, [DESIGN-07](../designs/DESIGN-07-lifecycle.md)) — TR-phase (transfer M1→M2) и REM-phase (offline replay M2→M3 promotion).
- `subconscious/DreamReplay` — детерминированный offline replay: выборка эпизодов из M1, прогон через `PredictionModel`, фиксация prediction-error.
- `subconscious/ImpulseGenerator` — классы: `CuriosityImpulse` (дефицит информации), `IntegrityImpulse` (consistency-check M3), `ShareImpulse` (M3→M4 digest готов к gossip), `ConsolidationImpulse` (фоновый M2→M3 promotion).
- `subconscious/Impulse` (sealed): `Curiosity | Integrity | Share | Consolidation`, поля `priority`, `evidence`, `costEstimate`.

## Инварианты

1. **Только через гейт**: подсознание НИКОГДА не вызывает `PlanRunner` или `api/*` напрямую; только через `Impulse → AttentionRouter → ActionGate` ([SPEC-006](./SPEC-006-consciousness-deliberation.md)). Source-scan страж `SubconsciousBypassGuardTest` (без ArchUnit dep, по образцу [DESIGN-14](../designs/DESIGN-14-bir-migration.md) INV-1).
2. **Детерминизм**: `DreamReplay` фиксирует seed; `ImpulseGenerator` сортирует по `priority` (tie-break по имени).
3. **K_MAX=20** (CONSTITUTION II): предикторы компилируются через `bir/BirCompiler`.
4. **FROZEN-зона** (`ethics/frozen/**`) недоступна из `subconscious/*` (CONSTITUTION III).
5. **Budget isolation**: подсознательный `TaskCell` имеет свой `Budget`, не делит с conscious loop ([DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)).

## Циклы

- **TR-phase** (transfer): эпизоды M1 с весом > θ переносятся в M2 (semantic). Детерминированная партиция; lineage через `audit/HashChain`.
- **REM-phase** (replay + consolidate): offline прогон эпизодов через `PredictionModel`; prediction-error > δ помечает эпизод для consolidation в M3; иначе — забывание.
- **Gossip** (share): M3-артефакты, прошедшие `IntegrityCheck`, отправляются в `noosphere/MeshFederation` для quorum M4 ([DESIGN-08](../designs/DESIGN-08-federation.md)).

## Связь с существующим

- `lifecycle/ConsolidationCycle` — базовая «sleep» функция ([DESIGN-07](../designs/DESIGN-07-lifecycle.md)); расширяется TR/REM фазами.
- `lifecycle/FnlGate` ([DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md)) — карантин новых артефактов: SHADOW→CANDIDATE→PROMOTED; подсознание пишет только SHADOW.
- `memory/HierarchicalMemory` ([DESIGN-05](../designs/DESIGN-05-memory.md)) — M1/M2/M3 storage; promotion gates — H-040 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- `noosphere/MeshFederation` — gossip M3→M4 ([DESIGN-08](../designs/DESIGN-08-federation.md)).
- `mediator/InstanceMediator` ([DESIGN-02](../designs/DESIGN-02-viewpoint.md)) — генерация `Impulse` иерархии драйверов.
- `federation/Anonymizer` — k-anonymous + DP-noise для share-digest (H-043, [HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).

## Тесты

- `SubconsciousDaemonTest` — изоляция от conscious loop (bypass-попытки → exception).
- `PredictionModelTest` — deterministic prediction на seed-фиксированных эпизодах.
- `DreamReplayTest` — replay sequence воспроизводима; prediction-error фиксируется.
- `ImpulseGeneratorTest` — детерминированный порядок; sealed-типы exhaust.
- `SubconsciousBypassGuardTest` — source-scan страж.

## Метрики / гейты

- EXP-кандидаты: H-039, H-040, H-041, H-045 ([HYPOTHESES-NEW](../research/HYPOTHESES-NEW.md)).
- Latency подсознания НЕ влияет на conscious latency budget ([DESIGN-18](../designs/DESIGN-18-consciousness-loop.md)).

## Отложено

- Полная интеграция с `brain/Viewpoint` ([DESIGN-02](../designs/DESIGN-02-viewpoint.md)) для weighted ensemble подсознательных сигналов.
- Hierarchical impulse-merge (многоуровневая иерархия драйверов) — research wave (текстовые ссылки в архиве `docs-v2/archive/2026-08-pre-v2/science/science/ALGORITHM-ATLAS-WAVE24.md` §92 «impulse-goal»).
- Federated dream-replay (coordinated across instances) — BLOCKED-EXT.

См. [architecture/MODULES.md](../architecture/MODULES.md), [research/HYPOTHESES.md](../research/HYPOTHESES.md).
