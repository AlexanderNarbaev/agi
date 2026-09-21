# EXP-009 Report — дистилляция в MATRIX, CPU+GPU-нога

**Статус: running** · Гипотеза: H-009 (BIR-classifier parity ±3 п.п. с LLM ≤3B при ≥10⁴× меньше энергии, ≥10³× меньше latency).

## Артефакты

- `scripts/gen_teacher_onnx.py` (research-only) — генерирует детерминированный FFN 16→64→GELU→64→1 (GELU = erf-композиция), seed=42; веса в `models/teacher/teacher_ffn16.onnx` (~5 KB).
- `scripts/bench_gpu_vs_bir.py` — torch cu128, та же функция, GPU-нога.
- `matrix-core/src/test/java/io/matrix/distill/Exp009bCpuVsOnnxTest.java` — distiller + цикл на JVM.
- `matrix-core/src/main/java/io/matrix/distill/OnnxActivationTeacher.java` — JSR-223/ONNX обёртка (API: `inferBatch(float[][])`).

## EXP-009B — CPU-нога (seed 42, N=2000)

| Инференс | Время | Согласованность с учителем (holdout) |
|---|---|---|
| ONNX Runtime CPU | 18.68 мс | эталон (fidelity к дистилляту 0.999) |
| **MATRIX BIR CPU** | **0.125 мс (~62 нс/eval)** | **99.90%** |

**Speedup: ×149** при fidelity.999. CUDA-EP: «Failed to find CUDA shared provider» в Java-стеке → GPU-нога через Java недоступна, требует нативного `onnxruntime_gpu`.

## EXP-009C — GPU-нога (RTX 5070 Ti, torch 2.12.1+cu130, fp32)

Та же функция учителя (веса из `teacher_ffn16.onnx`), 50 прогрев + 200 повторов батча, 1000 повторов одиночных.

| Инференс | Время |
|---|---|
| GPU батч N=2000 | **0.0200 мс** |
| GPU per-call одиночный | **17.25 µs** |
| MATRIX BIR CPU батч 2000 | 0.125 мс |
| ORT CPU батч 2000 | 18.68 мс |

**Сравнение с MATRIX BIR (per-call)**:
- GPU × ~276 медленнее BIR (17.25 µs против 62 нс) — для точечных одиночных вызовов GPU launch-overhead доминирует.
- GPU ×6.25 быстрее BIR на крупном батче (0.02 мс против 0.125 мс).

**Честный вывод**: проектный тезис подтверждён — для индивидуальных запросов скомпилированная булева функция на CPU даёт латентность на 2 порядка лучше, чем даже GPU-инференс нейростека; GPU выигрывает только при крупнобатчевой пропускной способности.

## Статус H-009

`running` — preliminary support (×149 latency,.999 fidelity) на синтетике. Полный verdict требует:
- Реальный LLM-срез (а не синтетический FFN16).
- Energy-метрики (10⁴× гейт).
- Production-domain dataset.

## Дальше
- Реальный.onnx экспорт (Qwen-0.6B FFN) — когда доступен python-тулчейн + веса.
- Проверка CUDA-ноги через Java `onnxruntime_gpu` (нужен системный CUDA 12 + cuDNN9).
- Энергия: пройти через wattmeter или модельное энергопотребление; текущая среда — домашний notebook, измерения не репрезентативны.