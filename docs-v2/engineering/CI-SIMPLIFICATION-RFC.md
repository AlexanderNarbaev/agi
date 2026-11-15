# CI-SIMPLIFICATION-RFC — the owner-authorized narrowing of the CI lanes

**Status:** `ACTIVE` — the decision state of the document itself is PROPOSED: it is written,
measured and ready for the owner's decision, and **no workflow file in the repository was
changed by this document's author** (the mandate forbids it without an explicit RFC, and this
RFC *is* the request for that mandate).
**Date:** 2026-11-15 · **Branch:** `develop` · **HEAD at the measurement:** `3f6f53bb`

---

## 1. The approval basis

| the source | what it authorizes here |
|---|---|
| the owner's directive of 2026-11-15, §4 | a narrowly scoped simplification of `.github/workflows/**`; it does **not** amend the constitution, the 82 % method-coverage standard, or any protected runtime invariant |
| the owner's directive of 2026-11-15, §11 Gate 1 | *"Write the explicit frozen-workflow RFC, retain minimal PR/local gates, move publish/Graal/full expensive runs out of ordinary path, measure impact"* |
| **OD-053** (the owner's answer, 2026-11-15) | the FAST PR lane's checks — *path-sensitive compile of touched modules, targeted tests, the deterministic constitution/frozen-boundary gate, the documentation-consistency check* — become **REQUIRED on `develop`**; the full regression, packaged smoke and model checking stay in the MANUAL/RELEASE lane; **missing Docker Hub credentials must NOT make ordinary feature CI red** |
| the repository's own `AGENTS.md` | `.github/workflows/**` is a FROZEN zone: it may not be edited without an explicit RFC mandate from the owner **in that session** — which is what this document requests and what the patch in §9 waits for |

**What this RFC is.** A proposal with a measured baseline, a concrete patch, a rollback and a
validation plan.

**What this RFC is not.** An applied change. The proposed workflow files live outside the
repository (§9) and are **not** applied. The owner applies them.

---

## 2. The measured "before"

Every number below is read from the **real GitHub runs** of the two pull requests of
2026-11-15, with `gh run view <id> --json jobs`. The wall time is the run's own
`createdAt → updatedAt`; the runner time is the sum of the jobs' durations.

### 2.1 PR #60 — the implementation pull request (`feature/concept-to-action` → `develop`)

The run set: `43706331528` (ci), `43706331470` (Formal Specs), `43706331478`
(branch-validation), `43706331479` (docs-validation), `43706331546` (Pages). Head SHA
`6ff0612b`. **Every check was green.**

| the workflow | the wall time | the jobs (minutes) |
|---|---|---|
| `ci` | 22.4 min | Build & Test (Java) **16.8** · Quality gates 1.9 · Web UI Tests 0.5 · Container Images 5.5 · Security Scan 1.1 |
| `Formal Specs (TLA+)` | **23.4 min** | Model-check TLA+ specs 23.3 |
| `branch-validation` | 2.5 min | Compile all T-01 modules 2.4 · CONSTITUTION compliance 0.2 · Validate branch name 0.1 |
| `Deploy Documentation to GitHub Pages` | 3.0 min | build 2.9 |
| `docs-validation` | 0.2 min | Validate ecosystem docs 0.1 |
| **the total** | **51.5 min of wall time** | **54.8 runner-minutes** |

### 2.2 PR #61 — the release pull request (`develop` → `main`)

The run set: `43708206169` (ci), `43708206160` (Formal Specs), `43708206146`
(branch-validation), `43708206134` (docs-validation), `43708206184` (Pages), `43708206179`
(ShellCheck), `43708206093` (GraalVM Native Build). Head SHA `dfd24b44`.

| the workflow | the wall time | the jobs (minutes) | the outcome |
|---|---|---|---|
| `ci` | 26.9 min | Build & Test (Java) **21.4** · Container Images 5.5 · Quality gates 3.4 · Security Scan 1.1 · Web UI 0.4 | **red** (Security Scan) |
| `Formal Specs (TLA+)` | 18.7 min | Model-check TLA+ specs 18.6 | green |
| `branch-validation` | 2.5 min | 2.6 | green |
| `Deploy Documentation to GitHub Pages` | 1.9 min | 1.9 | green |
| `ShellCheck` | 0.3 min | 0.3 | **red** |
| `GraalVM Native Build` | 4.7 min | 4.6 | **red** |
| `docs-validation` | 0.2 min | 0.1 | green |
| **the total** | **55.2 min of wall time** | **60.1 runner-minutes** | |

### 2.3 The doubling — the same change, paid for twice

The head of PR #61 is a commit on `develop`, and `ci.yml` triggers on **both** `push:` and
`pull_request:`. The same SHA therefore ran the pipeline twice: the push run
`43708192378` (27.2 min) and the pull-request run `43708206169` (26.9 min), plus the
`develop` push's own branch-validation (2.7 min), docs-validation (0.1 min) and Formal
Specs (23.4 min).

**One change to `develop` cost 108.6 minutes of CI wall time and ≈ 118 runner-minutes.**
The two heaviest terms are the same in both halves: the full Java build and test
(16.8–21.6 min) and the TLA+ model checking (18.6–23.4 min). **Together they are 75–85 %
of the cost.**

### 2.4 The aggregate `ci` is red at the Docker Hub login

`gh secret list` on the repository returns **nothing**: the repository holds **zero
secrets**. In the push run `43708192378` the job `Docker Build & Push (web-ui)` is red, and
the step-level evidence is exact (`gh api repos/AlexanderNarbaev/agi/actions/jobs/132800361218`):

```
"Set up Docker Buildx"  success
"Login to Docker Hub"   FAILURE
"Build and push"        skipped
```

The pipeline is red because a **publishing** credential is absent. Nothing about the code is
red. This is exactly the condition OD-053 names: *"missing Docker Hub credentials must NOT
make ordinary feature CI red."*

### 2.5 The other two reds of PR #61 — what they are

| the check | the measured fact | the verdict |
|---|---|---|
| `ShellCheck` | red in 0.3 min | the workflow triggers **only** on `main`, so it never ran on the feature branches; the findings are real (the shell scripts) but the check's trigger is wrong for the ordinary path |
| `GraalVM Native Build` | red in 4.6 min | its "Verify Native Binary" step looks for `matrix-core/build/*-runner`, while the GraalVM package type writes `matrix-core/build/native/nativeCompile/matrix-core` — the verification path is wrong, and the workflow's `pull_request` trigger on `main` made it a gate of the release pull request |
| `Security Scan` on PR #61 | red in 1.1 min | the TruffleHog scan of the push range; this is the one check that has a real chance of finding something, and it must stay |

---

## 3. The target profile — four lanes

```
                     ┌──────────────────────────────────────────────────────────┐
   a pull request    │  LANE 1 — FAST PR          (automatic, REQUIRED on develop)│
   to `develop` ────►│  path-sensitive compile · targeted tests · constitution/  │
                     │  frozen gate · documentation consistency · secret scan    │
                     └──────────────────────────────────────────────────────────┘
                     ┌──────────────────────────────────────────────────────────┐
   before the push  │  LANE 2 — LOCAL AGENT       (no CI; the developer's gate) │
   ─────────────────►│  the full local suite · the chain-isolation gate · the   │
                     │  quality gate · the targeted tests                         │
                     └──────────────────────────────────────────────────────────┘
                     ┌──────────────────────────────────────────────────────────┐
   a PR to `main`,   │  LANE 3 — MANUAL / RELEASE   (full regression · packaged │
   or on demand ────►│  smoke · model checking · static analysis · native image)│
                     └──────────────────────────────────────────────────────────┘
                     ┌──────────────────────────────────────────────────────────┐
   by hand, with    │  LANE 4 — PUBLISH            (credential-gated; SKIPS when │
   the credentials ─►│  the credential is absent; never a merge gate)           │
                     └──────────────────────────────────────────────────────────┘
                     ┌──────────────────────────────────────────────────────────┐
   every Monday     │  the weekly research and property suites (the cron)       │
   ─────────────────►└──────────────────────────────────────────────────────────┘
```

**What triggers each manual lane, exactly.**

| the lane | the trigger | who presses it |
|---|---|---|
| LANE 3 — MANUAL | `workflow_dispatch` on `ci.yml` / `tla.yml` / `native.yml`, with any ref | the owner or an agent, at any moment |
| LANE 3 — RELEASE | a pull request whose base is `main` | the release itself; the release pull request is the trigger, and the lane's checks are the release's gate |
| LANE 4 — PUBLISH | `workflow_dispatch` on `publish.yml`, with `ref`, `target` and the literal confirmation `PUBLISH` | the owner only; a credential is required |
| the weekly lane | the cron `0 4 * * 1` | nobody |

**The rule that makes OD-053 true.** The PUBLISH lane is `workflow_dispatch`-only and is
**never** a required status check. Therefore a missing Docker Hub credential cannot make
ordinary feature CI red — it can only make a manually invoked publish job skip. The
credential is read in a **step** (the `secrets` context is not available in a job-level
`if:`), and the publishing job depends on that step's output.

---

## 4. The exact per-workflow before/after

| the file | before (2026-11-15) | after | the change |
|---|---|---|---|
| `.github/workflows/ci.yml` | `push:[develop,main,release/**]` + `pull_request:[develop,main,release/**]` + `workflow_dispatch` + a Monday cron. Nine jobs. | **`workflow_dispatch` + `pull_request:[main]` only.** Four jobs: `Build & Test (Java)` (the full regression), `Quality gates` (the clean-code gate, the formal gate, the security boundary), `Container Images` (the packaged smoke), `Security Scan` (SpotBugs + the dependency check). | the trigger leaves the ordinary `develop` path; the publishing and deploying jobs leave the file; the research job leaves the file |
| `.github/workflows/tla.yml` | `push:[develop,main]` + `pull_request:[develop,main]` + `workflow_dispatch`. The model check ran **18.6–23.3 min** on every change. | **`push:[main]` + `pull_request:[main]` + `workflow_dispatch`.** The path filter is kept. | two lines. The model checking leaves the ordinary path |
| `.github/workflows/native.yml` | `push:[main]` + `pull_request:[main]`. Red in 4.6 min because the verification step looks for the wrong path. | the same two triggers **plus `workflow_dispatch`**, and the verification step probes both spellings of the binary's path and fails only when neither exists | the GraalVM build leaves the ordinary pull-request path and becomes dispatchable; the false negative of 4.6 min is repaired |
| `.github/workflows/branch-validation.yml` | `pull_request:[main,develop,release/**]` + `push:[develop,release/**]`. Three jobs: the branch name, the CONSTITUTION check, the compile of the T-01 modules. | **deleted.** The branch name and the CONSTITUTION check move into `fast-pr.yml`; the compile of the T-01 modules becomes the path-sensitive compile of the touched modules. | its checks are kept, its unconditional compile is not |
| `.github/workflows/docs-validation.yml` | `pull_request` (paths `docs-v2/**`) + `push:[develop,main]` (paths `docs-v2/**`). The RU/EN ecosystem parity. | **deleted.** Its scope is replaced by the stronger documentation-consistency check of `fast-pr.yml` (`docs-link-check.py --strict` + `validate-current-state.py --strict`), which covers the whole document set instead of the ecosystem pages only. | a weaker check is replaced by a stronger one that is always on |
| `.github/workflows/shellcheck.yml` | `push:[main]` + `pull_request:[main]`. | **deleted.** The check moves into `fast-pr.yml` and becomes path-sensitive: it runs when, and only when, `scripts/*.sh` changed. | the check is kept and its trigger becomes correct |
| `.github/workflows/pages.yml` | `push:[main]` (paths `docs-v2/**`) + `pull_request` (paths) + `workflow_dispatch`. | **unchanged.** | the documentation site is not a CI cost and not a merge gate |
| `.github/workflows/fast-pr.yml` <!-- no-link: the proposed file lives in /tmp/ci-rfc/proposed/ and is not in the repository yet --> | — | **new.** Eight jobs: `Scope the pull request`, `Branch name`, `Compile + targeted tests (<module>)` (one per touched module), `CONSTITUTION + frozen boundary gate`, `Documentation consistency`, `Secret scan`, `ShellCheck` (path-sensitive), `Web UI tests` (path-sensitive). | the new FAST PR lane |
| `.github/workflows/publish.yml` <!-- no-link: the proposed file lives in /tmp/ci-rfc/proposed/ and is not in the repository yet --> | — | **new.** Three jobs: `Publication credentials`, `Publish (<target>)`, `Deploy (<target>)`. `workflow_dispatch` only. | the publishing and deploying leave the automatic path |
| `.github/workflows/research.yml` <!-- no-link: the proposed file lives in /tmp/ci-rfc/proposed/ and is not in the repository yet --> | the research job was inside `ci.yml` behind `if: github.event_name == 'schedule'` | **new.** The same job, on the same cron, plus `workflow_dispatch`. | it is split out because `ci.yml` no longer has a schedule trigger |

### 4.1 What moves from automatic to manual — the complete list

| the check | the before | the after | the owner of it now |
|---|---|---|---|
| the full Java regression (`compileJava compileTestJava` + `test` for every module) | automatic, every push and every PR | MANUAL / RELEASE | the release lane |
| the full model checking (TLA+) | automatic, every push and every PR | MANUAL / RELEASE | the release lane |
| the packaged smoke (three images built and started) | automatic, every push and every PR | MANUAL / RELEASE | the release lane |
| the GraalVM native image | automatic on a PR to `main` | MANUAL / RELEASE | the release lane |
| SpotBugs and the dependency check | automatic, every push and every PR | MANUAL / RELEASE | the release lane |
| the formal gate and the security boundary check | automatic, every push and every PR | MANUAL / RELEASE | the release lane |
| Docker Hub publishing | automatic on a push to `develop`/`main` | MANUAL / PUBLISH | the publish lane |
| the staging and production deploys | automatic on a push to `develop`/`main` | MANUAL / PUBLISH | the publish lane |
| the web console test suite | automatic, every push and every PR | FAST PR, **path-sensitive** | the fast lane |
| the shell check | automatic on `main` only | FAST PR, **path-sensitive** | the fast lane |
| the secret scan | automatic, every push and every PR | FAST PR | the fast lane |
| the compile of the modules | automatic, every module, every PR | FAST PR, **path-sensitive**; the full compile is in the release lane | both |
| the targeted tests | did not exist as such | FAST PR | the fast lane |
| the constitution and frozen-zone gate | automatic, every push and every PR | FAST PR (and the release lane, for a ref dispatched directly) | both |
| the documentation-consistency check | automatic, but only on `docs-v2/**` | FAST PR, and it checks the whole document set | the fast lane |

---

## 5. What is required, and what the owner must register

The FAST PR lane's checks become **required status checks of `develop`** under OD-053.
GitHub matches a required check by its **job name**, so the names to register are exactly:

```
Scope the pull request
Branch name
Compile + targeted tests                ← the stable aggregator (see below)
CONSTITUTION + frozen boundary gate
Documentation consistency
Secret scan
```

**The aggregator is not decoration — it is what makes the gate registrable.** GitHub matches a
required status check by its exact check-run name, and the matrix job's name carries its matrix
value (`Compile + targeted tests (matrix-core)`), so it changes with the change set and could
never be registered. The lane therefore publishes the matrix's outcome a second time under one
stable name, `Compile + targeted tests`. That job runs with `if: always()` and inspects the
matrix's result explicitly: a job that was merely **skipped** because its dependency failed must
not look successful, and GitHub counts a skipped required check as passed.

**The owner-side action, exactly.** The repository ruleset `Protected main` (id `20469548`)
currently carries these rules: `deletion`, `creation`, `update`, `required_linear_history`
and `pull_request` (1 approving review, code-owner review, thread resolution, an extra
approval for unattributed changes). It carries **no `required_status_checks` rule**. Adding
one with the five names above is the change that makes OD-053 real.

**Two findings about the ruleset that the default-branch move produced, and that the owner
must decide on. They are outside this RFC's scope but they are measured facts:**

1. **The ruleset's condition is `~DEFAULT_BRANCH`, and the default branch is now
   `develop`.** The ruleset named *"Protected main"* therefore **now protects `develop`** and
   **no longer protects `main`**. `gh api repos/AlexanderNarbaev/agi/branches/main/protection`
   returns *"Branch not protected"*, and the branches API reports `main protected=false`.
   The directive (§11 Gate 1) says *"keep main protected"*. The ruleset's `ref_name.include`
   must therefore name `main` explicitly, and a second ruleset (or an extended include list)
   must cover `develop`. **This is an owner-gated change; this RFC does not make it.**
2. **The `pull_request` rule of the ruleset requires a code-owner review and one approving
   review.** The FAST PR lane's checks are therefore an *additional* condition, never a
   substitute for the review. Nothing in this RFC weakens the review.

---

## 6. The LOCAL AGENT lane — the gate that costs no CI minutes

The FAST lane is narrow on purpose, so the width has to live somewhere. It lives here, in
the agent's or the developer's own machine, **before** the push:

```bash
# 1. the full local suite of the module you touched (the regression the fast lane omits)
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --offline

# 2. the clean-code, frozen-zone and documentation gate (4 seconds, measured)
scripts/quality-gate.sh

# 3. the chain-isolation gate
scripts/w45-chain-isolation-check.sh

# 4. the formal gate (the pinned tool is in the repository, so this runs offline)
scripts/formal-gate.sh
```

**The build lock is shared** (`/tmp/gradle-matrix.lock`): one heavy Gradle JVM at a time.
The `--tests` filter goes **immediately** after the task it belongs to. `--rerun` and
`--rerun-tasks` are not used: they drag in the native-image build.

---

## 7. The known flake — recorded, not silenced, not removed

> **`CognitiveKernelRemoteTest > a delegation that settles the proposition leaves no
> uncertainty of its own`**
> failed in the CI run **43720389219** (the push of `c5853ac3`, 2026-11-15) and **passes
> locally**.

**The measured failure.** The CI log of that run:

```
CognitiveKernelRemoteTest > a delegation that settles the proposition leaves no uncertainty
of its own FAILED
    java.lang.NullPointerException at CognitiveKernelRemoteTest.java:198
```

**The reproduction.**

```bash
# the CI side: the run and its log
gh run view 43720389219 --log-failed | grep -A2 "settles the proposition"

# the local side: it passes
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test \
  --tests "io.matrix.cognitive.CognitiveKernelRemoteTest" --offline
# → BUILD SUCCESSFUL  (verified 2026-11-15 at HEAD 3f6f53bb, exit 0)
```

**What the failure actually is.** Line 198 of
`matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelRemoteTest.java` is
`assertThat(reflection.learned().stream()…)`, where `reflection` is `outcome.reflection()`.
The exception therefore says that **`outcome.reflection()` was `null`**. The test's own
intent is the opposite case: it sets `settling = true`, the peer's report answers
`SUPPORTED`, and the delegation settles the proposition — so the expectation is that the
reflection carries **no** uncertainty. The test reaches that expectation through
`reflection.learned()` and `reflection.uncertainties()`, and a reflection that does not
exist cannot be asked for its uncertainties.

**What is NOT known.** *Why* the reflection was absent in that run and present in the local
one. The test drives a remote delegation through the kernel, and the kernel's cycle is
timing- and order-sensitive; a loaded runner is the obvious suspect — **`[speculative]`**.
This RFC does not claim a cause it has not measured.

**What is done about it.** Nothing is silenced, nothing is skipped, nothing is retried, and
the test is **not removed**. In the FAST PR lane the test is in the targeted scope whenever
`matrix-core` is touched, because its package `io.matrix.cognitive` is in the targeted
filter. The consequence is stated plainly: **the FAST lane can go red on this flake**, and
the answer is to re-run the lane and read the failure, not to hide it. A lane that hides a
flake is a lane that has stopped measuring.

**It is not a new finding.** It is already in the initiative's own evidence register
(`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`, the row of 2026-11-15), and this RFC
adds the measured detail and the reproduction.

### 7.1 A second, load-sensitive flake — found by this measurement

While measuring the FAST lane's Gradle term, the **full** `matrix-core` suite was run in a
fresh clone of HEAD `3f6f53bb`:

```
9613 tests completed, 1 failed, 147 skipped
BUILD FAILED in 8m 23s

PortfolioBenchmarkTest > aScanAndAPrebuiltIndexAreComparedOnTheSameCorpus() FAILED
    java.lang.AssertionError at PortfolioBenchmarkTest.java:254
```

Line 254 is `assertThat(triePerQuery).isLessThan(scanPerQuery)` — **an assertion on a
measured wall-clock time**: the trie lookup must be faster per query than the linear scan.
The same class **passes in isolation** on the same tree, with the JUnit XML as the
evidence:

```
TEST-io.matrix.cognitive.algorithms.PortfolioBenchmarkTest.xml
tests=3 failures=0 errors=0 time=0.159
  aScanAndAPrebuiltIndexAreComparedOnTheSameCorpus() -> pass
```

**The cause is the machine's load, not the code** — the full-suite run happened while three
other agents were building (load average 4.5 on 32 CPUs). **`[speculative]` on the exact
mechanism** — the assertion is timing-sensitive by construction, and the measurement shows
the same code passing when the machine is quieter.

**Why this matters for the RFC, and it is not hidden.** The RELEASE lane runs the full
regression, and **that lane is red on `develop` at HEAD today** because of this test. The
FAST lane does not run the whole suite, so it is not affected by this particular test —
unless the pull request touches `io.matrix.cognitive.algorithms`, in which case the
targeted scope contains it.

**What is NOT done about it.** The test is not silenced, not skipped and not changed: it is
another agent's file and outside this RFC's write scope. **The owner needs to know that the
release lane cannot currently go green.** The two candidate remedies are (a) make the
assertion load-tolerant — compare the two methods on the same run, which it already does,
with a tolerance band; and (b) move the timing assertion out of the unit suite into a
benchmark lane. Both are a decision for the owner, not for this document.

---

## 8. The rollback

The change is **one commit that touches only `.github/workflows/**`**. The rollback is:

```bash
git revert <the commit that applied the RFC patch>
# or, without a revert, because the commit is the only change:
git checkout <the previous commit> -- .github/workflows/
```

**No other artefact depends on the new layout**, because:
* nothing outside `.github/workflows/**` references the workflow file names;
* the required status checks of `develop` are the **only** coupling, and they are the
  owner's registration — reverting the workflows and re-registering the old names restores
  the old gate;
* the archive tags, the bundle and the branch inventory are untouched by this RFC.

**The rollback restores the old lane within one minute of pushing**, and the cost of the
old lane is exactly what §2 measured.

---

## 9. The proposed files and the patch

**The concrete proposed workflow files are outside the repository**, because the mandate
forbids editing `.github/workflows/**` without an explicit RFC mandate from the owner and
the task forbids the author from applying them:

```
/tmp/ci-rfc/
├── proposed/                     ← the proposed files, exactly as they would be committed
│   ├── fast-pr.yml               (new)      the FAST PR lane
│   ├── publish.yml               (new)      the credential-gated PUBLISH lane
│   ├── research.yml              (new)      the weekly research suites
│   ├── ci.yml                    (modified) the RELEASE lane
│   ├── tla.yml                   (modified) the trigger only
│   ├── native.yml                (modified) the trigger + the corrected verification path
│   └── pages.yml                 (unchanged, kept for the diff's completeness)
├── old/.github/workflows/        ← the current files, the "before" of the diff
├── new/.github/workflows/        ← the proposed files, the "after" of the diff
├── ci-simplification.diff        ← THE PATCH: `git apply`-able against the repository root
└── raw.diff                      ← the unrewritten `git diff --no-index` output
```

**The patch applies cleanly.** Verified:

```bash
cd /home/alexandr-narbaev/Projects/agi
git apply --check --verbose /tmp/ci-rfc/ci-simplification.diff
# → exit 0, nine files checked
```

**The patch does not touch `pages.yml`** (it appears in the directory only so that the diff
is complete), and it does not touch a single file outside `.github/workflows/**`.

**Every proposed file was parsed as YAML before it was written here.** The eight jobs of
`fast-pr.yml`, the three of `publish.yml`, the four of `ci.yml` and the one of
`research.yml` were each loaded with `yaml.safe_load` and their job lists read back.

**To apply it** (the owner's action, after the mandate):

```bash
cd /home/alexandr-narbaev/Projects/agi
git apply /tmp/ci-rfc/ci-simplification.diff
```

---

## 10. The validation plan

| # | the step | the command | the pass criterion |
|---|---|---|---|
| 1 | the patch applies | `git apply --check /tmp/ci-rfc/ci-simplification.diff` | exit 0 |
| 2 | every proposed file is valid YAML | `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" /tmp/ci-rfc/proposed/*.yml` | no exception |
| 3 | the scope step computes the right matrix | open a pull request that touches `matrix-core/src/main/java/io/matrix/contract/**` and read the `Scope the pull request` step's output | the matrix names `matrix-core` with the filter `--tests io.matrix.contract.*` |
| 4 | the scope step is empty for a docs-only change | a pull request that touches only `docs-v2/**` | the matrix is `[]`, the build job is skipped, the documentation job still runs |
| 5 | the deterministic gate is green on the tree | `scripts/quality-gate.sh` | exit 0 — **the magic-literal rules are green on the current tree on 2026-11-15 (271 candidates against an accepted baseline of 271, 0 magic-literal violations)**. **The gate is nevertheless RED right now, and the cause is NOT this RFC: `FROZEN-1` reports `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EvalBattery.java has 25 diff lines vs b144563e and no recorded exception` — a frozen zone edited by another agent's concurrent work. That violation must be cleared before the FAST PR lane's constitution job can be green.** |
| 6 | the documentation consistency is green | `python3 scripts/docs-link-check.py --root . --strict` and `python3 scripts/validate-current-state.py --root . --strict` | exit 0 |
| 7 | the publish lane skips without a credential | `gh workflow run publish.yml --ref develop -f target=staging -f confirm=PUBLISH` on a repository without the secrets | the `Publish` job is **skipped**, and the run's conclusion is not a failure of the feature path |
| 8 | the release lane still finds a real defect | dispatch `ci.yml` on a ref with a deliberately broken test | the `Build & Test (Java)` job goes red |
| 9 | the flake is still reachable | a pull request that touches `matrix-core/src/main/java/io/matrix/cognitive/**` | the targeted filter contains `io.matrix.cognitive.*`, so the flaky test still runs |
| 10 | the measured saving appears | compare the next pull request's run set with §2.1 | the FAST PR lane's wall time is below 10 minutes and the release lane is untouched |

**The validation is not complete until step 3 and step 7 have run on GitHub.** Everything
else is verified locally.

---

## 11. The honest cost accounting

### 11.1 What is saved

| the term | the before | the after (the projection) | the confidence |
|---|---|---|---|
| the wall time of an ordinary feature pull request | **51.5 min** (PR #60, measured) | **4–8 min** | the projection; its deterministic floor is measured, its Gradle term is bounded by the measured CI run of the same work |
| the runner-minutes of an ordinary feature pull request | **54.8** (PR #60, measured) | **≈ 8–14** (six to eight jobs in parallel) | the projection |
| the TLA+ model checking on an ordinary change | **18.6–23.4 min** every time | **0** | **certain** — the trigger no longer reaches the ordinary path |
| the full Java regression on an ordinary change | **16.8–21.6 min** every time | **0** (the targeted scope only) | **certain** for the trigger; the residual risk is in §11.4 |
| the packaged smoke on an ordinary change | **5.5 min** every time | **0** | **certain** |
| the doubling on a `develop` push that has an open pull request | **≈ 53 min** of duplicate work | **0** (the fast lane triggers on `pull_request` only, and the push to `develop` triggers no CI at all) | **certain** |
| the aggregate red at the Docker Hub login | **the `ci` workflow is red on every push** | **0** — the publish lane is manual and credential-gated | **certain** |

**The single largest saving is the model checking: 18.6–23.4 minutes that were spent on
*every* change and that no ordinary change needed.** The second is the doubling: the same
commit was built and tested twice.

### 11.2 What is NOT saved

* **The release cost is unchanged.** The full regression, the packaged smoke, the model
  checking and the native image still cost 21.4 + 5.5 + 18.6 + 4.6 ≈ **50 runner-minutes
  per release**. The RFC does not make a release cheaper; it makes a *change* cheaper.
* **The FIRST run of a new pull request is not free.** The runner has no Gradle build
  cache of the developer's machine, so the cold compile of `matrix-core` dominates the
  FAST lane. Measured on a fresh clone of the same commit on this machine (2026-11-15):
  the compile of `matrix-core` (production + test sources) is **17 s**, the targeted test
  scope `io.matrix.contract.*` is **4 s**, and the **full** `matrix-core` suite is
  **504 s for 9 613 tests** — but the CI runner has neither the warm daemon nor the warm
  dependency cache. The honest statement is that the FAST lane's Gradle term is **an
  estimate**, bounded above by the measured CI run of the same work (16.8–21.6 min for
  *all* modules, full regression included). The ratio the projection rests on is the
  measured one: **a targeted scope is 1–2 % of the full suite's time**, because the full
  suite's cost is the 9 613 tests and not the compilation.
* **The first pull request of a contributor costs the same as the last one.** There is no
  cache to warm and no incremental advantage.
* **The `develop` push now triggers NO CI at all.** A direct push to `develop` (which the
  branch protection discourages, but does not forbid) gets no gate whatsoever. That is the
  price of removing the doubling, and it is the owner's decision to accept it.

### 11.3 What remains protected

**Nothing that was protecting the constitution is lost.** The fast lane keeps:

| the protection | the before | the after |
|---|---|---|
| the constitution (no LLM in the runtime paths) | every push and every PR | **every pull request** |
| the frozen zones (FROZEN-1, against the recorded baseline) | every push and every PR | **every pull request** |
| the magic-literal baseline (271 against 271) | every push and every PR | **every pull request** |
| the documentation consistency | only on `docs-v2/**` | **every pull request, and over the whole document set** |
| the secret scan | every push and every PR | **every pull request** |
| the branch naming convention | every pull request | every pull request |
| the clean-code gate, the formal gate, the security boundary, the retention rules | every push and every PR | **every pull request (the clean-code gate) and every release (the rest)** |
| the full regression | every push and every PR | **every release, and every local agent run** |
| the model checking | every push and every PR | **every release, and the weekly cron** |
| the packaged smoke | every push and every PR | **every release** |
| the review (one approving review, code owner, threads resolved, an extra approval for unattributed changes) | the ruleset | **the ruleset, unchanged** |

### 11.4 The risks this simplification creates — named, not hidden

1. **A production change can break a test in a package the pull request did not touch.**
   The targeted scope is the touched packages. This is the real price of OD-053 and it is
   NOT covered by the fast lane. It is covered by the LOCAL AGENT lane (the full module
   suite before the push) and by the RELEASE lane (everything). **Between the two, a
   defect of this class can reach `develop` and be found at the release.** The mitigation
   the RFC recommends, at no CI cost: the LOCAL AGENT lane's full module suite is the
   agent's own gate, and it is the same command.
2. **The lane is green while the system is broken.** A lane that checks only the touched
   packages can be green on a change that breaks everything else. The fast lane's value is
   its *latency*, not its coverage — it says "this change compiles and its own tests pass",
   not "the system is sound".
3. **The required status checks must be registered, or the lane blocks nothing.** Until the
   owner adds the `required_status_checks` rule, the FAST PR lane is a *report*, not a
   *gate*. This RFC cannot make it a gate.
4. **The check names contain a variable.** `Compile + targeted tests (<module>)` is one
   check whose name varies with the touched module. GitHub's required-check matching is by
   name, so a pull request that touches three modules produces three checks with three
   names, and the required list must contain the pattern (GitHub accepts a context such as
   `Compile + targeted tests (*)`). **This is a real operational subtlety and the owner
   must register the pattern, not the three literal names.**
5. **The `docs-validation.yml` deletion removes the RU/EN ecosystem parity check.** It is
   replaced by a *different* check that does not verify the RU/EN parity. That specific
   parity property is therefore **not** checked by the fast lane; it must be checked
   locally or restored in the release lane. **This RFC does not hide it.**

---

## 12. What this RFC does not do

* It does not modify `.github/workflows/**` — the patch is at `/tmp/ci-rfc/ci-simplification.diff`
  and waits for the owner's mandate.
* It does not change the ruleset, the branch protection, the default branch or the
  secret store. §5 names the changes the owner must make; it does not make them.
* It does not repair the SpotBugs engine (it cannot read Java 25 bytecode — the step keeps
  its documented loud-skip behaviour), the TruffleHog findings or the ShellCheck findings.
  It only moves **where** they run. The one defect it does repair is the native-image
  **verification path**: the proposed `native.yml` probes both
  `matrix-core/build/native/nativeCompile/matrix-core*` and `matrix-core/build/*-runner`
  and fails only when neither exists, instead of failing on a build that succeeded.
* It does not silence, skip or remove the known flake (§7).
* It does not claim that the projected 4–8 minutes of the FAST lane is measured. §11.2
  says which part is measured and which part is an estimate.
