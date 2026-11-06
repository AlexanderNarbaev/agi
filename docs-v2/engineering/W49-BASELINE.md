# W49 — BASELINE

**Wave:** W49 · **Task:** W49-00 · **Date:** 2026-11-06 · **Owner decision:** **OD-048**
(the public-research workstream) · **Prior wave:** W48, closed at `ed3725d5`

**What this document is.** The state of the repository and of its public
presence at the moment W49 started, with the command behind every claim. It is
the reference the wave's public statements are checked against — and the
record of what was true BEFORE anything was rewritten, so that a reader can
tell what the wave changed from what it found.

---

## 1. The repository state

| the fact | the value | how it was checked |
|---|---|---|
| the branch | `develop` | `git branch --show-current` |
| the HEAD at the start of W49 | `ed3725d58c9747533c44a5e03298fd473693a9cb` | `git rev-parse HEAD` |
| the remote | `origin/develop` — the same commit, no divergence | `git status --short --branch` → `## develop...origin/develop` |
| the working tree | clean | `git status --short` → empty |
| the default branch | `main` | `gh api repos/AlexanderNarbaev/agi` → `"default_branch": "main"` |
| what `main` holds | `7a0ed0cc` — "docs(session): RECON-W37.5 release — v18.0.0-mind Connectome Alpha", 2026-10-12 | `git log --oneline -1 main` |
| the gap between the branches | **357 commits** — `develop` holds the whole W37.5→W48 work that `main` does not | `git rev-list --count main..develop` |
| the local branches | 20 feature branches, none of them merged into `develop` | `git branch` |
| the repository | public, not a fork, not archived | `gh api repos/AlexanderNarbaev/agi` |

**What this means for a visitor.** The default branch is 357 commits behind the
development branch (2026-10-12 against 2026-11-06), and the public site is
built from that default branch. A
visitor who lands on the repository sees the W37.5 state of the project, not the
current one. The W49 directive is explicit that the default branch is not to be
changed without a separate decision, so W49 does not change it.

---

## 2. The CI state — GREEN on the mandatory checks, and one external blocker

The last observed run of the `ci` workflow is `42206946030` at `f6e2be9f`
(`gh run view --json status,conclusion,jobs 42206946030`):

| the job | the verdict |
|---|---|
| Build & Test (Java) | **success** |
| Web UI Tests | **success** |
| Quality gates (can fail) | **success** — the clean-code gate, the pinned formal tool, the formal gate over 15 specifications, the build, and the security boundary |
| Security Scan | **success** — the SpotBugs step reports the known Java 25 engine limitation as a loud warning, the secret scan ran for the first time and found nothing, the vulnerable-dependency check ran |
| Container Images (build + smoke) | **success** — all three smoke tests |
| Docker Build & Push (api-gateway) | **failure** — `::[error]Username and password required` at the FIRST step |
| Deploy to Staging / to Production | skipped — they need the same credentials |

The red job is the release path, not the verification path. Its cause is
external and exact: the step reads `secrets.DOCKERHUB_USERNAME` and
`secrets.DOCKERHUB_TOKEN` (`.github/workflows/ci.yml:460-463`) and **the
repository has no secrets at all**:

```
$ gh secret list
$ gh variable list
```

Both commands return nothing. The credentials are the owner's to configure; no
code change substitutes for them. The record is R-W48-04 in the risk register.

**The other workflows:** `branch-validation`, `Formal Specs (TLA+)`,
`docs-validation` — all green in their last runs (`docs-v2/engineering/W48-CI-RECONCILIATION.md`
§12 carries the per-run table). `native` — 1 403 runs on record, the last ten
all failures; the cause is an out-of-memory in the build, and the capability
stays **BLOCKED** (OD-046).

---

## 3. The public presence — what a visitor actually sees

### 3.1 The deployed site

The URL is read from the deployment API, never guessed:

```
$ gh api repos/AlexanderNarbaev/agi/pages
{"url":"https://api.github.com/repos/AlexanderNarbaev/agi/pages","status":"built",
 "html_url":"https://alexandernarbaev.github.io/agi/","build_type":"legacy",
 "source":{"branch":"main","path":"/"},"public":true, ...}
```

| the fact | the value |
|---|---|
| the deployed URL | `https://alexandernarbaev.github.io/agi/` |
| the branch it is built from | `main` (`.github/workflows/pages.yml` → `on: push: branches: [main]`) |
| the deployed commit | **`7a0ed0cc`** — the head of `main`; the legacy build type keeps no deployment records, so the deployed content is the content of `main` at that commit | `git log --oneline -1 main` |
| the build | Jekyll, from `docs-v2/` (`actions/jekyll-build-pages@v1`, `source: ./docs-v2`) |
| the deployments API | `repos/AlexanderNarbaev/agi/pages/deployments` → **404** — the legacy build type keeps no deployment records, so the deployed commit cannot be read from that API |
| the live title | `MATRIX: The Hybrid Intelligence Encyclopedia | MATRIX Encyclopedia` |
| the live content | "city of minds" ×4, "W1500" ×2, wave references from W30 to W33 |

**The site's own configuration contradicts the deployment.** `docs-v2/_config.yml`
declares `url: https://alexandr-narbaev.github.io` — the deployed host is
`alexandernarbaev.github.io`. The `n` is missing, so every canonical link the
site generates points at a host that does not serve it. `baseurl: /agi` is
correct.

**The site has no preview.** `pages.yml` deploys on a push to `main` and does
nothing else — there is no pull-request preview and no non-deploying build job,
so a change to `docs-v2/` can only be seen after it is published.

### 3.2 The repository metadata

```
$ gh api repos/AlexanderNarbaev/agi --jq '{description, topics, has_discussions, homepage}'
{"description": null, "topics": [], "has_discussions": false, "homepage": null}
```

No description, no topics, no homepage, Discussions disabled. The license is
`AGPL-3.0` ✓ and issues are enabled ✓.

### 3.3 The contributor-facing material

| the file | the state found | the evidence |
|---|---|---|
| `README.md` | describes W45.2/W46 as current, names the commit `a6ded233…`, counts the modules at 14, and carries the `develop`-branch state as the project's state | read in full — 121 lines |
| `CONTRIBUTING.md` | rewritten by W49 — see `docs-v2/engineering/W49-ONBOARDING.md` | the audit is in that document |
| `CONTRIBUTING` (no extension) | **a second, conflicting contribution guide** — it promises a 48-hour review, a chat, office hours, and links to the v1 `docs/L0`…`docs/L13` tree (which no longer exists) <!-- no-link: the v1 documentation tree was removed; the mention is historical --> and to `CODE_OF_CONDUCT.md` (a file that does not exist). **W49 resolved it:** the file is now a pointer to the canonical `CONTRIBUTING.md` | read in full |
| `CODE_OF_CONDUCT` | names `conduct@matrix-ai.org` — an address that is not configured anywhere in the repository; the text itself says the address "will be established" | read in full |
| `.github/ISSUE_TEMPLATE/security.md` | points at `SECURITY.md`, which does not exist in the repository | `ls SECURITY.md` → no such file |
| `.github/PULL_REQUEST_TEMPLATE.md` | asks for a Checkstyle pass — the project has no Checkstyle | `grep -rn "checkstyle" --include=*.gradle .` → no match |

### 3.4 The public statement's own vocabulary

The W48 records contain the statements that the public material must not
contradict. The ones the public documents have to carry:

| the statement | the value | the artifact that proves it |
|---|---|---|
| the mandatory CI checks on a clean runner | five of five green | run `42206946030` at `f6e2be9f` |
| the formal specifications | 15 on disk, model-checked by the formal gate | `formal/`, `scripts/formal-gate.sh`, the tool pinned by digest at `tools/tla2tools.jar` |
| the GPU | an **optional** accelerator (OD-047); the measured serving path is 12.06× / 11.68× / 11.47× over three runs; a single-point ONNX call is **0.24×** — the GPU is slower there; the historical 12.6× stays **HISTORICAL** | `docs-v2/engineering/W48-GPU-BENCHMARK.md` |
| the native-image capability | **BLOCKED** — no native executable on this host | `docs-v2/engineering/W48-TEST-RECONCILIATION.md`, R-W46-11 |
| the full `matrix-core` suite | one approved run (OD-041), used in W47: 1 226 classes, 9 051 executions, 25 failures — 23 of them native-binary failures across 8 classes | `docs-v2/engineering/W48-TEST-RECONCILIATION.md` |
| the autonomy layer | the scheduler accepts impulses under a budget and a FROZEN gate; the proposal-creating consumer (OD-045) is implemented in W49 and has **no external effect** | `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md`, the W49 commit `c86f6898` |
| the restore drill | performed against a copy; the original stayed byte-identical; the payload restore was **not** exercised | `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md`, `docs-v2/operations/RECOVERY-RUNBOOK.md` §9 |

---

## 4. The hardware and the runtime, measured today

| the fact | the value | how it was checked |
|---|---|---|
| the memory | 59 GiB total, 47 GiB available | `free -g` |
| the disk | 197 GiB free on the root filesystem; 30 GiB free on `/tmp` | `df -h /tmp /` |
| the JDK | 25 at `/home/alexandr-narbaev/jdk25` | `JAVA_HOME` |
| the Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| the GraalVM toolchain | **absent** — `~/.sdkman/candidates/java/` is empty and `native-image` is not on the PATH | `ls ~/.sdkman/candidates/java/`, `which native-image` |

**The native-image measurement (OD-046) therefore cannot be taken on this host:**
the toolchain that performs the build is not installed. What can be measured
is recorded in `docs-v2/engineering/W49-NATIVE-BUDGET.md`, with the missing
prerequisite named.

---

## 5. The blockers this wave starts from

1. **The release path cannot run** — no repository secrets (R-W48-04, OPEN,
   external, the owner's).
2. **The native-image capability is BLOCKED** — no GraalVM on the host; the CI
   workflow's own build dies of an out-of-memory (OD-046; W49-02 measures what
   it can).
3. **The static-analysis check produces no evidence** — the pinned SpotBugs
   engine (4.9.3, ASM 9.7.1) cannot read Java 25 bytecode. An engine that can
   (4.10.5, ASM 9.11) reports **1 414 findings** locally, 981 of them
   `EI_EXPOSE_REP*`; the policy for those findings is the owner's decision.
4. **The public site is stale and cannot be previewed** — it is built from the
   default branch, which is 357 commits behind, and there is no preview path.
5. **The contributor-facing material promises channels that do not exist** — a
   maintainer address, a 48-hour response, a chat, office hours, a moderation
   team, a private reporting channel.
6. **Two conflicting `CONTRIBUTING` files** — one with the `.md` extension and
   one without, contradicting each other.

---

## 6. The commands used for this baseline

```bash
git branch --show-current
git rev-parse HEAD
git status --short --branch
git log --oneline -1 main
gh run view --json status,conclusion,jobs 42206946030
gh api repos/AlexanderNarbaev/agi/pages
gh api repos/AlexanderNarbaev/agi --jq '{description, topics, has_discussions, homepage}'
gh secret list
gh variable list
free -g
df -h /tmp /
ls ~/.sdkman/candidates/java/ ; which native-image
curl -sSL https://alexandernarbaev.github.io/agi/ | grep -E '<title>|city of minds|W1500'
grep -rn "checkstyle" --include=*.gradle .
ls SECURITY.md CODE_OF_CONDUCT.md CONTRIBUTING.md CONTRIBUTING
```

## 7. The recommended repository metadata (prepared, not applied)

The directive asks for a **recommended** description and topic set. Applying
them is an owner action on the repository's settings; W49 prepares them and
records them here.

**Description (≤ 350 characters):**

> MATRIX — an open research project on evidence-grounded cognitive architectures: symbolic reasoning with explicit provenance, learning from feedback, memory, adaptive method selection and bounded autonomy. Experimental; not AGI and not production-ready.

**Topics** (GitHub's maximum is 20; the set below is 12):

`cognitive-architecture`, `neuro-symbolic-ai`, `knowledge-graph`, `formal-methods`,
`tla-plus`, `explainable-ai`, `reproducible-research`, `research`, `jvm`, `quarkus`,
`sqlite`, `onnx-runtime`
