# The retained manifests of the source-revision applicability gain

**Written:** 2026-11-24 · **Purpose:** say exactly which manifest verifies where, and why.

Every file below is a **digest record**. A digest record is only meaningful together with a
statement of **what it is relative to** and **whether the named files travel with it**. This
file is that statement. It was written after the repository's consolidation audit found that
three of the manifests named files the repository does not carry — which made them look
broken on any machine but the one that produced them.

## The three kinds

| kind | what it means | how to verify |
|---|---|---|
| **RETAINED** | every file the manifest names is **in this repository**, beside it or at a stated path | `cd <the manifest's directory> && sha256sum -c <manifest>` |
| **HELD BACK** | the manifest names the evaluator's **frozen cases or its gold**, which the mandate forbids publishing; the **digests are published**, the files are not | against **the owner's own copy**; the digests are the receipt |
| **POINT-IN-TIME** | the manifest records a payload **as it was proposed**, and the tree has since moved | it identifies bytes; it does not assert the tree still matches |

## The manifests, one by one

### `evidence/<run>/CHECKSUMS.sha256` — RETAINED (six runs)

Each of the six runs (`baseline`, `green-03ca971`, `green-916a471`, `green-2ffa2cb`,
`green-de3cb24`, `green-f68ec49`) carries its own `CHECKSUMS.sha256`, and **every one of them
verifies in full** in a clean checkout. The five **green** runs carry nine files each —
`command.txt`, `exit-status.txt`, `gradle.log`, `resource.txt`, the JUnit XML, the request
traces, `RESULT.md`, the audit script and the audit JSON; the **baseline** run carries
**seven**, because the audit script and the audit JSON were the evaluator's later addition
and the baseline has no counterpart.

```
cd docs-v2/engineering/evidence/crj01-source-revision/evidence/green-f68ec49
sha256sum -c CHECKSUMS.sha256          # 9 of 9 OK
```

**This needed a fix.** The `gradle.log` of each run is a build log, and the repository's
`.gitignore` excludes `*.log` as machine state — so six of the six manifests named a file
the repository did not carry, and a reader on another machine saw a manifest that could not
verify. The `.gitignore` now carries an explicit exception for
`docs-v2/engineering/evidence/**/*.log`: **these logs are the record of the run that
produced the result, not machine state.** With the exception, all six verify in full.

### `fixture-MANIFEST.sha256` — HELD BACK

Names `question.txt`, `v1.txt`, `v2.txt`: the evaluator's fixture — the question and the two
source revisions. These are the evaluator's **case inputs**; publishing them makes the
evaluation reconstructible, which is what the mandate's *"do not expose ... hidden gold"*
rules out. The digests are published so the owner can check their own copy.

### `green-frozen-v2-MANIFEST.sha256` — HELD BACK

Names the frozen case documents (`question.txt`, `v1.txt`, `v2.txt`,
`independent-observer.txt`, `competing-successor.txt`) and `CONTROLS.md`, the description of
the seven control groups. The cases are the evaluation; the digests are published.

### `runner-RUNNER.sha256`, `green-runner-RUNNER.sha256`, `green-runner-v2-RUNNER.sha256` — HELD BACK

Name the evaluator's own runner sources — `SourceRevisionBaselineTest.java`, `run.sh`,
`FrozenSourceRevisionTest.java`, `run-candidate.sh`. A runner's assertions **are** the
expected answers, so the sources are held back and the digests are published.

**What IS retained of the evaluation** is the *verifiable record*: the per-run commands,
the JUnit XMLs, the resource measurements, the request traces, the `RESULT.md` of each run,
the audit script and the audit JSON — and the audit reports the twelve REFUTED and seven
UNSUPPORTED responses it checked against frozen source bytes. That is enough for a reader to
**check the result** without the repository publishing **the answers**.

## Where the held-back files are

The owner's own copy is in the evaluator's session directory, and a copy of the held-back
files is in the consolidation backup at
`~/Projects/matrix-consolidation-backup/evaluation-gold/`:

| manifest | the owner's copy | the backup |
|---|---|---|
| `fixture-MANIFEST.sha256` | `matrix-revision-evaluator.teFBiFtH/fixture/` | `source-revision-fixture/` |
| `green-frozen-v2-MANIFEST.sha256` | `matrix-revision-evaluator.teFBiFtH/green-frozen-v2/` | `source-revision-green-frozen-v2/` |
| `runner-RUNNER.sha256` | `matrix-revision-evaluator.teFBiFtH/runner/` | `source-revision-baseline-runner/` |
| `green-runner-RUNNER.sha256` | `matrix-revision-evaluator.teFBiFtH/green-runner/` | `source-revision-green-runner/` |
| `green-runner-v2-RUNNER.sha256` | `matrix-revision-evaluator.teFBiFtH/green-runner-v2/` | `source-revision-green-runner-v2/` |

**The backup is a convenience, not the authority.** The authority is the owner's own copy;
the digests published here are the receipt that ties the two together.

## The one manifest that is POINT-IN-TIME

`evidence/<run>/CHECKSUMS.sha256` describes the run's own files. But the **premise**
evaluation's `commission/PROPOSED-PAYLOAD.sha256` (in the sibling evidence directory)
describes a **payload as it was proposed**, and the tree has since moved: of its nine
files, seven still match `develop` byte for byte, one (`ArtifactReportResource.java`) has
changed since, and one (`Crj01ProspectiveProbeTest.java`) never landed at all. That
manifest therefore **identifies bytes**; it does not assert that the tree still matches, and
it is not a verification target.
