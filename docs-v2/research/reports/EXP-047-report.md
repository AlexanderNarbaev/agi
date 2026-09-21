# EXP-047 — Cross-pillar latency budget

## Hypothesis
Cross-pillar latency budget split: perception < 5 ms, deliberation
< 50 ms, action < 10 ms (p99). Tick total budget = 65 ms p99 in
9/10 ticks.

## Setup
- ConsciousnessLoop orchestrator + ActionArena defaults
- Empty BRC chain (identity) and uniform saliency
- 1000 sequential ticks after 100 warmup
- 2026-08-28

## Results (real measurements)
- Tick p50 = 3,857 ns (~3.9 μs)
- Tick **p99 = 63,480 ns (~0.063 ms)** — far under the 65 ms cap
- Tick max = 567,431 ns (~0.567 ms)

## Verdict
**ACCEPTED** — p99 is 1024× under the proposed 65 ms budget. Per-stage
decomposition is not measured separately (the loop runs as one
monolithic tick); for that level of detail, a JMH-grade benchmark is
required. This wave documents the aggregate latency only.

## Files
- Preregistration: `docs-v2/research/protocols/H-047.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp047CrossPillarLatencyTest.java`