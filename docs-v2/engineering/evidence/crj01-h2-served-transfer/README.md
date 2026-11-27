# CRJ-01 / H2 — the retained evidence of the served transfer measurement

**Status:** `CANONICAL` · **Retained:** 2026-11-27 · **Classification:** RESEARCH_INFORMATION_GAIN · **Decision:** CONTINUE

**Why this directory exists.** The measurement's finding is published in
`docs-v2/engineering/CRJ-01-H2-SERVED-TRANSFER.md`. The repository's rule is that the evidence
of a claim is the artefact that proves it, and that the artefact must live **in the repository**:
a number whose only witness is a machine that is about to be wiped is a recollection.

## What is here

| | |
|---|---|
| `TEST-io.matrix.knowledge.CRJ01H2ServedTransferTest.xml` | the served measurement: the learning episode, the promotion, the restart, the NOVEL question, the kernel's selection, the grounding, the replay's inapplicability, the idempotency conflict, the superseded revision, the abstention and the per-metric arms |
| `TEST-io.matrix.knowledge.RestrictedReadingStatisticsTest.xml` | the correction's own regression: a restricted reading keeps the workspace's relevance statistics |
| `TEST-io.matrix.crj.corpus.Crj01H2CorpusReportBuilder.xml` | the corpus measured through the production import path |
| `TEST-io.matrix.crj.corpus.Crj01H2DevSplitValidatorTest.xml` | the protocol's §5.4 rules (1–7) and the H2 subset structure, enforced on the split |
| `CRJ-01-REPRODUCTION-restricted-reading-statistics.xml` | **the deliberate RED**: the same test with the pre-correction behaviour. All 7 tests fail, and the promotion is refused with "the candidate disagreed with the reference on the held-out case 'H2-A05', so it was not promoted" |
| the remaining `TEST-io.matrix.*.xml` | the targeted regressions of the touched paths: the report journey, the procedure learning and its measured cost, the source-revision chain, the epistemic durability and the provenance |
| `CRJ-01-H2-EVIDENCE.json` | the **retained** rows: one row per (case × arm × variant) and the per-metric summary. The test rewrites its working copy (`docs-v2/engineering/CRJ-01-H2-EVIDENCE.json`) on **every run**, so this frozen copy is the canonical one |
| `README.md`, `MANIFESTS.md` | what is here, what is deliberately not here, and which manifest verifies where |
| `CRJ-01-REPRODUCTION-restricted-reading-statistics-pinned.xml` | **the sharp RED**: the correction's own regression with the correction reverted — **1 test, 1 failure**, and the failure names the defect in numbers (the unrestricted reading cited **4** claims, the restricted one **2**) |
| `CHECKSUMS.sha256` | the digests of every file above — **and of nothing else**: the manifest does **not** list itself, so it verifies |

**43 tests, 0 failures, 0 errors, 0 skipped** across the 15 GREEN suites; the two RED XMLs carry
**8 failures on purpose**.

**A note on the timings in the rows.** The wall-clock numbers in the retained artefact are the
numbers of the run that produced it, and the test rewrites them on every run. Across this
milestone's runs the **difference** between arm C and arm A on wall time ranged from **−0.9 % to
+3.3 %** (median) and **−0.4 % to +7.7 %** (total): **the sign is not stable and the difference is
inside the noise**. **None of these numbers is asserted by any test, none of them is a property
of the code, and no cost advantage is claimed.**

## How the REDs were produced — the procedure, not a claim

A RED XML is only evidence if a reader can tell **what was changed** to produce it. Two REDs are
retained here, and each has its procedure:

### `CRJ-01-REPRODUCTION-restricted-reading-statistics.xml` — the whole milestone's suite

**What was changed:** exactly one expression in
`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java` — the retriever was built
with the **retrieval view's own** statistics instead of the view the reference was read from:

```java
// the pre-correction form:
Answer answer = new KnowledgeRetriever(snapshot, snapshot, ...)
// the correction:
Answer answer = new KnowledgeRetriever(snapshot, statistics, ...)
```

**Nothing else was touched** — the corpus, the split, the oracle, the learner and the method were
all as they are in the tree. **Result: 7 of 7 tests fail**, and the first failure is the
promotion's own refusal: *"the candidate disagreed with the reference on the held-out case
'H2-A05', so it was not promoted"*.

### `CRJ-01-REPRODUCTION-restricted-reading-statistics-pinned.xml` — the correction's own regression

**What was changed:** the same one expression, reverted the same way, and **only**
`io.matrix.knowledge.RestrictedReadingStatisticsTest` was run. **Result: 1 test, 1 failure**, and
the failure names the mechanism in numbers: the unrestricted reading cited **four** claims and
the restricted reading cited **two** —
`expected: ["clm-22457843", "clm-466ca75b", "clm-a6c95294", "clm-ce50a0e2"] but was:
["clm-22457843", "clm-a6c95294"]`.

This second RED is the sharp one: it is the correction's own gate failing, with the defect's
effect in plain sight, so the reproduction cannot be confused with an unrelated breakage.

**The re-derivation command** (run from the repository root, under the shared lock):

```
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline   --tests "io.matrix.knowledge.RestrictedReadingStatisticsTest"
```

## How to re-verify

```
cd docs-v2/engineering/evidence/crj01-h2-served-transfer
sha256sum -c CHECKSUMS.sha256     # 20 of 20 OK
```

The measurement's own rows and per-variant summary are in
`docs-v2/engineering/CRJ-01-H2-EVIDENCE.json`; the corpus the rows are scored against is in
`docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json`; the cases are in
`docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json`.

## The command that produced the GREEN set

```
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline \
  --tests "io.matrix.knowledge.CRJ01H2ServedTransferTest" \
  --tests "io.matrix.knowledge.RestrictedReadingStatisticsTest" \
  --tests "io.matrix.crj.corpus.Crj01H2CorpusReportBuilder" \
  --tests "io.matrix.crj.corpus.Crj01H2DevSplitValidatorTest" \
  --tests "io.matrix.knowledge.SourceRevisionSelectionTest" \
  --tests "io.matrix.knowledge.ClosedLoopReportJourneyTest" \
  --tests "io.matrix.knowledge.ClosedLoopReportJourneyMeasurementTest" \
  --tests "io.matrix.cognitive.report.*" \
  --tests "io.matrix.cli.LearnReportProcedureCommandTest" \
  --tests "io.matrix.knowledge.EpistemicDurabilityTest" \
  --tests "io.matrix.knowledge.KnowledgeProvenanceTest"
```

`GRADLE_USER_HOME` points at a writable copy of `~/.gradle` inside the worktree: the agent's
sandbox makes the home directory read-only, and the Gradle wrapper must write its lock there.

## What is deliberately NOT here

- **A sealed evaluation.** The isolation a sealed split needs — an implementer that cannot read
  the gold and an evaluator that cannot read the solver — has not been established from the
  executor that ran this milestone. The measurement is a **DEV** run on the solver's own split,
  and the artefact says so in its own `sealed` field.
- **The original baseline runner's bytes.** The legacy corpus-wide runners
  (`io.matrix.crj.runner.*`) rewrite `CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json` and
  `CRJ-01-FREEZE-RECORD.json` when they run. Those three files were rewritten as a side effect
  of one broad run during this milestone, and they are **not** this milestone's change set and
  are **not** retained here. **They are not identical to the baseline, and the difference is
  named here rather than glossed:** the two evidence files differ in their **wall-clock timings
  only** — every grounded count (6/9 for arm A, 0/9 for arm B on novel tasks, 36 correct in
  domain B), every digest and every role placeholder is unchanged — and the **freeze record
  differs in one frozen field**: the legacy runner re-pointed `fields/branchSha` from
  `0b34741dc7f0001069e1e7501511a20e771bc824` at the current HEAD
  `7d8c695fa75385ba09e70267e11b7f7301e415c2`. The protocol's §5.5 names `branchSha` as a frozen
  field and §5.6.4 says a change to the freeze voids it, so **that re-pointing is a protocol
  concern in its own right** — it is the reason these three files must not ship with this change
  set, and it is the owner's to decide, not this milestone's.
- **The full suite.** The milestone ran a bounded, named target only, under the repository's
  single shared Gradle lock.
