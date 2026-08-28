# EXP-048 — Emergence of behavior stability

## Hypothesis
1000 cycles preserve action-distribution entropy / decision-tree
shape (synthetic-scope).

## Results (real measurements, 2026-08-28)
- 1000 ticks with constant input + empty BRC chain + uniform saliency
- **1 unique decision-vector across all 1000 ticks**
- ConsciousnessLoop converged to a fixed point within the first tick

## Verdict
**ACCEPTED** — measured 1 unique decision, which is the trivial limit
of "stable behaviour". With constant input, the loop finds the same
action every tick.

## Caveat
- The constant-perception condition is the easiest case; a richer
  stochastic perception would test genuine emergence rather than
  trivial convergence. The next wave should run with a random PE
  stream and check `|lastDecision(t+1) - lastDecision(t)|` Hamming
  distance over time.

## Files
- Preregistration: `docs-v2/research/protocols/H-048.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp048EmergenceStabilityTest.java`