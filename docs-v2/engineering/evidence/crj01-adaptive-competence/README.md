# CRJ-01 / ADAPTIVE-COMPETENCE — the retained evidence of the adaptive competence milestone

**Status:** `CANONICAL` · **Retained:** 2026-11-27 · **Baseline:** `dff12b5883242b74c09cc2388be32061c2b87cf0`

**Why this directory exists.** The repository's rule is that the evidence of a claim is the
artefact that proves it, and that the artefact must live **in the repository**: a number whose
only witness is a machine that is about to be wiped is a recollection. The milestone's claim
is about what the **served path does** — a procedure whose support set cannot carry a
question's evidence is not selected, the reference answers instead, and the reason is
recorded — and that claim is witnessed by the route's own rows and by the JUnit XMLs of the
suites that produced them.

## What is here

| file | what it holds |
|---|---|
| `TEST-io.matrix.knowledge.AdaptiveCompetenceServedTest.xml` | **the GREEN**: the milestone's own supervisor gate on the **real served route** — the applicability gate, the fallback and its reason, the forced refusal, the restart, the identity conflict, the ambiguity, the citable-status filter, the statistics reuse and its invalidation, the competence revision, and the pre-registered cost grid |
| `CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml` | **the deliberate RED**: the same suite with the applicability gate removed (one expression), so that the failure the milestone sets out to remove is on the record as a reproduction and not as a claim — **5 of 15 tests fail**, and the README says which five and what each says |
| `CRJ-01-ADAPTIVE-EVIDENCE.json` | the **retained rows**: one row per (case × arm) with the method that actually ran, the grounding verdict, the citations, the fallback reason, the effect and the trace's phases — plus the promotion and revision outcomes |
| `CRJ-01-ADAPTIVE-COST-ROWS.json` | the **retained cost rows**: one row per (cell × arm × pass × case) with the wall and CPU times, the method that ran, the grounding verdict and the fallback reason, and the per-cell verdicts read against the **pre-registration** |
| `TEST-io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder.xml` | the instrument that measured the two corpora through the **production** import path |
| `TEST-io.matrix.crj.corpus.Crj01AdaptiveSeparationProbeTest.xml` | the instrument that measured how far each document's vocabulary reaches the others — the property a promotion's agreement depends on |
| `TEST-io.matrix.knowledge.CRJ01H2ServedTransferTest.xml` | the **previous** milestone's suite, re-run: the regression this milestone must not break |
| `TEST-io.matrix.knowledge.RestrictedReadingStatisticsTest.xml` | the correction's own regression, re-run |
| `README.md`, `MANIFESTS.md` | what is here, what is deliberately not here, and which manifest verifies where |
| `CHECKSUMS.sha256` | the digests of every file above — **and of nothing else**: the manifest does not list itself, so it verifies |

## How the RED was produced — the procedure, not a claim

A RED XML is only evidence if a reader can tell **what was changed** to produce it.

**What was changed:** exactly one expression, in
`matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedureMethod.java` — the
question-specific part of the precondition check:

```java
// the pre-correction form — the presence check alone:
return present.containsAll(support);
// the gate — the presence check AND the question-specific adequacy check:
return adequacyOf(input).applicable();
```

— that is, the **presence** check was left in place and the **adequacy** check was removed,
so that the procedure was admitted whenever its support set was **present**, whatever the
question was. **Nothing else was touched** — the corpora, the split, the oracle, the learner,
the method and the route are all as they are in the tree.

**Result — read from the retained XML, not from a recollection:** the suite goes red in
**6 of 16** tests, and the six are:

| the failing test | what it says |
|---|---|
| *a question the support set cannot answer falls back to the reference, and the reason names the evidence* | the procedure was **served** for `AC-DHX-13`, a question its support cannot answer — the method the milestone exists to stop |
| *the served evidence is written: the method that ran, the fallback reason, the evidence and the effect, case by case* | the served path's evidence is **not** the reference's own on the out-of-support cases, so the parity the milestone claims **fails case by case** |
| *the pre-registered cost grid: the reference against the procedure, warm and cold, over two domains and two corpus sizes* | the grid's cells cannot be built on a competence that answers what it cannot — the **distinct subsequent task** has no separate "before" and "after" to compare |
| *a failure episode yields a candidate revision, validated on a separate subset, and the changed competence is measured on distinct subsequent tasks* | *"a changed support set is a different competence, not a new version of the same one"* — with the presence-only rule the competence's support grows with the failure episode, and the revision path cannot be validated |
| *an ambiguous question is not answered, by either path* | the ambiguous question was **answered** — the gate is what keeps it unanswered |
| *every cited claim carries a citable status, and the evidence names its source* | the served response carried a body the route refused, so the evidence was never written |

**What the RED does NOT show, stated plainly:** the restricted reading is **not** served in
this RED. The presence-only rule makes the procedure **applicable**, and the procedure's own
execution then **refuses** the request with 422 — the forced arm answers `the response
carries a body`. The H2 defect's shape — a restricted reading served in place of the
reference — is therefore **not** what this RED reproduces; what it reproduces is the
**applicability** half of the defect: a procedure that is selected for a question its
support cannot answer. The retained XML is the record, and this paragraph is what it says.

**The re-derivation command** (run from the repository root, under the shared lock):

```
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline \
  --tests "io.matrix.knowledge.AdaptiveCompetenceServedTest"
```

## How to re-verify

```
cd docs-v2/engineering/evidence/crj01-adaptive-competence
sha256sum -c CHECKSUMS.sha256
```

The rows and the per-cell verdicts are in **this directory** —
`CRJ-01-ADAPTIVE-EVIDENCE.json` and `CRJ-01-ADAPTIVE-COST-ROWS.json` — **and nowhere
else**: there is no second copy in the engineering root, because a copy that can go stale is
a copy the gate and a reader can disagree about. The corpora are measured in
`docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json`; the cases are in
`docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json`; and the rule the verdicts were read
against is `docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md`.

## What is deliberately NOT here

- **A sealed evaluation.** The isolation a sealed split needs — an implementer that cannot
  read the gold and an evaluator that cannot read the solver — was inspected with **synthetic
  canaries only** and **could not be established from this executor**: one non-privileged
  account, no second identity (`setpriv --reuid` → `Invalid argument`), no usable namespace
  (`unshare --user/--mount/--net` → `Operation not permitted`; `bwrap` → *No permissions to
  create a new namespace*), no `firejail`/`podman`, a harness sandbox that is a **write**
  perimeter only (`bwrap --ro-bind / /`), a reachable Docker socket, and file modes that do
  **not** seal against a same-uid process — a `0400` gold file and a `0700` directory were
  read successfully by the solver's own process. The protocol's own requirement
  (`CRJ-01-PROTOCOL.md` §5.5a: *"Deny the implementer read access before the frozen run"*)
  therefore **cannot be met here**. The measurement is a **DEV** run on the solver's own
  split, and the evidence artefacts say so in their own `sealed` field.
- **A second copy of the rows.** The row artefacts live here and only here, and the gate
  reads them here. An earlier arrangement kept a copy in the engineering root as well; it
  was removed, because the two copies could disagree without anyone having done anything
  wrong.
- **The three legacy artefacts.** `CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json` and
  `CRJ-01-FREEZE-RECORD.json` are rewritten by the legacy corpus-wide runners
  (`io.matrix.crj.runner.*`) whenever they run, and they carry **another writer's local
  changes** in this worktree. They were **not read, not edited, not staged and not
  regenerated** by this milestone, and they are **not** its evidence.
- **The previous milestone's evidence.** `../crj01-h2-served-transfer/` is inherited, not
  re-made. Its manifest still verifies **20 of 20 OK**, and its rows are the **pre-gate**
  measurement — the record of the defect this milestone removes.
