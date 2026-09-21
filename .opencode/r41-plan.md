# R41-Plan: Wave RUN 41-50

## Goal

Continue wave-by-wave. RUN 12-40 are complete (447 tests, 0 failures).
This plan covers H-verifications (H-048, H-049, H-050), production
integrations, and remaining FINALSUMMARY items.

## RUNs

### RUN 41 — H-050 arousal dynamics verification

**Why**: arousal-update monotonicity under strictly-increasing
prediction-error stream.

**Tasks**:
1. New `ArousalDynamics` class with monotonic update function.
2. Property test: strictly increasing prediction error → strictly
   increasing arousal.
3. Falsification: counterexample sequence must produce arousal decrease.

**Done when**: 3+ tests pass, H-050 acceptance criterion met.

### RUN 42 — H-048 emergence of behavior verification

**Why**: N=1000 cycles preserve stable action-distribution entropy
and decision-tree shape.

**Tasks**:
1. Run 1000 ConsciousnessLoop ticks with seed-fixed replay.
2. Measure action-distribution entropy at intervals.
3. Compute drift between snapshots.

**Done when**: 3+ tests pass, drift below threshold.

### RUN 43 — Wire StageLatencyTracker + FreezeRecoveryManager into ConsciousLoop

**Why**: standalone components need production integration.

**Tasks**:
1. Add timing instrumentation to ConsciousLoop.tick().
2. Wire FreezeRecoveryManager into BrainLoopService so ethics
   violations trigger freeze.
3. Tests verify integration.

**Done when**: 3+ tests pass.

### RUN 44 — Tenant endpoint improvements

**Why**: TenantQaResource is functional but lacks pagination,
filtering.

**Tasks**:
1. Add pagination (offset, limit).
2. Add category filter.
3. Tests.

**Done when**: 3+ tests pass.

### RUN 45 — MetricsResource enhancement

**Why**: /v1/metrics is functional but could expose chain
runner per-layer stats.

**Tasks**:
1. Add chain per-layer stats (avg activations per layer).
2. Add temperature histogram.

**Done when**: 2+ tests pass.

### RUN 46 — H-verifier scaffolding

**Why**: hypothesis verification pattern is now standardized;
formalize it as a class.

**Tasks**:
1. New `HVerifier` abstract class with runHypothesis method.
2. Existing EXPs use it for consistency.

**Done when**: 2+ tests pass.

### RUN 47 — Final docs stabilization (RUN 41-46)

**Tasks**:
1. FINALSUMMARY §§L-LV for RUN 41-46.
2. WAL.md append.
3. RELEASE-NOTES.md update.

### RUN 48 — Additional improvements

Anything new that emerges.

## Estimated time

~3h total.
