# DESIGN-70 — Evidence-Grounded Reasoning and the Three Epistemic Axes

**Status:** normative design · RECON-W41 · 2026-11-01
**Author:** Coordinator, RECON-W41
**Supersedes:** nothing. **Amends:** the epistemic model of `io.matrix.knowledge`.
**Builds on:** CONSTITUTION Articles I, IV and VI; `docs-v2/engineering/W41-REALITY-AUDIT.md`;
`docs-v2/research/W41-RESEARCH-A-LEARNING-SCIENCE.md`,
`W41-RESEARCH-B-COGNITIVE-SYSTEMS.md`, `W41-RESEARCH-C-KNOWLEDGE-ENGINEERING.md`
**Hypotheses:** H-100 (axes), H-101 (entailment), H-102 (teachability validity)

---

## 0. What this document is

The record of an architectural decision that three independent research
streams converged on, and of the measurement discipline that follows from it.

It is **not** a claim that MATRIX now reasons well. It is not: the reasoning
engine is at its first increment, the second domain does not exist yet, and
the teach-back is still the containment measurement RECON-W40 built. What
this document fixes is the *shape* of the epistemic model and the *contract*
each measurement has to satisfy, so that the later increments cannot quietly
reintroduce the defect RECON-W41 found.

## 1. The defect this wave found

RECON-W40 shipped three numbers under measurement-like names:

1. the answer's `confidence` — the mean of the matched claims' **authored**
   confidence field times the lexical coverage, with no calibration data
   anywhere in the repository;
2. the teach-back's `gain` — measured by a learner that **ignores the
   question** and returns the concatenation of the lesson, so the score is
   the fraction of the item's key terms present in the lesson;
3. the capability `stage` — derived from key-term coverage.

None was a probability of correctness, and the interface did not say so.

The W41.0 audit recorded this in `W41-REALITY-AUDIT.md` §3. The three
research streams then independently asked the same question of their own
literatures and reached the same conclusion:

- **Stream C** (knowledge engineering): the claim's warrant (pramāna), the
  validity of its derivation, and the state of the conflict are three
  **orthogonal** axes. "Any summary in which these axes are merged into a
  single number should be rejected."
- **Stream B** (cognitive systems): the authoritative support of a claim must
  be **derived at the point of use** from the premises actually used, with
  an expectation and an observation per (claim, frame) — the error-gated
  update of Sutton & Barto's "Deadly Triad" argument, applied to MATRIX's own
  components.
- **Stream A** (learning science): the W40 teach-back fails **seven of seven**
  published validity requirements, and the AHRQ teach-back guidance names its
  exact anti-pattern: "If they parrot your words back to you, they may not
  have understood."

## 2. The three axes

A claim now carries three independent facts about its standing.

| Axis | Type | Question it answers |
|---|---|---|
| `ClaimStatus` | layer | Which epistemic layer is this claim in? |
| `ClaimWarrant` | warrant | What kind of thing established it? |
| `DerivationStatus` | validity | Is an inference from its premises sound? |

`ClaimWarrant` follows Nyaya's four pramanas because the distinction the
mission needs is exactly Nyaya's: *śabda* (testimony) is not *pratyakṣa*
(perception), and an inference (*anumāna*) is a third thing again. The Latin
names are recorded because this repository's doctrine attaches a research
finding to a primary source rather than to a mood.

**Why the separation is not cosmetic.** A derivation can be **valid** while
its premises are **false**, and it can be invalid while everything it
started from was true. Nyaya's *tarka* — "the consideration of validity and
invalidity" — is a separate instrument precisely because the question of
whether a conclusion follows is not the question of whether its premises
hold. A store that collapses the two lets a system believe what it has not
established.

**What this forbids.** No axis may be summed, averaged or otherwise folded
into a single number that is presented as a probability of truth. The
support score (§4) is a named heuristic with its inputs disclosed; it is not
a probability, and it says so.

## 3. The recorded derivation

An inference that leaves no record cannot be shown. `Derivation` names the
rule, its premises in order, its conclusion, the connective that joined
them, and the status the checker returned. The store validates before it
writes: the conclusion and every premise must be claims of the workspace,
and a one-premise connective must have exactly one premise.

The rule about one-premise connectives is not pedantry. The checker's
agreement test against an independent reference implementation over 1000
random theories failed 12 times — because the reference used a `HashMap`,
which does not keep the premise order, and the order decides which premise
`IMPLIES` and `NOT` read. The checker was right and the reference was
wrong. The fix was to say what the one-premise connectives mean, and to
refuse the ambiguous rule rather than guess.

## 4. The entailment checker

`EntailmentChecker` decides entailment exactly, by a bounded enumeration of
ground propositional models. The atoms are claim identifiers, a fact is an
atom asserted true, a rule is a recorded derivation, a model is an assignment
satisfying every fact and every rule, and the goal is entailed when every
such model satisfies it.

The contract:

- the answer is `DERIVED_VALID`, `DERIVED_INVALID` or `UNDECIDED` — never a
  score, never a count of supporting rules, never a timestamp, never a
  network call;
- `UNDECIDED` is a real answer, returned when the theory exceeds the atom
  budget (2^n valuations, default budget 16);
- validity says nothing about the truth of the premises. That is what the
  premises' own warrants are for.

The mission's §6.3 is the reason for the strictness: "A BIR execution is not
automatically a correct interpretation of the premises."

## 5. The layer separation in the store

The mission's §5.1 asks for ten information layers. What the store now holds:

| Layer | Where it lives |
|---|---|
| source material | `source`, with the version, the publication date and the content hash |
| candidate claims | `claim` with `ClaimStatus.CANDIDATE` |
| validated claims | `claim` with `SUPPORTED` / `VERIFIED` |
| hypotheses | `claim` with `ClaimStatus.DRAFT` and no source behind it |
| disputed claims | `claim` with `CONTESTED`, and the evidence that disputes it |
| active representations | `representation`, with its scope and its assumptions |
| episodic records | `asked_question` in the store; `HierarchicalMemory.EPISODE_TAG` in the brain |
| learning records | `assessment_outcome` |
| executable model artifacts | `derivation` — the rules, recorded |
| procedural skills | **not yet** — the open gap |

And the rule the mission states in §5.1 is now enforced in the brain: a
generated answer is stored as an **episode**, marked with
`HierarchicalMemory.EPISODE_TAG`, and the retrieval that builds the context
a generation is conditioned on excludes those records. The context a
generation sees is what the system has **learned**, not what it has **said**.

## 6. The provenance contract

§5.2 lists eleven provenance fields. The store now keeps all of them:

| Field | Where |
|---|---|
| source identifier | `claim.introducedBySourceId` |
| source version or content hash | `source.contentHash`, `source.publishedAt` |
| relevant excerpt or location | `evidence.excerpt`, `source.excerpt`, `source.locator` |
| source date | `source.publishedAt` |
| ingestion time | `source.addedAt`, `claim.createdAt` |
| extraction mechanism | `claim.extractionMethod` |
| validation method | `claim.validationMethod` |
| related concepts | `claim.conceptId`, `representation.prerequisiteConceptIds` |
| contradictory claims | `evidence.relation = CONTRADICTS` |
| current validity status | `claim.status` |
| applicable domain and scope | `claim.scope`, `claim.context` |

What cannot be recovered is marked unknown, never invented: the migration
that upgrades an older database recovers the source's content hash from the
stored excerpt, and marks the extraction and validation methods
`unknown: the record predates the provenance fields`.

## 7. The measurement contract for what comes next

The audit's third finding was that three heuristics had measurement-like
names. The contract for every number the Workbench shows from now on:

1. **a named scale** — "support score", "coverage", "gain", "stage" — never
   "confidence" unless calibration exists;
2. **its inputs disclosed** — the `SupportScore` record decomposes the
   score and names what was not computed;
3. **an honest unmeasured list** — the metrics report already ends with six
   explicitly unmeasured metrics, and it stays that way;
4. **no single intelligence score** — the mission forbids it and the
   dashboard must keep separate dimensions;
5. **a demonstration described as a demonstration** — a sample of 11 items
   is a demonstration, not a generalisation.

## 8. What is deliberately NOT in this document

- The reasoning engine's full pipeline — it is the next increment, and its
  shape is constrained by §2–§4 above rather than fixed here.
- The second domain — the mission's §10 asks for Boolean logic as an
  independent test of the engine; the pack is authored, not assumed.
- The rebuilt teach-back — research stream A specifies it, and the W41.5
  increment must author the adversarial item pack **before** writing the
  probe, or the experiment is not testable.
- The formal models for the new state transitions — the mission's §13 asks
  for them where the semantics are stable, and the semantics of the three
  axes are now stable enough to model.
