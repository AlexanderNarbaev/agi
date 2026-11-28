# CRJ-01 / EPISTEMIC-NECESSITY — the pre-registration

**Baseline:** `5402e8763579b7c785f91f9e03910403e36015be` · **Date:** 2026-11-28
**Status:** **written before the first measurement and before any tuning to the cases.**
**Milestone:** `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md` ·
**Case contract:** `docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md` ·
**Case set:** `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json`

> **What this document fixes, before any number exists.** The two rules that are compared, the
> inputs they are compared on, the slices, the oracle, the confusion matrix, every denominator,
> the convention for an undefined denominator, and the decisions the measurement may and may not
> make. Everything below is a commitment made in advance, and the milestone reports against it
> whether it likes the outcome or not.

---

## 1. The question

COMPETENCE-REACH §9 named the next scientific decision: **how often does the anchor admit a
scope that holds the claim the workspace's reading cites FIRST but not a claim the answer needs**
— and what does the claim-sufficiency rule's false-rejection rate cost on the same corpus.

**This milestone answers a different and prior question**, and it answers it because the previous
one turned out to be a question about the retriever rather than about the task:

> **What does a task actually require, and what happens when the evidence it requires is not
> present?**

Two consequences follow, and both are commitments:

1. **The unit is the task's prerequisites, not the reading's citations.** A prerequisite is
   something the task cannot be done without. A citation is something the reading happened to
   reach. The two are different, and the previous milestone's own rows showed the difference:
   the anchor's blind spot was not "two claims" but "a claim the reading reaches **later**".
2. **The answer to "what is missing" must be produced, not merely detected.** A diagnosis that
   names a missing claim but leaves the caller with no way to obtain it is not a repair. The
   milestone therefore carries a **typed acquisition request** on the served path, and a
   **durable** answer to the question "may this competence still be used" (the previous
   milestone falsified the durable retire and the durable narrow).

## 2. The two rules

Both rules decide the **same** question: *may this scope serve this task?* Both are computed
from the **workspace's own reading** of the task over the **same** retriever, the **same**
statistics view, the **same** bindings, the **same** question and the **same** view. Neither
rule sees a word, a concept, a domain, a case id or an oracle label.

### 2.1 Arm A — `ANCHOR` (the incumbent)

> The scope holds **the claim the workspace's own reading would cite first**.

Unchanged. It is the rule the served path applies today. Its reason strings are unchanged.

### 2.2 Arm B — `NECESSARY_PREREQUISITE` (the proposal)

> The scope holds **a sufficient set of the task's necessary evidence**.

Where:

- **the task's necessary evidence** is derived **experimentally** from the workspace, by
  **leave-one-source-out ablation** of the workspace's own reading of the task:
  1. the **reference answer** is the workspace's own reading of the task over the whole view;
  2. `P` is the set of propositions that answer states, and `D` is the set of sources that
     hold a claim stating a proposition of `P`;
  3. `S*` is the **inclusion-minimal** subset of `D` that still reproduces `P` — computed by
     dropping, one source at a time in a fixed order, every source whose removal leaves `P`
     intact;
  4. a claim of the reference answer is a **requirement** iff its source is in `S*` **and** its
     proposition is not stated by any claim of `S*` without that source — that is, iff the
     source cannot be dropped;
  5. a requirement whose proposition is **also** stated by a claim of another source of `D` that
     can carry it alone yields an **alternative sufficient set**: the requirement may be
     satisfied by that alternative instead.
- **a sufficient set** is a set of requirements that carries the whole of `P`.
- **the scope is adequate** iff it carries **every** requirement of **one** sufficient set.

**What arm B is not.** It is not a threshold: no coverage number decides it. It is not a
relevance rule: a claim's relevance is not a prerequisite. It is not a confidence rule: a
claim's authored confidence is carried **with** the requirement and never judges it. It is not
a provenance rule: the source a requirement comes from is carried **with** it and never judges
it. It is not an applicability rule: whether the scope may serve the task at all is the
**applicability** question, which the route decides separately and which this milestone does
not change.

### 2.3 The paired comparison

Arm A and arm B are run on **the same** scope, **the same** snapshot, **the same** bindings,
**the same** history and **the same** cases, in one process, and every row records the
**fingerprint of the view it was measured against**. The suite and the gate require the two
arms of a cell to carry **one** fingerprint. A pair measured against different views is a
defect, not a result.

**The arms are compared on the oracle's labels, and only on the oracle's labels.** The oracle
is the case set; it never enters production code.

## 3. The oracle

The case set is written by **independent case authors** under
`docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md`, which names no rule and no
implementation. If the independent authorship fails, the milestone says so, downgrades the
result to **implementer-authored DEV**, and attributes nothing falsely.

For a case `c` and a scope `S`:

```
oracle_adequate(c, S)  =
      c.answerable
  AND ( S carries every proposition of c.necessaryEvidence
        OR S carries one whole set of c.alternativeSufficientSets )
```

`S carries a proposition` iff `S` contains the source of the corpus document the proposition
is a sentence of. A case with `answerable == false` (a missing prerequisite, an unanswerable
question) is **never** adequate for any scope: the workspace cannot carry the whole answer.

**The oracle's labels, ids and propositions never reach production code.** The production
derivation sees only the workspace.

## 4. The slices

Three **disjoint** slices, as in the previous milestone:

| slice | role |
|---|---|
| `PROPOSAL` | the learning episode's own questions — the episode the competence is learned from |
| `VALIDATION` | the held-out questions the promotion is decided on |
| `EVALUATION` | the measured questions |

**A necessary proposition may not cross slices.** A proposition that is a gold of two slices puts
the validation's evidence into the learning episode. The suite asserts this and the milestone
reports every violation it finds rather than hiding it.

## 5. The measurement and its denominators

For every (case, scope, arm) the arm's decision is `ADMIT` or `REFUSE`, and the oracle's label
is `ADEQUATE` or `INADEQUATE`.

| | oracle ADEQUATE | oracle INADEQUATE |
|---|---|---|
| **ADMIT** | **TP** | **FP** |
| **REFUSE** | **FN** | **TN** |

Reported per arm, per condition, per domain and over the whole grid:

| quantity | formula | denominator |
|---|---|---|
| **FPR** | `FP / (FP + TN)` | the cases the oracle says are **inadequate** (the negatives) |
| **FNR** | `FN / (FN + TP)` | the cases the oracle says are **adequate** (the positives) |
| **FP/(TP+FP)** | the false discovery rate | the arm's **admissions** |
| **FN/(TN+FN)** | the false omission rate | the arm's **refusals** |
| `TP`, `FP`, `TN`, `FN` | raw counts | — |

**The two false rates are never folded together.** The FPR's denominator is the negatives and
the FNR's denominator is the positives; a rate whose denominator is empty is **UNDEFINED** and
is written as `UNDEFINED (0 denominator)` — never as `0.0` and never as `1.0`.

**Every case's own evidence and disposition are retained** — the case id, the condition, the
scope, the oracle's label, the arm's decision, the propositions the scope carried, the
propositions the scope did not, and the reason the arm gave. A confusion matrix without the
rows behind it is not evidence.

## 6. The conditions

The grid carries the conditions the previous milestone used, applied **identically to both
arms**:

| condition | what it does |
|---|---|
| `BASELINE` | the workspace as it is |
| `CHANGED_BINDINGS` | the production `recordLearningErrors` + `applyErrorDrivenDecay` path |
| `SUPERSEDED` | the production `selectSourceRevision` chain |

and the **case-level conditions** of the case contract — `LOWER_RANKED_NECESSARY`,
`JOINTLY_NECESSARY`, `ALTERNATIVE_SUFFICIENT`, `MISSING_PREREQUISITE`, `CONTRADICTION`,
`SUPERSESSION`, `CHANGED_BINDINGS`, `AMBIGUITY`, `UNANSWERABLE` — are the **shape of the
evidence**, and the milestone reports the confusion matrix **by condition** as well as over the
whole grid.

## 7. What the measurement may and may not decide

**It may** state: where the two arms differ, on which cells, with which denominators; which
arm admitted a scope the oracle calls inadequate; which arm refused a scope the oracle calls
adequate; and whether the difference survives the conditions.

**It may not** state:

- **no winner** is declared — the pre-registration fixes the comparison, not the outcome, and a
  rule with more false admissions on this corpus is not thereby the wrong rule;
- **no threshold** is proposed or changed;
- **no served default** is changed: the incumbent `ANCHOR` remains what the route applies unless
  a caller says otherwise, exactly as in the previous milestone;
- **no claim** about real data — the corpus is synthetic and test-owned;
- **no claim of generalisation or superiority** — the user-observable outcome (what the caller
  received) is reported separately from any transfer or generalisation statement;
- **no sealed evaluation** and **no new isolation probe** — the ADAPTIVE milestone's retained
  verdict on isolation is reused as it stands.

## 8. The stopping rules

1. The measurement stops when every `EVALUATION` case of every domain has been run under both
   arms and every condition, and every row is retained.
2. If a denominator is empty, the rate is **UNDEFINED** and the row says so. The measurement is
   not extended to manufacture a denominator.
3. If the two arms agree on **every** case of **every** cell, the comparison is **NOT
   ESTABLISHED** — on this corpus — and the milestone says so. It is not a failure and it is
   not evidence that the arms are equivalent.
4. If the independent case authorship fails, the milestone **records exactly what failed** and
   downgrades to implementer-authored DEV. It does not attribute authorship that did not happen.

## 9. The lifecycle the milestone must walk (P2–P4), on the served path

The measurement above is instrument-level. The milestone must additionally walk, **on the real
served route**:

1. an **unseen task** whose available competence fails — the failure is **observable** in what
   the caller received;
2. the **missing prerequisite**, named;
3. a **typed acquisition request** for it, produced by the served path, inside the existing
   authorisation — **no new endpoint, no new permission, no model call in the deterministic
   runtime**;
4. **authorised evidence** arriving through an existing authorised channel, and the **same task's
   disposition changing**;
5. **refusal** for evidence that is unauthorised, **conflicting** or **stale**;
6. **validation** of the revision on the held-out slice;
7. the **revision**, with its reason, its version and its causal link persisted;
8. after a **restart**, the **exact** revised procedure serving a **different** task, with an
   **independent oracle** for the effects;
9. a comparison against a **matched stateless reference**, **exact replay** and the **incumbent
   adaptive branches**, with the user-observable outcome kept separate from generalisation.

## 10. The durable lifecycle (P3)

Inside the canonical store's own contracts:

- **durable invalidation** — a retirement that survives a restart and prevents the stale
  selection;
- **validated narrowing/replacement** — a revision of a competence that is validated before it
  is written;
- **retirement retaining immutable historical evidence** — the retired version stays readable;
- **prevention of stale selection on restart**;
- **subsequent revalidation**.

**If a transition cannot be implemented inside the existing contracts**, the milestone retains a
**precise falsifier and a proposal** and does not assume the authority to make an architectural
change. The previous milestone falsified the durable retire and the durable narrow; this
milestone either repairs them inside the contracts or re-falsifies them with the same rigour.

## 11. The commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityServedTest"
```

> **`--rerun` is mandatory.** The case set and the corpus are not declared inputs of the test
> task, so Gradle considers the task up to date and keeps the **previous** XML. This happened
> twice in the previous milestone; every retained XML must be produced with `--rerun`.
