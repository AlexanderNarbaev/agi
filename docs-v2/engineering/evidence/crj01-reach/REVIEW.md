# CRJ-01 / COMPETENCE-REACH — independent review

**Reviewer:** an independent DSH reviewer, on the final stable snapshot of the `dsh-writer`
worktree · **Date:** 2026-11-27 · **Baseline under review:** `7e20e9d9f9945066ebc0120f8e5b379f255ef266`
· **Artefacts read:** the milestone document, the pre-registration, the 40-case DEV set, the
corpus report, the whole of `docs-v2/engineering/evidence/crj01-reach/`, the four gate scripts, the
three new production classes, the four modified production files, the served suite and the three
`io.matrix.crj.reach` / `io.matrix.crj.corpus` instruments, the two governance records and the
session checkpoint.

**What this review did NOT do:** it modified no file except this report, ran no Gradle build, and
changed no number anywhere. Every number below was re-derived with `python3` over the retained
`CRJ-01-REACH-GRID-ROWS.json` and `CRJ-01-REACH-GRID-SUMMARY.json`, or read out of the production
source, or observed by running the two gate scripts and the two validators.

---

## VERDICT

**Partly.** The milestone's *substantive* result — the criterion comparison, its two separate
denominators, the falsification of the durable retire and of the durable narrow, the unchanged
served default, and the honesty of the role record in the milestone document itself — is supported
by the retained evidence, and I verified each of those against the artefacts. But the milestone
document's **headline disposition for the strategy axis is contradicted by the milestone's own
table and by the retained rows**, the `SUPERSEDED` and `CHANGED_BINDINGS` sections of §3 contain
numbers that are not in the evidence (one of them under a demonstrably wrong definition of a
false rejection), the §9 "next scientific decision" rests on a premise its own rows refute, the
"independent" gate cannot detect a corrupted oracle, a forged scope or a falsified validation
outcome, and two of the milestone's own test artefacts still assert a case-author independence
that does not exist. In short: the measurement is sound and reproducible; the **prose that
reports it is not yet sound**, and four of the wrong numbers are wrong in the direction that makes
the result look cleaner than it is.

---

## FINDINGS

### F1 — the strategy-axis disposition is contradicted by the milestone's own table and rows

**SEVERITY: BLOCKER**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md`, §1 line 17-19 and §3.1 lines
79-83; §3.5 line 157. Repeated in `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row
`C2A-EVIDENCE-TO-ACTION`, `test_evidence` ("the citation-derived and the evidence-derived
strategy produced the SAME scope in every cell, so their comparison is NOT ESTABLISHED") and in
the row's `limitations` (2).

**WHAT IS WRONG.** The milestone states:

> "the **scope strategy** comparison is **NOT ESTABLISHED** for the pair
> `CITATION_DERIVED`/`EVIDENCE_DERIVED` (on this corpus the two produce the *same* scope in every
> cell, so there is nothing to compare)"

and, in §3.1:

> "⇒ **`CITATION_DERIVED` and `EVIDENCE_DERIVED` coincide in every cell**"

Both statements are false, and the milestone's **own table immediately above them** shows it: in
the `SUPERSEDED` rows of the §3.1 table, `CITATION_DERIVED` has **3** sources and
`EVIDENCE_DERIVED` has **4**, in both domains. The retained summary agrees
(`CRJ-01-REACH-GRID-SUMMARY.json`, cell `wbe/SUPERSEDED`: `CITATION_DERIVED.scopeSize = 3`,
`EVIDENCE_DERIVED.scopeSize = 4`; cell `hpd/SUPERSEDED`: 3 and 4). The two scopes are not the
same object, and the compared quantities do differ in those cells:

| cell | arm | admissions | refusals | false admissions | false rejections |
|---|---|---|---|---|---|
| wbe/SUPERSEDED | `CITATION_DERIVED/ANCHOR` | 8 | 4 | 4 | 1 |
| wbe/SUPERSEDED | `EVIDENCE_DERIVED/ANCHOR` | 12 | 0 | 7 | 0 |
| hpd/SUPERSEDED | `CITATION_DERIVED/ANCHOR` | 7 | 5 | 4 | 0 |
| hpd/SUPERSEDED | `EVIDENCE_DERIVED/ANCHOR` | 11 | 1 | 8 | 0 |

So "there is nothing to compare" is exactly wrong for the two `SUPERSEDED` cells: the comparison
was made and it **does** differ (by 4 and 6 admissions). The mechanism behind the divergence is
visible in the retained scopes: under supersession the citation-derived scope drops the superseded
source (3 sources) while the evidence-derived scope keeps it (4 sources) — the very widening
mechanism the milestone was built to measure, showing up in the condition the milestone narrates
as a different story. The milestone's §3.3 spends a paragraph on `SUPERSEDED` and reports only the
criterion axis.

Two further consequences. First, the strategy axis has **no verdict at all** in the retained
evidence: `CRJ-01-REACH-SUMMARY` carries a `criterionDifference.verdict` per strategy, and nothing
for the strategy comparison — so the milestone asserts a strategy-axis verdict that no artefact
derives. Second, the sentence is a **grid-level verdict** in a vocabulary the pre-registration
does not define for this axis (see F11).

**PROPOSED RESOLUTION.** (1) In §1 and §3.1, replace "the two produce the same scope in every
cell" with the per-cell truth: the two coincide in `BASELINE` and `CHANGED_BINDINGS` and differ
in `SUPERSEDED`, where the citation-derived scope drops the superseded source and the
evidence-derived scope keeps it. (2) Replace the single "NOT ESTABLISHED" for the strategy axis
with the per-cell verdict the pre-registration's vocabulary provides, and add the strategy-axis
verdict to `Crj01ReachSummary` so the retained summary derives what the document asserts. (3)
Apply the same correction to the registry row's `test_evidence` and `limitations` (2).

---

### F2 — `CHANGED_BINDINGS` did change verdicts; the milestone says it changed none

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.3, lines 130-133.

**WHAT IS WRONG.** The milestone states:

> "the verdicts are **identical to `BASELINE`** in every arm; only the coverage moves in the third
> decimal (0.380 → 0.378 in `wbe`). On this corpus the weakened bindings did not change a single
> verdict."

Two per-case verdicts **did** change. Comparing the retained rows case by case for
`hpd/DECLARED/ANCHOR`:

| case | BASELINE | CHANGED_BINDINGS | note |
|---|---|---|---|
| `RC-HPD-13` | **admitted** | **refused** | completeness 0.00 → 1.00 (the refusal serves the reference) |
| `RC-HPD-20` | **refused** | **admitted** | |

The arm's *aggregate* counts happen to coincide (9 admissions / 3 refusals in both conditions)
**only because one flip goes each way** — the milestone's summary is an aggregate and cannot show
this. The same comparison also contradicts "only the coverage moves": the arm's mean completeness
moved **0.875 → 0.958** (the largest single movement in the milestone), and the `hpd/DECLARED/ANCHOR`
coverage moved 0.436 → 0.439 while `wbe/DECLARED/ANCHOR` moved 0.385 → 0.383. The `wbe` figure
quoted (0.380 → 0.378) is the wide scopes' coverage, which is correct and is not the whole story.

This matters because `CHANGED_BINDINGS` is the condition the pre-registration introduced
specifically to test the anchor's snapshot-dependence (§5: "the anchor's snapshot-dependence,
measured with the confound removed"), and the milestone uses it to report that the condition was
inert.

**PROPOSED RESOLUTION.** Rewrite the bullet as: the *aggregate* counts are unchanged in every arm,
but two per-case verdicts flip in `hpd/DECLARED/ANCHOR` (one in each direction), the arm's mean
completeness moves 0.875 → 0.958, and the case-level rows are retained so that a reader can see
it. A per-cell verdict computed on the *paired per-case* rows rather than on the aggregates is
the shape the pre-registration's §7 asks for.

---

### F3 — the `SUPERSEDED` false-rejection numbers use the wrong definition of a false rejection

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.3, lines 139-142.

**WHAT IS WRONG.** The milestone states:

> "(`hpd`/`CITATION_DERIVED`: the anchor admits 7 with **1 false rejection**, the
> claim-sufficiency rule admits 1 with **4 false rejections**)"

The retained rows and the retained summary both say **0** and **3**
(`hpd/SUPERSEDED`, `CITATION_DERIVED`): `ANCHOR` → `falseRejections: 0`, `falseRejectionDenominator: 5`;
`CLAIM_SUFFICIENCY` → `falseRejections: 3`, `falseRejectionDenominator: 11`. The summary's own
`criterionDifference` for that cell records `falseRejections: 3`.

The mechanism is diagnosable and shows the number was computed under a **different definition**:
"refused **and** the scope carries the necessary evidence", *without* the pre-registration's
answerability condition. Counting that way gives exactly 1 for the anchor (`RC-HPD-18`, an
`UNSUPPORTED` case the corpus cannot answer) and exactly 4 for the sufficiency rule (`RC-HPD-13`,
`RC-HPD-16`, `RC-HPD-18`, `RC-HPD-19`, of which only the first, second and fourth are
answerable). The pre-registration's definition (§6) is "refused **and** the case is answerable
**and** the scope carries the necessary evidence" — the case set says the same ("a refusal of a
case the scope could have answered"). The milestone applies the right definition in §1 and §3.2
("one false rejection appears in the `wbe` cell and three in the `hpd` cell at `BASELINE`" — those
are correct) and the wrong one here, so the document is internally inconsistent about what a
false rejection is.

**PROPOSED RESOLUTION.** Replace "1" with "0" and "4" with "3", and state the false-rejection
definition inline once, so that the `SUPERSEDED` and `BASELINE` paragraphs cannot diverge again.

---

### F4 — the `BASELINE` false-admission breakdown under-counts and mis-denominates the anchor's blind spot

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.2, item 3, lines 112-124.

**WHAT IS WRONG.** The milestone states:

> "Only **2** false admissions in the `BASELINE` cells are admitted-and-incomplete (a
> `MULTI_SOURCE` and an `AMBIGUOUS` case, both under the declared scope)."
> "The anchor's blind spot is real but small here (**2 of 66** `BASELINE` admissions in the
> declared scope)"

There are **three** admitted-and-incomplete false admissions at `BASELINE`, all under
`DECLARED/ANCHOR`: `RC-WBE-14` (`MULTI_SOURCE`), `RC-HPD-16` (`AMBIGUOUS`) and **`RC-HPD-13`
(`MISLEADING_RELEVANCE`)**. The third is not a new fact to the document — `RC-HPD-13` is already
inside the milestone's own breakdown one paragraph above ("20 `MISLEADING_RELEVANCE` cases answered
`QUALIFIED` (15) or `ABSTAINED` (5)"; the 5 abstained `MISLEADING_RELEVANCE` rows at `BASELINE`
are `RC-HPD-13` in the six arms, and in `DECLARED/ANCHOR` it is admitted with
`evidenceComplete = false`). So §3.2 contradicts itself.

The denominator is wrong too. The `BASELINE` admissions of the **declared** scope are **32**
(`wbe` 10 + 8, `hpd` 9 + 5). The number **66** is the `BASELINE` admission count of the whole
**`wbe` cell across all six arms** — a different quantity, from a different domain, over a
different scope. The correct statement is "3 of 32".

**PROPOSED RESOLUTION.** Replace "Only 2" with "Only 3", name the third case (`RC-HPD-13`,
`MISLEADING_RELEVANCE`), and replace "2 of 66" with "3 of 32", or drop the fraction and keep the
three named cases.

---

### F5 — "every false rejection in the milestone belongs to the `DECLARED` arm" is false

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.2, item 4, lines 125-126.

**WHAT IS WRONG.** The milestone states:

> "The false rejections of the wide scopes are zero; **every false rejection in the milestone
> belongs to the `DECLARED` arm**, i.e. to a scope that was deliberately narrowed."

The first clause is true at `BASELINE`. The second is false for the milestone as a whole: the
`SUPERSEDED` cells carry **8** false rejections, and **7 of the 8 are on the wide scopes**
(`wbe/CITATION_DERIVED/ANCHOR` 1, `wbe/CITATION_DERIVED/CLAIM_SUFFICIENCY` 3,
`hpd/CITATION_DERIVED/CLAIM_SUFFICIENCY` 3); only **one** (`wbe/DECLARED/CLAIM_SUFFICIENCY`) is on
the declared arm. The claim is therefore wrong in the direction that matters most: it hides that
the criterion's cost at `SUPERSEDED` is borne mostly by the *wide* scopes — the scopes that are
the incumbent's own.

**PROPOSED RESOLUTION.** Restrict the sentence to `BASELINE` ("every false rejection in the
`BASELINE` cells belongs to the `DECLARED` arm") and add the milestone-wide split (1 on the
declared arm, 7 on the wide scopes, all at `SUPERSEDED`).

---

### F6 — the §9 premise ("the blind spot needs two necessary claims") is refuted by the milestone's own rows

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §9, lines 306-311 (and the
`limitations` of the registry row, which repeats "two cases of twenty-four").

**WHAT IS WRONG.** The milestone's "next scientific decision" is built on this sentence:

> "The anchor's blind spot is therefore only observable when a question genuinely needs **two
> necessary claims** — which this corpus produced in **two** cases of 24 (`RC-WBE-14`,
> `RC-HPD-14`/15)"

Three separate problems. (a) **The count is wrong.** Of the 24 `EVALUATION` cases, **eight** carry
two necessary claims — `RC-WBE-14/15/16/19` and `RC-HPD-14/15/16/19` (two `MULTI_SOURCE`, two
`AMBIGUOUS`, two `CONFLICT` per domain) — not two, and the three identifiers quoted are three of
the eight, not two. (b) **The generalisation is refuted by the milestone's own rows.** The one
admitted-and-incomplete false admission that needs only **one** necessary claim is `RC-HPD-13`
(gold: `hpd-d-labelling.md`, one claim): the anchor admitted the declared scope, the scope does
not carry the claim the answer needs, and the served answer was incomplete. The anchor's blind
spot is therefore observable **without** a second claim — it is observable whenever the claim the
reading cites *first* is not the claim the oracle requires. (c) The sentence immediately above it
— "the two rules **coincide on every wide scope** (12 of 12 cases, both domains, **both
conditions**)" — is false in the third condition (`hpd/SUPERSEDED/CITATION_DERIVED`: the anchor
admits 7, the sufficiency rule admits 1) and the "12 of 12" is the `wbe` figure (the `hpd` wide
scope admits 11 of 12, with both criteria agreeing on all twelve). The premise of the next
milestone is therefore stated in a form its own data contradicts.

**PROPOSED RESOLUTION.** Rewrite the premise from the measured instances rather than from the
case kinds: the blind spot appeared on three `BASELINE` admissions of the declared scope
(`RC-WBE-14`, `RC-HPD-13`, `RC-HPD-16`), of which one needs a single claim — so the next corpus
must contain questions whose *first-cited* claim is not the required one, not only questions that
need two claims. Qualify the "coincide" sentence with the conditions it holds in.

---

### F7 — the gate is not independent of the producer, and it can be fooled by exactly the corruptions the milestone's claims depend on

**SEVERITY: MAJOR**
**ARTEFACT:** `scripts/reach-gate.py` (checks at lines 96-108, 116-134, 139-166, 168-181,
189-195, 197-207); `scripts/reach-gate-selftest.sh`; `docs-v2/engineering/evidence/crj01-reach/README.md`
§2 lines 39-44; `docs-v2/engineering/TEST-CATALOGUE.yml`, entry `GATE-CRJ01-COMPETENCE-REACH`.

**WHAT IS WRONG.** The gate is described as "the INDEPENDENT supervisor gate" that "re-derives
the milestone's evidence from the RETAINED rows, **without trusting the test that produced them**".
What it actually does is re-derive the producer's *arithmetic* from the producer's *own booleans*:
`falseAdmission` is recomputed from the row's own `answerable`, `evidenceComplete`,
`dispositionAllowed` and `disposition` fields (lines 168-181), and the summary's counts are
recomputed from the rows' `admitted` / `falseAdmission` / `falseRejection` flags (lines 139-166).
**The rows are never cross-checked against the case set's oracle.** The case set is read only for
its corpus digests (lines 197-207).

I probed this on a copy of the evidence, refreshing `CHECKSUMS.sha256` after each mutation so
that the manifest check could not be the thing that caught it. The results:

| mutation | gate verdict |
|---|---|
| every row's `kind` overwritten to `PLAIN` | **17 checks, 0 failures — GREEN** |
| the `DECLARED` arm's `scope` forged in the rows | **17 checks, 0 failures — GREEN** |
| the summary's `scopeSize`, `validated` and `validationAgreement` all falsified | **17 checks, 0 failures — GREEN** |
| a row moved from one cell to another (same total) | caught, but by the summary re-derivation, not by any per-cell check |
| the oracle in the rows rewritten (`answerable` flipped, derived flags recomputed) | caught, but only because the summary no longer matched |

Two structural gaps follow. (1) The row-count check (lines 96-108) is **global** (432 in total)
and "every cell carries all six arms" (lines 116-134) only requires a count **greater than zero**,
so a cell may hold 11 rows and another 13 and the gate stays green. (2) The gate never checks the
summary's `scope`, `scopeSize`, `validated`, `validationAgreement` or `verdict` against the rows
or against the scope rows — so **the milestone's §3.1 scope table (the source counts and the
"validated yes (5/5)" column) is not protected by the gate at all**, and neither is the §3.2
kind breakdown.

The self-test is honest about its own limits ("it proves that the gate REFUSES six specific
classes of broken evidence, not that it refuses every possible one" — the catalogue entry says
this well), and it does prove the gate can go red: I ran it, and it reports "mutations 6, caught
by the right check 6". But all six of its mutations are *structural* corruptions; none of them
is a semantic one of the kind the milestone's prose depends on, and the semantic corruptions above
are green.

**PROPOSED RESOLUTION.** (1) Add a check that re-derives every row's oracle fields **from the
case set** (`kind`, `answerable`, `allowedDispositions`, the necessary claim ids) instead of from
the row, and fails on a mismatch. (2) Replace the global row-count check with a per-cell one:
every `(domain, condition, arm)` must hold exactly the evaluation cases of that domain. (3) Add a
check that the summary's `scope`, `scopeSize`, `validated` and `validationAgreement` agree with
the rows and with the scope rows. (4) Add two semantic mutations to `reach-gate-selftest.sh` (a
row's `kind` overwritten; a summary's `scopeSize` falsified) so that the self-test covers the
class of corruption that matters.

---

### F8 — two of the milestone's own instruments assert a case-author independence that does not exist

**SEVERITY: MAJOR**
**ARTEFACT:** `matrix-core/src/test/java/io/matrix/knowledge/CompetenceReachServedTest.java`,
class Javadoc lines 68-72, field Javadoc line 121, and the `@DisplayName` at lines 169-171 (the
display name is carried into the retained GREEN and RED XML as the test-case name);
`matrix-core/src/test/java/io/matrix/crj/reach/Crj01ReachCases.java`, class Javadoc lines 18-24
and 44.

**WHAT IS WRONG.** The directive requires the failed independence to be stated plainly with no
false role attribution. The milestone document (§5, lines 204-225), `CASES-PROVENANCE.md` (§1),
`MANIFESTS.md` (§3), the evidence `README.md` (header) and the case set's own `author` field all
state the truth: the cases are the implementer's, independence is **NONE**. But the *code* says
otherwise, in the files a reader of the code meets first:

- `CompetenceReachServedTest` class Javadoc: *"so this is a DEV measurement with a **role-separated
  case author**"* — the role separation failed; there is no role separation on the case side.
- `CompetenceReachServedTest` field Javadoc: *"The case set **the independent case author
  wrote**."*
- `CompetenceReachServedTest` `@DisplayName`: *"**the case set is the independent author's**, the
  slices are disjoint, and every gold is a sentence of the corpus bytes"* — this string is
  literally in the retained `TEST-io.matrix.knowledge.CompetenceReachServedTest.xml` and in
  `CRJ-01-REACH-REPRODUCTION-no-criterion.xml`.
- `Crj01ReachCases` class Javadoc: *"as the **independent case author wrote it**"*, and: *"The file
  is **read** here and never written: the case author owns the gold, and the implementer owns the
  measurement. A measurement that wrote its own yardstick would measure nothing."* — while
  `scripts/reach-cases.py`, a script owned by the implementer, writes exactly that yardstick
  (`OUT = docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json`).

The second bullet is the sharpest instance: the instrument asserts a separation that the
repository contradicts in the same commit.

**PROPOSED RESOLUTION.** In both files, replace "the independent case author" with "the
implementer" and delete the sentence about the yardstick; change the `@DisplayName` to
"the case set is the implementer's, the slices are disjoint, and every gold is a sentence of the
corpus bytes". A GREEN/RED re-run is then required, because the display name is in the retained
XML.

---

### F9 — the governance records state things the evidence does not support

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/TEST-CATALOGUE.yml`, entry `SCOPE-CRJ01-COMPETENCE-REACH`
(`evidence`, `what_it_proves`); `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row
`C2A-EVIDENCE-TO-ACTION` (`test_evidence`, `limitations`).

**WHAT IS WRONG.** (a) The catalogue's `evidence` says the retained directory holds *"the two
files of this scope and no others: `TEST-io.matrix.knowledge.CompetenceReachServedTest.xml` **and
`TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml`**"* — the corpus XML is in neither
the evidence directory nor `matrix-core/build/test-results/test/` (only the served-suite XML is
there). The claim that the retained copies "verify against the directory's own `CHECKSUMS.sha256`"
therefore covers one file, not two. (b) The catalogue's `what_it_proves` claims *"that the six arms
of the paired comparison receive the same immutable snapshot, the same bindings and the same
history"* — this is true of the code (`Crj01ReachGrid` captures one `view` and passes it to every
arm) but **the suite does not assert it**: `theSixArmsAreMeasuredOnIdenticalInputs` asserts the
row count, that every scope row carries a validation reason and that no row's scope is empty.
The milestone document repeats the overstatement (§3.1, lines 65-66: "the suite asserts it").
(c) The registry says *"7 targeted tests over 2 classes (**6 in the served suite** and 1 in the
corpus instrument)"* — the served suite has **7** tests (the catalogue's own `methods_executed: 8`
is the correct total). (d) The registry's `test_evidence` and `limitations` repeat F1's and F6's
false statements ("produced the SAME scope in every cell"; "the two criteria AGREE on every case
of every wide scope"; "two cases of twenty-four").

**PROPOSED RESOLUTION.** (1) Retain the corpus XML in the evidence directory (or drop the claim
from the catalogue's `evidence`). (2) Rewrite the "identical inputs" sentence as a property of the
instrument plus the one assertion that exists, or add the assertion. (3) Correct the 6/7 split.
(4) Apply F1's and F6's corrections to the registry row.

---

### F10 — the `DECLARED` strategy's production Javadoc describes the semantics the pre-registration amended away

**SEVERITY: MINOR**
**ARTEFACT:** `matrix-core/src/main/java/io/matrix/cognitive/report/ScopeStrategy.java`, lines
49-51 (`DECLARED`); `matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedureLearner.java`,
the Javadoc of `scope(...)` ("**Why the declared scope refuses rather than narrows.** … The
attempt is refused and the episode is named.").

**WHAT IS WRONG.** The pre-registration's dated §10 amendment changed `DECLARED` from *refusing* an
episode that cited outside the declaration to *bounding* the learning, and the implementation
follows the amendment (`scopeOf`'s `DECLARED` branch returns the declared scope and only counts
the out-of-declaration citations in the reason). Both production Javadocs still describe the
pre-amendment semantics. A reader of the production code is told the opposite of what the code
does, on the one rule the milestone amended.

**PROPOSED RESOLUTION.** Rewrite both Javadocs to the amended semantics (the declaration bounds
the learning; the validation is the guard against a too-narrow declaration), quoting the
pre-registration's §10 amendment as the reason.

---

### F11 — the milestone states grid-level verdicts in a vocabulary the pre-registration does not allow

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §1 lines 16-31 ("the **criterion**
comparison is **ESTABLISHED**, and it is the milestone's substantive result"; "the pair `DECLARED`
… **IS ESTABLISHED**"); §3.5 lines 153-159. Pre-registration §7 lines 182-211.

**WHAT IS WRONG.** The pre-registration fixes the verdict of a cell at exactly two values —
`DIFFERENCE OBSERVED` and `NOT ESTABLISHED` — and forbids a grid-level verdict and an aggregate
over the cells ("a verdict that appears only after the grid is seen is a description of the grid").
The summary honours the vocabulary (`verdictVocabulary`), but the milestone document introduces a
third term, "IS ESTABLISHED", and uses it to declare, once, for the whole grid, that the criterion
comparison is established and is "the milestone's substantive result". That is a grid-level
verdict in a vocabulary the pre-registration does not define, and it is stated for an axis
(the strategy axis) for which the summary contains **no** verdict at all (see F1). The prohibition
on a winner, on a universal threshold and on timing assertions is respected: I found no winner,
no threshold (the rates quoted are counts with their denominators, never a bound), and no timing
assertion (the cost fields carry an explicit "records, never assertions" note, and the milestone
§3.4 says so in prose).

**PROPOSED RESOLUTION.** Use the two pre-registered terms and attach the verdict to the cell it
belongs to, in the same order as the summary's `criterionDifference.verdict`; replace "the
milestone's substantive result" with a pointer to the per-cell verdicts.

---

### F12 — the false-admission breakdown sums to 62 of 67 and leaves five rows unaccounted for

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.2, item 3, lines 112-118.

**WHAT IS WRONG.** The breakdown lists 12 `INCOMPLETE` + 20 `MISLEADING_RELEVANCE` + 10
`CORROBORATED` + 12 `PLAIN`/`PARAPHRASE` + 6 `UNSUPPORTED` = 60, plus the 2 admitted-and-incomplete
cases = 62. The retained rows hold **67** `BASELINE` false admissions. The five unlisted rows are
the `MULTI_SOURCE` cases that were admitted with a `QUALIFIED` disposition (5 of the 6
`MULTI_SOURCE` false admissions; the sixth is the admitted-and-incomplete `RC-WBE-14`). The
document's own conclusion — "the false admissions are mostly a disposition mismatch, not a missing
evidence" — is true (of the 67, 64 are not admitted-and-incomplete and are pure disposition
mismatches; one of the remaining three, `RC-HPD-13`, is both), but the enumeration does not add
up and a reader checking it will find the gap.

**PROPOSED RESOLUTION.** Add the `MULTI_SOURCE` bucket (6: 5 `QUALIFIED`, 1 `ANSWERED`) to the
enumeration so that it sums to 67, and state the total.

---

### F13 — the retained `README.md` mis-states the row shape

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/README.md` §1, line 17.

**WHAT IS WRONG.** *"**every** per-case row of every arm, cell and condition — 432 rows, **33
fields each**"*. The rows carry **32** fields (`len(rows[0]) == 32`; the field list is
`admissionReason, admitted, answerable, arm, carries, caseId, citedClaimIds, completeness,
condition, coverage, criterion, disposition, dispositionAllowed, domain, evidenceComplete,
fallback, fallbackNanos, fallbackReason, falseAdmission, falseRejection, kind, missingClaimIds,
necessaryClaimIds, necessarySourceIds, readNanos, scope, scopeCoverage, selectionNanos, served,
strategy, totalNanos, workspaceCoverage`). The row count (432) is correct.

**PROPOSED RESOLUTION.** Change 33 to 32.

---

### F14 — the gold-uniqueness claim is false and the suite's assertion that checks it cannot fail

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/CASES-PROVENANCE.md` §3, line 73
("no necessary gold is used by two cases"); the local DSH checkpoint §4, line 85
("every gold is used once"); `matrix-core/src/test/java/io/matrix/knowledge/CompetenceReachServedTest.java`,
lines 193-203.

**WHAT IS WRONG.** (a) `RC-WBE-13` and `RC-WBE-14` both name the same necessary gold, *"The yearly
energy production of a wind-turbine decreases as the roughness of its blades increases."*
(`wbe-d-aerodynamics.md`). The reuse is deliberate — `RC-WBE-14` is the `MULTI_SOURCE` case that
pairs the coating proposition with the energy proposition — but the two provenance statements are
false as written. (b) The suite's assertion is vacuous: the gold set is keyed by
`testCase.id() + "|" + evidence.proposition()`, so it can never contain a duplicate by
construction; the assertion labelled *"the gold of %s appears in no other case"* cannot fail. (The
question-disjointness assertion in the same block is genuine, and it passes.) The slices
themselves are disjoint as *slices* — the reuse is within `EVALUATION`.

**PROPOSED RESOLUTION.** State the truth: the slices are disjoint as slices and by question; one
necessary gold is deliberately shared by two `EVALUATION` cases of the same domain. Fix the
assertion to key the gold set by proposition alone (within a domain), so it can fail.

---

### F15 — the summary's cell verdict ignores the admissions count, and there is no strategy-axis verdict

**SEVERITY: MINOR**
**ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/reach/Crj01ReachSummary.java`,
`difference(...)`, lines 178-202.

**WHAT IS WRONG.** The cell verdict is decided by three aggregates only:
`falseAdmissions != 0 || falseRejections != 0 || completeness != 0.0` (line 192). The
**admissions count is not among the compared quantities**, although it is the most visible
quantity the milestone reports (the anchor admits 7 and the sufficiency rule admits 1 in
`hpd/SUPERSEDED/CITATION_DERIVED`). In the retained data the verdict happens to come out right in
every cell, but the rule as written would declare `NOT ESTABLISHED` for an arm pair that served a
different number of cases. The completeness difference is also computed on **rounded** means
(`round` to three decimals, line 195), so a real difference below 0.0005 is invisible. Finally, the
summary has no verdict for the **strategy** axis at all (F1).

**PROPOSED RESOLUTION.** Include the admissions and refusals in the compared set, compute the
completeness difference on unrounded means, and add the strategy-axis verdict the milestone
asserts.

---

### F16 — `meanCompleteness` mixes the arm's own path with the fallback path

**SEVERITY: MINOR**
**ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/reach/Crj01ReachSummary.java`,
`counts(...)`, lines 140 and 159; `Crj01ReachGrid.java`, lines 272-275.

**WHAT IS WRONG.** A refused case is scored on the **reference** answer (the grid sets
`served = admitted ? restricted : reference`), so the completeness and coverage of a refusal are
the reference's — normally 1.0. The arm's `meanCompleteness` therefore averages the arm's own
served answers *and* its fallbacks, and a declared arm's mean completeness is **raised** by the
completeness of the answers it refused to serve. `wbe/DECLARED/ANCHOR`'s reported 0.958 is 0.950
over its ten **admitted** cases plus two refusal rows at 1.0 (`RC-WBE-13`, `RC-WBE-16`);
`hpd/DECLARED/ANCHOR`'s 0.875 is 0.833 over its nine admitted cases plus three refusal rows at
1.0. The milestone reads this number as "the criterion … buys complete evidence (mean
completeness 1.000 against 0.958 and 0.875)" (lines 108-110), which is exactly the direction the
mixing biases.

**PROPOSED RESOLUTION.** Report the arm's completeness over its **admitted** cases only (and the
fallback's completeness separately, which is the reference's), or state in the summary and in
§3.2 that the mean is over all twelve cases and includes the reference answers served on refusal.

---

### F17 — "every arm records its own selection, read and fallback cost" — the read cost is shared

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.4, lines 146-148;
`matrix-core/src/test/java/io/matrix/crj/reach/Crj01ReachGrid.java`, lines 260-263.

**WHAT IS WRONG.** The restricted reading is asked **once per (case, strategy)** and the answer is
shared by the two criteria of that scope (the grid says so in its own Javadoc: "the rule decides
ADMISSION and not the reading"), so the two arms of one scope carry the **same** `readNanos`. The
per-arm `readNanos` in the summary is therefore a per-scope measurement copied into two arms, not
an arm's own measurement; only `selectionNanos` is measured per arm. The milestone's "Every arm
records its own selection, read and fallback cost" overstates by one of the three. (The timing
substance is fine — no timing assertion is made anywhere, and the summary carries an explicit
"records, never assertions" note on every cost block.)

**PROPOSED RESOLUTION.** Say that the selection cost is per arm and the read and fallback costs are
per scope (and per case), shared by the two criteria of that scope.

---

### F18 — the case set's own `author` field and the provenance disagree about how many subagents were commissioned

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json`, `author.independence`
("**two** separately commissioned DSH case-author subagents were given the corpus and the oracle
contract and neither delivered a case set"); against
`docs-v2/engineering/evidence/crj01-reach/CASES-PROVENANCE.md` §1 (the `corpus-author` commission
was given "the corpus contract only" and **delivered 4 of the 8 corpus documents**).

**WHAT IS WRONG.** The case set's field describes the `corpus-author` subagent as a *case author*
that delivered nothing; the provenance describes it as a corpus author that delivered half the
corpus. The milestone document's role table (§5) is the accurate one ("the subagent wrote 4 of the 8
documents and stopped"). The error is in the direction of *more* failed independence, so it does
not overstate independence — but the role record is the artefact the directive asks to be exact.

**PROPOSED RESOLUTION.** Align the case set's `author.independence` with `CASES-PROVENANCE.md`:
one `case-author` commission that delivered nothing, and one `corpus-author` commission that
delivered four of the eight corpus documents. Regenerate the case set with `scripts/reach-cases.py`
(whose `provenance.corpusAuthor` already words it correctly).

---

### F19 — the corpus "carries no gold" argument does not hold, and the provenance's kind list and validation layout are incomplete

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §5, line 209 and
`docs-v2/engineering/evidence/crj01-reach/MANIFESTS.md` §3, line 35 ("the corpus carries no gold,
so the measurement's oracle is unaffected"); `CASES-PROVENANCE.md` §3 line 69 and §4 line 84.

**WHAT IS WRONG.** (a) The corpus carries no *labels*, but the case set's golds **are** corpus
sentences: each `necessaryEvidence` entry names "the **exact sentence of the corpus**"
(`CASES-PROVENANCE.md` §4). The oracle is therefore anchored in corpus bytes, and the corpus
author's output is part of what the oracle selects from. "The corpus carries no gold, so the
measurement's oracle is unaffected" is a stronger statement than the artefacts support; the
defensible statement is that the corpus's author never saw the mechanism, the cases or the
criterion, so the corpus could not be shaped to favour them. (b) §4's list of the case kinds
omits `CONFLICT` and `CORROBORATED`, both of which are present (one per domain) and both of which
the pre-registration §5 names. (c) §3's description of where the `VALIDATION` golds sit ("the
**identification** document (four) and the **reconciliation** document (one)") is the `hpd`
layout; the `wbe` validation golds are all five in the **inspection** document.

**PROPOSED RESOLUTION.** Reword (a) to the supportable claim; add `CONFLICT` and `CORROBORATED`
to the kind list; mark (c) as the `hpd` layout (or give both).

---

### F20 — the pre-registration's promised per-cell kind breakdown is not in the summary

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-REACH-PREREGISTRATION.md` §5, lines 133-139 ("The case
kinds are the second axis of the case set and are **reported as a breakdown within each cell**");
`docs-v2/engineering/evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json`.

**WHAT IS WRONG.** The summary carries no kind breakdown. The rows carry `kind`, so the breakdown
is derivable — and the milestone's §3.2 item 3 does give a kind breakdown of the `BASELINE` false
admissions, but not per cell, which is what the pre-registration asked for. This is a
deliverable that was quietly not produced.

**PROPOSED RESOLUTION.** Add a per-cell, per-kind count of admissions and refusals to
`Crj01ReachSummary` (the rows already carry `kind`), or record in the milestone that the
breakdown is derivable from the rows and is given only for the `BASELINE` false admissions.

---

### F21 — the generator's own docstring is stale

**SEVERITY: MINOR**
**ARTEFACT:** `scripts/reach-cases.py`, module docstring ("**36 cases** over two synthetic DEV
domains"; "VALIDATION — … their golds sit in ONE document (B)").

**WHAT IS WRONG.** The generator writes **40** cases (20 per domain), and the `hpd` validation
golds sit in two documents (four in the identification document, one in the reconciliation
document) — the generator's own `slices` block and the retained case set say so correctly, so only
the docstring is stale. (The brief's local scratch path does not exist; the generator
is `scripts/reach-cases.py`.)

**PROPOSED RESOLUTION.** Correct the docstring's counts and the validation description.

---

### F22 — the evidence manifest does not (and, after this report, cannot) cover every file in the directory

**SEVERITY: NOTE**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/CHECKSUMS.sha256`;
`docs-v2/engineering/evidence/crj01-reach/README.md` §1, line 21.

**WHAT IS WRONG.** The README describes the manifest as covering "every `.md`, `.json` and `.xml`
file" of the directory. It covers seven files and does not include this report, `REVIEW.md` — which
the same README's table (line 24) lists as an artefact of the directory. I am not permitted to
modify any file other than this report, so I cannot add the digest. The gate and
`sha256sum -c CHECKSUMS.sha256` both still pass (they verify the listed files), so nothing is
broken — but the manifest's stated coverage is now inaccurate.

**PROPOSED RESOLUTION.** Re-run `scripts/reach-retain-evidence.sh green` (or the suite's own
`refreshManifest`) after this report is accepted, so that the manifest covers `REVIEW.md` as well,
and amend the README's wording to "every file the manifest lists" if the intent is otherwise.

---

### F23 — the grid is not a route test; the lifecycle test is

**SEVERITY: NOTE**
**ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/reach/Crj01ReachGrid.java`, class Javadoc
lines 31-36; `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §4, lines 165-167.

**WHAT IS WRONG.** The grid calls `KnowledgeWorkbench.askInView` and `KnowledgeWorkbench.assessScope`
directly and says so ("without the route's artefact writes and trace lines"). The **lifecycle**
test is the route test: it drives `ArtifactReportResource.journey(...)` with a
`ReportJourneyPlanner`, reads and writes the durable store, and closes and reopens the store for
the restart. So the milestone's §4 claim ("walks the lifecycle through the served planner, the
authorisation, the procedure contract and the durable store") is accurate **for the lifecycle**,
and the milestone should say explicitly that the **432-row grid** is an instrument-level
measurement of the same workbench APIs the route calls, not a measurement of the route.

**PROPOSED RESOLUTION.** Add one sentence to §3.1 making the distinction explicit.

---

### F24 — the catalogue's `last_verified_commit` names the milestone's baseline, not a commit that contains the milestone

**SEVERITY: NOTE**
**ARTEFACT:** `docs-v2/engineering/TEST-CATALOGUE.yml` (the three new entries);
`docs-v2/architecture/CAPABILITY-REGISTRY.yml` (`last_verified_commit`).

**WHAT IS WRONG.** All four point at `7e20e9d9…`, the milestone's **baseline**; no commit currently
contains the milestone's production changes, its tests or its evidence. This follows the
repository's existing convention (the `SCOPE-CRJ01-ADAPTIVE-COMPETENCE` entry likewise names its
own baseline `dff12b58…`), and the milestone explicitly does not commit ("No commit, no push — the
coordinator reviews and integrates"), so it is not a defect — but it must be updated to the
integration commit when the coordinator commits, or it will point at a tree that never contained
the measurement.

**PROPOSED RESOLUTION.** Update the four `last_verified_commit` fields when the milestone is
committed.

---

## WHAT I CHECKED AND FOUND CORRECT

1. **The row corpus.** `CRJ-01-REACH-GRID-ROWS.json` holds **432** rows, each with **32** fields
   (README says 33 — F13); the keys are unique on
   `(domain, condition, arm, caseId)`; the six cells each hold 72 rows; every
   `(domain, condition, strategy, criterion)` arm holds exactly the 12 evaluation cases of its
   domain.
2. **The §3.2 `BASELINE` table.** All twelve rows of the milestone's table (admissions, refusals,
   both false-admission and false-rejection denominators, mean completeness, mean coverage) were
   recomputed from the rows and **match exactly**, including the two denominators kept separate
   (e.g. `wbe/DECLARED/CLAIM_SUFFICIENCY`: 8 admissions, 4 refusals, 5/8 and 1/4).
3. **The §3.1 scope table.** The six scope sizes and the six validation outcomes
   (`yes (5/5)` at `BASELINE` and `CHANGED_BINDINGS`, `no (0/5)` at `SUPERSEDED`, agreement 0.0) are
   exactly what the summary carries.
4. **The `SUPERSEDED` aggregates.** "false admissions rise to 66 of 97 admissions and false
   rejections to 8 of 47 refusals" — **correct** (66 = 35+31, 97 = 54+43, 8 = 5+3, 47 = 18+29).
5. **The `CHANGED_BINDINGS` coverage figure.** 0.380 → 0.378 in `wbe` for the wide scopes — correct.
6. **The two corpora.** 8 documents, **70 sentences, 70 claims, 0 missing** — re-derived by
   splitting the corpus bytes exactly as the builder does (hpd 37 + wbe 33 = 70) and confirmed
   against `CRJ-01-REACH-CORPUS-REPORT.json` (`claims 70`, `citableClaims 70`, `missingSentences 0`).
7. **The case set.** 40 cases; `PROPOSAL` 3 + `VALIDATION` 5 + `EVALUATION` 12 per domain; all
   questions unique; **every** necessary gold appears verbatim in the corpus file it names (0
   exceptions); the eight recorded corpus SHA-256 digests match the corpus bytes; the case set's
   `author.role` is `the IMPLEMENTER` and its `independence` field says **NONE** — truthful; the
   `sealed` flag is `false` with the sealed note in place.
8. **The shapes.** `PARAPHRASE`, `MISLEADING_RELEVANCE`, `MULTI_SOURCE`, `AMBIGUOUS`, `INCOMPLETE`,
   `UNSUPPORTED` — and also `PLAIN`, `CONFLICT`, `CORROBORATED` — all occur in **both** domains,
   as the milestone claims (§5). The decoys of the misleading-relevance cases really are in
   `allowedEvidence`, and the `MULTI_SOURCE`/`AMBIGUOUS`/`CONFLICT` cases really do carry two
   necessary claims.
9. **The anchor's reason strings are byte-identical to the retained ADAPTIVE evidence's.** I
   extracted every anchor admit/refuse string from
   `evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json` (35 admit, 16 refuse) and
   matched them against the new `ScopeCriterion.ANCHOR.judge` template: all 51 match the new
   format exactly, and all 186 + 21 anchor strings in the reach rows match it too. The milestone's
   claim (§2, lines 48-49) holds.
10. **The served default is unchanged for a record written by an older writer.** `assessSupport`
    now delegates to `assessScope(..., ScopeCriterion.ANCHOR).toAdequacy()`; the ANCHOR rule
    reconstructs the incumbent strings byte-for-byte, `bestClaimId`/`bestCoverage` come from the
    same `Relevance` object, `SupportAdequacy.none(reason)` and
    `ScopeAssessment.none(...).toAdequacy()` produce the same seven fields, and
    `ScopeCriterion.of(null/"")` and an unknown name both return `ANCHOR`. `ReportProcedure.record`
    keeps version `1.0.0` and the byte-identical applicability field for the incumbent, and
    `criterionOf` returns `ANCHOR` for a record that names no rule. The applicability string is
    parsed only by `ReportProcedure` itself; the procedure's applicability *domain* is
    `requiredFacts` (the support-present fact), which was not touched. No store, schema, planner,
    authorisation or infrastructure file was changed (the production diff is four files,
    +490/−83).
11. **Both falsifications are real, and the stated mechanisms are the real ones.**
    (a) *The durable retire.* `CognitiveStore.recordProcedure` compares
    `procedureDefinitionOf(procedure)` — name, problem family, applicability, required facts,
    reference algorithm, validation agreement, validation kind, learned-in-episode, validation note
    — against the stored definition and **returns silently** when they are equal.
    `invalidated_at` is not in that list, and the `INSERT` is never reached, so a write carrying
    `invalidatedAt` is accepted as "the same definition" and **dropped**: the stored procedure
    keeps `invalidatedAt = null`, `Procedure.active()` stays true, and the route keeps serving it.
    That is exactly the mechanism the milestone states. (b) *The durable narrow.*
    `ReportProcedure.idFor(support)` derives the identity from the support set alone
    (`ID_PREFIX + hash(sorted support)`), so a narrower scope is a **different competence**, not a
    new version; the test shows the narrow candidate refused by the validation with a named reason,
    `narrowed.procedure() == null`, and the predecessor still `active()` in the store.
12. **The served route test is a real route test.** `CompetenceReachServedTest` wires
    `ArtifactReportResource` with a `ReportJourneyPlanner` and a `CognitiveService`, drives
    `route.journey(...)`, reads the durable store through `cognitive.store()`, and closes and
    reopens the store before the restart. The restart step serves the **exact** promoted method
    (`procedure.<id>`) on a question drawn from the `EVALUATION` slice (disjoint from the
    `PROPOSAL` and `VALIDATION` slices) whose gold only the widened scope carries.
13. **The GREEN/RED XMLs.** The retained GREEN is 7 tests, 0 failures, 0 errors; the retained RED
    is the same 7-test suite with exactly **2** failures, and the two failures are the ones the
    milestone names (the grid-level criterion difference and the lifecycle's diagnosis). The
    retained file timestamps are consistent with the GREEN run having produced the rows, the
    summary and the manifest (`19:34:34` / `19:34:41` local against the XML's own
    `2026-11-27T16:34:28Z`).
14. **The gate and its self-test run and pass.** `bash scripts/reach-gate.sh` → *checks 17, failed
    0*, exit 0. `bash scripts/reach-gate-selftest.sh` → *mutations 6, caught by the right check 6*,
    exit 0, and the repository is never written to (the copy is made under `mktemp -d`).
15. **The validators pass.** `python3 scripts/validate-test-catalogue.py --strict` → *the defects:
    0*; `python3 scripts/validate-project-registry.py --strict` → *the defects: 0*. (Both need
    `GIT_CONFIG_COUNT` unset; the broken git environment is an environment artefact, not a
    repository defect, and I confirmed it independently.)
16. **No regression to the prior milestone.** `bash scripts/adaptive-competence-gate.sh` still
    reports *checks 32, failed 0* against the untouched ADAPTIVE and H2 evidence.
17. **The verdict vocabulary is honoured where it matters.** The summary's `verdictVocabulary` is
    exactly the pre-registration's two terms, every cell's `criterionDifference.verdict` is one of
    them, the two denominators are separate in every cell and are never folded, the summary
    carries an explicit `noWinnerNote`, and every cost block carries "the costs are RECORDS, never
    assertions". **No winner, no universal threshold and no timing assertion** appears anywhere in
    the milestone document, the summary, the case set or the evidence README.
18. **The conditions are the production paths the milestone names.** `CHANGED_BINDINGS` goes through
    `bench.store().recordLearningErrors(...)` + `bench.applyErrorDrivenDecay(...)`;
    `SUPERSEDED` goes through `bench.selectSourceRevision(...)` twice with a revised document
    imported through `workbench.importDocument`. The condition is applied to the workspace before
    the learning episode, hence identically to all six arms.
19. **The six arms really do receive one snapshot.** `Crj01ReachGrid.run` captures a single
    `KnowledgeSnapshot` per `(domain, condition)` and passes that same object to every arm's
    `assessScope` and `askInView`; the bindings are the workspace's, read at assessment time.
20. **The production classes are minimal, final and consistent with the repository's style.**
    `ScopeCriterion` and `ScopeStrategy` are enums with substantive Javadoc; `ScopeAssessment` is
    a record (implicitly final) with defensive `List.copyOf` in its compact constructor; the
    `Decision` record is nested in the enum; no new package dependency is introduced
    (`cognitive.report` → `knowledge` already existed); no new infrastructure, store or schema.
21. **The milestone's own bottom line is honest about the role failure.** The milestone document's
    §5, `CASES-PROVENANCE.md` §1 ("**The case author's independence therefore FAILED.**"), the
    `MANIFESTS.md` role table, the evidence README's header, the registry row and the case set's
    `author` field all state the failure plainly and produce a clearly downgraded DEV result.
    Only the two test artefacts (F8) still carry the false attribution.

---

## WHAT I COULD NOT CHECK

1. **Whether the suite still passes.** I was told not to run Gradle, so I did not execute
   `CompetenceReachServedTest` or `Crj01ReachCorpusReportBuilder`. I read the retained JUnit XML
   instead. The GREEN XML (7/0) and the RED XML (7/2) are consistent with each other and with the
   retained rows' timestamps, and the assertions in the source are consistent with the evidence
   they produced — but I did not observe the run. In particular I cannot rule out that the current
   working tree differs from the tree that produced the retained bytes (F8 requires a re-run
   anyway, because the `@DisplayName` is embedded in the retained XML).
2. **The corpus XML of the corpus-report builder.** The catalogue says it is retained; it is in
   neither the evidence directory nor the build directory (F9). I therefore could not check the
   corpus instrument's own assertions against its own retained output; I re-derived the corpus
   report's counts from the corpus bytes instead (correct: 70/70/0).
3. **The `CHANGED_BINDINGS` and `SUPERSEDED` conditions' semantics under a second run.** The
   conditions depend on wall-clock-free but stateful workspace preparation (decay, revision
   selection); a re-run could in principle produce different coverage numbers. I could not check
   run-to-run stability, and the milestone makes no stability claim — but the F2 finding is about a
   *within-run* comparison, which the retained rows settle.
4. **Whether the `ProcedureStore.invalidate` route really is "not wired into the served path"**
   (milestone §4.1(a)). I read `io.matrix.cognitive.algorithms.ProcedureStore` only far enough to
   see that it carries an `invalidate` operation and that `CognitiveStore` has no durable
   equivalent; I did not audit every caller of the in-memory store.
5. **The hidden/private owner state.** The directive forbids reading it and I did not.
6. **The upstream directive's six points.** I did not have the directive text, only the checkpoint's
   digest of it (the local DSH checkpoint §2) and the milestone's own reading of it. My
   findings about the directive are therefore limited to what the checkpoint records.
7. **Whether the corpus-author subagent really did write 4 of the 8 documents.** I took the
   milestone's word for the subagent history; no artefact of that commission is retained.

---

## SUMMARY OF THE FINDINGS

| severity | count | ids |
|---|---|---|
| BLOCKER | 1 | F1 |
| MAJOR | 8 | F2, F3, F4, F5, F6, F7, F8, F9 |
| MINOR | 11 | F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20 |
| NOTE | 3 | F22, F23, F24 |

**The smallest set of changes that makes the milestone's claims true of its own evidence:** F1, F3,
F4 and F5 are four sentences in §3.1/§3.2/§3.3; F2 is one bullet; F6 is one paragraph in §9; F8 is
four strings in two test files (plus a re-run); F9 is four strings in two governance records. F7
is the only finding that needs new code (four checks and two self-test mutations). Nothing in the
production change, in the corpus, in the case set or in the retained rows needs to change — the
measurement is sound; its report is not, yet.
