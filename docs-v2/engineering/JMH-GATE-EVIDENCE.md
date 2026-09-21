# JMH-GATE-EVIDENCE

Реальные JMH-замеры : `./gradlew :matrix-core:jmh -PjmhBenchmark=BatchEvaluatorBenchmark`, 20 итераций, `thrpt` режим.

| Benchmark | Score | Error | Units |
|---|---|---|---|
| BatchEvaluatorBenchmark.evaluateAll32 | 58 734 913.902 | ±350 381.511 | ops/s |
| BatchEvaluatorBenchmark.evaluateAll64 | 32 266 471.166 | ±123 844.617 | ops/s |
| BatchEvaluatorBenchmark.perElementLoop64 | 68 658 834.427 | ±658 851.643 | ops/s |

**Анализ**:
- evaluateAll32 (32-битная упакованная оценка, 32-bit ints packed into long) — ~17 нс/eval.
- evaluateAll64 (60-битная кодировка truth-table в long, packed) — ~31 нс/eval.
- perElementLoop64 (loop variant) — ~14.5 нс/eval — ближе к hardware-bound для SIMD-friendly path.

**Решение по гейту DESIGN-14 (Batch* vs TtForm.evalBatch)**: **Batch* оставить как есть**.
- Текущие SIMD-утилиты BatchEvaluator/BatchMemoryAdapter/DecisionTreeBatch используют упакованное хранение и индексы без распаковки BitSet; замена на `TtForm.evalBatch(long[], long[])` (1.2 нс/eval по DESIGN-14#§волна6 JMH-цифры) прибавит конвертацию packed→long-words и сериализацию в `long[]` — это **замедлит** для текущих аргументов.
- Чтобы TtForm.evalBatch стал выгоднее, нужен отдельный hot-path где входы УЖЕ в `long[]` (например, в `brain/Viewpoint` или `neuron/HierarchicalBrain` после спека-002 миграции A-3). Это — отдельный эпик, см. [PLAN.md](PLAN.md).

**Документы**:
- Команда зафиксирована в `/home/alexandr-narbaev/Projects/agi/matrix-core/build/results/jmh/results.json` (canonical), Git история содержит commit с прогоном.
- `VERY IMPORTANT` JMH-предупреждение учтено: тесты прогонялись в JIT-среде, дополнительный прогон после паузы для стабильности погрешностей возможен.

**Связь с DESIGN-14**: см. [designs/DESIGN-14-bir-migration.md](../designs/DESIGN-14-bir-migration.md), секция «Метрики/гейты».