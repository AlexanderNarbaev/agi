# EXP-MATRIX.40 — Qwen end-to-end real LLM in Java (RUN 66-75)

## Hypothesis

Real LLM inference should be possible entirely in Java using
existing ONNX Runtime + BPE tokenizer infrastructure, with no
Python in the critical path.

## Pipeline

```
user text
  ↓ BpeTokenizer.encode
  ↓ QwenChatTemplate.buildUserPrompt
prompt string
  ↓ QwenOnnxBridge.generate
  ↓ OnnxRuntimeAdapter.greedyNextToken
  ↓ argmax(logits[-1])
  ↓ QwenChatTemplate.cleanReply
reply text
```

## Capabilities delivered (RUN 66-75)

| RUN | Class / Method | Tests |
|---|---|---|
| 66 | QwenOnnxBridge + OnnxRuntimeAdapter.greedyNextToken | 8 |
| 67 | OnnxChatResource (/v1/onnx/{chat,status,reload}) | 6 |
| 68 | generateSampled (temperature) | 11 |
| 69 | OnnxInferenceMetrics + /v1/onnx/metrics | 8 |
| 70 | generateSampled + top-k + top-p | 12 |
| 71 | /v1/onnx/generate (all sampling params) | 10 |
| 72 | QwenChatTemplate (ChatML formatter) | 12 |
| 73 | bridge.chat() — applies template | 14 |
| 74 | chatWithHistory() — multi-turn | 15 |
| 75 | /v1/onnx/chat (REST chat endpoint) | 14 |

Cumulative: **110 tests** across these RUNs.

## Verdict

**End-to-end real LLM inference in pure Java, GPU-accelerated,
works.** The chain layer can call Qwen2.5-0.5B via this bridge to
get trained-quality text generation. Throughput: 15.40x faster
than CPU on RTX 5070 (5ms p50 vs 77ms).

## Honest caveats

- Greedy/sampling generation is non-deterministic for temperature > 0.
- EOS detection uses fixed token id 151645 (<|im_end|>); some
  outputs may need post-processing.
- ONNX export was 2.5 GB; first inference is slow due to CUDA
  kernel compilation. Steady-state: 5ms/forward-pass.
- No quantization yet (BF16 only); INT8 would be ~3x faster.

## Cross-references

- EXP-MATRIX.37: GPU ONNX verification
- EXP-MATRIX.38: GPU vs CPU benchmark (15.40x)
- EXP-MATRIX.39: QwenOnnxBridge initial
- FINALSUMMARY §LXIX-LXXVII
