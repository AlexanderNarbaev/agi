# MATRIX — PROJECT STATE

**Status:** `CANONICAL` · **This document is a SNAPSHOT, not a history.** It describes the
project at one commit and is replaced, not appended to, when the state moves.
**Snapshot of:** `64b1657a` · **Date:** 2026-11-11
**Validation:** `scripts/validate-project-state.py --strict` — it checks that the commit below
is the repository's HEAD, that the wave and the baseline below are the current ones, and that
the canonical documents it names exist. It reports a lag of one commit as an OBSERVATION, not
as a defect: the snapshot names the commit it was written at, and the commit that follows it
is the one that carries the snapshot itself.
**The wave that produced this snapshot is running.** The commit named above is the one
observed at 2026-11-11 (`git rev-parse HEAD`); `origin/feature/w53-distributed-cognition`
points at the same commit, and **the wave's working tree is ahead of it** — the rounds 41 to
55 are committed nowhere, and the wave's pull request is not opened. **The wave moves HEAD
while it runs**, so a reader must expect the repository to be ahead of the commit written
here — and must not read the lag as a defect.
**This snapshot replaces the one of 2026-11-07.** That one named W50 as the active wave; W50
is merged (pull request #47, squash `67b3410d`, 2026-11-07) and W51 is merged (pull request
#48, squash `3ef8a5c8`, 2026-11-09). The rows that were re-measured say so; the rows that
were carried say that too, and the rows whose number was measured by a different method
carry the method with them, because a number without its method is a number nobody can
reproduce.

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

**What changed in W51:** the two serving applications do not meet, but the parts of MATRIX
now interact through **one governed crossing** — the bridge, chain B → chain A — and the
cognitive cycle is a real runnable loop with all six memory roles, a reflection that
produces decisions, and a plugin architecture with a creator. **The distance did not close:
it moved.** The bridge, the canonical contracts, the world-state memory and the reflection
are **served**; the shared runtime, the plugin system, the meta-memory and the native
boundary are **not** — their only caller today is the wave's demonstration. The registry says
so on each of those rows, and §2 of this snapshot repeats it.

**What changed in W53:** the system's **procedural memory is no longer empty**. When the
cognitive runtime's model settles on exactly one rule, the system writes the procedure down
— with the standing it actually has, the episode it was learned in, and a note that says
what the agreement rests on — and a later cycle **consults that record and names it**, which
is the mandate's acceptance point 11 read literally. The reason the earlier wave refused to
write anything (the lamp world's input domain is exhausted once the model settles) is now
**proved in a test** rather than asserted: with V inputs the space holds 2^(2^V) candidates,
each distinct assignment eliminates half of them, and the model settles only after every
assignment the domain has. One epistemic rule changed — a supersession now requires a scoped
denial rather than a better-supported claim with lexically similar words — and the whole
29-case evaluation was re-run with it in place: **18 of 29, unchanged, the same eleven
failures**. The change is therefore not a score-motivated one, and the report names what it
does not fix: the denial's own scoping is still lexical, and the eight citation failures need
a match that does not require a shared rare term.

**What changed in W52:** the two serving applications **now talk in both directions** — chain
A's epistemic core offers what it has established and chain B's brain takes it into its own
memory, under the same signature, the same capability check, the same budget, the same
idempotency key and the same audit as the first direction. The canonical contracts are no
longer a vocabulary the runtime never speaks: the cognitive cycle produces the canonical form
of itself, and a test asserts that every contract the mandate names has a producer. The
system's profile of its own methods is durable, so a process that starts knows what its
methods cost before it has run any of them. **The epistemic evaluation still reads 18 of 29**
— the wave measured three more configurations of the relevance floor and **left the
threshold where it was**, because every configuration that made the answers better made the
abstentions worse.

---

## 2. The state

| | |
|---|---|
| the branch | `feature/w53-distributed-cognition` — the W53 wave's own branch. `develop` and `main` are **not touched** by the wave: `develop` is at `375ed3f3` and `main` at `7709dc9e` |
| the commit | `64b1657a` — the W53 wave's pushed head, observed at 2026-11-11 after the owner authorized the wave's commits; this snapshot is carried by that commit. **The wave's later rounds are not in it**: rounds 41 to 66 are in the working tree and await the owner's word for the commit and the push |
| the remote | `origin/feature/w53-distributed-cognition` — at the same commit as the local **head** (`64b1657a`), but **the local working tree is ahead of it**: the wave's rounds 41 to 66 are committed nowhere, and the wave's pull request is not opened. The wave is still running, so the remote moves with it. `origin/main` is `7709dc9e` — the **squash merge of pull request #49** (W52), merged by the owner on 2026-11-09; before it `3ef8a5c8` was the squash of pull request #48 (W51), `67b3410d` the squash of pull request #47 (W50) and `79045c57` the squash of pull request #44 (W49) |
| the release version | **none.** The tags `v1.0.0` and `v16.0.0-mind` exist, but no release process is defined and the `v16.0.0-mind` tag predates three waves of work |
| the latest completed wave | **W52** — the bidirectional knowledge exchange over the governed bridge: the canonical model extended, the two directions of the bridge, the versioning and the idempotency. It is **merged into `main`**: pull request #49, squash `7709dc9e`, 2026-11-09, **merged by the owner** on the same day. Before it, **W51** — the unified cognitive runtime, the world model and the safe self-extension, merged into `main` as pull request #48, squash `3ef8a5c8`, 2026-11-09, **merged by the owner** on the same day. Its report is `docs-v2/engineering/W51-STATUS.md`, its audit is `docs-v2/engineering/W51-AUDIT.md`, its architecture map is `docs-v2/engineering/W51-ARCHITECTURE-MAP.md` and its decisions are **CD-020 … CD-025** in `docs-v2/governance/DECISION-REGISTER.md`. Before it, **W50** — the local evidence-grounded demonstrator (pull request #47, squash `67b3410d`, 2026-11-07), **W49** (pull request #44, squash `79045c57`, 2026-11-06) and **W48** — the truth of the evidence |
| the previous waves | **W50** — the local evidence-grounded demonstrator: a bounded, licensed corpus in one domain (plate tectonics) — 2 seed documents and a 7-document pre-cached research pool, every file carrying its licence, its revision and its sha256 in a manifest the import verifies; the seed import produced **348 claims** and the bounded acquisition cycle **3 pool documents and 324 claims**; the four-turn demonstration ran on the packaged jar: Q1 answered from **153 claims of 2 sources**, Q2 from **25 claims of 4 sources**, Q3 a **CONTRADICTED** verdict that names the disputed premise and the contradicting claim with `promoted=false`, Q4 applied **exactly one supersession**; the teacher stays off (OD-049) with **0 teacher invocations** measured. Its report is `docs-v2/engineering/W50-STATUS.md`. Before it, W48 — the CI reconciliation and the native budget; W47 — the owner's four architecture decisions (`a6ded233`); W45.1 (`509d23ba`) — the crash consistency; W45.0 (`eae6003f`) — the cognitive kernel integrity; RECON-W44.0 (`ae6c017b`) — the bytecode archaeology |
| the active wave | **W53 — Distributed Cognition, Durable Task Execution and Closed-Loop Collaboration.** PR #49 was **merged by the owner on 2026-11-09** (`7709dc9e`), so the wave that follows it starts from the reconciled state: the reusable-procedure wave and W52 are in `main`. The wave works on the branch **`feature/w53-distributed-cognition`**, cut from `origin/develop`; it does not touch `main` and it does not touch the merged pull request. Its objective: chain A assigns a bounded cognitive task to chain B, chain B executes it through its own production path, the result returns as an authenticated report, and chain A evaluates that report before it affects anything. **The wave is in progress.** Its task list is §4.13 of `docs-v2/governance/ROADMAP.md`, and its design decision is `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md`. The baseline it started from is `docs-v2/engineering/W53-BASELINE.json`. |
| the modules | **14** included in `settings.gradle`, 3 commented out, 2 outside Gradle, 1 stray orphan file |
| the serving entry points | **2** applications — the Quarkus app on **9091** and the brain runtime's JDK server on **8765**. **Measured 2026-11-09:** chain A carries **205** HTTP method endpoints (the count of the `@Path` annotations that carry an HTTP method) and chain B's gateway registers **34** route contexts (the count of the distinct `createContext` paths — 33 of them literal and one, the second direction of the bridge, named by the constant `io.matrix.api.bridge.KnowledgeBridge.KNOWLEDGE_PATH`). The earlier figures in this snapshot — 210 and 28 — were measured by a different method and are not comparable; the method is written with each number here so that the next reader can reproduce it. |
| the CLI entry points | **13** `@Command` classes in chain A's two modules, measured 2026-11-09 by the count of the classes that carry the `@Command` annotation (`git grep -lE "@Command" -- matrix-core/src/main/java matrix-brain-runtime/src/main/java`). The previous snapshot said **12** and the method it named — the count of the annotations — gives **15** occurrences across the same 13 classes; the number here is the one the stated method reproduces, and the earlier figures of 79 and 12 were replaced rather than restated. |
| the databases | **one canonical** SQLite file (`data/knowledge/workbench.sqlite`, the knowledge schema **v19** — `KnowledgeStore.SCHEMA_VERSION = 19`) plus chain B's own `data/mind/mind.sqlite`. The W50 demonstration ran on that canonical file and left **687 claims** in each of its two workspaces; the claims carry the workspace id, but the `workspace` table itself holds no row — which is why the status route's workspace list comes back empty (Q-W50-03) |

**What this snapshot re-verified, and what it carried.** Re-verified on 2026-11-11: the
branch, the head and the remote (`git rev-parse HEAD`, `git rev-parse
origin/feature/w53-distributed-cognition` — the same commit, with the working tree ahead of
it), the merge of W52 into `main` (the squash `7709dc9e`), and the wave that is active.
Re-verified on 2026-11-07: the merge of W49 into `main` (the squash `79045c57`), the
wave that was active then, and the schema version
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
| the W50 acquisition and premise scope | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.acquisition.*" --tests "io.matrix.knowledge.PremiseAssessmentTest" --rerun-tasks` | **11 classes, 60 tests, 0 failures, 0 errors, 0 skipped** | `71b5b5c6` | JVM |
| the W51 wave's whole scope | `./gradlew :matrix-core:test --tests "io.matrix.contract.*" --tests "io.matrix.plugin.*" --tests "io.matrix.runtime.*" --tests "io.matrix.bridge.*" --tests "io.matrix.learning.env.*" --tests "io.matrix.nativekernel.*" --tests "io.matrix.w51.*" --tests "io.matrix.safety.*" --tests "io.matrix.knowledge.*" --tests "io.matrix.benchmark.w51.*" --tests "io.matrix.cognitive.*" --rerun --offline` | **1323 tests, 0 failures, 0 errors, 0 skipped** | `83fa911c` | JVM, OpenJDK 25 |
| the chain isolation gate, after the bridge | `bash scripts/w45-chain-isolation-check.sh` | **22 checks, 0 failed** | `83fa911c` | integration — a real chain B server |
| its negative control, after the bridge | `bash scripts/w45-chain-isolation-selftest.sh` | **12 checks, 0 failed** | `83fa911c` | integration — the isolation is broken on purpose |
| the cross-process bridge demonstration | `bash scripts/w51-bridge-demo.sh` | **10 checks, 0 failed** | `83fa911c` | integration — two processes, two databases |
| the end-to-end demonstration | `./gradlew :matrix-core:w51Demonstration` | **15 of 15 acceptance points**; the trace is `docs-v2/engineering/W51-DEMONSTRATION.json` | `83fa911c` | JVM |
| the W50 epistemic evaluation, before and after the W51 fixes | `python3 scripts/w51-epistemic-eval.py` | **12/29 → 18/29**, the baseline reproduced first on the same runner in a clean worktree at `a098f283` | `83fa911c` | evaluation — 29 cases, per-case report |
| the quality gate, after the wave | `bash scripts/quality-gate.sh` | **271 literal candidates at the baseline of 271** | `83fa911c` | gate |

**The four W48 rows are quoted from the wave's own committed records**, not re-run here:
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §3 (the two test scopes),
`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.1 (the disk-budget scope) and
`docs-v2/engineering/W48-GPU-BENCHMARK.md` §9 (the harness's arithmetic).

**The W50 row is the wave's own scope**, counted from its retained JUnit XML under
`matrix-core/build/test-results/test/`: the eleven classes of `io.matrix.knowledge.acquisition.*`
plus `io.matrix.knowledge.PremiseAssessmentTest`. It is a **scope**, not the project's total
test population — the full suite is still not run here (R-W46-10).

**The W51 rows are the wave's own scope**, measured on 2026-11-09 and counted from the
retained JUnit XML: **1323 tests across eleven scopes**, 0 failures, 0 errors, 0 skipped. They
are a **scope** too, and a wide one — the contract, the bridge, the plugin, the runtime, the
learning environment, the native boundary, the safety matrix, the knowledge store, the
benchmark and the cognitive kernel. **It is still not the project's total population**, and
the full suite is still not run here.

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
| the **static analysis produces no evidence** — the pinned SpotBugs plugin 6.1.7 resolves the engine 4.9.3, whose ASM 9.7.1 reads class-file major 68 and the code is major 69; the run yields **6 645** `Unsupported class file major version` lines and **no** findings | an inherited infrastructure limitation, **measured 2026-11-09** | nothing in the cognitive architecture — the gate is not a prerequisite of the deliverable | the owner (Q-W49-06) |
| the **secret scan exits 183** — TruffleHog finishes with **0 verified secrets** and **8 unverified** PostgreSQL-like findings, all documentation placeholders in `.veai/skills/**`; the job is red on the action's own exit code, not on a finding | an inherited infrastructure limitation, **measured 2026-11-09** | the job's green state, not the repository's secrets | the coordinator |
| the **ShellCheck job is red on the repository's other scripts** — **182** findings at the wave's head, where the base commit `a098f283` had **210**; the wave's own four scripts have **zero**. The job is red on `main` at `67b3410d`, `79045c57` and `7a0ed0cc` as well | an inherited infrastructure limitation, **measured 2026-11-09** | the job's green state, not the wave's scripts | the coordinator |
| the **workspace has no identity for a subject** beyond the words two sentences share — its concepts are scoped to the sentence they came from, and its scope and context are the sentence's own beginning | a missing capability | the eight citation failures (the answering claims rank immediately below the relevance floor, 10th of 161, because the question's rare terms carry the IDF weight and the claims do not contain them) and the two revision false positives (the denial's scoping is lexical) | the coordinator (Level 1 — a retriever that matches without a shared rare term, or a subject identity) |
| the **native image runs out of memory** — `native.yml:28-34` sets no memory limit, `-J-Xmx8g` with `--gc=epsilon` is a hard wall, and the build dies with `OutOfMemoryError: Java heap space` | an inherited infrastructure limitation, **measured 2026-11-09** | nothing in the cognitive architecture — the mandate says the native image is not a prerequisite | the owner (Q-W49-05) |

---

## 5. The pending owner decisions

The full register, with the competing explanations and the experiment that would resolve each,
is `docs-v2/governance/OPEN-QUESTIONS.md`. The owner questions that block the next work — the
three this wave raised are at the bottom:

| the ID | the question | what a decision unblocks |
|---|---|---|
| **Q-W46-01** | the destructive reload's consent parameter: `?replace=true` (as implemented) or the `replaceExisting: true` body field of the target contract? | the API shape of a destructive operation |
| **Q-W46-02** | a dry-run / preview mode for the destructive reload? | a new HTTP surface on a destructive path |
| **Q-W46-03** | what may a workspace replacement erase? | the erasure set of the workspace replacement |
| **Q-W46-13** | the retention policy for the append-only observation, adjudication and request-event histories | the unbounded growth of the audit trail |
| **Q-W50-01** | does the repository's own AGPL-3.0 licence interact with the share-alike of the CC BY-SA 4.0 Wikipedia extracts the corpus embeds? | whether the corpus may ship inside the repository as it does |
| **Q-W50-02** | the import stores the source text as the source excerpt, and the audit trace of an imported claim does not carry the byte offset of the quoted span | the auditability of every imported claim |
| **Q-W50-03** | the Workbench status route reports the database path and its size but leaves the workspace list and the counts empty | the observability of the demonstrator's own workspace |

**The first three rows are CLOSED in the register** (OD-025, OD-026 and OD-027, answered on
2026-11-05) and are kept here for the record; only **Q-W46-13** and the three W50 questions
are pending.

**One of the five this section used to list is now CLOSED and is no longer pending:**
**Q-W46-07** (the GPU path) was answered on 2026-11-06 — **OD-047**: the GPU is an **optional
accelerator**, not part of the default serving path. The register records it as `CLOSED`
(`docs-v2/governance/OPEN-QUESTIONS.md`). **It is not re-opened here.**

---

## 6. The next recommended work

The canonical roadmap is `docs-v2/governance/ROADMAP.md`. The tasks this section used to
list (TASK-W46-2…TASK-W46-6, TASK-W48-01…TASK-W48-03 and TASK-W49-01…TASK-W49-10) are
**done** and live in §4.1, §4.7 and §4.8 of the roadmap; the W51 tasks are in §4.10 and are
done as well, with one exception — TASK-W51-11, the closeout, was open at the moment this
snapshot was taken and is closed by the merge. **What is open now, in order:**

1. **The W53 tasks** — §4.13 of the roadmap: the baseline is reproduced and recorded; the
   distributed boundary is decided in an ADR; the typed remote task contracts exist and are
   tested; the bridge carries the two new rights; chain A has one coordinator; chain B
   executes its real path; the execution is durable on both sides; chain A adjudicates the
   report; the isolation gate grows; the cross-process demonstration runs; the method
   reliability is estimated honestly; the distributed path is measured; the research is
   persisted; and the closeout writes the report, the audit and the architecture map.
   Three things were added in the wave's last rounds, each with its own evidence: the
   delegation reaches the **reflection** when the task runs inside a cycle (a report that
   did not settle the proposition becomes an uncertainty, and the reflection turns it into
   a `LEAVE_UNRESOLVED` decision that names the request); both processes **report
   themselves** — chain A answers its health endpoint, chain B names the capability it can
   really execute — and the demonstration checks that the capability chain A used is one
   the peer reports as active; and the peer's duplicate guard was found to stand in
   **three** places, so the test now counts the brain's calls instead of trusting the
   answer alone. And the **last crash boundary** was found to be a hole rather than a
   covered case: a dispatch whose answer was on record but whose verdict was not — the
   process died between receiving the answer and judging it — was in no queue at all, so
   the task stayed unjudged for ever. The rule is now stated in the store where the truth
   is: a dispatch is unfinished while it owes chain A a decision (CD-036), and the
   cancelled and the timed-out dispatch, whose answer is deliberately kept unjudged, are
   finished and are left alone.
   **The previous wave's tasks** — §4.12 of the roadmap, all closed with their evidence:
   the procedure
   of the settled model is written down with the standing it actually has; a later cycle
   consults it and names it; the reason an independent validation is unreachable in the
   micro-world is proved in a test instead of asserted; the canonical `Procedure` and `Skill`
   are filled by the runtime's own record; and one epistemic rule was changed and measured —
   the score did not move, and the report says so.
2. **The release pull request** — the lead opens `develop` → `main` at the end of the wave,
   and **an agent does not merge it**. The pull request is a prepared path, not a release;
   the merge is the owner's decision (Q-W49-01). **W52's pull request (#49) is open and
   unmerged; W53's pull request follows it. The owner merged W50's pull request
   (#47) and W51's (#48) on 2026-11-07 and 2026-11-09.**
3. **The three W50 owner questions** — the licence interaction of the corpus inside the
   repository (Q-W50-01), the audit trace of an imported claim (Q-W50-02) and the status
   route's empty workspace list and counts (Q-W50-03). They are in §5 above and in the
   register, and W51 did not close them.
4. **The honest limits of the demonstrator** — the false-premise judgement is lexical, not
   semantic (R-W50-01); the extractor's corpus vocabulary is a heuristic verb list
   (R-W50-02); the answering path proves zero LLM calls by instrumentation, and the
   assertion check is itself a deterministic lexical pass (R-W50-03). They are named, not
   hidden.
5. **The W49 owner questions, unchanged** — the release path's missing credentials
   (R-W48-04), the static-analysis policy for the 1 414 findings of the Java 25-capable
   engine (Q-W49-06), and the native build's larger runner and one missing dependency
   (Q-W49-05).
6. **The wave's own report** — `docs-v2/engineering/W51-STATUS.md` for the wave that is
   merged, and the W52 report when the wave closes. This snapshot names them and does not
   restate them.

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
| the W50 demonstrator | `docs-v2/engineering/W50-STATUS.md` (the wave's own report) and `docs-v2/engineering/W50-LOCAL-MODEL.md` (the measured local-model evaluation); the demonstration itself is `docs-v2/demos/local-learning-loop/README.md`, its four prompts are `docs-v2/demos/local-learning-loop/OWNER-SEQUENCE.md`, and the expected answers are `docs-v2/demos/local-learning-loop/ANSWER-KEY.md` — deliberately kept outside the application |
| the W51 wave | `docs-v2/engineering/W51-STATUS.md` (the wave's own report) · `docs-v2/engineering/W51-AUDIT.md` (the audit and the classification of nineteen subsystems) · `docs-v2/engineering/W51-ARCHITECTURE-MAP.md` (the actual runtime interactions, with the file:line references) · `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` (the inter-chain model and the preserved boundaries) · `docs-v2/engineering/W51-PERFORMANCE.md` (the measured hot paths) · `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.md` and `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md` (the two measurements the mandate names) · `docs-v2/engineering/W51-DEMONSTRATION.json` (the machine-readable trace) |
| the W53 distributed-cognition wave | `docs-v2/engineering/W53-STATUS.md` (the wave's report) · `docs-v2/engineering/W53-AUDIT.md` · `docs-v2/engineering/W53-ARCHITECTURE-MAP.md` · `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` · `docs-v2/designs/DESIGN-84-distributed-cognitive-bridge.md` (the design the research forced) · `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` (the two-process demonstration) · `docs-v2/engineering/W53-PERFORMANCE.md` and `W53-PERFORMANCE.json` (the measured path) · `docs-v2/engineering/W53-EPISTEMIC-COMPARISON.md` (the 29 cases) · the decisions **CD-032, CD-033, CD-034** |
| the W53 wave | `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` (the wave's own report, with the five categories) · `docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` (the epistemic evaluation, machine-readable, with the supersession rule in place: 18 of 29, the same eleven failures) · the decisions **CD-030 and CD-031** in `docs-v2/governance/DECISION-REGISTER.md` · the proof of the exhaustion: `matrix-core/src/test/java/io/matrix/cognitive/microworld/DomainExhaustionTest.java` |
| the W52 wave | `docs-v2/engineering/W52-STATUS.md` (the wave's own report, with the five categories) · `docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json` (the epistemic evaluation, machine-readable, and the three measured configurations of the relevance floor in §5.4 of the report) · the decisions **CD-026 to CD-029** in `docs-v2/governance/DECISION-REGISTER.md` · the cross-process demonstration of both directions: `scripts/w52-bridge-both-directions-demo.sh` |
| the canonical cognitive contracts | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` (the model) and the package `io.matrix.contract` (the records themselves); the compatibility and migration strategy is §5 of `docs-v2/engineering/W51-STATUS.md`; **the producers and the boundary between the runtime's own records and the canonical ones are §5.2 of `docs-v2/engineering/W52-STATUS.md`**, and the acceptance is `CanonicalContractCoverageTest` |
| the inter-chain contract | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` and the decisions CD-020 to CD-025 in `docs-v2/governance/DECISION-REGISTER.md`; the evidence that the crossing is the only one is the gate `scripts/w45-chain-isolation-check.sh` and its negative control `scripts/w45-chain-isolation-selftest.sh` |
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

**This section was rewritten on 2026-11-09, against the observed tree.** The W47 snapshot
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

**The working tree carries the wave's own working files and nothing of the owner's.** At
2026-11-09 `git ls-files -m -o --exclude-standard` returned **one** entry:
`.dsh-project-memory/admission-shadow.jsonl`, which is **neither the wave's nor the owner's** —
it is the harness's journal, and the harness writes it on every call. **The set changes as
the wave runs**; what does not change is that **no owner entry is in it**.

**A session that finds a different set of owner entries is not a failure — it is a fact that
the owner worked in between.** Record the set; do not "restore" it.
