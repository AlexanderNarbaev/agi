# EXP-MATRIX.38 — GPU vs CPU ONNX benchmark (RUN 65)

## Hypothesis

GPU ONNX inference on RTX 5070 should outperform CPU for the
Qwen2.5-0.5B forward pass.

## Setup

- **Hardware**: NVIDIA GeForce RTX 5070 (12 GB VRAM)
- **Software**: CUDA 13.1 + onnxruntime_gpu:1.29.0
- **Model**: Qwen2.5-0.5B ONNX (2.5 GB model.onnx)
- **Inputs**: input_ids=[1..8], attention_mask=all-ones, position_ids=[0..7]
- **Iterations**: 10 each (GPU, CPU), after warmup

## Results (real measurements, 2026-09-05)

| Provider | p50 latency | p99 latency |
|---|---|---|
| **CUDA (GPU)** | **5 ms** | 8 ms |
| **CPU** | 77 ms | 89 ms |
| **Speedup** | **15.40x** | 11.13x |

```
[BENCH] GPU p50=5ms p99=8ms
[BENCH] CPU p50=77ms p99=89ms
[BENCH] speedup p50=15.40x p99=11.13x
```

## Verdict

**GPU inference is 15.4x faster than CPU** on the p50 latency for
Qwen2.5-0.5B forward pass (8 tokens). Even with the 2.5 GB model
size, GPU memory bandwidth advantage dominates.

Honest caveats:
- 8-token context is small; longer contexts may show different ratios.
- Some ONNX nodes still fall back to CPU (shape ops) per ONNX warning.
- Benchmark uses small batch (batch=1).

## Cross-references

- EXP-MATRIX.37: GPU ONNX verification (88ms first inference, 5ms steady state).
- EXP-MATRIX.35: HF/ONNX/GraalVM documentation.

## Test code

`matrix-core/src/test/java/io/matrix/research/Exp065GpuVsCpuBenchmarkTest.java`
(1 test, all pass)
