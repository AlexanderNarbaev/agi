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
| `scripts/validate-docs.sh` | **pre-existing red, not this work's**: it counts any unescaped double-brace Liquid sequence in `docs-v2/` as a potential Liquid issue and flags two GitHub-Actions code samples in files this branch does not touch (`W48-CI-RECONCILIATION.md:848`, `TRANSFORMATION-PLAN.md:237`). Recorded as an inherited baseline condition, not silently repaired — the heuristic change belongs to its own narrow correction. |

## Stage 2 — E2 retrieval quality (in progress, `51b5d3a0` + this round's artifacts)

| Step | Measured result | Artifact |
|---|---|---|
| baseline reproduced on the packaged jar | **18/29**, the documented eleven failing ids | `E2-FLOOR-0p25.json` |
| H1 — the answer floor is too restrictive | floor 0.0 → **15/29** (answerable 4/8, **unanswerable 1/6**): recovers `A-02`,`A-04`, breaks five `U-*` → **rejected** | `E2-FLOOR-SWEEP.json` |
| the two-threshold hypothesis (answer floor 0.25 + citation floor 0.0/0.10) | **17/29**, unanswerable 3/6 → **rejected and reverted before commit**: the quantity gate reads the *cited* set, so the widened evidence let `U-01` answer a "boiling point" question with 14 plate-tectonics claims. The run needed a temporary build whose knob is **not in this tree**, so the artifacts were removed; the numbers are the record of the experiment's result | `E2-RETRIEVAL-EXPERIMENT.md` §3 |
| the one kept production change | `matrix.retrieval.minimum-coverage` knob (default unchanged; contract test; 13/13 in the class) | commit `51b5d3a0` |
| **H2 — the status filter removes legitimate evidence** | **REFUTED**: every gold claim is `SUPPORTED` (citable); the filter removes none | `E2-CANDIDATE-RECALL.json` |
| **H3 — the text match misses legitimate claims** | **CONFIRMED as the residual**: 11 of 27 proposition entries are cited at no floor (no term match); of the 16 cited, Recall@1/5/10 = 3/4/6, MRR 0.2593 (0.1536 over all 27); some already-cited propositions rank 129–368 | `E2-CANDIDATE-RECALL.json` |
| **H4 — downstream** | **CONFIRMED for 3 cases**: `F-02`, `R-04`, `R-05` are unaffected by every floor setting | `E2-FLOOR-SWEEP.json` |
| the floor sweep's full interval | 0.25→18/29 · 0.15→17/29 · 0.10→15/29 · 0.0→15/29; no floor separates the answerable gains from the abstention losses → **no production change** | `E2-FLOOR-0p25/0p15/0p10/0p0.json` (the SWEEP artifact covers 0.10 and 0.15; reproduce with `--force`) |
| the residual's nature | the two never-cited propositions require the semantic relation subduction→trenches; the concept's `definition` is **empty** and no relation edge exists → **enrichment gap, owner-level**, not a ranking patch | `E2-RETRIEVAL-EXPERIMENT.md` §4d |

The full reading is in `E2-RETRIEVAL-EXPERIMENT.md`. Next leverage: H3 (the ranking —
needs a test-scope harness that exposes the per-claim scores) and the H4 cases
(`F-02` verdict logic, `R-04`/`R-05` over-supersession), which the floor sweep proved are
independent of the retrieval threshold.

## Stage 3 — E1 rule-learning evidence base (COMPLETE, result INSUFFICIENT_DATA)

| Fact | Measured | Artifact |
|---|---|---|
| dataset | real: **108 entries (64/44)**, 20 distinct inputs, 24 distinct features; synthetic: **144 (99/45)**, documented content rule, seed 42, deterministic (sha256 stable) | `e1/DATASET-MANIFEST.json`, `scripts/e1-build-dataset.py` |
| the production training window | the last 20 entries are **20 positive / 0 negative, 3 distinct inputs** — one class | `E1-LEARNING-EVIDENCE.json` |
| the real chronological test partition | **21/21 positive** — the 1.000 accuracy is degenerate (balanced 0.500) | same |
| the fold's cost on real data | **24 distinct features → 23 folded values: 1 exact, 1 CROSS-LABEL collision**; 46 informative bits in train, **71** over the log (the library counter's width defect was found in review and fixed; see `E1-LEARNING-EVIDENCE.md` §6) | same |
| fold vs informative projection | no measurable held-out difference on either split (real test degenerate; synthetic test 0.68–0.71 fold vs 0.54–0.68 projection) | same |
| the A/B/C decision | **INSUFFICIENT_DATA** — needs a real dataset with both classes in the evaluation window and >24 distinct features; ADR §4.4 stays OPEN; K_MAX, trainer width and the fold untouched | `E1-LEARNING-EVIDENCE.md` §4 |

## Stage 4 — Evidence-to-Action (IMPLEMENTED + VERIFIED)

| Item | Evidence |
|---|---|
| the only new side effect | `ArtifactWriter` (`matrix-core/src/main/java/io/matrix/actions/`) — canonical-path + traversal + symlink (target, staged path, index) + hard-link + control-character + size + strict-content boundary, atomic staged write, append-only index with torn-line tolerance, identity→artifact binding, crash recovery |
| the action contract | `ArtifactAction` registers `report.artifact.write` in the existing `ActionRegistry` |
| boundary tests | `EvidenceToActionTest` — **29 tests**: normal write, idempotent repeat, same-identity refusal, crash before commit, crash after commit, the move→COMMITTED crash window (same and cross identity), tampered/missing artifact, outside root, symlink escape at the target *and at the staged path and the index*, reserved names, control characters, content bounds, strict JSON, a forged record, a torn index, a hard-linked index, a move failure, the artifact budget, the action contract; plus the demonstration — **30 in total** |
| the real demonstration | `docs-v2/demos/evidence-to-action/output/evidence-report.md` — **51,541 bytes**, SHA-256 `115936e8…`, written from the real seed corpus (41 claims, 2 sources); trace: `.artifact-index.ndjson` (STAGED + COMMITTED); two refusals + a symlink refusal; retry `REPLAYED`; recovery `COMMITTED`; latency one run: seed 1,251 ms / write 1 ms (observed range 1.2–3.6 s) |
| reproduce | `./scripts/b3-demonstrate.sh`; result: `B3-EVIDENCE-TO-ACTION-RESULT.json` |
| the guarantee | at most one committed artifact per action identity; a retry can only reproduce it; a crash between the stage and its record leaves a temp that recovery removes |

## Remaining — independent review and Git integration

Stage 5 (integration verification), Stage 6 (independent review by a separate reviewer) and
Stage 7 (push, PR to `develop`, release workflow) remain ahead.
