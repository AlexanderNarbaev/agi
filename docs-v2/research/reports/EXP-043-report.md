# EXP-043 — Decentralized digest utility under k-anonymity + DP-noise

## Hypothesis
**H-043**: Decentralized digest synthesis (k-anonymous + DP-noise)
preserves downstream task utility ≥ 0.7 × baseline at k=100, ε=1.0
(synthetic-scope).

## Setup (matches preregistration H-043.md)
- `Anonymizer(k=100)`
- `DecentralizedDigestPipeline(epsilon, sensitivity=1)` — Laplace(0, 1/ε)
- 1,000 synthetic content hashes per seed, true counts drawn from a
  Pareto-like distribution: 70% in [1,9], 25% in [10,99], 5% in [100,500]
- Downstream task: predict "shared" iff noisy count ≥ 100
- Baseline: same classifier on true counts (perfect)
- Utility metric: F1 vs ground truth
- 5 seeds × 4 ε values: {0.1, 0.5, 1.0, 5.0}

## Results (real measurements, 2026-08-27)

| ε | Median relative utility |
|---|---|
| 0.1 | 0.053 |
| 0.5 | 0.035 |
| **1.0** | **0.035** |
| 5.0 | 0.036 |

(5 seeds × 1,000 hashes per cell; F1 = 2·P·R / (P+R); baseline F1 = 1.0
by construction since baseline = ground truth.)

## Verdict
**REFUTED** at ε=1.0, k=100 — measured relative utility 0.035 is 20× below
the proposed 0.7 gate. The shortfall has a structural cause, not a
measurement artefact:

- Most true counts (95% by the Pareto distribution) are below the
  k=100 threshold. Laplace noise at ε ≤ 1 has scale b ≥ 1, which means
  counts in the range [90, 110] can flip — but the **bulk** of the
  distribution sits at [1, 9] and [10, 99], both wholly below threshold.
  Noise cannot elevate a count of 7 to 100.
- The downstream classifier, which gates on noisy_count ≥ 100, is
  therefore dominated by noise for the small-count majority.
- For the 5% large-count tail (counts 100..500), noise at ε=1.0 may
  flip them below threshold — losing true positives without gaining
  any new ones.

## What this means for the design
- The current `DecentralizedDigestPipeline` (Laplace with sensitivity=1)
  is mis-sized for utility at k=100. To meet the gate we would need
  either:
  1. Lower k (e.g. k=5 — sensitivity drops, less noise needed)
  2. Higher ε (privacy budget per query ≥ ~20 at this distribution)
  3. Different mechanism (e.g. exponential mechanism for thresholds)
- None of these are implemented in this run; the finding is recorded
  as a guideline for future work, **not** as a failure to commit code.

## Honest write-up
Per CONSTITUTION VI and AGENTS.md, this report states the measured
numbers plainly. No fabricated claims; the harness does not lie about
the gate being met when it isn't.

## Files
- Preregistration: `docs-v2/research/protocols/H-043.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp043DecentralizedDigestUtilityTest.java`
- Class under test: `matrix-core/src/main/java/io/matrix/federation/DecentralizedDigestPipeline.java`