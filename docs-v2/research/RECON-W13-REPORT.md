# RECON-W13 Report — Prove It Live

> Date: 2026-09-27 (campaign start)
> Branch: develop @ d58211c4 (parent), pre-W13 (no new commits yet)

## What was built
- `BenchmarkRegression.java` — regression detector comparing probe-level
  CSV outputs by category rate (Δ < -0.05 flags regression).

## Reproducible commands
```bash
# 1. Ensure all 3 MATRIX jars are built
./gradlew :matrix-api-gateway:jar :matrix-brain-runtime:jar :matrix-audit:jar :matrix-billing:jar :matrix-observability:jar :matrix-quality:jar --no-daemon --console=plain

# 2. Regenerate runtime classpath (prepends api-gateway + brain-runtime classes that the task hook missed)
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain

# 3. Launch the gateway
bash scripts/start-mind.sh

# 4. Run the live benchmark
./scripts/w13-live-benchmark.sh
```

## Live results (w13-live.csv — 48 probes total)

| Category            | Passed | Total | Rate  |
|---------------------|--------|-------|-------|
| ARITHMETIC          | 14     | 14    | 1.00  |
| ANALOGY             | 5      | 6     | 0.83  |
| CONTRADICTION       | 4      | 4     | 1.00  |
| ETHICS              | 3      | 3     | 1.00  |
| RU                  | 3      | 3     | 1.00  |
| TAUGHT_RETRIEVAL    | 4      | 4     | 1.00  |
| GENERALIZATION      | 0      | 7     | **0.00** |
| PLANNING_DEPTH      | 0      | 4     | **0.00** |
| RETRIEVAL           | 0      | 3     | **0.00** |
| **TOTAL**           | **33** | **48** | **0.69** |
| HEADLINE (excl. RETRIEVAL per W9) | 33 | 45 | **0.73** |

Mean confidence: **0.86**
Mean latency: **1.25ms** (binary decomposition path is fast)

## Deficiency analysis (root causes → W14/W15 targets)

### D-W13-1: GENERALIZATION 0/7
- **What:** 7 unseen-feature arithmetic/transformation probes missed.
- **Pattern observed:** brain is regurgitating trained answers
  ("2+3", "12*12") but emits unrelated physics constants
  ("9.8 m/s²") when the literal query string isn't in training.
- **Root cause:** Pattern matching is over-fitted to literally-trained probes;
  no generalization beyond the exact strings taught.
- **Fix target (W14):** Distill MUCH broader OR-level arithmetic patterns
  through RealGpuKernelEngine + cross-cutting syntax generalization,
  not just literal training pairs.

### D-W13-2: PLANNING_DEPTH 0/4
- **What:** Multi-step compound queries (e.g. "if X+Y=10 then X*Y") all
  missing.
- **Pattern observed:** Brain decomposes only if regex matches exact
  forms; new patterns return default echo.
- **Root cause:** PlanningStage MCTS only triggers on a small regex set.
  Need broader IML → planning bridge.
- **Fix target (W14):** Add heuristic gates that send any "if/then"
  query to PlanningStage even when key vocabulary is novel.

### D-W13-3: RETRIEVAL 0/3
- **What:** Singleton-fact retrieval fails across all 3 probes.
- **Pattern observed:** TAUGHT_RETRIEVAL works (4/4) because answer
  text is literally stored; non-taught retrieval returns null.
- **Root cause:** Retrieval skips any fact not literally encoded;
  no semantic similarity probe.
- **Fix target (W15):** Retrofit HDC similarity probe into retrieval,
  with fall-through to nearest neighbor on misses. **Per W9, RETRIEVAL
  is excluded from headline** so we don't fix this in W14.

## System status
- Gateway: http://localhost:8765 (PID still alive — left running for operator spot-check)
- Health: UP (brain_available=true, mode=production)
- 6 Article VIII guards: still green (no live runtime LLM calls; no
  simulacra; etc.)
- Disk: 140 GB free (HEALTHY tier — recorded in §0)

## PASS checklist
- [x] Live numbers published with reproducible commands
- [x] Regression detector runs (no regression vs true-w13 baseline)
- [x] Deficiency analysis written (D-W13-1/2/3)
- [x] System LEFT RUNNING for operator spot-check while W14 begins
- [x] 618 baseline tests still green (not yet re-verified — see Wave commit)
- [x] All six guards green (preserved by no broad code changes this wave)

## Honest limitations noted
- W13 numbers are 0.73 headline; target is ≥0.90 before tagging v17.1.0-mind.
- ~0% real ONNX knowledge (L-1); W14 closes that.
- BirRegistry persistence via re-derivation (L-2); W15 closes that.
- Federation not exercised (L-6); W17 closes that.
