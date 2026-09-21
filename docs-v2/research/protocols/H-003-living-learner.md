# H-003 — Living-learner vs Tsetlin preregistration & gates (EXP-003)

Протокол preregistered EXP-003: «живой» обучатель (MpdtGaProducer, GA)
vs TsetlinTrainer как producer. Текущий verdict `refuted-toy `:
на toy-шкале GA оправдан и превосходит Tsetlin на всех трёх датасетах
(быстрее, точнее, сходится чаще). Карточка фиксирует gate-критерии для
повторного прогона.

## ID и привязка

- H-ID: H-003.
- EXP-ID: EXP-003.
- Соответствующий дизайн/спека (text-only): DESIGN-04 (продюсеры,
 `MpdtGaProducer`, `TsetlinTrainer`), CONSTITUTION II (K_MAX=20), III
 (Φ-functional в обучающем цикле).
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-003 —
 ни один «живой» обучатель не оправдан против Tsetlin», статус
 `refuted-toy `.
- Источник чисел (text-only): research/reports/EXP-003-report.md
 (3 датасета seed 42; среднее to99 Tsetlin 672.7 / GA 346.3;
 `reached99` Tsetlin 1/3, GA 2/3; средний testAcc Tsetlin.7833 /
 GA.8625).

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept H-003) | Gate (refute H-003) | Уровень |
|---|---|---|---|
| `examplesTo99TrainAcc` GA vs Tsetlin | GA ≥ Tsetlin (median) | GA > 2× Tsetlin (в худшую сторону) | multi-dataset |
| `reached99` rate | GA ≥ 2/3 | GA 0/3 | multi-dataset |
| Holdout-acc GA − Tsetlin | GA ≥ Tsetlin (median) | GA < Tsetlin −2 п.п. | multi-dataset |
| Full-train wall-clock | не регламентируется (побочная) | — | unit |
| Φ-functional объявлен до запуска | Φ зафиксирован в комментарии EXP | Φ не объявлен | CONSTITUTION III |
| Determinism | hash стабилен | любое расхождение | unit |
| JaCoCo gate | ≥ 82% | < 80% | CI |

Preliminary числа (см. EXP-003-report): GA выигрывает по to99 в 2/3
датасетов, holdout GA.8625 vs Tsetlin.7833 — refutes H-003 на toy.

## Methodology

- Артефакты: `evolution/MpdtGaProducer` (12 клауз, попул 40, gen 30);
 `TsetlinTrainer` grid-best; `Exp002Exp003ProtocolTest`.
- Корпус: 3 синтетических датасета (16/10, 16/12, 20/14), seed 42;
 удвоение подвыборок 20→320 для `examplesTo99TrainAcc`.
- Процедура: (1) grid-tuning Tsetlin по TRAIN-acc; (2) full-train
 wall-clock; (3) holdout-acc; (4) `examplesTo99TrainAcc` на расширяющейся
 подвыборке до достижения 0.99 или 320.
- Baseline: TsetlinTrainer (grid-best по train-acc).
- Φ-functional (CONSTITUTION III): монотонный `Φ = trainAcc на расширяющейся
 подвыборке` — фиксируется до запуска в комментарии теста.

## Prereqs

- Реализован `MpdtGaProducer` (есть, см. EXP-003-report).
- `Exp002Exp003ProtocolTest` зелёный с 3 синтетическими датасетами.
- Φ объявлен в коде/комментарии (CONSTITUTION III).
- JaCoCo gate ≥ 82% (CONSTITUTION V).
- Расширение — отдельный research wave (доменные корпуса заблокированы).

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict (`refuted-toy`) уже зафиксирован; для полного `accepted`
 или `refuted` нужен production-domain (см. EXP-003-report раздел
 «Ограничения»).

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- `refuted-toy` не экстраполируется на prod: «живые обучатели всегда
 оправданы» не публикуется.
- 3 крошечных синтетических датасета — это честно фиксируется.
- Tюнинг Tsetlin минимален; исчерпывающий тюнинг — отдельный research
 wave.
- Самонаблюдение и GATopologySearch из DESIGN-04 — отложены, не входят в
 эту карточку.

Next: при изменении `MpdtGaProducer` или `TsetlinTrainer` — повторный
3-dataset прогон + проверка gate-таблицы; полный prod-domain verdict —
когда появятся доменные корпуса (отложено).