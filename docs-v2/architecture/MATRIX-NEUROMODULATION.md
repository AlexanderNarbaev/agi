# MATRIX — Neuromodulation and internal regulation

**Status:** implemented in W44 · 2026-11-02
**Code:** `matrix-core/src/main/java/io/matrix/cognitive/neuromodulation/`, `io.matrix.cognitive.DriveSignal`
**Map:** `docs-v2/engineering/W44-CAPABILITY-MAP.md` §2 (rows 15, 16, 17, 18, 19, 20)

---

## 1. What was found, and what was therefore done

The reconnaissance found that **every hormone-like subsystem in this repository is either
unreachable or neutral**:

| System | Where | Production caller | Constants |
|---|---|---|---|
| `BiochemicalNetwork` | `matrix-core/…/federation/liquid/biochemistry/` | `IntegratedFederation`, which has no production caller | 10 hand-picked synergy/antagonism/catalysis coefficients, **no cited source** |
| `BiochemicalOrchestrator` | same package | the same unreachable path | production/decay/initial values, hand-picked and duplicated verbatim in `IntegratedFederation` |
| `KineticModulator` | `matrix-core/…/federation/liquid/` | the same island | mood and sleep thresholds, hand-picked; `getSleepNeed` reads a modulator (`ADENOSINE`) that is never registered |
| `CognitiveModulationBridge` | `matrix-core/…/federation/integration/` | **none** | reads two modulator ids that are never registered, so it always reads its `0.5` fallback |
| `Impulse` + `AttentionRouter` | `matrix-core/…/consciousness/` | `AttentionRouter.merge` is called with an **empty** impulse list in every production caller | — |
| `NeuroModulatorHub` | `matrix-brain-runtime/…/graph/` | `TrueMindCycle` → `HdcRetrievalStage` | the retrieval floor is MEASURED (AUC 0.716, `TUNING-PARAMETERS.md`); every other value is documented by its own author as "plumbing, not claims", and the attention that drives the floor is pinned at `NEUTRAL_ATTENTION = 0.0` |

**W44 therefore does not revive any of them.** Reviving an island whose coefficients were never
measured would import unmeasured numbers into a decision path, which is the opposite of what
the directive asks for. What W44 builds is a NEW, bounded, typed regulatory layer that is
actually reachable from the serving path — and the old islands stay documented as INACTIVE.

---

## 2. The rule that governs the whole layer

> A biological chemical must not be represented as an arbitrary magic number. A regulator must
> not be permitted to change a decision merely because its name suggests that it should.

The implementation of that rule is structural rather than a promise:

1. **A drive is a typed dimension with a bounded level, a named source and exactly ONE allowed
   effect.** `DriveSignal` has an `Effect` enum, and the effect is part of the record, so
   "what may this drive influence" is a property of the drive and not a decision made later.
2. **The allowed effects are only these:** `ALGORITHM_COST_PREFERENCE`, `EVIDENCE_REQUEST`,
   `TASK_ADMISSION`, `TASK_PRIORITY`, and `NONE`. There is deliberately **no** effect for
   confidence, truth, permission or safety. A drive cannot make a claim more established and
   cannot authorise anything.
3. **A drive reaches a subsystem only as a `RegulationBias`**, a four-field bounded value:
   a cost-weight multiplier, an evidence-request threshold, a task-admission flag and a task
   priority multiplier. The neutral bias is exactly `(1.0, 1.0, true, 1.0)`, so a system with
   no regulatory state behaves exactly as a system with neutral regulation — which is what
   makes the effect of a drive MEASURABLE by comparing two runs that differ in one drive.
4. **The safety boundary is independent of the regulation.** The reflection test in
   `RegulationSafetySurfaceTest` asserts that the declared field set of `RegulationBias` is
   exactly those four, so a future change that added a field capable of weakening a gate would
   fail the test rather than pass it.

---

## 3. The dimensions

| Dimension | Meaning | Effect it may bias |
|---|---|---|
| `ATTENTION` | how sharply the system filters what it attends to | `ALGORITHM_COST_PREFERENCE` |
| `UNCERTAINTY` | how poorly the current model discriminates between the live hypotheses | `EVIDENCE_REQUEST` |
| `REWARD_PREDICTION_ERROR` | the difference between the expected and the achieved outcome | `TASK_PRIORITY` |
| `NOVELTY` | how much the current input differs from what the model has seen | `TASK_PRIORITY` |
| `SUSTAINED_LOAD` | accumulated operational pressure that has not been relieved | `ALGORITHM_COST_PREFERENCE` |
| `VIGILANCE` | readiness to react to an unexpected event | `EVIDENCE_REQUEST` |
| `CURIOSITY` | the push to resolve an identified knowledge gap | `TASK_ADMISSION` |
| `CONSOLIDATION_DEMAND` | the need to consolidate what was learned | `TASK_ADMISSION` |

These are **computational** dimensions, not biochemical concentrations. `UNCERTAINTY` means "the
internal model does not discriminate between the live hypotheses"; it is not cortisol, and
`SUSTAINED_LOAD` is not a hormone level. Where a biological analogy is used it is a functional
label for a control role, never a claim about a concentration.

---

## 4. The dynamics

`DriveDynamics` is deterministic: the caller supplies the events and the step count, and there
is no `Random` and no wall clock in the state transition (CONSTITUTION Article I).

Each dimension has a baseline, a decay rate, a production rate per event kind, a minimum and a
maximum. The levels are bounded to `[0, 1]` and the record refuses an out-of-range level.

**The constants are CONVENTIONS, not measurements.** Every one of them is a named constant with
a comment saying so. They were chosen to make the DIRECTION of each influence visible and
testable; their MAGNITUDES are not calibrated against anything, and this document does not
claim they are. The one number in the layer with an external basis is the consolidation floor,
whose reasoning is inherited from the existing `NeuroModulatorHub.sleepDemand`
(`matrix-brain-runtime/…/graph/NeuroModulatorHub.java:149-183`): a reward-rich environment may
relieve a consolidation demand, but never below a floor, so consolidation can be slowed and
never prevented.

---

## 5. The measured effect

The directive requires that "at least one neuromodulatory signal changes real downstream
behaviour in a controlled and measured way". W44 demonstrates it in the kernel, where the
difference between two runs is attributable to ONE drive:

| Run | Regulatory state | Behaviour |
|---|---|---|
| A | neutral | the kernel does **not** request a confirmation it did not need: the trace has no probe with the marker `a confirmation the regulation asked for` |
| B | the same model, the same data, with `PREDICTION_ERROR` reported once | the uncertainty rises, the evidence-request threshold falls below 1.0, and the kernel DOES request a confirmation |

The assertion is in `CognitiveKernelTest.theRegulationAsksForAConfirmingExperiment()`: both runs
use the same cases and the same settled model, and the only difference is the regulatory state.
The second run also finds the error that run A could not find, because run A never looked.

**What is NOT measured:** the size of the effect of any drive on any downstream decision, and
the calibration of any constant. The layer's claim is about DIRECTION and BOUNDEDNESS, not
about magnitude.

---

## 6. The relationship with the existing islands

| Question | Answer |
|---|---|
| Does the kernel use `NeuroModulatorHub`? | **No.** It lives in `matrix-brain-runtime`, which `matrix-core` cannot depend on, and it carries the retrieval floor whose measured behaviour must not change. |
| Does the kernel use `BiochemicalNetwork`? | **No.** It is unreachable and its coefficients have no cited source. |
| Does the kernel use `Impulse` / `AttentionRouter`? | **No.** Their impulse list is empty in every production caller, and a second attention controller would be the competing controller the directive warns against. The kernel's action arbitration is the SELECT phase, which is a decision about which computation to run, not a second priority queue. |
| Does the kernel use `GoalManager` / `GoalTracker`? | **No.** `CognitiveTask` is a bounded unit of internal work with its own store. It never masquerades as a `LearningGoal`, and the kernel does not create or complete learning goals. |
| Will the old islands ever be revived? | Not in W44. If they are, they must first be measured — the same requirement this layer was held to. |

---

## 7. The safety invariants, and how they are tested

| Invariant | How it is enforced | Where it is tested |
|---|---|---|
| No regulator may lower a FROZEN safety constraint | `RegulationBias` has no field that could express it | `RegulationSafetySurfaceTest` (reflection over the declared field set) |
| No regulator may bypass authentication | no effect reaches authentication; the bias is consumed only by the selector and the task admission | `RegulationSafetySurfaceTest` |
| No regulator may authorise an external action | the only action type in W44 is a probe inside the micro-world | the kernel's action path |
| No regulator may suppress audit records | the drives do not reach the store | `RegulationControllerTest` |
| No regulator may erase contradiction history | the drives do not reach the store; the cases are written through the W43 transactional path | `CognitiveKernelTest` |
| No regulator may silently increase confidence | there is no confidence effect | `RegulationSafetySurfaceTest` |
| No regulator may prevent consolidation | the consolidation demand has a floor that a reward cannot cross | `RegulationControllerTest` |

---

## 8. Deferred

| Deferred | Why |
|---|---|
| Calibrated constants | Nothing in this wave measured them. The honest next step is a controlled experiment that varies one drive and measures one downstream effect; the machinery for that is the `RegulationEffectMeasurementTest` that ships with the layer. |
| A physiological vocabulary (acetylcholine, oxytocin, endorphins as such) | The directive is explicit that a digital signal must not be presented as a reproduction of a biological concentration. The functional mapping is documented; the name is not borrowed. |
| A hormonal coupling matrix | The existing `BiochemicalNetwork` has one and it is hand-picked. W44's interactions are the event→dimension productions, which are explicit and testable. |
