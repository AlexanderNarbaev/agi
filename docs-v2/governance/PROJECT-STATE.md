# MATRIX — PROJECT STATE

**Status:** `CANONICAL` · **This document is a SNAPSHOT, not a history.** It describes the
project at one commit and is replaced, not appended to, when the state moves.
**Snapshot of:** `1f7a18a09680117b995c2e9dcf23c089b763644c` · **Date:** 2026-11-06
**Validation:** `scripts/validate-project-state.py --strict` — it checks that the commit below
is the repository's HEAD, that the wave and the baseline below are the current ones, and that
the canonical documents it names exist. It reports a lag of one commit as an OBSERVATION, not
as a defect: the snapshot names the commit it was written at, and the commit that follows it
is the one that carries the snapshot itself.
**The wave that produced this snapshot is still running.** The commit named above is the one
observed at 2026-11-06T14:05:00+03:00 (`git rev-parse HEAD`); `origin/develop` pointed at
the same commit at that moment. **The wave moves HEAD while it runs**, so a reader must
expect the repository to be ahead of the commit written here — and must not read the lag as
a defect.

---

## 1. The identity, in one paragraph

MATRIX is a **deterministic neuro-symbolic core** in which knowledge is represented as
truth tables and their compilable forms (TT / CLAUSESET / BDD), and in which the learning
path is separated from the runtime: the runtime executes only compiled artefacts, without
LLM calls and without randomness. The repository holds **two serving applications that do
not meet**, a large body of implemented but unserved research subsystems, and a decade of
wave records.

**What it is not:** it is not a general LLM agent, it is not a single cognitive cycle, and it
is not a system in which every implemented class is served. The distance between "implemented"
and "served" is the single most important fact about this repository, and it is measured in
`docs-v2/architecture/CAPABILITY-REGISTRY.yml`.

---

## 2. The state

| | |
|---|---|
| the branch | `develop` |
| the commit | `1f7a18a09680117b995c2e9dcf23c089b763644c` — the W48 commit observed at 2026-11-06T14:40:00+03:00; this snapshot is carried by the commit that follows it |
| the remote | `origin/develop` — **the same commit** at the moment of writing (the local and the remote do not diverge). The wave is still running, so the remote moves with it |
| the release version | **none.** The tags `v1.0.0` and `v16.0.0-mind` exist, but no release process is defined and the `v16.0.0-mind` tag predates three waves of work |
| the latest completed wave | **W47** — the implementation of the owner's decisions of 2026-11-05 (OD-025…OD-044). Its sequence is **thirteen changesets after** the starting W46 commit `84955ec9`, and **fourteen entries with it**, through `eca23bfd`; before it, **W46** — the consolidation (`233af984`), and **W45.2** — the owner's four architecture decisions (`a6ded233`) |
| the previous waves | W45.1 (`509d23ba`) — the crash consistency; W45.0 (`eae6003f`) — the cognitive kernel integrity; RECON-W44.0 (`ae6c017b`) — the bytecode archaeology |
| the active wave | **W48 — its commits and its own report are in the history, and the owner has answered the three questions of 2026-11-06 (OD-045, OD-046, OD-047). The wave's report is `docs-v2/engineering/W48-STATUS.md`.** **What is still open, and named:** the **23 native-binary test failures** — `BLOCKED`, the GraalVM toolchain is absent on this host, and OD-046 puts the resource budget in the next wave (`docs-v2/engineering/W48-TEST-RECONCILIATION.md`); the **impulse consumer** — **approved (OD-045) and NOT implemented**: the ADR is the scope, the implementation is the next wave's first task (`docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md`); the **CI observation** — **the five mandatory checks are GREEN on a clean runner** (`42206946030`): Build & Test, the Web UI tests, the quality gates (the security boundary and the formal gate both hold there), the security scan (the secret scan ran for the first time and found nothing) and the container images (three smoke tests). The only red job is the release path, which needs the owner's Docker Hub and Kubernetes credentials — the repository has no secrets configured (`gh secret list` is empty), which is recorded as R-W48-04; the **two formal-spec mutants** that SURVIVED (`Consensus-evaluate-without-quorum`, `KnowledgeAxes-conflict-forgets-what-it-recorded`). **The next wave is W49, opened by OD-045.** **No W48 task is claimed here as done beyond what its own committed document states** |
| the modules | **14** included in `settings.gradle`, 3 commented out, 2 outside Gradle, 1 stray orphan file |
| the serving entry points | **2** applications — the Quarkus app on **9091** (210 routes) and the brain runtime's JDK server on **8765** (28 routes) |
| the CLI entry points | **79** |
| the databases | **one canonical** SQLite file (`data/knowledge/workbench.sqlite`, the knowledge schema **v19** — `KnowledgeStore.SCHEMA_VERSION = 19`) plus chain B's own `data/mind/mind.sqlite` |

**What this snapshot re-verified, and what it carried.** Re-verified on 2026-11-06: the
branch and the remote (`git rev-parse HEAD`, `git rev-parse origin/develop` — the same
commit at the moment of writing), the wave that is active, and the schema version
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:92` → `SCHEMA_VERSION = 19`).
**Carried unchanged from the W46/W47 measurement**, because this wave did not re-measure them:
the module count, the serving entry points and their route counts, the CLI entry point count
and the release tags. A row that was not re-measured is not a new claim; it is the previous
measurement, and it is marked here rather than silently restated.

---

## 3. The test baseline — commit-specific, and it is not one number

**The full `matrix-core` suite is not run in this repository's practice.** It needs the
owner's approval (the OOM risk; `AGENTS.md` §Тестирование). The baseline is therefore a set
of **scopes**, each verified on a named commit.

| the scope | the command | the result | the commit | the environment |
|---|---|---|---|---|
| the cognitive kernel and the epistemic store | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks` | **128 classes, 910 tests, 0 failures, 0 skipped** | `a6ded233` | JVM, OpenJDK 25.0.4.1 |
| the W43 regression scope | `./gradlew :matrix-core:test --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*" --rerun-tasks` | **98 classes, 1036 tests, 0 failures** | `a6ded233` | JVM |
| the brain runtime | `./gradlew :matrix-brain-runtime:test --rerun-tasks` | **119 classes, 914 tests, 0 failures, 2 skipped** | `a6ded233` | JVM |
| the API gateway | `./gradlew :matrix-api-gateway:test --rerun-tasks` | **43 classes, 240 tests, 0 failures** | `a6ded233` | JVM |
| the chain isolation gate | `bash scripts/w45-chain-isolation-check.sh` | **16 checks, 0 failed** | `a6ded233` | integration — a real chain B server |
| its negative control | `bash scripts/w45-chain-isolation-selftest.sh` | **6 checks, 0 failed** | `a6ded233` | integration — the isolation is broken on purpose |
| the process-kill journey | `bash scripts/w45-cognitive-recovery-journey.sh` | **37 checks, 0 failed** | `a6ded233` | integration — 3 real SIGKILLs |
| the quality gate | `bash scripts/quality-gate.sh` | **271 literal candidates at the baseline of 271** | `a6ded233` | gate |
| the security boundary | `bash scripts/security-boundary-check.sh` | **0 violations** | `a6ded233` | gate |
| the numerical-tolerance scope (the fixed flaky property test) | `./gradlew :matrix-core:test --tests "io.matrix.consciousness.CausalEmergencePropertyTest"` | **7 tests, 0 failures, 0 errors, 0 skipped** | `241af1e1` | JVM |
| the wall-clock compliance scope (the 21 adjudicated sites) | `./gradlew :matrix-core:test --tests "io.matrix.consciousness.Exp228ComplianceTest"` | **1 test, 0 failures, 0 errors, 0 skipped** | `4adbc664` | JVM |
| the disk-budget scope (the unit defect) | `./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.DiskBudgetTest"` | **10 tests, 0 failures, 0 errors, 0 skipped** | `af9ba688` | JVM |
| the GPU benchmark harness's own arithmetic | `./gradlew :matrix-core:test --tests "io.matrix.benchmark.GpuBenchmarkHarnessTest"` | **8 executions, 0 failures, 0 errors** | `75937934` | JVM |

**The four W48 rows are quoted from the wave's own committed records**, not re-run here:
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §3 (the two test scopes),
`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.1 (the disk-budget scope) and
`docs-v2/engineering/W48-GPU-BENCHMARK.md` §9 (the harness's arithmetic).

**Three things this baseline does NOT say:**

- **The total test population is unknown.** The W44 archaeology measured 9816 executions with
  24 failures during the W44 run; that is a **floor**, not a total, and it is a year old.
- **The native-image classes are neither passing nor failing.** Eight classes (23 failures)
  depend on GraalVM, which is **absent on this host** — `matrix-core/build/native` <!-- no-link: a build artefact, not a repository file --> has never
  existed here. **They are never counted as passing.** The true W44 split is 8 native-image
  classes / 23 failures **plus 1 ordinary JVM failure**
  (`io.matrix.consciousness.Exp228ComplianceTest`). **In W48 they became a scope of their
  own** (`31d8f832`): the default JVM `test` task excludes them, and
  `./gradlew :matrix-core:nativeBinaryTest` **refuses to run** with an explicit message while
  the executable is absent. Their state is **BLOCKED**, and it is not a pass
  (`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §5).
- **A stale count is not a baseline.** The per-scope registry is
  `docs-v2/engineering/TEST-CATALOGUE.yml`, and each row names its commit.

---

## 4. The known blockers

| the blocker | the kind | what it blocks | the owner |
|---|---|---|---|
| the **workspace reload** erases the evidence of an observation while keeping its case, its disposition and its adjudication history | an architecture decision (D3-c) | the meaning of "replace a workspace" | **the owner** |
| the **writer lock** does not resolve a symlinked path alias, so two writers can open the same file through different paths | a defect inside an approved contract (D4-a) | the guarantee the lock exists for | the coordinator (Level 2 — it touches the database-ownership boundary) |
| the **gateway serves a stub by default** (`MATRIX_MODE` defaults to `stub`), while the canonical declaration says chain B runs `TrueMindCycle` | an owner decision (F-01) | the truth of the serving declaration | **the owner** |
| chain A opens **three ports**, two of them (9093 multicast, 9094 TCP) undeclared in any serving record | an owner decision (F-02) | the truth of the serving declaration; the D2 "no new network exposure" claim | **the owner** |
| **no whole-database backup exists** — the SQLite backup API appears nowhere, and the runbook has no backup procedure | a missing implementation | the recoverability of a corruption or a bad migration | the coordinator (Level 1) |
| the **GPU is an OPTIONAL ACCELERATOR, and the speed-up is measured but the digit is bounded** — the ONNX CUDA registration fails because the library path is a JVM system property and a native `dlopen` consults `LD_LIBRARY_PATH`; the diagnostic that reports the active provider is a **hardcoded string** | a performance defect + a lying diagnostic | the truth of the GPU claim | **decided by the owner on 2026-11-06 — OD-047**: the GPU is an **optional** accelerator, not part of the default serving path; the measurement is kept, the silent CPU fallback stays visible in the registry, and no deployment is obliged to provide the CUDA libraries. The measurement: `docs-v2/engineering/W48-GPU-BENCHMARK.md` measures **12.06× / 11.68× / 11.47×** over three runs of MATRIX's own forward call (`75937934`), the end-to-end path at **12.69×**, and the point call **4× slower** on the GPU. The historical **12.6×** does **not** reproduce and stays **HISTORICAL**, bounded by that measurement. **The serving path does not change; the hardcoded diagnostic still stands.**
| the **quality gates are not blocking** — they live in a CI job named *«Quality gates (can fail)»*, and `STANDARDS-MATRIX.md` does not say so | a documentation and process defect | the trust a gate carries | the owner |
| the **readiness probe is permanently 503** — the reactive PostgreSQL datasource gates it and **no production code reads it** | an operational blocker | the CI smoke test; every deployment manifest | the coordinator |

---

## 5. The pending owner decisions

The full register, with the competing explanations and the experiment that would resolve each,
is `docs-v2/governance/OPEN-QUESTIONS.md`. The four that block the next work:

| the ID | the question | what a decision unblocks |
|---|---|---|
| **Q-W46-01** | the destructive reload's consent parameter: `?replace=true` (as implemented) or the `replaceExisting: true` body field of the target contract? | the API shape of a destructive operation |
| **Q-W46-02** | a dry-run / preview mode for the destructive reload? | a new HTTP surface on a destructive path |
| **Q-W46-03** | what may a workspace replacement erase? | the erasure set of the workspace replacement |
| **Q-W46-13** | the retention policy for the append-only observation, adjudication and request-event histories | the unbounded growth of the audit trail |

**One of the five this section used to list is now CLOSED and is no longer pending:**
**Q-W46-07** (the GPU path) was answered on 2026-11-06 — **OD-047**: the GPU is an **optional
accelerator**, not part of the default serving path. The register records it as `CLOSED`
(`docs-v2/governance/OPEN-QUESTIONS.md`). **It is not re-opened here.**

---

## 6. The next recommended work

The canonical roadmap is `docs-v2/governance/ROADMAP.md`. The W46 tasks that this section
used to list (TASK-W46-2…TASK-W46-6) are **done** and live in §4.1 of the roadmap. **What
is open now, in order** — this is the next wave's list, and it is the wave's own proposal
(`docs-v2/engineering/W48-STATUS.md` §9):

1. **The autonomy impulse consumer** — **approved (OD-045, 2026-11-06, option A) and NOT
   implemented.** The scope is `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md`
   §5 and the ten tests of its §9; the roadmap task is **TASK-W48-03**. **Until it is
   written, the truth is unchanged: the scheduler accepts impulses, nothing consumes them.**
2. **The native-image resource budget** — **OD-046**: measure the heap and the runner size
   the build actually needs, record them, and keep the capability **BLOCKED** until a build
   succeeds. The 23 native-binary failures stay `BLOCKED`
   (`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §4).
3. **The GPU as an optional accelerator** — **OD-047**: the registry states the optional
   role; **no serving-path change is required or proposed**, and the serving path does not
   change.
4. **The CI observation** — the fixes of the W48 wave are committed, but **no green `ci` run
   has been observed after them** (`docs-v2/engineering/W48-CI-RECONCILIATION.md` §3).
5. **The two surviving mutants of the formal gate** — recorded findings, not failures:
   `Consensus-evaluate-without-quorum` and `KnowledgeAxes-conflict-forgets-what-it-recorded`
   (`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.5).
6. **The wave's own report** — `docs-v2/engineering/W48-STATUS.md`. It is committed; this
   snapshot names it, and does not restate it.

---

## 7. The authoritative records — where each subject is written down

**One subject, one place.** If a subject is listed here and the document it names disagrees
with this snapshot, the document wins for its subject and this snapshot is wrong — and the
validator says so.

| the subject | the document |
|---|---|
| the principles | `CONSTITUTION.md` (normative) — and the unfilled template duplicate `constitution.md` <!-- no-link: removed on 2026-11-05 by the owner's decision; the file is gone --> was **removed on 2026-11-05** — the normative file is untouched |
| the session rules | `AGENTS.md` (normative, protected) |
| the decisions | `docs-v2/governance/DECISION-REGISTER.md` |
| the capabilities | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` |
| the serving architecture | `docs-v2/architecture/RUNTIME-TOPOLOGY.md` — the canonical runtime topology (W46.2); the evidence is `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` |
| the persistence | `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md` — the canonical persistence map (W46.2); the evidence is the same truth map §4 |
| the module graph | `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` |
| the roadmap | `docs-v2/governance/ROADMAP.md` |
| the risks | `docs-v2/governance/RISK-REGISTER.md` |
| the open questions | `docs-v2/governance/OPEN-QUESTIONS.md` |
| the agent protocol | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` |
| the document statuses | `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` |
| the machine | `docs-v2/operations/HARDWARE-PROFILE.md` (stale — the fresh profile is `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md`; the regeneration is TASK-W46-5) and `docs-v2/operations/RESOURCE-BUDGETS.md` |
| the procedures | `docs-v2/operations/RUNBOOK.md` and `docs-v2/operations/RECOVERY-RUNBOOK.md` (the backup and the restore — W46.2) |
| the test scopes | `docs-v2/engineering/TEST-CATALOGUE.yml` |
| the waves | `docs-v2/waves/INDEX.md` — the chronological work log (W46.2) — and `docs-v2/engineering/W4*.md`, the record of what was done, **immutable** |
| the research and the negative results | `docs-v2/research/RESEARCH-INDEX.md` and `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` (W46.2) |
| the documentation navigation | `README.md` and `docs-v2/INDEX.md` — both reconciled in W46.2 |
| the agent's skills | `docs-v2/governance/SKILL-INVENTORY.md` (what exists) and `docs-v2/governance/SKILLS.md` (what is recommended) |
| the end of a session | `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` |
| the agent's amendment | `docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md` — **the proposal; `AGENTS.md` itself is untouched** |

---

## 8. The mission's thirteen questions, and the document that answers each

**This is the acceptance test of the governance structure: a new agent must be able to answer
all thirteen from these documents alone.**

| # | the question | the document that answers it |
|---|---|---|
| 1 | What is MATRIX today? | §1 and §2 of this document; the identity in `README.md` |
| 2 | What is actually implemented? | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` — the `implementation` axis |
| 3 | What is reachable through a real serving path? | the same registry — the `serving_path` and `serving_chain` fields; the topology in `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` |
| 4 | What is tested, measured and independently verified? | the same registry — the `verification` axis and the `test_evidence`; the denominators in `docs-v2/engineering/TEST-CATALOGUE.yml` |
| 5 | What is experimental, inactive, obsolete, rejected or merely proposed? | the same registry — the `operational` axis; the rejected directions in `docs-v2/governance/ROADMAP.md` §8 |
| 6 | Which architectural decisions are authoritative? | `docs-v2/governance/DECISION-REGISTER.md` |
| 7 | Which research findings and failed hypotheses must be preserved? | `docs-v2/research/HYPOTHESES.md` and `docs-v2/research/HYPOTHESES-NEW.md`; the negative-results index is TASK-W46-7 |
| 8 | What are the current goals, blockers and next engineering tasks? | §6 of this document; `docs-v2/governance/ROADMAP.md`; the blockers in §4 |
| 9 | What may an autonomous agent change without asking the owner? | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3 — the autonomy levels |
| 10 | What requires explicit owner approval? | the same document §3.2 — the Level 3 list |
| 11 | What resources are actually available on the owner's machine? | `docs-v2/operations/RESOURCE-BUDGETS.md`; the evidence in `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` |
| 12 | How should an agent resume work after context loss? | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1 — the twelve steps; and the bootstrap block in `docs-v2/engineering/W46.0-FINAL-REPORT.md` §H |
| 13 | How should every session leave enough evidence for the next one? | the same document §6 — the handoff template |

**The five files an ordinary session reads:** this document · `governance/ROADMAP.md` ·
`governance/DECISION-REGISTER.md` · `governance/OPEN-QUESTIONS.md` · the subsystem's own
source and tests. **The history is not read unless the task changes an architectural
assumption.**

---

## 9. The owner's working tree — the set has changed, and the change is a fact

**This section was rewritten on 2026-11-06, against the observed tree.** The W47 snapshot
named **eleven** owner entries that were *uncommitted* at that time. **At the commit this
snapshot names, eight of them are tracked in git** — they were committed by the waves that
followed, and they are still the owner's:

`.gitignore` · `.opencode/todo.md` · `.opencode/w37-plan.md` · `AGENTS.md` ·
`opencode.json` · `.codex/` · `.opencode/opencode-swarm.json` · `.veai/` (30 tracked files)

The two that are still outside git are the two `.gitignore` excludes:
`/.opencode/goals/` (`.gitignore:116`) and `/.opencode/tmp/` (`.gitignore:117`). The deletion
of `opencode.json.bak` is **gone** — the file is no longer there to delete, and it is not in
the index (`git ls-files opencode.json.bak` → empty). The local journal
`wal/SESSION_WAL.md` is **tracked**, as it has been since the early waves; only
`wal/state.yaml` is excluded and does not exist <!-- no-link: the file is excluded by .gitignore and does not exist -->.

**The working tree carries the W48 wave's own working files and nothing of the owner's.**
At 2026-11-06T10:29:32+03:00 `git ls-files -m -o --exclude-standard` returned **seven**
entries, all of them W48's: the six documents this snapshot corrects
(`docs-v2/engineering/W47-STATUS.md`, `docs-v2/engineering/W45.2-STATUS.md`,
`docs-v2/governance/PROJECT-STATE.md`, `docs-v2/governance/ROADMAP.md`,
`docs-v2/specifications/SPEC-015-autonomy-impulses.md`,
`docs-v2/specifications/SPEC-016-decentralized-digests.md`) and one more that the wave's
own agent is still writing, `docs-v2/engineering/W48-CI-RECONCILIATION.md`. **The set
changes as the wave runs**; what does not change is that **no owner entry is in it**.

**A session that finds a different set of owner entries is not a failure — it is a fact that
the owner worked in between.** Record the set; do not "restore" it.
