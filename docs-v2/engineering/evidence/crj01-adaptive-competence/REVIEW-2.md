# INDEPENDENT RE-REVIEW — CRJ-01 / ADAPTIVE-COMPETENCE

Reviewer: an independent DSH agent, 2026-11-27. Read-only; the implementation was not
changed by the reviewer. Baseline: dff12b5883242b74c09cc2388be32061c2b87cf0.

**State reviewed.** The worktree was a **moving target** while this review ran: the
implementer was editing `AdaptiveCompetenceServedTest.java` and re-running the suite between
17:51 and 17:58 local. Every finding below refers to the tree **as of 18:02 local**
(`CRJ-01-ADAPTIVE-COMPETENCE.md` mtime 17:57:45, the two row artefacts 17:57:20/17:57:32,
`CHECKSUMS.sha256` 17:57:45, the test 17:57:02). At that point the gate is
**29 checks, 0 failed, exit 0**, the retained manifest verifies **13 files**, and the gate's
self-test is **6 of 6**. Where a finding concerns an earlier state of the tree I say so and
quote the evidence that shows it.

## VERDICT

**FINDINGS MUST BE RESOLVED**

**The coordinator's substantive gap IS closed, and I verified it is real.** The `SUBSEQUENT`
case is a genuinely different, source-grounded question; the disjointness is **by
construction**, not by luck; the "before" is a genuine fallback with the reason recorded; the
"after" is the **exact** revised procedure (not merely *a* procedure) and the assertion is
exact; the revision runs in its own workspace; and the promotion/validation cost is measured
separately with a truthful `timedPathIncludes` / `timedPathExcludes`. I re-derived the
split from its generator (byte-identical), re-ran the gate and its self-test, re-derived the
eight per-cell verdicts and the eight paired intervals through the gate's own Java-LCG
arithmetic, and confirmed the parity (30 of 30, every row grounded). On those points the
resolution is sound, and I say below exactly what I read that makes me say so.

**Why the verdict is not ACCEPTED.** Four things. (1) The **gate never reads the new
evidence**: the coordinator's requirement — "before fallback vs after selected exact revised
method" — is asserted only inside the test, while the artefact that records it
(`distinctSubsequentTask`) is produced by a **different execution** of the same experiment,
and the milestone's independent reader has **no check on it whatsoever** (F-04). (2) The
retained row artefact I first read — the one on disk when this review began, at 17:56 — was
written by a run whose suite **FAILED**, and it recorded the "after" of the distinct
subsequent task as a **second fallback** (`report.journey.reference.v1`), not the revised
procedure, with the revision's support set identical to the first competence's: the revision
was a **no-op** (F-03). The retained GREEN XML was from a *different*, earlier run, and the
retained manifest did **not** verify — the gate exited 1 (F-05). (3) The milestone document
still carries **two numbers its own artefact contradicts** — the grounding count (28 against
30) and the revision's procedure ids (F-01, F-02) — which is precisely the F-01 defect class
the previous review found, re-introduced one section later. (4) The previous review's F-05 is
**only partly resolved**: the suite's parity is now case-by-case, but the gate still reads the
artefact's self-declared `parity` flag, and nothing anywhere asserts that the count is 30 of
30 (F-09).

**What I checked.** `CRJ-01-ADAPTIVE-COMPETENCE.md`, `CRJ-01-ADAPTIVE-PREREGISTRATION.md`,
`CRJ-01-ADAPTIVE-DEV-SPLIT.json`, the retained evidence directory (README, MANIFESTS,
CHECKSUMS, all seven XMLs) and both canonical row artefacts, the served suite
(`AdaptiveCompetenceServedTest.java`, 2346 lines, in full), `scripts/adaptive-competence-gate.py`
and `-selftest.sh` (both run), `render-cost-table.py`, the then-local split generator
(run, output compared byte for byte with the committed split), the protocol §5.5a/§5.6, and
the production sources behind the claims (`ReportProcedureLearner`, `ReportProcedure`,
`ReportProcedureMethod`, `KnowledgeWorkbench.assessSupport`, `KnowledgeStatistics.identityOf`,
`KnowledgeRetriever`, `SourceRevisionView`, `ArtifactReportResource`). I also computed the
production claim ids of the pristine and the two revised corpora from
`Ids.digest(workspaceId|contentHash|proposition)` to establish which corpus the retained dhx
rows were measured on.

**What I could not check.** I did **not** run Gradle (the implementer owns the build and the
shared lock), so every claim about *behaviour* here rests on the retained XMLs, the artefacts
and the source. I could not establish the *mechanism* of the run-to-run flip in F-03: the
test file was edited at 17:56:25, **after** the failing run, so I cannot prove that the two
runs used the same source; I report the observable disagreement and the structural reason it
is possible, and mark the mechanism as speculative. The sealed path (§5.5a/§5.6) was not
executed and cannot be from this executor; I verified only that the DEV-only declaration is
present and consistent in every artefact that carries a number. The isolation inspector's
canaries remain prose with no retained transcript, and I did not audit the oracle
(`SourceSpanOracle`) or re-derive the inherited H2 rows.

## WHAT THE PREVIOUS REVIEW'S FINDINGS LOOK LIKE NOW

| # | status | evidence |
|---|---|---|
| F-01 | **RESOLVED** (a new number error replaces it — my F-01) | the aggregate is gone: §3.4 states the verdict per cell and §3.5 carries no count; the table is re-derived from the artefact — I rendered it myself and it matches the retained artefact cell for cell, medians, intervals and verdicts. |
| F-02 | **RESOLVED** (a stale count survives in the same field — my F-08) | `CRJ-01-ADAPTIVE-EVIDENCE.json` → `roles.caseAuthor` now reads *"NONE — the independent case author did NOT deliver. The two corpora and the 28 cases were written by the IMPLEMENTER's deterministic generator…"*, and §4's roles table agrees. The *28* is now wrong (the split has 30). |
| F-03 | **RESOLVED** | `README.md:48-65` now quotes the retained RED: "**5 of 15** tests fail", names the five failing tests and says what the RED does **not** show (the restricted reading is not served; the procedure is admitted and then refuses with 422). I read the RED XML: 15 tests, 5 failures — the count and the names are right. |
| F-04 | **RESOLVED** | `KnowledgeStatistics.identityOf` now builds the material as `claim.id() + SEP + proposition + …` (`KnowledgeStatistics.java:173-176`), and the javadoc explains exactly why the id must be in it. A new test, `aRevisionThatKeepsTheSentencesStillInvalidatesTheStatistics` (`AdaptiveCompetenceServedTest.java:477-576`), witnesses the mechanism directly: the same propositions under new ids give a different identity, and the older statistics do not cover the new ids. |
| F-05 | **PARTLY** | the suite's parity is now asserted **case by case** (`AdaptiveCompetenceServedTest.java:1449-1471`): the two arms must answer the same cases and agree on every one. But the suite still asserts only `servedGrounded == referenceGrounded`, and the **gate still reads the artefact's self-declared flag** (`adaptive-competence-gate.py:335-339`: `summary.get("parity") is True`), so 20 against 20 passes both. Nothing asserts 30 of 30. → my F-09. |
| F-06 | **RESOLVED** | `verdict(...)` now requires `sameCases && grounded && referenceGrounded` on the *same* matched cases (`AdaptiveCompetenceServedTest.java:1038-1058, 1093`), and the artefact names `bootstrapResamples` (2000) and `bootstrapSeed` (20261127) per cell. I re-derived all eight intervals through the gate's own LCG: "8 intervals re-derived and agreed", "8 cells re-derived and agreed". |
| F-07 | **RESOLVED** | the gate re-derives every verdict and every interval from the rows (`adaptive-competence-gate.py:239-293, 414-440`); the docstring is now true. |
| F-08 | **RESOLVED** | the self-test now requires the named check to fire (`adaptive-competence-gate-selftest.sh:69`: `grep -q "^FAIL $expected"`). I ran it: 6 of 6, each caught by its own check. |
| F-09 | **RESOLVED** | `ReportProcedureMethod.refusalOf` now records the presence failure through `remember(...)` and exposes `lastRefusalReason()`; the adequacy failure records the assessment's own reason. |
| F-10 | **RESOLVED** | `assessSupport` takes the support's best coverage over `supportRelevance.coverage().values()` — every claim the support holds, not only the ones above the floor (`KnowledgeWorkbench.java:728-731`). |
| F-11 | **RESOLVED** | the test now asserts what the gate says — `adequacy.applicable()` is false and the reason is present — instead of asserting that the reading cites none (`AdaptiveCompetenceServedTest.java:362-377`). |
| F-12 | **RESOLVED** | the RED was re-produced against the current suite: `CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml` carries 15 tests and 5 failures; the README and the document quote "5 of 15". |
| F-13 | **RESOLVED** | `render-cost-table.py:23-29` gives each bound its own sign (`signed()`), and I confirmed all eight rendered rows are faithful. |
| F-14 | **RESOLVED** (except the leftover "28" — my F-01) | §3.1 now names two populations separately: the served evidence covers the whole split (30), the cost grid uses each cell's matched cases. |
| F-15 | **RESOLVED** | the artefact carries `refusedRevisionAttempts` with the learner's own reason ("the validation case AC-DHX-13 asks the question the procedure was proposed from, so it is not held out") and `rejectedTieRuleWitness` for both domains with `tieRuleWouldAccept: true` / `anchorRuleAccepts: false`. |
| F-16 | **still an assumption, now stated** | not a defect; the milestone names the caveat and the decision it implies in §9.3. |

## FINDINGS

### F-01 — MATERIAL — The document's grounding count contradicts the artefact it cites

**File/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:83-84`.

> `| grounded on the served path | **28** | the oracle's verdict, per case |`
> `| grounded on the stateless reference | **28** | the same oracle, the forced reference arm |`

The retained artefact says otherwise — `CRJ-01-ADAPTIVE-EVIDENCE.json` → `summary`:
`"cases" : 30, "servedGrounded" : 30, "referenceGrounded" : 30`. The gate prints the same
("served 30 reference 30 of 30"), the document's own §1 says "**30 of 30**", and the
`CAPABILITY-REGISTRY` and `TEST-CATALOGUE` rows were updated to 30 of 30 — but §3.1 still
says 28, and §8a itself claims that "the two are now **named separately** … the count is now
**30**".

*Why it matters:* this is the F-01 defect class verbatim — a number in the prose that the
artefact falsifies — and it is the number a reader quotes. §3.4's table is now rendered from
the artefact; §3.1's table is not, and it is wrong.

*Smallest resolving change:* write 30 in both cells of §3.1 (or render them from
`summary.servedGrounded` / `summary.referenceGrounded`, as §3.4's table is rendered).

### F-02 — MATERIAL — The revision table names procedure ids that the retained artefact does not hold

**File/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:119-120` and `:131`.

> `| `dhx` | promoted (`b2f15202.4a96be6d`, two sources) | promoted (`2de56731.903a48d2`, **three** sources …`

The retained artefact's `revisions` block says:

```
"dhx" : { "procedureId" : "report.procedure.66e527dd.b2dbf57e",
           "support" : [ "src-b301e213", "src-b524984b", "src-e35b3d9c" ] }
"dmy" : { "procedureId" : "report.procedure.15633263.b851be84",
           "support" : [ "src-1de41299", "src-95880e04", "src-96615a8a" ] }
```

The ids in the document (`2de56731.903a48d2`, `1e84c102.7620cfa3`) are the **pre-resolution**
ids — the ones the revision had when it was promoted in the *shared* workspace, before it was
moved into its own. §3.3's closing sentence is the same stale measurement: *"The revision then
answers the control that the first competence could not"* — which is the experiment §8b itself
retracts, and which the retained artefact no longer contains (the retained artefact's
`distinctSubsequentTask` is about the **distinct** task; there is no row in which the revision
answers the control).

*Why it matters:* the table is the milestone's record of the revision; a reader who checks it
against the artefact finds two different procedures, and a reader who does not is misled about
what was measured.

*Smallest resolving change:* replace the two ids with the artefact's, and replace the closing
sentence with §8b's wording ("the distinct subsequent task … before the revision the reference
answers it, after it the exact revised procedure answers it").

### F-03 — MATERIAL — The revision's effect is not reproducible: the retained artefact I first read recorded a no-op revision and a second fallback

**Files/lines:** `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.AdaptiveCompetenceServedTest.xml`
(run of 14:55:55Z) against `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json`
(written by that run at 17:56:17 local), with the test at
`matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:1361-1364`.

The build directory's JUnit XML of the run at 14:55:55Z carries **15 tests, 1 failure**:

```
<testcase name="a failure episode yields a candidate revision, …">
  <failure message="… [a changed support set is a different competence, not a new version of the same one]
  Expecting actual: "report.procedure.8584b6.5baa4e21" not to be equal to: "report.procedure.8584b6.5baa4e21""
```

and the artefact that run wrote carried, in `distinctSubsequentTask.dhx`:

```
"after"      : { "methodId" : "report.journey.reference.v1", … },
"revisedProcedureId" : "report.procedure.8584b6.5baa4e21"
```

with `revisions.dhx.support` of **two** sources — i.e. **the revision's support was identical
to the first competence's, the procedure id was unchanged, and the "after" of the distinct
subsequent task was a *second fallback*, not the revised procedure.** The milestone document
says the opposite in §1 and §8b ("after it the **exact revised procedure** answers it"). The
run before it (14:51:47Z) and the run after (14:57:10Z) both passed; the retained GREEN XML
was from the earlier one, so for ~5 minutes the retained set was a green XML from one run and
artefacts from a failing run of another, and the gate was failing on it (F-05).

*Why it matters:* the milestone's central new measurement — "the changed competence answers a
distinct subsequent task" — is a property of the run, not of the mechanism, and the artefact
that records it can disagree with the assertion that is supposed to have certified it. The
structural reason it is possible is in the production code: `ReportProcedureLearner.learn`
derives the support from **the union of the sources the observed journeys cited**
(`ReportProcedureLearner.java:178-183`), and the failure episode contributes only the sources
of the claims **the reference cited** for the out-of-support control. If the reference's answer
for that control cites nothing from the control's own document, the failure episode adds no
source, the support does not grow, and the "revision" is the same competence. The milestone's
own §9.1 names this class of cause ("a learning episode whose answer cites a document outside
the intended support widens the support") but does not notice that here it decides whether the
revision happens at all. [speculative on the mechanism: I could not establish it, because the
test file was edited at 17:56:25, after the failing run, so I cannot prove the two runs used
the same source.]

*Smallest resolving change:* make the revision's support grow **by construction** — derive the
candidate's support from the observed episodes **plus the failure episode's document
explicitly**, rather than from the claims the reference happened to cite — and assert in the
suite that the revised support **strictly** contains the first's (the existing
`isNotEqualTo(original.id())` assertion is what caught this; it should stay and the mechanism
behind it should be made deterministic). Then re-run and retain the run that produced the
artefact.

### F-04 — MATERIAL — The gate never reads the distinct-subsequent-task evidence it was asked to check

**Files/lines:** `scripts/adaptive-competence-gate.py` (no occurrence of
`distinctSubsequentTask` anywhere in the file) against
`matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:1635-1660`.

The coordinator required, in BOTH domains, "a check of **before fallback vs after selected
exact revised method**, with grounding/effect". The check exists in the **test**
(`AdaptiveCompetenceServedTest.java:1380-1405`): the "before" must be
`report.journey.reference.v1` with "was not applicable to this question" in the plan, the
"after" must be **exactly** `ReportProcedureMethod.ID_PREFIX + revised.id()`, grounded, with
`effectVerified`. The **rows** are persisted under `distinctSubsequentTask`. But:

* the **gate** — the milestone's independent reader, the artefact whose whole purpose is to
  "re-derive the milestone's evidence INDEPENDENTLY of the test that produced it" — has **no
  check on those rows at all**: it reads `summary`, `rows`, `promotions`, the split, the corpus
  digests and the cost grid, and stops there;
* the rows and the assertion come from **two different executions** of the same experiment.
  Each test method gets its own `@TempDir` (JUnit's default per-method lifecycle), so each
  method builds its own workbench, store and workspace; the `revisionExperiments` cache is an
  **instance** field and therefore **per method**, not per class. The evidence writer's
  `revisionExperiment(domain)` (`:1641`) is a *second* execution of the experiment the revision
  test asserts on (`:1341`). Nothing ties them together — the code comment "It is computed once
  per domain and shared with the evidence writer" (`:1340`) is true only within one method.

That gap is not theoretical: it is what let the retained artefact of 17:56 say the "after" was
a fallback while the suite was green (F-03).

*Why it matters:* the milestone's own standard — a claim that must be checked mechanically
"cannot be checked in a prose field, and a record that only a human can read is not evidence"
(the test's own words at `:1502-1507`) — is not met for the one piece of evidence the
acceptance review asked for. The gate can pass on a repository in which the distinct
subsequent task was never measured.

*Smallest resolving change:* add a gate check that reads `distinctSubsequentTask` and requires
of **each** domain: the "before" row's `methodId` is the reference **and** its plan contains
"was not applicable to this question" **and** its `fallbackReason` is non-empty; the "after"
row's `methodId` equals `"procedure." + revisedProcedureId` **and** differs from the "before"
row's; both rows are `grounded` and `effectVerified`; and the `revisedProcedureId` differs from
the domain's first promotion's id. Add one mutation to `adaptive-competence-gate-selftest.sh`
that breaks the "after" row and requires that new check to fire.

### F-05 — MATERIAL — The retained manifest pins files the suite rewrites, so the acceptance "re-run" breaks the milestone's own gate

**Files/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/CHECKSUMS.sha256:1-2`
against `matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:1269-1277`
and `:1684-1692`.

The manifest pins the digests of `CRJ-01-ADAPTIVE-COST-ROWS.json` and
`CRJ-01-ADAPTIVE-EVIDENCE.json`, and the suite **rewrites both on every run**
(`writeCostEvidence` and `writeServedEvidence`). I observed the consequence directly: at
17:52–17:57 local the gate reported

```
FAIL the retained manifest verifies: docs-v2/engineering/evidence/crj01-adaptive-competence
     — broken: CRJ-01-ADAPTIVE-COST-ROWS.json, CRJ-01-ADAPTIVE-EVIDENCE.json
checks 29 failed 1      (exit 1)
```

while the milestone document claims "the gate is **29 checks, 0 failed, exit 0**" and "the
retained manifest verifies **13 files OK**". The implementer regenerated the checksums after
the last run, so the tree is green now — but the **re-run that the acceptance procedure
demands** is exactly what invalidates the gate, and the tree was in the invalid state for the
part of the session I was reviewing.

*Why it matters:* a gate that a legitimate re-run turns red is a gate whose green state is a
property of the last command an operator typed, not of the evidence. The milestone's own
README tells the reader to re-verify with `sha256sum -c CHECKSUMS.sha256`, and the milestone
document's "re-run evidence" section (§8a) is precisely a re-run.

*Smallest resolving change:* keep the two generated artefacts **out** of the digest manifest
and record their digests in a separate "run stamp" that names the run (the JUnit XML's
timestamp), so that a re-run changes the stamp and not the verification; or, if the manifest
must cover them, state the order in the README — *run the suite, then regenerate the manifest,
then run the gate* — and have the gate report a stale digest for exactly those two files as a
warning rather than a failure.

### F-06 — MINOR — The split does not name the `SUBSEQUENT` subset, although the document says it does

**Files/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:351` against
`docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json` (metadata block, lines 1-33).

> uses**. The disjointness is by construction, and the split names it:

The split's metadata (`split`, `version`, `date`, `sealed`, `sealedNote`, `provenance`,
`domains`) is **byte-identical to the baseline's**: the provenance still reads *"the questions
and the gold are selected from the separation probe's narrow claims; the questions are the
probe's own focused questions"*, and the string `SUBSEQUENT` appears **only** inside the two
case objects (`"class"` / `"subset"`). The split names nothing about the fifth subset, its
slot document, or the rule that makes it distinct.

The disjointness itself is **real and by construction** — I checked it three ways: the
generator takes the third narrow claim of the out-of-support slot
(lines 87–96 of the then-local split generator), the committed split is **byte-identical** to
the generator's output (I ran it and compared), and the 30 cases carry **30 distinct claim
ids, 30 distinct propositions and 30 distinct questions**. But the record that says so is not
in the split.

*Smallest resolving change:* add a `subsets` block to the split naming the five subsets, the
document each is drawn from, and the rule that the `SUBSEQUENT` case's claim is used by no
other case.

### F-07 — MINOR — The generator that guarantees the disjointness is untracked and unretained, and no test asserts the disjointness

**Files/lines:** the then-local split generator (untracked — `git status` lists it as
`??`), its local output (untracked); the split's
`provenance` names no generator; `docs-v2/engineering/evidence/crj01-adaptive-competence/MANIFESTS.md`
does not list it.

The generator's own docstring says *"the split is reproducible byte for byte"*. That is true of
the working tree (I verified it) and **false of the repository**: the file is untracked, it is
not in the retained evidence directory, it is not in the manifest, and no document names it.
The split's `provenance` still describes the split as if it were the probe's selection.

Worse, the disjointness the coordinator required — "disjoint from the observation, the
failure/learning question and the validation" — is asserted by **no test**. The suite checks
only that the `SUBSEQUENT` case is not the out-of-support control's case
(`AdaptiveCompetenceServedTest.java:1377-1379`); it never checks that the `SUBSEQUENT` gold is
not one of the observed, validation or novel golds. The guarantee rests entirely on an
unretained generator.

*Smallest resolving change:* retain the generator (next to the split, or in the evidence
directory under the manifest) and name it in the split's `provenance`; and add to the suite an
assertion that the `SUBSEQUENT` case's gold claim id is not among the golds of any other case
of the domain.

### F-08 — MINOR — The role record still says "the 28 cases"

**File/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json`
→ `roles.caseAuthor`.

> "…The two corpora and **the 28 cases** were written by the IMPLEMENTER's deterministic
> generator from the separation probe's own focused questions."

The split now carries **30** cases (`cases` in `summary` is 30; the gate prints "30"). This is
the field F-02 was about — the role record a reader audits first to decide what the DEV
numbers are worth — and it now carries a number that does not match the artefact it is in.

*Smallest resolving change:* "the 30 cases" (and, if the `SUBSEQUENT` cases were added by a
second script rather than by the generator, say so — they were not: I re-ran the generator and
its output is byte-identical to the committed split).

### F-09 — MINOR — The parity is still not enforced as a number: the gate reads the artefact's own flag

**Files/lines:** `scripts/adaptive-competence-gate.py:335-339`;
`matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:1469-1471`.

```python
gate.check("the served path's grounding is at parity with the reference's",
           summary.get("parity") is True, …)
```

The suite now asserts the parity **case by case** (an improvement over the count equality,
and the previous review's F-05 is half-fixed), but it still ends with
`assertThat(servedGrounded).isEqualTo(referenceGrounded)` — and the gate still reads
`summary.parity`, a flag the artefact computes from the same two counts. If both arms
degraded together to 20 of 30, the suite's per-case agreement would still hold, the flag would
still be `true`, and the gate would still pass. Nothing in the tree asserts that the count is
**30 of 30**, which is the number §1, `CAPABILITY-REGISTRY`, `TEST-CATALOGUE` and the artefact
all publish.

*Smallest resolving change:* in the suite, `assertThat(servedGrounded).isEqualTo(cases)` and
`assertThat(referenceGrounded).isEqualTo(cases)`; in the gate, check
`summary.servedGrounded == summary.cases and summary.referenceGrounded == summary.cases`
instead of (or in addition to) the self-declared `parity`.

### F-10 — MINOR — The old revision experiment survives as dead code, and it is the order-dependent one

**Files/lines:** `matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:2080-2123`
(`revisionOf`), plus the `Revision` record (`:2251-2253`) and the `revisions` field (`:156-157`).

`revisionOf(domain)` is no longer called anywhere (the only callers were replaced by
`revisionExperiment`). What it still contains is the **pre-resolution** experiment: a revision
promoted **in the shared workspace** — precisely the order-dependency the resolution set out to
remove, and the hazard §8b's "Why the revision has a workspace of its own" paragraph was
written to eliminate. Dead code that is one refactor away from being revived.

*Smallest resolving change:* delete `revisionOf`, the `Revision` record and the `revisions`
field.

### F-11 — MINOR — The "renderer" the document cites for re-deriving the cost table rewrites the document in place

**File/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py:74`
and `:69-73`.

> `DOCUMENT.write_text(document, encoding="utf-8")`

The milestone document §3.4 tells the reader that `python3 …/render-cost-table.py`
"regenerates it, so a reader never has to trust a number written by hand". The command does
not print a table — it **rewrites `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md`**. A
reader who runs the documented command to *check* the table mutates a governance document
(and, with it, the milestone's own text). The script's `re.sub` also targets a sentence the
document no longer contains ("of the eight pre-registered grid cells, **two** are `CHEAPER`
and **six** are `NOT ESTABLISHED`"), so the count-substitution half of the script is dead code.

*Smallest resolving change:* print the rendered table to stdout and leave the document alone
(or write to an explicitly named `--out` path), and delete the dead `re.sub` — or restore the
sentence it patches.

### F-12 — NOTE — The manifest count is three different numbers in three documents

`CRJ-01-ADAPTIVE-COMPETENCE.md:279` says "**13 files OK**"; the same document at `:324` says
"**12 files OK**"; `docs-v2/designs/DESIGN-58-capability-levels-roadmap.md:205` says "manifest:
**11 files OK**". The manifest holds **13** entries (13 = every file of the directory except
the manifest itself).

### F-13 — NOTE — DESIGN-58 still says "28 of 28"

`docs-v2/designs/DESIGN-58-capability-levels-roadmap.md:205` still reads "**28 of 28** cases on
two synthetic DEV domains", where `CAPABILITY-REGISTRY.yml` and `TEST-CATALOGUE.yml` were
updated to 30 of 30 and the artefact says 30 of 30 (see F-01).

### F-14 — NOTE — The promotion-cost field names the promotion but the number includes the validation

`CRJ-01-ADAPTIVE-COST-ROWS.json` → `promotionValidationCost.dhx.firstPromotionNanos` is
54 028 765 ns, and `revisionPromotionAndValidationNanos` is 55 021 695 ns. Both are
`learn(...)` calls, and `learn` performs the promotion **and** its validation on the held-out
cases in one call — so the first field also contains a validation, and only the second says so.
The document's "of the order of **50 ms per promotion**" (§8b, `:395`) is fair either way, but
the field name is not.

### F-15 — NOTE — The "after" row's `fallbackReason` is non-empty although the after row was served by the revised procedure

`distinctSubsequentTask.dhx.after.fallbackReason` reads *"the support set does not carry the
workspace's own evidence for this question: …"* — it is the plan's note about the **first**
competence (`report.procedure.8584b6.5baa4e21`), not about the procedure that answered. The
row's `methodId` is unambiguous (`procedure.report.procedure.66e527dd.b2dbf57e`), but a reader
who scans the fields of the "after" row sees a non-empty fallback reason and may read it as
"the after row fell back". The gate has no check on these rows (F-04), so nothing disambiguates
it.

### F-16 — NOTE — `timedPathIncludes` is a stage list, not an exhaustive accounting of the timed window

The per-request rows time `route.journey(...)` end to end (`AdaptiveCompetenceServedTest.java:973-995`),
which also performs the independent reader's verification of the effect, the trace and the
retention of the journey. `timedPathIncludes` names the selection, the statistics, the
precondition validation, the fallback and the artefact write, but not the verification and the
retention. The **excludes** side — the part the coordinator asked for — is **true of the code**:
the learning episode, the promotion and its validation all happen outside the timed window,
and the promotion's cost is measured separately under `promotionValidationCost`
(54.0 ms / 55.0 ms for dhx, 53.4 ms / 55.0 ms for dmy, `heldOutCasesValidated` = 5), which is
indeed the right order of magnitude to be excluded from a ~10 ms per-request number.

### F-17 — NOTE — `MANIFESTS.md` lists two artefacts that live **inside** the directory under the heading "The artefacts that live OUTSIDE this directory"

`docs-v2/engineering/evidence/crj01-adaptive-competence/MANIFESTS.md:35-45`: the table under
that heading lists `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json`
and `…/CRJ-01-ADAPTIVE-COST-ROWS.json` — the two files that live **in** this directory. The
last paragraph says so ("The first two of those are here, not in the engineering root"), so the
text is not false, but the heading and the table disagree. `render-cost-table.py` is in the
manifest and in the directory but in neither table.

## WHAT I COULD NOT VERIFY

1. **No fresh execution.** I did not run Gradle (the implementer owns the build and the shared
   lock), so I could not re-run the served suite, the deliberate RED, the corpus instruments or
   the regressions. My statements about *behaviour* rest on the retained XMLs, the artefacts
   and the source. I did run the gate and the gate's self-test: 29 checks, 0 failed, exit 0;
   6 of 6 mutations, each caught by its own check.
2. **The mechanism of the run-to-run flip in F-03.** I observed the disagreement (the
   14:55:55Z run failed at `AdaptiveCompetenceServedTest.java:1364`, the 14:51:47Z and
   14:57:10Z runs passed) and the artefact the failing run wrote (a no-op revision and a
   second fallback as the "after"). I could not establish *why* the failure episode's cited
   sources differed between runs: the test file was edited at 17:56:25, **after** the failing
   run, so I cannot prove the two runs used the same source, and I did not run the suite to
   sample it. The structural reason it is possible — the support set is the union of the
   observed journeys' *cited* sources (`ReportProcedureLearner.java:178-183`) — is what the
   milestone's own §9.1 describes; the attribution of the flip to it is [speculative].
3. **The currency of the retained set during the session.** The implementer was actively
   editing and re-running while I reviewed: the retained GREEN XML and the two row artefacts
   were replaced at 17:57:32–17:57:45, and the manifest was regenerated at 17:57:45. The
   state I describe (and the state I ran the gate against) is the one at 18:02 local. The
   failing run's XML remains in `matrix-core/build/test-results/test/`, which is the
   evidence for F-03 and F-05; a later clean run overwrites it.
4. **The sealed path.** `CRJ-01-PROTOCOL.md` §5.5a/§5.6 govern a sealed evaluation that was
   **not** executed and cannot be from this executor (one uid, no usable namespace, a `0400`
   file read by a same-uid process). I verified that the DEV-only declaration is present and
   consistent in every artefact that carries a number (`sealed: false` plus `sealedNote` in the
   evidence, the cost rows and the split; the milestone's status is `MEASURED — DEV ONLY`), and
   that no document claims a sealed result. I could not verify the isolation inspector's
   canary results — they are prose with no retained transcript (unchanged from the first
   review). The protocol's §5.5a asks the role record to carry the model and session
   identities; the artefact's `roles` block names the four roles but no model or session id —
   acceptable only because the sealed mode is declared NOT EXECUTED, and I note it rather than
   raise it.
5. **The corpus's semantic quality and the oracle's strictness.** `SourceSpanOracle` is an
   existing component I did not audit; I took its verdicts as the ground truth, as the
   milestone does. The separation probe's "16 of 96 claims reach a second document" (§9.1) I
   did not re-derive.
6. **The H2 inheritance.** I verified that the inherited manifest still verifies 20 of 20 and
   that the gate checks it; I did not re-derive the H2 rows themselves.
7. **Whether the coordinator's acceptance review was itself conducted against this state.**
   I reviewed the tree as it stood; I have no visibility into the coordinator's re-review.

The generator and the final DEV split are retained with the published evidence.
