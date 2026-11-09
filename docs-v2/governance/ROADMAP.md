# MATRIX — ENGINEERING ROADMAP

**Status:** `CANONICAL` · **Date:** 2026-11-07
**This document supersedes the role of `docs-v2/engineering/PLAN.md`.** That document remains
on disk as the record of what was planned up to W44; it is six waves behind and it points
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

### W52 — the canonical path, the second direction and the registers in truth (**the wave is running**)

**W52 is the wave that follows the merged W51.** It closes the three load-bearing gaps W51
named, and nothing else is in scope:

1. **the canonical contracts get their producers** — §3 of the mandate names twenty-two
   types, and W51 gave them meaning but left fourteen of them without a producer in the
   runtime; the two parallel vocabularies — the canonical interchange form and the cognitive
   package's older internal records — are reconciled by an explicit, tested mapping;
2. **the inter-chain contract gets its second direction** — chain A → chain B, under the same
   governance as chain B → chain A, with the isolation gate extended to prove that the second
   direction cannot break the first;
3. **the repository's own instruments tell the truth again** — the capability registry, the
   project state, this roadmap and the decision register know about the wave that made the
   subsystems serve.

**What the wave does NOT do:** it does not merge anything into `main`, it does not delete a
subsystem, and it does not rewrite the W45.1 recovery contracts. The commit and the push are
authorised by the owner's directive of 2026-11-08; **the merge into `main` is the owner's**.

**The tasks are in §4.11**, and the wave's report will be `docs-v2/engineering/W52-STATUS.md` <!-- no-link: the wave's report is written at its close; the document does not exist yet -->.

### W51 — the unified cognitive runtime, the world model and the safe self-extension (**merged**)

**W51 is merged**: pull request #48 (`develop` → `main`), squash `3ef8a5c8`, 2026-11-09,
**merged by the owner**. Its own report is `docs-v2/engineering/W51-STATUS.md`.

**W51 is the wave that made the parts of MATRIX interact.** Its own report is
`docs-v2/engineering/W51-STATUS.md`, the audit and the subsystem classification are
`docs-v2/engineering/W51-AUDIT.md`, the architecture map of the actual runtime
interactions is `docs-v2/engineering/W51-ARCHITECTURE-MAP.md`, the measured hot paths are
`docs-v2/engineering/W51-PERFORMANCE.md`, and the boundary decision is
`docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md`.

**What changed, in one sentence:** the two serving chains stayed separate processes with
separate databases and separate single writers, and the *bridge* became the one governed
crossing between them — the contract the repository had already written down in W45.2 and
never implemented.

**What it did NOT do:** it did not merge anything into `main` and it did not delete a
subsystem. The wave's own directive did not name the commit and the push — they are Level 3
operations under `AGENT-OPERATING-PROTOCOL.md` §3 — and the wave left the work in the
working tree, reviewed and measured. **The owner authorised the commit and the push on
2026-11-08**, and the wave's work is now eleven commits on `develop`, with pull request #48
(`develop` → `main`) open and **not merged by an agent**.

**The measured results:** the end-to-end demonstration performs **15 of 15** acceptance
points; the cross-process demonstration performs **10 of 10**; the isolation gate passes
**22 of 22** checks and its negative control **12 of 12**; the W50 epistemic evaluation
went from **12/29 to 18/29** on the wave's own runner, with the baseline reproduced first
on a clean worktree at `a098f283`; the wave's own test scopes are
**1 323 tests, 0 failures, 0 errors** in `matrix-core` and **3, 0 failures** in
`matrix-brain-runtime`;
the quality gate holds at **271** magic-literal candidates against a baseline of **271**.

### W50 — the local evidence-grounded demonstrator (**merged**)

**The wave is complete and merged.** It started at `8f617ce6` (the W49 close) on `develop`;
its own report is `docs-v2/engineering/W50-STATUS.md`, the measured local-model evaluation
is `docs-v2/engineering/W50-LOCAL-MODEL.md`, and the tasks it created are in §4.9. **The
wave's own order was W50-00 … W50-08.** Pull request #47 (`develop` → `main`) was merged by
the **owner** on 2026-11-07 as the squash `67b3410d`; an agent opened it and did not merge
it.

### W49 — the public-research workstream (**merged**)

**The wave is complete and merged.** Pull request #44 (`develop` → `main`) was merged on
2026-11-06 as the squash `79045c57`; its own report is `docs-v2/engineering/W49-STATUS.md`
and the tasks it created are in §4.8.

### W48 — the truth of the evidence (**the wave's work is committed**)

**The wave is committed and its report is written.** It started at `eca23bfd` (the last
commit of W47); its preflight is `docs-v2/engineering/W48-BASELINE.md`, its own report is
`docs-v2/engineering/W48-STATUS.md`, and the tasks it created are in §4.7. **The owner
answered the wave's three questions on 2026-11-06 — OD-045, OD-046 and OD-047.** The wave
that followed it was **W49**, opened by OD-045, and it is **merged**.

### W46 — the consolidation and the autonomous engineering system

W46 is described in full by its own report: `docs-v2/engineering/W46.0-FINAL-REPORT.md`.

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
answers are OD-039…OD-044. Two of them are implemented in this wave; one more was approved
and run here; and **three are the first tasks of W48 — and all three are now done**
(TASK-W47-01, TASK-W47-02, TASK-W47-03; see the status column).

| the id | the task | the goal | the decision | the status |
|---|---|---|---|---|
| **TASK-W46-26** | the container `matt-pocock` gets a root manifest | the owner decided that the container SHOULD change: it becomes one skill whose body indexes the seventeen disciplines inside it | OD-040 | **done in W47** — `.opencode/skills/matt-pocock/SKILL.md`, tracked in git and committed by `1371774e` (the harness's own discovery of it was not re-run in W48, so that half of the claim is NOT VERIFIED) |
| **TASK-W46-27** | the curiosity drive's envelope is the cost | the drive can be paid for, and the refusal path stays for the drives that should be refused | OD-044 | **done in W47** — `AutonomyEngine.java:116`, with a test that fails against the old envelope |
| **TASK-W47-01** | the snapshot's size bound, and the three-tier calendar on the rows | the database's own growth is bounded the way the backups are | OD-039 | **W48 — done.** `33d199e2`: the 8 MiB payload bound with the typed refusal `SNAPSHOT_PAYLOAD_TOO_LARGE` **before** the write, and the three-tier retention on the `workspace_snapshot` rows. The record is `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md` |
| **TASK-W47-02** | the restore drill | a restore is performed against a COPY of the live database, and the result is written into the runbook | OD-042 | **W48 — done.** `090247cb`: `scripts/restore-drill.sh` performs the drill against a copy; the result and the elapsed time are written into `docs-v2/operations/RECOVERY-RUNBOOK.md` §9 |
| **TASK-W47-03** | the committed GPU benchmark | the 12.6× claim becomes a re-runnable measurement or nothing | OD-043 | **W48 — done.** `75937934`: `scripts/gpu-benchmark.sh` and its harness. The record is `docs-v2/engineering/W48-GPU-BENCHMARK.md`. **The claim reproduces (≈12× on MATRIX's own forward call); the digit 12.6× does not** — three runs give 12.06× / 11.68× / 11.47×, and the historical figure stays **HISTORICAL**, bounded by that measurement |
| **TASK-W46-11** | the full `matrix-core` suite | the project's total test population becomes a number | OD-041 | **approved and run in W47** — one JVM, an 8 GiB heap, the other daemons stopped |

### 4.7 The tasks the W48 wave created

**Three tasks, and only two of them are implementations.** The wave's own task ids are in the
fourth column; the ids of this roadmap are stable and are the ones to use.

| the id | the task | the goal | the wave's task | the state | the evidence |
|---|---|---|---|---|---|
| **TASK-W48-01** | the legacy-snapshot compatibility | a restore must not destroy what the payload does not carry: an incomplete legacy payload is **refused before any destructive change**, and the refusal is typed | **W48-01** — the preflight's **P0** | **done** — `4084538a` (the guard) and `4ddcb58d` (the contract) | `SnapshotCoverage` and `IncompatibleSnapshotException`, identifier `INCOMPLETE_LEGACY_SNAPSHOT`; the contract is `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` |
| **TASK-W48-02** | the CI stabilisation | the three red workflows of the preflight become green, and every fix is proved by a check that can still fail | **W48-02 … W48-05** | **done** — `0c5b3980` (the gateway's path-portable tests), `d1f2e0fb` (the security check cannot open the live database), `f5870f4c` (the documentation gate is classified, not hidden), `02d2a516` (the GraalVM distribution and the SDK version contract), `16a2d0c5` and `9bb6bf5c` (the formal tool is pinned by bytes and verified by digest in both workflows) | `docs-v2/engineering/W48-CI-RECONCILIATION.md` — the state of every workflow, the cause behind every failure, and the negative control of every fix |
| **TASK-W48-03** | the autonomy impulse consumer | an impulse that the scheduler **accepted** should be **consumed** by a bounded internal proposal — and not by anything that acts | **W48-11** | **APPROVED by the owner on 2026-11-06 (OD-045, option A) — and NOT IMPLEMENTED.** The scope is the ADR's §5 with the ten tests of its §9; the implementation is the **first task of the next wave**. W48 wrote the ADR and nothing else |

**What TASK-W48-03 is not.** It is **not** an implementation task in execution: no consumer
exists, no proposal is created, and the scheduler's `FIRED` outcome still means only that
the scheduler **accepted** the impulse — not that work was dispatched, consumed, executed or
completed. The ADR that scopes it is
`docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` <!-- no-id: the ADR declares this identifier in its own heading; this line references the document, it does not define it --> —
**approved, not implemented**. Until the next wave writes it, the truth is unchanged: **the
scheduler accepts impulses, nothing consumes them.**

**Two more decisions of 2026-11-06, and what they leave open.** **OD-046** — the native-image
build gets a **measured** resource budget in the next wave (the heap and the runner size it
actually needs are recorded), and the native capability **stays `BLOCKED`** until a build
succeeds. **OD-047** — the GPU is an **optional accelerator**, not part of the default
serving path: the measurement is kept, the silent CPU fallback stays visible in the
registry, and no deployment is obliged to provide the CUDA libraries; **no serving-path
change is required or proposed**. Neither decision creates an implementation task inside
W48.

### 4.8 The tasks the W49 wave created

**W49 carries two kinds of work: the two implementation tasks the W48 wave left approved (TASK-W48-03 and OD-046), and the public-research workstream the owner opened with OD-048.** The ids of this roadmap are stable and are the ones to use.

| the id | the task | the goal | the state | the evidence |
|---|---|---|---|---|
| **TASK-W49-01** | the autonomy impulse consumer | an impulse the scheduler **accepted** becomes a bounded internal proposal with provenance, an accounted budget, a stable id, an expiry and an observable terminal outcome — and **nothing else happens** | **done — `c86f6898`** — the scope is `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` §5 and the acceptance criteria are its §9's ten tests and §6's eight safety invariants | `docs-v2/engineering/W49-STATUS.md` §3 — 12 tests, 0 failures, and a negative control that goes red |
| **TASK-W49-02** | the native-image resource budget | the heap and the runner size the native build actually needs are **measured**, and the capability stays `BLOCKED` until a build succeeds | **done — the measurement, not a build (OD-046)** — the heap is **> 8 GB** (8 GB OOMs; 16 GB clears the memory wall) and the runner needs **more than GitHub's standard 16 GB**; the build still fails on PDFBox's missing optional BouncyCastle module | `docs-v2/engineering/W49-NATIVE-BUDGET.md` — two measured attempts; the capability stays BLOCKED |
| **TASK-W49-03** | the public-presence audit | every high-visibility public document is compared with the repository, and the differences are listed before anything is rewritten | **done** | the audit in `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` |
| **TASK-W49-04** | the root README | an English README with a Russian companion, a clear language switch, and no stale claim | **done** — the README and its Russian companion | `README.md`, `README.ru.md` |
| **TASK-W49-05** | the Pages landing page and the high-visibility pages | the site's entry pages describe the current architecture, the capability maturity and the evidence | **done** — 35 pages rewritten or corrected, plus their Russian equivalents | `docs-v2/index.md`, `docs-v2/ecosystem/**`, `docs-v2/guide/getting-started.md` |
| **TASK-W49-06** | the Pages deployment path | the Jekyll build is **previewed** for pull requests, `url`/`baseurl` are validated against the **actual** deployment URL, and publishing stays on the reviewed `main` path | **done** — a pull-request build that does not publish, and a validator that checks the configuration against the deployment URL read from the API | `.github/workflows/pages.yml`; the recorded deployment URL and commit |
| **TASK-W49-07** | the contributor onboarding | a newcomer can participate without reading the wave archive; the research and pilot pathways ask for reproducibility | **done** | `CONTRIBUTING.md`, the issue templates, `CODE_OF_CONDUCT` |
| **TASK-W49-08** | the publication pipeline | a technical report has a defined lifecycle — `IDEA → OUTLINE → DRAFT → INTERNAL_REVIEW → REPRODUCED → SUBMITTED → PUBLISHED` — with the evidence each state requires | **done** | `docs-v2/research/PUBLICATION-PIPELINE.md` |
| **TASK-W49-09** | the collaboration brief and a reproducible opportunity | one concrete opportunity an external researcher can actually execute, and a brief that names what MATRIX is and is not | **done** — three opportunities, each one executed | `docs-v2/research/COLLABORATION-BRIEF.md`, `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` |
| **TASK-W49-10** | the wave report and the governance reconciliation | the wave's own report, the registers, and the **Public Research Impact Check** that every future wave must carry | **done** | `docs-v2/engineering/W49-STATUS.md` |

**What TASK-W49-01 is.** It is the implementation `TASK-W48-03` asked for. The scope is
**unchanged** from the approved ADR: the consumer creates and records typed internal
proposals and does nothing else — no messages, no shell commands, no external services,
no Chain A mutation. **The scheduler's `FIRED` outcome still means "accepted", and after
this task it means "accepted and recorded as a proposal" — never "executed".**

### 4.9 The tasks the W50 wave created

**W50 is the wave that turned research subsystems into a runnable, evidence-grounded
learning system.** The ids of this roadmap are stable and are the ones to use; the wave's
own task ids are in the fourth column.

| the id | the task | the goal | the wave's task | the state | the evidence |
|---|---|---|---|---|---|
| **TASK-W50-01** | the continuity and the preflight | the wave starts from a known state and writes down what is **not** done before it changes anything | **W50-00** | **done** | `docs-v2/engineering/W50-STATUS.md` — the honest opening state, written before the work |
| **TASK-W50-02** | the design | the demonstrator's shape — the learning path, the interface, the acquisition boundary — is written before it is built | **W50-01** | **done** | the same report, §"What exists now" |
| **TASK-W50-03** | the corpus and the acquisition | a bounded, licensed corpus in **one** domain, and a bounded acquisition cycle over a pre-cached pool | **W50-02** | **done** | `matrix-core/src/main/resources/corpus/plate-tectonics/corpus-manifest.json` and `matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json`; the seed import produced **348 claims**, the cycle **3 documents and 324 claims** |
| **TASK-W50-04** | the local model evaluation | one local open-weight model is measured against the deterministic extractor, and the teacher is kept or dropped **on the measurement** | **W50-03** | **done — the measurement, and the verdict: the teacher is not better and is not called** | `docs-v2/engineering/W50-LOCAL-MODEL.md` — **0** teacher invocations during the deterministic acquisition, and **72.7 %** of the teacher's propositions verbatim against the deterministic path's **100 %**; the decision is OD-049 |
| **TASK-W50-05** | the validation and the revision | a new source can revise the model, and the earlier claim **stays in the history** | **W50-04** | **done** | the demonstration's turn 4: **exactly one** supersession (relatedness 0.83) — the earlier claim SUPERSEDED (version 2), the new one SUPPORTED (version 2) |
| **TASK-W50-06** | the runnable local application | the demonstrator runs on the owner's machine from one command, with no network and no model | **W50-05** | **done** | `scripts/run-local.sh`; the packaged jar on `http://127.0.0.1:9091` |
| **TASK-W50-07** | the four-turn demonstration and its evaluation | the demonstration is actually **run**, and its answers are checked against a key that is **not** in the application | **W50-06** | **done** | `docs-v2/demos/local-learning-loop/OWNER-SEQUENCE.md` and `docs-v2/demos/local-learning-loop/ANSWER-KEY.md` — the run of 2026-11-07 |
| **TASK-W50-08** | the public demo package | a reader who has never seen the demonstration can reproduce it | **W50-07** | **done** | `docs-v2/demos/local-learning-loop/README.md` |
| **TASK-W50-09** | the closeout | the canonical registers are refreshed and the release pull request is opened | **W50-08** | **the registers are refreshed; the pull request `develop` → `main` is opened by the lead and is NOT merged by an agent** | this section; the wave report |

**What the wave's scope does NOT include.** The demonstrator is one bounded domain —
plate tectonics — on one local machine. The false-premise judgement is **lexical**, not
semantic; an answer's support value is **0.0**, because an imported claim carries no
authored confidence; the pool is **pre-cached and versioned**, not a live web search; and
the status route leaves the workspace list and the counts empty. Those are the risks
R-W50-01…R-W50-03 and the questions Q-W50-01…Q-W50-03 — named limits, not a defect of the
wave.

### 4.10 The tasks the W51 wave created

**W51 is the wave that turned a collection of subsystems into one interacting cognitive
system.** The ids of this roadmap are stable and are the ones to use; the wave's own task
ids are in the fourth column.

| the id | the task | the goal | the wave's task | the state | the evidence |
|---|---|---|---|---|---|
| **TASK-W51-01** | the audit and the classification | every subsystem is classified as KEEP / INTEGRATE / ADAPT / REWRITE CANDIDATE / ARCHIVE, with a path, a caller and a reason | **W51-00** | **done** | `docs-v2/engineering/W51-AUDIT.md` §2 — 19 subsystems classified; §7 — 12 entries that remain disconnected, experimental or archived, with the reason |
| **TASK-W51-02** | the canonical cognitive contracts | the concepts of the mandate are meaningful data contracts, not empty interfaces, and a candidate belief can never become an established one by similarity | **W51-01** | **done** | `io.matrix.contract` — 23 records; `CanonicalContractsTest` — **33 tests, 0 failures**; the status vocabulary separates OBSERVED / ASSERTED / CANDIDATE / SUPPORTED / CONTESTED / REFUTED / UNRESOLVED / SUPERSEDED |
| **TASK-W51-03** | the bridge between the chains | the parts of MATRIX interact through explicit, versioned, governed contracts — and the crossing is the only one | **W51-02** | **done** | `io.matrix.contract.bridge` (the protocol), `io.matrix.bridge` (the chain A service), `io.matrix.brain.runtime.bridge` (the chain B client); **76 tests, 0 failures**; the cross-process demonstration `scripts/w51-bridge-demo.sh` — **10 checks, 0 failed**; the gate's G7/G8/G9 and the controls C3/C4 |
| **TASK-W51-04** | the integrated cognitive runtime | the parts of the system are declared, their capabilities are named, and their dependencies are checked | **W51-03** | **done** | `io.matrix.runtime.{CognitiveRuntime,MatrixRuntimeComposition}` — **6 tests, 0 failures**; the decision is CD-022 (a declaration registry, not a service locator and not a global bus) |
| **TASK-W51-12** | the world-state memory | the role the audit found missing: a memory of the system's estimate of a domain, consulted before an action, with its transitions recorded | **W51-11** | **done** | `io.matrix.cognitive.worldstate.{WorldStateMemory,StateFact,MicroWorldStateAdapter}` — **6 tests, 0 failures**; it is a projection over the append-only case log, so it survives a restart without a sixth database; the canonical `WorldState` and `StateTransition` contracts got their first production user, and the cycle's trace carries a `WORLD` step |
| **TASK-W51-05** | the learning through an environment | the system learns by interaction, persists what it learned, and reuses it | **W51-04** | **done** | `io.matrix.learning.env` over the existing micro-world — **13 tests, 0 failures**; and the loop is now run **through the cognitive runtime** in the demonstration: 16 → 4 → 2 → 1 surviving hypotheses over three cycles, every probe a durable case, and the restored session starts with one survivor instead of sixteen |
| **TASK-W51-13** | the method-performance memory | the method-selection layer can weigh historical measured performance and previous outcomes — which it could not, because nothing ever recorded a measurement | **W51-12** | **done** | the kernel measures the method it runs and records it in the shared `CostLedger`; the demonstration's decision record shows the transition — the first cycle says `DECLARED_COMPLEXITY`, the second and third say `MEASURED` |
| **TASK-W51-14** | the cycle's reflection | reflection as an evaluation of the actual decision and outcome, producing machine-readable decisions — not more text about the result | **W51-13** | **done** | `io.matrix.cognitive.reflection.CycleReflection` — **5 tests, 0 failures**; the canonical `Reflection` and `Evaluation` contracts had no producer and now have one; the cycle's trace carries a `REFLECT` phase and the outcome carries the reflection, its learned items, its uncertainties and its decisions |
| **TASK-W51-15** | the meta-memory | what the system knows about the reliability of its own knowledge — the last of §4's six memory roles that had no carrier | **W51-14** | **done** | `io.matrix.knowledge.meta.KnowledgeReliability` — **4 tests, 0 failures**; a projection over the durable claims and revisions: how many of a source's claims and of a validation method's claims were revised afterwards, with the reasons carried next to the numbers; it decides nothing about citability — that stays with the claim's own status |
| **TASK-W51-06** | the plugin architecture and creator mode | a capability can be created, validated, granted, used and revoked, inside an isolation that is a boundary | **W51-05** | **done** | `io.matrix.plugin` — **21 tests, 0 failures**; the lifecycle PROPOSED → BUILDING → SANDBOXED → VALIDATED → SHADOW → ACTIVE is walked in full; the isolation is a closed declarative interpreter (CD-023) and the creator is bounded to one family of capability (CD-024) |
| **TASK-W51-07** | the Java/C boundary | the question "is the native path faster?" is answered by measurement, with the equivalence proved | **W51-06** | **done — and the answer is that the Java path stays selected** | `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md`; **31 tests, 0 failures**; the native path is 17–20× faster than the BIR reference API but only **0.75–1.02×** against the same reference code on flat data, and **67 %** of the native call is the marshalling; the decision is CD-025 |
| **TASK-W51-08** | the W50 epistemic reliability | the 29-case evaluation is machine-readable, has a committed runner, and the eight named defects are fixed | **W51-07** | **done — 12/29 → 18/29, and the eleven that still fail are named** | `scripts/w51-epistemic-eval.py`, `matrix-core/src/main/resources/epistemic-eval/cases.json`, `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.md` + `.json`; the **baseline was reproduced on the same runner** in a clean worktree at `a098f283` (12 PASS / 17 FAIL) before the fixes were credited; per category: unanswerable 2/6 → 6/6, revision 1/5 → 3/5; the knowledge scope is **641 tests, 0 failures** (+13); four existing expectations changed and each became **stricter** |
| **TASK-W51-09** | the safety, isolation and failure handling | the failure modes of §11 are tested against the real components | **W51-08** | **done** | `io.matrix.safety` — **27 tests, 0 failures** over the eleven failure modes; the matrix found **three real defects**, all fixed in this wave and covered by tests of their own (the wall-clock budget of the sandbox, the absent consumer of `RetryPolicy`, the capability check that happened only at activation). A **fourth** defect was found by the final test scope: the two stores that share the owner's database file wrote DEFERRED transactions, so a concurrent write could fail with `SQLITE_BUSY` in about one run in three; both now write IMMEDIATE transactions (§5.7 of the status report) |
| **TASK-W51-10** | the resource and configuration governance | the wave adds no magic numbers, and the touched paths are measured | **W51-09** | **done** | the quality gate: **271 candidates against a baseline of 271** — the wave adds **zero**; the measurements are in `docs-v2/engineering/W51-PERFORMANCE.md` |
| **TASK-W51-11** | the closeout | the report, the canonical registers and the commit | **W51-10** | **done** | `docs-v2/engineering/W51-STATUS.md`; the decisions are CD-020 … CD-025 in the decision register; the commit and the push are Level 3 operations under `AGENT-OPERATING-PROTOCOL.md` §3 and the **owner authorised both on 2026-11-08**. The wave's work is **thirteen commits** on `develop`, pull request **#48** (`develop` → `main`) was opened on 2026-11-09 and **merged by the owner the same day** as the squash `3ef8a5c8` |

### 4.11 The tasks the W52 wave creates

**W52 is the wave that follows the merged W51.** The ids of this roadmap are stable and are
the ones to use; the wave's own task ids are in the fourth column.

| the id | the task | the goal | the wave's task | the state | the evidence |
|---|---|---|---|---|---|
| **TASK-W52-01** | the capability registry learns about W51 and W52 | the repository's own measure of the distance between *implemented* and *served* knows about the wave that moved that distance | **W52-00** | **done** | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` — W51 added ten rows (**43 → 53**) and W52 added one more (**53 → 54**), the validator reports **0 defects**; four of the W51 rows carry an ISLAND or DORMANT axis, because the shared runtime, the plugin system, the meta-memory and the native boundary have no serving path today |
| **TASK-W52-02** | the project state and the registers tell the truth | a reader who opens the snapshot sees the wave that is running, not the one that ran two waves ago | **W52-01** | **done** | `docs-v2/governance/PROJECT-STATE.md` — a fresh snapshot: the merged W51, the active W52, the re-measured routes and CLI count **with their methods**, the four CI findings as measured blockers, and the working tree observed again; `scripts/validate-project-state.py --strict` reports **0 defects** |
| **TASK-W52-03** | the canonical contracts get their producers | §3's twenty-two types are not a vocabulary the runtime never fills, and the two parallel vocabularies are reconciled by an explicit, tested mapping | **W52-02** | **done — with two named absences** | `io.matrix.contract.CognitiveCycle` — the canonical form of a cycle, carried by the outcome; `io.matrix.cognitive.canonical.CanonicalCycleBuilder` — the mapping, named, pure and tested; `CanonicalContractCoverageTest` — **the acceptance: a test that asserts every canonical contract has a producer in the runtime path**, and the demonstration's report says which contracts the cycle filled and which it did not. **Two contracts are absent in the micro-world and the report says so:** the `Procedure` and the `Skill` come from the procedural memory, and the promotion is refused there by design — the W45 finding: the lamp world's input domain is exhausted once the model settles, so the validation set can never hold the two independent observations the promotion requires |
| **TASK-W52-04** | the second direction of the inter-chain contract | chain A → chain B exists under the same governance as chain B → chain A: versioned envelopes, an idempotency key, a retry and timeout policy, a bounded queue, a capability check, a durable audit and provenance | **W52-03** | **done** | `io.matrix.bridge.KnowledgePublisher` and `io.matrix.api.bridge.KnowledgeBridge` — chain A offers what it established, chain B's brain takes it into its memory; the right names the direction, the signature and the capability and the budget and the idempotency key and the audit are the same governance; the protocol is version 2 and still reads version 1. **The isolation gate grew G10** — **25 checks, 0 failed** — and **the cross-process demonstration exercises both directions in one run**: `scripts/w52-bridge-both-directions-demo.sh`, **10 checks, 0 failed**, with chain B's brain measured to hold **1** item after the first offering and **2** after the second |
| **TASK-W52-05** | the meta-memory across a restart | the system's record of how its own **methods** behaved survives a restart and is read by the method-selection layer | **W52-04** | **done** | `cognitive_method_measurement` — the profile is a table of the cognitive database, written by its single writer; `CostLedger` reads it back at construction; the selector consults it and **names** it: *"measured 5000ns; 2 call(s), 1 success(es), 1 failure(es)"*. `MethodProfileDurabilityTest` — 5 tests, the acceptance: the measurement is written, the store is closed, the store is opened again, and the selector weighs the restored measurement |
| **TASK-W52-06** | the epistemic failures that have an honest fix | the eleven named failures of the W50 evaluation are fixed where that can be done honestly, and the rest stay named with the expectation **strengthened** where it changes | **W52-05** | **done — with the failures kept** | the evaluation was re-run against this wave's build: **18 of 29**, the same as W51, no regression. The eleven failures were then MEASURED rather than argued about: three configurations of the relevance floor, each re-running the whole evaluation — 0.25 weighted (shipped) gives 18/29 with **6/6** unanswerable cases abstaining; 0.25 counted over the question's terms gives 19/29 with **5/6**; 0.12 weighted gives 15/29 with **1/6**. **The floor stays where it is** (CD-029): the answerable and the unanswerable families sit in the same band of coverage, so no threshold separates them. No case was excluded, no assertion weakened and no expectation changed |
| **TASK-W52-07** | the closeout | the report, the registers, the commit, the push and the pull request | **W52-06** | **done — the merge is the owner's** | `docs-v2/engineering/W52-STATUS.md` — the wave's report with the five categories, the measurements and the honest limits; the commit and the push are authorised by the owner's directive of 2026-11-08; the pull request `develop` → `main` is **prepared, not merged by an agent** |

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
| **Q-W46-07** | the GPU path | may the launcher export `LD_LIBRARY_PATH`, and how should the diagnostic report the truth? | **CLOSED on 2026-11-06 — OD-047:** the GPU is an **optional accelerator**, not part of the default serving path; the measurement is kept, the silent CPU fallback stays visible in the registry, and no deployment is obliged to provide the CUDA libraries. The measurement itself is committed (`75937934`, `docs-v2/engineering/W48-GPU-BENCHMARK.md`) and the historical 12.6× digit does not reproduce — it stays historical, bounded by the 12.06×–12.69× measurement |
| **Q-W46-13** | the retention policy | the append-only histories grow without bound | an audit trail that exhausts the disk, or a silent deletion of evidence |
| **Q-W50-01** | whether the repository's own AGPL-3.0 licence interacts with the share-alike of the CC BY-SA 4.0 Wikipedia extracts the corpus embeds | the texts are verbatim and unmodified and the per-file manifest records the attribution — but the interaction is the owner's to sign off | the corpus ships inside the repository without a signed-off licence position |
| **Q-W50-02** | whether the audit trace of an imported claim must carry the byte offset of the quoted span | the import stores the source text as the source excerpt, and the trace carries no offset | an imported claim cannot be located in its source by an offset |
| **Q-W50-03** | whether the Workbench status route must report the workspace list and the counts | it reports the database path and its size and leaves the rest empty | the demonstrator's own workspace is invisible to the route that describes it |

**One more decision was asked and has been answered.** Whether the **first impulse consumer
may be built at all** — and if so, whether its scope is a proposal-creating consumer that
records internal proposals and does nothing else — was put to the owner in the wave's
end-of-wave questionnaire of 2026-11-06. **The answer is OD-045 (option A): the consumer is
approved for the next wave and is NOT implemented in W48.** The ADR that states the question,
the evidence and the three options is
`docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` <!-- no-id: the ADR declares this identifier in its own heading; this line references the document, it does not define it -->.
**Until the next wave writes it, nothing changes: the scheduler accepts impulses, nothing
consumes them.**

**And the W50 wave's four boundaries are recorded as OD-049…OD-052** — the deterministic
extraction path stays the critical path and the teacher stays a teacher; the acquisition
boundary is a pre-cached, versioned, licensed pool and **no live web request is made**; a
user assertion is an input to evaluate and is **never promoted** into the store; and the
corpus is one bounded domain whose per-file licence, revision and sha256 live in a
manifest that the import verifies. They are in
`docs-v2/governance/DECISION-REGISTER.md`.

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
