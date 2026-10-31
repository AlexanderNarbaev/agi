# W40.0 — Reconnaissance: what MATRIX has, what it lost, and what the learning application is built from

**RECON-W40** · 2026-10-31 · owner directive §43

This is the first deliverable of the epistemic-learning mission. It answers one
question before any code is written: **what should be reused, what should be
corrected, what should be combined, and what should be deferred?**

Everything here was verified against the tree at `develop` @ `6763bdaa`. Where
a claim is about code, the path is given. Where it is about history, the
commit is given.

---

## 1. The headline

Three findings decide the architecture.

**1. There is already a working knowledge-acquisition path, and it is not the
one that looks like it.** `QaCorpusIndex` (`matrix-core/src/main/java/io/matrix/api/QaCorpusIndex.java`)
is a live, disk-backed, IDF-weighted inverted index over Q&A pairs, exposed as
`POST /v1/qa/learn` (`QaLearnResource`). It works. The machinery that *looks*
like the brain — `io.matrix.consciousness`, `ConsciousBrain`,
`CognitiveProcessor`, `CognitiveGenesisProfile` — has **zero production
callers** (`grep -rn "new ConsciousBrain" matrix-core/src/main matrix-api-gateway/src/main
matrix-brain-runtime/src/main` → no hits). `CognitiveGenesisProfile` is
thirteen unlabelled doubles with no text, no identifier and no provenance: it
is a telemetry vector and cannot hold knowledge.

**2. The one thing that looks like supervised language learning is not
learning at all.** `AgentBrainService.generateFromBir()`
(`matrix-core/src/main/java/io/matrix/agent/AgentBrainService.java:404-508`)
trains on **constant-`true` labels** (L446), never reads the corpus's
answers (L442-443), and encodes text with an FNV hash over the first 64
characters (L488-496). Its output is one bit, rendered as ASCII by
`decodeBits` (L498-508) — so it can only ever return `""` or `"!"`. The
caller at `OpenAIChatResource.java:397` requires `length() >= 8`, so **the
branch is unreachable**. It is a corpus fingerprinting exercise that cannot
affect a single response.

**3. The repository already knows what it lost.** `docs-v2/quality/lost-capability-tests/`,
`docs-v2/quality/OPEN-DECISIONS.md` (D2) and
`docs-v2/releases/CAVEATS-v17.6.0-mind.md` §4 record three capabilities that
exist on branches and not in `develop`. One of them — *tombstone-based
forgetting* — is exactly the mechanism a learning application needs, and
another — *turning experience into knowledge* — is the mission's central loop.

---

## 2. Architecture lineage

`develop` is a strict superset of every other branch in the repository
(`git rev-list --count develop..<branch>` = 0 for `main`, `release/v1.0`,
`safe-main`, `feature/t-01…t-10`, `backup-main-pre-merge-20260921-154355`
and `analysis-of-project-goal-implementation-5df35`). The branches that *do*
carry commits not in `develop` are the `feature/mind-w*` series — and their
**names trail their content by one wave**, because each was cut before its
twin was squash-merged. The content is largely present in `develop` anyway;
the audit compared blob SHAs file by file.

| Branch | Capability it carries | Verdict |
|---|---|---|
| `mind-w1-cognitive-orchestration` | MIND-W2 persistent mind: durable HDC store, retrieval stages | IMPLEMENTED_IN_ANOTHER_FORM — develop's copies are evolved supersets; the `io/matrix/brain/*` family was relocated to `matrix-api-gateway/.../llm_quarantine/` by `1520e9b7` because it reached ONNX inside a decision path (CONSTITUTION Art. I) — a correct move, not a loss |
| `mind-w2-persistent-mind` | MIND-W3 sleep consolidation: episodic replay → promote recurring patterns to canonical ids → paraphrase collapse → **prune by tombstone, not deletion** → dream report | **LOST_OR_NOT_INTEGRATED** — develop's two `ConsolidationCycle` classes do something else (`io/matrix/memory` moves M2→M1→M0 on an access counter; `io/matrix/lifecycle` drains route backlogs). **Sleep reorganises memory; it never turns experience into knowledge.** Eight tests survive uncompiled at `docs-v2/quality/lost-capability-tests/SleepAndConsolidationIntegrationTest.consolidation.java.txt` |
| `mind-w3-sleep-consolidation` | MIND-W4 autonomy: goals with **progress**, audit trail; inbox that becomes knowledge | **LOST_OR_NOT_INTEGRATED** — the inbox half is superseded by `RealInboxWatcher`; the goal-progress half is absent (`develop`'s `Goal(int id, String description, Status, int priority)` has no progress) and is recorded in develop's own `docs-v2/releases/CAVEATS-v17.6.0-mind.md:183` as NEEDS-OWNER |
| `mind-w4`, `mind-w5` | MIND-W5 distillation factory, MIND-W6 GPU | SUPERSEDED — develop rewrote the ledger as an 11-field record and added a real executor; the GPU files are byte-identical |
| `mind-w6`, `mind-w7` (three names) | GPU kernel, credit ledger, audit chain, licence, federation registry | PRESERVED_AND_ACTIVE — byte-identical to develop |
| `true-w0`…`true-w13` | ground-truth hygiene, real cores, persistent mind, real sleep, autonomy, distillation, audit, launch kit, sparse HDC | PRESERVED_AND_ACTIVE / IMPLEMENTED_IN_ANOTHER_FORM — every file present in develop, most evolved |
| `true-w6-w7-gpu-multilingual` | generic GPU operation dispatch (`GpuOperation` protobuf) | **LOST_OR_NOT_INTEGRATED** — the schema survived (`proto/matrix_federation_v1.proto` → generated `GpuOperation.java`), the dispatcher did not. develop exposes only `runBitCosine` |
| `feature/liquid-federation-dynamic-modulators` | federation protocol, modulator registry, WAL wave-split tooling | PRESERVED_AND_ACTIVE — all ten artefacts in develop, four byte-identical, and the schema is LIVE (`NeuroModulatorHub`, `ModulatorStage`) |
| `analysis-of-project-goal-implementation-5df35` | the whole pre-v2 doc archive (315 files) and the prototype→production transformation plan | PRESERVED_AND_ACTIVE — renamed to `docs-v2/_raw/**`; `WAL.md` became `wal/GLOBAL_WAL.md` + `wal/SESSION_WAL.md` |

### The four ideas worth re-examining for a learning application

1. **Tombstone-based forgetting** (`ConsolidationCycle` @ `7c4a1dda`) — pruning
   with tombstones, not deletion, so a retracted fact leaves a record that it
   was once believed. `develop`'s `PersistentHdcStore` has no
   tombstone/forget/prune semantics at all. *This is the memory half of the
   mission's §13 (conceptual change).*
2. **Episodic → knowledge promotion** (same file) — replaying experience,
   counting pattern recurrence, promoting a pattern to a canonical
   identifier so that paraphrases collapse on retrieval, deduplicating against
   what is already known. *This is the mechanism the mission's §20 calls
   `MemoryConsolidator`, and it is the one piece of the whole history with no
   functional equivalent in develop.*
3. **Goal progress** (`GoalTracker` @ `8db97956`) — `progress: 0.0–1.0` with
   timestamps and a before-the-fact audit trail. *A learner that cannot say
   "I am 60 % through this" cannot steer its effort or resume a partial task —
   and the mission's §10 demands multidimensional, observable state.*
4. **The `LearningLoop` seam** — `io.matrix.learning.LearningLoop`
   (`CycleResult talk(String); int learn(); void close();`), the only place in
   the repository where "ask → answer → improve" is expressed as a contract.
   *It is the natural interface for the mission's §20 loop.*

### A live defect the audit found

`DistillationLedger` — the ledger that is the Article VI evidence that
distillation improves the mind — throws `NullPointerException` on a null map
value and `NumberFormatException` on an empty field in a corrupt line, i.e.
it does not do the `ledger_handles_corrupt_lines_gracefully` its own test
name promises (`docs-v2/releases/CAVEATS-v17.6.0-mind.md` §5). Recorded here;
not fixed in this wave.

---

## 3. What exists, and what each thing is actually for

| Component | What it really is | Persistence | Production path | Verdict for the learning app |
|---|---|---|---|---|
| `QaCorpusIndex` + `QaLearnResource` | IDF-weighted inverted index over Q&A pairs with trigram query expansion | JSON corpus on disk, appended in place | YES — `POST /v1/qa/learn` on matrix-core | **Reuse** — it is the acquisition substrate that works today |
| `HierarchicalMemory` / `PersistentHierarchicalMemory` | 5-level store of chat turns; "hierarchical" means levels of abstraction, not a tree | JSONL, full-file rewrite, **no temp+rename**, and `restore()` loses ids, importance, access counts and timestamps | YES — CDI on every matrix-core boot | **Extend** — it is the write-then-recall store; its persistence fidelity must be fixed before anything is built on it |
| `SqliteMemoryBackend` | plain JDBC CRUD over `MemoryEntry` rows | one SQLite file, `CREATE TABLE IF NOT EXISTS` only — **no migration story**, two integrity bugs (a duplicated tag throws, an out-of-range level throws AIOOBE) | YES, but only under the gateway's `MATRIX_MODE=production` | **Replace for knowledge** — the new `KnowledgeStore` is a separate, versioned schema; this one stays for chat memory |
| `ConsciousBrain` + `CognitiveProcessor` + `CognitiveGenesisProfile` | a brain cycle over `float[]` observations producing a 13-number metric vector | none | **NO** — tests only | **Do not build on** — it is a research sandbox and its record cannot hold knowledge |
| `CognitiveErrorStream` + `ErrorDrivenLearner` | a bounded, hashable error ring buffer, and anti-Hebbian forgetting of the binding that caused an error | none | NO | **Reuse the shape** — a durable error log with a stable id is exactly what a learner needs; the bit-vector mechanism does not transfer to text |
| `BooleanRag` / `IndexedBooleanRag` / `HybridBooleanRag` | boolean-vector retrieval; `HybridBooleanRag` has weighted RRF fusion, knee-point pruning, a refusal signal and a hallucination guard | none (an unused binary persistence exists) | NO — tests and a benchmark only; `IndexedBooleanRag.init()` is never called | **Reuse the policy, not the encoder** — the fusion and the refusal signal are production-grade; the sparse branch searches for the literal strings `"bit_0 bit_1 …"` and the embedding branch ignores the query except for `query[0]` |
| `TsetlinTrainer` | a faithful Granmo Tsetlin machine: 1..20 input bits, seeded, deterministic, distillable to an exact BIR clause set | none (the distilled artifact is persistable) | YES — `RuleInductionEngine.induce()` inside `RealSleepScheduler` | **Reuse** — the strongest learning primitive in the repository; but it must be fed explicit text features, not an FNV hash |
| `WisardProducer` | a WiSARD-style RAM network; deterministic contiguous addressing | none | **NO callers anywhere** | **Available, unwired** — the same train/classify surface as the Tsetlin and the same BIR distillation, so it is a drop-in third candidate for `RuleInductionEngine` |
| `RuleInductionEngine` | train → hold-out fidelity → pick the best → register with provenance, with the `K_MAX ≤ 20` Article II guard | registers into `BirKnowledgeBase` | YES, in production mode | **Reuse** — add learners here rather than building a new induction loop |
| `BirKnowledgeBase` | a durable append-only store of learned boolean rules with contradiction quarantine and provenance | NDJSON | YES, in production mode | **Reuse** — the right target format for anything a Tsetlin or WiSARD learns |
| `PersistentMind` + `data/mind/*` | a three-tier (episodic/semantic/procedural) durable mind | SQLite + NDJSON (HDC, episodic, BIR) | YES, in production mode | **Reuse the directory convention**, not the code — it is the right shape for a local mind |

### Persistence mechanisms that are on a path a local user can reach

Without `MATRIX_MODE=production`: `models/training_data/qa_pairs.json`,
`data/hierarchical_memory.jsonl`, `data/chain_state.json`,
`data/conversations/*.ndjson`. Everything richer — SQLite, HDC store, episodic
log, BIR rules — sits behind the gateway's production switch in `data/mind/`.
Postgres is **configured** but nothing writes to it
(`PostgresTombstoneStorage` and `R2dbcEventJournal` have no production
caller; every real caller uses the in-memory journal).

**This is the constraint that shapes the MVP:** the learning application must
live in **matrix-core**, which starts with one command and no database, and
must not depend on the gateway's production switch.

---

## 4. The MVP architecture

### 4.1 Where it lives

**`matrix-core`**, the module that already starts with
`./gradlew :matrix-core:quarkusRun` (verified: *"Listening on:
http://0.0.0.0:9091"* in 1.4 s, **without Postgres and without Redis**), and
which serves the existing user-facing surfaces (`/v1/chat/completions`,
`/v1/qa/learn`). The learning workbench is a new package
`io.matrix.knowledge` plus one REST resource — **not** a second application
stack, and **not** the Next.js frontend (excluded from Gradle by design, a
heavy toolchain for a local single-user tool).

The interface is a REST API plus one static HTML page served from
`META-INF/resources/` — no build step, no Node toolchain, no credentials.

### 4.2 What it is made of

```
io.matrix.knowledge
  model      Concept, Claim, Source, Evidence, Representation, Prerequisite,
             KnowledgeRevision, and the enums that give them meaning
  Ids        deterministic identifiers, so re-importing is idempotent and a
             package keeps its identity across machines
  KnowledgeStore    SQLite: versioned schema, atomic imports, provenance rules
  KnowledgeSnapshot the unit of import, export and persistence
  (next) KnowledgeImporter   reads a curated pack into a snapshot
  (next) RetrievalEngine     answers a question from claims, with sources and
                              an abstention when the evidence is insufficient
  (next) AssessmentEngine    baseline, independent post-learning assessment,
                              mastery criteria with observable regression
  (next) LearningWorkbenchResource   the REST surface
  (next) static index.html            the human surface
```

### 4.3 The decisions, and why

| Decision | Why |
|---|---|
| **SQLite** | already in the dependency set (`org.xerial:sqlite-jdbc:3.43.0.0`); one file a user can copy; transactional; works offline with no server; and the mission forbids choosing a distributed database for a single-user local prototype without justification |
| **In matrix-core** | it is the only module that starts with one command and no external service. The gateway's mind is gated behind `MATRIX_MODE=production` and its JAX-RS annotations are decorative — its real routes are hand-registered in `MinimalHttpServer.registerRoutes()` |
| **A new package, not an extension of `io.matrix.knowledge`'s existing classes** | `SimpleKnowledgeBase` is a document RAG, `KnowledgeGraphStore` is a triple graph, `CommunityDetector` is a Leiden partitioner. None of them carries claims, evidence, abstraction levels or revisions. They are not redundant with the new model and may still be used for what they are |
| **The evidence rule is enforced by the store** | "a source being available does not automatically prove every extracted claim" is a claim about the SYSTEM, so it belongs in the system: an import that asserts an established status without evidence is rejected |
| **Deterministic identifiers** | re-importing a pack must not duplicate the workspace, and an exported package must keep its identity on another machine |
| **The first domain is photosynthesis and plant gas exchange** | it has real authoritative sources, a genuine introductory→mechanistic refinement, real misconceptions, and application problems whose answers depend on conditions — which is exactly what the progressive-refinement model is for |

---

## 5. The prioritized backlog

Ordered by what unblocks the most. Each item is independently verifiable.

### Wave A — the knowledge foundation (this wave, started)
- **A1** ✅ The model records and their enums — `io.matrix.knowledge`
- **A2** ✅ `KnowledgeStore`: versioned SQLite schema, atomic imports, evidence
  enforced, cycle detection, deterministic identifiers
- **A3** ✅ The persistence contract under test: restart recovery, duplicate
  ingestion, invalid import atomicity, dangling evidence, cycles, backup and
  restore (9 tests)
- **A4** ⬜ `KnowledgeImporter`: read a curated pack (Markdown with a simple
  source header, or JSON) into a `KnowledgeSnapshot`, with provenance
- **A5** ⬜ The curated pack itself — photosynthesis, with real sources, an
  introductory and a mechanistic representation, misconceptions, application
  problems and an assessment set

### Wave B — the first learning engine
- **B1** `RetrievalEngine`: answer a question from the claims, with sources,
  scope, confidence and an explicit abstention when the evidence is
  insufficient
- **B2** `KnowledgeGapDetector`: name what the workspace does not know
- **B3** Multi-level explanation: the same question answered at
  INTRODUCTORY, INTERMEDIATE and MECHANISTIC, each with its own scope
- **B4** `PracticeExecutor` + `OutcomeEvaluator`: attempt an application
  problem, compare expected with observed, record the attempt
- **B5** `KnowledgeRefiner`: a correction produces a revision, not a deletion

### Wave C — assessment and mastery
- **C1** The baseline assessment and the held-out evaluation set
- **C2** Independent post-learning assessment, on questions never used to
  build the model
- **C3** Mastery criteria as configuration, per domain, with the exact
  criteria recorded with each transition
- **C4** Observable regression: contradictory evidence lowers a mastery state

### Wave D — the workbench application
- **D1** `LearningWorkbenchResource`: the eight workflows as REST
- **D2** The static HTML workbench: dashboard, knowledge explorer, learning
  bench, question interface, progress and mastery, persistence and recovery
- **D3** The persistence verification view: the state as read back by a
  **new process**, not the state as written
- **D4** The first-run guide and the export/import of a knowledge package

### Wave E — understanding and teaching
- **E1** Causal explanations and counterexample generation
- **E2** Transfer assessments on unseen variants
- **E3** `TeachingComposer`: a multi-level lesson for a learner with limited
  prerequisites
- **E4** Machine-to-machine teach-back: a second learner is taught and then
  independently assessed; the learner's improvement is the measurement, not
  the lesson's polish

### Wave F — cognitive integration
- **F1** `generateFromBir()`: delete or rewrite. It cannot currently affect a
  response, and the rewrite must use explicit text features, not a hash
- **F2** Feed the Tsetlin and WiSARD explicit text features through
  `RuleInductionEngine`, and register the induced rules in the knowledge
  store with provenance
- **F3** Bring back **tombstone-based forgetting** from `mind-w2-persistent-mind`
  — a retracted fact must leave a record that it was once believed
- **F4** Goal progress, if the learning loop needs a resumable objective

### Deliberately deferred
- Generic GPU operation dispatch (NEEDS-OWNER since v17.6.0; unrelated to
  learning)
- The `io.matrix.consciousness` research stack — it is not on a production
  path and its record cannot carry knowledge
- Postgres and R2DBC for the knowledge store — a distributed database for a
  single-user local prototype, without justification

---

## 6. What this wave does not claim

- The knowledge foundation is a **store for records with provenance**, not a
  learning engine. Nothing yet extracts a claim from a source, and nothing
  yet answers a question.
- The 9 store tests are **targeted** tests. They were executed; no claim is
  made about the rest of the repository's suite.
- The historical verdicts are about **content**, not commits: a branch can
  carry commits that `develop` does not, and still carry nothing `develop`
  lacks.
