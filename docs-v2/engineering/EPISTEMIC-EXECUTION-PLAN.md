# EPISTEMIC-INTEGRITY — the execution plan, evidence ledger and question register

**Status:** `ACTIVE` · **Date:** 2026-11-15 · **Initiative:** EPISTEMIC-INTEGRITY (P0 → P4)
**Authority:** the owner's directive `MATRIX_AUTONOMOUS_DEVELOPMENT_MANDATE_2026-11-15.md`
(sha256 `fad9aeb5`, read-only copy outside the repository), scoped Level 3.
**Branch:** `develop` · **Baseline HEAD at the start:** `57f43aa6` (the C2A snapshot).

## 0. What this initiative is

The owner's central problem, in their words: *the project has consumed substantial time, agent
tokens, CI minutes and engineering effort; demonstrated generalizable user benefit remains
small; change the optimization target from activity and volume to verified capability and
useful outcomes per unit of compute, time and complexity.*

So the initiative's unit of work is **a measured change in behaviour on a served path**, not a
class, a document or a wave. The evidence hierarchy is the directive's: executed production
journey > independent held-out evaluation > bounded integration test > targeted test > source
inspection > agent report > proposal.

**The four P0 defects that matter, as measured by the previous wave:**

| the defect | the measurement |
|---|---|
| the asking path names or refuses a disputed premise in **0/6** unsupported probes; `PremiseAssessment` returns `CONTRADICTED` for **1/6** | unsupported is not contradicted — the repair must separate supported / refuted / unsupported / conflicting / ambiguous |
| conflicting evidence is identified in **0/3** explicit contradiction cases; ambiguous readings are completely represented in **0/4** | both sides and their provenance must become visible in the served response |
| **326 concepts, 348 claims, zero usable definitions, zero persisted relations; 61 concept names end in function words** | the concept identity is upstream of every enrichment attempt |
| experimental probes entered the durable learning log; the last-20 window went 20/0 → 10/10 and the induction fidelity 1.00 → **0.50**; the 256-bit 1-NN ceiling is **.544** balanced | evaluation content must never become learning content, and the label must be questioned |

## 1. The task graph

Status vocabulary: `PROPOSED / ACTIVE / VERIFIED_LOCAL / VERIFIED_CI / MEASURED / REJECTED /
BLOCKED / OWNER_ONLY / NAIVE_PROVISIONAL / RELEASED / SUPERSEDED`.

| id | the work | hypothesis | owner (agent) | depends on | write scope | resource class | evidence needed | acceptance | risk | independent reviewer | status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **P0.1** | explicit premise semantics in `KnowledgeWorkbench.ask` (supported / refuted / unsupported / conflicting / ambiguous) | the asking path can name a disputed premise without false refusals | epistemic-semantics specialist | — | `matrix-core/src/main/java/io/matrix/knowledge/**`, new tests under `knowledge/epistemic/` | build (matrix-core) | characterization before the change; per-class test counts; false-refusal count | a supported premise is answered; a refuted one is rejected with claim ids; an unsupported one is not called false | over-refusal (losing the 18/29 baseline) | the coordinator + the evaluation agent's set | `ACTIVE` |
| **P0.2** | conflicts and ambiguities visible in the served response and the HTTP report | a conflict the corpus holds must reach the caller with both sides | epistemic-semantics specialist | P0.1 | the same | build | the response body carries both sides and their provenance | 3 contradiction and 4 ambiguity cases handled **without** refusing everything | a noisier answer; a leaked path | the evaluation agent | `ACTIVE` |
| **P0.3** | a new sealed held-out set with positive and adversarial negative controls | the epistemic behaviour can be measured independently of the development cases | evaluation/QA specialist | — | `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.*`, `matrix-core/src/test/java/io/matrix/knowledge/epistemic/**` | build (matrix-core) | the freeze's sha256; per-case source spans; a baseline of the current path | the freeze precedes any candidate measurement; false refusals and false contradictions are counted separately | a weak or biased set | the coordinator | `ACTIVE` |
| **P0.4** | evaluation/probe records cannot enter durable organic learning; the contaminated ones stay auditable | provenance must travel with a record through the write path | data-integrity specialist | — | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/**`, its tests, `docs-v2/engineering/EPISTEMIC-LEARNING-ISOLATION.md` | build (brain-runtime) | a leakage test; the window before/after; the quarantine's reversibility | no evaluation record can reach the induction input; nothing is deleted | breaking organic learning by accident | the coordinator | `ACTIVE` |
| **P0.5** | the packaged application opens the SQLite store with no manual property | the packaging defect is a resource-layout problem, not a driver problem | packaging specialist | — | `matrix-core/build.gradle`, `application.properties`, new tests, `scripts/**` | build (packaged — slow) | a clean build + a run from a directory outside the repository | the report action works end to end with no ad-hoc property | a bespoke loader hack without evidence | the coordinator | `ACTIVE` |
| **P0.6** | the post-release `main`/`develop` divergence is reconciled with the live diff | the state must describe the truth, not an assumption | the coordinator | — | `docs-v2/governance/PROJECT-STATE.md` | none | the live `git diff --name-only` | the state names the four differing files and states that no code differs | a false "identical trees" claim | — | `VERIFIED_LOCAL` |
| **G1.a** | the CI simplification RFC and the concrete workflow patch (outside the repository) | the full lane is not needed on every push | DevEx/CI caretaker | — | `docs-v2/engineering/CI-SIMPLIFICATION-RFC.md` | none (CI reads) | the measured before/after from the real runs | the four lanes; the publish lane cannot make ordinary CI red | weakening a safety guard | the coordinator + the owner (OD-053) | `ACTIVE` |
| **G1.b** | the branch inventory and the recovery-verified prune list | most stale branches are safe to remove **with proof** | DevEx/CI caretaker | — | `docs-v2/engineering/BRANCH-INVENTORY.*`, recovery bundles | git only | per-ref recovery recipe; a restoration proven in a temporary clone | every deletion has a proven recovery; pre-W46 and ambiguous refs are preserved | losing unrecovered commits | the coordinator + the owner (OD-054) | `ACTIVE` |
| **G1.c** | `develop` becomes the repository's default branch | the integration branch should be the front door | the coordinator | G1.a | GitHub settings | none | the impact list, checked before the change | the default is `develop`; `main` stays protected | a PR base or workflow surprise | — | `VERIFIED_LOCAL` — applied and verified on 2026-11-15 (OD-056); every workflow names its branches explicitly, so no trigger changed; `delete_branch_on_merge` was already `false` |
| **P1** | concept identity: identify concepts, not fragments | the concept layer is the reason enrichment could not work | the concept-identity analyst (research done ✓) | the P0.3 evaluation for the measurement | `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md`, `matrix-core/src/test/java/io/matrix/knowledge/conceptidentity/**` | research + one targeted build | precision/recall on verified examples; the dangling-word rate; the effect on retrieval | the coverage is re-measured, not asserted | deleting evidence to make a number | — | `MEASURED` (research stage) — the root cause is measured; the implementation waits for P0.3 and for the owner's choice of the identity policy |
| **P2** | the closed-loop cognitive action on the report journey | a recorded method choice can improve the next analogous task | (after P0.1–P0.3) | `docs-v2/engineering/EPISTEMIC-CLOSED-LOOP-DESIGN.md` | design only so far | design | a baseline comparison with the trace disabled | a measured gain — or an honest zero | learning a wrong premise as a good method | — | `PROPOSED` (OD-055) |

**Concurrency controls.** One heavy Gradle JVM at a time through `/tmp/gradle-matrix.lock`; the
`--tests` filter immediately after the task name; never `--rerun`/`--rerun-tasks`; delete
`build/test-results` only inside the lock; the packaged Quarkus build is the slowest job and runs
alone. One agent owns a source file at a time — the write scopes in the table are disjoint, and
the coordinator owns `docs-v2/governance/**`, the capability registry and every commit.

## 2. The evidence ledger (append-only)

| when | what | the evidence | where |
|---|---|---|---|
| 2026-11-15 | the baseline of the served product | 201 write, 200 replay, 403 consent refusal, concurrent replay, restart replay; the artifact 52 971 B, sha256 `eba139f7…c6909ec` | `docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json` |
| 2026-11-15 | the baseline of the retrieval | 18/29, the identical eleven failures | `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md` |
| 2026-11-15 | the baseline of the held-out set | 38 cases; the full-path baseline 17/25 on the 25 comparable ask cases; no candidate accepted | `docs-v2/engineering/C2A-HELDOUT-EVALUATION.md` |
| 2026-11-15 | the baseline of the learning subsystem | the log 108 → 167; the window 20/0 → 10/10; the induction fidelity 1.00 → 0.50; the 256-bit 1-NN ceiling .544 balanced | `docs-v2/engineering/C2A-LEARNING-EVIDENCE.md` |
| 2026-11-15 | the baseline of the premise handling | the asking path refuses 0/6; `PremiseAssessment` contradicts 1/6; conflicts 0/3; ambiguities 0/4 | the owner's directive §2 items 3–4, to be re-measured by P0.3 |
| 2026-11-15 | the baseline of the CI | `Build & Test (Java)` 16m48s on the implementation PR; 21m38s and 21m22s on the two release-PR runs; the aggregate red at the Docker Hub login | the GitHub runs of PR #60 and #61 |
| 2026-11-15 | the release state | `main` `26c549c4`, `develop` `57f43aa6`; `main` ⊂ `develop`; the trees differ by four documentation files and no code | `git diff --name-only origin/main origin/develop` |
| 2026-11-15 | the concept-identity root cause (P1 research) | the concept NAME is the claim's **subject phrase** (`DocumentImporter.java:225-231`) and the concept ID is a digest of that name (`:338-341`) — a concept is the left side of a copula, cut where the verb list stopped; 319 of 326 concepts hold exactly one claim (97.9 %); a hand-labelled sample of 40 names puts the genuine-referent share at **30 %** (CI 18–45 %); the published "61 dangling names" is **not reproducible** from the repository (a band, 18.7–23.0 %) | `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md`, `ConceptIdentityBaselineTest` (6 tests green) |

## 3. The question register

| id | class | question | answer | recorded |
|---|---|---|---|---|
| D1-CI-REQUIRED-CHECKS-001 | D1 (reversible) | which checks are required on `develop` after the CI simplification | **A** — the FAST PR lane's checks are required; the full lane stays manual/release; missing Docker credentials cannot make ordinary feature CI red | OD-053 |
| D1-AUTODELETE-002 | D1 (reversible) | should merged feature branches be auto-deleted | **A** — no auto-delete; deletion only with a verified recovery reference and a proven restoration | OD-054 |
| D1-P2-USER-JOURNEY-003 | D1 (design) | which user journey the closed-loop cognitive action targets first | **A** — the report journey the product already has, driven by the kernel | OD-055 |

**The owner's question tool was used and returned answers in-session** (the harness's native
question tool ✓); no provisional timeout was needed ✓, and nothing was inferred from silence ✓.

**Open, owner-gated, not asked yet** (the directive keeps them owner-reserved and the current
work does not depend on them): the fold replacement A/B/C; the Exp324 2-ms threshold; the peer
payload-at-rest contract; any network-facing enablement of the artifact action.

## 4. The release discipline for this initiative

- Branch from `develop` for bounded, independently reviewable work; short-lived branches; merge
  into `develop`.
- A `main` release only at a user-visible milestone — **not** after every piece of work.
- One-time narrow admin-review bypass only where the current owner authorization applies, the
  applicable gates passed, the independent critical review is documented and the ruleset's
  documented bypass mechanism exists; **every bypass is logged**.
- If a genuine required check fails, the merge is blocked and the blocker is reported.
