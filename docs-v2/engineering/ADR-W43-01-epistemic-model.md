# ADR-W43-01 — The epistemic model of MATRIX

**Status:** accepted · 2026-11-02 · wave W43 · supersedes nothing
**Author:** the coordinator · **Decider:** the repository owner (see §9 for the
points that still need the owner)

---

## 1. The problem this decision solves

MATRIX already has a domain vocabulary: `Source`, `Claim`, `Evidence`,
`Representation`, `Concept`, `Derivation`, `KnowledgeRevision`, `ClaimStatus`,
`EvidenceRelation`, `PromotionStage`. It also has a working store, a workbench
and a retrieval path.

The problem is not the absence of classes. The problem is that **the classes
do not agree with each other about what a piece of knowledge IS**, and the
disagreements are invisible because they are spread across enums and records
rather than stated in one place:

- `ClaimStatus` has `UNKNOWN` but no `OBSERVED`, no `HYPOTHESIS` that means
  "a person asserted this", and no `INSUFFICIENT_EVIDENCE` that means "we
  looked and could not tell";
- `EvidenceRelation` has three values (`SUPPORTS`, `CONTRADICTS`,
  `CONTEXTUAL`) where the distinction between "the source does not bear on
  the claim", "the source bears on the claim and does not support it" and
  "the source qualifies the claim" is lost;
- `Evidence` carries an `int quality` — a number with no calibration, which
  is exactly the confidence-number-as-evidence the mission forbids;
- **there is no object for a case at all**: no positive example, no
  counterexample, no boundary case, no failed prediction. A counterexample
  therefore cannot exist as a first-class thing, and the mission's §5
  scenario is unimplementable without one;
- `Derivation` records the rule and the premises but not the goal, the
  retrieved evidence, the detected contradictions, the assumptions or the
  unresolved ambiguities — so a reader cannot see WHY a conclusion was
  reached.

An ADR is needed because the next step is a schema change. Changing the
schema first and deciding afterwards is how a repository ends up with two
status enums that mean different things.

---

## 2. The model

Six concepts, and what separates them.

```
   SOURCE ──────────► OBSERVATION ──────────► CASE
      │                                            │
      │ evidence                                    │ bears on
      ▼                                            ▼
   EVIDENCE ───────────────────────────────► CLAIM ──► INTERPRETATION
      ▲                                            │
      │                                            │ refined by
      │                                            ▼
      └──────────────── REASONING RESULT ◄──── REVISION
```

| Concept | What it is | What it is NOT |
|---|---|---|
| **Source** | Where information came from, and exactly where in it. | Not knowledge. Not a claim. |
| **Observation** | Something the system received or measured. | Not automatically a factual claim. |
| **Case** | A situation — observed or constructed — used to evaluate a model. | Not evidence on its own: a case bears on a claim *through* an evidence link. |
| **Claim** | A proposition that can be evaluated, in a stated scope. | Not a fact. A claim is supported, contradicted, or unresolved. |
| **Evidence** | A traceable relation between a source or observation and a claim, with a reason and a location. | Not a number. Not a confidence. |
| **Reasoning result** | A conclusion with its goal, premises, rules, evidence, derivation, contradictions, assumptions, scope limits and open questions. | Not the conclusion alone. |
| **Revision** | A non-destructive record of a change: what was believed, what contradicted it, what is proposed instead, why. | Not an overwrite. |

### 2.1 The four separations that matter

**Source ≠ Observation.** A source is a document or a feed. An observation is
what the system received or measured. A claim extracted from a source is
neither: it is the system's interpretation of the source.

**Claim ≠ Interpretation.** A claim is what the system says. An
interpretation is how the system read the source. When they disagree — a
claim whose `extractionMethod` says "manual" and whose `proposition` is the
operator's own sentence — the distinction is what keeps "the source says"
separate from "MATRIX says".

**Evidence ≠ Confidence.** `Evidence.quality` stays as a *ranking hint*, but it
is never the reason a claim is supported. The reason is the relation, the
excerpt and the location. A number with no calibration evidence must not be
presented as one; the mission's §14 forbids it, and this ADR demotes it.

**Counterexample ≠ Refutation.** A counterexample is a case that challenges a
claim. Whether it refutes it, reveals an exception, reveals a missing
condition, exposes a bad interpretation, or is itself ambiguous is a
**derivation**, and the derivation is what is stored.

---

## 3. Reuse: what already exists and is kept

The mission's §3 says to reuse existing domain objects where they genuinely
express the required semantics, and not to duplicate them for the sake of the
names. This is the mapping.

| Mission concept | Existing object | Verdict |
|---|---|---|
| Source | `io.matrix.knowledge.Source` | **REUSED and EXTENDED.** It already has `kind`, `title`, `locator`, `contentHash`, `publishedAt`, `language`, `authority`, `freshness`. It lacks the fields §3.1 asks for — see §4.1 |
| Claim | `io.matrix.knowledge.Claim` | **REUSED and EXTENDED.** `proposition` is the original wording, `scope` the scope, `context` the context, `warrant` the reasoning, `version` the version. It lacks modality, polarity, temporal scope and exceptions — see §4.2 |
| Evidence | `io.matrix.knowledge.Evidence` + `EvidenceRelation` | **REUSED and EXTENDED.** The relation is the substance; the vocabulary grows (§4.3) |
| Reasoning result | `io.matrix.knowledge.Derivation` | **REUSED and EXTENDED.** It has `ruleId`, `ruleName`, `premiseClaimIds`, `conclusionClaimId`, `status`. It lacks the goal, the retrieved evidence, the contradictions, the assumptions and the open questions — see §4.4 |
| Revision | `io.matrix.knowledge.KnowledgeRevision` + `RevisionKind` | **REUSED.** It is already non-destructive: `kind`, `summary`, `addedFacts`, `stillValidAssumptions`, `noLongerValidAssumptions`, `newlyExplainedCases`, `evidenceIds`. `RevisionKind` already distinguishes REFINEMENT, CORRECTION, SCOPE_CHANGE, CONFLICTING_SCHOOLS and INSUFFICIENT_EVIDENCE — this is very close to what the mission asks for |
| Case (example, counterexample, boundary, failed prediction) | **NONE** | **NEW.** §4.5 |
| Epistemic status | `io.matrix.knowledge.ClaimStatus` | **REUSED and EXTENDED.** The vocabulary grows (§4.2); the existing values keep their meaning |

**What is explicitly NOT duplicated:** no second claim store, no second
evidence table, no parallel "epistemic" model beside the existing one. The
mission's §5 forbids a second independent knowledge store, and the store
remains `KnowledgeStore`.

---

## 4. The schema changes

Each change below names the object, the change, and the reason. The store's
schema version moves from **15** to **16**, and the migration is additive: no
existing row is rewritten, because the mission's §3.7 requires that the
history of a claim survives its refinement.

### 4.1 `Source` — the location of the information

New optional fields: `author`, `license`, `importMethod`, `parserVersion`,
`documentPath`, `chapter`, `section`, `page`, `startOffset`, `endOffset`,
`extractionWarnings`, `supersededBySourceId`.

The content hash and the offsets are what make §5's "traceable from every
extracted claim to the original source span" checkable.

### 4.2 `Claim` — the vocabulary of epistemic status

`ClaimStatus` gains `OBSERVED`, `HYPOTHESIS`, `INSUFFICIENT_EVIDENCE` and
`REFINED`; `SUPERSEDED` and `REFUTED` keep their meaning. The mapping is
explicit and one-to-one:

| Mission status | `ClaimStatus` | Note |
|---|---|---|
| `UNKNOWN` | `UNKNOWN` | nothing is known and nothing has been looked for |
| `OBSERVED` | `OBSERVED` | the system saw it; it is not yet a claim's truth |
| `HYPOTHESIS` | `HYPOTHESIS` | an assertion is on record with no evidence yet |
| `SUPPORTED` | `SUPPORTED` | evidence with relation SUPPORTS |
| `CONTRADICTED` | `REFUTED` | evidence with relation CONTRADICTS, decisive |
| `CONTESTED` | `CONTESTED` | the sources disagree and both accounts are kept |
| `REFINED` | `REFINED` | superseded by a narrower claim, with the revision kept |
| `REJECTED` | `REFUTED` | the same decision, reached by a different route |
| `INSUFFICIENT_EVIDENCE` | `INSUFFICIENT_EVIDENCE` | the evidence does not decide |

`Claim` gains `modality`, `polarity`, `temporalScope`, `preconditions`,
`exceptions`, and `interpretationNote` (how the system read the source, kept
apart from the original wording).

**A status is per claim, and a claim has a scope.** A claim supported in one
context and contradicted in another is two claims with different scopes, not
one claim with two values. The repository already stores `scope`; §4.2 makes
it load-bearing.

### 4.3 `EvidenceRelation` — what the evidence does

`SUPPORTS`, `CONTRADICTS`, `CONTEXTUAL` are kept and joined by
`QUALIFIES`, `EXEMPLIFIES`, `FAILS_TO_SUPPORT`, `INSUFFICIENT`.

**The absence of evidence is not evidence of absence.** `FAILS_TO_SUPPORT` is
recorded only when the search was actually performed and the source was
found; `INSUFFICIENT` records that the evidence does not decide. Neither is
the same as "the claim is false".

### 4.4 `Derivation` — the reasoning result

New fields: `goal`, `evidenceIds`, `contradictedClaimIds`, `assumptions`,
`scopeLimitations`, `unresolvedAmbiguities`, `caseIds`, `direct` (whether
this conclusion is read off the evidence or derived from other conclusions).

`direct` is the mission's §3.5 distinction between direct evidence and a
derived conclusion, and it is the difference between "the source says X" and
"MATRIX concluded X from X".

### 4.5 `Case` — NEW, and the reason the mission's scenario was impossible

A new record `Case`:

```
Case(id, workspaceId, kind, claimId, proposition, conditions,
     observedOutcome, sourceId, evidenceId, createdAt)
```

with `CaseKind`: `POSITIVE_EXAMPLE`, `NEGATIVE_EXAMPLE`, `COUNTEREXAMPLE`,
`BOUNDARY_CASE`, `EXCEPTION`, `AMBIGUOUS_CASE`, `ADVERSARIAL_CASE`,
`FAILED_PREDICTION`, `SUCCESSFUL_PREDICTION`.

A counterexample names the claim it challenges (`claimId`) and the conditions
under which it does so (`conditions`). **That is what keeps "All birds fly"
and "Most birds fly under ordinary conditions" apart**: the penguin is a
counterexample to the first, and to the second it is a case that qualifies
rather than refutes — and the system decides which by reading `conditions`,
not by rewriting the claim.

### 4.6 `KnowledgeRevision` — the revision that does not erase

Already non-destructive. It gains one thing: `supersededClaimId`, so that a
revision points at the claim it replaces *and* the claim it produces, and
both stay readable. `RevisionKind.CORRECTION` and `SCOPE_CHANGE` are the
kinds the mission's scenario needs.

---

## 5. The learning cycle

The mission's §4 cycle, mapped onto the objects above:

```
OBSERVE → INTERPRET → CHECK_SOURCE → FORM_CLAIM → FIND_EXAMPLES
   → FIND_COUNTEREXAMPLES → TEST_MODEL → REVISE_OR_RETAIN → APPLY
   → EVALUATE → DELAYED_RECALL → CONSOLIDATE
```

Each stage produces a stored artefact or an explicit reason it could not
proceed. The artefacts already exist for most stages:

| Stage | Artefact | Already in the repository? |
|---|---|---|
| OBSERVE | `Source` | yes |
| INTERPRET | `Claim.extractionMethod` + `interpretationNote` | the method yes, the note new |
| CHECK_SOURCE | `Source.contentHash` + offsets | hash yes, offsets new |
| FORM_CLAIM | `Claim` | yes |
| FIND_EXAMPLES | `Case` | **new** |
| FIND_COUNTEREXAMPLES | `Case` | **new** |
| TEST_MODEL | `Derivation` with `contradictedClaimIds` | the derivation yes, the field new |
| REVISE_OR_RETAIN | `KnowledgeRevision` + a new `Claim` | yes, the linkage new |
| APPLY | `Representation` | yes |
| EVALUATE | `Case` of kind FAILED_PREDICTION | **new** |
| DELAYED_RECALL | the store's own retention | yes |
| CONSOLIDATE | `KnowledgeConsolidator` | yes |

---

## 6. Four outcomes, four treatments

The mission's §4 A-D, and what each one records.

**A. A successful example** → `Case(POSITIVE_EXAMPLE)`. The evidence link
says what it supports. It does **not** establish a universal rule: the
promotion policy (`PromotionPolicy.mayPromote`, already live and already
model-checked) is the place where "one example is not enough" is enforced,
and `VerifiedNeedsTwoIndependentSources` is the property that holds the line.

**B. A counterexample** → `Case(COUNTEREXAMPLE)` with its `claimId` and
`conditions`. The system then determines, and records in the derivation,
which of six explanations applies: it refutes the claim, reveals an
exception, reveals a missing condition, exposes an incorrect interpretation,
exposes an invalid inference, or is itself unreliable or ambiguous.
**The system does not choose automatically.** The six-way determination is
a derivation, and the derivation is inspectable.

**C. A failed prediction** → `Case(FAILED_PREDICTION)` carrying the original
prediction, the evidence available at decision time, the reasoning path, the
actual outcome, the difference and the suspected failure mode. The decision
context is preserved, because training on the outcome without the decision
context teaches the system to be right for the wrong reason.

**D. An incorrect reasoning method** → a `Case` of kind `NEGATIVE_EXAMPLE`
plus a `Derivation` that names the failure pattern. The catalogue the
mission lists is a **candidate list**, and only the patterns that can be
operationally defined and tested are implemented. The first three that can
be, and that this wave implements, are:

| Pattern | Operational definition | Test |
|---|---|---|
| overgeneralization from one example | a claim promoted to VERIFIED on one source | the promotion policy's refusal |
| affirming the consequent | a derivation whose conclusion does not follow from its premises | the entailment check on the derivation |
| scope error with negation | a refutation whose negated clause has a different subject | `PremiseChecker.denies`, already tested |

Each detected failure keeps a minimal reproducible case and a regression test.
**A failed approach is learning material; it is not proof that another
approach is right.**

---

## 7. What this ADR does NOT decide

The mission's §9 separates memory, structured learning and model training.
This ADR is about **structured learning** only.

- **No weights are trained.** No fine-tuning, no model parameter change, no
  "learning" from an imported book.
- **A user's correction is evidence about the user's intended meaning**, and
  at most a candidate claim. It is not automatically true.
- **No claim is promoted by a test passing.** A test is evidence about the
  implementation, not about the world.
- **No stored statement is automatically knowledge.** The mission's §3.2:
  "A stored statement is not automatically knowledge."

---

## 8. Consequences

**Positive.** The mission's §5 scenario becomes implementable: a case can
challenge a claim, the challenge can be recorded without erasing the claim,
a refinement can be proposed with its derivation, and the user can inspect
why the system retained, rejected or refined.

**Negative, and stated plainly.** `ClaimStatus` and `EvidenceRelation` change
— the store migrates to schema 16, and every consumer of those enums must
be checked. `Evidence.quality` is demoted, which means a consumer that used
it as a ranking signal will behave differently, and that consumer must be
found first.

**The cost of NOT deciding this.** Without a `Case` object, a counterexample
is a sentence in a dialogue turn; it cannot be stored, cannot be linked to a
claim, cannot be found later, and cannot be tested against.

---

## 9. What still needs the owner

| # | Point | Why it is the owner's |
|---|---|---|
| 1 | **The `Case` table and the schema-16 migration.** This is a schema change to the knowledge core, and it touches every consumer of `ClaimStatus` and `EvidenceRelation` | A schema change to the store is the kind of decision the mission's §16 reserves for the owner |
| 2 | **Whether `Evidence.quality` is removed or kept as an explicitly uncalibrated hint.** Removing it is a breaking change to every reader | It changes a public field's meaning |
| 3 | **Whether the six-way counterexample determination is decided by the system, proposed by the system and confirmed by the user, or proposed by the system and accepted automatically** | It decides who is responsible for a judgement |
| 4 | **The scope of `Claim.scope`** — a free-text field today; whether it becomes a structured scope (domain, conditions, temporal) | It is the difference between "scoped claims" as a claim and as a convention |

Until (1) is answered, this wave implements the slice **against the existing
schema** — a claim, a source, an evidence link with a relation, a revision —
and records the counterexample as an extension of the revision record rather
than as a first-class `Case`. That is a smaller thing than the mission
asks for, and it is stated here rather than presented as the whole.

---

## 10. The evidence behind this ADR

| Claim | Where |
|---|---|
| `ClaimStatus` holds 8 values, none of them `OBSERVED`, `HYPOTHESIS` or `INSUFFICIENT_EVIDENCE` | `matrix-core/src/main/java/io/matrix/knowledge/ClaimStatus.java:33` |
| `EvidenceRelation` holds 3 values | `matrix-core/src/main/java/io/matrix/knowledge/EvidenceRelation.java:4` |
| `Evidence` carries an `int quality` | `matrix-core/src/main/java/io/matrix/knowledge/Evidence.java:11` |
| `Derivation` has no goal, no evidence list, no contradiction list | `matrix-core/src/main/java/io/matrix/knowledge/Derivation.java:25` |
| `KnowledgeRevision` is already non-destructive and already has `RevisionKind.CORRECTION` and `SCOPE_CHANGE` | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRevision.java:15`, `RevisionKind.java:9` |
| `PromotionPolicy.mayPromote` is live and model-checked, and holds the "one source is not verification" line | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/PromotionPolicy.java:37`, `formal/PromotionStage.tla` (`VerifiedNeedsTwoIndependentSources`) |
| `KnowledgeRefiner` is on a production path (`KnowledgeWorkbench` → `KnowledgeStore`) | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java`, `KnowledgeStore.java` |
| The store's schema version is 15 and the store is in WAL mode with `synchronous=FULL` | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:57,82,83` |
| The premise checker already scopes negation to a clause with the premise's subject | `matrix-core/src/main/java/io/matrix/knowledge/PremiseChecker.java` (`denies`) |
| There is no class for an example, a counterexample or a failed prediction | a census of `matrix-core/src/main/java/io/matrix/knowledge/**` finds none |
