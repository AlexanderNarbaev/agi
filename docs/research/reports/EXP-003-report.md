# EXP-003 Report — «Живой» обучатель (GA/three-factor) против Tsetlin как producer

**Статус: running** · Гипотеза: H-003 (`docs/research/HYPOTHESES.md`) · Спека: SPEC-002 Этап C (FR-C1/C2)

## Preregistration
Правило решения зафиксировано заранее в SPEC-002#этап-c: если сложность «живого» механизма не окупается против автоматов Цетлина, механизм помечается superseded.

## Инфраструктура
Готово: TsetlinTrainer (базовая линия, принята), GA-инфраструктура `io.matrix.evolution` (EvolutionLoop, GeneticOperators, ParetoFitness), GATopologySearch помечен «экспериментальный» в DESIGN-04.

## Результаты
**Не собирались в этой сессии.** Сравнительный прогон сходимости/сложности не выполнялся; verdict не выставляется (протокол preregistration).

## Следующее действие
Прогнать EXP-003 (сходимость примеров до 99%, размер артефакта, LOC-сложность), оформить ADR «основной producer» по SPEC-002#критерии-приёмки.
