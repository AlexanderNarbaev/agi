# DECISION REGISTER — MATRIX

**Status:** CANONICAL · 2026-11-07 · branch `develop` · HEAD `71b5b5c6`
**Kind:** an index. Nothing in this file is a decision; every row points at the document that
holds the decision, and the full text, the rejected alternatives and the evidence live there.

This register answers one question — *what has been decided, by whom, when, and what replaced
it* — and it answers it by routing a reader, not by restating decisions. Where a row gives a
one-line summary, that summary is a pointer, not a substitute: the document named in the
"document that holds it" column is the authority.

It is **not** a capability registry (those claims live in `docs-v2/architecture/CAPABILITY-REGISTRY.md`
and `docs-v2/engineering/W44-CAPABILITY-REGISTRY.md`), **not** a research log (the hypothesis
registers and the experiment reports live in `docs-v2/research/`), **not** a roadmap (that is
`docs-v2/governance/ROADMAP.md`), and **not** a list of open questions (that is
`docs-v2/governance/OPEN-QUESTIONS.md`).

The registry was built by reading every decision-bearing document in the repository: the
eight ADRs, the two owner-decision documents of the W45/W45.2 waves, the two wave decision
logs (`docs-v2/engineering/W45-DECISION-LOG.md` and
`docs-v2/engineering/RECON-W42-DECISIONS.md`), the normative concept-corrections
log, the operator's decision ledger, the RFCs and the proposals. A decision that is written
down but not ruled on is recorded as `PROPOSED`; a document that does not say whether it is a
decision is recorded as `UNKNOWN`, with the reason.

**Three id prefixes are this register's own**, because the repository's bare `D` is
ambiguous (see §4): `OD-` for an owner decision, `CD-` for a coordinator decision, `PR-` for
a proposal. The normative decisions of `docs-v2/vision/CONCEPT-CORRECTIONS.md` get `ND-`,
a prefix the four given prefixes have no home for: they are decisions, but no document names
a decider for them.

---

## 1. The summary table

Sorted: owner decisions, then the ADRs, then the normative decisions, then the coordinator
decisions, then the proposals. Within a kind the newest wave comes first.

| id | the decision (one line) | the kind | the date | the status | the successor | the document that holds it | the effect on the runtime | the evidence or the scope of the approval |
|---|---|---|---|---|---|---|---|---|
| [OD-001](#od-001--the-canonical-entry-point-per-serving-application)| Name the canonical entry point of each serving application; keep the five cycles, merge nothing | owner | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 1); the contract in `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` | a contract | the owner's second answer to the questionnaire of 2026-11-04; every option in the questionnaire is recorded, and the first answer is OD-005 
| [OD-002](#od-002--the-two-chains-stay-isolated-and-fail-closed)| Keep the two serving chains isolated and fail closed; no bridge, no implicit write path, no new network surface | owner | 2026-11-04 | ACCEPTED | **CD-020 (the "no bridge" clause only, 2026-11-08)** | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 2); the contract and its limits in `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` | a boundary | the owner's second answer to the questionnaire of 2026-11-04; the first answer (a write bridge) is OD-005 
| [OD-003](#od-003--the-destructive-workspace-reload-fails-closed-and-leaves-a-snapshot)| The destructive workspace reload fails closed on a non-empty workspace and leaves a recoverable snapshot | owner | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 3) | a contract | the owner's second answer to the questionnaire of 2026-11-04; the first answer (versioning) is OD-005; this answers OD-014 
| [OD-004](#od-004--one-writer-per-database)| One writer process per database file, enforced and documented | owner | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 4) | a boundary | the owner's second answer to the questionnaire of 2026-11-04; the same answer to the fourth question 
| OD-005 | The first answer set: `TrueMindCycle` as a global conductor, an authenticated write bridge, a non-destructive reload by versioning | owner | 2026-11-04 | WITHDRAWN | OD-001, OD-002, OD-003 | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` §0 (the "first answer" column) | none | taken, then re-answered; the document records that the re-answer is the decision and that the first answer is kept because the difference between the two is the difference between a refactor and a boundary |
| OD-006 | D1 — BitNet: provision the weights and run the suites for real (option B) | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D1) | none | operator ruling of the RECON-W32.34 / RECON-W33.1 sweep; the ledger closure is the RECON-W38.0 sweep of 2026-10-18 |
| OD-007 | D2 — six integration tests that exist only on stale branches: recover and port them to the current API (option B) | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D2) | none | operator ruling of the RECON-W32.34 sweep; the document names RECON-W32.0/W38.0 recovery as the vehicle |
| OD-008 | D3 — `gitverse/master`: point it at `develop` (option B) | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D3) | none | operator ruling, dated 2026-10-05 in the document itself |
| OD-009 | D4 — release tagging: tag with an explicit caveats file rather than tag silently (option C) | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D4 — closed in the W33–W34 sweeps) | none | operator ruling; the caveats file the ruling requires is `docs-v2/releases/CAVEATS-v17.6.0-mind.md` (tagged 2026-10-05) |
| OD-010 | D5 — disk cleanup: reclaim the superseded smoke output and the scratch clone | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D5) | none | operator-gated ruling of the RECON-W32.34 sweep; the document records the reclaim and the ledger sequence |
| OD-011 | D6 — the `TrueMindCycle` literal `kill` reflex stays (closed against removal) | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D6) | none | operator ruling of the RECON-W32.34 sweep; the premise was found false — it is a safety refusal, load-bearing for Article I refusals |
| OD-012 | D7 — weekly CI: approve the RFC and implement the minimal scheduled change | owner | 2026-10-17 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D7); the RFC in `docs-v2/proposals/RFC-weekly-research-suite.md` | none | operator approval in the RECON-W38 pre-session; landed in commit `6a15f178` and visible in `.github/workflows/ci.yml` |
| OD-013 | D8 — the research-core failures: accept them as research debt per family, and burn down the rest | owner | 2026-10-05 | ACCEPTED | — | `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D8) | none | operator ruling of the RECON-W32.34 sweep (full attribution); the document records the per-family split and that F4 is a scientific question, not a test bug |
| OD-014 | May the workspace reload erase a contradiction history? | owner | 2026-11-02 | SUPERSEDED | OD-003 | `docs-v2/engineering/RECON-W44-BACKLOG.md` §5, item 1 | a boundary | the question as put to the owner on 2026-11-02; answered by OD-003 on 2026-11-04, which chose "fail closed with a snapshot" |
| OD-015 | May the full `matrix-core` suite be run on this host? | owner | 2026-11-02 | SUPERSEDED | OD-024 | `docs-v2/engineering/RECON-W44-BACKLOG.md` §5, item 2 | none | the question as put to the owner on 2026-11-02; it was answered the same day by OD-024 — the full run is withheld for now. The W44 audit then ran it inadvertently (the filter `io.matrix.*` matched everything) without the owner's approval; the decision was not rescinded, and `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` Q-W46-06 asks for the approval again |
| OD-016 | Is the inert biochemistry island measured or deleted? | owner | 2026-11-02 | UNKNOWN | — | `docs-v2/engineering/RECON-W44-BACKLOG.md` §5, item 3 | none | the question as put to the owner on 2026-11-02; no ruling recorded. The de facto state is that the island stays INACTIVE and unmeasured; `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §4 states nothing was revived |
| OD-017 | Is the `graph` package fixed and wired, or left as a library? | owner | 2026-11-02 | UNKNOWN | — | `docs-v2/engineering/RECON-W44-BACKLOG.md` §5, item 4 | none | the question as put to the owner on 2026-11-02; no ruling recorded. The package still has zero production callers (`docs-v2/engineering/W44-CAPABILITY-MAP.md`, item 14) |
| OD-018 | Does the schema-16 `Case` table keep its name? | owner | 2026-11-02 | UNKNOWN | — | `docs-v2/engineering/RECON-W44-BACKLOG.md` §5, item 5 | a data shape | the question as put to the owner on 2026-11-02; no ruling recorded. The de facto name is `epistemic_case` (`CASE` is a reserved SQL word) and the table exists in that shape |
| OD-019 | The frame of the answer is translated; the knowledge is not | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-011) | none | the owner was asked how far the translation reaches and answered on 2026-11-02; the entry itself is marked "Owner decision" |
| OD-020 | The nine shadow classes on `develop` are wired to the live engines rather than deleted, and their math is verified first | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-012) | a contract | the owner, on 2026-11-02; the entry names the order the owner gave (`ErrorDrivenLearner` first) and records what the verification found |
| OD-021 | The one disagreeing benchmark item stays answered, and the disagreement is recorded | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-014) | none | the owner, on 2026-11-02; the item is named `en-unanswer-2` in the document |
| OD-022 | The magic numbers are fixed in the code; the baseline is NOT updated | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-015) | none | the owner, on 2026-11-02; the document records that the frozen zones belong to the owner |
| OD-023 | The shadow classes are wired to the live engines one at a time, the math first | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-016) | a contract | the owner, on 2026-11-02; the entry says it sharpens D-012 (OD-020) rather than replacing it |
| OD-024 | The full repository test suite is not run; the targeted packages stand | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-017) | none | the owner, on 2026-11-02: the full run was withheld for now, and the document says the item "remains an open, owner-held item rather than a silent omission". The W44 audit later ran the suite inadvertently, without the owner's approval; the decision was not rescinded, and `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` Q-W46-06 asks for the approval again |
| OD-025 | The destructive reload erases the case together with its decision history: the erasure set covers the four observation tables | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-03); the question it answers | a schema and a behaviour change — NOT implemented in W46 | the owner's answer of 2026-11-05 to the W46 questionnaire; the implementation is TASK-W46-16 |
| OD-026 | The reload's consent flag accepts both `?replace=true` and `replaceExisting: true` | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-01) | an API change — NOT implemented in W46 | the owner's answer of 2026-11-05; the implementation is TASK-W46-16 |
| OD-027 | The reload gains a preview: `?dryRun=true` returns what would be replaced and changes nothing | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-02) | an API addition — NOT implemented in W46 | the owner's answer of 2026-11-05; the implementation is TASK-W46-16 |
| OD-028 | The GPU path is fixed in both places: the launcher exports `LD_LIBRARY_PATH`, and the provider diagnostic reports the truth | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-07); the measurement in `docs-v2/operations/HARDWARE-PROFILE.json` | a serving-path change — NOT implemented in W46 | the owner's answer of 2026-11-05; the measurement (12.6x) is in the hardware report; the implementation is TASK-W46-9 |
| OD-029 | The retention policy: the backups live outside the repository, 7 days daily plus 4 weeks monthly, and a restore happens only on the owner's explicit command | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-13); the procedure in `docs-v2/operations/RECOVERY-RUNBOOK.md` | an operational policy — the procedure is written; the schedule is not | the owner's answer of 2026-11-05; the schedule is TASK-W46-17 |
| OD-030 | The fifteenth TLA+ specification is gated: it moves into `formal/`, enters the specification map and enters the gate | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-16); `formal/spec-implementation-map.yml` | the gate's scope grows — NOT implemented in W46 | the owner's answer of 2026-11-05; the capability registry's count becomes 15; the implementation is TASK-W46-18 |
| OD-031 | The four subjects that lost their documents are WRITTEN, not mapped: the renumbered series keeps its numbers and the missing specifications are created | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-20) | four new normative documents — NOT written in W46 | the owner's answer of 2026-11-05; the implementation is TASK-W46-19 |
| OD-032 | PR-002 and PR-006 are ACCEPTED with a deadline: they are re-examined at W47 and must be closed there | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-18) | none yet | the owner's answer of 2026-11-05; the deadline is the W47 wave; the implementation is TASK-W46-20 |
| OD-033 | The four MATRIX skills are installed under `.agents/skills/` AND registered in the harness's trigger table | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/SKILLS.md`; `docs-v2/governance/SKILL-INVENTORY.md` | the harness's own source and its mirror in the home directory — NOT changed in W46 | the owner's answer of 2026-11-05; the implementation is TASK-W46-21 |
| OD-035 | The retention has THREE tiers: the daily backups of the last 7 days, one weekly point per week for 4 weeks, and one monthly point per month for 12 months | owner | 2026-11-05 | ACCEPTED | OD-029 (refined: the two tiers become three) | `docs-v2/operations/RECOVERY-RUNBOOK.md` §8; `scripts/backup-retention.py` | an operations tool that DELETES files — the policy it implements is the owner's | the owner's answer of 2026-11-05 to the W47 questionnaire, asked because the phrase in OD-029 was ambiguous; the implementation is TASK-W46-17 |
| OD-036 | A workspace replacement erases the workspace's LEARNING STATE with its knowledge: the assessment items, the learning state, the transitions, the outcomes, the dialogue turns, the asked questions and the hebbian bindings go with the concepts | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/engineering/W47-WORKSPACE-REPLACEMENT-CONTRACT.md` §2 and §5 | the erasure set grows by eight tables, and the snapshot must carry them | the owner's answer of 2026-11-05 to the W47 questionnaire; it closes the residual the W47 contract recorded in §5; the implementation is TASK-W46-16 |
| OD-037 | The k-anonymity counter counts DISTINCT NODES, as the class promises, not the contributions of one hash | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/specifications/SPEC-016-decentralized-digests.md` | `io.matrix.federation.Anonymizer` counts distinct nodes per content hash | the owner's answer of 2026-11-05 to the W47 questionnaire; the defect is at `matrix-core/src/main/java/io/matrix/federation/Anonymizer.java:25-27` |
| OD-039 | The snapshot's payload gets a SIZE BOUND, and the snapshot rows inside the database get the same three-tier calendar that the backups have | owner | 2026-11-05 | ACCEPTED | — | `scripts/backup-retention.py` (the calendar), the W48 task on the payload bound | a database that grows without bound is a disk that fills up; the refusal is explicit — the snapshot is not written and the caller is told | the answer to Q-W47-01 of the end-of-wave questionnaire of 2026-11-05; the recommended option was accepted |
| OD-040 | `matt-pocock` GETS A ROOT `SKILL.md`: the owner's container becomes a skill with its seventeen sub-skills inside it | owner | 2026-11-05 | ACCEPTED | OD-034 (the refusal it reverses) | `.opencode/skills/matt-pocock/SKILL.md` | the harness would offer `matt-pocock` as ONE skill whose body points at the seventeen sub-skills — the container's meaning changes, and the owner has decided that it should | the answer to Q-W47-02 of the end-of-wave questionnaire of 2026-11-05; the owner chose the option the wave had recommended AGAINST, and that choice is theirs to make |
| OD-041 | One full `matrix-core` suite run is APPROVED: one test JVM, an 8 GiB heap, the other daemons stopped, against a copy of the working tree | owner | 2026-11-05 | ACCEPTED | TASK-W46-11 (the approval it grants) | `docs-v2/engineering/W47-STATUS.md` §10 | the project's total test population becomes a number instead of a set of scopes — the residual R-W46-10 can be closed for this commit | the answer to Q-W47-03 of the end-of-wave questionnaire of 2026-11-05; the live databases are still not touched |
| OD-042 | The restore DRILL is performed against a COPY of the live database, in a temporary directory, and the elapsed time and the observed result are written into the runbook | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/operations/RECOVERY-RUNBOOK.md` §5 | the recovery claim moves from IMPLEMENTED to VERIFIED — and only the copy is touched; nothing is restored over the live database | the answer to Q-W47-04 of the end-of-wave questionnaire of 2026-11-05 |
| OD-043 | A COMMITTED GPU BENCHMARK is written: the same model, the same sequence length and iteration count, a warm-up, a median with the dispersion, and the CPU and CUDA cases under comparable conditions | owner | 2026-11-05 | ACCEPTED | — | the `W46-GPU` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | the 12.6× claim becomes either a re-runnable measurement or nothing; the resource cost is one GPU session | the answer to Q-W47-05 of the end-of-wave questionnaire of 2026-11-05 |
| OD-044 | The curiosity drive's envelope is raised to the COST (100), so that the drive can be paid for; the refusal path stays for the drives that should be refused | owner | 2026-11-05 | ACCEPTED | OD-038 (the fix it completes) | `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java` | a serving-path change: the gateway's curiosity cycle starts SPENDING budget, and that is observable in the budgeter | the answer to Q-W47-06 of the end-of-wave questionnaire of 2026-11-05; the conjugate budget's number is the owner's to set |
| OD-038 | The null ImpulseScheduler in the gateway's autonomy loop is fixed in W47, not deferred | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/specifications/SPEC-015-autonomy-impulses.md` (the invariant the fix satisfies) | a serving-path change in chain B | the owner's answer of 2026-11-05 to the W47 questionnaire; the defect is at `matrix-brain-runtime/.../AutonomyLoop.java:38` and `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:75` |
| OD-034 | The four skill packages whose declared name differs from their directory are FIXED: the names are corrected | owner | 2026-11-05 | ACCEPTED | — | `docs-v2/governance/SKILL-INVENTORY.md` §1 and §3 | the owner's library and the plugin bundle — NOT changed in W46 | the owner's answer of 2026-11-05; the implementation is TASK-W46-22 |
| OD-045 | An autonomy impulse CONSUMER is built: it turns an accepted impulse into a bounded internal proposal, records it inside Chain B, and never produces an external effect | owner | 2026-11-06 | ACCEPTED | — | `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` §5; the question in `docs-v2/engineering/W48-QUESTIONNAIRE.md` (Q-W48-01) | a new component in Chain B — NOT implemented in W48; the implementation is the first task of the next wave | the owner's answer of 2026-11-06 to the W48 questionnaire: option A — the proposal-creating consumer with stable ids, provenance, an accounted budget, a bounded queue with explicit backpressure, expiry, deduplication, a fresh FROZEN check at execution time and an observable terminal outcome |
| OD-046 | The native-image build gets a MEASURED resource budget in the next wave — the heap and the runner size it actually needs are recorded — and the native capability stays BLOCKED until a build succeeds | owner | 2026-11-06 | ACCEPTED | — | the `W48-NATIVE-IMAGE` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`; the question in `docs-v2/engineering/W48-QUESTIONNAIRE.md` (Q-W48-02) | a resource decision for the next wave — nothing is built in W48; the nine recorded failures of `.github/workflows/native.yml` are an OOM in the build, after the toolchain installed successfully | the owner's answer of 2026-11-06 to the W48 questionnaire; the W48 directive forbids creating a native build requirement without measuring its resource cost |
| OD-047 | The GPU is an OPTIONAL ACCELERATOR, not part of the default serving path: the measurement is kept, the silent CPU fallback stays visible in the registry, and no deployment is obliged to provide the CUDA libraries | owner | 2026-11-06 | ACCEPTED | — | the `W46-GPU` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`; the measurement in `docs-v2/engineering/W48-GPU-BENCHMARK.md`; the question in `docs-v2/engineering/W48-QUESTIONNAIRE.md` (Q-W48-03) | a description, not a behaviour — the serving path does not change; the 12.6× digit stays HISTORICAL and bounded by the 12.06×–12.69× measurement | the owner's answer of 2026-11-06 to the W48 questionnaire; the W46 registry row had carried "the owner must also decide whether the GPU is in scope for MATRIX at all" since W46 |
| OD-048 | MATRIX's PUBLIC RESEARCH PRESENCE becomes a permanent workstream: the README, the GitHub Pages site, the contributor-facing material, the publication pipeline and the collaboration brief are kept aligned with the repository's ACTUAL state, and every public capability statement is traceable to an artifact | owner | 2026-11-06 | ACCEPTED | — | `docs-v2/engineering/W49-STATUS.md`; the W49 public workstream in `docs-v2/governance/ROADMAP.md` §4.8 | a permanent direction, not a campaign — the measure is independent validation, useful research criticism, reproducible improvements and concrete domain testing, and never views, stars, downloads or publicity | the owner's W49 directive addendum of 2026-11-06. The decision is a DIRECTION and a STANDARD: public communication follows the same evidence rules as engineering, and the three descriptions — vision, current implementation, evidence and limitations — are never merged into one claim |
| OD-049 | The DETERMINISTIC EXTRACTION PATH STAYS THE CRITICAL PATH and the teacher stays a teacher: no model is called on the acquisition path or on the answering path | owner | 2026-11-07 | ACCEPTED | — | `docs-v2/engineering/W50-LOCAL-MODEL.md` (the measurement and its verdict); the wave report `docs-v2/engineering/W50-STATUS.md` | none — the teacher stays off; the acquisition path stays deterministic and the answering path makes no model call | the measurement of 2026-11-07: the teacher was **invoked 0 times** during the deterministic acquisition, and it is **not** better at extraction — 72.7 % of its propositions are verbatim in the source against the deterministic path's **100 %** |
| OD-050 | The ACQUISITION BOUNDARY is a pre-cached, versioned, licensed pool that ships with the application: no live web request is made | owner | 2026-11-07 | ACCEPTED | — | `docs-v2/engineering/W50-STATUS.md`; the pool's manifest `matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json` | the learning cycle reads a classpath catalogue, at most three documents per question, and every report says what it considered, what it acquired and what it skipped | the run of 2026-11-07: the cycle acquired **3** pool documents and **324 claims**, each document carrying its licence, its `oldid` and its access date — and **no request left the machine** |
| OD-051 | A USER ASSERTION IS AN INPUT TO EVALUATE and is never promoted into the store: the assessment route never writes | owner | 2026-11-07 | ACCEPTED | — | `docs-v2/engineering/W50-STATUS.md`; `matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java` | the assessment route returns a verdict with `promoted=false`; it creates no claim, no evidence and no status | the demonstration's turn 3: the verdict was **CONTRADICTED**, the disputed premise and the contradicting claim and its source were named, and the store held no trace of the assertion |
| OD-052 | The CORPUS IS ONE BOUNDED DOMAIN, with the per-file licence, revision and sha256 in a manifest that the import verifies | owner | 2026-11-07 | ACCEPTED | — | `matrix-core/src/main/resources/corpus/plate-tectonics/corpus-manifest.json`; `matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json` | the import refuses a corpus file whose bytes are not the recorded bytes; the seed import produced **348 claims** and the bounded cycle **324** more | the seed corpus is 2 documents (USGS *This Dynamic Earth*, US public domain; Wikipedia *Plate tectonics*, CC BY-SA 4.0, oldid 1381934246) and the pool is 7 versioned Wikipedia snapshots. The licence question the corpus raises is **not** answered by this decision — it is Q-W50-01 |
| ADR-2026-09-12-001 | Adopt an HDC × BitLinear × Boolean hybrid brain as an architectural primitive, orthogonal to MPDP | normative | 2026-09-12 | ACCEPTED | — | `docs-v2/architecture/ADR-2026-09-12-001-hdc-bitlinear-hybrid-brain.md` | a contract | the document names no decider; it is a standing architectural rule, referenced by `docs-v2/engineering/STANDARDS-MATRIX.md` as Accepted |
| ADR-2026-10-26-002 | `TrueMindCycle` is the one cognitive conductor — of the brain runtime | owner | 2026-10-26 | ACCEPTED | ADR-W45.2-01 (for the epistemic application only) | `docs-v2/architecture/ADR-2026-10-26-002-single-cognitive-conductor.md` | a contract | decided by the repository owner via MASTER ENGINEERING DIRECTIVE v2, §13, for `matrix-brain-runtime` and `matrix-api-gateway`. The header carries a scope annotation of 2026-11-04: the decision holds for the brain runtime, is **not** the conductor of the epistemic application, and the body was not rewritten |
| ADR-2026-10-30-003 | The HashChain trust model: an injected clock, sequence validation, and tamper-evidence rather than tamper-proofness | normative | 2026-10-30 | ACCEPTED | — | `docs-v2/architecture/ADR-2026-10-30-003-hashchain-trust-model.md` | a contract | the document names no decider; it records the RECON-W39 findings on `io.matrix.audit.HashChain` and the rules that follow |
| ADR-2026-10-30-004 | One deletion semantics per class in the Noosphere CRDT, with durable tombstones | normative | 2026-10-30 | ACCEPTED | — | `docs-v2/architecture/ADR-2026-10-30-004-noosphere-crdt-semantics.md` | a contract | the document names no decider; it records the RECON-W39 finding that three incompatible deletion semantics existed in one package |
| ADR-2026-10-30-005 | The production MCTS search has no reward signal, so no planning-superiority claim is made | normative | 2026-10-30 | ACCEPTED | — | `docs-v2/architecture/ADR-2026-10-30-005-mcts-reward-signal.md` | none | the document names no decider; the decision is a refusal to claim — the reward is a constant 0.5, so replacing it needs a separate decision |
| ADR-W43-01 | The epistemic model of MATRIX: what a piece of knowledge is, and the states it can be in | owner | 2026-11-02 | ACCEPTED | — | `docs-v2/engineering/ADR-W43-01-epistemic-model.md` | a data shape | the repository owner, via the W43 directive; the document names the coordinator as author and points at §9 for the points that still need the owner |
| ADR-W44-01 | The cognitive kernel: its contracts, its placement in `matrix-core`, and its schema | owner | 2026-11-02 | ACCEPTED | ADR-W45.2-01 (scope clarification only) | `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md` | a contract | the repository owner, via the W44 directive itself. The header carries a scope annotation of 2026-11-04: the canonical entry point is named per serving application and the five cycles are kept rather than merged; the body was not rewritten |
| ADR-W45.2-01 | The canonical entry point of each serving application is named in code | owner | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` | a contract | the repository owner, via the questionnaire of 2026-11-04 (OD-001). Enforced by `io.matrix.api.CanonicalEntryPoint` and the two tests the ADR names |
| ND-001 | The atomic computational unit is the BirUnit in one of the three BIR forms, evaluated through `BooleanRuntime.evaluate` | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-001) | a contract | the log is marked "normative · singleton · decisions log" and names no decider; each entry states a decision, a rationale and a criterion for reversal. **Date caveat:** the log carries no per-decision date, so the register uses the last revision of the log (2026-08-27) for all eleven entries |
| ND-002 | The producers are Tsetlin, WiSARD and MPDT-GA, the last as a baseline | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-002) | none | as ND-001; adopting a new producer is stated to require a separate RFC and a preregistered hypothesis |
| ND-003 | FROZEN ethics: the four prohibitions are implemented and cannot be lifted without the FROZEN procedure | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-003) | a contract | as ND-001; the entry names the implementing classes and the TLA+ contract, and states that only a formal change of the constitution by the owner can cancel it |
| ND-004 | Memory: the `m0` / `M0`…`M4` / `M5+` hierarchy | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-004) | a data shape | as ND-001; a change of the levels is stated to require an RFC and a TLA+ specification |
| ND-005 | Decentralisation through anonymised digests | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-005) | a contract | as ND-001; the entry names `io.matrix.federation.Anonymizer` and the post-quant ML-DSA signing channel |
| ND-006 | The external stack dependencies: Java 25, Quarkus 3.38.3, GraalVM plugin 1.1.10, Avro 1.12.2, ONNX Runtime 1.29.0, kafka-clients 4.3.1, Testcontainers 1.21.3 | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-006) | none | as ND-001; the versions match `matrix-core/build.gradle` today. A major upgrade is stated to require a separate RFC with JMH-gate measurements |
| ND-007 | The INV-1 source-scan guard | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-007) | a contract | as ND-001; the guard is `matrix-core/src/test/java/io/matrix/bir/Inv1SourceGuardTest.java`; it exists and runs in the ordinary test task |
| ND-008 | The wave-based mode of work: one coherent task per wave, with a checkpoint commit and a push | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-008) | none | as ND-001; a move to continuous integration is stated to require an RFC |
| ND-009 | The experiment protocol | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-009) | a contract | as ND-001; the protocol is `docs-v2/research/PROTOCOL.md` and the register is `docs-v2/research/HYPOTHESES.md` |
| ND-010 | GitHub Pages publishes `docs/`; the rest of `docs-v2/` is served through the repository | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-010) | none | as ND-001; the workflow is `.github/workflows/pages.yml` |
| ND-011 | Archiving: the history is kept for audit, and the active documents stay compact | normative | 2026-08-27 | ACCEPTED | — | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-011) | none | as ND-001. **The document's own "Решение" line is missing from this entry** — it jumps straight to the rationale and the criterion of reversal — so the one-line summary here is taken from the section title and its rationale, not from a decision sentence |
| CD-001 | The observation disposition is a new concept with its own table, not an overload of `Case.Origin` | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D1) | a data shape | taken by the W45.0 wave under the W45 directive; the log records the rejected alternatives and the evidence (`Case.Origin` has five provenance values, the evidence relation seven) |
| CD-002 | An unclassifiable record is held as `UNRESOLVED`, never eligible | coordinator | 2026-11-03 | ACCEPTED | CD-012 (the backfill clause only) | `docs-v2/engineering/W45-DECISION-LOG.md` (D2) | a data shape | taken by the W45.0 wave under the W45 directive; the log records the unsafe direction that was rejected. The v16→v17 backfill clause of this decision was corrected by CD-012 |
| CD-003 | The contradiction rule is "against a belief", not "against any prediction" | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D3) | a contract | taken by the W45.0 wave under the W45 directive |
| CD-004 | The reference method reads the model-eligible observations | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D4) | a contract | taken by the W45.0 wave under the W45 directive |
| CD-005 | No procedure is promoted without independent evidence, and the fast path is relabelled | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D5) | a contract | taken by the W45.0 wave under the W45 directive |
| CD-006 | The epistemic write is one transaction | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D6) | a contract | taken by the W45.0 wave under the W45 directive; implemented in `KnowledgeStore.recordObservationAtomically` |
| CD-007 | The cycle budget is a deadline on a monotonic clock | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D7) | a contract | taken by the W45.0 wave under the W45 directive |
| CD-008 | The drive state is durable and its version is allocated inside the transaction | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D8) | a data shape | taken by the W45.0 wave under the W45 directive |
| CD-009 | One cognitive cycle runs at a time per kernel | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D9) | a contract | taken by the W45.0 wave under the W45 directive |
| CD-010 | The W44 tests that encoded the defects were updated, not deleted | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D10) | none | taken by the W45.0 wave under the W45 directive; a rule about how the wave treats the previous wave's tests, with no runtime effect |
| CD-011 | The W44 archaeology artefacts were not regenerated | coordinator | 2026-11-03 | ACCEPTED | — | `docs-v2/engineering/W45-DECISION-LOG.md` (D11) | none | taken by the W45.0 wave under the W45 directive; a rule about artefact custody. Note that CD-015 later corrected one of those artefacts' claims, in a new document rather than in the old one |
| CD-012 | A legacy record is classified by the durable evidence, not by the evidence relation alone | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.1-LEGACY-DISPOSITION-REPORT.md`; the wave's statement in `docs-v2/engineering/W45.1-STATUS.md` ("Priority D") | a data shape | taken by the W45.1 wave under the W45 directive, after the W45.0 backfill was found wrong for one class of records; the corrected rule consults the cognitive error records and falls back to the relation only when they are absent |
| CD-013 | The request lifecycle is an explicit state machine, and an interrupted request is classified rather than repeated | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.1-CRASH-CONSISTENCY-DESIGN.md`; the per-crash-point table in `docs-v2/engineering/W45.1-RECOVERY-MATRIX.csv` | a contract | taken by the W45.1 wave under the W45 directive; the matrix names the durable records, the possible side effects, the recovery action and the verification test for each crash point |
| CD-014 | When the liveness of an owner cannot be disproved, the claim is treated as OWNED | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.1-CRASH-CONSISTENCY-DESIGN.md` (§ the ownership rule) | a contract | taken by the W45.1 wave under the W45 directive; the rule is deliberately asymmetric, because a claim wrongly considered alive is visible while one wrongly considered dead duplicates an execution. Its safe scope is bounded by OD-004 |
| CD-015 | The adjudication history is append-only; the current disposition is a projection of it, and a re-observation never adjudicates | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.1-DISPOSITION-LIFECYCLE.md` | a data shape | taken by the W45.1 wave under the W45 directive; the defect it fixes was reproduced against the pre-fix code, not merely described |
| CD-016 | A failed cycle has a truthful terminal state: a FAILURE step, a closed episode, a FAILED outcome | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.1-STATUS.md` ("Priority C") | a contract | taken by the W45.1 wave under the W45 directive; the HTTP surface maps a failure to 500 and a timeout to 503 |
| CD-017 | The canonical entry point is declared in code, so that a test can check it | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` (the declaration and its enforcement) | a contract | the mechanism the coordinator chose to implement OD-001 with; the names are strings, so `matrix-core` does not depend on the gateway, and `CanonicalEntryPointTest` and `CanonicalEntryPointChainBTest` resolve each name that can be resolved |
| CD-018 | The isolation of the two chains is proved by a gate that can go red, with a negative control | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` | a boundary | the mechanism the coordinator chose to implement OD-002 with; the gate reports 16 checks and the negative control 6, and both have been seen going red on a deliberately broken tree |
| CD-020 | The two chains stay separate processes and the **bridge is the only crossing**; the bridge implements BRIDGE-1…10 and may add an observation, a candidate or an evidence link, and nothing else | coordinator | 2026-11-08 | ACCEPTED | — | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md`; the requirement-to-code map in §4 of that document | a boundary | taken by the W51 wave under the owner's directive of 2026-11-08, which revised the strict separation: "process boundaries must no longer mean cognitive isolation". **It amends OD-002's "no bridge" clause only** — the isolation, the single writer, the fencing and the fail-closed rule all stand, and the gate's rules G1–G6 still hold |
| CD-021 | The canonical cognitive contracts live in `io.matrix.contract`, a package that neither chain owns and that may not open a database, serve a route or reach a store | coordinator | 2026-11-08 | ACCEPTED | — | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` §3; the rule in the gate as **G7** | a contract | taken by the W51 wave; the package placement is what lets the isolation gate keep G1–G6 unchanged and adds G7. A shared class that can open a database is a class both processes can call |
| CD-022 | The shared runtime is a **declaration registry** — components, capabilities, dependencies, health — and not a service locator and not an event bus | coordinator | 2026-11-08 | ACCEPTED | — | `io.matrix.runtime.CognitiveRuntime`; `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` §5 | a contract | taken by the W51 wave; the registry holds declarations and not references, so a component can be replaced without the runtime knowing about it, and there is no global bus in MATRIX |
| CD-023 | The plugin's isolation is a **closed declarative interpreter**; a Java classloader is not a sandbox, and WASM/WASI is unavailable in this environment | coordinator | 2026-11-08 | ACCEPTED | — | `io.matrix.plugin.DeclarativeSandbox`; the source-level proof in `PluginLifecycleTest`; `docs-v2/engineering/W51-STATUS.md` §3.2 | a boundary | taken by the W51 wave; the isolation is a property of the language's vocabulary — it contains no operation that opens a file, a socket, a process or a class — and it is honestly weaker than a WASM runtime |
| CD-024 | The creator generates a **bounded family** — a numeric threshold classifier — and **refuses every other gap with a reason** | coordinator | 2026-11-08 | ACCEPTED | — | `io.matrix.plugin.CreatorMode` | a contract | taken by the W51 wave; the mandate asks for "a bounded proof of concept" and explicitly does not ask for an unrestricted autonomous software engineer. A creator that faked a capability would be worse than one that says it cannot |
| CD-025 | The **Java path stays the selected path** for the BIR truth-table kernel; the native path is kept, measured, tested for exact equivalence and disabled by policy | coordinator | 2026-11-08 | ACCEPTED | — | `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md`; `io.matrix.nativekernel.NativeKernelPolicy` | a contract | taken by the W51 wave on measured grounds: the native path is 17–20× faster than the BIR reference API but only 0.75–1.02× against the same reference code on flat data, and 67 % of the native call is the marshalling. Turning it on needs a new measurement, not a configuration change |
| CD-032 | **A remote cognitive process is a capability provider, and its output is a report that must be evaluated** — not a source of truth | coordinator | 2026-11-09 | ACCEPTED | — | `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md`; `docs-v2/engineering/W53-BASELINE.json` | an architecture decision | taken by the W53 wave under the owner's directive of 2026-11-09: the W52 bridge moved knowledge in both directions, but a result produced by Chain B never returned to Chain A as something the epistemic model could judge. The rule now is that a remote result arrives as an AUTHENTICATED SECONDARY REPORT, keeps its own provenance, and is adjudicated by Chain A — used with a qualification, used only in its supported part, rejected, marked unresolved, or refused — and it never becomes an established belief because the remote call succeeded. A high remote confidence is not evidence either: the confidence's BASIS decides what it permits. The corollary is the lineage rule: a proposition that returns to Chain A through Chain B is recognised as returning, and is not counted as independent corroboration |
| CD-033 | **The remote execution is durable before it is dispatched, and the guarantee is at-least-once delivery with effectively-once logical effects** — no distributed exactly-once claim | coordinator | 2026-11-09 | ACCEPTED | — | `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` §4 | an architecture decision | taken by the W53 wave under the owner's directive of 2026-11-09: Chain A persists the dispatch intent before the request leaves; Chain B persists the accepted request, its payload hash and its state before it executes; the request id and the payload hash are durable on both sides, and the idempotency decision never rests on an in-memory map — which is the defect the W52 report named for the second direction. Two independently owned SQLite databases cannot offer a distributed exactly-once guarantee, and the design says so instead of implying one: the duplicate rules are explicit (the same id with the same hash replays the result; the same id with a different hash is refused as a conflict), and a request found in a non-terminal state after a restart is reconciled rather than silently re-run |
| CD-034 | **The remote report is admitted into chain A's epistemic model as a candidate, and the verdict on the proposition is a separate fact** — an admitted report is never a supported proposition | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.cognitive.RemoteReportAssessor`; `io.matrix.bridge.EpistemicBridgeSink`; `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` §2e | an architecture decision | taken by the W53 wave under the owner's directive of 2026-11-09: the mandate says a remote result must return to chain A "as an authenticated secondary report" and must not "directly become an established belief". The report therefore crosses into chain A through its own governed bridge receiver — the same crossing, the same rights and the same audit that admit chain B's knowledge messages — and it arrives as a candidate with the relation the peer stated, the lineage the peer declared, and **chain A's own confidence, which is none**. The verdict is then chain A's own reading of that record, and the two facts are stored apart (`report_disposition` and `disposition`), so a reader can tell a refused report from an admitted-and-qualified one. The two rules this decision carries are measured in the tests: a peer's confidence never makes a proposition supported here, and a report that rests on what chain A supplied is qualified rather than supported. In the recorded two-process demonstration the verdict was UNRESOLVED, and that is the intended outcome, not a shortfall |
| CD-035 | **A withdrawal is a crossing of its own** — it has its own right (`INFERENCE_CANCEL`), its own typed contract and its own message id; it is never a field of the request it withdraws | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.contract.bridge.CognitiveTaskCancel`; `io.matrix.contract.bridge.BridgeCapability`; `io.matrix.bridge.TaskPublisher`; `io.matrix.api.bridge.RemoteTaskStore`; `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` §6.5 | an architecture decision | taken by the W53 wave under the owner's directive of 2026-11-09: the mandate asks that cancellation be supported where it is safe, and a cancellation that stops only the caller hides work — the peer keeps running the task and commits its effect while the caller believes it stopped. Carried as a field of the request, the withdrawal would arrive with the work itself, too late to prevent anything, and every request would carry the authority to withdraw itself; as its own kind, the right is granted by the receiver, checked on every message and audited like every other crossing. The message id is **derived from the request id and is not the request id**: the bridge's idempotency is keyed on the request id, so a withdrawal that reused it would reach the peer as a second payload under an id it already knows, and be refused as a conflict — the message meant to end the request would be refused as a forgery of it. The peer's state machine then says what became of the task: a task withdrawn before it started is never executed, one withdrawn while it runs cannot be interrupted (the brain cannot be interrupted) and is closed as cancelled with its answer kept, and a task that already finished is not withdrawn at all — a cancellation cannot undo a committed effect, and saying otherwise would hide a fact the caller needs |
| CD-036 | **An answered dispatch that was never judged is unfinished work** — the reconciliation queue holds two kinds of unfinished dispatch: one whose answer is not on record yet, and one whose answer is on record but whose verdict is not; a cancelled or a timed-out dispatch, whose answer is deliberately kept unjudged, is finished | coordinator | 2026-11-10 | ACCEPTED | — | `io.matrix.cognitive.CognitiveStore.unfinishedDispatches`; `io.matrix.cognitive.distributed.DispatchReconciler`; `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` §6.4 | an architecture decision | taken by the W53 wave under the owner's directive of 2026-11-09: the mandate asks that the crash boundaries be tested and that the recovery be real. The boundary *after chain A receives the response but before its own result is committed* turned out to be a hole, not a covered case: the peer's answer was written to the dispatch row, the verdict was not, and the row was in neither of the two states the queue knew — the answer is not a verdict, so the task stayed unjudged for ever, and no pass could finish it because no pass could see it. The rule is now stated in the store, where the truth is: a dispatch is unfinished while it owes chain A a decision. The two states that keep an answer unjudged **on purpose** are excluded, so a task that was called off is never sent again. | `CognitiveCoordinatorRecoveryTest` (a crash between the answer and the verdict is finished by the pass; with the store's query reverted the row is not found and the test goes red); `DispatchIntentTest` (a cancelled or a timed-out dispatch is finished; with the exclusion removed the cancelled row joins the queue and that test goes red); the retained JUnit XML of 2026-11-10: 1617 tests, 0 failures |
| CD-030 | A **supersession requires a scoped denial**, not a better-supported claim with lexically similar words | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.knowledge.ModelRevisionDecider`; `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` | a rule | taken by the W53 wave after MEASURING the whole 29-case evaluation with the rule in place: the score is **18 of 29, unchanged**, and the eleven failures are the same eleven — so the change is not a score-motivated one, and no case was excluded, no expectation changed and no assertion weakened. The rule it replaces decided "about the same subject" by the words two sentences share, and the workspace has no other identity for a subject: its concepts are scoped to the sentence they came from, and its scope and context are the sentence's own beginning. The mandate says a supersession "must not be inferred from weak lexical similarity alone", so the relation that decides it is now a relation: the new sentence must deny what the older claim asserted, and the denial is scoped to that claim. A better-supported claim that denies nothing is an addition, and the decision says so — including the reason, which names the similarity it refused to act on. **What the rule does not fix is named too:** the denial's own scoping is still lexical, which is why the two revision cases that expect no supersession still get one — the blocker is in §7.4 of the wave's report, not in the rule |
| CD-031 | The **procedure of the settled model is written down**, with the standing it actually has — an in-sample consistency check is written as one, and a validated procedure is written as one | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.cognitive.CognitiveKernel`; `io.matrix.cognitive.algorithms.Procedure`; `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` | a contract | taken by the W53 wave: the W45 finding — the lamp world's input domain is exhausted once the model settles, so no observation can be both independent of the model and available — was true and was also half the story. The fast path was already being used as an in-sample shortcut of the reference, and the record that said so was never written, so the system's procedural memory was empty and the system could not claim that it had learned a procedure. The record now says what it rests on: the validation kind, the case count, the episode it was learned in, and the note that the agreement was measured over the model's own cases. The exhaustion is not a defect of the world but an arithmetic of the space — with V inputs there are 2^(2^V) candidates and each distinct assignment eliminates half of them, so the model settles only after every assignment the domain has — and that is proved in a test, not asserted |
| CD-028 | The **second direction of the bridge carries established knowledge**, and the RIGHT names the direction — chain A's bridge grants OBSERVATION, CANDIDATE and EVIDENCE_LINK, chain B's bridge grants KNOWLEDGE | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.bridge.KnowledgePublisher`; `io.matrix.api.bridge.KnowledgeBridge`; `io.matrix.contract.bridge.BridgeCapability` | a contract | taken by the W52 wave: the first direction let chain B report what it observed and chain A record it, and a system whose halves can only report upwards is not one system. The direction is carried by the granted right and not by a flag in the message, so a message of the wrong direction is refused for exactly the same reason a forged one is. The governance is the same governance on both sides — the signature, the capability, the budget, the idempotency key and the audit — because a second direction with weaker rules would be a second, quieter bridge. The protocol moved to version 2, and the version rule still reads version 1 |
| CD-029 | The **relevance floor of the epistemic retrieval stays at 0.25, weighted by IDF** — the measurement says it cannot be moved | coordinator | 2026-11-09 | ACCEPTED | — | `docs-v2/engineering/W52-STATUS.md` §5.4; `docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json` | a threshold | taken by the W52 wave after MEASURING three configurations of the whole 29-case evaluation: the shipped one gives 18 of 29 with 6 of 6 unanswerable cases abstaining; a floor counted over the question's terms gives 19 of 29 but 5 of 6; a floor at 0.12 gives 15 of 29 and 1 of 6. The unanswerable "How many research vessels are surveying the mid-ocean ridges at the moment?" and the answerable "How does subduction at a convergent boundary produce deep earthquakes and volcanoes?" sit in the same band of coverage, so no threshold on coverage separates the two families. The floor therefore stays, the eleven failures stay failures, and no expectation was changed, no case excluded and no assertion weakened |
| CD-026 | The **canonical vocabulary is the interchange form**, the runtime's own records stay the store's representation, and the boundary between them is **one named, tested mapping** — not a rewrite of the durable path | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.cognitive.canonical.CanonicalCycleBuilder`; `io.matrix.cognitive.canonical.CycleFacts`; `io.matrix.contract.CognitiveCycle` | a contract | taken by the W52 wave: the mandate forbids "competing implementations of the same responsibility", and two vocabularies for one concept are exactly that — unless the boundary between them is explicit. The runtime's own records stay: the store's schema and the W45.1 recovery contracts are written against them, and replacing them would be a refactor of a working, durable path for no measured gain. What changes is that the canonical form is now **filled**: the cognitive cycle produces it, and the test that asserts every canonical contract has a producer is the acceptance |
| CD-027 | A **domain may assert a connection; it may not assert how well supported it is** — the world-state memory owns the count, the evidence and the confidence of every relation it holds | coordinator | 2026-11-09 | ACCEPTED | — | `io.matrix.cognitive.worldstate.StateRelation`; `io.matrix.cognitive.worldstate.WorldStateMemory` | a data shape | taken by the W52 wave: the micro-world's assignment determines the lamp, and the domain knows that; but the system's evidence for it is the system's own, and the canonical Relation keeps association strength, frequency, causal relevance, confidence and evidential support apart. A template that carried the certainty would let a domain assert its own certainty |
| CD-019 | The contract for a future bridge between the chains is written down now | coordinator | 2026-11-04 | ACCEPTED | — | `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 2, the bridge contract) and `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` | a contract | the coordinator's commitment inside OD-002: ten requirements (BRIDGE-1…10) that a future bridge must satisfy, written down so that a later wave does not invent one. No bridge exists and none is to be built without a further decision |
| CD-W42-01 | The benchmark is built before any improvement is attempted | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-001) | a contract | taken by the W42 wave under the owner directive of 2026-11-01; the entry states what it forecloses: no claim in W42.1–W42.5 may state an improvement without the before-and-after numbers from that instrument |
| CD-W42-02 | No historical branch is merged, restored, or deleted | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-002) | none | the W42 wave under the owner directive; the entry records that the absence of the four classes is test-enforced |
| CD-W42-03 | The `KnowledgeGraphStore` becomes a projection, not a fourth store | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-003) | a data shape | the W42 wave under the owner directive |
| CD-W42-04 | The BM25 baseline is a measurement, not a candidate | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-004) | none | the W42 wave under the owner directive |
| CD-W42-05 | The learning state machine is persisted, not recomputed | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-005) | a data shape | the W42 wave under the owner directive |
| CD-W42-06 | The planner selects; it does not generate | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-006) | a contract | the W42 wave under the owner directive |
| CD-W42-07 | The mission's §14 targets are read as measurements, not as promises | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-007) | none | the W42 wave under the owner directive |
| CD-W42-08 | The defects found by the audit are fixed in the wave that found them | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-008) | none | the W42 wave under the owner directive |
| CD-W42-09 | The four W41 documentation contradictions are corrected, not rewritten | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-009) | none | the W42 wave under the owner directive; the same rule as CD-011, two waves earlier |
| CD-W42-10 | The specialist audits are the evidence base, not a summary of them | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-010) | none | the W42 wave under the owner directive |
| CD-W42-11 | A benchmark item's POINTER may be corrected; its ETALON may not | coordinator | 2026-11-01 | ACCEPTED | — | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-013) | none | the W42 wave under the owner directive; the entry carries the rule and the criterion that would change it |
| PR-001 | Add a GitHub Actions workflow that validates wave checkpoint integrity on every push | proposal | 2026-09-18 | REJECTED | — | `docs-v2/proposals/RFC-001-wal-checkpoint-workflow.md` | none | rejected by the owner on 2026-09-18 in favour of the manual process; the document keeps the rejected workflow for the historical record and states the replacement rule |
| PR-002 | Add a weekly CI job that runs the fresh-clone smoke test | proposal | 2026-09-29 | **REJECTED** | the manual step Z1 in `docs-v2/research/MIND-VALIDATION-CHECKLIST-v3.md:107` | `docs-v2/proposals/RFC-weekly-ci-smoke.md` | none | **W47, 2026-11-05.** The RFC itself says the automation blocks nothing (`RFC-weekly-ci-smoke.md:6`); the implementation needs an RFC mandate for `.github/workflows/**`, which is a FROZEN zone, and W47 has none; a weekly CI job already exists (`.github/workflows/ci.yml:13-16`) and runs something else; the predecessor PR-001 was closed the same way. The manual smoke step and the `bash -n` gate (`scripts/quality-gate.sh`) remain. The RFC is preserved, not rewritten. does not itself modify `.github/workflows/**` and that the campaign proceeds without the automation. No such workflow exists |
| PR-003 | Run the research and property suites on a weekly schedule in CI | proposal | 2026-10-05 | ACCEPTED | — | `docs-v2/proposals/RFC-weekly-research-suite.md`; the approval recorded in `docs-v2/quality/OPEN-DECISIONS.md` (CLOSED decisions, D7) | none | approved by the operator in the RECON-W38 pre-session and implemented in commit `6a15f178`; the schedule is visible in `.github/workflows/ci.yml`. This row and OD-012 are the same decision seen from two sides: the RFC is the proposal, the operator ruling is the decision |
| PR-004 | Global decentralized brain federation: a hybrid federation of local digital brains | proposal | 2026-09-17 | PROPOSED | — | `docs-v2/proposals/PROPOSAL-global-decentralized-brain-federation.md` | none | the document says "proposal · draft v0.1" and names no decider; it is a design proposal, not a decision. The `UNVERIFIED` mark that the W46 document audit gave it is a claim-provenance status, not a decision status, and it does not change this row |
| PR-005 | Wave 347: the architecture of the global decentralized brain federation | proposal | 2026-09-17 | PROPOSED | — | `docs-v2/proposals/PROPOSAL-wave-347-global-federation-architecture.md` | none | the document says "proposal · draft v1.0" and names no decider; it builds on PR-004 and on `DESIGN-58`. Same note as PR-004 about the `UNVERIFIED` mark |
| PR-006 | Split the monolithic `WAL.md` into one file per wave | proposal | 2026-09-18 | **SUPERSEDED** | `docs-v2/waves/INDEX.md` and the per-wave records `docs-v2/waves/WAL-NNN-*.md` | `docs-v2/proposals/PROPOSAL-wal-split-wave-based-architecture.md` | none | **W47, 2026-11-05.** The goal was reached by another path: the canonical chronological log is `docs-v2/waves/INDEX.md` (321 records) and the project state points at it (`docs-v2/INDEX.md:80`). The mechanism of the proposal is obsolete: `WAL.md` is not in the tree, the `wave-NNN-*.md` naming was not adopted, the waves index has no LATEST file does not exist and the archive was never created — none of its three migration phases was performed. Executing it now would rename or archive historical records, which the waves index forbids. The proposal is preserved, not rewritten.ords an acceptance or a rejection, so the status is `PROPOSED` rather than `ACCEPTED`. Its first two phases happened anyway: `docs-v2/specifications/SPEC-014-wal-management.md` (W350) and `scripts/extract-waves.py` (W351) exist. The bulk extraction did not: `docs-v2/waves/` holds exactly one `wave-*.md` file. The `WAL.md` the proposal splits is **not at that path in the repository today** — the journal is `wal/SESSION_WAL.md` — so the proposal describes a file layout that no longer exists under that name |

---

## 2. The superseded chain

| the superseded decision | replaced by | why |
|---|---|---|
| `docs-v2/architecture/ADR-2026-10-26-002-single-cognitive-conductor.md` — "`TrueMindCycle` is the one cognitive conductor" | `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` (OD-001) | the ADR was written before the bytecode was examined and reads as if one cycle governed the whole system. It still holds for the brain runtime, which is the scope its own "Applies to" line names; for the epistemic application the canonical entry point is `io.matrix.knowledge.LearningWorkbenchResource`. **The ADR was not rewritten** — it carries a one-line scope annotation in its header, dated 2026-11-04 |
| `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md` — the kernel's placement and contracts | `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` (OD-001), scope clarification only | the same annotation: the canonical entry point is named per serving application and the five cycles are kept rather than merged. The kernel's contracts, placement and schema are untouched |
| OD-005 — the first answer set (global conductor, write bridge, versioning) | OD-001, OD-002, OD-003 | the owner re-answered the questionnaire and took the recommended option on all four questions. Both answers are kept: the first is in `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` §0, the second in the same document's decision sections |
| OD-014 — "may the workspace reload erase a contradiction history?" | OD-003 — fail closed, with a recoverable snapshot | the question was put in `docs-v2/engineering/RECON-W44-BACKLOG.md` §5 on 2026-11-02 and answered on 2026-11-04 |
| the backfill clause of CD-002 — "classify a legacy record from the evidence relation" | CD-012 — classify it from the durable evidence | the W45.0 rule held every unexplained observation, which is the obsolete W44 rule preserved inside the migration. The correction is in `docs-v2/engineering/W45.1-LEGACY-DISPOSITION-REPORT.md`; CD-002's live-path clause is unchanged |
| the W44 claim that all 24 failures need a GraalVM native image | `docs-v2/engineering/W45-TEST-EVIDENCE-RECONCILIATION.md` §5.1 — 23 need it, one is a source-compliance regression | not a decision; a correction to an evidence claim, recorded here because a reader who stops at the W44 documents will reach the wrong conclusion |

---

## 3. The owner decisions that changed a runtime contract

These are the decisions whose effect is visible in the running system. Each row names the
contract, not the reasoning.

### OD-001 — the canonical entry point per serving application

| the application | the canonical entry point | the routes | the cycles it runs |
|---|---|---|---|
| the epistemic and workbench application | `io.matrix.knowledge.LearningWorkbenchResource` | `/v1/workbench/**` | `io.matrix.cognitive.CognitiveKernel` |
| | `io.matrix.api.OpenAIChatResource` | `/v1/chat/completions` | `io.matrix.reasoning.BrainLoopService` |
| the brain runtime | `io.matrix.api.MinimalHttpServer` | `POST /v1/analyze` | `io.matrix.brain.runtime.TrueMindCycle` |

**Evidence:** the decision text is `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 1);
the contract, its costs and its limits are `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md`;
the enforcement is `io.matrix.api.CanonicalEntryPoint` with `CanonicalEntryPointTest` and
`CanonicalEntryPointChainBTest`, as recorded in `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §1.
**What it did not do:** merge the cycles, create a sixth conductor, or join the two chains.

### OD-002 — the two chains stay isolated and fail closed

**What it changed:** no bridge, no implicit write path, no automatic trust, no new network
surface; chain B cannot alter claims, confidence, goals or safety policy.
**Evidence:** `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 2) for the decision,
`docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` for the contract and the honest limits of the
proof, and the gate `scripts/w45-chain-isolation-check.sh` with its negative control
`scripts/w45-chain-isolation-selftest.sh` for the proof itself.
**What it did not do:** add a bridge, grant chain B access to the epistemic store, or expose a
network surface.

### OD-003 — the destructive workspace reload fails closed and leaves a snapshot

**What it changed:** the load refuses to replace a non-empty workspace unless the caller asks
explicitly; nothing is deleted in that case. With the explicit flag, a snapshot is taken in
the same transaction as the replacement and the response carries the snapshot's id.
**Evidence:** `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 3); the executed checks
are named in `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §1.
**What it did not do:** remove the ability to replace a workspace, or version every read of
one. Two residual gaps are recorded as open in `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §3
and in `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` (Q-W46-01, Q-W46-02, Q-W46-03).

### OD-004 — one writer per database

**What it changed:** a second process opening the same cognitive database is refused at
start-up; the guard is a lock file holding the owner's pid, its start instant and the instance
token, and a lock left by a killed process does not block the next one.
**Evidence:** `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 4);
`docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §1 names the executed check, including the case
that starts a second JVM and the case that `SIGKILL`s a writer.
**What it did not do:** add leases, a fencing service or multi-writer support.

### The W45.0 and W45.1 coordinator decisions that changed a runtime contract

| decision | what it changed | where the evidence lives |
|---|---|---|
| CD-001 | a new enum and a new table (`observation_disposition`, schema v17) | `docs-v2/engineering/W45-CAPABILITY-DELTA.md` §1; `docs-v2/engineering/W45-INTEGRITY-DESIGN.md` |
| CD-006 | the epistemic write became one transaction | `docs-v2/engineering/W45-CAPABILITY-DELTA.md` §1; `docs-v2/engineering/W45-DECISION-LOG.md` (D6) |
| CD-007 | a monotonic deadline checked at ten phase boundaries; a timeout is an explicit, persisted terminal state | `docs-v2/engineering/W45-CAPABILITY-DELTA.md` §1 |
| CD-008 | the regulatory state and the provenance of every change are persisted together and restored at start-up | `docs-v2/engineering/W45-CAPABILITY-DELTA.md` §1 |
| CD-009 | one cycle at a time per kernel; every store write serialised; the sequence never repeats | `docs-v2/engineering/W45-CAPABILITY-DELTA.md` §1 |
| CD-012 | the corrected legacy backfill rule | `docs-v2/engineering/W45.1-LEGACY-DISPOSITION-REPORT.md` |
| CD-013 | the request lifecycle state machine and the classification of an interrupted request | `docs-v2/engineering/W45.1-RECOVERY-MATRIX.csv` (one row per crash point) |
| CD-014 | the ownership liveness rule | `docs-v2/engineering/W45.1-CRASH-CONSISTENCY-DESIGN.md` |
| CD-015 | the append-only adjudication history and the disposition projection | `docs-v2/engineering/W45.1-DISPOSITION-LIFECYCLE.md` |
| CD-016 | the truthful terminal state of a failed cycle | `docs-v2/engineering/W45.1-STATUS.md` ("Priority C") |
| CD-020 | the one crossing between the chains, and its ten requirements | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` §4 |
| CD-017, CD-018 | the mechanisms that make OD-001 and OD-002 checkable | `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md`; `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` |

### The earlier waves' decisions that changed a runtime contract

| decision | what it changed | where the evidence lives |
|---|---|---|
| CD-W42-01 | the benchmark exists before any improvement is attempted, and no improvement claim may skip the before-and-after numbers | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-001); the instrument is `w42-benchmark-v1.json` |
| CD-W42-03 | the `KnowledgeGraphStore` became a projection, not a fourth store | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-003) |
| CD-W42-05 | the learning state machine is persisted (`learning_state`), not recomputed | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-005) |
| CD-W42-06 | the planner selects; it does not generate | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-006) |
| OD-020, OD-023 | the shadow classes are wired to the live engines one at a time, and the math of each is verified first — the verification has already found one class whose error-driven part was dead by construction | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-012, D-016) |
| OD-024 | the full repository test suite is not run; the targeted packages stand | `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-017) |
| ND-003 | the four FROZEN prohibitions are implemented and cannot be lifted without the FROZEN procedure | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-003) |
| ND-007 | the INV-1 source-scan guard blocks legacy calls outside the whitelist | `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-007); the guard is `matrix-core/src/test/java/io/matrix/bir/Inv1SourceGuardTest.java` |

---

## 3.1 The owner's answers of 2026-11-05 — the W46 questionnaire

**These are the owner's decisions, taken in the questionnaire of 2026-11-05.** They are
**recorded, not implemented**: the owner's instruction for this wave was to fix the
decisions in the registers and to leave the behaviour to W47. Every row names the task
that implements it.

### OD-025 — The destructive reload erases the case together with its decision history: the erasure set covers the four observation tables

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-03); the question it answers
- **the effect on the runtime**: a schema and a behaviour change — NOT implemented in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05 to the W46 questionnaire; the implementation is TASK-W46-16

### OD-026 — The reload's consent flag accepts both `?replace=true` and `replaceExisting: true`

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-01)
- **the effect on the runtime**: an API change — NOT implemented in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the implementation is TASK-W46-16

### OD-027 — The reload gains a preview: `?dryRun=true` returns what would be replaced and changes nothing

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-02)
- **the effect on the runtime**: an API addition — NOT implemented in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the implementation is TASK-W46-16

### OD-028 — The GPU path is fixed in both places: the launcher exports `LD_LIBRARY_PATH`, and the provider diagnostic reports the truth

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-07); the measurement in `docs-v2/operations/HARDWARE-PROFILE.json`
- **the effect on the runtime**: a serving-path change — NOT implemented in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the measurement (12.6x) is in the hardware report; the implementation is TASK-W46-9

### OD-029 — The retention policy: the backups live outside the repository, 7 days daily plus 4 weeks monthly, and a restore happens only on the owner's explicit command

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-13); the procedure in `docs-v2/operations/RECOVERY-RUNBOOK.md`
- **the effect on the runtime**: an operational policy — the procedure is written; the schedule is not
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the schedule is TASK-W46-17

### OD-030 — The fifteenth TLA+ specification is gated: it moves into `formal/`, enters the specification map and enters the gate

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-16); `formal/spec-implementation-map.yml`
- **the effect on the runtime**: the gate's scope grows — NOT implemented in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the capability registry's count becomes 15; the implementation is TASK-W46-18

### OD-031 — The four subjects that lost their documents are WRITTEN, not mapped: the renumbered series keeps its numbers and the missing specifications are created

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-20)
- **the effect on the runtime**: four new normative documents — NOT written in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the implementation is TASK-W46-19

### OD-032 — PR-002 and PR-006 are ACCEPTED with a deadline: they are re-examined at W47 and must be closed there

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/OPEN-QUESTIONS.md` (Q-W46-18)
- **the effect on the runtime**: none yet
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the deadline is the W47 wave; the implementation is TASK-W46-20

### OD-033 — The four MATRIX skills are installed under `.agents/skills/` AND registered in the harness's trigger table

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/SKILLS.md`; `docs-v2/governance/SKILL-INVENTORY.md`
- **the effect on the runtime**: the harness's own source and its mirror in the home directory — NOT changed in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the implementation is TASK-W46-21

### OD-034 — The four skill packages whose declared name differs from their directory are FIXED: the names are corrected

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/governance/SKILL-INVENTORY.md` §1 and §3
- **the effect on the runtime**: the owner's library and the plugin bundle — NOT changed in W46
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05; the implementation is TASK-W46-22

## 3.2 The owner's answers of 2026-11-05 — the W47 questionnaire

**Four decisions, taken in the W47 questionnaire.** They are recorded with the
questions they answered, and every one of them names the task that implements it.

### OD-035 — The retention has THREE tiers: the daily backups of the last 7 days, one weekly point per week for 4 weeks, and one monthly point per month for 12 months

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: OD-029 (refined: the two tiers become three)
- **the document that holds it**: `docs-v2/operations/RECOVERY-RUNBOOK.md` §8; `scripts/backup-retention.py`
- **the effect on the runtime**: an operations tool that DELETES files — the policy it implements is the owner's
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05 to the W47 questionnaire, asked because the phrase in OD-029 was ambiguous; the implementation is TASK-W46-17

### OD-036 — A workspace replacement erases the workspace's LEARNING STATE with its knowledge: the assessment items, the learning state, the transitions, the outcomes, the dialogue turns, the asked questions and the hebbian bindings go with the concepts

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/engineering/W47-WORKSPACE-REPLACEMENT-CONTRACT.md` §2 and §5
- **the effect on the runtime**: the erasure set grows by eight tables, and the snapshot must carry them
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05 to the W47 questionnaire; it closes the residual the W47 contract recorded in §5; the implementation is TASK-W46-16

### OD-037 — The k-anonymity counter counts DISTINCT NODES, as the class promises, not the contributions of one hash

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/specifications/SPEC-016-decentralized-digests.md`
- **the effect on the runtime**: `io.matrix.federation.Anonymizer` counts distinct nodes per content hash
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05 to the W47 questionnaire; the defect is at `matrix-core/src/main/java/io/matrix/federation/Anonymizer.java:25-27`

### OD-038 — The null ImpulseScheduler in the gateway's autonomy loop is fixed in W47, not deferred

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/specifications/SPEC-015-autonomy-impulses.md` (the invariant the fix satisfies)
- **the effect on the runtime**: a serving-path change in chain B
- **the evidence or the scope of the approval**: the owner's answer of 2026-11-05 to the W47 questionnaire; the defect is at `matrix-brain-runtime/.../AutonomyLoop.java:38` and `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:75`

## 3.3 The end-of-wave questionnaire of 2026-11-05 — six questions, six answers

**The mandatory instrument of §16 was used: the harness's `ask_user_question` tool.**
The questions, their evidence, their alternatives and their consequences are in
`docs-v2/engineering/W47-QUESTIONNAIRE.md`; the answers are below, and the tasks they
create are in `docs-v2/governance/ROADMAP.md` §4.6.

### OD-039 — The snapshot's payload gets a SIZE BOUND, and the snapshot rows inside the database get the same three-tier calendar that the backups have

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `scripts/backup-retention.py` (the calendar), the W48 task on the payload bound
- **the effect on the runtime**: a database that grows without bound is a disk that fills up; the refusal is explicit — the snapshot is not written and the caller is told
- **the evidence or the scope of the approval**: the answer to Q-W47-01 of the end-of-wave questionnaire of 2026-11-05; the recommended option was accepted

### OD-040 — `matt-pocock` GETS A ROOT `SKILL.md`: the owner's container becomes a skill with its seventeen sub-skills inside it

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: OD-034 (the refusal it reverses)
- **the document that holds it**: `.opencode/skills/matt-pocock/SKILL.md`
- **the effect on the runtime**: the harness would offer `matt-pocock` as ONE skill whose body points at the seventeen sub-skills — the container's meaning changes, and the owner has decided that it should
- **the evidence or the scope of the approval**: the answer to Q-W47-02 of the end-of-wave questionnaire of 2026-11-05; the owner chose the option the wave had recommended AGAINST, and that choice is theirs to make

### OD-041 — One full `matrix-core` suite run is APPROVED: one test JVM, an 8 GiB heap, the other daemons stopped, against a copy of the working tree

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: TASK-W46-11 (the approval it grants)
- **the document that holds it**: `docs-v2/engineering/W47-STATUS.md` §10
- **the effect on the runtime**: the project's total test population becomes a number instead of a set of scopes — the residual R-W46-10 can be closed for this commit
- **the evidence or the scope of the approval**: the answer to Q-W47-03 of the end-of-wave questionnaire of 2026-11-05; the live databases are still not touched

### OD-042 — The restore DRILL is performed against a COPY of the live database, in a temporary directory, and the elapsed time and the observed result are written into the runbook

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/operations/RECOVERY-RUNBOOK.md` §5
- **the effect on the runtime**: the recovery claim moves from IMPLEMENTED to VERIFIED — and only the copy is touched; nothing is restored over the live database
- **the evidence or the scope of the approval**: the answer to Q-W47-04 of the end-of-wave questionnaire of 2026-11-05

### OD-043 — A COMMITTED GPU BENCHMARK is written: the same model, the same sequence length and iteration count, a warm-up, a median with the dispersion, and the CPU and CUDA cases under comparable conditions

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: the `W46-GPU` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`
- **the effect on the runtime**: the 12.6× claim becomes either a re-runnable measurement or nothing; the resource cost is one GPU session
- **the evidence or the scope of the approval**: the answer to Q-W47-05 of the end-of-wave questionnaire of 2026-11-05

### OD-044 — The curiosity drive's envelope is raised to the COST (100), so that the drive can be paid for; the refusal path stays for the drives that should be refused

- **kind**: owner
- **the date**: 2026-11-05
- **the status**: ACCEPTED
- **the successor**: OD-038 (the fix it completes)
- **the document that holds it**: `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java`
- **the effect on the runtime**: a serving-path change: the gateway's curiosity cycle starts SPENDING budget, and that is observable in the budgeter
- **the evidence or the scope of the approval**: the answer to Q-W47-06 of the end-of-wave questionnaire of 2026-11-05; the conjugate budget's number is the owner's to set

### OD-045 — An autonomy impulse CONSUMER is built: it turns an accepted impulse into a bounded internal proposal, records it inside Chain B, and never produces an external effect

- **kind**: owner
- **the date**: 2026-11-06
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` §5 (the scope), §6 (the safety invariants), §9 (the tests)
- **the effect on the runtime**: a new component in Chain B — **NOT implemented in W48**; the implementation is the first task of the next wave
- **the evidence or the scope of the approval**: the answer to Q-W48-01 of the end-of-wave questionnaire of 2026-11-06: option A. The scope is exactly the ADR's: stable proposal ids derived from the impulse, the cycle and a counter; the provenance; the budget accounted once, from the `FireRecord`; a bounded queue that refuses with an explicit backpressure; expiry and deduplication; a fresh FROZEN check at execution time; and an observable terminal outcome for every proposal. **No external effect, no bridge to Chain A, no executor.**

### OD-046 — The native-image build gets a MEASURED resource budget in the next wave, and the native capability stays BLOCKED until a build succeeds

- **kind**: owner
- **the date**: 2026-11-06
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: the `W48-NATIVE-IMAGE` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`; the scope in `docs-v2/engineering/W48-QUESTIONNAIRE.md` (Q-W48-02)
- **the effect on the runtime**: a resource decision for the next wave — **nothing is built in W48**; the capability stays BLOCKED
- **the evidence or the scope of the approval**: the answer to Q-W48-02 of the end-of-wave questionnaire of 2026-11-06. The recorded failures of `.github/workflows/native.yml` are an `OutOfMemoryError: Java heap space` in the build, after the toolchain installed successfully — the W48 directive forbids creating a native build requirement without measuring its resource cost.

### OD-047 — The GPU is an OPTIONAL ACCELERATOR, not part of the default serving path

- **kind**: owner
- **the date**: 2026-11-06
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: the `W46-GPU` row of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`; the measurement in `docs-v2/engineering/W48-GPU-BENCHMARK.md`
- **the effect on the runtime**: a description, not a behaviour — the serving path does not change, and no deployment is obliged to provide the CUDA libraries; the silent CPU fallback stays visible in the registry rather than in a catch block
- **the evidence or the scope of the approval**: the answer to Q-W48-03 of the end-of-wave questionnaire of 2026-11-06. The committed benchmark measures 12.06× / 11.68× / 11.47× over three runs on the serving path and positively verifies the CUDA execution provider; the historical 12.6× digit stays HISTORICAL and bounded by that measurement.

### OD-048 — MATRIX's public research presence becomes a permanent workstream

- **kind**: owner
- **the date**: 2026-11-06
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: the W49 public workstream in `docs-v2/governance/ROADMAP.md` §4.8; the wave report `docs-v2/engineering/W49-STATUS.md`
- **the effect on the runtime**: none — this is a communication and onboarding direction, not a behaviour. The repository keeps serving what it served; what changes is that the public documents describe it truthfully, and that the description is dated and linked to the artifact that proves it.
- **the evidence or the scope of the approval**: the owner's W49 directive addendum of 2026-11-06. Its audit found that the default branch (`main`) is at the 2026-10-12 commit `7a0ed0cc` while `develop` carries the current work; that the deployed Pages site still carries the "Hybrid Intelligence Encyclopedia" positioning, "city of minds", a W1500 claim and a test count that no longer holds; that the site's `_config.yml` names `https://alexandr-narbaev.github.io` while the actual deployment URL, read from the GitHub API, is `https://alexandernarbaev.github.io/agi/`; and that the contributor-facing material names paths, channels and response promises that do not exist. The decision's binding part is the EVIDENCE STANDARD: every public capability statement must be traceable to `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, `docs-v2/engineering/TEST-CATALOGUE.yml`, `docs-v2/governance/PROJECT-STATE.md`, the wave reports, the research protocols, the formal specifications with their model-check results, or a committed benchmark and its output. **A skipped test is not a pass; a proposed feature is not implemented; an isolated class is not a production capability; the blocked native-image build is not supported; the GPU is optional (OD-047); the approved autonomy consumer is not implemented until it is implemented.**
- **the deliberate non-actions**: the default branch is NOT changed (that is a separate decision); GitHub Discussions are NOT enabled (no moderation and reporting arrangement exists); no maintainer address, response SLA or support service is invented; no journal submission and no partner are required of the project.

## 3.4 The W50 boundaries — the wave of 2026-11-07

**Four decisions, taken under the owner's W50 directive of 2026-11-07.** They are the boundaries the
local demonstrator is allowed to have, and they are recorded here because each of them changes what the
runtime does — or, in the first case, deliberately changes what it does not do.

### OD-049 — The deterministic extraction path stays the critical path, and the teacher stays a teacher

- **kind**: owner
- **the date**: 2026-11-07
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/engineering/W50-LOCAL-MODEL.md` (the measurement and its verdict); the wave report `docs-v2/engineering/W50-STATUS.md`
- **the effect on the runtime**: none — the teacher stays **off**. The acquisition path stays deterministic, and the answering path makes no model call at all
- **the evidence or the scope of the approval**: the measurement of 2026-11-07 on this machine. The teacher — Qwen2.5-1.5B-Instruct-GGUF, revision `91cad51170dc346986eccefdc2dd33a9da36ead9`, Apache-2.0, sha256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`, Q4_K_M, run with llama.cpp on the RTX 5070 Ti — was **invoked 0 times** during the deterministic acquisition, measured with a counter that a known model call was proved to increment. And it is **not better at extraction**: 72.7 % of its propositions are verbatim in the source, against the deterministic path's 100 %. The teacher therefore stays a teacher: it may be offered behind an adapter, **off by default**, and anything it produces must pass the same verbatim-and-located evidence rule the deterministic path passes at 100 %.

### OD-050 — The acquisition boundary is a pre-cached, versioned, licensed pool that ships with the application

- **kind**: owner
- **the date**: 2026-11-07
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/engineering/W50-STATUS.md`; the pool's manifest `matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json`
- **the effect on the runtime**: the learning cycle reads a **classpath catalogue**, takes at most three documents per question, and reports which documents it considered, which it acquired, under which licence, and what it skipped. **No request leaves the machine.** There is no web search, and the word "research" in the panel's name does not imply one
- **the evidence or the scope of the approval**: the run of 2026-11-07. The cycle acquired **3** pool documents (continental drift, mid-ocean ridge, subduction) and **324 claims**, each document carrying its licence, its source URL, its `oldid` revision and its access date; the report states the boundary in the words the demonstration's answer key requires — *a pre-cached, versioned and licensed pool that ships with the application, no request leaves the machine*

### OD-051 — A user assertion is an input to evaluate and is never promoted into the store

- **kind**: owner
- **the date**: 2026-11-07
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `docs-v2/engineering/W50-STATUS.md`; `matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java`; the route in `matrix-core/src/main/java/io/matrix/knowledge/LearningCycleResource.java`
- **the effect on the runtime**: the assessment route **never writes**. It returns a verdict — supported, contradicted, unsupported or unresolved — together with the disputed premise, the contradicting claim and its source, and `promoted=false`. No claim, no evidence and no status is created by a check
- **the evidence or the scope of the approval**: the demonstration's turn 3. The verdict was **CONTRADICTED**, the disputed premise was named, the contradiction pointed at the claim that carries it and at the source that claim came from, and the store held no trace of the assertion. An assertion is an input to evaluate, never a confirmation to store

### OD-052 — The corpus is one bounded domain, with the per-file licence, revision and sha256 in a verified manifest

- **kind**: owner
- **the date**: 2026-11-07
- **the status**: ACCEPTED
- **the successor**: —
- **the document that holds it**: `matrix-core/src/main/resources/corpus/plate-tectonics/corpus-manifest.json`; `matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json`
- **the effect on the runtime**: the corpus is **one bounded domain** — plate tectonics — and every file is listed in a manifest with its licence, its licence URL, its canonical URL, its access date, its revision and the sha256 of the imported bytes. The import **verifies the manifest** before it reads a file, so a corpus file that is not the recorded bytes is refused. The seed import produced **348 claims**, and the bounded cycle added **324** more
- **the evidence or the scope of the approval**: the seed corpus is two documents — USGS *This Dynamic Earth* (US public domain) and Wikipedia *Plate tectonics* (CC BY-SA 4.0, oldid 1381934246) — and the research pool is seven versioned Wikipedia snapshots with their `oldid`s recorded. **This decision does not answer the licence question the corpus raises**: the interaction between the repository's own AGPL-3.0 licence and the share-alike of the quoted CC BY-SA 4.0 texts is **Q-W50-01**, and it is the owner's

## 4. The duplicate-identifier problem

The repository has one, and this register does not hide it. There are three distinguishable
kinds of duplication, and only the first is a real collision.

**(a) A genuine collision — the same id defined twice for different subjects.**

| id | the two claimants | note |
|---|---|---|
| `SPEC-002` | `docs-v2/specifications/SPEC-002-boolean-compute-layer.md` and `docs-v2/specifications/SPEC-002-quantum-bir-mps.md` | two different specifications. The repository itself already resolved it inside `docs-v2/specifications/INDEX.md:9-10`, where the second is called **`SPEC-002q`**. That is the precedent this register follows |
| `H-007` | `docs-v2/research/protocols/H-007-memory-stack.md` (the memory-stack hypothesis) and `matrix-core/docs/H-007-progress.md` (an RRF weight-tuning progress report) | two different subjects under one hypothesis id; the progress report borrows the id without claiming the hypothesis |

**(b) An announcement or citation of an id defined elsewhere.** These are not collisions, but
they are why a naive id scan reports dozens of duplicates: `docs-v2/research/summaries/INDEX-W20.md`
tabulates `DESIGN-01`…`DESIGN-12`; `docs-v2/research/W80-FINAL-SYNTHESIS-REPORT.md` announces
`DESIGN-60`…`DESIGN-62`; `docs-v2/waves/WAL-022-wave-15-17-l4-sensorimotor-design-58-v3-2026-09-1.md` announces `DESIGN-58` v3;
`docs-v2/waves/WAL-347.md` announces `SPEC-013`; `docs-v2/waves/WAL-350.md` announces
`SPEC-014`; `docs-v2/vision/FINALSUMMARY.md` tabulates `H-002`, `H-003`, `H-006`, `H-010`;
`docs-v2/specifications/INDEX.md` tabulates `SPEC-000`…`SPEC-012`; the
`docs-v2/research/reports/EXP-MATRIX.*` files and the `docs-v2/waves/WAL-0*.md` files
both announce `H-043`, `H-044`, `H-045`, `H-050`.

**(c) The decision-number space, which is the worst of them.** The bare token `D1` — and the
bare token `D-001` — mean several different decisions in several different documents:

| where | what "D1" / "D-001" means |
|---|---|
| `docs-v2/engineering/W45-DECISION-LOG.md` (D1…D11) | the disposition is a new concept (CD-001) |
| `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 1…4) | the canonical entry point per application (OD-001) |
| `docs-v2/quality/OPEN-DECISIONS.md` (D1…D8) | the BitNet failures: red or environment-blocked (OD-006) |
| `docs-v2/engineering/RECON-W42-DECISIONS.md` (D-001…D-017) | the benchmark is built before any improvement is attempted (CD-W42-01) |
| `docs-v2/vision/CONCEPT-CORRECTIONS.md` (D-001…D-011) | the atomic computational unit is the BirUnit (ND-001) |

and `docs-v2/engineering/RECON-W44-BACKLOG.md` §5 numbers its five owner questions `1`…`5`.
The W46 directive adds a sixth meaning: its "D1–D4" are the four architecture decisions of
OD-001…OD-004, which `docs-v2/engineering/W46-OWNER-DECISION-PACKET.md` §0 resolves.
**So `D-001` alone means two different decisions in two different documents, and `D1` alone
means three.**

**The rule this register uses.**

1. An id is minted once per **decision**, never per document.
2. A source document's own id is kept **only if it is unique in the repository**. All eight
   ADR ids are unique as definitions (each is cited several times, which is not a collision),
   so they are kept verbatim.
3. Everything else gets a prefixed id: `OD-` for an owner decision, `CD-` for a coordinator
   decision, `PR-` for a proposal, `ND-` for a normative decision. **A bare `D` is never used
   in this register**, because it already means five things across five documents.
   The W42 log's own `D-001`…`D-017` are kept as `CD-W42-01`…`CD-W42-11` and `OD-019`…`OD-024`,
   with the wave in the id, so that they cannot be confused with the W45 log's `D1`…`D11`.
4. Where a source id collides, the register keeps the id and adds a suffix that names the
   other claimant in the row's note. No id in this register required a suffix, because no
   decision was found in two documents at once.
5. The ADR series keeps its own numbering. A decision that is announced in several documents
   (PR-003 and OD-012 are one decision; CD-001 and the W45.0 integrity design are one
   decision) is registered **once**, under the document that holds the decision text.
6. **The kind is taken from the document, not from the wave.** The W42 log is a single file
   under one owner directive, but six of its seventeen entries say in their own text
   "Owner decision" or "The owner decided this on 2026-11-02" — D-011, D-012, D-014, D-015,
   D-016 and D-017. Those are registered as owner decisions (OD-019…OD-024); the other
   eleven are coordinator decisions under the directive (CD-W42-01…CD-W42-11). The wave a
   decision belongs to does not decide who took it.

---

## 5. What is NOT in this register

| what | where it lives instead |
|---|---|
| capability claims and their evidence | `docs-v2/architecture/CAPABILITY-REGISTRY.md` and `docs-v2/engineering/W44-CAPABILITY-REGISTRY.md` — note that both predate W45.0/W45.1/W45.2 and therefore describe a system that no longer exists; `docs-v2/engineering/W46-OWNER-DECISION-PACKET.md` and `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` (Q-W46-10) carry that finding |
| research findings, hypotheses, experiment reports | `docs-v2/research/` — the registers are `docs-v2/research/HYPOTHESES.md` and `docs-v2/research/HYPOTHESES-NEW.md`, the protocol is `docs-v2/research/PROTOCOL.md`, and the reports are `docs-v2/research/reports/` |
| the tasks and the plan | `docs-v2/governance/ROADMAP.md` — it supersedes the role of `docs-v2/engineering/PLAN.md`, whose last recorded wave is W44 and which therefore does not contain W45, W45.1 or W45.2 |
| open questions | `docs-v2/governance/OPEN-QUESTIONS.md` (the register of record) with `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` as its machine-readable snapshot; the older operator ledger is `docs-v2/quality/OPEN-DECISIONS.md`, which lists D1…D8 as CLOSED in a section at the top while the bodies of the same D1…D8 below still read as open — the CLOSED section is the later state, and this register follows it |
| the current state of the project | `docs-v2/governance/PROJECT-STATE.md` (a snapshot at one commit, replaced rather than appended to) |
| the status of a document, and who may change it | `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` |
| the risks | `docs-v2/governance/RISK-REGISTER.md`, with `docs-v2/engineering/W46-RISK-REGISTER.csv` as its machine-readable snapshot |
| the wave-by-wave history | `docs-v2/waves/` (320 files) and `wal/SESSION_WAL.md` |
| the release caveats and the shipped state | `docs-v2/releases/` and `docs-v2/engineering/RELEASE-NOTES.md` |

---

## 6. How to read this register

* **A row points at a document. It does not replace it.** The one-line decision in the second
  column is a routing hint; the argument, the rejected alternatives, the cost and the limits
  live in the document named in the seventh column.
* **An `ACCEPTED` row's document is the authority.** If a reader disagrees with the row, the
  row is wrong; the document decides.
* **A `SUPERSEDED` row's successor is the authority.** Follow the sixth column, then read the
  successor's document. A superseded row is kept because the history of a decision that was
  taken, changed and taken again is part of the architecture.
* **A `PROPOSED` row is not a decision.** It is written down and not ruled on. PR-002 and
  PR-006 are the two that a reader is most likely to mistake for something in force.
* **An `UNKNOWN` row is a question, not an oversight.** OD-015…OD-018 are owner questions that
  were put and never answered; the register records the question and the fact that no answer
  exists, rather than inventing one.
* **An ADR stops being authoritative when its assumptions are disproved — and it is then
  annotated, not rewritten.** The W45.2 example: `ADR-2026-10-26-002` and `ADR-W44-01` were
  both written before the bytecode was examined, and the bytecode contradicts the way they
  read. Rather than edit either document, the wave added a one-line scope annotation to each
  header, dated 2026-11-04, naming `ADR-W45.2-01` as the reconciling document. The bodies
  remain the historical decision. `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §4 states
  this rule: the two historical ADRs were not rewritten, they carry a status annotation
  pointing at the superseding document.
* **The kind column tells you who decided.** `owner` — the repository owner, by a directive, a
  questionnaire answer or an operator ruling. `coordinator` — an agent decision taken under
  an owner directive; the wave document is the authority, not the coordinator's preference.
  `normative` — a standing rule whose document names no decider. `proposal` — written down,
  not decided.
* **The register is dated and bounded.** It describes the decisions recorded in the repository
  at `71b5b5c6` on 2026-11-07. A decision taken afterwards is not here until it is indexed.
