# The retained evidence of the epistemic-integrity programme

This directory holds the **retained JUnit XML** of the targeted suites the programme's
conclusions rest on. The repository's rule is that the evidence of a claim is the XML, not a
console summary — and the XML has to be **copied out** of the build tree, because a Gradle test
run **wipes** `matrix-core/build/test-results/` (the rule is written down in the root
`AGENTS.md`).

## Why the directory exists

The hard review of 2026-11-18 found that **seven of the eight** XMLs the evidence ledger named
for this wave existed nowhere: not in the repository (the build tree is gitignored) and not in the
working tree (the next Gradle run had wiped them). The ledger pointed at artefacts that could not
be opened. The claims themselves were true — the review verified the numbers against the store and
against the code — but the **chain of evidence** had a hole in it, and a hole in the chain is
exactly what the programme exists to remove.

## How the artefacts are produced

One Gradle invocation, one filter per suite, and the XMLs are copied out **before** the next run:

```
export JAVA_HOME=$HOME/jdk25; export PATH=$JAVA_HOME/bin:$PATH
rm -rf matrix-core/build/test-results matrix-core/build/reports/tests
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test \
  --tests "io.matrix.knowledge.*" \
  --tests "io.matrix.auditor.*" \
  --tests "io.matrix.cognitive.report.*" \
  --tests "io.matrix.packaging.*" \
  --offline --console=plain
cp matrix-core/build/test-results/test/TEST-*.xml docs-v2/engineering/evidence/
```

**The retained set is now 74 files: 71 GREEN suites, 352 tests, 0 failures, 0 errors, 0
skipped** (the refresh of 2026-11-21, after CRJ-01's arm runner: the corpus report, the dev
split's validator, the arm runner, the cost curve and the measured cost join the set). The
remaining **3 files are the deliberate RED reproductions** — 6 tests, **4 of which fail on
purpose**, because they are the record of a defect that was found: a concurrent journey
refused for a busy database, an idempotency key reused with a different question, and a
journey whose trace episode was not a real episode. **They are supposed to fail**; the GREEN
file beside each one is the proof that the defect was fixed. It closes the two holes the release's own adversarial
review found: the `io.matrix.knowledge.epistemic.*` package — the class that measures the P0.3
metrics — had **no** retained XML at all, and the packaging claim had none either.

### The FULL suite, and what is NOT retained of it

The full `matrix-core` suite was run as well — **1373 suites, 9845 test methods, 0 failures,
0 errors, 23 skipped**, in **33 minutes 14 seconds** on the measuring machine (the refresh of
2026-11-21, after CRJ-01's arm runner; the previous refresh read **1359 suites, 9808 test
methods, 0 failures, 0 errors, 23 skipped** in 29 minutes 35 seconds). **Its XMLs are not all
retained here**, and the reason is the directory's own rule: the retained set is the suites the
conclusions **rest on**, and the full suite's 1373 XMLs are 5.5 MB of files whose
content the conclusions do not rest on — they rest on the **count**, and the count is recorded
here and in `docs-v2/engineering/CRJ-01-ARM-C.md`.

What the full run **found** is retained, because it rests on it:

| the file | what it holds |
|---|---|
| `CRJ-01-REPRODUCTION-concurrent-journey-sqlite-busy.xml` | the **RED** of the concurrency defect: one test, one failure — the journey refused a request because the database was busy and called it a broken output root |
| `TEST-io.matrix.knowledge.ConcurrentReportJourneyTest.xml` | the **GREEN** of the same gate: two concurrent requests with the same identity, 40 rounds, not one refusal |

The full suite's XMLs can be reproduced with the command above and `--tests "io.matrix.*"`; they
are not committed because a number that names 1373 files is a number a reviewer cannot open.

## What each file is

| the file | the suite | what it holds |
|---|---|---|
| `TEST-io.matrix.knowledge.concept.HeldOutEvaluationTest.xml` | the sealed held-out set | the two rows of the calibration (the unmigrated and the migrated copy), the per-case answered flags, and the claim-scoped comparison |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreMigrationDryRunTest.xml` | the dry run of the owner's live store | the per-workspace counts, the audit, the rollback |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreLauncherTest.xml` | the launcher's own contract | the backup-before-write order, the abort before the live file is touched, the atomic replace |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreServingCheckTest.xml` | the serving path | the served statements of both workspaces |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreKernelJourneyTest.xml` | Gate 5 | the plan, the permission, the action, the effect, the trace's phases |
| `TEST-io.matrix.knowledge.concept.research.LearningTablesAfterMigrationTest.xml` | the learning tables | the row counts, the dangling pointers, the before/after targets |
| `TEST-io.matrix.knowledge.concept.research.PrerequisiteMergeTest.xml` | the prerequisite merge | the collisions and the dropped self-loops |
| `TEST-io.matrix.knowledge.concept.research.PrepositionalPhraseProbeTest.xml` | the prepositional rule | the false promotion and the controls that must stay accepted |
| `TEST-io.matrix.knowledge.concept.research.ReferentKeyInstrumentTest.xml` | the identity instrument | the referents, the rejections, the definitions |
| `TEST-io.matrix.knowledge.epistemic.EpistemicBaselineTest.xml` | the P0.3 baseline | the sealed EPISTEMIC set's freeze digest, the per-case dispositions and the five metrics |
| `TEST-io.matrix.crj.corpus.Crj01CorpusReportBuilder.xml` | the corpus report | the pool's usable remainder: **540 claims, 483 verbatim spans, 470 fresh, 413 both** — and the builder's own assertion that it read the regression sets at all |
| `TEST-io.matrix.crj.corpus.Crj01DevSplitValidatorTest.xml` | the dev split's validator | every rule of the protocol's §5.4 on the split: the gold verbatim in its document, the gold a production claim, no four-token question overlap with the three regression sets, no regression gold, the classes present, the keys fresh |
| `TEST-io.matrix.crj.runner.Crj01ArmRunnerTest.xml` | the arm runner | **36 rows** — one per (case, arm) — the arms' groundings and their wall and CPU times, and the evidence artefact's byte-for-byte reproduction |
| `TEST-io.matrix.cognitive.report.ArmCCostMeasurementTest.xml` | the cost curve | the saving against the support's fraction of the workspace: **78 %** at a sixth of a 540-claim corpus, **≈0 %** at all of it |
| `TEST-io.matrix.cognitive.report.ReportProcedureMeasuredCostTest.xml` | the measured cost | the promotion records the cost it measured, the method declares it, and the decision's own reason names the basis `MEASURED_COMPLEXITY` |
| `TEST-io.matrix.knowledge.epistemic.HardReviewLeakagePlantTest.xml` | the leakage guard's exemption | the clean copy passing and the planted question caught — an **assertion**, not a print |
| `TEST-io.matrix.knowledge.HardReviewP1DefinitionsTest.xml` | the definition half | the migration rehearsed on a copy of the owner's live store, and its idempotence |
| `TEST-io.matrix.packaging.PackagedSqliteNativeLibraryTest.xml` | the packaging | the packaged jar carries the SQLite native library for this platform |
| `TEST-io.matrix.knowledge.ClosedLoopReportJourneyMeasurementTest.xml` | the closed loop's win | the two arms measured **interleaved**, the difference the method choice makes |
| `TEST-io.matrix.auditor.DecisionPathGateTest.xml` | Article I | the decision-path gate and its baseline groups |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreLauncherTest.xml` | the launcher's own contract | the backup-before-write order, the abort before the live file is touched, the atomic replace, **the live store's referent concepts (20), the claims on them (29) and the rejection split** |

## The rule

A row of the evidence ledger (`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`) that names a
number **names the file that carries it**, and the file must exist **here**, in the repository. A
number whose artefact is only on a machine that is about to be wiped is not a published number —
it is a recollection.
