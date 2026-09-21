# GPU + Native-Image Integration Wave (W34-W40)

**Date:** 2026-09-12
**Trigger:** Hardware upgrade — RTX 5070 Ti 12GB VRAM + 64GB CPU RAM unlocks previously-blocked research directions.

## Summary

With GPU + 64GB RAM, we unlocked several research directions that were previously impossible:
1. **CUDA-accelerated matmul** for BitLinear hot path (RUN 454, W34)
2. **GPU matmul size benchmarks** across matrix sizes (W35)
3. **Auto-dispatch** between CPU and GPU (RUN 455, W36)
4. **Real BitNet b1.58 2B model download + load test** (W37)
5. **CUDA tiled matmul** with shared memory (W38)
6. **Native-image Brotli config fix** (W39)

## New Artifacts (W34-W40)

### Code
- `matrix-core/src/main/c/cuda/bitlinear_matmul.cu` — CUDA kernel (273 lines)
- `matrix-core/src/main/c/cuda/libbitlinear_gpu.so` — 1MB shared library
- `matrix-core/src/main/java/io/matrix/imports/BitLinearGpu.java` — Java wrapper (314 lines)
- `matrix-core/src/main/java/io/matrix/neuron/BitLinearGpuForward.java` — auto-dispatch (105 lines)

### Tests
- `BitLinearGpuTest.java` — 8 tests
- `BitLinearGpuForwardTest.java` — 6 tests
- `GpuVsCpuBenchmarkTest.java` — 2 tests
- `GpuVsCpuSizeBenchmarkTest.java` — 5 tests
- `BitNetModelLoadTest.java` — 6 tests

Total: 27 new tests, 0 failures.

### Documentation
- `docs-v2/research/BITNET-2B-INTEGRATION-DEEP-RESEARCH.md` — comprehensive integration report

## Performance Results

### CUDA matmul throughput (RTX 5070 Ti)

| Matrix size | ops/sec | GFLOPS | Use case |
|-------------|---------|--------|----------|
| 64×64 | 15,417 | 0.1 | Small attention head |
| 256×256 | 9,379 | 1.2 | Medium FFN intermediate |
| 1024×1024 | 3,084 | 6.5 | Large FFN output |
| 4096×4096 | 344 | 11.5 | Very large feed-forward |

Peak GFLOPS scales with matrix size — bandwidth-bound for small, kernel-bound for large.

### BitNet b1.58 2B Model Download

- Downloaded `microsoft/bitnet-b1.58-2B-4T` from HuggingFace
- 1.1 GB safetensors file in 15 seconds
- 542 tensors verified via our existing `SafetensorsReader`
- Architecture: 30 layers × hidden=2560 × heads=20 × FFN=6912 × vocab=128256

### Native-image Build Progress

**Before (with OOM)**: Failed at C-link phase due to 7.85GB heap limit

**After W39 fix**: Now progresses past analysis phase, hits different error:
- `Brotli is not available` → FIXED by moving io.netty to run-time
- Current error: `ImageHeapScanner/validateReachableObject` (class init validation)
- 64GB RAM is available, no longer OOM-blocked

## Key Discoveries

### 1. CUDA + Panama FFM works (with caveats)

The `BitLinearGpu` class successfully loads `libbitlinear_gpu.so` via JEP 424 Panama FFM, dispatches int8 matmul to GPU, returns int32 results. Key fixes that were needed:

1. Explicit `System.load` of `libcudart.so` BEFORE our library (dlopen chain dependency)
2. `SymbolLookup.libraryLookup(path, arena)` instead of `linker.defaultLookup()` (default doesn't include user-loaded libraries)
3. `Arena.ofShared()` (not `ofConfined()`) to keep lookup alive for class lifetime
4. Multi-candidate `findLibraryPath()` for Gradle test executor's variable working directory

### 2. BitNet 2B weight format is non-trivial

Discovered that BitNet b1.58 2B weights are stored as `uint8` but the byte values (0-255) are NOT simple 2-bit packed ternary {-1, 0, +1}. The most frequent values are like 85 (0b01010101), 89 (0b01011001), 101 (0b01100101) — these look like a custom Microsoft packing format with metadata in high bits.

To fully integrate, would need to:
1. Read official `microsoft/BitNet` inference code (`bitnet.cpp`)
2. Reverse-engineer exact unpacking logic
3. Implement transformer block (attention + MLP + RoPE + ReLU²)
4. Llama-style BPE tokenizer for 128k vocab

Estimated: 2-3 weeks of focused engineering work.

### 3. Native-image blocker is now configuration, not memory

With 64GB RAM, the OOM is gone. The current blocker is GraalVM native-image configuration: specific class initialization order issues during the validation phase. Fixing requires incremental debugging of which class needs different initialization timing.

## Architectural Decisions (W34-W40)

### GPU dispatch threshold (BitLinearGpuForward)

```
GPU_DISPATCH_THRESHOLD = 4096
```

Below this size, CPU is faster (kernel launch overhead ~50μs dominates). Above, GPU wins.

### CUDA kernel selection

- `matmul_i8_naive_kernel`: per-thread full dot product — best for in_features < 512
- `matmul_i8_tiled_kernel`: shared memory chunks of 256 — best for in_features >= 512 (in theory)

In practice, the naive kernel is faster for our workload due to global memory bandwidth being the bottleneck. Future optimization: tile OUTPUTS (compute multiple per thread) and use int4 packed weights.

## HEAD and Commits

**HEAD:** `187ca26a` in `origin/main`

**6 commits in W34-W40:**
- W34: BitLinearGpu + tests (314+115 LOC)
- W35: GpuVsCpuSizeBenchmark
- W36: BitLinearGpuForward
- W37: BitNetModelLoadTest + deep-research report
- W38: CUDA tiled kernel
- W39: native-image Brotli fix

## Open Work

### Short-term (next session)
1. Read official `microsoft/BitNet` inference code to reverse-engineer weight format
2. Write Java unpacking utility matching Python reference
3. Test single BitLinear layer forward against Python `transformers` output

### Medium-term (1-2 weeks)
1. Implement BitNet transformer block (attention + MLP + RoPE + RMSNorm + ReLU²)
2. Tokenizer integration (Llama BPE for 128k vocab)
3. End-to-end inference: text → tokens → forward pass → tokens → text

### Long-term (1-3 months)
1. Multi-block autoregressive generation
2. KV-cache for efficient inference
3. Quantized attention (INT8 K/V cache)
4. Performance optimization (custom CUDA kernels with int4 packed weights)
5. Complete native-image configuration (incremental debugging)
