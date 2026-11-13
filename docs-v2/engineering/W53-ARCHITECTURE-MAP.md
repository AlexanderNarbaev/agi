# W53 — The architecture of the distributed execution

**Status:** `ACTIVE` — the map of the distributed execution as it exists in the code.
**Date:** 2026-11-09 · **Branch:** `feature/w53-distributed-cognition`

This map describes the architecture that **exists in the code**, not the one the mandate
sketched. Every arrow below is a call that really happens; the ones that do not happen yet are
listed as such at the end.

---

## 1. The two processes and the one crossing

```
                    CHAIN A — Quarkus (9091)                  CHAIN B — JDK server (8765)
  ┌──────────────────────────────────────────────┐          ┌─────────────────────────────────────┐
  │ POST /v1/cognitive/task                      │          │ POST /v1/bridge/task                │
  │   DistributedTaskResource                    │          │   TaskBridge                       │
  │     └─ CognitiveCoordinator                  │          │     ├─ BridgeReceiver.receive       │
  │          ├─ CognitiveStore                   │          │     │    (auth, rights, version)       │
  │          │   cognitive_task_dispatch         │          │     ├─ RemoteTaskStore (durable      │
  │          │   ← the intent, written FIRST     │          │     │    inbox: id, hash, state, result)│
  │          ├─ TaskPublisher  ── sign ──────────┼── HTTP ──┼──▶ ├─ CognitiveCapability.standard    │
  │          │                                    │          │     │    └─ the real brain (BIR)      │
  │          ├─ RemoteReportAssessor             │          │     └─ sign the response + the report│
  │          │   └─ RemoteVerdicts (the mapping)  │          │                                     │
  │          │       └─ the verdict of the answer │          │                                     │
  │          │           └─ LedgerVerdictSink    │          │                                     │
  │          │               └─ CostLedger →     │          │                                     │
  │          │                   cognitive_method_ │          │                                     │
  │          │                   verdict (durable) │          │                                     │
  │          └─ BridgeResource.service()         │          │                                     │
  │               └─ EpistemicBridgeSink          │          │                                     │
  │                    └─ the report becomes a   │          │                                     │
  │                       CANDIDATE               │          │                                     │
  └──────────────────────────────────────────────┘          └─────────────────────────────────────┘
```

The crossing is the bridge, over the network, in one governed protocol (version 3). There is
no second channel and no shared file: the reachability proof over the compiled classes
(`scripts/w45-chain-isolation-check.sh`, G8, G10, G11 and G12) shows that neither side reaches the
other's store inside one class graph.

---

## 2. The state machines that actually run

**Chain A — the dispatch.** Ten states, each of them a moment that really happened and is
durably recorded:

```
CREATED → DISPATCH_PENDING → DISPATCHED → COMPLETED → ADJUDICATED
                                   ├→ REFUSED  ├→ UNSUPPORTED
                                   ├→ FAILED   ├→ TIMED_OUT
                                   └→ CANCELLED
```

The order that matters: the intent is written in `DISPATCH_PENDING` **before** the request is
sent. A crash between the two leaves a row that `unfinishedDispatches()` finds, and the
replay sends the same request id and the same payload — which the peer's durable inbox
recognises.

The same queue holds the **other** crash: the one after the peer's answer arrived but before
chain A judged it. The answer is on record, the verdict is not, and an answer is not a
verdict — only chain A may judge it. Such a row is unfinished too, and the pass sends the
request again to obtain the same answer and to write the verdict. The two states that keep
an answer **on record and unjudged on purpose** — `CANCELLED` and `TIMED_OUT` — are
finished, not unfinished: a task that was called off is not to be sent again.

**Chain B — the inbox.** `REQUEST → RUNNING → COMPLETED`, with the refusal alternatives.
The request id and the payload hash are persisted; the same id with a different hash is a
conflict, refused.

---

## 3. The evaluation path — the part that matters epistemically

```
the peer's answer
      │
      ├─ does the signature verify?            no → the report is dropped (not noise, not a claim)
      │
      ├─ does it answer the task?               no → INVALID
      ├─ is the provenance complete?            no → INVALID
      ├─ is the status COMPLETED?               no → UNRESOLVED
      │
      ├─ does chain A's own evidence contradict? yes → REJECTED
      ├─ does chain A's own evidence support     yes, and the report is independent → SUPPORTED
      │                                         yes, and the report rests on what we supplied → QUALIFIED
      └─ nothing settles it                     → UNRESOLVED (or ABSTAINED)
```

Two rules hold everywhere: **a peer's confidence never makes a proposition supported here**,
and a report that rests on what chain A supplied to the peer is qualified rather than
supported — the same information coming back is not a second observation of it.

The admission of the report and the verdict on the proposition are **separate facts**:
`report_disposition` says what happened to the report, `disposition` says what chain A
concluded about the proposition.

---

## 4. What is not in this map yet

- The kernel's own phase sequence **does** carry the remote dispatch: `DISPATCH_REMOTE`,
  `RECEIVE`, `VALIDATE` and `EVALUATE_EVIDENCE` are real transitions of the cycle (§5). What
  remains is that the dispatch record and the cognitive episode are **two records in two
  stores** — the episode lives in the knowledge file with the service's cognitive tables, the
  dispatch in the cognitive file — and §8 of the mandate wants them to be one. The port that
  would carry the dispatch into the episode (`RemoteExecutor`) is **not supplied in the
  running application**: the local registry holds no remote capability, so the selector can
  never choose one, and the distributed path runs through the application entry point
  instead. The wave's honest list says so.
- The chain A plugin registry's health and budget checks are not consulted before a remote
  capability is invoked — chain B cannot reach chain A's classes at all, so the remote
  capability's lifecycle is the peer's business. The peer's own allowlist does carry the
  checks it can make: the capability must be registered, at a version this process serves,
  and a withdrawn capability is not run again.
- The peer cannot interrupt the brain cycle it is inside: a task withdrawn while it runs
  finishes its computation, and the withdrawal changes the record — the row is closed as
  cancelled, the answer is kept — not the computation. A task withdrawn before it started
  is never executed.

## 5. What the map gained after it was first written

The map was written before the last rounds of the wave; these are the parts that exist now
and were not in it then.

| what | where | the fact it holds |
|---|---|---|
| the request is stored with the intent | `cognitive_task_dispatch.request_json` | an unfinished dispatch can be sent again with the same request id, because the request id alone is not the request |
| the delivery attempts are counted durably | `attempts` in the same row | the retry budget belongs to the task: a restart does not buy a new one |
| the reconciliation pass at startup | `DispatchReconciler`, run by `DistributedTaskResource` when chain A starts | the work a crash left unfinished is finished after the restart |
| the reconciliation pass on a timer | `DispatchReconcilerTimer` (a JDK `ScheduledExecutorService` with one daemon thread) runs `DispatchReconciler.reconcileStale()` on a fixed delay and is stopped on `ShutdownEvent` | an answer that is lost while the process stays up is finished without waiting for the next restart; the tick is operational policy, and the content of the pass is read from the durable record |
| the dispatch this process is working on | `CognitiveCoordinator.isInFlight` — the request ids the coordinator holds in memory | a running dispatch and a lost answer look the same on the record, so the timer leaves the running one alone: a second delivery of a task the peer is still running would spend one retry of the durable budget for nothing |
| the close of a budget-spent dispatch | `CognitiveStore.closeDispatchAsFailed` | the dispatch will not be sent again, so the row is closed with its reason and leaves the unfinished queue — the adjudication write is not used for it, because it would claim a verdict about an answer that never arrived |
| the queue of the unfinished work | `CognitiveStore.unfinishedDispatches` | the work a crash leaves unfinished is either *not answered yet* or *answered and not yet judged* — and a task that was cancelled or timed out is finished, not unfinished |
| chain A's bounds | `DispatchPolicy` and the coordinator | the payload limit, the concurrency cap, the deadline and the cancellation are refused, not waited out |
| chain B's bounds | `RemoteTaskStore` (the inbox bound) and `TaskBridge` (the payload limit and the deadline) | the peer refuses what it cannot hold, and says so durably and with a signed response |
| the epistemic store's serialized writes | `KnowledgeStore` | one SQLite connection, one writer at a time: the remote report is not lost under concurrent writers |
| the peer's capability policy | `CognitiveCapability` (the allowlist), `CognitiveCapabilityPolicyTest` | the version must match the one this process serves, and a withdrawn capability is not run again — the check is per invocation |
| the episode the delegated task belongs to | `CognitiveCoordinator.TaskSpec.parentEpisodeId` → the correlation and `DispatchIntent.parentEpisodeId` | the same episode id is in the kernel's trace, in chain A's dispatch row and in the request the peer receives; a task with no episode names none |
| the withdrawal on its way to the peer | `CognitiveTaskCancel`, `BridgeCapability.INFERENCE_CANCEL`, `TaskPublisher.cancel`, `RemoteTaskStore.cancel` | the withdrawal is a crossing of its own: signed, audited, and granted by the peer. It carries its own message id, because reusing the request id would make the peer see a second payload under an id it knows and refuse the withdrawal as a conflict |
| chain A's own evidence in the adjudication | `KnowledgeStoreEvidence`, wired by `DistributedTaskResource.adjudicator()` | the adjudicator weighs the report against the claims and the evidence chain A already holds — and a report that states the proposition chain A supplied is qualified even when the peer declares no lineage |

**The peer measures its own stages, and keeps them on its own side.** The peer's inbox
write, the write that starts the request, its execution, the write of the outcome and the
signing of the answer are timed by the peer itself, with its own monotonic clock, and
written into its own durable row (`remote_task.stage_timings_json`). The performance
report reads that row; the answer the caller receives carries nothing new, so the bridge
protocol needed no new version. The whole reception is measured as well, and what is left
of it after the peer's own four writes and its execution — the authentication, the
validation and the receiver's bookkeeping together — is reported as a **remainder**, not as
a measurement of the signature alone.

| what | where | the fact it holds |
|---|---|---|
| the measured path | `docs-v2/engineering/W53-PERFORMANCE.md` | the single-task path p50 14.29 ms (p95 21.41 ms); the concurrent path (four at a time) the steady state p50 27.03 ms, every task adjudicated `UNRESOLVED` |
| the peer's own work inside the crossing | `CognitiveTaskResponse.durationMillis` → `CognitiveCoordinator`'s `peer_execution_nanos` | the peer says how long its execution took, so the crossing can be split into the remote cognitive work (1.27 ms at the median) and the governed crossing around it (5.84 ms) |
| the peer's own stages, measured by the peer | `TaskBridge` (the peer's own clock) → `RemoteTaskStore.recordStages` → the row's `stage_timings_json` | the peer separates its durable inbox write (0.215 ms), the write that starts the request (0.024 ms), its execution (1.43 ms), the write of the outcome (0.22 ms), the signing of the answer (0.58 ms), and the remainder of the reception that carries the authentication and the validation together (0.83 ms), against its whole reception of 2.82 ms. The measurement stays in the peer's own record: the answer on the wire is unchanged |
| the replay of a completed request, measured apart | `CognitiveCoordinator.resend` → the `replay_*` stages, `POST /v1/cognitive/task/replay` | the same request id and the same payload go to the peer again; the peer answers from its durable record, the coordinator times the replay on its own, and the report holds the two paths apart |
| the outcome across a restart of chain A | `CognitiveStore`'s dispatch row → `DistributedTaskResource.trace` (`GET /v1/cognitive/task/{id}`) | the task is asked for again after the process restarted and answers with the request id, the state and the disposition that were recorded before — the outcome is read, not recomputed |
| the delegation inside the cycle's reflection | `CognitiveKernel`'s remote phase → the reflection's `uncertainties` → `CycleReflection`'s `LEAVE_UNRESOLVED` decision | a remote report that did not settle the proposition is named as what the cycle did not settle, and the decision says which request left it open |
| what each process reports about itself | chain A's `/q/health`; chain B's `/health/live` → the served capability ids | each process answers for itself: chain A names the checks that are not up, chain B names the capabilities it can execute |
| the duplicate guard, in three places | the receiver's `DurableBridgeIdempotency.find`, the durable association's `firstOutcome`, and the sink's admission decision | a repeated request is answered from the durable record; the cognitive path is not entered a second time, and the test counts the brain's calls |
