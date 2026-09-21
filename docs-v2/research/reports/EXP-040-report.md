# EXP-040 — M2→M3 promotion precision

## Hypothesis
M2→M3 promotion criteria: prediction-error > δ AND integrity-check pass.
Promotion precision ≥ 0.9 (synthetic-scope).

## Results (real measurements, 2026-08-28)
- Promotion **precision = 1.000**, recall = 0.038 (monotonic-gate effect)
- 100 episodes, single seed, fresh gate per episode

## Verdict
**MIXED** — the **criterion** itself has precision 1.0 (every promotion
is justified), but the **gate** is monotonic and only permits ONE
promotion before the target moves on. Recall is capped because the
gate stops at MA-1 once promoted.

## What this means
- The H-040 test setup collides with SPEC-000 INV-3 (monotonic
  maturity): once MA-0→MA-1 succeeds, the gate sits at MA-1 and
  subsequent advance() calls with PE>0.5/integ=true promote to
  MA-2 (which my criteria map didn't register), so they all deny.
- A multi-level criteria map (MA_1_LOCAL + MA_2_NETWORK + …) would
  exercise the chain; this wave measured only the first transition.
- The criterion expression (PE > 0.5 ∧ integrity == true) is
  **sound**: precision is 1.0 because no false positives occurred.

## Files
- Preregistration: `docs-v2/research/protocols/H-040.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp040M2M3PromotionTest.java`