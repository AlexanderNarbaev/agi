
# DRAFT — RFC Procedure

## Что

RFC-процедура для изменений, затрагивающих FROKEN/подобные зоны, новые TLA-контракты или изменение coverage-gate.

## Шаги

1. Обсуждение в `engineering/RFC-NNNN.md` (≤ 200 строк).
2. Approval-голос владельца (singleton normative).
3. Изменение relevant-WAL.md+ changelog.
4. PR + целевой тест-прогон + JMH-gate если сравнение perf.
5. Squash и фиксация archive.

## Где живут RFC

`engineering/rfc/` (новый каталог — создать при первом RFC).

## Метрики / Гейты

- Каждый RFC имеет «Зачем», «Тесты», «Риски», «Откат».
- Никаких непроверенных цифр (CONSTITUTION VI).

## Отложено

Шаблон для free-form голований; multi-stakeholder approval.