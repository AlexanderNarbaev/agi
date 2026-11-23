# CRJ-01 — the retained evidence of the premise-refutation negative result

**Status:** `CANONICAL` · **Retained:** 2026-11-24 · **Finding:** `refuted-toy` · **Decision:** PIVOT

**Why this directory exists.** The finding this evidence supports is published in
`docs-v2/research/CRJ-01-PREMISE-TEXT-MATCHING-NEGATIVE-RESULT.md`. That document used to
point at a **path on one machine** — the session's temporary handoff directory — so a
reader on another machine would have found a canonical record pointing at nothing. The
evidence is now **in the repository**, and this file says what is here, how to check it,
and what is deliberately absent.

## What is here

| | |
|---|---|
| `MANIFEST.sha256` | the digests of the synthetic fixtures and the evaluation report — **11 of 11 files verify** with `sha256sum -c MANIFEST.sha256` |
| `oracle.json` | the synthetic fixture manifest: each document's filename, digest and size |
| `*.txt` | the synthetic documents themselves (55–89 bytes each) — the fixtures the evaluator built the cases from |
| `EVALUATION.md` | the evaluator's own report |
| `runner/` | the evaluator's frozen test and its runner script, with `RUNNER.sha256` |
| `evidence-baseline/` | the baseline run: `command.txt`, `exit-status.txt`, `resource.txt`, `gradle.log`, the JUnit XML |
| `evidence-baseline-rendered/` | the same run's XML in the rendered form the document publishes |
| `evidence-candidate/` | the candidate run: the same five records |
| `commission/` | the session's own record: the execution commission, the dispatch, the governance RFC, the source scope, the checkpoints and the applied patches |
| `PREMISE-MILESTONE-CHECKPOINT.md`, `GOAL-CHECKPOINT.md` | the session's checkpoints, as written |

## How to re-verify

```
cd docs-v2/engineering/evidence/crj01-premise-negative
sha256sum -c MANIFEST.sha256          # 11 of 11 OK
sha256sum runner/RUNNER.sha256        # the runner's own digest
```

The two JUnit XMLs report the seven frozen control groups and the five failures per run;
read the suite counters to reproduce the published table.

## What is deliberately NOT here

- **The evaluator's gold.** The mandate requires it — *"do not expose ... hidden gold"* —
  and a blind evaluation that publishes its answers is not blind. The digests above let
  the owner check the retained evidence against the gold they hold.
- **The candidate working copy** (about 174 MB of build output). It is machine state, and
  the repository does not carry build output.

## One machine-specific line, and how to run it on another machine

`runner/run-frozen-evaluator.sh` is parameterised (`$repo`, `$root`) with one exception: it
exports a `GRADLE_USER_HOME` that names the **original machine's** session directory. The
script is retained **byte-identical**, because its digest is published and a retained
evidence file that has been edited is not evidence.

To run it on another machine, copy the script out and change that one line — for example to
`export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"`. Everything else in the script
is already machine-independent. The evaluator's command line is recorded in
`evidence-candidate/command.txt` and in the document:

```
gradle 9.6.0 --offline --no-daemon --no-build-cache --max-workers=2 \
  :matrix-core:test --tests io.matrix.crj.runner.FrozenPremiseJourneyTest
```

under the repository's single shared Gradle lock (`flock /tmp/gradle-matrix.lock`).
