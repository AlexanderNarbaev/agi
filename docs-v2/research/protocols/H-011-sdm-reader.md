# H-011 — SDM M1 read beats flat top-K Hamming precision@5 preregistration & gates (EXP-011)

**Статус: normative · preregistration card** · пересмотр 2026-08-26 — brain wave v3 protocols · changelog 2026-08-26 — brain wave v3 protocols.

Протокол preregistered EXP-011: SDM M1 read (counter + Kanerva
radius-threshold) vs flat top-K по Хэммингу при одинаковом бюджете
памяти. Целевая метрика — precision@5 на корпусе эпизодов владельца.
Running-статус: `SdmReader` реализован; контрольного flat-baseline
бенча и корпуса эпизодов ещё нет → карточка фиксирует gate-критерии.

## ID и привязка

- H-ID: H-011.
- EXP-ID: EXP-011.
- Соответствующий дизайн/спека (text-only): DESIGN-05 (memory M1,
  `SdmReader`), SPEC-003 (knowledge topology, как вспомогательная
  диагностика drift), CONSTITUTION II/VI.
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-011 —
  SDM M1 read beats flat top-K Hamming precision@5», статус `running`.
- Источник чисел (text-only): research/reports/EXP-011-report.md — файл
  отсутствует на 2026-08-26, см. секцию «Ограничения».

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень |
|---|---|---|---|
| Precision@5 advantage (SDM − flat) | ≥ +5 п.п. | < +2 п.п. | multi-seed |
| Recall@5 advantage (companion) | ≥ +3 п.п. | < +1 п.п. | multi-seed |
| Memory budget parity | одинаковое число записей M1 | любое расхождение | unit |
| Latency p99 SDM read | ≤ 2× flat baseline | > 5× flat | JMH-grade |
| Kanerva radius threshold стабильность | std ≤ 0.02 на 5 seeds | std > 0.05 | multi-seed |
| Counter overflow guard | счётчики ограничены ≤ max-count | любое переполнение | unit |
| Determinism | hash SDM-выдачи стабилен | любое расхождение | unit |
| No-LLM constraint | без сетевых LLM в recall | любой LLM-call | unit |

## Methodology

- Артефакты: `memory/SdmReader` (SDM-счётчики + Kanerva radius +
  threshold), flat-top-K Hamming baseline (отдельный класс в `memory/`,
  фиксируется до запуска), `Exp011ComparisonTest`.
- Корпус: эпизоды владельца (фиксируется до запуска; hash в pre-
  registration); split 70/15/15 train/holdout/test.
- Процедура: (1) оба режима тренируются на train-эпизодах; (2) на
  test выдают top-5 (SDM через radius, flat через Хэмминг-расстояние);
  (3) precision@5, recall@5 по golden-меткам; (4) latency JMH-grade
  (warmup + forks); (5) determinism на 1k повторов.
- Memory budget: одинаковое число записей (N фиксируется до запуска;
  предварительно N = 10⁴).
- Radius-threshold подбирается на holdout grid-search (5 значений), затем
  фиксируется для test.

## Prereqs

- Реализован `SdmReader` (есть).
- Flat top-K Hamming baseline реализуется в отдельном `HammingFlatReader`
  (на 2026-08-26 не выделен; BLOCKED-EXT: код).
- `Exp011ComparisonTest` — на 2026-08-26 нет; пишется вместе с
  baseline.
- JaCoCo gate ≥ 82% на `memory/SdmReader.java` + новый baseline
  (CONSTITUTION V).
- Multi-seed: минимум 3 seed (42, 43, 44) для preliminary verdict.

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict — только в HYPOTHESES.md (running → accepted/refuted).
- Synthetic-scope пометка обязательна для любого preliminary verdict.

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Running-статус не экстраполируется: «SDM M1 всегда лучше flat» не
  публикуется до multi-seed замера.
- Precision@5 advantage ≠ общая SDM-польза: H-011 узкое, про @5.
- Radius-threshold grid-search выполняется один раз на holdout → любое
  изменение после test = отдельный EXP со статусом superseded.
- Flat baseline — это top-K по Хэммингу, не «без структуры вообще»;
  сравнение с произвольным random-выбором — отдельный EXP.

## Ограничения (честный running-status на 2026-08-26)

- EXP-011 прогон не выполнен → файл `research/reports/EXP-011-report.md`
  отсутствует. Любые числа выше помечены как gate-критерии, не
  наблюдения. Без flat-baseline класса и golden-эпизодов карточка
  остаётся `running` без preliminary verdict.

Next: реализовать `HammingFlatReader` + `Exp011ComparisonTest`; зафиксировать
golden-эпизоды + 3 seed; multi-seed прогон → перевод row H-011 в
`accepted (synthetic-scope)` либо `refuted-toy` в HYPOTHESES.md.