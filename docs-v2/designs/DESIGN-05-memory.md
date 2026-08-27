# DESIGN-05 — Память (M0…M4)

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Пять слоёв: рабочая (M0), эпизодическая (M1), семантическая (M2), процедурная (M3), коллективная M4. Физика: in-memory Map, PG (R2DBC) для эпизодов + lineage, BIR-реестр, CRDT для коллективной (M4).

## Реализация

- `memory/HierarchicalMemory`, `MemoryHierarchy`, `SdmReader` (SDM M1 — см. H-011 running).
- `noosphere/Crdt`, `GrowOnlySet`, `KnowledgeIndex`, `MeshFederation`, `QuorumChecker` — CRDT LWW-семантика для eventual consistency M4.
- `events/R2dbcEventJournal` (миграция между PG и журналом).

Тесты: юнит memory/*, integration PG (Testcontainers).

## Отложено

- Полная TLA+-спек `Memory-M4-Causal` (см. `architecture/FORMAL-CONTRACTS.md` — next-format-contracts).
- Доменные данные для recall-бенча H-007.