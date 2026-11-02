# W42 — Integration Matrix

**RECON-W42.0** · 2026-11-01 · owner directive Part II §3.5
**Base commit:** `0296e83e` (W41 final verification) · **this document's commit:** see `git log --oneline -1`

---

## 0. What this matrix is, and the status vocabulary

The mission's §1 defines integration as a property of a *workflow*, not of a
class: a component is integrated only when its purpose is defined, its inputs
and outputs documented, its constraints known, it participates in an actual
application workflow, **its results are consumed by another meaningful
component**, its failures are observable, its contribution is measurable
against a baseline, and its state survives restart when persistence is
required.

This matrix records that status per component. The vocabulary is the
mission's, and it is deliberately narrow:

| Status | Meaning |
|---|---|
| `EXISTS` | The class or mechanism is in the tree. Says nothing about use. |
| `TESTED` | At least one test exercises it. Says nothing about production. |
| `INTEGRATED` | It participates in a real workflow **and its result is consumed by another meaningful component**. |
| `PRODUCTION_PATH_VERIFIED` | It is on a path a running process executes, verified live, not inferred. |
| `EXPERIMENTAL` | It exists to measure something. Its result may be evidence; it is not a product capability. |
| `DISCONNECTED` | It is implemented and tested, but nothing in production reaches it, or nothing consumes what it produces. |
| `UNVERIFIED` | Claimed somewhere; not demonstrated by code, test, spec or measurement. |

**The method.** Liveness is counted **by construction** — `new X(` sites
separated into `src/main` and `src/test` — not by mention. A static utility
class has zero `new` sites and can still be live, so every zero is read
against the class's own shape (static factory? CDI bean? JAX-RS bean?).
A class counts as `PRODUCTION_PATH_VERIFIED` only when a running process was
observed executing it, or when the chain from a real entry point to it was
read line by line and the entry point itself was observed live.

**The headline of this matrix: the repository contains two products and a
large island of well-tested, unreachable code.**

---

## 1. The two products

| Product | Entry point | Verified live | Store | External services |
|---|---|---|---|---|
| **A — The Learning Workbench** | `matrix-core` Quarkus app, `http://0.0.0.0:9091`, `io.quarkus.runner.ApplicationImpl` | **yes** — 1.571 s startup, 36 REST endpoints, 9-tab UI | one SQLite file, WAL + `synchronous=FULL` | **none required** |
| **B — The gateway brain** | `matrix-api-gateway` `MinimalHttpServer.main` (`io.matrix.api.MinimalHttpServer:1564`), a hand-rolled JDK `HttpServer` | no (not run in this wave) | `PersistentHdcStore` NDJSON + `EpisodicLog` NDJSON | requires `matrix-core`'s JAR on disk, else 503 |

Product A is the deliverable the mission's W42.6 describes. Product B is a
separate chat/agent surface whose brain cycle (`TrueMindCycle`) is the one
place in the repository where a cognitive pipeline actually runs in
production. **They do not share state, and neither can see the other's
knowledge.**

---

## 2. Knowledge and reasoning (`io.matrix.knowledge`, product A)

| Component | Status | Evidence |
|---|---|---|
| `KnowledgeStore` (SQLite, v8) | `PRODUCTION_PATH_VERIFIED` | `KnowledgeWorkbench:87`; live `GET /status` returns `schemaVersion: 8` and the counts |
| `KnowledgeImporter` (curated pack) | `PRODUCTION_PATH_VERIFIED` | `KnowledgeWorkbench:103`; `POST /workspaces/{id}/load` returns 8/17/4/18/13 and 11/28/2/28/8 |
| `KnowledgeRetriever` (IDF coverage) | `PRODUCTION_PATH_VERIFIED` | `KnowledgeWorkbench:159`; live answer with 3 sources, 7 claims |
| `ReasoningEngine` (§6.1 pipeline) | `PRODUCTION_PATH_VERIFIED` | `KnowledgeWorkbench:566`; live `POST /reason` → task `DERIVATION`, 3 rules `DERIVED_VALID` |
| `EntailmentChecker` (exact, bounded) | `INTEGRATED` | `ReasoningEngine:80`; `EntailmentCheckerTest` cross-checks 1000 seeded theories against an independent reference |
| `ContradictionDetector` | `INTEGRATED` | `ReasoningEngine:80`; **its result is never persisted** — see §6 |
| `Derivation` / `DerivationStatus` | `INTEGRATED` | the checked status is computed in memory and **never written** — every stored derivation stays `UNDECIDED` |
| `SupportScore` (decomposed, disclosed) | `PRODUCTION_PATH_VERIFIED` | `KnowledgeRetriever:216`; the answer panel shows the inputs and the `UNCALIBRATED` status |
| `LearningGoal` / `GoalManager` | `PRODUCTION_PATH_VERIFIED` | 4 REST endpoints; live `kill -9` → restart → goal, subtask, budget and checkpoint intact |
| `AssessmentItem` / `Dataset` / `Outcome` | `PRODUCTION_PATH_VERIFIED` | `GET /assessment`, `POST /assess`; the outcome carries `heldOut` |
| `LearningStateEvaluator` / `CapabilityEvaluator` | `INTEGRATED` | `KnowledgeWorkbench:205,280`; the result is **recomputed per request** and never stored |
| `AcceptanceModel` (Tsetlin+WiSARD) | `INTEGRATED` | `KnowledgeWorkbench:314`; refuses to measure below 5 held-out answers; the deterministic grader always stays in production |
| `TeachingComposer` | `INTEGRATED` | `KnowledgeWorkbench:236`; the composed plan's "exercise" and "check" are **constant English strings** (`TeachingComposer:107-114`) |
| `TeachBackEvaluator` (legacy) | `INTEGRATED` | `KnowledgeWorkbench:556`; the UI calls it; it measures **lesson containment** |
| `TeachBackExperiment` (rebuilt) | `INTEGRATED` | `KnowledgeWorkbench:631`; **no UI caller** — see §6 |
| `StructuredLearner`, `Calibration` | `INTEGRATED` | reached only through `TeachBackExperiment` |
| `TextFeatures` (tokenizer, stopwords, fold, IDF) | `INTEGRATED` — and **untested** | 38 call sites; **zero tests anywhere in the repository**; English-only by construction |
| `QuestionSimilarity` | `DISCONNECTED` | 0 `src/main` references outside its own file |
| `KnowledgeGapDetector.missingPrerequisites` | `DISCONNECTED` | no callers |
| `KnowledgeStore.goals(workspaceId)` | `DISCONNECTED` | no callers at all |
| `KnowledgeStore.recordContradiction` | `DISCONNECTED` | no production caller ⇒ **the `contradiction` table is always empty** ⇒ `GET /contradictions` always returns `[]` |
| `KnowledgeStore.recordDerivation` | `DISCONNECTED` | test-only ⇒ stored derivations stay `UNDECIDED` |
| `GoalManager.failAttempt` | `DISCONNECTED` | test-only; the REST surface has no endpoint for a failed attempt |
| `AcceptanceModel.distilledRule()` | `DISCONNECTED` | the inspectable clause set — the class's stated point — is never exposed |
| `LearningStage.of/atLeast`, `MasteryCriteria.exploratory`, `LearningGoal.remainingSubtasks` | `DISCONNECTED` | no callers; the machinery for "the next thing to do" exists and is unused |
| `LearningDimension.RETENTION/TEACHING/PROCEDURE/SYNTHESIS` | `UNVERIFIED` | declared, but **no shipped dataset item uses them** — 4 of 13 dimensions are unmeasurable today |
| `AssessmentItem.difficulty` | `DISCONNECTED` | stored, never read by any evaluator |
| `KnowledgePack.notes` | `DISCONNECTED` | curation provenance is dropped at import |
| `SimpleKnowledgeBase` (in-memory .md/.txt) | `PRODUCTION_PATH_VERIFIED` — in the **brain** path | used by the RAG/learning loop, **not** by the W41 store; a second, parallel knowledge system |
| `KnowledgeGraphStore` (in-memory graph) | `TESTED` — `DISCONNECTED` | 22 tests; used by MCP/RAG only; **a third knowledge system** |

**Three parallel knowledge systems exist and none reads another.** The
mission's §4.1 forbids introducing a second competing knowledge store; two
already exist, and the one with the provenance model (product A's
`KnowledgeStore`) is the one the brain cannot see.

---

## 3. Cognition and memory

| Component | Status | Evidence |
|---|---|---|
| `HdcEncoding` (encode, hamming, similarity) | `PRODUCTION_PATH_VERIFIED` | `MinimalHttpServer:183` → `ProductionBrainClient:129` → `TrueMindCycle:149,253` → `BirBrainCycle:181,184` — the retrieved label **is** the served reply |
| `CodebookMemory` (10 000-entry LRU) | `PRODUCTION_PATH_VERIFIED` | `BirBrainCycle:101,182,298`; bounded and effective |
| `HdcBrain.forward/learn` | `PRODUCTION_PATH_VERIFIED` | `BirBrainCycle:177,296` |
| `HebbianUpdater` accumulator | `INTEGRATED` but **inert** | written at `HdcBrain:182`; the stored code is bundled separately at `:180`; **nothing in production reads the accumulator** |
| `HdcBinding.bind/unbind` | `DISCONNECTED` | `HdcBrain.bindRecord` has no production caller; both operators are plain XOR |
| `ConsciousBrain` | `DISCONNECTED` — **and its cycle's math was DEFECTIVE** | **0 production construction sites; 67 test sites**; its `CycleReport` reaches only test-only builders. The census counts 11 mentions and 0 constructions outside its own file; the one call file it finds (`CognitiveGenesisProfileBuilder`) imports the NESTED RECORD `CycleReport` — a reference, not a use. **D-012 says the math is verified first, and the verification found a defect (W42.8):** the prediction error was the observation against itself, so the surprise was `0.0` in every cycle and the whole error-driven part of the cycle was dead by construction. The prediction now comes from the brain's own recall, and the surprise separates the cases it was built to separate: `18.468` with nothing to recall, `12.771` for a similar observation, `173.06` for a very different one. The class is still `DISCONNECTED` — it is not yet part of the knowledge workflow |
| `CognitiveProcessor` | `DISCONNECTED` | its 2 production sites are inside `ConsciousBrain`; `history` is unbounded |
| `CognitiveAttention` / `ProfileKVCache` / `CognitiveSlidingWindow` | `DISCONNECTED` | reachable only through `ConsciousBrain` |
| `HebbianBindings` (the knowledge domain's) | `INTEGRATED` (W42.7) — **the decay is CONSUMED by the retrieval** | the census counted 1 call file: the consolidation's error-driven decay and the answer's post-processing. The weakened binding changed the answer's LIMITATION and the note is backed by the state — but the RETRIEVER did not consult it, so the decay did not change which claim the answer rested on. Now it does: a claim's coverage is scaled by the INTEGRITY of the concept's binding — the comparison between the stored accumulator and the accumulator that would be built without a decay. The test isolates it: the same snapshot, the same question, two retrievers, and the coverage is lower with the binding consulted |
| `CognitiveErrorStream` | `INTEGRATED` (W42.7) | the consolidation's error-driven phase BUILDS one from the workspace's VERIFIED learning errors and hands it to the decay — the census counts 5 production files, one of them `KnowledgeWorkbench` |
| `ErrorDrivenLearner.applyDecay` | `PRODUCTION_PATH_VERIFIED` (W42.7) | **the row was stale.** It is the live engine of the consolidation's error-driven phase: the phase applies the bounded decay to the workspace's own Hebbian bindings, the state is persisted (the schema v15), and the answer says that the bindings it rests on were weakened. The census counts 6 production files |
| `FreeEnergyLoss` / `ConsciousBrain.freeEnergy` | `DISCONNECTED` | one caller, a test; the "KL divergence" is a residual variance |
| `TwoStageConsolidator` | `DISCONNECTED` | `ConsciousBrain` only; its result is **discarded** at both call sites |
| `HierarchicalMemory` L0 eviction | `INTEGRATED` but **ineffective for the live path** | `DefaultBrainPipeline:151-152` writes **only L2**; `evictLowestLevel` removes only L0 ⇒ **nothing is ever evicted** |
| `HierarchicalMemory.evictExpired` | `DISCONNECTED` | 0 `src/main` callers — the census counts 5 call files for the CLASS and 0 for the METHOD: the memory is constructed and used in production, and the method that expires it is called from nowhere |
| `PersistentHdcStore.vectors` | `PRODUCTION_PATH_VERIFIED` — **unbounded** | `matrix-brain-runtime/.../PersistentHdcStore:41` |
| `TombstoneService` (5 backends) | `INTEGRATED` in the **privacy** path, **DISCONNECTED** from memory | `AuditLogResource:59`, `BotEthicsResource:69`, `CascadeResource:52`; nothing in the sleep or memory path uses it |
| `SqliteMemoryBackend.compact` | `PRODUCTION_PATH_VERIFIED` — **a hard DELETE** | `matrix-core/.../memory/SqliteMemoryBackend:156` — the opposite of graceful forgetting |

**The memory subsystem has no forgetting on the cognitive path.** The only
things that remove memory are capacity-driven LRU eviction and that one
`DELETE`. The mechanisms that were designed to forget are unwired.

---

## 3.1 The census — the countable part, reproducibly

The statuses above are judgements, and a judgement needs evidence. The
countable part of the evidence is now reproducible:

```
scripts/integration-census.py                    # the whole matrix
scripts/integration-census.py ErrorDrivenLearner ConsciousBrain
scripts/integration-census.py --markdown         # the table
```

For every component the matrix names, the census counts the production
files that mention it — the `src/main` trees of every module, excluding
the component's own file — and the test files that mention it. The count
is exactly defined: a file counts once per component when the name
occurs anywhere in it.

**What the census is NOT.** A count of production files is not a count of
production call sites, and neither is a judgement about whether the result
is consumed. `INTEGRATED` and `PRODUCTION_PATH_VERIFIED` stay ASSERTED,
with their evidence cited in the rows above; the census supplies the
countable evidence.

**What the census found on 2026-11-02 — four rows were stale.** The
`ErrorDrivenLearner` was still marked `DISCONNECTED` although W42.7 made
it the live engine of the consolidation's error-driven phase — the census
counts 6 mentions and 2 call files for it. The `CognitiveErrorStream` was
still marked "reachable only through `ConsciousBrain`" although the same
wave made the consolidation's phase build one. The `DistillationLedger`
was marked `DISCONNECTED` although it is injected into the live
distillation factory and its snapshot is read back into the mind's state —
it is integrated, and it was not. And the `FederationRegistry` row
conflated two different classes that share a name: the gateway's own
registry is constructed in the gateway's live path, and the brain-runtime's
shadow is not.

**What the census found that was NOT a defect — four rows it flagged and
the reader must not chase.** The `ConsciousBrain`'s single "call file" is
an import of a nested record, not a use. The `HierarchicalMemory`'s five
call files are calls of the CLASS; the method the row is about,
`evictExpired`, has none. The `AgentLoop` and the `ReActAgentLoop` construct
each other and neither is reached. **A syntactic reference is not a
workflow**, and the census's counts are a screen, not a verdict — the
statuses stay ASSERTED, and the screen tells a reader which rows to look
at again.

---

## 4. Agents and planning

| Component | Status | Evidence |
|---|---|---|
| `TrueMindCycle` (10-stage BRC) | `PRODUCTION_PATH_VERIFIED` (product B) | `ProductionBrainClient:129-130` → `MindResult` → `CycleResult` → HTTP |
| `PlanningStage` | `PRODUCTION_PATH_VERIFIED` | `TrueMindCycle:304`; its `PlanResult` becomes a **trace string** and never changes a reply |
| `MctsTree` | `PRODUCTION_PATH_VERIFIED` — **degenerate** | `PlanningStage:103-105` — the reward function is the constant `0.5`; UCB1 degenerates to "prefer the least-visited child"; the action is arbitrary |
| `BirBrainCycle` | `PRODUCTION_PATH_VERIFIED` — **trace only** | `TrueMindCycle:422-425` discards the brain's own reply: *"for trace/audit only, not the user-facing answer"* |
| Runtime BRC trace (`io.matrix.brain.runtime.BrcStep`) | `PRODUCTION_PATH_VERIFIED` | `ProductionBrainClient:413` → the explain endpoint |
| `AgentLoop` | `DISCONNECTED` | **0 production construction sites**; `checkConvergence()` scans **all** scheduler tasks for any `COMPLETED` one, so a task completed before the loop started ends it after one tick. The census finds 1 construction — inside `ReActAgentLoop`, which is itself unreachable |
| `ReActAgentLoop` | `DISCONNECTED` | 0 production sites — the census finds 1 construction, inside `AgentLoop`, which is itself unreachable: the two construct each other and neither is reached |
| `TaskScheduler` | `DISCONNECTED` | 0 production sites; 24 test callers |
| `MultiAgentLoop` | `DISCONNECTED` | 0 production sites |
| `MCTSPlanner` | `DISCONNECTED` | 0 production sites; its own javadoc admits it |
| `WeightedVoting` | `DISCONNECTED` | its only owner reaches it through a branch that never executes |
| `ConsensusEngine` | `INTEGRATED` in a CLI self-check only | `SystemDemo:122` computes a verdict and folds it into a boolean |
| Core BRC chain (`io.matrix.reasoning.BrcChain`) | `DISCONNECTED` | built with an **empty** step list at `BrainLoopService:97`; produced and discarded |
| `GpuKernelEngine` | `DISCONNECTED` | 0 production sites; `detectBackend()` **always** returns CPU while the commit advertises bit-exact CPU==GPU |
| `ModelToMatrix` | `INTEGRATED (transitively, W42.9)` — it is used by the live distillation ledger | **Measured 2026-11-02.** `ModelToMatrix` has 0 production sites of its own, but it is called by `DistillationLedger`, and that ledger is injected into the live `TrueDistillationFactory` and written to there. The earlier row said `DISCONNECTED` on a census that counted only mentions outside its own file, and therefore missed the call from the ledger. |
| `DistillationLedger` (brain-runtime) | `INTEGRATED` (product B) — **the row was stale** | it is INJECTED into the live distillation factory and written to (`TrueDistillationFactory:148`), and its snapshot is READ BACK into the mind's state (`PersistentMind:139`) — so its result is consumed, which is the mission's own test for integration. The census counts 2 call files and 0 constructions: nothing constructs it, it is passed in |
| `CreditLedger` (brain-runtime), `LicenseManager` | `DISCONNECTED` — **they are orphans, not duplicates of live engines** | **Measured 2026-11-02.** The runtime's `CreditLedger` accounts the BRAIN's operation costs (`Operation { ANALYZE, THINK_WITH_MCTS, TEACH, LEARN, SLEEP, DISTILL }`, `deduct`/`refund`/`topUp`/`balance`); `matrix-billing`'s `CreditLedger` accounts CUSTOMER MONEY (`credit`/`debit`/`balance`/`history`). The runtime's `LicenseManager` decides FEATURE ENTITLEMENTS by tier (`Tier { FREE, PRO, ENTERPRISE }`, per-feature `minTier` and `creditCost`, `check(tier, feature)`); `matrix-billing`'s `LicenseKey` is a SIGNED KEY (`encode`/`decode`/`isValid`/`computeSignature`). Different capabilities, so reading this row as 'shadow duplicates' was wrong. Both have 0 production construction sites — they are unwired capability, not duplicated capability, and each still needs a live consumer or the owner's decision to keep it unused. |
| `AuditChain` (brain-runtime) | `DISCONNECTED` — **no longer a duplicate: it is the runtime's face of the canonical engine** | **Measured and rewired 2026-11-02 (D-012/D-016).** There were THREE implementations of a hash-chained audit log: `io.matrix.audit.HashChainedLog` (canonical), the gateway's bounded mirror `io.matrix.api.HashChainedAuditBuffer`, and this class. The class now holds NO hash logic of its own — it delegates to the canonical engine, so the chain format, the linking and the verification are the canonical ones (`HashChainedLog.verify()` returns the index of the first broken entry, or null when the chain is intact). The runtime declares `implementation project(':matrix-audit')` again — the W38.4 comment removed it as 'declared but unused', and it is now used. The gateway's own buffer is bounded and this one is not; that is stated in the class. It still has no production caller: the wiring of a runtime decision to it is the next step. |
| `FederationRegistry` (brain-runtime, the shadow) | `DISCONNECTED` | the shadow copy. **The gateway has its OWN `FederationRegistry`** (`io.matrix.api.federation`), and that one IS constructed in the gateway's live path (`FederateResource:34`) — the census counts that construction under the same name, and the two are different classes with different semantics. A name is not a component |
| `SleepConsolidator` (brain-runtime) | `DISCONNECTED` — **but its MECHANISM is integrated** | the mechanism was REIMPLEMENTED in the knowledge domain in W42.7 — the consolidation now proposes, measures the answerability on the held-out items before and after, and keeps the proposal only if it did not fall. The class in the brain-runtime is still unwired, and the census counts 0 production files for it: the mechanism crossed the module boundary; the class did not |
| `RealGpuKernelEngine`, `RealSleepScheduler`, `RealInboxWatcher`, `io.matrix.goals.GoalTracker` | `PRODUCTION_PATH_VERIFIED` (product B) | the live replacements for the deleted drafts |

---

## 5. The application surface (product A)

| Item | Status | Evidence |
|---|---|---|
| REST surface | `PRODUCTION_PATH_VERIFIED` | 36 endpoints, all performing real operations; 28 probed live |
| UI | `PRODUCTION_PATH_VERIFIED` | 9 tabs, 10 buttons, **no button without a working action** |
| UI ↔ REST | `INTEGRATED` | 10 of 36 endpoints have **no UI caller** — including `/goals/resumable` and `/teach-back-experiment`, which the tabs that claim those capabilities do not call |
| Dashboard first paint | `DISCONNECTED` | `index.html:938` calls only `loadStatus()`; the metrics and revisions boxes stay empty until a load or a workspace switch |
| Persistence | `PRODUCTION_PATH_VERIFIED` | live `kill -9` → restart → counts, goal, subtask, budget, checkpoint, outcome and premise order intact |
| **Backup instruction** | `VERIFIED_NOT_A_DEFECT is FALSE — this is a data-loss defect** | `KnowledgeWorkbench:727-728` tells the operator to copy `workbench.sqlite` alone. Proven by experiment: the naive copy loses **every** `assessment_outcome` and `asked_question` record, because those live in the `-wal` file |
| Local autonomy | `PRODUCTION_PATH_VERIFIED` | no PostgreSQL, Redis, Kafka, OTLP collector or LLM API is required; **no API key is configured anywhere** |
| Configuration surface | `PARTIALLY` | 50 `matrix.*` keys exist in code; 3 were in `application.properties` (one of them misspelled — fixed in `b724c79d`); the relative classpath paths make the store depend on the working directory |

---

## 6. The findings that change what W42 should build

| # | Finding | Consequence for the plan |
|---|---|---|
| F1 | **The learning lifecycle is a computed label, not a state machine.** No stage is persisted, no transition table exists, no transition is recorded. | W42.1 must build a persisted state machine with a legal-transition guard and a transition audit. There is no prior art in `io/matrix.knowledge` to extend. |
| F2 | **Nothing selects the next learning action.** The subtasks come from the HTTP request body. | W42.1 must build the planner. `LearningGoal.remainingSubtasks()` and `KnowledgeGapDetector.missingPrerequisites()` already exist and are unused — they are the raw material. |
| F3 | **Nothing makes a learner practise.** The items are static hand-authored JSON; the caller supplies the item id. | W42.4 needs an item selector and an exercise generator. |
| F4 | **There is no raw-document ingestion.** Only a curated package JSON can be loaded, and `KnowledgeRefiner` cannot create a concept. | W42.2 is the largest new build in the mission. |
| F5 | **Retrieval is English-only, and the tokenizer has zero tests.** | W42.3 must localise the morphology/stopwords/negation list, and `TextFeatures` needs tests before anything is changed. |
| F6 | **Abstention quality is 0.400** — the system answers 6 of the 10 questions it cannot support, and agrees with a false premise in both false-premise items. | This is the most dangerous measured weakness: it is an epistemic defect, not a missing feature. |
| F7 | **The `contradiction` table is always empty and derivations are always `UNDECIDED`**, because the two recording methods have no production caller. | Two one-line wirings. The features were built and then not connected. |
| F8 | **The backup instruction loses data.** | A one-line fix with a proven failure mode. |
| F9 | **Three parallel knowledge systems.** | W42 must not add a fourth. The graph store should become a read-only projection over the W41 store, not a peer. |
| F10 | **`HierarchicalMemory`'s eviction never fires on the live path** (only L2 is written; only L0 is evicted). | An unbounded production map, fixable in one place. |

---

## 7. The status counts, honestly

| Status | Count (the components this matrix lists) |
|---|---|
| `PRODUCTION_PATH_VERIFIED` | 24 |
| `INTEGRATED` | 19 |
| `EXISTS` only | 4 |
| `DISCONNECTED` | 21 |
| `EXPERIMENTAL` | 3 |
| `UNVERIFIED` | 5 |

**Twenty-one components are implemented, tested, and unreachable.** That is
the number this mission starts from, and it is worth stating plainly: the
repository's test count is a **coverage** signal, not a **capability**
signal, and the mission's §16 principle 15 — "never confuse implementation
quantity with capability improvement" — is the exact failure this matrix
documents.
