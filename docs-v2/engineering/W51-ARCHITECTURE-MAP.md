# W51 — Architecture map: the actual runtime interactions

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-08 · **Wave:** W51 · **Branch:** `develop`

**What it is.** The map of the transitions that actually happen, with the contract each one
crosses, the carrier it crosses on, what is passed, where the state lands and who is allowed to
write it. It is a map of **facts**, not of intents: every row is a `file:line`, a named test or a
measured gate result.

**What it is not.** It does not replace `docs-v2/architecture/RUNTIME-TOPOLOGY.md` — that document
still owns what runs, what it binds and what it serves. This document owns **the edges between the
parts**: which class calls which, across which boundary, carrying what.

**The evidence behind it.** The isolation gate of 2026-11-08
(`scripts/w45-chain-isolation-check.sh`, **22 checks, 0 failed**), the demonstration of 2026-11-08
(`docs-v2/engineering/W51-DEMONSTRATION.json`, **15/15** acceptance items, **12** trace phases),
and the audit of the same day (`docs-v2/engineering/W51-AUDIT.md`).

**The rule.** *A class that is compiled is not a class that is called; a route that is declared is
not a route that is served; a table that exists is not a role that is filled.* Every connectivity
claim below is one of those three facts, and it is marked.

---

## 1. Two serving applications, one cognitive system

```
CHAIN A — the epistemic application          CHAIN B — the brain runtime
──────────────────────────────────────       ──────────────────────────────────────
io.matrix.MatrixApplication                  io.matrix.api.MinimalHttpServer
  @QuarkusMain  (MatrixApplication.java:18)    a JDK HttpServer (MinimalHttpServer.java:1701)
  binds 9091 (application.properties:31)        binds 8765 (MinimalHttpServer.java:1701)
  217 annotated routes (the gate, 2026-11-08)   9 declared, 28 served routes (the gate)
        │                                              │
  ┌─────┴──────────────────────────────┐      ┌──────┴─────────────────────────────┐
  │ /v1/workbench   LearningWorkbench… │      │ /v1/analyze  ProductionBrainClient  │
  │ /v1/cognitive   CognitiveResource  │      │   → TrueMindCycle → BirBrainCycle   │
  │ /v1/bridge      BridgeResource      │      │   → PersistentMind (own SQLite)     │
  │ /v1             OpenAIChatResource  │      └────────────────────────────────────┘
  └─────┬──────────────────────────────┘
        │ ONE crossing: HTTP POST /v1/bridge/messages
        ▼
  BridgeResource → BridgeService → EpistemicBridgeSink → KnowledgeStore
```

**The process boundary is preserved.** No process was merged into another; each chain keeps its own
runtime and its own files (`docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` §2).

**The boundary of KNOWLEDGE is revised.** Chain B's observations now enter chain A's epistemic
model — through one named, versioned, authenticated, audited component, and through nothing else
(ADR-W51-01 §1). The bridge adds **no port**: it reuses chain A's 9091 under a new prefix
(`/v1/bridge`, `BridgeResource.java:41`; `POST /messages` `:79`; `GET /status` `:109`).

**What the gate measured on 2026-11-08** (`scripts/w45-chain-isolation-check.sh`):

| the measurement | the value |
|---|---|
| chain A annotated routes | **217** |
| chain B declared routes / served routes | **9** / **28** |
| classes of `io.matrix.cognitive.**` reachable from chain B | **0** |
| classes of the epistemic store's own packages reachable from chain B | **0** |
| classes of `io.matrix.knowledge.**` reachable from chain B | **3** — `SimpleKnowledgeBase`, `$Document`, `$RetrievalResult`, **none of which can open a database** |
| classes of `io.matrix.contract.**` reachable from chain B | **18**, of which impure: **0** |
| classes reachable from chain B **and** from chain A's store | **0** |
| the summary | **22 checks, 0 failed** |

---

## 2. The actual transitions

| # | from (module / class / method) | to | the contract it crosses | the carrier | what is passed | where the state lands | the single writer |
|---|---|---|---|---|---|---|---|
| **T1** | `io.matrix.api.CognitiveResource.observe` — `matrix-core/.../cognitive/CognitiveResource.java:74`, call at `:80` | `io.matrix.cognitive.CognitiveService.observe` — `:301` | `CognitiveSignal` + an optional idempotency key | **in-process** (chain A) | the assignment text; the key names one logical request | `cognitive_idempotency`, `cognitive_episode` (chain A file) | `io.matrix.cognitive.CognitiveStore` |
| **T2** | `CognitiveService` — `:443` `kernel().process(signal, listener)` | `io.matrix.cognitive.CognitiveKernel.process` — `:287`, `:303` | `CognitiveSignal` → `CognitiveOutcome` | **in-process** (chain A) | the signal; the outcome carries the trace | the same chain A file | `CognitiveStore` |
| **T3** | `CognitiveKernel` — `:345` `sessionFactory.create(episodeId, remembered)` | `io.matrix.cognitive.microworld.MicroWorldSession` — `:47` | the micro-world session | **in-process** (chain A) | the remembered CASES; the session is rebuilt from the store, never from a private copy | the episode's cases, in the chain A file | `CognitiveStore` |
| **T4** | `CognitiveKernel` — `:497` `environment.probe(probed)` | `io.matrix.cognitive.microworld.LampEnvironment.probe` — `:44`, `:83` | `MicroWorldEnvironment` | **in-process** (chain A) | a `WorldInput`; returns an `Observation` | the observation becomes a case + evidence in the chain A file | `CognitiveStore` + `KnowledgeStore` |
| **T5** | `io.matrix.knowledge.KnowledgeWorkbench.store()` — `matrix-core/.../knowledge/KnowledgeWorkbench.java:156`, opens at `:158` | `io.matrix.knowledge.KnowledgeStore` — `:330` | the epistemic model | **SQLite** (chain A) | claims, evidence, cases, sources, goals, learning state | `data/knowledge/workbench.sqlite`, schema **v19** (`KnowledgeStore.java:92`) | **`KnowledgeStore` alone** — `applyChanges` is the only write path |
| **T6** | `io.matrix.knowledge.LearningWorkbenchResource` — `:479-480` (`POST /workspaces/{id}/ask`), 58 HTTP methods on `@Path("/v1/workbench")` `:51` | `KnowledgeWorkbench` → `KnowledgeStore` (T5) | the workbench facade | **in-process → SQLite** (chain A) | the question, the level; the answer carries the claims and their sources | the chain A file | `KnowledgeStore` |
| **T7** | `io.matrix.api.OpenAIChatResource` — `:253-260` `brainLoop.tick(observation)` | `io.matrix.reasoning.BrainLoopService` | the nine-stage loop | **in-process** (chain A) | a 64-bit sensor `BitSet`; the trace is telemetry and its failure never blocks the answer | in-memory, per request | — (no store) |
| **T8** | `io.matrix.cognitive.CognitiveService` — `:141` `new CognitiveStore(Paths.get(workbench.databasePath()))` | `io.matrix.cognitive.CognitiveStore` — `:112` | the cognitive store | **SQLite** — **the same file as T5** | predictions, errors, procedures, tasks, regulatory state, idempotency records | `data/knowledge/workbench.sqlite` — the **same** file | `CognitiveStore` for its own tables; `KnowledgeStore` for the epistemic ones. Both are one writer process; `WriterLock.java:158` refuses a second process |
| **T9** | `io.matrix.brain.runtime.bridge.BridgePublisher.publishObservation` — `matrix-brain-runtime/.../bridge/BridgePublisher.java:108`, path constant `:47` | `io.matrix.bridge.BridgeResource.messages` — `matrix-core/.../bridge/BridgeResource.java:79`, `:82` | `BridgeEnvelope` (versioned, signed, provenance-carrying) | **HTTP POST `/v1/bridge/messages`** (chain B → chain A) | the observation, the provenance, the request id (the idempotency key) | the bridge's own ledger `data/bridge/bridge.sqlite` (`BridgeResource.java:45`; `BridgeJournal.java:63`) **and** the chain A file | `BridgeJournal` for its own ledger; `KnowledgeStore` for the epistemic write |
| **T10** | `BridgeResource.messages` — `:82` | `io.matrix.bridge.BridgeService.accept` — `:119` | the bridge protocol | **in-process** (chain A) | the decoded envelope | — | `BridgeService` is the single owner of the write path (ADR-W51-01 §4.2, BRIDGE-1) |
| **T11** | `BridgeService.accept` — `:120` `receiver.receive(envelope, nowMillis())`; audit at `:121` | `io.matrix.contract.bridge.BridgeReceiver.receive` — `:141`, the write at `:298` `sink.accept(envelope)` | `BridgeReceiver` verifies version, signature, capability and budget before the store is touched | **in-process** (chain A) | the envelope | the audit chain `bridge_audit` (`BridgeJournal.java:80`) | `BridgeJournal` |
| **T12** | `BridgeReceiver` — `:298` | `io.matrix.bridge.EpistemicBridgeSink` — `:52` | `BridgeSink` | **in-process** (chain A) | the accepted envelope | the chain A file | `KnowledgeStore` |
| **T13** | `EpistemicBridgeSink` — `applyChanges` at `:102` (source + **candidate** + evidence link), `:126` (candidate only), `:148` (evidence link only) | `io.matrix.knowledge.KnowledgeStore` | `KnowledgeChanges` | **SQLite** (chain A) | a `Source` of kind `SECONDARY_REPORT`, a `Claim` with status `CANDIDATE` and confidence `0.0`, and an `Evidence` link — **nothing else** | `data/knowledge/workbench.sqlite` | `KnowledgeStore` |
| **T14** | `io.matrix.api.brain.ProductionBrainClient` — `matrix-api-gateway/.../brain/ProductionBrainClient.java:171-172` | `io.matrix.brain.runtime.TrueMindCycle` — `matrix-brain-runtime/.../TrueMindCycle.java:51`, `think` at `:155`, `:169` | the brain cycle | **in-process** (chain B) | the text, and the transliterated form beside it | chain B's own files | — |
| **T15** | `TrueMindCycle` — `:149` `new BirBrainCycle(rng)`, `:253` `core = brain.cycle(input)` | `io.matrix.brain.BirBrainCycle` — `matrix-core/.../brain/BirBrainCycle.java` | the BIR core: HDC + codebook + safety | **in-process** (chain B) | the text; returns the reply, the confidence and the BRC trace | chain B's own files | — |
| **T16** | `ProductionBrainClient` — `:405` `publishToBridge(input, mr)`, the method at `:465`; the publisher field at `:96`, attached at `:124` | `BridgePublisher` → **T9** | the bridge protocol | **HTTP** | the reply as an `Observation` with a `Provenance` | the chain A file, as a candidate only | `KnowledgeStore` |
| **T17** | `io.matrix.brain.runtime.PersistentMind` — `matrix-brain-runtime/.../PersistentMind.java:43,57,58` (`SqliteMemoryBackend`, `HierarchicalMemory`) | `io.matrix.memory.SqliteMemoryBackend` — `matrix-core/.../memory/SqliteMemoryBackend.java:40` | the chain B memory | **SQLite** (chain B) | three tiers: EPISODIC (NDJSON), SEMANTIC (SQLite), PROCEDURAL (BIR/Tsetlin clauses) | `data/mind/mind.sqlite` (`MinimalHttpServer.java:199`) | chain B alone — a file chain A never opens |
| **T18** | `io.matrix.runtime.CognitiveRuntime` — `matrix-core/src/main/java/io/matrix/runtime/CognitiveRuntime.java` | the declared components | the capability declarations | **in-process** | 7 components / 7 capabilities (the demonstration, acceptance item 2) | — | — |

**Reading the table.** T1–T8 and T9–T13 are chain A. T14–T17 are chain B. **Exactly one row crosses
the process boundary: T9** (and T16 is its tail). Every other row is in-process or is a SQLite file
that only one process opens. The gate proves this independently: **0** classes are reachable from
chain B and from chain A's store at the same time.

---

## 3. The canonical contracts

`io.matrix.contract` is the shared model. It is a package **neither chain owns**: chain A depends
on it and chain B depends on it, and it contains no class that can open a database, serve a route
or reach a store.

| the concept | the type | where it lives | who crosses the boundary with it |
|---|---|---|---|
| the domain records — Entity, Relation, Property, WorldState, StateTransition, Event, Observation, Evidence, Belief, Hypothesis, Prediction, Experiment, Action, Outcome, Goal, Task, Skill, Procedure, Algorithm, Plugin, Evaluation, Reflection | `io.matrix.contract.*` (records) | the package itself | the bridge's payloads (`io.matrix.contract.bridge.BridgePayload`) and the plugin's vocabulary |
| the wire envelopes — CognitiveCommand, CognitiveEvent, CognitiveResponse, Correlation, RetryPolicy | `io.matrix.contract.*` | the package | any process boundary that speaks the contract |
| the qualified quantities — Confidence (`Basis`: `DECLARED`/`MEASURED`/`UNESTABLISHED`), Provenance, ValidityInterval, BeliefStatus, Capability | `io.matrix.contract.*` | the package | every payload that carries a number with a basis |
| the bridge protocol — `BridgeProtocol`, `BridgeEnvelope`, `BridgePayload`, `BridgeDecision`, `BridgeCapability`, `BridgeBudget`, `BridgeIdempotency`, `BridgeAuditRecord`, `BridgeReceiver`, `BridgeSigner`, `HmacBridgeSigner`, `BridgeSink`, `BridgeRefusalReason` | `io.matrix.contract.bridge.*` | the package | **the bridge is the only user that crosses** |
| the epistemic record | `io.matrix.knowledge.Claim` / `Evidence` / `Source` | chain A's store | **never** — the bridge adapts into it, it does not carry it |

**The purity of the package, checked mechanically.** An independent check over the package's source
on 2026-11-08:

| the impurity to look for | the count over `io/matrix/contract/**` (50 files) |
|---|---|
| `import java.sql` / `javax.sql` / `org.sqlite` | **0** |
| JAX-RS annotations (`@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`, `@Produces`, `@Consumes`) | **0** |
| `import io.matrix.knowledge` / `import io.matrix.cognitive` | **0** |

**The same property is proved by the gate**, over the *compiled* classes rather than the source:
rule **G7** reports **18** contract classes reachable from chain B and **0** of them able to open a
database, serve a route or reach a store. The gate's negative control **C3** breaks this
deliberately and makes the gate red.

**What the contracts are not.** They are not a second implementation of the subsystems' own records:
`io.matrix.knowledge.Claim` remains the workbench's claim and
`io.matrix.cognitive.microworld.Observation` remains the micro-world's observation
(ADR-W51-01 §3). The canonical records are the **interchange** form.

---

## 4. The bridge: BRIDGE-1 … BRIDGE-10

| the requirement | where it is satisfied | what proves it |
|---|---|---|
| **BRIDGE-1** explicit | `io.matrix.contract.bridge.BridgeProtocol` names the contract; `io.matrix.bridge.BridgeService` is the single owner of the write path | `BridgeServiceTest`; gate G8 — **0** classes reachable from chain B and from chain A's store |
| **BRIDGE-2** versioned | `BridgeProtocol` carries the version; `CognitiveContract.compatible` decides; a refusal names the version | `BridgeServiceTest`; `BridgeProtocolInfo.VERSION` surfaced at `BridgeResource.java:114` |
| **BRIDGE-3** authenticated | `HmacBridgeSigner` signs the canonical bytes; the comparison is timing-safe; **there is no default secret** — without one the bridge refuses everything | `BridgeServiceTest`; `GET /v1/bridge/status` reports `"configured": false` (`BridgeResource.java:115`) |
| **BRIDGE-4** provenance-carrying | `Provenance` carries the source, the origin and the span with a declared unit; `BridgePayload` refuses a payload without provenance | `BridgeServiceTest`; `BridgePublisher.java:471-478` builds the provenance at the call site |
| **BRIDGE-5** safety-bounded | `BridgeCapability` names the three kinds; anything else is refused **before** the store is touched | `EpistemicBridgeSink.java:102,126,148` — the only three writes; `BridgeService.grantedCapabilities()` `:142` |
| **BRIDGE-6** fail-closed | `BridgeReceiver` turns every failure into a `BridgeDecision` of `REFUSED` with a reason; there is no degraded mode | `BridgeResource.java:82-105` — the HTTP status reflects the decision; `BridgeServiceTest` |
| **BRIDGE-7** audited | every call and every refusal is appended to the audit chain with its request id, its decision and its reason | `BridgeService.accept` `:122`; the table `bridge_audit` (`BridgeJournal.java:80`); `auditCount()` at `BridgeResource.java:118` |
| **BRIDGE-8** replayable | idempotent per request id; the effect is recorded once; the idempotency is durable | `BridgeIdempotency`, implemented by `BridgeJournal` (`BridgeJournal.java:40`); `EpistemicBridgeSink.alreadyApplied(claimId)` `:113` |
| **BRIDGE-9** bounded | a rate, a size, a batch size and a per-day ceiling, enforced before the write | `BridgeBudget`, checked in `BridgeReceiver` before the sink is reached |
| **BRIDGE-10** gated | the isolation gate is extended to the bridge; the bridge is the only crossing; the negative control is re-run against the tree that contains the bridge | gate rules **G8** and **G9**; the negative control **C4** |

**What the bridge may write: exactly three things.** A **source** of kind `SECONDARY_REPORT`, a
**claim** with status `CANDIDATE` and confidence `0.0`, and an **evidence link**
(`EpistemicBridgeSink.java:91-104`). It cannot alter a claim, a confidence, a goal, a safety policy,
a disposition or an adjudication (ADR-W51-01 §2).

---

## 5. The isolation gate

**Command:** `bash scripts/w45-chain-isolation-check.sh` — the proof is a reachability proof over the
**compiled** classes (`javap -p -c`), not a textual search: a textual search mistakes a mention for
a call.

| the rule | what it checks | the measured result, 2026-11-08 |
|---|---|---|
| **G0** | the contract of a future bridge is on record, and the document names every requirement the gate knows | `W45.2-CHAIN-BOUNDARY.md` exists and names BRIDGE-1 … BRIDGE-10 |
| **G1** | chain B reaches no class of `io.matrix.cognitive.**` | **0** classes |
| **G2** | chain B reaches no class of the epistemic store's own packages, and no class that names the epistemic database | **0** and **0** |
| **G3** | the `io.matrix.knowledge.**` classes chain B *does* reach cannot open a database | **3** reached (`SimpleKnowledgeBase`, `$Document`, `$RetrievalResult`), **0** of them reach the persistence stack |
| **G4** | chain B reaches no class that serves the chain A surface | **0** classes |
| **G5** | no route of chain B is a route of chain A | chain A **217** annotated; chain B **9** declared, **28** served; no overlap |
| **G6** | at run time: the chain B server refuses the chain A surface, and the owner's epistemic database is byte-identical before and after | `GET /v1/cognitive/summary` → **404**, `GET /v1/workbench/workspaces` → **404**, `POST /v1/cognitive/observe` → **404**, `GET /v1/status` → **200**; the database's SHA-256 is unchanged (`ca16c0ae…4592a` before and after) |
| **G7** | the shared contract layer is **pure** | **18** contract classes reachable from chain B, **0** impure |
| **G8** | the bridge is the **only** crossing | **0** classes reachable from chain B and from chain A's store; **0** chain A bridge-server classes reachable from chain B |
| **G9** | the chain B bridge client reaches no class of the store | the bridge client exists; **0** store classes in its closure |

**The summary: 22 checks, 0 failed.**

**The negative controls** (`scripts/w45-chain-isolation-selftest.sh`) break the isolation on purpose,
in a private copy of the compiled classes, and run the real gate against the broken tree:

| the control | what it breaks | what the gate must report |
|---|---|---|
| **C1** | a chain B class that **calls** the epistemic store | the store is reachable; the gate exits non-zero |
| **C2** | a chain B class that **declares** a route under `/v1/cognitive` | a route of chain A belongs to chain B; the gate exits non-zero |
| **C3** | a class of the **shared contract layer** that opens a database | the contract layer is impure (G7); the gate exits non-zero. **Only G7 fires** — the broken class does not reach the epistemic store, which is what makes it a control of G7 and not of G2 |
| **C4** | a chain B class that **reaches the chain A bridge server** | the bridge server is reachable from chain B (G8); the gate exits non-zero |

**One definition was corrected during the wave.** The first version of G8 compared chain B's closure
with the store's *whole* closure and reported 36 "crossings" — the shared substrate
(`io.matrix.bir`, `io.matrix.neuron`, `io.matrix.tsetlin`, `io.matrix.compression`). That was wrong:
the shared substrate is not part of the store. G8 now uses the store's **own** classes, the same
distinction G2 uses (`W51-AUDIT.md` §6).

---

## 6. The state of memory, by role

**The honest opening: there are not six memories.** There are **three SQLite files** and **four
NDJSON/JSONL files**, and the six roles are **roles over them**. A role with no dedicated table is
a role served by a structure that was built for something else.

| the role | where it lives **now** (chain A) | where it lives **now** (chain B) | what exists and what does not |
|---|---|---|---|
| **working** | `io.matrix.memory.HierarchicalMemory` (in-memory, tiered L0–L3) + `PersistentHierarchicalMemory` (`@ApplicationScoped`, JSONL flush every `matrix.memory.flush-seconds`) → `data/hierarchical_memory.jsonl` | `io.matrix.memory.HierarchicalMemory` inside `PersistentMind` (`PersistentMind.java:58`, capacity 10 000) | **exists** as a tiered structure with eviction. `io.matrix.memory.RecurrentSdm`, `SdmReader`, `MemoryHierarchy` are **test-only** — 0 production callers (W51-AUDIT §7) |
| **episodic** | the `epistemic_case` table in the chain A file (the observations with their conditions) | `io.matrix.brain.runtime.EpisodicLog` → `data/mind/episodic.ndjson` (`MinimalHttpServer.java:181`) — one line per cycle | **exists**, and it is the substrate the kernel restores its session from (`CognitiveKernel.java:345`) |
| **semantic** | the `claim`, `evidence`, `source`, `concept`, `representation` tables in the chain A file | `SqliteMemoryBackend` → `data/mind/mind.sqlite` (`MinimalHttpServer.java:199`), via `PersistentMind` | **exists** in both chains, with different content and no shared file |
| **procedural** | the `cognitive_procedure` table in the **cognitive** store — the procedure the kernel promotes (`CognitiveKernel.promoteIfValidated` `:815`) | the PROCEDURAL tier of `PersistentMind` (BIR/Tsetlin clauses) + `BirKnowledgeBase` → `data/mind/bir.ndjson` (`MinimalHttpServer.java:238`) | **exists** as a role, **but it is not the same artefact on the two sides**: chain A holds a promoted procedure record, chain B holds learned clauses. `io.matrix.cognitive.algorithms.ProcedureStore` is **0 production callers** |
| **state of the world** | `io.matrix.cognitive.worldstate.WorldStateMemory` — a **projection over the durable case log**, not a table of its own. The kernel rebuilds it at RESTORE (`CognitiveKernel.java`, the `RESTORE` step of `WorldStateMemory`), consults it before it acts, and records a `StateTransition` for every probe (the `WORLD` step of the trace) | the same absence | **exists as of W51 §4.** The estimate is expressed in the canonical `io.matrix.contract.WorldState`, the changes in the canonical `io.matrix.contract.StateTransition`, and there is **no** `world_state` table: the memory is re-derived from the append-only case log, so a restart costs nothing and the sixth database was not created. The environment itself is still a deterministic function (`io.matrix.cognitive.microworld.LampEnvironment`) — the memory holds what the system SAW, not what the world IS |
| **meta-memory** | `io.matrix.knowledge.meta.KnowledgeReliability` — a **projection over the durable claims and revisions**: how many of a source's claims and of a validation method's claims were revised afterwards, with the reasons. The schema's own migration chain (`schema_meta`, `cognitive_schema_meta`, schema **v19**) remains the history of the schema | the method-performance ledger of the brain runtime, and the reflections of the cognitive cycle (`io.matrix.cognitive.reflection.CycleReflection`) | **exists as of W51 §4.** The knowledge facet is a projection — it cannot disagree with the store and it survives a restart without a store of its own — and it decides **nothing** about citability: that stays with the claim's own status, in `io.matrix.knowledge.EpistemicPolicy`. The remaining facet, a per-method profile of the cognitive cycle's own history across a restart, is not built |

**The two tables that carry the learning state** — `learning_state`, `learning_error`,
`learning_transition`, `learning_goal`, `learning_goal_subtask`, `assessment_item`,
`assessment_outcome` — live in chain A's file and are the workbench's assessment machinery, not a
memory role in the sense the mandate uses.

**The single writer of each file.** `data/knowledge/workbench.sqlite` — `KnowledgeStore` for the
epistemic tables, `CognitiveStore` for the cognitive tables, **one process** (T8); the lock is
`io.matrix.cognitive.WriterLock` and a second process is refused. `data/mind/mind.sqlite` — chain B
alone. `data/bridge/bridge.sqlite` — `BridgeJournal` alone, and it holds **no knowledge** (only
`bridge_request`, `bridge_audit`, `bridge_meta`, `BridgeJournal.java:77-83`).

---

## 7. What is still disconnected, experimental or archived

**This section is a citation, not an invention.** The table is
`docs-v2/engineering/W51-AUDIT.md` §7, reproduced here in its own words so that this map is
readable on its own:

| the subsystem | the state | the decision |
|---|---|---|
| `io.matrix.chain` | 0 production callers; two safety predicates hard-wired to `false`; one strategy unimplemented | **ARCHIVE** — the strongest candidate, but the deletion is the owner's call |
| `io.matrix.neuron.{RecurrentSdm,SdmReader,MemoryHierarchy}` | test-only | **ARCHIVE** |
| `io.matrix.budgeter.BudgeterHomeostat` | 0 main references | **ARCHIVE** |
| `io.matrix.cognitive.algorithms.ProcedureStore` | 0 production callers; the procedure the kernel promotes is held by the **cognitive store** instead | **ARCHIVE** — but the *concept* survives, as `io.matrix.contract.Procedure` |
| `io.matrix.advanced.SparseHDC`, `io.matrix.neuron.HdcAsLlmPreprocessor` | test-only | **ADAPT** — the HDC stack has two implementations and one of them serves |
| `io.matrix.neuron.MpdtHdcBridge` | test-only | **REWRITE CANDIDATE** with MPDT |
| `graph.Connectome`, `SleepConsolidator`, `ReplayPhase` | 159 tests, 0 production callers | **INTEGRATE or ARCHIVE** |
| `io.matrix.brain.runtime.MindCycle` | CLI only, forbidden in production by a guard test | **ARCHIVE** |
| `io.matrix.embodied.*` | `SensorimotorCycle`'s action is a coin toss; `ActiveInferenceEngine` has no environment | **ARCHIVE** |
| `io.matrix.simulation.*`, `PilotGridWorld`, `GridWorldBridge` | real but GA fixtures; no prediction or learning loop | **DEFER** |
| `matrix-micro/src/mpdt_neuron.c` | 16 lines, a stubbed evaluator, and the module is commented out of the build | **REWRITE CANDIDATE** |

**What this means for the map.** Every row of §2 is a transition that **happens**. Everything in this
table is a transition that **does not happen** — and the count of production callers in the
"state" column is the number that says so.

---

## 8. What is proven, and what is not

### Proven, by the artifacts named here

1. **The two processes do not meet inside one class graph.** Gate G8: **0** classes are reachable
   from chain B and from chain A's store at the same time.
2. **Chain B cannot alter the epistemic model.** Gate G1/G2/G3/G4 and the run-time part of G6: the
   chain B server answers **404** for every chain A route, and the owner's epistemic database is
   **byte-identical** before and after the run.
3. **The shared contract layer is pure.** G7 over the compiled classes, and an independent source
   check: **0** sql imports, **0** JAX-RS annotations, **0** knowledge/cognitive imports in
   `io/matrix/contract/**`.
4. **The bridge is the only crossing and it is bounded.** T9–T13 are the whole write path; the sink
   has **three** write sites and each writes a source, a `CANDIDATE` claim and an evidence link, and
   nothing else.
5. **The two implementations of one kernel agree bit for bit.** 31 tests
   (`NativeKernel*Test`, 0 failures) and 262 144 rows compared in the harness.
6. **The demonstration ran.** `docs-v2/engineering/W51-DEMONSTRATION.json`: **15/15** acceptance
   items `DEMONSTRATED`, one cycle of **12** trace phases, episode `ep-sig-w51-1`, status
   `COMPLETED`.

### Not proven, and not claimed

1. **The audit is static.** It resolves the calls the compiler emitted. It does not cover
   reflection, JNI, a native image built from a different class set, or a class loaded by name at
   run time (`W51-AUDIT.md` §8.1). The gate has the same limit.
2. **The cross-process claim was proved by the gate and by the bridge's client and server, not by a
   demonstration.** The demonstrations of this wave were run **in one process**, deliberately: the
   cross-process claim and the component claim are proved separately, because mixing them would
   have made both weaker (`W51-AUDIT.md` §8.4). **No end-to-end run of two processes with the bridge
   in the middle is recorded.**
3. **The test population is unknown.** The full `matrix-core` suite is not run in this repository's
   practice — it needs the owner's approval because of the OOM risk. The baseline is a set of
   **scopes**, each verified on a named commit; `docs-v2/engineering/TEST-CATALOGUE.yml` is the
   registry.
4. **The route census counts annotations, not traffic.** 217 annotated routes in chain A and 28
   served routes in chain B are what the compiled classes declare. Nothing here says which of them
   receives a request.
5. **Chain B's default mode is still `stub`.** `MinimalHttpServer.java:127-128`, `:300-304`: unless
   `MATRIX_MODE=production` is set, `/v1/analyze` answers from an 87-line `StubBrainCycle`. **T14–T17
   describe the production mode, not the default.**
6. **The bridge is not wired into chain B's server.** `ProductionBrainClient.attachBridge` exists
   (`ProductionBrainClient.java:124`) and `BridgeClientConfiguration.publisher()` builds the client
   (`BridgeClientConfiguration.java:61-62`), but **`attachBridge` has no caller anywhere in the
   tree** — the only occurrence of the name is its own declaration, and `MinimalHttpServer` does not
   mention the bridge at all. **T16 is the implemented path; the server that would use it is not
   yet the one that boots.**
7. **The role "state of the world" and the role "meta-memory" have no store** (§6), and no claim is
   made that they should have one.

---

## 9. Where the evidence lives

| the claim | the evidence |
|---|---|
| the transitions of §2 | the `file:line` in the table; each row's caller is in `src/main/java` |
| the route census (217 / 9 / 28) | `scripts/w45-chain-isolation-check.sh`, the G5 rule |
| the isolation (22 checks, 0 failed) | the same script, run 2026-11-08 |
| the negative controls C1–C4 | `scripts/w45-chain-isolation-selftest.sh` |
| the contract package's purity | gate G7, and the independent source check in §3 |
| BRIDGE-1 … BRIDGE-10 | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` §4.2 and `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` §6 |
| the 15/15 demonstration and the 12 phases | `docs-v2/engineering/W51-DEMONSTRATION.json` |
| the classification of §7 | `docs-v2/engineering/W51-AUDIT.md` §7 |
| the Java/C boundary | `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md` |
| what runs, what it binds, what it serves | `docs-v2/architecture/RUNTIME-TOPOLOGY.md` |
