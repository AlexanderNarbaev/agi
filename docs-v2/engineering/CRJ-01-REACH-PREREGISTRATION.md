# CRJ-01 / COMPETENCE-REACH — the pre-registration of the reach comparison

**Status:** `CANONICAL` · **Written:** 2026-11-27, **before** any arm of this milestone was
measured and **before** the case set was read · **Baseline:**
`7e20e9d9f9945066ebc0120f8e5b379f255ef266`

**Why this document exists.** The ADAPTIVE-COMPETENCE milestone ended with three open decisions
(its §9). Two of them are answered here, and both are questions whose answer was previously
argued rather than measured: **how a competence's scope is obtained**, and **which rule decides
whether that scope may serve a question**. A comparison whose arms, cases, conditions, measures
or decision rule are chosen after the outcomes are visible is a description of what happened.
This document fixes all of them **first**.

**Nothing in this document is a result.** It contains no count, no timing and no verdict. The
results live in `docs-v2/engineering/evidence/crj01-reach/` and in the milestone's document.

**The standing constraints, carried from the directive and from the prior milestone.**

- The **sealed evaluation is NOT EXECUTED**. The ADAPTIVE milestone's retained isolation verdict
  (`SEALED_NOT_FEASIBLE` on this executor) is reused **as it stands**; the isolation is **not
  re-probed** here.
- The **H2 and ADAPTIVE experiments are not repeated** and no benchmark is scaled.
- **No universal threshold** is introduced and **no winner is named in advance**.
- The **candidate scope strategy is experimental**: the milestone does not adopt one.

## 1. The question

> **What is a competence's REACH?** — that is, (a) which documents a competence's scope should
> consist of, and (b) which rule decides whether that scope may serve a question asked.

The prior state, read from `CRJ-01-ADAPTIVE-COMPETENCE.md` §9:

1. The support set is the unit of competence, and it is currently the union of the sources the
   **learning episode cited** — so an **incidental citation widens** it and makes the procedure
   applicable to questions it must refuse.
2. The criterion — the **anchor** — was chosen because the tie rule was *measured* to be wrong,
   but it was **never compared with a competitor**. It has a known blind spot: it asks whether
   the scope holds the claim the reading would cite **first**, so a question whose answer needs a
   **second necessary claim** can be admitted on the first one alone.
3. The anchor's coverage numbers are **snapshot-dependent** under weakened Hebbian bindings, so
   the two readings it compares need not be scored from the same state.

⇒ This milestone compares **three scope strategies** against **two criteria**, on the same cases,
with the same inputs, and reports the difference per cell.

## 2. The corpus and the cases

- **Two** synthetic DEV domains on **two different technical subjects**, authored by an
  **independent case author** that was given the corpus contract and the oracle contract and
  **nothing else** — no proposed criterion, no scope strategy, no implementation, and no access
  to the prior milestone's corpora, cases, criteria or tests. The corpus is **synthetic**, so the
  milestone owns its bytes outright.
- The case set is split into **three disjoint slices**:
  - **PROPOSAL** — the learning episode's own cases. A competence may only be learned from these.
  - **VALIDATION** — the held-out cases the promotion is decided on. They are disjoint from the
    PROPOSAL slice by identity and by question, and the learner refuses a validation case that is
    not held out.
  - **EVALUATION** — the cases this milestone measures. Disjoint from the other two.
- The oracle is the case author's own label: for every case, the **minimal set of claims a
  correct answer must cite**, the **dispositions that count as correct**, and the
  **corroboration / contradiction** conditions. The oracle is **never** consulted by an arm; it
  is read only to score what an arm did.

## 3. The arms — six, and they are paired

**Two axes, both fixed here.**

| axis | values | what a value means |
|---|---|---|
| **scope strategy** | `CITATION_DERIVED`, `DECLARED`, `EVIDENCE_DERIVED` | where the candidate scope's boundary comes from (below) |
| **criterion** | `ANCHOR`, `CLAIM_SUFFICIENCY` | the rule that decides whether the scope may serve a question |

⇒ **six arms** = 3 × 2.

**The three scope strategies, defined operationally.**

| strategy | the candidate scope is | what it refuses |
|---|---|---|
| `CITATION_DERIVED` | the union of the sources the **observed journeys cited** (the incumbent) | nothing extra |
| `DECLARED` | the documents the competence **declares** it is about; the declaration **bounds** the learning, so a citation the episode made outside it is not learned and can never widen the scope | nothing extra — a declaration that is too narrow is caught by the validation, not by a rule |
| `EVIDENCE_DERIVED` | the sources carrying the claims the workspace's **own reading reaches** for the observed questions | nothing extra |

**The two criteria, defined operationally.** Both are computed from the two readings' **own**
numbers — the same retriever, the same statistics view, the same question, the same coverage
floor, the same weakened bindings:

| criterion | the scope may serve the question when |
|---|---|
| `ANCHOR` | the scope holds **the claim the workspace's own reading would cite first** (the incumbent) |
| `CLAIM_SUFFICIENCY` | the scope holds **every claim the workspace's own reading would cite** |

Both refuse when the workspace's own reading reaches no citable claim, for the reason the
incumbent already gives.

**The scope is subject to validation under every strategy.** Each of the three candidate scopes is
put through the **same** held-out validation the learner already performs (the reference must be
reproduced on **every** held-out case, and the scope's sources must still be applicable). A scope
that does not survive is **not** promoted, and the milestone records the refusal and its reason.

**The pairing.** Within a strategy, the two criteria see the **same scope**, the **same cases**
and the **same inputs**, so their difference is attributable to the criterion and to nothing
else. Across strategies, the scopes differ by construction — that is the factor under test — and
the **cases** are still the same, so the rows are paired on the case.

## 4. The inputs that are held identical across all six arms

Every arm of a domain reads **the same**:

- the **workspace snapshot** — captured once, before the grid runs, and passed to every arm;
- the **Hebbian bindings** — the workspace's stored weakened bindings, read once per snapshot;
- the **history** — the same source-revision selections, the same dialogue state, the same
  request identity;
- the **coverage floor**, the **statistics view** and the **question resolution** (the
  conversation-expanded question), because the readings compute their own.

**Why this matters.** The ADAPTIVE milestone's §9 named the assumption the anchor embeds: *"a
claim's coverage is snapshot-dependent under weakened bindings"*, so the two readings it compares
need not be scored from the same state. Holding the snapshot and the bindings identical across
the six arms removes that as a **confounder** between the arms; the binding sensitivity is then
measured **as a condition** (§5), applied identically to all six.

## 5. The conditions

The grid is run under **three workspace conditions**, and every condition is applied to **all six
arms identically**:

| condition | what changes | what it is for |
|---|---|---|
| `BASELINE` | nothing | the reference state |
| `CHANGED_BINDINGS` | the workspace's stored Hebbian bindings are weakened through the **production** path (verified learning errors → error-driven decay) | the anchor's snapshot-dependence, measured with the confound removed |
| `SUPERSEDED` | one document of the domain is superseded through the **production** source-revision path | what a scope means when the evidence under it is replaced |

The **case kinds** are the second axis of the case set and are reported as a breakdown within
each cell: `PLAIN`, `PARAPHRASE`, `MISLEADING_RELEVANCE`, `MULTI_SOURCE`, `AMBIGUOUS`,
`INCOMPLETE`, `UNSUPPORTED`, `CONFLICT`, `CORROBORATED`. The kinds that carry the milestone's
sharpest question are `MULTI_SOURCE` (a **second necessary claim** — the anchor's blind spot),
`AMBIGUOUS` (two defensible readings), `CONFLICT` (two sources that contradict), `CORROBORATED`
(one proposition, two sources) and `MISLEADING_RELEVANCE` (a decoy that is lexically closer to
the question than the gold).

## 6. What is measured — and the two errors with their own denominators

**Per case, per arm, one row is retained.** Every row is kept, including the rows where nothing
went wrong; a milestone that retains only its failures cannot show the denominator.

**The two errors.**

- A case is **answerable** when the oracle says the workspace holds the evidence the question
  needs.
- A scope **carries** a case when it holds the source of **every** claim in the oracle's
  necessary evidence of that case.
- **FALSE ADMISSION** — the arm **admitted** the case (its criterion said the scope was
  adequate) **and** the served answer was not acceptable:
  - an **answerable** case whose scope did **not** carry the necessary evidence (the served
    answer was degraded), or whose served disposition was not among the allowed ones;
  - a **non-answerable** case (`INCOMPLETE`, `UNSUPPORTED`) that was answered where the oracle
    allows only `QUALIFIED` or `ABSTAINED`.
  - **Denominator: the number of ADMISSIONS in that cell and arm.**
- **FALSE REJECTION** — the arm **refused** the case, the case is **answerable**, and the scope
  **did** carry the necessary evidence: the refusal cost a transfer the scope could have made.
  - **Denominator: the number of REFUSALS in that cell and arm.**

⇒ The two denominators are **separate and are reported separately**. A rate that folded them
together would let a change in the mix of refusals masquerade as a change in the rule's quality.

**The other measures.**

| measure | what it is |
|---|---|
| **evidence completeness** | the share of the oracle's necessary claims the served answer cited |
| **fallback** | whether the arm fell back to the reference, and the reason it recorded |
| **coverage** | the relevance coverage the served answer's claims reached |
| **revision effectiveness** | whether a promoted revision changed what the competence could answer, measured on a task disjoint from the proposal and the validation |
| **full cost** | the path's cost, counted as **selection** (the criterion's evaluation) + **read** (the reading that produced the answer) + **fallback** (the reference reading a refusal led to) |
| **proposal and validation cost** | counted **separately** from the per-request cost: the cost of obtaining the candidate scope, and the cost of the held-out validation. They are **not** part of the per-request path and are never added into it |

**No timing assertion is made and no cost grid is built.** The costs are **recorded** — the
prior milestone's cost grid already exists and is not replaced, and a second grid of the same
shape would be a second claim about the same thing. The numbers are machine state; a faster
answer is not evidence of competence.

## 7. The decision rule — fixed here, before the numbers

**The cell** = one (domain, condition). Each cell reports, for each of the six arms: the
admissions, the refusals, the false admissions with their denominator, the false rejections with
their denominator, the evidence completeness, the fallback count and the coverage.

**The verdict of a cell** is one of exactly two:

- **`DIFFERENCE OBSERVED`** — the compared quantities differ on that cell. The direction and the
  magnitude are named, and the paired per-case rows are retained so that a reader can see which
  cases carried the difference.
- **`NOT ESTABLISHED`** — the compared quantities do not differ on that cell.

**There is no verdict of superiority, and no aggregate.** Specifically:

- **No winner is named**, and none is implied by the order of the arms.
- **No universal threshold** is introduced: no minimum grounding rate, no minimum coverage, no
  minimum completeness, no minimum agreement. A cell's difference is reported as it is.
- **No aggregate over the cells** is computed, and no cell is weighted by another. The prior
  milestone's pre-registration forbade a grid-level verdict for the same reason: a verdict that
  appears only after the grid is seen is a description of the grid.
- **The structural consequence of the criteria is stated, not decided.** `CLAIM_SUFFICIENCY`
  requires the scope to hold every cited claim, so every scope it admits, `ANCHOR` admits too:
  the alternative is **strictly stronger**. It therefore cannot admit more degraded answers than
  the anchor, and it may refuse more scopes that could have answered. Which of the two losses is
  larger is what the cells show; this document does not say.

**The scope strategies are not ranked by this document.** The cells show what each strategy's
scope does; whether a strategy should be adopted is a **governance** question about what a
competence is, and it stays with the owner.

## 8. The lifecycle transitions that are pre-registered, and what is expected of them

The milestone exercises the **existing** contracts — the served planner, the authorization
(the quarantine list), the procedure record, the revision and the store. The transitions that
are pre-registered, and what the code is expected to support:

| transition | expected support | what happens if it is not supported |
|---|---|---|
| a **failure** is observable on the served route | supported | — |
| a **diagnosis** names the missing evidence | supported | — |
| a **versioned proposal** is inspectable | partially: the candidate record is inspectable **in the outcome**, but no proposal store exists | the gap is **stated**, and the concrete alternative is retained |
| a **separate validation** on a disjoint subset | supported | — |
| a **promotion** | supported | — |
| a **rejection** with a precise reason | supported | — |
| a **retire** of a promoted competence in the **durable** store | **not supported**: the store has no invalidation operation | the transition is **explicitly falsified** and the concrete alternative is retained |
| a **narrow** of a promoted competence's scope | **not supported** as an operation: a narrower scope is a **different identity** (`idFor(support)`), and retiring the predecessor needs the retire above | the transition is **explicitly falsified** and the concrete alternative is retained |

**"Explicitly falsified" means** the milestone shows the refusal with the code's own words — the
operation that would be needed, and the fact that it does not exist — and it does **not**
substitute a different mechanism and call it the same thing.

**After the restart**, the **exact** method that was promoted (or the exact candidate that was
rejected) is shown on a **genuinely different question**, disjoint from the proposal slice and
from the validation slice.

## 9. What is NOT measured, and NOT claimed

- **No sealed evaluation.** It is **NOT EXECUTED**, and the milestone is **DEV-only**.
- **No superiority claim** of any kind: not for a criterion, not for a strategy, not for a cell.
- **No generalisation** beyond the two synthetic domains and the corpus shape they have.
- **No timing claim**: no latency is asserted by any test, and the cost counts are records.
- **No adoption** of a scope strategy: the incumbent stays the default.
- **No new architecture, store or infrastructure.** The criterion is a parameter on an existing
  verdict; the rule a competence is judged by travels inside the applicability field the record
  already carries.

## 10. Amendments to this pre-registration — dated, and before any measurement

**One amendment was made, and it is recorded here rather than silently applied.**

- **The `DECLARED` strategy was first written to REFUSE an observed episode that cited a source
  the declaration does not name.** It was changed to **BOUND** the learning instead: the
  declaration is the scope, and a citation outside it is not learned. The reason is structural
  and was found while the implementation was being written: a refusing rule makes the declared
  arm **empty** in any domain whose learning episode cited an incidental source — which is
  exactly the situation this milestone exists to measure — and an arm with no scope cannot be
  compared with anything. The guard against a too-narrow declaration is not removed by the
  change: it is the **validation**, which asks whether the restricted reading still reproduces
  the reference on every held-out case, and which refuses a scope that cannot.
  **No arm had been measured when this amendment was made**, and the amendment changes neither
  the arms, nor the cases, nor the conditions, nor the measures, nor the decision rule.
