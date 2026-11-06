# MATRIX — OPEN QUESTIONS

**Status:** `CANONICAL` · **Date:** 2026-11-06
**The machine-readable snapshot of this register** is
`docs-v2/engineering/W46-OPEN-QUESTIONS.csv` — the W46.0 audit's evidence. **This document is
the register of record.** When the two disagree, this document is authoritative.

**The rule:** *do not convert an unanswered question into a fact.* Every record keeps the
competing explanations alive until an experiment or an owner decision settles it — and it says
which.

---

## The register at a glance

| the id | the type | the question | the owner decision | the status |
|---|---|---|---|---|
| Q-W46-01 | architecture-decision | Should the workspace replacement flag stay the query parameter ?replace=true, or become the body field replaceExisting: true named by the W46 target contract? | no | CLOSED |
| Q-W46-02 | product-decision | Should the workspace reload get a dry-run or preview mode that names what would be replaced? | no | CLOSED |
| Q-W46-03 | architecture-decision | What exactly may a workspace replacement erase? Today it erases the evidence of an observation while keeping the case, the disposition and the adjudication history. | no | CLOSED |
| Q-W46-04 | engineering-uncertainty | Does the writer guard admit two writers when the database is reached through two different paths, one of them a symlink? | no | OPEN |
| Q-W46-05 | missing-implementation | How is the canonical knowledge database backed up and restored as a whole? | no | OPEN |
| Q-W46-06 | engineering-uncertainty | What is the actual total test population, given that the full matrix-core suite is never run here? | **yes** | OPEN |
| Q-W46-07 | missing-measurement | Does the GPU actually accelerate anything in MATRIX, and which workload? | no | CLOSED |
| Q-W46-08 | engineering-uncertainty | What is the real concurrency envelope of the build on this machine? | no | OPEN |
| Q-W46-09 | architecture-decision | Which document is the single source of truth for the current state, and what is the fate of SESSION.md and state.yaml? | no | OPEN |
| Q-W46-10 | architecture-decision | Is the capability registry one machine-readable file with a multi-axis status model, and what happens to the two existing registries? | no | OPEN |
| Q-W46-11 | missing-implementation | Is there a link checker, and what does it find in the whole document graph? | no | OPEN |
| Q-W46-12 | engineering-uncertainty | Is the six-cycle truth (two serving paths inside chain A) reflected everywhere it needs to be? | no | OPEN |
| Q-W46-13 | product-decision | What is the retention policy for the append-only observation, adjudication and request-event histories? | no | CLOSED |
| Q-W46-14 | engineering-uncertainty | Does the project still need the gateway's JAX-RS facade and the stub cycle it mounts? | no | OPEN |
| Q-W46-15 | process | How does a session resume after a context loss, without repeating the archaeology? | no | OPEN |
| Q-W46-16 | architecture-decision | The fifteenth TLA+ specification (docs-v2/tla/FederationConsensus.tla) is neither gated by the formal gate nor registered in the specification map: should it move into formal/ and be gated, or be withdrawn? | no | CLOSED |
| Q-W46-17 | engineering-uncertainty | docs-v2/quality/OPEN-DECISIONS.md contradicts itself: the top section declares D1-D8 closed while the bodies of those same entries still read as open. Which is the truth? | no | OPEN |
| Q-W46-18 | product-decision | Two proposals have hung for months with no decision: PR-002 (the weekly CI smoke) and PR-006 (the WAL split). Should they be given a deadline, or rejected? | no | CLOSED |
| Q-W46-19 | engineering-uncertainty | The hypothesis register references eleven identifiers that it never declares. Should they be declared, or should the references be removed? | no | OPEN |
| Q-W46-20 | engineering-uncertainty | The DESIGN and SPEC series were renumbered, and eleven normative references still carry the pre-renumbering names (for example SPEC-008-autonomy-impulses, SPEC-009-decentralized-digests, DESIGN-07-consolidation-cycle, DESIGN-08-hierarchical-memory). Should the references be rewritten to the current names, or should the missing subjects be written? | no | CLOSED |
| Q-W46-21 | engineering-uncertainty | The migration from the v1 tree to docs-v2 removed the tree, and 41 references to it survive in the historical and the current documents (docs/spec/, docs/research/, docs/GLOSSARY.md, data/smoke-old, the archive tarball). Should they be pointed at the v2 equivalents, or marked as the removed tree? | no | OPEN |
| Q-W46-22 | operational-blocker | The harness reads its skills from `.agents/skills/`, `.dsh/skills/` and the user directories — NOT from `.opencode/skills/`. The repository's 27 packages there are invisible to a session (25 arrive through byte-identical mirrors, `coprocessor` through a mirror with 13 extra lines, `matt-pocock` not at all). Should the four recommended MATRIX skills be registered in the harness's trigger table (`src/lib/53-auto-skills.sh` and its mirror), or is the directory enough? | no | CLOSED |
| Q-W46-23 | operational-blocker | Four skill packages declare a name that does not match their directory (`seatch-first`→`search-first`, `scientific-thinking-scholar-evaluation`→`scholar-evaluation`, `plan.retired-backup`→`plan`, and `matt-pocock` without a root SKILL.md). Should they be corrected? | no | CLOSED |
| Q-W49-01 | architecture-decision | The default branch is 357 commits behind `develop` and the public site is built from it. Should `main` be moved to the current work, or should the site publish from `develop` through a reviewed path? | **yes** | OPEN |
| Q-W49-02 | product-decision | Should the recommended repository description and topic tags be applied, and should GitHub's private vulnerability reporting be enabled (there is no private channel today)? | **yes** | OPEN |
| Q-W49-03 | architecture-decision | Who are the authors, in what order, and is a formal release wanted? Without them a `CITATION.cff` could only carry invented values, so the citeable unit is a versioned commit for now. | **yes** | OPEN |
| Q-W49-04 | product-decision | Which of the two ready publication candidates goes first: B (evidence-preserving state and recovery) or C (workload-dependent CPU/CUDA acceleration)? | **yes** | OPEN |
| Q-W49-05 | operational-blocker | The native build needs more than 8 GB of heap and more than GitHub's 16 GB runner, and PDFBox's optional BouncyCastle module is missing. Is a larger runner acceptable, and should MATRIX carry BouncyCastle? | **yes** | OPEN |
| Q-W49-06 | architecture-decision | The static-analysis gate produces no evidence because the pinned engine cannot read Java 25 bytecode; the engine that can reports 1 414 findings, 981 of them `EI_EXPOSE_REP*`. What is the policy for those categories — fix, exclude, or accept? | **yes** | OPEN |

---

## The records

### Q-W46-01 — Should the workspace replacement flag stay the query parameter ?replace=true, or become the body field replaceExisting: true named by the W46 target contract?

- **type**: architecture-decision
- **why it matters**: The flag is the consent boundary of a destructive operation. Its shape decides whether the existing callers keep working.
- **what is known**: The intent is implemented: a non-empty workspace is refused without the explicit consent, and a snapshot is taken in the same transaction. The implemented shape is a query parameter named replace; the W46 directive names a body field named replaceExisting. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-026 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: LearningWorkbenchResource.java:160 (@QueryParam(\replace\") @DefaultValue(\"false\") boolean replace); the refusal body names the parameter at :174"
- **competing explanations**: (a) the directive's wording is descriptive and the shape does not matter; (b) the directive prescribes an exact contract and the shape does matter; (c) the shape was chosen in W45.2 without asking, and asking is still owed
- **available experiments**: A contract note in the ADR and either a compatibility shim (accept both) or a deprecation path for the query parameter
- **cost of resolving**: One small wave: a resource change, a test, a note
- **risk of not resolving**: A destructive operation whose consent parameter may not be the one the owner has in mind
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-026
- **status**: CLOSED

### Q-W46-02 — Should the workspace reload get a dry-run or preview mode that names what would be replaced?

- **type**: product-decision
- **why it matters**: The W46 target contract asks for it. Without it a caller can only learn what a replacement would erase by reading the tables.
- **what is known**: The refusal message names the flag, and the snapshot of a previous replacement is inspectable programmatically through store.workspaceSnapshots(workspaceId). There is no preview endpoint and no dry-run parameter. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-027 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: LearningWorkbenchResource.java:160-190 (the refusal and the response); W45.2-STATUS.md section 2, decision 3
- **competing explanations**: (a) a preview is worth a new HTTP surface; (b) the refusal plus the programmatic inspection is enough; (c) a preview that is not atomic with the replacement can lie
- **available experiments**: A preview parameter that runs the same emptiness check and the same table census without writing
- **cost of resolving**: One small wave: a resource change plus a test
- **risk of not resolving**: A caller must guess what a destructive operation will destroy
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-027
- **status**: CLOSED

### Q-W46-03 — What exactly may a workspace replacement erase? Today it erases the evidence of an observation while keeping the case, the disposition and the adjudication history.

- **type**: architecture-decision
- **why it matters**: The erasure set predates the observation tables, so a replacement leaves a decision whose evidence is gone. Either the observations belong to the workspace, or they do not.
- **what is known**: The erasure set is the ten tables of KnowledgeStore.java:1535-1536. observation_occurrence, observation_disposition, observation_adjudication and epistemic_case are not in it. The W45.2 decision preserved the contradiction history and the adjudication history deliberately. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-025 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: KnowledgeStore.java:1534-1540; W45.2-CAPABILITY-DELTA.md section 1, the row on the contradiction history
- **competing explanations**: (a) the observations are the workspace's content and must be erased with it; (b) the observations are the system's own record and must be preserved with their evidence; (c) the observation's evidence must move out of the erased tables
- **available experiments**: A decision, then either an erasure-set change or an observation-evidence relocation, with a migration test
- **cost of resolving**: One small wave plus a migration
- **risk of not resolving**: An epistemic record that survives its own evidence, and a destructive operation whose scope is undefined
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-025
- **status**: CLOSED

### Q-W46-04 — Does the writer guard admit two writers when the database is reached through two different paths, one of them a symlink?

- **type**: engineering-uncertainty
- **why it matters**: The W46 target contract names path aliases and symlinks explicitly. The guard normalises the path but does not resolve it.
- **what is known**: WriterLock.java:145 and :159 use databaseFile.toAbsolutePath().normalize(). normalize() collapses . and .. but does not resolve symbolic links. Two aliases therefore yield two lock files.
- **evidence**: WriterLock.java:145; WriterLock.java:159; the D4 target contract in the W46 directive
- **competing explanations**: (a) the deployment always uses one canonical path and the risk is theoretical; (b) a symlinked data directory is a normal deployment and the risk is real
- **available experiments**: A test that opens the same database through a symlink and through its target and asserts that the second writer is refused
- **cost of resolving**: One small defect fix plus a test
- **risk of not resolving**: Two writers on one database, which is exactly the condition the guard exists to prevent
- **owner decision required**: no — the contract is already approved; this is a defect inside it
- **dependencies**: none
- **status**: OPEN

### Q-W46-05 — How is the canonical knowledge database backed up and restored as a whole?

- **type**: missing-implementation
- **why it matters**: The W46 acceptance criteria require the backup and recovery procedures to be discoverable. The runbook has no backup procedure at all.
- **what is known**: There is no file-level backup: the SQLite backup API (BackupManager, VACUUM INTO) appears nowhere in the production sources. The only recovery mechanisms are the per-workspace logical snapshot and the per-request idempotency recovery.
- **evidence**: grep for backup/ / BackupManager / VACUUM INTO over matrix-*/src/main returns nothing in the knowledge or cognitive store; docs-v2/operations/RUNBOOK.md contains no occurrence of backup, restore or snapshot
- **competing explanations**: (a) the snapshot is sufficient for the realistic failure modes; (b) a file-level backup is needed for corruption and for a bad migration; (c) both are needed
- **available experiments**: A backup command that uses SQLite's own mechanism, a restore drill into a temporary copy, and a runbook section
- **cost of resolving**: One wave: a script, a drill, a runbook section
- **risk of not resolving**: A corruption or a bad migration that cannot be undone
- **owner decision required**: no — a runbook procedure is inside the agent's remit
- **dependencies**: none
- **status**: OPEN

### Q-W46-06 — What is the actual total test population, given that the full matrix-core suite is never run here?

- **type**: engineering-uncertainty
- **why it matters**: Every test claim in the documentation is a claim about a scope, and a reader needs to know what was not run.
- **what is known**: W44 recorded 9816 executions with 24 failures across nine GraalVM-dependent classes. W45.2 ran four targeted scopes totalling 3100 executions. The native-image classes cannot run at all: the GraalVM tooling is absent and matrix-core/build/native has never existed on this host.
- **evidence**: W44-CLAIM-EVIDENCE.csv; W45.2-TEST-REPORT.md section 1; the absence of the GraalVM tooling
- **competing explanations**: (a) the full suite is feasible on this machine and only the convention forbids it; (b) it is feasible but slow and OOM-prone, which is why the convention exists; (c) it is not feasible and the per-scope baselines are the right model
- **available experiments**: A resource estimate, then the owner's approval, then one run on a quiet machine with the native-image classes excluded
- **cost of resolving**: A resource estimate and one run
- **risk of not resolving**: Test claims that cannot be checked against a total
- **owner decision required**: YES — a full-suite run needs the owner's approval per the directive
- **dependencies**: the resource budget of W46.3
- **status**: OPEN

### Q-W46-07 — Does the GPU actually accelerate anything in MATRIX, and which workload?

- **type**: missing-measurement
- **why it matters**: The directive requires the historical GPU claims to be resolved rather than generalised. A configured provider is not proof of execution.
- **what is known**: The historical EXP-009 results are retained. The hardware probe and the ONNX provider discovery are being re-established in W46.0 (investigation T4). The models must be present on disk for an inference to run at all. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-028 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: the W46.0 hardware report, once T4 reports
- **competing explanations**: (a) the GPU is used by ONNX Runtime and is measurably faster for the model MATRIX ships; (b) it is available but the models are absent, so nothing runs; (c) it is used only by an external experiment
- **available experiments**: Load the shipped model with ONNX Runtime, print the actual providers, and compare an equivalent workload on the CPU and the GPU
- **cost of resolving**: One session of measurement, plus the models on disk
- **risk of not resolving**: A GPU claim that no one can reproduce
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: the presence of the models; the implementation: OD-028
- **status**: CLOSED

### Q-W46-08 — What is the real concurrency envelope of the build on this machine?

- **type**: engineering-uncertainty
- **why it matters**: The resource budget must say how many heavy builds may run at once. A previous wave was destabilised by two concurrent Gradle builds in one module.
- **what is known**: The machine has 32 logical CPUs and a large RAM, but the RAM is shared with the co-resident services and the harness. One build's resident cost has not been measured.
- **evidence**: the W46.0 hardware report, once T4 reports
- **competing explanations**: (a) two builds are safe; (b) one build is safe and two race on the build directory; (c) the limit is the disk, not the memory
- **available experiments**: Measure the resident memory and the wall time of one clean compile with the daemon sampled, then try two concurrently
- **cost of resolving**: One measurement session
- **risk of not resolving**: A wave that produces sixteen spurious failures and looks like a regression
- **owner decision required**: no
- **dependencies**: none
- **status**: OPEN

### Q-W46-09 — Which document is the single source of truth for the current state, and what is the fate of SESSION.md and state.yaml?

- **type**: architecture-decision
- **why it matters**: A new agent must find the current state in one place. Today there are at least four candidates of different ages.
- **what is known**: SESSION.md is 7885 lines and last describes the W43 era; state.yaml is dated 2026-08-10 and says status init; docs-v2/engineering/PLAN.md predates W45.2; the W45.2 status document describes one wave, not the project.
- **evidence**: SESSION.md (7885 lines); state.yaml (2026-08-10); docs-v2/engineering/PLAN.md
- **competing explanations**: (a) one canonical current-state document, generated from the repository and the registry; (b) a hand-written document kept in step; (c) both, with the generated one checked by the validator
- **available experiments**: Build the document, then the validator that checks its commit reference against HEAD
- **cost of resolving**: One wave
- **risk of not resolving**: Every new session re-derives the state, which is the cost this whole wave exists to remove
- **owner decision required**: no — the structure is proposed in W46.1 for the owner to approve
- **dependencies**: the document registry
- **status**: OPEN

### Q-W46-10 — Is the capability registry one machine-readable file with a multi-axis status model, and what happens to the two existing registries?

- **type**: architecture-decision
- **why it matters**: The directive forbids a single green tick and requires the implementation, integration, verification and operational statuses to be separable.
- **what is known**: Two registries exist: docs-v2/architecture/CAPABILITY-REGISTRY.md and docs-v2/engineering/W44-CAPABILITY-REGISTRY.md. Both predate W45.0, W45.1 and W45.2 and therefore describe a system that no longer exists.
- **evidence**: the two files; W46-CAPABILITY-RECONCILIATION.csv for the per-capability findings
- **competing explanations**: (a) one YAML registry with a validator, the other two marked superseded with pointers; (b) evolve the existing Markdown registry in place; (c) keep both and reconcile them by hand
- **available experiments**: Build the registry from the reconciliation table, then the validator
- **cost of resolving**: One wave plus the validator
- **risk of not resolving**: A capability picture that the code contradicts — the defect this wave exists to remove
- **owner decision required**: no — but the W46.1 structure is presented for approval
- **dependencies**: the capability reconciliation
- **status**: OPEN

### Q-W46-11 — Is there a link checker, and what does it find in the whole document graph?

- **type**: missing-implementation
- **why it matters**: Three current documents already point at a WAL.md that does not exist. The number of broken references is unknown.
- **what is known**: The W46.0 audit (investigation T3) counts the broken local links, the broken anchors and the duplicate identifiers across the tree.
- **evidence**: the W46.0 document audit
- **competing explanations**: (a) a small number of defects, quickly repaired; (b) a large number, which changes the size of the consolidation
- **available experiments**: Run the checker, then decide the repair wave's scope
- **cost of resolving**: One investigation, already in flight
- **risk of not resolving**: Broken references that mislead every reader, and no tool to catch the next one
- **owner decision required**: no
- **dependencies**: none
- **status**: OPEN

### Q-W46-12 — Is the six-cycle truth (two serving paths inside chain A) reflected everywhere it needs to be?

- **type**: engineering-uncertainty
- **why it matters**: The W44 cycle map names five. The code has six. Any document that lists the cycles and is read by a new agent is now wrong in the same way the ADR was.
- **what is known**: ADR-W45.2-01 and the io.matrix.api.CanonicalEntryPoint declaration name both chain A serving paths. The historical W44 documents still say five, which is correct for the moment they were written and must not be rewritten.
- **evidence**: ADR-W45.2-01-canonical-entry-point.md section 2.1; W44-COGNITIVE-CYCLE-MAP.md
- **competing explanations**: (a) the correction lives only in the superseding document, which is enough; (b) the current navigation must point at the superseding document so the old one is not read as current
- **available experiments**: The document registry marks the W44 map HISTORICAL and names its successor; the index points at the successor
- **cost of resolving**: Small
- **risk of not resolving**: The same error the W44 ADR made, reappearing in a reader who starts from the old map
- **owner decision required**: no
- **dependencies**: the document registry
- **status**: OPEN

### Q-W46-13 — What is the retention policy for the append-only observation, adjudication and request-event histories?

- **type**: product-decision
- **why it matters**: They grow without bound by design. A long-running deployment needs a policy, and the policy must not be a silent deletion of evidence.
- **what is known**: Nothing prunes them today. The design intent was append-only durability, and the W45.0/W45.1 waves made that explicit. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-029 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: the table definitions in CognitiveStore and KnowledgeStore; R-W46-18
- **competing explanations**: (a) unbounded is right for an audit trail and the volume is negligible; (b) a policy is needed before a long deployment
- **available experiments**: A measurement of the growth per request, then a decision
- **cost of resolving**: A measurement
- **risk of not resolving**: An audit trail that eventually exhausts the disk, or a silent deletion of evidence
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-029
- **status**: CLOSED

### Q-W46-14 — Does the project still need the gateway's JAX-RS facade and the stub cycle it mounts?

- **type**: engineering-uncertainty
- **why it matters**: They are unmounted, but they exist and someone could mount them. The isolation gate censuses them so that a mount would be visible.
- **what is known**: The facade constructs StubBrainCycle; ADR-2026-10-26-002 records it as unmounted; the W45.2 gate prints the nine annotated chain B routes.
- **evidence**: ADR-2026-10-26-002; the gate's route census
- **competing explanations**: (a) keep it, censored, as a placeholder for a future gateway; (b) remove it; (c) leave it and stop thinking about it
- **available experiments**: A decision, then either a removal or a permanent census rule
- **cost of resolving**: Small
- **risk of not resolving**: Nothing — the gate already makes the risk visible
- **owner decision required**: no — but a removal would be a Level 2 change
- **dependencies**: none
- **status**: OPEN

### Q-W46-15 — How does a session resume after a context loss, without repeating the archaeology?

- **type**: process
- **why it matters**: This is the objective of the wave: MATRIX must stop depending on the memory of the previous agent.
- **what is known**: The W46.3 deliverables are the agent operating protocol, the session-handoff template, the document registry and the validators. The W45.2 wave already produced one example of a complete handoff.
- **evidence**: the W46.3 plan; the W45.2-STATUS.md as the worked example
- **competing explanations**: (a) a protocol plus a bootstrap block; (b) a protocol plus the validators that check it; (c) both
- **available experiments**: Build the protocol, then the bootstrap block in the final report, then the validators
- **cost of resolving**: One wave
- **risk of not resolving**: Every session repeats the work of the last one — the cost the wave exists to remove
- **owner decision required**: no
- **dependencies**: the document registry and the capability registry
- **status**: OPEN

### Q-W46-16 — The fifteenth TLA+ specification (docs-v2/tla/FederationConsensus.tla) is neither gated by the formal gate nor registered in the specification map: should it move into formal/ and be gated, or be withdrawn?

- **type**: architecture-decision
- **why it matters**: The capability registry's C-01c claim is '9 TLA+ specs, model-checked in CI'. The truth is 14 model-checked of 15 on disk, and the fifteenth is an orphan. A specification that is not gated is not verified, and a claim that counts it is wrong.
- **what is known**: The formal gate checks 14 specifications; docs-v2/tla/FederationConsensus.tla is outside formal/ and outside formal/spec-implementation-map.yml, which explicitly says it is not formal/Consensus.tla. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-030 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: docs-v2/architecture/CAPABILITY-REGISTRY.yml (the C-01c row); ls formal/*.tla = 14; ls docs-v2/tla/ = 1
- **competing explanations**: (a) the file is a draft that was never adopted and should be withdrawn; (b) it is a real specification that was filed in the wrong directory; (c) it duplicates formal/Consensus.tla and should be deleted
- **available experiments**: Move the file into formal/ and add it to the spec map and to the gate, or withdraw it with a note. A move changes the gate's scope; a withdrawal changes the claim.
- **cost of resolving**: one wave: a move plus a map entry, or a withdrawal note
- **risk of not resolving**: the published formal-verification count stays wrong, and an ungated specification reads as verified
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-030
- **status**: CLOSED

### Q-W46-17 — docs-v2/quality/OPEN-DECISIONS.md contradicts itself: the top section declares D1-D8 closed while the bodies of those same entries still read as open. Which is the truth?

- **type**: engineering-uncertainty
- **why it matters**: The decision register followed the top section, so eight rows in the canonical register inherit an assumption that the source document does not support.
- **what is known**: The top section was written later and the entries below were not updated. The register's rows cite the top section as the evidence.
- **evidence**: docs-v2/quality/OPEN-DECISIONS.md (the 'CLOSED decisions' section against the entries below it); docs-v2/governance/DECISION-REGISTER.md (the rows that depend on it)
- **competing explanations**: (a) the top section is right and the bodies are stale prose; (b) the bodies are right and the top section was written optimistically; (c) some of the eight were closed and some were not
- **available experiments**: Compare each entry against the wave that claims to have closed it and against the code. The repair is an annotation of the source document, not a rewrite.
- **cost of resolving**: one small wave: a per-entry check and an annotation
- **risk of not resolving**: eight register rows rest on an unverified assumption
- **owner decision required**: no - the repair is an annotation, and the file is not protected
- **dependencies**: none
- **status**: OPEN

### Q-W46-18 — Two proposals have hung for months with no decision: PR-002 (the weekly CI smoke) and PR-006 (the WAL split). Should they be given a deadline, or rejected?

- **type**: product-decision
- **why it matters**: A proposal that is neither accepted nor rejected is a permanent open question, and the decision register grows a row every wave that re-lists it.
- **what is known**: PR-006's first two phases are done (SPEC-014 exists, scripts/extract-waves.py exists) and the third is not (there is exactly one wave-*.md in docs-v2/waves/). PR-002's weekly smoke is referenced by a CI job that the audit could not confirm green. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-032 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: docs-v2/governance/DECISION-REGISTER.md (the PR-002 and PR-006 rows); docs-v2/proposals/RFC-weekly-ci-smoke.md; docs-v2/proposals/PROPOSAL-wal-split-wave-based-architecture.md
- **competing explanations**: (a) they should be adopted; (b) they should be rejected and the documents archived; (c) they should be given a deadline and re-examined at the next wave
- **available experiments**: A decision; or a deadline recorded in the register with the wave at which it is re-examined.
- **cost of resolving**: one owner decision
- **risk of not resolving**: the register accumulates rows that no one will ever resolve, and a reader cannot tell an open question from an abandoned one
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-032
- **status**: CLOSED

### Q-W46-19 — The hypothesis register references eleven identifiers that it never declares. Should they be declared, or should the references be removed?

- **type**: engineering-uncertainty <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **why it matters**: Eleven hypotheses are cited by designs, specifications and experiment reports as if they existed, and no register declares them. A reader cannot tell whether the hypothesis was lost, renamed, or never written. The eleven are: H-012, H-014, H-016, H-017, H-018, H-021, H-023, H-024, H-036, H-097, H-098. <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **what is known**: The W46.2 link checker found the references: they are undefined identifiers, and each one is now marked with an explicit no-id exemption rather than silently repaired. The two registers are docs-v2/research/HYPOTHESES.md and docs-v2/research/HYPOTHESES-NEW.md. <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **evidence**: docs-v2/governance/OPEN-QUESTIONS.md (this record); the markers <!-- no-id: ... --> in the eleven citing documents <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **competing explanations**: (a) the hypotheses exist in a register that was never committed; (b) they were renamed when the register was renumbered; (c) they were planned and never written <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **available experiments**: Search the git history for the identifiers; if they are absent everywhere, decide whether to declare them or to strike the references. Declaring a hypothesis that was never tested would be fabricating evidence, so the default is to strike the reference. <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **cost of resolving**: one wave: a history search and then a decision per identifier <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **risk of not resolving**: eleven dangling references stay in the normative documents, and a reader who trusts a citation cites a hypothesis that does not exist <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **owner decision required**: no - unless the owner wants the hypotheses reconstructed <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **dependencies**: none <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->
- **status**: OPEN <!-- no-id: the record names the identifiers it is about; Q-W46-19 -->

### Q-W46-20 — The DESIGN and SPEC series were renumbered, and eleven normative references still carry the pre-renumbering names (for example SPEC-008-autonomy-impulses, SPEC-009-decentralized-digests, DESIGN-07-consolidation-cycle, DESIGN-08-hierarchical-memory). Should the references be rewritten to the current names, or should the missing subjects be written?

- **type**: engineering-uncertainty
- **why it matters**: The series was renumbered without rewriting the cross-references. Some references were repaired in W46.2 by matching the subject; where no document carries the subject today, the reference is marked as unresolved rather than pointed at a document about something else.
- **what is known**: W46.2 repaired 20 references by subject (the pipeline, the lifecycle, the memory hierarchy, the HADES reservoir, the BIR migration). The references that remain have no counterpart: no specification of 'autonomy impulses' and none of 'decentralized digests' exists under any number. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-031 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: docs-v2/engineering/W46.2-STATUS.md (the repair table); the no-link and no-id markers in the citing documents; docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md
- **competing explanations**: (a) the subjects were never written as specifications and belong in the design series; (b) they were merged into other specifications; (c) they were dropped on purpose
- **available experiments**: Decide per subject whether it is a specification, a design, or nothing. The series is NOT renumbered - the directive forbids it, and a renumbering would break every historical reference.
- **cost of resolving**: one wave: a decision per subject
- **risk of not resolving**: the normative layer keeps pointing at documents that do not exist, and a reader cannot tell whether the subject was dropped or merely moved
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-031
- **status**: CLOSED

### Q-W46-21 — The migration from the v1 tree to docs-v2 removed the tree, and 41 references to it survive in the historical and the current documents (docs/spec/, docs/research/, docs/GLOSSARY.md, data/smoke-old, the archive tarball). Should they be pointed at the v2 equivalents, or marked as the removed tree?

- **type**: engineering-uncertainty
- **why it matters**: The v1 tree was a real tree with real content; the references are not typos but pointers to a tree that no longer exists. A reader following them lands nowhere.
- **what is known**: W46.2 marked each of them with an explicit no-link marker naming the reason, rather than guessing a v2 equivalent that may not exist. The 41 references are spread over INSTALL.md, SESSION.md, MIGRATION.md, CONTRIBUTING.md, scripts/README.md and the paradigm documents.
- **evidence**: the no-link markers in the documents; docs-v2/engineering/W46-DOCUMENT-AUDIT.csv (the document registry)
- **competing explanations**: (a) the documents are historical and their references are history; (b) the documents are current and their references must point at the v2 tree
- **available experiments**: Decide per document whether it is current. The documents that are current get their references rewritten; the historical ones keep the markers.
- **cost of resolving**: one wave: a classification per document
- **risk of not resolving**: a reader of a current document follows a link into a tree that was deleted two years ago
- **owner decision required**: no - the classification is mechanical once the owner says which documents are current
- **dependencies**: none
- **status**: OPEN

### Q-W46-22 — The harness reads its skills from `.agents/skills/`, `.dsh/skills/` and the user directories — NOT from `.opencode/skills/`. The repository's 27 packages there are invisible to a session (25 arrive through byte-identical mirrors, `coprocessor` through a mirror with 13 extra lines, `matt-pocock` not at all). Should the four recommended MATRIX skills be registered in the harness's trigger table (`src/lib/53-auto-skills.sh` and its mirror), or is the directory enough?

- **type**: operational-blocker
- **why it matters**: The harness's skill roots are read at session start: `<project>/.dsh/skills`, `<project>/.agents/skills`, the custom roots, `~/.dsh/skills`, `~/.agents/skills` and the bundled skills. `.opencode/skills/` is not among them. A MATRIX skill written there would load never.
- **what is known**: The census is in docs-v2/governance/SKILL-INVENTORY.md §4 and the recommendation is in docs-v2/governance/SKILLS.md §2. The four recommended skills are to live in `.agents/skills/` — the default project root, and a location that touches no owner-owned file. The trigger table is the harness's own source, outside this repository, and its mirror is written into the user's home directory. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-033 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: docs-v2/governance/SKILL-INVENTORY.md §4; docs-v2/governance/SKILLS.md §2 and §8
- **competing explanations**: (a) the trigger table is an optimisation and the directory is enough; (b) the trigger table is what makes a skill discoverable and it must be extended; (c) the repository's `.opencode/skills/` should be migrated to a directory the harness reads
- **available experiments**: Install the four packages under `.agents/skills/` and measure whether a session finds them. If not, the trigger table needs four slugs — a change to the harness, and therefore the owner's call.
- **cost of resolving**: one wave: four directories, plus possibly a harness edit
- **risk of not resolving**: the recommended skills exist and are never loaded, which is worse than not having written them
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-033
- **status**: CLOSED

### Q-W46-23 — Four skill packages declare a name that does not match their directory (`seatch-first`→`search-first`, `scientific-thinking-scholar-evaluation`→`scholar-evaluation`, `plan.retired-backup`→`plan`, and `matt-pocock` without a root SKILL.md). Should they be corrected?

- **type**: operational-blocker
- **why it matters**: A skill whose declared name differs from its directory is loaded under a name that does not correspond to its path, and `plan.retired-backup` declares the name `plan` — the name of a live skill. The packages are the owner's library and the plugin bundle, and the directive protects them.
- **what is known**: The census is in docs-v2/governance/SKILL-INVENTORY.md §1 (the vocabulary) and §3 (the groups); the conflicts are in §9. **CLOSED on 2026-11-05:** the owner answered the W46 questionnaire — the decision is OD-034 in docs-v2/governance/DECISION-REGISTER.md. The question is not deleted: it stays, with the answer, because a closed question is the proof that the project once did not know.
- **evidence**: docs-v2/governance/SKILL-INVENTORY.md; the four packages under `.veai/skills/` and `.opencode/skills/`
- **competing explanations**: (a) the names are deliberate and the directories are the mistake; (b) the directories are deliberate and the names are the mistake; (c) the packages are obsolete and should be retired
- **available experiments**: A rename touches the owner's library. The minimum: the validator of the skill package reports the mismatch, and the owner decides the direction.
- **cost of resolving**: one small wave, but only with the owner's authorisation
- **risk of not resolving**: a skill loads under a name that points at another skill, and an agent that trusts the name acts on the wrong instructions
- **owner decision required**: no — the owner decided it on 2026-11-05
- **dependencies**: none; the implementation: OD-034
- **status**: CLOSED

---

## The ten that were closed on 2026-11-05

**The owner's questionnaire of 2026-11-05 settled ten questions.** They stay in the register
with their answer, because a closed question is the proof that the project once did not know:

| the id | the decision | the question |
|---|---|---|
| Q-W46-01 | **OD-026** | Should the workspace replacement flag stay the query parameter ?replace=true, or become the body field replaceExisting: true named by the W46 target contract? |
| Q-W46-02 | **OD-027** | Should the workspace reload get a dry-run or preview mode that names what would be replaced? |
| Q-W46-03 | **OD-025** | What exactly may a workspace replacement erase? Today it erases the evidence of an observation while keeping the case, the disposition and the adjudication history. |
| Q-W46-07 | **OD-028** | Does the GPU actually accelerate anything in MATRIX, and which workload? |
| Q-W46-13 | **OD-029** | What is the retention policy for the append-only observation, adjudication and request-event histories? |
| Q-W46-16 | **OD-030** | The fifteenth TLA+ specification (docs-v2/tla/FederationConsensus.tla) is neither gated by the formal gate nor registered in the specification map: should it move into formal/ and be gated, or be withdrawn? |
| Q-W46-18 | **OD-032** | Two proposals have hung for months with no decision: PR-002 (the weekly CI smoke) and PR-006 (the WAL split). Should they be given a deadline, or rejected? |
| Q-W46-20 | **OD-031** | The DESIGN and SPEC series were renumbered, and eleven normative references still carry the pre-renumbering names (for example SPEC-008-autonomy-impulses, SPEC-009-decentralized-digests, DESIGN-07-consolidation-cycle, DESIGN-08-hierarchical-memory). Should the references be rewritten to the current names, or should the missing subjects be written? |
| Q-W46-22 | **OD-033** | The harness reads its skills from `.agents/skills/`, `.dsh/skills/` and the user directories — NOT from `.opencode/skills/`. The repository's 27 packages there are invisible to a session (25 arrive through byte-identical mirrors, `coprocessor` through a mirror with 13 extra lines, `matt-pocock` not at all). Should the four recommended MATRIX skills be registered in the harness's trigger table (`src/lib/53-auto-skills.sh` and its mirror), or is the directory enough? |
| Q-W46-23 | **OD-034** | Four skill packages declare a name that does not match their directory (`seatch-first`→`search-first`, `scientific-thinking-scholar-evaluation`→`scholar-evaluation`, `plan.retired-backup`→`plan`, and `matt-pocock` without a root SKILL.md). Should they be corrected? |

**And what that means for the work:** the decisions are recorded, and the behaviour they
describe is **not** implemented — the owner's instruction for this wave was to fix the
decisions and to leave the code to W47. Every closed question names the task that implements it.

> **The end-of-wave questionnaire of 2026-11-05 was ASKED AND ANSWERED** — six questions
> through the harness's `ask_user_question` tool, the questions in
> `docs-v2/engineering/W47-QUESTIONNAIRE.md`, the answers in OD-039…OD-044, the tasks in
> `docs-v2/governance/ROADMAP.md` §4.6. One of the six answers went AGAINST the wave's
> recommendation — the owner chose to give the `matt-pocock` container a root manifest, where
> the wave had recommended leaving it alone — and that choice is the owner's to make.
>
> **W47 (2026-11-05) — the decisions of this wave are now IMPLEMENTED.** The owner's
> instruction was carried out: TASK-W46-16, TASK-W46-17, TASK-W46-18, TASK-W46-20,
> TASK-W46-21 and TASK-W46-22 are done, and the three tasks the questionnaire created
> (TASK-W46-23, TASK-W46-24, TASK-W46-25) are done as well. The reports are in
> `docs-v2/engineering/W47-STATUS.md`.
>
> **Q-W46-23 (OD-034) is closed on TWO of its four positions, and the other two are a
> boundary, not an oversight.** The two `.veai` directories were renamed (`git mv`, the
> history follows): `seatch-first` → `search-first` and
> `scientific-thinking-scholar-evaluation` → `scholar-evaluation` — in both cases the
> DECLARED name was right and the directory was wrong. The other two were refused on
> purpose: `matt-pocock` is an owner-owned container in `.opencode/**` with seventeen
> sub-skills and no root `SKILL.md`, and giving it one would change what the container
> means; `plan.retired-backup` is a retired, untracked asset of the opencode-swarm plugin
> that the plugin reinstalls by itself, and its declared name `plan` collides with a live
> `plan` — fixing it would mean reviving a retired bundle or renaming another bundle, and
> its name carries a dot, which the harness's own name rule forbids
> (`^[a-z0-9]+(?:-[a-z0-9]+)*$`). Both refusals are recorded in
> `docs-v2/governance/DECISION-REGISTER.md` OD-034 with the reason.

### Q-W49-01 — The default branch is 357 commits behind `develop`, and the public site is built from it

- **type**: architecture-decision
- **why it matters**: A visitor to the repository sees the W37.5 state of the project, and the deployed site carries the withdrawn positioning — because both are published from `main`, which is `7a0ed0cc` of 2026-10-12. The wave rewrote the site and the README on `develop`; nothing a visitor sees changes until the publication path is decided.
- **what is known**: the gap is **357 commits** (`git rev-list --count main..develop`); the deployed URL and its build branch were read from `gh api repos/AlexanderNarbaev/agi/pages`; the W49 wave added a pull-request build that validates the site **without publishing**, and the publication stays on the reviewed `main` path.
- **evidence**: `docs-v2/engineering/W49-BASELINE.md` §1 and §3.1; `.github/workflows/pages.yml`; `scripts/validate-pages-config.sh`
- **competing explanations**: (a) `main` is moved to the current work through a verified pull request; (b) the site publishes from `develop` and the default branch stays a release branch; (c) the gap is accepted and the README carries the pointer to `develop` that it carries now
- **available experiments**: none needed — the three options are configurations, and the second and the third are already implemented
- **cost of resolving**: one pull request and one deployment, or one workflow change
- **risk of not resolving**: the project's public face describes a project that no longer exists in the repository
- **owner decision required**: **yes** — the directive forbids changing the default branch without a separate decision
- **dependencies**: none
- **status**: OPEN

### Q-W49-02 — Should the recommended repository metadata be applied, and should a private security channel exist?

- **type**: product-decision
- **why it matters**: the repository has no description, no topics and no homepage; and it has **no private security-reporting channel** — `private_vulnerability_reporting` is not enabled, so the security template had nothing real to point at.
- **what is known**: a description and twelve topic tags are **recommended** in `docs-v2/engineering/W49-BASELINE.md` §7; the security template now states plainly that no private channel exists and asks the reader to request one rather than pointing at a file that does not exist; Discussions stays disabled, because no moderation or reporting arrangement exists.
- **evidence**: `gh api repos/AlexanderNarbaev/agi` → `description: null`, `topics: []`, `has_discussions: false`, `private_vulnerability_reporting: null`; `.github/ISSUE_TEMPLATE/security.md`
- **competing explanations**: (a) apply the description and the topics, and enable GitHub's private vulnerability reporting; (b) apply the description only, and leave the security channel as it is — a public issue tracker with no private route; (c) apply nothing and leave the repository as it is
- **available experiments**: none — these are settings
- **cost of resolving**: minutes
- **risk of not resolving**: a repository that is hard to find and a security contact that does not exist
- **owner decision required**: **yes**
- **dependencies**: none
- **status**: OPEN

### Q-W49-03 — Who are the authors, in what order, and is a formal release wanted?

- **type**: architecture-decision
- **why it matters**: `CITATION.cff` is the machine-readable citation, and the file would have to carry the author order, a DOI, a publisher, a release version and a release date. **None of those exists**: `gh release list` is empty, the tags stop at `v18.0.0-mind` (`7a0ed0cc`, 2026-10-12, an ancestor of `develop`), and there is no author list.
- **what is known**: the licence is AGPL-3.0 and the publication pipeline records that **until a release exists, the citeable unit is a versioned commit**.
- **evidence**: `gh release list` → empty; `docs-v2/research/PUBLICATION-PIPELINE.md` §5; `docs-v2/engineering/W49-BASELINE.md` §3.3
- **competing explanations**: (a) a formal release is cut and `CITATION.cff` is added with it; (b) no release is cut and the citation is the commit hash, as the pipeline says; (c) the author order is supplied and the rest waits for a release
- **available experiments**: none
- **cost of resolving**: a release procedure, or one sentence in the README
- **risk of not resolving**: the work cannot be cited in the way the field expects
- **owner decision required**: **yes** — the author order and the DOI are the owner's, and they must not be invented
- **dependencies**: none
- **status**: OPEN

### Q-W49-04 — Which publication candidate goes first: B or C?

- **type**: product-decision
- **why it matters**: two candidates are ready to be written up, and they need different things: **B** (evidence-preserving state and recovery) needs almost nothing external, while **C** (workload-dependent CPU/CUDA acceleration) needs a second GPU to be reproduced on.
- **what is known**: the verdicts and the missing evidence for each are in `docs-v2/research/PUBLICATION-CANDIDATES.md`: **B → PROCEED**, **C → PROCEED**, **A → REVISE**. The broad-AGI paper is not proposed, on the repository's own evidence.
- **evidence**: `docs-v2/research/PUBLICATION-CANDIDATES.md`; the reproducible opportunities in `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md`
- **competing explanations**: (a) B first, because it needs no external resource; (b) C first, because the negative half — the single-point call is four times SLOWER on the GPU — is the more transferable result; (c) neither yet, and the collaboration brief's six requests come first
- **available experiments**: both are already measured; what is missing is the writing and the review
- **cost of resolving**: the order of two writing tasks
- **risk of not resolving**: both sit half-written
- **owner decision required**: **yes**
- **dependencies**: Q-W49-03 for the citation metadata
- **status**: OPEN

### Q-W49-05 — Is a larger runner acceptable for the native build, and should MATRIX carry BouncyCastle?

- **type**: operational-blocker
- **why it matters**: the native-image build cannot succeed on the runner the workflow selects. The measurement says the heap must exceed 8 GB and the runner must have more than the standard 16 GB; and with the memory resolved the build stops on PDFBox's **optional** BouncyCastle dependency, which the application does not declare.
- **what is known**: the heap and the runner figures are measured (`docs-v2/engineering/W49-NATIVE-BUDGET.md`); the build is feasible on a 59 GiB host with GraalVM installed outside the repository; the fix is one line — `bcpkix-jdk18on` at the version PDFBox 3.0.3's parent declares.
- **evidence**: `docs-v2/engineering/W49-NATIVE-BUDGET.md` §2 and §4; the CI run `41219224376` log
- **competing explanations**: (a) a larger runner and the dependency — the native path becomes real; (b) a larger runner only — the build then fails on the dependency; (c) neither — the capability stays BLOCKED and the measurement is the deliverable
- **available experiments**: both are one configuration change and one dependency line
- **cost of resolving**: a larger runner is a **cost decision**; the dependency is a runtime dependency the project either carries or does not
- **risk of not resolving**: the native capability stays BLOCKED, and the 25 native-binary test failures stay "neither passing nor failing"
- **owner decision required**: **yes** — the runner is a cost, and the dependency changes the application's footprint
- **dependencies**: none
- **status**: OPEN

### Q-W49-06 — What is the policy for the 1 414 findings the Java 25-capable SpotBugs engine reports?

- **type**: architecture-decision
- **why it matters**: the static-analysis gate produces **no evidence** today, because the pinned engine (4.9.3, ASM 9.7.1) cannot read Java 25 bytecode. The engine that can — 4.10.5, ASM 9.11 — was measured locally and reports **1 414 findings**, 981 of them `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` ("may expose an internal representation by returning a mutable object").
- **what is known**: the W48 wave replaced the step's `errexit` bug, so the known limitation is now reported as a loud warning and the two real checks after it (the secret scan and the vulnerable-dependency check) actually run; the pin was **not** changed, because turning the engine on is a decision about 1 414 findings, not a version bump.
- **evidence**: `docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.9.2; the local run with the 4.10.5 engine
- **competing explanations**: (a) upgrade the engine and triage the findings by category; (b) upgrade the engine and exclude the low-signal categories with a recorded exclusion list; (c) keep the pin and accept that no static-analysis evidence is produced
- **available experiments**: the engine has already been run once; the triage is the work
- **cost of resolving**: one wave of triage, and possibly a large refactor under option (a)
- **risk of not resolving**: the repository has no static-analysis evidence at all, and a gate that silently produces nothing is the failure mode the W48 wave fixed elsewhere
- **owner decision required**: **yes**
- **dependencies**: none
- **status**: OPEN

---

## The types, and what each one needs

| the type | what it needs to close | who closes it |
|---|---|---|
| `scientific hypothesis` | an experiment with a stated falsification condition and a sample-size rationale | the research wave that owns it |
| `engineering uncertainty` | a measurement, or a test that fails | the coordinator |
| `architecture decision` | an owner's decision, recorded in the decision register | **the owner** |
| `product decision` | an owner's decision | **the owner** |
| `operational blocker` | a change to the environment or the configuration | the coordinator, or the owner for the deployment |
| `missing measurement` | the measurement itself | the coordinator |
| `missing implementation` | the implementation and its verification | the coordinator |

## The rule of closure

A question is closed when **the evidence that settles it exists and is named**. The record
stays, with the closing evidence — a closed question is the proof that the project once did
not know.
