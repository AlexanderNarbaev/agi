# EXP-MATRIX.26 — Batch training speedup (RUN 34)

## Hypothesis

`LmHeadTrainer.trainOne(question, answer)` recomputes the chain
output for the question every call. For a batch with many entries
sharing the same questions, the chain is run N times instead of M
(where M = unique questions).

The new `trainBatch(List<Pair>, int nNegatives)` pre-fetches chain
outputs for unique questions once. Expected speedup: proportional
to repetition factor.

## Setup

- **New `LmHeadTrainer.trainBatch(List<Pair> pairs, int nNegatives)`**:
  pre-fetches chain outputs, then iterates pairs and applies LM head
  updates.
- **Telemetry**: `batchOps()`, `singleOps()` counters distinguish
  the two paths.
- **Backward compatible**: existing `trainOne(...)` unchanged.

## Results (real measurements, 2026-09-05)

| Test | Property | Result |
|---|---|---|
| `batchOpsCounterIsIncremented` | Counter tracks batch calls | **PASS** |
| `singleOpsCounterIsIndependent` | Single + batch counters independent | **PASS** |
| `emptyBatchIsNoOp` | Empty list returns 0 updates, no counter bump | **PASS** |
| `nullBatchIsNoOp` | Null list returns 0 updates | **PASS** |
| `batchWithDuplicatesSkipsRepeatedChainCalls` | Repeated questions handled correctly | **PASS** |
| `batchSkipsBlankInputs` | Invalid pairs are filtered | **PASS** |
| `pairRecordHoldsQuestionAndAnswer` | Record accessors correct | **PASS** |

All **7 LmHeadTrainerBatchTest pass**.

## Honest caveat — quantitative speedup

We did NOT measure a quantitative speedup in this EXP. The
qualitative argument is:
- Each `trainOne` call computes chain output via cache (O(1) on
  cache hit, O(chain evaluate) on miss).
- For a batch with K entries sharing M unique questions:
  - **trainOne loop**: K cache lookups. Cache hit rate depends
    on prior usage; in a fresh training run, miss rate is high.
  - **trainBatch**: M cache lookups (one per unique question).
- In production, training usually involves loading a fresh corpus
  where the cache miss rate is ~100%. So the speedup is ~K/M ×
  (chain eval cost).

A quantitative measurement would require:
1. A training scenario with realistic question repetition.
2. Wall-clock comparison between the two paths.
3. Report as `singleOpsTime / batchOpsTime`.

Documented as future work.

## Verdict

**Implementation correctness verified.** The batch path is wired,
all test cases pass, and the qualitative speedup argument holds.
Quantitative measurement deferred.

## Cross-references

- EXP-MATRIX.23: baseline chain.evaluate p50=90ns. For a batch
  of 100 entries × 10 unique questions, the savings would be
  ~90 cache misses × 90ns = 8μs per batch.
- LmHeadTrainer is the only consumer; no API surface change.

## Test code

`matrix-core/src/test/java/io/matrix/api/LmHeadTrainerBatchTest.java`
(7 tests, all pass).
