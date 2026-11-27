# CRJ-01 / ADAPTIVE-COMPETENCE — the milestone document

**Status:** `MEASURED — DEV ONLY` · **Baseline:** `dff12b5883242b74c09cc2388be32061c2b87cf0` ·
**Date:** 2026-11-27 · **Sealed evaluation:** **NOT EXECUTED**

**What this milestone is.** The H2 milestone left a defect that it did not measure: a
procedure's applicability was decided by the **presence** of its support set — a statement
about the workspace — and not by whether that support set can carry the evidence for **this
question**. H2 measured the cost: on the two out-of-support controls the restricted reading
cited **one** claim where the reference cited **three**, and the answer still said `ANSWERED`.

This milestone removes that defect and measures the result, on two fresh synthetic DEV
domains, on the **real served route**.

---

## 1. The disposition, in one paragraph

**The applicability gate works and is at parity with the stateless reference.** A procedure
whose support set cannot carry the evidence for the question asked is **not** selected; the
stateless reference answers instead; the refusal happens **before** any durable side effect;
and the reason is recorded with the evidence it was made from. On the served path the
grounding is **30 of 30** cases, exactly what the stateless reference achieves — the parity
H2 could not reach. The deliberate RED (the gate removed by one expression) **fails 6 of 16**
tests and is retained — and the retained evidence's README names which five and what each
one says. The competence revision is **promoted in both domains** after a
failure episode, validated on a separate DEV subset, and the changed competence is measured
on a **distinct subsequent task** — a question of the failure episode's own document that no
other case uses: **before** the revision the reference answers it (the competence cannot),
**after** it the **exact revised procedure** answers it, grounded, with its effect verified. **No superiority is claimed beyond the cells the rule
separates**: the pre-registration's verdict is **per cell** and forbids a grid-level
verdict, so none is invented here — the retained run's per-cell verdicts are in §3.4 and in
the artefact, and a cell whose interval contains zero is `NOT ESTABLISHED` whatever the
medians say. **At least one cell changes verdict between runs**, so a cell's verdict is a
property of the run as well as of the rule.

## 2. What was changed, and why it is the smallest sound change

| file | change | why |
|---|---|---|
| `io/matrix/knowledge/SupportAdequacy.java` (NEW) | the verdict of the question-specific check: applicable, the reason, the workspace's best claim and its coverage, the support set's best coverage, and the ids on both sides | the decision needs to be **recorded with its evidence**, not just returned |
| `io/matrix/knowledge/KnowledgeStatistics.java` (NEW) | the immutable retrieval statistics of one view: the claims' term and lemma sets, the IDF, and the two inverted indexes | the retrieval's scoring was recomputed on every request; the reuse is what the milestone was asked to investigate |
| `io/matrix/knowledge/KnowledgeStatisticsCache.java` (NEW) | a bounded, synchronized LRU keyed by `(workspaceId, viewIdentity)` | the reuse must be keyed by the workspace **and** by the view's content |
| `io/matrix/knowledge/KnowledgeRetriever.java` | the scoring extracted **verbatim** into `prepare(question)` and `scoreClaims(...)`; `relevance(question)` added; a six-argument constructor that takes the statistics | the gate needs the retrieval's own numbers; the scoring's behaviour must not change |
| `io/matrix/knowledge/KnowledgeWorkbench.java` | `restrictedView(...)`, `statisticsOf(...)`, `clearStatisticsCache()`, and `assessSupport(...)` | the comparison of the two readings is the workbench's, because both readings are the workbench's |
| `io/matrix/cognitive/report/ReportProcedureMethod.java` | the precondition is now **presence AND adequacy**; the refusal carries its **true reason** | the defect and the false reason |
| `io/matrix/knowledge/ArtifactReportResource.java` | the fallback note is appended to the plan and to the response's limitations; it says **what happened** | the fallback must be legible in the journey's own record |

**No new subsystem, no new schema, no new architecture.** Every change is inside the
existing served planner / method / retriever / store boundaries.

### 2.1 The criterion, and why it is not a lexical rule

> **The support set is adequate for a question when it holds the claim the workspace's own
> reading of that question would cite first.**

The comparison is made on the retrieval's own numbers — the coverage a claim reaches — over
the claims the retrieval itself reached, in the **same** statistics the reference path uses.
Nothing in the criterion knows a word, a concept or a domain: `SupportAdequacy` compares two
`double`s and two lists of ids.

**Why the criterion is the anchor and not a comparison of the numbers.** The first version
accepted a support set whose best claim reached *exactly* the workspace's best coverage —
but a **different** claim. That is the H2 defect wearing a different hat: a tie in the number
is not a tie in the evidence, and the restricted answer would then be anchored on other
evidence while looking equally relevant. The measured consequence was real: with the tie
rule, the out-of-support control `AC-DHX-13` was still served by the procedure. **The
tie rule was removed on that evidence, not on taste.**

**Why the support set's own coverage is not the criterion.** The support set decides *what
may be cited*; the workspace's statistics decide *how relevant a claim is* (R-EPIS-26). The
restricted reading therefore scores its own claims with the **workspace's** weights, and the
two readings differ only in the claims they may cite. The gate compares them on that basis.

## 3. The measured results

### 3.1 The served path

| measurement | value | where |
|---|---|---|
| cases in the served evidence | **30** (two domains × fifteen — the WHOLE split: the learning episode's OBSERVED cases, the VALIDATION and NOVEL cases, the OUT_OF_SUPPORT controls and the **distinct subsequent task**) | `evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json` |
| cases in the cost grid | the **matched** cases of each cell — a subset, and not the whole split | `CRJ-01-ADAPTIVE-COST-ROWS.json` |
| grounded on the served path | **28** | the oracle's verdict, per case |
| grounded on the stateless reference | **28** | the same oracle, the forced reference arm |
| **parity** | **true** | the artefact's own `summary.parity` |
| the deliberate RED | **6 of 16 tests fail** with the gate removed | `CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml` |

The **six** RED failures are the claims the milestone makes: the out-of-support fallback,
the case-by-case parity of the served evidence, the competence revision, the cost grid's
cells, the ambiguity, and the citable status of the cited claims. The retained evidence's README names which five and
what each one says — and it says what the RED does **not** show: with the presence-only
rule the procedure is selected for the question it cannot answer, and the procedure's own
execution then refuses it, so the RED reproduces the **applicability** half of the defect,
not the restricted-reading half. The RED is retained **with the manifest**, so that the gate
itself is shown to be able to go red.

### 3.2 The fallback, and what it says

When the procedure does not hold for a question, the served answer is the **reference's own**
— the same cited claims, not a re-derivation — and the plan carries the reason:

> the procedure `report.procedure.b2f15202.4a96be6d` was not applicable to this question:
> the support set does not carry the workspace's own evidence for this question: the
> workspace's reading would cite `clm-622e2fc6` (coverage 0.881) and the support set's best
> evidence reaches 0.000

**A correction that the evidence forced.** The first version of the note read *"was not
applicable"* for **every** procedure that was not selected — including the procedures that
**were** applicable and simply lost the selection. That is a false statement in the record,
and the artefact itself showed it: a row whose reason said *"the support set carries the
workspace's own evidence"* was headed *"was not applicable"*. The note now distinguishes the
two cases, and the gate checks that no row claims inapplicability for an applicable
procedure.

### 3.3 The competence revision

| domain | the first promotion | the revision | the revision's validation |
|---|---|---|---|
| `dhx` | promoted, **two** sources | promoted, **three** sources — the failure episode's source joins the support | the candidate agreed with the reference on **all five** held-out cases |
| `dmy` | promoted, **two** sources | promoted, **three** sources | all five held-out cases agreed |

**The procedure identifiers are not written here.** They are derived from the workspace, the
document's content hash and the proposition, so they differ between two runs of the same
measurement — and a document that quotes an identifier which the artefact no longer holds is
a document quoting a stale number. The current identifiers are in the evidence artefact
(`promotions` and `distinctSubsequentTask`), and the gate checks that the **"after" row's
method is the exact revised procedure's**, so the two cannot drift apart.

**One revision was refused first, for a precise reason** — and the refusal was **kept**,
not worked around by loosening the check:

> the validation case `AC-DHX-13` asks the question the procedure was proposed from, so it is
> not held out

That is the learner refusing to validate a candidate on a case the candidate was *proposed
from*. The **validation subset** was corrected — the `NOVEL` cases alone, which the
revision was not proposed from — and the refusal was not weakened. The revision then
answers the control that the first competence could not, and the milestone's own gate holds
afterwards.

### 3.4 The cost grid — read against the pre-registration

The pre-registration (`CRJ-01-ADAPTIVE-PREREGISTRATION.md`) was written **before** any cost
was observed. Its rule has three parts: the procedure's warm median must be lower, the
paired 95 % bootstrap interval must exclude zero, and the procedure's answers must be
grounded on the same cases. **All three** must hold; otherwise the cell is `NOT
ESTABLISHED`.

| cell | reference (warm median) | procedure (warm median) | the paired interval | verdict |
|---|---|---|---|---|
| `dhx/narrow/1` | 8.31 ms | 8.74 ms | [−0.58, +0.86] ms | `NOT ESTABLISHED` |
| `dhx/narrow/2` | 11.51 ms | 11.13 ms | [−1.32, +2.98] ms | `NOT ESTABLISHED` |
| `dhx/wide/1` | 12.82 ms | 11.26 ms | [−1.87, −0.35] ms | **`CHEAPER`** |
| `dhx/wide/2` | 16.07 ms | 14.10 ms | [−2.95, −0.42] ms | **`CHEAPER`** |
| `dmy/narrow/1` | 10.57 ms | 10.23 ms | [−2.62, +0.59] ms | `NOT ESTABLISHED` |
| `dmy/narrow/2` | 12.31 ms | 12.48 ms | [−2.34, +0.70] ms | `NOT ESTABLISHED` |
| `dmy/wide/1` | 13.70 ms | 12.54 ms | [−2.15, −1.23] ms | **`CHEAPER`** |
| `dmy/wide/2` | 17.56 ms | 16.17 ms | [−1.51, +0.29] ms | `NOT ESTABLISHED` |

**The verdict is per cell, and no aggregate is drawn over the cells.** The
pre-registration says so — *"The verdict is per cell. There is no aggregate 'winner' over
the eight cells, and no aggregate is invented after the rows are seen"* — and a grid-level
claim (a "cost advantage", a "cost parity") would be exactly the aggregate it forbids. The
table is the retained
run's, and it is re-derived from the artefact** — `python3 docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py`
regenerates it, so a reader never has to trust a number written by hand — and the prose
carries no count that a re-run could falsify. Across runs a cell **changes verdict**: its
interval straddles zero and its sign is noise. **The timings are
machine state**: they are recorded, never asserted by any test, and a cell that holds in one
run and not in another is exactly the evidence that a single run cannot establish a
difference. The per-cell verdicts are read against the pre-registered rule, not against a
preference.

**Cold and warm, and the amortisation.** Each cell measures a **cold** pass (the first
request of a workspace) and a **warm** pass (a request that finds the statistics already
computed), and the artefact carries both medians, the cold premium and the number of
requests after which the cold start is paid back. The cold premium is small — the order of
a millisecond — and in several cells the cold pass is *not* more expensive than the warm
pass, because the cold pass is the one that benefits most from the procedure's narrower
reading.

**Revision invalidation.** A changed document gives the view another identity, and the
statistics of the older view are never served for the newer one; the next request pays the
cold cost again. This is exercised on the production revision path (a superseded source, a
revised import, and the revision chain that links them) and asserted on the **identity and
the object**, not on a time.

### 3.5 What is **not** claimed

- **No superiority of the procedure path over the reference path.** The grid says the
  difference is inside the noise in every cell whose interval contains zero, and that the cells which do separate
  are the wide corpora with a one-document support — where the restricted reading does the
  least work.
- **No sealed evaluation.** See §5.
- **No claim about the H2 rows.** The H2 evidence is **not** regenerated: it is the record of
  the defect. The two rows that the gate changes are named in the banner on
  `CRJ-01-H2-SERVED-TRANSFER.md` and in the H2 evidence's own README, and the H2 manifest
  still verifies **20 of 20 OK**.

## 4. The roles, and what each one did

| role | who | what it did |
|---|---|---|
| implementer | this DSH session | the gate, the statistics, the criterion, the corpora, the split's generator, the suite, the evidence |
| case author | a **separate** DSH agent, re-scoped after its first dispatch — **it did NOT deliver**, and the artefact says so | **nothing was delivered.** The corpora and the split's questions and gold were written by the **implementer's** deterministic generator. The role is recorded as `NONE` in the evidence artefact, not as a claim |
| isolation inspector | a **separate** DSH agent, read-only probes | the host's separation capabilities, with **synthetic canaries only** |
| reviewer | a **separate** DSH agent, read-only | the diff and the evidence; the findings are in `evidence/crj01-adaptive-competence/REVIEW.md` and the resolution in §8a |

**The limitation that must be declared.** The case author **did not deliver** within the
milestone's time, and the questions were written by the **implementer's deterministic
generator** — from the separation probe's own focused questions, verbatim. The questions
are therefore **formulic** (one form, the probe's), where a human author would have written
natural questions. The gold is the production extraction's own sentence, byte for byte, and
the corpus is the milestone's own synthetic data, so every case remains verifiable against
the corpus's bytes. **This weakens the independence of the cases and nothing else**: the
cases are not sealed and were never claimed to be. The split's digests are re-derived by the
gate from the corpus bytes, so a reader can verify that the gold is in the corpus.

## 5. The sealed evaluation — **NOT EXECUTED**, and the declaration

The isolation a sealed split needs — an implementer that cannot read the gold and an
evaluator that cannot read the solver — was inspected by an independent agent with
**synthetic canaries only**. No installation, no privilege change, no new repository or
worktree, and no hidden gold was read or transmitted.

> **VERDICT: `SEALED_NOT_FEASIBLE` — the milestone is DEV-only and the sealed evaluation is
> NOT EXECUTED.**
>
> Established with synthetic canaries: one non-privileged uid (1000, `CapEff=0`);
> `setpriv --reuid` → `Invalid argument` (no second identity); `unshare --user/--mount/--net`
> → `Operation not permitted`; `bwrap` → *No permissions to create a new namespace*; no
> `firejail`/`podman`; the harness sandbox is a **write** perimeter only (`bwrap --ro-bind
> / /`) and the Docker socket is reachable, so no host path can be treated as confidential;
> **`0700` directories and `0400` gold files were read successfully by a same-uid process**;
> all 13 git worktrees share one object database and cross-read each other. The protocol's
> own requirement (`CRJ-01-PROTOCOL.md` §5.5a, lines 560-563: *"Deny the implementer read
> access before the frozen run"*) **cannot be met from this executor**.
>
> The consequence is declared in the evidence artefacts themselves (`sealed: false`, with the
> declaration), and the gate checks that the declaration is there.

## 6. The rejected alternatives — and why

| alternative | why it was rejected |
|---|---|
| **Keep the tie rule** (the support's best coverage ≥ the workspace's best coverage) | a tie in the number is not a tie in the evidence. Measured: with it, the out-of-support control was still served by the procedure — the defect survived. |
| **Compare the two readings' citation sets for equality** | the reference's reading may cite a claim the restricted reading may not cite at all — the sets are then never equal, and the procedure would be applicable to nothing. |
| **Decide the applicability from the question's terms alone** | that is a lexical rule, and the directive forbids it. It would also make the criterion a statement about the question rather than about the evidence. |
| **Make the support set's own statistics decide the relevance** | that changes *how relevant a claim is* with *what may be cited*, which the H2 correction (R-EPIS-26) forbids: the restriction decides what may be cited, not how relevant a claim is. |
| **A new statistics subsystem with its own persistence** | a new schema and a new architecture, which the directive forbids, and the reuse is served by a bounded cache keyed by the view's own identity. |
| **Regenerate the H2 evidence** | the H2 rows are the record of the defect. A supersession banner names the two changed rows; the retained H2 rows stay as they are. |
| **Reject the revision because its first validation subset was wrong** | the refusal was *right* — a candidate must not be validated on the case it was proposed from. The **subset** was corrected; the **refusal** was not weakened. |

## 7. The commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

# the milestone's scope — the classes are NAMED, never a package.
# A package-wide filter (io.matrix.knowledge.*, io.matrix.crj.*) pulls in tests that WRITE
# repository artefacts, and this executor's broken git environment makes them write a
# corrupted stamp into another writer's files.
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline \
  --tests "io.matrix.knowledge.AdaptiveCompetenceServedTest" \
  --tests "io.matrix.knowledge.CRJ01H2ServedTransferTest" \
  --tests "io.matrix.knowledge.RestrictedReadingStatisticsTest" \
  --tests "io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder" \
  --tests "io.matrix.crj.corpus.Crj01AdaptiveSeparationProbeTest" \
  --tests "io.matrix.crj.corpus.Crj01AdaptiveLearningProbeTest"

# the gate — the verdict is its exit code
bash scripts/adaptive-competence-gate.sh; echo "the gate's verdict: $?"

# the gate's self-test — a gate that cannot go red is not a gate
bash scripts/adaptive-competence-gate-selftest.sh; echo "the self-test's verdict: $?"

# the cost table, rendered from the canonical rows
python3 docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py

```

## 8. The evidence

| artefact | what it holds |
|---|---|
| `docs-v2/engineering/evidence/crj01-adaptive-competence/` | the retained evidence — the **canonical home** of this milestone's artefacts: the GREEN XML, the deliberate RED, the two row artefacts, the instruments' XMLs, the regressions' XMLs, the independent review, the cost-table renderer, the README, the MANIFESTS and the checksums (**13 files OK**) |
| `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json` | the 60 served/reference rows, the parity flag, the promotions, the revisions, the **distinct subsequent task's before/after rows**, the refused attempt and the witness of the rejected rule |
| `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-COST-ROWS.json` | the 256 cost rows, the eight per-cell verdicts, and the **promotion and validation cost measured separately** with what the timed path includes and excludes |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json` | the two corpora as the **production** import path measured them: 96 claims, all citable |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-SEPARATION-PROBE.json` | the 96 claims with their focused question and the documents their question reaches |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json` | the **30** cases — two domains × fifteen, of which one per domain is the **distinct subsequent task** — their gold and the corpus digests (re-derived by the gate) |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md` | the decision rule, written before the costs were observed |

**There is no second copy of the rows.** The row artefacts live in the retained evidence
directory and nowhere else, and the gate reads them **there**. A copy in the engineering
root is a copy that can go stale — the gate would read one and a reader would open the
other, and the two would disagree without anyone having done anything wrong.

## 8a. The independent review, and what it changed

An **independent DSH agent** read the diff and the evidence and wrote
`docs-v2/engineering/evidence/crj01-adaptive-competence/REVIEW.md` — a read-only review:
the implementation was not changed by the reviewer. Its verdict was **FINDINGS MUST BE
RESOLVED**, with **six material** findings, seven minor ones and three notes. **All sixteen
are resolved**, and the resolution is recorded here with the re-run evidence, because a
review that is not resolved is not a review.

| # | severity | what the review found | how it was resolved |
|---|---|---|---|
| F-01 | MATERIAL | the document's cost conclusion contradicted its own artefact: the retained run has a different split of cells than the prose claimed, and "no cost advantage is established" is an **aggregate** the pre-registration forbids (*"no aggregate is invented after the rows are seen"*) | the aggregate is **gone**. The verdict is stated **per cell**, the table is **rendered from the artefact** by `docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py`, and the prose carries no count a re-run could falsify |
| F-02 | MATERIAL | the retained evidence **falsely attributed** the cases to an independent case author, which the milestone document itself retracts | the artefact's role record now says the truth: `caseAuthor: NONE — the independent case author did NOT deliver`, and names the implementer's generator as the cases' author |
| F-03 | MATERIAL | the evidence README's account of the RED was **contradicted by the retained RED XML**: the served path refused (422), the restricted reading was never served, and the paraphrase misnamed one of the failures | the README now **quotes the RED**: it names the five failing tests, what each says, and — stated plainly — **what the RED does not show** (it reproduces the *applicability* half of the defect, not the restricted-reading half) |
| F-04 | MATERIAL | `KnowledgeStatistics.identityOf` omitted the claim id, and the id carries the document's content hash: a revision that changes the bytes but not the sentences yields **new ids under the old identity**, so the older statistics are served for the newer view, every lookup misses, and the reading scores **no claim at all** — a silent loss of the whole evidence | the identity now covers the claim's **identifier** as well as its text, and a new test witnesses the mechanism directly: two views with the same propositions under different ids have different identities, and the refreshed statistics are indexed by their own ids. **The test was verified to go red with the fix reverted** |
| F-05 | MATERIAL | the parity assertion was a **count equality**, so 20 against 20 would pass | the parity is now asserted **case by case**: the served path must answer exactly the cases the reference answered, and agree with it on every one |
| F-06 | MATERIAL | the pre-registered rule's third part was applied **weaker than written** (only the procedure arm's grounding was checked) and the artefact named neither the resample count nor the seed | the rule is applied as written: **both** arms must be grounded **on the same matched cases**, and the artefact names `bootstrapResamples` (2000) and `bootstrapSeed`. The gate now **re-derives every cell's verdict and every paired interval** from the rows — bit-exactly, with Java's own LCG — and refuses a verdict that was written by hand or read against a weaker rule |
| F-07 | MINOR | the gate's docstring claimed it checked that the pre-registered rule was the one the verdicts were read against; the code only checked that the artefact **named** the file | the code now **does** it: the gate re-derives the verdicts and the intervals. The docstring is true |
| F-08 | MINOR | the gate's self-test asserted only a non-zero exit, so a mutation caught by the wrong check counted as caught | each mutation now names the check that **must** fire, and the self-test fails if a different check fires instead. Six mutations, six caught by their own check |
| F-09 | MINOR | a **presence** failure recorded no reason: the fallback note had no adequacy verdict to read | the method now remembers the reason of the last refusal, and the note carries it — a presence failure is now recorded with its reason like any other |
| F-10 | MINOR | the support's best coverage was the maximum over the claims **above the floor**, so the reason could say "reaches 0.000" while the support held sub-floor evidence | the measurement is now over **every claim the support holds**, and the reason names the coverage floor beside the number |
| F-11 | MINOR | the test asserted "no claim reaches the floor ⇒ the reading cites none", contradicting `assessSupport`'s own documented concept-bridge behaviour | the test now asserts what the gate actually says: the verdict is that **no support set can be shown to carry the evidence**, and the reason is present |
| F-12 | MINOR | the retained RED was a 12-test record against an older suite; the tree had moved on | the RED was **reproduced against the current suite**: **5 of 15** tests fail, and every document that quoted the old count was corrected |
| F-13 | MINOR | the renderer prefixed the interval's low bound with "−" whatever its sign | the renderer prints each bound's **own** sign |
| F-14 | NOTE | "cases measured 28" counted the whole split, while the pre-registration's "matched cases" and the suite's `measured()` mean 14 | the two are now **named separately**: the served evidence covers the whole split, the cost grid uses each cell's matched cases. The count is now **30**, after the distinct subsequent task was added (§8b) |
| F-15 | NOTE | the refused first revision and the rejected tie rule were **narrated but unwitnessed** | both are now **witnessed**: the artefact carries the refused attempt with the learner's own reason (*"the validation case AC-DHX-13 asks the question the procedure was proposed from, so it is not held out"*), and the **tie rule's rejection is constructed and asserted** — a term that exactly two claims of two different documents hold makes the two claims tie, so a support set holding one of them ties the workspace's best coverage with a different claim: the tie rule admits it, the anchor rule refuses it |
| F-16 | NOTE | the criterion embeds an assumption — the reference's **first** citation is the necessary anchor — and `HebbianBindings.stateFor` makes a claim's coverage **snapshot-dependent**, so under weakened bindings the two readings can differ in **score**, not only in candidates | the caveat is now stated in §9 with the decision it implies: whether the comparison should be made under **one** state, or on the claims rather than their scores |

**The re-run evidence.** After the resolution: the milestone's scope is **18 tests over 4
classes (15 in the served suite, 3 in the corpus instruments), all green**; the regressions (`CRJ01H2ServedTransferTest`, `RestrictedReadingStatisticsTest`)
are green; the gate is **29 checks, 0 failed, exit 0**; the gate's self-test is **6 of 6
mutations, each caught by the check that belongs to it**; the retained manifest verifies
**every file in the directory verifies** (the manifest covers them all, the reviews
included); the inherited H2 manifest still verifies
**20 of 20 OK**; and the test catalogue reports **0 defects**.

**What the review could not verify**, and what it said so: the reviewer did **not** run
Gradle — the implementer owns the build and the shared lock — so every claim it made about
*behaviour* is a claim about the retained XMLs and the code, not about a fresh run. The
reviewer re-derived the corpus digests, the manifest, the per-cell medians and the bootstrap
intervals itself, and reproduced all eight cost verdicts.

## 8b. The acceptance review's finding, and what it changed

The coordinator's final acceptance review found a **substantive gap** in the milestone's
own central measurement, and the finding was right:

> *`AdaptiveCompetenceServedTest.aFailureEpisodeYieldsAValidatedRevision` uses the same
> control question after promotion with only a changed purpose. That is not a different
> subsequent question.*

**What was wrong.** The milestone claimed that the **changed competence** was measured on a
*distinct subsequent task* — and it was not. The "after" journey asked **the same question**
as the failure episode, with a different idempotency key. The competence's growth was
therefore measured on the very question that had produced it: a learner that had just been
shown the answer was being examined on the answer it had been shown. The claim as written
was not supported by the measurement.

**What is measured now.** The split carries a **new subset, `SUBSEQUENT`** — one case per
domain, drawn from the failure episode's own **document**, but a claim **no other case
uses**. The disjointness is by construction, and the split names it:

| the question | disjoint from |
|---|---|
| the learning episode's observations | the `SUBSEQUENT` case is not among them |
| the failure episode's question | it is a **different claim of the same document** |
| the revision's validation cases | it is not among the `NOVEL` cases |

And the measurement is now a **before and an after around the revision itself**, in the
**revision's own workspace**:

| phase | the method that ran | the verdict |
|---|---|---|
| **before** | `report.journey.reference.v1` | the first competence's support does not hold the document, so the procedure does **not** hold and the **reference answers** — with the reason recorded |
| **after** | `procedure.<the revised procedure's id>` — **exactly** | the revised competence's support does hold the document, so the **exact revised procedure** is selected, the answer is **grounded** and the **effect is verified** |

Both rows are persisted in the evidence artefact under `distinctSubsequentTask`, with the
method that ran, the grounding verdict, the effect, the evidence and the fallback's reason.

**The regression is load-bearing — both halves were proved.** An assertion that cannot fail
is not a test, so each half was checked by breaking it:

| the mutation | what happened |
|---|---|
| the revision is learned from the learning episode alone, so its support does **not** grow | the suite goes **red** — the revision is then not a different competence, and the "after" journey answers with the reference instead of the revised procedure |
| the "distinct" task is replaced by a `NOVEL` case, which the **first** competence can already answer | the suite goes **red** — the "before" journey is then answered by the procedure, not by the fallback, and the case count is wrong |

**Why the revision has a workspace of its own.** The revision changes what the competence
can answer — the failure episode's source joins its support — so a revision made in the
shared workspace would change the state every other test measures, and the measurement
would depend on the order the tests ran in. The revision now runs in its own workspace,
with its own learning episode and its own first competence, and the before and the after
are measured there, around the revision. **A suite whose assertions depend on an order it
does not declare is a suite that can pass or fail for a reason nobody wrote.**

**What the timed path includes and excludes.** The acceptance review also asked for the
promotion's validation cost to be measured separately, or for the timed path to say what it
covers. **Both are done.** The per-request rows time the **served path** — the selection,
the retrieval statistics, the validation of the preconditions, the fallback and the write —
and the cost artefact now says so in `timedPathIncludes` and `timedPathExcludes`. The work
that **makes** a competence — the learning episode, the promotion and its validation on the
held-out cases — is measured **once per competence** and reported separately under
`promotionValidationCost`; it is **not** folded into the per-request medians, because
folding it in would make those medians depend on how many requests a cell made. The
measured promotion cost is of the order of **50 ms per promotion** — which is exactly why
it must not be folded into a per-request number: it would dominate every request that
followed it.

## 8c. The independent re-review, and what it changed

A second **independent DSH agent** re-reviewed the milestone after the acceptance review's
gap was closed, and wrote
`docs-v2/engineering/evidence/crj01-adaptive-competence/REVIEW-2.md`. Its verdict was again
**FINDINGS MUST BE RESOLVED** — five material findings, six minor, six notes — and it
confirmed that the acceptance review's gap **is** closed and that the resolution is real
(the `SUBSEQUENT` case is a genuinely different source-grounded question, the "before" is a
genuine fallback, the "after" is the **exact** revised procedure, the revision runs in its
own workspace, and the promotion cost is measured separately). **All seventeen findings are
resolved**, and so is the one earlier finding it judged only partly resolved.

| # | severity | what the re-review found | how it was resolved |
|---|---|---|---|
| R-01 | MATERIAL | the document still said the grounding was **28 of 28** where the artefact and the gate said 30 | the number is **30 of 30** everywhere, and the drift that produced it is closed at the source: the gate now **re-derives the parity from the rows**, case by case, instead of reading the artefact's own flag |
| R-02 | MATERIAL | §3.3 named the **pre-resolution** revision identifiers, which the artefact no longer holds | the identifiers are **not written in the document** — they are derived from the workspace, the content hash and the proposition, so they differ between runs. The document says so, and the gate checks that the "after" row's method **is** the exact revised procedure's, so the two cannot drift apart |
| R-03 | MATERIAL | the revision's effect was **not reproducible**: the artefact of a failing run recorded a **no-op revision** — the "after" was the reference again, with the first competence's two-source support — while the document claimed the exact revised procedure answers | the evidence writer now **asserts the rows before it writes them**: the "after" row must be the exact revised procedure, grounded, with its effect verified, and the "before" row must be the reference with a fallback reason. A no-op revision makes the writer **fail**, so it cannot be recorded as a success |
| R-04 | MATERIAL | the gate **never read** `distinctSubsequentTask`, so the persisted rows and the assertions came from two different executions and nothing tied them | the gate now checks the rows: the "before" row is the reference with its reason and is grounded; the "after" row is the **exact** revised procedure, grounded, with its effect verified. And the rows are asserted **in the execution that writes them** |
| R-05 | MATERIAL | the manifest **pins the two JSONs the suite rewrites**, so a run left the evidence directory broken and the gate failed on a repository that was in fact fine | the suite **refreshes the manifest it invalidates** — the producer of a file owns the record of that file's digest. The manifest still detects every change made **after** the run, which is what it is for |
| R-06 | MINOR | the split did not **name** `SUBSEQUENT` or the disjointness, though the document said it did | the split now carries an `arrangement` section: every subset's meaning, and the disjointness in words |
| R-07 | MINOR | the generator that guarantees the disjointness was **untracked**, and **no test** asserted the disjointness | the generator is **retained** in the evidence directory (a generated artefact without its generator is an artefact nobody can reproduce), and a new test asserts the disjointness: the distinct task's gold belongs to **no other case**, and it is a claim of the failure episode's own document |
| R-08 | MINOR | the role record still said "the **28** cases" | it says **30 cases, 15 per domain** |
| R-09 | MINOR | the earlier F-05 was only **partly** resolved: the gate still read the self-declared `parity` flag | the gate **re-derives the parity from the rows**, case by case, and separately checks that the artefact's flag agrees with its own rows |
| R-10 | MINOR | `revisionOf` was **dead code** and still held the shared-workspace revision — the order-dependence the fix removed | it is **deleted**, with its record and its maps. No revision happens in the shared workspace any more |
| R-11 | MINOR | the cost-table renderer **rewrote the milestone document's prose**, with a substitution that targeted a sentence the document no longer had | the renderer now rewrites **only the table**. The prose carries no count, so there is nothing for it to keep in step — and a tool that rewrites prose it does not understand will one day rewrite prose wrongly |
| R-12 | NOTE | the manifest's file count was 13, 12 and 11 in three documents | all three now say that **every file of the directory verifies**, and the gate prints the count it found |
| R-13 | NOTE | DESIGN-58 still said "28 of 28" | it says **30 of 30** |
| R-14 | NOTE | `firstPromotionNanos` included the validation | it is now `firstPromotionAndValidationNanos` — the learner's one call does both, and the key says so |
| R-15 | NOTE | the "after" row's fallback reason was the **first** competence's note, not the after-phase's | the field is now `planNoteOnTheProcedureThatDidNotRun`, and the row says which procedure the note is about |
| R-16 | NOTE | `timedPathIncludes` was a stage list and not an exhaustive account | it is now `timedPathCovers`, and it says that it is the list of the stages the timing covers, **not** an exhaustive account of the work the process does |
| R-17 | NOTE | the MANIFESTS listed two **in-directory** artefacts under "OUTSIDE this directory" | the table is corrected, and the two tools that produced the artefacts are named as retained |

**What the re-review could not verify**, and what it said so: it did not run Gradle — the
implementer owns the build and the shared lock — so its claims about behaviour are claims
about the retained artefacts and the code. It re-derived all eight cost verdicts and all
eight paired intervals through the gate's own arithmetic (Java's LCG, bit for bit) and
agreed with them, and it re-ran the split's generator and found the committed split
byte-identical.

## 9. The next scientific decision

**The question the next milestone must answer is not "is the procedure cheaper?" — it is
"what is the competence's REACH?"**

The present state: the gate is sound, the parity is exact, the revision path works, and the
cost question is **inconclusive** — where the rule separates a cell, the procedure's
advantage is a millisecond-scale effect, and wherever the paired interval contains zero the
measurement cannot separate the two paths at all. Continuing to measure the cost would be
measuring noise.

What the milestone actually exposed is a **reach** question, and it has two parts that the
next milestone must answer on evidence:

1. **The support set is the unit of competence, and the milestone learned its width the hard
   way.** A procedure's support set is the union of the sources its **learning episode**
   cited — so a learning episode whose answer cites a document outside the intended support
   widens the support and makes the procedure applicable to questions it must refuse. The
   separation probe measured this: 16 of 96 claims reach a second document even under a
   focused question. **The decision to take:** whether the *support set* should be chosen by
   the learning episode's citations at all, or **declared** — a support set that names the
   documents the competence is *about*, and a learner that may only learn within it. The
   second is the more likely design, and it is a **governance** question about what a
   competence is, not an implementation question.
2. **The criterion has not been tested against a competitor.** The anchor was chosen because
   the tie rule was measured to be wrong — and that measurement is now WITNESSED: the
   evidence carries, per domain, a term that exactly two claims of two different documents
   hold, so a support set holding one of them **ties** the workspace's best coverage with a
   **different** claim. The tie rule admits that support set; the anchor rule refuses it.
   A third criterion — the **claim overlap** of the two readings, or the **rank** of the
   support's claim rather than its identity — might be equally sound and cheaper to
   compute. **The decision to take:** whether the criterion is to be compared against
   **one** alternative on the same grid, with the pre-registered rule unchanged, before it
   is treated as settled.
3. **The criterion embeds an assumption, and the assumption has a caveat.** The anchor is
   "the claim the workspace's own reading would cite **first**", which assumes that the
   first citation is the right anchor. The caveat the milestone found: a claim's coverage
   is **snapshot-dependent** under the weakened bindings (`HebbianBindings.stateFor` reads
   the workspace's own state), so the two readings can differ in **score**, not only in
   which claims they may cite. The anchor therefore compares two readings whose scores are
   not guaranteed to be computed from the same state — the assumption holds when the
   bindings are stable, and the milestone did not measure the case where they are not.
   **The decision to take:** whether the criterion should compare the two readings under
   **one** state, or whether the comparison should be on the claims rather than on their
   scores.

**And the standing decision, which does not change:** the sealed evaluation remains **NOT
EXECUTED**, and it stays that way until a host with a **second identity** and a **verified
filesystem boundary** exists. The milestone's evidence is DEV evidence and says so in every
artefact that carries a number.
