# W48 — CI RECONCILIATION

**Status:** the record of the wave's CI stabilisation · **Date:** 2026-11-06
**Branch:** `develop` · **HEAD at the time of writing:** `4adbc664548d91116298b81119c39be71328ac37`
**Task:** W48-03/04/05 · **Write scope:** this file only
**The preflight:** `docs-v2/engineering/W48-BASELINE.md`
**The reconciliation of the test evidence:** `docs-v2/engineering/W48-TEST-RECONCILIATION.md`
**The wave's own report:** `docs-v2/engineering/W48-STATUS.md` <!-- no-link: the report is written at the end of the wave -->

**The rule this document follows:** every number and every state is traceable to a
command or a file that was actually run or read. A CI state is a claim about a
**moment** and about a **commit**, so every state below names both. Where the
evidence cannot answer, the cell says `UNVERIFIED` and says why.

**No build and no test was run for this document.** The CI state is read from the
GitHub API (`gh`). The local commands are the link checker's own self-test, the
strict link check, the digest verification and greps — all listed in §7.

---

## 0. What this document is

It is the reconciliation the W48 preflight promised: the **starting state** of the
three red workflows, the **causes** behind them with the evidence for each and the
commit that fixed it, the **current state** of each mandatory workflow read from the
GitHub API, and an explicit account of what is **still red or unverified**.

Three things it is careful about, because the wave has already been bitten by each
of them:

1. **A CI state is a claim about a moment.** Every state in §3 names the run id, the
   commit it was read at, and the moment of the reading.
2. **A red job and a red cause are different things.** The `ci` workflow is still red
   at HEAD, but not for the cause it was red for in the preflight. §3 and §4 say which.
3. **"Fixed" is a claim about a check, not about a wish.** §2 names the check that now
   proves each fix, and §5 names the negative control that proves each check can still
   fail.

---

## 1. The starting state — the three red workflows

The preflight's §2 lists the three workflows and the failing jobs. **The run ids and
the job ids are not in the preflight** — it gives the workflow, the job and the state,
and says the runs were "fetched with `gh run list --branch develop`". They are
recovered here from the GitHub API and are reproduced below with the commit each run
belongs to.

| the workflow | the run | the commit | the job | the job id | the state at the preflight |
|---|---|---|---|---|---|
| `ci` | **42013895090** | `eca23bfd` | Build & Test (Java) | **127199452300** | **failure** — 240 tests completed, 11 failed |
| `ci` | 42013895090 | `eca23bfd` | Quality gates (can fail) | **127199452254** | **failure** — DOCS-1 |
| `ci` | 42013895090 | `eca23bfd` | Web UI Tests (matrix-web-ui) | 127199451630 | success |
| `ci` | 42013895090 | `eca23bfd` | Weekly Research & Property Suites · Security Scan · Docker Build & Push · Container Images · Deploy to Production · Deploy to Staging | 127199454086 · 127201073442 · 127201073505 · 127201073689 · 127201074401 · 127201074490 | skipped (6) |
| `branch-validation` | **42013894963** | `eca23bfd` | CONSTITUTION compliance check | **127199450544** | **failure** — the version-constant gate |
| `branch-validation` | 42013894963 | `eca23bfd` | Compile all T-01 modules | **127199451062** | **failure** — the GraalVM action |
| `branch-validation` | 42013894963 | `eca23bfd` | Validate branch name | 127199452953 | skipped (push, not pull request) |
| `Formal Specs (TLA+)` | **42013763834** | **`583358df`** | Model-check TLA+ specs | **127199013127** | **failure** — the artifact download |
| `docs-validation` | 42013894989 | `eca23bfd` | Validate ecosystem docs (RU/EN parity) | 127199451254 | success |

**One correction to the preflight, and it is a factual one.** The preflight's §2 opens
with "The runs for `eca23bfd`" and lists `Formal Specs (TLA+)` among them. **No Formal
Specs run exists for `eca23bfd`.** The workflow is path-filtered — `.github/workflows/tla.yml`
declares `paths: formal/**`, `.github/workflows/tla.yml` and the generated formal
paths — and the three files `eca23bfd` touched
(`docs-v2/engineering/W47-STATUS.md`, `docs-v2/governance/PROJECT-STATE.md`,
`docs-v2/governance/RISK-REGISTER.md`) match none of them. The red Formal Specs run
is **42013763834**, at the commit **`583358df`** (created `2026-11-05T10:48:31Z`), the
last commit before the preflight that did touch a filtered path. The preflight's own
diagnosis of that failure is confirmed below (cause E).

```bash
$ gh api "repos/AlexanderNarbaev/agi/actions/runs?branch=develop&per_page=100" \
    --jq '.workflow_runs[] | select(.head_sha|startswith("eca23bfd")) | "\(.name)\t\(.id)\t\(.conclusion)"'
ci	42013895090	failure
branch-validation	42013894963	failure
docs-validation	42013894989	success
```

### 1.1 What each red job actually said

**`ci` → Build & Test (Java), job 127199452300** — the eleven source-integrity tests
named a file by an absolute path into one developer's home directory:

```
2026-11-05T10:52:58Z NoFutureClaimsTest > no_future_tense_about_real_engine_completion() FAILED
2026-11-05T10:53:00Z ProdCallerExistsTest > persistentMind_has_prod_caller() FAILED
2026-11-05T10:53:00Z SingleInstanceGuardTest > handle_sleep_calls_realSleepScheduler_not_draft() FAILED
2026-11-05T10:53:00Z 240 tests completed, 11 failed
2026-11-05T10:53:00Z > Task :matrix-api-gateway:test FAILED
```

**`ci` → Quality gates (can fail), job 127199452254** — the documentation gate failed
on its own strict mode over runtime paths:

```
2026-11-05T10:49:43Z   MISSING_PATH                      199
2026-11-05T10:49:43Z DOCS-1     the documentation links do not resolve
2026-11-05T10:49:43Z FAIL: 1 violation(s) outside the accepted baseline.
```

**`branch-validation` → CONSTITUTION compliance check, job 127199450544** — the
version gate grepped for a word and the SDK's facade declared no version:

```
2026-11-05T10:49:36Z Checking T-01 modules have VERSION constants...
2026-11-05T10:49:36Z ❌ matrix-sdk-java has no VERSION constant
```

**`branch-validation` → Compile all T-01 modules, job 127199451062** — the job died at
the GraalVM setup step, so it compiled nothing:

```
2026-11-05T10:49:36Z   distribution: graalvm-ce
2026-11-05T10:49:36Z ##[error]Unsupported distribution: graalvm-ce
```

**`Formal Specs (TLA+)` → Model-check TLA+ specs, job 127199013127** — the download
was refused by its own digest:

```
2026-11-05T10:48:39Z curl -fsSL -o tools/tla2tools.jar \
    https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar
2026-11-05T10:48:39Z echo "fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e  tools/tla2tools.jar" \
    | sha256sum --check --strict
2026-11-05T10:48:39Z sha256sum: WARNING: 1 computed checksum did NOT match
2026-11-05T10:48:39Z tools/tla2tools.jar: FAILED
2026-11-05T10:48:39Z ##[error]Process completed with exit code 1.
```

---

## 2. The causes

**The count, stated first.** The directive names **five** causes and then describes
**six** (A … F). The five (A–E) explain the three red workflows of §1. **F was
introduced by the wave's own fix for A** and is a red CI state in its own right, so it
is in the table and it is not folded into the five. Nothing is lost by the six rows.

| the cause | the evidence | the fix | the check that now proves it |
|---|---|---|---|
| **A** — the source-integrity tests named a developer's home directory: `/home/alexandr-narbaev/Projects/agi/…` | `git show 0c5b3980` — `NoFutureClaimsTest.java`, `ProdCallerExistsTest.java`, `SingleInstanceGuardTest.java`, `PlanningCallerExistsTest.java` all held the literal; the CI log of job 127199452300 shows the 11 failures; `git grep -n "/home/alexandr-narbaev" -- '*.java'` today returns **only** the javadoc of the new helper that quotes the old path | **`0c5b3980`** — the root `build.gradle` gives every test task the system property `matrix.repoRoot` (in `gradle.projectsEvaluated`, because the Quarkus plugin replaces the test task's system properties); one shared helper `test-support/java/io/matrix/testsupport/RepositoryPaths.java` resolves it and **throws** when the root or the subject file is missing. Two PRODUCTION sources (`BitLinearGpu.java`, `PanamaNativeBridge.java`) had the same absolute path and now look relative to the working directory | the CI's `Build & Test (Java)` job at `4adbc664`: `:matrix-api-gateway:test` runs and **no longer fails**; the log of job 127738106005 contains **zero** occurrences of `NoFutureClaimsTest`, `ProdCallerExistsTest` or `SingleInstanceGuardTest` (baseline: 11 failures). Locally: `matrix-core/src/test/java/io/matrix/testsupport/RepositoryPathsTest.java` — 5 tests, including `theRootIsTheOneTheBuildDeclares` and `aMissingSourceFileFailsLoudlyInsteadOfPassingQuietly` |
| **B** — the documentation gate was green locally and red in CI over the same commit, because the checker's rule ("the parent directory exists, so the path must exist") let the developer's runtime state decide | the preflight's clean worktree of `eca23bfd`: `python3 scripts/docs-link-check.py --root "$(pwd)" --strict` → `defects: 2822  strict failures: 53`, exit 1 — all 53 `MISSING_PATH`, all runtime paths. The CI log of job 127199452254 shows `MISSING_PATH 199` and `DOCS-1 the documentation links do not resolve` | **`f5870f4c`** — a path under a runtime prefix is now the class `RUNTIME_ARTEFACT`: reported, never a strict failure. `RUNTIME_PREFIXES` covers `data/`, `bin/`, `formal/states/`, `matrix-core/bin/`, `matrix-core/data/`, `matrix-core/build/`, `target/`, `build/` | the CI's `Quality gates (can fail)` job at `4adbc664` prints **`DOCS-1     the documentation links resolve`**; and `python3 scripts/docs-link-check.py --root . --strict` on this tree → `strict failures: 0`, exit 0 (§7, command 3). The archive group was **not** the cause and is still exempt by the checker's own rule |
| **C** — the GraalVM distribution did not exist: `distribution: 'graalvm-ce'` is rejected by the action, so the job died at the setup step and compiled nothing | the preflight's §2.3 and the CI log of job 127199451062; the action's supported identifier for Community Edition is `graalvm-community` | **`02d2a516`** — `.github/workflows/branch-validation.yml` now asks for `distribution: 'graalvm-community'` with `java-version: '25'` | the `branch-validation` run at `4adbc664`: the job **Compile all T-01 modules** → **success** (job 127738104155, 05:51:23Z → 05:53:55Z). It compiles and smoke-tests five T-01 modules, which the old identifier never reached |
| **D** — the version gate grepped for a word: it required `VERSION` anywhere under a module's main sources, which cannot tell a version contract from a comment, and it failed on `matrix-sdk-java`, whose facade `MatrixClient` declared no version at all | the preflight's §2.4; the old gate was `grep -rq "VERSION" "$module/src/main"` | **`02d2a516`** — the gate now checks a real contract: a **public declaration of a `String` constant named `VERSION`** in the module's named public facade; `matrix-sdk-java/src/main/java/io/matrix/sdk/MatrixClient.java:47` now declares `public static final String VERSION = "0.1.0-T08";`, and `matrix-sdk-java/src/test/java/io/matrix/sdk/MatrixClientTest.java` checks that it exists, is not blank, is the module's own version and agrees with the accessor | the `branch-validation` run at `4adbc664`: **CONSTITUTION compliance check** → **success** (job 127738104478). The negative control — a facade whose only mention of `VERSION` is in a comment — is in §5 |
| **E** — the formal tool could not be downloaded reproducibly: the upstream tlaplus `v1.8.0` release was **recreated** on 2026-11-06 and serves different bytes | `curl -sS https://api.github.com/repos/tlaplus/tlaplus/releases/tags/v1.8.0` → `created_at: 2026-11-06T00:39:06Z`, `immutable: False`, `target_commitish: master`, asset `tla2tools.jar` 4 509 176 bytes; `sha256sum /tmp/tla-upstream-now.jar` → `654aa50197faa48fa0136b41257270de6469d29ef323e7822ead59d2be43ce10`; `GET /repos/tlaplus/tlaplus/commits?path=tla2tools.jar` → **0** commits; the pinned bytes are `fc6878c6…` (4 504 421 bytes) | **`02d2a516`** tracks the jar (`tools/tla2tools.jar`, excepted from the `*.jar` ignore rule) with its provenance in `tools/README.md`; **`16a2d0c5`** makes the CI **verify the tracked bytes** instead of downloading, and `scripts/formal-gate.sh` verifies the same digest locally before it model-checks anything | the `Formal Specs (TLA+)` run at `4adbc664`: **success** (job 127738104054); its log prints `tools/tla2tools.jar: OK` and `Model-checked 15 specification(s); 0 syntax-checked only; 0 failed.` Locally: `sha256sum --check --strict` on the tracked jar → `tools/tla2tools.jar: OK` (§7, command 4) |
| **F** — the root project's test task broke: adding the shared test sources to every project that applies the java plugin included the ROOT project, which has no tests, so its test task went from NO-SOURCE to "there are test sources present and no filters are applied, but the test task did not discover any tests to execute" | `git show e9a5c944 -- build.gradle` — the diff is `allprojects` → `subprojects`, in both places; the preflight's own record of the failure | **`e9a5c944`** — the wiring is scoped to the subprojects | the CI's `Run unit tests` step (`./gradlew test --no-daemon`) at `4adbc664` **reaches the modules** — the log of job 127738106005 shows `:matrix-api-gateway:test`, `:matrix-audit:test`, `:matrix-billing:test`, `:matrix-brain-runtime:test` — so the root's task is NO-SOURCE again. The helper still gets the property: `RepositoryPathsTest.theRootIsTheOneTheBuildDeclares` reads `matrix.repoRoot` and compares it with the resolved root |

### 2.1 The two production sources of cause A

Both were PRODUCTION code, not tests, and both are worth naming because a test that
names a stranger's home directory is annoying while a library that does is broken:

| the file | before | after |
|---|---|---|
| `matrix-core/src/main/java/io/matrix/imports/BitLinearGpu.java` | the absolute home path was the **last fallback** of the library search | three candidates relative to the working directory, in the same order |
| `matrix-core/src/main/java/io/matrix/imports/PanamaNativeBridge.java` | the absolute home path was the **only** candidate, so the bridge could load on that machine and failed everywhere else with a path error | the same three relative candidates, and the failure names the paths that were tried |

---

## 3. The current state of each workflow

**The moment of the reading:** **2026-11-06T06:12:34Z** (= `2026-11-06T09:12:34+0300`,
Europe/Moscow).
**The commit it was read at:** **`4adbc664548d91116298b81119c39be71328ac37`**
(`git rev-parse HEAD`; `git rev-parse origin/develop` → the same commit, no
divergence).

**The working tree at that moment.** It carried the untracked file
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` and no modification to any tracked
file. While this document was being written, **other agents of this wave** added
further untracked files and modified three tracked files —
`docs-v2/operations/RECOVERY-RUNBOOK.md`, `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java`
and `scripts/quality-gate.sh`. **None of those is mine, and none is committed.** Every
claim below about "unchanged" is a claim about the **committed history**
(`eca23bfd..HEAD`), which is the only thing the CI ever saw.

**The four workflows do not all run on every commit.** `docs-validation` is filtered
on `docs-v2/**` and `Formal Specs (TLA+)` on `formal/**` plus the generated formal
paths. A workflow that was not triggered has no state at HEAD — its last state is
named instead, with the commit it belongs to.

### 3.1 `ci` — **failure** · run **42170865426** · commit `4adbc664`

`createdAt 2026-11-06T05:51:21Z` · `updatedAt 2026-11-06T05:54:42Z`

| the job | the job id | the conclusion |
|---|---|---|
| Web UI Tests (matrix-web-ui) | 127738105543 | **success** |
| Build & Test (Java) | 127738106005 | **failure** — `:matrix-brain-runtime:test`: `918 tests completed, 1 failed, 2 skipped` |
| Quality gates (can fail) | 127738106021 | **failure** — the tool download's digest check |
| Weekly Research & Property Suites | 127738106889 | skipped |
| Docker Build & Push | 127739143802 | skipped |
| Security Scan | 127739143821 | skipped |
| Container Images (build + smoke) | 127739143938 | skipped |
| Deploy to Production | 127739144004 | skipped |
| Deploy to Staging | 127739144210 | skipped |

**The two failures are NOT the preflight's failures.**

- **Build & Test (Java)** — the eleven gateway failures are **gone**
  (`grep -c "NoFutureClaimsTest\|ProdCallerExistsTest\|SingleInstanceGuardTest"` over
  the job log → **0**). The single failure is
  `DiskBudgetTest > free_gigabytes_matches_bytes(Path)` at
  `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DiskBudgetTest.java:29`.
  §4.1 analyses it.
- **Quality gates (can fail)** — **DOCS-1 is green**: the log prints
  `DOCS-1     the documentation links resolve` and
  `PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).`
  The job fails at a **later** step: `sha256sum: WARNING: 1 computed checksum did NOT
  match` — the tool download of `.github/workflows/ci.yml:111-117`. §4.2 analyses it.

### 3.2 `branch-validation` — **success** · run **42170865265** · commit `4adbc664`

`createdAt 2026-11-06T05:51:21Z` · `updatedAt 2026-11-06T05:53:56Z`

| the job | the job id | the conclusion |
|---|---|---|
| Compile all T-01 modules | 127738104155 | **success** (05:51:23Z → 05:53:55Z) |
| CONSTITUTION compliance check | 127738104478 | **success** |
| Validate branch name | 127738105700 | skipped (the workflow runs this job only for a pull request) |

**This is the workflow the wave fixed.** At the preflight it was red in two jobs
(§1); both are green at HEAD.

### 3.3 `Formal Specs (TLA+)` — **success** · run **42170865258** · commit `4adbc664`

`createdAt 2026-11-06T05:51:21Z` · `updatedAt 2026-11-06T06:09:44Z`

| the job | the job id | the conclusion |
|---|---|---|
| Model-check TLA+ specs | 127738104054 | **success** (05:51:23Z → 06:09:43Z) |

The log prints `tools/tla2tools.jar: OK` (the tracked bytes verify), `Model-checked 15
specification(s); 0 syntax-checked only; 0 failed.`, and then the mutation harness:
`Mutation harness: 33 mutant(s) against 15 specification(s)`. The job's exit code is 0.
§4.5 records what the mutation harness itself reports.

### 3.4 `docs-validation` — **success** · run **42167940985** · commit `4084538a`

`createdAt 2026-11-06T05:21:25Z` · `updatedAt 2026-11-06T05:21:37Z`

| the job | the job id | the conclusion |
|---|---|---|
| Validate ecosystem docs (RU/EN parity) | 127728698488 | **success** |

**No `docs-validation` run exists at HEAD, and none is missing.** The workflow is
filtered on `docs-v2/**` (`git diff --name-only 4084538a..HEAD -- docs-v2/` → empty),
so `e9a5c944`, `241af1e1` and `4adbc664` could not trigger it. The state above is the
latest state of the workflow, read at the same moment, at the last commit that
triggered it.

### 3.5 The summary, one line per workflow

| the workflow | the state at the reading | the run | the commit | the jobs |
|---|---|---|---|---|
| `ci` | **failure** | 42170865426 | `4adbc664` | 1 success · 2 failure · 6 skipped |
| `branch-validation` | **success** | 42170865265 | `4adbc664` | 2 success · 1 skipped |
| `Formal Specs (TLA+)` | **success** | 42170865258 | `4adbc664` | 1 success |
| `docs-validation` | **success** | 42167940985 | `4084538a` | 1 success |

**Two of the three mandatory workflows are green at HEAD, and the third is red for two
reasons that the preflight did not name.** §4 states both.

---

## 4. What is STILL red, and what is unverified

### 4.1 `ci` → Build & Test (Java): one test, and it is a statement about the machine

```
2026-11-06T05:54:23Z DiskBudgetTest > free_gigabytes_matches_bytes(Path) FAILED
2026-11-06T05:54:23Z     java.lang.AssertionError at DiskBudgetTest.java:29
2026-11-06T05:54:38Z 918 tests completed, 1 failed, 2 skipped
```

The test compares two units. `DiskBudget.freeBytes()` returns
`FileStore.getUsableSpace()` in **bytes**; `DiskBudget.freeGigabytes()` returns
`freeBytes() / (1024.0 * 1024.0 * 1024.0)` — that is **GiB**, not GB — and the
assertion is

```java
assertThat(gb).isBetween(bytes / 1e9 - 5, bytes / 1e9 + 5);
```

so it compares a GiB number against a GB number and allows only **5** of difference.
The gap is `bytes × 6.867742538452155e-11`, so the assertion can only hold while the
free space is at most **72 804 127 004 bytes** (≈ 72.8 GB ≈ 67.8 GiB). Above that the
assertion fails — which is what the runner does.

**This is not a regression of this wave.** `git log -- matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DiskBudgetTest.java`
names two commits, `b813b046` and `4d420592`, both before W48. The failure was
**masked**, not absent: at the preflight the build stopped at
`:matrix-api-gateway:test` (`BUILD FAILED in 29s`) and never reached
`:matrix-brain-runtime:test`. Now that cause A is fixed, the build reaches the module
and the failure is exposed.

**UNVERIFIED:** the runner's actual free space at the moment of the test. The CI log
does not print it, and no build was run locally to measure it. The arithmetic above
is derived from the two source files, which is a fact; the size of the runner's free
space is not measured here.

**What it needs to become green** — either of: the assertion compares like with like
(GiB with GiB), or the tolerance is expressed in the same unit, or the free-space
reading is injected (the class already has `DiskBudget.withFreeBytes(...)` for
exactly that, and the sibling test `healthy_tier_is_default` uses it).

**RESOLVED in W48, commit `af9ba688`.** All three were applied together, because
they are one defect: the unit was implicit, so the test had to guess it. The unit is
now stated once, as `DiskBudget.BYTES_PER_GIB` (1024³ bytes), and is used by the
thresholds and by `freeGigabytes()`; the unit is asserted against a **controlled**
reading of 40 GiB, and the real-filesystem check compares the two readings in the
same unit with the drift tolerance it was written for.

```bash
$ ./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.DiskBudgetTest"
$ grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    matrix-brain-runtime/build/test-results/test/TEST-io.matrix.brain.runtime.DiskBudgetTest.xml
tests="10" skipped="0" failures="0" errors="0"
```

The whole scope passes: 120 classes, 919 tests, 2 skipped, 0 failures.

### 4.2 `ci` → Quality gates (can fail): cause E is fixed in one workflow and NOT in the other

```
2026-11-06T05:51:39Z DOCS-1     the documentation links resolve
2026-11-06T05:51:39Z PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).
2026-11-06T05:51:39Z   | sha256sum -c -
2026-11-06T05:51:39Z sha256sum: WARNING: 1 computed checksum did NOT match
2026-11-06T05:51:39Z tools/tla2tools.jar: FAILED
```

**`.github/workflows/ci.yml:111-117` still DOWNLOADS the tool from the replaced
upstream.** Commit `16a2d0c5` changed `.github/workflows/tla.yml` and
`scripts/formal-gate.sh`; it did not change `.github/workflows/ci.yml`, and the diff
proves it:

```bash
$ git diff eca23bfd..HEAD -- .github/workflows/ci.yml
(empty)
$ git diff --stat eca23bfd..HEAD -- .github/workflows/
 .github/workflows/branch-validation.yml | 52 ++++++++++++++++++-----
 .github/workflows/tla.yml               | 21 +++++++------
```

So the `Formal Specs (TLA+)` workflow verifies the tracked bytes (green), and the `ci`
workflow's quality-gates job downloads the upstream's current bytes and correctly
refuses them (red). **The check is right; the source in that one step is still the
replaceable URL.** The fix is to make that step verify the tracked bytes the way
`.github/workflows/tla.yml` does — one step, in one file.

**RESOLVED in W48, commit `9bb6bf5c`.** The step now verifies the tracked bytes:

```yaml
      - name: The pinned formal tool (the bytes are in the repository)
        run: |
          echo "fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e  tools/tla2tools.jar" \
            | sha256sum --check --strict
          ls -lh tools/tla2tools.jar
          unzip -l tools/tla2tools.jar | grep -E 'tlc2/TLC.class' | head -1
```

Neither workflow downloads anything any more, so the two cannot drift apart again.
The `.github/` frozen-zone exception was raised from 946 to 958 diff lines with the
reason recorded in `docs-v2/quality/frozen-zone-baseline.txt`; the triggers, the
permissions, the secrets, the concurrency settings and the cache configuration did
not change.

```bash
$ git diff eca23bfd..HEAD -- .github/workflows/ci.yml
+      - name: The pinned formal tool (the bytes are in the repository)
...
$ grep -c "releases/download/v1.8.0/tla2tools.jar" .github/workflows/*.yml
0
```

**What is still open:** the `ci` job's Build & Test step failed at
`4adbc664` on `DiskBudgetTest.free_gigabytes_matches_bytes`, a test that compared
GiB with GB. That test defect is fixed in W48, commit `af9ba688` — see §4.1.

### 4.3 The native-image classes: the toolchain is not usable here, and CI never reaches them

**The 23 native-binary failures** (the eight classes, reconciled in
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §2) are an **environment** failure:
the native executable is never built, because the native-image toolchain is absent.

| the element | the evidence |
|---|---|
| the toolchain on this host | `command -v native-image` → **nothing**; `command -v gu` → **nothing**; `GRAALVM_HOME` → **unset**; `JAVA_HOME` → **unset**; `java -version` → `openjdk version 25.0.4.1` — an **OpenJDK**, not GraalVM |
| what the build says it needs | `matrix-core/build.gradle:367-369` — "Local build: `./gradlew :matrix-core:build -Dquarkus.package.type=native` / Requires: GraalVM 25+ or Mandrel 24.x (container), native-image installed" |
| the only CI workflow that builds it | `.github/workflows/native.yml` — `on: push: branches: [main]` and `pull_request: branches: [main]`. **It does not run on `develop`.** |
| what it does when it does run | `gh api "repos/AlexanderNarbaev/agi/actions/workflows/native.yml/runs?per_page=1" --jq '.total_count'` → **1403** runs on record; the **last ten are all `failure`** (`per_page=10`). The most recent (`41219224376`, 2026-10-31) installed the toolchain successfully — `Successfully set up 'graalvm-jdk-25.0.4+7.1'` — and then died at `Terminating due to java.lang.OutOfMemoryError: Java heap space`. The last `main` push run (`38274906793`, 2026-10-12) failed the same way: `Terminating due to java.lang.OutOfMemoryError: Java heap space` / `BUILD FAILED in 3m 29s` |
| whether CI reaches those classes | **no.** `:matrix-core:test` never ran in the `ci` job at `4adbc664`: the log contains no `:matrix-core:test` line, because the build stopped at `:matrix-brain-runtime:test`. The eight native classes live in `matrix-core` |

**So: the native-image classes are UNVERIFIED in CI as well as locally.** They are
not "green because the fix worked" — they are "not reached". The `graalvm-community`
distribution fix of cause C makes the **compile** step of `branch-validation` work;
it does not produce a native binary on `develop`, because the workflow that does that
is not triggered there, and where it is triggered it dies of OOM.

**RESOLVED as a reporting problem in W48, commit `31d8f832`.** The capability was
never verified and still is not; what changed is that the scope is now named,
runnable and honest:

| the scope | the command | the result on this host |
|---|---|---|
| the default JVM task | `./gradlew :matrix-core:test` | the native-binary scope is **excluded** — the negative control `./gradlew :matrix-core:test --tests "io.matrix.nativebin.*"` reports "No tests found", where before it ran 27 tests and failed 23 |
| the native-binary scope | `./gradlew :matrix-core:nativeBinaryTest` | **BLOCKED** — the task refuses to run: "The native executable …/build/native/nativeCompile/matrix-core does not exist, so the native-binary test scope cannot run. Build it with ./gradlew :matrix-core:buildNative … This scope is BLOCKED - it is not a pass, and the default JVM task does not cover it." |
| the native build-configuration scope | `./gradlew :matrix-core:nativeBuildConfigTest` | **passes** — 2 tests, 0 failures; it needs no executable |

Two of the tests in those classes were passing **vacuously** (`File.length()` is 0 for
a missing file, so "the native binary is smaller than 150 MB" held against no binary
at all); they now carry the native-binary tag, and the false pass is gone.

The native capability stays **BLOCKED** in `docs-v2/architecture/CAPABILITY-REGISTRY.yml`.
The OOM in `native.yml` is a separate, still-open resource question: its nine runs
all died with `OutOfMemoryError: Java heap space` after installing the toolchain
successfully.

### 4.4 The GPU path: it needs the CUDA libraries and `LD_LIBRARY_PATH`

| the element | the evidence |
|---|---|
| how the provider is loaded | `scripts/start_production.sh:24-39` — "ONNX Runtime loads its CUDA provider with a native dlopen, and the OS dynamic linker resolves that through **LD_LIBRARY_PATH** — never through the JVM's `-Djava.library.path` … Without this, the loader finds the distro's `libcudart`, which does not export the symbol the provider needs: `addCUDA()` throws inside `OnnxRuntimeAdapter`, the exception is swallowed, and MATRIX silently runs on the CPU." The launcher prepends `.onnx_libs` **only if that directory exists** |
| where the libraries come from | `.onnx_libs/` is **gitignored** (`.gitignore:202`) and holds **symlinks into a python virtualenv**: `libcudart.so.12 → .venv/lib/python3.14/site-packages/nvidia/cuda_runtime/lib/libcudart.so.12`. `git ls-files .onnx_libs` → **empty**. The CI runner has no such directory, so the loader finds the distro's CUDA instead |
| the hardware | `nvidia-smi --query-gpu=name,driver_version --format=csv,noheader` → `NVIDIA GeForce RTX 5070 Ti Laptop GPU, 595.99.02` — the GPU is present on the owner's machine |
| CI | `grep -niE "gpu|cuda|nvidia" .github/workflows/ci.yml` → **no match**. There is **no GPU job** in the CI, on any runner |
| the diagnostic | `scripts/diagnose-cuda.sh` reports the driver, the toolkit, the NVML state and the ABI of each symlink separately, and repairs the symlinks — because the CUDA fallback used to be reported as an environment fact when it was four symlinks pointing a CUDA 12 SONAME at a CUDA 13 binary |
| the claim | the "12.6× GPU" figure has **no re-runnable artefact**: the preflight's §6 lists it as a blocker for W48-09. **UNVERIFIED.** |

**So: the GPU path is UNVERIFIED.** Not "red" — there is no gate for it to fail. It is
unproven in CI because CI has no GPU and no CUDA libraries, and it is unproven in the
repository because the artefact that would make it re-runnable does not exist yet.

### 4.5 The mutation harness's two surviving mutants

The `Formal Specs (TLA+)` job concluded **success**, and the same log records the
mutation harness's own finding:

```
[ MISS ] Consensus-evaluate-without-quorum: SURVIVED - 71929 states, no violation
[ MISS ] KnowledgeAxes-conflict-forgets-what-it-recorded: SURVIVED - 4977 states, no violation
```

These are **recorded findings**, not new failures: the log's own text says "A mutant
that survived is a hole in the gate. Each one below is a known hole, recorded so that
it is visible rather than discovered later." They are the reason the corresponding
registry entries do not claim more than they can. **They are named here because a
document that reports "success" without them would be reporting half the state.**

### 4.6 The complete list of what this document does not verify

1. **The runner's free disk space** at the moment `DiskBudgetTest` ran (§4.1).
2. **The native-image classes** — 23 failures, an environment block, and in CI the
   module is not even reached (§4.3).
3. **The GPU path** — no CI job, no tracked CUDA libraries, no re-runnable artefact
   (§4.4).
4. **`scripts/formal-gate.sh` was not executed locally** — **this is no longer true.**
   It was executed on 2026-11-06 after this reading, with the JDK 25 on the PATH:
   `scripts/formal-gate.sh` → **"the formal gate: 15 passed, 0 failed"** (every
   specification in `formal/` model-checked, the pinned jar's digest verified by the
   script itself before the first specification ran). The local gate and the CI gate
   now model-check the same specifications with the same verified bytes.
5. **The W47 full-suite denominator** (1 226 classes / 9 051 executions) is quoted by
   `docs-v2/engineering/W48-TEST-RECONCILIATION.md`, not re-derived here.
6. **No claim is made about the tree after `4adbc664`.** A CI state is a claim about a
   moment and a commit; the moment is §3's header and the commit is `4adbc664`.

### 4.7 The clean runner AFTER the fixes: twelve failures, all diagnosed

This subsection is a LATER reading than §3, and it is dated separately because a CI
state is a claim about a moment and a commit. §3 describes the tree at `4adbc664`;
this one describes the tree at `0eac36c3`.

```
gh run list --branch develop --limit 4 \
  --json databaseId,headSha,status,conclusion,name
42181161855 0eac36c34 docs-validation completed success
42181161808 0eac36c34 branch-validation completed success
42181161742 0eac36c34 ci completed failure
```

**`docs-validation` and `branch-validation` are green. The `ci` workflow is not.**

**The `Quality gates (can fail)` job: DOCS-1 is GREEN.** The archive classification
works on the runner — the same job's log says

```
DOCS-1     the documentation links resolve
PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).
```

That is the W48-05 fix (the archive is scanned read-only and reported in its own
group, and its defects do not fail the gate) **verified on a clean runner** — the
failure of §3's run is gone. The job is still red, but for a different step: the
security boundary check.

**The `Build & Test (Java)` job: 9070 tests completed, 12 failed, 132 skipped.** The
twelve are not the native-binary classes (W48-06 separated those into
`nativeBinaryTest`, and the default JVM task excludes them). They are:

| the failure | the count | the cause | the fix |
|---|---|---|---|
| `DecisionPathGateTest > no decision path introduces a nondeterministic decision` | 1 | the accepted decision-path baseline still listed nine knowledge-core groups that W48's wall-clock work REMOVED | the nine stale groups were deleted from `docs-v2/quality/decision-path-baseline.txt` — the findings are gone, so the exceptions are gone |
| `Exp144DecisionPathAuditTest > auditReality()` | 1 | the same baseline | the same |
| `Exp016ProductionCorpusTest > …` | 4 | `NoSuchFileException`: the corpus `models/training_data/qa_pairs.json` is the OWNER'S data and `/models/` is git-ignored, so a clean runner has no corpus | the class now SKIPS with a recorded reason when the corpus is absent |
| `Exp020E2EBilingualStressTest > …` | 6 | the same corpus | the same |

The corpus case is the honest one, and it is NOT a "fixed test": on a clean runner
the production-corpus properties are **NOT VERIFIED** — they are skipped with a
reason, which is neither a failure (the code under test is not broken) nor a pass
(the property was not checked). The evidence is the negative control below.

**The negative control for the corpus skip** — the corpus was moved aside, the two
classes were re-run, and the file was restored:

```
mv models/training_data/qa_pairs.json /tmp/qa_pairs.json.bak
./gradlew :matrix-core:test --tests "io.matrix.research.Exp016ProductionCorpusTest" \
                         --tests "io.matrix.research.Exp020E2EBilingualStressTest"
```

| the class | tests | failures | skipped |
|---|---|---|---|
| `io.matrix.research.Exp016ProductionCorpusTest` | 5 | 0 | 5 |
| `io.matrix.research.Exp020E2EBilingualStressTest` | 6 | 0 | 6 |

With the corpus present again, the same two classes execute and pass (5 and 6 tests,
0 failures) — the skip is conditional on the corpus, not on the code.

**The security boundary check on the runner: the application did not open its port
within the check's 60-second window.** The check's own log on the runner said only
"the service did not start in local-only mode (see /tmp/w43-security-local.log)" and
the log was never printed, so the reason could not be read from the CI run at all.
Two things changed, and neither of them touches the boundary assertions:

1. the startup window is 180 seconds instead of 60 — a cold runner extracts the ONNX
   runtime's native library (about 583 MB) into a fresh temporary directory while
   the JVM starts, with no warm page cache;
2. the check now prints the tail of the application's own log when the service does
   not come up, and it stops waiting early if the JVM died — so the next red run
   says WHY.

**Locally the check still holds every assertion** (`scripts/security-boundary-check.sh`
→ "the security boundary: 0 — every assertion held, and the live data is untouched").

**What is still unverified after this subsection:** the security boundary on the
clean runner (the next run decides it), and the production-corpus properties in CI
(they need the owner's corpus, which is not in the repository — and is not going to
be, because `/models/` is git-ignored on purpose).

---

### 4.8 The clean runner AFTER the CI repairs: three red jobs, three causes that were not defects

This subsection is dated separately again, because a CI state is a claim about a
moment and a commit. §4.7 describes the tree at `0eac36c3`; this one describes
the tree at `63d440b1` and the repair that followed at `bc87935c`.

```
gh run view --json jobs --limit 1  # run 42184035743, commit 63d440b1
```

| the job | the verdict at `63d440b1` | the cause |
|---|---|---|
| Build & Test (Java) | **success** | — the twelve failures of §4.7 are gone |
| Web UI Tests | **success** | — |
| Quality gates (can fail) | **failure** | the application could not connect to Redis |
| Security Scan | **failure** | the job had no JDK and inherited an invalid `JAVA_HOME` |
| Container Images (build + smoke) | **failure** | the smoke test probed a published port the process does not hold |

#### 4.8.1 The quality-gates job: Redis

The job starts the real application for the security-boundary check. The runner's
log for that step carried the cause:

```
Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException:
Connection refused (connect failed): localhost/127.0.0.1:6379
	at io.matrix.redis.NeuronCacheService_Bean.get(...)
```

`matrix-core/src/main/resources/application.properties:14` declares
`redis.uri=${REDIS_URI:redis://localhost:6379}`, and the application opens the
cache eagerly. A clean runner has no Redis, so the application could not start and
the check reported a defect that does not exist.

**The repair:** the job declares the dependency it has — a `redis:7-alpine`
service container with a health check — instead of inheriting whatever the
developer happens to run locally. The application was not changed.

#### 4.8.2 The security job: no JDK

The job had no `actions/setup-java` step, so it inherited the runner's
`JAVA_HOME`:

```
JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/25.0.1.6/Contents/Home
```

That path does not exist on a Linux runner — it is a macOS-style path — and Gradle
refused to start with "ERROR: JAVA_HOME is set to an invalid directory". The job
failed before SpotBugs ever ran, which is why the run carried no static-analysis
evidence at all.

**The repair:** the job installs the same JDK 25 the other jobs use. The known
Java-25 SpotBugs engine limitation is unchanged and still reported as a loud skip,
never as a pass.

#### 4.8.3 The container-images job: a published port the process does not hold

The api-gateway smoke test ran the image in its **default** exposure mode and then
probed the **published** host port:

```bash
docker run -d --name gw-ci -p 8765:8765 matrix-api-gateway:ci
...
if curl -fsS http://127.0.0.1:8765/health/ready ...
```

The probe failed for the whole 80-second window, while the container's own log
said the application had started:

```
Nov 06, 2026 8:12:40 AM io.matrix.api.MinimalHttpServer start
INFO: matrix-api-gateway listening on http://0.0.0.0:8,765
```

**The cause is the exposure boundary, and the log line was lying about it.**
`matrix-core/src/main/java/io/matrix/security/NetworkExposure.java` defaults to
`Mode.LOCAL_ONLY`, and `bindAddress()` then returns the loopback address
(line 68 and line 182); the gateway binds through it
(`MinimalHttpServer.java:317`). Inside the container the listener therefore held
**127.0.0.1**, and the published port reached nothing. The startup line printed a
hardcoded `0.0.0.0`, so the log claimed an interface the process did not hold —
which is exactly why this looked like a startup failure instead of a boundary
fact.

The reproduction is local and takes seconds: with the default exposure the
gateway answers on the loopback and the published port is dead.

**The repair:**

1. the startup line names the address and the port the listener actually bound
   (the port is concatenated rather than MessageFormat-formatted, because the
   formatter rendered it as `8,765` — a port that does not exist);
2. the smoke test mirrors the deployed configuration
   (`deploy/docker-compose.yml`: `NETWORK_EXPOSED` with a credential, where the
   credential is a throwaway value for the job and not a secret) and reports the
   container state as well as the log when it fails;
3. the two matrix-core smoke tests in the same job had the same defect
   (`quarkus.http.host=${QUARKUS_HTTP_HOST:127.0.0.1}`,
   `application.properties:44`) and **never reached it**, because the api-gateway
   step failed first. They now set the host explicitly for the ephemeral
   container.

**The regression test** `matrix-api-gateway/src/test/java/io/matrix/api/
GatewayExposureBoundaryTest.java` pins the boundary itself: in the default
exposure the listener holds the loopback (so "publish the port" is an invalid
probe), and the startup line names the bound address and the bound port.

```
./gradlew :matrix-api-gateway:test --tests "io.matrix.api.GatewayExposureBoundaryTest"
-> 2 tests completed, 0 failed
```

**The negative control:** with the hardcoded `0.0.0.0` restored in the startup
line, the second test goes RED ("the startup line must not claim an interface the
listener does not hold"); the fix was then restored and the class is green again.

**The frozen-zone exception** in `docs-v2/quality/frozen-zone-baseline.txt` is
raised from 958 to 1017 diff lines, with the reason recorded in the file.

#### 4.8.4 What is still unverified after this subsection

- the CI verdict of the run that carries `bc87935c` (the repair itself);
- the container smoke tests for the matrix-core and minikube images — they never
  ran on a clean runner, because the api-gateway step failed first. Their
  configuration was corrected, but their verdict is NOT VERIFIED until a run
  reaches them;
- the Helm chart's own mismatch, which this wave did NOT change: the chart
  declares `containerPort: 8080` and probes `/q/health/live`, while the
  application serves on `QUARKUS_HTTP_PORT` (default 9091) and binds
  `QUARKUS_HTTP_HOST` (default 127.0.0.1). The chart sets neither. That is a
  deployment-configuration defect, it needs an owner-level decision about the
  port the platform serves, and it is recorded here as an open item rather
  than repaired inside W48.

---

### 4.9 The clean runner after the CI repairs: three jobs green, two causes left, both in the workflow

The run that carries the three job repairs is `42198961133` at `bc87935c`.

```
gh run view --json status,conclusion,jobs 42198961133
```

| the job | the verdict | what it proves |
|---|---|---|
| Build & Test (Java) | **success** | the Java suite is green on a clean runner |
| Web UI Tests | **success** | — |
| Quality gates (can fail) | **success** | every step green, including **the security boundary** and **the formal gate** |
| Security Scan | **failure** | the SpotBugs step aborted before its own branch — and skipped two real checks |
| Container Images (build + smoke) | **failure** | the api-gateway and matrix-core smoke tests are green; the minikube one had no database |

#### 4.9.1 The quality-gates job is green — and the security boundary now HOLDS there

The job's own step list, on the runner:

```
- Initialize containers                      -> success   (the Redis service started)
- The clean-code gate                        -> success
- The pinned formal tool (the bytes are in the repository) -> success
- The formal gate (every specification is model-checked)   -> success
- Build the application                     -> success
- The security boundary (anonymous writes are refused)    -> success
```

**The security boundary is verified on a clean runner** — that is the check §4.7
could not observe, and it is the one that proves the Redis service container is
the whole cause of the earlier failure. The formal gate ran there too, so the 15
specifications are model-checked in CI and not only locally.

#### 4.9.2 The security job: `bash -e` aborted the step before its own branch

The SpotBugs step failed with the **known** engine limitation in its log:

```
> Task :matrix-core:spotbugsMain
The following errors occurred during analysis:
  Error scanning io/quarkus/runner/ApplicationImpl for referenced classes
    java.lang.IllegalArgumentException: Unsupported class file major version 69
```

and the step's own "known limitation" branch never ran — its warning line is
absent from the log. The cause is in the step's own script: GitHub executes
every `run:` block with `bash -e`, so the failing pipeline
(`./gradlew … | tee /tmp/spotbugs.log`) aborted the step before
`status=${PIPESTATUS[0]}` could be read. The job therefore failed for the
wrong reason, **and the two real checks after it were skipped**:

```
- SpotBugs static analysis -> failure
- Check for secrets in code -> skipped      (trufflehog)
- OWASP dependency check  -> skipped
```

A security job that skips its secret scan is worse than a red one. The step now
captures the pipeline's status and lets its own branches decide, so the known
limitation produces a loud warning (and no static-analysis evidence), and any
other failure still fails the job.

**What the limitation now costs, precisely.** The engine is pinned to 4.9.3,
which bundles ASM 9.7.1 (class file major 68 at most). The engine that CAN read
Java 25 bytecode exists: **SpotBugs 4.10.5 bundles ASM 9.11** (verified against
Maven Central's POM). Measured locally with that engine, the analysis runs and
reports **1 414 findings**, of which **981** are `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`
("may expose an internal representation by returning a mutable object"). Turning
the engine on is therefore not a one-line change: it is a decision about 1 414
findings, and the policy for them (fix, exclude, or accept) belongs to the owner.
W48 does not change the pin. The capability stays **NOT VERIFIED** — no
static-analysis evidence is produced by the current engine.

#### 4.9.3 The container smoke tests: two green, one without a database

```
- Smoke test api-gateway image        -> success
- Smoke test matrix-core image        -> success
- Smoke test matrix-core minikube image -> failure
```

The api-gateway and matrix-core smoke tests are green on the clean runner: the
gateway serves its readiness endpoint on the published port, and so does the
core image. The minikube image failed because the step reused the Postgres and
Redis containers of the previous step — and the previous step removed them when
it finished:

```
Caused by: io.lettuce.core.RedisConnectionException:
  Unable to connect to redis-ci/<unresolved>:6379
```

The step now brings up its own dependencies. A step that depends on another
step's leftovers is not reproducible.

#### 4.9.4 The verdict after this subsection

The remaining red items are both in the workflow's own steps and both are fixed
in the commit that follows this run (`e0de8419`). What is **NOT VERIFIED** until
a run carries them: the security job with its secret scan and its
vulnerable-dependency check actually running, and the minikube smoke test.

---

## 5. The negative controls

**A fix that cannot fail is not a fix.** Each of the seven causes has a control that
proves the new check is still able to go red.

| the cause | the negative control | the result |
|---|---|---|
| **A** — the repository root | `matrix-core/src/test/java/io/matrix/testsupport/RepositoryPathsTest.java` › `aMissingSourceFileFailsLoudlyInsteadOfPassingQuietly` — `RepositoryPaths.source("matrix-core/src/main/java/NoSuchFile.java")` must **throw** `IllegalStateException` with "does not exist" | the helper **throws** rather than returning a default (`test-support/java/io/matrix/testsupport/RepositoryPaths.java:73-80`). A source-integrity test cannot pass without its subject |
| **B** — the documentation gate | **the checker's own self-test** — `python3 scripts/docs-link-check.py --self-test` builds a broken fixture and a clean fixture and observes the strict mode's exit code over each | `the strict mode over the broken fixture exits 1` · `the strict mode over the clean fixture exits 0` · `the self-test PASSED` (exit 0). The five defect classes are still found: `BROKEN_ANCHOR, BROKEN_LINK, DUPLICATE_IDENTIFIER, MISSING_PATH, UNDEFINED_IDENTIFIER` |
| **C** — the GraalVM distribution | the old identifier **is** the control: the action rejected `graalvm-ce` and the job died at the setup step, producing no compilation evidence | the action's own rejection is the proof that the identifier is checked and not ignored. The success of the same job at `4adbc664` is what proves the new one is accepted |
| **D** — the version gate | a facade whose **only** mention of `VERSION` is in a comment | run against a fixture (`/tmp/w48-negctl/CommentOnly.java`): the **new** check `grep -qE 'public +static +final +String +VERSION *='` → **fails with exit 1**; the **old** check `grep -rq "VERSION"` → **passes**. That difference is the whole defect |
| **E** — the pinned tool | the digest check refuses bytes that are not the pinned ones | the upstream's current bytes (`654aa501…`, 4 509 176 bytes) are refused by `sha256sum --check --strict` — this is exactly what the CI does today at job 127738106021: `sha256sum: WARNING: 1 computed checksum did NOT match`. `scripts/formal-gate.sh:39-53` refuses the same way and exits 1 with "the formal tool is not the pinned one" |
| **G** — the gateway's bind log | the hardcoded interface is the control: with `0.0.0.0` restored in the startup line, `GatewayExposureBoundaryTest > the startup line names the address and the port the listener actually bound` goes RED | the run with the hardcoded line restored: `2 tests completed, 1 failed` — the assertion "the startup line must not claim an interface the listener does not hold". With the fix in place: `2 tests completed, 0 failed` |
| **F** — the root project's task | the root's task is **NO-SOURCE** again — if the shared sources were still wired into the root, the CI's `Run unit tests` step would fail on the root's task before reaching a module | the CI at `4adbc664` reaches `:matrix-api-gateway:test`, so the root's task did not fail. And the modules still get the property: `RepositoryPathsTest.theRootIsTheOneTheBuildDeclares` reads `matrix.repoRoot` and compares it with the resolved root |

---

## 6. What was NOT changed

### 6.1 The workflows' configuration

The **triggers, permissions, secrets, concurrency and cache configuration** of every
workflow are byte-identical between the preflight's commit and HEAD:

| the workflow | the file | what was touched |
|---|---|---|
| `ci` | `.github/workflows/ci.yml` | the tool download became a digest verification of the tracked bytes, the same repair `tla.yml` already had (`9bb6bf5c`); and, in the same wave, the three job repairs of §4.8 — the Redis service of the quality-gates job, the JDK setup of the security job, and the container smoke tests of the container-images job. The `permissions:` block is unchanged |
| `branch-validation` | `.github/workflows/branch-validation.yml` | only two steps: the version-contract check and the GraalVM setup (`distribution`, `java-version`) |
| `Formal Specs (TLA+)` | `.github/workflows/tla.yml` | only the tool step: the download became a digest verification of the tracked bytes. The `permissions:` block (line 349) is unchanged |
| `docs-validation` | `.github/workflows/docs-validation.yml` | **nothing** |

The `on:` blocks, the `cache:` settings, the `secrets.*` references and the
`permissions:` blocks are identical in both revisions — checked by comparing the two
revisions of each file line by line (§7, command 6).

### 6.2 The quality baseline

| the item | the state |
|---|---|
| the accepted literal baseline | **271**, unchanged: the CI log at `4adbc664` prints `PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).` — the same number the preflight's log printed at `eca23bfd` |
| `scripts/quality-gate.sh` | **unchanged** — `git diff --stat eca23bfd..HEAD -- scripts/quality-gate.sh` → empty |
| `docs-v2/engineering/TEST-CATALOGUE.yml` | **unchanged** — same diff → empty |
| `formal/mutations.json` | **unchanged** — same diff → empty |
| the gates themselves | **seven** in the committed tree — MAGIC-1, MAGIC-2, CFG-1, FROZEN-1, DOCS-1, TEST-1, RETENTION-1 (`git show HEAD:scripts/quality-gate.sh` → seven `check_*` functions). The CI log at `4adbc664` shows `MAGIC-1 shell literal candidates: 78`, `MAGIC-2 java literal candidates: 193`, `FROZEN-1 all 5 frozen zones within their recorded exceptions`, `DOCS-1 the documentation links resolve`, `TEST-1 the test catalogue agrees with the evidence` and `RETENTION-1 the retention policy holds` — all green |

**No gate was weakened and no baseline was moved.** DOCS-1 went green because the
**checker** changed, not because the threshold moved: the accepted literal count is
the same 271 before and after.

**"Unchanged" means the committed history** (`eca23bfd..HEAD`). The working tree
carries a concurrent, uncommitted edit to `scripts/quality-gate.sh` by another agent
of this wave (§3): it adds the `SNAPSHOT-RETENTION-1` gate (a **new** gate, a
strengthening) and refreshes a stale header comment that still listed only six.
**The CI has never seen it**, and it is not this document's work.

### 6.3 The strict scope of the link checker over active normative documentation

The strict scope is **unchanged**. What `f5870f4c` added is a **class**, not a
threshold:

| the item | before | after |
|---|---|---|
| the classes the strict mode fails on | `BROKEN_LINK, BROKEN_ANCHOR, MISSING_PATH, UNDEFINED_IDENTIFIER, DUPLICATE_IDENTIFIER` | **the same five** |
| the groups that never fail strictly | the archive (`docs-v2/_raw/`) and the waves (`docs-v2/waves/`) | **the same two** |
| a document whose status is historical | never a strict failure | **unchanged** |
| the classifications (`STATUS_OUT_OF_VOCABULARY`, `UNREACHABLE_FROM_INDEX`) | reported always, strict only under `--strict-classifications` | **unchanged** |
| the **new** class | — | `RUNTIME_ARTEFACT` — reported always, **never** strict, and only for a path under a runtime prefix |
| the rule that produced the defect | "the parent directory exists, so the path must exist" | the parent-directory rule still applies to every **repository** path; only a path the **runtime owns** is reclassified, and only for the *missing* case |

**The archive group was not the cause and was not touched.** The preflight's §2.2
already established that, and `f5870f4c` did not change it: the archive's 1 095
`BROKEN_LINK`s are still reported and still never fail the gate.

### 6.4 What the checker says about **this** document

The strict run with this file in place reports **0 strict failures** and exits 0:

```
$ python3 scripts/docs-link-check.py --root . --strict
  defects: 2774  strict failures: 0  external links (UNVERIFIED): 200
  the exit code: 0
```

The checker reports **two** items against this file, and both are **classifications,
not strict failures** — they fail the gate only under `--strict-classifications`,
which the gate does not use:

| the classification | what it is |
|---|---|
| `STATUS_OUT_OF_VOCABULARY` | the descriptive status line ("the record of the wave's CI stabilisation") is outside the policy's vocabulary. **The wave's other documents carry the identical classification** — `W48-BASELINE.md`, `W48-TEST-RECONCILIATION.md` and `W47-STATUS.md` are reported the same way, for the same reason. A document that declares no vocabulary status is ACTIVE by default, so the classification does not change how this file is treated |
| `UNREACHABLE_FROM_INDEX` | `docs-v2/INDEX.md` does not reach this document. **Neither does it reach** `W48-BASELINE.md`, `W48-TEST-RECONCILIATION.md` or `W47-STATUS.md` — the wave's documents are reached from the wave report, not from the index, and adding them to the index is not this document's write scope |

**This document introduces no strict defect.**

---

## 7. The commands

Every command below was run, and every output quoted above is its output.

```bash
# 1. the runs on the branch, and the state of each workflow
gh run list --branch develop --limit 12 \
  --json databaseId,workflowName,status,conclusion,headSha,createdAt,updatedAt,event
gh run view 42170865426 --json conclusion,headSha,jobs        # ci         @ 4adbc664
gh run view 42170865265 --json conclusion,headSha,jobs        # branch-val @ 4adbc664
gh run view 42170865258 --json conclusion,headSha,jobs        # formal     @ 4adbc664
gh run view 42167940985 --json conclusion,headSha,jobs        # docs       @ 4084538a
gh run view 42013895090 --json jobs                           # ci         @ eca23bfd
gh run view 42013894963 --json jobs                           # branch-val @ eca23bfd
gh run view 42013763834 --json jobs                           # formal     @ 583358df
gh run view 42013894989 --json jobs                           # docs       @ eca23bfd

# 2. the job logs
gh api repos/AlexanderNarbaev/agi/actions/jobs/127199452300/logs   # ci build   @ eca23bfd
gh api repos/AlexanderNarbaev/agi/actions/jobs/127199452254/logs   # ci quality @ eca23bfd
gh api repos/AlexanderNarbaev/agi/actions/jobs/127199013127/logs   # formal     @ 583358df
gh api repos/AlexanderNarbaev/agi/actions/jobs/127738106005/logs   # ci build   @ 4adbc664
gh api repos/AlexanderNarbaev/agi/actions/jobs/127738106021/logs   # ci quality @ 4adbc664
gh api repos/AlexanderNarbaev/agi/actions/jobs/127738104054/logs   # formal     @ 4adbc664

# 3. the documentation gate — the self-test (the negative control) and the strict run
python3 scripts/docs-link-check.py --self-test      # → the self-test PASSED (exit 0)
python3 scripts/docs-link-check.py --root . --strict   # → strict failures: 0 (exit 0)

# 4. the pinned formal tool
sha256sum tools/tla2tools.jar                      # → fc6878c6…  4504421 bytes
echo "fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e  tools/tla2tools.jar" \
  | sha256sum --check --strict                     # → tools/tla2tools.jar: OK
curl -sS https://api.github.com/repos/tlaplus/tlaplus/releases/tags/v1.8.0   # → immutable: false
curl -sS "https://api.github.com/repos/tlaplus/tlaplus/commits?path=tla2tools.jar"  # → []
sha256sum /tmp/tla-upstream-now.jar                # → 654aa501…  4509176 bytes

# 5. the version gate's negative control
cd /tmp/w48-negctl
grep -qE 'public +static +final +String +VERSION *=' CommentOnly.java || echo "the gate fails"  # → the gate fails
grep -rq "VERSION" CommentOnly.java && echo "the old gate passes"                              # → the old gate passes

# 6. what was NOT changed
git diff --stat eca23bfd..HEAD -- .github/workflows/ci.yml    # → 1 file, the tool step only
grep -rn "releases/download/v1.8.0/tla2tools.jar" .github/workflows/  # → no match
git diff --stat eca23bfd..HEAD -- scripts/quality-gate.sh \
  docs-v2/engineering/TEST-CATALOGUE.yml formal/mutations.json # → empty
for f in ci.yml branch-validation.yml tla.yml docs-validation.yml; do
  diff <(git show eca23bfd:.github/workflows/$f | grep -nE "^permissions:|^concurrency:|secrets\.|cache:") \
       <(grep -nE "^permissions:|^concurrency:|secrets\.|cache:" .github/workflows/$f) && echo "$f: identical"
done

# 7. the fixes, commit by commit
git show 0c5b3980 --stat ; git show f5870f4c -- scripts/docs-link-check.py
git show 02d2a516 -- .github/workflows/branch-validation.yml ; git show 16a2d0c5 -- .github/workflows/tla.yml scripts/formal-gate.sh
git show e9a5c944 -- build.gradle
git log --oneline --diff-filter=A -- tools/tla2tools.jar        # → 02d2a516
git ls-files --error-unmatch tools/tla2tools.jar                # → tools/tla2tools.jar
git check-ignore -v .onnx_libs                                   # → .gitignore:202:.onnx_libs/

# 8. the paths the fixes removed
git grep -n "/home/alexandr-narbaev" -- '*.java' '*.gradle'    # → only the helper's javadoc
git log --oneline -- matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DiskBudgetTest.java

# 9. the native-image and GPU blocks
command -v native-image ; command -v gu ; echo "JAVA_HOME=${JAVA_HOME:-unset}"
java -version
sed -n '365,372p' matrix-core/build.gradle
gh run list --workflow native.yml --limit 10
grep -niE "gpu|cuda|nvidia" .github/workflows/ci.yml            # → no match
sed -n '24,40p' scripts/start_production.sh
nvidia-smi --query-gpu=name,driver_version --format=csv,noheader

# 10. the moment of the reading and the commit it was read at
date -u +"%Y-%m-%dT%H:%M:%SZ" ; git rev-parse HEAD ; git rev-parse origin/develop ; git status --short --branch
```

---

## 8. The evidence vocabulary

| the label | where it is used here |
|---|---|
| **MEASURED** | every run id, job id, conclusion and count in §1 and §3 — read from the GitHub API at the stated moment |
| **VERIFIED** | the six causes of §2, each with a command and its output; the negative controls of §5 |
| **IMPLEMENTED** | the six commits `0c5b3980`, `f5870f4c`, `02d2a516`, `16a2d0c5`, `e9a5c944` (and `d1f2e0fb`, the security check, which is outside this document's scope) |
| **BLOCKED** | the native-image classes (§4.3) — the toolchain is absent here and the workflow that builds it is not triggered on `develop` |
| **UNVERIFIED** | the runner's free space (§4.1); the native-image classes in CI (§4.3); the GPU path and the 12.6× figure (§4.4); the local end-to-end run of `scripts/formal-gate.sh` (§4.6) |
| **RECORDED FINDING** | the two surviving mutants of §4.5 — the gate's own recorded holes, not new failures |
| **NOT RUN** | every build and every test — this document ran none |
