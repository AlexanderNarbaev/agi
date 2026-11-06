# W48 — BASELINE

**Status:** the preflight of the W48 wave · **Date:** 2026-11-06
**Branch:** `develop` · **Starting commit:** `eca23bfdff6e77798f0513d2b5660d8a5daa5272`
**Report of record:** this file. The wave's own report is `docs-v2/engineering/W48-STATUS.md` <!-- no-link: the report is written at the end of the wave -->.

**Every statement below is a command or a file reference.** A statement without one is not
evidence, and the W48 directive says so.

---

## 1. The repository state

| | | the command |
|---|---|---|
| the branch | `develop` | `git branch --show-current` |
| the local HEAD | `eca23bfdff6e77798f0513d2b5660d8a5daa5272` | `git rev-parse HEAD` |
| the remote HEAD | **the same commit** — no divergence in either direction | `git rev-parse origin/develop`; `git rev-list --count HEAD..origin/develop` → 0; `git rev-list --count origin/develop..HEAD` → 0 |
| the working tree | **clean** — no modified, no staged, no untracked file | `git status --short --branch` → `## develop...origin/develop`; `git ls-files -m -o --exclude-standard` → empty |
| the W47 commit sequence | **13 changesets** after `84955ec9` | `git log --oneline 84955ec9..HEAD` → 13 entries; with the starting commit, 14 through `eca23bfd` |

**The owner's changes are preserved.** The protected inventory of the W47 wave
(`.opencode/`, `.codex/`, `.veai/`, `.agents/`, `AGENTS.md`, the configuration, the WAL
records) was not touched by this wave's preflight — the tree is clean and nothing is
staged.

## 2. The CI state — RED, and the reasons are known

The runs for `eca23bfd` (fetched with `gh run list --branch develop`):

| the workflow | the job | the state |
|---|---|---|
| `ci` | Build & Test (Java) | **failure** — 240 tests completed, 11 failed |
| `ci` | Quality gates (can fail) | **failure** — DOCS-1 |
| `branch-validation` | CONSTITUTION compliance check | **failure** — the version-constant gate |
| `branch-validation` | Compile all T-01 modules | **failure** — the GraalVM action |
| `Formal Specs (TLA+)` | Model-check TLA+ specs | **failure** — the artifact download |
| `docs-validation` | — | success |

**Each failure was reproduced from the logs** (`gh api …/actions/jobs/<id>/logs`), and each
was reproduced locally or in a clean checkout.

### 2.1 The gateway tests read a developer's home directory

```
NoFutureClaimsTest > no_future_tense_about_real_engine_completion() FAILED
NoFutureClaimsTest > handle_distill_reports_planned_wave() FAILED
NoFutureClaimsTest > no_endpoint_returns_synthetic_distilled_json() FAILED
ProdCallerExistsTest > persistentMind_has_prod_caller() FAILED
ProdCallerExistsTest > autonomyLoop_has_prod_caller() FAILED
ProdCallerExistsTest > realAuditService_has_prod_caller() FAILED
ProdCallerExistsTest > realSleepScheduler_has_prod_caller() FAILED
ProdCallerExistsTest > realInboxWatcher_has_prod_caller() FAILED
SingleInstanceGuardTest > handle_status_calls_realSleepScheduler_or_autonomyLoop() FAILED
SingleInstanceGuardTest > handle_distill_returns_not_implemented_until_W5() FAILED
SingleInstanceGuardTest > handle_sleep_calls_realSleepScheduler_not_draft() FAILED
```

**The cause** — `matrix-api-gateway/src/test/java/io/matrix/api/NoFutureClaimsTest.java:21`,
`:31`, `:42`, `ProdCallerExistsTest.java:21,31,40,50,59` and `SingleInstanceGuardTest.java:75`
all contain the literal path `/home/alexandr-narbaev/Projects/agi/…`. On the runner that
file does not exist, so `Files.readString` throws and the test fails. **On this machine the
same tests pass**, because the file exists here — which is exactly why the defect is
invisible locally.

### 2.2 DOCS-1 fails on a clean checkout — and the reason is NOT the archive

The W48 directive names the archive as the cause. **The archive is not the cause** — the
archive group is exempt from the strict set by the checker's own rule
(`scripts/docs-link-check.py:463-467`, `group in HISTORICAL_GROUPS → strict = False`), and
the 1095 archive `BROKEN_LINK`s are reported and never fail the gate.

The real cause was found by running the checker in a **clean worktree** of `eca23bfd`
(`git worktree add /tmp/agi-w48-ci eca23bfd`):

```
$ python3 scripts/docs-link-check.py --root "$(pwd)" --strict
  defects: 2822  strict failures: 53
$ echo $?
1
```

**All 53 strict failures are `MISSING_PATH`, and every one of them names a RUNTIME path
under `data/`, `bin/`, `formal/states/`, `matrix-core/bin/` or `matrix-core/data/`:**

```
.agents/skills/matrix-architecture-navigator/references/two-chains.md:12  data/mind/mind.sqlite
docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md:24                     data/mind/mind.sqlite
docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md:208                          data/mind/episodic.ndjson
docs-v2/engineering/W47-STATUS.md:26                                        data/mind/mind.sqlite
docs-v2/governance/PROJECT-STATE.md:44                                      data/mind/mind.sqlite
docs-v2/operations/RECOVERY-RUNBOOK.md:20                                   data/mind/mind.sqlite
… (53 in total)
```

**Why the machine decides whether the gate is green.** These paths exist on the owner's
machine — `data/` is ignored wholesale (`.gitignore:77`), so the runtime files are not in
git — and they do not exist in a fresh checkout. The checker's rule is: a path whose
**parent directory** exists is a `MISSING_PATH` (strict); a path whose parent does not
exist is a "planned artefact" (not a defect). In the clean checkout `data/mind/` exists
(it holds the tracked `hdc_kb.ndjson`), so `data/mind/mind.sqlite` has a parent and is
reported; on the owner's machine the file itself exists and is not reported at all.

**So the gate was green locally and red in CI, and the difference was a runtime artefact
rather than a documentation defect.** That is the defect W48-05 must correct.

### 2.3 The GraalVM distribution identifier is not supported

`.github/workflows/branch-validation.yml:105-108` requests `graalvm/setup-graalvm@v1` with
`distribution: 'graalvm-ce'`. The action rejects that value; the supported identifier for
GraalVM Community Edition is `graalvm-community`. The job failed at the action itself, so
no compilation evidence was produced.

### 2.4 The version-constant gate greps for a word, not for a contract

`.github/workflows/branch-validation.yml:84-95` requires the string `VERSION` to appear
somewhere under `$module/src/main`. `matrix-api-gateway` has it; **`matrix-sdk-java` does
not** — its public facade is `matrix-sdk-java/src/main/java/io/matrix/sdk/MatrixClient.java`,
and it declares no version at all. The gate's `grep -rq "VERSION"` cannot tell a real
version contract from a comment that happens to contain the word.

### 2.5 The TLA+ artifact was replaced in place

```
$ curl -fsSL -o /tmp/tla-upstream.jar \
    https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar
$ sha256sum /tmp/tla-upstream.jar
654aa50197faa48fa0136b41257270de6469d29ef323e7822ead59d2be43ce10   (4,509,176 bytes)

$ sha256sum tools/tla2tools.jar
fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e   (4,504,421 bytes)
```

**The upstream `v1.8.0` release was recreated on 2026-11-06T00:39:06Z** (the GitHub API
reports `created_at: 2026-11-06T00:39:06Z`, `immutable: false`, `target_commitish: master`)
— it serves different bytes today than it served in October, when the W46 wave downloaded
and recorded them in `formal/mutations.json`. The tlaplus **repository** does not track
`tla2tools.jar` (`GET /repos/tlaplus/tlaplus/commits?path=tla2tools.jar` → `[]`), and the
artifact is not published to Maven Central (`search.maven.org` → 0 hits), so there is no
content-addressed upstream copy to point at.

**The consequence:** the CI's `sha256sum -c` correctly refuses the bytes the upstream now
serves, and the formal job cannot start. The check is right; the SOURCE is not immutable.

## 3. The live database — and a defect the W47 report did not see

**The W47 report said the live knowledge database was at schema v16 and had not been
opened. The measured truth:**

```
$ python3 -c "… sqlite3.connect('file:data/knowledge/workbench.sqlite?mode=ro', uri=True) …"
  data/knowledge/workbench.sqlite: schema v19
  data/mind/mind.sqlite: OperationalError: no such table: schema_meta   (chain B's own schema)
```

**It is at v19** — the W47 wave's additive migration ran against the **owner's live
database**. The timestamps show it: `data/knowledge/workbench.sqlite` itself is unchanged
(2026-11-02T22:09), but `workbench.sqlite-wal` was written at **2026-11-05T08:19** and
`workbench.sqlite.writer.lock` at 08:19 — during the W47 wave.

**The cause**, found by reading the scripts the wave ran: `scripts/security-boundary-check.sh`
starts the Quarkus application with the default configuration (lines 130-131, 170-171,
200-201), and the default is `matrix.knowledge.path = data/knowledge/workbench.sqlite`
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:71`). The check runs
from the repository root, so **the check opened the live database**, and the store migrated
it. The W47 wave ran that check; the migration is additive and re-runnable, so no knowledge
was lost — but the wave's own rule ("do not modify live databases under `data/`") was
broken by a gate the wave trusted, and **nothing in the repository detected it**.

**This is the first defect of W48**, and the fix is not only "do not run the check
against the live file": it is that **the check must be unable to touch the live file**, and
there must be a gate that says so.

## 4. The tests and the known failures

The W47 full run (`OD-041`) is the denominator: **1226 classes, 9051 executions, 23
skipped, 25 failures**. The exact reconciliation — class, method, category, whether it
existed before W47, whether W48 fixes it — is
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` <!-- no-link: the reconciliation is written by W48-06 -->.

The two failures that are not the native-binary classes:

| the class | the method | the category | the evidence |
|---|---|---|---|
| `io.matrix.consciousness.Exp228ComplianceTest` | `realProjectCompliance()` | **pre-existing, proved** | the same test on a worktree of the pre-wave commit `84955ec9` fails with the same numbers (`random=63, wallClock=47`, 21 unadjudicated wall-clock sites); the wave added **zero** wall-clock or random sites to `matrix-core/src/main/java` (`git diff 84955ec9..HEAD -- matrix-core/src/main/java \| grep -cE "currentTimeMillis\|nanoTime\|new Random\|Math\.random\|Instant\.now\|LocalDate\.now"` → 0) |
| `io.matrix.consciousness.CausalEmergencePropertyTest` | `propertyEIOfNonNegativeDistribution()` | **a flaky property test** | the assertion is `ei >= 0.0` with no tolerance; the run produced `-3.2034265038149176E-16`; the same test passes standalone — the seed decides |

The 22 native-binary failures are the GraalVM classes: the native executable was never
built (`Cannot run program "build/native/nativeCompile/matrix-core"`).

## 5. The hardware and the runtime, measured today

| | the measurement |
|---|---|
| the CPU | 32 threads (`nproc`) |
| the memory | 59 GiB total, 41 GiB available at the probe (`free -g`) |
| the GPU | NVIDIA GeForce RTX 5070 Ti Laptop GPU, 12227 MiB, driver 595.99.02 (`nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader`) |
| `/tmp` | 30 GiB tmpfs, 2.2 GiB used |
| the JDK | openjdk 25.0.4.1 at `/home/alexandr-narbaev/jdk25` |
| the Gradle daemon | stopped before the W47 full run (`./gradlew --stop` → "No Gradle daemons are running") |
| the formal tool | `tools/tla2tools.jar`, TLC2 2026.10.28.014557, digest `fc6878c6…` — the bytes the local evidence was produced with |

## 6. The blockers this wave starts from

| the blocker | the owner | the way out |
|---|---|---|
| the CI is red in three workflows | the coordinator | W48-02 … W48-05 |
| the formal tool's upstream source is not immutable | the coordinator | W48-04 — the bytes must be pinned in the repository |
| a gate opens the owner's live database | the coordinator | W48-00/W48-06 — the check must be unable to touch it |
| the legacy snapshot payload can erase learning rows it does not carry | the coordinator | W48-01 — **P0** |
| the snapshot payload is unbounded and the rows are never pruned | the coordinator | W48-07 |
| the restore has never been performed | the coordinator | W48-08 — against a copy |
| the 12.6× GPU claim has no re-runnable artefact | the coordinator | W48-09 |
| the impulse has no consumer | the owner | W48-11 — an ADR and a decision, **not** an implementation |

## 7. The commands used for this baseline

```bash
pwd; git branch --show-current; git rev-parse HEAD; git rev-parse origin/develop
git status --short --branch; git ls-files -m -o --exclude-standard
git log --oneline 84955ec9..HEAD
gh run list --branch develop --limit 8
gh api repos/AlexanderNarbaev/agi/actions/jobs/<id>/logs
git worktree add /tmp/agi-w48-ci eca23bfd
python3 scripts/docs-link-check.py --root "$(pwd)" --strict; echo $?
sha256sum tools/tla2tools.jar /tmp/tla-upstream.jar
curl -fsSL -o /tmp/tla-upstream.jar \
  https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar
curl -sS https://api.github.com/repos/tlaplus/tlaplus/releases/tags/v1.8.0
sqlite3 'file:data/knowledge/workbench.sqlite?mode=ro' \
  "select value from schema_meta where key='version'"
nproc; free -g; df -h /tmp; nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader
```
