# H-015 — Binary intESN within 3 п.п. of float-ESN at ≥10× less energy preregistration & gates (EXP-015)

**Статус: normative · preregistration card** · пересмотр 2026-08-26 — brain wave v3 protocols · changelog 2026-08-26 — brain wave v3 protocols.

Протокол preregistered EXP-015: бинарный intESN (VSA-state +
перестановочная рекуррентность + TT/WNN-readout, фиксированный seed)
vs float-ESN той же ёмкости на временны́х доменах. Running-статус:
`IntEsNetwork` реализован; float-ESN baseline и energy-инфра ещё не
зафиксированы → карточка фиксирует gate-критерии.

## ID и привязка

- H-ID: H-015.
- EXP-ID: EXP-015.
- Соответствующий дизайн/спека (text-only): DESIGN-10 (бинарный
  резервуар, `IntEsNetwork`, D=8192, bundle+циклический сдвиг +
  упакованное хранение в `long[]`), DESIGN-04 (readout-обучение через
  TT/WNN), CONSTITUTION II/IV/VI.
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-015 —
  Binary intESN reservoir within 3 п.п. of float-ESN at ≥10× less
  energy», статус `running`.
- Источник чисел (text-only): research/reports/EXP-015-report.md — файл
  отсутствует на 2026-08-26, см. секцию «Ограничения».

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень |
|---|---|---|---|
| Accuracy gap (float-ESN − intESN) | ≤ 3 п.п. на ≥ 2 temporal доменах | > 10 п.п. на любом | multi-seed |
| Energy per decision | ≤ 0.1× float-ESN (≥ 10× less) | ≥ 0.5× float-ESN | prod-domain |
| Determinism (5 seeds, bit-equal traces) | полная повторяемость | любое расхождение | unit + JMH |
| Reservoir capacity parity | D ∈ {2048, 4096, 8192} для обоих | любое расхождение | unit |
| Readout training (one-pass TT/WNN) | до 10⁴ примеров до 99% | > 10⁵ примеров | multi-seed |
| Latency p99 inference | ≤ 1 мс JVM | > 10 мс | JMH-grade |
| State-bit stability (D=8192) | 0/1 long[] корректно | bit corruption | unit + jqwik |
| Seed-bits reproducibility | hash трассы стабилен на 1k повторов | любое расхождение | unit |

## Methodology

- Артефакты: `reservoir/IntEsNetwork` (bundle + XOR-binding +
  permutation ρ, хранение в `long[]`), `tsetlin/{TsetlinTrainer,
  WisardProducer}` для readout, отдельный `FloatEsNetwork` baseline
  (на 2026-08-26 BLOCKED-EXT: код).
- Корпус: ≥ 2 temporal-домена (фиксируется до запуска): синтетический
  temporal-XOR + один из {SCADA event sequences, cluster telemetry};
  split 70/15/15 train/holdout/test.
- Процедура: (1) оба резервуара одной ёмкости D; (2) readout
  учится однопроходово TT/WNN на train; (3) accuracy на test;
  (4) energy-per-decision (модельная оценка ops×нДж vs FLOP×пДж, см.
  EXP-009 framing; wattmeter — опционально); (5) determinism на 1k
  повторов.
- Energy: модельная оценка по `ANALYSIS §5` (ops × нДж) для
  preliminary; wattmeter-замер — для prod-domain.
- Float-ESN baseline: внешняя реализация либо своя минимальная
  (PyTorch/NumPy); числа помечаются «external-reference» с источником.

## Prereqs

- Реализован `IntEsNetwork` (есть, см. DESIGN-10).
- Float-ESN baseline — на 2026-08-26 BLOCKED-EXT: код (минимальная
  реализация для JMH-grade).
- Temporal-XOR синтетика seed 42/43/44 — фиксируется до запуска.
- SCADA/cluster-telemetry — BLOCKED-EXT: данные (доменный корпус).
- JaCoCo gate ≥ 82% на `reservoir/IntEsNetwork.java`
  (CONSTITUTION V).
- Determinism: `IntEsNetwork(2048, 32, seed)` повторяем на одних входах
  (базовый smoke-test, на 2026-08-26 done).

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict — только в HYPOTHESES.md (running → accepted/refuted).
- Energy-метрика пока preliminary модельная; wattmeter — отдельный
  research wave с собственным preregistration.

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Running-статус не экстраполируется: «intESN быстрее float-ESN в 10×»
  не публикуется до замера на обоих доменах.
- Модельная energy-оценка ≠ реальная wattmeter; показанное «10× less»
  на модели — не production-grade.
- Determinism на одном seed не имплицирует детерминизм на любом seed;
  5 seed — gate, не наблюдение.
- Бинарный резервуар ≠ float-ESN с ограниченной точностью: «3 п.п.»
  относится к accuracy, не к внутренним состояниям.

## Ограничения (честный running-status на 2026-08-26)

- EXP-015 прогон не выполнен → файл `research/reports/EXP-015-report.md`
  отсутствует. Любые числа выше помечены как gate-критерии, не
  наблюдения. Без float-ESN baseline (BLOCKED-EXT: код) и SCADA/cluster
  корпуса (BLOCKED-EXT: данные) карточка остаётся `running` без
  preliminary verdict.

Next: реализовать минимальный `FloatEsNetwork` baseline + temporal-XOR
синтетику; multi-seed прогон на синтетике → preliminary `accepted
(synthetic-scope)` либо `refuted-toy`; wattmeter-замер и домен — отдельный
research wave.