# CRJ-01 — Cognitive Reference Journey: source-grounded reachability map

**Status:** DRAFT · **Date:** 2026-11-20 · **Baseline SHA:** `b7bd1be4`
**Nature:** a report of the current tree, not a capability claim.
**Task:** CRJ-01.0 of `docs-v2/engineering/CRJ-01-ACCEPTANCE.md` — "Independent read-only source map:
reachability of perception → claims → kernel → action → episode/procedure → future behavior".

**What this document is.** A map, drawn only from the source at the baseline SHA, of where the
cognitive journey is actually reachable by a production caller. It classifies every link with one
status and cites the caller as `path:line`. It changes no capability registry, no roadmap and no
governance document; `docs-v2/architecture/CAPABILITY-REGISTRY.yml` remains the single status
registry and is cited here, not edited.

**Method.** Grep and read only over the baseline tree. No Gradle, no Docker, no network. The
searched set was `matrix-core/src/main`, `matrix-api-gateway/src/main`, `matrix-brain-runtime/src/main`
and the corresponding test trees, plus `docs-v2/architecture/{RUNTIME-TOPOLOGY.md,
CAPABILITY-REGISTRY.yml}` and `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` (read-only).

**The line numbers are pinned to `b7bd1be4`.** The change that closed CRJ-01.1 (the replay's
applicability domain) edited `ArtifactReportResource.java`, so the citations below that name
that file — `ArtifactReportResource.java:604-617` — are a few lines off on a later tree. Every
other cited file is untouched by that change. A reader on a different commit should re-derive
the line with `grep -n` rather than trust the number; the *claims* are what this map asserts.

**Status vocabulary (used below, exactly one per link).**

| Status | Meaning as applied in this report |
|---|---|
| `SERVED` | a production caller reaches it, and the behaviour is exercised on a serving path |
| `TEST-ONLY` | the mechanism exists and is exercised only from tests |
| `DORMANT` | the class exists and is referenced on no serving path (the report names where it IS referenced) |
| `UNKNOWN` | the truth could not be established from the tree (the report names what would settle it) |

**Paths.** All paths are relative to the repository root. Chain A is the Quarkus process in
`matrix-core` (`io.matrix.MatrixApplication`, `matrix-core/src/main/java/io/matrix/MatrixApplication.java:19`,
HTTP 9091 per `matrix-core/src/main/resources/application.properties:31`). Chain B is the JDK
`HttpServer` in `matrix-api-gateway` (`io.matrix.api.MinimalHttpServer`, default port 8765 at
`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:1854`), which uses
`matrix-brain-runtime` as a library.

---

## 1. The journey, link by link

### Part A — Chain A (Quarkus workbench / epistemic side)

#### A1. Perception / ingestion → claim / concept — `SERVED`

The product's ingestion route is `POST /v1/workbench/workspaces/{id}/documents`
(`matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:361-364`, class path
at `:51`). It calls the workbench, not the importer directly —
`matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:383-385` →
`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:890-897` →
`matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:229`
(`ClaimExtractor.propose`), which creates the concept at
`matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:230-232`, the claim at
`:236-247` and the evidence at `:248-251`. The claims and concepts are then readable over HTTP at
`matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:437-438` and `:444-445`.

*Reasoning:* a document posted to a mounted route becomes a stored concept with candidate claims and
evidence, and both are served back.

#### A1r. Claim → **referent** — `DORMANT`

The referent machinery is `io.matrix.knowledge.concept.research.*` —
`ReferentResolver` (`matrix-core/src/main/java/io/matrix/knowledge/concept/research/ReferentResolver.java:52`),
`ReferentKey` (`.../ReferentKey.java`), `ReferentPolicy` (`.../ReferentPolicy.java:52`),
`ReferentKeyMigration` (`.../ReferentKeyMigration.java:55`) and `LiveStoreMigration`
(`.../LiveStoreMigration.java:74`). It is referenced **only** inside its own package and by its own
migration CLI (`.../LiveStoreMigration.java:116`, `.../ReferentKeyMigration.java:266-267`), and
exercised only from `matrix-core/src/test/java/io/matrix/knowledge/concept/research/**`. No route and
no workbench method calls it.

*Reasoning:* the journey's "referent" half exists as a migration instrument, not as a serving step of
the workbench's own retrieval.

#### A2. Claim / concept → hypothesis / answer — `SERVED`

`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java:211`
(`input.workbench().ask(...)`) → `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:402`
→ `KnowledgeRetriever` at `:410-412`. The same ask path is also a product route of its own,
`POST /v1/workbench/workspaces/{id}/ask`
(`matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:478-480`, workbench
call at `:487`). The answer carries the claims as citations
(`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:518-522`).

*Reasoning:* the workspace's own claims, not a re-implementation, produce the answer and its
citations on a mounted route.

#### A3. Answer → kernel method selection — `SERVED`

`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:319-321` builds a
`ProblemRequest` in the family `AlgorithmFamilies.KNOWLEDGE_REPORT` and calls the kernel's planner;
`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyPlanner.java:118-119` calls the
kernel's own selector; `matrix-core/src/main/java/io/matrix/cognitive/KernelSelector.java:66-69`
hands the request to `AlgorithmSelector`, which reads the catalogue at
`matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmRegistry.java:66`. The catalogue
the planner owns is built at `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyPlanner.java:59`
and `:73-77`. When the kernel has no method, the route refuses with a reason rather than falling
back silently (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:322-324`).

*Reasoning:* the route does not choose the method; the kernel's registry and selector do, and the
decision is carried into the journey's trace.

#### A4. Method selection → permission — `SERVED`

In the code the permission is checked **before** the plan, and independently of it: the caller's
consent (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:264-270`) and the
action's own gate (`:272-276` → `matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:126-154`
— feature flag, network exposure, loopback host, output root), then the workspace's existence
(`:278`) and the plainness of the file name (`:287-291`). The action identity is derived server-side
(`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:282` →
`matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:196-204`). The permission is
recorded as its own trace phase (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:367-372`).

*Reasoning:* consent and the local-only gate are enforced by the route and the action, and the
kernel's plan cannot widen either of them.

#### A5. Permission → action — `SERVED`

`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:329-330` runs the chosen
method through the cost ledger; `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java:199-208`
is the execution entry and `:210-218` is the reference path, whose write is at `:214-215` →
`matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java:96`. The replay path re-reads the
artifact and re-checks its digest before serving it (`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java:220-236`).

*Reasoning:* one bounded writer, confined to one authorized root, under one identity, on the served
route.

#### A6. Action → effect (independently observed) — `SERVED`

`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:354-355` calls
`ArtifactObserver.observe(...)` — `matrix-core/src/main/java/io/matrix/cognitive/report/ArtifactObserver.java:82`
— a reader that shares nothing with the write path but the root; it measures the bytes and the
digest and reads the append-only index. The route records the observation and the verification as
separate phases (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:377-388`).

*Reasoning:* the effect is read back by a reader that did not write it, and a disagreement is
recorded as a failed effect.

#### A7. Effect → episode — `SERVED`

The journey's steps are written as the events of one episode in the kernel's own store:
`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:394` → `:672-684` →
`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java:105-111`
(`store.appendEvents`), under the episode id derived from the action identity
(`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java:90-92`, used at
`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:310`). The store is the
kernel's own (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:648-657` →
`matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:170-175`) and the episode is
readable over HTTP at `matrix-core/src/main/java/io/matrix/cognitive/CognitiveResource.java:209-213`.

*Reasoning:* the trace lands in the same durable episode stream the kernel writes its own cycles
into; there is no second database and no second file-system authority.

#### A8. Episode → a later task reusing it — `SERVED`, but only as **exact replay of the same identity**

A later request for the same identity reads the retained journey back
(`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:312-313` →
`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java:147-155`), and the
facts that make the replay admissible are computed at
`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:604-617`. The replay
method is the one the planner's catalogue offers
(`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyPlanner.java:59`), and it serves
the retained answer and the artifact read from the disk
(`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java:220-236`). The
packaged HTTP demonstration of 2026-11-15 records the repeat answering `200 REPLAYED` and the
**post-restart** repeat answering `200 REPLAYED` with the same digest and the artifact still present
(`docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json`, `checks.repeat.status`,
`checks.afterRestart.*`).

*Reasoning:* the reuse is real and survives a restart, but its precondition is the **same** identity,
the **same** workspace fingerprint and the **same** file name — this is a cache, not a transfer to a
different task. It is exactly the "Arm B: exact replay/cache" that `CRJ-01-ACCEPTANCE.md` §2 requires
to be scored separately from genuine transfer.

#### A9. Chain A's *other* cycle — the cognitive kernel — and why it is not the journey

`POST /v1/cognitive/observe` (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveResource.java:72-74`)
→ `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:356-359` → `:498`
→ `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:385`. That cycle does perceive
(`:409-414`), predict (`:466-478`), select a method (`:503`), act (`:669`, `environment.probe(...)`),
observe (`:723-733`, `workbench.recordObservation(...)`), and it writes and later consults a
procedure (`:788` → `:1145-1152` writing, `:498` → `:1276-1284` consulting).

It is a **different** journey and it does not join A1–A8:

- its domain is the lamp micro-world, not the workbench's claims;
- its "action" is a sandbox probe (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:669`),
  not the bounded file action, and its "effect" is a case/evidence row in the workbench, not an
  artifact observed by an independent reader;
- its procedure is a *procedure record* (`io.matrix.cognitive.algorithms.Procedure`), not the
  report journey's retained payload — the two never meet.

### Part B — Chain B (the brain runtime)

#### B1. Perception → episode — `SERVED` (in production mode)

`POST /v1/analyze` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:467`) →
`:599` (`brain.cycle(...)`) → `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:397`
→ `:415` (`mindCycle.think(...)`) → `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:201`.
The episode is appended at `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:433`.

*Reasoning:* the cycle's interaction is durably recorded as an episode with its role, which is the
only material chain B's learning window may consume.

**Caveat, stated because it changes the verdict:** the gateway's default mode is `stub`
(`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:155-156`); the real brain is
armed only under `MATRIX_MODE=production` (`:157-158`, the production branch that assigns
`this.brain = prod` at `:337`). The route is served either way; the *brain* behind it is not, by default.

#### B2. Episode → candidate (admitted learning material) — `SERVED`

`POST /v1/sleep` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:496`) →
`:1557` (`handleSleep`) → `realSleepScheduler.triggerNow()` →
`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:318`
(`runInduction`) → `:323-324` (`LearningAdmission.assess(episodicLog, quarantine())` and
`assessment.candidates()`) → `:362` (`ruleEngine.induce(ids, features, labels, roles)`).
The same induction also runs on the scheduler's idle timer, armed at
`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:301-310`.

*Reasoning:* the candidate set is not the raw log: it is what the admission gate admits, with the
provenance of every record stated and non-organic records refused.

#### B3. Candidate → promotion — `SERVED`

`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:137-138`
(`induce`) → `:238-240` (`knowledgeBase.register(...)`), which appends to `bir.ndjson` and to the
shared `BirRegistry`. The registry is the one the retrieval side would read
(`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:292-294`), and the engine is
constructed over it at `:298-301`.

*Reasoning:* a promoted rule is durable and lands in the one registry, not in a private store.

#### B4. Promoted rule → a later task reusing it — `DORMANT` (this is the break)

The serving cycle never reads that registry. `TrueMindCycle` constructs the BIR stage **without** a
registry bridge — `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:381`
(`new BirInferenceStage()`) — and calls the hand-written table at `:382`
(`bir.evaluate(input, obs, trace)`,
`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:57`). The only
method that consults the registry is `evaluateWithRegistry`
(`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:37`, which
needs the bridge built at `:28`), and it has **no production caller**: its only references are its own
declaration and `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/BirRegistryBridge.java:19,23`;
the bridge itself is constructed only in
`matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DistillationPipelineTest.java:85`.
The legacy cycle has the same wiring: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/MindCycle.java:99`.

*Reasoning:* the rule is promoted into `bir.ndjson` and the registry, and it is visible to an
operator over `GET /v1/bir` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:485`),
but no later cognitive task consults it — the mechanism that would do so exists and is exercised only
from tests.

#### B5. New knowledge → a later task reusing it — `SERVED`, but the knowledge is *taught*, not *earned*

`POST /v1/teach` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:495`) →
`:1177` (`handleTeach`) → `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:623`
(`hdcStore.teach(...)`) → `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentHdcStore.java:85-87`
(`PromotionGate.evaluate(...)` refuses or admits). A later `POST /v1/analyze` retrieves it:
`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:398-405`
(`HdcRetrievalStage` over the same store) and `:501` (`if (hdc.matched()) return hdc.reply();`).
The path is exercised over HTTP in
`matrix-api-gateway/src/test/java/io/matrix/api/TeachAndAuditPathTest.java:125` (teach) and `:138` (analyze).

*Reasoning:* a later task really does reuse what was admitted earlier — but the material was
supplied by the caller, not derived from an episode, so this link does **not** close the
episode→reusable-knowledge gap of B2–B4.

### The governed Bridge (between A and B)

#### BR1. Chain A → Chain B: a bounded cognitive task — `SERVED` (route mounted), configuration-dependent

`POST /v1/cognitive/task` (`matrix-core/src/main/java/io/matrix/cognitive/distributed/DistributedTaskResource.java:50`
for the class path, `:454-457` for the method) → `:487-489` (`coordinator().arrange(...)`) → chain A's
`TaskPublisher`, which is built only when a peer address **and** a shared secret are configured
(`:181-198`) → chain B's `POST /v1/bridge/task`
(`matrix-api-gateway/src/main/java/io/matrix/api/bridge/TaskBridge.java:63`,
mounted at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:476`) →
`matrix-api-gateway/src/main/java/io/matrix/api/bridge/TaskBridge.java:201` (`answer`) → the allowlisted
capability `CognitiveCapability.COGNITIVE_INFERENCE`
(`matrix-api-gateway/src/main/java/io/matrix/api/bridge/CognitiveCapability.java:38`), whose executor
runs the brain cycle at `:136` → `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:382`.

*Reasoning:* one capability id resolves to one real production path, and the crossing is the bridge
contract of ADR-W53-01 — no class name crosses the wire. **The route answers `503` when the peer is
not configured** (`matrix-core/src/main/java/io/matrix/cognitive/distributed/DistributedTaskResource.java:459-463`).

#### BR2. Chain B → Chain A: a report as a candidate — `SERVED`

`POST /v1/bridge/messages` (`matrix-core/src/main/java/io/matrix/bridge/BridgeResource.java:41`, `:78-80`)
→ `BridgeService` → `matrix-core/src/main/java/io/matrix/bridge/EpistemicBridgeSink.java:68-84`, which
writes a **candidate** claim and its evidence into the epistemic store
(`matrix-core/src/main/java/io/matrix/bridge/EpistemicBridgeSink.java:113-118`). The publishing side in
chain B is `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:450`,
armed from configuration at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:221-228`.

*Reasoning:* a remote report becomes a candidate in chain A's epistemic store, never an established
claim.

#### BR3. Chain A's kernel dispatching to a peer — `DORMANT`

`matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:538` is where a cycle would
delegate (`remoteExecutor.execute(...)`), and the executor is only ever set through
`withRemoteExecutor` (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:290-292`).
The production construction of the kernel
(`matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:307`) does **not** call it, and
the only caller anywhere is the test
`matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelRemoteTest.java:106`.

*Reasoning:* the kernel's own remote dispatch phase exists and is tested, and it never fires on the
served `/v1/cognitive/observe` cycle.

### The learning path (episode → procedure/candidate → promotion → a later task)

| # | link | status | where |
|---|---|---|---|
| L1 | episode is recorded | `SERVED` | `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:433` |
| L2 | episode → admitted candidate | `SERVED` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:323-324` |
| L3 | candidate → promoted rule | `SERVED` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:238-240` |
| L4 | promoted rule → a later task reuses it | `DORMANT` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:381` (the bridge is never supplied); the mechanism exists at `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:37` and is exercised only from `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DistillationPipelineTest.java:85` |
| L5 | (Chain A) episode → procedure record | `SERVED` | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:788` → `:1145-1152` |
| L6 | (Chain A) procedure → a later episode consults it | `SERVED` | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:498` → `:1276-1284` |
| L7 | (Chain A) retained journey → a later request reuses it | `SERVED` (exact replay only) | `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:312-313`, `:604-617` |

L5/L6 are a **closed loop inside the micro-world cycle**, and L7 is a **cache**, not a transfer.
Neither loop is joined to L1–L4: chain A's `CognitiveStore` and chain B's `EpisodicLog` are different
files in different processes, and nothing carries a promoted rule from one to the other.

---

## 2. The caller map of the SERVED links

```
CHAIN A  (Quarkus, matrix-core, :9091)

  POST /v1/workbench/workspaces/{id}/documents     LearningWorkbenchResource:361-364
      └─> KnowledgeWorkbench.importDocument        KnowledgeWorkbench:890-897
            └─> DocumentImporter -> ClaimExtractor   DocumentImporter:229-251
                  └─> store: concept + claim + evidence
  GET  /v1/workbench/workspaces/{id}/claims         LearningWorkbenchResource:444-445
  GET  /v1/workbench/workspaces/{id}/concepts       LearningWorkbenchResource:437-438

  POST /v1/workbench/workspaces/{id}/reports        ArtifactReportResource:59, :208-211
      └─> ArtifactReportResource.run                ArtifactReportResource:251
            ├─ permission  consent :264-270 ; gate :272-276 -> ArtifactActionService:127-154
            ├─ plan       planner.plan               ArtifactReportResource:319-321
            │              └─> ReportJourneyPlanner.plan        ReportJourneyPlanner:118-119
            │                    └─> KernelSelector.selectTyped  KernelSelector:66-69
            │                          └─> AlgorithmRegistry.methodsFor  AlgorithmRegistry:66
            ├─ action     method.execute             ArtifactReportResource:329-330
            │              └─> ReportJourneyMethod.reference     ReportJourneyMethod:210-218
            │                    └─> ArtifactWriter.write         ArtifactWriter:96
            ├─ effect     ArtifactObserver.observe   ArtifactReportResource:354-355 -> ArtifactObserver:82
            ├─ episode    ReportJourneyTrace.appendAll            ArtifactReportResource:394 -> ReportJourneyTrace:105-111
            │                └─> CognitiveStore.appendEvents     (the kernel's own store: CognitiveService:170-175)
            └─ reuse      ReportJourneyTrace.retained            ArtifactReportResource:312-313 -> ReportJourneyTrace:147-155
                          └─> ReportJourneyMethod.replay        ReportJourneyMethod:220-236

  GET  /v1/cognitive/trace/{episodeId}              CognitiveResource:209-213
  POST /v1/cognitive/observe                        CognitiveResource:72-74
      └─> CognitiveService.observe                  CognitiveService:356-359 -> :498
            └─> CognitiveKernel.process              CognitiveKernel:385 (select :503, act :669, observe :723-733,
                                                        procedure :788 / :1145-1152, consult :498 / :1276-1284)
  POST /v1/cognitive/task                            DistributedTaskResource:50, :454-457
      └─> CognitiveCoordinator.arrange               DistributedTaskResource:487-489
            └─> TaskPublisher (peer + secret)        DistributedTaskResource:181-198
  POST /v1/bridge/messages                           BridgeResource:41, :78-80
      └─> BridgeService -> EpistemicBridgeSink.accept  EpistemicBridgeSink:68-84, :113-118

CHAIN B  (JDK HttpServer, matrix-api-gateway, :8765)

  POST /v1/analyze                                  MinimalHttpServer:467 -> :599
      └─> ProductionBrainClient.cycle               ProductionBrainClient:397 -> TrueMindCycle:201
            ├─> episodicLog.append                  ProductionBrainClient:433        [EPISODE]
            ├─> publishToBridge                     ProductionBrainClient:450        [REPORT -> chain A]
            └─> hdcStore.teach / HdcRetrievalStage  TrueMindCycle:398-405, :501
  POST /v1/sleep                                    MinimalHttpServer:496 -> :1557
      └─> RealSleepScheduler.runInduction           RealSleepScheduler:318
            ├─> LearningAdmission.assess            RealSleepScheduler:323-324       [CANDIDATE]
            └─> RuleInductionEngine.induce          RealSleepScheduler:362 -> RuleInductionEngine:137
                  └─> BirKnowledgeBase.register      RuleInductionEngine:238-240     [PROMOTION -> bir.ndjson + BirRegistry]
                        └─> (never read by the cycle: TrueMindCycle:381)            [REUSE — BROKEN]
  POST /v1/teach                                    MinimalHttpServer:495 -> :1177
      └─> hdcStore.teach -> PromotionGate.evaluate  ProductionBrainClient:623 -> PersistentHdcStore:85-87
  POST /v1/bridge/task                              MinimalHttpServer:476 -> TaskBridge:63, :201
      └─> CognitiveCapability.execute              CognitiveCapability:216 -> :136 (brain.cycle)
  GET  /v1/bir                                      MinimalHttpServer:485 (the promoted rules are visible HERE only)
```

---

## 3. Verdict: the journey does **not** exist as one connected served path

**Verdict: `PARTIAL` — one connected served path exists (Chain A's report journey), and the rest of
the journey exists in separate pieces that do not meet.**

What is connected:

- **A1 → A8 is one connected served path.** A document is ingested into claims and concepts; a
  question is answered from them; the kernel selects a method; consent and the local-only gate are
  enforced; one bounded artifact is written; an independent reader observes the effect; the steps are
  written as an episode in the kernel's store; and a later request reads the retained journey back and
  serves it — across a restart, per the 2026-11-15 HTTP demonstration.

What is **not** connected, with the exact break points:

- **BREAK-1 — the learning path stops at the promoted rule (Chain B).**
  `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:381`: the serving
  cycle's BIR stage is built without the registry bridge, so the rule promoted at
  `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:238-240` is
  never consulted by a later task. The reuse mechanism exists
  (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:37`) and is
  exercised only from tests.
- **BREAK-2 — chain A's kernel never delegates.**
  `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:290`: `withRemoteExecutor` has no
  production caller, so the `DISPATCH_REMOTE` phase at `:538` is unreachable on the served
  `/v1/cognitive/observe` cycle. The crossing exists, but the kernel does not use it.
- **BREAK-3 — the crossing is conditional, and chain B's brain is a stub by default.**
  `matrix-core/src/main/java/io/matrix/cognitive/distributed/DistributedTaskResource.java:459` answers
  `503` without a configured peer and secret (`:181-198`);
  `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:155-156` serves
  `StubBrainCycle` unless `MATRIX_MODE=production` is set.
- **BREAK-4 — Chain A's "later task" is the same task.**
  `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:604-617`: the reuse is
  conditioned on the same identity, the same workspace fingerprint and the same file name. The
  journey closes on itself; it does not transfer to a different task.
- **BREAK-5 — the two chains' learning stores never meet.**
  Chain A's episodes live in the cognitive store (`matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:170-175`);
  chain B's episodes live in the episodic log
  (`matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:433`). Nothing
  carries a promoted rule from B into A's selector, and nothing carries A's retained journey into B's
  learning window.

**The honest one-line answer:** the journey exists as a **complete single-request path** and as
**three disconnected learning mechanisms**; it does not exist as one connected served path that
perception reaches a later, different task through what it learned.

---

## 4. DORMANT mechanisms found (and where each IS referenced)

| # | mechanism | where it is actually referenced | status |
|---|---|---|---|
| D1 | `io.matrix.cognitive.algorithms.ProcedureStore` — `matrix-core/src/main/java/io/matrix/cognitive/algorithms/ProcedureStore.java:41` | only by `ProcedureValidator` (`:77`), by the javadoc of `io.matrix.contract.Skill` (`matrix-core/src/main/java/io/matrix/contract/Skill.java:11`) and by `matrix-core/src/test/java/io/matrix/cognitive/algorithms/ProcedureStoreTest.java:45,63,75,88,102,117,131,143,191,197,208,230` | `TEST-ONLY` |
| D2 | `io.matrix.cognitive.algorithms.ProcedureMethod` — `.../algorithms/ProcedureMethod.java:45` | constructed only in `matrix-core/src/test/java/io/matrix/cognitive/algorithms/ProcedureStoreTest.java:163,179,215,237,281,283`; its `ProcedureBacked` marker is read by `.../algorithms/AlgorithmSelector.java:298`, but no production code registers a `ProcedureMethod` into any registry | `TEST-ONLY` |
| D3 | `io.matrix.cognitive.algorithms.ProcedureValidator` — `.../algorithms/ProcedureValidator.java:26` | only by `ProcedureStore` (`.../algorithms/ProcedureStore.java:77`) and by `ProcedureMethod`'s verification string (`.../algorithms/ProcedureMethod.java:124`) | `TEST-ONLY` |
| D4 | `io.matrix.actions.PlanRunner` — `matrix-core/src/main/java/io/matrix/actions/PlanRunner.java:20` | nothing outside its own file | `DORMANT` |
| D5 | `io.matrix.actions.PlanPreprocessor` — `matrix-core/src/main/java/io/matrix/actions/PlanPreprocessor.java:19` | nothing outside its own file | `DORMANT` |
| D6 | `io.matrix.actions.ActionRegistry` — `matrix-core/src/main/java/io/matrix/actions/ActionRegistry.java:21` | only by `io.matrix.actions.ArtifactAction` (`matrix-core/src/main/java/io/matrix/actions/ArtifactAction.java:36-39`) and by the javadocs of `matrix-core/src/main/java/io/matrix/plugin/PluginServiceRegistry.java:15` and `matrix-core/src/main/java/io/matrix/contract/Plugin.java:12`; no production code registers an action into it | `DORMANT` |
| D7 | `io.matrix.actions.ArtifactAction` (the registry adapter of the writer) — `matrix-core/src/main/java/io/matrix/actions/ArtifactAction.java:19` | `ArtifactAction.of(writer)` is called only from `matrix-core/src/test/java/io/matrix/actions/EvidenceToActionTest.java:493` and `matrix-core/src/test/java/io/matrix/knowledge/EvidenceToActionDemonstrationTest.java:138,176` | `TEST-ONLY` |
| D8 | `io.matrix.brain.runtime.BirRegistryBridge` — `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/BirRegistryBridge.java:19` | constructed only in `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/DistillationPipelineTest.java:85`; declared as a field in `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:22,28` | `TEST-ONLY` |
| D9 | `io.matrix.brain.runtime.stages.BirInferenceStage.evaluateWithRegistry` — `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:37` | only its own declaration; the serving cycle calls the static table instead (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java:382`) | `DORMANT` (mechanism exercised only from tests) |
| D10 | the referent machinery — `matrix-core/src/main/java/io/matrix/knowledge/concept/research/{ReferentResolver.java:52, ReferentKey.java, ReferentPolicy.java:52, ReferentKeyMigration.java:55, LiveStoreMigration.java:74}` | only inside its own package and by its own migration CLI (`.../LiveStoreMigration.java:116`, `.../ReferentKeyMigration.java:266-267`); exercised only from `matrix-core/src/test/java/io/matrix/knowledge/concept/research/**` | `DORMANT` (CLI only, not a serving path) |
| D11 | `io.matrix.brain.runtime.PersistentMind` — `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentMind.java:40` | opened at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:235` and read nowhere else in the gateway; the field is assigned once and never used | `DORMANT` (constructed, never consulted) |
| D12 | `io.matrix.brain.runtime.graph.{Connectome.java:35, ReplayPhase.java:41, SleepConsolidator.java:44}` | only by their own package's constants and javadoc (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/HdcNode.java:79`, `.../CognitiveNode.java:4`, `.../Activation.java:9,74`) and by the tests `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/graph/*` | `DORMANT` (an island — consistent with `docs-v2/architecture/RUNTIME-TOPOLOGY.md:127`) |
| D13 | the kernel's remote dispatch — `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:538` | the executor is only settable through `withRemoteExecutor` (`:290`), whose only caller is `matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelRemoteTest.java:106` | `DORMANT` |

---

## 5. UNKNOWN items (and what would settle each)

| # | question | what would settle it |
|---|---|---|
| U1 | Is the report route enabled in any **running** deployment? The code makes it opt-in (`matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:56`, default `false`) | the deployed instance's `matrix.actions.artifact.enabled` / `matrix.actions.artifact.root`, or its application log; the 2026-11-15 demonstration artefact proves the route works when enabled, not that it is enabled anywhere |
| U2 | Does any deployed gateway run in `production` mode rather than `stub`? The default is `stub` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:155-156`) | `GET /health/live` on the running gateway (the `mode` field), or the deployment manifest |
| U3 | Is the chain A → chain B task crossing configured anywhere (peer + secret)? The route refuses with `503` otherwise (`matrix-core/src/main/java/io/matrix/cognitive/distributed/DistributedTaskResource.java:459-463`) | the deployment's environment (`matrix.peer.url`, `matrix.bridge.secret`) or `GET /v1/bridge/status` on chain A (`matrix-core/src/main/java/io/matrix/bridge/BridgeResource.java:109-124`, which reports `configured`) |
| U4 | Are the chain B `bir.ndjson` files on any running instance carrying **induced** rules rather than only the hand-written seed set? The wake-up comment at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:276-282` records that they once did not | the instance's `data/mind/bir.ndjson` provenance field, or `GET /v1/bir` on the running gateway |
| U5 | Does the served replay path still hold after a restart **at this baseline**? The 2026-11-15 demonstration says so, but that artefact was produced at an earlier commit (`b7bd1be4` is later) | a packaged run of the route against this baseline; the route's own behaviour is unchanged in the sources cited above, but "unchanged by reading" is not "verified by running" |

---

## 6. The smallest missing link, and the contracts a thinnest integration must reuse

**The smallest missing link.** One thing is missing, and it is not a component: it is a **connection**.
A promoted, independently admitted candidate must be able to change a **later, different** task's
choice. Everything needed to *hold* a promoted candidate already exists on both sides — chain B
holds promoted rules in the `BirRegistry`/`bir.ndjson`, chain A holds procedure records and a
retained journey in the cognitive store — and everything needed to *serve* a task already exists on
both sides. What does not exist is a path from one to the other.

**The thinnest integration, and only the existing contracts it must reuse:**

*Chain B side — one constructor argument, no new store, no new registry:*
give the serving cycle the bridge over the registry that already holds the promoted rules, so that
`evaluateWithRegistry` is the path the cycle takes. Contracts to reuse, all existing:
- `io.matrix.brain.runtime.BirRegistryBridge` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/BirRegistryBridge.java:23`) — the registry→inference adapter that already exists;
- `io.matrix.brain.runtime.stages.BirInferenceStage.evaluateWithRegistry` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java:37`) — the existing evaluation path;
- `io.matrix.brain.runtime.BirKnowledgeBase.registry()` (`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:292-294`) — the one registry, already shared with the induction side;
- `io.matrix.brain.runtime.LearningAdmission.assess` + `io.matrix.brain.runtime.EpisodeRole.learningEligible` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:323-324`,
  `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:160-166`) — the admission rule that keeps evaluation traffic out;
- `io.matrix.brain.runtime.PromotionGate.evaluate` (`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentHdcStore.java:86`) and `io.matrix.brain.runtime.KMaxEnforcer.K_MAX` — the promotion rule and the cap.

*Chain A side — one more entry in the catalogue the route already uses, no new conductor:*
register one further `AlgorithmMethod` in the report journey's own `AlgorithmRegistry` so that the
**planner** can choose it and the trace can record why — the same registry, the same selector, the
same `CostLedger`. Contracts to reuse, all existing:
- `io.matrix.cognitive.algorithms.AlgorithmMethod` / `AlgorithmCapability` / `AlgorithmRegistry` /
  `AlgorithmSelector` / `CostLedger` (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/`, wired at
  `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyPlanner.java:73-77`);
- `io.matrix.cognitive.distributed.CognitiveCoordinator` + `io.matrix.bridge.TaskPublisher`
  (`matrix-core/src/main/java/io/matrix/cognitive/distributed/DistributedTaskResource.java:487-489`,
  `:181-198`) — the one coordinator and the one transport; **no second conductor**;
- the bridge contracts of ADR-W53-01: `io.matrix.contract.bridge.CognitiveTaskRequest` /
  `CognitiveTaskResponse`, `BridgeEnvelope` / `BridgeAnswer`, `HmacBridgeSigner`, the granted rights
  `INFERENCE_REQUEST` / `INFERENCE_RESPONSE`, and the version rule — the **only** sanctioned crossing;
- `io.matrix.api.bridge.CognitiveCapability.COGNITIVE_INFERENCE`
  (`matrix-api-gateway/src/main/java/io/matrix/api/bridge/CognitiveCapability.java:38`) — the allowlisted
  capability the peer already serves, with its version check (`:235-243`);
- `io.matrix.cognitive.RemoteReportAssessor` (`matrix-core/src/main/java/io/matrix/cognitive/RemoteReportAssessor.java`)
  — the rule that a remote report is a **candidate**, never an established belief;
- `io.matrix.cognitive.CognitiveStore.appendEvents` + `io.matrix.cognitive.report.ReportJourneyTrace`
  (`matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java:105-111`) — the one
  durable episode stream; **no new knowledge database**;
- `io.matrix.cognitive.algorithms.Procedure` (`matrix-core/src/main/java/io/matrix/cognitive/algorithms/Procedure.java:47`)
  — the existing promoted-procedure record; **no new skills directory**.

**What the thinnest integration must not add:** a global conductor, a second knowledge database, a
skills directory, or an agent framework. Each of those would duplicate something the tree already
owns — the coordinator (`CognitiveCoordinator`), the store (`CognitiveStore`), the registry
(`BirRegistry` / `AlgorithmRegistry`), and the method portfolio (`AlgorithmMethod`).

**And the discipline that follows from this map:** the first thinnest integration must be measured
against the three arms of `CRJ-01-ACCEPTANCE.md` §2, because the only reuse the tree currently has is
arm B (exact replay), and a gain measured against arm B alone is a caching gain, not a learning
transfer.

---

## 7. Cross-references (read-only; not modified by this report)

- `docs-v2/architecture/RUNTIME-TOPOLOGY.md` — the two chains, the ports, the stub default (`:70-82`),
  the islands (`:125-131`).
- `docs-v2/architecture/CAPABILITY-REGISTRY.yml` — the registry of record; the C2A row names the
  serving path of the report route and records `production_callers: []`; the procedure row records
  the kernel's procedure write and the later consultation.
- `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` — the governed bridge contract that §6
  above reuses.
- `docs-v2/engineering/CRJ-01-ACCEPTANCE.md` — the contract this report answers (task CRJ-01.0).
- `docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json` — the packaged HTTP evidence for A1–A8,
  including the post-restart replay.
