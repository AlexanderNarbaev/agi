
## EXP-009B 2026-08-25 — первые измерения CPU-инференса (учитель ONNX → дистиллят BIR)

**Артефакты:** `scripts/gen_teacher_onnx.py` (FFN 16→64→GELU→64→1, детерминированный) → `models/teacher/teacher_ffn16.onnx`; харнесс `distill.Exp009bCpuVsOnnxTest` (N=2000, TRAIN/TEST split, порог=медиана активаций).

| Инференс N=2000, single-run | Время |
|---|---|
| ONNX Runtime CPU | 18.68 мс |
| **Дистиллят BIR (наш)** | **0.125 мс** |

**Speedup: ~149×** при согласованности с учителем **99.90%** (holdout).

**GPU:** CUDA EP недоступен в Java-стеке — «Failed to find CUDA shared provider» (нужен onnxruntime_gpu нативный + CUDA/cuDNN тулчейн). Установка тулчейна откроет GPU-ногу сравнения; CPU-нога уже измерена.

**Статус H-009:** running → preliminary support по латентности (×149 ≥ ×1000 гейта пока НЕ достигнут на этом микробенче; энергометрика не измерялась). Полный вердикт — после экспорта реального LLM-среза и energy-протокола.
