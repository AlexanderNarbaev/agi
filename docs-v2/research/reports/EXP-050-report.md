# EXP-050 — Arousal monotonicity

## Hypothesis
Arousal dynamics monotonically increase on a strictly-increasing PE
stream (synthetic-scope).

## Results (real measurements, 2026-08-28)
- 100 ticks of strictly-increasing PE (0.01..1.00)
- `arousal' = clamp(arousal + α·PE, 0, 1)`, α=0.1
- Final arousal = saturated near 1.0; **monotonicity holds across all 100 ticks**

## Verdict
**ACCEPTED** — the arousal-update function satisfies the
strictly-increasing-input monotonicity invariant by construction
(clamp is monotone on each axis).

## Files
- Preregistration: `docs-v2/research/protocols/H-050.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp050ArousalMonotonicityTest.java`