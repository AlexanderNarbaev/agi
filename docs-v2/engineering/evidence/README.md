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

**The retained set is now 51 files, 234 tests, 0 failures, 0 errors, 0 skipped** (the refresh of
2026-11-20). It closes the two holes the release's own adversarial review found: the
`io.matrix.knowledge.epistemic.*` package — the class that measures the P0.3 metrics — had **no**
retained XML at all, and the packaging claim had none either.

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
| `TEST-io.matrix.knowledge.epistemic.HardReviewLeakagePlantTest.xml` | the leakage guard's exemption | the clean copy passing and the planted question caught — an **assertion**, not a print |
| `TEST-io.matrix.knowledge.HardReviewP1DefinitionsTest.xml` | the definition half | the migration rehearsed on a copy of the owner's live store, and its idempotence |
| `TEST-io.matrix.packaging.PackagedSqliteNativeLibraryTest.xml` | the packaging | the packaged jar carries the SQLite native library for this platform |
| `TEST-io.matrix.auditor.DecisionPathGateTest.xml` | Article I | the decision-path gate and its baseline groups |
| `TEST-io.matrix.knowledge.concept.research.LiveStoreLauncherTest.xml` | the launcher's own contract | the backup-before-write order, the abort before the live file is touched, the atomic replace, **the live store's referent concepts (20), the claims on them (29) and the rejection split** |

## The rule

A row of the evidence ledger (`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`) that names a
number **names the file that carries it**, and the file must exist **here**, in the repository. A
number whose artefact is only on a machine that is about to be wiped is not a published number —
it is a recollection.
