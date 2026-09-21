# PROTOCOL — методология экспериментов

Единая система измерений для `H-*` карточек. Все числа в отчётах получаются по этим правилам.

## Preregistration

Карточка гипотезы в `HYPOTHESES.md` фиксирует **до** запуска:
- Метрика (accuracy, latency, energy, etc.).
- Критерий accept/refute (численные пороги).
- Baseline (что с чем сравниваем).
- Размер датасета + split (train/test/holdout).
- Фиксация seed (deterministic по умолчанию).

## Уровни доказательства

| Уровень | Что значит | Что попадает в verdict |
|---|---|---|
| **single-run JVM** | наноtime-замеры на одной JVM-прогонке (warmup отсутствует) | preliminary числа; НЕ даёт accept |
| **JMH-grade** | `org.openjdk.jmh` с прогревом, форками, погрешностями | gate для приёмочных критериев (напр. ≤10% latency) |
| **multi-dataset × seeds** | сетка датасетов с несколькими seed | preliminary support/refute с пометкой «synthetic-scope» |
| **production-domain** | реальные нагрузки, домен | полный verdict |

## «synthetic-scope» — что это значит

Все подтверждающие измерения проекта на получены на синтетических датасетах (seed-controlled bit generators), не на реальных доменах. Выводы верны в той мере, в которой синтетика отражает домен — этого нам не известно. Это честно фиксируется в каждом отчёте.

## Где смотреть JMH-инфраструктуру

- `matrix-core/src/jmh/java/io/matrix/bir/BirEvaluateBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/FrozenFNLBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/CompressionBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/BinaryBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/NeuronHotPathBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/SimdTruthTableBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/HashChainBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/NeuralBrainBenchmark.java`
- `matrix-core/src/jmh/java/io/matrix/benchmark/BatchEvaluatorBenchmark.java`

Запуск: `./gradlew :matrix-core:jmh -PjmhBenchmark=Name`. См. [engineering/JMH-GATE-EVIDENCE.md](../engineering/JMH-GATE-EVIDENCE.md).

## Полный verdict — только в `HYPOTHESES.md`

Карточка кардинально (running → accepted/refuted/superseded) обновляется после:
1. успешного JMH-grade gate на нужных метриках;
2. либо multi-dataset × seeds для «preliminary support»;
3. либо production-domain для полного verdict.

Все синтетические результаты держат карточку на **running** или помечают «refuted-toy» с пинами в `tests/`.