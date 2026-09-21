# EXP-MATRIX.39 — QwenOnnxBridge (RUN 66)

## Hypothesis

End-to-end real LLM inference in Java — without Python in the
critical path — should be feasible using a bridge between
`BpeTokenizer` (vocab.json + merges.txt) and `OnnxRuntimeAdapter`
(model.onnx with optional CUDA execution).

## Setup

- **Bridge**: `QwenOnnxBridge` (`io.matrix.api`)
- **Tokenizer**: existing `BpeTokenizer` from `models/hf_cache/qwen05b`
- **ONNX**: existing `OnnxRuntimeAdapter` with `setUseGpu(true)`
- **Generation**: greedy autoregressive (argmax at each step)

## Test results (8/8 pass)

- `constructorSetsFields`
- `useGpuFlagToggles`
- `maxNewTokensClampedToRange`
- `infoBeforeLoad`
- `loadsTokenizerAndOnnx` (real model, GPU)
- `generatesTokensForPrompt` (real generation, GPU)
- `handlesMissingModelDir`
- `handlesMissingOnnxFile`

## What it does

```java
QwenOnnxBridge bridge = new QwenOnnxBridge(modelDir);
bridge.useGpu(true);            // RTX 5070 CUDA
bridge.setMaxNewTokens(64);
bridge.load();                  // tokenizer + ONNX
String reply = bridge.generate("Hello", 8);
```

Internally:
1. `tokenizer.encode(prompt)` → prompt token ids
2. Loop up to `maxTokens`:
   - `onnx.greedyNextToken(allIds)` → argmax of last logits
   - Stop on EOS (151645 = `<|im_end|>`)
   - Append to allIds
3. `tokenizer.decode(generatedIds)` → text

## Verdict

**Pure-Java inference pipeline WORKS**. The first class that turns
the ONNX adapter into a real production inference path: text in,
text out, GPU-accelerated, no Python anywhere.

## Cross-references

- EXP-MATRIX.37: GPU ONNX verification
- EXP-MATRIX.38: GPU vs CPU benchmark (15.40x speedup)
- EXP-MATRIX.35: HF + ONNX + GraalVM docs
