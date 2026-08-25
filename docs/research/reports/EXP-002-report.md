# EXP-002 Report — CLAUSESET (Tsetlin) vs MPDT-GA producer

**Статус: running** · Гипотеза: H-002 (`docs/research/HYPOTHESES.md`) · Спека: SPEC-002 FR-B3

## Preregistration
Метрики и критерии зафиксированы карточкой H-002 до запуска; базовая линия — MPDT-GA на идентичном бинарном входе.

## Инфраструктура
Готово: продюсеры обеих линий присутствуют в ядре — `io.matrix.tsetlin.TsetlinTrainer` (Этап B FR-B1/B2 принят) и GA-линия `io.matrix.evolution` (MPDT-ГА); экспорт DNF→ClauseSetForm покрыт property-тестом эквивалентности.

## Результаты
**Не собирались в этой сессии.** JVM/JMH-прогон сравнения (quality × bytes, wall-clock) не выполнялся. Согласно протоколу экспериментов и CONSTITUTION VI (запрет неподтверждённых чисел) вердикт не выставляется.

## Следующее действие
Запустить EXP-002 по JMH-профилю (-PjmhBenchmark) на фикс. датасетах; заполнить таблицу метрик; вынести verdict accepted/rejected в HYPOTHESES.md.
