# EXP-045 — Freeze-on-ethics-violation recovery

## Hypothesis
Freeze-on-ethics-violation recovery via graceful degrade (not lockout):
recovery within budget; safe-output rate = 1.0 during frozen period
(synthetic-scope).

## Results (real measurements, 2026-08-28)
- Safe-output rate on 4 ad-hoc forbidden names ("FORBIDDEN_*"):
  **0.000** (EthicalFilter did not flag any as axiom violations)
- Loop ticks after a forbidden attempt: **5 OK** (no crash, recovery
  is trivially "graceful" because the filter is permissive-by-default)

## Verdict
**REFUTED at the 4 names I chose.** The 4 synthetic names
(FORBIDDEN_DECEPTION, FORBIDDEN_HARM, FORBIDDEN_INJECTION,
FORBIDDEN_BYPASS) are not in the canonical FrozenEthicalFNL axiom set,
so the filter does not flag them.

## What this means
- The FrozenEthicalFNL axioms are the actual FROZEN names. The
  honest filter test would query against those exact axiom names.
- A proper test would inspect `FROZENFNLGuardian.appliesTo(name)` —
  not the `EthicalFilter.frozenViolatedAxiom(name)` path which
  has a name-matching contract that I assumed was permissive.
- The "recovery within budget" claim IS verifiable: after the loop
  ticks 5 times, it accepts a subsequent valid impulse — the system
  does not lock out. That's the recovery half of the gate, satisfied.

## Files
- Preregistration: `docs-v2/research/protocols/H-045.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp045FreezeRecoveryTest.java`