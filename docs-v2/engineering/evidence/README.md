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
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test \
  --tests "io.matrix.knowledge.concept.research.*" \
  --tests "io.matrix.knowledge.concept.HeldOutEvaluationTest" \
  --offline --console=plain
cp matrix-core/build/test-results/test/TEST-*.xml docs-v2/engineering/evidence/
```

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

## The rule

A row of the evidence ledger (`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`) that names a
number **names the file that carries it**, and the file must exist **here**, in the repository. A
number whose artefact is only on a machine that is about to be wiped is not a published number —
it is a recollection.
