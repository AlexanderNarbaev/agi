# R47-Plan: Wave RUN 47-55

## Goal

Continue wave-by-wave. RUN 12-46 are complete (483 tests, 0 failures).
This plan covers H-verifications, production integrations, and
remaining FINALSUMMARY items.

## RUNs

### RUN 47 — H-047 stress test under realistic load

**Why**: RUN 40 verified latency budgets under light load. Stress
test under realistic load needed.

**Tasks**:
1. Stress test: 100 concurrent ticks × 50 iterations.
2. Measure per-stage max latency under contention.
3. Verify budget adherence (p99 within budget for ≥9/10 runs).

**Done when**: 3+ tests pass, p99 numbers recorded.

### RUN 48 — H-049 share-impulse verification

**Why**: H-049 hypothesis: share-impulse fires when M3 quorum
acceptance crosses utility threshold.

**Tasks**:
1. Implement synthetic M3Quorum tracker.
2. ShareImpulseFirer fires when utility > θ_s.
3. EXP test verifies firing rate.

**Done when**: 3+ tests pass.

### RUN 49 — Wire FreezeRecoveryManager into BrainLoopService

**Why**: standalone FreezeRecoveryManager needs production integration.

**Tasks**:
1. BrainLoopService holds a FreezeRecoveryManager.
2. On ethics violation, reports and gates actions.
3. Tests verify integration.

**Done when**: 3+ tests pass.

### RUN 50 — Wire ArousalDynamics into ConsciousnessLoop

**Why**: standalone ArousalDynamics needs production integration.

**Tasks**:
1. ConsciousnessLoop holds an ArousalDynamics.
2. tick() updates arousal based on prediction-error.
3. Tests verify integration.

**Done when**: 3+ tests pass.

### RUN 51 — Sparse weight storage: fix decay rule

**Why**: RUN 29 found sparsity is 0% because decay keeps non-firing
slots non-zero. Fix: floor-at-zero for non-firing.

**Tasks**:
1. Add `floorDecay` mode to LmHead.
2. Implement floor-at-zero for non-firing slots.
3. Recompute sparsity under floor-decay.

**Done when**: 3+ tests pass, sparsity > 0.

### RUN 52 — Native build retry attempt 2

**Why**: RUN 18 found cascading class-init issues.

**Tasks**:
1. Add more class-init entries to native config.
2. Try build again.

**Done when**: build runs, status reported.

### RUN 53 — H-044 calibration on production corpus

**Why**: RUN 35 used synthetic data. Verify on real corpus.

**Tasks**:
1. Train LM head on production Q&A pairs.
2. Measure ECE on held-out questions.

**Done when**: 3+ tests pass, ECE recorded.

### RUN 54 — Final docs + sign-off

**Tasks**:
1. FINALSUMMARY §§LVI-LXII.
2. WAL.md append.
3. RELEASE-NOTES update.

## Estimated time

~3h total.
