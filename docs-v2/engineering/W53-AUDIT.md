# W53 — Audit of the subsystem integration

**Status:** `ACTIVE` — the audit of the subsystem integration, one answer per subsystem with its proof.
**Date:** 2026-11-09 · **Branch:** `feature/w53-distributed-cognition` · **Wave:** distributed cognition

This audit answers one question per subsystem: **is the W53 path a real production call path,
and what proves it?** A row that says "implemented" without a path is not an answer.

---

## 1. The call paths that exist

| The path | The code that carries it | The proof |
|---|---|---|
| Chain A receives a cognitive task | `DistributedTaskResource.arrange` (`POST /v1/cognitive/task`) | the demonstration posts to it against a running Quarkus process |
| Chain A resolves the requirement | `CognitiveCoordinator.arrange` → `TaskSpec` → `CognitiveTaskRequest` | `CognitiveCoordinatorTest` (11) |
| Chain A persists the dispatch intent | `CognitiveStore.recordDispatchIntent` → the `cognitive_task_dispatch` table | `DispatchIntentTest` (8): the intent survives a restart |
| Chain A signs and sends | `TaskPublisher.dispatch` → `POST /v1/bridge/task` | `TaskRoundTripTest` (3): a real HTTP round trip |
| Chain B authenticates and validates | `TaskBridge.answer` → `BridgeReceiver.receive` | the forged-report case of `TaskRoundTripTest` |
| Chain B admits the request durably | `RemoteTaskStore` (WAL, `synchronous=FULL`, busy timeout) | `RemoteTaskStoreTest` (7) |
| A duplicate never reaches the brain — the guard is in three places | the receiver's durable idempotency (`BridgeReceiver.receiveAnswer` → `DurableBridgeIdempotency.find`), the durable association itself (`firstOutcome`), and the admission decision in the sink (`RemoteTaskStore.Decision.DUPLICATE_TERMINAL`) | `TaskPeerRecoveryTest`: the retry of a task the reconciliation closed is answered with the recorded failure **and the brain is not asked again** — the test counts the calls, and with the row absent the count goes up and the test goes red |
| Chain B executes the **production** cognitive path | `CognitiveCapability.standard(BrainCycle)` → the gateway's real brain | the demonstration runs the gateway with `MATRIX_MODE=production`; `StubBrainCycle` is never on this path |
| Chain B persists the result | `RemoteTaskStore` (state, the result, the idempotency association) | the demonstration reads the inbox after the restart |
| Chain B measures its own stages | `TaskBridge` (the peer's own clock) → `RemoteTaskStore.recordStages` → the row's `stage_timings_json` | `PeerStageTimingsTest` (3): the stages are on record, the execution stage is measured rather than constant, and the measurement does not travel on the wire |
| Chain B answers, signed | `TaskBridge` signs the response envelope and the report envelope | the demonstration's answer carries both signatures |
| Chain A verifies both signatures | `TaskPublisher.verifiedResponse` / `verifiedReport` | a forged report is dropped — `TaskRoundTripTest` |
| Chain A admits the report into its model | `DistributedTaskResource.coordinator()` → `BridgeResource.service().accept` → `EpistemicBridgeSink.writeTaskReport` | `CognitiveCoordinatorLoopTest` (4): the report is a `CANDIDATE` |
| Chain A evaluates and decides | `RemoteReportAssessor.assess`, reading chain A's own knowledge through `KnowledgeStoreEvidence` | `RemoteReportAssessorTest` (8), `KnowledgeStoreEvidenceTest` (4) |
| Chain A records the verdict and the admission | `CognitiveStore.recordDispatchDisposition` / `recordReportDisposition` | `CognitiveCoordinatorLoopTest` (4) |
| What chain A concluded about the peer's answer reaches the profile of methods | `RemoteVerdicts.of` (the table of meanings) → `CognitiveCoordinator.recordVerdict` → `CognitiveCoordinator.VerdictSink` → `LedgerVerdictSink` → `CostLedger.record` → `CognitiveStore.recordVerdict` (the durable row, in the stratum of the capability, the task family and the capability version) | `RemoteVerdictsTest` (5), `LedgerVerdictSinkTest` (3), `CognitiveCoordinatorTest` (11) — the verdict is a decided trial only when the verifier settled something, and the demonstration's own verdict is `UNVERIFIED` because chain A's evidence settles nothing there |
| The task is inspectable by its id | `DistributedTaskResource.trace` (`GET /v1/cognitive/task/{id}`) | the demonstration reads it after the task finished |
| The same request may be retried | `CognitiveCoordinator.replay` → the same request id, the same payload | the demonstration's inbox row: one row for that request id, with the result stored once |
| The task survives the restart of chain A | `DistributedTaskResource.trace` → the dispatch row in chain A's own store | the demonstration restarts chain A, asks for the task again by its stable id and checks that the request id, the state and the disposition are the ones it recorded before the restart |
| The delegation reaches the reflection | `CognitiveKernel`'s remote phase → `CycleReflection.of(...)` | a delegated task that did not settle the proposition becomes an uncertainty of the reflection and a `LEAVE_UNRESOLVED` decision that names the request |
| Both processes report their health and the peer's active capabilities | chain A's `/q/health` and chain B's `/health/live`, which now carries the served capability ids | the peer names what it can really execute; chain A names the capability it used and where it ran |
| A duplicate finds the task running | the peer answers with the task's status (`IN_PROGRESS`), and the coordinator waits inside its budget instead of judging | `TaskPeerCancellationTest` (6), `CognitiveCoordinatorTest` (11) — with the waiting disabled, exactly the matching coordinator test goes red |
| The delegated task belongs to an episode | the correlation carries the episode's real id and the dispatch row records it durably | `CognitiveCoordinatorTest` (11) — with the placeholder episode restored, exactly the two matching tests go red |
| The reconciliation waits for a running task | the replay path treats `IN_PROGRESS` like the first dispatch does: no verdict, no closed task | `CognitiveCoordinatorRecoveryTest` (6) — with the replay's waiting disabled, exactly the matching test goes red |
| A crash between the peer's answer and chain A's own verdict is finished by the pass | the answer is on record and the verdict is not: `unfinishedDispatches()` returns such a row, the pass sends the same request again and the dispatch ends `ADJUDICATED` — while a cancelled or a timed-out dispatch keeps its answer unjudged **on purpose** and is left alone | `CognitiveCoordinatorRecoveryTest` (6), `DispatchIntentTest` — with the store's query reverted the row is not found and the test goes red; with the cancellation exclusion removed the cancelled row joins the queue and that test goes red |
| The reconciliation runs while the process is up, not only when it starts | `DistributedTaskResource.onStartup` starts `DispatchReconcilerTimer`, whose `ScheduledExecutorService` runs `DispatchReconciler.reconcileStale()` on a fixed delay and is stopped on `ShutdownEvent` | `DispatchReconcilerTimerTest` (3) — the timer finishes a dispatch whose answer was lost; three negative controls (A: the in-flight exclusion, B: the budget closure, C: the failure write) were red and every file was restored byte-exactly |
| A dispatch that is in flight is not sent again by the timer | the coordinator keeps the request ids it is working on (`CognitiveCoordinator.isInFlight`), and the pass leaves them alone — on the record a running dispatch and a lost answer look the same | `DispatchReconcilerTimerTest` — *the timer leaves a dispatch that is in flight alone*; the negative control A is red without the exclusion |
| A dispatch whose retry budget is spent leaves the unfinished queue | the pass closes it before it sends it: `CognitiveStore.closeDispatchAsFailed` writes the state, the disposition and the reason in one statement, and the row stops being unfinished | `DispatchReconcilerTimerTest` — *a dispatch whose budget is spent is finished, and is not sent again*; the negative controls B and C are red without the closure and with the adjudication write |
| The measurement of the path is a measurement of the path, and not of the laboratory's history | chain A's cognitive store is opened at the configured path (`matrix.cognitive.path`, the environment variable `MATRIX_COGNITIVE_PATH`) and the benchmark and the demonstration name both of chain A's stores to their own directory | `CognitiveStorePathTest` (3) — the named file wins over the environment, the environment over the default; the same workload measured against the accumulated store of the wave and against a store of its own gave 32.33 ms and 13.87 ms at p50, and the difference is in the assessment (6.57 ms against 0.35 ms), which is the stage that compares the peer's report with chain A's knowledge |
| The performance report measures the processes, not only the client | the benchmark samples the CPU time, the heap, the garbage collection, the allocation and the resident set from outside the two processes | `scripts/w53-process-metrics.py` — and the guard fails loudly: with a process that cannot be read the script exits non-zero and the benchmark stops, so the report can never quietly omit a dimension |
| The boundary refuses what it must | the peer's allowlist (unserved capability), chain A's durable identity (the same request id under a different payload), and the signature (a forged request) | the demonstration's `refusals` section: each refusal carries the reason its own receiver gave, and the forgery is checked by its reason — `scripts/w53-forge-request.py` |
| The peer survives its own crash | the rows the previous run left unfinished are closed with a reason, and a later delivery of such a task is answered with that failure | `TaskPeerRecoveryTest` (3) — with the reconciliation disabled, exactly the two matching tests go red |

---

## 2. The boundaries that still hold

| The boundary | How it is held | The proof |
|---|---|---|
| Two processes | chain A is the Quarkus application; chain B is the gateway's JDK server | the demonstration starts both, in their own directories |
| Two databases | chain A's `data/cognitive/cognitive.sqlite`, chain B's `data/mind/tasks.sqlite` | the inbox path follows chain B's own `MATRIX_MIND_DIR` |
| One writer per database | chain A's `CognitiveStore`; chain B's `RemoteTaskStore` | no second writer is opened anywhere on the path |
| The bridge is the only crossing | the reachability proof over the compiled classes | G1–G12 of `scripts/w45-chain-isolation-check.sh` (32 checks) |
| The new direction is governed too | the same proof, extended | **G11**: the task client reaches no store, the task receiver reaches neither the store nor chain A's bridge server |
| Chain A does not reach chain B's stores | the same proof, extended the other way, plus the constant pool of every class of matrix-core | **G12**: the chain A task client reaches no class of chain B's store, and no class of chain A names one of chain B's databases |
| The gate can go red | the negative control breaks G11 and G12 on purpose | **C5** and **C6** of `scripts/w45-chain-isolation-selftest.sh` (19 checks) |
| Fail closed | no secret ⇒ no dispatch; an ungranted right ⇒ refusal; a bad signature ⇒ the report is dropped | `DistributedTaskResource` answers 503 without a configured peer; `BridgeReceiver` refuses an ungranted kind |
| No secret in the artefacts | the demonstration generates the secret, gives it to both processes through the environment and never prints it | `scripts/w53-distributed-demo.sh`; the report contains no secret |

---

## 3. The integration gaps, named

| The subsystem | The state | What is missing |
|---|---|---|
| `CognitiveKernel` | **integrated (round 7)** | the kernel grew a port, not a second selector; the episode shows four real transitions — `DISPATCH_REMOTE`, `RECEIVE`, `VALIDATE`, `EVALUATE_EVIDENCE`. What remains: the dispatch row and the cognitive episode are two records |
| the reconciliation pass | **runs at startup and on a timer, on both sides** | the request travels with the durable intent, so the unfinished dispatches can be sent again with the same request id; `DispatchReconciler` walks them, chain A's entry point runs the pass when the process starts, and `DispatchReconcilerTimer` runs it again every 30 seconds while the process is up — the startup pass covers the crash, the timer covers the answer that is lost while the process stays up. The peer reconciles its **own** rows the same way (`RemoteTaskStore.reconcileUnfinished`): a task that was in flight when the peer died is closed as a reasoned failure rather than left in `RUNNING` for ever. What remains: no timer, and the interrupted work is lost rather than resumed |
| cancellation and deadlines | **the deadline is honoured on both sides, and the withdrawal now reaches the peer** | the budget and the deadline travel; chain A can cancel a task in flight (`CANCELLED`, the peer's answer stays on record) and chain B answers `TIMED_OUT` when its work outlasts the task's deadline, keeping the result. Since round 19 the withdrawal also travels to the peer as its own signed kind (`INFERENCE_CANCEL`), and the peer acts on it: a task withdrawn before it started is never executed, one withdrawn while it runs is closed as cancelled with the answer kept, and a finished task is not withdrawn (`TaskPeerCancellationTest`, 6). What remains: the peer cannot interrupt the brain cycle it is inside |
| the plugin registry | **exercised on the peer's side** | the remote capability must be in the peer's allowlist, at a version this process serves, and a withdrawn capability is not run again — the check is per invocation, so a revocation takes effect on the next call (`CognitiveCapabilityPolicyTest`). What remains: the chain A plugin registry's health and budget checks are not consulted, because chain B cannot reach chain A's classes at all |
| the concurrency and queue limits | **enforced on both sides** | the coordinator refuses an oversized payload before it writes or sends anything, caps the dispatches in flight, honours the deadline with an injected monotonic clock and can cancel a task; chain B's durable inbox is bounded on the work in flight and refuses beyond it, an input larger than the limit the task declared is refused before it reaches the brain, and work that outlasts the deadline is answered `TIMED_OUT` with the result kept. The retry budget is spent durably: the attempts are counted in the dispatch's own row, a failed delivery is retried with the same request id, and a spent budget ends in a finished, traceable failure. What remains: the peer cannot interrupt the brain cycle it is inside |
| the reliability estimate | **implemented and used** | the Wilson lower bound at the 90 % level, the width gate and the three states are implemented (`MethodReliability`); the selector reads the estimate and holds it out of the score unless the state is `ESTIMATED` (`AlgorithmSelector`), and the decision says which basis it used. What remains: the local call sites record `UNVERIFIED`, so the local profile has no decided trial, and no held-out set exists yet (`H-132` stays open) |
| the bounds | **enforced and negative-controlled** | `DispatchPolicy` names the payload limit, the concurrency cap, the retry budget and the defaults; with the payload limit, the concurrency cap, the deadline check and the cancellation check disabled, exactly the four matching tests of `CognitiveCoordinatorLimitsTest` went red; with the peer's three bounds disabled, exactly the four matching tests of `TaskPeerBoundsTest` went red; with the request no longer stored with the intent, exactly the three matching tests of `CognitiveCoordinatorRecoveryTest` went red; with the retry budget widened past the policy, exactly the two matching tests of `CognitiveCoordinatorRetryTest` went red |
| the benchmark | **measured for one task at a time and under a bounded concurrency of four**, against a store of the run's own | 30 tasks through the full path: p50 14.29 ms, p95 21.41 ms, 42.99 tasks/s, every sample `UNRESOLVED`; 24 tasks four at a time: every task adjudicated `UNRESOLVED`, the steady state p50 27.03 ms and 143.95 tasks/s, the cold start separated (`docs-v2/engineering/W53-PERFORMANCE.json`). The concurrent path is slower per task than the single-task one and reaches a higher throughput, and the report says both. The two processes' own accounting is measured too, and the **peer's own execution is told apart from the crossing** (1 ms against 5.84 ms at the median): the remote cognitive work is not what makes the path slow |
| the concurrency cap | **measured above the cap, not only asserted** | eight clients against the cap of four: **12 of 24 refused**, at a client round trip of 3–33 ms, with no stage timings and no durable record — the replay of a refused request is answered "no dispatched task with that id". The queue depth is 0: the cap refuses the excess instead of queueing it | the performance report's §5d; `CognitiveCoordinatorLimitsTest` holds the cap, and the over-cap run is the measurement of it |
| the measurement's own noise | **measured, and no optimization accepted on its strength** | seven runs of the same code, each against a store of its own, differ by **9.9 %** at p50 (the sequential path; six of the seven inside 3 %) and by **2.7 %** at the median of the concurrent path's steady state. The mandate asks that an accepted change exceed the noise; nothing this wave changed was measurable above it. The wave's earlier noise figures were measured against the accumulated store of the wave and are withdrawn |
| the durable idempotency path | **measured apart from the first execution** | the same requests sent again with the same id and payload: the replay costs p50 8.07 ms against the first execution's 14.29 ms, 18.3 ms of CPU against 64.7 ms, and 30 of 30 replays answered with the disposition of the first execution; the coordinator writes the `replay_*` stages and `CognitiveCoordinatorRetryTest` holds them — with the measurement removed, exactly that test goes red |
| the concurrent write of a remote report | **fixed and proven** | the store's write transactions are serialized on the store's own monitor; `KnowledgeStoreConcurrencyTest` holds four writers, one store and one workspace, and checks the state (every claim on record, and after a restart). With the serialization removed, both of its tests go red |
| the epistemic evaluation | **regression-free, compared case by case** | 18/29, the same eleven failures — the distributed path did not degrade it, and it did not improve it either. `docs-v2/engineering/W53-EPISTEMIC-COMPARISON.md` lists all 29 cases with their observation and the cause of each failure, and states what the distributed path does to the epistemic model: a report is admitted as a report, it cannot promote itself, and a report resting on chain A's own material comes back qualified |

---

## 4. The inherited CI findings (not W53 regressions)

Reproduced from the current base, not copied from an old log: the shellcheck findings in the
repository's other scripts, the TruffleHog documentation placeholders under `.veai/skills/**`,
the native build's heap exhaustion, and the Docker push authentication. The W53 scripts
(`scripts/w53-distributed-demo.sh`, `scripts/w53-inbox-reader.py`,
`scripts/w53-distributed-benchmark.sh`, `scripts/w53-inbox-reader.py`,
`scripts/w53-answer-reader.py`) add **zero** shellcheck findings and the quality gate stays
at its accepted baseline of 271 candidates — no new unscoped magic number was introduced,
and the two the first draft of the demonstration introduced were removed by naming them.

**One correction is recorded here rather than left in a chat.** An earlier report of this
wave called the repeated `the request id req-w53-bench-N is already dispatched` a
concurrency defect. It was **correct behaviour**: chain A's cognitive database is
persistent by design, the benchmark reused the request ids of the previous run, and the
durable idempotency refused a request id it had already seen — that refusal is the
feature working. The benchmark now stamps every run (`w53-bench-<runStamp>-<index>`),
`CognitiveStoreConcurrencyTest` shows that four threads with four distinct request ids are
all recorded on one store, and the round-9 narrowing means a busy or a not-null violation is
no longer misreported as "already dispatched".
