# Project Context — MATRIX cognitive system

> Last compaction: 2026-09-05 18:36 UTC, RUN 65.
> Single source of truth: this file + FINALSUMMARY.md.

## Current Status

### Branch
- `main` HEAD: `e2ea110e` (RUN 64-65 docs commit)
- All work committed.

### Hardware
- **GPU**: NVIDIA GeForce RTX 5070 (12 GB VRAM) — **CUDA 13.1 toolkit installed**, cuDNN started
- CUDA 12 libs (compatible with onnxruntime_gpu 1.29.0) installed via pip
  at `.venv/lib/python3.14/site-packages/nvidia/*/lib/`

### Major breakthroughs this session
- **GPU ONNX inference VERIFIED** at **15.40x speedup** over CPU
  (p50: GPU=5ms, CPU=77ms; 8-token forward pass)
- Qwen2.5-0.5B HF-downloaded (954 MB) + ONNX-exported (2.5 GB) +
  GPU inference working

### Tests cumulative
- **538 tests, 0 failures, 0 errors**
  (RUN 12-65 incremental: 531 → 538)
- All tests pass: OnnxRuntimeGpuTest 6/6, QwenModelAdapterTest 6/6,
  MetricsResourceTest 6/6, Exp065GpuVsCpuBenchmarkTest 1/1, plus
  519 prior tests.

### RUNs delivered this session (62-65)
| RUN | Deliverable | Tests |
|---|---|---|
| 62 | GPU ONNX adapter | 0 |
| 63 | Real GPU inference test (88ms, argmax=6) | +6 |
| 64 | QwenModelAdapter CDI compatible | 0 |
| 65 | GPU vs CPU benchmark (15.40x speedup) | +1 |

### Architecture
- `MultiModelLoader` → ONE `BooleanChainRunner` (24 layers, 21,960 neurons)
- `QaCorpusIndex` inverted-index over 6,607 Q&A pairs (v2 envelope)
- `ChainTextGenerator` autoregressive generation
- `ConversationMemory` per-conv bounded ring buffer
- `LmHead` sparse Hebbian classifier with floor-at-zero decay
- `ConsciousnessLoop` nine-stage orchestrator
- `BrainLoopService` production wiring
- `OnnxRuntimeAdapter` CPU+GPU ONNX inference
- `HuggingFaceFetcher` + `QwenModelAdapter` for HF integration
- `FreezeRecoveryManager` + `ArousalDynamics` + `EmergenceAnalyzer`
- 26+ new Java classes this session

### Tech stack
- Java 25.0.4, Quarkus 3.38.3, Gradle 9.6.0
- GraalVM CE 25.0.2 (build attempts fail, see blocked list)
- ONNX Runtime 1.29.0 (CPU + GPU JARs)

### 6 hypothesis cards accepted
- H-043, H-044 (calibration), H-045 (freeze recovery),
  H-046, H-049 (share impulse firer), H-050 (arousal dynamics)

### 16 EXP reports
- EXP-MATRIX.21 to EXP-MATRIX.38 (the most recent covers GPU vs CPU benchmark)

### Documentation
- FINALSUMMARY.md: ~2160 lines, Sections XII-LXXIII
- WAL.md: complete timeline through RUN 65
- 5 normative specs (SPEC-008..012)
- FORMAL-CONTRACTS.md with TLA+ specs

## Pending Tasks

### Immediate (next 1-2 RUNs)
1. **Goal Guard review cycle**: dirty tree needs clean state + reviewer gates
2. **Continue wave-by-wave**: any remaining FINALSUMMARY items

### Blocked
1. **Native GraalVM build**: 4-step cascading failure (RUN 18/53/55).
   Mandrel container requires auth token (401 UNAUTHORIZED).
   3 RFC paths documented in EXP-MATRIX.36:
   - Mandrel token (Red Hat developer account)
   - Scala replacement for DnsNameResolverBuilder
   - --report-unsupported-elements-at-runtime flag
   JVM mode (155 MB uber-jar) is production target.

### Deferred (user choice)
- **Native build retry**: requires Docker Hub / Quay.io auth
- **WAL.md final trim**: defer
- **OnnxRuntimeAdapter as standalone inference path**:
  currently wraps model loading + provider config; actual inference
  integration with chain is future work

## Honest findings (CONSTITUTION VI)

- **REAL LLM behavior**: real corpus-backed answers, persisted learn,
  multi-turn context, chain-driven generation, training with write-back
  that VERIFIABLY modifies chain weights.
- **GPU ONNX inference VERIFIED**: 15.40x speedup over CPU on RTX 5070.
- **NOT YET achieved**: fluent text generation from chain (LM head
  infrastructure present but opt-in due to coverage), native-image
  binary (Mandrel auth required), real semantic retrieval (token-overlap
  has 0% hit rate on disjoint samples).

## File locations

- `/home/alexandr-narbaev/Projects/agi/.opencode/context.md` — this file
- `/home/alexandr-narbaev/Projects/agi/docs-v2/vision/FINALSUMMARY.md` — ~2160 lines
- `/home/alexandr-narbaev/Projects/agi/WAL.md` — full timeline
- `/home/alexandr-narbaev/Projects/agi/matrix-core/` — Quarkus app
- `/home/alexandr-narbaev/Projects/agi/models/hf_cache/qwen05b/` — Qwen2.5-0.5B
- `/home/alexandr-narbaev/Projects/agi/models/onnx/qwen05b/model.onnx` — exported model
- `/home/alexandr-narbaev/Projects/agi/docs-v2/research/reports/EXP-MATRIX.37-gpu-onnx.md`
- `/home/alexandr-narbaev/Projects/agi/docs-v2/research/reports/EXP-MATRIX.38-gpu-vs-cpu.md`

## Key code files

- `matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java`:
  GPU ONNX adapter with setUseGpu + OrtCUDAProviderOptions
- `matrix-core/src/main/java/io/matrix/api/QwenModelAdapter.java`:
  CDI-compatible (RUN 64 fix)
- `matrix-core/src/main/java/io/matrix/api/HuggingFaceFetcher.java`:
  HF CLI wrapper with @Observes StartupEvent hook
- `matrix-core/src/test/java/io/matrix/api/OnnxRuntimeGpuTest.java`:
  6 GPU tests including real inference
- `matrix-core/src/test/java/io/matrix/research/Exp065GpuVsCpuBenchmarkTest.java`:
  GPU vs CPU benchmark
- `matrix-core/build.gradle`:
  onnxruntime_gpu:1.29.0 (GPU JAR listed first),
  --enable-native-access=ALL-UNNAMED,
  systemProperty java.library.path for NVIDIA libs

## Commit graph (recent)
- `f65a255a` RUN 61 docs (initial)
- `69b8801e` RUN 62-63 docs (FINALSUMMARY §LXXI + WAL)
- `3c83e7c7` RUN 64 QwenModelAdapter CDI compatible
- `e2ea110e` RUN 64-65 docs (FINALSUMMARY §§LXXII-LXXIII + WAL)
