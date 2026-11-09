# DESIGN-84 — The distributed cognitive bridge

> W53 research-engine iteration: the four cross-disciplinary tracks of the distributed
> cognition wave, and the design they forced.
> Domain: distributed systems, cybernetics, cooperative reasoning, statistical reliability.
> Sources: the four documents under `docs-v2/research/W53-RESEARCH-*.md`, which cite the
> primary literature; this design records what was **adopted**, what was **rejected**, and
> what is still **deferred**.

## Goal

Make MATRIX capable of assigning a bounded cognitive task to another running cognitive
process, receiving its actual result, evaluating that result, and completing the original
task with a durable, verifiable execution trace — without giving up control over its
reasoning, knowledge or state.

## What the research forced (and what it refused)

| The finding | The decision | The status |
|---|---|---|
| A durable inbox plus a durable outbox, and delivery that is at-least-once with an idempotent effect, is the honest way to cross a process boundary that owns two databases (Gray & Reuter, 1993; Kleppmann, 2018, ch. 11) | the intent is written **before** the send; the peer keeps a durable inbox keyed by the request id and the payload hash; a duplicate returns the first result | **adopted** |
| A distributed exactly-once guarantee is not available across two independently owned databases without a distributed transaction, and the manifesto's two-phase commit is exactly the cost one avoids (Gray & Lamport, 2007) | the guarantee is stated as **at-least-once delivery with effectively-once logical effects**, and the demonstration's report says so in its own words | **adopted** |
| A regulator needs feedback about the plant it regulates; a delegation with no feedback is open-loop and therefore unbounded (Wiener, 1948; Ashby, 1956) | the request carries the **criterion** by which the answer will be judged, and the answer comes back with the evidence it rests on — the loop is closed by the criterion, not by the confidence | **adopted** |
| A peer is a *source of reports*, not a source of truth; the report's provenance and source dependence must survive the crossing (Bruner, 1983; Hutchins, 1995) | the report carries the **relation** and the **lineage**; the sink never invents a `SUPPORTS`; a report that rests on what was supplied is **qualified**, not supported | **adopted** |
| The same information returning through another process is not independent corroboration (Pearl, 1988; Hájek, 1997) | the lineage travels with the report and is compared with what chain A supplied | **adopted, partially** — the comparison is by lineage, not by proposition; the full predicate is an open item |
| A method's success rate from a handful of observations is not a reliability estimate; the Wilson interval is the defensible one, and it must be wide when the evidence is thin (Wilson, 1927; Brown, Cai & DasGupta, 2001) | the direction is settled: a **Wilson lower bound**, and the estimate stays out of the score until the evidence is representative | **deferred** — researched, not scored; the wave records the deferral rather than an unjustified number |
| A bounded autonomy needs a bound on the recursion, on the queue, and on the budget (Beer, 1972; Bødker & Weil, 2002) | one remote hop (`MAX_REMOTE_HOPS = 1`), a payload limit, a budget and a deadline travel in the request | **adopted and enforced on both sides** — chain A caps the dispatches in flight and refuses an oversized task before it writes or sends anything; chain B's durable inbox is bounded on the work in flight and refuses beyond it. The negative controls prove the guards can go red |

## The shape that resulted

```
chain A:  intent (durable) → dispatch → verify → admit as candidate → evaluate → record
chain B:  authenticate → admit durably → execute the production path → answer signed
the loop:  the request carries the criterion; the report carries the relation and the lineage
```

Three rules hold everywhere, and each is held by a test rather than by a comment:

1. **A peer's confidence never makes a proposition supported here.**
2. **A report that rests on what chain A supplied is qualified, not supported.**
3. **The admission of the report and the verdict on the proposition are separate facts.**

## What was rejected

- **A second method selector.** The kernel's `AlgorithmSelector` still chooses the method;
  the remote port only asks whether the capability that was already chosen is one another
  process can serve. A second ranking would have been a second opinion about the same
  question, and the two would drift.
- **A universal service locator.** The coordinator depends on two ports — the transport and
  the report sink — and on nothing else concrete.
- **Distributed two-phase commit.** It would have bought an exactly-once story at the price
  of the availability and the autonomy that the two-process architecture exists to keep.
- **Treating the HTTP 200 as the outcome.** The demonstration's recorded verdict is
  `UNRESOLVED` even though every exchange succeeded.

## What the wave ended with

The design above was the shape at the beginning of the wave; these are the decisions the
implementation added, each with the evidence that holds it.

| the decision | why | the evidence |
|---|---|---|
| the request is stored **with** the durable intent, so the unfinished dispatch can be sent again with the same request id | a request id is the identity of an attempt, not its content: without the request itself the recovery path is theoretical | `cognitive_task_dispatch.request_json`; `CognitiveCoordinatorRecoveryTest` — with the request no longer stored, exactly the three matching tests go red |
| the delivery attempts are counted in the dispatch's own row | the retry budget belongs to the *task*, not to the life of a process; a restart must not buy a new one | `CognitiveCoordinatorRetryTest` — four tests, and with the budget widened past the policy exactly the two matching tests go red |
| the peer refuses a task it cannot hold, and says so **durably** | a refusal that is not durable is a timeout the caller has to discover on its own | `TaskPeerBoundsTest` — the inbox bound, the payload limit and the deadline; with the three bounds disabled exactly the four matching tests go red |
| the reconciliation runs when chain A starts | the unfinished work exists exactly after a crash | `DispatchReconciler` and the startup pass in `DistributedTaskResource` |
| the epistemic store's write transactions are serialized | one SQLite connection, one writer: two writers interleaved on it and one writer's `setAutoCommit(true)` cancelled the other's transaction — the remote report was lost with it | `KnowledgeStoreConcurrencyTest` — with the serialization removed, both of its tests go red |
| the delegated task names the episode it belongs to | §8.1 asks that the same task id and correlation be visible across both processes; the correlation travelled with the placeholder `ep-parent` and the dispatch row named no episode at all | `DispatchIntent.parentEpisodeId`, the correlation's episode, the executor's episode — with the placeholder restored, exactly the two matching tests go red |
| the peer closes the rows its last run left unfinished | §6.1 forbids a request that stays `RUNNING` after a process recovery; the peer's inbox promised a reconciliation pass and did not have one | `RemoteTaskStore.reconcileUnfinished` — with the reconciliation disabled, exactly the two matching peer tests go red |
| a duplicate finds the task running, and the peer says so | §6.3 asks that a duplicate return the existing status; the peer returned no status at all for a task that was still running, and the caller spent its budget discovering a delay the peer knew about | `TaskBridge.runningResponse` (the status `IN_PROGRESS`) and the coordinator's waiting — with the waiting disabled, exactly the matching coordinator test goes red |
| the withdrawal reaches the peer and the peer acts on it | §6.5 asks that a cancellation be supported where it is safe; a withdrawal that stops only the caller hides work | `CognitiveTaskCancel`, the `INFERENCE_CANCEL` right, `TaskPublisher.cancel`, the peer's `RemoteTaskStore.cancel` — with the peer's handling disabled, exactly the matching peer test goes red, and with the post-execution check disabled, exactly the matching one |
| chain A's adjudication reads chain A's own knowledge | §7 asks that the remote result be evaluated against existing evidence; the only implementation of that question was "nothing", so every report came back unresolved for want of a reading | `KnowledgeStoreEvidence` — a citable claim with supporting evidence supports, a candidate supports nothing, a claim whose evidence contradicts contradicts. With the propositional check disabled, exactly the matching assessor test goes red |
| the peer's allowlist checks the version and honours a revocation | §11 asks that a remotely selected capability be registered, version-compatible and revocable, and that the permissions be checked on every invocation | `CognitiveCapabilityPolicyTest` — with the version check and the revocation check disabled, exactly the two matching tests go red |
| the measurement waits for its own jobs only | a bare `wait` waits for the two servers as well, so the concurrent run stopped after the first batch and looked like a defect of the path | `scripts/w53-distributed-benchmark.sh`; the concurrent measurement is in `docs-v2/engineering/W53-PERFORMANCE.md` |

## What is still open

- The full circular-evidence predicate (§7.3 of the mandate): the lineage travels and is
  compared with what chain A supplied, **and** the proposition itself is compared with
  the propositions of the supplied records — so a peer that states what chain A sent
  cannot make it independent by leaving its lineage undeclared. What is still missing is
  the semantic half: two differently worded statements of the same thing are still not
  recognised as one proposition.
- The reconciliation of unfinished dispatches runs when chain A **starts**; it is not on a
  timer, so an answer lost while the process stayed up waits for the next restart.
- The peer reconciles its **own** unfinished rows at startup, and it closes them as a
  reasoned failure rather than finishing them: the peer keeps the request's digest and
  not its payload, so it cannot resume an interrupted execution, and re-running it
  would risk a second effect for a task whose first effect may have landed. The
  interrupted work is therefore lost — visibly, with the reason on the row.
- The peer cannot interrupt the brain cycle it is inside: a task withdrawn while it runs
  finishes its computation, and the withdrawal changes the *record* — the row is closed as
  cancelled and the answer is kept — not the computation itself. A task withdrawn before
  it started is never executed at all.
- The reliability estimate in the score, and a held-out set that would show it helps
  (the deferral is recorded, not hidden — hypothesis **H-132** is OPEN).
- The chain A plugin registry's own health and budget checks are not consulted for a
  remote capability: chain B cannot reach chain A's classes at all — the isolation gate
  holds that — so the remote capability's lifecycle is the peer's business, and the peer's
  own allowlist now carries the version check and the revocation.
