# MATRIX — RISK REGISTER

**Status:** `CANONICAL` · **Date:** 2026-11-04
**The machine-readable snapshot of this register** is `docs-v2/engineering/W46-RISK-REGISTER.csv` —
the W46.0 audit's evidence. **This document is the register of record**: it is what an agent
reads, what a reviewer diffs, and what the owner decides against. When the two disagree, this
document is authoritative and the CSV is the stale snapshot.

**The rule:** *do not equate a dormant subsystem with a production vulnerability.* Each record
states the **actual exposure** separately from the implementation status — a subsystem that is
implemented, tested and never called has no exposure, and saying so is part of the record.

---

## The register at a glance

| the id | the risk | the severity | the probability | the status |
|---|---|---|---|---|
| [R-W46-01](#r-w46-01--the-destructive-workspace-reload-erases-the-evidence-of-an-observation-while-keeping-its-case-disposition-and-adjudication-history)| The destructive workspace reload erases the evidence of an observation while keeping its case, disposition and adjudication history | high | medium | CLOSED — W47, OD-025 and TASK-W46-16 
| [R-W46-02](#r-w46-02--no-whole-database-backup-or-restore-procedure-exists-for-the-canonical-knowledge-database)| No whole-database backup or restore procedure exists for the canonical knowledge database | high | medium | OPEN 
| [R-W46-03](#r-w46-03--the-writer-guard-does-not-resolve-path-aliases-so-two-writers-can-open-the-same-file-through-different-paths)| The writer guard does not resolve path aliases, so two writers can open the same file through different paths | high | low | OPEN 
| [R-W46-04](#r-w46-04--the-writer-guard-degrades-to-a-per-host-guarantee-on-a-filesystem-that-does-not-coordinate-cross-host-locks)| The writer guard degrades to a per-host guarantee on a filesystem that does not coordinate cross-host locks | medium | low | DOCUMENTED-OPEN 
| [R-W46-05](#r-w46-05--the-root-readme-presents-the-brain-runtime-as-the-project-and-never-mentions-the-epistemic-application)| The root README presents the brain runtime as the project and never mentions the epistemic application | medium | high | OPEN 
| [R-W46-06](#r-w46-06--stateyaml-describes-a-session-of-2026-08-10-with-status-init-and-an-empty-next-list)| state.yaml describes a session of 2026-08-10 with status init and an empty next list | medium | high | OPEN 
| [R-W46-07](#r-w46-07--three-current-documents-reference-a-walmd-that-does-not-exist)| Three current documents reference a WAL.md that does not exist | low | high | OPEN 
| [R-W46-08](#r-w46-08--sessionmd-is-7885-lines--467-kb-at-the-repository-root-and-is-neither-an-index-nor-a-current-state-record)| SESSION.md is 7885 lines / 467 KB at the repository root and is neither an index nor a current-state record | medium | medium | OPEN 
| [R-W46-09](#r-w46-09--the-two-capability-registries-use-an-ambiguous-status-vocabulary-and-predate-w450-w452)| The two capability registries use an ambiguous status vocabulary and predate W45.0-W45.2 | high | high | OPEN 
| [R-W46-10](#r-w46-10--the-full-matrix-core-suite-is-not-run-so-the-total-test-population-is-not-known)| The full matrix-core suite is not run, so the total test population is not known | medium | high | ACCEPTED-OPEN 
| [R-W46-11](#r-w46-11--eight-graalvm-dependent-test-classes-are-permanently-red-in-this-environment-and-are-counted-as-neither-passing-nor-failing)| Eight GraalVM-dependent test classes are permanently red in this environment and are counted as neither passing nor failing | low | high | OPEN 
| [R-W46-12](#r-w46-12--a-full-suite-run-has-twice-produced-spurious-failures-from-a-build-directory-race)| A full-suite run has twice produced spurious failures from a build-directory race | medium | medium | OPEN 
| [R-W46-13](#r-w46-13--qhealthready-answers-503-permanently-in-this-environment)| /q/health/ready answers 503 permanently in this environment | low | high | OPEN 
| [R-W46-14](#r-w46-14--the-tmp-quota-has-taken-the-tooling-down-before)| The /tmp quota has taken the tooling down before | medium | low | OPEN 
| [R-W46-15](#r-w46-15--the-epistemic-application-runs-two-loops-and-the-historical-cycle-map-names-five-cycles-not-six)| The epistemic application runs TWO loops, and the historical cycle map names five cycles, not six | low | high | CLOSED 
| [R-W46-16](#r-w46-16--the-gateways-jax-rs-facade-constructs-a-stub-cycle-and-is-not-mounted)| The gateway's JAX-RS facade constructs a stub cycle and is not mounted | low | high | CLOSED 
| [R-W46-17](#r-w46-17--a-dormant-subsystem-is-not-a-production-vulnerability-and-the-two-must-not-be-conflated)| A dormant subsystem is not a production vulnerability, and the two must not be conflated | medium | medium | OPEN 
| [R-W46-18](#r-w46-18--the-observation-tables-grow-without-a-retention-policy)| The observation tables grow without a retention policy | low | medium | OPEN-OWNER-DECISION 
| [R-W46-19](#r-w46-19--the-documentation-link-graph-is-not-checked-by-anything)| The documentation link graph is not checked by anything | medium | high | CLOSED — W46, the link checker is in the quality gate 
| [R-W46-20](#r-w46-20--w46-itself-must-not-commit-or-push-without-the-owners-authorisation-so-the-waves-evidence-lives-only-in-the-working-tree)| W46 itself must not commit or push without the owner's authorisation, so the wave's evidence lives only in the working tree | low | high | ACCEPTED 
| [R-W46-21](#r-w46-21--two-agents-editing-the-same-file-in-one-wave-the-coordinator-and-a-worker-both-write-into-docs-v2governance-and-a-build-by-one-rewrites-the-compiled-classes-another-is-reading)| Two agents editing the same file in one wave: the coordinator and a worker both write into docs-v2/governance/, and a build by one rewrites the compiled classes another is reading | medium | medium | OPEN 
| [R-W47-01](#r-w47-01--the-trigger-tables-authoritative-copy-lives-in-another-repository-so-the-four-new-skill-slugs-exist-only-in-this-repositorys-fork)| The trigger table's authoritative copy lives in another repository, so the four new skill slugs exist only in this repository's fork | low | medium | OPEN 
| [R-W47-02](#r-w47-02--the-retired-plugin-asset-planretired-backup-still-declares-the-name-plan-which-collides-with-a-live-package)| The retired plugin asset `plan.retired-backup` still declares the name `plan`, which collides with a live package | low | low | ACCEPTED-BY-DECISION 
| [R-W47-03](#r-w47-03--a-fired-autonomy-impulse-has-no-consumer-the-schedulers-counters-are-reachable-but-nothing-subscribes-to-the-impulse)| A fired autonomy impulse has no consumer: the scheduler's counters are reachable, but nothing subscribes to the impulse | medium | medium | OPEN 
| [R-W47-04](#r-w47-04--the-126-gpu-speed-up-is-a-number-from-an-ad-hoc-probe-that-was-never-preserved-so-it-cannot-be-re-run)| The 12.6× GPU speed-up is a number from an ad-hoc probe that was never preserved, so it cannot be re-run | medium | high | OPEN-OWNER-DECISION 

---

## The records

### R-W46-01 — The destructive workspace reload erases the evidence of an observation while keeping its case, disposition and adjudication history

- **category**: data-loss
- **severity**: high
- **probability**: medium
- **impact**: high
- **evidence**: The erasure set of a workspace replacement is the ten tables revision/prerequisite/representation/evidence/derivation/contradiction/learning_goal/claim/source/concept (KnowledgeStore.java:1535-1536). observation_occurrence, observation_disposition, observation_adjudication and epistemic_case are NOT in that set, so a load leaves the case and its decision history but erases the evidence rows the observation wrote.
- **exposure**: Real: the HTTP endpoint is reachable and the owner's workspace is the one being replaced
- **mitigation**: Take the observation tables out of the erasure set, or erase them together with the evidence. The first is the smaller change and it changes what a workspace replacement MEANS, so it is the owner's decision (D3-c).
- **owner**: owner (decision); agent (implementation)
- **status**: CLOSED — the owner decided (OD-025, 2026-11-05) and W47 implemented it (TASK-W46-16, commit `84955ec9`). The four observation tables and the epistemic case are erased WITH the evidence, in the order their references require, and the snapshot carries them. The erasure set grew further in W47 (OD-036): the learning state goes with the knowledge too.
- **the residual**: none of the original defect survives. The schema still declares no foreign key, so the ORDER of the erasure is a contract the code keeps and not a constraint the database checks — the contract test asserts the absence of dangling references instead, and it failed loudly once, on an incomplete fixture.

### R-W46-02 — No whole-database backup or restore procedure exists for the canonical knowledge database

- **category**: data-loss
- **severity**: high
- **probability**: medium
- **impact**: high
- **evidence**: The SQLite backup API (BackupManager / VACUUM INTO) appears nowhere in the production sources (grep over matrix-*/src/main). The only recovery mechanism is the per-workspace logical snapshot of W45.2, and docs-v2/operations/RUNBOOK.md contains no occurrence of backup, restore or snapshot.
- **exposure**: Real: a corruption or a bad migration is not recoverable from anything in the repository
- **mitigation**: Write the file-level backup with SQLite's own mechanism (not a naive copy of a live file with WAL active), a restore drill, and a runbook section. The recovery runbook is a named deliverable of W46.1/46.3.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-03 — The writer guard does not resolve path aliases, so two writers can open the same file through different paths

- **category**: concurrency
- **severity**: high
- **probability**: low
- **impact**: high
- **evidence**: WriterLock.java:145 and :159 derive the lock file from databaseFile.toAbsolutePath().normalize(). normalize() collapses . and .. but does NOT resolve symlinks, so a symlink and its target yield two different lock files and the guard admits both.
- **exposure**: Low but real: it needs a deployment that uses a symlinked or aliased data path
- **mitigation**: Resolve to the real path (Path.toRealPath()) before deriving the lock file, with a fallback to the normalised path when the file does not exist yet. A local defect fix inside an already-approved contract (D4-a).
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-04 — The writer guard degrades to a per-host guarantee on a filesystem that does not coordinate cross-host locks

- **category**: concurrency
- **severity**: medium
- **probability**: low
- **impact**: medium
- **evidence**: The guard is the operating system's lock on a lock file plus the record of the owner. On NFS or SMB the FileLock semantics are unreliable, and the other host's pid is invisible, so the record would be judged dead and the file taken over.
- **exposure**: Conditional: it needs a shared network volume with two writers
- **mitigation**: Document the supported topology as local-only (it already is, in W45.2-CHAIN-BOUNDARY.md and the WriterLock javadoc). Do not introduce a distributed lease without a real multi-host use case, which the owner has explicitly declined.
- **owner**: owner (already declined the alternative)
- **status**: DOCUMENTED-OPEN

### R-W46-05 — The root README presents the brain runtime as the project and never mentions the epistemic application

- **category**: stale-documentation
- **severity**: medium
- **probability**: high
- **impact**: medium
- **evidence**: README.md:1-40 describes the gateway on port 8765 and /v1/analyze as THE entry point. The work of W44, W45.0, W45.1 and W45.2 is entirely in the epistemic and cognitive chain A, and the canonical entry points are declared in io.matrix.api.CanonicalEntryPoint.
- **exposure**: Real: a new agent or a new reader starts from the README and is misled about what the project currently is
- **mitigation**: Rewrite the README as a concise entry point that names both applications and points at the canonical documents. Part of W46.2.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-06 — state.yaml describes a session of 2026-08-10 with status init and an empty next list

- **category**: stale-documentation
- **severity**: medium
- **probability**: high
- **impact**: medium
- **evidence**: state.yaml:1-14 — session id 20260810-130733, status init, artifacts.changed empty, decisions empty, next empty. The repository is at a6ded233 with four waves completed since.
- **exposure**: Real: the file is at the root, so it reads as the current state
- **mitigation**: Reconcile SESSION.md, state.yaml and the new canonical current-state document in W46.2; state.yaml should either be generated or retired with a pointer.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-07 — Three current documents reference a WAL.md that does not exist

- **category**: stale-documentation
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: BRANCHING-STRATEGY.md:147 and :170, INSTALL.md:11 and :43, docs/index.md:7 all point at WAL.md. The file was renamed to wal/GLOBAL_WAL.md + wal/SESSION_WAL.md (recorded in docs-v2/engineering/W40-RECONNAISSANCE.md:71 and verified in W42-VERIFICATION-ARCHAEOLOGY.md:118).
- **exposure**: Real: a reader following the branching strategy is sent to a file that is not there
- **mitigation**: Repair the references with a compatibility pointer, and repair them in the proposed amendments rather than by editing AGENTS.md, which the directive protects.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-08 — SESSION.md is 7885 lines / 467 KB at the repository root and is neither an index nor a current-state record

- **category**: stale-documentation
- **severity**: medium
- **probability**: medium
- **impact**: low
- **evidence**: wc -l SESSION.md = 7885; the file mixes session history with instructions.
- **exposure**: Low: it is a historical journal, not a live instruction, but its size invites the reader to treat it as current
- **mitigation**: Classify it as HISTORICAL with a pointer to the canonical work log, in the document registry, and leave the content untouched.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-09 — The two capability registries use an ambiguous status vocabulary and predate W45.0-W45.2

- **category**: stale-documentation
- **severity**: high
- **probability**: high
- **impact**: high
- **evidence**: docs-v2/architecture/CAPABILITY-REGISTRY.md and docs-v2/engineering/W44-CAPABILITY-REGISTRY.md both exist; the W44 one predates the observation-adjudication history, the request lifecycle, the workspace snapshot, the writer guard and the canonical entry points.
- **exposure**: Real: a reader of either registry gets a capability picture that the code contradicts
- **mitigation**: Build ONE machine-readable registry with the multi-axis status model, and mark the other two as superseded with pointers. Part of W46.1/46.2.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-10 — The full matrix-core suite is not run, so the total test population is not known

- **category**: verification
- **severity**: medium
- **probability**: high
- **impact**: medium
- **evidence**: W44 archaeology recorded 9816 executions with 24 failures in nine GraalVM-dependent classes; W45.2 ran targeted scopes only (910 + 1036 + 914 + 240). The GraalVM tooling is absent on this host, so the native-image classes cannot be executed at all.
- **exposure**: Real but bounded: the targeted scopes that cover the changed subsystems are green and the failures are known and environmental
- **mitigation**: Document the limitation and keep the per-scope baselines commit-specific. A full run needs a resource estimate and the owner's approval, per the directive.
- **owner**: owner (approval for a full run)
- **status**: ACCEPTED-OPEN

### R-W46-11 — Eight GraalVM-dependent test classes are permanently red in this environment and are counted as neither passing nor failing

- **category**: verification
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: The W44 archaeology established the split as 8 native-image classes / 23 failures plus 1 ordinary JVM failure (io.matrix.consciousness.Exp228ComplianceTest). The tooling is absent, so matrix-core/build/native has never existed on this host.
- **exposure**: Low: they fail for an environmental reason, not a code reason
- **mitigation**: Record the split and the reason in the test catalogue, so that nobody reads the count as a regression.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-12 — A full-suite run has twice produced spurious failures from a build-directory race

- **category**: verification
- **severity**: medium
- **probability**: medium
- **impact**: low
- **evidence**: W45.1 recorded 16 spurious failures in the W43 scope caused by two concurrent Gradle builds in the same module; the scope passed cleanly on a solo re-run (182 classes / 1563 tests / 0 failures).
- **exposure**: Low, and avoidable: it needs two builds in one module at once
- **mitigation**: The resource budget must forbid concurrent heavy builds; the W46.3 tooling must serialise them.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-13 — /q/health/ready answers 503 permanently in this environment

- **category**: environment
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: The application declares an optional PostgreSQL datasource at localhost:5433, which is not running, so the readiness probe fails while the application is healthy.
- **exposure**: Low: liveness (/q/health/live) is the correct readiness signal here
- **mitigation**: Document it in the runbook and the test catalogue so that the 503 is not mistaken for a regression.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-14 — The /tmp quota has taken the tooling down before

- **category**: environment
- **severity**: medium
- **probability**: low
- **impact**: medium
- **evidence**: A W42 wave recorded that every start leaked 583 MB into /tmp until the quota took the tools down (commit 9cb03ffc).
- **exposure**: Low but recurrent: any integration harness that writes large artefacts can hit it
- **mitigation**: The resource budget must set a temporary-directory budget and a minimum free-disk threshold; the harnesses must clean up only their own directories.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-15 — The epistemic application runs TWO loops, and the historical cycle map names five cycles, not six

- **category**: architecture
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: The W44 cycle map lists five. io.matrix.reasoning.BrainLoopService is driven by OpenAIChatResource and is served. The W45.2 declaration names both serving paths of chain A.
- **exposure**: Low: the code is right and the document was incomplete
- **mitigation**: The correction is recorded in ADR-W45.2-01 and in the capability delta. The historical W44 documents are evidence and are not rewritten.
- **owner**: agent (done)
- **status**: CLOSED

### R-W46-16 — The gateway's JAX-RS facade constructs a stub cycle and is not mounted

- **category**: security
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: ADR-2026-10-26-002 established that the facade is unmounted and uses StubBrainCycle. The W45.2 isolation gate censuses the nine annotated chain B routes even though they are not mounted, so that a future mount would be visible.
- **exposure**: Low: it is not served
- **mitigation**: Keep the census of the unmounted routes in the gate — it is what makes the decision reversible and reviewable.
- **owner**: agent (Level 1)
- **status**: CLOSED

### R-W46-17 — A dormant subsystem is not a production vulnerability, and the two must not be conflated

- **category**: security
- **severity**: medium
- **probability**: medium
- **impact**: low
- **evidence**: The W44 inventory marks whole clusters as inactive-but-tested with zero production callers, including the measurement cluster that hangs off io.matrix.neuron.ConsciousBrain.
- **exposure**: Low: there is no exposure
- **mitigation**: The capability registry must separate the operational status from the implementation status, so that a dormant cluster reads as dormant rather than as a risk.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-18 — The observation tables grow without a retention policy

- **category**: data-governance
- **severity**: low
- **probability**: medium
- **impact**: low
- **evidence**: observation_occurrence, observation_adjudication and cognitive_request_event are append-only by design (W45.0/W45.1) and nothing prunes them.
- **exposure**: Low: the growth is bounded by the request rate
- **mitigation**: Record the growth in the risk register and decide the retention policy as an owner question, not silently.
- **owner**: owner (decision)
- **status**: OPEN — and now precisely bounded. The owner's decision of 2026-11-05 (OD-035, TASK-W46-17) governs the BACKUPS: three tiers, outside the repository, with a tool that refuses to delete a valid copy. It does NOT bound what the live database keeps: the snapshot payload is still unbounded (the W45.2 residual) and the observation tables still grow. The owner approved the bound on 2026-11-05 (OD-039): a size limit on the payload, and the same three-tier calendar on the snapshot ROWS. That is TASK-W47-01, the first task of W48.

### R-W46-19 — The documentation link graph is not checked by anything

- **category**: verification
- **severity**: medium
- **probability**: high
- **impact**: medium
- **evidence**: The W46.0 audit found broken references to WAL.md in three current documents (R-W46-07). No tool checks the link graph; the wave that could have found them was a human reading.
- **exposure**: Real: the next broken reference will also be found by a human reading
- **mitigation**: The document link checker is a named deliverable of W46.3, with a strict CI mode and a report-only mode.
- **owner**: agent (Level 1)
- **status**: OPEN

### R-W46-20 — W46 itself must not commit or push without the owner's authorisation, so the wave's evidence lives only in the working tree

- **category**: process
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: The W46 directive forbids committing or pushing until the owner reviews the final report. The W46.0 documents are therefore uncommitted.
- **exposure**: Low: the working tree is preserved, and the wave's documents are files
- **mitigation**: Present the final report with the exact file list and ask for the authorisation explicitly.
- **owner**: owner (authorisation)
- **status**: ACCEPTED

### R-W46-21 — Two agents editing the same file in one wave: the coordinator and a worker both write into docs-v2/governance/, and a build by one rewrites the compiled classes another is reading

- **category**: process
- **severity**: medium
- **probability**: medium
- **impact**: medium
- **evidence**: Observed twice in W46.1: the governance directory did not exist and two workers created it, and the W46.0 audit had a worker's Gradle build rewrite the class files while another worker was resolving the constant pool - which also surfaced stale .class files in the repository root (./io/, 3 files) and in matrix-core/bin/ (3435 files) that silently poison a -cp . invocation
- **exposure**: Real: the audit's own numbers were invalidated once and had to be re-derived
- **mitigation**: Disjoint write scopes declared before dispatch; the coordinator reviews the final diff; the compiled tree is verified fresh (find src/main/java -newer build/classes/java/main) before any bytecode conclusion, and classes are never resolved through a relative classpath
- **owner**: coordinator
- **status**: OPEN


### R-W47-01 — The trigger table's authoritative copy lives in another repository, so the four new skill slugs exist only in this repository's fork

- **category**: configuration
- **severity**: low
- **probability**: medium
- **impact**: low
- **evidence**: the generator that writes the trigger mirror is `src/lib/53-auto-skills.sh` — this repository's copy carries the four new slugs, and the authoritative copy lives in `~/Projects/opencode_initializer`, which does not. The installer's progress file (`~/.cache/opencode-setup/progress`) currently marks the step done, so the next run skips the regeneration.
- **exposure**: real but bounded: until the progress file is reset, nothing overwrites the mirror, and the four slugs survive. If the progress file IS reset, the installer regenerates the mirror from the old copy and the four slugs disappear — the skills still exist and are still discovered by the harness, but the trigger table stops suggesting them.
- **mitigation**: bring the two copies together, or write down that this repository's copy is the source of truth for the four MATRIX slugs.
- **owner**: the owner (the other repository is the owner's bootstrap repository)
- **status**: OPEN

### R-W47-02 — The retired plugin asset `plan.retired-backup` still declares the name `plan`, which collides with a live package

- **category**: identity
- **severity**: low
- **probability**: low
- **impact**: low
- **evidence**: `.swarm/bundled-skills/plan.retired-backup/SKILL.md` declares `name: plan`; a live `plan` exists at `.opencode/skills/plan` and in the user's skill root. The asset is retired by its directory name, untracked in git, and reinstalled by the opencode-swarm plugin.
- **exposure**: low — the harness's own name rule forbids a dot, so the package cannot be loaded under its directory name, and the retired asset is not in any of the harness's roots.
- **mitigation**: the owner decided on 2026-11-05 (OD-040, and Q-W47-02) to leave it: reviving a retired bundle or renaming another bundle's file is not the project's to do.
- **owner**: the owner
- **status**: ACCEPTED-BY-DECISION

### R-W47-03 — A fired autonomy impulse has no consumer: the scheduler's counters are reachable, but nothing subscribes to the impulse

- **category**: functional-gap
- **severity**: medium
- **probability**: medium
- **impact**: medium
- **evidence**: `ImpulseScheduler.fire` returns a `FireRecord` and dispatches to nobody; nothing in the tree subscribes to `AutonomyImpulse.CURIOSITY` or `INTEGRITY_CHECK`. The effects of a fired impulse are the budget reservation and the scheduler's counters.
- **exposure**: real — the drive is paid for and counted, and nothing acts on it. Before W47 the drive was not even paid for (the null scheduler) and then was always refused (the envelope of 50 against a cost of 100).
- **mitigation**: the owner's decision of 2026-11-05 (OD-044) raised the envelope to the cost, so the drive is paid for; wiring an impulse to a consumer is a design question that this wave did not answer inside a defect fix.
- **owner**: the owner (the design); the coordinator (the task)
- **status**: OPEN

### R-W47-04 — The 12.6× GPU speed-up is a number from an ad-hoc probe that was never preserved, so it cannot be re-run

- **category**: evidence
- **severity**: medium
- **probability**: high
- **impact**: medium
- **evidence**: the figures (191.367 ms on the CPU against 15.190 ms on CUDA) come from the W46 hardware report; the probe that produced them is not in the repository, so no one can re-run it. The `W46-GPU` row of the capability registry now says so in `next_verification_condition`.
- **exposure**: real — the claim is published in the hardware report and would be read as a measurement.
- **mitigation**: the owner approved a committed benchmark on 2026-11-05 (OD-043): the same model, the same sequence length and iteration count, a warm-up, a median with the dispersion, and the CPU and CUDA cases under comparable conditions.
- **owner**: the agent (the task); the owner (the approval, given)
- **status**: OPEN-OWNER-DECISION — the decision is given; the task is TASK-W47-03, in W48
---

## How a risk is closed

1. The **mitigation** is implemented and **verified** by an executed check — the evidence
   names the command and the exit code.
2. The **status** moves to `CLOSED` and the record stays, with the closing evidence. **A risk
   record is never deleted**: a closed risk is the proof that the project once had it.
3. If the risk is **accepted**, the record says so explicitly and names who accepted it. An
   accepted risk is a decision, and it goes into `docs-v2/governance/DECISION-REGISTER.md`.

## How a risk is added

Any agent may add a record. A record must state the **evidence** (a path, a command or a
measurement) and the **actual exposure** — not the implementation status. A record with no
evidence is a suspicion, and it is marked as such.
