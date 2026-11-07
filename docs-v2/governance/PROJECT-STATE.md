# MATRIX — PROJECT STATE

**Status:** `CANONICAL` · **This document is a SNAPSHOT, not a history.** It describes the
project at one commit and is replaced, not appended to, when the state moves.
**Snapshot of:** `71b5b5c66907883c1eaf9c38433197ba3191c5db` · **Date:** 2026-11-07
**Validation:** `scripts/validate-project-state.py --strict` — it checks that the commit below
is the repository's HEAD, that the wave and the baseline below are the current ones, and that
the canonical documents it names exist. It reports a lag of one commit as an OBSERVATION, not
as a defect: the snapshot names the commit it was written at, and the commit that follows it
is the one that carries the snapshot itself.
**The wave that produced this snapshot is still running.** The commit named above is the one
observed at 2026-11-07T20:48:27+03:00 (`git rev-parse HEAD`); `origin/develop` pointed at
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
| the commit | `71b5b5c66907883c1eaf9c38433197ba3191c5db` — the W50 commit observed at 2026-11-07T20:48:27+03:00; this snapshot is carried by the commit that follows it |
| the remote | `origin/develop` — **the same commit** at the moment of writing (the local and the remote do not diverge). The wave is still running, so the remote moves with it. `origin/main` is `79045c57` — the **squash merge of pull request #44** (W49), merged on 2026-11-06 |
| the release version | **none.** The tags `v1.0.0` and `v16.0.0-mind` exist, but no release process is defined and the `v16.0.0-mind` tag predates three waves of work |
| the latest completed wave | **W49** — the public-research workstream (OD-048) and the two implementation tasks W48 left approved. It is **merged into `main`**: pull request #44, squash `79045c57`, 2026-11-06. Before it, **W48** — the truth of the evidence (the CI reconciliation, the capability-claims guard, the measured native budget), and before that **W47** — the implementation of the owner's decisions of 2026-11-05 (OD-025…OD-044) |
| the previous waves | W48 — the CI reconciliation and the native budget; W47 — the owner's four architecture decisions (`a6ded233`); W45.1 (`509d23ba`) — the crash consistency; W45.0 (`eae6003f`) — the cognitive kernel integrity; RECON-W44.0 (`ae6c017b`) — the bytecode archaeology |
| the active wave | **W50 — MATRIX Local Evidence-Grounded Demonstrator: from research subsystems to a runnable, evidence-grounded learning system. Its report is `docs-v2/engineering/W50-STATUS.md`; the measured model evaluation is `docs-v2/engineering/W50-LOCAL-MODEL.md`.** **What it delivered:** a **bounded, licensed corpus in one domain** (plate tectonics) — 2 seed documents (USGS *This Dynamic Earth*, US public domain; Wikipedia *Plate tectonics*, CC BY-SA 4.0, oldid 1381934246) and a **7-document pre-cached research pool** of versioned Wikipedia snapshots, every file carrying its licence, its revision and its sha256 in a manifest that the import verifies; the seed import produced **348 claims** and the bounded acquisition cycle **3 pool documents and 324 claims**. The **four-turn demonstration ran on the packaged jar** (`matrix-core/build/quarkus-app/quarkus-run.jar`, `http://127.0.0.1:9091`): Q1 answered from **153 claims of 2 sources**; Q2 from **25 claims of 4 sources** after the acquisition; Q3 a **CONTRADICTED** verdict that names the disputed premise and the contradicting claim and its source, with `promoted=false` — the assertion was never stored; Q4 acquired the *Slab pull* document (**14 claims**) and applied **exactly one supersession** (relatedness 0.83) — the earlier claim stays in the history as SUPERSEDED (version 2), the new one is SUPPORTED (version 2), and the answer moves from **118 claims of 5 sources** to **128 claims of 7 sources**. **The teacher stays off** (OD-049): the local model evaluation measured **0 teacher invocations** during the deterministic acquisition, and the teacher is **not** more faithful (72.7 % of its propositions are verbatim against the deterministic path's 100 %). **What is still open, and named:** the false-premise judgement is **lexical, not semantic**; an answer's support value is **0.0**, because an imported claim carries no authored confidence; the pool is pre-cached and versioned, **not live web** — no request leaves the machine; and the status route leaves the workspace list and the counts empty (the three risks R-W50-01…R-W50-03, and the question Q-W50-03). **The public face is published from `main`, which now carries W49** (the squash `79045c57`) and is behind `develop` by the W50 work (R-W49-02, Q-W49-01); the **release path** has no credentials (R-W48-04); the **static analysis produces no evidence** and the Java 25-capable engine reports 1 414 findings (Q-W49-06); the **native build** needs a larger runner and one dependency (Q-W49-05) |
| the modules | **14** included in `settings.gradle`, 3 commented out, 2 outside Gradle, 1 stray orphan file |
| the serving entry points | **2** applications — the Quarkus app on **9091** (210 routes) and the brain runtime's JDK server on **8765** (28 routes) |
| the CLI entry points | **79** |
| the databases | **one canonical** SQLite file (`data/knowledge/workbench.sqlite`, the knowledge schema **v19** — `KnowledgeStore.SCHEMA_VERSION = 19`) plus chain B's own `data/mind/mind.sqlite`. The W50 demonstration ran on that canonical file and left **687 claims** in each of its two workspaces; the claims carry the workspace id, but the `workspace` table itself holds no row — which is why the status route's workspace list comes back empty (Q-W50-03) |

**What this snapshot re-verified, and what it carried.** Re-verified on 2026-11-07: the
branch and the remote (`git rev-parse HEAD`, `git rev-parse origin/develop` — the same
commit at the moment of writing), the merge of W49 into `main` (the squash `79045c57`), the
wave that is active, and the schema version
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

**The four W48 rows are quoted from the wave's own committed records**, not re-run here:
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §3 (the two test scopes),
`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.1 (the disk-budget scope) and
`docs-v2/engineering/W48-GPU-BENCHMARK.md` §9 (the harness's arithmetic).

**The W50 row is the wave's own scope**, counted from its retained JUnit XML under
`matrix-core/build/test-results/test/`: the eleven classes of `io.matrix.knowledge.acquisition.*`
plus `io.matrix.knowledge.PremiseAssessmentTest`. It is a **scope**, not the project's total
test population — the full suite is still not run here (R-W46-10).

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
**done** and live in §4.1, §4.7 and §4.8 of the roadmap. **What is open now, in order:**

1. **The release pull request** — the lead opens `develop` → `main` at the end of the wave,
   and **an agent does not merge it**. The pull request is a prepared path, not a release;
   the merge is the owner's decision (Q-W49-01).
2. **The three owner questions this wave raised** — the licence interaction of the corpus
   inside the repository (Q-W50-01), the audit trace of an imported claim (Q-W50-02) and the
   status route's empty workspace list and counts (Q-W50-03). They are in §5 above and in
   the register.
3. **The honest limits of the demonstrator** — the false-premise judgement is lexical, not
   semantic (R-W50-01); the extractor's corpus vocabulary is a heuristic verb list
   (R-W50-02); the answering path proves zero LLM calls by instrumentation, and the
   assertion check is itself a deterministic lexical pass (R-W50-03). They are named, not
   hidden.
4. **The W49 owner questions, unchanged** — the release path's missing credentials
   (R-W48-04), the static-analysis policy for the 1 414 findings of the Java 25-capable
   engine (Q-W49-06), and the native build's larger runner and one missing dependency
   (Q-W49-05).
5. **The wave's own report** — `docs-v2/engineering/W50-STATUS.md` and
   `docs-v2/engineering/W50-LOCAL-MODEL.md`. This snapshot names them and does not restate
   them.

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

**This section was rewritten on 2026-11-07, against the observed tree.** The W47 snapshot
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

**The working tree carries the W50 wave's own working files and nothing of the owner's.**
At 2026-11-07T20:51:28+03:00 `git ls-files -m -o --exclude-standard` returned **five**
entries: the three documents of the demonstration
(`docs-v2/demos/local-learning-loop/README.md`, `docs-v2/demos/local-learning-loop/OWNER-SEQUENCE.md`
and `docs-v2/demos/local-learning-loop/ANSWER-KEY.md`), the launcher `scripts/run-local.sh`
— **untracked**, so it is in no commit yet — and one file that is neither the wave's nor the
owner's, `.dsh-project-memory/admission-shadow.jsonl`, which the harness writes. **The set
changes as the wave runs**; what does not change is that **no owner entry is in it**.

**A session that finds a different set of owner entries is not a failure — it is a fact that
the owner worked in between.** Record the set; do not "restore" it.
