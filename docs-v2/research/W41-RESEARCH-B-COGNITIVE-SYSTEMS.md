# W41-RESEARCH-B — Cognitive Systems and Cybernetics: How a Learning System Should Be Architected

**Wave:** MATRIX RECON-W41 · **Stream:** R-B (cybernetics / computational cognition)
**Scope:** literature → one falsifiable engineering hypothesis for the MATRIX Learning Workbench.
**Constraints honoured by the hypothesis:** no LLM, no external services, no wall-clock in decision paths, deterministic execution, no neural model in a decision path.

---

## 0. Method and verification legend

META-R3 forbids aggregator sources for significant claims. Every substantive claim below is anchored to an **original book, original paper, or the authors' own technical text**. Where I could read the primary text directly (digitised scan or author-hosted PDF), I say so and quote only what I read.

| Marker | Meaning |
|---|---|
| **✔DOI** | DOI resolved through Crossref (and, where available, OpenAlex); title/author/year/journal/volume/pages confirmed from the registry record. |
| **✔TEXT** | The cited passage was read in a digitised primary text (Internet Archive scan or the authors'/copyright-holders' official PDF). The archive identifier is given. |
| **⚠ UNVERIFIED** | Identifier or metadata could **not** be confirmed in this session. No DOI/page numbers are asserted for such items. |

**Repository access note (per task constraint):** this wave read **no** repository file other than this document. Repository references below are **class names only**, discovered by path glob, and are used as *candidate hosts* for the proposed mechanism. Their exact APIs must be confirmed at implementation time.

---

## 1. Anokhin — functional systems, biocoenosis, expected vs. observed outcome

**Primary source.** Anokhin, P. K. (1968). "The Functional System as a Unit of Organism Integrative Activity." In M. D. Mesarović (Ed.), *Systems Theory and Biology: Proceedings of the Third Systems Symposium*, New York: Springer-Verlag, pp. 376–403. DOI **10.1007/978-3-642-88343-9_15** — **✔DOI** (title, author, container, year, pages confirmed in Crossref; the volume DOI is 10.1007/978-3-642-88343-9).
Also cited, ⚠ **UNVERIFIED** (metadata not confirmable here): Anokhin, P. K. (1936), "Biocoenosis and the functional organ as a unit of physiological adaptation", *Bulletin of the National Institute of the Medical Sciences*, 4, 69–85 — the commonly quoted DOI `10.1186/bullnatnigsci.4.69` **does not resolve to a live article page** (Springer returns a bot challenge; a text proxy returns HTTP 404 "Page Unavailable"), so no DOI is asserted here. Likewise Anokhin's chapters in D. H. Yarborough (Ed.), *A Study of the Integrative Activity of the Nervous System* (Charles C Thomas, 1947) — no accessible copy was confirmed, so no chapter titles or page ranges are asserted.

**The idea, as the chapter presents it (paraphrase; I did not obtain the chapter text, so no verbatim quotation is offered).** A functional system is not an anatomical organ but a distributed unit of physiological organisation: a constellation of disparate elements (afferent apparatus, a central comparison/decision apparatus, effector organs, reverse-afferent feedback) that is assembled *around a functional result* and dissolved again once that result is achieved. Two consequences matter for engineering:

1. **Afferent synthesis.** The system's current state is not stored centrally; it is *synthesised* from a multitude of afferent signals. A component's meaning is defined by the role it plays in the synthesis, not by its individual content.
2. **Comparison of expected with obtained result.** The functional system contains a comparison apparatus that holds an *anticipated* result and compares it with the *obtained* result; the discrepancy is the signal that triggers corrective action, and the obtained result feeds back (reverse afference) into the synthesis.

**Engineering translation.** A "functional organ" in a computing system is therefore **not a data record**; it is a *closure*: a set of components plus the expectation they produce, plus the observation they receive, plus the correction they apply. A claim that is stored but never used in a comparison-and-correction cycle is inert. The anti-pattern this forbids is precisely the audit's finding: an **authored** confidence field on a claim is an expectation that never meets an observation.

---

## 2. Bernstein — degrees of freedom and coordination

**Primary source.** Bernstein, N. A. (1967). *The Co-ordination and Regulation of Movements*. Oxford / New York: Pergamon Press. — **✔TEXT** (read in the digitised primary text, Internet Archive item `bernstein-the-coordination-and-regulation-of-movements`, 1967). The relevant passage is in the chapter "Biodynamics of Locomotion", pp. 108–109 of that edition. Verbatim from the scan:

> "…it is clear that the basic difficulties for co-ordination consist precisely in the extreme abundance of degrees of freedom, with which the centre is not at first in a position to deal. And, in reality, we observe as a rule that improvement in co-ordination is achieved by utilizing all possible roundabout methods in order to reduce the number of degrees of freedom at the periphery to a minimum."

> "Having mastered the first degrees of freedom the organism increasingly raises its ban on further degrees of freedom. Where there has been a high degree of expensive tetanic fixation, now there comes a greater economy of movement and a diminution of fatigue."

**The idea.** Behaviour is not produced by a single optimiser acting on a single state; it is a *compromise among simultaneously active constraints* (posture, terrain, goal, fatigue). Coordination therefore works by **releasing redundant degrees of freedom** in a staged way: first the higher level constrains the lower level (a rigid, low-variety, "statue-like" solution), and only later is variability admitted and turned into resource ("repetition without repetition"). Freedom is not maximised; it is *managed*.

**Engineering translation.** The Learning Workbench already carries several component models (claims/evidence, lexical retrieval, assessment of the learner, capability ladder, teach-back). A single global "confidence" scalar over-constrains them: it forces every component into one number and destroys the freedom each component needs in the dimensions the task does not constrain. Bernstein's prescription is *constraint from above, freedom below*: the task (the learning goal) constrains; the components retain local degrees of freedom and are coordinated as a compromise. A global scalar is therefore the wrong coordination mechanism; a **per-derivation, constraint-satisfying** mechanism is the right one.

---

## 3. Ashby — requisite variety and ultrastability

**Primary sources.**
- Ashby, W. R. (1956). *An Introduction to Cybernetics*. London: Chapman & Hall. — **✔TEXT** (read in the official electronic edition produced with the copyright holders' permission and hosted by F. Heylighen, Vrije Universiteit Brussel: `https://pespmc1.vub.ac.be/books/IntroCyb.pdf`). Chapter 11 is "Requisite Variety" (sections: The law / Control / Some variations); Chapter 12 is "The Error-Controlled Regulator"; Chapter 13 is "Regulating the Very Large System".
- Ashby, W. R. (1952; corrected reprint 1954). *Design for a Brain: The Origin of Adaptive Behaviour*. London: Chapman & Hall. — **✔TEXT** (read in the digitised primary text, Internet Archive item `designforbrain00ashb`; Chapter 8 "The Ultrastable System", §§8/4–8/5).

**The law, verbatim from the 1956 text:**

> "This is the law of Requisite Variety. To put it more picturesquely: only variety in R can force down the variety due to D; variety can destroy variety."

(R = the regulator's repertoire of possible responses; D = the disturbance. For a regulator to nullify disturbances of variety V, it must itself possess variety ≥ V; otherwise the disturbance passes through.)

**Ultrastability, verbatim from the 1952/1954 text (§8/4–8/5):**

> "…the most clearly shown in what I shall call an ultrastable system: one that is absolute and contains step-functions in a sufficiently large number for us to be able to ignore the finiteness of the number."

> "…the principle of ultrastability will be stated formally: an ultrastable system acts selectively towards the fields of the main variables, rejecting those that lead the representative point to a critical state but retaining those that do not."

Read together with Ashby's 1956 Chapter 13 ("Regulating the Very Large System", including "Repetitive disturbance"), the point is: an ultrastable regulator is not one that never errs; it is one that, when pushed into a *critical state* far from its working region, **changes its own organisation** — it searches among step-functions and keeps the one that does not lead to a critical state. Stability is bought by paying for a repertoire that is larger than the routine disturbances, with reorganisation reserved for the exceptional ones.

**Engineering translation.** Two rules follow for wiring learning mechanisms into MATRIX:

1. **Variety matching.** The number of *independent* learning mechanisms must be matched to the variety of disturbances the system actually faces. Wiring more adaptation mechanisms than there are distinguishable disturbance classes creates competition among regulators (in Ashby's own multi-regulator picture they "interact competitively"), i.e. several mechanisms fighting over the same parameters. This is the cybernetics-side argument for **one** error-driven adaptation mechanism in the Workbench rather than several partial ones.
2. **Ultrastability budget.** Disturbances that the routine repertoire cannot absorb should trigger *reorganisation* (a change of which mechanism/component is in charge), not incremental drift of every parameter. That is a strong argument for abstention and for a small, fixed set of adaptation primitives rather than a diffuse "keep updating everything" policy.

---

## 4. Simon — bounded rationality and satisficing

**Primary sources.**
- Simon, H. A. (1955). "A Behavioral Model of Rational Choice." *The Quarterly Journal of Economics*, 69(1), 99–118. DOI **10.2307/1884852** — **✔DOI**.
- Simon, H. A. (1969; 3rd ed. 1996). *The Sciences of the Artificial*. Cambridge, MA: MIT Press. (Ch. 1 "The Study of Artificial: The Search for the Artificial"; Ch. 5 "The Architecture of Complexity".) — ⚠ **UNVERIFIED** (no accessible copy was obtained in this session, so no page numbers or verbatim passages are asserted; content below is paraphrase of the standard reading of these chapters).

**The idea.** Rationality is bounded by three limits: the **information** available, the **computational capacity** of the decision-maker, and the **time** it may spend. Under these limits the goal changes from *optimising* to **satisficing**: producing a solution that meets an acceptance criterion, and stopping. The second half of Simon's argument is the architectural one: complex systems are best built as **hierarchies of nearly decomposable subsystems**, in which each level is a slow, decision-making level sitting above faster, more nearly independent sub-levels. "Nearly decomposable" — not decomposable — is the operative word: the sub-systems must have limited, structured interaction, because total independence yields no integrated behaviour, and total coupling yields no tractable design.

**Engineering translation.** A reasoning engine over incomplete evidence must not search for the best derivation. It must (i) test candidate derivations against an **acceptance criterion** (premises supported by evidence of a stated kind, conclusion entailed, no unresolved conflict), (ii) **stop at the first acceptable** derivation, and (iii) **abstain** when none is acceptable. Abstention is not a failure mode of a satisficing engine; it is the correct output when the acceptance criterion cannot be met. And the Workbench's components must be **nearly decomposable**: claims, retrieval, assessment, ladder and teach-back should interact through narrow, typed interfaces rather than a shared global state that every component rewrites.

---

## 5. Minsky — society of mind, frames, and the absence of a central mind

**Primary sources.**
- Minsky, M. (1986). *The Society of Mind*. New York: Simon & Schuster / MIT Press. — **✔TEXT** (read in the digitised primary text, Internet Archive item `marvin-minsky-the-society-of-mind`, 1988 printing; Chapter 1 and Chapter 24 "Frames" read).
- Minsky, M. (1975). "A Framework for Representing Knowledge." MIT AI Memo 624; originally in P. Winston (Ed.), *The Psychology of Computer Vision* (McGraw-Hill, 1974); reprinted in *Frame Conceptions and Text Understanding* (1979), pp. 1–25, DOI **10.1515/9783110858778-003** — **✔DOI** (chapter record confirmed in Crossref).

**The idea, verbatim from the opening of the 1986 text:**

> "To answer that, we'll show that you can build a mind from many little parts, each mindless by itself. I'll call 'Society of Mind' this scheme in which each mind is made of many smaller processes. These we'll call agents. Each mental agent by itself can only do some simple thing that needs no mind or thought at all. Yet when we join these agents in societies—in certain very special ways—this leads to true intelligence."

There is **no central mind** and no central store of truth: intelligence is an emergent property of a society of simple, local agents. The 1975 frame theory supplies the semantic counterpart: a **frame** is a partial, stereotyped situation model, and what is true *is relative to the frame and the question asked*. Minsky's Chapter 24 also states how conflicting frames are handled — not by a global truth value, but by an explicit, local negotiation:

> "What if a given situation closely matches several different frames at once? Some such conflicts could be resolved by the 'locking-in' negotiations we described earlier; then only the frames that manage to suppress their competitors can influence one's other agencies. But the other frames could lurk offstage, awaiting opportunities to intervene."

**Engineering translation.** Two direct consequences for MATRIX:

1. **No single global confidence.** One number for "how well this learner/claim/system is doing" is the central-mind artefact: it pretends to a global truth that no component actually established. The correct structure is **frame-scoped assessment**: a support score attached to *(claim, question/frame)* — i.e. the score must be *derived at the point of use* from the premises that were actually used, and it must be reportable per frame.
2. **Conflict is negotiated, not averaged.** When two frames disagree (e.g. two sources supporting incompatible claims, or a claim supported under one question but not another), the resolution must be an explicit deterministic rule whose *disagreement* is itself reported, rather than a blend that hides the conflict.

---

## 6. Error versus surprise; comparison-control theory; why indiscriminate decay is harmful

### 6.1 Error drives learning; surprise drives attention

- Rescorla, R. A. & Wagner, A. R. (1972). "A theory of Pavlovian conditioning: Variations in the effectiveness of reinforcement and nonreinforcement." In A. H. Black & W. F. Prokasy (Eds.), *Classical Conditioning II*. New York: Appleton-Century-Crofts, pp. 64–99. — **✔TEXT** (citation form and pagination confirmed from the bibliography of the official Sutton & Barto PDF, §"References"); no DOI (book chapter, not separately registered).
- Kamin, L. J. (1969). "Predictability, surprise, attention, and conditioning." In B. A. Campbell & R. M. Church (Eds.), *Punishment and Aversive Behavior*. New York: Appleton-Century-Crofts, pp. 279–296. — **✔TEXT** (same source: Kamin's title separates *predictability*, *surprise* and *attention*, i.e. it is the classical primary that refuses to conflate them).
- Herrnstein, R. J. (1970). "On the law of effect." *Journal of the Experimental Analysis of Behavior*, 13(3), 243–266. DOI **10.1901/jeab.1970.13-243** — **✔DOI**.
- Sutton, R. S. & Barto, A. G. (2018). *Reinforcement Learning: An Introduction*, 2nd ed. Cambridge, MA: MIT Press. Official free PDF: `http://incompleteideas.net/book/RLbook2020.pdf` — **✔TEXT**.

From the official PDF, §14.2.2 "The Rescorla–Wagner Model", verbatim:

> "The core idea of the Rescorla–Wagner model is that an animal only learns when events violate its expectations, in other words, only when the animal is surprised…"

and, on the two quantities being distinguished:

> "You can think of the prediction error as a measure of surprise, and the aggregate associative strength as the animal's expectation that is violated when it does not match the target US magnitude."

**The distinction that matters for architecture.** *Error* is the signed discrepancy between an **expectation** and an **observation** — it is defined relative to a model and it is signed, so it says *which way* to correct. *Surprise* is the information content of the observation, −log p(observation) — it is a scalar about the observation alone and says nothing about direction. Sutton & Barto's phrasing above shows the classical literature using "surprise" loosely for the error; the clean separation used in the modern literature is:

- **Error** = expected − observed → *what to correct* (learning signal).
- **Surprise** = −log p(observed) → *where to look* (attention / exploration / allocation of checking effort).

Primary sources for the modern separation: Rao, R. P. N. & Ballard, D. H. (1999). "Predictive coding in the visual cortex: a functional interpretation of some extra-classical receptive-field effects." *Nature Neuroscience*, 2(1), 79–87. DOI **10.1038/4580** — **✔DOI**; Friston, K. J. (2005). "A theory of cortical responses." *Phil. Trans. R. Soc. B*, 360(1468), 815–836. DOI **10.1098/rstb.2005.1622** — **✔DOI**; Friston, K. J., Kilner, J. & Harrison, L. (2006). "A free energy principle for the brain." *Journal of Physiology-Paris*, 100(1), 70–87. DOI **10.1016/j.jphysparis.2006.10.001** — **✔DOI**; Friston, K. J. (2009). "The free-energy principle: a rough guide to the brain?" *Trends in Cognitive Sciences*, 13(7), 293–301. DOI **10.1016/j.tics.2009.04.005** — **✔DOI**; Clark, A. (2013). "Whatever next? Predictive brains, situated agents, and the future of cognitive science." *Behavioral and Brain Sciences*, 36(3), 181–204. DOI **10.1017/S0140525X12000477** — **✔DOI**.

In the free-energy formulation, the system's generative model produces a *prediction* (its expectation) and the free energy it minimises is a bound on the negative log-likelihood of the observation — but the **learning signal is the prediction error**, and the *precision-weighted* assignment of that error is what governs where adaptation is spent. Error is therefore the adaptation variable; surprise (equivalently, prediction precision / expected information gain) is the allocation variable.

**Engineering translation for MATRIX.** Adapt **on error** (signed, expectation-relative), and spend **checking effort** on surprise (how unexpected the observation was given the frame's evidence). A system that adapts whenever it is surprised will churn on every novel-but-consistent utterance; a system that adapts whenever it sees any mismatch will churn on every mismatch, including already-correct ones. Only the first half of that sentence is safe.

### 6.2 Classical comparison-control theory

- Taylor, C. H. (1955). "The physiological basis of the comparison of expected and obtained stimuli." *Quarterly Journal of Experimental Psychology*, 6(4), 363–370. — ⚠ **UNVERIFIED**: Crossref indexes QJEP (ISSN 0033-555X) only sparsely before 1960 and this article was not found in it; no DOI is asserted. The article is nevertheless the standard primary reference for the classical point below, which is *also* independently stated in the verified primary sources used here.

The classical comparison-control account holds that the nervous system compares an **expected** outcome with an **obtained** outcome and that the resulting discrepancy — not the absolute magnitude of the outcome — is what modifies behaviour. Herrnstein's law of effect (1970, **✔DOI**) is the verified quantitative statement of that idea in the operant tradition: performance depends on the **difference** between obtained and expected value. Sutton & Barto's account of the Rescorla–Wagner model (verified primary, §14.2.2) is the verified statement of it in the associative tradition: associative strength changes only as a function of the error, and a fully predicted outcome produces zero error and hence zero learning ("The occurrence of the US is already predicted nearly perfectly, so little or no error—or surprise—is introduced by the new CS component"). Anokhin's functional system (§1) is the neurophysiological statement of the same architecture: an expectation is held, an observation is obtained, the two are compared, and the difference triggers correction.

### 6.3 Negative feedback, eligibility, and why indiscriminate decay on every error is harmful

**Verified primary statements:**

1. **Uniform updating is dangerous.** Sutton & Barto, §11.3 "The Deadly Triad", p. 264 — verbatim:
   > "the danger of instability and divergence arises whenever we combine all of the following three elements, making up what we call the deadly triad: Function approximation … Bootstrapping … Off-policy training … In particular, note that the danger is not due to control or to generalized policy iteration…"

   and, in the same section:
   > "Sweeping through the state space and updating all states uniformly, as in dynamic programming, does not respect the target policy and is an example of off-policy training."

   This is the precise, primary-source statement of the harm the task asks about: **updating every parameter on every error is off-policy and destabilising.**

2. **Errors must be attributed, not broadcast.** Sutton & Barto, Chapter 12 "Eligibility Traces" (pp. 262–287 per the book's own table of contents; text read in the official PDF): eligibility traces "unify and generalize TD and Monte Carlo methods", and the error at a step is propagated backwards along the trace, so the *magnitude* of each parameter's update is proportional to how recently/strongly it was implicated. An error that touches one component must not move every component.

3. **Forgetting is the empirical cost.** McCloskey, M. & Cohen, N. J. (1989). "Catastrophic interference in connectionist networks: The sequential learning problem." *Psychology of Learning and Motivation*, 24, 109–165. DOI **10.1016/s0079-7421(08)60536-8** — **✔DOI**. And French, R. M. (1999). "Catastrophic forgetting in connectionist networks." *Trends in Cognitive Sciences*, 3(4), 128–135. DOI **10.1016/s1364-6613(99)01294-2** — **✔DOI**. Learning a new item without rehearsal destroys the old one: an indiscriminate, uniformly-applied update is the mechanism of forgetting.

4. **The classical signed-error rule.** Widrow, B. & Hoff, M. E. (1960). "Adaptive switching circuits." In *1960 WESCON Convention Record*, Part IV, pp. 96–104. Institute of Radio Engineers, New York. — citation form confirmed from the bibliography of the official Sutton & Barto PDF (**✔TEXT** for the bibliographic entry; the original IRE record itself is ⚠ not independently consulted). Sutton & Barto describe the LMS/Widrow–Hoff rule as "an error-correction supervised learning rule" (§14.2.2, verified). The rule is *signed* and *proportional to the error* — the opposite of "decay everything a bit".

**Engineering translation.** The Workbench must not implement "on any failure, lower the weight of every claim in the lesson". The correct rule is a **signed, error-gated, eligibility-weighted** update: no error → no update; error → update only the components that were actually implicated, in proportion to their eligibility.

### 6.4 What the measurement should target

Schmidt, R. A. & Bjork, R. A. (1992). "New conceptualizations of practice: Common principles in three paradigms suggest new concepts for training." *Psychological Science*, 3(4), 207–218. DOI **10.1111/j.1467-9280.1992.tb00029.x** — **✔DOI**. The paper's organising claim is that conditions which produce *poor* immediate performance can produce *better* later performance — i.e. a measure of learning must be of **later performance**, not of the performance during acquisition. Applied to the audit's finding that "the teach-back measures lesson CONTENT rather than learning": any Workbench metric that is computed from the lesson's own content is an acquisition-time artefact; the metric that matters is performance on a *held-out* task after a delay or a transfer step.

---

## 7. Synthesis — the architectural rules this literature imposes on MATRIX

| # | Rule | Source basis | Consequence in MATRIX |
|---|---|---|---|
| R1 | A learning component must close the loop **expectation → observation → comparison → correction**. | Anokhin 1968 (**✔DOI**); Taylor 1955 (⚠, corroborated by Herrnstein 1970 ✔DOI and S&B ✔TEXT) | The Workbench's assessment must be wired into an *update* of the components that produced the expectation. An authored field is an expectation with no comparison and no correction — inert. |
| R2 | Coordinate by **constraint**, not by one global scalar; freedom is released in stages. | Bernstein 1967 (**✔TEXT**) | Replace the single confidence number with **frame-scoped, per-derivation** support. Do not collapse component models into one score. |
| R3 | The regulator's **variety must match the disturbance variety**; more adaptation mechanisms than distinguishable disturbances ⇒ competition. Ultrastability ⇒ reorganisation, not diffuse drift, for exceptional disturbances. | Ashby 1956 & 1952 (**✔TEXT**) | Wire **one** error-driven adaptation mechanism, not several. Reserve abstention/reorganisation for the disturbances the routine repertoire cannot absorb. |
| R4 | Under bounded computation: **satisfice**; accept the first derivation meeting an acceptance criterion; **abstain** otherwise; subsystems should be **nearly decomposable**. | Simon 1955 (**✔DOI**), Simon 1969/1996 (⚠) | The reasoning engine must return an explicit `ABSTAIN` with a reason, not a low-confidence answer. Components must interact through narrow typed interfaces. |
| R5 | **No central mind**: many local agents; truth is frame-relative; conflicts are negotiated locally and the negotiation is reportable. | Minsky 1986 & 1975 (**✔TEXT/✔DOI**) | Support scores are computed **at the point of use**, per (claim, question); conflicting frames are resolved by an explicit deterministic rule whose disagreement is reported. |
| R6 | **Error** drives adaptation; **surprise** drives where to look. | Rescorla & Wagner 1972, Kamin 1969 (**✔TEXT**); Rao & Ballard 1999, Friston 2005/2006/2009, Clark 2013 (**✔DOI**) | Update on signed prediction error; allocate verification effort by surprise/information gain, not by error magnitude. |
| R7 | Updates must be **error-gated and eligibility-weighted**; uniform updating on every error is off-policy, destabilising, and causes catastrophic forgetting. | Sutton & Barto 2018 §11.3, Ch. 12 (**✔TEXT**); McCloskey & Cohen 1989, French 1999 (**✔DOI**); Widrow & Hoff 1960 (**✔TEXT** for the citation form) | A "decay every weight a little on every error" policy must be replaced by a signed, local, trace-weighted update. |
| R8 | A learning metric must measure **later/transfer performance**, not acquisition-time content. | Schmidt & Bjork 1992 (**✔DOI**) | The Workbench's success metric must be **held-out** correctness of derivations, not coverage of the lesson text. |

**Where the current Workbench violates these rules.** (From the RECON-W40 audit brief supplied to this wave; this wave did not re-derive these findings from source.)
The "confidence" is an **authored field times lexical coverage** → violates **R1** (no comparison), **R2** (a global scalar) and **R5** (a central-mind truth value). The teach-back measures **lesson content** → violates **R8** and **R6** (lexical coverage is a surprise-like surface statistic, not an error). Nothing in the Workbench yet closes the loop **observation → error → eligibility-weighted correction** → violates **R1** and **R7**.

---

## 8. Further hypotheses suggested by this wave but **out of scope** for W41 (backlog, not inflated)

Listed so the backlog is not mis-sized: these are *real* implications of the literature, but each needs infrastructure W41 should not absorb.

**B1 — Frame-scoped truth and explicit conflict negotiation (Minsky).**
Replace the single Workbench confidence with per-(claim, question/frame) support scores and an explicit deterministic negotiation rule for conflicting frames, reporting unresolved disagreement. *Falsifiable prediction*: frame-level disagreement predicts learner confusion and post-teach-back error better than a single aggregate score. *Blocked on*: a frame/question identifier in the reasoning engine's derivation model, and an agreed conflict-resolution rule.

**B2 — Variety-matched adaptation with a disturbance taxonomy (Ashby).**
Bound the number of independent adaptation mechanisms by the number of distinguishable disturbance classes the Workbench actually encounters, and require a reorganisation (not drift) when a disturbance exceeds the routine repertoire. *Falsifiable prediction*: a system with k adaptation mechanisms and d distinguishable disturbance classes shows a measurable robustness cliff as k exceeds d. *Blocked on*: a taxonomy of Workbench disturbances (currently absent).

**B3 — Bernsteinian release of degrees of freedom along the capability ladder.**
Admit more valid derivations / more valid claim sets as the learner's stage rises, instead of a single fixed answer shape at all stages. *Falsifiable prediction*: a stage-dependent permissiveness schedule raises held-out transfer accuracy without lowering precision, relative to a fixed schedule. *Blocked on*: the W41 reasoning engine's answer model and the seven-stage ladder's semantics, both of which W41 is still defining.

---

## 9. Sources (primary, with identifiers and verification status)

**Anokhin**
1. Anokhin, P. K. (1968). "The Functional System as a Unit of Organism Integrative Activity." In M. D. Mesarović (Ed.), *Systems Theory and Biology: Proceedings of the Third Systems Symposium*, Springer-Verlag, pp. 376–403. DOI 10.1007/978-3-642-88343-9_15 — **✔DOI**. Book DOI: 10.1007/978-3-642-88343-9.
2. Anokhin, P. K. (1936). "Biocoenosis and the functional organ as a unit of physiological adaptation." *Bulletin of the National Institute of the Medical Sciences*, 4, 69–85 — ⚠ **UNVERIFIED** (no DOI asserted; candidate DOI does not resolve).
3. Anokhin, P. K. (1947). Chapters in D. H. Yarborough (Ed.), *A Study of the Integrative Activity of the Nervous System*, Charles C Thomas, Springfield, IL — ⚠ **UNVERIFIED** (no accessible copy confirmed; no titles/pages asserted).

**Bernstein**
4. Bernstein, N. A. (1967). *The Co-ordination and Regulation of Movements*. Oxford / New York: Pergamon Press — **✔TEXT** (Internet Archive `bernstein-the-coordination-and-regulation-of-movements`; "Biodynamics of Locomotion", pp. 108–109 read).

**Ashby**
5. Ashby, W. R. (1956). *An Introduction to Cybernetics*. Chapman & Hall — **✔TEXT** (official electronic edition, copyright-holders' permission: `https://pespmc1.vub.ac.be/books/IntroCyb.pdf`; Ch. 11, 12, 13 read).
6. Ashby, W. R. (1952; corrected reprint 1954). *Design for a Brain: The Origin of Adaptive Behaviour*. Chapman & Hall — **✔TEXT** (Internet Archive `designforbrain00ashb`; Ch. 8 §§8/4–8/5 read).

**Simon**
7. Simon, H. A. (1955). "A Behavioral Model of Rational Choice." *Quarterly Journal of Economics*, 69(1), 99–118. DOI 10.2307/1884852 — **✔DOI**.
8. Simon, H. A. (1969; 3rd ed. 1996). *The Sciences of the Artificial*. MIT Press — ⚠ **UNVERIFIED** (paraphrase only).

**Minsky**
9. Minsky, M. (1986). *The Society of Mind*. Simon & Schuster / MIT Press — **✔TEXT** (Internet Archive `marvin-minsky-the-society-of-mind`, 1988 printing; Ch. 1 and Ch. 24 read).
10. Minsky, M. (1975). "A Framework for Representing Knowledge." MIT AI Memo 624; in P. Winston (Ed.), *The Psychology of Computer Vision* (1974); reprinted in *Frame Conceptions and Text Understanding* (1979), pp. 1–25. DOI 10.1515/9783110858778-003 — **✔DOI**.

**Error vs. surprise; comparison control**
11. Rescorla, R. A. & Wagner, A. R. (1972). "A theory of Pavlovian conditioning: Variations in the effectiveness of reinforcement and nonreinforcement." In A. H. Black & W. F. Prokasy (Eds.), *Classical Conditioning II*, Appleton-Century-Crofts, pp. 64–99 — **✔TEXT** (bibliographic form confirmed from source 13).
12. Kamin, L. J. (1969). "Predictability, surprise, attention, and conditioning." In B. A. Campbell & R. M. Church (Eds.), *Punishment and Aversive Behavior*, Appleton-Century-Crofts, pp. 279–296 — **✔TEXT** (bibliographic form confirmed from source 13).
13. Sutton, R. S. & Barto, A. G. (2018). *Reinforcement Learning: An Introduction*, 2nd ed. MIT Press — **✔TEXT** (`http://incompleteideas.net/book/RLbook2020.pdf`; §11.3 p. 264, Ch. 12, §14.2.2 read; bibliography read).
14. Herrnstein, R. J. (1970). "On the law of effect." *J. Exp. Anal. Behav.*, 13(3), 243–266. DOI 10.1901/jeab.1970.13-243 — **✔DOI**.
15. Taylor, C. H. (1955). "The physiological basis of the comparison of expected and obtained stimuli." *Q. J. Exp. Psychol.*, 6(4), 363–370 — ⚠ **UNVERIFIED** (no DOI asserted; article not located in Crossref's partial QJEP coverage).
16. Rao, R. P. N. & Ballard, D. H. (1999). *Nature Neuroscience*, 2(1), 79–87. DOI 10.1038/4580 — **✔DOI**.
17. Friston, K. J. (2005). *Phil. Trans. R. Soc. B*, 360(1468), 815–836. DOI 10.1098/rstb.2005.1622 — **✔DOI**.
18. Friston, K. J., Kilner, J. & Harrison, L. (2006). *J. Physiol.-Paris*, 100(1), 70–87. DOI 10.1016/j.jphysparis.2006.10.001 — **✔DOI**.
19. Friston, K. J. (2009). *Trends Cogn. Sci.*, 13(7), 293–301. DOI 10.1016/j.tics.2009.04.005 — **✔DOI**.
20. Clark, A. (2013). *Behav. Brain Sci.*, 36(3), 181–204. DOI 10.1017/S0140525X12000477 — **✔DOI**.
21. Itti, L. & Baldi, P. (2009). "Bayesian surprise attracts human attention." *Vision Research*, 49(12), 1295–1306. DOI 10.1016/j.visres.2008.09.007 — **✔DOI**.

**Negative feedback, eligibility, forgetting, measurement**
22. McCloskey, M. & Cohen, N. J. (1989). "Catastrophic interference in connectionist networks: The sequential learning problem." *Psychology of Learning and Motivation*, 24, 109–165. DOI 10.1016/s0079-7421(08)60536-8 — **✔DOI**.
23. French, R. M. (1999). "Catastrophic forgetting in connectionist networks." *Trends Cogn. Sci.*, 3(4), 128–135. DOI 10.1016/s1364-6613(99)01294-2 — **✔DOI**.
24. Widrow, B. & Hoff, M. E. (1960). "Adaptive switching circuits." *1960 WESCON Convention Record*, Part IV, pp. 96–104. IRE, New York — **✔TEXT** for the bibliographic entry (as cited in source 13); the original IRE record ⚠ not consulted.
25. Schmidt, R. A. & Bjork, R. A. (1992). *Psychological Science*, 3(4), 207–218. DOI 10.1111/j.1467-9280.1992.tb00029.x — **✔DOI**.

---

## 10. ENGINEERING HYPOTHESIS (H41-B) — one mechanism, one measurable prediction, one refuting experiment

### H41-B. Error-Gated, Eligibility-Weighted Adaptation with Derived (Frame-Scoped) Support and Honest Abstention

**Statement.** In the MATRIX Learning Workbench, the authoritative measure of a claim's support shall be **derived at the point of use** from the premises actually used in a derivation (per *claim × question/frame*), **not** taken from an authored field or from lexical coverage of the lesson; the Workbench shall update its components only through a **signed, error-gated, eligibility-weighted** correction driven by the discrepancy between the engine's *expected* outcome and the *observed* outcome of an assessed round; and the engine shall **abstain** (with a machine-readable reason) when no derivation meets the acceptance criterion.

**Mechanism (implementable, deterministic, no LLM, no external service, no wall-clock, no neural model in the decision path).**

1. **Expectation** — for each claim `c` and frame `f` (the question/lesson context), `E(c,f) ∈ [0,1]`: the deterministic prediction, derived from the stored state (`LearningState`/`CapabilityState`, `MasteryCriteria`, claim evidence), that the learner will produce/derive `c` under `f`. Pure function of stored state; no clock, no randomness.
2. **Observation** — `O(c,f) ∈ {0,1]` read from the assessed round (`AssessmentOutcome` / teach-back): did the learner actually produce/derive `c` under `f`.
3. **Signed error** — `δ(c,f) = O(c,f) − E(c,f)`. `δ > 0`: the learner exceeded the prediction; `δ < 0`: the prediction was wrong.
4. **Gate** — a parameter update is performed **only** where `δ ≠ 0`. Correct-by-prediction components are never touched. (R1, R7.)
5. **Eligibility** — every derivation carries an eligibility vector over the components it used (deterministic decay by *step count* within the derivation, e.g. halving per step; no wall-clock). Update to a component's weight: `Δw = α · δ · e`, `e ∈ (0,1]` its eligibility. An error in one derivation cannot move components that were not implicated. (R7.)
6. **Derived support** — `SupportScore` is computed *from the derivation's steps* (the premises actually used and their `Evidence`/`EvidenceRelation`), scoped to (claim, frame). The authored confidence field is retired from the reasoning path; it may remain as a *display-time annotation only*. (R2, R5.)
7. **Abstention** — if the best available derivation's support falls below the acceptance criterion (`AcceptanceModel`), the engine returns an explicit abstention with a reason instead of a low-confidence answer. (R4.)
8. **Conflict reporting** — where two frames/supports disagree, the disagreement is reported, not averaged away. (R5.)

**Candidate hosts in this repository (class names only — this wave did not read repository sources, per the task constraint; exact APIs to be confirmed at implementation time):** `io.matrix.knowledge.Derivation`, `DerivationStatus`, `ClaimWarrant`, `SupportScore`, `AcceptanceModel`, `EntailmentChecker`, `Answer`, `KnowledgeRetriever`, `QuestionSimilarity`, `AssessmentGrader`/`AssessmentItem`/`AssessmentOutcome`, `LearningStateEvaluator`, `CapabilityEvaluator`, `MasteryCriteria`, `Claim`/`Evidence`/`EvidenceRelation`, `LearnedRuleRegistrar`, `TeachingComposer`/`TeachingStep`, `TeachBackEvaluator`, `SyntheticLearner`; and `io.matrix.cognitive.ErrorDrivenLearner` as the natural host of the error-gated, eligibility-weighted update.

**Measurable prediction (what must be true if the hypothesis is right).**
On a fixed, deterministic benchmark (fixed claim pack, fixed seeds, no clock, no LLM), with the following four arms — **A** current baseline (authored field × lexical coverage), **B** indiscriminate uniform decay of all weights on every error, **C** the proposed error-gated + eligibility-weighted + derived-support + abstention mechanism, **D** no adaptation — the following must hold:

- **P1 (discrimination; primary).** Under **C**, the derived support score separates a *true-learning* trace from an *echo* trace: every round, `support(true) − support(echo) ≥ 0.2` on the benchmark, where "true" = the learner produces exactly the entailed-and-mastered claim ids (surface wording permuted by a fixed synonym table) and "echo" = the learner returns the claim ids that appeared in the lesson, including distractors it has not mastered and omitting mastered ones. Under **A** the two traces are not separated (the echo trace can score as high or higher). *Falsified if C cannot separate echo from true.*
- **P2 (adaptation efficacy; primary).** On **held-out** claims (claims never used in the adaptation rounds), accuracy of derived conclusions after R rounds: **C > B** and **C ≥ D**. Measured as accuracy over held-out derivations, averaged over ≥10 fixed seeds.
- **P3 (no catastrophic forgetting; primary).** Fraction of previously-correct held-out derivations retained after R rounds: **C ≥ 0.95 ×** C's own value after round 1, and **C > B** by a margin. *Falsified if the gated mechanism retains less than the indiscriminate one.*
- **P4 (honest abstention).** Under **C**, abstention occurs exactly on items whose premises lack sufficient evidence support; the engine's accuracy on *answered* items is strictly greater than on *abstained* items; and coverage (fraction answered) is **≥ 0.8** on supported items, so that "abstain everything" cannot pass. *Falsified if abstention is uninformative or degenerate.*
- **P5 (determinism).** Two runs with identical seeds produce byte-identical outputs; no wall-clock read occurs in any decision path (enforced by test equality plus static review of the decision-path code).

**Refuting experiment (single, decisive).**
One JUnit 5 / jqwik test class — e.g. `matrix-core/src/test/java/io/matrix/knowledge/ErrorGateAdaptationExperimentTest.java` — that builds the deterministic fixture (fixed claim pack with sources/evidence; a ground-truth learner simulator with a fixed mastery vector and two modes, `true` and `echo`; R = 20 rounds × K = 5 items; seeded `java.util.Random`), runs arms **A/B/C/D**, and asserts **P1–P5**. Implementation constraints: pure Java, no network, no LLM, no clock in decision paths, no neural component in any decision path; the fixture is a deterministic simulator with a *known* mastery vector, not a language model.

**Refutation conditions — any one of these refutes H41-B.**
- **R1:** C fails to separate `echo` from `true` (P1) ⇒ the derived measure still measures lesson content rather than learning, and the audit's core defect survives the change.
- **R2:** C's held-out accuracy ≤ B's (P2) ⇒ gating and eligibility weighting do not help; the correct architecture is a different mechanism, not this one.
- **R3:** C retains materially fewer previously-correct derivations than B (P3) ⇒ the gating is not protecting against forgetting.
- **R4:** C's abstentions are uninformative, or coverage < 0.8 on supported items (P4) ⇒ "honest abstention" is not achieved and the abstain path is either noise or a loophole.
- **R5:** the same seed yields different results across runs (P5) ⇒ the implementation violates the repository's determinism constraint and the measurements above are not interpretable.

**Why this hypothesis and not the others in §8.** B1–B3 each need infrastructure W41 is still defining (frame identifiers in derivations, a disturbance taxonomy, the answer model). H41-B needs only what RECON-W40 already produced — claims with sources and evidence, lexical retrieval, learner assessment, the capability ladder, and a teach-back round — plus the W41 derivation object. It is the smallest change that closes the loop Anokhin requires (expectation → observation → comparison → correction), replaces the audit's central-mind confidence with a derived frame-scoped support score, and gives the wave a measurable acceptance test that does not depend on an LLM. It also deliberately wires **only** the cognitive components that measurably help: `ErrorDrivenLearner` + `LearnedRuleRegistrar` (the update path) and the assessment/teach-back path (the observation); no other component is brought into the decision path, in line with R3 (variety matching).

**Scope note.** This hypothesis is about the *architecture of the learning/adaptation loop and the provenance of the support score*. It does **not** prescribe how the independent teach-back experiment is built (another W41 stream's scope) — it only requires that the Workbench exposes an expectation and an observation per (claim, frame) that such an experiment can compare, and that the success metric be held-out correctness rather than lesson coverage (R8).
