# CRJ-01 — the retained evidence of the source-revision applicability gain

**Status:** `CANONICAL` · **Retained:** 2026-11-24 · **Classification:** PRODUCT_GAIN · **Decision:** CONTINUE

**Why this directory exists.** The finding this evidence supports is published in
`docs-v2/engineering/CRJ-01-SOURCE-REVISION-APPLICABILITY.md`. That document used to point
at a **path on one machine** — the evaluator's session directory — so a reader on another
machine would have found a canonical record pointing at nothing. The evidence is now **in
the repository**.

## What is here

| | |
|---|---|
| `evidence/` | every evaluator run: `baseline`, `green-03ca971`, `green-916a471`, `green-2ffa2cb`, `green-de3cb24` and the accepted `green-f68ec49`. Each carries `command.txt`, `exit-status.txt`, `resource.txt`, `gradle.log`, the JUnit XML, `RESULT.md`, the audit and the request traces |
| `diagnostic/` | the diagnostic run's records |
| `pr78-review/` | the implementation PR's **independent review evidence**: the reviewer's report, the continuation's checkpoints, the architecture prompt and the served-path test XMLs |
| `fixture-MANIFEST.sha256`, `runner-RUNNER.sha256`, `green-runner-RUNNER.sha256`, `green-runner-v2-RUNNER.sha256`, `green-frozen-v2-MANIFEST.sha256` | the digests of the evaluator's fixture and runners |

## How to re-verify

```
cd docs-v2/engineering/evidence/crj01-source-revision/evidence/green-f68ec49
sha256sum -c CHECKSUMS.sha256          # 9 of 9 OK for the accepted run
```

The accepted run's JUnit XML reports the **seven frozen control groups and the ten
reliability controls**; the audit reports the twelve REFUTED and seven UNSUPPORTED
responses it checked against frozen source bytes.

## What is deliberately NOT here

- **The evaluator's frozen cases and gold.** The mandate requires it — *"do not expose ...
  hidden gold"* — and a blind evaluation that publishes its answers is not blind. The
  digests above let the owner check the retained evidence against the gold they hold.
- **The seven evaluator working copies** (about 130 MB each). Machine state; the
  repository does not carry build output.

## The accepted run

The accepted head is `f68ec498a6a6a50a34eecf0653a64385ed9d6c78`, merged as PR #78 at
`b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4`. The evaluator's command line is recorded in
`evidence/green-f68ec49/command.txt`:

```
gradle 9.6.0 --offline --no-daemon --no-build-cache --max-workers=2 \
  :matrix-core:test --tests io.matrix.crj.runner.FrozenSourceRevisionTest
```

under the repository's single shared Gradle lock (`flock /tmp/gradle-matrix.lock`).
