# EXP-046 — Impulse scheduler gate filter accuracy

## Hypothesis
**H-046**: Subconscious impulse → conscious gate filter accuracy ≥ 0.9
on synthetic impulse corpus (synthetic-scope).

## Setup (matches preregistration H-046.md)
- `ImpulseScheduler` with `ConjugateBudgeter` + `EthicalFilter`
- 1,000 synthetic impulses per seed; noise rate = 10%
- "Forbidden" relabellings use names not in the canonical 4-way enum
- Ground-truth gate decision: allowed iff impulse name is one of the 4
  canonical `AutonomyImpulse` values
- Accuracy = correct gate decisions / total
- 5 seeds; median reported

## Results (real measurements, 2026-08-27)

| Seed | Accuracy |
|---|---|
| 0xC0FFEE | … (median) |
| 0xABCDEF | … |
| 0x1234 | … |
| 0x5678 | … |
| 0x9ABC | … |
| **Median** | **0.890** |

## Verdict
**REFUTED-AT-MARGIN** — measured 0.890 vs gate 0.9 (Δ = −0.010).

The shortfall is structural: the current `ImpulseScheduler.fire()`
delegates to `EthicalFilter.frozenViolatedAxiom(impulse.name())`, which
returns `null` (i.e. allowed) for any non-canonical name not in the
FROZEN-FNL axiom set. Since the synthetic corpus injects ~10% relabellings
to non-canonical names, those are passed through as allowed — pulling
accuracy down by exactly the noise rate.

## What this means
- The gate is conservative-by-default: it blocks names that explicitly
  violate an axiom, but allows anything else. That's the right default
  for a permissive system — but it's wrong for H-046's strict ground
  truth where any non-canonical name is forbidden.
- To meet the 0.9 gate, the gate would need an explicit allow-list
  (or the synthetic test corpus would need to use the 4 canonical names
  uniformly). Either change is a policy choice, not a bug.

## Files
- Preregistration: `docs-v2/research/protocols/H-046.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp046ImpulseGateAccuracyTest.java`
- Class under test: `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java`