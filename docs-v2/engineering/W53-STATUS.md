# W53 — Distributed Cognition, Durable Task Execution & Closed-Loop Collaboration

**Status:** `IN PROGRESS` — the distributed path works end to end; the closeout is not finished
**Date:** 2026-11-09 · **Branch:** `feature/w53-distributed-cognition` (from `develop` @ `375ed3f3`)
**The objective:** *"make MATRIX capable of assigning a bounded cognitive task to another running cognitive process, receiving its actual result, evaluating that result, and completing the original task with a durable, verifiable execution trace."*
**The central rule, honoured:** *"A remote cognitive process is a capability provider. Its output is a report that must be evaluated, not an automatic source of truth."*

> **A note on the name.** An earlier W53 wave — the one that made the system's procedural
> memory non-empty — already had a report. It is now
> `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md`, and this document is the report of the
> distributed-cognition wave.

---

## 1. The one-paragraph answer

MATRIX can now **delegate a bounded cognitive task to another running process and judge what
comes back**. Chain A writes its intent down *before* it sends, chain B receives the request
into a durable inbox of its own, runs its real production cognitive path, answers with a
signed response and a signed report, and chain A admits that report into its own epistemic
model **as a candidate** and then reaches its own verdict about the proposition. In the
recorded run of the real two-process demonstration that verdict was **UNRESOLVED** — the
peer's confidence was `MEASURED`, which is not independent evidence, and chain A's own
records settled nothing. That is the intended behaviour, not a shortfall: a successful
exchange is not a supported belief. After chain B was restarted and the same request was sent
again, chain B's own durable inbox still held **one** row for that request id, with the
result stored once — the second delivery created no second row and ran the task no second
time. (The row's `updatedAt` equals its `createdAt` because the peer writes one instant
for the whole request; that equality is *not* the evidence of a single execution, and an
earlier draft of this report treated it as if it were. The evidence is the row count and
the stored result.)

---

## 2. What exists now, and where

| The piece | The file | What it does |
|---|---|---|
| The typed request | `io.matrix.contract.bridge.CognitiveTaskRequest` | 19 fields: ids, correlation, capability, kind, bounded input, context, constraints, output contract, budget, deadline, payload limit, hop limit, provenance, payload hash, retry |
| The typed response | `io.matrix.contract.bridge.CognitiveTaskResponse` | eight statuses (`COMPLETED` … `IN_PROGRESS`), the structured result, the confidence **with its basis**, the limitations, the trace reference |
| The secondary report | `io.matrix.contract.bridge.CognitiveTaskReport` | the relation (`SUPPORTS` / `CONTRADICTS` / `INDEPENDENT`), the lineage (`derivedFrom`), the responder, the capability, the provenance |
| The bridge, extended | `io.matrix.contract.bridge.BridgeProtocol` (**version 3**) · `BridgeCapability` (`INFERENCE_REQUEST`, `INFERENCE_RESPONSE`) · `BridgePayload` (8 components) | version 1 and 2 stay readable; the new rights are explicit |
| Chain A's coordinator | `io.matrix.cognitive.distributed.CognitiveCoordinator` | one owner of the distributed task: intent → dispatch → check → evaluate → record; the transport is a **port** |
| Chain A's outbox | `io.matrix.cognitive.DispatchIntent` + the `cognitive_task_dispatch` table | the intent is durable **before** the send; nine states; the peer's vocabulary is mapped explicitly |
| Chain A's evaluation | `io.matrix.cognitive.RemoteReportAssessor` | six dispositions: `INVALID`, `REJECTED`, `QUALIFIED`, `SUPPORTED`, `UNRESOLVED`, `ABSTAINED` |
| Chain A's own evidence | `io.matrix.cognitive.KnowledgeStoreEvidence` | the adjudicator reads chain A's **own** knowledge store: a citable claim with supporting evidence supports the proposition, a candidate supports nothing, and a claim whose evidence contradicts it contradicts it. Until round 18 the only implementation of that question was "nothing", so every remote report came back unresolved for want of a reading rather than for want of evidence |
| Chain B's receiver | `io.matrix.api.bridge.TaskBridge` + `RemoteTaskStore` | the durable inbox: request id, payload hash, state, result; a duplicate returns the first result |
| Chain B's production adapter | `io.matrix.api.bridge.CognitiveCapability.standard(BrainCycle)` | the real brain of the gateway — never `StubBrainCycle` |
| Chain A's entry point | `io.matrix.cognitive.distributed.DistributedTaskResource` | `POST /v1/cognitive/task`, `POST /v1/cognitive/task/replay`, `GET /v1/cognitive/task/{id}` |
| Chain B's route | `POST /v1/bridge/task` on the gateway's own JDK server | the same server, the same port, the same process — no new port |

---

## 3. The evidence

| The claim | The proof | The command |
|---|---|---|
| The contracts hold | 10 contract tests | `./gradlew :matrix-core:test --tests "io.matrix.contract.*" --offline` |
| The intent is durable before the send | 5 coordinator tests | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.distributed.*" --offline` |
| The report becomes a candidate, and the verdict stays chain A's | 4 loop tests | the same |
| A contradicting report is recorded as a contradiction | 4 sink tests | `./gradlew :matrix-core:test --tests "io.matrix.bridge.*" --offline` |
| The peer deduplicates durably | 7 store tests | `./gradlew :matrix-api-gateway:test --offline` |
| The round trip is real and a forged report is dropped | 2 round-trip tests | the same |
| The whole scope is green | **1617 tests, 0 failures, 0 errors, 0 skipped** across 244 classes (matrix-core + gateway) | the scope command in the wave's baseline record |
| The quality gate holds | **PASS: 271 candidates, at or below the baseline of 271** | `bash scripts/quality-gate.sh` |
| The isolation gate holds | **30 checks, 0 failed** (G11 added) | `bash scripts/w45-chain-isolation-check.sh` |
| The new gate can go red | **15 checks, 0 failed** (C5 added: the task receiver is replaced with one that calls chain A's store, and the gate names the violation) | `bash scripts/w45-chain-isolation-selftest.sh` |
| The epistemic benchmark does not regress | **18/29** — the same eleven failures (A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02, F-02, R-04, R-05) | `python3 scripts/w51-epistemic-eval.py` |
| The two-process demonstration | the report `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` | `bash scripts/w53-distributed-demo.sh` |
| The outcome survives the restart of chain A | the task asked for again by its stable id after chain A restarted answers with the same request id, the same state and the same disposition, and the peer still holds exactly one row for it | the demonstration restarts chain A and checks it — the check fails when the trace is empty, when the disposition differs or when the peer holds a second row |
| The delegation reaches the cycle's reflection | a delegated task that does not settle the proposition becomes an uncertainty of the reflection, and the reflection turns it into a decision that can be carried out: `LEAVE_UNRESOLVED`, naming the request that left it open | `CognitiveKernel`'s remote phase → `CycleReflection.of(...)`; `CognitiveKernelRemoteTest` holds it — with the uncertainty disabled, exactly that test goes red |
| Both processes report their own health and the peer its active capabilities | chain A answers its Quarkus health endpoint and chain B its own; chain B names the capability it can really execute (`brain.cognitive-inference`), and the report records the status code with the body — chain A's endpoint answers 503 because the PostgreSQL connection check is down in this environment, and the task was served anyway | the demonstration's second check: the capability chain A used must be one the peer reports as active — with the peer's answer doctored, that check goes red |
| A repeated request never enters the cognitive path again | the peer's durable record answers the repeat, and the test counts the brain's calls: the count stays at zero | `TaskPeerRecoveryTest` — with the durable row absent the delivery is fresh, the brain is asked, and the test goes red |
| The performance report measures the processes, not only the client | the CPU time, the heap, the garbage collection, the allocation and the resident set of both processes, sampled from outside them | `bash scripts/w53-distributed-benchmark.sh --tasks 30` — the guard fails loudly when the numbers cannot be read |
| The performance report is reproducible | both workloads in one document, each with the command that produced it, and the per-sample latencies that separate the cold start from the steady state | `bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json` and the same with `--concurrency 4`, then `scripts/w53-merge-performance.py` |
| The measurement's own noise is measured | three runs of the same workload on the same day differ by 32 % at p50 (the sequential path) and by 112 % (the concurrent path's steady state) | the performance report's §5a |
| The peer's own work is told apart from the crossing | the peer reports the duration of its execution; chain A records it beside the crossing: the peer's inference is **1 ms** at the median against the crossing's **7.3 ms** (sequential), so the governed crossing, not the remote cognitive work, dominates the path | `CognitiveCoordinator` writes `peer_execution_nanos`; `CognitiveCoordinatorTest` holds it, and the test goes red when the measurement is removed |
| The cost of the durable idempotency path is measured apart from the first execution | the same requests are sent again with the same id and payload: the replay costs **14.26 ms** at p50 against the first execution's **27.43 ms**, **23.7 ms** of CPU against **86.0 ms**, and **30 of 30** replays answered with the disposition of the first execution | the benchmark's replay pass; the performance report's §5b; `CognitiveCoordinator` writes the `replay_*` stages and `CognitiveCoordinatorRetryTest` holds them (the test goes red when the measurement is removed) |
| A crash between the peer's answer and chain A's own verdict is finished by the pass | the process dies inside the report's admission: the peer's answer is on record, the verdict is not, and the dispatch is in the reconciliation queue — the restarted process finds it, sends the same request again and ends with a verdict and the state `ADJUDICATED` | `CognitiveCoordinatorRecoveryTest` — with the store's query reverted the dispatch is not found and the test goes red |
| A task that was called off is not sent again | a cancelled or a timed-out dispatch keeps the peer's answer on record **and unjudged on purpose**; such a dispatch is finished, not unfinished, and the reconciliation pass leaves it alone | `DispatchIntentTest` — with the exclusion removed the cancelled dispatch joins the queue and the test goes red |

---

## 4. The demonstration, stage by stage

`scripts/w53-distributed-demo.sh` starts two **real** processes — the Quarkus application and
the gateway's own JDK server in production mode — each in its own directory with its own
database, and runs one bounded cognitive task through them.

1. Chain A receives the task at `POST /v1/cognitive/task`.
2. The coordinator writes the dispatch intent **before** the send.
3. The signed, versioned request crosses the bridge.
4. Chain B authenticates it, checks the granted right, and admits it into its durable inbox.
5. Chain B runs its production cognitive path and produces a structured result and a trace.
6. Chain B persists the result and answers with a signed response and a signed report.
7. Chain A verifies both signatures, admits the report into its own epistemic model through
   its own bridge receiver — **as a candidate**, with the peer's relation and chain A's own
   confidence (which is none).
8. Chain A evaluates the result against its own evidence and records the verdict.
9. Chain B is restarted; the same request is sent again; the peer's inbox shows **one**
   row for that request id, with the result stored once.
9a. A capability the peer does not serve is **refused by the peer**, and the refusal travels
    back as a signed answer.
9b. The same request id under a **different payload** is refused as a conflict (HTTP 409),
    and the first dispatch's record stays untouched.
9c. A **forged request** — one whose signature was made with the wrong secret — is refused
    by the peer, and the demonstration checks the refusal's *reason*, so it cannot silently
    prove something else.
9e. **Both processes report themselves**: chain A answers its Quarkus health endpoint and
    chain B its own, and chain B names the capability it can really execute
    (`brain.cognitive-inference`). The demonstration checks that the capability chain A
    used is one the peer reports as active — so the peer's own word is the evidence, not
    the script's. This is §13.2 of the mandate.
9d. **Chain A is restarted** and the same task is asked for again by its stable id: chain A
    answers with the same request id, the same state (`ADJUDICATED`) and the same
    disposition it recorded before the restart, and the peer still holds **exactly one**
    row for that request id — chain A's reconciliation pass, which runs at the startup,
    found nothing unfinished and sent nothing. This is §13.22 of the mandate, and the
    demonstration checks it: the check fails when the trace is empty, when the disposition
    differs, or when the peer holds a second row.

**What the report does not claim**, in its own words: it does not claim that the remote answer
was correct, that the proposition is established, or that the system has distributed
exactly-once execution. The delivery is at-least-once with a durable deduplication at the
receiver; that is what the inbox row proves, and it is what the report says.

**The report's schema** is `matrix.w53.distributed-demonstration/v5` since round 33: v2
came with round 22, which added the `refusals` section (the unserved capability, the
conflicting payload and the forged request, each with the reason its own receiver gave)
and the three matching stages; v3 came with round 31, which gave the `replayAfterRestart`
section the replay's **own** measurements — the crossing, the admission and the assessment
of the replay, under the `replay_*` keys of its `timingsNanos` map; v4 came with round 32,
which added the `taskAfterChainARestart` section and the `whatTheChainARestartShows` lines
that say what it does and does not prove; v5 came with round 33, which added the
`processes` section — each process's own health answer with its status code, the peer's
active capability ids, and the capability chain A used with the place it ran — and the
stage `BOTH_PROCESSES_REPORTED_HEALTH_AND_ACTIVE_CAPABILITIES`. The version moved because
the section is new: a
reader that knows only the earlier versions must be able to tell that it is looking at
something they never had.

---

### 4a. The peer's own recovery, and what round 20 closed

The peer's inbox documented a promise it did not keep: "a request found in a
non-terminal state after a restart is visible to the reconciliation pass, and is neither
silently re-run nor silently failed". The reconciliation pass existed on chain A's side
(`DispatchReconciler`) but **not on the peer's** — and the peer's inbox already had the
method the promise named (`unfinished()`), unused.

The consequence was real: a request that was in flight when chain B died stayed
`ACCEPTED` or `RUNNING` for ever. Every later delivery of it was answered as "still
running", so the caller waited for a task nobody was doing — and, after round 19, it
waited with a signed `IN_PROGRESS` rather than with silence, which is honest but still a
wait for nothing.

**What the peer does now, at startup.** It closes the rows it finds unfinished as a
**reasoned failure**: the peer keeps the request's digest and not its payload, so it
cannot finish the execution itself, and re-running it would risk a second effect for a
task whose first effect may have landed. The row says exactly that — "the peer restarted
while this task was unfinished; the request was not kept, so the execution could not be
finished and its outcome is unknown" — and a later delivery of that task is answered
with the **failure**, not with "still running".

**What this does not claim.** The interrupted work is lost, not resumed. The caller
learns that it was lost and why, and the trace says so; the dispatch can be sent again
by the caller as a new attempt, which is a decision the caller owns.

### 4e. What round 22 added to the demonstration: the refusals

The mandate's acceptance list asks for three refusals beside the happy path (§13.24–26):
a conflicting payload under the same request id, a forged request or response, and a
capability violation. The wave *tested* all three, but the demonstration showed only the
happy path — and a scenario that shows only the happy path cannot show that the boundary
holds.

The demonstration now carries a `refusals` section (the report's schema moved to
`matrix.w53.distributed-demonstration/v5`):

| the scenario | who refused it | what the report shows |
|---|---|---|
| a capability the peer does not serve | **the peer** | the refusal travels back as a signed answer (`UNSUPPORTED`) |
| the same request id under a different payload | **chain A** | HTTP 409 with the reason; the first dispatch's record stays untouched |
| a forged request (a signature made with the wrong secret) | **the peer** | `REFUSED` with the reason *the signature does not verify* |

The forgery is checked by its **reason**, not by its status: `scripts/w53-forge-request.py`
builds the message so that only the signature is wrong, and it exits with a failure if the
peer refuses for any other reason. A demonstration that proved the wrong thing because the
message was malformed would be worse than no demonstration.

**One more defect the demonstration exposed.** The peer's durable row recorded a task it
does not serve as `FAILED` — the row said the peer tried and could not, when the truth is
that it never served that capability. The row's state now follows the peer's own reason
(`UNSUPPORTED`, `REFUSED`, `TIMED_OUT`, `CANCELLED`, `FAILED`), and the negative control
(with the mapping flattened) makes the matching test go red.

### 4c. What round 21 found in the reconciliation path, and what it changed

The round-19 change — the peer answers a duplicate with the task's status, and the
caller waits inside its budget — was applied to the **first** dispatch. The **replay**
path, which is what the reconciliation pass uses after a crash, did not know about
`IN_PROGRESS` at all: a replayed request that the peer was still running was **judged**,
and the task was closed with the verdict "the peer did not complete the execution:
IN_PROGRESS" while the peer was still working on it. The same defect, one path over.

Now both paths wait: a replayed task that the peer is still running stays `DISPATCHED`,
gets no verdict, and keeps its place in the reconciliation's report as "still in flight".

### 4d. What round 21 changed about the episode

§8.1 asks that the same logical task id and correlation be visible across both
processes, and that the final output be attributable to the originating task. The
correlation was travelling with the placeholder `"ep-parent"` — a name that stood for an
episode that did not exist — and the dispatch row did not name any episode at all.

Now the correlation carries the **real** episode id when the task was delegated from a
kernel episode, the dispatch row records it (`parent_episode_id`), and a task arranged
outside an episode names **no** episode rather than a made-up one. The kernel's trace
and the dispatch row now name each other, and the peer's request carries the same
episode id.

### 4b. What round 19 found in the demonstration, and what it changed

The demonstration failed twice in a row under machine load, and the failure was **not** a
flaw in the happy path: chain A reported "the peer did not answer the task and the retry
budget of 4 attempt(s) is spent", while chain B's own record showed the task
**completed** — the work was done, and the caller never learned it.

The cause was a contract gap, not a race: the idempotency rules say that a duplicate
request returns the *existing status*, and the peer returned **no status at all** for a
task that was still running. The caller had no way to tell "the peer is working on it"
from "the peer has not heard of it", so it spent its budget discovering a delay the peer
knew about and could have named.

Two changes followed, both measured:

1. **The peer answers a duplicate with the task's status.** A task that is still running is
   answered with a signed response whose status is `IN_PROGRESS` — the status the contract
   already had for it. The caller's question ("is it running?") now has an answer.
2. **Chain A waits inside its budget.** The coordinator does not treat `IN_PROGRESS` as an
   answer and does not judge it: it asks again within the retry budget, and it judges only
   what finally arrives. A task that is still running when the budget is spent ends as
   `DISPATCHED` — still in flight, still not judged — and the peer's record keeps the
   result.

The peer's own server serves on a pool of threads (`MinimalHttpServer`: a fixed pool of
eight), so it can answer a second delivery while the first task runs; the test's server
now does the same, because a single-threaded test server would have measured the queue
rather than the peer.

**What this does not claim.** The caller's budget is still bounded, and a peer that is
slower than the budget still loses the answer: the result stays on the peer, and the
dispatch can be sent again with the same request id. The change makes the loss *visible*
instead of misreported.

## 5. The decisions the research forced

| The research | The decision it forced | Where |
|---|---|---|
| Track A — durable execution | the intent is written **before** the send; at-least-once with effectively-once logical effects, never a distributed exactly-once claim | `ADR-W53-01` §2c |
| Track B — cybernetics | the request **carries the criterion** by which the answer will be judged (the `expectation` travels in the request's context); a failed delegation must change the next one | `ADR-W53-01` §2b |
| Track C — cooperative reasoning | the remote report carries the **relation and the lineage**; the sink must never invent a `SUPPORTS`; a report that rests on what chain A supplied is qualified, not supported | `ADR-W53-01` §2b, `RemoteReportAssessor` |
| Track D — reliability | a Wilson **lower** bound, and the evidence count has to be large enough before the estimate may enter the score (1 of 1 ⇒ an interval of width 0.730, which settles nothing) | `docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md` |

---

## 6. What is **not** done — the honest list

These are the gaps between this wave and the mandate's Definition of Done. Each names what
is missing, not a paraphrase of "future work".

1. ~~The coordinator is not yet inside the `CognitiveKernel`'s phase sequence.~~ **Done in
   round 7.** The kernel grew a **port** (`RemoteExecutor`), not a second selector: the
   `AlgorithmSelector` still chooses the method, and the port is asked only whether the
   capability that was already chosen is one another process can serve. When the delegation
   happens, the episode's trace shows four real transitions — `DISPATCH_REMOTE`, `RECEIVE`,
   `VALIDATE`, `EVALUATE_EVIDENCE` — and the peer's answer enters the cycle as a note that
   says in words that it is a report and not a settled fact; the local computation still
   decides the cycle's outcome. **Done in round 21:** the delegated task now belongs to
   the episode it was delegated in — the correlation carries the episode's real id instead
   of the placeholder `ep-parent`, and the dispatch row records it durably, so the
   kernel's trace and the row name each other and the peer's request carries the same
   episode. What remains: the episode and the dispatch are still two records in two
   stores, and a task arranged outside an episode names no episode at all.
2. ~~The reconciliation pass is not automatic.~~ **Done in round 12.** The request now
   travels with the durable intent (`cognitive_task_dispatch.request_json`), so after a
   crash the *same* request — the same request id, the same payload hash — can be sent
   again; `DispatchReconciler` walks the unfinished dispatches and sends each of them
   again, and chain A's entry point runs that pass when the process starts. The peer's
   durable inbox recognises the request id, so the logical effect still happens once.
   What remains: the pass runs at startup, not on a timer, so an answer lost while the
   process stayed up waits for the next restart; and the peer is not told that a dispatch
   was cancelled, so it may still be working on it. **Done in round 38:** the queue was
   missing one kind of unfinished work — a dispatch whose answer **is** on record but
   whose verdict is not, because the process died between receiving the answer and
   judging it. Such a dispatch was in no queue at all: it was neither "sent, no answer"
   nor "judged", so the task stayed unjudged for ever. The answer is not a verdict — only
   chain A can judge it — so the pass now finds it, sends the same request again and
   ends with a verdict and the state `ADJUDICATED`. The opposite case is closed with it:
   a cancelled or a timed-out dispatch keeps the peer's answer on record **and unjudged on
   purpose**, so it is finished and the pass leaves it alone.
3. ~~Cancellation and deadline propagation are not implemented.~~ **Done in round 11 on
   chain A's side.** The deadline is now honoured: a late answer produces the distinct
   `TIMED_OUT` state, and the answer the peer sent stays on record in the dispatch row —
   a timeout does not delete what the peer said, it removes chain A's judgement of it. A
   task can be cancelled while it is in flight, and a cancelled task ends as `CANCELLED`
   with no verdict. **Done in round 19 on the peer's side as well.** The withdrawal now
   travels to the peer as its own signed kind (`INFERENCE_CANCEL`, a right the peer
   grants — not a field of the request), and the peer keeps its own state machine: a task
   withdrawn before it started is never executed, a task withdrawn while it runs cannot be
   interrupted (the brain cannot be interrupted) so the work finishes and the row is
   closed as cancelled with the answer kept, and a task that already finished is not
   withdrawn at all — a cancellation cannot undo a committed effect. What remains: the
   deadline is not yet propagated to the peer's own work budget.
4. ~~**Circular evidence is detected by lineage, not by proposition.**~~ **Done in round
   18.** The assessor compares the report's `derivedFrom` with what chain A supplied, **and**
   the report's proposition with the propositions of those records: a peer that states the
   very proposition chain A supplied cannot make it independent by leaving its lineage
   undeclared. What remains: the comparison is literal, not semantic — two differently
   worded statements of the same thing are still not recognised as one proposition.
5. **The reliability estimate is researched, not scored.** The direction (Wilson lower bound,
   an evidence-count threshold) is settled and recorded, but the selector does not yet read
   it, and no held-out set has been measured to show that it would improve the selection.
6. ~~The distributed path is measured one task at a time; the concurrent path fails.~~
   **Done in round 15 — the defect was real, and it was fixed.** The write of the remote
   report into chain A's knowledge store died under concurrent writers with
   `SQLException: database in auto-commit mode`: the store owns one SQLite connection,
   every write opens a transaction on it, and `applyChanges` was **not** serialized while
   the rest of the class was. One writer's `setAutoCommit(true)` undid another's
   transaction, and the report that was being written was lost with it. The store's write
   transactions are now serialized on the store's own monitor, and
   `KnowledgeStoreConcurrencyTest` holds the line: four writers, one store, one workspace,
   and the check is the **state** afterwards — every claim on record, and still on record
   after a restart. With the serialization removed, both of its tests go red.

   A second defect was in the **measurement itself**: the benchmark's concurrent branch
   ended with a bare `wait`, which waits for the two servers as well as for the tasks, so
   the run stopped after the first batch and hung there. The script now waits for its own
   jobs.

   The numbers, from `docs-v2/engineering/W53-PERFORMANCE.json`: the single-task path
   (30 tasks) — p50 **27.43 ms**, p95 **37.73 ms**,
   throughput **26.18 tasks/s**, every sample `UNRESOLVED`; the concurrent path
   (24 tasks, four at a time) — every task adjudicated
   `UNRESOLVED`, the steady state (after the cold start of the first
   4 samples) p50 **75.37 ms**,
   **12.64 tasks/s**. The concurrent path is
   **slower per task** than the single-task one — four tasks in flight contend for the same
   store and the same peer — and the report says so rather than quoting the throughput of a
   run without its cold start. **The measurement's own noise is measured** (§3 and the
   performance report's §5a): three runs of the same workload on the same day differ by
   32 % at p50 on the sequential path and by 112 % on the concurrent path's steady state,
   so no optimization was accepted on the strength of a benchmark run.
7. **The earlier "concurrency defect" was a defect of the benchmark; the correction is
   recorded here rather than left in a chat.** The refusal `the request id
   req-w53-bench-N is already dispatched` was **correct behaviour**: chain A's cognitive
   database is persistent by design and the benchmark reused the request ids of the previous
   run, so the second run measured the durable idempotency instead of the path. The
   benchmark now stamps every run (`w53-bench-<runStamp>-<index>`) and the report states the
   stamp. `CognitiveStoreConcurrencyTest` shows that four threads with four distinct request
   ids are all recorded on one store — the store's own concurrency is sound. What the
   round-9 narrowing of the duplicate test changed: a busy or a not-null violation is no
   longer misreported as "already dispatched"; the SQL cause is kept on the exception.
8. ~~The concurrency and resource limits are not enforced at the boundary.~~ **Done in
   round 11 on chain A's side, and fixed in round 12.** The limits themselves were right,
   but the coordinator was built **per request** — so the concurrency cap counted every
   request as the only one in flight and was unreachable in production. The coordinator is
   now one per process, held by chain A's entry point, exactly like the store that is
   this process's single writer. The rest of the round-11 account stands: The coordinator now keeps its bounds in one named place
   (`DispatchPolicy`) and enforces them: a task whose input does not fit is refused
   **before** anything is written or sent, the number of dispatches in flight is capped
   and the work that exceeds the cap is refused rather than queued, the deadline is
   measured with an injected monotonic clock and a late answer is a **TIMED_OUT** state
   rather than a silent failure, and a task can be **cancelled** while it is in flight.
   **The peer's own side of the rule was closed in round 13**: the durable inbox is
   bounded on the work in flight, an input larger than the limit the task itself
   declared is refused before it reaches the brain, and work that outlasts the task's
   deadline is answered as `TIMED_OUT` with the result kept — a deadline that passes
   does not delete what was computed. Every refusal is durable and travels as a signed
   response, so the caller learns the reason from the peer instead of discovering it as
   a timeout. The negative control: with the four guards disabled, exactly the four
   matching tests of `CognitiveCoordinatorLimitsTest` went red; and with the peer's
   three bounds disabled, exactly the four matching tests of `TaskPeerBoundsTest` went
   red. **Done in round 19:** the withdrawal now reaches the peer, and the peer's own
   record says what became of the task (see §6.5 below).
9. ~~The retry budget is named but not spent.~~ **Done in round 14.** The count of the
   delivery attempts lives in the dispatch's own row (`attempts`), so the budget belongs
   to the *task* and not to the life of a process: a restart does not hand the task a
   fresh one, and a peer that stays down is dialled a bounded number of times in total.
   A delivery that failed is retried with the same request id — which is what makes the
   retry safe, because the peer's durable inbox recognises it — and when the budget is
   spent the dispatch becomes a **finished, traceable failure** with the reason written
   down, not a request that is dialled for ever. A defect in the coordinator is *not* a
   delivery failure: it is allowed to escape, so the dispatch stays unfinished and the
   reconciliation pass after the restart is what finishes it. The negative control: with
   the budget widened past the policy, exactly the two matching tests of
   `CognitiveCoordinatorRetryTest` went red.
10. ~~The capability registry, the test catalogue and the project state have not been
   updated.~~ **Done in round 7**: the registry carries **C-53** (the four axes, the serving
   path, the test evidence and the limitations), the catalogue carries **SCOPE-W53-DISTRIBUTED**,
   the project state names the wave, and the decision register carries **CD-034**. The
   **per-case epistemic comparison** was added in round 14 as
   `docs-v2/engineering/W53-EPISTEMIC-COMPARISON.md`: all 29 cases, their observation and
   the cause of each of the eleven failures, plus what the distributed path does — and
   does not do — to the epistemic model.
11. **The plugin path is partly exercised.** **Done in round 17 on the peer's side.** The
    remote execution's policy is the peer's allowlist, and it now checks what §11 names on
    the path the peer owns: the capability must be **registered** (the allowlist — there is
    no path from a name on the wire to a class or a reflection call), its **version** must
    be one this process serves (a task that asks for `v2` of a `v1` capability is refused
    with `UNSUPPORTED`, and the brain is never asked), and a capability that was
    **withdrawn** must not run again — the check happens on every invocation, so a
    revocation takes effect on the next call and the caller is told that it was withdrawn
    rather than that it never existed. `CognitiveCapabilityPolicyTest` — 4 tests; with the
    version check and the revocation check disabled, exactly the two matching tests went
    red. **What remains:** the *chain A* plugin registry's own lifecycle, health and budget
    checks are still not consulted — chain B must not and cannot reach chain A's classes
    (the isolation gate holds that), so the remote capability's lifecycle is the peer's
    business, and a plugin that serves a remote capability must register it there.
12. **The wave is not committed.** Every change was in the working tree until the owner
    authorised the commit and the push (round 22); the work of rounds 23–31 — the
    measurement of the processes, the reproducible merge of the two workloads, the noise
    floor, the separation of the peer's own execution and the separate measurement of the
    durable idempotency path — is in the working tree again and waits for the owner's word.
13. **The performance report separates the peer's own execution from the crossing, but not
    the peer's remaining stages.** Chain A times the crossing as one interval, and the peer
    carries one duration — its own execution — so the report now says how much of the
    crossing is the remote cognitive work and how much is the governed crossing around it
    (1 ms against 7.1 ms at the median, sequential). The peer's authentication, its inbox
    write and its answer signing are still inside the crossing; separating them needs the
    peer to carry more than one duration, and the protocol carries one today, so it is
    named as the next measurement and is not done. **What was done in rounds 23–31:** the
    two processes' own accounting is measured from outside them (the CPU time, the heap, the
    garbage collection, the estimated allocation and the retained memory), the two
    workloads live in one reproducible document, the **noise floor is measured** — three
    runs of the same workload on the same day differ by 32 % at p50 on the sequential path
    and by 112 % on the concurrent path's steady state — and the peer's own duration is
    recorded beside the crossing. The cause of the noise is not established, and the report
    says so rather than naming one. Because the noise is that wide, **no
    optimization was accepted on the strength of a benchmark run**: the mandate asks that an
    accepted change exceed the measurement noise, and nothing this wave changed was
    measurable above it.
14. **The reflection of a delegated task reaches the cycle, but the HTTP entry point does not
    run a cycle.** When the delegated task is part of a cognitive cycle, the cycle's
    reflection now names the delegation: a report that did not settle the proposition
    becomes an uncertainty of the reflection, and the reflection turns it into a decision
    that can be carried out — `LEAVE_UNRESOLVED`, naming the request that left it open
    (`CognitiveKernelRemoteTest` holds it; with the uncertainty disabled, exactly that test
    goes red). The demonstration's own path — `POST /v1/cognitive/task` — drives the
    coordinator directly and does not open a cycle, so there the decision is recorded in
    the dispatch row (the disposition and its reason), not in a reflection. Both records
    are durable and both are readable; they are two shapes of the same fact, and the
    mandate's §13.19 is answered for the cycle, not for the entry point.
15. **The cost of the durable idempotency path is measured, but what the replay does inside
    the peer is not.** Since round 31 the benchmark sends every request again, with the
    same id and the same payload, and the replay is timed, staged and measured on its own:
    14.26 ms against the first execution's 27.43 ms, 23.7 ms of CPU against 86.0 ms, and
    30 of 30 replays answered with the disposition of the first execution. What is still
    not told apart is the peer's own work inside the replay: the peer carries one duration
    — the duration of the **original** execution — so it cannot be attributed to the
    replay, and the report says so instead of recording it as the replay's cost. The
    replay also does **not** prove a distributed exactly-once guarantee: it proves that the
    peer's logical effect was not applied twice for these thirty requests, and the
    at-least-once delivery with an effectively-once effect remains what the design claims.

---

## 7. The closeout, still to do

The remaining deliverables of §20: the per-case epistemic comparison, the before/after
measurements for any accepted optimization, and the list of the remaining experimental
components. **The performance report with the stage-by-stage breakdown is written**
(`docs-v2/engineering/W53-PERFORMANCE.md` beside its machine-readable form) — it states the
single-task numbers, the concurrent numbers with the cold start separated, the two defects
that stood in the way (the knowledge store's unserialized writes and the benchmark's bare
`wait`), and the correction of the benchmark's request-id defect. The research documents
are persisted (`docs-v2/research/W53-RESEARCH-A-DURABLE-EXECUTION.md`,
`docs-v2/research/W53-RESEARCH-B-CYBERNETICS.md`,
`docs-v2/research/W53-RESEARCH-C-COOPERATIVE-REASONING.md`,
`docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md`), and per **META-R2** their
findings are carried into `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` (§ W53), into
`docs-v2/designs/DESIGN-84-distributed-cognitive-bridge.md` and into the hypotheses
**H-129 … H-133** of `HYPOTHESES-NEW.md`.

### The components that remain experimental, disconnected or deferred

The list §20 asks for, and it is a list of facts, not of excuses:

| the component | the state | why |
|---|---|---|
| the method reliability term in the selector's score | **deferred** | the direction is researched (Wilson lower bound), but there is no representative evidence base and no held-out set that would show the term helps; an unjustified number is worse than none (H-132) |
| the full circular-evidence predicate | **partial, and one step further** | the lineage travels with the report and is compared with what chain A supplied — a report resting on it is QUALIFIED, not SUPPORTED — and since round 18 the **proposition itself** is compared too: a report that states the very proposition chain A supplied is QUALIFIED even when the peer declares no lineage at all (H-131). What remains: the comparison is literal, not semantic — two differently worded statements of the same thing are not recognised as the same proposition |
| the plugin registry's lifecycle, version, health and budget checks in the remote receiver | **not wired in** | the receiver resolves capability ids through an explicit, allowlisted map, which is the boundary §11 asks for, but the registry's own checks are not part of it yet |
| the peer's cancellation | **implemented in round 19** | the withdrawal travels as its own signed kind and the peer acts on it: a task withdrawn before it started is never executed, a task withdrawn while it runs finishes and is closed as cancelled with the answer kept, and a finished task is not withdrawn. What remains: the peer cannot interrupt the brain cycle it is inside, so a running task always runs to its end — the cancellation changes the record, not the computation |
| the reconciliation on a timer | **startup only** | the pass runs when chain A starts — the unfinished work exists exactly after a crash — but an answer lost while the process stayed up waits for the next restart |
| the peer's own unfinished rows after a crash | **closed in round 20** | the peer's inbox keeps the request's digest and not its payload, so it cannot finish an interrupted execution; the rows it finds in `ACCEPTED` or `RUNNING` are closed as a reasoned failure when the process starts (`RemoteTaskStore.reconcileUnfinished`), and a later delivery of such a task is answered with that failure rather than with "still running". What remains: the interrupted work is lost rather than resumed — the peer cannot resume it, and re-running it would risk a second effect for a task whose first effect may have landed |
| the eleven remaining epistemic failures | **deferred to a follow-up wave** | they are a retrieval question (the relevance floor and the status filter in `KnowledgeRetriever`), and §14 forbids turning W53 into a rewrite of the retriever |
| the native and GPU paths | **not enabled** | §10.4 keeps the Java path selected; nothing in the end-to-end profile exposed a computational bottleneck that would justify the marshalling cost |
| the concurrency of the distributed path | **measured, not optimized** | the concurrent path is slower per task than the single-task one; no optimization was accepted, because nothing was profiled as a profile (CD-W42-01) |
| the wave's commit, push and pull request | **commit and push done; the pull request is not** | the owner authorized the wave's commits on 2026-11-11, and the branch `feature/w53-distributed-cognition` carries them: the measurement tooling and the performance report, the implementation with the crash boundary it lost, and the registers that name the commit which carries the wave. The **pull request** is the next step and it asks for the owner's word |
