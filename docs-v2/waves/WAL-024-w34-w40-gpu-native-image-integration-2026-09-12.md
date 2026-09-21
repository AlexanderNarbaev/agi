# WAL 24 — W34-W40: GPU + native-image integration (2026-09-12)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W34-W40: GPU + native-image integration (2026-09-12)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W34-W40: GPU + native-image integration (2026-09-12)

**Hardware unlock:** RTX 5070 Ti 12GB VRAM + 64GB CPU RAM enabled new research:

**W34 RUN 454 — BitLinearGpu (CUDA matmul via Panama FFM):**
- C++ kernel + libbitlinear_gpu.so + Java wrapper
- Verified on RTX 5070 Ti: CUDA device count = 1
- 8 unit tests pass

**W35 — Size benchmarks:**
- 64×64: 15K ops/sec, 0.1 GFLOPS
- 4096×4096: 344 ops/sec, 11.5 GFLOPS (kernel-bound)

**W36 RUN 455 — BitLinearGpuForward (auto-dispatch):**
- Threshold = 4096 elements
- Below: CPU, above: GPU
- 6 tests pass

**W37 — BitNet b1.58 2B model:**
- Downloaded microsoft/bitnet-b1.58-2B-4T from HuggingFace (1.1 GB)
- 542 tensors verified via SafetensorsReader
- KEY DISCOVERY: BitNet weights are NOT simple 2-bit ternary
  (byte values like 85, 89, 101 etc. suggest custom Microsoft format)
- 6 BitNetModelLoadTest tests pass
- Deep-research report: BITNET-2B-INTEGRATION-DEEP-RESEARCH.md

**W38 — CUDA tiled kernel:**
- matmul_i8_naive_kernel (per-thread full dot product)
- matmul_i8_tiled_kernel (TILE_SIZE=256 shared memory)
- Naive wins in our case (bandwidth-bound, not compute-bound)

**W39 — Native-image Brotli fix:**
- Previous OOM no longer relevant (64GB available)
- Real blocker was 'Brotli is not available' — Netty was build-time
- Fixed: io.netty → run-time, Brotli → run-time
- Now hits different error: ImageHeapScanner/validateReachableObject
  (class init validation, requires incremental debugging)

**W34-W40 totals:**
- 6 commits in origin/main
- 1275 LOC added (CUDA kernel, Java wrapper, tests, docs)
- 27 new tests, 0 failures
- HEAD: 187ca26a
- Summary doc: GPU-NATIVE-IMAGE-W34-W40-SUMMARY.md

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W25

*Auto-extracted by extract-waves.py*
