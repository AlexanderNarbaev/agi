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

## 3. What the directive asked, and what answered it

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

## 4. The owner decisions of 2026-11-06

| the decision | the answer |
|---|---|
| **OD-045** — the autonomy impulse consumer | **option A** — a proposal-creating consumer inside Chain B, implemented in the NEXT wave |
| **OD-046** — the native-image build | measure the resource cost and fix the build in the next wave; the capability stays **BLOCKED** until a build succeeds |
| **OD-047** — the role of the GPU | an **optional accelerator**, not part of the default serving path; the silent CPU fallback stays visible in the registry |

## 5. The tests, with their exact counts

| the scope | the classes | the methods | the failures | the commit |
|---|---|---|---|---|
| SCOPE-COGNITIVE-KNOWLEDGE (`io.matrix.cognitive.*`, `io.matrix.knowledge.*`) | 133 | 968 | **0** | `33d199e2` |
| SCOPE-BRAIN-RUNTIME (`io.matrix.brain.runtime.*`) | 120 | 919 (2 skipped) | **0** | `af9ba688` |
| SCOPE-NATIVE-BUILD-CONFIG | 1 | 2 | **0** | `31d8f832` |
| SCOPE-NATIVE-BINARY | 8 | 25 discovered | **not executed — BLOCKED** | `31d8f832` |

The evidence is the JUnit XML under `<module>/build/test-results/`.

## 6. The negative controls

| the control | the command | the result |
|---|---|---|
| the native scope is excluded, not silently absent | `./gradlew :matrix-core:test --tests "io.matrix.nativebin.*"` | **"No tests found for given includes"** — where the same command before `31d8f832` ran 27 tests and failed 23 |
| the native scope refuses to pretend | `./gradlew :matrix-core:nativeBinaryTest` | **exit 1** with "This scope is BLOCKED - it is not a pass, and the default JVM task does not cover it." |
| the retention tool's own checks | `python3 scripts/snapshot-retention.py --self-test` | 40 checks, 0 failures |
| the version gate's negative control | the gate against a comment-only file | the gate **fails**, as it must |
| the compatibility refusal leaves no trace | the fingerprint before and after the refusal | **identical** (6 of the 12 tests) |

## 7. What was NOT verified — the honest list

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
5. **The CI's own verdict is not yet observed.** The fixes are committed, but the wave
   did not observe a green `ci` run after them.
6. **The W47 full-suite XML was not retained**, so the per-method identity of the 23
   native failures in the W47 run itself is **derived**, not read from that run's XML.
7. **Two formal-spec mutants SURVIVED** (`Consensus-evaluate-without-quorum`,
   `KnowledgeAxes-conflict-forgets-what-it-recorded`) — known holes the gate logs, not
   new failures.

## 8. The deliberate non-actions

| the item | why it was not done in W48 |
|---|---|
| the autonomy impulse consumer | the owner approved the scope for the **next** wave; the directive forbids implementing unapproved autonomous behaviour, and W48 implements none |
| the native build | the toolchain is absent and the CI's own attempt dies of OOM; the decision (OD-046) is about the next wave's measurement |
| `LearningStateMachine.reconcile` builds the transition id from a digest that includes `occurredAt` | the id is therefore not reproducible; it needs its own change and its own mandate |
| the auditor's import-line exemption | a real latent bug — a plain import exempts the whole file; it needs a follow-up wave |
| the snapshot retention pass is not scheduled | the tool exists and is tested; **who runs it and when is the operator's** — the calendar is a policy, not a cron |

## 9. The next wave's proposal

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

## 10. The instructions for the next session

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
