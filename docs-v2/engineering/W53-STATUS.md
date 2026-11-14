# W53 — Distributed Cognition, Durable Task Execution & Closed-Loop Collaboration

**Status:** `RELEASED` — the wave is complete and released into `main` (pull request #50, squash `bcdd1a0a`, 2026-11-14; release record `c4cee8ab`; registers `1dc01537`). The body below was written at the wave's closeout on `develop`; it is preserved as the wave's own record. The release addendum is §12.
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
| The bridge, extended | `io.matrix.contract.bridge.BridgeProtocol` (**version 3**) · `BridgeCapability` (`INFERENCE_REQUEST`, `INFERENCE_RESPONSE`, `INFERENCE_CANCEL`) · `BridgePayload` (9 components) | version 1 and 2 stay readable; the new rights are explicit |
| Chain A's coordinator | `io.matrix.cognitive.distributed.CognitiveCoordinator` | one owner of the distributed task: intent → dispatch → check → evaluate → record; the transport is a **port** |
| Chain A's outbox | `io.matrix.cognitive.DispatchIntent` + the `cognitive_task_dispatch` table | the intent is durable **before** the send; ten states; the peer's vocabulary is mapped explicitly |
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
| The contracts hold | **12** tests in `CognitiveTaskContractTest` — a typed and bounded request, the refusal of recursive delegation, a request without its bounds, the payload that carries nothing else, the kind and the content agreeing, the withdrawal and the task messages surviving the codec, the old messages keeping their meaning, a response that is not a result saying why, a confidence saying what it rests on, a response bound to its request, and a receiver without the rights refusing the task | `./gradlew :matrix-core:test --tests "io.matrix.contract.bridge.CognitiveTaskContractTest" --offline` |
| The intent is durable before the send | the test `theIntentIsDurableBeforeTheSend` in `CognitiveCoordinatorTest` (**11** tests in the class, 0 failures) | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.distributed.CognitiveCoordinatorTest" --offline` |
| The report becomes a candidate, and the verdict stays chain A's | **4** tests in `CognitiveCoordinatorLoopTest` | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.distributed.CognitiveCoordinatorLoopTest" --offline` |
| A contradicting report is recorded as a contradiction | **4** tests in `io.matrix.bridge.TaskReportSinkTest` | `./gradlew :matrix-core:test --tests "io.matrix.bridge.TaskReportSinkTest" --offline` |
| The peer deduplicates durably | **7** tests in `io.matrix.api.bridge.RemoteTaskStoreTest` | `./gradlew :matrix-api-gateway:test --offline` |
| The round trip is real and a forged report is dropped | **3** tests in `io.matrix.api.bridge.TaskRoundTripTest` — the bridge reports the capability it really serves, a dispatched task comes back, a forged report is dropped | `./gradlew :matrix-api-gateway:test --tests "io.matrix.api.bridge.TaskRoundTripTest" --offline` |
| The whole scope is green | **1646 tests, 0 failures, 0 errors, 0 skipped** across 250 classes (matrix-core + gateway), re-measured on 2026-11-12 against the final tree, counted from the retained JUnit XML of both modules | the scope command in `docs-v2/engineering/TEST-CATALOGUE.yml` (SCOPE-W53-DISTRIBUTED) |

**Every count in this table is the retained JUnit XML's, counted from the files, and the
first six rows were re-audited on 2026-11-11 against `matrix-core/build/test-results/test/*.xml`
and `matrix-api-gateway/build/test-results/test/*.xml`.** Two of them were wrong and are
corrected above: the contract class carries **12** tests and not 10, and the round-trip
class carries **3** and not 2 — the classes grew, and the table counted the growth wrong
rather than the other way round. **A count that is not checked against the artefact is an
anecdote**, and the row now names the class it counts so that the next reader can count
the same thing.
| The quality gate holds | **PASS: 271 candidates, at or below the baseline of 271** | `bash scripts/quality-gate.sh` |
| The isolation gate holds | **32 checks, 0 failed** (G11 added for the task direction, G12 for the other half of it) | `bash scripts/w45-chain-isolation-check.sh` |
| The new gate can go red | **19 checks, 0 failed** (C5 replaces the task receiver with one that calls chain A's store; C6 replaces chain A's task publisher with one that opens chain B's own database; the gate names both violations) | `bash scripts/w45-chain-isolation-selftest.sh` |
| The epistemic benchmark does not regress | **18/29** — the same eleven failures (A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02, F-02, R-04, R-05), **re-run on 2026-11-12 against the wave's final tree**, with the per-case table of `W53-EPISTEMIC-COMPARISON.md` verified row by row against that run (29 of 29 rows match, verdict, observation, problem and cause) | `python3 scripts/w51-epistemic-eval.py --work /tmp/w53-eval-final --report docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` |
| The baseline reproduces on the wave's final tree | **the sequential p50 14.95 ms against the published 14.29 ms (+4.7 %, inside the measured noise of 9.9 %), the concurrent steady p50 27.05 ms against 27.03 ms (+0.1 %), the steady throughput 145.75 against 143.95 tasks/s, and the same twelve of twenty-four tasks refused above the cap**; the tails (the p95, the cold start, the refusal latency) moved by 14–19 % and their noise was never measured — named, not explained | §4o; the artefact `docs-v2/engineering/W53-PERFORMANCE-VERIFICATION.json`; the commands are in §4o |
| The peer's answers reach the profile of methods | **the remote path is the one place where a verifier exists — chain A's own assessment — so it is the source of the system's first decided trials.** Each verdict is recorded in the stratum of the capability, the task family and the capability version that ran; a supported-but-not-independent report is its own kind (`QUALIFIED`): a decided trial that is not a validated one. The rate that produces is stated with its confounder — it is about the peer *and* about chain A's evidence | §4q; `RemoteVerdicts`, `CognitiveCoordinator.VerdictSink`; `RemoteVerdictsTest` and two coordinator tests; CD-046 |
| The CI state, reproduced | **ShellCheck 0.10.0: 156 files in the tree carry findings (182 findings in total) — the inherited number, all of them in scripts this wave never touched; the wave's own scripts carry 0 findings after one fix (`SC2155` in `w53-distributed-benchmark.sh`)**; the formal gate 15/15; the security boundary held and the live databases were untouched; TruffleHog is a CI step that runs on the push and is **not** reproducible here — the wave's own files carry no secret-shaped literal | §4p; `find . -name "*.sh" -exec shellcheck {} +`, `scripts/formal-gate.sh`, `scripts/security-boundary-check.sh` |
| The two-process demonstration | the report `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` | `bash scripts/w53-distributed-demo.sh` |
| The outcome survives the restart of chain A | the task asked for again by its stable id after chain A restarted answers with the same request id, the same state and the same disposition, and the peer still holds exactly one row for it | the demonstration restarts chain A and checks it — the check fails when the trace is empty, when the disposition differs or when the peer holds a second row |
| The delegation reaches the cycle's reflection | a delegated task that does not settle the proposition becomes an uncertainty of the reflection, and the reflection turns it into a decision that can be carried out: `LEAVE_UNRESOLVED`, naming the request that left it open | `CognitiveKernel`'s remote phase → `CycleReflection.of(...)`; `CognitiveKernelRemoteTest` holds it — with the uncertainty disabled, exactly that test goes red |
| Both processes report their own health and the peer its active capabilities | chain A answers its Quarkus health endpoint and chain B its own; chain B names the capability it can really execute (`brain.cognitive-inference`), and the report records the status code with the body — chain A's endpoint answers 503 because the PostgreSQL connection check is down in this environment, and the task was served anyway | the demonstration's second check: the capability chain A used must be one the peer reports as active — with the peer's answer doctored, that check goes red |
| A repeated request never enters the cognitive path again | the peer's durable record answers the repeat, and the test counts the brain's calls: the count stays at zero | `TaskPeerRecoveryTest` — with the durable row absent the delivery is fresh, the brain is asked, and the test goes red |
| The performance report measures the processes, not only the client | the CPU time, the heap, the garbage collection, the allocation and the resident set of both processes, sampled from outside them | `bash scripts/w53-distributed-benchmark.sh --tasks 30` — the guard fails loudly when the numbers cannot be read |
| The performance report is reproducible | both workloads in one document, each with the command that produced it, and the per-sample latencies that separate the cold start from the steady state | `bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json` and the same with `--concurrency 4`, then `scripts/w53-merge-performance.py` |
| The measurement's own noise is measured | **seven runs of the same code, each against a store of its own**, differ by **9.9 %** at p50 on the sequential path (six of the seven lie inside 3 %) and by **2.7 %** at the median of the concurrent path's steady state; the earlier table across the wave's rounds is the path's **history**, not the measurement's variance — the code changed between those rounds, and the state the measurement ran against grew | the performance report's §5a |
| The bound itself is measured, not only asserted | **eight clients against the coordinator's cap of four**: **12 of 24 tasks refused** at a client round trip of **3–33 ms**, with no stage timings (the refusal happens before the durable intent is written) and **no durable record** — the replay of a refused request is answered "no dispatched task with that id" while the replay of an admitted one is answered from the peer's record. **The queue depth is 0 and the wait is 0: the cap refuses the excess instead of queueing it** | the performance report's §5d; the coordinator's `DispatchPolicy` and `arrange(...)`; `CognitiveCoordinatorLimitsTest` holds the cap |
| The measurement is taken against a store of the run's own | chain A's cognitive and knowledge stores are named to the benchmark's own directory (`-Dmatrix.cognitive.path`, `-Dmatrix.knowledge.path`), so the measurement does not include the state the earlier runs of the wave left behind: the same workload against the **accumulated** store gave **32.33 ms** at p50, against a store of its own **13.87 ms** — the state, not the crossing, was the dominant cost | `scripts/w53-distributed-benchmark.sh --work-dir`; the performance report's §5c |
| The peer's own work is told apart from the crossing | the peer reports the duration of its execution, and chain A records it beside the crossing: the peer's inference is **1 ms** at the median against the crossing's **5.84 ms** (sequential), so the governed crossing, not the remote cognitive work, dominates the path. **Done in round 41:** the peer's **own stages** are separated too — its durable inbox write (**0.215 ms**), the write that starts it (**0.024 ms**), its execution (**1.272 ms**), the write of the outcome (**0.192 ms**), the signing of the answer (**0.493 ms**), and the remainder of the reception, which carries the authentication and the validation together (**0.671 ms**), against its whole reception of **2.530 ms** | `CognitiveCoordinator` writes `peer_execution_nanos`; `PeerStageTimingsTest` holds the peer's own stages — with the measurement removed the test goes red, and with the execution stage turned into a constant it goes red too |
| The cost of the durable idempotency path is measured apart from the first execution | the same requests are sent again with the same id and payload: the replay costs **8.07 ms** at p50 against the first execution's **14.29 ms**, **18.3 ms** of CPU against **64.7 ms**, and **30 of 30** replays answered with the disposition of the first execution | the benchmark's replay pass; the performance report's §5b; `CognitiveCoordinator` writes the `replay_*` stages and `CognitiveCoordinatorRetryTest` holds them (the test goes red when the measurement is removed) |
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

**The report's schema** is `matrix.w53.distributed-demonstration/v6` since round 33: v2
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
`matrix.w53.distributed-demonstration/v6`):

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

### 4f. What round 43 added: the reconciliation pass on a timer

The honest list carried one item for ten rounds: *"the reconciliation on a timer |
startup only"*. The startup pass covers the crash — the process died, the unfinished
work is on record, the restart finishes it. It did not cover the case where the process
stays up and the answer is lost anyway: a connection that drops, a peer that is
restarted mid-flight. Then the task waits for the next restart, which may be never.

**What round 43 added.**

1. `DispatchReconcilerTimer` (new) — a JDK `ScheduledExecutorService` with one daemon
   thread, started when chain A starts and stopped when it shuts down. No new
   dependency: `quarkus-scheduler` is not on the classpath, and a timer that the
   framework owns would have been a dependency for one method call. The interval is a
   named constant (30 s).
2. `DispatchReconciler.reconcileStale()` — the timer walks the unfinished dispatches
   and sends the ones this process is **not** working on right now. The distinction
   matters: on the record, a dispatch that is in flight and a dispatch whose answer was
   lost look the same — the row says `DISPATCHED` and carries no answer. The
   coordinator now keeps the request ids it is working on in memory
   (`CognitiveCoordinator.isInFlight`), and the pass leaves those alone. A second
   delivery of a request that is still running would spend one retry of the durable
   budget for nothing, and with a small budget it would close a task that is not
   closed.
3. **The clock decides when, the record decides what.** The wall clock selects the tick;
   the pass never reads it to decide whether a dispatch is old enough. That is the rule
   `AGENTS.md` sets for a decision path, and the timer keeps to it: the *schedule* is
   operational policy, the *content* of the pass is read from the durable record.
4. **The budget-spent hole, closed.** `CognitiveCoordinator.resend` returns `FAILED`
   *without writing the row* when the retry budget is spent, so the row stayed
   `DISPATCHED` and every pass picked it up again, for ever. The pass now closes such a
   dispatch before it sends it: the row becomes `FAILED` with its reason and leaves the
   unfinished queue.

**A real defect the round found.** The first version of the closure called
`CognitiveStore.recordDispatchDisposition`, which is the *adjudication* write: it
records the verdict chain A reached about the peer's answer and moves the row to
`ADJUDICATED`. A dispatch with a spent budget has no answer at all, so that write
claimed an adjudication that never happened — the row was closed as `FAILED` and
immediately overwritten with `ADJUDICATED`, and the closure did not hold. The store now
has `closeDispatchAsFailed`, which says exactly what is true: the dispatch is over, it
failed, and here is why. The defect was found by the test, not by inspection.

**The evidence.** `DispatchReconcilerTimerTest` (3 tests) holds the three claims: the
timer finishes a dispatch whose answer was lost; the timer leaves a dispatch that is in
flight alone; a dispatch whose budget is spent is closed and is not picked up again.
Three negative controls were executed and were red, and every file was restored
byte-exactly (md5 verified):

| the control | what it breaks | what went red |
|---|---|---|
| A | the in-flight exclusion in `reconcileStale` | *"a request that is still in flight must not be sent again"* |
| B | the closure of a budget-spent dispatch | *"a dispatch that will not be sent again must be closed"* |
| C | the failure write claims `ADJUDICATED` | *"a dispatch that will not be sent again must be closed"* |

Control C is the defect above, kept as a control: it proves that the new store write is
what holds the closure, and not the old adjudication write.

**What this does not claim.** The timer does not make the delivery exactly-once. The
re-delivery is at-least-once, and the logical effect is effectively-once because the
peer's durable inbox recognises the request id. The timer also does not make the peer
finish an interrupted execution: the peer keeps the request's digest and not its
payload, so a task the peer was interrupted in stays the peer's open case (§6).

### 4g. What round 44 corrected about the measurement's own noise

> **Superseded by §4h.** The runs below were measured against the store the wave had
> accumulated, not against a store of its own: the state is part of what was measured, and
> §4h re-measures the noise against the same state and gives the number the wave quotes now
> (9.9 % on the sequential path, 2.7 % at the median of the concurrent one).

The wave's performance report carried a table of five runs of "the same workload" and
called the spread a **noise floor**: 43 % at p50 on the sequential path, 157 % on the
concurrent path's steady state. Round 44 measured the noise the way §10.3 asks for it —
**on the same code** — and the table turns out to have been a misreading.

**What was measured.** The run of round 43, plus three further runs of the identical build
back to back with nothing else running (sequential path, 30 tasks) and two further runs of
the concurrent path (24 tasks, four in flight):

| the workload | run of round 43 | the next | the next | the next | the spread |
|---|---|---|---|---|---|
| the sequential path, p50 | 31.41 ms | 30.28 ms | 32.49 ms | 31.30 ms | **7.3 %** |
| the sequential path, the throughput | 22.62 tasks/s | 25.09 tasks/s | 23.35 tasks/s | 23.73 tasks/s | 7.4 % |
| chain A's own total | 28.3245 ms | 27.1351 ms | 28.3225 ms | 27.7997 ms | 4.4 % |
| the replay path, p50 | 16.94 ms | 15.13 ms | 16.69 ms | 15.60 ms | 10.3 % |
| the concurrent path, the steady-state p50 | 93.84 ms | 103.69 ms | 98.06 ms | — | **10.5 %** |
| the concurrent path, the steady-state throughput | 10.68 tasks/s | 10.11 tasks/s | 10.40 tasks/s | — | 5.7 % |

**What the earlier table actually was.** The wave changed the code between those rounds —
the episode integration, the adjudication of the report, the trace, the timer's in-flight
set — so the table shows the **cost of that work**, not the variance of a measurement.
Calling it a noise floor was wrong twice over: it understated how stable the measurement
is, and it overstated the reason to refuse an optimization.

**What follows.** The noise of this path is **7–11 %**, and that is the threshold an
accepted change must exceed. The wave's decision to accept **no** optimization on the
strength of a benchmark run stands — and it now rests on a measured number rather than on
a shrug. The run reported in §3 and §5.2 of the performance report sits inside the band of
the same-code runs, so those figures are representative rather than outliers.

**A piece of hygiene came out of it too.** Both wave scripts take their working state
from `mktemp -d` and neither removed that directory at the end of a **successful** run:
eight directories were left in `/tmp` after the measurements above. A finished run now
removes its directory (its report is written outside it), and an unfinished run keeps it
and says where it is — the logs of the two processes are the evidence of what happened.
After the fix: **no directory is left behind by either script**.

**A fact about the peer came out of it.** The peer keeps its own measurement in its own
row, so every one of those runs collected it: the peer's own execution sits between
**1.28 ms and 1.48 ms** at the median across four runs of the same code, while its p95
moves by **30 %**. The remote cognitive work is a small, stable part of the crossing; the
variance of the path lives in the **tail** of the remote execution, not in its median.
That is a fact about the tail and not a diagnosis of it.

### 4h. What rounds 48–53 corrected about the measurement itself

Round 48–49 gave the wave's benchmark **its own stores**: chain A's cognitive store and its
knowledge store are now named to the directory the benchmark works in
(`-Dmatrix.cognitive.path`, `-Dmatrix.knowledge.path`), and the benchmark can be told which
directory to work in (`--work-dir`) and to keep it (`--keep-work`) when a run fails. What
that exposed is worth a section of its own, because **the wave's earlier performance numbers
were a measurement of the laboratory, not of the path**.

**What the state was doing to the numbers.** The same workload — thirty tasks, one at a
time — measured against the store the wave had accumulated gave **32.33 ms** at p50; the
same code against a store of its own gave **13.87 ms**. The difference is not the crossing
and not the peer's work: it is the **assessment**, the comparison of the peer's report
against chain A's knowledge, which grew from **0.35 ms** against a store of thirty claims to
**6.57 ms** against the thousands the wave's own runs had left behind. The accumulated
state was the dominant cost of the path, and the wave had been reading it as the cost of
the work it had done.

**Three conclusions the wave's earlier report drew, and what they are now.**

| the earlier claim | what the measurement shows now |
|---|---|
| "the concurrency does not buy throughput on this path" | **wrong, and it was the state that made it wrong.** Against a store of its own, four tasks in flight reach **143.95 tasks/s** in the steady state against the single-task path's **42.99** — the concurrency buys **3.3 times** the throughput, at the price of a doubled latency (27.03 ms against 14.29 ms at p50) and of more CPU per task (117.5 ms against 64.7 ms) |
| "the path grew by 51 % over the wave's rounds" | **the path did not grow; the laboratory did.** The run measured against a store of its own (14.29 ms) is *faster* than every earlier run of the wave, the fastest of them being 20.77 ms |
| "the noise of the measurement is 7–11 %" | **it is 9.9 % on the sequential path** — seven runs of the same code, each against a store of its own, from 13.87 ms to 15.24 ms — and **2.7 % at the median of the concurrent path**. The earlier figure was measured against the accumulated store, so it was the variance of a different path |

**The history is bounded, and the growing term is named.** §16 asks for bounded behaviour
under a growing cognitive history. Measured against one store that grows from thirty to
ninety dispatches, the end-to-end p50 moves from 14.26 ms to 15.26 ms — **one millisecond
for three times the history**, which is the size of the measurement's own noise. The
growing term is identifiable: the assessment goes **0.346 → 0.562 → 0.773 ms** as the
knowledge grows, because it compares the peer's report against what chain A holds. It is
still a small part of the path (0.77 ms of 15.26 ms at the median), and the report says
where the growth is rather than only that it is small.

**A claim the wave does not make.** The measurement is bounded under a growing history *for
the range measured* — thirty to ninety dispatches. It is not a proof that the path is
bounded for ever: the assessment is linear in the knowledge it compares against, and a
knowledge base of a million claims would cost a million comparisons. The honest statement is
the measured one, and the next wave's work is the retriever, not the bridge.

**What did not change.** The demonstration still passes end to end against the isolated
stores (`docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json`, schema v6, sixteen stages,
all `OK`), the peer still holds exactly one durable row for the task after the restarts of
both processes, and the epistemic benchmark is still 18/29.

### 4i. What round 55 re-verified: the epistemic model, byte for byte

The wave's later rounds changed the coordinator, the episode, the trace, the durable
intent, the reconciliation timer, the store paths and the measurement. **None of them
touched the retriever — and the wave now holds the proof of that in bytes.**

**What was run.** The original 29-case evaluation, the same runner, the same dataset, the
same expectations, against the tree that carries the whole wave:

```
python3 scripts/w51-epistemic-eval.py --work /tmp/w53-eval-final \
  --report docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json
```

**What it produced.** **18 of 29** — the same eleven failures (A-02, A-04, A-05, A-06,
F-02, M-01, M-03, P-01, P-02, R-04, R-05), the same observations, the same first
problems and the same causes. The machine-readable report came out with the checksum
`957f97de55fa40ba5b4858babc023889` — **the checksum of the file committed on 2026-11-09**:
`git diff` on it is empty. **A byte-identical result is a stronger statement than an equal
score** — it says that what the retriever does did not change at all, not only that its
verdict did not.

**The per-case table was verified row by row.** All twenty-nine rows of
`W53-EPISTEMIC-COMPARISON.md` were compared with the fresh report — the verdict, the
observation, the first recorded problem and the place it comes from — and every row
matches. The table is the current run's own record, not a remembering of the earlier one.

**What this does not claim.** It does not claim that the eleven failures are fixed: they
are a retrieval question (the relevance floor and the status filter in
`KnowledgeRetriever`, and the evidence demand of the multi-claim and revision cases), and
§14 of the mandate forbids turning W53 into a rewrite of the retriever. It does not claim
that the distributed path is harmless to the epistemic model — the wave's own work is what
keeps it harmless: the remote report is admitted as a **candidate**, the verdict on the
proposition is a **separate fact**, and a report resting on what chain A itself supplied
is QUALIFIED and not SUPPORTED.

### 4j. What round 56 audited: the wave's own evidence table

A wave's evidence table is a set of claims about artefacts. **This round counted them
against the artefacts**, and two of them were wrong.

**What was checked.** The first six rows of §3 name a number of tests. Each number was
counted from the retained JUnit XML (`matrix-core/build/test-results/test/*.xml` and
`matrix-api-gateway/build/test-results/test/*.xml`), class by class.

**What was wrong.** Two rows undercounted, and they undercounted in the direction that
makes a report look *worse* than the truth:

| the row | what it claimed | what the class holds |
|---|---|---|
| the contracts hold | 10 contract tests | **12** — `CognitiveTaskContractTest` |
| the round trip is real and a forged report is dropped | 2 round-trip tests | **3** — `TaskRoundTripTest` (the bridge reports the capability it really serves, a dispatched task comes back, a forged report is dropped) |

The classes grew and the table did not. **The correction is not a matter of taste: a row
that names a class and a count is checkable, and a row that names a category is not.**
Every row of the first six now names the class it counts, the command that runs it, and —
where the claim is one test — the test's own name.

**What the audit did not find.** The other 22 rows of §3 stand as they are: the scope
(1646 tests, 250 classes, 0 failures), the gates (271/271, 32/0, 19/0), the epistemic
benchmark (18/29 with the same eleven failures), the demonstration (16 stages, all `OK`),
the performance numbers (checked against `W53-PERFORMANCE.json` field by field) and the
over-cap measurement (12 of 24 refused, no durable record).

**The rule this round adds to the wave.** A count that is not checked against the artefact
is an anecdote. The wave's own table said "10" and "2" for two classes that hold 12 and 3
— and nobody noticed for forty rounds, because the numbers were plausible and the wave was
moving. **The audit is cheap** (one pass over the XML) and **the report is the artefact
that outlives the wave**, so it is the report that must be checked.

**The same audit was run over the performance numbers of every wave document.** Eleven
figures — the sequential p50 and p95, the sequential throughput, the concurrent whole-run
and steady-state p50, the steady-state throughput, the crossing, the replay, the CPU per
task, the CPU per replay and the refused tasks — were computed from
`W53-PERFORMANCE.json` and compared with what the seven wave documents say: **eleven of
eleven agree, zero mismatches**.

**And the audit found a design document that no longer matched the code.** The ADR's §4.3
spoke of "a bounded queue" — the implementation has **no queue**: the coordinator refuses
the work over its concurrency cap. §4.3 now states the rule as it is implemented (the
refusal, the empty durable trace, the work that was not done), §6.4 states what the
measurement of the bound does and does not say, and the ADR's list of what the wave does
not claim now carries the queue and the portability of the numbers.

### 4k. What round 58 audited: the wave's place in the document tree

The wave's code was measured and its own tables were counted against the artefacts
(§4j). This round asked a different question: **does the wave's documentation say what the
wave did, and can a reader reach it?** The document-integrity pass — the seven defect
classes and the four checkers of the `matrix-document-integrity` skill — found **seven
defects, all of them the wave's own**, and all of them are now closed.

**1. Two waves carry the name W53, and the second had overwritten the first's report.**
`docs-v2/engineering/W53-STATUS.md` was the *reusable-procedure* wave's completion report;
the distributed-cognition wave wrote over it. The first wave's report was preserved as
`docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` with a note that says which wave it
belongs to — and **the note itself named the two reports the wrong way round**. Both the
overwrite and the note are fixed.

**2. The roadmap's §4.12 — the first wave's seven tasks and their evidence — had been
overwritten** by the distributed wave's rows. It was restored from `origin/develop`, and the
note that tells a reader which wave a section belongs to was written.

**3. The same published ids were used twice**: `TASK-W53-01…07` by both waves. The
distributed wave's ids are now `TASK-W53-2-01…14` (internal `W53-2-00…13`). The first
attempt used a *letter* as the suffix and **the link checker itself rejected it** — its
identifier grammar stops an identifier at a letter that follows the number — so the suffix is
a digit. **CD-043** records the rule: *a published identifier is never reused for a
different task*.

**4. One strict link-check failure**, and the cause was not the identifier:
`TASK-W53-2-07` was "referenced 1 time(s) and never defined" because the roadmap row that
declares it **did not end with the closing `|`** — the checker did not see it as a table row
at all. One character, and a declaration the checker's eye could not reach. The same row
also lost a full stop in the middle of its sentence; both are fixed, and the strict link
check now reports **0 failures** (it reported 1 at the start of this round).

**5. Six of the wave's documents declared a status outside the vocabulary** (`IN PROGRESS`,
"W53 evidence", "research input for W53" …) and four more declared none at all. A status is
an explicit line with a value from the policy's vocabulary, so all ten now say `ACTIVE`,
`RESEARCH` or `CANONICAL` — the value each document actually is.

**6. Twelve of the wave's documents were unreachable from the index** — the checker counts a
document that the index cannot reach as a defect. `docs-v2/INDEX.md` §5 now names the wave's
family with a link to each of its documents; the index reaches **137** documents by a
clickable link against **125** before.

**7. The document census did not know the wave.** `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv`
is the registry of the tree and it was frozen at W46. The wave added its own **12 markdown
documents and one new row for `docs-v2/waves/INDEX.md`** — 13 rows — classified that index as
`CANONICAL` (the status the work log declares for itself) and reclassified `WAL-021` from `ACTIVE` to `HISTORICAL` — it is a dated
completion report of W32, like all 319 of its siblings, and it was the only wave report left
as `ACTIVE`. The index's published counts were recomputed from the census: engineering
75 → **82**, research 178 → **182**, designs 88 → **89**; waves **321** was already published
and is now true.

**The debt that was not paid, and why.** After the wave's own rows, **69 documents under
`docs-v2/` are still not in the census**: the governance set (11), the engineering reports
of W46.0–W52 (41), six research documents, three W47 specifications, two operations
documents, four demonstration documents, one architecture document and one W47 design. This
is **inherited** debt — the obligation "the document registry stays reconciled" stands in
`ROADMAP.md` §9, and none of the waves that created those documents closed it. The wave
**named** it (the index's header box now carries the exact count and the list) instead of
guessing the status of 69 documents it did not write.

**The rule this round adds to the wave.** A wave that adds documents must also **put them
where the reader's tools look**: in the census, with a status from the vocabulary, and
reachable from the index. The report is the artefact that outlives the wave, and a report
that cannot be reached is a report that does not exist for the next reader — and a report
that overwrites another wave's report is worse than no report.

### 4l. What round 60 added: the other half of the boundary

The mandate's §12 asks the isolation gate for a list of explicit statements about the new
boundary, and one of them was **not there**: *"Chain A cannot reach Chain B's stores."*
The gate proved the other direction — that chain B's task receiver reaches no class of
chain A's epistemic store (G11) — and **a boundary that is proved from one side only is
half a boundary**.

**What the new rule measures (G12).** Two statements, both about chain A:

1. **The chain A task client reaches no class of chain B's store subsystem** — the closure of
   `io.matrix.bridge.TaskPublisher` is measured against the closure of chain B's own store
   classes (`io.matrix.api.bridge.RemoteTaskStore`, `io.matrix.brain.runtime.PersistentMind`).
2. **No class of chain A names one of chain B's databases** — the constant pool of every
   compiled class of matrix-core is searched for `tasks.sqlite`, `mind.sqlite` and
   `MATRIX_MIND_DIR`.

The second statement is the stronger one, and it is the one a closure check cannot make: a
class that opens the peer's file **by its path** never has to name the store class, so the
reachability proof alone would not see it. On the wave's own tree both statements hold —
**0** classes — and the gate now runs **32 checks, 0 failed**.

**And the rule can go red (C6).** The negative control replaces chain A's task publisher with
one that opens the peer's inbox — the SQLite URL of chain B's own inbox (`jdbc:sqlite:`
followed by the inbox's relative path, `data/mind/tasks.sqlite`) — and calls chain B's
store class. The gate goes red and names **both** halves: *"1 class(es) of chain A name one of
chain B's databases"* and *"5 class(es) of chain B's store are reachable from the chain A
task client"*. The control now runs **19 checks, 0 failed**.

**The gate caught the author's own mistake inside the round.** The first version of the
analysis reused the name `chain_b_store_own` for chain B's **mind** store — a name the
analyzer already used for the **epistemic** store's own classes. The report's
`chain_b.reachable_store_own` then carried the wrong set, and the gate went red on **G2**, a
rule that has nothing to do with the change. It was found because the gate was **run**, not
because it was read: a rule that has never gone red on a wrong tree is not evidence, and the
author's own tree was the wrong tree.

**The rule this round adds to the wave.** A gate that proves one direction of a boundary does
not prove the boundary (CD-044). The new rule is not the point — the point is that the
mandate's list of statements about the boundary was checked against the gate **item by
item**, and one item was missing.

### 4m. What round 61 implemented: the reliability rule, in code and enforced

The wave had deferred the method reliability with a reason
(`docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md` §4 is a rule, not a wish), and
the mandate's §9 asks for the rule to be *in the selection path*. This round wrote that rule
down in the code, in the shape the research prescribed — and the rule's own verdict is that
the estimate does **not** enter the score, because the system's evidence does not bear it.

**The rule, as implemented.** The estimator is the Wilson score interval at the 90 % level
(`RELIABILITY_CONFIDENCE = 0.90`, `RELIABILITY_Z = 1.6448536269514722`), its **lower** limit,
and its width against the gate `RELIABILITY_MAX_WIDTH = 0.20`. The point estimate is reported
as the **Jeffreys posterior mean** `(x + ½)/(n + 1)`, named as such, so that the prior is
visible instead of hidden in a formula. The three states are the research's:

| the state | when | the reliability term |
|---|---|---|
| `NO_EVIDENCE` | nothing was observed **for this stratum** | the declared accuracy stands |
| `NOT_YET_USABLE` | something was observed, but the interval is at least 0.20 wide | the declared accuracy stands |
| `ESTIMATED` | the interval is narrower than 0.20 | the 90 % **lower limit** replaces the declared accuracy |

The score therefore has two bases for the accuracy term, exactly as the cost term already had:
`score = ACCURACY_WEIGHT · A(m, s) − costWeight · costFraction(m)`, where `A` is the measured
lower limit when the state is `ESTIMATED` and the declared accuracy otherwise. **The state
never overrides the score**: a method measured at 0 of 30 still loses to an unmeasured method
that declares 0.9 — *known to be bad* is worse than *not known*.

**The verdict taxonomy the profile now carries.** The mandate's §9.1 asks that a technical
failure not be counted as an incorrect inference and an abstention not be counted as a
failure. The profile therefore records, per call, what the call *asserted*: `VALIDATED`,
`INCORRECT`, `PARTIAL`, `UNSUPPORTED` (the four kinds that **decide**), `ABSTAINED` (a
deliberate refusal), `UNVERIFIED` (it returned and nothing confirmed it), and the technical
kinds `TECHNICAL_FAILURE`, `TIMEOUT`, `REFUSED`, `CANCELLED`. Only the four deciding kinds
enter the estimate's denominator; the rest are counted and reported beside it.

**The stratum, and the refusal to pool.** The verdicts are keyed by
`(workspace_id, method_id, family, capability_version)` — the key the research names — and
the verdicts recorded *without* a stratum are **pooled**. A pooled rate answers "how often
does this method behave as recorded, over everything we ever ran it on", which is a different
question from the one the selection asks; the refusal to pool is therefore a **state**, not an
accident of the key: a method whose evidence is pooled is `NO_EVIDENCE` for a request, and the
decision says so.

**What the system's own evidence is.** The local call sites record `UNVERIFIED`: the call
returned, and **nothing in that path verifies the answer**. The system's verdicts are therefore
not decided trials, `n` is zero, and the state is `NO_EVIDENCE` — **the rule holds the term out
of the score for the reason the rule gives**, and the decision says which reason. The remote
path's verdicts (the dispositions the assessor produces) were **not yet fed into the profile**
when this section was written; **§4q** records the step that fed them in.

**The numbers the research published, reproduced by the implementation.** One decided trial:
the interval **[0.270, 1.000]**, width **0.730**, the state `NOT_YET_USABLE`, and the
Jeffreys mean 0.750 — "this is the unjustified confidence" the research warns against, and it
is kept out of the score. Twenty-seven of thirty: **[0.774, 0.959]**, width **0.185**, the
state `ESTIMATED` — the only way the term can enter the score. Both are asserted in
`MethodReliabilityTest`, against the numbers the research document itself publishes.

**The negative control.** The same test records the verdicts twice: once **pooled** and once
**in the stratum**. Pooled, the score keeps the declared accuracy and the decision says
`DECLARED` and names the pooling; in the stratum, the score carries the measured lower bound
and the decision says `MEASURED` and names the interval. The refusal to pool is therefore not
a promise: the score visibly moves when the stratum is there and visibly does not when it is
not.

**The determinism.** Every input is `(x, y)` from the durable profile, the stratum derived
from the request and the capability, and the named constants of the rule. No clock, no
randomness, no iterative solver, and no measurement inside the decision path — the cost, as
before, is measured after the call and only informs later decisions.

### 4n. What round 62 mapped: the crash boundaries of §6.4, one by one

The mandate's §6.4 names eight places where the process may die, and it says that "a crash
test must verify the database state and the final observable result, not only the returned
HTTP code". The wave had the tests — but not the **map**: nothing in the report said which
boundary each test proved, and a reader could not tell a covered boundary from an uncovered
one. This round wrote the map, and the map is honest about the one boundary that is only
*partly* recoverable.

| §6.4's boundary | what the system does when the process dies there | the proof |
|---|---|---|
| **before the request is persisted** | nothing was dispatched: the durable intent is written **before** the send, so a crash before it leaves no trace of a dispatch at all | `CognitiveCoordinatorTest` — the intent is durable before the send, and the negative control removes that write and the test goes red |
| **after the request is persisted, before the dispatch** | the intent holds the request, so the reconciliation pass sends it again **with the same identity** | `CognitiveCoordinatorRecoveryTest` — "the intent keeps the request, so it can be sent again" and "after a crash the same request is sent again, with the same identity" |
| **after the remote receiver accepted it** | the peer's row is in flight; after the restart the peer closes it **with a reason**, and a re-delivery is answered with that failure — not with "still running" | `TaskPeerRecoveryTest` — "a task that was in flight when the peer died is closed with a reason" and "a duplicate of a task the reconciliation closed is answered with the failure" |
| **during the cognitive computation** | the same as the boundary above: the peer's row is in flight and the brain's effect is not committed | the same two tests, plus `RemoteTaskStoreTest` — "a request that was accepted but never finished is visible after a restart" |
| **after the computation, before the memory effect** | the peer's inbox keeps the request's **digest**, not its payload — so an interrupted execution **cannot be finished**, the row is closed as a reasoned failure, and the work is **lost rather than resumed** | `TaskPeerRecoveryTest`; the honest list says the same thing in words: re-running it would risk a second effect for a task whose first effect may have landed |
| **after the memory effect, before the outcome is committed** | **the one boundary that is only partly recoverable.** The effect is in the brain's memory and the row's `effect_committed` flag is not yet set — the two facts are separable because the peer keeps an **effect ledger** — so the restarted peer closes the row as a reasoned failure and **does not repeat the effect**. What is lost is the *answer*, not the effect | `RemoteTaskStoreTest` — the unfinished row carries `effectCommitted`; `TaskPeerRecoveryTest` — the row is closed with a reason |
| **after the outcome is committed, before the response is delivered** | the row holds the outcome, so the re-delivery is answered from it (the idempotency) and the reconciliation leaves the completed row alone | `TaskPeerRecoveryTest` — "a completed task is untouched by the reconciliation"; the demonstration's replay answers with the first execution's disposition |
| **after chain A received the response, before its own result is committed** | the answer is on record and the verdict is not, so the dispatch is in the reconciliation queue; the restarted process finishes it with a verdict and the state `ADJUDICATED` | `CognitiveCoordinatorRecoveryTest` — "a crash between the peer's answer and chain A's own verdict is finished by the reconciliation pass", which asserts the **database state** before and after the restart |

**What the map says that the report did not say before.** Seven of the eight boundaries end
in a *decided* state: the work is either redone identically or closed with a reason. The
sixth ends in a state that is decided but **lossy**: the effect is applied and the answer is
gone. The wave had implemented that asymmetry on purpose — the peer's inbox keeps the
request's digest and not its payload, so a duplicate can never apply the effect twice, at
the price of not being able to resume an interrupted execution. **A map that said "all eight
are covered" would have been wrong**; the map says which one is lossy and why.

### 4o. What round 62 measured again: the baseline against the wave's final tree

The wave's performance baseline was measured in round 54. Since then the tree changed — the
reliability rule of §4m, the ledger, the gate's new rule group — and **a number published
against a tree that has changed is a number about a tree that no longer exists**. This round
measured the whole workload again, against the wave's final tree, with the benchmark's own
percentile rule (the nearest rank) and against a store of each run's own.

```bash
bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/w53-r62-seq.json
bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4 --report /tmp/w53-r62-conc.json
bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 8 --report /tmp/w53-r62-over.json
python3 scripts/w53-merge-performance.py /tmp/w53-r62-seq.json /tmp/w53-r62-conc.json \
        /tmp/w53-r62-over.json docs-v2/engineering/W53-PERFORMANCE-VERIFICATION.json
```

**What reproduced, and what did not.**

| the quantity | the published (round 54) | the fresh (round 62) | the difference |
|---|---|---|---|
| the sequential p50 | 14.29 ms | **14.95 ms** | +4.7 % — inside the measured noise (9.9 %) |
| the sequential p95 | 21.41 ms | 18.44 ms | −13.9 % |
| the sequential throughput | 42.99 tasks/s | 42.21 tasks/s | −1.8 % |
| the replay's p50 | 8.07 ms | 8.81 ms | +9.2 % — inside the noise |
| the concurrent steady p50 | 27.03 ms | **27.05 ms** | +0.1 % — inside the measured noise (2.7 %) |
| the concurrent steady throughput | 143.95 tasks/s | 145.75 tasks/s | +1.3 % |
| the cold start's p50 | 355.35 ms | 296.48 ms | −16.6 % |
| the tasks refused above the cap | **12 of 24** | **12 of 24** | the same, and the same reason |
| the refusal latency's p50 | 5.13 ms | 6.13 ms | +19 % |
| the CPU per task | 64.7 ms | 60.0 ms | −7.3 % |

**The verdict, and it has two halves.** The **headline** numbers reproduced: the sequential
p50 inside the noise band the wave measured, the concurrent steady p50 to a tenth of a
percent, the steady throughput to 1.3 %, and the bound above the concurrency cap refusing
**the same twelve of twenty-four** for the same reason. The **tails** did not: the sequential
p95, the cold start and the refusal latency each moved by 14–19 %, and **the wave never
measured the noise of those statistics** — its noise was measured on the p50, and on the
median of the concurrent path's steady state (§4g). A tail whose variance was never
quantified cannot be called reproduced or not reproduced; it is **named** here, and no
explanation is offered for it, because the measurement does not support one.

**One thing the re-measurement confirms about the published document.** The benchmark's
percentile rule is the nearest rank, so over thirty samples the p99 **is** the largest sample
— and it is in both runs (272.52 ms then, 267.54 ms now). The published report already refuses
to claim a p99 from a sample of that size (`W53-PERFORMANCE.md` §5.1), and the JSON says the
same in its own list of what it does not claim. **The re-measurement confirms that the
disclaimer is true and not a formality**: the number is there, and it is called what it is.

**What this round did not do.** It did not replace the published baseline with the fresh
numbers: the baseline is a measurement with its own provenance (its commit, its tree, its
date), and a second document with the same numbers would be a second thing to drift. The
verification is a separate, dated artefact —
`docs-v2/engineering/W53-PERFORMANCE-VERIFICATION.json`, the same schema
`matrix.w53.distributed-performance/v3` — and the baseline stays where it was.

### 4p. What round 63 reproduced: the CI state, on this machine, today

The mandate's §18 asks for the **actual** CI state at the wave's base — not a summary of a
previous wave's summary — and for the inherited findings to be reported **separately** from
the wave's own regressions. The wave's report had never stated this. This round reproduced
the checks the CI runs that can be reproduced here, and named the ones that cannot.

| the CI check | the tool and version | the result | whose finding |
|---|---|---|---|
| **ShellCheck** | ShellCheck 0.10.0, the CI's own command `find . -name "*.sh" -exec shellcheck {} +` | **156 files in the whole tree carry findings** (182 findings in total) — the same file count W52 reported, and **every one of them in a script this wave never touched** | **inherited** |
| …the wave's own scripts under the same linter | the same | **0 findings** — and finding them took one fix: `scripts/w53-distributed-benchmark.sh` carried `SC2155` (a `readonly` that masked the command substitution's status). The declaration and the assignment are now separate, and the fix is a real improvement: a failing `date` now stops the run instead of leaving an empty stamp that would collide with the previous run's request ids | **the wave's own, now fixed** — the tree's count is back to the inherited 156, so the wave adds **zero** |
| **the formal gate** | the pinned TLA+ tool from the repository (`tools/tla2tools.jar`) | **15 specifications model-checked, 15 passed, 0 failed** (`scripts/formal-gate.sh`) | green |
| **the security boundary** | the script the CI runs | **every assertion held**, and the live knowledge and mind databases were untouched (`scripts/security-boundary-check.sh`) | green |
| **TruffleHog** | the CI action scans the commits a push carries | **reproduced locally on 2026-11-13** with the same image the CI uses: `docker run --rm -v "$PWD":/repo:ro ghcr.io/trufflesecurity/trufflehog:latest git file:///repo --results=verified,unverified,unknown --json` — **44 findings in the whole tree, 0 verified**, and the CI run of the push (`43366273512`, the `develop` push at `9f9948e7`) carries **exactly one** finding inside its own 16-commit range: `docs-v2/engineering/W53-STATUS.md:572`, the verbatim SQLite URL of chain B's inbox — the driver prefix `jdbc:sqlite:` followed by the relative path `data/mind/tasks.sqlite` — a **relative local path with no credentials**, so a false positive. The job is red on the action's **exit code 183**, its convention for "unverified findings remain", and not on a secret | **inherited** — the job was already red on `main` for the same class of reason (run `42618343390`) |
| **the native build** | — | inherited (the heap exhaustion) — the wave did not touch it, and §10.4 keeps the Java path selected | **inherited** |
| **SpotBugs** | — | the limitation is documented in the workflow itself, and the workflow is frozen and was not touched | **inherited** |
| **the Java build and test** | JDK 25 | the CI runs the **full** `./gradlew test`. The wave's authorised scope was the targeted suite (1646 tests, 250 classes, 0 failures). **On 2026-11-13 the full module was run as well** — `matrix-core`: the **last** run of the tree is **1300 classes, 9522 tests, 0 failures, 0 errors, 23 skipped**, in 24 minutes 22 seconds; the 23 skipped tests are the native-image and the GPU-bound classes, which are **BLOCKED** on this host and are never counted as passing. An **earlier** run of the same tree (9521 tests, before the wave's own last method) ended with **1 failure** in 26 minutes 51 seconds. **That one failure was a wall-clock assertion, not a defect of the wave**: `Exp324EndToEndForwardPassTest.endToEndForwardPassLatency` asserts that the median of the boolean-chain forward pass is under 2 ms, and this machine now measures a median of 1992–2350 µs (four isolated runs; the mean 1992–2127 µs, the max 2678–3565 µs) — the bound sits exactly where the machine stands. **It is not caused by the wave's changes**: the same test fails with the wave's production change and its test removed from the tree; and the **first** full run of the same tree passed (24 min 55 s, BUILD SUCCESSFUL), while the failing run took 12 % longer. **The bound was not raised** — raising it is a decision about the performance envelope the project claims, and it is the owner's. The **last** full run of the same tree **passed**, which confirms the attribution: the assertion is a statement about this machine's load. The W53 scope after the release hardening: **255 classes and 1668 tests** across `matrix-core` and the gateway, 0 failures | green within the scope; the last full-module run is green as well, and the one earlier full-module failure is a wall-clock assertion |
| **the quality gates** | — | the clean-code gate: **271 candidates against the accepted baseline of 271**, 0 other violations | green |

**What the wave's contribution to the CI state is: zero new findings and one fixed.** The
workflow files under `.github/workflows/**` are frozen and were not edited. The number that
the CI will count after the wave is pushed is the same number it counts now.

### 4q. What round 64 added: the peer's answers reach the profile of methods

Round 61 implemented the reliability rule and named the next step: *the remote path's
verdicts are not yet fed into the profile*. This round took that step, and the step turned
out to be worth taking for a reason nobody had written down yet — **the remote path is the
only place in the system where a verifier exists.**

**The problem the step solves.** A reliability estimate is computed over *decided trials*:
a call whose answer something verified. The local call sites record `UNVERIFIED` because
nothing in those paths verifies the answer, so the local profile has `n = 0` and the state
`NO_EVIDENCE` — the rule works, and it holds the term out of the score for exactly the
reason the rule gives. The remote path is different: chain A **assesses** the peer's report
against chain A's own evidence, and that assessment ends in a disposition. The dispositions
are verdicts, and they are the system's first decided trials.

**What was built.**

| the piece | what it is |
|---|---|
| `MethodOutcome.QUALIFIED` | a new kind: the report was **supported but not independent** — chain A's own evidence backs it, but only because it rests on what chain A supplied. Folding it into `VALIDATED` would count the peer's echo of chain A's material as the peer's success; folding it into `UNSUPPORTED` would deny a support that exists. It is a decided trial and not a validated one, so it is in the denominator and not in the numerator |
| `RemoteVerdicts` | the mapping from the peer's status and chain A's disposition to that kind, as a named table of meanings with its own tests |
| `CognitiveCoordinator.VerdictSink` | the port: the coordinator names a verdict, and the sink decides where it is kept |
| the coordinator's two adjudication sites | the dispatch and the replay now record the verdict, in the stratum of the **capability**, the **task family** and the **capability version** that ran |
| `CognitiveStore` | the durable verdict row gained the `qualified` counter, with the migration that an already existing database needs |
| `CognitiveService.ledger()` | one ledger for the whole process — the same one the selector reads, and the same one a peer's verdict is recorded into |

**What the resulting quantity is, said plainly.** A verdict here is *chain A's reading of the
peer's answer*, so the rate it produces answers: **"how often does a report from this
capability become a validated proposition in chain A's own epistemic model?"** That is a
quantity about the peer **and** about chain A's evidence — a stricter chain A produces more
rejections from the same peer — and it is **not** a measure of the peer's reasoning alone.
The research that forced the taxonomy says the same about every rate it warns against: a
rate is confounded by the strictness of the verifier that produced it
(`W53-RESEARCH-D-RELIABILITY-CALIBRATION.md` §3.8). The estimate therefore names the stratum
it was measured in, and the decision that reads it names the basis it is used on.

**What the demonstration produced, said plainly.** The two-process demonstration runs the
real path, and its chain A ends the task with the disposition `UNRESOLVED` — the report is
admitted, but **nothing in chain A's own knowledge settles the proposition**. The verdict
that reaches the profile is therefore `UNVERIFIED`: the call is counted, it is **not** a
decided trial, and the estimate stays at `NO_EVIDENCE`. That is the honest state of the
system, not a defect in the wiring: the machinery ran, the row is on record in its stratum,
and the estimate is not earned yet because the evidence is not there. A demonstration whose
chain A held the supporting evidence would produce a `VALIDATED` trial — and the machinery
would then have its first decided trial to estimate.

**What is still missing, and it is not the wiring.** The local methods still record

**What round 66 changed here.** The review of §4s corrected two things in this section's wiring: the sink now records the **verdict alone** — the peer's price was measured by the peer's own clock and must not share the local methods' cost column — and the peer's `REFUSED` is a **refusal**, not an abstention. The mapping's table names the confounder: the peer's protocol has one status for a policy refusal and for the brain's own decline, so the verdict cannot tell them apart.

`UNVERIFIED`, and no held-out set with ground truth exists, so the local selector still
reads no measured reliability — the honest list's item 5 says so. What changed is that the
machinery now has **real data** to move on, in one stratum, from one verifier.

### 4t. What round 66 measured again: the same workload after the fixes

The peer's inbox now serializes its writes (§4s), so the numbers of the distributed path had
to be taken again — **a number measured against a tree that has changed is a number about a
tree that no longer exists** (§4o). The measurement was taken on a machine that was otherwise
idle, with the benchmark's own nearest-rank rule and a store of each run's own, and the
verification artefact (`docs-v2/engineering/W53-PERFORMANCE-VERIFICATION.json`) now holds this
run.

| the quantity | the published (round 54) | the fresh (round 62) | the fresh (round 66) | the difference |
|---|---|---|---|---|
| the sequential p50 | 14.29 ms | 14.95 ms | **14.44 ms** | +1.1 % over the published — inside the measured noise (9.9 %) |
| the sequential p95 | 21.41 ms | 18.44 ms | 23.89 ms | +11.6 % — the tails moved, and the measurement does not say why |
| the sequential throughput | 42.99 tasks/s | 42.21 tasks/s | 44.59 tasks/s | +3.7 % — inside the noise |
| the replay's p50 | 8.07 ms | 8.81 ms | 8.88 ms | +10.0 % — at the edge of the noise |
| the concurrent steady p50 | 27.03 ms | 27.05 ms | **26.03 ms** | −3.7 % — inside the noise |
| the cold start's p50 | 355.35 ms | 296.48 ms | 332.66 ms | −6.4 % — inside the noise |
| the refused tasks above the cap | 12 of 24 | 12 of 24 | **12 of 24** | unchanged |
| the refusal's p50 | 5.13 ms | 6.13 ms | 5.33 ms | +3.9 % — inside the noise |
| the CPU per task | 64.7 ms | 60.0 ms | 62.3 ms | −3.7 % — inside the noise |
| the crossing's p50 | 5.84 ms | — | 6.35 ms | +8.7 % — the peer's writes are serialized now, and the rest is not explained |

**What this says, and what it does not.** The headline statistics are inside the measured
noise of the published baseline, the refusal rate above the concurrency cap is unchanged, and
the p99 is still the largest sample of a 30-sample run — the report refuses to call it a p99.
The tails moved, and this round does not claim to know why: the sequential p95 is 11.6 %
above the published figure, and the measurement of the tail's own noise was never made. The
crossing grew by 0.5 ms at the median; the peer's own durable write grew by 0.02 ms of that,
which is the cost of the lock the review asked for, and the rest of the difference is not
attributed — **a difference that has no explanation is named, not explained**. The first run
of this round was taken while the wave's own other work was running on the same machine: its
sequential p50 was 16.05 ms, and it was discarded and taken again on an idle machine rather
than reported, because a number taken under a load the report does not name is not a
measurement of the distributed path.

### 4s. What round 66 found: two independent reviews of the wave's own work

The wave's own report is written by the same process that wrote the code, so it can only be
as good as that process's reading of itself. This round had **two independent readers**: one
that read the uncommitted production code against the mandate's requirements and the
repository's conventions, and one that read the wave's documents against the artefacts that
prove them — the retained JUnit XML, the benchmark's JSON, the demonstration's report, the
census. Both read-only; neither changed a file.

**What the code review found — and what it changed.**

| the finding | the defect | what the wave did |
|---|---|---|
| **the timer killed a live dispatch** | `deliver()` dialled the peer once per attempt of the retry budget even when the peer answered `IN_PROGRESS`, and the counter of the attempts was spent by those answers; the reconciliation pass then read the spent budget as a lost task and closed the dispatch as `FAILED` — so a task the peer was still working on lost its answer | the attempt now counts **the deliveries that produced no answer**: an answer — including "still running" — spends nothing, the peer is asked once, and the pass asks again until the peer answers with a result or with its own timeout |
| **the store's new writes were not serialized** | `CognitiveStore.recordVerdict` and `verdicts` were not `synchronized`, though the class holds one JDBC connection and every other method is | both are now synchronized, and the W52 measurement pair with them |
| **the completion counters conflated the kinds** | a timeout, a refusal and a cancellation were all counted as *successes*, which is exactly the confusion the taxonomy exists to prevent | the counters count **completions** (`MethodOutcome.completed()`), and the decision's wording says "completed" and "not completed" instead of "success" and "failure" |
| **an observed but undecided stratum claimed no evidence** | a stratum with calls but no decided trial was reported `NO_EVIDENCE`, whose own definition is "nothing was observed" | it is `NOT_YET_USABLE` now, and the state decides between candidates that scored the same |
| **the peer's price sat in the local cost table** | the sink's Javadoc said the peer's cost never entered the comparison with the local methods, and the code put it there — under the peer's own capability id, but in the same table, measured by another process's clock | the sink records the **verdict alone**; the peer's price stays on the dispatch's own record, and the port carries no cost |
| **the refusal was counted as an abstention** | the peer's `REFUSED` was recorded as `ABSTAINED` and its `UNSUPPORTED` as `REFUSED` — the two are the wrong way round | both are refusals now, and the table names the confounder: the peer's protocol has **one** status for a policy refusal and for the brain's own decline, so the verdict cannot tell them apart |
| **the disposition column had two domains** | a dispatch closed as failed wrote `FAILED` into the column that holds chain A's verdict about a peer's answer | the column keeps its one domain and stays empty; the reason of the closure goes into the reason column, and the unfinished query was corrected to match |
| **the configuration could put two writers on one database** | the path of the dispatch store could be pointed at the knowledge file, and the writer lock would not catch it — both holders are in one process | the resource refuses that configuration at the moment the store is opened |
| **the peer's inbox was written concurrently on one connection** | the peer's store holds one JDBC connection and its methods were not synchronized, and the wave had added one more write to it | every method of the peer's store synchronizes on the store, exactly as chain A's store does; the stages of a **failed** delivery are recorded too, not only of a successful one |
| **the dead and the stale** | a factory with no caller, an accessor with no caller, a Javadoc that no longer described the comparator, an orphaned comment, a timer that could be started twice | all corrected; the decision now names the **interval** and the posterior mean, so the estimate's own numbers reach the trace |

**What the evidence audit found.** The audit did not find a false number in the performance
report — every figure of §4o reproduces from the artefacts with the benchmark's own
percentile rule — but it found the wave's documents describing a tree that no longer exists:
stale test counts (the coordinator's class has 11 tests, not 9), a demonstration schema
still named `v5` where the artefact is `v6`, **withdrawn performance numbers presented as
current** in the architecture map (29.69 ms and 91.37 ms — the numbers the wave withdrew in
round 41, which survive only in the performance report's own history), a wrong count of the
bridge's payload components (9, not 8), of the dispatch's states (10, not 9) and of the
bridge's rights (3, not 2), a claim that the selector does not read the reliability estimate
(it does), a claim that the reconciliation has no timer (it has one), a claim that the peer
takes no cancellation signal (it has taken one since round 19), the CI row's scope numbers
from an earlier round, and the capability registry naming the kernel as a production caller
of the distributed capability — the kernel's `RemoteExecutor` port is **not** supplied in the
running application, and the registry now says so in the entry's own `limitations`.

**The correction the audit forced, which is the wave's own honest list.** The remote path's
verdicts now reach the profile (§4q), but the capability they are filed under —
`brain.cognitive-inference` — **is not a candidate of the local selector**, and the local
registry holds no remote capability at all. The estimate the remote path accumulates is
therefore real, durable and **read by no decision**: the machinery works, and nothing consumes
it yet. That is stated in the honest list, in the audit and in the capability registry's
entry, and it is the same gap the §8 residual names: the distributed path is not inside the
cognitive cycle's own selection.

**What did not change.** The baseline holds: **18/29** on the epistemic evaluation, the
quality gate at **271 candidates against the baseline of 271**, the isolation gate at **32
checks and 0 failures**, its negative control at **19 checks and 0 failures**, and the
demonstration's **16 stages, all `OK`**. The wave's scope is **1646 tests in 250 classes, 0
failures, 0 errors, 0 skipped** — counted from the retained XML, not from a log.

### 4r. What round 66 checked: the wave's own documents, swept

The wave's documents grew by seven sections and five production classes since the last
sweep, and a document that is not checked is a document that drifts. This round ran the
repository's own document-integrity checkers over the tree and read the result, rather than
assuming the wave's documents were still sound.

**The checkers, all four, over the whole tree.**

| the checker | the result |
|---|---|
| `scripts/docs-link-check.py --root . --strict` | **strict failures: 0** (2794 defects in the non-strict set — the archive, the waves area and the historical documents) |
| `scripts/validate-project-registry.py --strict` | **0 defects** |
| `scripts/validate-project-state.py --strict` | **0 defects** |
| `scripts/validate-test-catalogue.py --strict` | **0 defects** |

**The wave's own documents.** Every one of the wave's documents carries a **status line in
the vocabulary of the policy** — `ACTIVE` for the report, the audit, the map, the
performance report, the comparison and the design; `CANONICAL` for the ADR; `RESEARCH` for
the four research tracks ✓ — and no document reads its status from its title. The eight
defects that the checker still reports in the wave's vicinity are **inherited, not the
wave's**: `docs-v2/INDEX.md` cites three documents that are `HISTORICAL` and links to the
waves area, `docs-v2/research/HYPOTHESES-NEW.md` cites one `HISTORICAL` plan, the
decision register's own header carries a backticked token the checker reads as a status, and
two registry documents that are `.md` beside the `.yml` ones are not reachable from the
index. **They are reported here and not fixed**: a checker that fixes is a second source of
truth, and the wave's mandate asks for inherited findings to be *reported*, not absorbed.

**What the sweep did find in the wave's own tree, and it was fixed.** Two statements had
gone stale while the wave ran: `docs-v2/governance/PROJECT-STATE.md` said that *rounds 41
to 55* are uncommitted (it is 41 to 65), and the wave's honest list said the same of
*rounds 41 and 43*. Both now name the rounds that are actually in the working tree, and the
audit, the architecture map and the ADR carry the new verdict path (`RemoteVerdicts`,
`LedgerVerdictSink`, the durable `cognitive_method_verdict` row) with the tests that prove
each step.

**The counts were re-derived from the artefacts, not copied.** The wave's scope is **1646
tests in 250 classes** — counted from the retained JUnit XML of matrix-core and the
gateway together, 0 failures, 0 errors, 0 skipped — and the catalogue, the report's
evidence table and the closeout all carry that number. The performance figures of §4o were
re-derived from the verification artefact with the benchmark's own nearest-rank rule:
14.954 ms at p50, 18.439 at p95, 42.21 tasks/s, the steady state 27.049 ms and 145.75
tasks/s, 12 of 24 tasks refused above the cap, and 0.06 s of CPU per task — every figure in
§4o's table is the artefact's own number.

*(The count above is the wave's own count, measured on 2026-11-12. The release hardening of
2026-11-13 added seventeen methods in matrix-core and five in the gateway, and the scope is
now **1668 tests in 255 classes** — §4u.)*

### 4u. What the release hardening of 2026-11-13 added: six regression tests, one real defect

The owner's directive of 2026-11-13 asked for the wave's fixes to be **held by tests that
catch them**, not merely accompanied by them, and for the failing security scan to be
**localized** rather than silenced. This section records what that phase produced. **The work is committed** — the guard's
fix (`04b7c5de`), the five tests that hold the wave's own fixes (`027e8f76`) and the
research with its evidence (`5e26748d`) — and the merge of pull request #50 remains the
owner's release decision.

**The six fixes and the test that holds each of them.** Each test was proven to catch its
defect by **mutating the production code and restoring it byte-identically** (`diff`
against the copy taken before the mutation) — the mutation is the evidence that the test
is not a bystander:

| the fix | the test | what the mutation was | what the mutation did |
|---|---|---|---|
| the cognitive store's writes are serialized | `CognitiveStoreConcurrencyTest` (the third of its tests) | `synchronized` removed from `nextSequence`, `recordMeasurement` and `recordVerdict` | **the test failed** at the write of a concurrent writer: `IllegalStateException — failed to allocate the next value of the sequence event-position` |
| an observed but undecided stratum is `NOT_YET_USABLE`, not `NO_EVIDENCE` | `MethodReliabilityStateTest` (7 tests) | the state forced to `NO_EVIDENCE` whenever the stratum holds no decision | **three of the seven failed**: `expected: NOT_YET_USABLE but was: NO_EVIDENCE` |
| a disposition is not substituted by its reason | `DispatchDispositionSeparationTest` (4 tests) | the closure wrote its reason into the `disposition` column | **two of the four failed**: `Expecting null or empty but was: "the peer never answered within the budget"` |
| two writers on one database are refused | `TwoWritersOnOneDatabaseTest` (5 tests) | the guard compared the **spelled** paths again (the pre-fix comparison) | **the two symlink cases failed**: `Expected java.lang.IllegalStateException to be thrown, but nothing was thrown` |
| the peer's inbox is serialized around its one JDBC connection | `PeerInboxSerializationTest` (3 tests) | `synchronized` removed from `admit`, `markCompleted`, `recordStages`, `find`, `unfinished` and `size` | **the test failed** in all three runs: `org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_PRIMARYKEY] A PRIMARY KEY constraint failed` — two deliveries of one request id both passed the check and both wrote |
| a refused delivery keeps its stage timings | `RefusedDeliveryStageTimingsTest` (2 tests) | the measurement written only where the delivery ran to the end (the pre-fix shape) | **the test failed**: `a delivery that was refused is measured too: the stages it did reach are the stages that happened` |

**The six mutations were run again on 2026-11-14, on the tree that is to be committed**, and
each of them still turns its test red, for the same reason and not for another one:

| the mutation | the test that went red |
|---|---|
| `synchronized` removed from the cognitive store's writes | `CognitiveStoreConcurrencyTest > concurrent writers on one store lose no row and no sequence number` |
| the state forced to `NO_EVIDENCE` whenever the stratum holds no decision | `MethodReliabilityStateTest` — **three** of its seven tests |
| the closure writes its reason into the `disposition` column | `DispatchDispositionSeparationTest` — **two** of its four tests |
| the guard compares the spelled paths again | `TwoWritersOnOneDatabaseTest` — the **two symlink cases** |
| `synchronized` removed from the peer's inbox | `PeerInboxSerializationTest > concurrent deliveries of one request id are admitted once` |
| the measurement written only where the delivery ran to the end | `RefusedDeliveryStageTimingsTest > a refused delivery keeps the stages it reached in its own row` |

Two details of that re-run are worth keeping. First, **the last mutation must reproduce the
shape of the old implementation, not a syntactic move**: a refusal that returns normally is
still on the happy path, so placing the write at the end of the `try` block leaves the test
green; the pre-fix code wrote the measurement only where the delivery ran to the end — the
outcome write — and the mutation that reproduces *that* turns the test red. Second, after
every mutation the production file was restored and compared with `git show HEAD:<path>`:
**byte-identical in all six cases**.

**One of the six found a real gap, and the gap is in serving code.** The two-writer guard
compared `knowledge.toAbsolutePath().normalize()` with `cognitive.toAbsolutePath().normalize()`.
`normalize()` collapses `.` and `..` but does **not** resolve a symbolic link, so **a
symlinked alias of the knowledge database was not caught** — the very condition the guard
exists to prevent. The guard now resolves the path (the directory part through
`toRealPath()`, the last element through `readSymbolicLink`, bounded at 16 hops) before it
compares, and it refuses the alias with the same reason it refuses the identical path. This
is the **same defect class as the open question Q-W46-04**, whose guard — the knowledge
store's own `WriterLock` — is still unresolved; the fix here is in the dispatch store's
guard and does not close that question.

**The security finding was localized, not silenced.** The CI's `Security Scan` job of the
`develop` push (run `43366273512`, head `9f9948e7`) is red. The action scans the **commits
the push carried** — sixteen of them — and exactly **one** of the tree's forty-four local
findings lies in that range: `docs-v2/engineering/W53-STATUS.md:572`, the verbatim SQLite URL
of chain B's inbox — the driver prefix `jdbc:sqlite:` followed by the relative path
`data/mind/tasks.sqlite`. It is a **relative local path with no credentials**, so the finding
is a false positive; the **0 verified secrets** are the fact that matters. The scanner was
**not** disabled and **no wide exception** was added; the finding itself is answered by the
narrowest possible change — the documentation line that quoted the URL verbatim now names
the driver prefix and the path separately, so the file carries the same fact without the
literal. **The fix is committed** (`5e26748d` carries the documents that no longer quote the
literal); the scanner's verdict on the pushed tree is recorded in the wave's release
section.

### 4v. What the reconciliation of `main` into `develop` was rehearsed as, after this wave's own work landed

The owner's procedure for the reconciliation has five steps: the list of the conflicting
files, what of `main` is already in `develop`, a resolution that keeps both behaviours, the
affected serving paths, stores and protocols, and the targeted tests. The rehearsal of
2026-11-13 answers each of them, and it was run **in a disposable clone**, on the tree
that includes this wave's own working state — because the order of operations matters: the
merge must be applied **after** this wave's changes are committed onto `develop`, not
before.

| the step | what the rehearsal found |
|---|---|
| the conflicting files | **17**, and they are the same seventeen as the first rehearsal: the four documents of this wave's own correction (`W53-STATUS.md`, `DECISION-REGISTER.md`, `PROJECT-STATE.md`, `ROADMAP.md`), the gateway's `MinimalHttpServer`, five classes of chain A's cognitive core (`CognitiveService`, `CognitiveStore`, `AlgorithmSelector`, `CostLedger`, `MethodMeasurementStore`), the three bridge contract classes, two bridge tests, and the two chain-isolation scripts |
| what of `main` is already in `develop` | **everything**: `main` is W52 + the reusable procedure (the squash of pull request #49), and `develop` carries the same work as its own sequence of commits |
| the resolution | **every conflict resolved to `develop`'s side**, and the merged tree is `1ddcc244…` — **byte for byte the tree of this wave's working state**: the merge changes **no file**, and therefore no serving path, no store and no protocol |
| the targeted tests | on the merged tree: the chain A cognitive scope **64 classes / 489 tests, 0 failures**, and the gateway **54 classes / 284 tests, 0 failures** |
| the mergeability of pull request #50 | the merge commit's parents are this wave's commit and `7709dc9e` (`main`), so **`main` is contained in `develop`'s history** ⇒ merging `develop` into `main` is a **fast-forward**: the pull request becomes **technically mergeable** with no conflicts and no merge commit of its own. Until the merge is applied and pushed, the pull request stays `CONFLICTING` on GitHub — that is the state of the repository, not a property of the merge |

**The order of operations, and one trap.** The merge must be applied **after** this wave's
changes are committed onto `develop`: the four corrected documents are themselves among the
seventeen conflicting files, so a merge prepared against the earlier tree is stale the
moment this wave's commit lands. And the harness's own file
(`.dsh-project-memory/admission-shadow.jsonl`) is **tracked in the repository** — `main` and
`develop` hold the same blob for it — while the harness writes to it at runtime; a commit
that stages its live version produces a **false eighteenth conflict** on a file that is not
the project's. The rehearsal saw exactly that, and the count returned to seventeen once the
file was restored to its tracked blob.

**Nothing was applied.** The rehearsal lives in a disposable clone under `/tmp`, the merge
commit of the first rehearsal lives in a **detached** worktree (`f55a3a47`, its tree
identical to `develop`'s), and **no branch moved, nothing was pushed, and pull request #50
was not merged**. The real merge touches serving code and is the owner's decision.

### 4w. What the release of 2026-11-14 did: the wave is in `main`, and the two branches hold one project

The W53 release is done. This section is the record of it — the commits, the merge, the
squash, the synchronization, the checks and the two reds that are inherited rather than
caused.

**The commits on `develop`, in order.**

| the commit | what it carries |
|---|---|
| `04b7c5de` | the dispatch store's two-writer guard resolves a symbolic link, and the test that holds it |
| `027e8f76` | the five regression tests that hold the wave's own fixes |
| `5e26748d` | REP-SUBSTRATE: the research, the ADR, the two JMH harnesses, the bench artefacts and the four research test classes |
| `9ec6723b` | the registers, the wave's report and the twelve corrected statements |
| `bfbec57b` | the merge of `main` into `develop` — the tree does not change |
| `bb46fc41` | the demonstration's null-prediction guard (a flaky CI failure that was **not** the wave's) and the next cycle's proposal |
| `e41cd3c1` | the post-squash synchronization of `main` back into `develop` |
| `d71b3d73` | the release record — the identifiers, the checks and the inherited reds |
| `0bd5b574` | the synchronization of the release record's own squash back into `develop` |

**The reconciliation.** `main` carried W52 and the reusable procedure as the squash
`7709dc9e` of pull request #49, and `develop` carried the same work as its own sequence of
commits — which is why the two histories diverged and why pull request #50 was marked as
conflicting although no content differed. The merge `bfbec57b` resolved the **seventeen**
conflicting files to `develop`'s side, and the merged tree was **byte for byte the tree
`develop` had before the merge** (`f182fa6f`): no serving path, no store, no protocol and
no document changed. The targeted tests on the merged tree were green (the chain A
cognitive scope 489 tests, the gateway 284, the brain runtime 928).

**The release.** Pull request #50 was merged as a **squash** — the repository's established
policy — as `bcdd1a0a` on 2026-11-14, and the release record itself followed as pull
request #51, squash `c4cee8ab`. The two branches now hold **the same tree**
(`git rev-parse origin/main^{tree}` and `git rev-parse origin/develop^{tree}` are equal):
`main` is contained in `develop`'s history, `develop` is 442 commits ahead with nothing
behind it, and `main..develop` holds the whole of W53.

**The checks on the released branches.**

| the check | the release commit `bb46fc41` (push) | `main`'s squash `bcdd1a0a` |
|---|---|---|
| Build & Test (Java) | **success** (CI run `43573097294`) | **success** (CI run `43574788457`) |
| Quality gates | success | success |
| Web UI Tests | success | success |
| Container Images (build + smoke) | **success** | **success** |
| **Security Scan (TruffleHog)** | **success** — 0 verified secrets | **success** — 0 verified secrets |
| branch validation, docs validation, TLA+ model check, Pages | success | success |
| ShellCheck | **failure — inherited** | **failure — inherited** |
| GraalVM Native Build | **failure — inherited** | **failure — inherited** |
| Docker Build & Push | **failure — inherited** | **failure — inherited** |

**The three inherited reds, and what each one is.** They are not the wave's, and they are
not new: the ShellCheck job has been red since **2026-11-09** (the repository's own
scripts — `snapshot_state.sh`, `mc-direct.sh`, `local-alpha-restart.sh`, the chain-isolation
check — with 182 findings, of which the wave's own scripts have none); the GraalVM native
build has been red since **2026-11-08** with *«The Native Image build process ran out of
memory»* on the runner; and the Docker push cannot log in at all, because **the repository
has no secrets** (`gh api repos/AlexanderNarbaev/agi/actions/secrets` → `total_count: 0`) and
the workflow asks for `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`. The last two were **hidden
before**: the security scan was red, so the jobs behind it were skipped, and the scan is
green now, so they run and show what they always showed.

**The flaky failure that was not the wave's.** The release commit's first CI run was red on
`W51DemonstrationTest > the report carries every section of the trace` — a null pointer
exception inside the W51 demonstration, while the parallel pull-request run of the same
commit passed. The demonstration is not deterministic by design (the record identifiers are
random UUIDs), so a cycle occasionally ends without a prediction, and the two acceptance
points that quote the prediction did not guard it — while the cycle section twenty lines
above already did. The guard is now in place: **25 consecutive runs pass**, and the
demonstration's acceptance table after the fix is identical to the committed
`docs-v2/engineering/W51-DEMONSTRATION.json` — fifteen of fifteen points demonstrated.

**What the security scan still reports, and why the job is green.** The pull-request event
scans the whole branch and finds **9 unverified results and 0 verified**, all attached to
**historical** commits: the W51 infrastructure plugin's placeholder URLs, the placeholder
URLs in the owner-protected `.veai/skills` files, and the wave's own historical commit — the
one whose current form no longer contains the literal. A push that carries only the new
commits finds nothing, and the job is green on both released branches. **No scanner was
disabled and no exception was added**: the wave's own finding was answered by the
narrowest possible change, and what remains is the history of the literals.

**How the pull requests were merged.** The repository's ruleset ("Protected main") requires
one approving review from a code owner, and the repository has **one** collaborator — the
owner — who is also the author of both pull requests; GitHub does not allow an author to
approve their own pull request, so the requirement cannot be met by anybody but the
owner's own merge. The ruleset therefore carries the administrator in its bypass list, and
the two merges were made through that path (`gh pr merge --squash --admin`). **No check was
disabled, no assertion was weakened and no approval was faked** — and the owner may approve
or merge by hand instead, which would leave the same trees.

**The open owner decisions are untouched.** The Exp324 threshold stands at 2 ms. The
replacement of the trainer's fold (ADR §4.4, options A/B/C) stays **OPEN**, and the next
wave's proposal says so in its own first paragraph. The peer's durable contract — whether
the payload may be stored at rest — stays **OPEN**.

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
   stores, and a task arranged outside an episode names no episode at all. **And what
   remains of the production wiring: the port is not supplied in the running
   application, and it could not be supplied alone.** The kernel delegates only when
   the capability the **local** selector chose is one another process serves, and the
   local registry holds no remote capability — so a supplied port would never fire,
   and §17 forbids an abstraction with no runtime use. The running system's
   distributed path therefore goes through the application's entry point, and the
   dispatched task belongs to no episode. Closing this honestly needs the *selector*
   to be able to choose a remote implementation (a remote capability registered as a
   candidate, with its cost measured through the delegation), which is a larger
   change than this wave makes; the gap is named here instead of being papered over
   with a wiring that would never fire.
2. ~~The reconciliation pass is not automatic.~~ **Done in round 12.** The request now
   travels with the durable intent (`cognitive_task_dispatch.request_json`), so after a
   crash the *same* request — the same request id, the same payload hash — can be sent
   again; `DispatchReconciler` walks the unfinished dispatches and sends each of them
   again, and chain A's entry point runs that pass when the process starts. The peer's
   durable inbox recognises the request id, so the logical effect still happens once.
   What remains: the peer is not told that a dispatch was cancelled, so it may still be
   working on it. **Done in round 43:** the pass runs on a timer as well
   (`DispatchReconcilerTimer`, §4d), and it leaves the dispatch that this process is
   working on right now alone. **Done in round 38:** the queue was
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
5. **The reliability estimate is researched and implemented, but not yet scored with real
   data.** The direction (Wilson lower bound at a fixed confidence, an interval-width
   threshold) is settled and recorded (§4m), and the selector **does** read it — but only
   when the estimate's state is `ESTIMATED`, and it keeps the term out of the score for
   every other state, naming the reason in the decision record. **What remains:** no
   held-out set has been measured to show that it would improve the selection, and the
   system still has **no decided trials**: the local call sites record `UNVERIFIED`
   because nothing in those paths verifies the answer, so `n` is zero and the state is
   `NO_EVIDENCE`. The remote path is the one place where a verifier exists — chain A's
   own assessment of the peer's report — and §4q describes how its verdicts reach the
   profile and what the resulting quantity does and does not mean.
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
   (30 tasks) — p50 **14.29 ms**, p95 **21.41 ms**,
   throughput **42.99 tasks/s**, every sample `UNRESOLVED`; the concurrent path
   (24 tasks, four at a time) — every task adjudicated
   `UNRESOLVED`, the steady state (after the cold start of the first
   4 samples) p50 **27.03 ms**,
   **143.95 tasks/s**. The concurrent path is
   **slower per task** than the single-task one — four tasks in flight contend for the same
   store and the same peer — and the report says so rather than quoting the throughput of a
   run without its cold start. **The measurement's own noise is measured** (§3 and the
   performance report's §5a): seven runs of the same code, each against a store of its own,
   differ by **9.9 %** at p50 on the sequential path and by **2.7 %** at the median of the
   concurrent path's steady state, so no optimization was accepted on the strength of a
   benchmark run. **These numbers were re-measured in rounds 48–53, against a store of the
   run's own** (§4h): the figures above are the isolated ones, and the wave's earlier
   figures — measured against the state the laboratory had accumulated — are withdrawn.
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
12. **The wave is committed, merged into `develop` and pushed; the release into `main` is the
    owner's.** The owner authorised the wave's commits and the push; the wave's own head is
    `8cfcf854`, the merge that brought it onto `develop` is `48bfe37a`, and `develop` was
    pushed with the four dependabot bumps that follow it (`9f9948e7`). **Pull request #50
    (`develop` → `main`) is open**, and the merge of it is the owner's release decision — an
    agent does not take it. What remains for the wave is that release, the reconciliation of
    `main` into `develop` (prepared and reviewed, not applied), and the limitations this
    table names. The wave's own report is not a claim that nothing is open: what is open is
    named here rather than left to be discovered.
13. ~~**The performance report separates the peer's own execution from the crossing, but not
    the peer's remaining stages.**~~ **Done in round 41.** The wave had written that
    separating the peer's authentication, its inbox write and its answer signing needs the
    peer to carry more than one duration, and that this needs a protocol-version decision
    rather than only a measurement. **That was over-cautious.** The measurement is the
    peer's own bookkeeping about its own work, so the peer now measures it and keeps it
    in its own durable row, and the performance report reads the peer's own file: the
    answer contract did not change, the caller receives exactly what it received before,
    and the protocol needed no new version. The peer's own numbers are now separate:
    its durable inbox write, the write that starts the request, its execution, the write
    of the outcome, the signing of the answer, and the remainder of the reception — which
    carries the authentication, the validation and the receiver's bookkeeping together,
    and is therefore named a remainder and not a measurement of the signature alone.
    **What remains:** the peer's stages are measured on the peer's side only; chain A
    still times the crossing as one interval, and the remainder is not split further.
    **What was done in rounds 23–31:** the
    two processes' own accounting is measured from outside them (the CPU time, the heap, the
    garbage collection, the estimated allocation and the retained memory), the two
    workloads live in one reproducible document, the **noise is measured on the same code**
    — four runs of the identical build differ by 7.3 % at p50 on the sequential path and by
    10.5 % on the concurrent path's steady state — and the peer's own duration is
    recorded beside the crossing. **Round 44 corrected the earlier reading of this.** The
    table that spans the wave's rounds (43 % and 157 %) was reported as a noise floor, and
    that was wrong: the wave changed the code between those rounds — the episode
    integration, the adjudication, the trace, the timer — so the table shows the cost of
    that work, not the variance of a measurement. The same-code measurement puts the real
    noise at 7–11 %, and the path's growth across the rounds is explained by the path
    doing more. Because the real noise is that narrow, **no
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
    8.07 ms against the first execution's 14.29 ms, 18.3 ms of CPU against 64.7 ms, and
    30 of 30 replays answered with the disposition of the first execution. What is still
    not told apart is the peer's own work inside the replay: the peer carries one duration
    — the duration of the **original** execution — so it cannot be attributed to the
    replay, and the report says so instead of recording it as the replay's cost. The
    replay also does **not** prove a distributed exactly-once guarantee: it proves that the
    peer's logical effect was not applied twice for these thirty requests, and the
    at-least-once delivery with an effectively-once effect remains what the design claims.

16. **The document census does not know 69 documents of the earlier waves.** The wave added
    its own twelve to `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv` and named the rest, but
    the census itself is still a W46 artefact: the governance set, the engineering reports of
    W46.0–W52, the W47 specifications and six other documents are in the tree and not in the
    registry. The obligation stands in `ROADMAP.md` §9 and no wave that created them closed
    it. **This wave does not guess a status for a document it did not write** — it counts the
    debt and names it (§4k).
17. **The isolation gate is a static proof over the compiled classes, and a path computed at
    runtime would escape it.** The gate measures what the bytecode says: the closure of the
    entry points, and the literal names of the databases in the constant pool. It proves
    that no class of chain A names chain B's databases **as they are written in the code
    today**. A path assembled from parts at runtime, or a file opened by a name that no
    class holds literally, would not be seen by it. The gate also says nothing about the
    **content** that crossed the boundary — that is the epistemic evaluation's business, and
    it is a different rule (§4l names what the gate does and does not measure).

---

## 7. The closeout, still to do

The remaining deliverables of §20 are the per-case epistemic comparison, the before/after
measurements for any accepted optimization, and the list of the remaining experimental
components. **The comparison is written and re-verified** (§4i below); **no optimization
was accepted**, so the before/after list is empty by fact and not by omission — the
performance report states the measurement's own noise (9.9 % on the sequential path) and
why nothing the wave changed was measurable above it; and the list of the remaining
experimental components is the table at the end of this section. **The peer's answers reached the profile of methods in round 64 (§4q)** — the remote path is
the only place where a verifier exists, so it is the source of the system's first decided
trials; each verdict is recorded in its stratum, and the rate it produces is stated with the
confounder it carries (**CD-046**).
**The CI state was reproduced on this machine in round 63 (§4p)** — the linter the CI runs
(156 files with findings — 182 findings in total, all inherited — and **zero** in the wave's own scripts after one
fix), the formal gate (15/15), the security boundary (held, the live data untouched), and the
one check that cannot be reproduced here (TruffleHog — a CI step that runs on the push),
stated as not run rather than as a pass.
**The crash boundaries of §6.4 were mapped onto the tests that prove them in round 62 (§4n)** —
seven of the eight end in a decided state, and the sixth (the memory effect committed, the
outcome not) is named as the lossy one it is.
**The performance baseline was measured again against the wave's final tree in round 62 (§4o)**:
the headline statistics reproduced inside the measured noise, the tails moved and are named
without an explanation the measurement does not support, and the verification is a dated
artefact of its own (`W53-PERFORMANCE-VERIFICATION.json`) rather than a replacement.
**The method reliability rule was implemented and enforced in round 61 (§4m)** — the Wilson
lower limit at the 90 % level, the three states, the two bases of the accuracy term, the
verdict taxonomy and the stratified key — and the rule's own verdict is that the estimate does
not enter the score, because the system's evidence does not bear it. The scope grew to
**1646 tests in 250 classes, 0 failures**.
**The isolation gate was checked against the mandate's own list of statements in round 60
(§4l) and one statement was missing** — *chain A cannot reach chain B's stores* — so the gate
grew **G12** and its negative control grew **C6**: the gate now runs **32 checks, 0 failed**
and the control **19 checks, 0 failed**.
**The wave's place in the document tree was audited in round 58 (§4k) and the audit is
closed**: two waves carry the name W53, the second had overwritten the first's report, the
same task ids were published twice, the census did not know the wave's documents, and the
index did not reach them — all of it is fixed, and the debt that belongs to the earlier
waves is named rather than absorbed.
**The wave's own work was reviewed twice in round 66 (§4s)**, by two independent readers: one
that read the uncommitted production code against the mandate, one that read the wave's
documents against the artefacts that prove them. The code review found a defect that would
have killed a live task — the reconciliation pass closed a dispatch the peer was still
working on — and eight smaller ones; the evidence audit found the wave's documents describing
a tree that no longer exists, among them **withdrawn performance numbers presented as
current**. All of it is fixed, the honest list names what the fixes did not close (the remote
profile that no decision reads yet), and the baseline still holds: **18/29**, **271/271**,
**32 checks**, **19 checks**, **1646 tests in 250 classes, 0 failures**, and the
demonstration's **16 stages, all `OK`**.
**The performance report with the stage-by-stage breakdown is written**
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
| the method reliability term in the selector's score | **the rule is implemented; the estimate is still out of the score** | §4m: the estimator, the three states, the two bases of the accuracy term, the verdict taxonomy and the stratified key are in the code, and the rule holds the term out — the system's own verdicts are `UNVERIFIED` (nothing verifies a local method's answer), so there is no decided trial and the state is `NO_EVIDENCE`. §4q: the **remote** path's verdicts now reach the profile, in the stratum of the capability, the family and the version — but the held-out measurement that would show the term helps the selection is not done (H-132 stays OPEN), and the peer's capability is not a candidate of the local selector, so the estimate the remote path accumulates is not yet read by any decision |
| the full circular-evidence predicate | **partial, and one step further** | the lineage travels with the report and is compared with what chain A supplied — a report resting on it is QUALIFIED, not SUPPORTED — and since round 18 the **proposition itself** is compared too: a report that states the very proposition chain A supplied is QUALIFIED even when the peer declares no lineage at all (H-131). What remains: the comparison is literal, not semantic — two differently worded statements of the same thing are not recognised as the same proposition |
| the plugin registry's lifecycle, version, health and budget checks in the remote receiver | **not wired in** | the receiver resolves capability ids through an explicit, allowlisted map, which is the boundary §11 asks for, but the registry's own checks are not part of it yet |
| the peer's cancellation | **implemented in round 19** | the withdrawal travels as its own signed kind and the peer acts on it: a task withdrawn before it started is never executed, a task withdrawn while it runs finishes and is closed as cancelled with the answer kept, and a finished task is not withdrawn. What remains: the peer cannot interrupt the brain cycle it is inside, so a running task always runs to its end — the cancellation changes the record, not the computation |
| the reconciliation on a timer | **implemented in round 43** | the pass runs on a timer as well as at the startup: `DispatchReconcilerTimer` runs `DispatchReconciler.reconcileStale()` every 30 s, and the pass sends only the dispatches this process is **not** working on right now — a dispatch that is in flight and a dispatch whose answer was lost look the same on the record, and a second delivery of a running task would spend one retry of the durable budget for nothing. The clock decides *when* the pass runs; the durable record and the in-flight set decide *what* it sends. The peer deduplicates the request id, so the re-delivery is at-least-once with an effectively-once effect |
| the peer's own unfinished rows after a crash | **closed in round 20** | the peer's inbox keeps the request's digest and not its payload, so it cannot finish an interrupted execution; the rows it finds in `ACCEPTED` or `RUNNING` are closed as a reasoned failure when the process starts (`RemoteTaskStore.reconcileUnfinished`), and a later delivery of such a task is answered with that failure rather than with "still running". What remains: the interrupted work is lost rather than resumed — the peer cannot resume it, and re-running it would risk a second effect for a task whose first effect may have landed |
| the eleven remaining epistemic failures | **deferred to a follow-up wave** | they are a retrieval question (the relevance floor and the status filter in `KnowledgeRetriever`), and §14 forbids turning W53 into a rewrite of the retriever |
| the native and GPU paths | **not enabled** | §10.4 keeps the Java path selected; nothing in the end-to-end profile exposed a computational bottleneck that would justify the marshalling cost |
| the concurrency of the distributed path | **measured, not optimized** | the concurrent path is slower per task than the single-task one (27.03 ms against 14.29 ms at p50) and reaches **143.95 tasks/s** against the single-task path's **42.99** with four tasks in flight; no optimization was accepted, because nothing was profiled as a profile (CD-W42-01), and the measurement's own noise — **9.9 %** on the sequential path — is larger than every difference the runs showed |
| the concurrency cap | **measured at the cap and above it** | the cap of four is measured from both sides: at four in flight the path reaches 143.95 tasks/s, and at eight clients **half the tasks are refused** rather than queued (§5d of the performance report, CD-042). What is not measured is the cost of the refusal to the *caller's* work: the refused task is not done, and the report says so |
| the wave's commit, push and pull request | **committed, pushed, merged into `develop`, and RELEASED into `main`** | the owner authorized the wave's commits on 2026-11-11, and the branch `feature/w53-distributed-cognition` carries them. **The release itself is done**: the hardening and the representation-substrate research were committed on `develop` (`04b7c5de`, `027e8f76`, `5e26748d`, `9ec6723b`), `main` was merged into `develop` (`bfbec57b`, the tree unchanged), pull request #50 was **squash-merged** into `main` (`bcdd1a0a`, 2026-11-14), and `main` was merged back into `develop` (`e41cd3c1`) so that the two branches hold the same tree (`d434705b`) the measurement tooling and the performance report, the implementation with the crash boundary it lost, and the registers that name the commit which carries the wave. **Rounds 41 to 65 are committed** — they are the peer's own stage measurement, the reconciliation timer, the isolation gate's second half, the reliability rule and the remote path's verdicts — and the wave's head is `8cfcf854`, merged into `develop` by `48bfe37a` and pushed with the dependabot bumps that follow it (`9f9948e7`). The **pull request #50** (`develop` → `main`) is open, and **its merge is the owner's decision**: it is a release operation over serving code, and an agent does not take it |

---

## 12. Release addendum (2026-11-14)

The wave was released into `main` after this report was written: pull request #50, squash
`bcdd1a0a`, 2026-11-14; the release record followed as pull request #51 (`c4cee8ab`) and the
registers naming the release's final commits as pull request #52 (`1dc01537`). The released
tree and the integration tree are byte for byte identical (`da7edd36`), and `main` is
contained in `develop`'s history.

The status field above now says `RELEASED`; nothing else in this document changed. The
recorded run, the `UNRESOLVED` verdict, the single durable inbox row and the unsigned-claim
caveats are the evidence of the wave at its own commit and remain exactly as measured.
