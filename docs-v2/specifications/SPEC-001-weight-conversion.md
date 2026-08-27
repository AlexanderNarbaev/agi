# SPEC-001 — Верифицируемая конвертация весов

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Spec описывает дистилляцию весов трансформеров в BIR-артефакты с измеримой fidelity. Этап A — карантин (никаких непроверенных артефактов в `/v1/models`). Этап B — экспорт DL-учителя → калибровочный корпус → дистиллятор → BIR + метрика fidelity ≥0.9 на holdout.

## Реализация в коде

Пакет `distill/` (`io.matrix.distill.*`):
- `Distiller(inputBits, threshold)` — `capture(long[], float[])`, `synthesize(provenance) → Bir`, `fidelity(Bir, long[][], float[][])` — метрики согласованности с учителем.
- `OnnxActivationTeacher(modelPath)` — JSR-ONNX обёртка, `inferBatch(float[][]) → float[]`.

Тесты: `DistillerTest` (synthetic), `OnnxActivationTeacherTest` (fail-fast), `Exp009bCpuVsOnnxTest` (CPU-side distillation + measurement).

## Эксперименты

- EXP-009 (H-009, running): preliminary 0.999 fidelity, latency ×149 vs ORT-CPU, ×276 vs GPU per-call (RTX 5070 Ti).

## BLOCKED-EXT / отложено

- Реальный LLM-срез (.onnx FFN): python-тулчейн + веса; инфраструктура (OnnxActivationTeacher) готова.
- Доменный калибровочный корпус; архивы в git-истории (удалены по директиве, восстанавливаются).
- Energy-метрики (10⁴× гейт H-009).

См. [engineering/PLAN.md](../engineering/PLAN.md).