# EXP-MATRIX.24 — Sparse weight storage measurement (RUN 29)

## Hypothesis

The `LmHead` saves weights sparsely (only non-zero entries on disk)
but stores them densely in memory (a `double[]` per token). For
large corpora with many tokens, this is wasteful — most neurons
have weight 0 after a brief training period.

## Setup

- **New `LmHead` diagnostics** (RUN 29):
  - `denseMemoryBytes()`: total in-memory bytes (vocab × neurons × 8).
  - `sparseMemoryBytes()`: bytes if sparse (nonZeroCount × 12 + overhead).
  - `sparsityRatio()`: fraction of weight slots that are zero.
  - `nonZeroWeightCount()`, `totalWeightSlots()`: raw counts.

## Results (real measurements, 2026-09-05)

For a 1-token LM head trained 50 epochs with a 30-neuron firing
fingerprint:

| Metric | Value |
|---|---|
| Vocabulary coverage | 1 token |
| Total neurons | 100 |
| Total slots | 100 |
| Non-zero slots | 100 (decay keeps non-firing slots non-zero) |
| Sparsity ratio | 0% |
| Dense memory | 800 bytes |
| Sparse memory | 1204 bytes (per-token overhead dominates) |

## Honest finding

In the current `LmHead` design, ALL slots end up non-zero because
the Hebbian decay term (`-0.01` per non-firing epoch) keeps
non-firing slots at small negative values forever. So sparsity
is effectively 0% under the default training regime.

Sparse storage would help IF we changed the decay rule to
"floor at zero" (i.e., non-firing slots stop at 0, never go
negative). That's a design decision beyond RUN 29 scope.

## Verdict

**Diagnostics added.** Sparsity measurement is now exposed. The
sparse-vs-dense break-even requires a future change to the
training rule.

## Test code

`matrix-core/src/test/java/io/matrix/api/LmHeadTest.java` — 4
new tests for sparsity diagnostics (19 total, all pass).
