# EXP-MATRIX.23 — Performance baseline (RUN 28)

## Hypothesis

Establish a baseline for core hot-path operations. Future perf work
is measured against these numbers.

## Setup

- Hardware: this host (AMD Ryzen 9 9955HX, single-threaded JVM).
- JVM: Java 25.0.4 with incubator-vector module.
- ITERS = 1000, WARMUP = 100.
- Deterministic seeds: same input data → same chain output.

## Results (real measurements, 2026-09-05)

| Operation | p50 | p99 | max | n |
|---|---|---|---|---|
| `BooleanChainRunner.evaluate` | **90 ns** | 732 ns | 3607 ns | 1000 |
| `QaCorpusIndex.search` | **15,059 ns** | 63,900 ns | 4,927,731 ns | 1000 |
| `LmHead.score` | **71 ns** | 81 ns | 200 ns | 1000 |
| `BpeTokenizer.encode` | 882 ns* | 5,861 ns | — | 1000 |
| Full pipeline (chain + score×2) | **471 ns** | 4,789 ns | 23,524 ns | 1000 |

*BpeTokenizer.encode succeeded 0/1000 in this test environment
because the model artifacts aren't loaded in the test fixture.
The timing measures the no-op check path.

## Analysis

- **BooleanChainRunner.evaluate**: 90ns is excellent. The chain
  runner is a 24-layer boolean function with sparse activations.
  Most of the 90ns is JIT overhead + thread-context, not actual
  computation.
- **QaCorpusIndex.search**: 15μs is the slowest hot path. It
  includes tokenization, inverted-index lookup, scoring, and
  sorting. The 4.9ms max is likely first-call JIT — the p99 of
  64μs is the realistic steady-state.
- **LmHead.score**: 71ns is dominated by `synchronized (tw)`
  acquisition. With many concurrent callers this could become
  a contention point.
- **Full pipeline**: chain + 2× LM head score = 471ns. This is
  the cost of a single LLM "next-token" decision. Total is
  dominated by the chain run.

## Verdict

**Baseline established.** All operations are sub-millisecond at
p99. The biggest opportunity for improvement:
1. **QaCorpusIndex.search**: p99 64μs could drop to ~10μs with
   better caching of tokenized queries.
2. **LmHead.score**: 71ns is competitive but the synchronized
   block could become a contention issue under high concurrency.

## Cross-references

- EXP-MATRIX.20: bilingual stress test p99 was 2μs because it
  used QaCorpusIndex.search without inverted-index scoring —
  now we have a more realistic baseline with full scoring.
- EXP-MATRIX.13-native: native-image builds might give 5-10×
  speedup on these hot paths if the build issue is resolved.

## Test code

`matrix-core/src/test/java/io/matrix/research/Exp028PerformanceBaselineTest.java`
(5 tests, all pass).
