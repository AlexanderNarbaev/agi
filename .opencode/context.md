# Project Context — SESSION CONTINUITY (compaction #30)

## Ловушки
- Целевые прогоны --tests; LSP FpgaBackend ложная и Exp002ComparisonTest 107/117/134 устаревшие (фиксы на месте, BUILD зелёный); субагенты недоступны. Коммиты до 403f43f (запушен).

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE
Всё из #29 + INV-1 source-scan guard (8d038f8) + DESIGN-03 аудит закрыт (403f43f).

## ТЕКУЩИЙ ШАГ: два хвоста одним заходом
### 1) A-3c: neuron/SchemaDescriptor (3 сайта table.evaluate)
`grep -n "table\|TruthTable\|class\|public" matrix-core/src/main/java/io/matrix/neuron/SchemaDescriptor.java | head -15` — определить тип получателя.
- Если TruthTable поле → применить паттерн кэша (Map<TruthTable,TtForm> + evaluateViaBir) точечно в этом файле; прогон --tests "io.matrix.neuron.*".
- Если другой тип → пометить needs-analysis окончательно в аннексе.
Аннекс: строку A-3b/SchemaDescriptor обновить по факту.

### 2) DESIGN-06 хвосты — аудит signals/
`ls matrix-core/src/main/java/io/matrix/signals/` + grep имён модулей (lexicon/template/thermometer/audio/embed).
Классификация: что из линейки DESIGN-06 существует в проде vs прототип vs отсутствует. Результат — строка в журнале плана: DESIGN-06 = [факт]. embed-hash (внешняя зависимость) и audio-events (этап 3) → BLOCKED-EXT/отложено если отсутствуют.

## Финал захода
git add изменённые файлы (SchemaDescriptor при миграции, аннекс, журнал); commit «WAL: A-3c SchemaDescriptor + DESIGN-06 сигналы аудит»; push. Отчёт пользователю компактно: статус очереди после этого (остаются только DJL/ONNX dep, доменные данные, алиасы косметика, audio-events этап 3).

## Правила
FROZEN/avro/workflows не трогать; без новых зависимостей; forbidden claims избегать.
