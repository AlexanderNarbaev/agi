# Project Context — SESSION CONTINUITY (compaction #15)

## Ловушки
- Целевые прогоны `--tests`; LSP FpgaBackend.java:150 ложная; субагенты недоступны; коммиты до 8cbe22b.
- НЕ добавлять зависимости в build.gradle (риск сети/сборки): DJL/ONNX учитель = BLOCKED-EXT(pending dep) честно.

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE
W1-W5,W7 ядро+CRD+ElspChannel; DESIGN-02/07(min)/09/11/12/13/15 закрыты; журнал актуален до «DESIGN-07 done». Коммиты ed42fd1→8cbe22b.

## ТЕКУЩИЙ ШАГ: DESIGN-14 остаток — аудит булевых call-sites (аннекс)
Цель: превратить «~118 прочих call-sites» из оценки в конкретный реестр.
1. Собрать кандидатов:
   grep -rn "\.evaluate(" matrix-core/src/main/java --include=*.java | grep -v "/bir/" | grep -v "/ethics/frozen/" 
   плюс ".eval(" для TtForm/ClauseSetForm вне bir. Посчитать по файлам (sort | uniq -c | sort -rn | head -40).
2. Создать аннекс `docs/engineering/DESIGN-14-call-site-audit.md`: таблица Файл→кол-во вызовов→категория (runtime-ready/cacheable/frozen-excluded/training-side/needs-analysis) по правилам DESIGN-14 (waves ✅ уже мигрированы — исключить cluster/api/bridge/explain/neuron файлы из «мигрированных», отметить их как done).
   Честная методология в шапке: статический grep, ручная классификация отложена для файлов с семантикой.
3. Обновить журнал плана: DESIGN-14 остаток → аудит-аннекс готов (файл), миграция самих sites — следующие заходы по таблице.
4. git add аннекс+журнал+context.md; commit «WAL: DESIGN-14 audit annex».

## ЗАТЕМ (если контекст позволит)
W6: ls matrix-core/src/jmh — есть ли готовый бенчмарк tsetlin/ga; если есть подходящий — запуск фонового JMH (run_background) с последующим разбором. Иначе честная пометка.

## Правила
FROZEN/avro/workflows не трогать; без новых зависимостей; forbidden claims избегать.
