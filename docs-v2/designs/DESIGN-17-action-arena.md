# DESIGN-17 — Action Arena (Transaction & Arbitration)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild) · brain wave v1 · changelog 2026-08-26 — brain wave v1.

## Что

Среда исполнения моторного слоя [SPEC-005](../specifications/SPEC-005-action.md): транзакционная изоляция через `lifecycle/TaskCell` (DESIGN-12 бюджеты), арбитраж concurrent actions, аудит через `events/KafkaEventJournal` + `audit/HashChain`. Каждое исполнение — наблюдаемое, откатываемое, верифицируемое.

## Мотивация

- Wall-clock изоляция: бюджеты CPU/mem/wall/ttl защищают ядро от зависших effectors.
- Конкурентность без гонок: только один PlanRunner держит конкретный ресурс.
- Наблюдаемость: append-only Kafka-журнал + hash-chain обеспечивают tamper-evidence.
- Делиберативный аудит: каждое решение гейта пишется в `events/ClusterEvent` + хешируется.
- Соответствие P-E-D pipeline [DESIGN-03](../designs/DESIGN-03-pipeline.md): arena — последний шлюз перед side-effect.

## Архитектура

### Action Arena = TaskCell × ActionRegistry × Audit

```
BRC decision ──→ ethics/FROZEN gate ──→ TaskCell.spawn(budget)
                                            │
                                            ▼
                                    PlanRunner внутри ячейки
                                            │
              ┌─────────────────────────────┼──────────────────────┐
              ▼                             ▼                      ▼
       KafkaEventJournal           HashChain.append            LieDetector
       (events/)                  (audit/)                     (post-hoc)
```

### TaskCell (DESIGN-12 бюджеты)

- `lifecycle/TaskCell.State{CREATED, RUNNING, COMPLETED, FAILED, TIMEOUT, DESTROYED}`.
- `execute(TaskExecutor)` с бюджетами CPU/mem/wall/ttl; `destroy()` принудительно по исчерпании.
- `lifecycle/FnlGate` (SHADOW→CANDIDATE→PROMOTED|DEMOTED) для новых effectors: параллельная теневая проверка, promote после `score ≥ threshold`.
- K8s: `matrix-operator/TaskCellResource` CRD — внешнее масштабирование.

### Concurrent action arbitration

- Один TaskCell держит один план; multi-step план — последовательный внутри ячейки.
- Параллельные действия — разные ячейки; конфликт по ресурсу разрешается через CAS на `ActionRegistry.has(name)` + pessimistic lock на ресурс.
- `PlanPreprocessor` (DESIGN-15) запускается ДО spawn TaskCell: AC-3 fail-fast отсекает неконсистентные планы (`IllegalStateException` на пустом домене).
- `VersionedContract` [DESIGN-13](../designs/DESIGN-13-action-registry.md) атомарный своп: tampered `domainHash` → `IllegalArgumentException`; concurrent swap → только первый проходит CAS.

### Audit pipeline

Двойной журнал:

1. **`events/KafkaEventJournal`** (production):
   - `events/{EventJournal, KafkaEventJournal, R2dbcEventJournal, InMemoryEventJournal, BatchKafkaJournal, KafkaTopics, ClusterEvent, ClusterEventType}`.
   - Топики per arena: `matrix.arena.{arena-id}.events`.
   - Retention 72h (см. archive L9 для совместимости), hot-loop для аудита.

2. **`audit/HashChain`** (immutable):
   - `audit/{HashChain, HashLink, FrozenFNLHashChain}` — append-only, thread-safe (внутренний lock).
   - `HashLink(prevHash, eventDigest, timestamp, signature)` — цепочка фиксируется в журнал.
   - FROZEN-цепочка отдельно: `FrozenFNLHashChain` — фиксирует, что FROZEN-нейроны не модифицированы.

Контракт: для каждого `Action.execute()` пишется пара `(KafkaEvent, HashLink)`; репликация и ретенш определяются per-tier.

## Метрики / гейты

- **TaskCell**: spawn latency p99 < budget `wall/2` (gate — следующая сессия, см. DESIGN-12 EXP-012 proposed).
- **AC-3 fail-fast**: пустой домен → `IllegalStateException` (DESIGN-15); покрытие юнит-тестами обязательно.
- **HashChain verify**: O(N) walk после батча; mismatch → tamper-detection alert (`events/ClusterEventType.TAMPER_DETECTED`).
- Готовых perf-метрик нет — отложено.

## Реализация в коде

- `actions/{PlanRunner, PlanPreprocessor, ActionRegistry, VersionedContract}`.
- `lifecycle/{TaskCell, FnlGate, CauldronProtocol}` — arena-обвязка.
- `events/{EventJournal, KafkaEventJournal, R2dbcEventJournal, InMemoryEventJournal, BatchKafkaJournal, KafkaTopics, ClusterEvent, ClusterEventType}`.
- `audit/{HashChain, HashLink, FrozenFNLHashChain}`.
- `matrix-operator/TaskCellResource` (CRD) — внешнее.
- Тесты: `actions/*`, `lifecycle/*`, `events/*` (Testcontainers — `KafkaIntegrationTest` требует Docker-сервиса живого), `audit/*`.

## Отложено

- EXP-012 (TaskCell p99 vs direct) — proposed (DESIGN-12).
- Двойной карантин DESIGN-12 ↔ DESIGN-08 (federation quarantine).
- BDD-бисимуляция версий (DESIGN-13) для action-arena тестов.
- Hard-real-time планирование для motor-примитивов (HW-bound).
- Адаптивный размер среза для FnlGate (DESIGN-12).

См. также [SPEC-005](../specifications/SPEC-005-action.md), [DESIGN-12](../designs/DESIGN-12-taskcell-fnl.md), [DESIGN-13](../designs/DESIGN-13-action-registry.md), [DESIGN-14](../designs/DESIGN-14-bir-migration.md), [DESIGN-15](../designs/DESIGN-15-plan-preprocessing.md).