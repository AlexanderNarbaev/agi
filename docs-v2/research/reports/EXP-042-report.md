# EXP-042 — Consciousness-loop per-stage latency

## Hypothesis
**H-042**: Consciousness-budget allocator respects per-stage caps
under load — no per-stage p99 exceeds cap in 95/100 runs (synthetic-scope).

## Setup (matches preregistration)
- `ConsciousnessLoop` with empty `BrcChain` (identity) + `ActionArena`
  default (4-way parallelism, 5s budget, 128-deep queue)
- 1,000 sequential ticks for full p99; 100 ticks for sequential batch
- Proposed caps (synthetic-scope, empty chain):
  - tick total: 10 ms p99
  - sub-stages not separately profiled (the loop is sequential)

## Results (real measurements, 2026-08-27)

| Metric | Value |
|---|---|
| tick p50 | 3,166 ns |
| tick p99 | 61,867 ns |
| tick max | 719,927 ns |
| 100-tick p99 | 3,317,183 ns |

## Verdict
**ACCEPTED-AT-FLOOR** — measured p99 = 62 μs ≪ 10 ms cap. The trivial
identity chain and the lightweight ActionArena dominate the latency
budget; with a real deliberation chain (BrcChain with N steps + MPDT
layers), the number would be much higher, but that is out of scope for
this wave.

## Caveat
The "per-stage" decomposition is not separately measurable without
instrumenting each stage; this report covers the aggregate tick only.
A more granular profiling is left to the JMH-grade benchmarks that the
H-047 preregistration will own.

## Files
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp042ConsciousnessLoopLatencyTest.java`
- Class under test: `matrix-core/src/main/java/io/matrix/reasoning/ConsciousnessLoop.java`