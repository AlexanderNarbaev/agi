# EVID-BASE — final report (2026-11-14)

**Status:** `RESEARCH` · **Branch:** `feature/evid-base-evidence-to-action` · **Base:** `origin/develop` = `343299b1`, `origin/main` = `1dc01537`

> This is the directive's §14 report, written from the artifacts in the repository. Sections F
> (CI and release) and the PR identifiers are marked *pending* until the Git integration in
> §12 completes; every other number was produced by a program and is reproducible by the
> command named beside it.

---

## A. Final state

| Item | Value |
|---|---|
| repository | `AlexanderNarbaev/agi` (remotes: `origin` GitHub, `gitverse`) |
| branch | `feature/evid-base-evidence-to-action` |
| base | `origin/develop` `343299b1` (the directive's verified head) |
| commits | 16 at the time of writing (see `git log --oneline origin/develop..HEAD`) |
| working tree | clean except the harness-owned `.dsh-project-memory/` |
| PR | **#53** (feature→develop) — merged as `01d4cdc0`; release PR *pending* |
| final tree SHA | `01d4cdc0`'s tree — see `git rev-parse origin/develop^{tree}`; the release records the final one |
| integration status | **integrated into `develop`** as `01d4cdc0` (PR #53); release to `main` *pending* |

## B. Implemented capabilities

### B1. The bounded Evidence-to-Action file action

| Item | Value |
|---|---|
| source | `matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java`, `ArtifactAction.java` |
| serving path | registered as the action `report.artifact.write` through the existing `ActionRegistry` contract; **not** wired into a production HTTP route (`ActionRegistry` is not instantiated by the running service) — deliberately not claimed as operational |
| user-visible behaviour | one Markdown/JSON artifact per authorized request, in one authorized root; refusals with named dispositions; an append-only trace |
| persistence | the artifact + `.artifact-index.ndjson` (STAGED/COMMITTED records) in the authorized root |
| authorization | the root is an explicit constructor argument; no default root exists; the writer never follows a link (symlink or hard link), never writes outside the root, and binds one action identity to one artifact name |
| tests | `EvidenceToActionTest` — **29 tests**; the demonstration — **1** |
| evidence | `docs-v2/demos/evidence-to-action/output/evidence-report.md` (51,541 bytes, SHA-256 `115936e8…c8ef1b`), its trace, and `B3-EVIDENCE-TO-ACTION-RESULT.json` |
| status | **implemented · tested · demonstrated in-process** (not integrated into a production route, not operational) |

### B2. The project-truth checker

| Item | Value |
|---|---|
| source | `scripts/validate-current-state.py`, `scripts/validate-current-state-selftest.sh` |
| behaviour | report-only deterministic checks: a release claim contradicted by `origin/main`'s subjects; a topology that denies the governed crossing while the contract declares it; stale evidence citations (path:line against the file, unique-basename resolution) |
| tests | the self-test's 7 controls (clean silent; stale release; topology denial; unresolved citation; line beyond EOF; strict exit 1; strict exit 0; real repo 0) — all pass |
| status | **implemented · tested · green** (0 contradictions on the branch) |

### B3. The E2 measurement instruments

`scripts/e2-retrieval-experiment.py` (the floor sweep, one knob), `scripts/e2-candidate-recall.py`
(candidate recall/MRR from the real candidate list) — research instruments, not production code.

### B4. The E1 evidence-base instruments

`scripts/e1-build-dataset.py` (deterministic dataset), `E1LearningEvidenceTest` (the harness that
writes `E1-LEARNING-EVIDENCE.json`) — research instruments. One production defect was found by
the review and fixed: `TsetlinTrainer.countInformativeBits` did not see beyond the first 64 bits
(`CountInformativeBitsTest`, 4 cases). The fold, the trainer width and `K_MAX` are untouched.

## C. E2 results

**Baseline**: `w50-plate-tectonics` v1.1, 29 cases, **18 pass / 11 fail** — reproduced exactly
(`E2-FLOOR-0p25.json`), same failing ids `A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01,
P-02, R-04, R-05` and the same per-category split.

**Root cause per case (measured, `E2-CANDIDATE-RECALL.json`)**: all 27 required-proposition
entries (22 distinct) are present in the workspace and **citable** (`SUPPORTED`) — the status
filter (H2) is refuted. At a zero answer floor 16 of 27 entries are cited (ranks 1–368), 11 are
not cited at all; Recall@1/5/10 = 3/4/6 of 16, MRR 0.2593 cited-only / 0.1536 over all.

| case | cause |
|---|---|
| `A-02`, `A-04` | the answer floor excludes their gold claims (PASS at 0.0) |
| `A-05`, `A-06`, `M-01`, `P-01` | the floor for part of the propositions AND the term match for the rest |
| `M-03`, `P-02` | the term match: the propositions share no term with the question |
| `F-02` | downstream: the premise verdict (`PremiseAssessment.agreeOn`) |
| `R-04`, `R-05` | downstream: over-supersession (`ModelRevisionDecider`, `KnowledgeRefiner`) |

**Comparisons**: answer-floor sweep 0.25→18/29, 0.15→17/29, 0.10→15/29, 0.0→15/29 — no floor
separates the answerable gain from the abstention loss, so **no floor change was made**. The
two-threshold variant (citation floor) measured 17/29 but was **rejected and reverted** because
the quantity gate reads the cited set (a "boiling point" question answered). The residual's
nature: the two never-cited propositions need the semantic relation subduction→trenches; the
concept `Oceanic trenches` has an empty definition and no relation edge — an **enrichment gap**,
not a ranking patch.

**Chosen implementation and measured benefit**: only the inert floor knob
(`matrix.retrieval.minimum-coverage`, default unchanged, 11 tests in
`KnowledgeRetrieverTest`), which enables the experiments; **no retrieval improvement was
adopted** and that is the honest result. No unsupported-confidence increase: the abstention
cases stay refused.

## D. E1 results

| Item | Value |
|---|---|
| dataset | real: `data/mind/episodic.ndjson` by path+sha256 (108 entries, **64/44** labels, 20 distinct inputs, **24 distinct features**); synthetic: 144 controlled episodes (99/45), documented content rule, seed 42, deterministic (stable sha256), 6/2/2 partitions |
| provenance | `matrix-brain-runtime/src/test/resources/e1/DATASET-MANIFEST.json`; generator `scripts/e1-build-dataset.py` |
| leakage | 0 synthetic↔real exact-text overlaps (asserted); real split: 17 of 21 test entries reuse an input seen in train, 0/21 exact pairs (measured, disclosed) |
| fold collisions | real: 24 distinct features → 23 folded values, **1 exact + 1 CROSS-LABEL collision**; synthetic: 144 → 141, 3 exact, 0 cross-label |
| informative bits | 46 (training split) / **71** (log) real; 35 synthetic (the earlier 84 was a library counter defect, fixed) |
| representations | production fold vs informative projection (16 bits) vs random projection: on the real log the test partition is **21/21 positive** (accuracy 1.000 is degenerate, balanced 0.500); synthetic test 0.679–0.714 (fold) vs 0.536–0.679 (projection) vs 0.286–0.464 (random) |
| conclusion | **data distribution is the primary bottleneck**; the fold's measured cost is 1 cross-label collision of 24 distinct features; the informative projection did **not** beat the fold; **the A/B/C decision is INSUFFICIENT_DATA** — ADR-REP-SUBSTRATE-01 §4.4 stays OPEN; `K_MAX=20`, trainer width 16 and the fold are untouched |

## E. Evidence-to-Action

**Reproduce:** `export JAVA_HOME=<full JDK 25, not the JRE at /usr/lib/jvm>; ./scripts/b3-demonstrate.sh`

| Item | Measured |
|---|---|
| task | "What is plate tectonics and what does it say about the Earth's lithosphere?" |
| input | the licensed seed corpus imported through the production path (`usgs-this-dynamic-earth.md`, `wikipedia-plate-tectonics.md`) |
| output | `docs-v2/demos/evidence-to-action/output/evidence-report.md` — answered, 41 claims, 2 sources |
| digest | SHA-256 `115936e8e14a0326aa08260d5c196fd38e924c875aea7472edf7fdc2c8c8ef1b` |
| trace | `.artifact-index.ndjson`: STAGED + COMMITTED, same digest |
| success case | WRITTEN; verification: the artifact exists, its digest matches the record, every cited claim and source appears |
| refusal case | parent-directory name → REFUSED_TRAVERSAL; absolute destination → REFUSED_TRAVERSAL; symlink escape → REFUSED_SYMLINK_ESCAPE (nothing written) |
| repeated identity | REPLAYED, no second write |
| crash/retry | before commit → NO_COMMIT, temp removed; move→COMMITTED window → treated as a commit and replayed; after commit → COMMITTED; tampered/missing → RECONCILED_WRITTEN with a measured digest |
| performance | one run: seed 1,251 ms, write 1 ms, total 1,316 ms; observed range 1.2–3.6 s (JVM warm-up) |

## F. CI and release

**PR #53 (feature → develop)** — every job that ran on the final head `43fba124` passed:

| job | result |
|---|---|
| Build & Test (Java) | **pass** |
| Quality gates (can fail) | **pass** (it initially failed on two literal candidates this branch added, 271→273; both were removed and the gate is back at its accepted baseline 271 — it was not baselined) |
| Container Images (build + smoke) | **pass** |
| Security Scan | **pass** |
| Compile all T-01 modules | **pass** |
| Model-check TLA+ specs | **pass** |
| CONSTITUTION compliance check | **pass** |
| Web UI Tests (matrix-web-ui) | **pass** |
| Validate branch name · RU/EN parity | **pass** |
| Deploy Documentation to GitHub Pages | **pass** (it initially failed on a literal Liquid brace sequence this branch wrote into the ledger; rephrased, not ignored) |

The inherited non-green items are unchanged and were not touched: Docker Hub authentication
during image publication (aggregate workflow `failure` at the verified heads), ShellCheck,
GraalVM Native Build resource limits, and historical unverified TruffleHog findings. This branch
disabled no check and weakened no scan.

The release (develop → main) and its squash commit are recorded in the release PR; the
repository's `main` ruleset requires one approving review, which a sole author cannot supply, so
the directive's single, explicitly recorded administrative bypass applies **only** to that
review rule (the conditions: two independent reviews with documented findings, every genuinely
required check green, no protected file changed, all material findings resolved).

## G. Architectural integrity

- Chain ownership: no Chain A/B database sharing, no new crossing; the action is local and in
  Chain A's process (matrix-core), and the writer touches only its authorized directory.
- Reuse: the corpus import (`AcquisitionCycle.loadSeed`), the workbench answer, the existing
  `ActionRegistry` contract, and the project's own test conventions. No new framework,
  orchestrator, database, plugin manager or representation substrate.
- Frozen invariants: `K_MAX=20`, trainer width 16, the fold, Exp324's 2 ms, the bridge v3
  contracts, `.github/workflows/**`, `CONSTITUTION.md`, `AGENTS.md` — **untouched**.
- New coupling: none in production; the action is not wired into a route, so no serving change.
- Capability registry: not updated (the action is not operational); a follow-up is named in H.
- The one production change: `countInformativeBits` is now correct beyond 64 bits (a bug fix
  with its own regression test); the floor knob is inert by default.

## H. Owner decisions

| decision | state |
|---|---|
| Exp324 threshold | **unchanged at 2 ms** |
| fold replacement A/B/C | **OPEN** — the E1 evidence says INSUFFICIENT_DATA; the experiment that would decide it: a real evaluated window with both classes and > 24 distinct features (see D) |
| peer durable payload contract | **OPEN** — untouched (E3 boundary) |
| new: a production route for the file action | **owner's** — the action is implemented and tested; wiring it into an HTTP route, and its consent model, is a serving decision |
| new: a durable, tamper-evident trace | **owner's** — the current trace is an append-only NDJSON index; moving it onto the existing `HashChain` is a design decision with storage/privacy trade-offs |
| new: concurrency for one root | **owner's** — the guarantee is single-writer; multi-writer needs a lock or a single-writer service |

## I. Next development priorities

1. **Concept enrichment for retrieval** (the E2 residual's measured cause): give concepts
   definitions and relations; measure on the same 29 cases + a held-out set. This is the only
   measured path to the answerable cases that remain failing.
2. **Wire the file action into one serving route** with an explicit consent contract, and
   exercise the directive's §7 demonstration over HTTP (it is currently in-process).
3. **A real evaluated E1 window** (both classes, more distinct features) so the fold decision
   can leave INSUFFICIENT_DATA — otherwise the representation work stays blocked behind data.

## J. Handoff

**Completed:** project-truth corrections + the consistency checker; E2 measured and classified
with a reproducible negative result and no unsound production change; E1 measured with a
provenance-carrying dataset and an INSUFFICIENT_DATA verdict; the bounded Evidence-to-Action
action implemented, hardened through two independent reviews, tested (30 tests) and demonstrated
with a real artifact, refusals and a trace.

**Not completed:** the PR/release (Stage 7, *pending*); a production route for the action; a
hash-chained trace; multi-writer support; a real E1 evaluation window.

**Verified:** the commands and artifacts above, the targeted suites, the demonstration's digest
and trace, the doc validators (0 defects, 0 contradictions, link-check exit 0).

**Not verified:** the full `matrix-core` suite at the final HEAD (the gate is running; its result
is recorded before the push), and CI on the remote.

**Remaining blockers:** none technical; the release depends on the repository's single-owner
review ruleset and on the Docker Hub credentials state (inherited).

**Smallest actionable next step:** open the PR from `feature/evid-base-evidence-to-action` into
`develop` and read the workflow's individual job conclusions; then the release PR
`develop`→`main` per the project's squash convention.
