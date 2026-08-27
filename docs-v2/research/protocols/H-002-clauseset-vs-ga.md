# H-002 — CLAUSESET vs MPDT-GA preregistration & gates (EXP-002)

Протокол preregistered EXP-002: CLAUSESET (Tsetlin) vs MPDT-GA producer
на synthetic-scope. Текущий verdict `refuted-toy `: GA быстрее
×5.5 в среднем, точнее на +7.9 п.п., компактнее в тысячи–миллионы раз.
Карточка фиксирует gate-критерии для повторного прогона при изменении
продюсеров или расширении корпуса.

## ID и привязка

- H-ID: H-002.
- EXP-ID: EXP-002.
- Соответствующий дизайн/спека (text-only): DESIGN-04 (продюсеры,
 `TsetlinTrainer`, `MpdtGaProducer`), CONSTITUTION II (K_MAX=20).
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-002 —
 CLAUSESET (Tsetlin) бьёт MPDT-GA», статус `refuted-toy `.
- Источник чисел (text-only): research/reports/EXP-002-report.md
 (3 синтетических датасета, seed 42; среднее acc Tsetlin.7833 / GA
.8625; speedup GA ×5.5; сходимость Tsetlin to99 672.7 / GA 346.3).

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept H-002) | Gate (refute H-002) | Уровень |
|---|---|---|---|
| Train speedup Tsetlin vs GA | ≥ 10× | < 5× | multi-dataset |
| Quality (acc) advantage Tsetlin − GA | ≥ 0 п.п. | < −2 п.п. | multi-dataset |
| Artifact size ratio Tsetlin/GA | ≤ 100 (Tsetlin компактнее) | > 1000 | multi-dataset |
| `examplesTo99TrainAcc` Tsetlin vs GA | Tsetlin ≤ GA (или ≤ 1.5×) | Tsetlin > 2× GA | multi-dataset |
| `reached99` rate Tsetlin | ≥ 2/3 датасетов | 0/3 | multi-dataset |
| Determinism | hash стабилен | любое расхождение | unit |
| JaCoCo gate | ≥ 82% на `evolution/**`, `tsetlin/**` | < 80% | CI |

Preliminary числа (см. EXP-002-report): GA ×5.5 быстрее, +7.9 п.п.
точнее, компактнее в тысячи раз — refutes H-002 на всех трёх gate-осях.

## Methodology

- Артефакты: `evolution/MpdtGaProducer` (детерминированный GA: элитизм
 25%, турнир, per-clause кроссовер, мутация p=1/K, MDL-давление λ=0.1);
 `TsetlinTrainer` с grid-tuning (clauses×epochs×S); `Exp002ComparisonTest`
 + `Exp002Exp003ProtocolTest`.
- API producer: `trainBatch(long[] inputs, boolean[] labels, int
 generations)`, `predict(long packed)`, `toDecisionClauseSet(provenance)`,
 `literalCount()`.
- Корпус: 3 синтетических датасета (16/10, 16/12, 20/14), seed 42.
- Процедура: (1) grid-tuning Tsetlin по TRAIN-acc; (2) full-train
 wall-clock на каждом датасете; (3) holdout-acc; (4) `examplesTo99TrainAcc`
 удвоением подвыборок 20→320; (5) literal count.
- Baseline: TsetlinTrainer (clauses grid-best по train-acc).

## Prereqs

- Реализован `MpdtGaProducer` (есть, см. EXP-002-report).
- `Exp002ComparisonTest` + `Exp002Exp003ProtocolTest` зелёные.
- JaCoCo gate ≥ 82% (CONSTITUTION V).
- 3 синтетических датасета фиксированы; расширение — отдельный research
 wave (доменные корпуса заблокированы: BLOCKED-EXT: данные).

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict (`refuted-toy`) уже зафиксирован; для полного `accepted`
 или `refuted` нужен production-domain (см. EXP-002-report раздел
 «Ограничения»).

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- `refuted-toy` не экстраполируется на prod: «GA всегда лучше Tsetlin»
 не публикуется.
- Tюнинг Tsetlin (балансы пар автоматов, S-параметр) выполнен минимально;
 исчерпывающий тюнинг — отдельный research wave.
- Синтетика — 3 крошечных датасета; это честно фиксируется.

Next: при изменении `MpdtGaProducer` или `TsetlinTrainer` — повторный
3-dataset прогон + проверка gate-таблицы; полный prod-domain verdict —
когда появятся доменные корпуса (отложено).