# Project Context — SESSION CONTINUITY (compaction #16)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; zsh: кавычить --include="*.java"; LSP FpgaBackend ложная; субагенты недоступны.
- XML ошибок: grep -oE 'message="[^"]{0,200}' matrix-core/build/test-results/test/TEST-<Класс>.xml
- ls+grep пакета ДО нового кода. Коммиты серии: ed42fd1→235c984→66ad152→f1c94bd→8cbe22b→cf369c1 (последний).

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE (всё зелёное, журнал плана актуален)
W1-W5,W7 ядро; DESIGN-02/07(min)/09/11/12/13/15; CRD operator 4 файла+тесты; DESIGN-14 аннекс-реестр (`docs/engineering/DESIGN-14-call-site-audit.md`: 125 вызовов/55 файлов, категории A/B/C/D, волны A-1 ExplanationGenerator(24)→A-2 NeuralBrain/ChatBot/OpenAIChatResource→A-3 neuron остатки→INV-1 CI).

## ТЕКУЩИЙ ШАГ: волна A-1 миграции — ExplanationGenerator → BooleanRuntime
1. Осмотр: `grep -n "evaluate\|TruthTable\|NeuronLayer\|import" matrix-core/src/main/java/io/matrix/explainability/ExplanationGenerator.java | head -25` + тесты `ls matrix-core/src/test/java/io/matrix/explainability/`.
2. Паттерн по DESIGN-14 §волны: ленивый weak-кэш TtForm как в NeuronLayer (образец `neuron/NeuronLayer.java` — grep "weak\|TtForm" для копирования приёма) + equivalence-тест по образцу `cluster/BirMigrationEquivalenceTest.java` (сначала прочитать его!).
3. Правка: заменить прямые вызовы evaluate на BIR-путь с кэшем форм; НЕ менять публичный API класса.
4. Тест: equivalence (старый путь через сохранённую копию логики vs новый) + существующие тесты пакета зелёные. Прогон `--tests "io.matrix.explainability.*"`.
5. JMH не гонять в этой волне (время) — пометить в аннексе «latency-контроль отложен».

## Финал захода
Аннекс: отметить A-1 [x] с фактом; git add файлы; commit «WAL: A-1 ExplanationGenerator → BIR»; отчёт.

## Если ExplanationGenerator окажется слишком связанным (например использует DecisionTree напрямую без TT)
— честно пометить в аннексе A-1 как needs-analysis и переключиться на A-2 OpenAIChatResource(2 вызова — самый маленький), тот же цикл.

## Правила
FROZEN/avro/workflows не трогать; без новых зависимостей; публичные API не ломать; forbidden claims избегать.
