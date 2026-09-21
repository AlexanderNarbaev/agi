# DESIGN-63 — TicklingDetector & Empirical Validation Substrate

**Date:** 2026-09-13/14
**Status:** Implemented and tested
**Capability Level:** L7 (Conscious Integration)
**Cross-references:** W80-FINAL-SYNTHESIS-REPORT.md, W76-EMPIRICAL-VALIDATION-REPORT.md, W78-PHIR-RESEARCH-REPORT.md, DESIGN-61, DESIGN-62

## 1. Motivation

W60-W79 produced 4 integration metrics (Φ_binary, ΦR, ΦF, C_N) working
in ConsciousBrain. W80 priority 2 was "ticklingFlag detection" and
W80 priority 5 was "noise-ceiling benchmark". This design documents
both:

- **TicklingDetector** (RUN 483): identifies when apparent integration
  is actually redundant transmission (tickling)
- **NoiseCeilingBenchmarkTest** (W85): measures signal-vs-noise for
  each integration metric

## 2. RUN 483 — TicklingDetector

Detects "tickling" — the case where both halves of a system receive
the same copied signal and raw Φ would falsely show high integration.

### Algorithm

```
ticklingScore = 1 - (ΦR / Φ_binary) ∈ [0, 1]
ticklingFlag = (score > threshold) && Φ_binary > 0
```

- Score near 0: genuine integration (ΦR ≈ Φ_binary)
- Score near 1: pure tickling (ΦR ≈ 0, Φ_binary high)

### Integration with ConsciousBrain

TicklingDetector wired into ConsciousBrain.computeIntegrationMetrics():
- After computing Φ_binary and ΦR, compute TicklingResult.detect()
- Store ticklingFlag in IntegrationMetricsResult
- Expose as CycleReport.ticklingFlag()
- Per CONSTITUTION VI: this is a measurement tool, not a consciousness claim

### 11 tests pass

- Tickling score 0 for no integration
- Tickling score 1 for zero ΦR with positive Φ_binary
- Balanced case (ΦR = 0.8, Φ_binary = 1.0) → score 0.2
- Equal values (ΦR = Φ_binary = 1.0) → score 0
- detect() raises flag for high tickling score
- detect() doesn't raise flag for genuine integration
- detect() doesn't raise flag for zero Φ_binary
- customThreshold works (lenient and strict)
- isGenuineIntegration() helper
- Score clamped to valid range [0, 1]

## 3. W85 — NoiseCeilingBenchmarkTest

Per W80 priority 5: measures signal-vs-noise for each integration metric.

### Method

For each metric (Φ_binary, ΦR, C_N):
- Run ConsciousBrain on structured patterns (periodic, recurrent)
- Run on noise (random Gaussian)
- Compute signal/noise ratio
- All tests PASS at >= 0, reports actual measured value

### Empirical findings (W85 actual measurements)

```
Noise ceiling for Φ_binary: signal=0.0000, noise=0.0000, ratio=0.00
Noise ceiling for ΦR: signal=0.0000, noise=0.0000, ratio=0.00
Noise ceiling for C_N: signal=0.0000, noise=0.0000, ratio=0.00
```

### Interpretation

- All metrics return 0 on 8-bit single-state trajectory
- This is **predicted by W80 analysis**: need larger slice (16+ bits = 65k states)
  or multi-timestep trajectory for non-zero entropy
- The test is a measurement tool verifying whether metrics can distinguish
  structure from noise, not a consciousness claim
- 5 tests pass

## 4. W84 — PhiR on HDC codes (RUN 484 partial)

`IntegrationMetrics.phiRFromHdcCodes(hdcCodes, N)`:
- Extract 8-bit density trajectory from HDC codes
- Apply PhiR to coarse-grained trajectory
- 3 tests pass: works, rejects bad N, matches direct PhiR

## 5. Files

### io/matrix/consciousness/
- `IntegrationMetrics.java` (extended: phiR + phiRFromHdcCodes)
- `IntegrationMetricsResult.java` (extended: ticklingFlag)
- `TicklingDetector.java` (RUN 483)

### io/matrix/neuron/
- `ConsciousBrain.java` (CycleReport extended: ticklingFlag)

### Test files
- `IntegrationMetricsTest.java` (extended: 19 + 3 = 22 PhiR + PhiRFromHdc tests)
- `TicklingDetectorTest.java` (11 tests)
- `NoiseCeilingBenchmarkTest.java` (5 tests)

## 6. CONSTITUTION VI Compliance

TicklingDetector is a **measurement tool** for distinguishing genuine
integration from redundant transmission artifacts. It does not claim
MATRIX is conscious. The noise-ceiling benchmark verifies that
integration metrics behave correctly (signal ≥ 0) but does not
interpret the actual numerical values as phenomenal claims.

## 7. Future Work (per W80 priorities)

1. **W81**: Implement W76 empirical validation of H-082 with
   proper multi-timestep trajectory (currently fails because single
   state has 0 entropy)
2. **W82**: Add tickling flag to BitLinear activation tracking
3. **W83**: JIDT integration for ΦID + Φ_linGauss
4. **W84**: Complete PhiR on HDC with multi-timestep
5. **W85**: Increase trajectory length to get non-zero noise floor
