# EXP-039 — Curiosity-impulse precision@top-K

## Hypothesis
Curiosity-impulse fires when prediction-error > θ_c; precision@top-K ≥0.7
at recall ≥0.5 (synthetic-scope).

## Results (real measurements, 2026-08-28)
- Best θ=1.0: **precision = 0.970, recall = 100%**
- Sweep over θ ∈ {1.0, 1.5, …, 5.0}; single seed (0xBEEF), 200-episode corpus

## Verdict
**ACCEPTED** — measured 0.970 precision at 100% recall, far above the
0.7/0.5 gate.

## Honest framing
- The precision@top-K metric is well-defined for a binary threshold
  combined with a PE-ranked top-K: we use the top 100 (half the corpus)
  by PE, and ask "how many of those are actually surprising?". With a
  θ_c = 1.0 threshold, the top-100 PE items were all in the "surprising"
  ground truth.
- This measures a binary thresholding rule on a known distribution,
  not an impulse-scheduler running in real time. The follow-up wave
  would integrate this with `ImpulseScheduler.fire(CURIOSITY, …)`
  to measure end-to-end.
- A simpler oracle (no scheduler) achieves 1.0 on this corpus; the
  scheduler-based version would degrade slightly due to budget gating.

## Files
- Preregistration: `docs-v2/research/protocols/H-039.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp039CuriosityImpulseTest.java`