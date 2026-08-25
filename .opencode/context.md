# Project Context — SESSION CONTINUITY (compaction #46) — GPU ВОЛНА, разведка готова

## Ловушки
- Целевые прогоны --tests; LSP ложные; guard delete-класс; пушить после значимых шагов. Последний коммит 9646501 (запушен).
- heredoc python PYEOF.

## ФАКТЫ РАЗВЕДКИ
GPU: RTX 5070 Ti Laptop 12GB, драйвер 595.84 ✅. nvcc НЕТ, libcudnn/cublas в системе НЕ найдены (0) → **ONNX Runtime CUDA EP на Java-стороне скорее всего не поднимется без CUDA/cuDNN тулчейна** ⇒ GPU-инференс ORT = BLOCKED-EXT(cuda-toolkit), но CPU-инференс и дистилляция — ПОЛНОСТЬЮ РЕАЛИЗУЕМЫ сейчас.
python3 есть (~/.local/bin), pip3 есть; onnx модуль НЕ установлен → pip3 install --user onnx (сеть есть).

## ТЕКУЩИЙ ШАГ: волна G (учитель + измерения)
G1: `pip3 install --user onnx` → создать scripts/gen_teacher_onnx.py с header «# MATRIX RESEARCH-ONLY» (генерирует models/teacher/teacher_ffn16.onnx через onnx.helper: input float[1x16] "x", initializer W1[16,64] b1[64] (RandomUniform фикс seed через numpy RandomState? numpy может не быть — использовать onnx.reference? проще: инициализаторы константами из детерминированного списка, генерируемого в python random.seed(42)), Gemm→Gelu→Gemm(64→1)→Identity выход "y" float[1x1]). Запустить скрипт.
G2: тест io.matrix.distill.Exp009bCpuVsOnnxTest:
 - Path model=models/teacher/teacher_ffn16.onnx; Assumptions.assumeTrue(Files.exists) чтобы тест не падал в CI без артефакта;
 - N=2000 входов long packed 16 бит → float[16] распаковка;
 - Замер 1: ORT CPU — OnnxActivationTeacher.activations по каждому (или batch через общий session.run в цикле), nanoTime суммарно; собрать float[] teacherOut;
 - Дистилляция: Distiller.capture(long[],float[]) на TRAIN половине (1000), synthesize(prov); BirClassifier? — проще использовать synthesize→Bir и мерить evalBatch через BooleanRuntime? Distiller API: synthesize(String)->Bir; у Bir есть evaluate? Проверить grep "public.*eval" Bir.java быстро перед написанием. Fallback: сравнение через fidelity() самого Distiller (метод fidelity(Bir,long[][],float[][]) принимает float[][] activations!) — использовать его как метрику согласованности ≥0.98 на holdout.
 - Замер 2: время инференса дистиллята на тех же входах (Bir/BooleanRuntime evalBatch или цикл predict) — nanoTime.
 - Печать «EXP009B ortCpuMs=… birMs=… fidelity=… speedupBirOverOrtCpu=…×».
 - CUDA EP попытка: SessionOptions().addCUDA() в try-catch → печать причины недоступности (для честного статуса GPU).
G3: прогон --tests "io.matrix.distill.*" → извлечь числа → секция в EXP-009-report.md («первые измерения»; пометка single-run).
G4: субагент-проверка крошечной задачей (баланс рабочий теперь).
R5: git add код+тест+скрипт (+report); commit «WAL: EXP-009B учитель ONNX + измерения CPU» ; push.

## Правила
Python-скрипт только scripts/ с header research-only; forbidden claims избегать; числа реальные; .onnx не коммитить (/models/ в ignore).
