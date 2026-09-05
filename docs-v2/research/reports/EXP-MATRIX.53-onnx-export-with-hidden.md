# EXP-MATRIX.53 — ONNX export with hidden_states (RUN 108)

## Hypothesis

Re-exporting Qwen2.5-0.5B ONNX model with hidden_states output
should expose the per-token hidden representations for true
embedding similarity computation.

## Setup

- Used optimum-cli to re-export with output config
- Result: `models/onnx/qwen05b_hidden/model.onnx` (1.1 MB)
- Plus `model.onnx_data` (2.5 GB)
- Verified exported model inputs/outputs

## Finding

The standard `optimum-cli export onnx` does NOT expose hidden_states
by default. Only `logits` is the output, just like the original
export.

```python
Outputs:
  logits: tensor_type (1, sequence_length, 151936)
```

To get true embeddings, we'd need to either:
1. Modify the model's forward method to return hidden_states
2. Use a custom ONNX export with `output_hidden_states=True` config
3. Use a different export tool

## Verdict

Honest finding: real semantic similarity embeddings from Java/ONNX
require additional export work. The current `TextEmbedder` uses
a placeholder approach that produces normalized but semantically
empty vectors.

## Cross-references

- EXP-MATRIX.52: Embedding similarity EXP (showing 0.0 cosine)
- TextEmbedder.java: current placeholder implementation
