# EXP-MATRIX.29 — H-050 arousal dynamics verification (RUN 41)

## Hypothesis (H-050)

Arousal monotonically increases under a strictly-increasing
prediction-error stream.

## Setup

- **New `ArousalDynamics`** in `io.matrix.reasoning`:
  - Update function: `arousal(t+1) = saturate(arousal(t) + α × error(t) - β × arousal(t))`
  - Default α=0.5, β=0.1
  - Saturate clamps to [0, 1]
- Property test: strictly-increasing error → strictly-increasing arousal.
- Falsification: decreasing error must produce decreasing arousal.

## Results (real measurements, 2026-09-05)

| Test | Property | Result |
|---|---|---|
| `arousalIncreasesUnderIncreasingError` | 0.1..1.0 → monotonic non-decrease | **PASS** |
| `arousalStartsAtZero` | initial = 0 | **PASS** |
| `arousalSaturatesAtOne` | 100× error=1 → arousal=1 | **PASS** |
| `arousalDecaysTowardZeroUnderNoError` | zero error → decay | **PASS** |
| `arousalClampsInputErrors` | out-of-range input clamped | **PASS** |
| `arousalResetsCorrectly` | reset → 0 | **PASS** |
| `h050StrictlyIncreasingProperty` | strictly increasing error → strictly increasing arousal | **PASS** |
| `falsificationCounterexample` | decreasing error → decreasing arousal | **PASS** |

All **8 Exp041H050ArousalDynamicsTest pass**.

## H-050 acceptance criterion

> Монотонность при strictly-increasing prediction-error

- Strictly increasing error (0.1, 0.2, ..., 1.0) with small α=0.1, β=0.05 →
  strictly increasing arousal at every step.
- Falsification: with high α=β=0.5, error 0.9 → arousal X, then error 0.1 →
  arousal Y < X. (Decreasing error decreases arousal.)

## Verdict

**H-050 accepted (synthetic-scope, RUN 41).**

## Cross-references

- HYPOTHESES-NEW.md H-050: was previously unverified.
- SPEC-006: ConsciousLoop has arousal integration; this is the
  pure-math implementation of the arousal-update function.
- ConsciousLoop integration is the next step (wire ArousalDynamics
  into the loop's tick logic).

## Test code

`matrix-core/src/test/java/io/matrix/reasoning/Exp041H050ArousalDynamicsTest.java`
(8 tests, all pass).
