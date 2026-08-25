# Project Context — SESSION CONTINUITY (compaction #14)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; LSP FpgaBackend.java:150 ложная; субагенты недоступны.
- XML ошибок: grep -oE 'message="[^"]{0,200}' matrix-core/build/test-results/test/TEST-<Класс>.xml
- ls+grep пакета ДО нового кода. Коммиты: …66ad152→f1c94bd (последний).

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE (всё зелёное)
W1-W5,W7 ядро; operator CRD 4 файла (compile EXIT=0 + CrdFactoriesTest); actions.PlanPreprocessor+тест (DESIGN-15 закрыт); журнал плана актуален до этого места.

## ТЕКУЩИЙ ШАГ: DESIGN-07 сон-цикл (минимум)
В io.matrix.lifecycle создать `ConsolidationCycle`:
- record DrainSummary(int routesDrained, long itemsMigrated)
- поля: Map<String,Integer> routeBacklogs (route→pending), boolean open
- методы: `open()` (idempotent, бросает IllegalStateException если уже открыт? нет — просто флаг), `drain(String route, int batchSize)` → уменьшает backlog на min(batch,size), возвращает фактически перенесённое; `close()` → DrainSummary(число маршрутов с нулевым остатком после цикла? проще: routesDrained = кол-во маршрутов доведённых до 0 за окно, itemsMigrated = сумма перенесённого) и закрывает.
Детерминизм, без clock. Тест ConsolidationCycleTest: юнит drain частичный/полный, close суммирует, drain при закрытом окне → IllegalStateException("cycle_closed"); jqwik: суммарный перенесённый ≤ начального backlog.
Прогон --tests "io.matrix.lifecycle.*".

## Финал захода
Журнал: DESIGN-07 сон-цикл done(minimum). git add lifecycle/{ConsolidationCycle.java,+test} + журнал; commit «WAL: DESIGN-07 consolidation cycle minimum». Отчёт пользователю: статус волны + очередь (W6 прогоны JMH, DJL/ONNX учитель, DESIGN-03/06/14 остатки, BLOCKED-EXT список).

## Правила
FROZEN/avro/workflows не трогать; детерминизм; forbidden claims избегать.
