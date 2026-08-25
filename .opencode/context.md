# Project Context — SESSION CONTINUITY (compaction #13)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; LSP FpgaBackend.java:150 ложная; субагенты недоступны; jqwik `.list()`.
- XML: grep -oE 'message="[^"]{0,200}' matrix-core/build/test-results/test/TEST-<Класс>.xml
- ls+grep пакета ДО нового кода. Коммиты: ed42fd1→235c984→66ad152.

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE
W1-W5,W7 ядро (см. журнал плана — актуален). Конфиг модели 10/10. Только что: operator CRD SignalModuleResource/SignalModuleSpec/TaskCellResource/TaskCellSpec (компиляция :matrix-operator:compileJava EXIT=0); actions.PlanPreprocessor (PlanStep record + AC-3 preprocess, DeclaredArc record implements BinaryConstraint{consistent,vi,vj}) — compileJava EXIT=0. ТЕСТОВ К НИМ ЕЩЁ НЕТ.

## НЕМЕДЛЕННЫЕ ШАГИ
1. Написать PlanPreprocessorTest (io.matrix.actions): юнит — happy-path (шаг с 2 переменными domains {2,2}, arcs [[0,1]] → проходит без исключения); противоречие через пустой domain (domainSizes без ключа var) → IllegalStateException "unsatisfiable_preconditions"; bad arc length → IllegalArgumentException. Прогон `--tests "io.matrix.actions.*"`.
2. Быстрый тест operator? Проверить наличие существующих: ls matrix-operator/src/test — если есть стиль, добавить лёгкий SmokeTest на create()-фабрики (метаданные+spec). Если модуль тестов не имеет инфраструктуры — пропустить (compileJava уже EXIT=0).
3. Журнал плана: W7-CRD done(compile), DESIGN-15 done(preprocessor+tests).
4. git add: matrix-operator/src/main/java/io/matrix/operator/{SignalModuleResource,SignalModuleSpec,TaskCellResource,TaskCellSpec}.java; matrix-core/.../actions/PlanPreprocessor.java; matrix-core/src/test/java/io/matrix/actions/PlanPreprocessorTest.java; docs/engineering/PLAN-FULL-IMPLEMENTATION.md; .opencode/context.md. Commit «WAL: W7 CRD + DESIGN-15 plan preprocessing».
5. Отчёт пользователю кратко.

## Правила
FROZEN/avro/workflows не трогать; детерминизм; forbidden claims избегать.
