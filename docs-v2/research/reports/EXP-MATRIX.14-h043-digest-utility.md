# EXP-MATRIX.14 — H-043 Decentralized Digest Utility (Wave RUN 16)

## Hypothesis

**H-043**: Decentralized digest synthesis (k-anonymous + DP-noise)
preserves utility ≥ 0.7 at k=100, ε=1.0 (synthetic-scope).
(Cluster: «federation & share».)

The anonymizer must k-anonymize federated M3 quorum digests while
keeping downstream retrieval utility above 0.7 of the baseline
(no-anonymization) utility.

## Setup

- **Anonymizer**: `io.matrix.research.DigestAnonymizer`.
- **Corpus**: 1,000 synthetic digests, 10 distinct quasi-identifiers
  (one per tenant), each repeated 100 times.
- **Mechanism**:
  1. Group digests by first-8-hex-chars quasi-identifier.
  2. Suppress groups smaller than k.
  3. Add Laplace DP-noise to bucket counts (`scale = 1/ε`).
- **Utility measure**: fraction of original digests whose hash appears
  in any surviving bucket. Range [0, 1].
- **Determinism**: seeded PRNG (Random(42) for k=100/ε=1.0 case).

## Results (real measurements, 2026-09-05)

| Test | k | ε | Buckets | Utility | Verdict |
|---|---|---|---|---|---|
| **H-043 acceptance** | 100 | 1.0 | 10 | **1.000** | **≥ 0.7 ✅** |
| Higher-k sanity | 50 | 1.0 | 10 | 1.000 | (k=50 keeps all) |
| Higher-k sanity | 200 | 1.0 | 0 | 0.000 | (no group ≥ 200) |
| Higher-k sanity | 500 | 1.0 | 0 | 0.000 | (no group ≥ 500) |
| Noise vs ε | 100 | 1.0 | — | mean abs noise 0.800 | — |
| Noise vs ε | 100 | 0.1 | — | mean abs noise 10.500 | smaller ε → more noise |

### Verdict

**ACCEPTED** — utility 1.000 ≥ 0.7 acceptance criterion. At the
H-043 design point (k=100, ε=1.0), all 10 tenant groups have ≥ 100
members, so every digest survives in a bucket; the DP-noise on
counts is bounded (±1 typical) and does not affect utility.

### Caveat (CONSTITUTION VI — honest framing)

The "1.000 utility" is a *survival* metric, not a content-fidelity
metric. Digests are present in the buckets, but downstream code
that relies on per-tenant count accuracy must use the DP-noised
counts. With 100 records per group and ε=1.0, the relative error
on counts is ~1% — well within H-043's design tolerance.

When k > group size (k=200, k=500), no buckets survive and utility
collapses to 0. This is the expected k-anonymity behavior; in
production, k must be calibrated to the smallest meaningful group.

### Test code

`matrix-core/src/test/java/io/matrix/research/Exp043DecentralizedDigestTest.java`
(5 tests). All pass.

### Cross-references

- DESIGN-08 (federation)
- M3 digest corpus
- H-043 in HYPOTHESES-NEW.md
