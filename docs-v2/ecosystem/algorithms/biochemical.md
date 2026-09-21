---
layout: default
title: Biochemical Modulator Network
parent: Algorithms
nav_order: 6
permalink: /ecosystem/algorithms/biochemical/
---

# Biochemical Modulator Network

## What is the Biochemical Network?

The **Biochemical Modulator Network** is a system of coupled ordinary
differential equations (ODEs) that simulate a regulatory network — similar
to gene regulatory networks in biology. MATRIX uses it to model the
4 FROZEN modulators (Ethics, Safety, Consistency, Lie Detector).

## Mathematical Foundation

Each modulator's level follows a logistic ODE with cross-inhibition:

```
dE/dt = α_E * (1 - E) - β_E * E * S - γ_E * E * L
dS/dt = α_S * (1 - S) - β_S * S * E - γ_S * S * C
dC/dt = α_C * (1 - C) - β_C * C * S - γ_C * C * L
dL/dt = α_L * (1 - L) - β_L * L * E - γ_L * L * C
```

Where E, S, C, L ∈ [0, 1] are modulator levels and α, β, γ are positive
constants tuned to give biologically-plausible dynamics:
- **Stable rest state**: each modulator near 1.0 by default
- **Transient inhibition**: when one modulator drops, neighbors rise
- **Recovery**: exponential return to rest after perturbation

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/brain/modulators/`](https://github.com/AlexanderNarbaev/agi/tree/develop/matrix-core/src/main/java/io/matrix/brain/modulators)

Key classes:
- `BiochemicalNetwork` — the 4-ODE system
- `Modulator` — wraps level + threshold + veto rule
- `ModulatorRegistry` — the 4 FROZEN entries

Performance:
- **1 step**: ~10µs (4 simple ODEs)
- **1000 steps**: ~10ms (typical inference uses ~100 steps)

## History

The biochemical metaphor for cognitive regulation comes from:

- **Minsky** (1986, "Society of Mind") — k-lines and nemes
- **Varela, Thompson, Rosch** (1991, "The Embodied Mind") — autopoiesis
- **Maturana & Varela** (1980) — biological autonomy

MATRIX treats modulators as **autopoietic agents** — they maintain their own
identity (the FROZEN contract) while responding to perturbations.

## Pros

- ✅ Smooth dynamics (no jitter)
- ✅ Coupling between modulators (one fails → others compensate)
- ✅ Biologically plausible
- ✅ Easy to tune (4 ODEs, ~12 parameters)

## Cons

- ❌ ODE integration has floating-point error
- ❌ Coupling constants need careful tuning
- ❌ Cannot reason about modulator logic (it's analog)

## When the Biochemical Network runs in MATRIX

Every inference cycle ends with modulator evaluation:

```
[MCTS_PLAN_SELECTED]
  ↓
[MODULATORS_APPLIED]   ← BiochemicalNetwork.step()
   ├─ ODE integration (4 modulators × 1 step)
   ├─ Each modulator votes pass/fail based on level
   └─ ALL must pass for accepted=true
  ↓
[Output]
```

The modulator snapshot is recorded in the XAI trace:

```json
{
  "modulator_snapshot": {
    "ethical_filter": 0.95,
    "safety_monitor": 0.92,
    "consistency_checker": 0.88,
    "lie_detector": 0.91
  }
}
```

## CONSTITUTION compliance

Per Article IV, the 4 modulators are **FROZEN**. Their dynamics are
encoded in matrix-core at the lowest level. No developer can override
them via API or configuration. This is enforced by CI:

```yaml
# .github/workflows/branch-validation.yml
- name: Verify modulator registry is unchanged
  run: |
    diff <(git show main:BiochemicalNetwork.java) BiochemicalNetwork.java
```

---

**Last updated:** 2026-09-21 (Wave T-03)
