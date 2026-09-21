# H-006 — FROZEN-guardrail preregistration & gates

Протокол preregistered EXP-006: проверка композиционного guardrail между
FROZEN-BIR-ядром и обучаемыми слоями (этические гейты + structural-safety
+ lie-detector через BIR-composition). Цель — зафиксировать численные
gate-критерии и методику до запуска полного prod-трафика; preliminary unit-
замеры уже зелёные (см. row H-006 в реестре карточек), полный verdict —
production-domain.

## ID и привязка

- H-ID: H-006.
- EXP-ID: EXP-006.
- Соответствующий дизайн/спека (text-only): DESIGN-14 (BIR-миграция,
 FROZEN-guardrail контур), SPEC-005 (action gate), CONSTITUTION IV.
- Источник вердикта (text-only): research/HYPOTHESES.md, row «H-006 — BIR-
 composition guardrail», статус `running`.

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень доказательства |
|---|---|---|---|
| FPR @ TPR≥95% | FPR ≤ 5% | FPR > 10% | JMH-grade + prod-domain |
| TPR @ FPR≤5% | TPR ≥ 95% | TPR < 90% | JMH-grade + prod-domain |
| p99 latency JVM | ≤ 50 мс | > 100 мс | JMH-grade (warmup+forks) |
| p99 latency GPU-нога (если доступна CUDA) | ≤ 50 мс | > 100 мс | JMH-grade |
| Determinism replay | hash(p99-batch) стабилен на 1k повторов | любое расхождение | unit + JMH |
| Coverage | JaCoCo METHOD ≥ 82% (CONSTITUTION V) | < 80% | CI gate |
| False-negative на «Четырёх запретах» | 0 на fixed-corpus | > 0 | unit + prod-domain |

Preliminary unit-числа (см. row H-006): FPR 0%, TPR 100%, P99 0 мс — **не**
являются preregistered verdict; verdict требует multi-seed × multi-dataset
или prod-domain по methodology PROTOCOL.md.

## Methodology

- Артефакт: `bir/guardrail/CompositionGuard` + `FrozenFNLBenchmark`
 (matrix-core jmh-каталог).
- Корпус: fixed-corpus из 4 классов запретов (Четыре запрета CONSTITUTION
 IV) + синтетические негативы (seed 42/43/44); расширение — prod-трафик.
- Процедура: (1) прогрев JMH ≥ 25 итераций, форки ≥ 2; (2) 1000 повторов
 p99-замера; (3) проверка hash-реплея детерминизма; (4) отчёт
 median/IQR/макс.
- Baseline: «без guardrail» (whitelist-обход) — должно провалить TPR-гейт.
- Split: train/holdout на синтетике 70/30; prod-трафик — отдельный holdout.

## Prereqs

- Реализован `bir/guardrail/CompositionGuard` (есть, см. row H-006).
- Реализован `FrozenFNLBenchmark` (jmh-каталог, см. PROTOCOL.md).
- `JaCoCo` gate зелёный на `bir/guardrail/**`.
- INV-1 source-scan-страж: нет обходов whitelist вне `bir`, `ethics/
 frozen`, `neuron internals`, `TruthTableMinimizer`.
- GPU-нога — опционально; при отсутствии CUDA EP — только CPU-нога, gate
 p99 ≤ 50 мс остаётся.

## Methodology framework (text-only)

- Уровни доказательства и preregistration-rules — см. файл PROTOCOL.md в
 той же директории research/.
- Полный verdict (running → accepted/refuted/superseded) — только в
 HYPOTHESES.md.

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Preliminary unit-числа FPR 0%, TPR 100%, P99 0 мс — не preregistered
 verdict; не публикуются как accept до прохождения gate-таблицы.
- Никаких обещаний «безопасен по построению», «не лжёт», «не нарушает» —
 только проверяемое: механизм + измерение.
- Prod-domain verdict — отдельный holdout, не подменяет синтетику.

Next: после прохождения JMH-grade gate на полном synthetic-corpus
(multi-seed) — зафиксировать row H-006 в HYPOTHESES.md как
`accepted (synthetic-scope)` либо `refuted-toy`; prod-трафик — отдельный
EXP-006-prod цикл.