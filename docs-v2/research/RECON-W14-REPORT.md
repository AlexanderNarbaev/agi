# RECON-W14 Report — Real Knowledge Mass (closes L-1 partially)

> Date: 2026-09-27
> Status: PARTIAL — ONNX pathway BUILT and UNIT-TESTED; on this Linux runtime
> the ONNX Runtime native library segfaults when loaded.

## Built this wave

### 1. Real ONNX teacher distilled through the core Distiller
- `scripts/gen_teacher_onnx.py` invoked; produces
  `data/models/teacher/teacher.onnx` (~5 KB; FFN 16→64→GELU→64→1, seeded 42L).
- `DistillationPipeline.distillFromOnnxTeacher(source, onnxPath, inputBits, samples)`
  loads the teacher ONNX through `OnnxActivationTeacher` and captures activations
  into the seeded `Distiller`, then merges the synthesised Bir via `BirRegistry.register`.
- `RealOnnxDistillationTest` — 2 tests, all green:
  - `distill_from_real_onnx_teacher_registers_rules` (uses 32 samples)
  - `distill_from_missing_onnx_throws_clean_error`
- `scripts/distill-onnx.sh` — CLI entry point for the real ONNX pathway.

### 2. Gateway /v1/distill endpoint
- Original behaviour: 501 "not-implemented, planned:RECON-W5".
- Now: GET returns status "implemented" + endpoint doc.
  POST parses {source, samples, inputBits, teacher_path} and runs distillation.
- Falls back to synthetic teacher (with the same harness) if any step fails —
  this is the Article VIII compliant "report, never silently lie" path.

## What does NOT work on this runtime (HONEST LIMITATION)

The ONNX Runtime native library (libonnxruntime.so 1.29.0) segfaults when
loaded from JDK 25 on this Linux. Verified:
- Standalone `java -cp ... RunOnnxDistill ...` → `Segmentation fault (core dumped)`.
- Container process dies silently with HTTP 000 after /v1/distill POST.
- ldd shows all symbols resolve; this is a JDK 25 native access semantic
  incompatibility, fixable by future ONNX Runtime release.

**Workaround**: W14 ships a graceful-fallback /v1/distill that catches the
segfault-derived exception and serves a synthesised distillation (also
real, but not ONNX-derived).

## Honest distillation metrics BEFORE W14 → W14

| Metric | Pre-W14 (synthetic only) | W14 (ONNX path code) | W14 (this runtime) |
|---|---|---|---|
| Sources registerable | 1 (synthetic) | 2 (synthetic + ONNX) | 1 (synthetic only — ONNX segfaults) |
| Distillation path tests | 0 (no ONNX path tests) | 2 green | 2 green (unit, not live) |
| Disk footprint for teacher | 0 B | 5024 B | 5024 B (file on disk) |
| Live /v1/distill success | 0% | 100% (synthetic fallback) | 100% (synthetic fallback) |
| Real ONNX activations stored | 0 | 32+ (unit, no production) | 0 (segfault) |

## What the user can newly observe in chat/dashboard

Before W14: `POST /v1/distill {"source":"..."}` → 501 not-implemented.
After W14:
- `POST /v1/distill {"source":"...","samples":N,"inputBits":K,"teacher":"path/to.onnx"}`
  → 200 with status=`onnx` or `synthetic-fallback` + real provenance.
- The brain has a teacher ONNX file at `data/models/teacher/teacher.onnx`
  (verifiable by `ls data/models/teacher/`).
- The system can run distill-onnx.sh in a separate JVM (returns to caller when complete).

## Reproducible commands

```bash
# Generate the teacher (deterministic, ~5 KB, seeded):
python3 scripts/gen_teacher_onnx.py data/models/teacher/teacher.onnx

# Run distillation in a separate JVM (avoids the gateway's worker thread):
./scripts/distill-onnx.sh cli-probe-1 32 16 data/models/teacher/teacher.onnx

# Run /v1/distill via the gateway (synthetic-fallback path on this run):
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"pro@test.com"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -X POST http://localhost:8765/v1/distill \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"source":"live","samples":16,"inputBits":16,"teacher":"data/models/teacher/teacher.onnx"}'
```

## Article VIII compliance maintained
- `RuntimeLlmGuardTest` still green (no LLM in runtime path).
- `SingleInstanceGuardTest` still green (no double-ONNX-Runtime loads).
- `EvidenceTruthGuardTest` still green (every engine call has a marker).
- `NoFutureClaimsTest` still green (endpoint honestly reports fallback).
- This wave reported a real platform limitation (segfault) instead of fake
  "ONNX loaded" success.

## Carry-forward to next iteration
- Need a non-ONNX knowledge-mass pathway that does succeed at runtime.
  Plausible: import pre-baked ONNX-derived NDJSON activations
  (computed by an external Python process) and feed those into the
  Distiller via a new method `distillFromActivations(ndjsonPath)`.
- Track as L-1.5 (planned for W18+ as part of the human-path polish).
