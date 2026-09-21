# EXP-041 — Offline dream-replay vs online retention

## Hypothesis
Offline dream-replay beats online retention on F1; ΔF1 ≥ 0.05
in favour of REM arm (synthetic-scope).

## Results (real measurements, 2026-08-28)
- F1 online (no replay): **0.000**
- F1 replay (REM arm): **0.000**
- ΔF1: **0.000** — well below the 0.05 gate

## Verdict
**REFUTED** at this setup. Both arms achieved 0 F1 because the
synthetic 20-episode corpus never reached the k=2 anonymity threshold —
the SubconsciousConsolidator never observed any episode as "anonymous"
in 10 cycles.

## What this means
- The synthetic setup is too small: each episode is recorded once
  per cycle, with 20 unique episodes circulating. The Anonymizer
  accumulates 1 contribution per episode per cycle, so the k=2
  threshold requires 2 nodes to record the same episode — but each
  node only sees one. With more nodes (the prompt's k=100), the
  rem arm would actually matter; with k=2 and a single node, the
  threshold is unreachable.
- The replay arm doubles the count per episode for cycles ≥ 5, but
  with a single node, that still doesn't cross k=2.
- This is a **measurement-vs-threshold-mismatch** rather than a
  discovery about the replay logic. A follow-up wave would need to
  either (a) lower k to 1 for the test, or (b) simulate multi-node.

## Files
- Preregistration: `docs-v2/research/protocols/H-041.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp041OfflineReplayRetentionTest.java`