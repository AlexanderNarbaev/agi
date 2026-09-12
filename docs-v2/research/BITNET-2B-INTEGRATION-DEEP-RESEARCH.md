# BitNet b1.58 2B Integration Deep-Research Report

**Date:** 2026-09-12
**Status:** Model file downloaded (1.1 GB), header verified, weight format partially reverse-engineered. Full inference requires implementing BitNet transformer block.

## 1. Model Acquired

**Source:** `microsoft/bitnet-b1.58-2B-4T` from HuggingFace (Apache 2.0 license).
**Size:** 1.1 GB safetensors file (smaller than FP16 equivalent due to 1.58-bit weights).
**Download:** ~15 seconds on home internet.
**Path:** `/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/.../model.safetensors`

## 2. Model Architecture (verified from config.json)

```
hidden_size:            2560
num_hidden_layers:      30
num_attention_heads:    20
num_key_value_heads:    5   (GQA — grouped query attention)
intermediate_size:      6912 (FFN)
vocab_size:             128256
max_position_embeddings: 4096
hidden_act:             relu2 (squared ReLU)
torch_dtype:            bfloat16
quantization_config:    { method: bitnet, linear_class: autobitlinear, mode: offline }
```

Total parameters: ~2B (1.58 bits per weight + scales ≈ 400MB for weights + scales)

## 3. Weight Format Investigation (key finding)

### 3.1 File structure

542 tensors organized as:
- `model.embed_tokens.weight` — bf16 [128256, 2560]
- `model.layers.{0-29}.{input_layernorm, post_attention_layernorm, attn_sub_norm, ffn_sub_norm}.weight` — bf16 [2560 or 6912]
- `model.layers.{0-29}.self_attn.{q,k,v,o}_proj.{weight, weight_scale}` — uint8 + bf16
- `model.layers.{0-29}.mlp.{gate,up,down}_proj.{weight, weight_scale}` — uint8 + bf16

### 3.2 Unexpected byte values (key discovery)

When loading `model.layers.0.mlp.gate_proj.weight` (shape [1728, 2560], dtype uint8), the byte values are NOT 2-bit packed ternary {-1, 0, +1}. Instead, the byte distribution shows:

```
Most frequent values:
85  (0b01010101): 103380 occurrences
89  (0b01011001): 81866
101 (0b01100101): 81492
81  (0b01010001): 80848
86  (0b01010110): 80789
69  (0b01000101): 80422
149 (0b10010101): 80321
84  (0b01010100): 79810
21  (0b00010101): 79277
...
```

These byte values do NOT fit the simple ternary {-1, 0, +1} or 2-bit packed encoding. Possible explanations:

1. **Microsoft's actual format**: BitNet 2B may use a different encoding than the paper (e.g., per-block packing with metadata in high bits).
2. **Post-quantization transformation**: The values may be `uint8` representations of a packed 1.58-bit format with additional metadata bits.
3. **Different quantization scheme**: The model uses a custom `autobitlinear` class (per config), which may have a non-standard format.

### 3.3 What this means for integration

To fully integrate the BitNet 2B model into our Java inference pipeline, we need to:

1. **Reverse-engineer the exact byte format** — read the official `microsoft/BitNet` inference code (e.g., `bitnet.cpp`) to find the unpacking logic
2. **Implement transformer block** — BitNet attention + MLP with RoPE, ReLU², RMSNorm
3. **Implement tokenizer** — Llama-style BPE for 128k vocab
4. **Implement sampling** — temperature, top-p, etc.

This is a substantial engineering effort (~2-3 weeks of focused work). Current state: we have validated file loading via our existing SafetensorsReader.

## 4. What We Did Verify

- [x] Model file downloaded successfully
- [x] Safetensors header parses correctly (542 tensors)
- [x] Tensor names match expected architecture (embed_tokens, layer norms, attention/MLP projections)
- [x] Layer count = 30 (matches config.json)
- [x] Weight + scale pairs exist for all linear layers
- [x] Embedding shape [128256, 2560] matches config
- [x] File size ~1.1 GB (compressed 2B params at 1.58 bits)

## 5. Integration Roadmap (Future Work)

### Short term (next 1-2 weeks)
1. Read official microsoft/BitNet inference code
2. Reverse-engineer exact uint8 weight unpacking
3. Write Java unpacking utility matching Python reference

### Medium term (2-4 weeks)
1. Implement BitNet transformer block in Java
2. Tokenizer integration (Llama BPE)
3. Single-block inference (forward pass) test
4. Compare output with Python `transformers` library

### Long term (1-3 months)
1. Multi-block autoregressive generation
2. KV-cache for efficient inference
3. Quantized KV-cache (INT8 attention)
4. Performance optimization (GPU matmul via BitLinearGpu)

## 6. Performance Estimate (based on our BitLinear GPU benchmarks)

For BitNet 2B on RTX 5070 Ti:
- Per-token matmul ops: ~30 layers × 3 projections × (2560 × 6912) = ~1.6 GFLOPS
- At 11.5 GFLOPS sustained (4096×4096 benchmark), inference: ~7 tokens/sec
- With KV-cache and optimization: ~20-50 tokens/sec achievable

## 7. References

- Model: https://huggingface.co/microsoft/bitnet-b1.58-2B-4T
- Paper: Ma S. et al. (2024). *The Era of 1-bit LLMs*. arXiv:2402.17764.
- Official inference: https://github.com/microsoft/BitNet (bitnet.cpp)
- Our BitLinear: `matrix-core/src/main/java/io/matrix/neuron/BitLinear.java`
- Our GPU matmul: `matrix-core/src/main/java/io/matrix/imports/BitLinearGpu.java`
- Load test: `matrix-core/src/test/java/io/matrix/research/BitNetModelLoadTest.java`
