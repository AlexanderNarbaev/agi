# W46.0 — ARCHITECTURE TRUTH MAP

**What MATRIX actually is on 2026-11-04, as opposed to what the documents say.**
**Method:** the current source, the current compiled classes (`javap -p -c` over the constant
pool), and the current configuration. Every claim cites a file and a line.

---

## 1. The two serving chains — and the fact that chain A is one process with three paths

```
┌───────────────────────────────────────────── CHAIN A — one Quarkus process ──────────────────────────────┐
│                                                                                                        │
│  io.matrix.MatrixApplication  (@QuarkusMain, matrix-core/src/main/java/io/matrix/MatrixApplication.java:30)  │
│            │  binds  9091 HTTP  (application.properties:31)  +  9094 TCP  +  9093 multicast              │
│            │                                                                                             │
│   ┌────────┴─────────┬──────────────────┬───────────────────────────┐                                    │
│   │                  │                  │                           │                                    │
│   ▼                  ▼                  ▼                           ▼                                    │
│ LearningWorkbench  CognitiveResource  OpenAIChatResource      36 more resources                        │
│ @Path /v1/workbench @Path /v1/cognitive @Path /v1              (/api/v1/**, /v1/**)                     │
│   57 routes          11 routes          6 routes                136 routes                               │
│        │                  │                  │                                                            │
│        ▼                  ▼                  ▼                                                            │
│ KnowledgeWorkbench  CognitiveService   io.matrix.reasoning                                             │
│        │              │                   .BrainLoopService  ← THE SIXTH CYCLE                           │
│        │              │                        │  (tick → X-Matrix-Trace)                               │
│        │              │                        ▼                                                        │
│        │              │                  ConsciousnessLoop  ← the nine-stage loop                         │
│        ▼              ▼                                                                                 │
│  KnowledgeStore ────────┐        CognitiveStore                                                      │
│  (27 tables, v19)       │        (12 tables, v4)                                                      │
│        └────────────────┴──────► ONE FILE: data/knowledge/workbench.sqlite                              │
│                          WriterLock — one writer PROCESS per file                                       │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────── CHAIN B — one JDK HttpServer process ────────────────────────────────┐
│  io.matrix.api.MinimalHttpServer  (matrix-api-gateway/.../MinimalHttpServer.java:1674)                  │
│            │  binds 8765 (the -Dport property, then args[0]); 127.0.0.1 unless matrix.security.mode says  │
│            │                                                                                             │
│            ├── MATRIX_MODE=production ──► ProductionBrainClient ──► TrueMindCycle ──► BirBrainCycle   │
│            │        (the real brain)               │                    │                                 │
│            │                                      │                    ├── PersistentMind ──► mind.sqlite │
│            │                                      │                    ├── NeuroModulatorHub              │
│            │                                      │                    └── GoalTracker, HierarchicalMemory │
│            │                                                                                             │
│            └── DEFAULT (stub) ──────────► StubBrainCycle  (87 lines)  ← WHAT ACTUALLY SERVES BY DEFAULT   │
│                                                                                                        │
│  28 routes: /v1/analyze /v1/explain /v1/audit* /v1/distill /v1/bir /v1/conflicts /v1/generalize /v1/gpu │
│              /v1/federate /v1/auth/login /v1/learn /v1/teach /v1/sleep /v1/status /v1/goals              │
│              /v1/inbox/scan /v1/transcode/* /health/live /health/ready /q/openapi /metrics /openapi.yaml│
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### The route census

| the surface | the routes | the source |
|---|---|---|
| `/v1/workbench/**` | **57** | `LearningWorkbenchResource` |
| `/v1/cognitive/**` | **11** | `CognitiveResource` |
| `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/brain/think|plan|subagent` | 6 | `OpenAIChatResource` |
| `/api/v1/**` | 73 | 16 resources |
| `/v1/**` (the rest) | 63 | 19 resources |
| **chain A total** | **210** | the constant-pool census over the 39 mounted route classes |
| chain B's served routes | **28** | `MinimalHttpServer.registerRoutes()` (`:370-412`) |
| chain B's **declared but unmounted** JAX-RS routes | **9** | `BrainQuarkusResource` (4), `AnalyzeResource`, `AuditResource`, `ExplainResource` (2), `FederateResource` (2) |

**The naming trap:** `/v1/brain/think|plan|subagent` are **chain A** routes (inside
`OpenAIChatResource`), while the gateway's `/v1/brain/**` is **unmounted**.

---

## 2. What the isolation really is

The constant-pool census over **2909 classes and 76 858 edges**:

| the measurement | the value |
|---|---|
| chain A's reachable closure (from the 39 mounted route classes) | **1015 classes** |
| chain B's reachable closure (from `MinimalHttpServer`) | **465 classes** |
| the intersection | **179 classes** — the shared substrate (`io.matrix.bir`, `io.matrix.neuron`, `io.matrix.tsetlin`, `io.matrix.compression`) |
| chain B ∩ `io.matrix.cognitive.**` | **0** ✓ |
| chain B ∩ the epistemic store's own classes | **0** ✓ |
| chain B ∩ the classes that name the epistemic database | **0** ✓ |
| chain B ∩ the chain A serving surface | **0** ✓ |
| chain B ∩ the persistence stack, other than `SqliteMemoryBackend` | **1** — `io.matrix.memory.SqliteMemoryBackend`, which is chain B's **own** mind store |
| chain B ∩ `io.matrix.knowledge.**` | **3** — `SimpleKnowledgeBase` and its two nested types: a **read-only RAG index** over the markdown of `data/knowledge/`, no connection, no writes |

**The isolation holds.** The shared 179 classes are a library, not a path to the store — and
the gate asserts on the store rather than on the package, printing the three exceptions so
that the exception stays visible.

---

## 3. The six cycles — the truth, not the map

| the cycle | the status | the production callers | the evidence |
|---|---|---|---|
| `io.matrix.reasoning.BrainLoopService` | **SERVED (chain A)** | **1** — `OpenAIChatResource` | `@ApplicationScoped @Startup` (`:48-50`); `OpenAIChatResource.java:260` `brainLoop.tick(observation)` → the `X-Matrix-Trace` header |
| `io.matrix.reasoning.ConsciousnessLoop` | **SERVED (chain A)** | **1** — the reasoning `BrainLoopService` | constructed by it |
| `io.matrix.cognitive.CognitiveKernel` | **SERVED (chain A)** | 1 — `CognitiveService` | `CognitiveService.java:123-128` |
| `io.matrix.brain.runtime.TrueMindCycle` | **SERVED (chain B)** | **3** — `ProductionBrainClient`, `MindRuntimeCli`, `ActuationStage` | the constant pool |
| `io.matrix.brain.BirBrainCycle` | **SERVED (chain B)** + its own `main` | **3** — `BrainHttpServer` (unmounted), `PersistentMind`, `TrueMindCycle` | `BirBrainCycle.java:330` |
| `io.matrix.brain.runtime.MindCycle` | **experimental / CLI only** | **1** — `MindRuntimeCli` | `MindRuntimeCli` is in **neither** serving closure |
| `io.matrix.consciousness.BrainLoopService` | **inactive-but-tested** | 25 — **none** in a serving closure | a plain Java class, *"no CDI, no Quarkus"* (`:26-29`); the 25 callers are profiling, checkpointing, replay, health and ablation tooling inside `io.matrix.consciousness` |
| `io.matrix.neuron.ConsciousBrain` | **inactive-but-tested** | **0** | 19 test references |

**The six that the W44 map names five.** The sixth — `io.matrix.reasoning.BrainLoopService`
— is served and is the only cycle behind the chat surface. The W44 map is **not wrong for the
moment it was written**; the correction is recorded in the W45.2 documents, and the W44
documents are not rewritten.

### The two `BrainLoopService` classes — they are not the same thing

| | `io.matrix.consciousness.BrainLoopService` | `io.matrix.reasoning.BrainLoopService` |
|---|---|---|
| the file | `matrix-core/.../consciousness/BrainLoopService.java` (115 L) | `matrix-core/.../reasoning/BrainLoopService.java` (201 L) |
| the CDI | **none** — *«intentionally a plain Java class — no CDI, no Quarkus, no LLM»* | `@ApplicationScoped @Startup` |
| the loop | 5 stages: perception → attention → deliberation → gate → action | 9 stages, including consolidation, subconscious and prediction-error |
| the callers | **25**, all measurement tooling | **1** — the chat resource |
| the status | **inactive-but-tested** | **SERVED** |

**The first is the RUN-158 research/measurement loop, designed to run on FPGA and edge
targets; the second is the production wiring of `ConsciousnessLoop`, built so that the brain
loop is observable from the wire.** The same simple name in two packages means a text search
conflates them; in the constant pool the numbers are 25 : 1.

---

## 4. The persistence architecture

### The one file, and the two classes over it

| | the epistemic store | the cognitive store |
|---|---|---|
| the class | `io.matrix.knowledge.KnowledgeStore` | `io.matrix.cognitive.CognitiveStore` |
| the schema | **v19** (`KnowledgeStore.java:90`) | **v4** (`CognitiveStore.java:97`) |
| the tables | **27**: `workspace`, `workspace_snapshot`, `concept`, `source`, `claim`, `evidence`, `representation`, `prerequisite`, `revision`, `derivation`, `derivation_premise`, `contradiction`, `epistemic_case`, `learning_goal`, `learning_goal_subtask`, `learning_state`, `learning_transition`, `learning_error`, `asked_question`, `assessment_item`, `assessment_outcome`, `dialogue_turn`, `hebbian_binding`, `observation_occurrence`, `observation_disposition`, `observation_adjudication`, `schema_meta` | **12**: `cognitive_episode`, `cognitive_prediction`, `cognitive_error`, `cognitive_event`, `cognitive_procedure`, `cognitive_task`, `cognitive_regulatory_state`, `cognitive_regulatory_event`, `cognitive_sequence`, `cognitive_idempotency`, `cognitive_request_event`, `cognitive_schema_meta` |
| the indexes | 26 | 3 |
| the connection | `jdbc:sqlite:` + the configured path | the **same file**, its own connection |
| the durability | `PRAGMA journal_mode=WAL` + `PRAGMA synchronous=FULL` (`:184`) | the same (`:169-170`) |
| the transactions | 10 explicit blocks | 2 explicit blocks |

**The schema chain:** the knowledge store migrates in a `while (version < SCHEMA_VERSION)` loop
(`:214`), writing the version **last**, so an interrupted migration is re-runnable. Every
migration is additive (`CREATE TABLE IF NOT EXISTS`, `addColumnIfMissing`).

### Who opens a connection

| the class | the file:line | the file it opens |
|---|---|---|
| `KnowledgeStore` | `KnowledgeStore.java:170` | the canonical database |
| `CognitiveStore` | `CognitiveStore.java:146` | **the same** database |
| `SqliteMemoryBackend` | `SqliteMemoryBackend.java:40` | `data/mind/mind.sqlite` — chain B's own mind store |

**Three connections in the repository, two files.** There is no second store that duplicates
the knowledge store's responsibility.

### The transactional boundaries and the crossings

- **Within one transaction:** the workspace snapshot and the workspace replacement (the same
  transaction, so a replacement cannot happen without a recovery point); the atomic epistemic
  write (`recordObservationAtomically`); the idempotency claim/complete/fail; the request
  checkpoint and the request event.
- **Across two connections:** the cognitive kernel writes to **both** stores — the epistemic
  state through the workbench (`CognitiveKernel.java:343` `storeForValidation()`, `:552`
  `recordObservation(...)`) and the cognitive state through the `CognitiveStore`
  (`:309`, `:378`, `:590`, `:596`, `:657`, `:870`, `:917`). **The two are not one transaction.**
  That is why W45.0 made the *epistemic* write atomic and W45.1 made the *request* recovery
  idempotent, rather than pretending the pair is atomic.

### The mechanisms that make the writes recoverable

| the mechanism | where |
|---|---|
| the durable observation disposition | `observation_disposition` (the projection) + `observation_occurrence` (the act) + `observation_adjudication` (the append-only history) |
| the request lifecycle | `cognitive_idempotency` (the claim, the checkpoint, the owner token, the owner pid, the attempts) + `cognitive_request_event` (the append-only event log) |
| the ownership fencing | `OwnershipToken(pid, startedAt, token)`; `classify(owner, mine)` → MINE / LIVE_OTHER / DEAD |
| the crash recovery | `IdempotencyRegistry.recover` + `CognitiveService.recoverInterruptedRequests`; the recovery write is **deliberately unfenced**, because it acts on behalf of a provably dead owner |
| the single-writer guard | `WriterLock` — the OS lock + the owner record; **one writer process per file** |
| the migration | the additive chain, the version written last |

### The deletion and replacement paths

| the path | what it erases | the protection |
|---|---|---|
| `deleteWorkspace` (`KnowledgeStore.java:1534-1540`) | the rows of **ten** tables: `revision, prerequisite, representation, evidence, derivation, contradiction, learning_goal, claim, source, concept` + `derivation_premise` + `learning_goal_subtask` + the workspace row | the HTTP load refuses without `?replace=true` and takes a snapshot in the same transaction (W45.2) |
| the cognitive tables | **nothing** erases them — they are append-only by design | — |
| **the gap** | the erasure set **predates** the observation tables: it erases `evidence` but **not** `epistemic_case`, `observation_occurrence`, `observation_disposition` or `observation_adjudication`. **A load therefore leaves a case and its decision history while erasing the evidence rows the observation wrote.** | **open — the owner's decision (D3-c)** |
| **no file-level backup exists** | the SQLite backup API (`BackupManager`, `VACUUM INTO`) appears **nowhere** in the production sources; `docs-v2/operations/RUNBOOK.md` contains no occurrence of backup, restore or snapshot | **open — the recovery runbook is a W46 deliverable** |

---

## 5. The architecture documents against the architecture

| the document | what it claims | the truth |
|---|---|---|
| `docs-v2/architecture/OVERVIEW.md` | a one-layer diagram, *«the execution is a single BIR point»*, the INV-1 guard | **true** — the guard exists and runs in CI. But it names **no chain and no cycle**: `grep` for chain/cycle/`MinimalHttpServer`/`TrueMindCycle` → **0 matches**. And it points at `ethics/frozen/FROZENFNLGuardian`, while the class is at `io.matrix/ethics/FROZENFNLGuardian.java` |
| `docs-v2/architecture/MATRIX-COGNITIVE-ARCHITECTURE.md` | *«the serving surface (the same Quarkus process)»*, *«the stores (one SQLite file)»* — both **true** | but it leaves the merging open: *«Merging … is a separate decision with its own ADR»* (`:139`). **W45.2 decided it**: isolation, fail closed |
| `docs-v2/architecture/CAPABILITY-REGISTRY.md` | *«authoritative · 2026-10-26»* | its vocabulary is the **provenance of a claim** (`VERIFIED/UNSOURCED/CORRECTED/REWORDED/REMOVED`), not the **state of a capability**. It predates W45.0, W45.1 and W45.2 and **contains three refuted or stale numbers** (the test count, the TLA count, the speed-up factor) |
| `docs-v2/engineering/W44-CAPABILITY-REGISTRY.md` | a second registry, with a different vocabulary (`IMPLEMENTED/TESTED/SERVED/ISLAND/INACTIVE/…`) | **two registries, two vocabularies, both claiming canonicality** |
| `docs-v2/engineering/PLAN.md` | the current plan | its last recorded wave is **W44** — it is **three waves behind** |
| `docs-v2/engineering/STANDARDS-MATRIX.md` | the required versions and the ADR list | the Java version is cited to `gradle.properties`, which has **no version line** (it lives in `build.gradle:9-10`); the ADR table lists 5 of the 8 ADRs and marks the superseded `ADR-2026-10-26-002` as *«✅ Accepted»*. **Nothing in it is enforced** — the gates live in a CI job named *«Quality gates (can fail)»* |
| `docs-v2/architecture/RUNTIME-TOPOLOGY.md` | the runtime topology | it predates the P2P findings and the gateway's stub default |

---

## 6. The truth that no document records

| # | the fact | the evidence |
|---|---|---|
| **T-01** | **the gateway serves a stub by default** — `MATRIX_MODE` defaults to `stub` (`MinimalHttpServer.java:127-128`), so `/v1/analyze` is answered by an 87-line `StubBrainCycle` and `/health/live` reports `"brain_available": false` | verified in the source by the coordinator |
| **T-02** | **the Quarkus application opens three ports** — 9091 HTTP, 9094 TCP, 9093 multicast — because `P2PNetwork` is `@ApplicationScoped` with a `@PostConstruct` that starts the sockets (`P2PNetwork.java:28-29,37,108,121`), and the mounted route `/api/v1/noosphere/p2p` reaches them | verified in the source by the coordinator |
| **T-03** | **the gateway's JAX-RS facade is a stub-shaped decoy** — if it were mounted, `StubBrainCycle` would silently become the served implementation of `/v1/analyze` and `/v1/explain`, replacing the real `ProductionBrainClient` path | `matrix-api-gateway/build.gradle:20-24` (the module's own comment) + the constant pool |
| **T-04** | **the gateway drags 2622 matrix-core classes onto chain B's classpath** while mounting none of them — 211 of chain B's 465 reachable classes are matrix-core's | the closure census |
| **T-05** | **`ActiveBrainService`… no.** The 42-package SCC means "the packages of matrix-core are mutually reachable" — a maintainability fact, not a serving fact | the Tarjan census |
| **T-06** | **the readiness probe is permanently 503 in this environment** — the reactive PostgreSQL extension contributes a datasource health check, the default port is 5433 while the machine's PG is on 5432, and **no production code queries the datasource** | `application.properties:47-50`; the datasource grep is empty |
| **T-07** | **the GPU is available and MATRIX's Java path silently does not use it** — the ONNX provider is compiled in but `addCUDA` fails because the library path is set as a JVM system property and a native `dlopen` consults `LD_LIBRARY_PATH` instead; the diagnostic that reports the active provider is a **hardcoded string** | `OnnxRuntimeAdapter.java:104-114,118-123`; `matrix-core/build.gradle:135-155`; the measured 191 ms → 15 ms |
| **T-08** | **`ActuationStage` is in neither serving closure** — it calls `TrueMindCycle`, but nothing served calls it | the constant pool |
| **T-09** | **the audit log has no file** — `io.matrix.audit.HashChainedLog` is in-memory only and `data/audit/` is empty | the class and the directory |
| **T-10** | **the cognitive and epistemic stores share one file with two connections, and the kernel's write crosses both** — the pair is **not** one transaction | `CognitiveKernel.java:343,552` against `:309,378,590,596,657,870,917` |
