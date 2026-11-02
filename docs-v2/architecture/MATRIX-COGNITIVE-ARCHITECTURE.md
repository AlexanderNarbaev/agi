# MATRIX — The cognitive architecture

**Status:** W44 implementation · 2026-11-02
**Companion documents:** `MATRIX-COGNITIVE-KERNEL.md` · `MATRIX-NEUROMODULATION.md` ·
`MATRIX-ADAPTIVE-COMPUTATION.md` · `docs-v2/engineering/W44-CAPABILITY-MAP.md`

This is the top-level view. It says what the parts are, how they cooperate, and — as important
— which parts of the long-term architecture are NOT implemented. The three companion documents
carry the detail of the three subsystems that W44 actually built.

---

## 1. The three questions the architecture answers

The W44 directive insists on separating three questions that are usually conflated:

| Question | Subsystem | W44's state |
|---|---|---|
| **What does the system know about the world?** | the world model: claims, evidence, cases, hypotheses | **implemented** — the W43 epistemic core plus the schema-16 `Case` |
| **How does it obtain and validate knowledge?** | the cognitive process: perception, prediction, experiment, comparison, revision | **implemented** — the kernel and the micro-world |
| **How should it spend its resources?** | the metacognitive layer: selection by preconditions and measured cost, regulation | **implemented** — the portfolio, the selector and the drives |

---

## 2. The architecture as it is

```
                    ┌──────────────────────────────────────────────┐
   user request ──▶ │  SERVING SURFACE (the same Quarkus process)  │
                    │  /v1/workbench/**   /v1/cognitive/**          │
                    └───────────┬──────────────────────┬───────────┘
                                │                      │
                    ┌───────────▼──────────┐  ┌────────▼────────────────┐
                    │  EPISSTEMIC CORE     │  │      COGNITIVE KERNEL    │
                    │  KnowledgeWorkbench  │  │  perceive → predict →    │
                    │  Source Claim Evidence│  │  select → act → compare  │
                    │  Derivation Revision  │  │  → revise → regulate     │
                    │  CASE (schema 16)     │  └────────┬────────────────┘
                    └───────────┬──────────┘           │
                                │                      │
                    ┌───────────▼──────────────────────▼───────────┐
                    │              THE STORES (one SQLite file)     │
                    │  epistemic tables │ cognitive_* tables      │
                    └──────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
 ┌─────────────┐      ┌──────────────────┐      ┌──────────────────┐
 │ ALGORITHM   │      │  REGULATION      │      │  MICRO-WORLD     │
 │ PORTFOLIO   │      │  8 typed drives  │      │  hidden rule     │
 │ + SELECTOR  │      │  bounded bias    │      │  competing       │
 │ precond./cost│     │  safety invariant│      │  hypotheses      │
 └─────────────┘      └──────────────────┘      └──────────────────┘
```

### 2.1 The parts

| Part | Responsibility | Owns | Talks to |
|---|---|---|---|
| **Perception** (`ChannelClassifier`, `Percept`, `CognitiveSignal`) | what KIND of thing arrived | nothing | the kernel |
| **World model** (the W43 epistemic core + `Case`) | what is believed, on what evidence | the epistemic tables | the kernel, the workbench |
| **Cognitive kernel** (`CognitiveKernel`) | one cycle: perceive, restore, predict, select, act, observe, learn, regulate | nothing of its own | everything below |
| **Algorithm portfolio** (`AlgorithmRegistry`, `AlgorithmSelector`, the methods) | which computation to run | the registry, the cost ledger | the kernel, the solver |
| **Regulation** (`DriveSignal`, `DriveDynamics`, `RegulationController`) | how much to weigh cost, when to ask for evidence, whether to admit a task | the regulatory state | the kernel |
| **Micro-world** (`MicroWorldSession`, `HypothesisSpace`, `LampEnvironment`) | a sandbox with a hidden rule | nothing | the kernel |
| **Memory** | the cases ARE the episodic memory; the promoted procedures ARE the procedural memory | the stores | the kernel |
| **Goal** (`CognitiveTask`) | a bounded unit of internal work with a budget and a stop condition | the cognitive store | the kernel |

### 2.2 The rules that hold the architecture together

1. **One epistemic model.** Claims, evidence, sources, derivations, revisions and cases live in
   the W43 store. The kernel has no private truth.
2. **One task vocabulary that is not a hierarchy.** `CognitiveTask` is a bounded unit of
   internal work. It does not compete with `GoalManager`, and it never masquerades as a
   `LearningGoal`.
3. **One attention decision.** The kernel's SELECT phase decides which computation runs. There
   is no second priority queue and no impulse list that is always empty.
4. **The regulation is bounded, and the boundary is structural.** Four fields, no effect for
   confidence, truth, permission or safety, and a reflection test that fails if a fifth field
   appears.
5. **Every decision is a record.** `AlgorithmDecision` for the choice, `LearningTrace` for the
   cycle, `Prediction` and `ErrorSignal` for the cognition, `Case` and `Evidence` for the
   world.
6. **Nothing runs outside the sandbox.** The only action type in W44 is a probe inside the
   micro-world.

---

## 3. The long-term architecture, and what is missing from it

The directive sketches thirteen functional regions. This is where each one actually stands.

| Region | W44 state | What is missing |
|---|---|---|
| sensory and perception | **partial** — text, structured, binary, tabular and time-series channels are classified by FORM; the interpretation of an image or a sound is not implemented | modality-specific interpretation; cross-modal grounding |
| thalamic-style routing | **not implemented** | the kernel dispatches on the problem family, which is routing at the level of one request; there is no priority-queue router |
| hippocampal episodic memory | **implemented, minimally** — the cases are the episodic memory, and they are the model's own observations | episodic recall over a general episode stream; the distinction between "what happened" and "what it means" |
| semantic and associative cortex | **implemented by W43** | the epistemic core is a strong semantic store; the associative part is the HDC layer, which lives in the other serving chain |
| cerebellar-style prediction and correction | **implemented, minimally** — a prediction, a comparison, an error, a revision | the "learn a reusable procedure from an expensive reasoning" loop exists (promotion), but the cerebellar analogue of "predict the next state" does not |
| prefrontal-style executive | **partial** — the kernel coordinates and allocates a budget | decomposition into a goal/subtask hierarchy; the deliberate comparison of candidate plans |
| basal-ganglia-style action selection | **partial** — the selector chooses between candidate procedures by evidence and cost | a learned policy over the selector; habit formation |
| amygdala-style salience and risk | **not implemented** — the directive forbids reducing it to a keyword detector, and nothing was built | a real salience and risk model |
| anterior-cingulate-style conflict monitoring | **partial** — the kernel detects prediction errors and model disagreement | conflict detection between competing SUBSYSTEMS |
| hypothalamic-style homeostasis | **partial** — `SUSTAINED_LOAD` and `CONSOLIDATION_DEMAND` exist | resource-pressure measurement from the real system, not from events |
| insula-style internal state | **not implemented** | a unified model of memory pressure, CPU saturation, queue length |
| global integration and workspace | **not implemented** | the kernel's trace is a per-episode record, not a broadcast workspace |

**Two of these are not small.** The salience/risk region and the global workspace are the two
places where the long-term architecture needs a mechanism rather than a name, and W44
deliberately did not fake either.

---

## 4. What the architecture refuses to do

| Refusal | Why |
|---|---|
| revive `BiochemicalNetwork`, `CognitiveModulationBridge`, `AttentionRouter` | unreachable islands with hand-picked coefficients and no cited source |
| wire the `graph` package | 159 tests, zero production callers, and a documented defect in the anneal guard |
| claim a GPU result | the provider set is hard-coded and unobservable |
| publish an aggregate cognitive score | the directive forbids inventing one global intelligence score; each capability is measured separately |
| let a regulator change a fact, a confidence or a permission | the safety boundary is not learnable and is not adjustable |
| let a learned procedure bypass its preconditions | the fast path always names the reference it falls back to |
| erase a claim's provenance during consolidation | the consolidator MARKS; the one path that erases is recorded as an open risk in the backlog |

---

## 5. The order of the next waves

1. **Instrument, then decide.** The research agenda's Q1 (the oracle gap) has to be measured
   before a learned policy can be justified, and Q5 (the price of the guards) has to be
   measured before a guard can be defended.
2. **Second domain.** Everything above is measured on one micro-world. A second domain is what
   turns "the kernel generalises" from an empty phrase into a measurement.
3. **Salience and risk, properly.** Not a keyword detector: a model of significance and of
   potential hazard, with the two kept apart.
4. **The workspace.** The trace is per-episode; the long-term architecture needs a mechanism
   through which important outputs compete for attention and become globally available.
5. **The chains.** Merging the Quarkus epistemic core with the brain runtime is a separate
   decision with its own ADR, and it needs the security boundary to be settled first.
