# SPEC-011 — Hierarchical Memory (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.memory`
**Related**: [SPEC-003](./SPEC-003-knowledge-topology.md), [DESIGN-08](../designs/DESIGN-08-hierarchical-memory.md)

## Что

`memory/` — иерархическая память MATRIX с тремя уровнями
(L1 hot/L2 module/L3 archive), drift-detection, persistent backends
(SQLite, SDM). Поддерживает causal-relations между записями
для M4-Causal TLA+ контракта (next).

## Архитектура

```
HierarchicalMemory           — основной API
 ├── Level { L1_HOT, L2_MODULE, L3_ARCHIVE }
 ├── MemoryEntry { content, level, importance, tags, timestamp }
 ├── DriftSignal { level, magnitude, recommendedAction }
 ├── store/recall/search/expire
MemoryHierarchy               — навигация по дереву уровней
PersistentHierarchicalMemory   — расширение с persistence
SqliteMemoryBackend            — SQLite-реализация
SdmReader                      — чтение SDM-формата
memory.query/                  — DSL для выборок
```

## FR (классы и интерфейсы)

- `memory/HierarchicalMemory` — основной API. Конструктор без параметров
  (CDI bean). Методы:
  - `store(Level, content, source, tags)` → `MemoryEntry`
  - `recall(MemoryEntry)` → перенос на L1
  - `search(query, topK)` → `List<MemoryEntry>` ranked by importance+recency
  - `expire(olderThan)` → List<MemoryEntry> evicted
  - `drift()` → `DriftSignal` (per-level)
- `memory/MemoryHierarchy` — утилитарный класс для работы с уровнями
  (`Level.index()`, `Level.baseImportance()`).
- `memory/PersistentHierarchicalMemory` — расширение, добавляющее
  `flush()`/`load()` через backend.
- `memory/SqliteMemoryBackend` — JDBC/SQLite backend.
- `memory/SdmReader` — чтение SDM-снапшотов (legacy format).

## Уровни памяти

| Level | index | baseImportance | TTL | Описание |
|---|---|---|---|---|
| L1_HOT | 0 | 1.0 | 1h | active context (chat, current task) |
| L2_MODULE | 1 | 0.7 | 7d | module-level knowledge (per-topic) |
| L3_ARCHIVE | 2 | 0.3 | ∞ | long-term archive (cold storage) |

Promotion: L3 → L2 при `accessCount > 5`, L2 → L1 при `accessCount > 20`.
Demotion: L1 → L2 при `lastAccessMs > 1h`, L2 → L3 при `lastAccessMs > 7d`.

## Инварианты

1. **Детерминизм (CONSTITUTION I)**: `search(query, topK)` — чистая
   функция. Random допустим только при tie-break на equal importance.
2. **FROZEN-гейт (CONSTITUTION III)**: `store` не может сохранить
   контент, проходящий через `FROZENFNLGuardian` с `Verdict.deny`.
   `Verdict.deny` → `EthicalStorageException`, запись отклонена.
3. **K_MAX=20 (CONSTITUTION II)**: query-expressions компилируются
   через `bir/BirCompiler` в boolean-форму. Сложность query ≤ 2^20.
4. **Coverage gate ≥82%** на всех memory-классах.
5. **Drift detection**: `drift()` возвращает signal, когда
   `uniqueRate < 0.5` или `entropy < 0.3` в L1. INV-5 в [INVARIANTS](../engineering/INVARIANTS.md).

## MemoryEntry

Immutable record:

```
MemoryEntry {
  long id
  String content
  Level level
  double importance
  Set<String> tags
  long timestampMs
  int accessCount
  long lastAccessMs
}
```

Методы `withAccessed()` и `withLevel(Level)` возвращают новый
immutable instance — старый не мутируется.

## DriftSignal

```
DriftSignal {
  Level level
  double magnitude         // 0.0 .. 1.0
  String recommendation    // PROMOTE | DEMOTE | ARCHIVE | NOOP
  boolean isSignificant()  // magnitude > 0.3
}
```

`magnitude > 0.7` → рекомендация `ARCHIVE` (cold-storage).

## PersistentHierarchicalMemory

Добавляет persistence-методы:

- `flush()` — сериализует все уровни в backend.
- `load()` — восстанавливает состояние из backend.
- `compact()` — дедупликация по `hash(content)` в L3.

`SqliteMemoryBackend` хранит записи в трёх таблицах
(`memory_l1`, `memory_l2`, `memory_l3`) с индексами по
`timestampMs`, `importance`, `accessCount`.

## Связь с существующим

- `agent/LongHorizonPlanner` использует `HierarchicalMemory` для
  retrieval по плановому контексту.
- `brain/BrainPipeline` ([DESIGN-13](../designs/DESIGN-13-brain-pipeline.md))
  использует `L2_MODULE` для world-model.
- `OpenAIChatResource` ([api/OpenAIChatResource](../../matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java))
  использует `longTermMemory` (L2 + L3) для retrieval.
- `lifecycle/ConsolidationCycle` ([DESIGN-07](../designs/DESIGN-07-consolidation-cycle.md))
  выполняет promotion/demotion как часть drain-summary.

## Тесты

`HierarchicalMemoryTest` (22 теста), `MemoryHierarchyTest` (8),
`PersistentHierarchicalMemoryTest` (12), `SqliteMemoryBackendTest`
(14), `DriftSignalTest` (6). Total 62 теста.

## TLA+ формализация

`Memory-M4-Causal` ([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)) —
кандидат на TLA+ (RUN 14). Формализует promotion/demotion как
state-machine с инвариантами capacity bounds и reachability.
