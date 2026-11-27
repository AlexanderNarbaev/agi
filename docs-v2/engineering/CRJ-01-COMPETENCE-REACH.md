# CRJ-01 / COMPETENCE-REACH — the milestone document

**Baseline:** `7e20e9d9f9945066ebc0120f8e5b379f255ef266` (branch `dsh-writer`) · **Date:** 2026-11-27
**Pre-registration:** `docs-v2/engineering/CRJ-01-REACH-PREREGISTRATION.md` (with the dated §10
amendment, written **before** the first measurement) · **Case set:** `CRJ-01-REACH-DEV-CASES.json` ·
**Corpus report:** `CRJ-01-REACH-CORPUS-REPORT.json` · **Evidence:** `evidence/crj01-reach/`

> **This milestone is DEV-only.** The corpus is synthetic and test-owned, the cases were written
> by the implementer, and no sealed evaluation was executed. Nothing here is a claim about the
> system's behaviour on real data.

---

## 1. The disposition, in one paragraph

The milestone **measured** the two open questions of CRJ-01 §9 and **settled neither of them**. The
pre-registration's vocabulary is per cell, and this document uses it: **DIFFERENCE OBSERVED** and
**NOT ESTABLISHED**, and nothing else.

- **The scope STRATEGY axis.** At `BASELINE` and `CHANGED_BINDINGS` the citation-derived scope and
  the evidence-derived scope are **the same scope** (four documents in both domains), and every arm
  of the two is identical — that comparison is **NOT ESTABLISHED** there. At `SUPERSEDED` they
  **differ** (the citation-derived scope narrows to three documents, because the superseded source
  leaves the applicable view, while the evidence-derived scope keeps four) and the arms differ with
  them (`wbe` 8 against 12 admissions, `hpd` 7 against 11) — **DIFFERENCE OBSERVED** there. The
  **declared** scope is three documents everywhere and differs from both in every cell.
- **The scope CRITERION axis.** The anchor and the claim-sufficiency rule **agree on every case of
  every wide scope** at `BASELINE` and `CHANGED_BINDINGS` — when the scope already holds every
  claim the workspace's own reading cites, the two rules cannot differ — and they **differ on the
  narrow declared scope** and on the wide scopes under `SUPERSEDED`: over the whole grid the two
  rules agree on **189 of the 216** case-arms and differ on **27**. Where they differ, the
  claim-sufficiency rule refuses more and buys complete evidence at the price of **false
  rejections**, whose denominator is the refusals and never the admissions.

Both false admissions and false rejections are counted with **separate denominators**. The
milestone also **falsified** two lifecycle transitions the durable store does not support — the
durable **retire** and the durable **narrow** — and retains the concrete alternative for each.
**No winner is declared, no threshold is proposed, and the milestone does not change the served
default.**

## 2. What was changed in production, and why it is the smallest sound change

| change | file | why |
|---|---|---|
| the criterion as an explicit two-valued rule | `io/matrix/knowledge/ScopeCriterion.java` (new) | the comparison needs two rules that can be selected **per competence**, not a global switch |
| the verdict of a scope assessment | `io/matrix/knowledge/ScopeAssessment.java` (new) | the diagnosis must carry the missing claims, not a boolean |
| the scope strategy as an explicit three-valued rule | `io/matrix/cognitive/report/ScopeStrategy.java` (new) | the three strategies must be selectable per learning episode |
| `assessSupport` delegates to a criterion-aware `assessScope` | `io/matrix/knowledge/KnowledgeWorkbench.java` | the served path must be able to apply either rule |
| the rule is written into the procedure record and read back | `io/matrix/cognitive/report/ReportProcedure.java`, `ReportProcedureMethod.java` | the rule belongs to the competence, so a restart serves the same rule |
| the learner can propose, validate and record a scope by strategy and criterion | `io/matrix/cognitive/report/ReportProcedureLearner.java` | the milestone compares strategies; the incumbent path is unchanged |

**What did NOT change.** The served default. `ScopeCriterion.of(name)` returns the incumbent
`ANCHOR` for an unknown or absent name, so a procedure written by an older writer behaves exactly
as before. The anchor's **reason strings are byte-identical** to the retained ADAPTIVE evidence's
(the milestone checked them against the retained bytes), so no retained record is contradicted.
No store, no schema, no planner, no authorisation and no infrastructure was touched.

**What is DEV-only and must not be read as a decision.** The *strategy* and the *criterion* are
**experimental hypotheses of this milestone**: `DECLARED` and `EVIDENCE_DERIVED` exist only so the
comparison can be made; neither is wired into any served default; the incumbent path
(`CITATION_DERIVED` + `ANCHOR`) is what the served route uses unless a caller says otherwise.

---

## 3. The measured results

### 3.1 The grid

Six arms (3 strategies × 2 criteria) × 2 domains × 3 conditions × 12 evaluation cases =
**432 rows**, every row retained (`evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json`), every
cell summarised (`CRJ-01-REACH-GRID-SUMMARY.json`). All six arms of a cell receive **the same
immutable snapshot, the same bindings and the same history** — and that is **checked from the
measurement, not promised**: every row carries the fingerprint of the view it was measured
against, and the suite and the gate both require the six arms of a cell to carry **one**.

**The scopes are listed in a deterministic order.** The candidate scope is an insertion-ordered
copy of the support set, so two runs of the same measurement list the same sources in the same
order — an immutable set would iterate in a salted order that differs between JVM runs, and the
durable identity of a competence must not depend on the run.

**The scopes** (source count per cell):

| domain | condition | `CITATION_DERIVED` | `DECLARED` | `EVIDENCE_DERIVED` | validated |
|---|---|---|---|---|---|
| wbe | BASELINE | 4 | **3** | 4 | yes (5/5) |
| wbe | CHANGED_BINDINGS | 4 | **3** | 4 | yes (5/5) |
| wbe | SUPERSEDED | 3 | 3 | 4 | **no** (0/5) |
| hpd | BASELINE | 4 | **3** | 4 | yes (5/5) |
| hpd | CHANGED_BINDINGS | 4 | **3** | 4 | yes (5/5) |
| hpd | SUPERSEDED | 3 | 3 | 4 | **no** (0/5) |

⇒ **The two evidence-derived strategies coincide at `BASELINE` and `CHANGED_BINDINGS`**: the
journey's citations and the reading's reach carry the same sources there, so their comparison is
**NOT ESTABLISHED** in those cells — not because it was skipped, but because the two rules produced
the same object. **At `SUPERSEDED` they differ**: the citation-derived scope narrows to three
documents (the superseded source leaves the applicable view, so the journey's citations no longer
include it) while the evidence-derived scope keeps four, and the arms differ with them. The
`DECLARED` scope (the three documents that bound the learning) is **strictly narrower** than both
in every cell, and that difference is measurable.

### 3.2 The arms at `BASELINE` — the two denominators, separate

| domain | strategy | criterion | admissions | refusals | false admissions / denom. | false rejections / denom. | mean completeness | mean coverage |
|---|---|---|---|---|---|---|---|---|
| wbe | CITATION_DERIVED | ANCHOR | 12 | 0 | 6 / 12 | 0 / 0 | 1.000 | 0.380 |
| wbe | CITATION_DERIVED | CLAIM_SUFFICIENCY | 12 | 0 | 6 / 12 | 0 / 0 | 1.000 | 0.380 |
| wbe | DECLARED | ANCHOR | 10 | 2 | 6 / 10 | 0 / 2 | 0.950 | 0.385 |
| wbe | DECLARED | **CLAIM_SUFFICIENCY** | **8** | **4** | 5 / 8 | **1 / 4** | 1.000 | 0.380 |
| wbe | EVIDENCE_DERIVED | ANCHOR | 12 | 0 | 6 / 12 | 0 / 0 | 1.000 | 0.380 |
| wbe | EVIDENCE_DERIVED | CLAIM_SUFFICIENCY | 12 | 0 | 6 / 12 | 0 / 0 | 1.000 | 0.380 |
| hpd | CITATION_DERIVED | ANCHOR | 11 | 1 | 6 / 11 | 0 / 1 | 1.000 | 0.439 |
| hpd | CITATION_DERIVED | CLAIM_SUFFICIENCY | 11 | 1 | 6 / 11 | 0 / 1 | 1.000 | 0.439 |
| hpd | DECLARED | ANCHOR | 9 | 3 | 6 / 9 | 1 / 3 | 0.833 | 0.436 |
| hpd | DECLARED | **CLAIM_SUFFICIENCY** | **5** | **7** | 2 / 5 | **3 / 7** | 1.000 | 0.439 |
| hpd | EVIDENCE_DERIVED | ANCHOR | 11 | 1 | 6 / 11 | 0 / 1 | 1.000 | 0.439 |
| hpd | EVIDENCE_DERIVED | CLAIM_SUFFICIENCY | 11 | 1 | 6 / 11 | 0 / 1 | 1.000 | 0.439 |

**The completeness column is the mean over the ADMITTED rows.** A refused row was served by the
reference, whose completeness is 1.0 by construction, so a mean over every row would mix the two
paths and flatter the arm that refuses more. The fallbacks are counted beside it.

**What the table says, precisely.**
1. **The criterion changes nothing on a scope that already holds the evidence.** At `BASELINE` and
   at `CHANGED_BINDINGS` the anchor and the claim-sufficiency rule produce *identical* numbers on
   the two wide scopes, in every case of every domain: when the scope holds every claim the
   workspace's own reading cites, the two rules cannot differ. Over the whole grid the two rules
   agree on **189 of the 216** case-arms and differ on **27**; of those 27, **17** are in the
   declared arm and **10** are in the wide arms under `SUPERSEDED`, where the scope can no longer
   carry what the workspace's own reading cites.
2. **The criterion changes the narrow scope.** On the declared scope the claim-sufficiency rule
   refuses **twice as many** cases (4 against 2 in `wbe`, 7 against 3 in `hpd`) and buys complete
   evidence (mean completeness over the admitted rows 1.000 against 0.950 and 0.833) — at the price
   of **false rejections**: one in `wbe`, three in `hpd`, each of them a case the scope *could*
   have answered, which falls back to the reference.
3. **The false admissions are mostly a disposition mismatch, not a missing evidence.** In the
   `BASELINE` cells the **67** false admissions are: 12 `INCOMPLETE` cases answered `ANSWERED`
   where the oracle allows `QUALIFIED`/`ABSTAINED`; 20 `MISLEADING_RELEVANCE` cases answered
   `QUALIFIED` (15) or `ABSTAINED` (5) where the oracle allows only `ANSWERED`; 10 `CORROBORATED`
   and 12 `PLAIN`/`PARAPHRASE` cases answered `QUALIFIED`; 6 `MULTI_SOURCE` cases answered
   `QUALIFIED` (5) or `ANSWERED` (1); 1 `AMBIGUOUS` case answered `QUALIFIED`; and **6
   `UNSUPPORTED` cases answered at all** — the workspace answered a question about a subject the
   corpus says nothing about. The breakdown by the kind of the case is retained per cell in the
   summary (`byKind`) and the gate re-derives it from the rows.
   ⇒ **The measured damage of the anchor on this corpus is NOT mainly "the anchor admits a
   degraded answer"; it is that the served answers are systematically hedged where the oracle
   demands a plain one, and that the system answers questions the corpus cannot support.** The
   anchor's blind spot is real but small here: **3 of the 32 admissions of the declared arms**
   (of 124 in the whole grid) were admitted **and** incomplete, and all three are in the
   **declared** scope of the anchor — a multi-source case answered `ANSWERED`, a
   misleading-relevance case abstained, and an ambiguous case qualified; the claim-sufficiency
   rule refuses all three instead, and creates 4 false rejections where the anchor created none.
4. **At `BASELINE` the false rejections of the wide scopes are zero**: all 5 false rejections of
   that condition belong to the `DECLARED` arm, i.e. to a scope that was deliberately narrowed.
   Over the whole grid that is no longer true — at `SUPERSEDED`, where the evidence under the
   scope has been replaced, **7 of that condition's 8** false rejections are on the **wide**
   scopes. Over the three conditions the grid carries **17** false rejections: 5 at `BASELINE`,
   4 at `CHANGED_BINDINGS` (all four on the declared scope) and 8 at `SUPERSEDED`.

### 3.3 The conditions

- **`CHANGED_BINDINGS`** (the production `recordLearningErrors` + `applyErrorDrivenDecay` path):
  the condition changes **2 of the 144** verdicts against `BASELINE`, both in the **declared** arm
  of `hpd` (one case that was admitted is refused, one that was refused is admitted); the other
  142 verdicts are identical, and the coverage moves only in the third decimal (0.380 → 0.378 in
  `wbe`). The weakened bindings therefore did **not** decide the comparison, and the condition
  was applied identically to all six arms, so it cannot have confounded it.
- **`SUPERSEDED`** (the production `selectSourceRevision` chain): **no scope could be validated**
  (agreement 0.0 on the held-out cases in every cell) and the mean completeness collapses
  (0.625 in `wbe`, 0.458 in `hpd`): after the supersession the restricted reading can no longer
  carry the evidence the oracle requires, so false admissions rise to **66 of 97** admissions and
  false rejections to **8 of 47** refusals, and the mean completeness over the admitted rows
  falls to **0.620** in `wbe` and **0.407** in `hpd`. Here the criterion's difference is largest
  (`hpd`/`CITATION_DERIVED`: the anchor admits 7 with **0** false rejections, the
  claim-sufficiency rule admits 1 with **3**) — a narrow scope under a superseded source is
  exactly where the two rules part company. The same condition is also where the two
  evidence-derived **strategies** part company (§3.1).

### 3.4 The cost — records, never assertions

Every arm records a selection cost, a read cost and a fallback cost, and every cell records the
proposal and the validation cost separately (`summary.cells[].strategies[].proposalNanos` /
`validationNanos`). **No timing assertion is made anywhere**: the milestone does not claim that
one strategy is faster, and the numbers are wall-clock records of one DEV run on one machine.
They are retained because the pre-registration asks for the full cost, not because they decide
anything.

**Three things about the cost fields a reader must know, because the fields alone would mislead.**
(i) The **restricted reading is measured once per scope** and shared by the two criteria of that
scope — the rule decides the admission, not the reading — so the two criteria's read costs are
**the same by construction** and must never be read as a difference between the rules. (ii) A
**refused row served nothing from the scope**: its read is zero and the reference reading is
counted **once**, as the fallback. (iii) The reference reading is measured **once per case** and
shared by every arm that falls back.

### 3.5 What is **not** claimed

- No winner. No threshold. No universal rule about scopes or criteria.
- Nothing about the system's behaviour on real data: the corpus is synthetic and test-owned.
- Nothing about the two strategies that coincided **at `BASELINE` and `CHANGED_BINDINGS`**:
  their comparison is **not established in those cells** (they differ at `SUPERSEDED`).
- No claim that the anchor is *wrong*. The measurement shows where the two rules differ on this
  corpus and what the difference costs; it does not say which rule should be served.

---

## 4. The served lifecycle, walked on the served route

`io.matrix.knowledge.CompetenceReachServedTest` walks the lifecycle through the **served
planner, the authorisation, the procedure contract and the durable store** — no new architecture:

| step | what the milestone observed |
|---|---|
| the observable failure | the served answer under the incumbent rule **omits a necessary claim** of the oracle — the failure is on the served route, not on an instrument |
| the diagnosis | the competitor rule, on the **same scope and the same question**, refuses and **names the claims the scope does not carry** (`ScopeAssessment.missingClaimIds`) |
| the inspectable, versioned proposal | the widened candidate is a **separate, inspectable record**: its identity is derived from its scope, its scope is in the support set, and its rule is in the record's applicability field (`criterion=…`) when the rule is not the incumbent — a record that names no rule keeps the version `1.0.0` the previous codec wrote |
| the separate validation | the validation is a **separate door** (`learner.validate(...)`), runs on the held-out cases and reports its own agreement |
| the promotion and the rejection | one candidate is promoted, one is **refused** with a named reason; the refusal leaves no procedure behind |
| after a restart | the **exact** promoted method serves a **different** question, disjoint from the proposal and the validation slices, and its answer differs from the predecessor's |
| the reference fallback | the refusal of the narrow scope and the fallbacks of the grid are **before** any wrong durable effect: the fallback carries its own reason |

### 4.1 The two transitions that were **falsified**, with the concrete alternative retained

**(a) The durable RETIRE is not supported.** `CognitiveStore.recordProcedure` compares the
**definition** of a version, and `invalidatedAt` is **not** part of that definition. The write of
a retired version of a promoted competence is therefore **accepted as "the same definition" and
dropped** — the stored procedure keeps `invalidatedAt = null`, stays `active()`, and the route
keeps serving it. There is **no durable retire transition**, and the milestone does not pretend
there is.
*Concrete alternative (an owner-level architectural commitment, kept in the DEV instruments and
not decided here):* add an explicit `retireProcedure(workspaceId, id, version, at)` operation to
the durable store that writes the retirement instant, with the same immutability rule as the
definition — or route the retirement through the in-memory `ProcedureStore.invalidate`, which
exists but is **not wired into the served path**.

**(b) The durable NARROW is not supported.** A narrower scope is a **different competence
identity**: `ReportProcedure.idFor(support)` derives the identity from the support set alone, so
a narrow is not a new version of the wide competence but a second competence. Three observations
are on the record: (i) a too-narrow candidate is **refused by the validation** — the restricted
reading over the narrow scope no longer reproduces the reference on the held-out cases; (ii) even
a promotable narrow would carry **its own id**, and the predecessor stays `active()` and served;
(iii) the route has no transition that hands the serving over from one to the other.
*Concrete alternative:* the narrow is written under a **new id** and the predecessor is retired
through the operation of (a) — which is why (a) is the blocking decision for (b).

---

### 4.2 The two checks this milestone leaves behind

The milestone cost most of its time on two traps that were **not** visible in the code, and it
leaves two checks behind so that neither trap can be entered again silently.

**The case set's coherence with its corpus.** A case whose gold the workspace's reading cannot
reach measures the retriever, not the scope; and a held-out question that reaches a document the
declaration leaves out can never reproduce the reference — which empties the declared arm without
any visible failure. Both are now asserted: every evaluation case's gold is reachable, and every
held-out question reaches only the declared documents. (This corpus needed the labelling document
to be rewritten with a distinct vocabulary before the held-out slice could satisfy the second
property at all.)

**The incumbent's contract.** The anchor's reason is what the prior milestone's retained evidence
records, and the milestone's change touches the path that produces it. The suite now reads the
prior milestone's retained evidence **read-only** and asserts that the **form** of the reason the
current code produces — both the admitting one and the refusing one — is one that evidence
records. A silent rewording of the incumbent's sentence would fail the reach suite.

## 5. The roles — and what each one did

| role | who | what they did | independence |
|---|---|---|---|
| implementer | this DSH session | the production changes, the corpus bytes, the instruments, the case set, the milestone document | — |
| corpus author | `corpus-author` (DSH subagent) + the implementer | the subagent was given the **corpus contract only** — never the oracle contract — and wrote **4 of the 8** documents before it stopped (tool limit); the implementer wrote the other four and the repairs | **NONE** — the corpus is the implementer's; what that does not affect is the oracle (the answers are extracted from the corpus by the production acquisition path), and what it does affect is the CHOICE of the sentences |
| case author | `case-author-2` (DSH subagent) — **did not deliver** | the subagent was commissioned with only the corpus and the oracle contract and produced no case set (tool limit, twice) | **NONE** — the cases below are the implementer's |
| reviewer | an independent DSH reviewer, commissioned on the final stable snapshot | see §8.3 | the review was **adversarial** and found a BLOCKER and eight MAJOR defects, all of which were corrected |

### 5.1 The independent development gate — **it FAILED, and this is what was done about it**

The directive's first requirement is a **separate case author** with only the corpus and the oracle
contract, and it says plainly what to do when the independence fails: *allow a clearly downgraded
DEV result, with no false role attribution*. **That is the branch this milestone is on, and the
failure is stated here rather than worked around.**

- **The gate failed.** Two separately commissioned DSH subagents were given the corpus and the
  oracle contract — no proposed criterion, no proposed strategy, no implementation — and neither
  delivered a case set; both stopped at the tool limit. The gate that asked for an independent
  oracle was **not satisfied**.
- **The root cause was worked around, not hidden.** The cases are now **generated** by
  `scripts/reach-cases.py` from the corpus bytes, and the generator is retained beside the case
  set it wrote, so every case, every gold and every digest can be re-derived. The two properties
  that had been silently broken by the hand-written set — that every gold is REACHABLE by the
  workspace's reading, and that every held-out question stays INSIDE the declared scope — are
  now **asserted by the suite** (the seventh test) rather than hoped for. They were not: the
  hand-written set reached documents outside the declaration, which would have emptied the
  declared arm and made the strategy comparison vacuous.
- **The downgrade is total and visible.** The oracle is **not independent**; the case set is the
  implementer's; the measurement is **DEV**; the milestone claims **no winner, no threshold and
  no adoption**; the served default is unchanged; and every artefact that states the roles says
  the same thing — the milestone document, `CASES-PROVENANCE.md`, the evidence `README.md` and
  `MANIFESTS.md`, the catalogue entry, the registry row, the case set's own `author` field, and
  **the instruments themselves** (the suite's display name and the case set's header).
- **What the downgrade does NOT excuse.** The milestone may not be read as a property of the
  system: the corpus is synthetic and test-owned, the cases are 24 per domain written by the party
  that wrote the mechanism, and the sealed evaluation is **NOT EXECUTED**. The next milestone must
  commission the independent case author again — with a shorter, bounded task, because the two
  failures were tool-limit failures rather than refusals — or must state that the oracle remains
  the implementer's.

**The case set** (`CRJ-01-REACH-DEV-CASES.json`, 40 cases): three disjoint slices per domain —
`PROPOSAL` (3, the learning episode), `VALIDATION` (5, the held-out cases the promotion is decided
on), `EVALUATION` (12, the measured cases) — with the necessary evidence of every case named as
the **exact sentence of the corpus**, the allowed epistemic dispositions, and the
corroboration/contradiction conditions. It contains, by construction, natural paraphrases,
misleading relevance, multi-source necessity, an ambiguous concept, an incomplete case and an
unsupported case in both domains. Its provenance, the corpus digests and the authoring record are
in `evidence/crj01-reach/CASES-PROVENANCE.md`.

---

## 6. The sealed evaluation — **NOT EXECUTED**

No sealed evaluation was executed and no isolation was re-probed; the ADAPTIVE milestone's
retained verdict on isolation is reused as it stands. This milestone is DEV-only and says so in
every artefact it writes.

---

## 7. The commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

# the corpus report — the production import path, every sentence accounted for
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder"

# the serving suite — the milestone's instrument
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.CompetenceReachServedTest"

# the retained evidence: the XML of the last run, and the manifest over the directory
./scripts/reach-retain-evidence.sh green

# the gate — the verdict is its exit code
./scripts/reach-gate.sh

# the gate's self-test — a gate that cannot go red is not a gate
./scripts/reach-gate-selftest.sh
```

> **`--rerun` is mandatory.** The case set and the corpus are not declared inputs of the test
> task, so Gradle considers the task up-to-date and keeps the **previous** XML. This happened
> twice during the milestone; every retained XML in `evidence/crj01-reach/` was produced with
> `--rerun`.

### 7.1 The RED reproduction — the procedure, not a claim

`CRJ-01-REACH-REPRODUCTION-no-criterion.xml` is the XML of a run in which the **competitor rule
was collapsed onto the incumbent** — one expression, `ScopeCriterion.CLAIM_SUFFICIENCY.judge`
delegating to `ANCHOR.judge` — and the file was **restored byte-for-byte** afterwards (the
restoration is verifiable: the file carries no such delegation). **2 of the 8 tests fail**: the
grid-level criterion difference and the lifecycle's diagnosis. That is what the criterion switch
buys, shown as a reproduction rather than asserted.

---

## 8. The evidence

| artefact | what it is |
|---|---|
| `evidence/crj01-reach/CRJ-01-REACH-GRID-ROWS.json` | every per-case row of every arm, cell and condition (432 rows) |
| `evidence/crj01-reach/CRJ-01-REACH-GRID-SUMMARY.json` | the per-cell aggregation with the two denominators separate, the breakdown by the kind of the case, and no winner |
| `evidence/crj01-reach/TEST-io.matrix.knowledge.CompetenceReachServedTest.xml` | the GREEN run of the serving suite |
| `evidence/crj01-reach/CRJ-01-REACH-REPRODUCTION-no-criterion.xml` | the RED reproduction |
| `evidence/crj01-reach/CHECKSUMS.sha256` | the manifest of the directory |
| `evidence/crj01-reach/README.md` | what each artefact is, how it was produced and how to re-check it |
| `evidence/crj01-reach/MANIFESTS.md` | the provenance table: the command, the input hashes and the role behind every file |
| `evidence/crj01-reach/CASES-PROVENANCE.md` | the case set's provenance: the authoring record, the corpus digests and the slice contract |
| `evidence/crj01-reach/TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml` | the corpus instrument's XML: the corpus measured through the production acquisition path, with no sentence left unextracted |
| `evidence/crj01-reach/REVIEW.md` | the independent reviewer's findings, and what each of them changed (§8.3) |
| `evidence/crj01-reach/REVIEW-2.md` | the reviewer's re-check of the resolutions |

The volatile test output (`matrix-core/build/**`) is **not** retained; the retained directory
holds the bytes a reader may re-check against the manifest.

### 8.1 The regression the catalogue's own condition required

The catalogue's `SCOPE-CRJ01-ADAPTIVE-COMPETENCE` entry says that **a change to the
applicability criterion requires that scope to be re-run**. This milestone changed that path
(`assessSupport` now delegates to a criterion-aware `assessScope`), so the ADAPTIVE scope and the
H2 scope were re-run in this session against the milestone's production changes and **both
passed**. The ADAPTIVE suite's own writer rewrites the prior milestone's retained evidence and the
three legacy evidence files, so those bytes were **restored from HEAD** afterwards:
`git status` shows `CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json` and
`CRJ-01-FREEZE-RECORD.json` unmodified, and the ADAPTIVE evidence directory's own
`CHECKSUMS.sha256` verifies (15 files, 0 failures). **The prior milestone's evidence bytes are
unchanged.**

### 8.2 The strict validations

| validation | result |
|---|---|
| `python3 scripts/validate-test-catalogue.py --strict` | **0 defects** (19 scopes) — *only with `GIT_CONFIG_COUNT` unset: the broken git environment of this host makes every `git cat-file` fail, and then **every** entry, including the pre-existing ones, is reported as `UNKNOWN_COMMIT`* |
| `python3 scripts/validate-project-registry.py --strict` | **0 defects** (57 capabilities, 24 fields each) |
| `bash scripts/validate-docs.sh` | the two reported "Liquid syntax issues" are **pre-existing** and are in `docs-v2/engineering/W48-CI-RECONCILIATION.md` and `docs-v2/TRANSFORMATION-PLAN.md` — the two GitHub Actions `github.ref` expressions, whose double-brace form the scanner reads as Liquid. **No file of this milestone contains one** (this line deliberately names the expression without its braces, so that the milestone does not add a third). The script's own summary line reports `FAILED` because of them; that condition is the repository's, not this milestone's |
| `bash scripts/reach-gate.sh` | **23 checks, 0 failures** |
| `bash scripts/reach-gate-selftest.sh` | **15 mutations, 15 caught by the check they belong to** |

### 8.3 The independent review, and what each finding changed

An independent DSH reviewer read the final stable snapshot and wrote
`evidence/crj01-reach/REVIEW.md`. Its verdict was **partly**: the measurement was sound and
reproducible, but **the prose that reported it was not** — four wrong numbers, a headline claim
contradicted by the milestone's own table, a §9 premise refuted by the milestone's own rows, a
gate that could be fooled by the corruptions the claims depend on, and false role attributions in
two test artefacts. **Every finding was resolved**, and the resolutions are recorded here.

| finding | severity | the resolution |
|---|---|---|
| **F1** the strategy axis was reported as "the same scope in every cell" | BLOCKER | **The claim was false and is corrected.** At `SUPERSEDED` the citation-derived scope narrows to three documents while the evidence-derived keeps four, and the arms differ (`wbe` 8 against 12 admissions, `hpd` 7 against 11). §1 and §3.1 now say **NOT ESTABLISHED** at `BASELINE`/`CHANGED_BINDINGS` and **DIFFERENCE OBSERVED** at `SUPERSEDED`; the registry's two statements were corrected too |
| **F2** the `CHANGED_BINDINGS` verdicts were reported as identical to `BASELINE` | MAJOR | **Corrected**: the condition changes **2 of the 144** verdicts (both in the declared arm of `hpd`), and the other 142 are identical |
| **F3** the `SUPERSEDED` false rejections were quoted from a stale run | MAJOR | **Corrected**: the anchor admits 7 with **0** false rejections, the claim-sufficiency rule admits 1 with **3** |
| **F4** "2 false admissions" and "2 of 66 admissions" | MAJOR | **Corrected**: **3** admitted-and-incomplete admissions, **3 of the 32** admissions of the declared arms (of 124 in the grid) |
| **F5** "every false rejection belongs to the declared arm" | MAJOR | **Corrected and scoped**: that is true at `BASELINE` (5 of 5); at `SUPERSEDED` **7 of that condition's 8** are on the wide scopes, and the grid as a whole carries 17 (5 + 4 + 8) |
| **F6** the §9 premise ("only where a question needs two necessary claims, two cases of 24") | MAJOR | **The premise was refuted by the milestone's own rows and §9 was rewritten**: **eight** of the 24 cases carry two or more necessary claims, and one of the three anchor blind-spot instances needs only **one** — the blind spot is about the claim the reading cites **first**, not about the number of claims |
| **F7** the gate could be fooled by a mislabelled kind, a forged scope, a falsified validation outcome and a falsified breakdown | MAJOR | **The gate gained four checks** (four more were added by the second review, §8.4 — 23 in all): every row must name the case of the case set it measures with that case's own kind and answerability; every arm's scope must be the scope the summary records, with its validation outcome consistent with its own agreement; the breakdown by the kind of the case must be re-derived from the rows; the six arms of a cell must carry **one input fingerprint**. The self-test gained the matching mutations and now refreshes the copy's manifest first, so that no mutation is caught by the manifest alone |
| **F8** false role attribution in the instruments | MAJOR | **Corrected**: the suite's display name now says "the case set is the **IMPLEMENTER's own**", and the case set's header states that the implementer wrote both the cases and the measurement — and why that is the milestone's principal limitation. The display name is in the retained XML, so the suite was re-run and both XMLs re-retained |
| **F9** the catalogue claimed a retained corpus XML that was not retained; the registry repeated the false statements | MAJOR | **Corrected**: the retention script now keeps the corpus instrument's XML and it is in the evidence directory; the catalogue's evidence text names the two files that are there; the registry's count and its two false statements were corrected |
| **F10** the `DECLARED` rule's production Javadoc still described the pre-§10-amendment refusing semantics | MINOR | **Corrected**: the Javadoc now describes the bounding rule and names the amendment and its reason |
| **F11** "IS ESTABLISHED" was a third, grid-level verdict | MINOR | **Corrected**: the document uses only the pre-registered vocabulary, per cell and per axis |
| **F12** the false-admission breakdown did not sum to the total | MINOR | **Corrected**: the breakdown now lists every kind (67 in all) and the summary carries it per cell as `byKind`, re-derived by the gate |
| **F13** "33 fields each" | MINOR | **Corrected**: the rows carry 33 fields — the count is right again after the input fingerprint was added |
| **F14** "no necessary gold is used by two cases", and a vacuous disjointness assertion | MINOR | **Corrected**: the claim now says what is true (no gold crosses a slice; three golds are shared inside the evaluation slice), and the suite asserts the property that matters — that no gold of one slice is a gold of another |
| **F15** the summary's verdict ignored the admissions count | MINOR | **Corrected**: the verdict now reads the admissions as well, so two arms that differ in how many cases they admitted are not reported as equal |
| **F16** the mean completeness mixed the arm's path with its fallbacks | MINOR | **Corrected**: the mean is over the **admitted** rows, with a note that says why |
| **F17** the read cost is shared by the two criteria of a scope | MINOR | **Corrected**: the cost note says so, a refused row's read is now **zero** (the reference reading is counted once, as the fallback), and §3.4 states the three things a reader must know about the cost fields |
| **F18** the case set's author field called the corpus author a case author | MINOR | **Corrected**: the author field names the implementer as the case author and records the corpus's authorship separately |
| **F19** "the corpus carries no gold" is not airtight | MINOR | **Corrected**: every gold **is** a sentence of the corpus; what the corpus's authorship does not affect is the oracle, and what it does affect is the difficulty of the cases |
| **F20** the pre-registered breakdown by the kind of the case was missing | MINOR | **Corrected**: the summary carries it per cell and per arm, and the gate re-derives it |
| **F21** the generator's docstring said 36 cases | MINOR | **Corrected**: 40 |
| **F22** the manifest did not cover the reviewer's report | NOTE | **Corrected**: the retention script refreshes the manifest after every copy, and the report is in the manifest |
| **F23** the grid is instrument-level | NOTE | **Stated**: §8 and the evidence README now say that only the lifecycle test drives the served route, the durable store and a restart |
| **F24** the `last_verified_commit` fields name the baseline | NOTE | **Stated**: the coordinator updates them at integration; the milestone writes no commits |

**Nothing in the production change, the corpus, the case set or the retained rows had to change** for
the findings to be resolved: the corrections are in the prose, in the instruments' honesty, in the
gate and in the summary's semantics.

### 8.4 The second review — the re-check of the resolutions

The same reviewer was asked to re-check the fixes on the final stable snapshot and wrote
`evidence/crj01-reach/REVIEW-2.md`. Its verdict: **BLOCKER 0, MAJOR 1, MINOR 13, NOTE 4**, and
**"the milestone may be integrated as a DEV result once the MAJOR is corrected"**. The MAJOR was
that **the BLOCKER's sentence survived in two artefacts** — the evidence README's "What this
evidence does NOT show" and the catalogue's `what_it_does_not_prove` — while the milestone document
and the registry had been corrected; both were corrected, and the remaining MINORs rode along:

| finding | the resolution |
|---|---|
| **M1** the strategy-axis sentence survived in the evidence README and the catalogue | **both corrected**: the comparison of the two evidence-derived strategies is **not established** at `BASELINE` and `CHANGED_BINDINGS` and **is established** at `SUPERSEDED` |
| **m1** the provenance named three shared golds | **corrected**: **one** gold is shared inside the evaluation slice (`RC-WBE-13` and `RC-WBE-14`) |
| **m2** §3.2 and §3.3 still quoted the old completeness metric | **corrected**: 0.950 and 0.833 in the table, and the `SUPERSEDED` means are now stated as 0.620 and 0.407 |
| **m3** the document's validation table said 17 checks and 6 mutations | **corrected** |
| **m4/m5/m6** the manifests, the catalogue's gate entries, the registry and the checkpoint quoted the old gate | **all corrected** |
| **m7** the gate still accepted a falsified mean, a falsified verdict and a restamped cell | **the gate gained two more checks** (23 in all): the means and the fallbacks are re-derived from the rows, and **every cell's verdict is re-derived from the arms it compares**; every cell must also carry **every case of its domain in every one of the six arms, once**. The self-test gained the matching mutations (**15** in all) |
| **m8** the corpus was still described as carrying no gold, and the kind list was short | **corrected**: the corpus's authorship does not affect the oracle and does affect the difficulty; the kind list now names all nine kinds, and the validation slice's gold placement is stated for **both** domains |
| **m9** the `ScopeStrategy.DECLARED` Javadoc still described the pre-amendment refusing rule | **corrected** |
| **m10** §3.4's opening contradicted the three caveats below it | **corrected** |
| **m11** §3.5's bullet was unqualified | **corrected**: the comparison is not established **at `BASELINE` and `CHANGED_BINDINGS`** |
| **m12** §4's table called the widened candidate `1.1.0` with the rule in the record | **corrected**: the rule travels in the applicability field **when it is not the incumbent**, and a record that names no rule keeps the version `1.0.0` the previous codec wrote |
| **m13** the case set's independence record credited the corpus author with the oracle contract | **corrected**: the corpus author was given the **corpus contract only** |
| **NOTE 1** the fingerprint proves sameness of the label, not of the view | **stated**: §3.1 and the evidence README say the fingerprint proves that the six arms of a cell were measured against **one recorded input**; it does not re-derive the view |
| **NOTE 2** the manifest did not cover the second review | **corrected**: the retention script refreshes it |
| **NOTE 3** the row count was checked globally | **corrected**: the per-cell check above |
| **NOTE 4** the validation outcome remains a producer claim | **stated**: the gate checks that the summary's outcome is **consistent with its own agreement** and with the scope the rows carry; it cannot re-run the held-out validation, because it does not hold the workspace |

**The second review's own verdict on the headline claims**: the strategy axis, the criterion axis,
the two separate denominators, the two falsifications, the unchanged served default and the honest
role record are **supported by the milestone's own evidence**, and **the milestone may be
integrated as a DEV result**.

---

### 8.5 The CI regression of PR #89 — the legacy report-learning suites, and what it found

The required GitHub CI of the pull request caught **five failures in the legacy
report-learning suites** (`ReportProcedureLearningTest` at four places and
`ReportProcedureMeasuredCostTest` at one), every one of them a `NullPointerException` inside
the learner's `learn(...)`. The suite is reproduced here, and the failure has **two** causes.

**The first cause — the crash.** A workspace may hold a claim that names **no introducing
source**. Such a claim is in no support set, and the restriction has always left it out — the
learner handed the reading path a null-tolerant set, and a null-tolerant set answers "no"
about a null key. The milestone's own candidate scope was made an **immutable** copy of the
support set, and an immutable set **throws** when it is asked about a null key: the restricted
reading of such a workspace died instead of reading. The fix is in the **reading path**, not in
the caller — `KnowledgeWorkbench.restrictedTo` now says out what it means
(`KnowledgeWorkbench.java`, the two filters of the restricted view): a claim whose introducing
source is null is not in any support set and is left out, and a source with no id is left out
too. **The outcome is the one the null-tolerant set gave**, and the fix protects every caller,
including the served path.

**The second cause — the order.** The same immutable copy also **randomised the order** of a
scope: an immutable set iterates in a salted order that differs between JVM runs, so the
durable identity of a competence and every artefact that lists a scope differed between two
runs of the same measurement. The candidate scope is now an **insertion-ordered** copy
(`ReportProcedureLearner.Scope`), which is also the order the learner has always built it in.

**What the fix did NOT change.** Every number of the milestone. The retained rows were
compared with the committed ones field by field: the counts, the verdicts and the reasons are
identical, and the scope **sets** are identical; only the scope **lists** were reordered, the
timings changed, and the snapshot identity changed — the three things a re-run always changes.
The evidence was refreshed, the gate re-ran (**23 checks, 0 failures**) and the self-test
re-ran (**15 mutations, 15 caught**).

**The guard.** A new regression test
(`io.matrix.knowledge.RestrictedViewNullIntroducedClaimTest`) asks the restriction for an
**immutable** support set over a workspace that holds a claim with no introducing source: it
must not throw, and that claim must be absent from the restricted view. The test **fails
without the fix** and passes with it.

**One thing that was checked and found innocent.** Running the prior milestone's own suite
rewrites that milestone's retained evidence, and the rewritten file differed from the committed
one. The difference was traced with an A/B run — the milestone's production files reverted to
the baseline and the suite run again — and **the baseline produces the same difference**. The
cause is the **worktree's split file**, not the milestone's code: the committed ADAPTIVE
evidence was measured against a different split than the one this worktree holds. The prior
milestone's evidence was therefore **restored byte-for-byte from `HEAD`** and its
`CHECKSUMS.sha256` verifies (15 files).

## 9. The next scientific decision

**The next decision is NOT "which criterion should be served".** It is narrower, and it is
answerable with the machinery this milestone already has:

> **How often does the anchor admit a scope that holds the claim the workspace's reading cites
> FIRST but not a claim the answer needs — and what does the claim-sufficiency rule's
> false-rejection rate cost on the same corpus?**

**The premise this milestone's own rows corrected.** The first version of this section said that
the anchor's blind spot is observable only where a question needs **two** necessary claims, and
that this corpus produced two such cases of twenty-four. **The rows say otherwise**: **eight** of
the twenty-four evaluation cases carry two or more necessary claims, and **one of the three
anchor blind-spot instances needs only ONE** — `RC-HPD-13`, whose gold lies outside the declared
scope while the claim the workspace cites first lies inside it. The blind spot is therefore not
about the number of necessary claims at all:

> **The anchor asks whether the scope holds the FIRST claim the workspace's reading would cite.
> The blind spot is every case whose answer needs a claim the reading reaches LATER — whether
> that is the second of two necessary claims or the only one, as long as it is not the first.**

The next milestone should therefore **construct** such cases deliberately — a question whose
reading cites a claim inside the scope first and a claim outside it afterwards, and whose answer
needs the latter — and measure, on that corpus:

1. how often the anchor admits a scope that cannot carry the answer, over the cases of each kind
   (a second necessary claim, a single later claim), with the **false admissions counted over the
   admissions**;
2. what the claim-sufficiency rule's **false-rejection rate** is on the same corpus, with the
   **refusals** as its denominator and the two denominators never folded together;
3. whether the **declared** scope — the only scope whose width differs — changes either number,
   and whether the strategy's difference under `SUPERSEDED` (the citation-derived scope narrows,
   the evidence-derived scope does not) survives a corpus with the same shape.

**The decision that must NOT be taken from this milestone:** that the claim-sufficiency rule
should replace the anchor, or that the declared scope should become the default. Neither is
supported: the comparison against the anchor was made on 24 DEV cases written by the implementer,
and the two strategies that were supposed to be compared produced identical scopes in half of the
cells.
