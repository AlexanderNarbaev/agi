# DESIGN-14 — Аннекс: реестр булевых call-sites вне BIR

**Статус: living** · Методология: статический grep `.evaluate(|.eval(` по `matrix-core/src/main/java` (исключены `bir/`, `ethics/frozen/`, тесты). Дата: 2026-08-25.
**Итог выборки:** 125 вызовов в 55 файлах.

## Классификация

### A. Приоритет миграции (рантайм-контур, крупные концентрации)
| Файл | Вызовов | Примечание |
|---|---|---|
| `explainability/ExplanationGenerator.java` | 24 | крупнейшая концентрация; проверить дублирование с мигрированным explain/ |
| `minecraft/NeuralBrain.java` | 9 | рантайм ботов |
| `neuron/HierarchicalBrain.java` | 4 | возможно уже идёт через NeuronLayer→BIR — сверить |
| `neuron/BatchEvaluator.java` | 4 | батчевая точка — кандидат на evaluateBatch |
| `dialog/ChatBot.java` | 3 | пользовательский контур |
| `neuron/{NeuralTextGenerator,NeuralMemoryResponse,MultiBrainEnsemble,DecisionTreeBatch,BatchMemoryAdapter}` | 3+3+3+3+3 | ансамбли/память |
| `api/OpenAIChatResource.java` | 2 | публичный фасад |
| `mcts/MctsTree.java` | 2 | поиск |

### B. Training-side / вне рантайма (⏭️ не мигрировать)
`agent/PretrainedLoader`(2), `agent/AgentBrainService.evaluateTreeFitness`(2) — переклассифицированы ранее; `SystemDemo`(5), `simulation/AgentBrain`(4), `consensus/ConsensusBenchmark`(2), `evolution/ProtectedSelfRewrite`(2), `compression/TruthTableMinimizer`(1, tooling).

### C. Семантика рядом с FROZEN (🔒 осторожно)
`ethics/guardrail/EthicalGuardrailInterceptor`(2), `safety/SafetyMonitor`(1), `safety/LieDetector`(1) — миграция только после сверки с этическими тестами.

### D. Требует анализа
`shadow/DigitalShadow`(3), `neuron/SchemaDescriptor`(3), `neuron/DecisionTree(2)`+`DecisionTreeAdapter` использование, прочие единичные (хвост ~15 файлов по 1).

## Правила следующей волны миграции
1. Порядок: A сверху вниз; для каждого файла — адаптер через `BooleanRuntime` + equivalence-тест по образцу `cluster/BirMigrationEquivalenceTest`.
2. JMH-контроль ≤10% latency после каждой волны (DESIGN-14 §приёмка).
3. Категория B исключается из INV-1; категория C — только с RFC.

## Прогресс
- [x] Реестр создан (этот документ)
- [x] Волна A-1: ExplanationGenerator (24 вызова → BIR через per-instance TtForm-кэш; тесты пакета зелёные; JMH-latency контроль отложен)
- [ ] Волна A-2: NeuralBrain + ChatBot + OpenAIChatResource
- [ ] Волна A-3: neuron/* остатки
- [ ] INV-1 ArchUnit в CI
