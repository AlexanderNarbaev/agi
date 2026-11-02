# DESIGN-72 — The cognitive kernel

**Status:** implemented in W44 · 2026-11-02
**Supersedes:** nothing. **Related:** `ADR-W44-01-cognitive-kernel.md`,
`docs-v2/architecture/MATRIX-COGNITIVE-KERNEL.md`,
`docs-v2/architecture/MATRIX-NEUROMODULATION.md`,
`docs-v2/architecture/MATRIX-ADAPTIVE-COMPUTATION.md`,
`docs-v2/research/MATRIX-COGNITIVE-RESEARCH-AGENDA.md`

---

## 1. The design in one paragraph

MATRIX gains a **kernel**: one component that takes a signal it does not understand, turns it
into a percept, consults a world model it does not fully trust, makes a prediction it records
BEFORE it knows the answer, chooses a computation by declared preconditions and measured cost,
acts inside a bounded sandbox when the model does not settle the question, compares what it
predicted with what it observed, and records the difference as an error with provenance instead
of quietly adjusting the model. Everything it learns that is reusable is promoted into a
**procedure** only after it has been validated against the expensive reference on held-out
cases, and every procedure names the reference it falls back to. The internal state that
governs how much the system weighs cost, when it asks for more evidence and whether it admits
a task of its own is a **typed regulatory layer** with four bounded fields, and it can never
change a fact, a confidence value or a safety gate.

---

## 2. The decisions, and why they were made this way

### 2.1 The kernel is in `matrix-core`, and it is a plain object

**Decision.** `io.matrix.cognitive.*` in `matrix-core`. The kernel takes its collaborators as
constructor arguments; it is not a bean and has no plugin discovery.

**Why.** `matrix-core` is the only module with the Quarkus plugin, the owner of the epistemic
store and the workbench. The kernel has to be reachable from a user request and has to write
into the same SQLite file, or it becomes a second application with a second knowledge store —
which the W44 directive forbids in as many words. A plain object is what makes the kernel
testable without a container: the tests drive it exactly as the HTTP endpoint drives it.

**Cost.** The kernel's collaborators are narrow interfaces (`AlgorithmSelectorBridge`,
`RegulatoryBridge`) rather than the concrete classes. That is a small indirection paid for the
ability to replace the selector with a measured policy later without touching the kernel.

### 2.2 The epistemic model is reused, not duplicated

**Decision.** Claims, evidence, sources, derivations and revisions stay in the W43 store. The
kernel writes observations there through `KnowledgeWorkbench.recordObservation`. A new
**`Case`** entity — the model `ADR-W43-01` §4.5 planned and `RECON-W43-STATUS.md` recorded as
`PLANNED` — carries the conditions an observation was made under.

**Why.** Two knowledge stores is how a repository ends up with two answers to "what does the
system believe". The case model is the one thing that was missing from the W43 core, and the
W44 directive is the owner decision that unblocks it. The case id is derived from the
conditions AND the outcome, so a repeated write updates the row instead of doubling the
evidence.

**Cost.** The case table is named `epistemic_case`, because `CASE` is a reserved SQL word and
every query would otherwise need quoting.

### 2.3 The prediction and the observation are different objects

**Decision.** `Prediction` has no "confirmed" field. Whether a prediction held is decided by
the comparison with an `ErrorSignal`, never by the prediction itself.

**Why.** A prediction that can mark itself confirmed is how a system ends up citing its own
guess as evidence. The separation also makes the ambiguous case expressible: a prediction whose
surviving hypotheses disagree records the disagreeing ids and stays ambiguous.

### 2.4 The model is not rewritten by a counterexample

**Decision.** An observation that the model cannot explain produces an `ErrorSignal` with a
disposition — `UNRESOLVED` for a confident wrong answer, `MISSING_CONDITION` for an outcome no
hypothesis accounts for — and a **bounded** `CognitiveTask`. The hypothesis set is left
standing and the review flag is set.

**Why.** Silently rewriting the model on the latest observation is how a learner overfits the
most recent data. The task is what makes the failure actionable: it carries a budget, a stop
condition, a completion condition and an allow-list of one action (`probe-microworld`).

### 2.5 The portfolio declares its assumptions and is selected by them

**Decision.** Every method declares `requiredPreconditions`, a complexity profile, a resource
profile, an **accuracy evidence with a denominator**, a determinism profile, a reliability
profile, a verification strategy and a **fallback algorithm**. The selector admits a method
only when every declared precondition is established, and scores the admitted ones by
`accuracy − costWeight × relativeCost`.

**Why.** `Dijkstra`'s class javadoc says "non-negative weights" and the code never checks
them. The portfolio's wrapper does, and the wrapper refuses even when the caller asserts the
fact — the selector's check and the method's own check are separate, and the method's wins.

**Cost.** The selector compares **per-call** costs and does not amortise a preprocessing cost.
The benchmark measures the break-even (288 queries for a 64-query corpus) and shows that the
scan is 2.9× cheaper end to end at that scale — and the selector picks the index anyway,
because `INDEX_PREBUILT` is the caller's assertion. This is recorded as an open item.

### 2.6 A fast path is promoted only after it is validated, and only inside its domain

**Decision.** The promotion requires agreement with the reference on **every** held-out case
and a minimum case count. The promoted procedure stores its applicability domain
(`MODEL_SETTLED`) and its reference fallback. The method checks the domain itself at
admission.

**Why.** The W44 directive §22 separates three kinds of reuse — a cached ANSWER, a cached
PROCEDURE and a learned MODEL — and each has different validation requirements. Only the
middle one is implemented, because it is the one the kernel can justify. The research wave
found the promotion rule too strict in one direction and too weak in another (H-113, H-116),
and both are recorded as open.

### 2.7 The regulation is typed, bounded and structurally incapable of weakening a gate

**Decision.** Eight named dimensions, each with exactly one allowed effect. The effect reaches
a subsystem only as a `RegulationBias` with four fields. There is no effect for confidence,
truth, permission or safety.

**Why.** The directive's rule is that a regulator must not change a decision merely because its
name suggests it should. The old islands failed that rule in the most expensive way: their
coefficients were hand-picked with no source, and nothing could tell them apart from a real
mechanism. `DriveSignal.Effect.NONE` is the honest form of "a frame, not a mechanism".

**Cost.** The constants are CONVENTIONS, not calibrated values. The wave measures the
DIRECTION and the ATTRIBUTION of each influence and claims nothing about the magnitude.

### 2.8 The world model is the memory

**Decision.** The micro-world session is rebuilt from the CASES on every cycle. There is no
private copy of what the system saw.

**Why.** A private copy is a second source of truth about what happened, and it is the copy
that survives a restart. The cases are the durable record, so a restart cannot make the system
forget — and the workbench's Epistemic panel shows the same evidence the cognitive trace does.

**Cost.** A session rebuilt from the cases has no memory of its own prior PREDICTIONS, so a
prediction error is detected by the kernel's own comparison rather than by the session. The
teammate's report measured the consequence: **one cycle of latency** on an unexpected outcome,
not a lost observation.

---

## 3. The cycle, in code

| phase | class | what it decides |
|---|---|---|
| PERCEIVE | `ChannelClassifier` | the FORMAT of the payload; malformed → refuse |
| RESTORE | `MicroWorldSession` + `MicroWorldCodec` | the model, rebuilt from the cases |
| PREDICT | `MicroWorldSession.predict` | what the model believes, and how confidently — recorded BEFORE the outcome |
| SELECT | `AlgorithmSelector` | which computation, and why — recorded with every rejected candidate |
| COMPUTE | the selected method | the answer, or a refusal |
| PROPOSE / PROBE | `MicroWorldSession.proposeExperiment`, the environment | what to try next, and what the world said |
| OBSERVE | `KnowledgeWorkbench.recordObservation` | the case, the evidence, the relation — computed, not assumed |
| LEARN | `ProcedureValidator` | whether a cheaper procedure may be promoted |
| REGULATE | `RegulationController` | the bounded bias for the NEXT decision |
| ANSWER | the kernel | the answer, with the trace |

---

## 4. What the research waves changed about this design

Three cross-disciplinary waves (R-A — contemporary ML/DL, R-B — cybernetics and computational
cognition, R-D — early-learning neuroscience) were raised in parallel, as the owner rule
META-R1 requires for a change of this class. Their findings are in
`docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §9, and the hypotheses are in
`HYPOTHESES-NEW.md` (H-113…H-128). Three of them bear on THIS document:

| hypothesis | what it says about this design | what this design does about it |
|---|---|---|
| **H-122** (Spelke et al. 1994; Spelke & Kinzler 2007) | "enumerate all sixteen Boolean functions" is not a neutral start — it already presupposes truth-functional rules over the declared variables | the design **keeps** the complete space (completeness is what makes the measurement meaningful) and records that completeness is a **declared** assumption, not a discovered neutrality. A prior is NOT introduced in W44: a prior can hide a non-monotone true rule, and the escape hatch it would need is not built. |
| **H-124** (Cashon & Cohen 2000; Munakata 2000; Rankin et al. 2009) | "any deviation is a model error" fires on perceptual noise as readily as on a wrong model | the design **separates** the error from the surprise: `ErrorSignal` carries a disposition (`MODEL_DEFECT`, `EXTERNAL`, `MISSING_CONDITION`, `UNRESOLVED`) and a provenance, and the revision path requires the disposition, not a reaction. A perceptual-noise floor is NOT measured in W44 — that is the open item. |
| **H-123 / H-126** (Gopnik et al. 2001; Kushnir & Gopnik 2005) | passive covariation is a free experiment, and a promoted procedure must carry the regime in which it matched | the design stores the applicability domain and the reference fallback, and the "regime" (the conditions of the held-out cases) is part of the stored `Case` — but the promotion rule does not yet **compare** regimes, and there is no `CONFOUNDED` flag on an experiment. |

Two of the three (H-124, H-126) are partially satisfied by construction and partially open;
one (H-122) is a documented **deliberate** choice with a named cost. None of them changes the
architecture of this document, and none of them was implemented silently: the wave's own
report says which are open.

---

## 5. What this design does NOT claim

- It does not claim general intelligence, consciousness, or understanding. It claims a
  measurable loop.
- It does not claim that the neuromodulatory constants are calibrated.
- It does not claim that the portfolio is representative: four families and one domain, out of
  a library of 116 algorithms.
- It does not claim that a settled model is correct: a settled model that the environment
  contradicts is exactly the case the error path exists for.
- It does not claim a GPU result, and it does not claim that the brain runtime and the
  epistemic core have been merged.
