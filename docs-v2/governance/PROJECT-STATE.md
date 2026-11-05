# MATRIX — PROJECT STATE

**Status:** `CANONICAL` · **This document is a SNAPSHOT, not a history.** It describes the
project at one commit and is replaced, not appended to, when the state moves.
**Snapshot of:** `a6ded23338e069b8bc8a3744c234be5a03995f43` · **Date:** 2026-11-04
**Validation:** a validator that checks that the commit below is the repository's HEAD, and
that the wave and the baseline below are the current ones, is TASK-W46-3's
`scripts/validate-project-state.py` — **it does not exist yet**, so this snapshot is currently
maintained by hand. Until it exists, a change to the state below must be made here and
verified by hand.

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
| the commit | `a6ded23338e069b8bc8a3744c234be5a03995f43` |
| the remote | `origin/develop` — **the same commit** (the local and the remote do not diverge) |
| the release version | **none.** The tags `v1.0.0` and `v16.0.0-mind` exist, but no release process is defined and the `v16.0.0-mind` tag predates three waves of work |
| the latest completed wave | **W45.2** — the owner's four architecture decisions (`a6ded233`). **W46 is complete in the working tree and uncommitted** |
| the previous waves | W45.1 (`509d23ba`) — the crash consistency; W45.0 (`eae6003f`) — the cognitive kernel integrity; RECON-W44.0 (`ae6c017b`) — the bytecode archaeology |
| the active wave | **W46** — the consolidation. W46.0 (the audit), W46.1 (the canonical structure), W46.2 (the reconciliation) and W46.3 (the tooling) are **complete and uncommitted**; the wave waits for the owner's authorisation to commit and to push |
| the modules | **14** included in `settings.gradle`, 3 commented out, 2 outside Gradle, 1 stray orphan file |
| the serving entry points | **2** applications — the Quarkus app on **9091** (210 routes) and the brain runtime's JDK server on **8765** (28 routes) |
| the CLI entry points | **79** |
| the databases | **one canonical** SQLite file (`data/knowledge/workbench.sqlite`, the knowledge schema **v19** and the cognitive schema **v4** over it) plus chain B's own `data/mind/mind.sqlite` |

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

**Three things this baseline does NOT say:**

- **The total test population is unknown.** The W44 archaeology measured 9816 executions with
  24 failures during the W44 run; that is a **floor**, not a total, and it is a year old.
- **The native-image classes are neither passing nor failing.** Eight classes (23 failures)
  depend on GraalVM, which is **absent on this host** — `matrix-core/build/native` <!-- no-link: a build artefact, not a repository file --> has never
  existed here. **They are never counted as passing.** The true W44 split is 8 native-image
  classes / 23 failures **plus 1 ordinary JVM failure**
  (`io.matrix.consciousness.Exp228ComplianceTest`).
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
| the **GPU is available and unused** — the ONNX CUDA registration fails because the library path is a JVM system property and a native `dlopen` consults `LD_LIBRARY_PATH`; the diagnostic that reports the active provider is a **hardcoded string** | a performance defect + a lying diagnostic | the truth of the GPU claim; a measured **12.6×** is available | the owner (a serving-path change) |
| the **quality gates are not blocking** — they live in a CI job named *«Quality gates (can fail)»*, and `STANDARDS-MATRIX.md` does not say so | a documentation and process defect | the trust a gate carries | the owner |
| the **readiness probe is permanently 503** — the reactive PostgreSQL datasource gates it and **no production code reads it** | an operational blocker | the CI smoke test; every deployment manifest | the coordinator |

---

## 5. The pending owner decisions

The full register, with the competing explanations and the experiment that would resolve each,
is `docs-v2/governance/OPEN-QUESTIONS.md`. The five that block the next work:

| the ID | the question | what a decision unblocks |
|---|---|---|
| **Q-W46-01** | the destructive reload's consent parameter: `?replace=true` (as implemented) or the `replaceExisting: true` body field of the target contract? | the API shape of a destructive operation |
| **Q-W46-02** | a dry-run / preview mode for the destructive reload? | a new HTTP surface on a destructive path |
| **Q-W46-03** | what may a workspace replacement erase? | the erasure set of the workspace replacement |
| **Q-W46-07** | may the serving path export `LD_LIBRARY_PATH` so that the GPU is used — and how should the provider diagnostic report the truth? | a **12.6×** measured difference, and the truth of a published claim |
| **Q-W46-13** | the retention policy for the append-only observation, adjudication and request-event histories | the unbounded growth of the audit trail |

---

## 6. The next recommended work

The canonical roadmap is `docs-v2/governance/ROADMAP.md`. In order:

1. **TASK-W46-2** — the capability registry's validator (the registry itself is being written in W46.1).
2. **TASK-W46-3** — the documentation link checker, then the repair of the current navigation.
3. **TASK-W46-4** — the defects inside the already-approved contracts (the symlink alias; the `BirBrainCycle` attribution).
4. **TASK-W46-5** — the extended hardware probe, the resource budgets, the corrected GPU claim.
5. **TASK-W46-6** — the whole-database backup and the recovery runbook.

---

## 7. The authoritative records — where each subject is written down

**One subject, one place.** If a subject is listed here and the document it names disagrees
with this snapshot, the document wins for its subject and this snapshot is wrong — and the
validator says so.

| the subject | the document |
|---|---|
| the principles | `CONSTITUTION.md` (normative) — and `constitution.md` is an **unfilled template duplicate** of it, a risk, not an alternative |
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

## 9. The owner's working tree — eleven entries, protected

At the snapshot commit, the working tree carries **eleven entries that belong to the owner**
and that no agent may touch, discard, stage or commit:

`.gitignore` · `.opencode/todo.md` · `.opencode/w37-plan.md` · `AGENTS.md` ·
`opencode.json` · the deletion of `opencode.json.bak` · `.codex/` ·
`.opencode/goals/` · `.opencode/opencode-swarm.json` · `.opencode/tmp/` · `.veai/`

Plus the local journal `wal/SESSION_WAL.md`, which is **outside git** (`/wal/` is in
`.gitignore`) and therefore local-only.

**A session that finds a different set of owner entries is not a failure — it is a fact that
the owner worked in between.** Record the set; do not "restore" it.
