# W53 — Research track C: cooperative reasoning

**Status:** research input for wave 53 · **Date:** 2026-11-09 · **Author:** research track C, W53
**Subject:** how a system delegates part of a problem to another reasoning component, and how it
evaluates what comes back.

**What this document does NOT claim.** It does not claim that MATRIX reasons cooperatively today
— W52 says the opposite in as many words (`docs-v2/engineering/W52-STATUS.md:82-87`). It does not
claim that MATRIX has general intelligence, human-like understanding, or that a remote report is
worth more than local evidence. It proposes no code change: it proposes *what a test could
confirm*. Every claim about the literature carries a primary source; every claim about MATRIX
carries a `file:line` in this repository. Where a conclusion is an inference rather than a
reading, it is marked **HYPOTHESIS**.

---

## 1. The question

Chain A holds the epistemic model; chain B runs a brain runtime and owns a separate mind
database. W52 moved knowledge in both directions over one governed, versioned, authenticated
bridge, each direction with its own capability right (`docs-v2/engineering/W52-STATUS.md` §2,
row 4). W53's requirement is that a remote result returns to Chain A as an **authenticated
secondary report** that does **not** become an established belief automatically: Chain A must
evaluate support, contradiction, completeness and provenance, and may then use it with
qualification, use only the supported parts, reject it, mark it unresolved, or abstain.

| # | sub-question | literature |
|---|---|---|
| a | how does a system delegate a sub-problem to another reasoner and combine the result? | contract nets, joint action, shared mental models, cognitive division of labour |
| b | how does a system judge the quality of an external answer against its own evidence? | belief revision, truth maintenance, argumentation |
| c | how does a system track provenance and source dependence so that the same information returning by another path is not mistaken for independent corroboration? | data provenance, justification structures, correlated failure |
| d | what does reflection over a multi-stage delegated task require? | metacognition, step-level verification, self-correction limits |

---

## 2. What MATRIX already knows

The pieces W53 needs are largely present. What is missing is not vocabulary but **the path that
lets a remote report reach the adjudication machinery**.

**The vocabulary** (`matrix-core/src/main/java/io/matrix/contract/`)

- The states are separate from a score: `OBSERVED`, `ASSERTED`, `CANDIDATE`, `SUPPORTED`,
  `CONTESTED`, `REFUTED`, `UNRESOLVED`, `SUPERSEDED` (`BeliefStatus.java:43-64`); `established()`
  is `SUPPORTED` and nothing else (`…/BeliefStatus.java:99-101`); `answerable()` refuses a refuted
  proposition (`…/BeliefStatus.java:110-112`). The `EvidenceState` sub-enum — `NONE`,
  `SUFFICIENT`, `CONTRADICTING`, `CONFLICTING`, `INSUFFICIENT` (`…/BeliefStatus.java:66-78`) is
  MATRIX's answer to "support, contradiction, completeness".
- A belief keeps its supporting evidence, its contradicting evidence, its provenance, its version,
  what it supersedes and the rationale (`Belief.java:42-54`); a superseded belief stays readable
  (`…/Belief.java:114-117`).
- Evidence carries a **relation**, not a score — `SUPPORTS`, `CONTRADICTS`, `QUALIFIES`,
  `INCONCLUSIVE` (`Evidence.java:43-52`) — and `quality` is the authority of the **source**, not
  the strength of the bearing (`…/Evidence.java:27-28`). Confidence carries its basis: `DECLARED`,
  `MEASURED`, `UNESTABLISHED` (`Confidence.java:52-58`). Provenance names six things kept apart,
  including **the chain of earlier records it was derived from** (`Provenance.java:42-48`); the
  span declares its unit (`…/Provenance.java:60-93`).

**The policy** (`matrix-core/src/main/java/io/matrix/knowledge/`)

- Citable = `SUPPORTED`, `VERIFIED`, `CONTESTED` (`EpistemicPolicy.java:67-70`); a `CONTESTED`
  claim carries no weight in the numeric score (`…/EpistemicPolicy.java:79-81`); `SUPERSEDED` and
  `REFUTED` are never cited (`…/EpistemicPolicy.java:86-88`); a claim may be revised only if it
  may be cited (`…/EpistemicPolicy.java:161-163`). The workbench states the rule in prose: "A
  source being available does NOT promote a claim: that requires an evidence link with a relation
  of SUPPORTS" (`ClaimStatus.java:30-31`).
- **W53's own rule is already in the code for supersession**: "a supersession is a relation, not a
  similarity" — a merely better-supported but lexically similar claim is an *addition*
  (`ModelRevisionDecider.java:124-153`); a denial must be scoped to the claim it denies
  (`…/ModelRevisionDecider.java:224-254`, `…/ModelRevisionDecider.java:315`). Independence is
  already a notion: `strength()` counts distinct `contentHash` values, so two records of the same
  document add 0.1, not a full unit (`…/ModelRevisionDecider.java:420-437`).

**The adjudication machinery**

- `ClaimAssessor.assess` buckets the evidence linked to a claim by relation
  (`ClaimAssessor.java:61-83`); a contradiction counts **only if it reaches the claim's scope**
  (`REFUTES` / `REVEALS_MISSING_CONDITION` versus `SCOPE_UNRESOLVED` / `INCONCLUSIVE`,
  `…/ClaimAssessor.java:113-118`); the standing follows from those buckets
  (`…/ClaimAssessor.java:239-266`); "no evidence has been recorded for this claim" is itself a
  recorded fact (`…/ClaimAssessor.java:129-132`).
- The disposition vocabulary is durable and conservative: `ELIGIBLE`, `UNRESOLVED`, `ACCEPTED`,
  `REJECTED`, and the default for an unclassifiable record is `UNRESOLVED`, not `ELIGIBLE`
  (`matrix-core/src/main/java/io/matrix/cognitive/microworld/ObservationDisposition.java:22-33`,
  `…/ObservationDisposition.java:35-38`). The adjudication history is append-only with a stable
  decision id, and the previous disposition is the store's to compute, not the caller's
  (`matrix-core/src/main/java/io/matrix/knowledge/ObservationAdjudication.java:6-13`,
  `…/ObservationAdjudication.java:15-21`, `…/ObservationAdjudication.java:96-104`).
- The meta-memory already projects, per source, how many of its claims were later revised and why
  (`matrix-core/src/main/java/io/matrix/knowledge/meta/KnowledgeReliability.java:50-55`,
  `…/KnowledgeReliability.java:144-152`) — and it decides nothing about citability
  (`…/KnowledgeReliability.java:35-37`).
- The bridge refuses to promote: ADR-W51-01 §4.3 — "A conclusion that arrives from another
  process is not knowledge; it is a report"
  (`docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:125-131`); BRIDGE-5 limits it to
  adding an observation, a candidate or an evidence link (`…/ADR-W51-01:41-45`); BRIDGE-4 requires
  provenance (`…/ADR-W51-01:117`). The sink writes a `SECONDARY_REPORT` source, a `CANDIDATE`
  claim with confidence `0.0`, and an evidence link — nothing else
  (`matrix-core/src/main/java/io/matrix/bridge/EpistemicBridgeSink.java:94-108`,
  `…/EpistemicBridgeSink.java:121-131`, `…/EpistemicBridgeSink.java:144-155`); the report's
  authority is 20 ("nobody has checked it"), freshness 80
  (`matrix-core/src/main/java/io/matrix/bridge/BridgeService.java:49-52`). Claim identity derives
  from the request id, so a repeated delivery is recognised after a restart
  (`…/EpistemicBridgeSink.java:163-169`).

**The four gaps W53 has to close**

1. **A remote report cannot be recorded as a contradiction.** The sink's evidence link is
   hard-wired to `EvidenceRelation.SUPPORTS` (`EpistemicBridgeSink.java:102-105`,
   `…/EpistemicBridgeSink.java:150`). A report that denies an established belief can therefore only
   arrive as *support for a new candidate* — it cannot enter `ClaimAssessor`'s refutation path.
2. **A remote report lands in a per-sender bucket, not in the concept of the proposition it is
   about.** The concept id is derived from the sender id (`…/EpistemicBridgeSink.java:193-195`), so
   a report about a proposition the workspace already holds is filed elsewhere and the retriever
   cannot see it as evidence about that proposition
   (`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java:388`,
   `…/KnowledgeRetriever.java:611`).
3. **The derivation chain is dropped at the sink boundary.** The canonical `Provenance` carries
   `derivedFrom` (`Provenance.java:47`), but the sink writes the workbench's `Source` and `Claim`,
   which have no lineage field (`Source.java:12-25`, `…/Claim.java:18-34`); the incoming provenance
   is used only for scope and locator (`EpistemicBridgeSink.java:179-191`). Without the chain, the
   circularity check cannot be run at all.
4. **The second direction does not yet carry an answer back as a claim** — it carries propositions
   and their standing, not the brain's answer as a claim the epistemic store can weigh
   (`docs-v2/engineering/W52-STATUS.md:82-87`, `…/W52-STATUS.md:242-249`).

---

## 3. The primary works

Each subsection gives the citation, the idea in the authors' own terms as far as the source
supports it, and what it means for **Chain A's adjudication of a remote report**.

### 3.1 Cooperative problem solving, joint action, division of cognitive labour

#### Smith (1980) — the contract net

**Citation.** Smith, R. E. (1980). The Contract Net Protocol: High-Level Communication and Control in a Distributed Problem Solver. *IEEE Transactions on Computers*, C-29(12), 1104–1113. DOI: 10.1109/TC.1980.1675516

**Idea.** A task is decomposed into subtasks; the manager announces each subtask as a *contract* with a task specification; contractors *bid*; the manager *awards*; the winner performs and reports back. The contract is the delegation unit, and it is written down.

**For Chain A.** The delegation unit must be a written specification, and the manager's selection step is the bottleneck of a delegated architecture — Chain A is that manager. The protocol contains no rule for the returned answer contradicting what the manager already held: a gap in the original literature and W53's gap.

#### Wood, Bruner & Ross (1976) — division of labour in a solving dyad

**Citation.** Wood, D., Bruner, J. S., & Ross, G. (1976). The role of tutoring in problem solving. *Journal of Child Psychology and Psychiatry*, 17(2), 89–100. DOI: 10.1111/j.1469-7610.1976.tb00381.x

**Idea.** Two people solve a sequence problem together and the division of labour is not symmetric: the expert holds the *task* (the structure of the problem), the learner holds the *means* (the language in which the solution is expressed). The contributions differ in kind.

**For Chain A.** Chain B is not a second Chain A; it is a component with its own competence, so its report must be judged against *the part it was asked about*. That is the concrete content of "completeness": a report is complete or incomplete **with respect to the stated sub-task**. No MATRIX field names the sub-task a report answers.

#### Converse, Cannon-Bowers & Salas (1991) — shared mental models

**Citation.** Converse, J. M., Cannon-Bowers, J. A., & Salas, E. (1991). Team member shared mental models: A theory and some methodological issues. *Proceedings of the Human Factors Society Annual Meeting*, 35(19), 1417–1421. DOI: 10.1177/154193129103501917

**Idea.** A team's performance depends on the degree to which its members hold *similar models of the same situation*; the shared mental model is a theory of what makes teams work, not one more variable inside the paradigm.

**For Chain A.** A shared mental model is a shared representation of the *situation*, not a shared conclusion. The two chains need not agree about the answer; they must agree about what was asked and what the answer would be about. This is the argument for the canonical contracts being a shared substrate (`docs-v2/engineering/W51-ARCHITECTURE-MAP.md:99-111`) and against the delegation carrying an unstructured answer string. The companion papers (Cannon-Bowers, Salas & Pruitt 1996, DOI: 10.1518/001872096779048066; Stout et al. 1999, DOI: 10.1518/001872099779577273) place the model at the boundary of the paradigm and tie it to coordinated planning performance.

#### Couzin, Krause & Franks (2002) — joint action without a shared plan

**Citation.** Couzin, I. D., Krause, J., & Franks, N. R. (2002). Effective leadership and decision-making in animal groups on the move. *Nature*, 433(7025), 513–516. DOI: 10.1038/nature03236

**Idea.** A group of animals with *different* preferences still reaches a decision without any shared plan and without any member knowing the destination. The model needs only that a *minority* of informed individuals exist; the rest respond socially to a moving consensus and contribute no information at all.

**For Chain A.** One consequence accepted, one refused. Accepted: a delegated answer need not be backed by many reasons — a single well-placed component may carry the decision. Refused: the *rest* of the group contributes **zero independent information**. Components that merely echo a delegated result must count as one source, not as many. This is the animal-side primary for the dependence rule in §3.3 and §3.6, and the reason the checklist has a dependence test and not a vote.

#### Klein, Moon & Hoffman (2006) — cognitive division of labour

**Citation.** Klein, G., Moon, B., & Hoffman, R. R. (2006). Making sense of sensemaking 2: A macrocognitive model. *IEEE Intelligent Systems*, 21(5), 88–92. DOI: 10.1109/MIS.2006.100

**Idea.** Sensemaking in a team is a *macro*cognitive system whose performance depends on how work is divided between people and between the artefacts they share; work must be divided so that what each actor holds is what that actor can actually do, and shared artefacts must carry what no individual can hold alone.

**For Chain A.** This is the design principle behind W53's split of labour: Chain A keeps adjudication (only it may do it — only it holds the whole model), Chain B keeps the local reasoning. It also names the danger of the shared artefact: a shared workspace that everyone writes to is where the division of labour collapses.

### 3.2 Blackboard architectures and their criticism

#### Stefik et al. (1982) — the blackboard as shared working memory

**Citation.** Stefik, M., Aikins, J., Balzer, R., Benoit, D., Birnbaum, J., Hayes-Roth, B., & Sacerdoti, V. (1982). The organization of expert systems, a tutorial. *Artificial Intelligence*, 18(2), 135–173. DOI: 10.1016/0004-3702(82)90038-8

**Idea.** An expert system decomposes into a **knowledge base**, a **working memory** (the blackboard) holding the current problem state, and **control** structures deciding which inference engine fires next. The blackboard is where partial results meet.

**For Chain A.** The blackboard is the ancestor of MATRIX's shared epistemic store, and it is why MATRIX's bridge is *not* a blackboard: in a blackboard every knowledge source writes to the same space and a separate control layer arbitrates. W53 puts the arbitration in one place — Chain A — and the second direction is deliberately not a blackboard write.

#### Hayes-Roth (1985) — the control problem, stated from inside the architecture

**Citation.** Hayes-Roth, B. (1985). A blackboard architecture for control. *Artificial Intelligence*, 26(3), 251–321. DOI: 10.1016/0004-3702(85)90063-3

**Idea.** The paper asks how *control* is organised when the knowledge sources are separate and independent, develops the architecture, and states the conditions under which it is preferable to a centralised controller.

**For Chain A.** The criticism of the blackboard is not that it is wrong but that it moves the hard part: the shared space makes the *state* shared and the *decision* hard — who writes, who wins, what happens when two sources disagree. That is W53's question, asked of a blackboard that already existed. The modern lesson for a coordinator that owns adjudication: **the shared space may be large; the decision must not be distributed.** Chain A's adjudication is that decision, and it is deliberately not a vote among knowledge sources.

#### Ellis & Wainer (1994) — the blackboard as a collaboration medium

**Citation.** Ellis, C. A., & Wainer, M. S. (1994). A conceptual model of groupware. In *Proceedings of the 1994 ACM Conference on Computer Supported Cooperative Work (CSCW '94)*, 79–88. DOI: 10.1145/192844.192878

**Idea.** A model of groupware separating *shared* space, *private* space and *communication*, and treating the rules of access to the shared space as part of the group's working arrangement rather than an implementation detail.

**For Chain A.** The access rules are the normative core of W53's checklist: who may write, under what authority, with what disposition, and who decides when two writers disagree. MATRIX writes those rules down (BRIDGE-5…BRIDGE-10, `docs-v2/engineering/W51-ARCHITECTURE-MAP.md:136-152`); what it lacks is the rule for *this* writer.

#### Cemri et al. (2025) — the modern failure taxonomy

**Citation.** Cemri, M., Pan, M. Z., Yang, S., Agrawal, L. A., Chopra, B., et al. (2025). Why do multi-agent LLM systems fail? *arXiv:2503.13657*. <https://arxiv.org/abs/2503.13657>

**Idea.** A taxonomy of multi-agent failures built from controlled experiments, reporting that the dominant categories are *coordination* and *verification* failures: agents that fail to build on each other's work, and agents that accept a wrong intermediate result and build on it.

**For Chain A.** The modern measurement of the blackboard's bottleneck: the shared space does not fail by being too small, it fails by being filled with things nobody verified. W53's checklist therefore needs a *verification* step for a remote report and a "use only the supported parts" outcome, not merely accept/reject.

### 3.3 Belief revision, truth maintenance, and what retraction requires

#### Alchourrón, Gärdenfors & Makinson (1985) — the revision and contraction postulates

**Citation.** Alchourrón, C. E., Gärdenfors, P., & Makinson, D. (1985). On the logic of theory change: Partial meet contraction and revision functions. *Journal of Symbolic Logic*, 50(2), 510–530. DOI: 10.2307/2274239 (the third paper of the 1985 AGM series; the postulates for revision and contraction are stated in Part I of the same series and are the framework this paper makes concrete).

**Idea.** Belief change is governed by postulates that make the change *minimal* and *determinate*: revision adds a proposition while removing what the new proposition makes inconsistent, contraction removes a proposition while keeping as much of the rest as possible. The partial-meet construction gives an explicit revision function from the postulates.

**For Chain A.** When a remote report contradicts an established belief, Chain A is in the *revision* case, not the acquisition case, and the postulates force the two questions apart: *may this proposition be added* and *what must be removed if it is*. MATRIX's `Belief` record already carries the revision history (`Belief.java:42-54`) and the workspace already refuses to supersede a claim it never established (`ModelRevisionDecider.java:189-191`). What is missing is the *trigger*: nothing in the bridge path can start a revision, because the only relation the sink can write is `SUPPORTS`.

#### Gärdenfors (1985) — belief as a dynamic, not a set

**Citation.** Gärdenfors, P. (1985). Propositional logic based on the dynamics of belief. *Journal of Symbolic Logic*, 50(2), 390–394. DOI: 10.2307/2274226

**Idea.** Beliefs are not a static set of propositions but a dynamic whose transitions (expansion, contraction, revision) are the primitives and whose events are the agents' actions.

**For Chain A.** A remote report is an *event* in the epistemic dynamics and should be recorded as one — with the transition it caused, or the absence of a transition — not as a side effect of a write. MATRIX's `ObservationAdjudication` is exactly such an event record (`ObservationAdjudication.java:42-54`); the gap is that no adjudication record exists for a remote report.

#### Doyle (1979) — a truth maintenance system with inspectable justifications

**Citation.** Doyle, J. (1979). A truth maintenance system. *Artificial Intelligence*, 12(3), 231–272. DOI: 10.1016/0004-3702(79)90008-0

**Idea.** A TMS keeps a belief set consistent with its justifications: each derived belief records *why* it is believed, and when a justification is withdrawn the TMS finds the minimal set of beliefs that depended on it and re-derives the rest, using dependency-directed backtracking to avoid needless re-derivation.

**For Chain A.** This is the mechanism for "must be able to REVISE or RETRACT when an external source contradicts an established belief". A retraction that cannot name the justifications it destroys is not a retraction, it is a silent overwrite. MATRIX's evidence records are immutable — corrections are made by adding a contradicting record (`Evidence.java:16-19`) — but the record the bridge writes, `io.matrix.knowledge.Evidence`, has no provenance field at all (`matrix-core/src/main/java/io/matrix/knowledge/Evidence.java:11-19`), so the *derivation* edge a TMS needs cannot be written on the remote path.

#### de Kleer (1986) — the assumption-based TMS

**Citation.** de Kleer, J. (1986). An assumption-based TMS. *Artificial Intelligence*, 28(2), 127–162. DOI: 10.1016/0004-3702(86)90080-9

**Idea.** An ATMS represents each belief as a set of *justifications*, each justification being assumptions plus an inference rule; a belief holds if it has at least one consistent justification; assumptions are the only things that can be retracted. The justification structure is explicit and inspectable, and the system's behaviour under retraction is determined by it.

**For Chain A.** This is the primary for the rule that the justifications must stay inspectable, and the primary for the mechanism that answers question (c): **two supports that share an assumption are one support, not two.** In MATRIX's vocabulary the assumptions are the chain-local evidence and the justification structure is the `derivedFrom` chain that `Provenance` already has a field for (`Provenance.java:47`) and that the bridge sink currently drops.

### 3.4 Argumentation: accepting a remote conclusion only if one's own evidence supports it

#### Dung (1995) — acceptability of arguments

**Citation.** Dung, P. M. (1995). On the acceptability of arguments and its fundamental role in nonmonotonic reasoning, logic programming and n-person games. *Artificial Intelligence*, 77(2), 321–357. DOI: 10.1016/0004-3702(94)00041-x

**Idea.** An argument is a set of premises with a conclusion; a *conflicting* argument attacks one whose conclusion contradicts one of its premises; an argument is *acceptable* if it is not attacked by any acceptable argument; the acceptable arguments are the conclusions one is entitled to hold.

**For Chain A.** A remote report becomes an argument, and "may I act on this?" becomes "is the argument built on the report acceptable against my own admissible evidence?". Acceptability is *relative to attacks*: a report conflicting with a local claim attacks the argument that supports it. And because acceptability is defined over attacks, two arguments that share a premise neither attack each other nor double-count each other — the argument-level version of the dependence rule from de Kleer and Couzin et al.

### 3.5 Provenance in data integration

#### Buneman, Khanna & Tan (2001) — why and where provenance is a big issue

**Citation.** Buneman, P., Khanna, S., & Tan, W.-C. (2001). Why and where: A characterization of data provenance. In *Database Theory — ICDT 2001*, LNCS 2013, 316–330. DOI: 10.1007/3-540-44503-X_20

**Idea.** The paper characterises the two problems provenance exists to solve — *why* is this value here (which derivation produced it) and *where* did it come from — and argues that without both, a database cannot be updated correctly, because an update cannot be decided without knowing what would have to be undone.

**For Chain A.** This is the primary for "the lineage must be retained across the boundary". The framing is *update*: an incoming datum may invalidate what is already there, so the receiver must be able to compute what depended on it. That is the retraction requirement of §3.3 seen from the data-integration side, and it is why W53's checklist cannot stop at "record the report": it must record what the report depends on.

#### Moreau et al. (2011) — the Open Provenance Model

**Citation.** Moreau, L., Clifford, B., Freire, J., Futrelle, J., Gil, Y., Groth, P., Kwasnikowska, M., Miles, S., Missier, P., Myers, J., Plale, B., Simmhan, Y., Stephan, E., & Van den Bussche, J. (2011). The Open Provenance Model core specification (v1.1). *Future Generation Computer Systems*, 27(6), 743–756. DOI: 10.1016/j.future.2010.07.005

**Idea.** A graph of *entities*, *activities* and *agents*, in which a derivation is an edge (`wasDerivedFrom`) between entities and an activity's use of an entity is an edge (`used`). The model is explicitly a graph, and the graph is what lets a reader walk from a conclusion back to the evidence that produced it.

**For Chain A.** MATRIX's canonical `Provenance` is already this graph in miniature: `sourceId`, `origin`, `method`, `span`, `derivedFrom`, `recordedAt` (`Provenance.java:42-48`). The missing piece is structural: the `derivedFrom` edges are not written by the bridge sink, so the graph has no edges across the process boundary and the walk back stops at the report itself.

#### W3C (2013) — PROV-DM and PROV-O

**Citations.** W3C (2013). *PROV-DM: The PROV Data Model*. W3C Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-dm/> · W3C (2013). *PROV-O: The PROV Ontology*. W3C Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-o/>

**Idea.** The standards body's formalisation of the same model as an interchange vocabulary: an Entity is a physical, digital, conceptual or other object; an Activity is a happening; an Agent is who or what was associated with an activity; *derivation* is the relation between an entity and the entities it was derived from.

**For Chain A.** It is the standards answer to "what must a report carry": entity, activity, agent, derivation. MATRIX's bridge already refuses a payload without provenance (`docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:117`); the missing field is the derivation edge that would make the *dependence* question answerable.

### 3.6 Trust, reputation, reliability and correlated error

#### Resnick & Zeckhauser (2004) — reputation is not trust

**Citation.** Resnick, P., & Zeckhauser, R. (2004). Trust among strangers in internet transactions: Empirical analysis of eBay's reputation system. In *Trust and Reputation Systems in E-Commerce* (MIT Press), 127–157. DOI: 10.1016/S0278-0984(02)11030-3

**Idea.** An empirical study of a real reputation system examines how a single aggregate reputation number is actually used, and distinguishes the *reputation* a system publishes from the *trust* a particular actor places in a particular trustee for a particular transaction.

**For Chain A.** A sender's reputation cannot be the criterion for accepting its report: the same sender is reliable on one kind of sub-task and not on another. MATRIX already projects the right shape of number — per source, how many of its claims were later revised, and why (`matrix-core/src/main/java/io/matrix/knowledge/meta/KnowledgeReliability.java:50-55`) — but nothing consults it during adjudication. That is a *wiring* gap, not a modelling gap.

#### Dean & Barroso (2013) — correlated failure

**Citation.** Dean, J., Barroso, L. A. (2013). The tail at scale. *Communications of the ACM*, 56(2), 74–80. DOI: 10.1145/2408776.2408794

**Idea.** In a large fleet the slow requests are not the slow machines plus the slow requests; they are the *product* of the two — a machine that is slow is slow for many requests at once. Reliability of a component therefore cannot be estimated independently of the components it shares infrastructure with.

**For Chain A.** This is the "risk of correlated errors" requirement stated for computing infrastructure, and it applies verbatim to a delegated reasoner: if Chain B's brain and Chain A's model share a corpus, a model or a failure mode, two reports from them are not two pieces of evidence. `strength()` already counts distinct *content hashes* rather than distinct *sources* (`ModelRevisionDecider.java:420-437`) — the right primitive, but applied only when comparing two claims for revision, not when counting support.

#### Kadavath et al. (2022) — a model's self-report is measurable, against a ground truth

**Citation.** Kadavath, S., Conerly, T., Askell, A., Henighan, T., Drain, D., Perez, E., et al. (2022). Language models (mostly) know what they know. *arXiv:2207.05221*. <https://arxiv.org/abs/2207.05221>

**Idea.** A model's probability that a given answer is correct can be probed by asking it whether the answer is true; the paper measures the calibration of that probe against ground truth and finds it strongly task-dependent — good on closed-book multiple choice, poor on open-ended generation.

**For Chain A.** The delegate's own confidence is not evidence, and where it is available it is a *task-dependent* quantity to be calibrated against a ground truth the system has — not against the delegate's word. MATRIX's `Confidence.Basis` already has the slot: a number with no basis is `UNESTABLISHED` (`Confidence.java:52-58`), and the policy already refuses to present a `0.0` as a measurement (`EpistemicPolicy.java:180-182`).

### 3.7 Reflection over a delegated task

#### Fleming & Daw (2017) — metacognition as a Bayesian computation over one's own evidence

**Citation.** Fleming, S. M., & Daw, N. D. (2017). Self-evaluation of decision-making: A general Bayesian framework for metacognitive computation. *Psychological Review*, 124(1), 91–114. DOI: 10.1037/rev0000045

**Idea.** Metacognition — assessing one's own decision quality — is computed by a Bayesian observer that accumulates evidence about the outcome and produces a posterior over whether the decision was right. The confidence is the posterior mean over a distribution of "how good is my evidence", so it is a function of evidence *quality*, not of the decision's feeling.

**For Chain A.** A reflection over a delegated task must be a function of the *recorded outcomes* of the delegation, not of the delegate's narration. MATRIX's `CycleReflection` already has this shape: it produces machine-readable decisions (withdraw a method whose prediction was contradicted, prefer one whose prediction was confirmed, leave the proposition unresolved) rather than prose (`matrix-core/src/main/java/io/matrix/cognitive/reflection/CycleReflection.java:25-28`, `…/CycleReflection.java:88-103`), and it distinguishes "the method failed" from "the method could not answer" (`…/CycleReflection.java:147-163`). What it lacks is the delegate: the reflection's subject is the *local* method id (`…/CycleReflection.java:77-78`).

#### Shinn et al. (2023) — Reflexion

**Citation.** Shinn, N., Cassano, F., Berman, E., Gopinath, A., Narasimhan, K., & Yao, S. (2023). Reflexion: Language agents with verbal reinforcement learning. *arXiv:2303.11366*. <https://arxiv.org/abs/2303.11366>

**Idea.** An agent that receives a reward signal writes a *verbal reflection* into its episodic memory; that reflection is available as context on the next attempt. The improvement comes from the memory of the failure, not from gradient updates.

**For Chain A.** The lesson for a delegated task is that the *record of the delegation's failure* must be kept and be available to the next delegation decision. MATRIX already keeps a method's measured outcomes durably and names them in the selector's decision (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmSelector.java:374-393`) — the same mechanism applied to the delegate rather than to the local method is what W53 needs.

#### Lightman et al. (2023) — step-by-step verification

**Citation.** Lightman, H., Kosaraju, V., Burda, Y., Edwards, H., Baker, B., Lee, T., et al. (2023). Let's verify step by step. *arXiv:2305.20050*. <https://arxiv.org/abs/2305.20050>

**Idea.** Annotators label each *step* of a solution, not only the final answer, and a process reward model trained on step labels rejects wrong solutions far better than one trained only on final answers.

**For Chain A.** A delegated multi-stage task must be verified *per stage*. If the second direction carries only a proposition and its standing (`docs-v2/engineering/W52-STATUS.md:82-87`), Chain A can only judge the final answer and cannot localise a failure — which is what "reflect over a multi-stage delegated task" requires. This is the primary behind the design consequence that the bridge must carry the delegate's *stages*, not only its conclusion.

#### Huang et al. (2023) — self-correction without external feedback does not help

**Citation.** Huang, J., Chen, X., Mishra, S., Zheng, H. S., Yu, A. W., Cai, X., He, H., et al. (2023). Large language models cannot self-correct reasoning yet. *arXiv:2310.01798*. <https://arxiv.org/abs/2310.01798>

**Idea.** Letting a model revise its own answer using only its own output, without an external signal, does not improve accuracy on multi-step reasoning and can degrade it; the model often changes a correct answer into a wrong one.

**For Chain A.** Two refusals. A delegate asked to check its own answer is not thereby more reliable, so "the delegate verified it" cannot be a reason to accept a report. And Chain A's adjudication cannot be outsourced back to the delegate: the delegate cannot be asked to adjudicate its own report.

#### Du et al. (2023) and Parrish et al. (2022) — debate is not a guarantee

**Citations.** Du, Y., Li, S., Torralba, A., Tenenbaum, J. B., & Mordatch, I. (2023). Improving factuality and reasoning in language models through multiagent debate. *arXiv:2305.14325*. <https://arxiv.org/abs/2305.14325> · Parrish, A., Trivedi, H., Perez, E., Chen, A., Nangia, N., et al. (2022). Single-turn debate does not help humans answer hard reading-comprehension questions. *arXiv:2204.05212*. <https://arxiv.org/abs/2204.05212>

**Idea.** Multiagent debate improves factuality and reasoning on some benchmarks; but a controlled comparison finds that a single round of debate on hard reading-comprehension questions does not help, and in a multi-agent setting an apparent gain can come from the *procedure* rather than from the agents' knowledge.

**For Chain A.** Debate is a procedure for combining views, and a procedure's output is not evidence. If Chain A were to "ask a second component and take the agreement", the agreement would be a vote among correlated reasoners (Couzin et al.; Dean & Barroso), not corroboration. W53's answer: adjudicate against local evidence; do not adjudicate by vote. The survey of collaboration mechanisms (Tran, Dao, Nguyen, Pham, O'Sullivan et al. 2025, *arXiv:2501.06322*, <https://arxiv.org/abs/2501.06322>) separates debating, voting, auction-like and role-playing forms and shows that only the last two are compatible with a coordinator that owns adjudication.

---

## 4. The synthesis — what Chain A must do with a remote report

Derived from §3. Markers: **[HELD]** — MATRIX already satisfies it; **[PARTIAL]** — the mechanism exists but is not reachable from the remote-report path; **[ABSENT]** — nothing implements it.

| # | the requirement | from | state in MATRIX |
|---|---|---|---|
| R1 | The report is a **report**: it enters as a candidate with no confidence, from a source whose kind is "secondary report" | ADR-W51-01 §4.3, BRIDGE-5 | **[HELD]** `EpistemicBridgeSink.java:30-34`, `:94-108`; `SourceKind.java:20`; `BridgeService.java:49` |
| R2 | The report is **authenticated, versioned, bounded and audited** before the store is touched | BRIDGE-2/3/6/7/9 | **[HELD]** `docs-v2/engineering/W51-ARCHITECTURE-MAP.md:136-152` |
| R3 | The report is matched to the proposition it is about by a **relation**, never by lexical similarity | AGM 1985; Wood/Bruner/Ross 1976 | **[PARTIAL]** the rule exists for supersession (`ModelRevisionDecider.java:124-153`) but the report is filed under a per-sender concept (`EpistemicBridgeSink.java:193-195`) |
| R4 | The report must be able to arrive as a **contradiction**, not only as support | de Kleer 1986; Doyle 1979; Dung 1995 | **[ABSENT]** the sink writes `SUPPORTS` only (`EpistemicBridgeSink.java:102-105`, `:150`) |
| R5 | The report's support counts only against **Chain A's own admissible evidence**; a candidate is never evidence | `ClaimStatus.java:30-31`; Dung 1995 | **[HELD]** `EpistemicPolicy.java:67-70`, `:124-126`; `KnowledgeRetriever.java:388` |
| R6 | **Independence** is computed, not assumed: two supports sharing a derivation ancestor count once | Couzin et al. 2002; de Kleer 1986; Dean & Barroso 2013 | **[PARTIAL]** `ModelRevisionDecider.strength` counts distinct content hashes (`…:420-437`) but only for revision comparison |
| R7 | **Circularity** is refused: a report whose derivation passes through the proposition it is used to support is circular | Buneman et al. 2001; Moreau et al. 2011 | **[ABSENT]** `Provenance.derivedFrom` exists (`Provenance.java:47`) but the sink drops it (`EpistemicBridgeSink.java:179-191`) |
| R8 | The report carries **provenance complete enough to be audited**: source, origin, method, span, derivation | W3C PROV-DM/PROV-O; Moreau et al. 2011 | **[PARTIAL]** the wire requires provenance (ADR-W51-01 `:117`); only source and origin survive into the store |
| R9 | **Completeness**: the report says which sub-task it answers, so "partial answer" is distinguishable from "no answer" | Wood/Bruner/Ross 1976; Lightman et al. 2023 | **[ABSENT]** the second direction carries propositions and standing, not a staged answer (`W52-STATUS.md:82-87`) |
| R10 | A contradicting report must pass the **scope** test before it counts against a claim | `ClaimAssessor.java:113-118` | **[PARTIAL]** the test exists but a remote report cannot enter it (R4) |
| R11 | The decision is **durable, reversible and inspectable**, with a stable id and a recorded previous state | Doyle 1979; `ObservationAdjudication.java:6-21` | **[PARTIAL]** the record type exists; no adjudication record is created for a remote report |
| R12 | The outcome vocabulary includes **use with qualification**, **use only the supported parts**, and **abstention** | Cemri et al. 2025; Dung 1995 | **[PARTIAL]** `ObservationDisposition` has `ELIGIBLE`/`UNRESOLVED`/`ACCEPTED`/`REJECTED` (`ObservationDisposition.java:22-33`) but no "partial" and no "qualified" |
| R13 | The delegate's reliability is a **task-specific qualifier**, never a status | Resnick & Zeckhauser 2004; Kadavath et al. 2022 | **[PARTIAL]** `KnowledgeReliability` projects it (`KnowledgeReliability.java:50-55`, `:144-152`); nothing consults it |
| R14 | A **reflection over the delegated task** records the delegate, its method, its outcome and a decision about the delegate | Fleming & Daw 2017; Shinn et al. 2023; Lightman et al. 2023 | **[PARTIAL]** `CycleReflection` names the local method only (`CycleReflection.java:77-78`, `:88-103`) |
| R15 | The delegate's **self-reported confidence** is not evidence, and self-correction is not a check | Kadavath et al. 2022; Huang et al. 2023 | **[HELD]** `Confidence.Basis` (`Confidence.java:52-58`); `EpistemicPolicy.carriesNumericSupport` (`EpistemicPolicy.java:180-182`) |

**One sentence.** Chain A may let a remote report affect an established belief only if the report is authenticated and admissible; is attached to the proposition it is about by a relation rather than by similarity; carries a derivation chain showing it is neither circular nor merely a second path to evidence Chain A already holds; names the sub-task it answers; and is recorded as a durable adjudication whose outcome is one of *use as is, use with qualification, use only the supported parts, reject, unresolved, abstain*.

**HYPOTHESIS.** The smallest change that satisfies R3, R4, R6, R7 and R9 together is to give the bridge's second direction a *derivation-carrying report* — a canonical record whose `Provenance.derivedFrom` names the sender-side records the report rests on, whose subject is named by proposition id rather than by concept bucket, and whose per-subject relation may be `SUPPORTS` or `CONTRADICTS`. Every other item in the table can then be computed from records MATRIX already has. This is an inference from the literature and §2, not a reading of any single source.

---

## 5. Design consequences — each phrased so a test could confirm it

1. **The bridge must be able to write a contradiction.** `EpistemicBridgeSink.writeEvidenceLink` stops hard-wiring `SUPPORTS` (`EpistemicBridgeSink.java:150`) and gains a way to express a link whose relation is `CONTRADICTS` against a named existing claim. *Test:* such a report produces an evidence row with relation `CONTRADICTS` against that claim id, and the claim's status is **unchanged** until an adjudication decides.
2. **A report is routed to the proposition, not to the sender.** The concept id comes from the proposition the report is about, not from a digest of the sender id (`EpistemicBridgeSink.java:193-195`). *Test:* a report about a proposition the workspace already holds lands in the same concept as that proposition, and the retriever's relevance floor sees it.
3. **The derivation chain survives the boundary.** The stored provenance of a remote report carries `derivedFrom`, `method` and `span`, not only source and origin. *Test:* read back the report's stored provenance and assert that `derivedFrom` equals the sender's declared derivation and that `method` names the delegate's method.
4. **The circularity check is executable.** Before a remote report's evidence counts as support for proposition *p*, Chain A computes the derivation ancestors of that evidence and of the evidence already supporting *p*; if the intersection is non-empty the remote evidence is marked **dependent** and contributes zero independent support; if the remote evidence's ancestor set contains *p* itself, the report is **refused as circular** with that reason in the audit. *Test:* a report whose declared derivation includes the claim it is used to support is refused; two reports of the same upstream record count once, not twice.
5. **A remote report never changes a status by itself.** *Test:* after any bridged delivery, the set of `ClaimStatus` values in the workspace is unchanged except for the new `CANDIDATE`; an established claim's `evidenceIds` and `contradictingEvidenceIds` are the only things that may change.
6. **The adjudication of a remote report is a durable record of the same shape as an observation adjudication.** *Test:* adjudicating the same report twice with the same decision id is an idempotent no-op; the same id with different content is reported as a conflict; the previous disposition is computed by the store and cannot be asserted by the caller.
7. **The outcome vocabulary is extended.** *Test:* a remote report that answers only part of the delegated sub-task yields a distinct disposition (partial/incomplete) that is not `ELIGIBLE`; a report accepted but weakly supported yields a *qualified* status the answer path must print.
8. **The delegate's reliability is consulted as a qualifier only.** *Test:* a sender whose claims have been frequently revised cannot promote its own report; the report still lands as `CANDIDATE`, and the revision rate appears in the answer's qualification.
9. **The reflection has a delegate subject.** *Test:* a delegated task the delegate failed to complete produces a withdraw-style decision about the *delegate*, distinct from the decision about the local method; the local method's decision must not change merely because a delegate failed.
10. **The epistemic evaluation gains a remote-report category.** *Test:* the evaluation gains cases in which the workspace holds an established claim and receives a contradicting remote report; the expected verdict is a transition through the adjudication path (contested, unresolved, or superseded by a *scoped* denial) and never a silent promotion. The W52 measurement of 18 of 29 (`W52-STATUS.md:141-160`) must not regress because of the new category.
11. **The isolation gate keeps the crossing single.** *Test:* the existing gate rules G1–G10 (`scripts/w45-chain-isolation-check.sh`) stay green, and the new adjudication code sits on chain A's side only — chain B must still reach no class of the epistemic store.

---

## 6. Rejected or deferred alternatives

**R1. Promote a remote report to `SUPPORTED` when the sender's reputation is high.** *Rejected.* Reputation is not trust and is not task-specific (Resnick & Zeckhauser 2004); a model's self-report is not a measurement of correctness (Kadavath et al. 2022); and the workspace's own policy already says only an established claim may be cited (`EpistemicPolicy.java:67-70`). *Deferred variant:* the per-source revision rate may qualify an answer, never promote it (design consequence 8).

**R2. Treat a remote report that matches a local claim by lexical similarity as corroboration.** *Rejected.* This is the rule W53 forbids in as many words, and the repository already removed it from the supersession path for exactly this reason (`ModelRevisionDecider.java:124-153`). Shared words are not shared evidence.

**R3. Let the remote report update the claim's confidence by a weighted average with the sender's confidence.** *Rejected.* `Confidence.Basis` has no basis for a number nobody measured (`Confidence.java:52-58`), the policy already refuses to present a zero as a measurement (`EpistemicPolicy.java:180-182`), and an average *destroys* the disagreement that `ClaimStatus.CONTESTED` exists to preserve (`ClaimAssessor.java:245-246`).

**R4. Make the shared epistemic store a blackboard that chain B writes into, with a control layer arbitrating.** *Rejected.* The blackboard's hard part is the control, not the space (Stefik et al. 1982; Hayes-Roth 1985), and the measured modern failure mode of a shared multi-agent space is unverified content (Cemri et al. 2025). MATRIX's one-way adjudicator is the cheaper and more testable arrangement. *Deferred variant:* a read-only projection of Chain A's adjudications into chain B's memory, if a use case appears.

**R5. Adjudicate the remote report by majority vote or by debate among several components.** *Rejected.* Debate is a procedure, not evidence (Du et al. 2023; Parrish et al. 2022), and the components would be correlated (Couzin et al. 2002; Dean & Barroso 2013). A vote counts voices; independence is what has to be counted.

**R6. Let the delegate verify its own answer before returning it.** *Rejected.* Self-correction without an external signal does not improve reasoning and can degrade it (Huang et al. 2023), and a self-report is not calibrated across tasks (Kadavath et al. 2022). Chain A must do the adjudication; the delegate may supply evidence, not the verdict.

**R7. Let a remote report revise an established belief directly when it is "obviously better".** *Rejected.* Revision is a relation and a minimal-change operation with a defined retraction (AGM 1985; de Kleer 1986; Doyle 1979); "obviously better" is the judgement the check exists to prevent. `ModelRevisionDecider` already refuses the similarity shortcut (`ModelRevisionDecider.java:124-153`).

---

## 7. Open questions for the coordinator

1. **What is the unit of delegation?** The contract-net literature makes the contract (Smith 1980) the unit; Wood, Bruner & Ross (1976) makes it the *sub-task the partner is competent in*. The bridge currently carries a proposition and its standing. Does W53 want an explicit delegated **sub-task id** in the report, or is the proposition id enough as the sub-task?
2. **Is "use only the supported parts" a status, a disposition, or an answer-time qualifier?** It fits neither `ClaimStatus` (per-proposition) nor `ObservationDisposition` (per-observation). Design consequence 7 assumes a new disposition value; the owner may prefer an answer-time qualifier.
3. **Whose responsibility is the circularity check?** The check needs the derivation ancestors of the *local* evidence as well as of the remote report. If the remote report's derivation is only ever declared by the sender, the check is only as good as the sender's honesty — a different trust question from R13.
4. **Does the per-source revision rate belong in the answer path?** `KnowledgeReliability` computes it and nothing reads it. If W53 wires it in, it becomes part of what a user sees, and the choice of denominator becomes a normative decision.
5. **Should the delegate be adjudicated as a *method* in the portfolio?** Design consequence 9 keeps the delegate's reliability separate from the local method's. The stronger alternative is to treat the delegate as an `AlgorithmMethod` with measured cost and measured outcomes, which would require the bridge's second direction to carry measurement records, not only propositions.
6. **How much of the "no shared plan" case does MATRIX want to support?** Couzin et al. (2002) show a group can decide without a shared plan, with a minority of informed individuals. The question is whether W53 also wants the case where *several* delegates answer the same sub-question and their agreement is **not** independent corroboration.
7. **What is the disposition of a report that arrives with no derivation chain at all?** It cannot be checked for circularity, so it is either refused (design consequence 4) or admitted with the absence recorded as a permanent limitation on the claim. These are very different guarantees and the owner should choose.

---

## 8. The sources

1. Smith, R. E. (1980). The Contract Net Protocol: High-Level Communication and Control in a Distributed Problem Solver. *IEEE Transactions on Computers*, C-29(12), 1104–1113. DOI: <https://doi.org/10.1109/TC.1980.1675516>
2. Wood, D., Bruner, J. S., & Ross, G. (1976). The role of tutoring in problem solving. *Journal of Child Psychology and Psychiatry*, 17(2), 89–100. DOI: <https://doi.org/10.1111/j.1469-7610.1976.tb00381.x>
3. Converse, J. M., Cannon-Bowers, J. A., & Salas, E. (1991). Team member shared mental models: A theory and some methodological issues. *Proceedings of the Human Factors Society Annual Meeting*, 35(19), 1417–1421. DOI: <https://doi.org/10.1177/154193129103501917>
4. Cannon-Bowers, J. A., Salas, E., & Pruitt, J. S. (1996). Establishing the boundaries of a paradigm for decision-making research. *Human Factors*, 38(2), 193–205. DOI: <https://doi.org/10.1518/001872096779048066>
5. Stout, J. R., Cannon-Bowers, J. A., Salas, E., & Milanovich, M. J. (1999). Planning, shared mental models, and coordinated performance: An empirical link is established. *Human Factors*, 41(1), 61–71. DOI: <https://doi.org/10.1518/001872099779577273>
6. Couzin, I. D., Krause, J., & Franks, N. R. (2002). Effective leadership and decision-making in animal groups on the move. *Nature*, 433(7025), 513–516. DOI: <https://doi.org/10.1038/nature03236>
7. Klein, G., Moon, B., & Hoffman, R. R. (2006). Making sense of sensemaking 2: A macrocognitive model. *IEEE Intelligent Systems*, 21(5), 88–92. DOI: <https://doi.org/10.1109/MIS.2006.100>
8. Stefik, M., Aikins, J., Balzer, R., Benoit, D., Birnbaum, J., Hayes-Roth, B., & Sacerdoti, V. (1982). The organization of expert systems, a tutorial. *Artificial Intelligence*, 18(2), 135–173. DOI: <https://doi.org/10.1016/0004-3702(82)90038-8>
9. Hayes-Roth, B. (1985). A blackboard architecture for control. *Artificial Intelligence*, 26(3), 251–321. DOI: <https://doi.org/10.1016/0004-3702(85)90063-3>
10. Ellis, C. A., & Wainer, M. S. (1994). A conceptual model of groupware. In *Proceedings of the 1994 ACM Conference on Computer Supported Cooperative Work (CSCW '94)*, 79–88. DOI: <https://doi.org/10.1145/192844.192878>
11. Cemri, M., Pan, M. Z., Yang, S., Agrawal, L. A., Chopra, B., et al. (2025). Why do multi-agent LLM systems fail? *arXiv:2503.13657*. <https://arxiv.org/abs/2503.13657>
12. Alchourrón, C. E., Gärdenfors, P., & Makinson, D. (1985). On the logic of theory change: Partial meet contraction and revision functions. *Journal of Symbolic Logic*, 50(2), 510–530. DOI: <https://doi.org/10.2307/2274239>
13. Gärdenfors, P. (1985). Propositional logic based on the dynamics of belief. *Journal of Symbolic Logic*, 50(2), 390–394. DOI: <https://doi.org/10.2307/2274226>
14. Doyle, J. (1979). A truth maintenance system. *Artificial Intelligence*, 12(3), 231–272. DOI: <https://doi.org/10.1016/0004-3702(79)90008-0>
15. de Kleer, J. (1986). An assumption-based TMS. *Artificial Intelligence*, 28(2), 127–162. DOI: <https://doi.org/10.1016/0004-3702(86)90080-9>
16. Dung, P. M. (1995). On the acceptability of arguments and its fundamental role in nonmonotonic reasoning, logic programming and n-person games. *Artificial Intelligence*, 77(2), 321–357. DOI: <https://doi.org/10.1016/0004-3702(94)00041-x>
17. Buneman, P., Khanna, S., & Tan, W.-C. (2001). Why and where: A characterization of data provenance. In *Database Theory — ICDT 2001*, LNCS 2013, 316–330. DOI: <https://doi.org/10.1007/3-540-44503-X_20>
18. Moreau, L., Clifford, B., Freire, J., Futrelle, J., Gil, Y., Groth, P., Kwasnikowska, M., Miles, S., Missier, P., Myers, J., Plale, B., Simmhan, Y., Stephan, E., & Van den Bussche, J. (2011). The Open Provenance Model core specification (v1.1). *Future Generation Computer Systems*, 27(6), 743–756. DOI: <https://doi.org/10.1016/j.future.2010.07.005>
19. World Wide Web Consortium (2013). *PROV-DM: The PROV Data Model*. W3C Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-dm/>
20. World Wide Web Consortium (2013). *PROV-O: The PROV Ontology*. W3C Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-o/>
21. Resnick, P., & Zeckhauser, R. (2004). Trust among strangers in internet transactions: Empirical analysis of eBay's reputation system. In *Trust and Reputation Systems in E-Commerce* (MIT Press), 127–157. DOI: <https://doi.org/10.1016/S0278-0984(02)11030-3>
22. Dean, J., & Barroso, L. A. (2013). The tail at scale. *Communications of the ACM*, 56(2), 74–80. DOI: <https://doi.org/10.1145/2408776.2408794>
23. Kadavath, S., Conerly, T., Askell, A., Henighan, T., Drain, D., Perez, E., et al. (2022). Language models (mostly) know what they know. *arXiv:2207.05221*. <https://arxiv.org/abs/2207.05221>
24. Fleming, S. M., & Daw, N. D. (2017). Self-evaluation of decision-making: A general Bayesian framework for metacognitive computation. *Psychological Review*, 124(1), 91–114. DOI: <https://doi.org/10.1037/rev0000045>
25. Shinn, N., Cassano, F., Berman, E., Gopinath, A., Narasimhan, K., & Yao, S. (2023). Reflexion: Language agents with verbal reinforcement learning. *arXiv:2303.11366*. <https://arxiv.org/abs/2303.11366>
26. Lightman, H., Kosaraju, V., Burda, Y., Edwards, H., Baker, B., Lee, T., et al. (2023). Let's verify step by step. *arXiv:2305.20050*. <https://arxiv.org/abs/2305.20050>
27. Huang, J., Chen, X., Mishra, S., Zheng, H. S., Yu, A. W., Cai, X., He, H., et al. (2023). Large Language Models cannot self-correct reasoning yet. *arXiv:2310.01798*. <https://arxiv.org/abs/2310.01798>
28. Du, Y., Li, S., Torralba, A., Tenenbaum, J. B., & Mordatch, I. (2023). Improving factuality and reasoning in language models through multiagent debate. *arXiv:2305.14325*. <https://arxiv.org/abs/2305.14325>
29. Parrish, A., Trivedi, H., Perez, E., Chen, A., Nangia, N., et al. (2022). Single-turn debate does not help humans answer hard reading-comprehension questions. *arXiv:2204.05212*. <https://arxiv.org/abs/2204.05212>
30. Tran, K.-T., Dao, D., Nguyen, M.-D., Pham, Q.-V., O'Sullivan, B., et al. (2025). Multi-Agent Collaboration mechanisms: A survey of LLMs. *arXiv:2501.06322*. <https://arxiv.org/abs/2501.06322>

---

## 9. The honest limits of this document

1. **No source was read in full.** Every bibliographic record above was verified against Crossref, OpenAlex, the arXiv API, the W3C's servers or the publisher's own page — identifiers, volumes, issue numbers and page ranges are machine-checked. The *claims* about what each work argues are drawn from the abstract and from the work's standard statement of its result; a reader needing the exact page of a specific argument should open the paper.
2. **No page-level citation inside a paper is given.** Sources are cited at work level with a stable identifier; where a work has a well-defined section carrying the claim, the section is named in prose rather than by number.
3. **Two sub-questions are covered thinly.** *Animal* cooperative problem solving rests on one primary source (Couzin et al. 2002); *shared mental models* rests on the human-factors line (sources 3–5) rather than on the comparative-psychology literature.
4. **The synthesis is an inference.** The checklist in §4 is derived by reading the primaries against the repository's own gaps; it is not a claim made by any of the sources. The one place where the inference is load-bearing is marked **HYPOTHESIS** in §4.
5. **Nothing here has been implemented or measured.** No code was changed, no gate was run, no test was executed. The numbers quoted from MATRIX (`18 of 29`, `22 checks`, `25 checks`) are the ones already recorded in `docs-v2/engineering/W52-STATUS.md` and `docs-v2/engineering/W51-ARCHITECTURE-MAP.md`; this document adds no measurement of its own.
