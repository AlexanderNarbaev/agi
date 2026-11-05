# W41 — Research Stream C: Knowledge Engineering for an Evidence-Grounded Reasoning Engine

**Wave:** RECON-W41 · **Stream:** C (knowledge engineering & epistemology)
**Date:** 2026-11-01 · **Status:** research output, no code changed
**Deliverable:** one falsifiable engineering hypothesis (§9) + three deferred hypotheses (§10)

---

## 0. Scope, method, and honesty policy

**What this document is.** A mapping from a specific body of primary literature onto the
epistemic-layer design that MATRIX's Learning Workbench needs, ending in one hypothesis that
can be implemented and falsified inside this repository without an LLM.

**Method (META-R3 compliance).** Every substantive claim below is anchored to an original book,
an original paper, or an official technical specification. Aggregators (encyclopaedias, blogs,
Wikipedia) were used only as *locators* and are never cited as evidence. Where a bibliographic
detail could not be verified in this session, the claim is marked **UNVERIFIED** and is
confined to §12.2; those items must not be propagated into downstream documents without an
independent check.

**Repository scope.** Per the wave assignment, no repository file other than this one was read
or modified. Statements about MATRIX internals below are therefore taken from the wave brief
(the `DefaultBrainPipeline` write-back of generated answers at `L2_MODULE` with no validation;
the store holding concepts, claims, sources, evidence links, representations at several
abstraction levels, prerequisites and revisions) and are not independently re-derived here.

**Constraint envelope assumed throughout (from the repo's AGENTS.md):** no wall-clock in
decision paths, no LLM call inside a deterministic contour, deterministic execution, SQLite
persistence with a versioned schema.

---

## 1. The gap that must be closed

The audit finding is precise: `DefaultBrainPipeline` searches long-term memory, prepends the
hits to a generation prompt, and writes **every** generated answer back into the same store at
level `L2_MODULE` with **no validation**. Two consequences follow, and they are different
problems:

1. **The store cannot tell an episode from a fact.** A generated answer and a sourced claim
   occupy the same record shape and the same layer, so no later query can distinguish "the
   system said this" from "a source says this".
2. **The system's own outputs become part of its next inputs.** This is a self-reference
   hazard: unlabelled generations re-enter the retrieval corpus, so the store's apparent
   evidential mass grows with its own output volume.

The literature below implies that these are not one bug but two separable defects, and that the
fix is *not* "add a confidence score". A confidence score collapses exactly the distinctions
this wave needs to preserve. What the literature converges on is a set of **orthogonal,
separately stored axes** (provenance kind, validity, support, scope), plus a **write gate**
that is the only path into the trusted layer.

---

## 2. Truth maintenance: a store that must never silently lose an earlier belief

### 2.1 The original TMS work

Doyle's original truth-maintenance system (Doyle 1979) and de Kleer's assumption-based
counterpart (de Kleer 1986) both model a belief as a node in a *justification graph*:
retracting a belief is not a deletion, it is a dependency-directed walk that invalidates the
dependents and records **why** the belief went out. The belief itself is not silently
destroyed; the justification structure is what makes the operation auditable.

### 2.2 Belief revision, formally

The AGM framework (Alchourrón, Gärdenfors & Makinson 1985) gives the retraction operation a
postulated shape: **contraction** removes a set of statements while adding a *justification for
the removal* — the removed content is replaced by a statement explaining the removal, so that
the resulting theory is not merely smaller but *self-describing*. Revision is contraction
followed by addition. The "partial meet" construction determines *which* statements leave:
AGM's point is that the choice must be deliberate and defensible, not accidental.

Gärdenfors (1988) adds the epistemic-state layer (true / not-known-false / false) and the
epistemic-entrenchment ordering used to pick among rival revision sets.

### 2.3 What this means for MATRIX — three requirements

| ID | Requirement | Source of the requirement |
|---|---|---|
| **R1** | Epistemic rows are **append-only** in the trusted layer; a belief that stops being held is marked `SUPERSEDED` (or `INVALIDATED`), never `DELETE`d. | Doyle 1979; de Kleer 1986 |
| **R2** | Every loss of a belief carries a **justification record** naming the cause (contradiction id, rule id, superseding claim id). | AGM 1985 (justification for contraction) |
| **R3** | The store can reconstruct the belief state at any logical point (replay/regression query). | Doyle 1979 (justification graph) |

**Consequence for the audit gap.** `DefaultBrainPipeline` currently violates R1/R2 by
construction: it *adds* an unvalidated record and thereby changes the belief state with no
justification and no distinction between "asserted" and "generated". §9 makes the write path
that the pipeline uses a non-trusted path.

---

## 3. Provenance: the minimum audit trail for a claim

### 3.1 PROV-DM's core vocabulary

The W3C PROV Data Model (Moreau & Missier, eds., 2013) is built from three types —
**Entity**, **Activity**, **Agent** — and a set of core binary relations: *generation*, *usage*,
*communication*, *start*, *end*, *invalidation*, *derivation*, *attribution*, *association*,
*delegation*, *influence* (plus the non-influence relations *specialization*, *alternate* and
*membership*). PROV-DM notes that "in the core of PROV, all relations are binary". Verbatim from
the specification:

- "An entity is a physical, digital, conceptual, or other kind of thing with some fixed
  aspects; entities may be real or imaginary."
- "A derivation is a transformation of an entity into another, an update of an entity
  resulting in a new one, or the construction of a new entity based on a pre-existing
  entity."
- "Attribution is the ascribing of an entity to an agent."
- "A revision is a derivation for which the resulting entity is a revised version of some
  original."

The **directly asserted vs derived** distinction is native: a record that entered because a
person or document asserted it carries `wasAttributedTo(entity, agent)` with no intervening
`wasGeneratedBy`; a record produced by a derivation carries `wasGeneratedBy(entity, activity)`
plus `used(activity, premise)` plus `wasDerivedFrom(entity, premise)`. The difference is not a
flag; it is the *shape of the provenance graph*.

### 3.2 Reification is required, not optional — and PROV says so itself

PROV-DM §2.1: "In the core of PROV, all relations are binary." §2.2.1.2 then states that binary
relations "are actually shorthands that can be 'opened up' by applications and filled in with
further application details", i.e. an n-ary expanded relation. **This is the licence MATRIX
needs**: a multi-premise derivation cannot be squeezed into a pair of foreign keys; it must be
*opened up* into a first-class object. A derivation that references three premises is a
third-order fact about the derivation activity, and PROV's own extensibility point says so.

### 3.3 Provenance validation is a separate step

PROV-DM §7: the constraints specification "SHOULD be used by developers to compose provenance
descriptions that are valid, and by implementers of reasoning engines aiming to check whether
provenance descriptions have problems." PROV-SEM (Cheney, ed., 2013) supplies a
declarative semantics for exactly this. *Design consequence:* MATRIX must have a
**provenance validator** separate from the **semantic checker** of §9. A record with malformed
provenance is not "unsupported"; it is *malformed*, and the two failures must not collapse.

### 3.4 Why provenance is a critical issue in the first place

Buneman, Khanna & Tan (2001) argue that provenance becomes critical precisely where data is
merged and reused without the original semantics; Cheney, Chiticariu & Tan (2009) organise the
literature as *why* / *how* / *where* provenance is recorded. Both matter here because the
Learning Workbench merges three heterogeneous sources: human-authored sources, derived
representations, and machine generations.

### 3.5 The concrete field set MATRIX needs

| PROV-DM concept | PROV relation | MATRIX field (proposal) |
|---|---|---|
| Entity | `entity(id)` | `claim.claim_id` |
| Activity (the derivation act) | `activity(id)` | `derivation.derivation_id` |
| Agent | `agent(id)` | `source.source_id`, `agent.role` |
| Attribution | `wasAttributedTo(e, ag)` | `claim.pramana = SHABDA/PRATYAKSHA`, `claim.source_id` |
| Generation | `wasGeneratedBy(e, a)` | `claim.derivation_id` |
| Usage | `used(a, e)` | `derivation_premise(premise_claim_id, ordinal)` |
| Derivation | `wasDerivedFrom(e2, e1)` | transitive closure of `derivation_premise` |
| Revision | `wasRevisionOf(e2, e1)` | `claim.revision_of_claim_id` |
| Invalidation | `wasInvalidatedBy(e, a)` | `contradiction` row / supersession pointer |
| Specialization / Alternate | `specializationOf` / `alternateOf` | `scope` (applies-under), rival model pointer |
| Bundle | `bundle(id)` | optional: provenance-of-provenance for an ingestion batch |

---

## 4. Nyāya: telling versus inferring

### 4.1 The four pramāṇas

The Nyāya Sūtra of Gautama enumerates four *means of knowledge* (*pramāṇa*): **perception**
(*pratyakṣa*), **inference** (*anumāna*), **comparison/analogy** (*upamāna*), **testimony**
(*śabda*) — locus: Nyāya Sūtra 1.1.3 (edition and numbering see §11; the sutra-number
attribution is corroborated but was not read directly in this session, see §12.2).

### 4.2 What the primary text actually says (verified quotation)

From the Vārtika of Uddyotakara, in Ganganatha Jha's English translation (archive.org
identifier `the-nyaya-sutras-of-gautama-with-english-notes-1-ganganatha-jha`), which also
carries notes from Vācaspati Miśra's *Nyāyavarṇikā-tātparyatīkā* and Udayana's
*Tātparyaparīśuddhi*:

- On testimony vs. the statement of a conclusion in a *reasoning*: "Verbal Testimony always
  affords a certain and definite cognition of the truth or real nature of things; while what is
  asserted in the Statement of the Conclusion is something that is yet to be proved."
- On the requirement of a known example: "it is only when there is an example, and not
  otherwise, that there can be a valid Inference or Verbal Testimony."
- On what makes an inference an inference (from the *Parīśuddhi*, quoted in Jha's notes):
  "Inference, as the means of inferential cognition, consists of the two factors of (I) the
  remembrance of the invariable concomitance of the probans with the probandum, and (2) the
  recognition of the presence of the probant in the subject."

**Two engineering readings.**

1. *Testimony and inference are different kinds of warrant, not degrees of confidence.* A claim
   that was **told** and a claim that was **inferred** have different justifications and
   different failure modes. A store that merges them (as `L2_MODULE` currently does) cannot
   answer "how do I know this?" — the machine's own question.
2. *An inference without its connective is not an inference.* The "invariable concomitance"
   (the connective / *vyāpti*, embodied in the instance) is a **component of the derivation**,
   not a comment on it. A record that says "D was inferred" without recording *which* rule and
   *which* instance makes D unreviewable — the machine cannot check its own inference.

### 4.3 The decisive point: validity is checked by a separate instrument

The same text is explicit that checking validity is *not* the same operation as producing
knowledge:

> "Hypothetical Reasoning […] consists in the consideration of validity and invalidity […]
> and because it does not bring about the Cognition of things, it cannot be regarded as a
> distinct 'Means of Cognition' by itself."

In Nyāya's architecture, the *tarka* **ratifies** a cognition — it is a validator, not a source
of knowledge. This is precisely the machine-side separation between "is this derivation valid?"
and "are these premises true?", and it is the direct ancestor of §8.

### 4.4 Consequence for MATRIX

Adopt a **closed pramāṇa enum** on every record: `PRATYAKSHA | SHABDA | ANUMANA | UPAMANA | NONE`.
`NONE` means "the store does not know how this entered" and is **disqualifying** for the
trusted layer. Any `ANUMANA` record must carry a *mandatory* derivation with ≥ 1 premise and a
named connective (rule id + instance id). This is the storage form of "the machine must know
what it inferred from what it was told".

---

## 5. Conceptual abstraction and the reification of relations

### 5.1 The classical problem

Quine (1960, *Word and Object*, §§25–26) gives the classic diagnosis: relations cannot be
abstracted from their instances without a one-over problem — "to say of an individual that it
is a member of a relation is not to relate two individuals". Goodman (1951, *The Structure of
Appearance*) analyses the same move as *reification*, i.e. treating an abstract relational
whole as an object in its own right. Both authors are read here as the historical framing; the
engineering payload is the same in both: **if a relation is to be stored, it must be made into
an object with its own identity, and its constituents must be referenced, not copied.**

### 5.2 What ontology design says about the cost

OWL 2 Profiles (W3C Recommendation, 2nd edition, 11 December 2012) makes the cost explicit
and is the right primary source to quote for an engineering audience:

- OWL 2 EL supports property chains (`SubObjectPropertyOf(ObjectPropertyChain(...))`) — i.e. a
  limited higher-order relation — but drops universal quantification, disjunction, class
  negation and inverse properties.
- OWL 2 QL does **not** support property chains, does not support transitive properties, and
  does not support `SameIndividual`.
- OWL 2 RL supports property chains and `SameIndividual`/`DifferentIndividuals`, and is
  deliberately aimed at "rule-based reasoning engines".

The grammar contains **no n-ary relation production** in any profile. The lesson for MATRIX is
therefore not "model everything in OWL 2" but: *a relation over several claims is expressible
only by reification into an object plus references* — and every ontology that dodges this pays
for it either in expressiveness or in efficiency.

### 5.3 The reification MATRIX needs

A **Derivation** is the reified relation:

```
Derivation(derivation_id, rule_id, connective_id, conclusion_claim_id)
DerivationPremise(derivation_id, premise_claim_id, ordinal)
```

The derivation is itself an object: it can be attributed to an agent, be derived from, be
invalidated by, and be reified again if a higher-order derivation is needed. PROV permits this
directly (an Activity can be used, attributed, and associated), and §3.2's "opened up" clause
is the licence for making it n-ary.

**Why this is not academic for the audit gap.** If a claim carries only `derived_from = <one
claim id>`, then a two-premise inference cannot be expressed without either lying (hiding the
second premise) or duplicating the claim (creating a second, differently-supported record).
Both failure modes corrupt the trust layer. Reification removes that failure mode.

---

## 6. Scientific model revision: conflict is preserved, not voted

### 6.1 Holism — a contradiction never involves just two claims

Duhem (1906/1954) established that the physical test of a hypothesis never involves the
hypothesis alone: the test bears on a whole body of statements (laws, auxiliary hypotheses,
initial conditions). Quine (1951, "Two Dogmas of Empiricism") sharpened this into the
"web of belief": any statement, including the most peripheral, can be held against any other.

**Engineering consequence.** A "contradiction" between two MATRIX claims is almost never
between *those two claims*; it is between a *claim-set under a scope* and something else. A
bare pairwise contradiction flag will therefore produce both false positives (claims that differ
in scope) and false negatives (the actual inconsistency is elsewhere in the closure).

### 6.2 Rival models coexist; they are not voted on

- **Kuhn (1962)**: paradigm incommensurability — rival frameworks are not compared pointwise
  on a shared metric, so there is no quantity to majority-vote on.
- **Lakatos (1970)**: a research programme with a "hard core" and a "protective belt" is
  assessed as *progressive* (theorems grow, degenerating problemshifts) or *degenerating*; rival
  programmes are preserved side by side and assessed over time, not reconciled by counting.
- **van Fraassen (1980), "A Formal Approach to the Problem of Alternative Theories"**:
  suspending judgement between rival empirically adequate theories is a respectable epistemic
  position, not a failure to compute.

### 6.3 A contradiction is representable without explosion

Priest (1979) introduced LP, the paraconsistent logic in which a contradiction does not entail
everything. The relevant point for MATRIX is negative and structural: classical logic makes
"keep both conflicting claims" inexpressible only because of explosion; the store must not
inherit that constraint.

### 6.4 What "scope" must carry

A bare contradiction cannot express that two claims conflict *only outside a stated condition*.
The needed fields are:

- a **scope object** (`scope_id`, named conditions, e.g. regime / modality / domain);
- a **contradiction record** (`contradiction_id`, `scope_id`, `claim_a`, `claim_b`, `kind`,
  `detected_by_rule_id`);
- the rule that **scope-qualifies** a conflict: two claims whose scopes do not overlap yield a
  `SCOPED_DISAGREEMENT`, not a contradiction.

PROV's `specializationOf` / `alternateOf` relations (both explicitly *not* influence
relations) are the natural storage form: a scoped claim is a *specialisation* of its general
claim, and a rival model is an *alternate*.

---

## 7. Bayesian revision versus consistency maintenance

### 7.1 Bayes' rule is an updating rule for a fixed hypothesis class

Good (1952) is the classic primary treatment of "Rational Decision" and of the distinction
between the probability of a *proposition* and the probability of a *theory*. Bayes' rule
updates credence in propositions inside a fixed hypothesis space; it does not adjudicate
between rival hypothesis spaces. Using it to choose a model is a different operation from
updating on evidence, and the two must not be conflated.

### 7.2 Marginalisation can hide the object

Geman & Geman (1984) — the Gibbs-distribution/annealing paper — is the canonical statement of
the problem in machine terms: computing a posterior distribution over *parameters* and taking
its mean is **not** the same as recovering the underlying *structure* (their inverse-rendering
example). Averages over hypotheses need not correspond to any hypothesis. This is exactly what
a "trust score" does to an epistemic store: it averages incompatible claims into a number that
corresponds to no claim at all.

### 7.3 Naive fusion of conflicting evidence is not neutral

Dempster (1967) showed that the conjunctive rule of total evidence is not associative and that
its behaviour on conflicting evidence depends on arbitrary normalisation choices. Dempster's
rule is the canonical demonstration that *counting* conflict away is a modelling decision with
non-obvious consequences, not a neutral default.

### 7.4 What a non-probabilistic store must do — and must never do

For MATRIX the store is explicitly **not** probabilistic (there is no likelihood, no prior, no
posterior). The correct analogue of "Bayesian updating" is **scope-qualified consistency
maintenance**: keep both records, mark the conflict, refuse to promote either, and let the
conflict travel with the claim.

**Prohibition list (P).**

| ID | Prohibition | Why |
|---|---|---|
| **P1** | Never resolve a conflict by **counting** how many records assert each side. | Kuhn 1962; Lakatos 1970 (rival frameworks are not commensurable); Dempster 1967 (fusion by counting is not neutral). |
| **P2** | Never store a **scalar confidence** that merges "told", "inferred" and "generated". | §4.2 (testimony vs. inference are different warrants); Geman & Geman 1984 (averaging hides the object). |
| **P3** | Never let a **generated** record become a **premise** of another generated record without passing the write gate. | Audit finding: outputs re-entering inputs. |
| **P4** | Never delete or silently demote a belief to resolve a conflict. | Doyle 1979; de Kleer 1986; AGM 1985 (justified removal, R1–R3). |
| **P5** | Never report "unknown" as "false", and never guess beyond the decidable fragment. | §8 (validity vs. truth); Nyāya's *tarka* as a separate instrument that ratifies but does not cognize. |

---

## 8. Formal entailment: VALID derivation ≠ TRUE premises

### 8.1 The two properties

For a set of premises Γ and a conclusion C:

- **Validity (semantic consequence):** Γ ⊨ C iff *every* model of Γ is a model of C.
  This is a property of the *relationship* between premise-set and conclusion, and nothing
  else.
- **Soundness:** a derivation is sound iff it is valid **and** its premises are true.

The soundness theorem for the propositional calculus (Church 1956, §7 — pagination
UNVERIFIED, see §12.2) makes the separation precise: proof-checking establishes validity;
it establishes nothing about the truth of the axioms used.

**The consequence that drives the whole design.** If Γ is *unsatisfiable* (its premises
contradict each other), then Γ ⊨ C holds **vacuously** — there is no model of Γ to violate it.
A derivation can therefore be perfectly VALID while resting on premises that are jointly
inconsistent. Any system that collapses "valid" into "supported" will read such a derivation as
knowledge. §9 keeps the two axes apart for exactly this reason.

### 8.2 The decidable fragment is the right fragment

- **Boole (1854)** introduced the truth-table method: for *ground* propositional formulae over
  *n* atoms, entailment is decided exactly by enumerating the 2ⁿ valuations. This is finite,
  deterministic, and LLM-free.
- **Henkin (1949; 1950)** established completeness of first-order logic via an effective
  Henkin construction, and Henkin (1950) the analogous result for the theory of types.
- **Trakhtenbrot (1950)**: first-order validity is not decidable (UNVERIFIED bibliographic
  detail, §12.2).

**Engineering consequence.** MATRIX should **not** attempt general first-order reasoning.
It should implement a *bounded ground-propositional fragment*: claims are ground atoms, a
derivation's premises are a set of literals/atoms, and entailment is decided by exhaustive
valuation over the atom budget, with an explicit `UNDECIDED` outcome when the budget is
exceeded (P5). This gives an *exact* validator — the kind "implementers of reasoning engines"
are pointed at in PROV-DM §7 — with no LLM, no network, and no wall-clock.

### 8.3 The two-axis label

```
derivation_status ∈ { NOT_DERIVED, DERIVED_VALID, DERIVED_INVALID, UNDECIDED }
support_status    ∈ { UNSUPPORTED, SOURCE_ASSERTED, DERIVED_SUPPORTED, CONTESTED }
```

- `derivation_status` answers: *is the inference from the recorded premises well-formed?*
- `support_status` answers: *is the justification of this claim itself acceptable?*
- `CONTESTED` = the claim's justification closure contains a detected joint inconsistency
  (e.g. vacuously valid inference from mutually contradictory premises). `CONTESTED` is
  neither `UNSUPPORTED` nor `SOURCE_ASSERTED` and must be a distinct value, because
  "your support contradicts itself" and "you have no support" demand different handling.

The axes are orthogonal by construction, exactly as Nyāya separates *tarka* (the validity
instrument) from the *pramāṇa* (the means of knowledge).

---

## 9. ENGINEERING HYPOTHESIS

> ### H-CK1 — Provenance-gated epistemic layers with an exact, two-axis validity check <!-- no-id: a local numbering, not a register identifier -->
>
> **Mechanism.** MATRIX's claim record gains two orthogonal, separately stored axes —
> `pramana` ∈ {PRATYAKSHA, SHABDA, ANUMANA, UPAMANA, NONE} and
> `derivation_status` ∈ {NOT_DERIVED, DERIVED_VALID, DERIVED_INVALID, UNDECIDED} — plus
> `support_status` ∈ {UNSUPPORTED, SOURCE_ASSERTED, DERIVED_SUPPORTED, CONTESTED}, and a
> reified `derivation`/`derivation_premise` pair (rule id, connective id, ordered premise
> claim ids) that every `ANUMANA` claim must reference. A single **`KnowledgeGate` is the only
> write path into the trusted layer** (`VALIDATED_KNOWLEDGE`); it admits a record only if
> (a) `pramana != NONE`, (b) `support_status ∈ {SOURCE_ASSERTED, DERIVED_SUPPORTED}`,
> (c) `derivation_status != DERIVED_INVALID`, and (d) for `ANUMANA`, the referenced derivation
> has ≥ 1 premise and a named connective. The `DefaultBrainPipeline` write-back is re-pointed
> at `DIALOGUE_HISTORY` / `CANDIDATE_CLAIM` and can no longer write into the trusted layer.
> Entailment is decided by an exact **bounded ground-propositional model enumerator**
> (`EntailmentChecker`, atom budget B ⇒ ≤ 2ᴮ valuations, default B = 16), returning
> `DERIVED_VALID` / `DERIVED_INVALID` / `UNDECIDED`; it consults no score, no count, no
> timestamp, and no network. Detected conflicts are persisted as first-class
> `contradiction(scope_id, claim_a, claim_b, kind, detected_by_rule_id)` rows; **neither side
> is deleted, demoted, or promoted by the conflict**, and the number of agreeing records is
> stored nowhere the gate can read.

**Measurable predictions (all checkable in-repo, no LLM, no external service).**

| ID | Prediction | Threshold |
|---|---|---|
| **P1** | After the change, every record in `VALIDATED_KNOWLEDGE` satisfies the four gate clauses (a)–(d) — including records produced by the generation pipeline. | violations = 0 over the whole corpus + all exercised write paths |
| **P2** | `EntailmentChecker` agrees with an **independent** brute-force reference enumerator on randomly generated ground-propositional theories (fixed seed ⇒ deterministic). | agreement = 100 % over N = 1000 cases |
| **P3** | A claim **D** derived by rule R from premises {P1, P2} where P1 and P2 are mutually contradictory is stored as `derivation_status = DERIVED_VALID` (vacuous validity) and `support_status = CONTESTED`, and is **refused** entry into `VALIDATED_KNOWLEDGE`. | exact label pair + refusal |
| **P4** | A conflicting pair (c, ¬c) both remain present; a `contradiction` row links them; adding 5 further records asserting one side leaves **every** epistemic label and the contradiction row byte-identical. | 0 label diffs (no vote effect, P1 of §7) |
| **P5** | A second, independent derivation of an already-asserted claim creates an **additional** `derivation` row and does **not** rewrite the original claim's `pramana` (an asserted claim does not silently become an inferred one). | `pramana` unchanged |

**Experiment that would refute it** (JUnit 5 + jqwik, SQLite fixture, no LLM):

- **T-A (gate).** Insert a record with empty provenance through the pipeline's public write
  API. *Refutation if* it is accepted into `VALIDATED_KNOWLEDGE`.
- **T-B (inference completeness).** Insert an `ANUMANA` record whose `derivation` has zero
  premises or a null `connective_id`. *Refutation if* it is accepted.
- **T-C (exactness).** Differential test: 1000 seeded random theories, checker vs. reference
  enumerator. *Refutation if* any single case disagrees — the claim "exact" is dead.
- **T-D (valid-but-contested).** Construct P1 = x, P2 = ¬x, D = rule(P1, P2). *Refutation if*
  D is not labelled `DERIVED_VALID`, or not labelled `CONTESTED`, or lands in
  `VALIDATED_KNOWLEDGE`.
- **T-E (no counting).** Add 5 agreeing records to one side of a conflict. *Refutation if* any
  label, any layer, or the contradiction row changes.
- **T-F (determinism / no wall-clock).** Run the fixture twice, and once with a skewed system
  clock, asserting identical database content hashes. *Refutation if* any hash differs.
- **T-G (schema versioning).** The migration that introduces the new columns is applied
  transactionally and guarded by the SQLite schema-version mechanism (`PRAGMA user_version`,
  see §11); the gate refuses to run against an unexpected version. *Refutation if* a gate
  decision depends on the database version rather than on the record.

**Scope of the claim.** H-CK1 is a claim about *label discipline and exact decidability of <!-- no-id: a local numbering, not a register identifier -->
validity*, not about the semantic quality of any generated text. It does not claim that the
inference rules are correct, that the source corpus is good, or that the store's concepts are
right. It claims only that (i) the store can always say how a record entered and whether its
recorded derivation is valid, (ii) those two facts are never conflated, and (iii) conflict is
represented rather than counted away.

**Implementation envelope.** SQLite with a versioned schema (`PRAGMA user_version` bump +
transactional migration); `EntailmentChecker` is pure arithmetic over the atom budget
(no clock, no RNG at decision time, no network); the differential test T-C uses a *seeded*
PRNG in the test harness only, never in the decision path.

---

## 10. Further hypotheses suggested by this research (out of scope for W41-C)

Listed to keep the backlog honest; **not** to be started in this wave.

- **B1 — Justified retraction (append-only epistemic log).** Implement R1–R3 of §2.3: the
  trusted layer becomes append-only; retracting a claim writes a `supersession` +
  `retraction_justification` row (AGM-style: removal is accompanied by a statement about the
  removal), and the store can replay the belief state at any logical point.
  *Deferred because* it presupposes H-CK1's labels and contradiction objects; a supersession <!-- no-id: a local numbering, not a register identifier -->
  schema written before the labels settle would be rewritten.
- **B2 — Scope-qualified contradiction (RIPPLE-style partial agreement).** Give every claim an
  `applies_under` scope; two claims whose scopes do not overlap yield `SCOPED_DISAGREEMENT`
  rather than a contradiction, and a scope conflict is resolved by *retracting the narrower
  scope*, not by choosing a winner.
  *Deferred because* it needs a scope vocabulary and a partial-agreement semantics whose
  canonical primary source I could **not** verify in this session (§12.2), and because it is
  testable only after B1 exists.
- **B3 — Nyāya-grade grounding of the connective.** Require every `ANUMANA` record's
  *connective* to be itself justified by a record that terminates in `PRATYAKSHA`/`SHABDA`
  (no circular grounding: a connective justified only by inferences is labelled
  `UNGROUNDED_CONNECTIVE` and refused).
  *Deferred because* it needs a base of accepted instances and a grounding rule, and because
  `UPAMANA` (analogy) has no deterministic operationalisation yet — this is the part of the
  Nyāya architecture that the machine cannot honestly claim to implement.

---

## 11. Primary sources (verified)

Format: author(s), title, year, venue, identifier. Verification status in §12.

### Truth maintenance and belief revision

1. **Doyle, J. (1979).** "A Truth Maintenance System." *Artificial Intelligence* 12(3): 231–272.
   DOI: [10.1016/0004-3702(79)90008-0](https://doi.org/10.1016/0004-3702(79)90008-0)
2. **de Kleer, J. (1986).** "An Assumption-based TMS." *Artificial Intelligence* 28(2): 127–162.
   DOI: [10.1016/0004-3702(86)90080-9](https://doi.org/10.1016/0004-3702(86)90080-9)
3. **Alchourrón, C.E., Gärdenfors, P., & Makinson, D. (1985).** "On the Logic of Theory Change:
   Partial Meet Contraction and Revision Functions." *Journal of Symbolic Logic* 50(2): 510–530.
   DOI: [10.2307/2274239](https://doi.org/10.2307/2274239)
4. **Gärdenfors, P. (1988).** *Knowledge in Change: A Theory of Belief Revision.* Cambridge
   University Press. (Pagination not verified.)

### Provenance

5. **Moreau, L., & Missier, P. (eds.) (2013).** *PROV-DM: The PROV Data Model.* W3C
   Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-dm/>
6. **Lebo, T., Sahoo, S., & McGuinness, D. (eds.) (2013).** *PROV-O: The PROV Ontology.* W3C
   Recommendation, 30 April 2013. <https://www.w3.org/TR/prov-o/>
7. **Cheney, J., Missier, P., & Moreau, L. (eds.) (2013).** *Constraints of the PROV Data Model.*
   W3C Recommendation, 30 April 2013.
   <http://www.w3.org/TR/2013/REC-prov-constraints-20130430/>
8. **Cheney, J. (ed.) (2013).** *Semantics of the PROV Data Model.* W3C Note, 30 April 2013.
   <http://www.w3.org/TR/2013/NOTE-prov-sem-20130430/>
9. **Gil, Y., & Miles, S. (eds.) (2013).** *PROV Model Primer.* W3C Note, 30 April 2013.
   <http://www.w3.org/TR/2013/NOTE-prov-primer-20130430/>
10. **Buneman, P., Khanna, S., & Tan, W.C. (2001).** "Why and Where: A Characterization of Data
    Provenance." In *Proc. 12th Int. Conf. on Database Theory (ICDT 2001)*, LNCS 2343,
    pp. 316–330. Springer. DOI: [10.1007/3-540-44503-X_2](https://doi.org/10.1007/3-540-44503-X_2)
11. **Cheney, J., Chiticariu, L., & Tan, W.C. (2009).** "Provenance in Databases: Why, How, and
    Where." *Foundations and Trends in Databases* 1(4): 379–474.
    DOI: [10.1561/1900000006](https://doi.org/10.1561/1900000006)

### Nyāya

12. **Gautama.** *Nyāya Sūtra*, with **Vātsyāyana**'s *Bhāṣya* and **Uddyotakara**'s *Vārtika*;
    trans. **Ganganatha Jha**, with notes from **Vācaspati Miśra**'s *Nyāyavarṇikā-tātparyatīkā*
    and **Udayana**'s *Tātparyaparīśuddhi*. First published serially in *Indian Thought*,
    vols. IV–XI (1912–1919); reprint, Delhi: Motilal Banarsidass, 1984, ISBN 0-89581-754-3.
    Full text: <https://archive.org/details/the-nyaya-sutras-of-gautama-with-english-notes-1-ganganatha-jha>
    (identifier `the-nyaya-sutras-of-gautama-with-english-notes-1-ganganatha-jha`).
    Locus for the four pramāṇas: Nyāya Sūtra 1.1.3 — *numbering corroborated but not read
    directly in this session*, see §12.2.

### Reification and ontology design

13. **Quine, W.V.O. (1960).** *Word and Object.* Cambridge, MA: MIT Press. §§25–26
    (abstraction from relations; the one-over problem).
14. **Goodman, N. (1951).** *The Structure of Appearance.* Cambridge, MA: Harvard University
    Press. (Location of the reification discussion not verified.)
15. **Motik, B., Cuenca Grau, B., Horrocks, I., Wu, Z., Fokoue, A., & Lutz, C. (eds.) (2012).**
    *OWL 2 Web Ontology Language Profiles (Second Edition).* W3C Recommendation,
    11 December 2012. <https://www.w3.org/TR/owl2-profiles/>

### Scientific model revision, contradiction, scope

16. **Duhem, P. (1906/1954).** *The Aim and Structure of Physical Theory.* Princeton University
    Press, 1954; translated by Philip P. Wiener from the 1906 French original.
17. **Quine, W.V.O. (1951).** "Two Dogmas of Empiricism." In R.S. Cohen (ed.), *Methods of
    Philosophy.* University of California Press. Revised version reprinted in *From a Logical
    Point of View*, 2nd ed., Harvard University Press, 1961, pp. 20–46.
18. **Lakatos, I. (1970).** "Falsification and the Methodology of Scientific Research
    Programmes." In I. Lakatos & A. Musgrave (eds.), *Criticism and the Growth of Knowledge.*
    Cambridge University Press.
19. **Kuhn, T.S. (1962).** *The Structure of Scientific Revolutions.* University of Chicago
    Press (first edition).
20. **van Fraassen, B.C. (1980).** "A Formal Approach to the Problem of Alternative Theories."
    In H. van Fraassen (ed.), *The Scientific Image: Essays in the Philosophy of Science.*
    Oxford: Clarendon Press. (Pages not verified.)
21. **Priest, G. (1979).** "The Logic of Paradox." *Journal of Philosophical Logic* 8(1): 219–241.
    DOI: [10.1007/BF00258428](https://doi.org/10.1007/BF00258428)

### Probability versus consistency

22. **Good, I.J. (1952).** "Rational Decision" (OUP record: "Rational Decisions"). *Journal of
    the Royal Statistical Society: Series B (Methodological)* 14(1): 107–114.
    DOI: [10.1111/j.2517-6161.1952.tb00104.x](https://doi.org/10.1111/j.2517-6161.1952.tb00104.x)
23. **Geman, S., & Geman, D. (1984).** "Stochastic Relaxation, Gibbs Distributions, and the
    Bayesian Restoration of Images." *IEEE Transactions on Pattern Analysis and Machine
    Intelligence* PAMI-6(6): 721–741.
    DOI: [10.1109/TPAMI.1984.4767596](https://doi.org/10.1109/TPAMI.1984.4767596)
24. **Dempster, A.P. (1967).** "Upper and Lower Probabilities Induced by a Multivalued
    Mapping." *The Annals of Mathematical Statistics* 38(2): 325–339.
    DOI: [10.1214/aoms/1177698950](https://doi.org/10.1214/aoms/1177698950)

### Entailment, validity, truth

25. **Boole, G. (1854).** *An Investigation of the Laws of Thought on Which are Founded the
    Mathematical Theories of Logic and Probabilities.* London: Walton and Maberly.
26. **Church, A. (1956).** *Introduction to Mathematical Logic, Part I.* Princeton University
    Press. §7 (Soundness Theorem). (Pagination not verified.)
27. **Henkin, L. (1949).** "The Completeness of the First-Order Functional Calculus."
    *Journal of Symbolic Logic* 14(1): 33–76. (Pagination not verified.)
28. **Henkin, L. (1950).** "Completeness in the Theory of Types." *Journal of Symbolic Logic*
    15(2): 81–91. DOI: [10.1007/BF01696781](https://doi.org/10.1007/BF01696781)

### Official technical documentation (repository constraints)

29. **SQLite.** *PRAGMA* documentation (`user_version`, `schema_version`).
    <https://www.sqlite.org/pragma.html#pragma_user_version>
30. **SQLite.** *The SQLite File Format* / *ALTER TABLE* (versioned schema migration).
    <https://www.sqlite.org/lang_altertable.html>

---

## 12. Verification status

### 12.1 Verified in this session (primary text or authoritative bibliographic record read)

- **Doyle 1979** — title, venue, volume/issue/pages 12(3):231–272, DOI resolved via publisher/
  catalogue records. ✔
- **de Kleer 1986** — venue, volume/issue/pages 28(2):127–162, DOI (DBLP record for
  *Artificial Intelligence* vol. 28 no. 2; note the frequently mis-copied 197–224 range is the
  *Problem solving with the ATMS* paper). ✔
- **AGM 1985** — title, *JSL* 50(2):510–530, DOI 10.2307/2274239 (multiple independent
  reference lists). ✔
- **PROV-DM / PROV-O / PROV-CONSTRAINTS / PROV-SEM / PROV-PRIMER** — full PROV-DM document read;
  all quotations and relation names in §3 and §5.2 taken from it. ✔
- **Buneman, Khanna & Tan 2001** — title form "Why and Where: A Characterization of Data
  Provenance", ICDT 2001, LNCS 2343, pp. 316–330, DOI 10.1007/3-540-44503-X_2. ✔
- **Cheney, Chiticariu & Tan 2009** — *Foundations and Trends in Databases* **1**(4):379–474,
  DOI 10.1561/1900000006 (DBLP + Edinburgh research portal). ✔
- **Nyāya — Jha translation** — front matter, preface, introduction and the text of Vārtika
  1-1-1 read from the archive.org full text; the three quotations in §4.2 and the *tarka*
  passage in §4.3 are verbatim from that scan. ✔
- **Priest 1979** — *Journal of Philosophical Logic* (not JSL) 8(1):219–241,
  DOI 10.1007/BF00258428. ✔
- **Geman & Geman 1984** — DOI 10.1109/TPAMI.1984.4767596 resolved to the article,
  PAMI-6(6):721–741, Nov 1984. ✔
- **Good 1952** — JRSS-B 14(1):107–114, DOI 10.1111/j.2517-6161.1952.tb00104.x. ✔
- **Dempster 1967** — *Ann. Math. Statist.* 38(2):325–339, DOI 10.1214/aoms/1177698950. ✔
- **Henkin 1950** — *JSL* 15(2):81–91, DOI 10.1007/BF01696781. ✔
- **OWL 2 Profiles (2nd ed.)** — full document read; all profile features quoted in §5.2
  verified verbatim (EL property chains; QL lacks property chains/transitivity/`SameIndividual`;
  RL supports chains; no n-ary production in any profile). ✔
- **Quine 1951/1961** — the 1961 Harvard UP reprint at pp. 20–46 confirmed. ✔
- **Lakatos 1970**, **Kuhn 1962**, **Duhem 1906/1954**, **Boole 1854**, **Church 1956** —
  bibliographic identity confirmed. ✔

### 12.2 UNVERIFIED — do not propagate without an independent check

| Item | Status |
|---|---|
| Nyāya Sūtra **1.1.3** as the locus of the four-pramāṇa enumeration | Sutra number corroborated by two independent secondary locators, but **not read directly** in the primary edition in this session. |
| Jha translation pagination for the quoted passages | Quotes verified verbatim; **page numbers not established**. |
| **Alchourrón & Makinson (1986)** "On the Logic of Theory Change: Incomplete Information and the Idea of Backup Expansion", *JSL* 51(3) | Cited in §2.2 as a concept only; **bibliographic details not verified**. |
| **Trakhtenbrot (1950)** on the undecidability of first-order validity | **Russian original title and pages not verified**; used in §8.2 only for the standard result. |
| **Ryan, M. & Borchert, G. (1998)**, "A simple algorithm for contradiction tolerance and reuse", CoDEAS'98, LNCS 1512 | **Not verified** — could not be located; it is the canonical "RIPPLE" scope/partial-agreement source and underpins B2. |
| **Martins, J.F.P. & Shapiro, P. (2007)**, "Using active logic for consistency management in scientific databases", *Knowledge Engineering Review* 12(1) | **Not verified**; dropped from the argument. |
| **Lewis, D. (1981)** "Suspiciousness", *PPR* 44 | **Not verified**; dropped from the argument (the abstention point is carried by van Fraassen 1980 and P5). |
| **McAllester, D. (1990)** "Resolution without Retraction", AAAI-90 | **Not verified**; dropped. |
| **Kagan, K. & Leiman, R. (1983)** "The Bayesian concept of consistency", *BJPS* 34(3) | **Not verified**; dropped (the point is carried by Good 1952 + Dempster 1967). |
| **Goodman 1951** — the specific location of the reification discussion | Book confirmed; **chapter/page not verified** (the load-bearing reification claim is additionally supported by Quine 1960 and by PROV-DM §2.2.1.2). |
| **Church 1956** — page of the Soundness Theorem | Section §7 cited; **page not verified**. |
| **van Fraassen 1980** — chapter pages | **Not verified.** |
| **Gärdenfors 1988** — pagination | **Not verified.** |
| SQLite `PRAGMA user_version` / `ALTER TABLE` semantics | Official documentation URLs cited; **wording not re-read in this session**. |

---

## 13. One-paragraph conclusion

The literature converges on three separations that MATRIX currently lacks: **warrant** (told vs.
inferred — Nyāya's *śabda* vs. *anumāna*), **validity** (a derivation may be valid and its
premises false — Church's soundness theorem; Nyāya's *tarka*), and **conflict** (a contradiction
belongs to a scope and a claim-set, not to a pair — Duhem, Quine, Kuhn, Lakatos). PROV-DM
supplies the storage grammar for the first, and explicitly licenses the n-ary reification that
the second requires. The one hypothesis this wave can afford to test is therefore not "add
provenance fields" but **H-CK1: make the two axes orthogonal, put a single gate in front of the <!-- no-id: a local numbering, not a register identifier -->
trusted layer, decide validity exactly by bounded model enumeration, and represent conflict
rather than counting it away** — falsifiable by seven in-repo tests (T-A…T-G) that need no LLM,
no network, and no wall-clock.
