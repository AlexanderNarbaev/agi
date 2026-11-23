# The retained manifests of the premise-refutation negative result

**Written:** 2026-11-24 · **Purpose:** say exactly which manifest verifies where, and why.

Every file below is a **digest record**. A digest record is only meaningful together with a
statement of **what it is relative to** and **whether the named files travel with it**. This
file is that statement. It was written after the repository's consolidation audit found that
the evidence set was **incomplete** — several manifests named files the repository does not
carry, so a reader on another machine saw manifests that could not verify.

## The manifests, one by one

### `MANIFEST.sha256` — RETAINED (11 files)

The synthetic fixtures' manifest: the nine fixture documents and the evaluation report.

```
cd docs-v2/engineering/evidence/crj01-premise-negative
sha256sum -c MANIFEST.sha256          # 11 of 11 OK
```

### `runner/RUNNER.sha256` — RETAINED (2 files)

The evaluator's frozen test and its runner script, both beside the manifest.

```
cd docs-v2/engineering/evidence/crj01-premise-negative/runner
sha256sum -c RUNNER.sha256            # 2 of 2 OK
```

### `evidence-baseline/RUNNER.sha256` — HELD BACK, and one of its two files is GONE

It names the **baseline** run's runner — and it is worth reading precisely, because the two
files stand in different states:

| named file | its bytes | where |
|---|---|---|
| `run-frozen-evaluator.sh` | **RETAINED** — in `runner/`, byte for byte | `runner/run-frozen-evaluator.sh` |
| `FrozenPremiseJourneyTest.java` (digest `6b7c3779…`) | **NOT ON THIS MACHINE** | the baseline's copy, before the evaluator applied its correction |

The retained `runner/FrozenPremiseJourneyTest.java` is the **corrected** version
(`95125cec…`) — the one the candidate ran — and it is what `runner/RUNNER.sha256` digests.
The **baseline's** copy of that file was replaced in the evaluator's working directory and
**was not retained anywhere on this machine**; a search of every session directory returns
nothing with that digest.

So: **the baseline's runner bytes are irrecoverable here.** What survives is the **digest**,
in this manifest and in the canonical document, which is enough for the owner to check their
own copy — if they still hold one. The baseline's **result** is fully retained: its JUnit
XML, its rendered evidence, its command, its exit status and its resource measurement.

### `commission/PROPOSED-PAYLOAD.sha256` — POINT-IN-TIME

Records the payload **as it was proposed**, with repository-relative paths. It therefore
verifies **from the repository root**, not from its own directory:

```
cd <the repository root>
sha256sum -c docs-v2/engineering/evidence/crj01-premise-negative/commission/PROPOSED-PAYLOAD.sha256
```

Of its nine files, **seven still match `develop` byte for byte**, one
(`ArtifactReportResource.java`) has changed since, and one (`Crj01ProspectiveProbeTest.java`)
**never landed at all**. So it identifies bytes; it does not assert that the tree still
matches, and it is not a verification target.

### `commission/PROPOSED-PREMISE-SOURCE-EXTENSION.sha256` — RETAINED (2 files, repository-relative)

The two source-extension files the negative result's proposal would have added. **Both are
in `develop` and both match byte for byte**, so this one verifies from the repository root:

```
cd <the repository root>
sha256sum -c docs-v2/engineering/evidence/crj01-premise-negative/commission/PROPOSED-PREMISE-SOURCE-EXTENSION.sha256
```

### `evidence-baseline/`, `evidence-baseline-rendered/`, `evidence-candidate/` — RETAINED

Each carries the run's `command.txt`, `exit-status.txt`, `resource.txt`, `gradle.log` and the
JUnit XML, and the two XMLs the canonical document publishes their digests for.

**This needed a fix.** The `gradle.log` of each run is a build log, and the repository's
`.gitignore` excludes `*.log` as machine state — so three manifests named a file the
repository did not carry, and a reader on another machine saw a manifest that could not
verify. The `.gitignore` now carries an explicit exception for
`docs-v2/engineering/evidence/**/*.log`: **these logs are the record of the run that
produced the result, not machine state.**

## The hidden gold

The evaluator's **gold** — the expected dispositions for the frozen cases — was never
published and is not published here. The mandate forbids it, and a blind evaluation that
publishes its answers is not blind. **The digests published above are the receipt** that
ties the retained evidence to the owner's own copy of the gold, without the repository
carrying the answers.

## Where the held-back files are

The owner's own copy is in the evaluator's session directory, and a copy is in the
consolidation backup at `~/Projects/matrix-consolidation-backup/evaluation-gold/`:

| held back | the owner's copy | the backup |
|---|---|---|
| the corrected frozen test and its runner | `matrix-crj-provenance.5BER3h/handoff/evaluator-evidence.KZ8jhJaC/runner/` | `premise-evaluator-runner/` |
| the premise oracle | the same directory's `oracle.json` | `premise-oracle.json` |
| the **baseline's** frozen test | **not on this machine** — only its digest survives | **not recoverable** |

**The backup is a convenience, not the authority.** The authority is the owner's own copy.
