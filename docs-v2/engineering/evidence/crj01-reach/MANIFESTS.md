# CRJ-01 / COMPETENCE-REACH — the provenance table

**Baseline:** `7e20e9d9f9945066ebc0120f8e5b379f255ef266` (branch `dsh-writer`) ·
**Date:** 2026-11-27 · **Milestone:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md`

Every row says **what was run**, **what it read** and **who is behind it**. A number without its
provenance is a claim, and this milestone keeps the two apart.

---

## 1. The inputs of the measurement

| input | where it lives | digest / identity |
|---|---|---|
| the corpus (8 documents, 70 sentences) | `matrix-core/src/test/resources/crj01-reach/` | the per-file SHA-256 in `CASES-PROVENANCE.md` and in the case set's `provenance.corpusSha256` |
| the corpus report | `docs-v2/engineering/CRJ-01-REACH-CORPUS-REPORT.json` | 70 sentences, 70 claims, **0 missing** — every sentence became a claim through the production import path |
| the case set | `docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json` | 40 cases, 3 slices per domain, every gold a sentence of the corpus bytes |
| the pre-registration | `docs-v2/engineering/CRJ-01-REACH-PREREGISTRATION.md` | written **before** the first measurement; the §10 amendment is dated and also precedes it |
| the production code under measurement | the baseline commit + the milestone's own production changes | `ScopeCriterion`, `ScopeAssessment`, `ScopeStrategy` (new) and the four modified files |

## 2. The commands, and what each one produced

| command | what it produced |
|---|---|
| `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun --tests "io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder"` | the corpus report: the corpus measured **through the production acquisition path** (`workbench.importDocument` → `ClaimExtractor.propose`), with the assertion that **no sentence was left unextracted** |
| `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun --tests "io.matrix.knowledge.CompetenceReachServedTest"` (GREEN) | the grid rows, the summary, the manifest, and the GREEN XML that `scripts/reach-retain-evidence.sh green` copied into this directory |
| the same run with `ScopeCriterion.CLAIM_SUFFICIENCY.judge` delegating to `ANCHOR.judge` (RED) | the RED XML that `scripts/reach-retain-evidence.sh red` copied into this directory; the file was restored byte-for-byte afterwards |
| `./scripts/reach-gate.sh` | the independent verdict: **23 checks**, 0 failures |
| `./scripts/reach-gate-selftest.sh` | the proof that the gate can go red: **15 mutations**, 15 caught by the right check (the copy's manifest is refreshed first, so no mutation is caught by the manifest alone) |

## 3. The roles behind the numbers

| artefact | produced by | independent? |
|---|---|---|
| the corpus | the implementer (a corpus-author subagent wrote 4 of the 8 documents and stopped) | the corpus is the implementer's; what that does not affect is the oracle (the answers are extracted from the corpus by the production acquisition path), and what it does affect is the CHOICE of the sentences |
| the case set | **the implementer** (two commissioned case-author subagents delivered nothing) | **NO** — this is the milestone's principal limitation, and it is stated in the milestone document, in the case set itself and here |
| the measurement | the implementer | — |
| the gate | the implementer, but it re-derives the counts from the retained rows and its own self-test proves it can fail | the gate checks the *evidence*, not the mechanism |
| the review | an independent DSH reviewer on the final stable snapshot | see `REVIEW.md` and §8.3 of the milestone document, which records what each finding changed |

## 4. The digest of the inputs the milestone asserted

The gate checks that the case set's corpus digests match the corpus **bytes** — so the case set
cannot silently drift away from the corpus it names. That check is one of the seventeen, and the
gate's self-test proves it by mutating a digest.

## 5. What was deliberately NOT produced

- **No timing assertion** appears in any artefact: the cost fields are records of one DEV run.
- **No winner, no threshold, no cost grid** beyond what the pre-registration names.
- **No historical evidence bytes were modified.** The ADAPTIVE milestone's retained files were
  restored byte-for-byte when a regression rewrote them, and `CHECKSUMS.sha256` there verifies.
  The three legacy evidence files (`CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json`,
  `CRJ-01-FREEZE-RECORD.json`) had been rewritten with new timings by an earlier run in this
  session; they were restored from `HEAD` and `git status` shows them unmodified.
- **No sealed evaluation** was executed, and no isolation was re-probed. The ADAPTIVE milestone's
  retained verdict on isolation is reused as it stands.

The grid records SHA-256 input fingerprints as colon-separated hexadecimal bytes. The encoding preserves all 32 digest bytes and leaves the recorded outcomes and timings unchanged.
