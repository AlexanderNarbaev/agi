# MANIFESTS — CRJ-01 / ADAPTIVE-COMPETENCE

**What this file is.** The list of what this milestone retained and where each artefact came
from. It is the answer to the question a reader asks first: *is this evidence, or is this a
claim about evidence?*

## The manifests

| manifest | covers | verified by |
|---|---|---|
| `CHECKSUMS.sha256` (this directory) | every file of this directory **except the manifest itself** — a manifest that lists itself cannot verify itself | `sha256sum -c CHECKSUMS.sha256` |
| `../crj01-h2-served-transfer/CHECKSUMS.sha256` (inherited) | the **previous** milestone's retained evidence, unchanged except for the README that carries the supersession banner | `sha256sum -c CHECKSUMS.sha256` in that directory — **20 of 20 OK** |

## Where each artefact was produced

| artefact | produced by | command |
|---|---|---|
| `TEST-io.matrix.knowledge.AdaptiveCompetenceServedTest.xml` — **the GREEN** | `io.matrix.knowledge.AdaptiveCompetenceServedTest` | `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.AdaptiveCompetenceServedTest"` |
| `CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml` — **the deliberate RED** | the same suite with the applicability gate removed by one expression (see the README) | the same command, with the gate removed |
| `CRJ-01-ADAPTIVE-EVIDENCE.json` | the GREEN run, written by the suite itself | the same command |
| `CRJ-01-ADAPTIVE-COST-ROWS.json` | the GREEN run, written by the suite itself | the same command |
| `TEST-io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder.xml` | the corpus instrument | `./gradlew :matrix-core:test --offline --tests "io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder"` |
| `TEST-io.matrix.crj.corpus.Crj01AdaptiveSeparationProbeTest.xml` | the separation probe | `./gradlew :matrix-core:test --offline --tests "io.matrix.crj.corpus.Crj01AdaptiveSeparationProbeTest"` |
| `TEST-io.matrix.crj.corpus.Crj01AdaptiveLearningProbeTest.xml` | the learning-episode probe | `./gradlew :matrix-core:test --offline --tests "io.matrix.crj.corpus.Crj01AdaptiveLearningProbeTest"` |
| `TEST-io.matrix.knowledge.CRJ01H2ServedTransferTest.xml` | the **previous** milestone's suite, re-run — the regression this milestone must not break | `./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.CRJ01H2ServedTransferTest"` |
| `TEST-io.matrix.knowledge.RestrictedReadingStatisticsTest.xml` | the correction's own regression, re-run | `./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.RestrictedReadingStatisticsTest"` |
| `REVIEW.md` | the **independent review** by a separate DSH agent: its verdict, its findings and what it could not verify. It is in this directory, and **the manifest covers it** — a review that is not in the manifest is a review nobody can verify has not been changed | written by the reviewer, read by the coordinator |

**The environment.** Every run used `JAVA_HOME=$HOME/jdk25`,
`GRADLE_USER_HOME=$PWD/.gradle/dsh-home`, `--offline`, and the shared lock
`/tmp/gradle-matrix.lock`. The test data is **test-owned** (a temporary directory per
test), and the corpora are the milestone's own synthetic files under
`matrix-core/src/test/resources/crj01-adaptive/`.

## The artefacts that live OUTSIDE this directory, and why

The rows and the numbers are in **this** directory, and only in this one — there is no
second copy in the engineering root, because a copy that can go stale is a copy the gate
and a reader can disagree about. The other artefacts live in the engineering root, because
that is where the catalogue and the capability registry point:

| artefact | what it holds |
|---|---|
| `docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json` | the corpora as the production import path measured them |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-SEPARATION-PROBE.json` | the separation probe: the questions and the reach of every claim |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json` | the DEV cases, their gold and the corpus digests |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md` | the rule the verdicts were read against, written BEFORE the costs were observed |
| `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md` | the milestone document: the measured disposition and the next scientific decision |

**The rows are here, not in the engineering root**: they are the canonical rows, and this
directory verifies on its own — a reader who takes it alone still holds the rows, the costs
and the reviews.

## The tools that produced the artefacts, retained here

| tool | what it made |
|---|---|
| `adaptive-split-fallback.py` | the DEV split — the questions and the gold, selected from the separation probe's narrow claims. It is retained because the split is **generated**, and a generated artefact without its generator is an artefact nobody can reproduce |
| `render-cost-table.py` | the milestone document's cost table, rendered from the canonical rows, so that the table cannot drift from the artefact it cites |
