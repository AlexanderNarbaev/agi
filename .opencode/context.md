# Project Context — SESSION CONTINUITY (compaction #18)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; zsh кавычить --include="*.java"; LSP FpgaBackend ложная; субагенты недоступны.
- heredoc python: НЕ вставлять EOF внутри; использовать PYEOF и аккуратность.
- Коммиты: …8cbe22b→cf369c1→050ef25 (последний).

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE
Всё из #17 + волна A-2: minecraft/NeuralBrain 9 сайтов DecisionTree→BIR (static FORM_CACHE, evaluateViaBir через DecisionTreeAdapter.toBir(dt, dt.inputCount())), тесты зелёные. ChatBot/OpenAIChatResource переклассифицированы: их .evaluate — это EthicalFilter/ProactiveInterface, НЕ TT-сайты (в аннекс как N/A).

## ТЕКУЩИЙ ШАГ: волна A-3 — классификация neuron остатков
Файлы из аннекса: neuron/{HierarchicalBrain(4),BatchEvaluator(4),SchemaDescriptor(3),NeuralTextGenerator(3),NeuralMemoryResponse(3),MultiBrainEnsemble(3),DecisionTreeBatch(3),BatchMemoryAdapter(3)},neuron/DecisionTree(2).
1. Для каждого: `grep -n "\.evaluate(\|\.eval(" <файл> | head -4` и определить получателя:
   - если получатель NeuronLayer/иерархия, идущая через NeuronLayer.evaluate → «BIR-backed transitively» (wave 6 закрыл NeuronLayer);
   - если сырой TruthTable/DecisionTree.evaluate(BitSet) → пометить «raw — мигрировать в A-3b» (код НЕ трогать в этом заходе).
2. Итог классификации дописать в аннекс docs/engineering/DESIGN-14-call-site-audit.md секцию D/A-3 (таблица файл→получатель→статус).
3. Журнал плана: A-2 done, A-3 classified.
4. git add аннекс+журнал+context; commit «WAL: волна A-2 миграция + A-3 классификация»; отчёт.

## Правила
FROZEN/avro/workflows не трогать; без новых зависимостей; forbidden claims избегать.
