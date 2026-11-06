# W48 — STATUS

**Status:** the implementation of the owner's W48 directive and of the decisions of
2026-11-06 · **Date:** 2026-11-06
**Branch:** `develop` · **Starting commit:** `eca23bfd` (the last commit of W47)
**Ending commit:** the commit that carries this document
**Report of record:** this file. The decisions are in
`docs-v2/governance/DECISION-REGISTER.md` (OD-045…OD-047), the tasks are in
`docs-v2/governance/ROADMAP.md`, the questionnaire is
`docs-v2/engineering/W48-QUESTIONNAIRE.md`.

---

## 1. The starting state

| the item | the value at `eca23bfd` | the evidence |
|---|---|---|
| the mandatory CI checks | **red** — the `ci` workflow failed at run `42170865426` | `docs-v2/engineering/W48-CI-RECONCILIATION.md` §3 |
| the formal tool | downloaded from a release whose asset was **replaced in place** | `tools/README.md`; the upstream serves `654aa501…`, the pinned bytes are `fc6878c6…` |
| the snapshot payload | **unbounded**, and a legacy payload could destroy the learning history silently | `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §1 |
| the restore claim | IMPLEMENTED, never performed | `docs-v2/operations/RECOVERY-RUNBOOK.md` §5 |
| the GPU claim | 12.6×, from an ad-hoc probe nobody could re-run | the `W46-GPU` row of the capability registry |
| the full-suite result | 25 failures, of which 23 were native-binary | `docs-v2/engineering/W48-TEST-RECONCILIATION.md` §2 |

The wave's preflight, with the baseline that was frozen before anything changed, is
`docs-v2/engineering/W48-BASELINE.md`.

## 2. The commits

| the commit | the change |
|---|---|
| `0c5b3980` | the tests that read the tree read it through the build, not through a home directory |
| `d1f2e0fb` | the security check no longer opens the owner's live database — and proves it |
| `f5870f4c` | the documentation gate no longer decides on a developer's runtime state |
| `02d2a516` | the branch validation asks GraalVM for a distribution that exists and checks a real version contract |
| `16a2d0c5` | the formal tool is pinned by bytes, not by a URL that can be replaced |
| `55841168` | the frozen exception records what the W48 workflow repairs cost |
| `4084538a` | a restore refuses a payload that cannot bring back what it would destroy |
| `e9a5c944` | the shared test support reaches the modules, not the root project |
| `241af1e1` | the property test tolerates the round-off; the compliance check stops passing when it finds nothing |
| `4adbc664` | the twenty-one wall-clock sites are adjudicated — seven got a clock, fourteen got a reason |
| `33d199e2` | the snapshot payload is measured, bounded and aged on a three-tier calendar |
| `090247cb` | the restore drill runs against a copy and the answer is that the snapshot restores |
| `75937934` | the GPU benchmark is reproducible, and the 12.6× digit is bounded rather than repeated |
| `31d8f832` | the native-binary tests are a scope of their own, and the scope refuses to pretend |
| `af9ba688` | the disk budget states its unit, and the test that guessed it is measured |
| `9bb6bf5c` | the main workflow verifies the tracked formal tool instead of downloading a replaced asset |
| `3d5cab36` | the CI reconciliation records the state of every check and what W48 changed |
| `40b0942c` | the 25 failures are reconciled, and two of them are fixed with a commit |
| `4ddcb58d` | the snapshot payload contract is written down, with its refusals and its limits |
| `31989733` | the autonomy impulse consumer is proposed, not built |
| `93350867` | the registries carry the W48 measurements, and the native capability finally has a row |
| `b0b8e110` | the owner answered three questions, and the wave report says what is still unproven |
| `0eac36c3` | the wave report carries the task labels, the CI record and the confirmations |
| `64f43f74` | the clean runner's twelve failures are diagnosed and fixed |
| `63d440b1` | the governance records now agree with what was committed and answered |
| `bc87935c` | the three red CI jobs are repaired, and the gateway's bind log stops lying about the interface it holds |
| `9f4d1280` | the wave report records the three CI repairs and their causes |
| `e0de8419` | the security step's errexit no longer skips two real checks, and the minikube smoke test brings its own database |
| `2c633579` | the wave report records the clean runner's verdict after the CI repairs |
| `0e647a45` | the risk register carries the CI jobs' hidden dependencies |
| `f6e2be9f` | the secret scan scans the commits the push carried, and the state names the current commit |
| `e84422f4` | the reconciliation records the second reading and the secret scan's silence |

## 3. The tasks, with their status labels

The labels are the directive's: **DONE**, **PARTIAL**, **BLOCKED**, **REFUSED**,
**DEFERRED**, **NOT VERIFIED**. The evidence column says **how** it was established —
locally tested, CI tested, model-checked, native-image tested, hardware measured,
operationally rehearsed, or documented.

| the task | the subject | the status | the evidence | the commit |
|---|---|---|---|---|
| W48-00 | the baseline and the current-state snapshot | **DONE** | documented — every statement carries a command | `docs-v2/engineering/W48-BASELINE.md` |
| W48-01 | the legacy-snapshot restore compatibility | **DONE** | locally tested — 12 tests on real temporary SQLite databases | `4084538a`, `4ddcb58d` |
| W48-02 | the gateway's path-portable tests | **DONE** | locally tested | `0c5b3980` |
| W48-03 | the branch validation and the GraalVM action | **DONE** | CI tested — the workflow's own steps; the compile was reached in CI | `02d2a516` |
| W48-04 | the TLA artifact's immutability and the CI formal execution | **DONE** | CI tested + model-checked — 15 specifications, `tools/tla2tools.jar: OK`; and the LOCAL formal gate: **15 passed, 0 failed** | `16a2d0c5`, `9bb6bf5c` |
| W48-05 | the quality gate's scope and the historical documents | **DONE** | locally tested + CI tested — 0 strict link failures locally, and DOCS-1 **PASS** on the clean runner (`42181161742`), with the historical archive reported separately | `f5870f4c`, `d1f2e0fb`, `64f43f74` |
| W48-05b | the three red jobs of the clean runner (Redis, the missing JDK, the published port) | **DONE** — locally tested | each cause was read out of the runner's own log; the gateway's boundary is pinned by `GatewayExposureBoundaryTest` (2 tests) with a red/green control | `bc87935c` |
| W48-06 | the full-suite failure reconciliation and the numerical/compliance repairs | **DONE** for 2 of 25, **BLOCKED** for the 23 native | locally tested — the 23 re-measured, 2 fixed and green | `241af1e1`, `4adbc664`, `31d8f832`, `40b0942c` |
| W48-07 | the snapshot size measurement, the bound and the row retention | **DONE** | locally tested — 12 tests and the tool's own 40 checks; the measurement was taken on a copy | `33d199e2` |
| W48-08 | the restore drill against a copy | **DONE** | operationally rehearsed — the live file byte-identical before and after | `090247cb` |
| W48-09 | the reproducible GPU benchmark | **DONE** | hardware measured — three independent runs on the GPU; the CUDA provider positively verified | `75937934` |
| W48-10 | the governance and the registry reconciliation | **DONE** | documented — 4 validators, 0 defects | `93350867`, `b0b8e110`, `63d440b1` |
| W48-11 | the autonomy consumer's ADR and the owner decision | **DONE** — the ADR and the decision; the consumer itself is **DEFERRED** to W49 | documented — the owner's answer is OD-045 | `31989733` |
| — | the native-image capability | **BLOCKED** | not executed — the toolchain is absent; the scope refuses to run | `31d8f832` |
| — | the GPU path in CI | **NOT VERIFIED** | no gpu/cuda/nvidia job exists in any of the seven workflows | — |
| — | the clean runner's own twelve failures | **DONE** | CI tested — the twelve were diagnosed from the job log and fixed; the corpus classes now SKIP there with a reason, and the security check's startup window is readable | `64f43f74` |
| — | the retention pass on a schedule | **DEFERRED** | the tool is tested; who runs it and when is the operator's | `33d199e2` |

## 4. What the directive asked, and what answered it

### 3.1 The loss of knowledge and learning history — CLOSED

The hazard was a restore that erases the workspace and writes back a payload that does
not carry the learning family. The refusal now happens **before the transaction and
before the write lock** (`SnapshotCoverage` / `IncompatibleSnapshotException`,
identifier `INCOMPLETE_LEGACY_SNAPSHOT`), and the database is byte-identical after a
refusal. The contract is in `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md`; the
12 tests are in `SnapshotRestoreCompatibilityTest`.

**Verified:** 133 classes / 968 tests / 0 failures in the cognitive+knowledge scope.

### 3.2 The reproducible CI — CLOSED for the mandatory checks

The main workflow carried a **copy** of the download step that the `tla.yml` repair had
already removed, so the "Quality gates" job was still red at the very step that had
just been fixed elsewhere (`9bb6bf5c`). Neither workflow downloads anything now.

**What is still open:** the GPU path is not exercised in CI at all — `grep -rniE
"gpu|cuda|nvidia" .github/workflows/` → **no match** in any of the seven workflows.

### 3.3 The snapshot size and retention — CLOSED

The payload is measured (the owner's workspace: **103 075 bytes**), bounded (8 MiB
default, configurable), and refused with `SNAPSHOT_PAYLOAD_TOO_LARGE` **before the
write** — never truncated. The `workspace_snapshot` rows get the three-tier calendar
(daily 7 / weekly 4 / monthly 12, per workspace, UTC), dry-run by default.

### 3.4 The restore drill — PERFORMED, against a copy

`scripts/restore-drill.sh`: the original is v19 / 34 tables / 199 rows with the schema
digest `02aac93a…`, and the copy is identical in every per-table fingerprint. Stage B
migrates a derived v16 image 16 → 17 → 18 → 19 to an identical result, with 0 dangling
references, a clean start and stop, and a second writer refused. Stage C restores into
an isolated damaged target and matches the copy's record.

**What was NOT proved:** the **payload** restore was not exercised — the live
`workspace_snapshot` table holds **0 rows**. No restore was made over the original.
`mind.sqlite` was untouched.

### 3.5 The full-suite evidence — RECONCILED, two defects fixed

The 25 failures are 23 native-binary + 1 wall-clock compliance + 1 numerical
tolerance. The 23 were re-measured at HEAD ("27 tests completed, 23 failed") and the
per-class counts match the W44 measurement exactly. Two are fixed and committed
(`241af1e1`, `4adbc664`), and both classes are green now (7 tests / 0 failures and
1 test / 0 failures).

### 3.6 The reproducible GPU benchmark — MEASURED

| the path | the CPU median | the CUDA median | the factor |
|---|---|---|---|
| the MATRIX forward call | 160.995 ms | 13.345 ms | **12.06×** |
| the raw `session.run` | 173.846 ms | 7.325 ms | 23.73× |
| the end-to-end path | 2 220.734 ms | 174.952 ms | **12.69×** |
| a single-point ONNX call | 0.008 ms | 0.034 ms | 0.24× — the GPU is **4× slower** |

Three independent runs give **12.06× / 11.68× / 11.47×** on the serving path: the
~12× claim reproduces, the digit **12.6× does not** (every run is 4.3–9.0 % below it),
so 12.6× stays **HISTORICAL** and is bounded by the new measurement. The CUDA execution
is positively verified: the execution profile names `CUDAExecutionProvider` 1 431 times.

### 3.7 The state, the registers, the specifications — UPDATED

The capability registry (35 rows, including the new `W48-NATIVE-IMAGE` row that did not
exist), the test catalogue (11 scopes, including the two native scopes), the risk
register (two new risks, six updated, none deleted), the project state, the roadmap,
the decision register (OD-045…OD-047), SPEC-015 and SPEC-016.

### 3.8 The next autonomy decision — PREPARED, NOT IMPLEMENTED

`docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` records the evidence, the
alternatives, the recommended scope, the safety invariants, the cost, the failure modes
and the tests. **Nothing was implemented** — the owner's answer (OD-045) approves the
scope **for the next wave**.

## 5. The owner decisions of 2026-11-06

| the decision | the answer |
|---|---|
| **OD-045** — the autonomy impulse consumer | **option A** — a proposal-creating consumer inside Chain B, implemented in the NEXT wave |
| **OD-046** — the native-image build | measure the resource cost and fix the build in the next wave; the capability stays **BLOCKED** until a build succeeds |
| **OD-047** — the role of the GPU | an **optional accelerator**, not part of the default serving path; the silent CPU fallback stays visible in the registry |

## 6. The tests, with their exact counts

| the scope | the classes | the methods | the failures | the commit |
|---|---|---|---|---|
| SCOPE-COGNITIVE-KNOWLEDGE (`io.matrix.cognitive.*`, `io.matrix.knowledge.*`) | 134 | 969 | **0** | `f48427b7` |
| SCOPE-BRAIN-RUNTIME (`io.matrix.brain.runtime.*`) | 120 | 919 (2 skipped) | **0** | `af9ba688` |
| SCOPE-NATIVE-BUILD-CONFIG | 1 | 2 | **0** | `31d8f832` |
| SCOPE-NATIVE-BINARY | 8 | 25 discovered | **not executed — BLOCKED** | `31d8f832` |

The evidence is the JUnit XML under `<module>/build/test-results/`.

## 7. The negative controls

| the control | the command | the result |
|---|---|---|
| the native scope is excluded, not silently absent | `./gradlew :matrix-core:test --tests "io.matrix.nativebin.*"` | **"No tests found for given includes"** — where the same command before `31d8f832` ran 27 tests and failed 23 |
| the native scope refuses to pretend | `./gradlew :matrix-core:nativeBinaryTest` | **exit 1** with "This scope is BLOCKED - it is not a pass, and the default JVM task does not cover it." |
| the retention tool's own checks | `python3 scripts/snapshot-retention.py --self-test` | 40 checks, 0 failures |
| the version gate's negative control | the gate against a comment-only file | the gate **fails**, as it must |
| the compatibility refusal leaves no trace | the fingerprint before and after the refusal | **identical** (6 of the 12 tests) |

## 8. What was NOT verified — the honest list

1. **The payload restore was not exercised by the drill.** The live
   `workspace_snapshot` table holds 0 rows; the payload path is covered by unit tests
   on temporary databases only.
2. **The native-image capability was never built here.** No native executable exists on
   this host; the scope is BLOCKED, not passing.
3. **The GPU benchmark was not re-run by this wave's later commits** — the figures come
   from `75937934`, which measured them with the CUDA session; the harness's own 8 tests
   pass.
4. **The full `matrix-core` suite was NOT run in W48.** OD-041 authorised one full run
   and it was used in W47; a second needs fresh approval. The full-suite denominators
   (1 226 classes / 9 051 executions) are cited from the W47 report and are **not
   re-verified here**.
5. **A green `ci` run is still not observed.** The run `42198961133` (`bc87935c`)
   is green in three of the five jobs — including the security boundary and the
   formal gate — and the two red jobs are repaired in `e0de8419`. The wave has
   not yet seen the run that carries that repair, so the verdict stays open until
   it does.
6. **The W47 full-suite XML was not retained**, so the per-method identity of the 23
   native failures in the W47 run itself is **derived**, not read from that run's XML.
7. **Two formal-spec mutants SURVIVED** (`Consensus-evaluate-without-quorum`,
   `KnowledgeAxes-conflict-forgets-what-it-recorded`) — known holes the gate logs, not
   new failures.
8. **The production-corpus properties are NOT VERIFIED on a clean runner.** The corpus
   `models/training_data/qa_pairs.json` is the owner's data and `/models/` is
   git-ignored, so the two corpus classes (11 methods) SKIP there with a recorded
   reason. They execute and pass where the corpus is present. A skip is not a pass,
   and this is recorded rather than smoothed away.
9. **The security boundary on the clean runner is now VERIFIED.** The run
   `42198961133` carries the step "The security boundary (anonymous writes are
   refused)" as **success** — the check that §4.7 could not observe. The
   application had failed on the runner for two unrelated reasons in a row (its
   60-second startup window, and a Redis the runner does not have); both were
   repaired, and the boundary now holds there.
   **The static analysis, however, produced NO evidence in that run:** the pinned
   SpotBugs engine (4.9.3, ASM 9.7.1) cannot read Java 25 bytecode. An engine
   that can — 4.10.5, ASM 9.11 — exists and reports **1 414 findings** locally,
   981 of them `EI_EXPOSE_REP*`. The capability therefore stays **NOT VERIFIED**,
   and the decision about those findings is the owner's.
10. **The container smoke tests for the matrix-core and minikube images have never run
    on a clean runner.** The api-gateway step failed first and the job stopped there.
    Their configuration was corrected in `bc87935c` (the application binds the
    loopback interface by default, so their published ports reached nothing), but
    their verdict is **NOT VERIFIED** until a run reaches them.
11. **The Helm chart's own port and interface mismatch was NOT repaired.** The chart
    declares `containerPort: 8080` and probes `/q/health/live`, while the application
    serves on `QUARKUS_HTTP_PORT` (default 9091) and binds `QUARKUS_HTTP_HOST`
    (default 127.0.0.1); the chart sets neither. Which port the platform serves is an
    owner-level decision, so the wave records the defect instead of changing the
    deployment.

## 9. The deliberate non-actions

| the item | why it was not done in W48 |
|---|---|
| the autonomy impulse consumer | the owner approved the scope for the **next** wave; the directive forbids implementing unapproved autonomous behaviour, and W48 implements none |
| the native build | the toolchain is absent and the CI's own attempt dies of OOM; the decision (OD-046) is about the next wave's measurement |
| `LearningStateMachine.reconcile` builds the transition id from a digest that includes `occurredAt` | the id is therefore not reproducible; it needs its own change and its own mandate |
| the auditor's import-line exemption | a real latent bug — a plain import exempts the whole file; it needs a follow-up wave |
| the snapshot retention pass is not scheduled | the tool exists and is tested; **who runs it and when is the operator's** — the calendar is a policy, not a cron |
| `.opencode/status.md` was not rewritten | it is an **untracked local file** (`git ls-files .opencode/status.md` → nothing) that still describes waves 16–34 at an Evidence Snapshot of 2026-08-23. It is not the project's status of record: that is `docs-v2/governance/PROJECT-STATE.md` (canonical, validated) and the wave report. Overwriting an untracked owner-side file is not a documentation fix, so W48 records the fact instead — the file is stale by construction and the authoritative source is named above |
| the MATRIX-specific skill triggers were not merged into a project overlay | the directive's rule is *recommended*, and the trigger table lives in the **opencode-swarm plugin bundle** — another project's asset, which the directive forbids modifying without approval. The MATRIX packages themselves are tracked (`git ls-files .opencode/skills/` → 82 files, including the `matt-pocock` root manifest of OD-040). The overlay is therefore a **next-wave** item with its own mandate, not a W48 edit |

## 10. The next wave's proposal

1. **The autonomy impulse consumer** (OD-045) — implement §5 of the ADR exactly, with
   the ten tests of §9. No external effect, no bridge to Chain A, no executor.
2. **The native-image resource budget** (OD-046) — measure the heap and the runner size
   the build actually needs, record them, and keep the capability BLOCKED until a build
   succeeds.
3. **The GPU as an optional accelerator** (OD-047) — the registry states the optional
   role; no serving-path change is required or proposed.
4. **The CI observation** — watch the `ci` run of the W48 commits; the fixes are
   committed, but the wave has not yet seen a green run.
5. **The two known holes** — the auditor's import-line exemption and the
   non-reproducible transition id.

## 11. The changed files

**85 files, +12 409 / −229 lines**, from `eca23bfd` to the commit that carries this
document (`git diff --shortstat eca23bfd..HEAD`). The groups:

| the group | the files |
|---|---|
| the workflows (a frozen zone, excepted with a recorded reason) | `.github/workflows/ci.yml`, `branch-validation.yml`, `tla.yml`; `docs-v2/quality/frozen-zone-baseline.txt` |
| the build | `build.gradle`, `matrix-core/build.gradle`, `.gitignore` |
| the formal tool | `tools/tla2tools.jar` (tracked), `tools/README.md` (the provenance) |
| the production code | `KnowledgeStore`, `WorkspaceSnapshot`, `SnapshotCoverage`, `IncompatibleSnapshotException`, `LearningWorkbenchResource`, `KnowledgeWorkbench`, `DiskBudget`, and the ten decision-path classes that got a clock or a reason |
| the test code | the shared `RepositoryPaths` helper and its users, the 12 compatibility tests, the 12 retention tests, the 4 drill-probe tests, the 8 GPU-harness tests, the new `KnowledgeClockInjectionTest`, and the 8 native classes that got a tag |
| the scripts | `scripts/gpu-benchmark.sh`, `scripts/restore-drill.sh`, `scripts/snapshot-retention.py`, `scripts/quality-gate.sh` |
| the documents | the eight W48 deliverables, the questionnaire, the runbook, and the five governance registers |

## 12. The CI record

| the workflow | the run at the preflight | the conclusion | the state now |
|---|---|---|---|
| `ci` | `42170865426` | **failure** — Build & Test `127738106005`, Quality gates `127738106021`; Web UI `127738105543` success | the causes are fixed and committed (§4.2); **the wave has not yet observed a green `ci` run** |
| `ci` (later reading) | `42181161742` at `0eac36c3` | **failure** — Build & Test: 9070 tests, 12 failed, 132 skipped; Quality gates: DOCS-1 **PASS**, the security boundary failed on its startup window | the twelve failures are diagnosed and fixed in `64f43f74`, the startup window and the missing log in the same commit; the detailed evidence is `docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.7 |
| `ci` (later reading) | `42184035743` at `63d440b1` | **failure** — Build & Test **success**; Quality gates: the application could not reach a Redis the runner does not have; Security Scan: no JDK, an invalid `JAVA_HOME`; Container Images: the published port vs. the loopback bind | all three causes are diagnosed and fixed in `bc87935c`; the evidence is `docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.8 |
| `ci` (later reading) | `42198961133` at `bc87935c` | **failure** — Build & Test, Web UI and **Quality gates are green** (the security boundary and the formal gate both hold on the clean runner); Security Scan: `bash -e` aborted the SpotBugs step and skipped the secret scan; Container Images: the api-gateway and matrix-core smoke tests are green, the minikube one had no database | both remaining causes are in the workflow's own steps and are fixed in `e0de8419`; the evidence is `docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.9 |
| `branch-validation` | `42170865265` | success | the GraalVM distribution and the version contract are fixed (`02d2a516`) |
| `Formal Specs (TLA+)` | `42170865258` | success — 15 specifications, `tools/tla2tools.jar: OK` | the tool is tracked and verified by digest in both workflows (`16a2d0c5`, `9bb6bf5c`) |
| `docs-validation` | `42167940985` (at `4084538a`) | success | path-filtered on `docs-v2/**`; no run exists at the preflight commit |
| `native` | 1 403 runs on record; the last ten are all `failure` | the OOM in the build | **BLOCKED** (OD-046) |

The per-run evidence — the log lines, the job ids and the conclusion of every step — is
in `docs-v2/engineering/W48-CI-RECONCILIATION.md` §3.

## 13. The snapshot compatibility matrix

The full matrix is in `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §2 and §3.1;
the short form:

| the payload | the target holds | the outcome |
|---|---|---|
| legacy (v1) | learning rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| legacy (v1) | observation rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| v2 without the learning family | learning rows | **REFUSED** — `INCOMPLETE_LEGACY_SNAPSHOT` |
| legacy (v1) | nothing of the missing families | **ALLOWED**, and the snapshot says what it does not carry |
| complete (v2 with the learning family) | anything | **ALLOWED** |
| a future version | anything | **REFUSED** — fail closed |
| corrupt | anything | **REFUSED** — fail closed |
| over the 8 MiB bound | anything | **REFUSED** — `SNAPSHOT_PAYLOAD_TOO_LARGE`, before the write |

## 14. The risk register and the capability registry

**The risks:** two new (`R-W48-01` the incomplete-legacy-snapshot restore — closed by
the coverage contract, with the residual stated; `R-W48-02` CI reproducibility — partly
closed), six updated, **none deleted**.

**The capability registry:** 35 rows (was 34) — the new one is `W48-NATIVE-IMAGE`,
which did not exist before W48 at all. Four rows were refreshed (`C-01`,
`W45-FAILCLOSED`, `W45-SNAPSHOT`, `W46-GPU`). The audited-FALSE finding about the GPU
is preserved as history, and the current state is appended beside it.

**The skill packages and the status file** (the directive's §10.8 and the skill-ownership
paragraph): `.opencode/status.md` is an **untracked local generated snapshot** of an
earlier wave and was deliberately NOT overwritten — the authoritative status source is
`docs-v2/governance/PROJECT-STATE.md`, and this report. The MATRIX skill packages are
tracked in the repository (`git ls-files .opencode/skills/` → 82 files; the
`matt-pocock` root manifest that OD-040 asked for is `.opencode/skills/matt-pocock/SKILL.md`),
and the generic upstream trigger table belongs to another project's plugin bundle, which
W48 does not touch. The rule that the directive *recommends* — a version-controlled
MATRIX-owned overlay that the installer merges deterministically — is therefore recorded
as a **next-wave** item rather than performed here.

## 15. The security and the chain-isolation confirmation

| the invariant | the state | the evidence |
|---|---|---|
| the live database is never a test database | **held** | the drill used `VACUUM INTO` on a read-only copy; the original's sha256 (and its `-wal`/`-shm`) are unchanged before and after |
| the security check cannot open the owner's live database | **held** | `d1f2e0fb` — the check now runs against an isolated copy and proves it |
| no implicit bridge between Chain A and Chain B | **held** | nothing in this wave touches the chain boundary; the autonomy consumer is **not implemented** |
| no new network surface | **held** | no listener, port or route was added |
| no destructive operation without a recoverable snapshot | **held** | the coverage check and the size bound both run before the erasure |
| no unapproved autonomous behaviour | **held** | the consumer is an ADR and a decision (OD-045) for the next wave |

## 11. The instructions for the next session

1. Read this document, then `docs-v2/governance/PROJECT-STATE.md`,
   `docs-v2/governance/ROADMAP.md`, `docs-v2/governance/DECISION-REGISTER.md` and
   `docs-v2/governance/OPEN-QUESTIONS.md`.
2. The active wave is **W49**, opened by OD-045 (the autonomy impulse consumer).
3. **One full `matrix-core` suite run needs a fresh owner approval** — OD-041 covered
   the W47 run only.
4. The baseline is `eca23bfd`; the wave's commits are the table in §2.
5. `bash scripts/quality-gate.sh` must PASS at **271** literal candidates — the
   baseline is never raised; a literal is extracted instead.
6. The live knowledge database is at **schema v19**, one workspace (`photosynthesis`),
   **zero** snapshot rows. The restore drill used `VACUUM INTO` on a read-only copy and
   left the original byte-identical.
