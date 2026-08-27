# DESIGN-14 — Миграция потребителей на BIR

**Статус: normative (living)** · пересмотр (v2 rebuild).

## Что

Стратегия strangler-fig: всё вычисление через `BooleanRuntime.evaluate/evaluateBatch`. INV-1 source-scan-страж блокирует прямые `.evaluate()`-вызовы вне whitelist (bir, ethics/frozen, neuron internals, TruthTableMinimizer).

JMH-гейт Batch*→evalBatch: **выполнен **, см. [engineering/JMH-GATE-EVIDENCE.md](../engineering/JMH-GATE-EVIDENCE.md) — 32–69M ops/s, решение «оставить как есть» (Batch* SIMD утилиты остаются).

## Реализация

- 37 sites мигрировано через волны A: A-1 (ExplanationGenerator 24), A-2 (NeuralBrain 9), A-3c (SchemaDescriptor 4).
- `bir.Inv1SourceGuardTest` — source-scan guard (без deps).
- Адаптеры `bir/TruthTableAdapter.toBir(TruthTable)`, `bir/DecisionTreeAdapter.toBir(DecisionTree, int k)`.

Реестр + классификация — `engineering/SDD-COVERAGE.md` (M4-CRDT уже реализован в `noosphere/Crdt`, schema `KnowledgeIndex` etc.).

## Метрики / гейты

- **INV-1 страж** зелёный с первого прогона (нет нарушений вне whitelist).
- **A-1 done**: ExplanationGenerator per-instrument.
- **JMH-гейт выполнен**: см. [engineering/JMH-GATE-EVIDENCE.md](../engineering/JMH-GATE-EVIDENCE.md).
- Контракт ≤10% latency: для текущих hot-paths — соблюдён (SIMD-утилиты измерительно стабильны).

## Отложено

- Полная BDD-эквивалентность при version swap (см. DESIGN-13).
- A-3b SIMD-утилиты → TtForm.evalBatch только в сценариях, где вход УЖЕ в `long[]` (после спеки-002 миграции в отдельных местах).