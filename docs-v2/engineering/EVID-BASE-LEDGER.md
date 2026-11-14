# EVID-BASE — the initiative ledger

**Status:** `ACTIVE` · **Date:** 2026-11-14 · **Branch:** `feature/evid-base-evidence-to-action`
**What this is:** the persistent evidence ledger of the EVID-BASE & Evidence-to-Action
initiative. Each stage records the command that produced its number and the artifact that
holds it, so nothing in this initiative depends on an agent's chat memory.

---

## Stage 0 — baseline (done, `8050de55`)

| Fact | Value | Command |
|---|---|---|
| `origin/main` | `1dc01537` · tree `da7edd36` | `git rev-parse origin/main origin/main^{tree}` |
| `origin/develop` | `343299b1` · tree `da7edd36` | `git rev-parse origin/develop origin/develop^{tree}` |
| `main` ancestor of `develop` | yes | `git merge-base --is-ancestor origin/main origin/develop` |
| ahead / behind | `0 444` | `git rev-list --left-right --count origin/main...origin/develop` |
| hardware | 32 logical CPUs · 59 GiB RAM · JDK 25.0.5 · 186 GiB free | `nproc`, `free -g`, `java -version`, `df -h` |
| evaluation | **18/29**, the documented eleven failing ids | `python3 scripts/w51-epistemic-eval.py` |

The artifact is `docs-v2/engineering/EVID-BASE-BASELINE.json`. The failing set reproduced
exactly (`A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01, P-02, R-04, R-05`), so the E2
workstream starts from a verified baseline rather than a quoted one.

## Stage 1 — canonical corrections (in progress)

### 1a. The verified contradictions, corrected (`1687de91`)

| Document | Contradiction | Correction |
|---|---|---|
| `PROJECT-STATE.md` | the header said pull request #50 was open and the release pending; the snapshot named `0bd5b574`, `origin/main` `c4cee8ab`, "442 ahead" | the header states the release (PR #50 `bcdd1a0a`, #51 `c4cee8ab`, #52 `1dc01537`); the rows name `origin/develop 343299b1`, `origin/main 1dc01537`, `0 444` |
| `PROJECT-STATE.md` | the "active wave" row said W53 awaited the release decision | the row says W53 is released and the active cycle is the EVID-BASE initiative |
| `ROADMAP.md` | "the reconciliation … waits for the owner's word"; the rehearsal described the merge as future | the reconciliation is recorded as done (`e41cd3c1`, `0bd5b574`, `097ffeec`, `1dc01537`, `343299b1`) and the rehearsal's prediction recorded as held |
| `RUNTIME-TOPOLOGY.md` | "They do not meet" with no crossing | no shared data ownership **and** exactly one governed crossing (the bridge) |
| `W53-STATUS.md`, `W53-AUDIT.md` | top-level status `ACTIVE` | `RELEASED`; the wave narrative preserved; a release addendum (§12) added |
| `README.md`, `README.ru.md` | §4 "two applications that do not meet" | §4 "two applications, one governed crossing" + the bridge paragraph |
| `RISK-REGISTER.md` | R-W46-03 cited only the old state | evidence enriched: W53's `04b7c5de` fixed a *different* guard (`DistributedTaskResource`), `WriterLock` still admits aliases, the risk **stays OPEN** |

`scripts/validate-project-state.py --strict` reports **0 defects** after the corrections
(one snapshot-lag observation, by the document's own convention).

### 1b. The consistency check (this commit)

`scripts/validate-current-state.py` — report-only, deterministic, offline. It cross-checks a
small set of invariants between the current-state documents and facts already in the tree:

| Check | The contradiction it catches | The repository fact it compares against |
|---|---|---|
| `RELEASE_CLAIM` | a current-state document says a pull request is open / the release is not done | the main line carries that pull request's squash (`(#N)` in the commit subjects) |
| `TOPOLOGY_CROSSING` | the topology says the chains do not meet | the bridge contract file exists in the tree |
| `STALE_EVIDENCE` | an OPEN register entry cites a file or line that no longer resolves | the file exists (by path, or by a unique basename) and has that many lines |

Usage:

    python3 scripts/validate-current-state.py            # report-only
    python3 scripts/validate-current-state.py --strict   # exit 1 on a contradiction
    python3 scripts/validate-current-state.py --json /tmp/state.json

The negative control is `scripts/validate-current-state-selftest.sh`: it plants a stale
release claim, a topology denial, an unresolved citation and a line beyond EOF into
temporary copies and requires the checker to notice all four, stay silent on the clean copy,
and exit 1/0 correctly in strict mode. It also runs the checker on the real repository and
requires 0 contradictions.

**What the checker deliberately does not do:** it does not parse prose for meaning, does not
check network state (the main-line squash is read from local git), does not rewrite any
document, and does not treat a historical document as a current snapshot — it reads only the
documents named as current state.

### 1c. Validator status after Stage 1 (all run 2026-11-14, on this branch)

| Validator | Result |
|---|---|
| `scripts/validate-project-state.py --strict` | **0 defects** (one snapshot-lag observation, by the document's own convention) |
| `scripts/validate-project-registry.py` | **0 defects** — 56 capabilities, 24 fields per record |
| `scripts/validate-test-catalogue.py` | **0 defects** — 13 scopes, 2 cannot run on this machine |
| `scripts/validate-current-state.py` | **0 contradictions** |
| `scripts/validate-current-state-selftest.sh` | **PASS** (7 controls) |
| `scripts/docs-link-check.py` | exit 0 (0 strict failures; 185 external links unverified) |
| `scripts/validate-docs.sh` | **pre-existing red, not this work's**: it counts any `{{` in `docs-v2/` as a potential Liquid issue and flags two GitHub-Actions code samples in files this branch does not touch (`W48-CI-RECONCILIATION.md:848`, `TRANSFORMATION-PLAN.md:237`). Recorded as an inherited baseline condition, not silently repaired — the heuristic change belongs to its own narrow correction. |

## Stage 2 — E2 retrieval quality (in progress, `51b5d3a0` + this round's artifacts)

| Step | Measured result | Artifact |
|---|---|---|
| baseline reproduced on the packaged jar | **18/29**, the documented eleven failing ids | `E2-FLOOR-0p25.json` |
| H1 — the answer floor is too restrictive | floor 0.0 → **15/29** (answerable 4/8, **unanswerable 1/6**): recovers `A-02`,`A-04`, breaks five `U-*` → **rejected** | `E2-FLOOR-SWEEP.json` |
| the two-threshold hypothesis (answer floor 0.25 + citation floor 0.0/0.10) | **17/29**, unanswerable 3/6 → **rejected and reverted before commit**: the quantity gate reads the *cited* set, so the widened evidence let `U-01` answer a "boiling point" question with 14 plate-tectonics claims | `E2-CITE-SWEEP.json` |
| the one kept production change | `matrix.retrieval.minimum-coverage` knob (default unchanged; contract test; 13/13 in the class) | commit `51b5d3a0` |
| **H2 — the status filter removes legitimate evidence** | **REFUTED**: every gold claim is `SUPPORTED` (citable); the filter removes none | `E2-CANDIDATE-RECALL.json` |
| **H3 — the text match misses legitimate claims** | **CONFIRMED as the residual**: 2 of 18 required propositions are cited at no floor (no term match); the floor-0 recall is 3/4/6 of 18 at K=1/5/10, MRR 0.2593; some already-cited propositions rank 129–303 | `E2-CANDIDATE-RECALL.json` |
| **H4 — downstream** | **CONFIRMED for 3 cases**: `F-02`, `R-04`, `R-05` are unaffected by every floor setting | `E2-FLOOR-SWEEP.json` |

The full reading is in `E2-RETRIEVAL-EXPERIMENT.md`. Next leverage: H3 (the ranking —
needs a test-scope harness that exposes the per-claim scores) and the H4 cases
(`F-02` verdict logic, `R-04`/`R-05` over-supersession), which the floor sweep proved are
independent of the retrieval threshold.

## Stages B2/B3 — not started

E1 (rule-learning evidence base) and Evidence-to-Action remain ahead. Their baselines,
hypotheses and acceptance criteria are fixed in the directive and will be recorded here when
each stage begins, with the command that produced each number.
