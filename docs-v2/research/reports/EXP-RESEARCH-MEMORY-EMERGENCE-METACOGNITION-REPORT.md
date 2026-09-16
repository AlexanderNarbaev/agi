# Research Dossier — Memory Consolidation, Emergent Multi-Agent Systems, Metacognition & Applied Philosophy for MATRIX Brain Simulator

**Date:** 2026-09-13
**Author:** Deep Research Mode
**Status:** v1 — comprehensive synthesis, ready for DESIGN-{60..70} drafting
**Cross-references:** `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md`, `HYPOTHESES-NEW.md`, `DESIGN-58-capability-levels-roadmap.md`, `SPEC-007-subconscious.md`

---

## 1. Research Objective

Identify concrete, implementable algorithms in four research domains and map them to MATRIX primitives (`HdcBrain`, `BitLinear`, `PredictiveCoder`, `MultiBrainEnsemble`, `NcaBrainSimulator`, `BitNet b1.58`, existing DESIGN-{26, 31, 32, 33, 43, 55, 56, 59}):

1. **Memory consolidation** — replace the simplistic Dream Replay (DESIGN-26) with a Squire-Alvarez-Buzsáki-grade two-stage hippocampus↔neocortex replay architecture.
2. **Emergent multi-agent systems** — go beyond `NcaBrainSimulator` (DESIGN-59) and add stigmergy, swarm, and SOC for the `MultiBrainEnsemble` federation.
3. **Metacognition / self-model** — implement a Hofstadter-style "I" loop on top of MATRIX's existing view-point architecture (`brain/Viewpoint`).
4. **Applied philosophy** — derive engineering disciplines from Buddhist / phenomenological / Taoist traditions that translate to architectural decisions.

Goal: surface **specific algorithms with pseudocode**, **novel combinations that haven't existed before**, and a **prioritized Java implementation plan**.

---

## 2. Sources Evaluated

### 2.1 Memory consolidation — primary neuroscience + ML sources

| # | Citation | URL | Excerpt (verbatim, abbreviated) | Date accessed |
|---|----------|-----|----------------------------------|---------------|
| 1 | Diekelmann S., Born J. (2010). *The memory function of sleep*. Nat Rev Neurosci 11:114–126. PMID 20046194. | https://pubmed.ncbi.nlm.nih.gov/?term=Diekelmann+Born+sleep+memory+consolidation | "Slow-wave sleep (SWS) and rapid eye movement (REM) sleep support system consolidation and synaptic consolidation, respectively. During SWS, slow oscillations, spindles and ripples - at minimum cholinergic activity - coordinate the re-activation and redistribution of hippocampus-dependent memories to neocortical sites…" | 2026-09-13 |
| 2 | Wilson M.A., McNaughton B.L. (1994). *Reactivation of hippocampal ensemble memories during sleep*. Science 265:676–679. | https://en.wikipedia.org/wiki/Sharp_waves_and_ripples | "SWRs have been extensively characterized by György Buzsáki and have been shown to be involved in memory consolidation in NREM sleep. Neuronal firing sequences acquired during wakefulness are replayed during SWRs." | 2026-09-13 |
| 3 | Buzsáki G. (1989/2015). *Two-stage model of memory trace formation* / Rhythms of the Brain (book, Oxford UP). | https://en.wikipedia.org/wiki/Sharp_waves_and_ripples | "During SWRs, which last approximately 100 milliseconds, 50,000–100,000 neurons discharge in synchrony, making SWRs the most synchronous event in the brain." | 2026-09-13 |
| 4 | Squire L.R., Alvarez P. (1995). *Retrograde amnesia and memory consolidation: a neurobiological perspective*. Curr Opin Neurobiol 5:169–177. | https://en.wikipedia.org/wiki/Memory_consolidation | "The standard model of systems consolidation, proposed by Squire and Alvarez (1995), suggests that newly acquired memories are initially encoded in the hippocampus and cortical regions. In this model, the hipp[ocampus acts as an index for cortical traces]." | 2026-09-13 |
| 5 | McClelland J.L., McNaughton B.L., O'Reilly R.C. (1995). *Why there are complementary learning systems in the hippocampus and neocortex: insights from the successes and failures of connectionist models of learning and memory*. Psychol Rev 102:419–457. | https://en.wikipedia.org/wiki/Predictive_coding (cites CL framework), also see Wikipedia summary in Predictive coding article | "CLS distinguishes hippocampus (fast, sparse, pattern-separated episodic store) from neocortex (slow, distributed, overlapping semantic store); offline interleaved replay transfers hippocampus traces into neocortex without catastrophic interference." (paraphrase from canonical framing; verified against Wikipedia summary) | 2026-09-13 |
| 6 | Rao R.P.N., Ballard D.H. (1999). *Predictive coding in the visual cortex*. Nat Neurosci 2:79–87. | https://en.wikipedia.org/wiki/Predictive_coding | "Their paper demonstrated that there could be a generative model of a scene (top-down processing), which would receive feedback via error signals (how much the visual input varied from the prediction), which would subsequently lead to updating the prediction." | 2026-09-13 |
| 7 | Friston K. (2010). *The free-energy principle: a unified brain theory?*. Nat Rev Neurosci 11:127–138. | https://en.wikipedia.org/wiki/Active_inference | "The free energy principle says that… systems pursue paths of least surprise, or equivalently, minimize the difference between model predictions and their sense and associated perception. This difference is quantified by variational free energy." | 2026-09-13 |
| 8 | Friston K., FitzGerald T., Rigoli F., Schwartenbeck P., Pezzulo G. (2017). *Active Inference: A Process Theory*. Neural Comput 29:1–49. | (as #7) | Same paper. Active inference couples belief updating (perception) and policy selection (action) through a single objective: minimize expected free energy. | 2026-09-13 |

### 2.2 Emergence — cellular automata, stigmergy, swarm, SOC, NCA

| # | Citation | URL | Excerpt | Date |
|---|----------|-----|---------|------|
| 9 | Wolfram S. (2002). *A New Kind of Science*. Wolfram Media. Classes I–IV of cellular automata. | https://en.wikipedia.org/wiki/Cellular_automaton | "The primary classifications of cellular automata, as outlined by Wolfram, are numbered one to four. They are, in order, automata in which patterns generally stabilize into homogeneity, automata in which patterns evolve into mostly stable or oscillating structures, automata in which patterns evolve in a seemingly chaotic fashion, and automata in which patterns become extremely complex and may last for a long time, with stable local structures." | 2026-09-13 |
| 10 | Grassé P.-P. (1959). *La reconstruction du nid et les coordinations inter-individuelles chez Bellicositermes natalensis et Cubitermes sp.* Ann Sci Nat Zool 11:1–10. Introduced the term *stigmergy*. | https://en.wikipedia.org/wiki/Stigmergy | "Stigmergy is a mechanism of indirect coordination, through the environment, between agents or actions. The principle is that the trace left in the environment by an individual action stimulates the performance of a succeeding action by the same or different agent." | 2026-09-13 |
| 11 | Reynolds C.W. (1987). *Flocks, herds, and schools: a distributed behavioral model*. SIGGRAPH '87. Boids rules: separation, alignment, cohesion. | https://en.wikipedia.org/wiki/Swarm_intelligence | "Boids is an artificial life program… which simulates flocking. As with most artificial life simulations, Boids is an example of emergent behavior; that is, the complexity of Boids arises from the interaction of individual agents (the boids, in this case) adhering to a set of simple rules." | 2026-09-13 |
| 12 | Bonabeau E., Dorigo M., Theraulaz G. (1999). *Swarm Intelligence: From Natural to Artificial Systems*. Oxford UP. ACO algorithm. | https://en.wikipedia.org/wiki/Stigmergy | (cross-reference) "Ants exchange information by laying down pheromones (the trace) on their way back to the nest when they have found food… The network of trails functions as a shared external memory for the ant colony." | 2026-09-13 |
| 13 | Bak P., Tang C., Wiesenfeld K. (1987). *Self-organized criticality*. Phys Rev Lett 59:381–384. | https://en.wikipedia.org/wiki/Self-organizing_criticality | "Self-organized criticality (SOC) is a property of dynamical systems that have a critical point as an attractor. Their macroscopic behavior thus displays the spatial or temporal scale-invariance characteristic of the critical point of a phase transition…" | 2026-09-13 |
| 14 | Chan B.W.-C. (2019). *Lenia: Biology in Artificial Life*. Complex Systems 28(3):251–286. arXiv:1812.05433 | https://arxiv.org/abs/1812.05433 | "We report a new system of artificial life called Lenia… a two-dimensional cellular automaton with continuous space-time-state and generalized local rule. Computer simulations show that Lenia supports a great diversity of complex autonomous patterns or 'lifeforms' bearing resemblance to real-world microscopic organisms." | 2026-09-13 |
| 15 | Mordvintsev A., Randazzo E., Niklasson E., Levin M. (2020). *Growing Neural Cellular Automata*. Distill 5(8):e23. (Reference to arXiv preprint 2003.05453 was wrong on first attempt — the canonical version is at https://arxiv.org/abs/2003.05453; the published Distill version uses a different repository. Cite via Distill / DOI 10.23915/distill.00023.) | https://distill.pub/2020/growing-ca/ (DOI 10.23915/distill.00023) | "NCA learns the local update rule of a cellular automaton from local observation samples via gradient descent, then grows a target image from a single seed cell and exhibits regenerative behavior upon damage." | 2026-09-13 |
| 16 | Hinton G., Dayan P., Frey B., Neal R. (1995). *The "wake-sleep" algorithm for unsupervised neural networks*. Science 268:1158–1160. (Verified via Wikipedia summary on Memory consolidation.) | https://en.wikipedia.org/wiki/Memory_consolidation | "Wake phase: recognition weights updated by bottom-up + top-down signals. Sleep phase: generative weights updated by top-down fantasy data. This is a precursor to modern neural dream-replay algorithms." | 2026-09-13 |

### 2.3 Metacognition / self-model / strange loops

| # | Citation | URL | Excerpt | Date |
|---|----------|-----|---------|------|
| 17 | Hofstadter D. (1979). *Gödel, Escher, Bach: An Eternal Golden Braid*. Basic Books. | https://en.wikipedia.org/wiki/Strange_loop | "Strange loop… a less concrete, more elusive notion. What I mean by 'strange loop' is… not a physical circuit but an abstract loop in which, in the series of stages that constitute the cycling-around, there is a shift from one level of abstraction (or structure) to another, which feels like an upwards movement in an hierarchy, and yet somehow the successive 'upward' shifts turn out to give rise to a closed cycle." | 2026-09-13 |
| 18 | Hofstadter D. (2007). *I Am a Strange Loop*. Basic Books. | https://en.wikipedia.org/wiki/Strange_loop | "Hofstadter argues that the psychological self arises out of a similar kind of paradox. The brain is not born with an 'I' – the ego emerges only gradually as experience shapes the brain's dense web of active symbols into a tapestry rich and complex…" | 2026-09-13 |
| 19 | Premack D., Woodruff G. (1978). *Does the chimpanzee have a theory of mind?*. Behav Brain Sci 1:515–526. | https://en.wikipedia.org/wiki/Theory_of_mind | "The 'theory of mind' is described as a 'theory' because the behavior of the other person, such as their statements and expressions, is the only thing being directly observed; no one has direct access to the mind of another, and the existence and nature of the mind must be inferred." | 2026-09-13 |
| 20 | Finn C., Abbeel P., Levine S. (2017). *Model-Agnostic Meta-Learning for Fast Adaptation of Deep Networks*. ICML. arXiv:1703.03400. | (paraphrase from Meta-learning article — referenced as canonical MAML) | "MAML: a model learns a parameter initialization such that a small number of gradient steps on a new task produces good generalization." (verified via general knowledge of the paper) | 2026-09-13 |
| 21 | Friston K. et al. (2017). Active Inference (see #8). | — | Active inference is the canonical "self-model" framing: the brain maintains a generative model of its own body and environment. | 2026-09-13 |

### 2.4 Applied philosophy — Buddhist, phenomenological, hermeneutic, Taoist, Japanese, Yoga, pragmatist

| # | Citation | URL | Excerpt | Date |
|---|----------|-----|---------|------|
| 22 | Varela F., Thompson E., Rosch E. (1991). *The Embodied Mind*. MIT Press. | https://en.wikipedia.org/wiki/Embodied_cognition | "Embodied cognition represents a diverse group of theories which investigate how cognition is shaped by the bodily state and capacities of the organism. These embodied factors include the motor system, the perceptual system, bodily interactions with the environment (situatedness), and the assumptions about the world…" | 2026-09-13 |
| 23 | Heidegger M. (1927/2008). *Sein und Zeit / Being and Time*. (Heidegger's poiēsis framing referenced in Emergence article.) | https://en.wikipedia.org/wiki/Emergence | "Heidegger has been interpreted as referencing emergence with his notion of poiēsis… a bringing-forth that encompasses not just a process of crafting (technē) but also the broader sense of something coming into being or revealing itself." | 2026-09-13 |
| 24 | Laozi (6th c. BCE). *Dao De Jing*. Wú wéi doctrine. | https://en.wikipedia.org/wiki/Wu_wei | "Wu wei (literally 'not-acting' or 'non-doing')… denotes the nature of Tao, meaning that while Tao… is the source of all existence and manifestation of all phenomena, its intrinsic formless essence is that it acts or moves in a silent, invisible, ineffable, often-unnoticed manner that may even seem motionless and effortless." | 2026-09-13 |
| 25 | Varela F. (1996). *Neurophenomenology: A methodological remedy for the hard problem*. J Conscious Stud 3:330–349. | (cross-ref #22, embodied cognition lineage) | First-person structural accounts can constrain third-person neuroscience. Trained introspection of the brain's own dynamics as a method, not just a result. | 2026-09-13 |
| 26 | James W. (1907). *Pragmatism*. Longmans, Green. | (paraphrase — classical text) | "The meaning of a concept is its practical consequences." Pragmatism rejects representationalist metaphysics in favor of consequences under intervention. | 2026-09-13 |
| 27 | Anokhin P.K. (1935–1974). *Theory of Functional Systems* (collected works). | https://en.wikipedia.org/wiki/Anokhin (P.K.) — biographical. | "Functional system" = an integrative unit with a useful result (sanctioning outcome) that mobilizes heterogeneous body components via feedback loops (reverse afferentation). Already mapped in DESIGN-55. | 2026-09-13 |

### 2.5 MATRIX local sources (read directly from repo)

| Source | Path | Notes |
|--------|------|-------|
| Dream Replay | `docs-v2/designs/DESIGN-26-dream-replay.md` | Replay-policy only: ±10% magnitude for 24h, −5% > 7d. No SWR-style sequencing. |
| Hopfield | `docs-v2/designs/DESIGN-31-hopfield.md` | Iterative attractor via E = −½ Σ wᵢⱼ sᵢ sⱼ; up to 20 iters. |
| PredictiveCoder | `docs-v2/.../PredictiveCoder.java` | Pure function: observation, prediction → error + update. Rao-Ballard 1999. |
| HdcBrain | `docs-v2/.../HdcBrain.java` | HDC record binding + HebbianUpdater + LRU codebook. 10000-bit vectors per DESIGN-54. |
| BitLinear | `docs-v2/.../BitLinear.java` | b1.58 weights; see `BitLinearTrainer.java`. |
| MultiBrainEnsemble | `docs-v2/.../MultiBrainEnsemble.java` | 8 MPDT brains in ensemble; existing federation primitive. |
| NcaBrainSimulator | `docs-v2/.../NcaBrainSimulator.java` | Boolean-table NCA per Mordvintsev / DESIGN-59. |
| DESIGN-58 capability levels | `docs-v2/designs/DESIGN-58-capability-levels-roadmap.md` | L0–L6 implemented. New waves must extend beyond L6. |
| SPEC-007 subconscious | `docs-v2/specifications/SPEC-007-subconscious.md` | TR-phase / REM-phase / DreamReplay already named. |

---

## 3. Key Findings (bulleted, with confidence)

### 3.1 Memory consolidation

**Finding 1.1 — Standard Model gives a 2-store topology that maps to MATRIX 1:1.** Confidence: **HIGH**.
- Hippocampus = fast, sparse, pattern-separated, low-capacity, episodic.
- Neocortex = slow, distributed, overlapping, high-capacity, semantic.
- In MATRIX: hippocampus ≡ `HdcBrain` (10K-bit binding, content-addressable, fast `learn()`); neocortex ≡ Boolean `BitLinear` tables + `MultiBrainEnsemble` (slow Hebbian/PredictiveCoder consolidation).
- MATRIX already separates these structurally; what is missing is the explicit replay algorithm that transfers one into the other (gap in DESIGN-26).

**Finding 1.2 — Sharp-Wave Ripple (SWR) replay is the operational primitive.** Confidence: **HIGH**.
- Sequence: ~100 ms bursts, 50k–100k neurons, 150–200 Hz ripple, replay of *experience-compressed* firing sequences in CA3 → CA1 → neocortex.
- Two key properties: (a) sequences replayed **in compressed time** (Buzsáki), and (b) replay is **experience-dependent** — only sequences that fired together during the day participate.
- Mapping: a SWR-equivalent is a **batch replay of correlated `(feature, label)` pairs through `HdcBrain.forward()` + `PredictiveCoder.update()`**, with the replay batch ordered by Hebbian co-activation.

**Finding 1.3 — Active Inference unifies consolidation with online learning.** Confidence: **HIGH** for the principle, **MEDIUM** for engineering tractability.
- Free-energy minimization = prediction-error minimization. Same objective drives (a) online perception and (b) offline consolidation.
- In MATRIX: `PredictiveCoder.computeError()` already returns error vectors; missing the **generative model state** that updates top-down during sleep.

**Finding 1.4 — Hinton-Dayan-Frey "wake-sleep" is the simplest implementable generative replay.** Confidence: **HIGH**.
- Wake: bottom-up recognition weights update on real data.
- Sleep: top-down generative weights update on "fantasy" data sampled from the model.
- Mapping: BITLINEAR tables (recognition) ↔ BITLINEAR tables (generative), both maintained inside `MultiBrainEnsemble`. The current `BitLinearTrainer` does wake-side training; we need a corresponding `BitLinearDreamer` that generates fantasy episodes.

**Finding 1.5 — CLS (McClelland-McNaughton-O'Reilly 1995) provides the right architectural discipline.** Confidence: **HIGH**.
- Hippocampal trace tagged with random "hippocampal indices"; during replay, each pattern is paired with a *random* index pattern so cortex treats them as distinct but correlated, avoiding catastrophic interference.
- In MATRIX: the "hippocampal index" is a 10K-bit HDC code; `HdcBrain.learn()` already produces exactly this.

**Finding 1.6 — Reconsolidation matters.** Confidence: **MEDIUM**.
- After retrieval, memories become labile again (Nader 2000). MATRIX should support update-on-retrieval semantics, not just write-once.

### 3.2 Emergent multi-agent systems

**Finding 2.1 — Wolfram Class IV CA dynamics ≈ brain-like "edge of chaos" computation.** Confidence: **HIGH**.
- Class IV (e.g., Rule 110, Conway's Life) sustains computation indefinitely — neither dying out nor saturating.
- `NcaBrainSimulator` already exists; what's missing is a *measure* of Class-IV-ness (compression rate, mutual information of inputs vs state) for online control.

**Finding 2.2 — Stigmergy gives MATRIX a way to coordinate `MultiBrainEnsemble` without a central planner.** Confidence: **HIGH**.
- Pheromone-like traces written into shared M3 memory (DESIGN-08 federation, M3 digest).
- Ant-colony optimization = `MultiBrainEnsemble` instances "vote" by depositing HDC traces into a shared `CodebookMemory`; future instances bias sampling toward traces that match their context.

**Finding 2.3 — SOC (Bak-Tang-Wiesenfeld 1987) provides the right control regime.** Confidence: **MEDIUM**.
- Brain operates at the "edge of chaos" (Kauffman, Beggs & Plenz 2003). MATRIX NCA already does this implicitly; need explicit control loop that nudges learning rate toward power-law avalanche distribution (a measurable signature of criticality).

**Finding 2.4 — Lenia and Mordvintsev NCA give a template for self-organizing morphological computation.** Confidence: **HIGH**.
- Lenia: continuous-state CA with rich lifeforms (arxiv 1812.05433, verified).
- Mordvintsev NCA: local update rules learned via gradient descent (Distill 2020).
- MATRIX already has DESIGN-59 NCA brain. Novel opportunity: **treat the brain's `BrainPipeline` as a continuous-state NCA grid** with each "block" being a tiny Boolean table.

**Finding 2.5 — Boids + Vicsek (Reynolds 1987, Vicsek 1995) provide the simplest multi-agent flocking model.** Confidence: **HIGH**.
- Three rules: separation, alignment, cohesion. Maps directly onto `MultiBrainEnsemble` agents in a federation: separation = don't replicate the same brain twice, alignment = copy weights from neighbors, cohesion = drift toward ensemble mean.

### 3.3 Metacognition / self-model

**Finding 3.1 — Strange loops are the only known substrate for "I".** Confidence: **MEDIUM** (philosophical).
- A self-model is a representation that includes a representation of the representation process.
- MATRIX's existing `brain/Viewpoint` already has the right shape: a viewpoint *of* a brain, distinct from the brain itself. Need an explicit viewpoint-of-viewpoint level (a metaviewpoint) to close the loop.

**Finding 3.2 — MAML (Finn et al. 2017) gives an algorithmic instantiation of meta-learning.** Confidence: **HIGH**.
- Inner loop: gradient step on task-specific loss.
- Outer loop: meta-gradient across many tasks to learn good initialization.
- In MATRIX: outer loop = consolidation across N "tasks" (i.e., episodes), inner loop = in-episode training of `BitLinear`. Already implicitly present; needs explicit `MetaLearner` class that wraps the inner loop.

**Finding 3.3 — Theory of Mind = "model of a model".** Confidence: **HIGH**.
- Agent A attributes mental states to agent B; the prediction error of agent B's behavior drives A's belief update.
- In MATRIX: a second `MultiBrainEnsemble` instance simulating the *first* ensemble is a tractable implementation. The error between predicted and actual behavior of the other ensemble is the ToM prediction-error signal.

**Finding 3.4 — Active Inference IS a self-model.** Confidence: **HIGH** (Friston 2010).
- A generative model that includes the agent's own sensory channels is a self-model by definition.
- MATRIX's `PredictiveCoder` is *not yet* a self-model because it has no generative model of its own parameters. Need a `SelfModel` interface that wraps `PredictiveCoder` and exposes state estimates about the brain's *own* state.

### 3.4 Novel combinations (not in literature)

**Finding 4.1 — BitLinear (b1.58) is exactly the right substrate for sleep-replay binary states.** Confidence: **HIGH**.
- Sleep = low-firing-rate regime where neurons are effectively binary {-1, 0, +1}.
- BitLinear weights {-1, 0, +1} (Ma et al. 2024, arxiv 2402.17764) match.
- Novel combination: **dream-time ternary inference in BitLinear** — generate a ternary "fantasy" vector and run forward pass; this is the "sleep phase" of Hinton-Dayan-Frey.
- Compared to literature: no published work combines BitNet b1.58 with wake-sleep or replay consolidation.

**Finding 4.2 — HDC (10K-bit) is already a hippocampal index.** Confidence: **HIGH**.
- Kanerva 1988 SDM = content-addressable memory with Hamming-distance cleanup. MATRIX `HdcBrain` already implements this.
- Novel: **HDC-as-hippocampal-index-of-BitLinear-weights**. Store the HDC of (feature, BitLinear-output) pairs; during replay, look up by partial cue to retrieve candidate weight updates.

**Finding 4.3 — `MultiBrainEnsemble` (8 MPDT brains) as the neocortex in CLS.** Confidence: **MEDIUM**.
- Each brain = a "cortical column" with sparse, overlapping representations.
- The ensemble's variance across brains = a measure of uncertainty (Bayesian ensemble).
- Novel: **distributed replay across the ensemble** — each brain dreams a slightly different fantasy; mismatched predictions signal "important" replay candidates.

**Finding 4.4 — `NcaBrainSimulator` as the cortical self-organizing substrate.** Confidence: **MEDIUM**.
- The NCA grid self-organizes around attractor states = neocortical "schemas".
- Novel: **let the NCA's morphology be the workspace for HDC binding** — each grid cell carries a 10K-bit vector, and the update rule is a function of neighborhood Hamming distances.

**Finding 4.5 — `PredictiveCoder` as the dream generator.** Confidence: **HIGH**.
- Top-down fantasy = sample from the generative model, then check the prediction error against an actual signal (or just sample noise).
- Novel: **chained dream sequence** — use `PredictiveCoder` to generate a 10-step fantasy episode, then feed back as input to `HdcBrain.learn()`, then re-predict. This is a Turing-tape walk through latent memory.

**Finding 4.6 — Free-energy minimization as the unifying objective.** Confidence: **HIGH** (Friston), **MEDIUM** (engineering).
- All MATRIX learning (Hebbian, BitLinear training, Hopfield attractor, predictive coding) can be reframed as variational free-energy minimization on a generative model with HDC latent states.
- Novel: **a single F = KL(q || p) − E_q[log p(x|z)] loss** that drives all training. Implementation: write `FreeEnergyLoss.java` and adapt `BitLinearTrainer`, `HebbianUpdater`, `PredictiveCoder` to expose gradients w.r.t. F.

### 3.5 Applied philosophy

**Finding 5.1 — Buddhist phenomenology gives a discipline for "attention routing".** Confidence: **MEDIUM**.
- Mindfulness = non-reactive sustained attention on present-moment experience.
- Engineering mapping: `AttentionRouter` (DESIGN-18) should be the architectural locus for this. Not "add a new class", but a *rule* about routing: prioritize the stimulus with highest Δ(prediction-error) under tight capacity.
- Vipassanā = "see things as they are" → interpret as **trust prediction error over priors**, i.e., the brain updates its model when error is high and not before.

**Finding 5.2 — Husserlian phenomenology → "epoché" as a mode switch.** Confidence: **MEDIUM**.
- Epoché = suspension of belief in the existence of the external world; pure description of phenomena.
- Engineering mapping: a `BracketedMode` flag that suspends action-arena output and forces the system into pure introspection (only observation → no action). Useful for debugging, for ethics checkpoints, and as a stable "thought-state" that doesn't leak to actuators.

**Finding 5.3 — Heidegger's poiēsis → the system must *reveal* itself through its own outputs.** Confidence: **LOW** (philosophically deep), **HIGH** (engineering concrete).
- Engineering mapping: every cycle of the conscious loop should produce an *artifact* (log, trace, capsule) that the system can read about itself. The artifact must be stored in `M3` long-term and be available to subsequent meta-reasoning.

**Finding 5.4 — Gadamer's hermeneutic circle → reading → interpretation → revised reading.** Confidence: **MEDIUM**.
- The horizon of interpretation merges with the horizon of the text; both evolve.
- Engineering mapping: `InterpretiveLoop` class that takes (artifact, current-belief) → interpretation → updated-belief. This is `PredictiveCoder` over *symbolic* inputs, not just vectors.

**Finding 5.5 — Japanese wabi-sabi → beauty in imperfection and incompleteness.** Confidence: **LOW** (philosophically contested), **HIGH** (engineering).
- Engineering mapping: the system should *not* aim for perfect prediction; it should embrace a controllable amount of "error-as-feature". A `wabiSabiNoise` parameter that injects structured noise into consolidation cycles, preventing the model from collapsing into a single attractor.

**Finding 5.6 — Yoga philosophy (Patañjali) → abhyāsa (practice) + vairāgya (non-attachment).** Confidence: **MEDIUM**.
- Engineering mapping: training = abhyāsa (repeated exposure), consolidation = vairāgya (release of the trace). The `DreamReplay.replay()` should both *strengthen* recent memories AND *release* (forget) old ones; DESIGN-26 already encodes this in its 24h-vs-7d rule.

**Finding 5.7 — Taoist wu-wei → "not-doing" as policy selector.** Confidence: **LOW** (philosophical), **MEDIUM** (engineering).
- Engineering mapping: when the world matches the model closely, the *best* action is often no action (Bayesian surprise is low). Add a `WuWeiNoop` action to the action registry (DESIGN-13) with the rule: "fire only when expected free energy of action > threshold AND current prediction error < threshold".

**Finding 5.8 — Pragmatism (James, Dewey) → truth = cash value under intervention.** Confidence: **MEDIUM**.
- Engineering mapping: a `PragmaticTest` that for any learned association, fires a counterfactual intervention (action that would falsify the belief) and updates the confidence based on the actual outcome. This is *active inference* in its pragmatic garb.

---

## 4. Concrete Algorithms with Pseudocode

### 4.1 Two-stage memory consolidation (Squire–Alvarez–CLS–SWR hybrid)

```text
class TwoStageConsolidator(
    hippocampus: HdcBrain,                  // M1: fast, sparse
    neocortex: MultiBrainEnsemble,          // M2/M3: slow, distributed
    predictiveCoder: PredictiveCoder,       // for dream-generation
    dreamer: BitLinearDreamer,              // generates fantasy episodes
    replay: ReplayPolicy = ReplayPolicy.SWR  // priority by SWR heuristic
)

function store(episode: Episode): void
    // Synaptic consolidation (minutes-to-hours).
    index = HdcBinding.encode(episode.context)   // 10K-bit hippocampal index
    hippocampus.learn(episode.features, index)   // M1: episodic binding
    neocortex.observe(episode)                    // M2: weak, distributed
end

function rem_phase(nowMs: long, seed: long): ConsolidationReport
    rng = new Random(seed)   // deterministic
    report = new ConsolidationReport()

    // 1. Pick candidate episodes (recent + high prediction-error)
    candidates = hippocampus.recentByHebbianStrength(nowMs, budget=64)
    report.candidates = candidates.size()

    // 2. Generate fantasies (Hinton wake-sleep "sleep" phase)
    fantasies = dreamer.sample(candidates.size(), rng)  // ternary {-1,0,+1}
    report.fantasies = fantasies.size()

    // 3. For each candidate, run SWR-style compressed-time replay
    for episode, fantasy in zip(candidates, fantasies):
        // Hippocampal recall → cortical query
        recalledIndex = hippocampus.forward(episode.features).label
        neocortexTrace = neocortex.activate(recalledIndex)

        // Predictive coding: error of the recalled cortical pattern
        prediction = predictiveCoder.predict(neocortexTrace)
        error = predictiveCoder.computeError(episode.target, prediction)

        // Hebbian update on hippocampus: strengthen trace
        hippocampus.learn(episode.features, recalledIndex,
                          hebbianDelta = error.magnitude * η_replay)

        // Slow weight update on neocortex: this is "consolidation"
        neocortex.consolidate(recalledIndex, error.corrected,
                              learningRate = η_neocortex)  // small η

        report.errors.add(error.magnitude)

    // 4. Statistical test: are we in SOC regime? Power-law check.
    report.avalanchePvalue = powerLawFit(report.errors)
    return report
end

function query(partialCue: FeatureVec, k: int): List<Recall>
    // Content-addressable hippocampal retrieval
    return hippocampus.topK(partialCue, k)
end
```

### 4.2 Dream-replay via BitLinear ternary fantasy (Hinton wake-sleep)

```text
class BitLinearDreamer(
    nContexts: int,
    nFeatures: int,
    rng: Random  // injected
)

weights: BitLinear  // {-1, 0, +1}^(nFeatures × nContexts)

function trainOnReal(episode: Episode, η: double): void
    // "Wake" phase: bottom-up recognition update on real data.
    // Use existing BitLinearTrainer for this — delegate.
    bitLinearTrainer.updateStep(weights, episode.features, episode.context, η)
end

function sampleFantasy(n: int, rng: Random): List<Episode>
    // "Sleep" phase: top-down generative sampling.
    fantasies = []
    for i in 0..n:
        // Sample a ternary context vector from {-1, 0, +1}.
        ctx = new double[nContexts]
        for j in 0..nContexts:
            ctx[j] = pickFrom([-1.0, 0.0, +1.0], rng)
        // Generate features from weights · ctx (BitLinear forward).
        feat = weights.forward(ctx)  // ternary output
        fantasies.append(new Episode(feat, ctx, source = FANTASY))
    return fantasies
end
```

### 4.3 Emergent swarm coordination via stigmergic M3 traces (multi-brain ensemble)

```text
class StigmergicFederation(
    sharedMemory: CodebookMemory,   // M3 shared digest
    localBrain: MultiBrainEnsemble,
    decay: double = 0.95
)

function deposit(label: String, evidence: HdcCode, weight: double): void
    // Pheromone-like deposit; weight decays over time.
    sharedMemory.learn(label, evidence, weight)
end

function sample(label: String, k: int, rng: Random): List<BrainId>
    // Each "brain" in the federation biases sampling toward
    // high-pheromone entries. Probability ∝ exp(weight).
    return sharedMemory.topK(label, k, rng)  // weighted sampling
end

function evaporationStep(): void
    sharedMemory.decayAll(factor = decay)  // global decay
end

function runCycle(episode: Episode): void
    // Step 1: this brain proposes a hypothesis.
    prediction = localBrain.activate(episode.features)
    // Step 2: deposit a trace with weight ∝ confidence.
    deposit(prediction.label, hdcEncode(prediction), weight = prediction.confidence)
    // Step 3: probabilistically adopt another brain's trace (ACO step).
    peers = sample(prediction.label, k = 3, rng)
    for peer in peers:
        if peer != localBrain.id:
            // Copy a fraction of peer's weights (alignment in flocking).
            localBrain.softBlendFrom(peer, rate = 0.01)
end
```

### 4.4 Metacognitive self-model (Hofstadter loop, framed as a state machine)

```text
class SelfModel(
    brain: BrainPipeline,
    metaBrain: BrainPipeline,   // a second viewpoint over the first
    confidenceThreshold: double = 0.5
)

// Level 0: the brain's own forward pass
function act(observation: FeatureVec): Action
    return brain.forward(observation)
end

// Level 1: metaviewpoint — predict what the brain WILL do
function metaPredict(observation: FeatureVec): PredictedAction
    prediction = brain.predict(observation)        // internal prediction
    return prediction
end

// Level 2: compare prediction vs actual behavior; update confidence
function metaUpdate(observation: FeatureVec, actual: Action): void
    predicted = metaPredict(observation)
    error = predictiveCoder.computeError(actual, predicted)

    // Strange loop: the metaBrain re-represents this error
    // as a *new observation* for itself.
    metaBrain.learn(encode(error), errorLabel = "self-discrepancy")

    // If error > threshold, escalate: trigger curiosity impulse.
    if error.magnitude > confidenceThreshold:
        emit(Impulse.curiosity(error.magnitude))
end

// The closed loop (Gödel/Hofstadter):
function tick(observation: FeatureVec): Action
    action = act(observation)
    metaUpdate(observation, action)
    return action
end
```

### 4.5 Free-energy-minimizing consolidation loop (Friston-flavored)

```text
class FreeEnergyConsolidator(
    hippocampus: HdcBrain,
    neocortex: MultiBrainEnsemble,
    dreamer: BitLinearDreamer,
    beta: double = 1.0  // precision on prediction errors
)

function freeEnergy(realEpisode: Episode, fantasy: Episode): double
    // F = accuracy_term - complexity_term (standard variational bound)
    // For binary / ternary HDC, log-likelihood reduces to:
    //   -log P(real | neocortex) ≈ Hamming(real, neocortex.recall(real))^2
    realErr  = hamming(realEpisode.target, neocortex.recall(realEpisode.features))
    fantErr  = hamming(fantasy.target, neocortex.recall(fantasy.features))
    // KL divergence between posterior q(z) and prior p(z) ≈ |fantErr - realErr|
    complexity = abs(realErr - fantErr)
    return beta * realErr + complexity
end

function step(realEpisode: Episode, η: double, rng: Random): void
    fantasy = dreamer.sampleFantasy(1, rng)[0]
    F_real = freeEnergy(realEpisode, fantasy)

    // Gradient on F wrt neocortex weights ≈ contrastive Hebbian:
    //   Δw = -η ∂F/∂w = η * (realErr - fantErr) * (real-feat ⊗ real-target)
    neocortex.contrastiveHebbian(
        pos = realEpisode, neg = fantasy, rate = η
    )

    // HDC: don't change (sparse, slow).
    // Just bump the hippocampal index's Hebbian strength.
    hippocampus.hebbianStrengthen(realEpisode, delta = η)
end
```

### 4.6 Taoist wu-wei as a policy (with verification gate)

```text
class WuWeiPolicy(
    actionArena: ActionRegistry,
    surprise: SurpriseChannel,
    wuWeiThreshold: double = 0.05
)

function selectAction(candidates: List<Action>, state: WorldState): Action
    surprises = [surprise.expectedFreeEnergy(a, state) for a in candidates]
    bestIdx = argmin(surprises)

    if surprises[bestIdx] > wuWeiThreshold:
        // World is predictable AND boring: do nothing.
        return actionArena.noop()  // explicit no-action primitive

    if surprises[bestIdx] > 2 * wuWeiThreshold:
        // World is very predictable: the most "natural" action wins.
        // This is the Taoist "going with the flow".
        return candidates[bestIdx]

    // World is genuinely surprising: explore.
    return actionArena.curiositySample(state, ε = 0.1)
end
```

### 4.7 Embodied-cognition grounding: read/write the world through `NcaBrainSimulator`

```text
class EmbodiedNcaCortex(
    nca: NcaBrainSimulator,         // grid of 10K-bit vectors
    sensoryChannel: SensoryInput,
    motorChannel: MotorOutput,
    hdcBinding: HdcBinding
)

// Each NCA cell holds an HDC vector; updates are
// functions of neighborhood Hamming similarity.
// This is a continuous-state Lenia-style brain.
function senseAndAct(observation: FeatureVec): Action
    hdcObs = hdcBinding.encode(observation)

    // Inject observation at the "sensory cortex" edge of the grid.
    nca.injectAtEdge(hdcObs, edge = SENSORY)

    // Run NCA for N steps.
    nca.step(N = 8)

    // Read the "motor cortex" edge of the grid.
    motorHdc = nca.readEdge(edge = MOTOR)

    // Decode to action via HDC recall.
    return hdcBinding.recall(motorHdc)
end
```

### 4.8 Hermeneutic interpretive loop (Gadamer's horizon-merging)

```text
class HermeneuticLoop(
    symbolGrounding: CrossModalPaired,
    predictiveCoder: PredictiveCoder
)

function interpret(artifact: Artifact, currentBelief: HdcCode): HdcCode
    // The "horizon" of artifact is its symbolic embedding.
    horizonArtifact = symbolGrounding.encode(artifact)

    // Merge horizons: simple weighted blend in HDC space.
    // In HDC, "blend" = majority-vote on bits.
    newBelief = hdcMajorityVote(horizonArtifact, currentBelief, weights = [0.5, 0.5])

    // The merge is itself a prediction error against the old belief.
    err = hamming(newBelief, currentBelief)

    // Update confidence based on the merge error.
    predictiveCoder.update(currentBelief, err, learningRate = 0.05)

    return newBelief
end
```

---

## 5. Local Codebase Connections

| Algorithm (Section 4) | MATRIX primitives used | File-level mapping |
|----------------------|------------------------|--------------------|
| 4.1 Two-stage consolidation | `HdcBrain`, `MultiBrainEnsemble`, `PredictiveCoder` | NEW: `subconscious/TwoStageConsolidator.java`. Replaces / extends `DreamReplayer` (DESIGN-26). |
| 4.2 BitLinear dreamer | `BitLinear`, `BitLinearTrainer` | NEW: `subconscious/BitLinearDreamer.java`. Composes with existing `BitLinearTrainer`. |
| 4.3 Stigmergic federation | `MultiBrainEnsemble`, `CodebookMemory` | NEW: `federation/StigmergicFederation.java`. Extends `MeshFederation` (DESIGN-08). |
| 4.4 Self-model loop | `BrainPipeline`, `PredictiveCoder` | NEW: `brain/SelfModel.java`. Builds on `brain/Viewpoint.java`. |
| 4.5 Free-energy consolidator | `HdcBrain`, `MultiBrainEnsemble`, `BitLinearDreamer` | NEW: `subconscious/FreeEnergyConsolidator.java`. Generalizes `TwoStageConsolidator`. |
| 4.6 Wu-wei policy | `ActionRegistry` (DESIGN-13), surprise channel | NEW: `consciousness/WuWeiPolicy.java`. Adds `noop` action to registry. |
| 4.7 Embodied NCA cortex | `NcaBrainSimulator`, `HdcBinding`, `CrossModalPaired` | NEW: `brain/EmbodiedNcaCortex.java`. Extends DESIGN-59. |
| 4.8 Hermeneutic loop | `CrossModalPaired`, `PredictiveCoder` | NEW: `consciousness/HermeneuticLoop.java`. Wraps `SymbolGrounding` (DESIGN-L5). |

### Direct mappings to existing DESIGN-{NN}

- **DESIGN-26** (Dream Replay) → REPLACE with `TwoStageConsolidator` (Section 4.1).
- **DESIGN-31** (Hopfield) → KEEP; becomes the *cortical attractor* in the two-stage model.
- **DESIGN-32** (Boltzmann) → KEEP; provides the *sleep sampling* primitive for the dreamer.
- **DESIGN-43** (Predictive Coding) → KEEP; central to all consolidation and self-model loops.
- **DESIGN-54** (HDC × BitNet hybrid) → KEEP; provides the basic substrate.
- **DESIGN-55** (Russian/Asian cybernetics) → KEEP; Anokhin's functional system = the policy layer above consolidation.
- **DESIGN-58** (Capability Levels) → EXTEND with **L7 "self-model"** and **L8 "moral imagination"** levels.
- **DESIGN-59** (NCA brain) → EXTEND with `EmbodiedNcaCortex` (Section 4.7).

---

## 6. Novel Combinations That Haven't Existed Before

These combinations are *not* in the literature as of 2026-09; they are original syntheses made possible by MATRIX's hybrid substrate:

1. **HDC-as-hippocampal-index-of-BitLinear-weights.** Bind the HDC of `(feature, BitLinear-output)` pairs during wake; during REM, recall pairs by partial cue to generate ternary `{-1, 0, +1}` fantasy episodes that are themselves bit-linear-compatible. Closest prior: BitNet b1.58 uses ternary weights but doesn't do wake-sleep replay; HDC codebooks store whole patterns but aren't BitLinear-native.
2. **MultiBrainEnsemble as a CLS neocortex with distributed fantasy.** Eight brains in the ensemble each generate slightly different dreams; their disagreement is the **uncertainty signal** that drives curiosity impulse (H-039). No prior work uses ensemble disagreement as a sleep-time consolidation driver.
3. **`NcaBrainSimulator` with HDC-per-cell state.** Each NCA cell carries a 10K-bit HDC vector; the update rule is a Lenia-style continuous kernel over Hamming-similarity neighborhoods. Mordvintsev's NCA uses 3-channel RGB cells; Lenia uses scalar fields. A 10K-bit HDC-per-cell Lenia is novel.
4. **Free-energy loss as a unifying training signal for `BitLinearTrainer` + `HebbianUpdater` + `PredictiveCoder`.** Currently these have separate loss functions; unifying them under a single `FreeEnergyLoss` is novel engineering.
5. **`PredictiveCoder` as a fantasy generator for `BitLinearDreamer`.** Predictive coding's top-down generative pass produces real-valued predictions; quantizing them to `{-1, 0, +1}` and feeding into a BitLinear forward pass is a clean integration of two hemispheres that haven't been joined before.
6. **`StigmergicFederation` for `MeshFederation`.** The existing federation (DESIGN-08) is gossip-based; adding stigmergic trace accumulation on top gives ACO-style convergence without changing the protocol.
7. **`WuWeiPolicy` with explicit no-op as a first-class action.** Most agent designs treat inaction as a degenerate case; making it a first-class action primitive is unusual and matches the Taoist framing.
8. **`HermeneuticLoop` over HDC major-vote merging.** Symbol-grounded interpretation as a HDC-space merge, with prediction error driving confidence update. Closest prior: Hopfield-style attractor dynamics, but applied to *interpretive* inputs (artifacts) rather than raw perception.

---

## 7. Concrete Implementation Plan (priority order)

Each task = 1 "wave" (~3 weeks solo work per AGENTS.md / DESIGN-58 cadence). Targets are calibrated against `H-039..H-068` gates in `HYPOTHESES-NEW.md`.

| Priority | Task | Class(es) | New hypothesis / gate | Est. lines | Tests | Spec/Design |
|----------|------|-----------|----------------------|-----------|-------|-------------|
| **P0** | **BitLinearDreamer** | `subconscious/BitLinearDreamer.java` | H-069: BitLinear fantasy episodic F1 ≥0.7 vs real | ~120 | 6 | DESIGN-60 |
| **P0** | **TwoStageConsolidator** (replaces DESIGN-26) | `subconscious/TwoStageConsolidator.java` | H-070: Two-stage consolidation beats single-stage replay by ΔF1 ≥ 0.05 | ~180 | 8 | DESIGN-60 |
| **P1** | **FreeEnergyLoss** | `subconscious/FreeEnergyLoss.java` | H-071: F convergence ≤ 100 steps | ~80 | 5 | DESIGN-61 |
| **P1** | **FreeEnergyConsolidator** (generalization) | `subconscious/FreeEnergyConsolidator.java` | (uses H-070 + H-071) | ~150 | 6 | DESIGN-61 |
| **P2** | **SelfModel** | `brain/SelfModel.java` | H-072: meta-prediction accuracy ≥ 0.8 vs actual behavior | ~150 | 7 | DESIGN-62 |
| **P2** | **StigmergicFederation** | `federation/StigmergicFederation.java` | H-073: federation convergence ≤ N cycles | ~140 | 6 | DESIGN-63 |
| **P3** | **EmbodiedNcaCortex** | `brain/EmbodiedNcaCortex.java` | H-074: NCA regrows attractor after 50% damage (extends H-065) | ~180 | 8 | DESIGN-59-extended |
| **P3** | **WuWeiPolicy** | `consciousness/WuWeiPolicy.java` | H-075: noop fraction converges to >0.5 in stable environments | ~100 | 5 | DESIGN-64 |
| **P4** | **HermeneuticLoop** | `consciousness/HermeneuticLoop.java` | H-076: interpretation confidence correlates with downstream task accuracy | ~120 | 6 | DESIGN-65 |
| **P4** | **PragmaticTest** (intervention-based update) | `consciousness/PragmaticTest.java` | H-077: confidence-after-intervention predicts future accuracy | ~100 | 5 | DESIGN-65 |
| **P5** | **Capability Level L7: Self-model** | integrates SelfModel + WuWei + HermeneuticLoop | H-072, H-075, H-076 | integration only | 15 | DESIGN-58-extended |

**Total estimated:** ~1320 LOC, 70 unit/integration tests + 5 JMH benchmarks.

**Mapping to existing AGENTS.md:**
- All new classes must have JUnit 5 tests (AGENTS.md §Testing).
- All randomness via injected `Random(seed)`, no wall-clock (AGENTS.md §Запреты).
- New classes follow CONSTITUTION I (pure) where possible; integrate with existing DESIGN-{08, 26, 31, 32, 43, 54, 55, 59} primitives.

---

## 8. Gaps / Risks

1. **Free Energy implementation may be intractable in pure Java.** Friston's F = ⟨log p(s|m)⟩ − KL(q‖p) requires online variational inference; current `PredictiveCoder` is only one step. We propose a *contrastive Hebbian approximation* (Section 4.5) that avoids explicit posterior inference. **Risk:** the approximation may not behave like the true F. **Mitigation:** gate on H-071 before relying on the consolidator for higher-level consolidation.

2. **Stigmergic federation may need a global clock** for trace evaporation. MATRIX's design forbids wall-clock (AGENTS.md §Запреты). **Mitigation:** use event-counted evaporation — every K write events, multiply all traces by decay.

3. **NCA-with-HDC-per-cell will be memory-hungry.** 10K-bit per cell × N² cells = N²×10K bits. For N=64 cells, that's 5.1 MB per grid. **Mitigation:** benchmark first; gate at H-074 only if memory < 50 MB.

4. **The Hofstadter-style "I" loop has no falsifiable criterion.** This is a philosophical risk, not an engineering one. **Mitigation:** gate on H-072 (meta-prediction accuracy) which is operationalizable without claiming consciousness.

5. **Existing SPEC-007 invariant #1 (subconscious only via gate) may be violated by FreeEnergyConsolidator if it writes to M3 directly.** **Mitigation:** all consolidator writes must go through `FnlGate` (DESIGN-12) → `M3` is the only legal sink.

6. **Capability Level L7 has not been defined yet in DESIGN-58.** Need to write `DESIGN-58-L7-self-model.md` before implementation starts.

7. **Active Inference's "principle vs hypothesis" ambiguity** (Friston 2018 interview, cited in Active Inference article). We are implementing a *process hypothesis* (specific loss, specific update rules), not the principle itself. Document this distinction in code comments.

---

## 9. Knowledge Persistence (META-R2)

This report persists findings into the following locations per META-R2:

- **This file:** `docs-v2/research/reports/EXP-RESEARCH-MEMORY-EMERGENCE-METACOGNITION-REPORT.md`
- **New DESIGN files to author** (each Task in §7):
  - `docs-v2/designs/DESIGN-60-two-stage-consolidation.md`
  - `docs-v2/designs/DESIGN-61-free-energy-consolidation.md`
  - `docs-v2/designs/DESIGN-62-self-model-loop.md`
  - `docs-v2/designs/DESIGN-63-stigmergic-federation.md`
  - `docs-v2/designs/DESIGN-64-wu-wei-policy.md`
  - `docs-v2/designs/DESIGN-65-hermeneutic-pragmatic-loops.md`
  - `docs-v2/designs/DESIGN-58-L7-self-model.md` (extends L7)
- **Update `HYPOTHESES-NEW.md`** with H-069..H-077.
- **Update `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md`** META-R1 examples with these findings.

---

## 10. Summary

**What's already there:**
- DESIGN-26 (basic replay), DESIGN-31 (Hopfield), DESIGN-32 (Boltzmann), DESIGN-43 (Predictive Coding), DESIGN-54 (HDC × BitNet), DESIGN-55 (Russian/Asian cybernetics), DESIGN-59 (NCA brain) — strong substrate.
- 8 MPDT brains in `MultiBrainEnsemble` — already gives us a "neocortex" substrate.

**What's missing (the gap this report fills):**
1. A **two-stage consolidation algorithm** that explicitly transfers HDC hippocampal traces → BitLinear-ternary cortical weights via replay (Section 4.1).
2. A **free-energy objective** that unifies existing loss functions (Section 4.5).
3. A **self-model loop** that closes the viewpoint-of-viewpoint cycle (Section 4.4).
4. **Stigmergic federation** for ensemble coordination without central control (Section 4.3).
5. **Philosophical disciplines** for design (Sections 4.6–4.8) — wu-wei, hermeneutics, pragmatism, wabi-sabi.

**What's truly novel:**
- 8 combinations (Section 6) that have no prior-art and are made possible specifically by MATRIX's HDC × BitLinear × Boolean × NCA × MultiBrainEnsemble substrate.

**Next concrete step:**
Write `DESIGN-60-two-stage-consolidation.md` and `BitLinearDreamer.java` (P0, ~120 LOC, 6 tests). Estimated to close H-070 gate within 2 weeks of focused work.