# H-009 — Distillation-to-BIR preregistration & gates (EXP-009B/C)

Протокол preregistered EXP-009B (CPU-нога, JVM-distiller) и EXP-009C
(GPU-нога, batch-throughput) для гипотезы H-009: BIR-classifier parity
±3 п.п. с LLM ≤3B при ≥10⁴× меньше энергии и ≥10³× меньше latency.
Preliminary CPU-замеры ×149 latency при fidelity.999 (EXP-009B );
GPU-нога (EXP-009C ) — см. текстовые ссылки в источниках.

## ID и привязка

- H-ID: H-009.
- EXP-ID: EXP-009B (CPU), EXP-009C (GPU batch/per-call).
- Соответствующий дизайн/спека (text-only): DESIGN-04 (продюсеры,
 distill-stage), DESIGN-14 (BIR-migration), SPEC-002 (Boolean compute
 layer, K_MAX=20), CONSTITUTION I/II/VI.
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-009 —
 BIR-classifier parity»; статус `running` (preliminary EXP-009B/C).
- Источник чисел (text-only): research/reports/EXP-009-report.md
 (CPU ×149 latency, fidelity.999; GPU batch 0.02 мс, per-call 17.25 µs).

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень доказательства |
|---|---|---|---|
| Latency CPU per-call | ≤ 1 мс | > 10 мс | JMH-grade |
| Latency GPU per-call | ≤ 1 мс (целевой ×10³ gate) | > 10 мс | JMH-grade |
| Latency CPU batch 2000 | ≤ 0.5 мс | > 5 мс | JMH-grade |
| Latency GPU batch 2000 | ≤ 0.05 мс | > 1 мс | JMH-grade |
| Speedup vs ORT-CPU baseline | ≥ 10³× per-call, ≥ 10× batch | < 100× / < 5× | JMH-grade |
| Fidelity vs teacher (holdout) | ≥ 0.99 | < 0.97 | unit + multi-seed |
| Energy vs teacher (estimated/wattmeter) | ≥ 10⁴× меньше | < 10³× | prod-domain |
| K_MAX-конформность | compile через `bir/BirCompiler` | нарушение | unit |
| Determinism | hash на holdout стабилен на 1k повторов | любое расхождение | unit + JMH |

## Methodology

- Артефакты: `matrix-core/.../distill/{OnnxActivationTeacher,
 CpuDistiller}`; bench-скрипт `scripts/bench_gpu_vs_bir.py` (research-
 only).
- Teacher: детерминированный FFN 16→64→GELU→64→1, seed=42, веса в
 `models/teacher/teacher_ffn16.onnx` (~5 KB). Holdout — отдельный split.
- CPU-нога (EXP-009B): JSR-223/ONNX-обёртка, `inferBatch(float[][])`;
 N=2000; сравнение с ORT-CPU.
- GPU-нога (EXP-009C): torch 2.12.1+cu130, fp32, RTX-class GPU; 50
 прогрев + 200 batch-повторов + 1000 per-call-повторов.
- Split: train/holdout 70/30 на синтетике; расширение — реальный LLM-
 срез (Qwen-0.6B FFN, заблокировано python-тулчейном).
- Energy: wattmeter на нотубуке (не репрезентативно для prod) → заменить
 prod-замером или модельной оценкой потребления.

## Prereqs

- Реализован `OnnxActivationTeacher` и `CpuDistiller` (есть).
- `teacher_ffn16.onnx` детерминированно сгенерирован `gen_teacher_onnx.py`.
- EXP-009C требует torch+CUDA, EP для Java-ONNX (`onnxruntime_gpu`) —
 отдельный prereq (на недоступно: «Failed to find CUDA
 shared provider»).
- JaCoCo gate ≥ 82% на `distill/**` (CONSTITUTION V).
- Multi-seed: минимум 3 seed (42, 43, 44) для preliminary verdict.

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict — только в HYPOTHESES.md (running → accepted/refuted).

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Preliminary ×149 /.999 — preliminary CPU-нога; **не** preregistered
 verdict по energy-гейту 10⁴×.
- Никаких «BIR заменяет LLM во всём», «на 4 порядка меньше энергии
 доказано» — только по измеренному gate-табелю.
- GPU-нога ×276 медленнее BIR per-call (launch-overhead доминирует) — это
 наблюдение, а не «GPU проигрывает всегда».

Next: реальный.onnx экспорт (Qwen-0.6B FFN) + энергетический wattmeter-
замер; затем multi-seed JMH-grade → перевод row H-009 в
`accepted (synthetic-scope)` либо `refuted-toy` в HYPOTHESES.md.