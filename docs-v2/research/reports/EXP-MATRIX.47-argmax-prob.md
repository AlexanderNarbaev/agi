# EXP-MATRIX.47 — Argmax probability calibration (RUN 92)

## Hypothesis

Real LLMs should have calibrated confidence — when the model
predicts a token with high probability, it should be correct
more often than when it predicts with low probability.

## Setup

- **Qwen2.5-0.5B-Instruct** on GPU via ONNX Runtime
- **5-turn diverse conversation** (greeting, personal, recall, factual, math)
- **generateWithProbs** records argmax probability per token

## Findings

- Argmax probability tracking added to OnnxInferenceMetrics
- Average probability per token ≈ typical LLM behavior (high
  probability on confident continuations, lower on uncertain ones)
- Wired into the metrics endpoint `/v1/onnx/metrics`

## Use case

This data feeds calibration experiments like H-044 (ECE).
Future work can compare ONNX GPU argmax probs against actual
ground-truth correctness to compute Expected Calibration Error
(ECE) on real LLM outputs.

## Cross-references

- EXP-MATRIX.44: 5-turn conversation EXP
- EXP-MATRIX.40: end-to-end pipeline
- H-044: calibration hypothesis

## Code

- `OnnxInferenceMetrics.recordArgmaxProbability(prob)`
- `OnnxInferenceMetrics.avgArgmaxProbability()`
- `QwenOnnxBridge.generateWithProbs()` calls
  `metrics.recordArgmaxProbability(...)` per token
