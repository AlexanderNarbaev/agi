# MATRIX — RISK REGISTER

**Status:** `CANONICAL` · **Date:** 2026-11-07 (the W46.0 audit of 2026-11-04, refreshed by W48 on 2026-11-06 and by W50 on 2026-11-07)
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
| [R-W46-02](#r-w46-02--no-whole-database-backup-or-restore-procedure-exists-for-the-canonical-knowledge-database)| No whole-database backup or restore procedure exists for the canonical knowledge database | high | medium | CLOSED for the procedure — W48-08 (OD-042): the restore drill and the runbook section 9 exist and the drill was performed against a copy; the residual is named in the record |
| [R-W46-03](#r-w46-03--the-writer-guard-does-not-resolve-path-aliases-so-two-writers-can-open-the-same-file-through-different-paths)| The writer guard does not resolve path aliases, so two writers can open the same file through different paths | high | low | OPEN 
| [R-W46-04](#r-w46-04--the-writer-guard-degrades-to-a-per-host-guarantee-on-a-filesystem-that-does-not-coordinate-cross-host-locks)| The writer guard degrades to a per-host guarantee on a filesystem that does not coordinate cross-host locks | medium | low | DOCUMENTED-OPEN 
| [R-W46-05](#r-w46-05--the-root-readme-presents-the-brain-runtime-as-the-project-and-never-mentions-the-epistemic-application)| The root README presents the brain runtime as the project and never mentions the epistemic application | medium | high | OPEN 
| [R-W46-06](#r-w46-06--stateyaml-describes-a-session-of-2026-08-10-with-status-init-and-an-empty-next-list)| state.yaml describes a session of 2026-08-10 with status init and an empty next list | medium | high | OPEN 
| [R-W46-07](#r-w46-07--three-current-documents-reference-a-walmd-that-does-not-exist)| Three current documents reference a WAL.md that does not exist | low | high | OPEN 
| [R-W46-08](#r-w46-08--sessionmd-is-7885-lines--467-kb-at-the-repository-root-and-is-neither-an-index-nor-a-current-state-record)| SESSION.md is 7885 lines / 467 KB at the repository root and is neither an index nor a current-state record | medium | medium | OPEN 
| [R-W46-09](#r-w46-09--the-two-capability-registries-use-an-ambiguous-status-vocabulary-and-predate-w450-w452)| The two capability registries use an ambiguous status vocabulary and predate W45.0-W45.2 | high | high | OPEN 
| [R-W46-10](#r-w46-10--the-full-matrix-core-suite-is-not-run-so-the-total-test-population-is-not-known)| The full matrix-core suite is not run, so the total test population is not known | medium | high | CLOSED for this commit — W47, the owner's OD-041: 1226 classes, 9051 executions, 25 failures, none of them the wave's. Two of the 25 are fixed in W48 (241af1e1, 4adbc664) and the 23 native ones are now a BLOCKED scope of their own. The full suite is NOT green |
| [R-W46-11](#r-w46-11--eight-graalvm-dependent-test-classes-are-permanently-red-in-this-environment-and-are-counted-as-neither-passing-nor-failing)| Eight GraalVM-dependent test classes are permanently red in this environment and are counted as neither passing nor failing | low | high | OPEN — MEASURED: 23 failures across 8 classes, and the register's guess of eight was RIGHT. Since W48 the scope is separated, it is BLOCKED and it refuses to run; the default JVM task no longer carries it |
| [R-W46-12](#r-w46-12--a-full-suite-run-has-twice-produced-spurious-failures-from-a-build-directory-race)| A full-suite run has twice produced spurious failures from a build-directory race | medium | medium | OPEN 
| [R-W46-13](#r-w46-13--qhealthready-answers-503-permanently-in-this-environment)| /q/health/ready answers 503 permanently in this environment | low | high | OPEN 
| [R-W46-14](#r-w46-14--the-tmp-quota-has-taken-the-tooling-down-before)| The /tmp quota has taken the tooling down before | medium | low | OPEN 
| [R-W46-15](#r-w46-15--the-epistemic-application-runs-two-loops-and-the-historical-cycle-map-names-five-cycles-not-six)| The epistemic application runs TWO loops, and the historical cycle map names five cycles, not six | low | high | CLOSED 
| [R-W46-16](#r-w46-16--the-gateways-jax-rs-facade-constructs-a-stub-cycle-and-is-not-mounted)| The gateway's JAX-RS facade constructs a stub cycle and is not mounted | low | high | CLOSED 
| [R-W46-17](#r-w46-17--a-dormant-subsystem-is-not-a-production-vulnerability-and-the-two-must-not-be-conflated)| A dormant subsystem is not a production vulnerability, and the two must not be conflated | medium | medium | OPEN 
| [R-W46-18](#r-w46-18--the-observation-tables-grow-without-a-retention-policy)| The observation tables grow without a retention policy | low | medium | OPEN-OWNER-DECISION — the snapshot half is closed by W48-07 (OD-039, commit 33d199e2): the payload is bounded and the rows have a three-tier calendar. The observation tables still grow 
| [R-W46-19](#r-w46-19--the-documentation-link-graph-is-not-checked-by-anything)| The documentation link graph is not checked by anything | medium | high | CLOSED — W46, the link checker is in the quality gate 
| [R-W46-20](#r-w46-20--w46-itself-must-not-commit-or-push-without-the-owners-authorisation-so-the-waves-evidence-lives-only-in-the-working-tree)| W46 itself must not commit or push without the owner's authorisation, so the wave's evidence lives only in the working tree | low | high | ACCEPTED 
| [R-W46-21](#r-w46-21--two-agents-editing-the-same-file-in-one-wave-the-coordinator-and-a-worker-both-write-into-docs-v2governance-and-a-build-by-one-rewrites-the-compiled-classes-another-is-reading)| Two agents editing the same file in one wave: the coordinator and a worker both write into docs-v2/governance/, and a build by one rewrites the compiled classes another is reading | medium | medium | OPEN 
| [R-W47-01](#r-w47-01--the-trigger-tables-authoritative-copy-lives-in-another-repository-so-the-four-new-skill-slugs-exist-only-in-this-repositorys-fork)| The trigger table's authoritative copy lives in another repository, so the four new skill slugs exist only in this repository's fork | low | medium | OPEN 
| [R-W47-02](#r-w47-02--the-retired-plugin-asset-planretired-backup-still-declares-the-name-plan-which-collides-with-a-live-package)| The retired plugin asset `plan.retired-backup` still declares the name `plan`, which collides with a live package | low | low | ACCEPTED-BY-DECISION 
| [R-W47-03](#r-w47-03--a-fired-autonomy-impulse-has-no-consumer-the-schedulers-counters-are-reachable-but-nothing-subscribes-to-the-impulse)| A fired autonomy impulse has no consumer: the scheduler's counters are reachable, but nothing subscribes to the impulse | medium | medium | OPEN 
| [R-W47-04](#r-w47-04--the-126-gpu-speed-up-is-a-number-from-an-ad-hoc-probe-that-was-never-preserved-so-it-cannot-be-re-run)| The 12.6× GPU speed-up is a number from an ad-hoc probe that was never preserved, so it cannot be re-run | medium | high | CLOSED — W48-09 (OD-043), commit 75937934: a committed benchmark replaces the ad-hoc probe, and the 12.6× digit is BOUNDED by it, not replaced. The residual is that the serving path still falls back to the CPU unless LD_LIBRARY_PATH is set 
| [R-W47-05](#r-w47-05--a-property-test-asserts-a-floating-point-quantity-against-zero-without-a-tolerance-so-it-fails-on-a-sample-that-comes-out-a-rounding-artefact-below-zero)| A property test asserts a floating-point quantity against zero without a tolerance, so it fails on a sample that comes out a rounding artefact below zero | low | high | CLOSED — W48-06, commit 241af1e1: the tolerance and a negative control that proves it is not a blanket pass 
| [R-W48-01](#r-w48-01--a-restore-of-an-incomplete-legacy-snapshot-erases-the-history-it-cannot-bring-back-and-reports-success)| A restore of an incomplete legacy snapshot erases the history it cannot bring back and reports success | high | medium | CLOSED — W48-01, commit 4084538a: the coverage contract refuses an incomplete legacy payload before any destructive change. The residual is named in the record |
| [R-W48-02](#r-w48-02--the-ci-toolchain-is-not-reproducible-and-the-gpu-path-is-not-exercised-by-any-ci-job)| The CI toolchain is not reproducible, and the GPU path is not exercised by any CI job | medium | medium | PARTIALLY CLOSED — the formal tool is tracked and verified (16a2d0c5, 9bb6bf5c). The GPU path is NOT exercised in CI: no workflow contains a gpu/cuda/nvidia job |
| [R-W48-03](#r-w48-03--the-mandatory-ci-jobs-depended-on-things-the-runner-does-not-have-and-on-steps-that-had-never-run)| The mandatory CI jobs depended on things the runner does not have, and on steps that had never run | high | high | CLOSED — the three causes are diagnosed from the runner's own logs and fixed (bc87935c, e0de8419); the security boundary and the formal gate are green on a clean runner |
| [R-W48-04](#r-w48-04--the-release-path-needs-credentials-the-repository-does-not-have-so-it-is-red-on-every-push)| The release path needs credentials the repository does not have, so it is red on every push | medium | high | OPEN — external: `gh secret list` is empty; the owner's credentials are required |
| [R-W49-01](#r-w49-01--the-native-build-cannot-succeed-on-the-runner-the-workflow-selects-and-its-second-blocker-is-a-missing-dependency)| The native build cannot succeed on the runner the workflow selects, and its second blocker is a missing dependency | medium | high | MEASURED — the heap and the runner budget are recorded; the capability stays BLOCKED (OD-046) |
| [R-W49-02](#r-w49-02--the-public-face-is-published-from-main-which-now-carries-w49-and-is-behind-develop-by-the-w50-work)| The public face is published from `main`, which now carries W49 and is behind `develop` by the W50 work | high | high | OPEN — the owner's decision: move `main` again, or publish from `develop` (Q-W49-01) |
| [R-W49-03](#r-w49-03--the-shellcheck-workflow-has-been-red-since-at-least-2026-10-31)| The ShellCheck workflow has been red since at least 2026-10-31 | low | low | OPEN — inherited, recorded, not repaired in this wave |
| [R-W50-01](#r-w50-01--the-premise-check-is-lexical-so-a-truncated-premise-can-read-as-contradicted-by-any-claim-that-covers-its-terms)| The premise check is lexical, so a truncated premise can read as contradicted by any claim that covers its terms | medium | medium | OPEN — a named limit of the demonstrator: the verdict is lexical, and nothing is stored |
| [R-W50-02](#r-w50-02--the-extracted-propositions-are-whole-sentences-now-but-the-extractors-corpus-vocabulary-is-a-heuristic-verb-list)| The extracted propositions are whole sentences now, but the extractor's corpus vocabulary is a heuristic verb list | medium | medium | OPEN — the deterministic path stays the critical path; the heuristic is measured, not replaced |
| [R-W50-03](#r-w50-03--the-answering-path-proves-zero-llm-calls-by-instrumentation-and-the-assertion-check-itself-is-a-deterministic-lexical-pass)| The answering path proves zero LLM calls by instrumentation, and the assertion check itself is a deterministic lexical pass | low | medium | OPEN — measured for the acquisition path (0 invocations), proved by construction for the answering path |

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
- **status**: CLOSED for the PROCEDURE — W48-08 (OD-042), commit 090247cb. The procedure now exists and was PERFORMED: `scripts/restore-drill.sh` runs the whole drill against a COPY of the live database in a temporary directory, and `docs-v2/operations/RECOVERY-RUNBOOK.md` §9 records it. The measured result of 2026-11-06: the original is schema v19 with 34 tables and 199 rows, and its digest is unchanged before and after; the copy is identical in every per-table fingerprint; the copy opens under the current code and migrates a derived v16 image 16 → 17 → 18 → 19 to an identical result, with 0 dangling references, a clean start and stop and a second writer refused; the restore into an isolated damaged target gives integrity `ok`, v19 with no migration, and counts and fingerprints equal to the copy's own record. The drill also measures the hazard it protects against: a plain `cp` of the main file is v16 with 30 tables and would silently roll the database back three schema versions while still reporting `integrity_check = ok`.
- **the residual**: what the drill does NOT prove, in its own words (§9.8): it does not restore over the original — a restore over the owner's live database is still the owner's explicit command (OD-029); it does not exercise the PAYLOAD restore, because the live `workspace_snapshot` table holds no rows, so `SnapshotCoverage` and `IncompatibleSnapshotException` were not exercised by it — that restore is covered by `SnapshotRestoreCompatibilityTest` (R-W48-01); it does not prove that a backup taken DURING a migration is restorable; it does not touch `data/mind/mind.sqlite`; it measures correctness, not the cost of a restore on the owner's hardware; and it does not replace the full suite — the owner's one approved full run (OD-041) is spent. The drill is also not scheduled: it is run by hand.

### R-W46-03 — The writer guard does not resolve path aliases, so two writers can open the same file through different paths

- **category**: concurrency
- **severity**: high
- **probability**: low
- **impact**: high
- **evidence**: WriterLock.java:145 and :159 derive the lock file from databaseFile.toAbsolutePath().normalize(). normalize() collapses . and .. but does NOT resolve symlinks, so a symlink and its target yield two different lock files and the guard admits both. **Verified 2026-11-14:** W53's `04b7c5de` fixed a *different* guard of the same class — `DistributedTaskResource.refuseTheKnowledgeFile` now resolves aliases (the directory part through `toRealPath()`, the last element through `readSymbolicLink`, bounded at 16 hops) and `TwoWritersOnOneDatabaseTest` holds it with two symlink refusal tests. **This risk's guard, `WriterLock`, is unchanged** and still admits aliases: the fix is scoped, the broader risk stays open.
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
- **status**: CLOSED for this commit — the owner approved one full run on 2026-11-05 (OD-041), and it ran: **1226 classes, 9051 executions, 23 skipped, 25 failures**, in 25m45s with one test JVM and an 8 GiB heap. The population is now a number rather than a set of scopes. The 25 failures are NOT the wave's: **23** are the GraalVM native-binary classes across **eight** classes (the toolchain is absent; the count and the class count are reconciled in `docs-v2/engineering/W48-TEST-RECONCILIATION.md` §2 against the W44 measurement, class by class), one is the compliance check (PROVED pre-existing on a worktree of the pre-wave commit `84955ec9` — the same 21 unadjudicated wall-clock sites), and one is a FLAKY property test with no floating-point tolerance. **Two of the 25 are fixed in W48** — the property test by `241af1e1` and the compliance check by `4adbc664` — and the 23 native-binary failures are no longer failures of the default JVM task: since `31d8f832` they are a scope of their own that is BLOCKED and refuses to run. **The full suite is NOT green**, and it is not run routinely here: the owner's one approved full run is spent.

### R-W46-11 — Eight GraalVM-dependent test classes are permanently red in this environment and are counted as neither passing nor failing

- **category**: verification
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: The W44 archaeology established the split as 8 native-image classes / 23 failures plus 1 ordinary JVM failure (io.matrix.consciousness.Exp228ComplianceTest). The tooling is absent, so matrix-core/build/native has never existed on this host.
- **exposure**: Low: they fail for an environmental reason, not a code reason
- **mitigation**: Record the split and the reason in the test catalogue, so that nobody reads the count as a regression.
- **owner**: agent (Level 1)
- **status**: OPEN — and MEASURED instead of guessed, and the guess was RIGHT. The full run of 2026-11-05 puts **23 failures across 8 classes** on this account (the native binary was never built: `Cannot run program "build/native/nativeCompile/matrix-core"`); the register's earlier figure of "22 failures across 6 classes" was a transcription error, and `docs-v2/engineering/W48-TEST-RECONCILIATION.md` §2 proves it against the W44 measurement of 2026-11-04, class by class. They are counted as neither passing nor failing, and they are the reason the full suite is red. **What W48 changed is the reporting, not the capability** (commit `31d8f832`): the native-binary scope is now named (`SCOPE-NATIVE-BINARY` in the test catalogue), the default JVM task EXCLUDES it on purpose, and the task `:matrix-core:nativeBinaryTest` REFUSES to run with an explicit message instead of reporting a pass or a failure. Two of the size assertions in those classes were passing VACUOUSLY — `File.length()` is 0 for a missing file, so "the native binary is smaller than 150 MB" held against no binary at all — and the false pass is gone. The capability itself stays BLOCKED: there is no native executable on this host, and the CI workflow that would build one (`.github/workflows/native.yml`, which triggers only on `main`) has 1403 runs on record and its last 100 are 99 failures and 1 cancelled; the most recent run (41219224376, 2026-10-31) set the toolchain up successfully and then died with `Terminating due to java.lang.OutOfMemoryError: Java heap space`.

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
- **status**: OPEN — and now precisely bounded. The owner's decision of 2026-11-05 (OD-035, TASK-W46-17) governs the BACKUPS: three tiers, outside the repository, with a tool that refuses to delete a valid copy. It does NOT bound what the live database keeps: the observation tables still grow. **The snapshot half of this risk is closed** — W48-07 (OD-039, commit 33d199e2): the snapshot payload is now BOUNDED (the default 8 MiB, configurable through `matrix.knowledge.snapshot.max-payload-bytes`), an over-bound payload is REFUSED with the typed error `SNAPSHOT_PAYLOAD_TOO_LARGE` before the write and the transaction rolls back — never trimmed — and the `workspace_snapshot` rows themselves now get the same three-tier calendar as the backups (daily 7 / weekly 4 / monthly 12, per workspace, in UTC), applied by `scripts/snapshot-retention.py`, which is dry-run by default, never deletes a corrupt or undated snapshot, and refuses a pass that would leave a workspace with no recoverable snapshot. The gate row `SNAPSHOT-RETENTION-1` checks it. **What is still open:** the observation tables grow, and the retention pass is not scheduled — it is run by hand, or by the gate in its dry-run mode.

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
- **status**: CLOSED — W48-09 (OD-043), commit 75937934. The benchmark is committed: `scripts/gpu-benchmark.sh` with the harness `matrix-core/src/test/java/io/matrix/benchmark/GpuBenchmarkHarness.java`, and the report is `docs-v2/engineering/W48-GPU-BENCHMARK.md`. The quantity now has a re-runnable measurement behind it: the MATRIX forward call is 160.995 ms on the CPU against 13.345 ms on the CUDA — 12.06× — over three independent runs of 12.06× / 11.68× / 11.47×, with the raw `session.run` at 23.73× and the end-to-end path at 12.69×. The historical 12.6× digit is **BOUNDED, not replaced**: every re-measurement lands 4.3–9.0 % below it and both of its absolute figures are 12–27 % higher than today's, so the W46 report stays the record of what was measured on 2026-11-04. The benchmark refuses to publish a GPU number when the execution profile does not name `CUDAExecutionProvider` (exit 5) and reports NOT VERIFIED when the device, the CUDA libraries, the ONNX Runtime GPU jar or the model is missing (exit 3); both paths were exercised.
- **the residual**: the GPU is served CONDITIONALLY, and the condition is not inside the repository: the serving path falls back to the CPU unless `LD_LIBRARY_PATH` names a libcudart that exports `cudaLibraryGetKernel`, and `.onnx_libs` is gitignored. No CI job exercises the path (R-W48-02). The owner's question Q-W46-07 — whether the GPU belongs in MATRIX's serving path at all — is still open; the measurement half of it is answered, the decision half is not the benchmark's to make.

### R-W47-05 — A property test asserts a floating-point quantity against zero without a tolerance, so it fails on a sample that comes out a rounding artefact below zero

- **category**: test-quality
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: `io.matrix.consciousness.CausalEmergencePropertyTest.propertyEIOfNonNegativeDistribution` asserts `ei >= 0.0` with no offset, and the effective information of a random sample came out as `-3.2034265038149176E-16` in the full run of 2026-11-05. The same test PASSES on a standalone run — it is the seed that decides.
- **exposure**: real, and it is a false failure: it is counted among the 25 failures of the full suite, and a reader of that number would look for a defect that is not there.
- **mitigation**: one line — an offset, the way the neighbouring property in the same class already uses one (`isCloseTo(0.0, Assertions.offset(1e-9))`).
- **owner**: the coordinator
- **status**: CLOSED — W48-06, commit 241af1e1. The test now carries the tolerance `ROUND_OFF = 1e-12` AND a negative control, `aMateriallyNegativeValueIsStillRefused()`, so the tolerance is not a blanket pass: it accepts `-3.2e-16` and still refuses `-0.001` and `-1.0`. The class runs 7 tests, 0 failures, 0 errors.

### R-W48-01 — A restore of an incomplete legacy snapshot erases the history it cannot bring back and reports success

- **category**: data-loss
- **severity**: high
- **probability**: medium
- **impact**: high
- **evidence**: A restore erases the workspace and then writes the payload back. The payload is not "the workspace": it is the typed knowledge content plus the rows of the tables a replacement erases, and WHICH of those tables it carries depends on the build that wrote it. A version-1 payload is the bare knowledge content; a version-2 payload carries the observation family; only since OD-036 does it carry the learning family as well. A restore of an older payload therefore erased the observation history, or the learning state, or both — and nothing in the flow said so. Found in W48; the fix is commit 4084538a.
- **exposure**: Real, and it was silent: the restore reported success after deleting the learner's history, and the rows it had deleted could not be put back.
- **mitigation**: The coverage contract. `SnapshotCoverage` names what a payload actually carries — KNOWLEDGE_ONLY, OBSERVATION_HISTORY, FULL, UNKNOWN — and it is derived from the payload's own table list: a family is carried when the payload holds an entry for every table of that family, and an entry with no rows is still an entry, because "the table was there and it had no rows" is a fact about the snapshot while "the table is not in the payload" is a fact about the build. Before the transaction and before the write lock, the restore compares the payload's coverage with what the target holds; when the payload lacks a family and the target holds rows of it, the restore is REFUSED with `IncompatibleSnapshotException` — the stable identifier `INCOMPLETE_LEGACY_SNAPSHOT` — and the database is left exactly as it was.
- **owner**: agent (Level 1)
- **status**: CLOSED — W48-01, commit 4084538a. The evidence is twelve tests, 0 failures: the legacy payload against a target with learning rows is refused, and the FULL-DATABASE FINGERPRINT — every row of every table, not the counts — is identical before and after the refusal; the same for a version-2 payload without the learning family and for a version-1 payload against a target holding an observation history; a legacy payload against a target that holds none of the missing rows restores; a complete payload restores all eight learning tables and the observation history stays referentially consistent; a payload from a newer build and a corrupt payload are refused with the database unchanged; a payload naming a table the store does not capture fails inside the transaction and leaves no partial replacement; and a restore does not touch another workspace. The inspection contract was repaired with it: `empty()` now covers everything the payload carries, and the snapshot says what it carries (`coverage`) and how much of it (`untypedCounts`).
- **the residual**: the refusal is CONDITIONAL on what the target holds. A legacy payload against a target that holds none of the missing rows is ALLOWED, and the restored workspace then has less history than the build that wrote the payload — the restore cannot lose what is not there, but it cannot report the difference either. The restore drill (R-W46-02) does not exercise the payload restore at all: the live `workspace_snapshot` table holds no rows, so the coverage contract is proved by tests and not by the drill. And the payload's own size is now bounded (R-W46-18): an over-bound payload is REFUSED with `SNAPSHOT_PAYLOAD_TOO_LARGE` rather than trimmed — so a workspace whose payload crosses the bound cannot be replaced until the bound is raised.

### R-W48-02 — The CI toolchain is not reproducible, and the GPU path is not exercised by any CI job

- **category**: verification
- **severity**: medium
- **probability**: medium
- **impact**: medium
- **evidence**: Two facts, both measured. First, the formal tool was fetched from an upstream URL that had been RECREATED: the tlaplus `v1.8.0` release was created on 2026-11-06 and serves different bytes (sha256 `654aa501…`, 4 509 176 bytes) than the pinned ones (`fc6878c6…`, 4 504 421 bytes), and the `ci` workflow's quality-gates job downloaded the upstream and correctly refused them — the check was right and the SOURCE was replaceable. Second, the GPU path is not exercised by CI at all: `grep -rniE "gpu|cuda|nvidia" .github/workflows/` returns NO match in any of the seven workflows (`branch-validation.yml`, `ci.yml`, `docs-validation.yml`, `native.yml`, `pages.yml`, `shellcheck.yml`, `tla.yml`), and `.onnx_libs` — the symlinks into the virtualenv's CUDA libraries — is gitignored, so the libraries the measurement needs are not in the repository.
- **exposure**: Real and bounded. The formal tool was the only replaceable input, and it is now pinned by bytes. The GPU path is unproven in CI, which means the committed benchmark is reproducible on the owner's machine only. The native path is unproven in CI for a third reason: `.github/workflows/native.yml` triggers only on `main`, so `develop` never reaches it (R-W46-11).
- **mitigation**: The formal tool is TRACKED — `tools/tla2tools.jar`, with its provenance in `tools/README.md` — and VERIFIED by digest: `16a2d0c5` makes `.github/workflows/tla.yml` and `scripts/formal-gate.sh` verify the tracked bytes instead of downloading them, and `9bb6bf5c` makes the `ci` workflow's step do the same, so the two workflows cannot drift apart again (`grep -rn "releases/download/v1.8.0/tla2tools.jar" .github/workflows/` returns nothing). For the GPU: a CI job on a runner that has the CUDA libraries and the documented model digests — the graph sha256 `5f59f2cd…` and the external weights sha256 `72303ef1…`.
- **owner**: agent (Level 1) for the pinning; the owner for a GPU runner, which is a cost decision
- **status**: PARTIALLY CLOSED — the REPRODUCIBILITY half is closed by `16a2d0c5` and `9bb6bf5c`: the tool is in the repository and its digest is checked by every workflow that uses it. The COVERAGE half is OPEN: no workflow exercises the GPU path, and no workflow produces a native image on `develop`. Nothing in CI would notice if the CUDA provider stopped executing — which is exactly the gap the committed benchmark (R-W47-04) fills on the owner's machine and only on the owner's machine.

### R-W48-03 — The mandatory CI jobs depended on things the runner does not have, and on steps that had never run

- **category**: verification
- **severity**: high
- **probability**: high
- **impact**: high
- **evidence**: The `ci` workflow's five mandatory jobs were red on the clean runner, and every red job had a cause that the job's own steps contained. (a) The quality-gates job starts the real application, and the application opens its Redis cache eagerly (`matrix-core/src/main/resources/application.properties:14`); the runner's log carried `io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused (connect failed): localhost/127.0.0.1:6379` at `io.matrix.redis.NeuronCacheService_Bean.get(...)`. (b) The security job had NO `actions/setup-java` step, so it inherited the runner's `JAVA_HOME` — `/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/25.0.1.6/Contents/Home`, a macOS-style path that does not exist on a Linux runner — and Gradle refused to start with "ERROR: JAVA_HOME is set to an invalid directory". (c) The container smoke tests probed PUBLISHED ports while the applications bind the loopback interface by default (`NetworkExposure.LOCAL_ONLY`, `application.properties:44`), so the published ports reached nothing; the api-gateway probe failed for its whole 80-second window while the container's log said the application had started — and the startup line printed a hardcoded `0.0.0.0`, so the log misdescribed the interface the process held. (d) The SpotBugs step ran under `bash -e`, so its failing pipeline aborted the step before the step's own "known limitation" branch could read the log, the warning never printed, and the two REAL checks after it — the secret scan and the vulnerable-dependency check — were SKIPPED. (e) The minikube smoke test reused the Postgres and Redis containers of the previous step, which that step removed when it finished: "Unable to connect to redis-ci/<unresolved>:6379".
- **exposure**: The jobs had never run to completion, so none of these was a defect of the application: they were defects of the JOBS. The exposure was threefold — a red pipeline that said nothing about the code, two security checks that did not run at all, and a startup log that stated an interface the process did not hold.
- **mitigation**: (a) the quality-gates job declares the Redis service it needs; (b) the security job installs the same JDK 25 as the other jobs; (c) the gateway's startup line names the address and the port the listener actually bound, and `GatewayExposureBoundaryTest` pins the boundary with a red/green control; the smoke tests mirror the deployed configuration; (d) the SpotBugs step captures the pipeline's status instead of aborting on it; (e) every smoke test brings up its own dependencies.
- **owner**: agent (Level 1) — the causes were in the workflow's own steps, and the W48 directive sanctions the repair of a red mandatory check.
- **status**: CLOSED — the run `42206946030` carries **all five mandatory jobs as GREEN**: Build & Test, the Web UI tests, the quality gates (the security boundary and the formal gate both hold there), the security scan (the secret scan ran for the first time and found nothing) and the container images (three smoke tests). The release path's missing credentials are a separate record (R-W48-04). **The residual is a separate risk**: the pinned SpotBugs engine (4.9.3, ASM 9.7.1) cannot read Java 25 bytecode, so the static-analysis check produces NO evidence at all; an engine that can (4.10.5, ASM 9.11) reports 1 414 findings locally, 981 of them `EI_EXPOSE_REP*`. The capability therefore stays NOT VERIFIED, and the policy for those findings is the owner's decision.

### R-W48-04 — The release path needs credentials the repository does not have, so it is red on every push

- **category**: operational
- **severity**: medium
- **probability**: high
- **impact**: high
- **evidence**: The run `42206946030` is green in all five MANDATORY jobs, and red in one: "Docker Build & Push (api-gateway)". Its FIRST step fails with `::[error]Username and password required`. The step reads `secrets.DOCKERHUB_USERNAME` and `secrets.DOCKERHUB_TOKEN` (`.github/workflows/ci.yml:460-463`), and the repository has no secrets at all: `gh secret list` and `gh variable list` both return nothing. The deploy jobs below it need `K8S_STAGING_KUBECONFIG`, `K8S_PROD_KUBECONFIG`, `STRIPE_WEBHOOK_SECRET` and `LICENSE_SIGNING_SECRET`, which are equally absent. Before W48's repairs this job never ran at all: it was skipped because its dependencies were red, so the missing credentials had never surfaced.
- **exposure**: The pipeline is red on every push to `develop` for a reason that has nothing to do with the code, and the red step is the FIRST one — so a reader sees a credential problem where they expect a build problem. The images are built and smoke-tested by the container job, so the artifacts are verified; what cannot happen is their PUBLICATION and the two deployments.
- **mitigation**: Either the owner configures the credentials, or the release job is declared to run only where they exist (a job that cannot run is a red job, not a safety net). W48 does neither: the credentials are the owner's to set, and marking the job away would remove the release path from the pipeline without an owner decision.
- **owner**: the owner — the credentials and the release policy are both owner decisions.
- **status**: OPEN — external, precisely stated. The five mandatory checks are green (`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.11); this record is the release path, not the verification path.

### R-W49-01 — The native build cannot succeed on the runner the workflow selects, and its second blocker is a missing dependency

- **category**: operational
- **severity**: medium
- **probability**: high
- **impact**: high
- **evidence**: The native workflow builds with Quarkus's default native-image heap, which is **8 GB** — the CI's own log says `7.64GB of memory (45.6% of system memory, set via '-Xmx8g')`, i.e. a ≈16.75 GB machine — and it dies with `java.lang.OutOfMemoryError: Java heap space`. W49 measured the budget directly (`docs-v2/engineering/W49-NATIVE-BUDGET.md`): **8 GB fails**, **16 GB clears the memory wall**, and a build that needs more than 8 GB of heap does not fit on the standard 16 GB runner. With the memory resolved, the build stops at a second blocker: `NoClassDefFoundError: org/bouncycastle/cms/CMSException` — PDFBox declares BouncyCastle as an **optional** dependency and `matrix-core/build.gradle:72` does not add it.
- **exposure**: The native capability is **BLOCKED** and has been for 1 403 recorded CI runs. The 25 native-binary test failures are counted as neither passing nor failing, so the project's total test population stays unknown (R-W46-10). The risk is not "the build is flaky" — it is that the build **cannot** succeed on the runner the workflow selects, so no configuration of the code alone can fix it.
- **mitigation**: measured, not guessed. The workflow needs a runner with more than 16 GB, and the application needs the BouncyCastle module that PDFBox's own POM declares. Both are one line each. The W49 wave measured and recorded them; it did not apply the dependency, because it is the owner's call (Q-W49-05).
- **owner**: the owner — a larger runner is a cost decision, and the dependency changes the application's footprint
- **status**: MEASURED, not closed — the budget is known (`docs-v2/engineering/W49-NATIVE-BUDGET.md`) and the capability stays **BLOCKED** until a build succeeds.

### R-W49-02 — The public face is published from `main`, which now carries W49 and is behind `develop` by the W50 work

- **category**: operational
- **severity**: high
- **probability**: high
- **impact**: high
- **evidence**: The repository's default branch is `main`, and it **now carries W49**: pull request #44 (`develop` → `main`) was merged on 2026-11-06 as the squash `79045c57`, whose tree is identical to the W49 close `8f617ce6`. The gap is therefore the W50 work: `git rev-list --count origin/main..develop` → **374** commits of history (the squash keeps `main`'s own history), and the **content** difference between the two branches is exactly **27 files** — the W50 corpus, the learning routes and the demonstration. The GitHub Pages site is still built from `main` (`.github/workflows/pages.yml` → `on: push: branches: [main]`), so a visitor sees W49 and not W50.
- **exposure**: Everything the W50 wave wrote — the corpus, the learning routes, the premise assessment, the revision and the demonstration — is on `develop` and therefore **invisible** to a visitor. The directive forbids changing the default branch without a separate decision, so the wave cannot close this itself.
- **mitigation**: the wave made the publication path observable and correct without changing the branch: the Pages workflow now builds and validates the site on a pull request without publishing, `scripts/validate-pages-config.sh` checks the configuration against the deployment URL read from the GitHub API, and the rewritten site is ready to publish the moment the owner decides where publishing happens from.
- **owner**: the owner (Q-W49-01) — the default branch, and the publication path, are owner decisions
- **status**: OPEN — the W49 content is now on `main`; what is left is the W50 gap and the decision on where publishing happens from.

---

### R-W49-03 — The ShellCheck workflow has been red since at least 2026-10-31

- **category**: operational
- **severity**: low
- **probability**: high
- **impact**: low
- **evidence**: `.github/workflows/shellcheck.yml` runs only on a pull request to `main`, and this repository has had none — so the gate has not run since the W48 native-image attempt. Its last runs are `41219224444` (2026-10-31, at `e3dc1825`), `39316277048` and `39125667404`: **all failed**. The pull request that carries the W49 work to `main` (#44) ran it for the first time in months: **130 findings across ~30 inherited scripts** (SC2086, SC2155, SC2164, SC2034 and the like). **None of the findings is in this wave's scripts** — `scripts/validate-pages-config.sh` is not among the reported files.
- **exposure**: a red gate on every release pull request, and no signal about new findings — the gate cannot distinguish "nothing new" from "never ran". The findings themselves are style and quoting issues in operational scripts; one of them (`SC2086`, an unquoted expansion) can split a word, which is a real defect class in a path-handling script.
- **mitigation**: none in this wave, deliberately. Repairing 130 findings across ~30 scripts — among them `w45-chain-isolation-check.sh`, `w45-cognitive-recovery-journey.sh` and `security-boundary-check.sh`, the operational checkers themselves — means editing the scripts that decide whether the system is considered healthy, and that deserves its own wave with its own evidence. The honest record is that the gate was already red before this wave and that nothing in this wave made it worse.
- **owner**: the owner — whether the shell gate is repaired, or its scope is narrowed to the scripts this repository maintains, is a decision about the gate's meaning, not a mechanical fix
- **status**: OPEN — inherited, recorded, not repaired

---

### R-W50-01 — The premise check is lexical, so a truncated premise can read as contradicted by any claim that covers its terms

- **category**: evidence-quality
- **severity**: medium
- **probability**: medium
- **impact**: high
- **evidence**: `PremiseAssessment` decides `CONTRADICTED` from **terms**, not from meaning: the minimum subject overlap is 0.30, the minimum shared terms is 2 and the minimum denial coverage is 0.50 (`matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java:168`, `:171`, `:181`). Nothing in the decision is semantic. The demonstration's turn 3 returned `CONTRADICTED` for the premise "…was widely accepted by geologists when it was first proposed in the early twentieth century", contradicted by the claim that "…was widely rejected" — and that verdict is right. But the same rule applied to a **truncated** premise ("continental drift was proposed in the early twentieth century") covers the terms of that claim without being denied by it, so it would read as contradicted too.
- **exposure**: real and bounded: the route assesses one statement at a time, the report names the disputed premise and the contradicting claim and its source, and **nothing is stored** — the response carries `promoted=false` and the assertion never becomes a claim. A wrong verdict therefore corrupts no state; it misleads the reader.
- **mitigation**: keep the check lexical and say so — the route's own response says it is a deterministic check — and require the report to name the contradicting claim and its source, so a reader can check the match by hand. A semantic check would put a model on a decision path, which `CONSTITUTION.md` forbids.
- **owner**: agent (the record); the owner (whether a semantic check is ever wanted)
- **status**: OPEN — a named limit of the demonstrator, not a defect the wave repaired

### R-W50-02 — The extracted propositions are whole sentences now, but the extractor's corpus vocabulary is a heuristic verb list

- **category**: evidence-quality
- **severity**: medium
- **probability**: medium
- **impact**: medium
- **evidence**: `ClaimExtractor` proposes a sentence only when it recognises the sentence as declarative, and it recognises it through `DECLARATIVE_VERBS` — a hard-coded list of base forms, matched with a trailing-`s` fold, an `-es` fold and, since W50, a past-tense fold. The class's own javadoc says the list is a heuristic and that a full part-of-speech tagger would be the proper instrument. W50 extended the list by **24** verbs of the scientific register (142 verbs → 166: "envision", "believe", "suggest", "publish", "measure", "infer", …) and taught the extractor to set aside a leading discourse marker, so that the corpus's own sentences are proposed instead of dropped. The propositions are now **whole sentences** — the Markdown's hard wrap is no longer a sentence boundary — but a sentence whose verb is not in the list is still **not proposed**, and a sentence is proposed on the strength of that verb alone: the text before it is taken as the subject when it is a phrase and not a clause.
- **exposure**: real and bounded: it is a **recall** limit on the corpus vocabulary, and it is visible — the corpus is one bounded domain, the manifest lists every document, and the propositions per document are measurable. The consequence is that a claim the corpus states but the list does not recognise never enters the store.
- **mitigation**: re-measure the extraction against the corpus whenever the list changes — the numbers are in `docs-v2/engineering/W50-LOCAL-MODEL.md` §4 — and tie the list's growth to a measured gap rather than to a guess.
- **owner**: agent (Level 1)
- **status**: OPEN — a named limit of the deterministic path; the wave measured it and did not replace the heuristic

### R-W50-03 — The answering path proves zero LLM calls by instrumentation, and the assertion check itself is a deterministic lexical pass

- **category**: verification
- **severity**: low
- **probability**: medium
- **impact**: medium
- **evidence**: the local-model evaluation counted the teacher's invocations on the deterministic acquisition path with a **validated** counter — a known model call incremented it by 1, and the deterministic path incremented it by **0** — and the cost was 0.04–0.05 s and 58–61 MB of resident memory, against the teacher's 0.31–5.52 s per chunk and 1.41 GiB. That is a **measurement** about the acquisition path. The **answering** path is proved by construction, not by the counter: `KnowledgeRetriever.answer(...)` renders the stored representations, computes the support from the claims' own stored `confidence` and lists what is not covered. And the premise check is a **deterministic lexical pass** — no model is involved in it either, but it is also not an evidence proof: it is the term matching of R-W50-01.
- **exposure**: low, and it is named: the "no model on the path" property is established for the acquisition path **by measurement** and for the answering path **by reading the code**, and the assertion check is lexical in both senses.
- **mitigation**: keep the instrumentation point — the counter, the cost accounting and the kill switch — in front of any model that might ever be plugged in, and keep the answering path free of a model, because `CONSTITUTION.md` forbids LLM calls in decision paths.
- **owner**: agent (Level 1)
- **status**: OPEN — the measurement is the mitigation's evidence, and the property it establishes is narrower than "MATRIX never calls a model"

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
