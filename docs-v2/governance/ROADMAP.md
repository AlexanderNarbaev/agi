# MATRIX — ENGINEERING ROADMAP

**Status:** `CANONICAL` · **Date:** 2026-11-04
**This document supersedes the role of `docs-v2/engineering/PLAN.md`.** That document remains
on disk as the record of what was planned up to W44; it is three waves behind and it points
here.

---

## 1. The strategic objectives

Three objectives, in priority order. Everything in the task list below serves one of them.

| the objective | what it means | how it is measured |
|---|---|---|
| **O1 — MATRIX explains itself** | a new agent must be able to answer the project's questions without repeating the archaeology | the time from a cold session to a correctly scoped task, and the number of documents a session must read |
| **O2 — the truth is one document per subject** | no subject has two documents that claim it, and every capability claim is tied to executed evidence | the number of superseded registries and the number of claims with no evidence |
| **O3 — the work is reproducible** | a session's verification is a command with an exact denominator, and the machine's limits are written down | every claim of "passing" is backed by a JUnit XML or a gate's exit code |

**What is deliberately NOT an objective:** adding a cognitive capability. The waves that
changed the system's behaviour are done (W45.0–W45.2); this wave makes the project
maintainable. A new kernel, a new memory store, a new conductor or an agent framework is out
of scope until a concrete end-to-end use case and a safety contract exist for it.

---

## 2. The current milestone

**M1 — the project can explain its own state.** From: a repository whose documentation
described the seventies of the project, whose architecture overview named no chain and no
cycle, and whose current state existed in four documents of four different ages.

| the milestone step | the state |
|---|---|
| W46.0 — the audit | **done** — ten deliverables in `docs-v2/engineering/W46-*` |
| W46.1 — the canonical structure | **in progress** — the governance documents, the capability registry, the test catalogue, the resource budgets |
| W46.2 — the reconciliation | **pending the owner's review of W46.0** — the navigation, the broken references, the stale documents |
| W46.3 — the tooling | **pending** — the link checker, the validators, the probe, the protocol |

---

## 3. The active wave

### W46 — the consolidation and the autonomous engineering system

The active wave is described in full by its own report: `docs-v2/engineering/W46.0-FINAL-REPORT.md`.

---

## 4. The tasks

Every task has a stable id. A task is **ready** when its prerequisites are met and it needs
no owner decision. **A task is never deleted: a done task stays, with its evidence.**

### 4.1 The completed tasks

| the id | the task | the goal | the result | the evidence |
|---|---|---|---|---|
| **TASK-W46-2** | the capability registry's validator | the registry cannot drift from the tree | **done** — `scripts/validate-project-registry.py`: 34 capabilities, 24 fields, the statuses, the paths, and the `SERVED`/`MEASURED` rules. **Plus** `scripts/validate-test-catalogue.py` (the catalogue against the JUnit XML) and `scripts/validate-project-state.py` (the state against the repository) | all three `--strict` exit 0 with **0 defects** |
| **TASK-W46-3** | the documentation link checker and the repair of the current navigation | the broken references are found by a tool, not by a reader | **done** — `scripts/docs-link-check.py` (the links, the anchors, the identifiers, the duplicates, the unreachable) and **360 → 0** strict failures | `--strict` exits 0; `--self-test` proves it goes red |
| **TASK-W46-5** | the extended hardware probe and the regenerated profile | the profile distinguishes the hardware, the software, the availability, the usage and the benefit | **done** — 58 measurements in five categories; the profile regenerated; the JSON written | `docs-v2/operations/HARDWARE-PROFILE.md` and `.json` |
| **TASK-W46-6** | the whole-database backup and the recovery runbook | a corruption or a bad migration is recoverable | **the runbook is done** — `docs-v2/operations/RECOVERY-RUNBOOK.md`: `VACUUM INTO`, the verification, the restore, and what recovery is **not**. **The drill is not**: see TASK-W46-13 | the runbook |
| **TASK-W46-7** | the negative-results index | the refuted findings are discoverable | **done** — `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` and `RESEARCH-INDEX.md` | the link checker |
| **TASK-W46-8** | the skill inventory and the minimal MATRIX skill set | the agent ecosystem is known | **done** — `docs-v2/governance/SKILL-INVENTORY.md` (149 packages, 82 names, 22 conflicts) and `SKILLS.md` (**four** recommended skills, two merges) | the inventory and the recommendation |

| **TASK-W46-9** | the GPU launcher and the truthful provider diagnostics | the launcher exports `LD_LIBRARY_PATH` so that the native loader finds the CUDA libraries the provider needs; the diagnostic reports the state that was OBSERVED — hardware, libraries, provider, registration, selection, execution, fallback — and never a hardcoded provider name | `scripts/start_production.sh`, `io.matrix.api.OnnxRuntimeAdapter` | OD-028 | **medium** — a serving-path change | the launcher block is in place and harmless when `.onnx_libs` is absent; the diagnostic's seven states are derived by a pure function that a test exercises with no GPU; the CUDA registration failure is RECORDED with its cause, not only logged; the CPU fallback stays safe | the executed tests, and the A/B with and without `LD_LIBRARY_PATH` | **yes** — the owner approved it; it changes the serving path |
| **TASK-W46-16** | the reload's contract: both flags, the preview, the erasure scope | `?replace=true` and `replaceExisting: true` both work; `?dryRun=true` returns what would be replaced and changes nothing; the erasure set covers the four observation tables | **done** — `84955ec9`: one declaration (`KnowledgeStore.ERASED_TABLES`) drives the erasure, the preview and the emptiness check; the four observation tables join the erasure set; the payload is versioned and carries them; the in-process door asks too | **196 classes, 1561 executions, 0 failures, 0 errors, 0 skipped** — `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.cognitive.*" --tests "io.matrix.api.*" --rerun-tasks`; the contract in `docs-v2/engineering/W47-WORKSPACE-REPLACEMENT-CONTRACT.md` |
| **TASK-W46-17** | the retention schedule | 7 days daily + 4 weeks monthly, outside the repository, and a restore only on the owner's explicit command | **done** — `651a7653`: `scripts/backup-retention.py` (three tiers, the points derived from the backups' own timestamps), the runbook's §8, and the gate's RETENTION-1 check | **25 checks, 0 failures** — `python3 scripts/backup-retention.py --self-test` |
| **TASK-W46-20** | PR-002 and PR-006 close at W47 | the two proposals are implemented or rejected at the W47 wave | **done** — `33a257a9`: PR-002 is **REJECTED** (the FROZEN zone needs a mandate W47 has not, a weekly CI job already exists, and the RFC itself says the automation blocks nothing); PR-006 is **SUPERSEDED** (the goal was reached by another path, and none of its three migration phases was performed) | the two rows in `docs-v2/governance/DECISION-REGISTER.md` |

### 4.2 The ready tasks

| the id | the task | the goal | the subsystem | the dependencies | the risk | the resource | the acceptance criteria | the required evidence | the test scope | the owner approval |
|---|---|---|---|---|---|---|---|---|---|---|
| **TASK-W46-2** | the capability registry's validator | the registry cannot drift from the tree | `scripts/` | the capability registry (W46.1) | low | light | the validator fails on a bad source path, an illegal status, a `SERVED` without a serving path, and a `MEASURED` without a benchmark | the validator's own output on a deliberately broken copy | the script's self-test | no |
| **TASK-W46-3** | the documentation link checker and the repair of the current navigation | the broken references are found by a tool, not by a reader | `scripts/`, the current documents | the document statuses (W46.1) | medium | light | the checker reports **0** broken links in the current documents, with a strict mode and a report-only mode, and no network access | the checker's machine-readable output | the script's self-test | **yes** for the `AGENTS.md` amendment and for the `constitution.md` decision |
| **TASK-W46-5** | the extended hardware probe and the regenerated profile | the profile distinguishes the hardware, the installed software, the runtime availability, the verified usage and the benchmarked benefit | `scripts/hardware-probe.sh`, `docs-v2/operations/` | the capability registry (W46.1) | low | light | the profile names the CUDA **toolkit** and the **compatibility** separately, and probes `/tmp`, cuDNN, the Gradle heap and workers, the test heap, Docker, the Python/ML stack, the ONNX providers, GraalVM and the build-directory sizes | the regenerated profile against the independent probes | the probe's `--stdout` mode | no |
| **TASK-W46-6** | the whole-database backup and the recovery runbook | a corruption or a bad migration is recoverable | `scripts/`, `docs-v2/operations/` | the storage ownership map | medium | light | a backup that uses SQLite's own mechanism (never a naive copy of a live file with WAL active), a restore drill into a temporary copy, and a runbook section | the drill's output | the script's self-test | no |
| **TASK-W46-7** | the negative-results index | the refuted findings are discoverable | `docs-v2/research/` | the document statuses | low | light | an index of the refuted hypotheses, the failed algorithms, the invalid measurements and the experiments that could not be completed — each with a path, and each marked discoverable or not | the index | the link checker | no |
| **TASK-W46-8** | the skill inventory and the minimal MATRIX skill set | the agent ecosystem is known | `docs-v2/governance/` | the protocol (W46.1) | low | light | an inventory of every skill with its purpose, its tools, its dependencies, its safety boundaries and its validation command, and a recommendation of a **compact** set | the inventory | — | no — but `opencode.json` must not be edited to activate anything |

---

### 4.3 The new tasks — the ones the wave left behind

| the id | the task | the goal | the subsystem | the dependencies | the risk | the resource | the acceptance criteria | the required evidence | the test scope | the owner approval |
|---|---|---|---|---|---|---|---|---|---|---|
| **TASK-W46-13** | the executed restore drill | a backup procedure that has never been restored from is a procedure, not a capability | `scripts/`, the tests | the recovery runbook (TASK-W46-6) | medium | medium | a `VACUUM INTO` backup of an **isolated temporary database**, restored and verified: the `integrity_check` is `ok`, the schema version is the expected one, the row counts match, and the restored database opens under the writer lock without a migration | the drill's output, recorded | the new test, in isolation, **never against the owner's database** | no |
| **TASK-W46-14** | the four skill packages | the recommended skills exist and are loaded | `.agents/skills/` | `SKILLS.md` (TASK-W46-8) | low | light | four packages under `.agents/skills/`, each with the frontmatter of `SKILLS.md`, each passing the package validator, and each reachable by a session **without** editing `opencode.json` | the validator's output and a session that finds them | the package validator | no |
| **TASK-W46-15** | the classifications and the duplicate identifiers | every document has an explicit status, and every duplicate identifier is explained | the documents of the current group | Q-W46-19 and Q-W46-20 | low | light | **0** documents of the current group with a status outside the vocabulary (there are 261 today), and every remaining duplicate identifier either resolved or explained in the register | the link checker's classification report | the link checker in strict mode | no |

### 4.4 The W47 tasks — the owner's decisions of 2026-11-05, recorded and not yet implemented

**The owner's questionnaire of 2026-11-05 settled ten questions. The decisions are recorded in
`docs-v2/governance/DECISION-REGISTER.md` (OD-025…OD-034). The owner's instruction for the W46
wave was to record the decisions and leave the behaviour to W47 — so every row below is an
implementation task that W46 deliberately did NOT do.**

| the id | the task | the goal | the subsystem | the decision | the risk | the resource | the acceptance criteria | the required evidence | the test scope | the owner approval |
|---|---|---|---|---|---|---|---|---|---|---|
| **TASK-W46-16** | the reload's contract: both flags, the preview, the erasure scope | `?replace=true` and `replaceExisting: true` both work; `?dryRun=true` returns what would be replaced and changes nothing; the erasure set covers the four observation tables | `io.matrix.knowledge.KnowledgeStore`, `LearningWorkbenchResource` | OD-025, OD-026, OD-027 | **high** — the erasure set grows | medium | the two flags are equivalent; the preview returns the counts and the rows without writing; after a replacement no `epistemic_case` survives without its evidence; the old requests without consent still fail closed | the executed tests | `io.matrix.knowledge.*`, `io.matrix.cognitive.*` | **yes** — it is approved, and the change is a data-loss boundary |
| **TASK-W46-17** | the retention schedule | 7 days daily + 4 weeks monthly, outside the repository, and a restore only on the owner's explicit command | the operations | OD-029 | low | light | a script that prunes by age, a directory outside the repository, and the runbook's command | the script's output on a temporary tree | the script's self-test | no |
| **TASK-W46-18** | the fifteenth TLA+ specification enters the gate | `docs-v2/tla/FederationConsensus.tla` moves into `formal/`, enters the specification map and enters the gate | `formal/`, the gate | OD-030 | medium — the gate's scope grows | medium | the gate runs **15** specifications, the map holds 15 entries, and the capability registry's count says 15 | the gate's output | the formal gate | no — the decision is made |
| **TASK-W46-19** | the four missing subjects are written | the autonomy-impulses, the decentralized-digests, the consolidation-cycle and the hierarchical-memory subjects get their own documents; the series is **not** renumbered | `docs-v2/specifications/`, `docs-v2/designs/` | OD-031 | medium — four new normative documents | medium | four documents with the subject they name, each with its status, its evidence and its test scope; the eleven stale references point at them | the documents themselves | the link checker | no — the decision is made |
| **TASK-W46-20** | PR-002 and PR-006 close at W47 | the two proposals are implemented or rejected at the W47 wave | the CI, the WAL tooling | OD-032 | low | light | both rows in the decision register carry a terminal status, and the W47 report says which | the register | — | no |
| **TASK-W46-21** | the four MATRIX skills are installed and registered | the skills exist under `.agents/skills/` and the harness's trigger table knows them | `.agents/skills/`, the harness's source | OD-033 | medium — a change to the harness's source and to a file in the home directory | light | the four packages pass the package validator; a session finds them by their frontmatter; the trigger table and its mirror are regenerated | the validator's output and a session that finds them | the package validator | **yes** — it edits the harness's source |
| **TASK-W46-22** | the four skill packages get their names right | the declared name matches the directory | `.veai/skills/`, the plugin bundle | OD-034 | medium — renaming a package that something loads | light | the four packages load under their own names; the validator reports no mismatch; nothing that depended on the old name breaks | the validator's output | the package validator | **yes** — the owner's library and the plugin bundle |

### 4.5 The tasks the W47 questionnaire created

**Four answers came out of the questionnaire of 2026-11-05 (OD-035…OD-038).** They are the
owner's decisions, and each one is an implementation task.

| the id | the task | the goal | the scope | the decision | the risk | the acceptance criteria | the evidence | the owner approval |
|---|---|---|---|---|---|---|---|---|
| **TASK-W46-23** | the learning state goes with the knowledge | a workspace replacement erases the assessment items, the learning state, the transitions, the outcomes, the dialogue turns, the asked questions and the hebbian bindings, and the snapshot carries them | `io.matrix.knowledge.KnowledgeStore` | OD-036 | **medium** — the erasure set grows by eight tables | the eight tables hold no row of the replaced workspace; the snapshot carries them; a restore brings them back byte-identically; no learning row points at a concept that is not there | the executed tests | **yes** — it is approved; it changes what a replacement destroys |
| **TASK-W46-24** | the k-anonymity counter counts distinct nodes | `Anonymizer.recordContribution` counts the NODES that contributed a hash, not the contributions of one hash, as the class's own javadoc promises | `io.matrix.federation.Anonymizer` | OD-037 | low — an island class, no production caller | the same hash from two nodes counts 2; the same hash twice from one node counts 1; a test that fails against the old implementation | the executed tests | **yes** — it is approved |
| **TASK-W46-25** | the gateway's autonomy loop must not die | the loop must not throw a NullPointerException 120 seconds after the gateway starts; the correct behaviour is decided from the code and stated in the specification | `io.matrix.autonomy.AutonomyEngine`, the brain runtime's `AutonomyLoop` | OD-038 | **medium** — a serving-path change | the loop survives its first cycle; the behaviour is stated in SPEC-015; a test that fails against the current wiring | the executed tests | **yes** — it is approved |

### 4.6 The tasks the end-of-wave questionnaire created

**Six questions, six answers, through the harness's `ask_user_question` tool, on 2026-11-05.**
The questions and their alternatives are in `docs-v2/engineering/W47-QUESTIONNAIRE.md`; the
answers are OD-039…OD-044. Two of them are implemented in this wave; four are the first
tasks of W48.

| the id | the task | the goal | the decision | the status |
|---|---|---|---|---|
| **TASK-W46-26** | the container `matt-pocock` gets a root manifest | the owner decided that the container SHOULD change: it becomes one skill whose body indexes the seventeen disciplines inside it | OD-040 | **done in W47** — `.opencode/skills/matt-pocock/SKILL.md`, and the harness now discovers it |
| **TASK-W46-27** | the curiosity drive's envelope is the cost | the drive can be paid for, and the refusal path stays for the drives that should be refused | OD-044 | **done in W47** — `AutonomyEngine.java:116`, with a test that fails against the old envelope |
| **TASK-W47-01** | the snapshot's size bound, and the three-tier calendar on the rows | the database's own growth is bounded the way the backups are | OD-039 | **W48** — the first task of the next wave |
| **TASK-W47-02** | the restore drill | a restore is performed against a COPY of the live database, and the result is written into the runbook | OD-042 | **W48** |
| **TASK-W47-03** | the committed GPU benchmark | the 12.6× claim becomes a re-runnable measurement or nothing | OD-043 | **W48** |
| **TASK-W46-11** | the full `matrix-core` suite | the project's total test population becomes a number | OD-041 | **approved and run in W47** — one JVM, an 8 GiB heap, the other daemons stopped |

## 5. The blocked tasks

**TASK-W46-9 is no longer here**: the owner's decision OD-028 of 2026-11-05 unblocked it, and
it is one of the W47 tasks in §4.4.

| the id | the task | blocked by | what unblocks it |
|---|---|---|---|
| **TASK-W46-4** | the defects inside the already-approved contracts: the writer lock's symlink alias (D4-a), the `BirBrainCycle` attribution (F-03), the stub default (F-01), the undeclared P2P ports (F-02) | F-01 and F-02 need an owner decision; D4-a touches the database-ownership boundary, which the W46 directive protects until the owner approves | the owner's answers to Q-W46-07 and to the F-01/F-02 questions; for D4-a, the owner's approval of the change |
| **TASK-W46-10** | the readiness probe: either wire the PostgreSQL datasource or remove it | it changes the CI smoke test and every deployment manifest | the owner's decision — this is a deployment question, not a documentation one |
| **TASK-W46-11** | the full `matrix-core` suite | it needs the owner's approval and a resource estimate | the owner's approval; the estimate must use the measured envelope (one test JVM at a time, 16 g against ~38.7 GiB available) |
| **TASK-W46-12** | the class-level cycle `AgentBrainService ↔ NeuralMemoryResponse` | it is a refactor across packages | a decision on whether the "no new cycles" gate should also require the existing ones to be broken; the second cycle is in the **FROZEN** `ethics/frozen/` zone and needs an RFC |

---

## 6. The owner decisions

The full register is `docs-v2/governance/OPEN-QUESTIONS.md`. The decisions that block work:

| the ID | the decision | the question | the cost of not deciding |
|---|---|---|---|
| **Q-W46-01** | the destructive reload's consent parameter | `?replace=true` or the `replaceExisting: true` body field? | a destructive operation's consent parameter may not be the one the owner has in mind |
| **Q-W46-02** | the preview mode | a dry-run that names what would be replaced — yes or no? | a caller must guess what a destructive operation destroys |
| **Q-W46-03** | the erasure scope of a workspace replacement | does the observation's evidence belong to the workspace? | an epistemic record survives its own evidence |
| **Q-W46-07** | the GPU path | may the launcher export `LD_LIBRARY_PATH`, and how should the diagnostic report the truth? | the serving path runs a 12.6×-slower provider while a diagnostic claims "CPU" |
| **Q-W46-13** | the retention policy | the append-only histories grow without bound | an audit trail that exhausts the disk, or a silent deletion of evidence |

---

## 7. The deferred work

| what | why it is deferred | what would revive it |
|---|---|---|
| the **native-image build** | GraalVM and Mandrel are absent on this host and the mandrel builder image is a multi-GB pull; the eight native-image test classes cannot run | the tooling installed, or a decision to drop the native target |
| the **`matrix-fpga`, `matrix-micro`, `matrix-ros2` modules** | commented out of `settings.gradle`; dormant | a concrete use case and the toolchains |
| the **measurement cluster** (`io.matrix.neuron.ConsciousBrain` and its 19 test references) | zero production callers | a research question that needs it |
| the **`io.matrix.consciousness` measurement package** (297 types, 11 served) | its 25 callers measure the package, not a serving path | a decision to serve or to retire it |
| the **biochemical island** | explicitly rejected by the W44 audit; the coefficients have no source | a source for the coefficients |
| the **connectome** (`Connectome`, `ReplayPhase`, `SleepConsolidator`) | explicitly rejected by the W44 audit; 0 production callers against 159 tests | a use case |
| the **gateway's JAX-RS facade** | unmounted by construction; mounting it would make `StubBrainCycle` the served implementation | nothing — it should stay unmounted, and the gate censuses it so that a mount would be visible |

---

## 8. The rejected directions

These were decided against. **They are not tasks, and a future agent must not silently revive
them.**

| the direction | the rejection | where |
|---|---|---|
| a **global cognitive conductor** (`TrueMindCycle` for the whole system) | the bytecode says the epistemic application runs a different loop; the decision was to name per application | `ADR-W45.2-01`, `W45.2-OWNER-DECISIONS.md` |
| an **authenticated write bridge** between the chains | the owner re-answered and chose isolation; any future bridge needs a separate, versioned, provenance-carrying contract (BRIDGE-1…10) | `W45.2-CHAIN-BOUNDARY.md` |
| a **non-destructive reload by versioning** | the owner chose fail-closed with a recoverable snapshot | `W45.2-OWNER-DECISIONS.md` |
| **distributed leases** for the database writer | the owner chose one writer process per file; a lease framework needs a real multi-host use case | `W46-OWNER-DECISION-PACKET.md` |
| the **33,000× speed-up claim** | never measured; it was a synthetic ops/sec comparison | `BENCHMARK-REPORT-W620.md`, the capability registry |
| the **90% distillation retention claim** | no measurement exists | `DISTILLATION-LEDGER.md` |
| the **"GPU unavailable"** claim | it was a loader-path fact, not a hardware fact | `W46-HARDWARE-AND-RESOURCE-REPORT.md` §6 |

---

## 9. The maintenance and reliability work

The standing obligations — not a project, but the work that keeps the project true.

| the obligation | the trigger | the evidence it produces |
|---|---|---|
| the **test scopes** stay green | every change | the per-scope denominators, on the commit |
| the **gates** stay green | every change | the quality gate, the security boundary, the chain isolation and its negative control |
| the **capability registry** stays reconciled | a capability changes | the registry's `last_verified_commit` moves |
| the **document registry** stays reconciled | a document is added, moved or retired | the registry's row |
| the **profile** stays current | the machine changes, or a quarter passes | the regenerated profile |
| the **project state** stays current | a wave completes | this document is replaced, not appended to |
| the **risks** and the **open questions** are re-read | at the start of every wave | the mitigation and the resolution |
