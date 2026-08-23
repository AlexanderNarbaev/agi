# DESIGN-14. Миграция потребителей на BIR (Критерий A, SPEC-002)

- **Статус:** living (стратегия исполнения Критерия A; обновляется по мере волн миграции)
- **Основание:** SPEC-002 §3 «Критерий A», ROADMAP §3; решение WAL 2026-08-23

## 1. Цель

Весь существующий функционал ядра исполняется через BIR (`BooleanRuntime.evaluate`) без регрессии тестов; JMH-отклонение latency ≤ 10% (измерено с запасом: legacy 5.619 ns/op vs ttEval 0.642 / runtimeEval 2.191 — `matrix-core/build/results/jmh/results.json`).

## 2. Стратегия: strangler-fig через адаптеры

- Старый API (`neuron.TruthTable`/`DecisionTree`) остаётся working-deprecated; потребители переключаются по одному.
- Обёртка: `TruthTableAdapter.toBir/fromBir`, `DecisionTreeAdapter` (уже существуют).
- Единая точка исполнения: `io.matrix.bir.BooleanRuntime.evaluate/evaluateBatch`.
- Каждый своп = отдельный коммит + property-тест эквивалентности старого и нового пути на seeded-случайных входах (образец: `cluster/BirMigrationEquivalenceTest`).

## 3. Правило кэширования форм (главный риск производительности)

- TtForm строится **один раз на принятый/загруженный нейрон** и кэшируется рядом с ним (пример: `NeuronClusterActor.formCache` — synchronized WeakHashMap по immutable-таблице).
- **Запрещено** конвертировать формы внутри горячих циклов обучения: адаптация DecisionTree→TtForm кандидата при K=20 стоит 2^20 eval'ов против ~100 вызовов фитнес-цикла (`AgentBrainService.evaluateTreeFitness`) — мигрировать только финальные принятые нейроны либо вводить кэш форм до цикла.

## 4. Реестр прогресса (обновлять в каждой волне)

| Потребитель | Call-sites | Статус |
|---|---|---|
| cluster/NeuronClusterActor | 1 | ✅ wave 1 (кэш форм + equivalence test) |
| api/MatrixResource | 1 | ✅ wave 2 (/truth-table через BIR; api-пакет 163/0) |
| bridge/NeuroSymbolicBridge | 2 | pending |
| explain/BooleanExplainability | 2 | pending |
| agent/PretrainedLoader | 2 | pending |
| agent/AgentBrainService (fitness) | 1+ | ⚠️ только после кэша форм (§3) |
| ethics/frozen/FrozenAxiomNeuron | 1 | 🔒 FROZEN-зона — не мигрировать без RFC владельца |
| прочие `.evaluate(` вне bir/neuron | ~118 | аудит семантики (часть — не булевы нейроны: FROZENFNLGuardian и т.п., вне scope) |

## 5. Критерий завершения

Все булевы call-sites из реестра идут через BooleanRuntime; ArchUnit-правило INV-1 (сырые структуры не проникают в рантайм) включено в CI; полный прогон зелёный; JMH ≤10% подтверждён повторно.
