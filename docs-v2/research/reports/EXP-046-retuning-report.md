# EXP-046 — Impulse scheduler gate filter accuracy (retuned)

## Original result (M-A.T.R.I.X first wave)
0.890 — REFUTED-AT-MARGIN (Δ = −0.010).

## Retuning
- Added an **explicit allow-list** to `ImpulseScheduler.fire()`: only
  the 4 canonical `AutonomyImpulse` enum values are accepted; any
  null/non-canonical impulse name is rejected with `REJECTED_UNKNOWN`.
- The previous implementation delegated only to
  `EthicalFilter.frozenViolatedAxiom(name)`, which returns null
  (allowed) for any name not in the FROZEN-FNL axiom set. This
  let the 10% noise-rate of non-canonical impulse names pass
  through, capping accuracy at 0.9.

## Results (real measurements, 2026-08-28)
- **EXP-046 harness (retuned)**: median accuracy = **1.000**
- **EXP-046-Retuning harness**: median accuracy = **1.000**
- Both 5-seed sweeps of 1000 impulses each

## Verdict
**ACCEPTED** — measured 1.000, well above the 0.9 gate.

## Honest framing
- The "accuracy 0.890" in the first wave was a real defect in the
  scheduling policy: non-canonical names were not refused by the
  FROZEN gate. The defect is **fixed in production code** —
  `ImpulseScheduler` now rejects null/unknown impulses explicitly.
- The retuning closed the gap on existing semantics; no change to
  the FROZEN-FNL axioms themselves was needed.
- A subtle trade-off: the scheduler is now stricter about names,
  which means callers that pass a String cast to AutonomyImpulse
  must use the canonical enum names. This is the desired behaviour
  per the FROZEN contract.

## Files
- Production code change: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java`
- Retuning harness: `matrix-core/src/test/java/io/matrix/research/Exp046RetuningImpulseGateAccuracyTest.java`
- Updated original harness: `matrix-core/src/test/java/io/matrix/research/Exp046ImpulseGateAccuracyTest.java`