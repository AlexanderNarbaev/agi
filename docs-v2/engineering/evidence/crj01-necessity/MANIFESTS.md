# CRJ-01 / EPISTEMIC-NECESSITY — the provenance table

**Date:** 2026-11-28 · **Milestone:** `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md`

Every file of this directory, what produced it, and what role stands behind it.

| file | produced by | role |
|---|---|---|
| `case-authors/A1.json` | the DSH case-author subagent of assignment **A1** | **independent case author** — the oracle |
| `case-authors/A2.json` | the DSH case-author subagent of assignment **A2** | **independent case author** — the oracle |
| `case-authors/A3.json` | the DSH case-author subagent of assignment **A3** | **independent case author** — the oracle |
| `case-authors/A4.json` | the DSH case-author subagent of assignment **A4** | **independent case author** — the oracle |
| `case-authors/A5.json` | the DSH case-author subagent of assignment **A5** (the follow-up round) | **independent case author** — the oracle |
| `case-authors/A6.json` | the DSH case-author subagent of assignment **A6** (the follow-up round) | **independent case author** — the oracle |
| `case-authors/TEMPLATE.json` | the implementer | the shape the authors were given |
| `CRJ-01-NECESSITY-GRID-ROWS.json` | `io.matrix.knowledge.EpistemicNecessityServedTest` — the measurement | the implementer's instrument |
| `CRJ-01-NECESSITY-GRID-SUMMARY.json` | the same suite, from the same rows | the implementer's instrument |
| `TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml` | the same suite's GREEN run | the implementer's instrument |
| `CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml` | `io.matrix.knowledge.EpistemicNecessityRedReproduction` — the **RED** run, with the derivation switched off | the implementer's instrument |
| `REVIEW.md` | the **independent DSH reviewer** subagent | the independent review of the milestone — **0 BLOCKER, 9 MAJOR, 8 MINOR, 4 NOTE** |
| `REVIEW-RESOLUTIONS.md` | the implementer | the response to every finding, with what was changed and how it was verified |
| `README.md` | the implementer | what the evidence is and is not |
| `MANIFESTS.md` | the implementer | this file |
| `CHECKSUMS.sha256` | `scripts/necessity-retain-evidence.sh` | the manifest of the directory |

## What this directory does NOT cover

The artefacts of the **prior** milestones — `evidence/crj01-reach/`,
`evidence/crj01-adaptive-competence/` and the `C2A-*` files — are **not** this milestone's
deliverables. Their suites rewrite their own retained evidence whenever they run, so a broad
test run regenerates them; the files were **restored byte-for-byte from `HEAD`** afterwards.

## The inputs the measurement was made against

| input | where it is | digest recorded in |
|---|---|---|
| the corpus of `wbe` and `hpd` | `matrix-core/src/test/resources/crj01-reach/` | the case set's `provenance.corpusSha256` |
| the case set | `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json` | the assembler records the six authors' digests in `provenance.assignments` |
| the six authors' files | `case-authors/` | `provenance.assignments[].sha256` |
| the pre-registration | `docs-v2/engineering/CRJ-01-NECESSITY-PREREGISTRATION.md` | — |

## The commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

python3 scripts/necessity-case-check.py docs-v2/engineering/evidence/crj01-necessity/case-authors/A*.json
python3 scripts/necessity-cases.py
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityServedTest"
./scripts/necessity-retain-evidence.sh green
python3 scripts/necessity-gate.py
```
