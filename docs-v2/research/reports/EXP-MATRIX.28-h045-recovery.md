# EXP-MATRIX.28 — H-045 ethics violation recovery (RUN 39)

## Hypothesis (H-045)

After an ethics violation, the system gracefully degrades (enters
FROZEN state) and recovers after a cooldown — NOT a permanent
lockout. Recovery rate = 100% (synthetic-scope).

## Setup

- **New `FreezeRecoveryManager`** in `io.matrix.ethics`:
  - State machine: `NORMAL → FROZEN → RECOVERING → NORMAL`
  - `reportViolation()` enters FROZEN, blocks actions
  - `isActionAllowed()` checks cooldown; auto-transitions to
    RECOVERING when elapsed
  - `manualRecover()` bypasses cooldown (ops use only)
- Configurable freeze duration (default 1000ms; tests use 200ms).

## Results (real measurements, 2026-09-05)

| Test | Property | Result |
|---|---|---|
| `initialStateIsNormal` | starts in NORMAL | **PASS** |
| `violationEntersFrozenState` | report → FROZEN | **PASS** |
| `frozenStateRecoversAfterCooldown` | FROZEN → RECOVERING after cooldown | **PASS** |
| `multipleViolationsAreTracked` | all violations logged | **PASS** |
| `manualRecoveryReturnsToNormal` | manualRecover → NORMAL | **PASS** |
| `recoveryCountTracksAutoAndManualRecoveries` | counter accurate | **PASS** |
| `actionIsBlockedDuringFreeze` | FROZEN blocks actions | **PASS** |
| `freezeDurationIsRespected` | long duration keeps FROZEN | **PASS** |
| `reportViolationIsIdempotent` | repeated reports keep FROZEN | **PASS** |
| `zeroOrNegativeFreezeDurationClamped` | invalid duration clamped to ≥100ms | **PASS** |

All **10 Exp045H045FreezeRecoveryTest pass**.

## H-045 acceptance criterion

> Recovery в течение budget; safe-output rate 100% (synthetic-scope)

- Recovery within budget: ✅ `frozenStateRecoversAfterCooldown`
  confirms auto-recovery after cooldown elapses.
- Safe-output rate 100%: ✅ `actionIsBlockedDuringFreeze` confirms
  ALL actions during FROZEN are blocked.
- Recovery rate: ✅ `manualRecoveryReturnsToNormal` + auto-recovery
  tests confirm 100% recovery.

## Verdict

**H-045 accepted (synthetic-scope, RUN 39).**

## Cross-references

- HYPOTHESES-NEW.md H-045: was previously unverified. Now has
  full implementation + 10 test cases.
- ConsciousLoop integration is the next step (wire FreezeRecoveryManager
  into the loop's tick logic when an ethics violation is detected
  by the FROZEN-FNL filter). Documented as future work — current
  FreeRecoveryManager is a STANDALONE state tracker, not yet
  integrated into the loop.

## Test code

`matrix-core/src/test/java/io/matrix/ethics/Exp045H045FreezeRecoveryTest.java`
(10 tests, all pass).
