
# Design-DRAFT — Memory M4 (Causal CRDT)

## Что

`noosphere/Crdt` (текущий merge: commutative/associative/idempotent) расширяется до causal CRDT с tombstone-причинностью для M4-реплик noosphere.

## Блокеры

- TLA+-спека `Memory-M4-Causal` (next-format-contracts).
- ALGORITHM-ATLAS §50..§52 — causal tree CRDT.
- ALGORITHM-ATLAS-WAVE10 §50..§52 — R/W quorum с merge(online).

## Реализация (набросок)

```
io.matrix.noosphere (расширение):
 CausalEvent(timestamp, parentHashes, payload)
 CausalCrdt.merge(other): CausalCrdt // preserves happens-before
 TombstoneAt(nodeId, eventId): void // irreversible
 Quorum read: monotonic snapshot at version >= barrier
```

## Метрики / Гейты EXP-022

- Monotonicity: для всех v,w: v.merge(w) имеет ≥ v и ≥ w по векторным часам.
- Tombstone irreversibility: ST-set не может содержать удалённый event-id.
- Eventual consistency: read окончает возвратом v с v’ ≥ barrier.

## Отложено

Реальная noosphere-федерация; K8s CRD интеграция.