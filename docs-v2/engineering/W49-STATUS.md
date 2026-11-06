# W49 — STATUS

**Wave:** W49 · **Owner decisions:** **OD-045** (the autonomy consumer, answered
2026-11-06), **OD-046** (the native-image resource budget), **OD-047** (the GPU
is an optional accelerator) and **OD-048** (the public-research workstream,
2026-11-06) · **Date:** 2026-11-06

**What this wave was.** Two implementation tasks the W48 wave left approved
(TASK-W48-03 and OD-046), and one new permanent workstream the owner opened:
making the project **publicly understandable and externally verifiable**
without making it look more advanced than it is (OD-048).

**The one-line outcome.** The mandatory CI checks are green, the accepted
autonomy impulse is now consumed by a bounded internal proposal that does
nothing else, the native build's resource budget is measured instead of
guessed, and the project's public face — README, site, onboarding,
publication path, collaboration brief — now states what the repository
actually does, with every claim traceable to an artefact.

---

## 1. The commits

| the commit | what it carries |
|---|---|
| `c86f6898` | the autonomy impulse consumer (OD-045): the record, the store, the consumer, the wiring, and the ADR's ten acceptance tests |
| `4d86c5f4` | the public-presence wave: the README, the site, the onboarding, the publication pipeline, the collaboration brief, the audits, the native measurement, and this report |

The wave started at `ed3725d58c9747533c44a5e03298fd473693a9cb` (the W48 close)
on `develop`, and `origin/develop` carried every commit.

---

## 2. The tasks and their states

| the id | the task | the state | the evidence |
|---|---|---|---|
| **W49-00** | the baseline and the audit of the public presence | **DONE** | `docs-v2/engineering/W49-BASELINE.md`, `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` |
| **W49-01** | the autonomy impulse consumer (OD-045, TASK-W48-03) | **DONE — locally tested and CI-tested** | `c86f6898`; 12 tests, 0 failures; the ADR's §9 discharged one by one |
| **W49-02** | the native-image resource budget (OD-046) | **DONE — the measurement is taken; the capability stays BLOCKED** | `docs-v2/engineering/W49-NATIVE-BUDGET.md` |
| **W49-03** | the public-presence audit | **DONE** | `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` |
| **W49-04** | the root README (EN + RU) | **DONE** | `README.md`, `README.ru.md`, `docs-v2/engineering/W49-PUBLIC-README.md` |
| **W49-05** | the Pages landing page and the high-visibility pages | **DONE** | `docs-v2/index.md` and 34 more pages; the audit |
| **W49-06** | the Pages deployment path | **DONE** | `.github/workflows/pages.yml` (a pull-request build that does not publish), `scripts/validate-pages-config.sh` |
| **W49-07** | the contributor onboarding | **DONE** | `CONTRIBUTING.md`, `CODE_OF_CONDUCT`, the five issue templates, the PR template, `docs-v2/engineering/W49-ONBOARDING.md` |
| **W49-08** | the publication pipeline | **DONE** | `docs-v2/research/PUBLICATION-PIPELINE.md`, `PUBLICATION-CANDIDATES.md` |
| **W49-09** | the collaboration brief and a reproducible opportunity | **DONE** | `docs-v2/research/COLLABORATION-BRIEF.md`, `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` |
| **W49-10** | this report and the governance reconciliation | **DONE** | this document; the registers |

---

## 3. The autonomy impulse consumer — OD-045

**The scope was unchanged from the approved ADR.** The consumer creates and
records typed internal proposals and does nothing else: no message, no
command, no external service, and no write to Chain A.

| the file | what it is |
|---|---|
| `matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposal.java` | the record: id, impulse, kind, provenance, budget, expiry, state |
| `matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposalStore.java` | the seam — the only thing the consumer writes |
| `matrix-core/src/main/java/io/matrix/autonomy/BoundedImpulseProposalStore.java` | the bounded in-memory implementation; a full queue refuses |
| `matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposalConsumer.java` | the consumer |

**The scheduler's record now carries the shadow price** of the allocation that
accepted the impulse, because the proposal has to record "the shadow price at
the moment of the allocation" and the scheduler is the only place that
allocation happens. The consumer never allocates on its own.

**The consumer is wired into the serving paths**: the autonomy engine, the
self-improving engine, and the brain runtime's autonomy loop. The engine's
idle cycle sweeps the expired proposals, so the bounded queue never fills with
dead entries.

**The scheduler's `FIRED` outcome still means "accepted".** It now also means
"recorded as a proposal". It never means "executed".

### 3.1 The tests — the ADR's §9, one by one

| the ADR's test | the test method | the result |
|---|---|---|
| §9.1 an accepted impulse produces exactly one proposal with provenance, budget and a stable id | `anAcceptedImpulseProducesExactlyOneProposal` | pass |
| §9.2 a refused impulse produces no proposal | `aRefusedImpulseProducesNoProposal` | pass |
| §9.3 the proposal id is reproducible | `theProposalIdIsReproducible` | pass |
| §9.4 the queue is bounded; the overflow is refused and counted | `theQueueIsBoundedAndTheOverflowIsRefused` | pass |
| §9.5 a duplicate is refused | `aDuplicateIsRefused` | pass |
| §9.6 the FROZEN gate is consulted at execution time | `theFrozenGateIsConsultedAtExecutionTime` | pass |
| §9.7 the consumer cannot reach Chain A | `theConsumerCannotReachChainA` | pass |
| §9.8 every proposal reaches a terminal outcome after its expiry | `everyProposalReachesATerminalOutcome` | pass |
| §9.9 the expiry comes from the injected monotonic source | `theExpiryUsesTheInjectedMonotonicSource` | pass |
| §9.10 the budget is accounted once, not twice | `theBudgetIsAccountedOnce` | pass |

Plus two contract tests (a consumer without a gate cannot be constructed; the
shipped gate is the ethical filter's frozen-axiom check).

**The negative control**: with the execution-time FROZEN check disabled, the
suite reports *"12 tests completed, 1 failed"* — the §9.6 test goes red. With
the check restored, it is green.

**One deviation, stated.** The ADR's §9.7 asks for "the Chain A fingerprint is
unchanged across a proposal". The test is **structural rather than a
fingerprint**: it asserts that the consumer's source references no Chain A
store, no JDBC, no process, no socket and no file write, and that its
collaborators are only the store, the gate and the clock. The alternative —
opening the live knowledge database to fingerprint it — is forbidden by the
data-safety rule, and a temporary database would make the assertion vacuous.

---

## 4. The native-image resource budget — OD-046

The measurement, its commands and its limits are in
`docs-v2/engineering/W49-NATIVE-BUDGET.md`. The short form:

| the question | the measured answer |
|---|---|
| the heap the build needs | **more than 8 GB** — 8 GB is Quarkus's default and it OOMs; 16 GB clears the memory wall |
| the runner the build needs | **more than GitHub's standard 16 GB runner** — the CI's own log gives 7.64 GB = 45.6 % of a ≈16.75 GB machine |
| the local feasibility | **feasible** — 59 GiB total, 47 GiB available, with GraalVM CE 25.5.5.0.3 installed outside the repository |
| the capability | **still BLOCKED** — the build does not succeed |

**The second blocker is named, not guessed.** With the memory resolved, the
image generation fails on
`NoClassDefFoundError: org/bouncycastle/cms/CMSException`: PDFBox declares
BouncyCastle as an **optional** dependency and the application does not add
it. The fix is one line — `bcpkix-jdk18on` at the version PDFBox 3.0.3's
parent declares — and it is **not applied**, because adding a runtime
dependency is the owner's call and OD-046 asked for a measurement.

**What was NOT measured**: the size of the produced binary — the build never
completes, so no number is offered.

---

## 5. The public presence — OD-048

### 5.1 What was found

| the finding | the evidence |
|---|---|
| the default branch is **357 commits** behind `develop` | `git rev-list --count main..develop`; `main` = `7a0ed0cc`, 2026-10-12 |
| the deployed site carries the withdrawn positioning | the live page: "city of minds" ×4, "W1500" ×2, wave references W30–W33; the title "MATRIX: The Hybrid Intelligence Encyclopedia" |
| the site's `url` named a host that does not serve it | `docs-v2/_config.yml` said `https://alexandr-narbaev.github.io`; the deployment URL, read from the API, is `https://alexandernarbaev.github.io/agi/` |
| there was **no preview path** for the site | `.github/workflows/pages.yml` deployed on a push to `main` and did nothing else |
| the repository had no description, no topics and no homepage | `gh api repos/AlexanderNarbaev/agi` |
| two conflicting contribution guides existed | `CONTRIBUTING.md` and `CONTRIBUTING`; the second promised a 48-hour review, a chat and office hours, and linked to the v1 `docs/L0`…`docs/L13` tree <!-- no-link: the v1 documentation tree was removed; the mention is historical --> and to `CODE_OF_CONDUCT.md` — none of which exist |
| the security template pointed at a file that does not exist | `.github/ISSUE_TEMPLATE/security.md` → `SECURITY.md`; and `private_vulnerability_reporting` is **not** enabled on the repository |
| the pull-request template asked for a Checkstyle pass | the project has no Checkstyle: `grep -rn "checkstyle" --include=*.gradle .` → no match |

### 5.2 What was rewritten

| the surface | the files | the record |
|---|---|---|
| the README | `README.md` (539 lines), `README.ru.md` (538 lines) | `docs-v2/engineering/W49-PUBLIC-README.md` |
| the site | `docs-v2/index.md` + 34 ecosystem/guide/story pages and their Russian equivalents | `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` |
| the configuration | `docs-v2/_config.yml` — the url typo, the title, the description, the footer | the same audit |
| the onboarding | `CONTRIBUTING.md`, `CODE_OF_CONDUCT`, the five issue templates, the PR template, and `CONTRIBUTING` → a pointer | `docs-v2/engineering/W49-ONBOARDING.md` |
| the publication path | `docs-v2/research/PUBLICATION-PIPELINE.md`, `PUBLICATION-CANDIDATES.md` | the pipeline document itself |
| the collaboration | `docs-v2/research/COLLABORATION-BRIEF.md`, `REPRODUCIBLE-OPPORTUNITIES.md` | the brief itself |

### 5.3 The falsehoods that were removed

The pages carried claims that no artefact supports. The ones that were cut:

- **"33 000× faster than an LLM"** — the origin was `docs-v2/story/overview.md`,
  which divided a generation rate by a single-call latency. It is a refuted
  claim (NR-015) and the page that produced it was rewritten.
- **"724 tests pass"**, **"9 432 tests"**, **"100 % compliance with the
  Constitution"**, **"100 % on TerminalBench"**, **"100 % on ARC-AGI"** — no
  artefact measures them.
- **a hosted API at `api.matrix.ai`** with keys, plans and rate limits — it does
  not exist, and the Java SDK's own default base URL points at that
  non-existent host.
- **`localhost:8080/health`** and **`POST /brain/query`** — no such port and no
  such route anywhere in the repository.
- **eight invented articles of the constitution**, **twelve non-existent
  algorithm classes**, **HDC as 10 000 bits** (it is 1024), **BIR as forward
  chaining over Horn clauses** (it is a compiler over bit-level functions),
  **"1 000 req/s, 50 ms p99"**, **a 1 000-node federation pool**, **five trace
  stages the runtime does not emit**.
- **the "Three Prohibitions"** (the constitution has four), **"AGPLv3 with
  Ethical Restrictions"** (AGPL-3.0 is the only licence), **a maintainer
  address that does not exist**, **a 48-hour response**, **a chat**, **office
  hours**, **a moderation team**.

### 5.4 The three descriptions, kept apart

The directive requires the **vision**, the **current implementation** and the
**evidence and limitations** to stay separate. In the rewritten material they
are three separate sections, and the landing page's dated status block names
the commit and links to the reports.

### 5.5 The recommended repository metadata — prepared, not applied

The description and the twelve topic tags are in `W49-BASELINE.md` §7. They
are **not** applied: the repository's settings are the owner's. GitHub
Discussions stays **disabled** — there is no moderation and reporting
arrangement, and the directive forbids enabling it until there is one.

---

## 6. The tests, the gates and the CI

| the check | the command | the result |
|---|---|---|
| the consumer's suite | `./gradlew :matrix-core:test --tests "io.matrix.autonomy.ImpulseProposalConsumerTest"` | **12 tests, 0 failures, 0 errors, 0 skipped** (JUnit XML) |
| the autonomy and lifecycle scopes | `./gradlew :matrix-core:test --tests "io.matrix.autonomy.*" --tests "io.matrix.lifecycle.*"` | **65 tests, 0 failures, 0 errors, 0 skipped** |
| the gateway | `./gradlew :matrix-api-gateway:test` | **BUILD SUCCESSFUL** |
| the whole tree compiles | `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| the clean-code gate | `bash scripts/quality-gate.sh` | **PASS: 271 candidate(s), 0 other violation(s)** — the literal baseline is unchanged |
| the documentation gate | `python3 scripts/docs-link-check.py --root . --strict` | **0 strict failures** |
| the Pages configuration | `bash scripts/validate-pages-config.sh` | **exit 0** — the configuration matches the deployment |
| the formal gate | `bash scripts/formal-gate.sh` | **15 specifications, 15 passed, 0 failed**, 18.8 s |
| the GPU benchmark | `bash scripts/gpu-benchmark.sh` | **exit 0**, 2 m 37 s — the CUDA provider is positively observed (`CUDAExecutionProvider ×1431`); see §10 |
| the native build | two attempts, measured | **both failed** — see §4 |
| the fresh-clone smoke | `bash scripts/fresh-clone-smoke.sh` | **PASS** — "the mind is awake and answering"; the five repro commands are printed by the script itself. This is the check behind the acceptance criterion "the contributor instructions work from a clean clone" |
| the CI | run `42206946030` at `f6e2be9f` | the five mandatory jobs **green**; the release path red for want of the owner's credentials |
| the CI (the consumer's own run) | run `42228476279` at `c86f6898` | **all five mandatory jobs green again** — Build & Test (which executed the consumer's 12 tests), the Web UI tests, the quality gates, the security scan and the container images. The only red job is "Docker Build & Push", which fails at its first step with "Username and password required" — the release path, and the external blocker of R-W48-04 |

**The negative controls**: the consumer's execution-time gate (red when
disabled), the Pages configuration validator (it rejects the withdrawn
positioning strings and a wrong url), and the formal gate's two surviving
mutants, which the gate logs rather than hides.

---

## 7. The capability registry

**One row added**, `W49-AUTONOMY-PROPOSALS` — the autonomy impulse path had no
row at all, neither the scheduler nor the consumer. The file goes from 35 rows
to **36**; 17 of them are `SERVED`. The row was written from the source and
the executed test evidence, not from the ADR's own claim, and it carries the
consumer's two honest limits (the store is in memory, so an open proposal does
not survive a restart; `ACCEPTED_FOR_REVIEW` means "recorded and waiting", not
"done").

---

## 8. What was NOT verified — the honest list

1. **The native build does not succeed.** The heap and the runner are
   measured; the produced binary's size is **NOT MEASURED**, and the capability
   stays BLOCKED.
2. **The public site was not rebuilt or republished.** The Jekyll build runs
   in CI on a pull request now, but the deployment path publishes from `main`,
   and the default branch is the owner's decision — so the live site still
   carries the old content until that branch is moved.
3. **The repository metadata was not changed.** The description and the topics
   are recommended in `W49-BASELINE.md` §7; applying them is the owner's.
4. **GitHub Discussions stays disabled**, and no private vulnerability-reporting
   channel was enabled. The security template now says plainly that no private
   channel exists and asks for one instead of pointing at a file that does not
   exist.
5. **The `CITATION.cff` was not added.** The repository has no formal release
   (`gh release list` is empty), so the file could only carry an invented
   author order, an invented DOI and an invented publisher. The pipeline
   document records that the citeable unit is, for now, **a versioned commit**.
6. **No publication was submitted** and no partner was sought — the directive
   does not require either.
7. **The full `matrix-core` suite was not run.** OD-041 authorised one run and
   it was used in W47; a second needs fresh owner approval. The wave's
   evidence is per-scope.
8. **The GPU figures moved between runs** — the fourth run gave 13.62× where
   the W48 runs gave 12.06× / 11.68× / 11.47×. The public documents now state
   all four, and the cause (the CPU median differs by 17 % between the runs) is
   in the reproducible-opportunities document.

---

## 9. The deliberate non-actions

| the item | why it was not done in W49 |
|---|---|
| the default branch was not moved | the directive forbids changing it without a separate decision. The 357-commit gap is recorded, and the Pages publication path stays on `main` |
| the repository's description and topics were not applied | the directive says **prepare** them; the settings are the owner's |
| GitHub Discussions was not enabled | there is no moderation or reporting arrangement to support it |
| no maintainer address, response time, moderation team or support service was invented | none exists; the Code of Conduct now names the only channel that does — the public issue tracker — and says that a private report is not currently provided |
| `CITATION.cff` was not added | there is no release to cite; the author order, the DOI and the publisher do not exist and must not be invented |
| the BouncyCastle dependency was not added | it would complete the native build, but it is a runtime dependency and the owner's call; the measurement OD-046 asked for is complete without it |
| the `docs-v2/story/` pages outside `overview.md` were not rewritten | they are the historical record of how the project was once described; the rewritten story links them as such and claims nothing from them |
| the historical archive was not touched | `docs-v2/_raw/`, `docs-v2/archive/` and `docs-v2/waves/` are history |

---

## 10. The GPU — the fourth run

| the quantity | the W48 run | the W49 run |
|---|---|---|
| the MATRIX forward call | 12.06× | **13.62×** |
| the raw `session.run` | 23.73× | 23.25× |
| the single ONNX point call | 0.24× | 0.25× |
| the BIR point call | 0.94× | 1.01× |
| the end-to-end path | 12.69× | 11.17× |

**The qualitative picture does not change**: the CUDA provider is a large win on
the transformer forward pass, **no** win at all on a single-point call (the GPU
is about four times slower there), and the historical 12.6× stays historical.
The ratio moves with the machine's state — the CPU median was 189.4 ms in the
W49 run against 161.0 ms in the W48 run.

**The GPU remains an optional accelerator (OD-047)** — the serving path does
not change, and the CPU fallback stays visible.

---

## 11. The changed files

The wave touches the public surfaces, the governance registers and the
autonomy consumer. The exact counts are in the commit that carries this
document; the groups are:

| the group | the paths |
|---|---|
| the implementation | `matrix-core/src/main/java/io/matrix/autonomy/**` (4 new), `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java`, `matrix-core/src/main/java/io/matrix/autonomy/{AutonomyEngine,SelfImprovingEngine}.java`, the gateway's four wiring sites, `matrix-brain-runtime/.../AutonomyLoop.java`, one new test class, one updated test class |
| the public surfaces | `README.md`, `README.ru.md`, `CONTRIBUTING.md`, `CONTRIBUTING`, `CODE_OF_CONDUCT`, `.github/ISSUE_TEMPLATE/**` (5), `.github/PULL_REQUEST_TEMPLATE.md`, `docs-v2/index.md`, `docs-v2/_config.yml`, 34 ecosystem/guide/story pages and their Russian equivalents |
| the research surfaces | `docs-v2/research/{PUBLICATION-PIPELINE,PUBLICATION-CANDIDATES,COLLABORATION-BRIEF,REPRODUCIBLE-OPPORTUNITIES}.md`, the dated note on `W31-ARXIV-PAPER-DRAFT.md` |
| the governance | `DECISION-REGISTER.md` (OD-048), `ROADMAP.md` (§4.8), `RISK-REGISTER.md`, `PROJECT-STATE.md`, `CAPABILITY-REGISTRY.yml`, `frozen-zone-baseline.txt` |
| the wave's own documents | `W49-BASELINE.md`, `W49-NATIVE-BUDGET.md`, `W49-PUBLIC-PRESENCE-AUDIT.md`, `W49-PUBLIC-README.md`, `W49-ONBOARDING.md`, `W49-STATUS.md`, `scripts/validate-pages-config.sh` |

**The frozen zone**: `.github/` grew from a recorded 1 056 to **1 619** diff
lines against the baseline ref, and the exception was raised to 1 619 **with
the reason recorded** in `docs-v2/quality/frozen-zone-baseline.txt` — the
owner's W49 addendum names the issue templates and requires the Pages
deployment path to be changed, and no workflow was touched beyond that.

---

## 12. The security and chain-isolation confirmation

| the property | the evidence |
|---|---|
| the gateway binds the loopback by default and refuses to start when exposed without a credential | `matrix-core/src/main/java/io/matrix/security/NetworkExposure.java`; the W48 clean-runner check "the security boundary (anonymous writes are refused)" is **green** |
| the consumer cannot reach Chain A | the ADR's S2, pinned by `theConsumerCannotReachChainA` — no Chain A import, no JDBC, no process, no socket, no file write |
| the consumer has no external effect | the ADR's S1 — the proposal store is its only collaborator that can write |
| the FROZEN gate is consulted twice | the ADR's S3, pinned by `theFrozenGateIsConsultedAtExecutionTime` and by its negative control |
| the two chains do not meet | unchanged — the consumer lives in Chain B's package and touches nothing in Chain A |
| the secret scan runs and finds nothing | run `42206946030`, the Security Scan job — the first time the scan ever examined this repository |

---

## 13. The Public Research Impact Check

The five questions the directive requires of every future wave:

1. **Did this wave produce a result worth explaining externally?** Yes — the
   consumer (a mechanism, not a claim) and the native-build budget (a
   measurement that replaces an out-of-memory error). Both are engineering
   results, and both are more useful as a report than as a headline.
2. **Is there evidence strong enough to publish it?** Two candidates are ready:
   **B** (evidence-preserving state and recovery — `PROCEED`) and **C**
   (workload-dependent CPU/CUDA acceleration — `PROCEED`). **A** (the
   architecture and research status) is `REVISE`: its architectural half is
   publishable today, its "research status" half is not.
3. **Did any public capability claim become stale?** Yes, and the wave fixed
   them: the site's configuration, the README's wave state, the capability
   counts, the GPU figures (a fourth run) and the autonomy layer's status.
4. **Is there an experiment that an external researcher could reproduce?** Yes
   — three are written out in
   `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md`, each one **executed**:
   the formal gate over 15 specifications (18.8 s), the GPU benchmark (2 m
   37 s), and the snapshot-compatibility tests (13 tests, 17 s).
5. **Is there a concrete opportunity for independent testing or collaboration?**
   Yes — six concrete requests in `docs-v2/research/COLLABORATION-BRIEF.md`,
   each labelled with the capability's real status.

---

## 14. The next wave's proposal

1. **The default branch and the site.** The gap is 357 commits and the live
   site is stale. The decision — move `main`, or publish the site from
   `develop` through a reviewed path — is the owner's.
2. **The release path.** The repository has no secrets; "Docker Build & Push"
   is red on every push. Configure them, or declare the job as one that runs
   only where they exist.
3. **The native build.** The budget is measured: give the workflow a runner
   with more than 16 GB and add the BouncyCastle module that PDFBox declares as
   optional. Then the 25 native-binary failures become either executed tests
   or an honest BLOCKED — not a third thing.
4. **The static-analysis policy.** The pinned engine cannot read Java 25
   bytecode; 4.10.5 can, and reports 1 414 findings, 981 of them
   `EI_EXPOSE_REP*`. The policy for those categories is the owner's decision.
5. **The owner questions the wave raised** — the author order and the DOI for
   a `CITATION.cff`, whether a formal release is wanted, and which of the two
   publication candidates goes first. They are in
   `docs-v2/governance/OPEN-QUESTIONS.md`.
6. **The autonomy consumer's durable store.** The proposal store is in memory;
   an open proposal does not survive a restart. That is a change with its own
   mandate, and it is the first thing the next wave would need for the consumer
   to be more than a record.

---

## 15. The links

- the baseline: `docs-v2/engineering/W49-BASELINE.md`
- the native budget: `docs-v2/engineering/W49-NATIVE-BUDGET.md`
- the public-presence audit: `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md`
- the README audit: `docs-v2/engineering/W49-PUBLIC-README.md`
- the onboarding audit: `docs-v2/engineering/W49-ONBOARDING.md`
- the publication pipeline: `docs-v2/research/PUBLICATION-PIPELINE.md`
- the collaboration brief: `docs-v2/research/COLLABORATION-BRIEF.md`
- the reproducible opportunities: `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md`
- the decision: `docs-v2/governance/DECISION-REGISTER.md` → OD-048
- the tasks: `docs-v2/governance/ROADMAP.md` §4.8
