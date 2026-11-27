# CRJ-01 / COMPETENCE-REACH — the retained evidence

**Milestone:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` ·
**Pre-registration:** `docs-v2/engineering/CRJ-01-REACH-PREREGISTRATION.md` ·
**Baseline:** `7e20e9d9f9945066ebc0120f8e5b379f255ef266` · **Date:** 2026-11-27

> **DEV-only.** The corpus is synthetic and test-owned, the cases were written by the
> implementer, and no sealed evaluation was executed. These bytes are evidence of *this*
> measurement on *this* corpus — not a claim about the system's behaviour on real data.

---

## 1. What is in this directory, and how it was produced

| file | what it is | how it was produced |
|---|---|---|
| `CRJ-01-REACH-GRID-ROWS.json` | **every** per-case row of every arm, cell and condition — 432 rows, 33 fields each, including the **fingerprint of the view** every row was measured against | written by the serving suite (`theEvidenceIsWritten`) during the GREEN run |
| `CRJ-01-REACH-GRID-SUMMARY.json` | the per-cell aggregation: the scope and its validation outcome, the admissions and refusals with **separate denominators**, the false admissions and false rejections, the completeness and coverage means, and the cost records | written by the same run |
| `TEST-io.matrix.knowledge.CompetenceReachServedTest.xml` | the **GREEN** run of the serving suite: 8 tests, 0 failures, 0 errors | copied from `matrix-core/build/test-results/test/` by `scripts/reach-retain-evidence.sh green` |
| `TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml` | the corpus instrument's XML: the corpus measured through the **production acquisition path**, with the assertion that no sentence was left unextracted | copied by the same script |
| `CRJ-01-REACH-REPRODUCTION-no-criterion.xml` | the **RED** reproduction: the same suite with the competitor criterion collapsed onto the incumbent — 8 tests, **2 failures** | copied from the build directory by `scripts/reach-retain-evidence.sh red` during the mutated run |
| `CHECKSUMS.sha256` | the manifest of the directory: `sha256  name`, sorted, over every `.md`, `.json` and `.xml` file | written by the suite's own manifest refresh, and by the retention script after the XMLs were copied |
| `MANIFESTS.md` | the provenance table: the command, the inputs and the role behind every file | this milestone |
| `CASES-PROVENANCE.md` | the case set's provenance: the authoring record, the corpus digests, the slice contract | this milestone |
| `REVIEW.md` | the independent reviewer's findings and what each of them changed | the independent reviewer |

## 2. How to re-check the evidence

```bash
# the manifest verifies
cd docs-v2/engineering/evidence/crj01-reach && sha256sum -c CHECKSUMS.sha256

# the gate re-derives the milestone's counts from the rows, without trusting the test
./scripts/reach-gate.sh

# the gate proves it can go red
./scripts/reach-gate-selftest.sh
```

The gate is **deterministic, offline and read-only**: it re-derives the admissions, the refusals,
the false admissions and the false rejections from the retained rows, checks the two denominators
are separate, checks that every refusal names a reason, that every scope carries its validation
outcome, that the case set's corpus digests match the corpus bytes, and that the retained GREEN
carries no failure while the retained RED carries failures.

**Three checks the INDEPENDENT REVIEW demanded**, without which the gate could have been fooled:
every row must name **the case of the case set it measures**, with that case's own kind and
answerability (a producer that mislabels a row leaves every count intact); every arm's scope must
be **the scope the summary records**, and its validation outcome consistent with its own
agreement; and the **breakdown by the kind of the case** must be re-derived from the rows. The
six arms of a cell must also carry **one input fingerprint**. The self-test mutates the evidence
**fifteen** ways — including a mislabelled kind, a forged scope, a falsified validation outcome, a
falsified breakdown and an arm measured against another input — and requires each mutation to be
caught by the check it belongs to, with the copy's manifest refreshed first, so that no mutation
is caught by the manifest alone.

## 3. The exact commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.CompetenceReachServedTest"
./scripts/reach-retain-evidence.sh green
./scripts/reach-gate.sh
./scripts/reach-gate-selftest.sh
```

> **`--rerun` is mandatory.** The case set and the corpus are not declared inputs of the test
> task, so Gradle treats the task as up-to-date and keeps the **previous** XML. Two runs during
> this milestone were stale for exactly that reason. Every retained XML here was produced with
> `--rerun`, and the rows, the summary and the manifest come from the same GREEN run.

## 4. The RED reproduction — the procedure, not a claim

The RED was produced by **one expression**: the competitor rule
`ScopeCriterion.CLAIM_SUFFICIENCY.judge` was made to delegate to `ANCHOR.judge`, so the six arms
collapse into three and the milestone's comparison becomes impossible. The file was **restored
byte-for-byte** afterwards — the restored file carries no such delegation, and the GREEN run
after the restoration is the one retained here.

The two failures are exactly the two claims the milestone makes about the criterion:

1. `the six arms are measured on identical inputs, and the two criteria differ on a case that
   needs a second claim` — *the two criteria disagree on at least one case of the grid*;
2. `the lifecycle: an observable failure, a diagnosis, …` — *the competitor rule REFUSES the
   same scope for the same question*.

## 5. What this evidence does NOT show

- It does not show that the claim-sufficiency rule should be served. It shows where the two rules
  differ on this corpus and what the difference costs — with separate denominators.
- It does not compare `CITATION_DERIVED` with `EVIDENCE_DERIVED` at `BASELINE` and
  `CHANGED_BINDINGS`: on this corpus they produce the same scope and the same numbers there, so
  that comparison is **not established** in those cells. At `SUPERSEDED` they **differ** (3 against
  4 sources) and the arms differ with them — that comparison **is** established there.
- It does not show anything about the system's behaviour on real data: the corpus is synthetic and
  the case set was written by the implementer, who also wrote the mechanism it measures. **The
  directive's INDEPENDENT DEVELOPMENT GATE FAILED** — two separately commissioned case authors
  stopped at the tool limit and delivered nothing — and this milestone is on the directive's own
  branch for that case: a clearly downgraded DEV result, with the failure stated in every
  artefact and no false role attribution.
- The cost numbers are **records** of one DEV run on one machine. No timing is asserted anywhere.
- **The input fingerprint proves sameness of the LABEL, not of the view.** It says that the six
  arms of a cell were measured against one recorded input; it does not re-derive the view, because
  the view lives in the workspace and the evidence does not hold it.
- **The validation outcome is a producer claim.** The gate checks that it is consistent with its
  own agreement and with the scope the rows carry; it cannot re-run the held-out validation.
- **The grid is an instrument, not a route.** The six arms are measured against a captured view
  of a workspace, through the workbench's own reading; only the lifecycle test drives the
  **served route** (`ArtifactReportResource.journey`), the durable store and a restart. A reader
  who wants the served path's behaviour must read the lifecycle test, not the grid.
- **The corpus is not gold-free in the strict sense**: every gold IS a sentence of the corpus.
  What the corpus's authorship does not affect is the ORACLE — the answers are extracted from
  the corpus by the production acquisition path, so no one wrote the expected answers; what it
  does affect is the CHOICE of the sentences, and therefore the difficulty of the cases.
