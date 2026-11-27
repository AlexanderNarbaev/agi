# CRJ-01 / ADAPTIVE-COMPETENCE — the pre-registration of the cost grid

**Status:** `CANONICAL` · **Written:** 2026-11-27, **before** any cost of this milestone was
measured · **Baseline:** `dff12b5883242b74c09cc2388be32061c2b87cf0`

**Why this document exists.** The H2 milestone's own disposition says what must NOT happen:
*"re-tuning the same recogniser, re-running the same 12 cases, or reading this milestone's
negative as 'the mechanism does not work'"*. A cost measurement whose grid, sample count,
order and decision rule were chosen **after** the costs were seen is not a measurement of a
hypothesis — it is a description of what happened. This document fixes those things **first**,
so that the numbers this milestone produces are read against a rule that did not move.

**Nothing in this document is a result.** It contains no timing, no count and no verdict. The
results live in `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json` and in the milestone's
document; this document says what they will be compared against.

## 1. The question

> On the path the product really serves, **what does a learned, versioned procedure cost when
> its applicability is decided from the evidence and for the question asked** — and does the
> advantage the H2 milestone could not establish survive the correction the H2 milestone named?

The H2 milestone left the question in this exact form: the correction that keeps a restricted
reading comparable with the reference *"buys comparability, and it pays for it in one
tokenisation pass per reading"*, and *"whether that pass can be made once per workspace instead
of once per reading is the natural next engineering question"*. This milestone answers that
engineering question (the statistics are now reusable per **workspace and view identity**)
and asks the question it creates: **what does the applicability gate cost on top?**

## 2. The corpus and the cases

- **Two** synthetic DEV domains, on **two different technical subjects**, authored by an
  **independent case author** from the production extraction's own report
  (`docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json`). The implementer does not see
  the gold before the mechanism is frozen; the oracle (`SourceSpanOracle`) re-reads the corpus
  bytes and never calls the retriever.
- The **matched cases** of the cost grid are the **NOVEL** cases of each domain: five per
  domain, each with its gold inside the domain's support documents. The **OUT_OF_SUPPORT**
  cases (two per domain) are the **correctness controls** — they are the cases the gate must
  refuse — and they are measured for cost **only** in the wide cells, where the reference has
  an answer to give.
- The **OBSERVED** and **VALIDATION** cases are the learning episode's and the promotion's;
  they are not measured for cost.

## 3. The grid

**Two axes, both fixed here.**

| axis | values | what a value means |
|---|---|---|
| **corpus size** | `narrow`, `wide` | `narrow` = the workspace holds the domain's **support documents only**; `wide` = the workspace holds **all** of the domain's documents, so the support is a small, discriminative part of a large corpus |
| **support fraction** | `1/n`, `2/n` | the number of documents the procedure's support set covers, as a fraction of the cell's corpus (`n` = the cell's document count) |

⇒ **eight cells** (2 domains × 2 sizes × 2 support fractions). Every cell is measured on the
**same matched cases** of its domain — the five NOVEL cases — so the comparison inside a cell
is **paired**.

**The size axis is what the H2 milestone asked for.** Its corpus was four documents and the
support two of them, so the saving a narrower reading can win was small in absolute terms and
the corpus could not have the shape the question needs. `narrow` is the shape H2 had; `wide`
is the shape the next experiment must have.

## 4. The arms, the order and the sample count

Per cell, per matched case, in the **order the cases appear in the split**:

| # | arm | what it runs |
|---|---|---|
| 1 | **A — reference** | the stateless reference, **forced** (`report.journey.reference.v1`) |
| 2 | **C — procedure** | the learned procedure, **forced** — the support-restricted reading |
| 3 | **S — served** | the **real served path**: the kernel's selector chooses on its own evidence |

and, in the `wide` cells only, a fourth arm:

| # | arm | what it runs |
|---|---|---|
| 4 | **F — fallback** | the served path on an **OUT_OF_SUPPORT** case: the gate refuses the procedure and the reference answers |

**The sample count** is the number of matched cases in the cell (five), and the **same** five
in every arm of that cell. **There is no repetition of a case to reduce the noise**: the
measurements are paired, and the pairing is the unit of the uncertainty.

**The order is fixed** and is the order above, because a measurement whose order is chosen
after the fact is a measurement of the order.

**Warm and cold.** Each arm runs each case **twice**: the **first** pass is the **cold** one
(the statistics cache does not hold this view yet, so the pass over the claims is paid in
full) and the **second** is the **warm** one (the cache holds the view, so the pass is not
paid again). Both are recorded; **the warm one is the one the decision rule uses**, because
the cold one measures the cost of building a cache, which is a cost of the first question
after a restart and not of the path.

**Revision invalidation.** After the workspace's view is changed by a source revision, the
next reading is **cold again** — the view's identity changed and the statistics of the older
view must not be served for it. That reading is measured and recorded as its own row.

## 5. What is measured

For every row: the **wall time** and the **CPU time** of the reading, the number of claims
the answer cited, the **method that actually ran** (read from the journey's own trace, not
from the code that ran it), the **fallback reason** when there was one, the **evidence** the
answer cited, and whether the **effect** was verified by the independent reader.

The cost that is compared is the **absolute** cost of the whole path: selection, statistics,
validation and fallback. A path that is cheap because it did less work is not cheap if it
answered a different question, so the rows carry the grounding verdict too.

## 6. The decision rule — fixed here, before the numbers

For a cell, the procedure path is declared **CHEAPER** than the reference **only if** all three
hold:

1. the procedure arm's **warm median wall time** is **lower** than the reference arm's;
2. the **paired** differences (case by case, warm pass) have a **95 % bootstrap interval that
   excludes zero** — the interval is over the matched cases of the cell, resampled with a
   fixed seed, and the number of resamples is named in the evidence;
3. the procedure arm's answers are **grounded on the same matched cases** as the reference's
   — a cheaper answer to a different question is not a cheaper answer.

If any of the three fails, the cell's verdict is **NOT ESTABLISHED** and the milestone says so.
If the interval contains zero, the verdict is **NOT ESTABLISHED** even when the medians differ
in the procedure's favour: the H2 milestone measured a difference whose **sign was not stable
across runs**, and a difference that a resampling cannot separate from zero is not a
difference.

**The verdict is per cell.** There is no aggregate "winner" over the eight cells, and no
aggregate is invented after the rows are seen.

## 7. What is NOT measured, and NOT claimed

- **No latency is asserted by any test.** The timings are machine state; they are recorded and
  read against the rule above, and a faster answer is not evidence of competence.
- **No sealed evaluation.** The separation a sealed split needs was inspected with synthetic
  canaries and **could not be established from this executor**; the milestone is **DEV-only**.
- **No superiority claim** of any kind unless the rule of §6 is met in a cell.
- **No generalisation** beyond the two synthetic domains and the corpus shape they have.
