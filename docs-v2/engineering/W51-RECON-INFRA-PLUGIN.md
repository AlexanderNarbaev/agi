# W51 — RECON: CI / technical debt, the plugin reality, and the canonical entry point

**Status:** read-only reconnaissance · **Date:** 2026-11-08 · **Branch:** `develop` @ `a098f283`
**Wave:** W51 · **Sections:** 8 (plugin architecture) and 13 (CI technical debt)
**Method:** `grep` / `glob` / `read` / `bash` only. No source file was modified. No test suite was run.
Two commands were executed that read the tree and produce a number (the quality gate in report
mode, and ShellCheck over the tracked `.sh` files inside a container with the repository mounted
read-only); both are recorded below with their exact invocation. The full repository test suite was
NOT run — `AGENTS.md` §Тестирование reserves it for the owner because of the OOM risk.

---

## 0. The one-paragraph answer

The five CI issues are **four STILL PRESENT and one UNCHANGED-AND-CORRECT**: SpotBugs cannot read
Java 25 bytecode (plugin `6.1.7`, engine `toolVersion = '4.9.3'`, ASM 9.7.1 → class-file major 68
vs. the project's 69); the TruffleHog step is wired but its five PostgreSQL-like "unverified"
findings are **five documentation placeholders** in `.veai/skills/**`, not secrets; ShellCheck is
still red — **106 findings across 41 tracked scripts**, measured here with ShellCheck 0.11.0; the
GraalVM native-image job still dies of `java.lang.OutOfMemoryError: Java heap space` with
`-J-Xmx8g`; and the quality gate's magic-literal baseline is **exactly 271**, with the measured
count also **271** (shell 78 + Java 193) and exit 0. **There is no plugin system in MATRIX** — no
`ServiceLoader`, no `META-INF/services`, no `ExtensionPoint`, no `org.graalvm.polyglot`, no WASM
runtime, no sandbox-capable dependency in any build file; what exists is a set of *in-process
registries* populated by hard-coded constructors, plus one genuine third-party plugin
(`matrix-spigot`, a Bukkit `JavaPlugin` for Minecraft) and two allow-list "sandboxes" that are
string filters, not isolation. **The canonical entry point is per serving application, not one
class**: `io.matrix.api.CanonicalEntryPoint` (in `matrix-core`) declares chain A's
`io.matrix.knowledge.LearningWorkbenchResource` (`/v1/workbench/**`) and chain B's
`io.matrix.api.MinimalHttpServer` (`POST /v1/analyze`); the documented run command for chain A is
`./gradlew :matrix-core:quarkusRun`.

---

# A. CI / technical debt findings

## A.1 Every file under `.github/workflows/` — seven workflows, eighteen jobs

| # | file | job (`name:`) | what the job does | triggers |
|---|---|---|---|---|
| 1 | `ci.yml` | `build-test` — "Build & Test (Java)" | checkout → JDK 25 → `compileJava compileTestJava` → `./gradlew test` → grep for forbidden LLM imports in production runtime paths → `:matrix-api-gateway:installDist` → upload JaCoCo HTML | push/PR on `develop`, `main`, `release/**`; `workflow_dispatch`; **skipped** on `schedule` (`ci.yml:28`) |
| 2 | `ci.yml` | `quality-gates` — "Quality gates (can fail)" | full-depth checkout → JDK 25 → `scripts/quality-gate.sh` → sha256-verify the tracked `tools/tla2tools.jar` → `scripts/formal-gate.sh` → `:matrix-core:quarkusBuild` → `scripts/security-boundary-check.sh`. Declares a **redis:7-alpine service** because the security-boundary check starts the real application (`ci.yml:104-113`) | same; skipped on `schedule` |
| 3 | `ci.yml` | `web-ui-tests` — "Web UI Tests (matrix-web-ui)" | Node 22 → `npm ci --legacy-peer-deps` → `npx jest --ci` in `matrix-web-ui/` | same; skipped on `schedule` |
| 4 | `ci.yml` | `container-images` — "Container Images (build + smoke)" | build 3 images (core, core-minikube, api-gateway) with Buildx, then smoke-test each: health probe of `:8765/health/ready` for the gateway, `:9091/q/health/ready` for core, `:9092` for minikube, each with its **own** Postgres 17 + Redis 7 on a dedicated docker network | needs `build-test`; skipped on `schedule` |
| 5 | `ci.yml` | `security` — "Security Scan" | full-depth checkout → JDK 25 → SpotBugs (with an explicit "known Java-25 engine limitation" branch) → **TruffleHog secret scan** → OWASP-lite dependency check (grep for `log4j-core` in the runtime classpath) | needs `build-test`; skipped on `schedule` |
| 6 | `ci.yml` | `docker` — "Docker Build & Push" | Buildx → Docker Hub login → push `api-gateway` and `web-ui` images tagged `:${sha}` and `:latest` | needs `build-test, security, container-images, web-ui-tests`; **push** only on `develop`/`main` |
| 7 | `ci.yml` | `deploy-staging` — "Deploy to Staging" | kubeconfig from secrets → `helm upgrade --install matrix-staging` → sleep 30 → `curl https://staging-api.matrix.ai/q/health` | needs `docker`; `ref == refs/heads/develop`; environment `staging` |
| 8 | `ci.yml` | `deploy-prod` — "Deploy to Production" | kubeconfig from secrets → `helm upgrade --install matrix-prod` → sleep 60 → `curl https://api.matrix.ai/q/health` | needs `docker`; `ref == refs/heads/main`; environment `production` |
| 9 | `ci.yml` | `research-suites` — "Weekly Research & Property Suites" | `:matrix-core:test` and `:matrix-brain-runtime:test` filtered to `*PropertyTest`, `*PropertyBasedVerifierTest`, `*Pbt*Test`, `*StudyTest` | **only** on the weekly `cron: "0 4 * * 1"` (`ci.yml:16`) |
| 10 | `branch-validation.yml` | `validate-branch-name` — "Validate branch name" | regex check of the branch name against `feature|fix|feat|chore|docs|test|refactor|perf|security` prefixes, or `backup-<name>-<ts>`; the two integration branches are exempt | PR to `main`/`develop`/`release/**`; the "no direct push" half runs on push |
| 11 | `branch-validation.yml` | `constitution-guard` — "CONSTITUTION compliance check" | forbid `openai|anthropic|cohere|gemini` in the T-01 modules' production sources; require a **public `String VERSION` constant** in each module's named public facade | PR + push |
| 12 | `branch-validation.yml` | `ecosystem-compile` — "Compile all T-01 modules" | GraalVM CE 25 → compile + test the five T-01 modules (`GRADLE_OPTS=-Xmx8g`) → a two-class `matrix-core` smoke test | PR + push |
| 13 | `docs-validation.yml` | `validate-docs` — "Validate ecosystem docs (RU/EN parity)" | 9 EN + 9 RU ecosystem sections exist → internal-link census → frontmatter on every ecosystem page → the CONSTITUTION's **eight** articles compared by heading and order against `docs-v2/ecosystem/constitution/index.md` and its RU twin | paths-filtered on `docs-v2/**` |
| 14 | `native.yml` | `native-build` — "GraalVM Native Build" | GraalVM 25 → Gradle cache → `:matrix-core:quarkusBuild -Dquarkus.native.enabled=true -Dquarkus.package.type=native --no-daemon -x test` → `ls`/`file` the `*-runner` → upload artifact | push/PR to `main` **only** — never on `develop` |
| 15 | `pages.yml` | `build` + `deploy` | `scripts/validate-pages-config.sh` → `scripts/docs-link-check.py --strict` → Jekyll build of `docs-v2` → upload artifact; `deploy` publishes to GitHub Pages **only when the event is not `pull_request`** | push/PR on `docs-v2/**`; `workflow_dispatch` |
| 16 | `shellcheck.yml` | `shellcheck` — "ShellCheck" | `apt-get install -y shellcheck` → `find . -name "*.sh" -exec shellcheck {} +` | push/PR to `main` **only** — never on `develop` |
| 17 | `tla.yml` | `tlc` — "Model-check TLA+ specs" | JDK 25 → sha256-verify the tracked `tools/tla2tools.jar` → **SANY** parse + semantic check of every spec → **TLC** model-check of every spec that has a configuration → the mutation harness ("can the gate still go red?") → a summary | paths-filtered: `formal/**`, the workflow file, and a **generated** list of every implementation/test the spec-implementation map names (regenerate with `./gradlew :matrix-quality:regenerateFormalPaths`) |

**Two structural facts that matter for this audit.** First, `shellcheck.yml` and `native.yml` fire
**only on `main`**, and this repository's PRs target `develop` — which is exactly why the two jobs
have been dark (see C3, C4). Second, `ci.yml` carries a global
`env: GRADLE_OPTS: "-Xmx8g -Dorg.gradle.daemon=false"` (`ci.yml:19`), but **`native.yml` does
not** — the native job inherits only the repository-wide `gradle.properties`.

---

## A.2 The five known issues, verified against the current tree

| id | the issue | **verdict** | the decisive evidence |
|---|---|---|---|
| **C1** | SpotBugs cannot analyse Java 25 bytecode (ASM too old) | **STILL PRESENT** | `matrix-core/build.gradle:4` `id 'com.github.spotbugs' version '6.1.7'`; `matrix-core/build.gradle:317` `toolVersion = '4.9.3'`; the file's own comment at `:310-316` states 4.9.3 bundles **ASM 9.7.1 → max class-file major 68 (Java 24)** against the project's **major 69 (Java 25)**. `matrix-core/build.gradle:30-31` pins `VERSION_25`. |
| **C2** | TruffleHog reported 5 unverified PostgreSQL-like findings | **STILL PRESENT (the findings), but NOT APPLICABLE as a secret leak** | The step is `ci.yml:426-429` (`trufflesecurity/trufflehog@main`, `path: ./`, `extra_args: --results=verified,unverified,unknown`); there is **no** `.trufflehog.yml` / `.trufflehogignore` anywhere in the repo. The five strings are in §A.2.2 below — all five are **documentation placeholders**. |
| **C3** | ShellCheck multiple findings | **STILL PRESENT** | `.github/workflows/shellcheck.yml` (17 lines, one job). **Measured here: 106 findings across 41 tracked `.sh` files** (1 error, 50 warnings, 55 notes) with ShellCheck 0.11.0. The repo's own record says 130 findings across ~30 scripts (`docs-v2/governance/RISK-REGISTER.md:451-457`, `docs-v2/engineering/W49-STATUS.md:243`) — the same gate, a different ShellCheck version and tree. |
| **C4** | native-image job fails from memory exhaustion on the standard runner | **STILL PRESENT** | `.github/workflows/native.yml:28-34` — the job sets **no memory limit at all**; the only ceiling that reaches native-image is `-J-Xmx8g` (`matrix-core/build.gradle:383`) together with `--gc=epsilon` (`:387`, no GC, so the 8 GiB ceiling is hard). Recorded failure: `java.lang.OutOfMemoryError: Java heap space` with `-Xmx8g` (`docs-v2/engineering/W50-STATUS.md:182`). |
| **C5** | Quality gate magic-literal baseline = 271 | **STILL PRESENT, and the baseline is correct** | `docs-v2/quality/quality-baseline.txt:1` `literal_candidates=271`; the gate script is `scripts/quality-gate.sh` (510 lines). **Measured here: 271 (shell 78 + Java 193), exit 0** — the gate is green *at* the baseline, so the debt is unchanged, not reduced. |

### A.2.1 C1 — SpotBugs in detail

* Plugin: `matrix-core/build.gradle:4` → `id 'com.github.spotbugs' version '6.1.7'`.
* Engine: `matrix-core/build.gradle:317` → `toolVersion = '4.9.3'` (the engine, not the plugin).
* Effort/report level: `Effort.DEFAULT`, `Confidence.MEDIUM` (`:301-302`); exclude filter
  `config/spotbugs/exclude.xml` (`:303`).
* Target: `matrix-core/build.gradle:30-31` → `sourceCompatibility`/`targetCompatibility` =
  `JavaVersion.VERSION_25` → class-file major **69**.
* The repo's own note (`matrix-core/build.gradle:310-316`, dated *verified 2026-10-26*): *"4.9.3
  is the newest release on Maven Central and still bundles ASM 9.7.1, which supports class file
  major version 68 (Java 24) at most."*
* CI behaviour is **not** a silent pass: `.github/workflows/ci.yml:384-406` captures the pipeline
  status with `PIPESTATUS[0]` (because the runner's `bash -e` would otherwise abort the step),
  then branches: exit 0 with a `::warning::SpotBugs SKIPPED …` notice when the log contains
  `Unsupported class file major version`, and **exit 1 for any other SpotBugs failure**. The step
  says so itself: *"No static-analysis evidence was produced for this run. This is a known,
  tracked limitation, NOT a passing check."*
* **What this means for the audit:** the *gate* is honest, the *capability* is still missing. The
  static-analysis step of the `security` job produces **no evidence** on any run.

### A.2.2 C2 — where the five PostgreSQL-like TruffleHog findings come from

**The TruffleHog configuration.** There is exactly one place: `.github/workflows/ci.yml:408-429`,
step "Check for secrets in code", `uses: trufflesecurity/trufflehog@main`, `with: path: ./` and
`extra_args: --results=verified,unverified,unknown`. `find . -iname "*trufflehog*"` over the
whole repository returns **nothing** — no `.trufflehog.yml`, no ignore file, no config of any
kind. The step's own comment records the W48 fix: the previous `base:`/`head:` pair made the
action refuse with *"BASE and HEAD commits are the same. TruffleHog won't scan anything"*, so the
scan was a no-op; the defaults now scan the commits the push carried.

**The five findings.** A repository-wide search of tracked, non-test, non-example files for
`postgres://`, `postgresql://` and `jdbc:postgresql` produces exactly **five** occurrences that
match TruffleHog's PostgreSQL URI detector shape (scheme `postgres`/`postgresql` **plus**
`user:password@host`). All five are in the agent-skill documentation directory `.veai/skills/`,
which was added by commit `233af984` ("WAL: the three phase reports of W46, and the owner's agent
configuration") and carried to `main` by the W49 PR `79045c57`:

| # | file:line | what it is | verdict |
|---|---|---|---|
| 1 | `.veai/skills/deployment-patterns/SKILL.md:340` | a twelve-factor `DATABASE_URL=postgres://<user>:<pass>@<host>:5432/<db>` example inside a bash code fence | **looks like a placeholder**: the credentials and the host are the literal words `user`, `pass`, `host`, `db`; the file is a documentation pattern template, not a deployment manifest |
| 2 | `.veai/skills/docker-patterns/SKILL.md:39` | a Docker-Compose `environment:` example, `DATABASE_URL=postgres://postgres:postgres@db:5432/app_dev` | **looks like a placeholder**: `postgres:postgres` is the Compose *default* credential for a local `db` service, the host `db` is a Compose service name that resolves only inside the example network, and the file is a documentation pattern template |
| 3 | `.veai/skills/docker-patterns/SKILL.md:162` | the same URI repeated in a "Service Discovery" example, with the comment *"`db` resolves to the db container"* | **looks like a placeholder**: same value as (2), in a doc example |
| 4 | `.veai/skills/prisma-patterns/SKILL.md:221` | `DATABASE_URL="postgresql://user:pass@host/db?connection_limit=1&pool_timeout=20"` in a `.env` example | **looks like a placeholder**: literal `user`/`pass`/`host`/`db` in a documentation snippet |
| 5 | `.veai/skills/prisma-patterns/SKILL.md:224` | the same URI with `?pgbouncer=true&connection_limit=1` | **looks like a placeholder**: literal `user`/`pass`/`host`/`db` in a documentation snippet |

**The near-misses, stated so the record is complete.** These *are* PostgreSQL URIs in tracked
production files but they do **not** carry `user:password@`, so they do not match the detector
and are not among the five:

* `docker-compose.yml:88` and `docker-compose.yml:130` — `QUARKUS_DATASOURCE_JDBC_URL:
  jdbc:postgresql://postgres:5432/matrix` (no credentials in the URL).
* `matrix-core/src/main/resources/application.properties:50` — `quarkus.datasource.reactive.url=
  postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/matrix` (the credentials are injected by
  environment variables, deliberately not in the URL).
* `infra/docker-compose.yml:8-10`, `docker-compose.dev.yml:11-13`, `docker-compose.yml:6-8` —
  `POSTGRES_PASSWORD: matrix` (the local dev password, again not in a URI).

**Verdict:** the five findings are **false positives of the scanner against documentation
placeholders**. No live secret is exposed by them. The correct remediation is *not* to rotate
anything but to give the scan a scope it cannot misread — the workflow has no allow-list
mechanism today, so the honest options are (a) exclude `.veai/skills/**` from the scan path, or
(b) rewrite the five examples to use `${POSTGRES_USER}:${POSTGRES_PASSWORD}` placeholders so the
detector stops matching them. Both are owner decisions; this audit makes no change.

### A.2.3 C3 — ShellCheck in detail

* Workflow: `.github/workflows/shellcheck.yml`, 17 lines, one job, three steps: checkout →
  `sudo apt-get install -y shellcheck` → `find . -name "*.sh" -exec shellcheck {} +`. There is **no**
  `.shellcheckrc` and **no** `.shellcheckignore` in the repository.
* **Trigger gap:** `on: push: branches: [main]` and `on: pull_request: branches: [main]` — the
  workflow has never run on `develop`, where this repository's work happens.
* **Script count.** `git ls-files "*.sh"` → **63 tracked shell scripts**. `find . -name "*.sh"`
  (what the workflow actually runs) → **72** on a working tree that also contains a `.venv`; on a
  fresh CI checkout the count is the tracked 63.
* **Measured findings (this audit, ShellCheck 0.11.0, over the 63 tracked files):**
  ```
  docker run --rm -v "$PWD:/mnt:ro" -w /mnt koalaman/shellcheck:stable \
      $(git ls-files "*.sh")
  ```
  → **106 findings across 41 files** — 1 `error`, 50 `warning`, 55 `note`.
  By code: SC2086 ×18, SC2155 ×16, SC2034 ×13, SC2012 ×12, SC2329 ×7, SC2164 ×7, SC2064 ×7,
  SC2181 ×3, SC2015 ×3, SC2010 ×3, then 14 codes with 1–2 each.
  The single `error` is `scripts/diagnose-cuda.sh:105:3: 'local' is only valid in functions.
  [SC2168]`.
* **The repo's own record:** `docs-v2/governance/RISK-REGISTER.md:451-457` (R-W49-03) — *"The
  ShellCheck workflow has been red since at least 2026-10-31 … **130 findings across ~30 inherited
  scripts** (SC2086, SC2155, SC2164, SC2034 and the like). None of the findings is in this wave's
  scripts."* and `docs-v2/engineering/W50-STATUS.md:159-166,182` — the two scripts W50 added
  (`scripts/run-local.sh`, `scripts/offline-answer-proof.sh`) are clean under ShellCheck 0.10.0,
  but *"the inherited findings"* keep the job red.
* **Version caveat:** CI installs ShellCheck from Ubuntu's apt repository (0.9.x/0.10.x depending
  on the runner image); this measurement used 0.11.0 in a container. The finding *count* is
  therefore not directly comparable with the recorded 130 — the **gate is red either way**, and
  the direction of the change (fewer findings, same failing job) is what matters.

### A.2.4 C4 — the native-image OOM in detail

* Job: `.github/workflows/native.yml`, job `native-build`, 46 lines.
* Build step (`native.yml:28-34`): `./gradlew :matrix-core:quarkusBuild -Dquarkus.native.enabled=true
  -Dquarkus.package.type=native --no-daemon -x test`.
* **Memory settings that actually reach the build:**

  | setting | where | value |
  |---|---|---|
  | the native-image driver's own JVM heap | `matrix-core/build.gradle:383` (`graalvmNative.buildArgs`) | `-J-Xmx8g` |
  | the GC | `matrix-core/build.gradle:387` | `--gc=epsilon` — *epsilon GC collects nothing*, so the 8 GiB ceiling is a hard wall, not a soft one |
  | the Gradle daemon heap | `gradle.properties:3` | `org.gradle.jvmargs=-Xmx4g` |
  | the workflow's own `GRADLE_OPTS` | — | **absent from `native.yml`**; `ci.yml:19` sets `-Xmx8g` but `native.yml` is a separate workflow with its own environment |
  | `native-image.properties` `Args` | `matrix-core/src/main/resources/META-INF/native-image/native-image.properties` | init-at-build/run-time flags only — **no `-J-Xmx`** |
  | `Dockerfile.native` | — | builds inside `quay.io/quarkus/ubi-quarkus-mandrel-builder:jdk-25` with **no memory limit** either |

* **The recorded failure.** `docs-v2/engineering/W50-STATUS.md:182` — the `native-build` job of
  the W50 pull request: **fail**, *"`java.lang.OutOfMemoryError: Java heap space` with `-Xmx8g` —
  the inherited memory blocker, not a W50 regression."* The same failure is recorded in
  `docs-v2/architecture/CAPABILITY-REGISTRY.yml:1459` (run `41219224376`, 2026-10-31: the
  toolchain installed successfully — *"Successfully set up graalvm-jdk-25.0.4+7.1"* — and then
  *"died with 'Terminating due to java.lang.OutOfMemoryError: Java heap space', BUILD FAILED in
  3m 27s"*) and in `docs-v2/engineering/TEST-CATALOGUE.yml:242-248`.
* **Why 8 GiB is not enough.** `docs-v2/operations/NATIVE-IMAGE-DEEP-RESEARCH.md` §3.1 measures
  the two phases: the *analysis* phase (reachability, reflection, JNI) exhausts ~5–7 GB, and the
  *final C-link* phase (LLVM bitcode → ELF) needs an additional **8–12 GB peak**. §4.2 and §4.4
  conclude that the fix is *"hardware with ≥16GB RAM"* or a dedicated build machine.
* **The consequence that is recorded as a capability gap.** `CAPABILITY-REGISTRY.yml:1461` —
  *"native.yml has 1403 runs on record, and its last 100 are 99 failures and 1 cancelled"*; and
  `:1463` — *"the native.yml job dies of OOM after the toolchain is installed, so the block is a
  RESOURCE question as well as a toolchain question, and installing the toolchain alone would not
  close it."* The 25 native-binary test methods have therefore never been executed against a real
  binary on any host.
* **Two structural defects in the job itself**, both still present:
  1. `native.yml:3-7` — the job triggers on `main` only, so **`develop` never builds a native
     image** and the capability cannot be verified on the branch where work happens.
  2. `native.yml:38-39` — the "Verify Native Binary" step expects `matrix-core/build/*-runner`,
     but the Quarkus native build produces `matrix-core/build/native/nativeCompile/matrix-core`
     (the path the registry and the test catalogue both name). The step's glob therefore matches
     nothing even if the build succeeds.

### A.2.5 C5 — the magic-literal baseline in detail

* Gate script: `scripts/quality-gate.sh` (510 lines). Its rules are MAGIC-1 (numeric literals in
  shell scripts), MAGIC-2 (numeric literals in production Java), CFG-1, FROZEN-1, DOCS-1, TEST-1,
  RETENTION-1 and SNAPSHOT-RETENTION-1 (`scripts/quality-gate.sh:10-22`).
* Baseline file: `docs-v2/quality/quality-baseline.txt`, line 1 → **`literal_candidates=271`**.
  The header comment (`:3`) records `updated: 2026-11-02T18:20:00+0300 ref=b303f22e`.
* The frozen-zone reference is separate and unchanged: `scripts/quality-gate.sh:45`
  `readonly BASELINE_REF="b144563e"`.
* **Measured here (read-only, no `--update-baseline`):**
  ```
  bash scripts/quality-gate.sh
  → MAGIC-1 shell literal candidates: 78
  → MAGIC-2 java  literal candidates: 193
  → PASS: 271 candidate(s), at or below the accepted baseline of 271; 0 other violation(s).
  → exit 0
  ```
  Every other rule also passed on this tree: `CFG-1 matrix.env: 14 assignments, 5 provenance tags`;
  `FROZEN-1 all 5 frozen zones within their recorded exceptions vs b144563e`; `DOCS-1 the
  documentation links resolve`; `TEST-1 the test catalogue agrees with the evidence`;
  `RETENTION-1 the retention policy holds`; `SNAPSHOT-RETENTION-1 the snapshot retention policy
  holds, the bound is configured, and the dry run changes nothing`.
* **The debt is unchanged, not repaid.** The gate *blocks* on an increase and never on a
  decrease, so a gate at exactly 271 means "no new literal since 2026-11-02". The repo says the
  same thing in prose: `docs-v2/engineering/W42-RISK-REGISTER.md:80` (R28) records that the
  owner's decision D-015 is **not** to raise the baseline but to extract the literals, and that
  *"the gate is still RED — R27 stays open"*. The 341 candidates over 299 lines of production Java
  in 167 files are still to be extracted, file by file.

---

## A.3 The local commands, as the repository's own scripts and docs state them

### A.3.1 The quality gate and the other gates

| gate | the exact command | the source |
|---|---|---|
| the clean-code (magic-literal) gate | `bash scripts/quality-gate.sh` | `README.md:444`, `README.ru.md`, `docs-v2/architecture/RUNTIME-TOPOLOGY.md:133` |
| — report only, always exit 0 | `bash scripts/quality-gate.sh --report` | `scripts/quality-gate.sh:29` |
| — accept the current state as the baseline (**writes** `docs-v2/quality/quality-baseline.txt`) | `bash scripts/quality-gate.sh --update-baseline` | `scripts/quality-gate.sh:30` |
| the security boundary | `bash scripts/security-boundary-check.sh` | `README.md:234,445` |
| the chain isolation | `bash scripts/w45-chain-isolation-check.sh` | `README.md:235,446` |
| its negative control | `bash scripts/w45-chain-isolation-selftest.sh` | `README.md:236` |
| the crash-recovery journey (real `SIGKILL`s) | `bash scripts/w45-cognitive-recovery-journey.sh` | `README.md:237,447` |
| the formal gate (TLA+, pinned tool) | `bash scripts/formal-gate.sh` | `.github/workflows/ci.yml:150`, `README.md:260` |
| the pages configuration | `bash scripts/validate-pages-config.sh` | `.github/workflows/pages.yml:40` |
| the documentation links | `python3 scripts/docs-link-check.py --root . --strict` | `.github/workflows/pages.yml:43` |
| the fresh-clone smoke (build → launch → query → clean up) | `bash scripts/fresh-clone-smoke.sh` | `README.md:432` |

**Memory safety of the gates.** All of the above are shell/Python and are **safe to run locally
without OOM risk**: the heaviest of them (`quality-gate.sh`) only greps and AWKs the tree, and its
snapshot-retention check explicitly verifies that the dry run leaves the owner's SQLite database
byte-identical (`scripts/quality-gate.sh`, the `before`/`after` `sha256sum` comparison).
`w45-cognitive-recovery-journey.sh` launches the application and `SIGKILL`s it 37 times — that is
process churn, not heap pressure, but it does bind port 9091 and write into `data/`.

### A.3.2 The tests

`README.md:439-441` documents exactly three **targeted** scopes, each with its own commit in
`docs-v2/engineering/TEST-CATALOGUE.yml`:

```bash
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks
./gradlew :matrix-brain-runtime:test --rerun-tasks
./gradlew :matrix-api-gateway:test --rerun-tasks
```

The weekly research/property suites (the `research-suites` job) are:

```bash
./gradlew :matrix-core:test --no-daemon \
  --tests "io.matrix.**.*PropertyTest" \
  --tests "io.matrix.**.*PropertyBasedVerifierTest" \
  --tests "io.matrix.**.*StudyTest"
./gradlew :matrix-brain-runtime:test --no-daemon \
  --tests "io.matrix.**.*PropertyTest" \
  --tests "io.matrix.**.*Pbt*Test" \
  --tests "io.matrix.**.*StudyTest"
```

`AGENTS.md` §Тестирование gives the general form: `./gradlew :matrix-core:test --tests
"io.matrix.<pkg>.*"`.

**The OOM boundary — what is safe and what is not.**

| command | safe locally? | why |
|---|---|---|
| `bash scripts/quality-gate.sh` and every other gate script | **YES** | shell/Python only; no JVM heap |
| `./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*"` | **YES** (the documented targeted scope) | the module's test task still *declares* `maxHeapSize = '16g'` / `-Xmx16g` (`matrix-core/build.gradle:119,121`), so it **reserves** the ceiling but a 969-method scope does not fill it |
| `./gradlew :matrix-brain-runtime:test` | **YES** | 919–120 classes; measured green at `af9ba688` (`README.md:206`) |
| `./gradlew :matrix-api-gateway:test` | **YES** | 43 classes / 240 methods; measured green at `a6ded233` |
| the `--tests "io.matrix.**.*PropertyTest"` research suites | **YES** | the same filter CI runs weekly (`ci.yml:557-567`) |
| `./gradlew :matrix-core:quarkusBuild` (JVM fast-jar) | **YES**, but it is a build, not a test | needs a JDK 25; `gradle.properties` gives the daemon 4 GiB |
| **the full `./gradlew test` for the whole repository** | **NO — owner's agreement required** | `README.md:450` — *"the `matrix-core` test JVM asks for **16 GiB**, and two concurrent runs do not fit in the machine's available memory"*; `AGENTS.md` §Тестирование — «Полный прогон — только по согласованию (риск OOM)»; `docs-v2/operations/RESOURCE-BUDGETS.md` tabulates the limits |
| the native-image build (`:matrix-core:quarkusBuild -Dquarkus.native.enabled=true` / `:matrix-core:buildNative`) | **NO — it is the C4 blocker** | needs ≥16 GB of RAM for the LLVM C-link phase; on this host it is the known OOM |

**The other CI-adjacent commands.** `./gradlew :matrix-core:nativeBuildConfigTest` (1 class / 2
tests, green at `31d8f832`), `./gradlew :matrix-core:nativeBinaryTest` (8 classes / 25 methods —
**BLOCKED**, it refuses to run because the executable does not exist),
`npx jest --ci` inside `matrix-web-ui/` (needs `npm ci --legacy-peer-deps`), and
`bash scripts/formal-gate.sh` (model-checks all 15 specifications with the tracked
`tools/tla2tools.jar`, whose digest the gate and CI both verify).

---

# B. The plugin / extension mechanism — a reality check

## B.1 What was searched, and where

Whole repository, all modules, not just `matrix-core`:

`git grep` over `*.java`, `*.gradle`, `*.kts`, `*.toml`, `*.properties`, `*.yml`, `*.yaml`,
`*.json`, `*.md` for: `Plugin`, `plugin`, `ServiceLoader`, `SPI`, `ExtensionPoint`, `Capability`,
`sandbox`, `WASM`, `WASI`, `graaljs`, `polyglot`, plus `META-INF/services`, `Class.forName`,
`loadClass`, `newInstance`.

Also: `git ls-files` for every file whose path contains `plugin`; every file matching
`*Plugin*`; every build file in the repository (16 of them) read for sandbox-capable
dependencies.

## B.2 The hits, classified

### B.2.1 **Real plugin runtime — none**

There is **no** plugin runtime in MATRIX. The evidence is negative and complete:

* **`ServiceLoader`**: the only hits in the whole repository are (i) a comment in the archived
  prototype `docs-v2/_raw/2026-08-pre-v2/research/research/prototype-java/src/matrix/io/ModuleRegistry.java:12`
  — *"никакого ServiceLoader/classpath-сканирования"* — and (ii) a comment in
  `matrix-brain-runtime/build.gradle:34-35` stating that the module has *"no ServiceLoader or
  Class.forName lookup against them, and no META-INF/services entry"*. **No production code calls
  `ServiceLoader`.**
* **`META-INF/services`**: `git ls-files | grep META-INF/services` → **empty**. There is not one
  provider declaration file in the repository.
* **`ExtensionPoint`**: **zero** hits anywhere, in code or in docs.
* **Classpath scanning for extensions**: the only `Class.forName` / `newInstance` calls in
  production are three hard-coded string lookups in
  `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:151-165`
  (`io.matrix.brain.BirBrainCycle`, `io.matrix.knowledge.SimpleKnowledgeBase`,
  `io.matrix.learning.ConversationLearner`) — these exist to break a **module dependency cycle**
  (the gateway must not depend on `matrix-core` at compile time), not to discover extensions.
  `matrix-core/src/main/java/io/matrix/federation/gpu/MatrixNativeMath.java:64` does the same for
  `jdk.incubator.vector.LongVector` (a JDK-internal capability probe).
* **No plugin manifest, no plugin descriptor, no plugin directory.** The repository's own
  architectural statement is unambiguous: `docs-v2/architecture/MATRIX-COGNITIVE-KERNEL.md:152` —
  *"The kernel is a plain object with constructor arguments. It is not a bean, it has no plugin
  discovery."* (repeated at `docs-v2/designs/DESIGN-72-cognitive-kernel.md:33`).

### B.2.2 **In-process registries (the closest thing that exists) — real code, but NOT a plugin runtime**

These are genuine extension points in the *type* sense — an interface, a registry, a
`register()` call — but every one of them is **populated by a hard-coded constructor inside the
same compilation unit**, and none of them can be extended from outside the JAR.

| registry | file | how it is populated | verdict |
|---|---|---|---|
| `ActionRegistry` | `matrix-core/src/main/java/io/matrix/actions/ActionRegistry.java:22-49` | `register(Action)` into a `ConcurrentHashMap`; the actions are constructed by the caller in the same module | **in-process registry — not a plugin runtime** |
| `AlgorithmRegistry` | `matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmRegistry.java:29-59` | `register(AlgorithmMethod)`; each method declares an `AlgorithmCapability`; a duplicate id is refused | **in-process registry — not a plugin runtime**. Its own javadoc (`:15-25`) says the point is that a selector chooses *"from the declarations rather than from a caller's memory"* — a selection mechanism, not an extension mechanism |
| `SignalModuleRegistry` | `matrix-core/src/main/java/io/matrix/signals/SignalModuleRegistry.java:12-55` | `register(SignalModule)` keyed by `modality@version` | **in-process registry — not a plugin runtime** |
| `DocumentExtractorRegistry` | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentExtractorRegistry.java:14-36` | seven extractors constructed as `private final` fields and switched over `DocumentFormat` | **a switch statement in class form — not even a registry in the extensible sense** |
| `MatrixMcpServer` | `matrix-core/src/main/java/io/matrix/mcp/MatrixMcpServer.java:104-105, 238-320` | `registerTool(McpTool)`; `createDefaultServer()` registers the MATRIX tools by hand | **real, but the wrong direction**: this exposes MATRIX's capabilities to *external* MCP clients; it is not a way to extend MATRIX |
| `io.matrix.tools.Tool` + `ToolsResource` | `matrix-core/src/main/java/io/matrix/tools/Tool.java`, `.../tools/ToolsResource.java` | the tool contract (name / description / `execute(Map)`); the resource dispatches by name | **a fixed tool set behind an interface — not a plugin runtime** |
| `GateOrchestrator` | `matrix-quality/src/main/java/io/matrix/quality/gates/GateOrchestrator.java:21-40` | twelve gates added in the constructor, plus a final auditor and a quality gate | **a hard-coded list — not a plugin runtime** |
| `FNLLoader` | `matrix-core/src/main/java/io/matrix/cluster/FNLLoader.java:17-60` | loads a **`.ldn` snapshot JSON file** describing a functional neural layer | see B.2.3 |

### B.2.3 **Configuration-file "plugins" — two, and both are data, not code**

| what | file | verdict |
|---|---|---|
| `.ldn` FNL snapshot files | `matrix-core/src/main/java/io/matrix/cluster/FNLLoader.java:50-60` — parses the file as a `ClusterSnapshot` and extracts neuron count and timestamp; the FNL name is the filename | **configuration-file plugin**: a data file that parameterises a runtime behaviour. It carries **no executable content** — the code path is fixed in Java |
| Bukkit/Paper plugin descriptor | `matrix-spigot/src/main/resources/plugin.yml` and `matrix-spigot/src/main/resources/paper-plugin.yml` (`main: io.matrix.spigot.MatrixPlugin`, `api-version: 1.20`, `bootstrapper`/`loader` in the Paper variant, an optional `server: matrix-core: required: false` dependency) | **configuration-file plugin — and a REAL one, but for a third-party platform.** `matrix-spigot/src/main/java/io/matrix/spigot/MatrixPlugin.java:42` is a genuine `extends JavaPlugin`. This is MATRIX integrating *into* Minecraft's plugin system, not MATRIX having one. The repo notes its independence: *"an **independent toolchain: Java 21**, against 25 everywhere else"* (`docs-v2/ecosystem/architecture/index.md:34`) |

### B.2.4 **Naming-only hits**

| hit | file:line | why it is naming-only |
|---|---|---|
| `SpigotBridge` javadoc | `matrix-core/src/main/java/io/matrix/agent/SpigotBridge.java:8` — *"Java Plugin for Minecraft Spigot/Paper servers"* | the class is a **TCP/WebSocket client** that talks to the Spigot plugin; it is not a plugin and does not implement any plugin interface |
| `Capability` as a word | `matrix-core/src/main/java/io/matrix/brain/runtime/actuation/DeviceAdapter.java:34`, `io/matrix/federation/liquid/CapabilityConsensusEngine.java`, `io/matrix/federation/liquid/SybilResistance.java`, `io/matrix/neuron/ConsciousBrain.java:19`, plus the protobuf field `Capability` in `matrix-core/src/generated/java/io/matrix/federation/proto/MatrixFederationV1.java:510,528` | "capability" here means a **cognitive level (L0–L7)**, a **device ability**, or a **federation node's advertised capability** in a protobuf schema — none of them is an extension point |
| `Plugin` in the Quarkus sense | `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md:51,58`, `RECON-W44-ARCHAEOLOGY.md:178,408`, `W42-RECONNAISSANCE.md:73` | these all say *"the module applies **no** Quarkus plugin"* — the word is used to describe what is **absent** |
| `Plugin` in `matrix-quality` | `matrix-quality/src/main/java/io/matrix/quality/gates/GateOrchestrator.java:16-18` — *"the 14 Goal Guard **plugin**"* | refers to an external tool concept, not to code in this repository |
| `.opencode/**` | 105 tracked files under `.opencode/` | these are **agent tooling** (opencode-swarm skills and configuration), not MATRIX runtime code; they are not on the Gradle classpath |
| `.veai/**` | 30 tracked files under `.veai/` | the same: the owner's agent configuration, tracked in git (and the source of the C2 findings) |

### B.2.5 **"Sandbox" — two string filters, and no isolation at all**

| what | file:line | what it actually does |
|---|---|---|
| the shell executor | `matrix-core/src/main/java/io/matrix/tools/ToolsResource.java:173-197` (the allow-list itself at `:176`) | **an allow-list of nine command names** (`ls`, `cat`, `echo`, `date`, `pwd`, `whoami`, `df`, `du`, `free`) checked against the *first word only*, then runs `bash -c <the whole command>` | **not a sandbox.** The check is a prefix match on the first token; `bash -c` still interprets the rest of the string, and the process is not isolated in any way |
| the calculator | `matrix-core/src/main/java/io/matrix/tools/ToolsResource.java:198-210` | a regex allow-list `[0-9+\-*/().\s]+` plus a hand-written recursive-descent parser. The comment at `:205` records the reason: *"JavaScript engine removed from JDK 15+"* — there is **no JS engine** anywhere in the tree |
| the file reader/writer | `matrix-core/src/main/java/io/matrix/tools/ToolsResource.java:149-172` | rejects `..`, `/etc`, `/proc` — a path filter, not a jail |
| the "sandbox" in the cognitive architecture | `io.matrix.cognitive.microworld.MicroWorldSession` / `Experiment` | a **model** of a bounded world (a lamp on/off micro-world) that a probe acts inside; *"Nothing outside the sandbox is touched"* (`docs-v2/architecture/MATRIX-COGNITIVE-KERNEL.md:98`) — a property of the experiment, not an OS-level isolation |
| `/v1/sandbox` | `matrix-core/src/main/java/io/matrix/api/SandboxResource.java:48-180` | an **interactive chat/explain/inspect/topology REST surface** ("Interactive sandbox (Wave M)") — a name for a demo endpoint, not a security boundary |
| `MaturityLevel.MA_0_SANDBOX` | `matrix-core/src/main/java/io/matrix/devloop/MaturityLevel.java:22` | a maturity label, not a mechanism |

### B.2.6 **Absent**

| the thing | the search | the result |
|---|---|---|
| `org.graalvm.polyglot` / GraalJS / any polyglot runtime | `git grep` over every build file + all Java | **zero** hits in code or in build files. `graalvm` appears in exactly three places, all about **native-image packaging**: `matrix-core/build.gradle:7` `id 'org.graalvm.buildtools.native' version '1.1.10'`, `matrix-core/build.gradle:371` the `graalvmNative` block, and `docs-v2/engineering/STANDARDS-MATRIX.md:20` (*"GraalVM plugin \| 1.1.10"*) |
| a WASM / WASI runtime | `git grep -iE "\bwasm\b\|\bwasi\b"` over Java/build files | **zero** hits. The only WASM in the tree is **indirect npm transitive dependency** in `matrix-web-ui/package-lock.json` (`@napi-rs/wasm-runtime`, `@emnapi/wasi-threads`, `@tybys/wasm-util`, `@unrs/resolver-binding-wasm32-wasi`) — these are npm's own optional binaries for native Node addons, not a WASM runtime MATRIX loads |
| WASM as a *plan* | `docs-v2/levels/L15-LearningProgram.md:43,93`, `docs-v2/paradigm/REQUIREMENTS.md:169` (R14.5 "WASM Playground"), `docs-v2/paradigm/TRACEABILITY.md:172` (**❌ not implemented**) | documented roadmap only |
| Docker / chroot / jail isolation as a *dependency* | `git grep -iE "docker\|chroot\|jail\|containerd"` over every `*.gradle`/`*.kts` | **zero** hits in any build file. Docker appears only in `Dockerfile*`, `docker-compose*.yml` (packaging) and in `scripts/*.sh` (operational helpers) |
| `ServiceLoader` / SPI in production | see B.2.1 | **zero** |
| `ExtensionPoint` | see B.2.1 | **zero** |

### B.2.7 **Sandbox-capable dependencies in the build files — the complete answer**

Every dependency declaration in the 16 build files, filtered for anything that could isolate or
execute untrusted code:

| dependency | version | where | sandbox-capable? |
|---|---|---|---|
| `org.graalvm.buildtools.native` (Gradle plugin) | **1.1.10** | `matrix-core/build.gradle:7` | **No.** It is the native-image **packaging** plugin; it has no isolation semantics |
| `io.quarkus` (Gradle plugin) | **3.38.3** | `matrix-core/build.gradle:5` | **No.** CDI/JAX-RS runtime, not an isolation boundary |
| `io.quarkus.platform:quarkus-bom` | **3.38.3** | `matrix-core/build.gradle:39` | No |
| `io.quarkus:quarkus-reactive-pg-client`, `quarkus-jdbc-postgresql` | 3.38.3 (BOM) | `matrix-core/build.gradle:55-56` | No |
| `org.xerial:sqlite-jdbc` | **3.43.0.0** | `matrix-core/build.gradle:57` | No — embedded file DB |
| `org.tukaani:xz` | **1.9** | `matrix-core/build.gradle:43` | No |
| `joda-time:joda-time` | **2.12.7** | `matrix-core/build.gradle:53` | No |
| `me.champeau.jmh` (Gradle plugin) | **0.7.3** | `matrix-core/build.gradle:6` | No — benchmark harness |
| `com.github.spotbugs` (Gradle plugin) | **6.1.7** | `matrix-core/build.gradle:4` | No — static analysis |
| `io.quarkus:quarkus-arc` / `quarkus-smallrye-health` / `quarkus-rest` / `quarkus-rest-jackson` / `quarkus-websockets` / `quarkus-opentelemetry` / `quarkus-logging-json` / `quarkus-micrometer-registry-prometheus` / `quarkus-hibernate-validator` / `quarkus-picocli` | 3.38.3 (BOM) | `matrix-core/build.gradle:44-52` | No |

**There is no `org.graalvm.polyglot`, no GraalJS, no WASM/WASI runtime, no container/chroot
library, and no security-manager or sandbox library anywhere in the repository's dependency
graph.** The five other modules (`matrix-api-gateway`, `matrix-audit`, `matrix-billing`,
`matrix-observability`, `matrix-sdk-java`) and the three `pilots/*` modules declare ordinary
HTTP/JDBC/JWT/Stripe libraries and nothing that could host an untrusted extension.

## B.3 The verdict, in one paragraph

**MATRIX has no plugin architecture, and this is not a gap in the implementation — it is a
decision the codebase has made explicitly and repeatedly.** There is no `ServiceLoader` call, no
`META-INF/services` file, no `ExtensionPoint`, no SPI, no classpath scanning, and no
sandbox-capable dependency of any kind (no `org.graalvm.polyglot`, no WASM/WASI runtime, no
chroot/container isolation) in any of the 16 build files. What *does* exist is a family of
**in-process registries** — `ActionRegistry`, `AlgorithmRegistry`, `SignalModuleRegistry`,
`DocumentExtractorRegistry`, the `Tool`/`ToolsResource` contract, and `GateOrchestrator` — each a
real interface plus a registry, but each populated by hard-coded constructor calls inside its own
module, so none of them can be extended from outside the JAR; a **configuration-file plugin** in
the data-only sense (the `.ldn` FNL snapshot files read by `FNLLoader`, and the Bukkit/Paper
`plugin.yml`/`paper-plugin.yml` descriptors that make `matrix-spigot` a genuine — but
third-party-platform — plugin); two `sandbox`-named mechanisms that are **string filters, not
isolation** (a nine-command allow-list in front of `bash -c` in `ToolsResource`, and a regex
allow-list in front of a hand-written arithmetic parser); and a large amount of **naming-only**
hits (the word `Capability` means a cognitive level, a device ability or a protobuf field;
`SpigotBridge` is a client, not a plugin; every reference to a "Quarkus plugin" in the docs says
that the module applies **no** such plugin). The repository's own architectural documents say so
in as many words: the cognitive kernel *"is not a bean, it has no plugin discovery"*
(`docs-v2/architecture/MATRIX-COGNITIVE-KERNEL.md:152`) and *"it is not a framework and it has no
plugin mechanism"* (`docs-v2/engineering/W44-CLASS-INVENTORY.csv:1122`). **Consequence for
section 8 of W51:** if the wave's intent is to *add* a plugin architecture, this audit establishes
that nothing exists to extend, that the five "registry" classes are the natural seed points
(each already has an interface, a `register()` and a duplicate-refusal rule), and that the
isolation requirement is the part with **no** substrate at all — a `ServiceLoader`-style
discovery plus a real isolation boundary (a separate process, a container, or GraalVM polyglot)
would both be net-new. If the wave's intent is to *describe* what exists, the description is
"none, deliberately".

---

# C. Canonical entry points

## C.1 `public static void main` in `matrix-core/src/main/java` — 61 entry points

| # | class (`file:line`) | one-line purpose |
|---|---|---|
| 1 | `io.matrix.MatrixApplication` (`matrix-core/src/main/java/io/matrix/MatrixApplication.java:30`) | **the `@QuarkusMain` entry of the epistemic application** — prepares the scratch dir and calls `Quarkus.run(...)`; also the `mainClass` of the native binary (`matrix-core/build.gradle:375`) |
| 2 | `io.matrix.MatrixSimulation` (`.../MatrixSimulation.java:25`) | Phase-0 CLI: GridWorld agent training via a genetic algorithm |
| 3 | `io.matrix.MinecraftExperiment` (`.../MinecraftExperiment.java:23`) | the Minecraft survival experiment — neural-network evolution in the Minecraft-like sandbox |
| 4 | `io.matrix.SystemDemo` (`.../SystemDemo.java:67`) | a system-wide demo exercising all MATRIX components end-to-end |
| 5 | `io.matrix.brain.BirBrainCycle` (`.../brain/BirBrainCycle.java:330`) | runs the BIR-based brain cycle standalone (the specialist loop of `ADR-W45.2-01`) |
| 6 | `io.matrix.brain.BrainTelemetry` (`.../brain/BrainTelemetry.java:116`) | the brain telemetry endpoint / runner |
| 7 | `io.matrix.brain.LearningMemory` (`.../brain/LearningMemory.java:215`) | the learning memory that stores facts learned from conversations |
| 8 | `io.matrix.consciousness.BrainLoopDemo` (`.../consciousness/BrainLoopDemo.java:21`) | CLI runner for the RUN-158 `BrainLoopService` loop (a research/unserved cluster) |
| 9 | `io.matrix.federation.liquid.benchmark.BenchmarkRunner` (`.../federation/liquid/benchmark/BenchmarkRunner.java:153`) | the liquid-federation benchmark runner |
| 10 | `io.matrix.federation.liquid.simulation.FederationSimulation` (`.../federation/liquid/simulation/FederationSimulation.java:251`) | the large-scale federation simulation |
| 11 | `io.matrix.federation.liquid.simulation.SleepConsolidationStudy` (`.../federation/liquid/simulation/SleepConsolidationStudy.java:172`) | the sleep-consolidation study |
| 12 | `io.matrix.knowledge.SimpleKnowledgeBase` (`.../knowledge/SimpleKnowledgeBase.java:163`) | the simple RAG knowledge base |
| 13 | `io.matrix.learning.ConversationLearner` (`.../learning/ConversationLearner.java:106`) | the conversation learner |
| 14 | `io.matrix.cli.RealConversationCli` (`.../cli/RealConversationCli.java:39`) | real-conversation CLI with multi-session continuity |
| 15 | `io.matrix.cli.RealConversationServer` (`.../cli/RealConversationServer.java:50`) | the real-conversation HTTP server |
| 16 | `io.matrix.cli.RealConversationReplay` (`.../cli/RealConversationReplay.java:23`) | the real-conversation replay CLI |
| 17 | `io.matrix.cli.ConversationStreamServer` (`.../cli/ConversationStreamServer.java:40`) | the streaming chat server (SSE) |
| 18 | `io.matrix.cli.ModelDownloader` (`.../cli/ModelDownloader.java:28`) | the model downloader CLI |
| 19 | `io.matrix.cli.NdjsonToTraining` (`.../cli/NdjsonToTraining.java:27`) | converts NDJSON conversation logs into training pairs |
| 20 | `io.matrix.cli.ConversationSearch` (`.../cli/ConversationSearch.java:23`) | conversation search |
| 21 | `io.matrix.cli.ConversationDelete` (`.../cli/ConversationDelete.java:18`) | conversation delete |
| 22 | `io.matrix.cli.ConversationMerge` (`.../cli/ConversationMerge.java:24`) | conversation merge |
| 23 | `io.matrix.cli.ConversationExport` (`.../cli/ConversationExport.java:27`) | conversation export |
| 24 | `io.matrix.cli.ConversationHtmlExport` (`.../cli/ConversationHtmlExport.java:25`) | conversation HTML export |
| 25 | `io.matrix.cli.ConversationToSql` (`.../cli/ConversationToSql.java:29`) | conversation → SQL |
| 26 | `io.matrix.cli.ConversationImport`-family: `ConversationArchive` (`:31`), `ConversationBackup` (`:21`), `ConversationRestore` (`:22`), `ConversationBatch` (`:29`) | archive / backup / restore / batch operations on the conversation store |
| 27 | `io.matrix.cli.ConversationBenchmark` (`:26`), `ConversationModelEval` (`:26`), `ConversationModelTest` (`:18`), `ConversationPerplexity` (`:23`) | the measurement CLIs: benchmark, model evaluation, model test, perplexity |
| 28 | `io.matrix.cli.ConversationBook*`: `ConversationBookmark` (`:28`), `ConversationCompact` (`:23`), `ConversationCount` (`:19`), `ConversationDedup` (`:27`), `ConversationDiff` (`:22`), `ConversationDocker` (`:30`), `ConversationEdgeTest` (`:40`), `ConversationExtract` (`:24`), `ConversationFavorite` (`:26`), `ConversationFind` (`:24`), `ConversationGenerate` (`:53`), `ConversationHead` (`:20`), `ConversationKnowledgeAdder` (`:21`), `ConversationListNamed` (`:21`), `ConversationMonitor` (`:22`), `ConversationName` (`:25`), `ConversationPatterns` (`:25`), `ConversationPrompt` (`:18`), `ConversationReport` (`:30`), `ConversationShare` (`:26`), `ConversationStats` (`:25`), `ConversationSummarize` (`:20`), `ConversationSummary` (`:25`), `ConversationTag` (`:27`), `ConversationTail` (`:22`), `ConversationTimeline` (`:34`), `ConversationValidate` (`:19`) | the remaining 27 single-purpose conversation-store CLIs (each is a one-line command over the same store: `.../cli/<Name>.java:<line>`) |

**Row 27/28 line references** are `matrix-core/src/main/java/io/matrix/cli/<Class>.java:<line>`.
The `io.matrix.cli` package alone accounts for **48** of the 61 entry points.

## C.2 `public static void main` in `matrix-brain-runtime/src/main/java` — 3 entry points

| # | class (`file:line`) | one-line purpose |
|---|---|---|
| 1 | `io.matrix.brain.runtime.MindRuntimeCli` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindRuntimeCli.java:37`) | a tiny REPL-style CLI to exercise the cognitive conductor (`TrueMindCycle`) |
| 2 | `io.matrix.brain.runtime.BenchmarkRunner` (`.../brain/runtime/BenchmarkRunner.java:205`) | the TRUE-W13 benchmark runner for the brain runtime |
| 3 | `io.matrix.brain.runtime.KnowledgeForgeIngest` (`.../brain/runtime/KnowledgeForgeIngest.java:169`) | a committed, re-runnable ingest for the knowledge-forge corpus |

*(For completeness, outside the two modules the task names: `matrix-api-gateway` has **13**
`main`s — `MinimalHttpServer:1699` plus 12 in `io.matrix.api.llm_quarantine` (quarantined) —
`matrix-quality` has `QualityCli:21` and `matrix-tools-distill` has `DistillCli:62`.)*

## C.3 Which one the repository's own docs call canonical

**The repository does not name a single canonical entry point — it names one *per serving
application*, and that is a deliberate owner decision.**

`docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` (status: **accepted**, 2026-11-04,
wave W45.2, owner decision) says in §2:

> *"The canonical entry point is named per serving application. The cycles are kept, with their
> distinct responsibilities. Nothing is merged, and no global conductor is declared."*

Its §2.1 table:

| the application | the canonical entry point | the routes | the cycles it runs |
|---|---|---|---|
| **the epistemic and workbench application** (`matrix-core`, Quarkus) | `io.matrix.knowledge.LearningWorkbenchResource` | `/v1/workbench/**` | `io.matrix.cognitive.CognitiveKernel` — the W44 learning loop |
| | `io.matrix.api.OpenAIChatResource` | `/v1/chat/completions` | `io.matrix.reasoning.BrainLoopService` — the nine-stage loop |
| **the brain runtime** (`matrix-brain-runtime`, behind the hand-written server) | `io.matrix.api.MinimalHttpServer` | `POST /v1/analyze` | `io.matrix.brain.runtime.TrueMindCycle` |

> *"**The epistemic application runs TWO loops.** That is not a wart to be smoothed over — it is
> the fact the archaeology found, and naming only the cognitive kernel would repeat the ADR's
> original error in the opposite direction."*

**The naming is in code, not only in prose.** `matrix-core/src/main/java/io/matrix/api/CanonicalEntryPoint.java`
holds the machine-readable declaration (`DECLARATIONS`), and it is checked by two test classes:
`matrix-core/src/test/java/io/matrix/api/CanonicalEntryPointTest.java` and
`matrix-api-gateway/src/test/java/io/matrix/api/CanonicalEntryPointChainBTest.java`. The classes
are named **by string**, not by reference, so that `matrix-core` does not depend on the gateway.

**The nuance worth stating plainly:** the ADR names **resource/server classes** as the canonical
entry points (`LearningWorkbenchResource`, `OpenAIChatResource`, `MinimalHttpServer`) — these are
the classes a *request* enters through. The nearest thing to a **`main`** in the same declaration
is `io.matrix.MatrixApplication` (`matrix-core/src/main/java/io/matrix/MatrixApplication.java:30`,
the `@QuarkusMain`), which is the process-level entry of chain A and the `mainClass` of the native
binary; `MinimalHttpServer` has its own `main` at
`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:1699`, and
`docs-v2/architecture/RUNTIME-TOPOLOGY.md:116` records it as **served** (*"a `main` at `:1674`"* —
the file has since grown to `:1699`).

## C.4 The run commands the repository documents

### From `docs-v2/engineering/LEARNING-WORKBENCH.md` §1 "Start it" — the chain-A canonical path

```bash
./gradlew :matrix-core:quarkusRun
```

then open <http://localhost:9091/workbench/>. The document states: *"That is the whole procedure.
There is **no** database server to install, **no** model provider to configure, **no** API key,
and **no** account."* (§4 restarts with the same command, line 213.) The documented API calls are
at lines 267-288 and 395-405, e.g.:

```bash
curl http://localhost:9091/v1/workbench/status
curl -X POST http://localhost:9091/v1/workbench/workspaces/photosynthesis/load
curl -X POST http://localhost:9091/v1/workbench/workspaces/photosynthesis/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"...","level":"INTRODUCTORY"}'
```

### From `README.md` §8 "Building and running a supported path"

```bash
# the epistemic application (chain A)
./gradlew :matrix-core:quarkusBuild --no-daemon --console=plain
java -jar matrix-core/build/quarkus-app/quarkus-run.jar
#   → binds 127.0.0.1:9091 by default

# the local learning demonstrator (W50)
./scripts/run-local.sh
#   → then open http://127.0.0.1:9091/workbench/

# the brain runtime (chain B)
./gradlew :matrix-api-gateway:installDist --no-daemon --console=plain
bash scripts/start-mind.sh          # production mode
#   → binds 127.0.0.1:8765 by default; requires a credential in network-exposed mode

# from a fresh clone
bash scripts/fresh-clone-smoke.sh    # build, launch, query, then clean up
```

### The single most useful line for this wave

> **The canonical entry point of the epistemic application is `io.matrix.knowledge.LearningWorkbenchResource`
> (`/v1/workbench/**`), and the documented way to reach it is `./gradlew :matrix-core:quarkusRun`
> (`docs-v2/engineering/LEARNING-WORKBENCH.md:15`) or
> `./gradlew :matrix-core:quarkusBuild && java -jar matrix-core/build/quarkus-app/quarkus-run.jar`
> (`README.md:368-369`).** The brain runtime's canonical entry point is
> `io.matrix.api.MinimalHttpServer` (`POST /v1/analyze`), reached by
> `./gradlew :matrix-api-gateway:installDist && bash scripts/start-mind.sh` (`README.md:413-414`).

---

## D. What this document does NOT verify

1. **No workflow was executed.** Every CI verdict above is read from the workflow files and from
   the repository's own recorded runs; the current red/green state of the GitHub Actions runs on
   `develop` was not queried (no network access to the GitHub API was used).
2. **The full test suite was not run** — reserved for the owner by `AGENTS.md` §Тестирование
   (OOM risk). The only measurements taken are the quality gate (271, exit 0) and ShellCheck
   (106 findings across 41 tracked scripts, ShellCheck 0.11.0).
3. **The SpotBugs engine was not re-resolved.** The verdict rests on the repo's own pinned
   `toolVersion = '4.9.3'` and its dated comment (2026-10-26) that 4.9.3 bundles ASM 9.7.1.
   Whether a newer engine has since shipped on Maven Central was not checked.
4. **The TruffleHog scan was not re-run.** The five findings are located by static search of the
   tracked tree, not by executing TruffleHog; the claim that the detector matches exactly these
   five is an inference from the detector's URI shape (`scheme://user:password@host`) plus the
   repository-wide search, which is why the "near-misses" are listed explicitly in A.2.2.
5. **`git ls-files "*.sh"` = 63 vs. the workflow's `find . -name "*.sh"`** — on a fresh CI
   checkout these coincide; on this working tree `find` also reaches `.venv/` (72 files). The
   ShellCheck measurement was taken over the 63 tracked files, which is the CI-relevant set.
6. **The `main()` inventory covers `matrix-core/src/main/java` and
   `matrix-brain-runtime/src/main/java` only**, as the task specified. The other modules' entry
   points are noted once, in §C.2.
7. **`--gc=epsilon` is stated to be the reason the 8 GiB ceiling is hard.** That is the documented
   meaning of the flag (no collection → no reclamation), but the repository does not contain a
   measurement that isolates the flag as the cause of the OOM; the recorded OOM is the
   observation, and the 8–12 GB C-link peak in `NATIVE-IMAGE-DEEP-RESEARCH.md` §3.1 is the
   cause the repository itself names.
