# W42 — Architecture Reality Map

**RECON-W42.0** · 2026-11-01 · owner directive Part II §3.3
**Purpose:** show the architecture as it **runs**, not as it is documented.
Every chain below was read line by line from the tree; the live chains were
also observed running.

---

## 1. The two running topologies

### 1.1 Product A — the Learning Workbench (the W42 deliverable's base)

```
                    HTTP :9091
                        │
        ┌───────────────┴────────────────┐
        │  Quarkus / JAX-RS              │  static assets
        │  LearningWorkbenchResource     │  META-INF/resources/workbench/index.html
        │  @Path("/v1/workbench")        │  9 tabs, 10 buttons
        │  :42                           │
        └───┬───────────────────────┬────┘
            │ @Inject                │ fetch('/v1/workbench/...')
            ▼                        │
   KnowledgeWorkbench  @ApplicationScoped
   :31-32                     │
            │ store() :87      │
            ▼                  │
   KnowledgeStore (SQLite, WAL, synchronous=FULL)
   :57 schema v8 · 16 tables
            │
            ├─► KnowledgeImporter   (curated pack JSON → snapshot)
            ├─► KnowledgeRetriever  (IDF coverage → Answer + SupportScore)
            ├─► ReasoningEngine     (8 stages → Result)
            │     ├─ EntailmentChecker (exact, 16-atom budget)
            │     └─ ContradictionDetector (in memory only)
            ├─► LearningStateEvaluator → CapabilityEvaluator
            ├─► TeachingComposer → TeachBackEvaluator | TeachBackExperiment
            ├─► AcceptanceModel (Tsetlin + WiSARD, ≥5 held-out items)
            ├─► QuestionConsolidator (tombstone resolution of asked questions)
            └─► GoalManager (persistent goals, schema v8)
```

**36 endpoints. All of them perform the real operation; none returns a
placeholder.** Ten have no UI caller (§4).

### 1.2 Product B — the gateway brain

```
  java -jar matrix-api-gateway (JDK HttpServer, NOT Quarkus)
        MinimalHttpServer.main :1564
                │
                ├─ new ProductionBrainClient(hdcStore, episodicLog, null) :183
                │        └─ new TrueMindCycle(new Random(42L), hdcStore) :129-130
                │                 ├─ ReflexEngine, HarmfulIntentClassifier
                │                 ├─ SignalStage, SaliencyEngine
                │                 ├─ BirBrainCycle.cycle :149
                │                 │     ├─ HdcBrain.forward  → HdcEncoding.hamming
                │                 │     └─ CodebookMemory.query → THE SERVED REPLY :182-184
                │                 ├─ ArithmeticStage → PlanningStage → MctsTree (constant reward)
                │                 ├─ AnalogyStage, RelationalReasoningStage, BilingualFactLookup
                │                 ├─ BirInferenceStage, HdcRetrievalStage, TsetlinStage
                │                 ├─ SafetyMonitor, ConsistencyChecker, LieDetector
                │                 └─ composeReply / composeConfidence :441-492
                ├─ new RealSleepScheduler :258   → SleepCycle + ConsolidationCycle + RuleInduction
                ├─ new RealInboxWatcher :286
                ├─ new RealGpuKernelEngine :277
                └─ new TrueDistillationFactory :220
```

**The brain's own reply is discarded** —
`TrueMindCycle:422-425`: *"The brain's own reply (`core.reply()`) is for
trace/audit only, not the user-facing answer."* The served answer is
composed by `composeReply` from the stage results, with the HDC codebook's
retrieved label as the primary input.

**Product B requires `matrix-core`'s JAR on disk**
(`ProductionBrainClient:48-61` searches a candidate list); if it is absent
the gateway answers 503 (`ProductionBrainClient:143-146`).

---

## 2. The data flow of a question (Product A)

```
question ──► KnowledgeRetriever.answer
               ├─ TextFeatures.tokens(question)          ← English stopwords, English fold
               ├─ IDF over the claims' folded terms
               ├─ score each claim: matched IDF / total question IDF
               ├─ keep claims with coverage ≥ 0.25        ← DEFAULT_MINIMUM_COVERAGE
               ├─ if none survive → ABSTAIN (unmatchedTerms named)
               └─ else → Answer(statement, supportScore, SupportScore,
                                 concepts, claims, sources, limitations, gaps)
                        │
                        ├─► REST ──► the UI's answer panel
                        └─► QuestionConsolidator (canonical key, tombstone resolution)
```

**The refusal threshold is the single most consequential number in the
product.** It is `0.25`, and the measured abstention quality is **0.400**:
the system answers six of the ten questions it cannot support. W42.1 must
treat this as an epistemic defect to be fixed and measured, not as a
threshold to be tuned blindly.

---

## 3. The learning flow (Product A) — and where it stops

```
assessment item (static JSON)  ──►  POST /assess  ──►  AssessmentGrader
                                                              │
                                                              ▼
                                                    assessment_outcome (row)
                                                              │
                    ┌─────────────────────────────────────────┼──────────────────┐
                    ▼                                         ▼                  ▼
        LearningStateEvaluator                    AcceptanceModel          MetricsReport
        (per dimension, recomputed)               (Tsetlin + WiSARD)       (rates + NOT MEASURED)
                    │                                         │
                    ▼                                         ▼
        CapabilityEvaluator (the 7-stage ladder)     LearnedRuleRegistrar
                    │                                         │
                    ▼                                         ▼
              recomputed label                  a NEW CLAIM + SOURCE + EVIDENCE
              (never stored)                    (via store.applyChanges)
```

**It stops here.** There is:

- **no stage stored** — `KnowledgeWorkbench:202-219` recomputes every state
  on every request from `store().outcomes(workspaceId)`;
- **no transition recorded** — no table, no audit;
- **no planner** — nothing selects the next action; the subtask list arrives
  in the HTTP request body;
- **no exercise generator** — the items are static JSON and the **caller**
  supplies the item id;
- **no delayed-retention schedule** — no `dueAt`/`reviewAt` column, and
  `LearningDimension.RETENTION` has no dataset item at all;
- **no ingestion** — only a curated pack JSON can enter, and
  `KnowledgeRefiner` cannot create a concept.

**What exists and is unused:** `LearningGoal.remainingSubtasks()`,
`LearningGoal.blockedSubtasks()`, `LearningGoal.hasDependencyCycle()`,
`KnowledgeGapDetector.missingPrerequisites()`, the `prerequisite` table with
its acyclicity check, and `CurriculumOrderer` in `io.matrix.ktopo` — which
the knowledge subsystem never references.

---

## 4. The application surface, exhaustively

36 REST endpoints; 10 have no UI caller.

| Missing from the UI | Why it matters |
|---|---|
| `GET /workspaces` | the UI hardcodes the two workspace ids instead of asking |
| `GET /workspaces/{id}/evidence` | the provenance view has no screen |
| `GET /workspaces/{id}/goals/resumable` | **the restart-recovery surface**, and the Persistence tab — which is the tab about recovery — does not call it |
| `POST /workspaces/{id}/teach-back-experiment` | **the rebuilt experiment that can fail** is unreachable from the interface; the UI still runs the W40 containment measurement |
| `GET /workspaces/{id}/outcomes` | the learner cannot see their own history |
| `GET /workspaces/{id}/export` | the mission's §10.1 item 18 (export a knowledge package) has no UI |
| `POST /workspaces/{id}/transfer?to=` | the mission's §10.1 item 14 (share with another instance) has no UI |
| `GET /workspaces/{id}/capability/{conceptId}` | per-concept mastery has no UI |
| `POST /workspaces/{id}/learned-rule` | promoting a measured rule has no UI |
| `GET /workspaces/{id}/acceptance-model` | the Tsetlin/WiSARD measurement has no UI |

**UI defects found:** the Dashboard is empty on first paint (`index.html:938`
calls only `loadStatus()`); the nav handler has no branch for `dashboard`,
`ask` or `persistence`; `consolidate-questions` uses `class="btn"`, which the
stylesheet does not define (`:247`); the workspace `<select>` hardcodes two
ids.

**There is no button without a working action.** That is a real and unusual
property of this codebase and it is worth preserving.

---

## 5. The persistence architecture

```
matrix-core/data/knowledge/workbench.sqlite        278 528 B
matrix-core/data/knowledge/workbench.sqlite-wal    737 512 B   ← 2.6× the database file
matrix-core/data/knowledge/workbench.sqlite-shm     32 768 B
```

`PRAGMA journal_mode=WAL`, `synchronous=FULL`, `foreign_keys=ON`
(`KnowledgeStore:82-84`). The four multi-statement mutations are
transactional (import `:512`, applyChanges `:851`, recordDerivation `:1250`,
recordGoalWithSubtasks `:1454`); everything else is autocommit.

**Every committed statement is durable across `kill -9`** — verified live:
the schema version, the counts, the goal, its completed subtask, its spent
budget, its checkpoint, the assessment outcome and the derivation's premise
order were all intact after a `SIGKILL` and a restart.

**⚠ One defect with a proven failure mode.** `KnowledgeWorkbench:727-728`
publishes in the Persistence tab:

> *"none: the store is a single SQLite file. A copy of workbench.sqlite is
> the backup, and taking one is the operator's job."*

That is false for a WAL-mode store. Proven by experiment: a copy of
`workbench.sqlite` **alone** loses every `assessment_outcome` and every
`asked_question` record — the records the same screen calls *"the evidence
that something was learned"* — because they live in the `-wal` file. A copy
of the three files keeps them. **This is the one defect in the W41 wave that
can lose user data, and it is a one-line fix.**

---

## 6. The configuration surface

50 distinct `matrix.*` `@ConfigProperty` keys exist across the tree; three
were in `application.properties` — one of them misspelled (`atrix.knowledge.assessment`),
**fixed in `b724c79d`**.

**A hazard the properties file does not mention:** the store's path and the
pack paths are **relative classpath paths**, so the store's location depends
on the working directory. `quarkusRun` sets the CWD to `matrix-core/`, so
the app uses `matrix-core/data/knowledge/workbench.sqlite`. Running the same
JAR from the repository root creates a **second, empty** store at
`./data/knowledge/workbench.sqlite`. The Persistence screen does not say so.

**External services are all optional, and the evidence is a live process:**

| Service | Configured | Required | Evidence |
|---|---|---|---|
| PostgreSQL | yes (`:34-37`) | **no** | the reactive pool is created lazily; the workbench never appends an event; zero PG errors in the live log |
| Redis | `redis.uri` (`:14`), raw Lettuce, no Quarkus extension | **no** | Lettuce connects lazily; zero Redis errors |
| Kafka | `matrix.kafka.bootstrap.servers` | **no** | `BatchKafkaJournal.onStart` constructs a producer, which connects on a background thread; **2 119 connection failures in the live log and the app still serves requests** — the cost is one thread and log spam |
| OTLP | `:17-18` | **no** | the exporter logs failures continuously; the app serves normally |
| An external LLM API | **not configured at all** | **no** | `OpenAIChatResource` is an OpenAI-**compatible server** MATRIX *serves*; the only external model key in the tree is the optional `HF_API_KEY` |

**No API key material exists anywhere in the repository's configuration.**
Every secret is an environment variable with a safe default.

---

## 7. The three knowledge systems, drawn

```
        ┌──────────────────────────────────────────────┐
        │  Product A — the Learning Workbench          │
        │  KnowledgeStore (SQLite, provenance, v8)      │──► the retriever, the
        │  • 16 tables • atomic imports • WAL           │    evaluator, the engine
        └──────────────────────────────────────────────┘
                        ╳  no connection
        ┌──────────────────────────┐   ┌──────────────────────────────┐
        │ SimpleKnowledgeBase      │   │ KnowledgeGraphStore          │
        │ in-memory .md/.txt       │   │ in-memory entity/relation    │
        │ used by the brain and    │   │ used by MCP and the RAG      │
        │ the learning loop        │   │ 22 tests, no production path │
        └──────────────────────────┘   └──────────────────────────────┘
```

**The mission's §4.1 forbids introducing a second competing knowledge store.
Two already exist, and the one with the provenance model is the one the brain
cannot see.** W42 must not add a fourth. The graph store's machinery — the
entity/relation model, the community detection, the traversal — is worth
keeping as a **read-only projection over `KnowledgeStore`**, which is what
the mission's §10.2 "Knowledge Graph" screen needs.

---

## 8. The architecture the mission asks for, mapped onto what exists

| Mission's functional system | What exists | What is missing |
|---|---|---|
| **§4.1 Knowledge Intelligence** | `KnowledgeStore` + provenance + representations + revisions + prerequisites + contradictions (unwritten) | document ingestion; the graph projection; contradiction persistence |
| **§4.2 Reasoning Intelligence** | `ReasoningEngine` (8 stages), `EntailmentChecker` (exact, bounded) | induction, abduction, analogy, counterfactual, temporal, constraint reasoning; **no derivation is persisted**; the flagship domain has 0 rules |
| **§4.3 Learning Intelligence** | `LearningGoal`/`GoalManager`, `AcceptanceModel`, `TeachBackExperiment` | a planner, an exercise generator, delayed retention, consolidation, per-item mastery |
| **§4.4 Dialog Intelligence** | `TextNormalizer` (whitespace only, **test-only**), `BpeTokenizer` (1 production site) | language identification, morphology, lemmatisation, typo handling, intent detection, reference resolution, clarification, multilingual retrieval |
| **§4.5 Memory Intelligence** | `HierarchicalMemory` (5 levels), `PersistentHdcStore`, `EpisodicLog`, `TombstoneService` | differentiated memory types, consolidation, reversible forgetting, an associative index over the knowledge |
| **§4.6 Planning Intelligence** | `TaskScheduler` (unreachable), `MctsTree` (constant reward), `LearningGoal` (records only) | a planner over prerequisites and gaps; a bounded action executor; a reward signal that means something |
| **§4.7 Meta Intelligence** | `MetricsReport` (with an explicit "NOT measured" list), `CapabilityState`, `AcceptanceModel` | a per-capability record with datasets, held-out sets, regression history, calibration status, and a last-validation date |
| **§4.8 Distributed Intelligence** | `io.matrix.federation.liquid.*` (35 classes), `DynamicModulatorRegistry`, `KnowledgeExchangeProtocol` | a persistent peer registry with heartbeat; trust metadata on exchanged knowledge; a local-first guarantee |

---

## 9. The honest summary of the map

The repository contains a **careful, deterministic, honestly self-documenting
knowledge store with a measurement harness** attached to it, and a
**well-tested island of cognitive code** that no running product reaches.

Neither of those is a failure. The store is exactly the substrate the
mission's W42 needs to build on: atomic imports, provenance on every claim, a
versioned schema, a real restart guarantee, and a refusal mechanism that
works when it triggers. The island is the reservoir of mechanisms the
mission's §11 says to reuse rather than replace — **provided each one is
wired and measured before it is counted**.

The two things the map says are missing everywhere: **something decides what
to do next**, and **something can read a document**. Those are W42.1 and
W42.2, and they are the next two waves.
