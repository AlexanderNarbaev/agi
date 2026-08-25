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
- [x] Волна A-2: NeuralBrain 9 сайтов → BIR (DecisionTreeAdapter); ChatBot/OpenAIChatResource = N/A (не TT-сайты)
- [x] Волна A-3: классификация завершена — 13 транзитивно-BIR; Batch* = SIMD-утилиты (JMH-гейт W6); SchemaDescriptor needs-analysis
- [ ] INV-1 ArchUnit в CI

## Волна A-2/A-3 — статус 2026-08-25

### A-2 выполнено
- `minecraft/NeuralBrain` — 9 сайтов DecisionTree→BIR (`FORM_CACHE` + `DecisionTreeAdapter.toBir(dt, inputCount())`); тесты пакета зелёные.
- `dialog/ChatBot`, `api/OpenAIChatResource` — переклассифицированы N/A: их `.evaluate` принадлежат `EthicalFilter`/`ProactiveInterface`, не булевым TT-структурам.

### A-3 классификация neuron/*
| Файл | Получатель | Статус |
|---|---|---|
| HierarchicalBrain (4) | NeuronLayer-поля | BIR-backed transitively ✅ |
| NeuralTextGenerator (3) | NeuronLayer | BIR-backed transitively ✅ |
| NeuralMemoryResponse (3) | NeuronLayer | BIR-backed transitively ✅ |
| MultiBrainEnsemble (3) | NeuronLayer | BIR-backed transitively ✅ |
| BatchEvaluator (4) | сырой TruthTable | raw → миграция A-3b |
| BatchMemoryAdapter (3) | сырой TruthTable | raw → миграция A-3b |
| SchemaDescriptor (3) | table.evaluate | требует анализа (тип table не подтверждён) |
| DecisionTree (2) | левый/правый потомок | legacy-internal: оборачивается адаптером у потребителей |
| DecisionTreeBatch (3) | tree.evaluate | raw → миграция A-3b |

**Итог волны A:** мигрировано 33 сайта (A-1:24 + A-2:9); транзитивно-BIR 13; остающихся raw ≈10 (A-3b) + 3 needs-analysis.

### A-3b переклассифицировано 2026-08-25
BatchEvaluator / BatchMemoryAdapter / DecisionTreeBatch — НЕ raw-сайты, а **SIMD-батчевые утилиты** (evaluateAll64/evaluatePacked/trueCount над int-индексами вершин). Их перевод на `TtForm.evalBatch` требует JMH-гейта ≤10% против текущего упакованного пути — вынесено в W6 (отдельный замер), слепой рерайт запрещён правилом latency.
SchemaDescriptor — needs-analysis (тип получателя не подтверждён).

**Итого DESIGN-14 после волн A:** мигрировано 33, транзитивно-BIR 13, SIMD-утилиты 10 (JMH-гейт), N/A 5, needs-analysis 3, legacy-internal 2.

## INV-1 реализован 2026-08-25
`matrix-core/src/test/java/io/matrix/bir/Inv1SourceGuardTest.java` — source-scan страж без внешних зависимостей: запрещает `(truthTable|modified|tt|tree|table|*Tree).evaluate(` вне whitelist (bir/, ethics/frozen/, neuron/, compression/TruthTableMinimizer). Выполняется штатным test-таском → действует в CI без изменения FROZEN-workflows.
