# W44 — Neuromodulation map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-CLASS-INVENTORY.csv`, `W44-CALL-GRAPH.csv` (bytecode), the modulator
registries and orchestrators, and the W44 measurement of the regulatory layer.

---

## 1. The four regulatory systems

| system | FQN | served | modulator kinds | written by | read by |
|---|---|---|---|---|---|
| **the brain hub** | `io.matrix.brain.runtime.graph.NeuroModulatorHub` | yes (3 callers) | dopamine, serotonin, noradrenaline, cortisol, acetylcholine | `TrueMindCycle` / `ModulatorStage` | `TrueMindCycle.finalize` — the confidence composition |
| **the liquid registry** | `io.matrix.federation.registry.DynamicModulatorRegistry` | yes | DOPAMINE, SEROTONIN, NOREPINEPHRINE, ACETYLCHOLINE, CORTISOL, ENDORPHIN, OXYTOCIN, GABA, GLUTAMATE, ADENOSINE | `DynamicModulatorRegistry` | the federation path |
| **the chemistry island** | `io.matrix.federation.liquid.biochemistry.BiochemicalNetwork` + `BiochemicalOrchestrator` | **island** | 10+ named transmitters with interaction matrices | `BiochemicalOrchestrator.tick` | `IntegratedFederation` — which is itself an island |
| **the W44 regulator** | `io.matrix.cognitive.neuromodulation.RegulationController` | yes | 8 **drives**, not chemicals: ATTENTION, UNCERTAINTY, REWARD_PREDICTION_ERROR, NOVELTY, SUSTAINED_LOAD, VIGILANCE, CURIOSITY, CONSOLIDATION_DEMAND | `CognitiveKernel` | `CognitiveKernel`'s SELECT phase |

**Plus the loose ends:** `io.matrix.consciousness.ArousalDynamics` and
`io.matrix.reasoning.ArousalDynamics` — **two classes with the same simple name in two
packages, both served**; `io.matrix.consciousness.Impulse` + `AttentionRouter` (the
router is called by `BrainLoopService.cycle` and `BirBrainCycle.cycle`, but `Impulse` is
referenced by nothing except `AttentionRouter.merge`); `io.matrix.mediator.DriverState`
and `InstanceMediator` (6 callers each).

---

## 2. Every candidate signal the directive names, and its true state

| signal | exists as | runtime implementation | production caller | influences a decision? |
|---|---|---|---|---|
| dopamine | enum + registry entry + orchestrator row | yes | yes (registry) | **no measurement** |
| serotonin | enum + registry entry + orchestrator row | yes | yes (registry) | **no measurement** |
| norepinephrine | enum + registry entry | yes | yes (registry) | **no measurement** |
| acetylcholine | enum + registry entry | yes | yes (registry) | **no measurement** |
| cortisol | enum + registry entry + antagonism row | yes | yes (registry) | **no measurement** |
| endorphin-like | enum + registry entry | yes | yes (registry) | **no measurement** |
| oxytocin-like | enum + registry entry | yes | yes (registry) | **no measurement** |
| GABA-like | enum + registry entry | yes | yes (registry) | **no measurement** |
| glutamate-like | enum + registry entry | yes | yes (registry) | **no measurement** |
| adenosine-like | enum + registry entry | yes | yes (registry) | **no measurement** |
| attention | W44 drive | yes | yes (kernel) | yes — the drive bias is recorded |
| uncertainty | W44 drive | yes | yes (kernel) | **yes, measured** |
| reward / prediction error | W44 drive | yes | yes (kernel) | yes |
| novelty | W44 drive | yes | yes (kernel) | yes |
| sustained load | W44 drive | yes | yes (kernel) | **yes, measured** |
| vigilance | W44 drive | yes | yes (kernel) | no effect in the current bias |
| curiosity | W44 drive | yes | yes (kernel) | yes |
| consolidation demand | W44 drive | yes | yes (kernel) | yes |

**Every candidate the directive lists has a runtime implementation.** What none of the
*chemical* ones has is a measurement: the repository contains **no experiment that compares
a decision taken with and without a modulator**, and no modulator is credited in a trace
with the outcome it produced.

---

## 3. The chemistry island, in detail

`io.matrix.federation.liquid.biochemistry.BiochemicalNetwork` declares an interaction
matrix — for example `new Interaction("CORTISOL", "DOPAMINE", ANTAGONISM, 0.7, 0.8)` and
`new Interaction("DOPAMINE", "SEROTONIN", SYNERGY, 0.3, 0.4)` — and
`BiochemicalOrchestrator` declares production and decay values per modulator.

**Every one of those coefficients is hand-picked and has no cited source.** The audit
checked the file and found no citation, no measurement and no test that derives a
coefficient from data. The class is named `BiochemicalNetwork`, which invites a reading
it does not deserve: these are not the constants of a biochemistry, they are constants
someone chose.

The island is reachable from `IntegratedFederation`, which is itself reachable only from
`AdversarialAttackSuite`, `EmergentBehaviorMonitor`, `FederatedLearningEngine` and
`SwarmScaleExperiment` — experiment harnesses. **No serving path reaches a chemical
modulator.**

---

## 4. The W44 regulatory layer — the only measured one

`RegulationBias` has exactly four fields:

| field | neutral | what moves it | measured effect |
|---|---|---|---|
| `costWeightMultiplier` | 1.0 | `SUSTAINED_LOAD` | load 0 → 1.0 moves it 1.000000 → **1.500000**; the other three fields are unchanged |
| `evidenceRequestThreshold` | 1.0 | `UNCERTAINTY` | uncertainty 0.15 → the threshold is **0.925000**; without it, 1.000000 |
| `admitNonCriticalTask` | true | `CONSOLIDATION_DEMAND` | — |
| `taskPriorityMultiplier` | 1.0 | `NOVELTY`, `CURIOSITY` | — |

The load→cost curve, measured over ten samples: 0.5 → 1.000000, **0.6 → 1.100000**, 0.7 →
1.200000, 0.8 → 1.300000, 0.9 → 1.400000, 1.0 → 1.500000. The preference starts to bite
between the fifth and the sixth sample.

**What makes this layer different from the chemical one** is not the model, it is the
discipline: four fields, one allowed effect per drive, no effect that can express a
change to a fact, a confidence value, a permission or a safety gate, and a reflection
test that fails if a fifth field is added. The constants remain conventions, and the
benchmark says so in as many words.

---

## 5. The `Impulse` dead end, in detail

`io.matrix.consciousness.Impulse` has **one** production caller: `AttentionRouter.merge`.
`AttentionRouter` itself has 5 production callers, including `BrainLoopService.cycle` and
`BirBrainCycle.cycle` — both on serving paths.

So the router runs, and the router merges impulses, and **nothing in the repository
produces an impulse**. The earlier wave recorded this as "the merge is always called with
an empty impulse list"; the bytecode shows the *producer* is absent, which is a stronger
and different statement. The audit does not claim to have verified the emptiness claim
itself.

---

## 6. What the map forces

1. **Do not wire the chemistry island.** It has no sources, no measurement, and its root is
   an experiment harness.
2. **Measure one chemical modulator before adding another.** The registry has ten; the
   repository has zero outcome-level measurements.
3. **Merge the two `ArousalDynamics` classes.** Two classes with one name in two packages,
   both served, is a bug waiting for a reader.
4. **Either produce impulses or delete the type.** A router that merges nothing is a
   no-op with a cost.
5. **Keep the W44 layer's shape** — four bounded fields, one effect per drive, no effect
   that can weaken a gate — and extend it with drives, not with chemicals.
