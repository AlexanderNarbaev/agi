# EXP-049 — Share-impulse precision

## Hypothesis
Share-impulse fires when M3 quorum acceptance crosses utility
threshold. Impulse precision ≥ 0.8 (synthetic-scope).

## Results (real measurements, 2026-08-28)
- Best **θ_s = 0.9**, precision = **0.952**
- 200 synthetic digests with random utility ∈ [0, 1]
- Single seed (0x5EED), k=200 (M3 quorum size)

## Verdict
**ACCEPTED** — 0.952 > 0.8 gate.

## Honest framing
- This test characterizes a **threshold-fire** rule (fire iff utility
  ≥ θ_s). The actual Share-impulse integration with the
  ImpulseScheduler is left to a follow-up wave.
- The 0.952 precision at θ=0.9 reflects that the top-decile
  utilities are sparse, so the rule fires sparingly and rarely misses.

## Files
- Preregistration: `docs-v2/research/protocols/H-049.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp049ShareImpulseTest.java`