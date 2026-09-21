# DESIGN-04 — Обучение (продюсеры)

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Пять офлайн-продюсеров:
1. `TsetlinTrainer` (автоматы Цетлина, multiclass) — Этап B SPEC-002 FR-B1/B2 принят.
2. `WisardProducer` (WiSARD WNN, запись по RAM).
3. `MpdtGaProducer` (эволюционный baseline).
4. Самонаблюдение через контрпримеры (отложено).
5. GATopologySearch (экспериментальный).

## Реализация

`io.matrix.tsetlin.{TsetlinTrainer, TsetlinAutomaton, WisardProducer}` и `io.matrix.evolution.{MpdtGaProducer, EvolutionLoop,...}`.

Тесты: юнит каждого продюсера; `Exp010ComparisonTest` (WiSARD vs Tsetlin, 9 прогонов); `Exp002Exp003ProtocolTest` (сходимость).

## Метрики / гейты

- **H-010 accepted (synthetic-scope)**: medianSpeedup=242×, WiSARD 9/9 точность (см. EXP-010-report).
- **H-002/H-003 refuted-toy**: GA быстрее ×5–10, точнее на toy-уровне (см. EXP-002/003-report).
- **JMH-гейт Batch\*** (DESIGN-14 тесно связан): 32–69M ops/s → SIMD-утилиты оставлены.

## Отложено

- Самонаблюдение; GATopologySearch.
- Полный verdict — на доменных корпусах.