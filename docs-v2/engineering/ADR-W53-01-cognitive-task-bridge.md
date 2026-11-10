# ADR-W53-01 — The cognitive task bridge: a bounded task, executed remotely, evaluated at home

**Status:** `CANONICAL` · **Date:** 2026-11-09 · **Branch:** `feature/w53-distributed-cognition`
**Subject:** the distributed cognitive execution boundary between Chain A and Chain B
**Companions:** `ADR-W51-01-cognitive-runtime-and-bridge.md` (the bridge that exists) ·
`docs-v2/engineering/W52-STATUS.md` (the two knowledge directions) ·
`docs-v2/engineering/W53-STATUS.md` (the wave's report) ·
`docs-v2/governance/DECISION-REGISTER.md` (CD-032, CD-033)

---

## 1. The decision

**MATRIX gains one governed capability: Chain A may assign a bounded cognitive task to Chain
B, Chain B executes it through its own production cognitive path, and the result returns to
Chain A as an authenticated report that Chain A evaluates before it affects anything.**

The decision has five parts, and each of them is a rule rather than an intention.

1. **The remote process is a capability provider.** Chain B holds capabilities that Chain A
   does not: its BIR rule base, its persistent mind, its HDC store, its own modulator and
   sleep machinery. A remote task asks for one of them, by an allowlisted capability id, and
   nothing else. There is no general "run this code" surface, no class name on the wire and
   no reflection entry point.
2. **The remote result is a report, not a source of truth.** It arrives as a secondary
   report with its own provenance, and Chain A's epistemic model adjudicates it: it may be
   used with an explicit qualification, used only in its supported part, rejected, marked
   unresolved, or cause an abstention. It never becomes an established belief because the
   remote call succeeded.
3. **The execution is durable before it is dispatched.** Chain A persists the dispatch
   intent before the request leaves; Chain B persists the accepted request before it
   executes. The request id and the payload hash are durable on both sides, and the
   idempotency decision never rests on an in-memory map. The guarantee is
   **at-least-once delivery with effectively-once logical effects**, not a distributed
   exactly-once claim — two independently owned SQLite databases cannot offer that, and the
   document says so instead of implying it.
4. **The crossing is the existing bridge, extended.** The W53 capability rights are two
   new rights inside the W52 bridge contract (`INFERENCE_REQUEST`, `INFERENCE_RESPONSE`).
   The direction is carried by the granted right, as in W52; the version rule is unchanged
   (version 2 is still the version, version 1 is still readable, an unknown version is still
   refused with its number named); the signature, the budget, the provenance and the audit
   are the same governance. No new port, no second channel, no route mounted in the wrong
   process.
5. **One coordinator, no second selector.** Chain A has exactly one place that decides which
   capability answers a task — the existing `AlgorithmSelector`, reached through explicit
   adapters — and one coordinator that drives the task's life. A remote capability is
   selected by the same machinery that selects a local method, and the selection trace says
   which was considered, which preconditions held, and why the chosen one won.

---

## 2. What is preserved, and what is not

**Preserved, unchanged:**

- two processes, two databases, one designated writer each, no cross-database transaction;
- the writer locks, the fencing, the snapshot and recovery guarantees;
- the bridge as the only governed crossing, and the isolation gate that proves it;
- the fail-closed behaviour of every refusal;
- the existing knowledge directions (Chain B → Chain A, Chain A → Chain B) with their
  rights and their semantics — **a knowledge message is never reinterpreted as a task
  request**;
- the canonical contracts, extended only where the new responsibility is genuinely absent;
- the epistemic rules: a candidate never answers as an established fact, similarity is not
  evidential support, provenance stays inspectable, evidence and belief stay two records,
  contradictions stay inspectable, revisions keep their history, and a lexical heuristic
  never impersonates a semantic proof.

**Not preserved — deliberately changed:**

- **Chain B's request-id registry for a task stops being process-local.** W52's second
  direction deduplicates in memory, and W52's report named that as a defect. For a task
  that executes a real cognitive path and can commit a memory effect, an in-memory map is
  not enough: a restart between two deliveries of the same request id can apply the effect
  twice. The W53 receiver writes the request id, the payload hash, the state and the result
  into chain B's own store before it executes.
- **The first remote task is narrow.** One task kind, one capability, one hop. The mandate
  asks for a demonstrably supported capability rather than an invented universal reasoning
  API, and the breadth comes later if the narrow path is proven.

---

## 2a. What the research tracks found, and what this wave changed because of it

The wave ran four cross-disciplinary research tracks (META-R1) before writing the code, and
two of them found defects in the implementation that was already in flight. They are
recorded here because a decision that ignores its own research is not a decision.

**Track A (durable execution)** established that "no cross-database transaction" is a
property of SQLite, not a policy: the WAL documentation states that a transaction over
several attached databases "are atomic for each individual database, but are not atomic
across all databases as a set". No configuration rescues a distributed two-phase commit, so
the design is **crash-consistent, never atomic** — and the reconciliation is a re-dispatch
with the same request id. It also found four defects, two of them beyond what W52 named:

- chain A's durable ledger is **find-then-act** and writes its row *after* the effect ran,
  and it stores no effect — so "replay of a completed result" is unimplemented on chain A;
- the fingerprint binds the **payload only**, so the same request id with a different
  capability right is not detected as a conflict;
- two SQLite files open plain connections with no WAL, no `synchronous=FULL` and no busy
  timeout.

**Two of those were in the code this wave had just written**, and both are fixed: the
task inbox now opens with the same PRAGMA discipline the cognitive store uses, and the
bridge no longer falls back to a process-local registry — a bridge without a durable inbox
**refuses** the task rather than serving it from memory, which is the fail-closed answer
and the only one that does not let the W52 defect survive as a configuration.

**Track C (cooperative reasoning)** established the blocker that this wave's third phase
cannot pass without a change: **a remote report cannot express a contradiction.** The
bridge's sink writes the evidence relation as `SUPPORTS` unconditionally, so a report that
denies an established belief can only arrive as support for a new candidate, and it can
never reach the refutation path of the claim assessor. Two smaller gaps come with it: the
derivation chain is dropped at the sink boundary (the canonical provenance carries
`derivedFrom`, and the workbench's own records do not), so the circularity check has
nothing to run on; and a remote report is filed under a per-sender concept, so the
retriever cannot see it as evidence about the proposition it actually addresses. The
smallest change that makes the adjudication computable is therefore a **derivation-carrying
report** — a canonical record whose provenance names the sender-side records it rests on,
whose subject is named by proposition rather than by concept bucket, and whose relation may
be `SUPPORTS` **or** `CONTRADICTS`.

**Track B (cybernetics and distributed control)** turned the design into three testable
consequences, and the first of them changes the contracts:

1. **The request must carry the criterion by which the result will be judged.** Ashby's
   law of requisite variety, read in the form he gives it (§11/11, p. 210): *a regulator's
   capacity cannot exceed its capacity as a channel of communication*. A peer can be wrong in
   more ways than the refusal vocabulary of a coordinator can name — and Ashby's own
   "rudder that is stuck" is a well-formed report that says nothing. The current contracts
   bind the answer to the request by id and digest ✓, but they do not require the
   coordinator to have recorded **what it expected**, so the answer stays a report that
   nobody judged. The request therefore carries the coordinator's prediction, and the
   response is comparable to it or it is not.
2. **The deadline must be derived from what is left, and the two numbers in the
   repository contradict each other.** The kernel's cycle budget is 250 ms
   (`CognitiveKernel.java`) and the publisher's timeout is 5 s (`KnowledgePublisher.java`).
   A deadline that is not derived from the remaining budget is a number nobody enforces.
3. **A failed delegation must change the next delegation.** Ashby's ultrastability
   (1952, §8): stability comes from the refusal and the re-parameterisation, not from a
   well-matched actuator. Today a remote failure is recorded and forgotten — nothing
   reaches the method measurement, the selector or the regulator — so the loop has no
   feedback. The delegation's outcome must enter the same profile the local methods feed.

**Track D (reliability and calibration)** established that the durable method profile does
not measure reliability at all: its `successes` means "did not throw", so a wrong answer
counts as a success and a deliberate refusal counts as a failure, while the scored accuracy
is the **declared** point estimate. The profile's key is also a pooling defect — every
family, version and protocol lands in one rate, which is Simpson's paradox in a table. The
wave therefore records the reliability work as **designed but not yet implemented**
(§7.3 of the wave's report), with the estimator named: a 90 % Wilson **lower** limit, which
at one observation of one gives a width of 0.730 and therefore leaves the score exactly as
it is — a handful of observations must not create unjustified confidence.

---

## 2b. What the wave did with the blockers, and the protocol version it moved to

The three research tracks did not only produce documents; they changed the code, and the
wave records what changed.

**The protocol is version 3.** A payload that carries a task request, a task response or a
task report is a version-3 payload. A version-2 reader has never heard of those fields,
and BRIDGE-2 says a reader does not guess: it refuses. Versions 1 and 2 keep their
meaning exactly — nothing this wave changed touches the observations, the candidates, the
evidence links or the established knowledge — and the tests read a version-1 and a
version-2 message and check that the same records are written.

**The remote report can now contradict.** The report carries the relation the peer
actually stated — `SUPPORTS`, `CONTRADICTS` or `INDEPENDENT` — and the sink writes what it
was told: the evidence relation is the peer's relation, and a report that asserts no
relation is recorded as `CONTEXTUAL`, never as support. Before this, the sink wrote
`SUPPORTS` unconditionally, which meant a peer could never tell chain A "no". The report
is still a candidate, with this process's confidence of zero: a peer that is confident and
right and contradicts everything chain A believed still cannot promote itself.

**The lineage survives the boundary.** The records the peer derived the report from travel
with the report and land in the claim's extraction method, so a reader can tell a
proposition that came back from the peer from one the peer found on its own. The same
information returning is not a second, independent observation of it.

**The answer is authenticated in both directions.** The response and the report each
travel as their own signed envelope, in the same canonical form as every other message.
The publisher verifies both signatures before either reaches the epistemic side, and a
report whose signature does not verify is dropped rather than passed on.

---

## 2c. The coordinator and the durable intent

**One coordinator.** `io.matrix.cognitive.distributed.CognitiveCoordinator` owns the
distributed task: it resolves what the task needs, builds a bounded request, writes its
intent down, sends the request through the governed crossing, checks what came back,
evaluates it against chain A's own evidence and records the verdict. It is not a second
method selector: it does not choose *how* to think, it arranges *who* thinks, and the
choice of the peer capability is a property of the task's requirement. The transport is a
port, so the same lifecycle runs against the real publisher and against a test double.

**The intent comes first.** The dispatch intent is written to chain A's own database
*before* the request is sent. That order is the recovery story: after a crash, the
unfinished dispatches are still there, the same request id can be sent again, and the
peer's durable deduplication means the logical effect still happens once. The guarantee
this yields is at-least-once delivery with effectively-once logical effects — and the
guarantee it does not yield, a distributed exactly-once execution, is written down so
that no later reader has to guess it.

**The unfinished work is what the store says it is.** The reconciliation queue holds two
kinds of unfinished dispatch: one whose answer is not on record yet, and one whose answer
*is* on record but whose verdict is not — the process died between receiving the answer and
judging it. The second kind was a hole: the answer is not a verdict, so the task stayed
unjudged for ever, and no pass could finish it because no pass could see it. A dispatch is
unfinished while it owes chain A a decision; the two states that keep an answer unjudged
**on purpose** — a cancelled and a timed-out dispatch — are finished and are left alone.

**The verdict is chain A's.** `RemoteReportAssessor` turns the peer's answer into one of
six dispositions. Two rules hold everywhere: a peer's confidence never makes a
proposition supported, and a report that rests on what chain A supplied to the peer is
qualified rather than supported — the same information coming back is not a second
observation of it. A declared or measured confidence basis is not independent evidence,
and the assessor says so in the reason it records.

---

## 2d. The demonstration, and what it showed

`scripts/w53-distributed-demo.sh` starts two real processes — the Quarkus application and
the gateway's own JDK server in production mode — each in its own directory with its own
database, and runs one bounded cognitive task through them. The machine-readable report is
`docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` (schema
`matrix.w53.distributed-demonstration/v5`).

**The demonstration shows the refusals too.** A scenario that only shows the happy path
cannot show that the boundary holds. Since round 22 the report carries a `refusals`
section: a capability the peer does not serve (refused by the peer, and the refusal
travels back as a signed answer), the same request id under a different payload (refused
as a conflict, HTTP 409, with the first dispatch's record untouched), and a forged
request — one whose signature was made with the wrong secret (refused by the peer). The
forgery is checked by its **reason**, not by its status: a demonstration that proved the
wrong thing because the message was malformed would be worse than no demonstration.

**What the recorded run showed.** The task was dispatched, chain B ran its production
cognitive path, chain A evaluated the answer and recorded **UNRESOLVED** — the peer's
confidence was MEASURED, which is not independent evidence, and chain A's own records
settled nothing. That disposition is the honest one and it is the point of the whole
design: a successful exchange is not a supported belief.

**What the restart showed.** Chain B was restarted and the same request was sent again. The
report carries the peer's own durable inbox: **one row, whose updated timestamp equals its
creation timestamp**. The task was not executed a second time, and the result was still on
disk after the restart. That is the durable claim, measured rather than asserted — and it
is a claim about at-least-once delivery with effectively-once logical effects, not about
distributed exactly-once execution.

---

## 2e. The report enters the model, the verdict does not follow it

The peer's report is not only evaluated — it is **admitted**. It goes into chain A's
epistemic store through chain A's own bridge receiver, the same governed crossing that
admits chain B's knowledge messages, with the same authentication, the same capability
rights and the same audit trail. There is no second way in, and a report cannot reach the
model by any path that the knowledge messages cannot.

What it becomes is a **candidate**. The relation the peer stated is the relation that is
recorded, the lineage the peer declared stays visible, and the confidence of the claim is
chain A's own — which is none, because chain A has not confirmed anything. A peer that is
confident, correct and contradicting everything chain A believed still arrives as a
candidate with a contradicting link.

The verdict is a separate fact, and it does not follow the admission. Chain A's own
evidence — not the peer's confidence, not the fact that the exchange succeeded — decides
between use, qualification, rejection and abstention. The dispatch record keeps both:
`report_disposition` says what happened to the report, `disposition` says what chain A
concluded about the proposition. A reader can therefore tell a refused report from an
admitted-and-qualified one, and cannot mistake the first for the second.

---

## 2f. The remote task is inside the episode, not beside it

The mandate (§8) is explicit: the remote task must be part of the same logical cognitive
episode, and "a phase must not be added just to decorate the trace — it must represent an
actual execution or state transition".

The kernel therefore grew a **port**, not a second selector. `RemoteExecutor` says what the
kernel needs — *can another process serve this capability, execute this task, tell me what
came back* — and nothing about the transport. The **choice of the method is still the
`AlgorithmSelector`'s**: the port is asked only whether the capability that was already
chosen happens to be one another process can serve. `CoordinatorRemoteExecutor` is the thin
adapter that binds the port to the coordinator, so the kernel's path and the HTTP entry
point's path are the same path.

When the delegation happens, the episode's own trace shows four real transitions:

| The phase | What it records |
|---|---|
| `DISPATCH_REMOTE` | the capability that was chosen and the fact that it was delegated |
| `RECEIVE` | the request id, the status the peer named, the responder |
| `VALIDATE` | that the answer was checked against the contract, and the disposition |
| `EVALUATE_EVIDENCE` | the result measured against this process's own evidence, with the reason |

The peer's answer enters the cycle as a **note**, not as a fact: the note says in words that
the answer is a report and not a settled fact, and the local computation still decides the
cycle's own outcome. The delegation is bounded — a capability the peer does not serve is not
delegated, and the trace then contains no `DISPATCH_REMOTE` at all.

---

## 3. The contracts

### 3.1 The request

`io.matrix.contract.bridge.CognitiveTaskRequest` — a typed request for remote cognitive
work. It carries the protocol version, the stable task id, the stable request id, the
correlation (reusing `io.matrix.contract.Correlation`), the parent episode id, the
requesting sender and component, the target capability id and version, the task kind, a
bounded structured input, the declared constraints, the required output contract, the
execution budget, the payload limits, the deadline, the provenance, the capability
authorisation and the payload hash.

**The input is typed, and the natural-language text is one field of it.** The request is not
a free-form string: the task kind says which shape the input has, and the payload is
validated against the kind before the request is signed. The smallest context is what the
declared task needs — Chain A does **not** send its memory, its snapshot or its claim
store to Chain B.

### 3.2 The response

`io.matrix.contract.bridge.CognitiveTaskResponse` — the structured answer, with a status
vocabulary that separates the states the mandate separates:

| the status | what it means |
|---|---|
| `COMPLETED` | the capability executed and produced a result |
| `PARTIAL` | the capability produced a result that does not cover the whole request |
| `REFUSED` | the request was refused by policy (a right, a budget, a constraint) |
| `UNSUPPORTED` | the capability does not exist or does not serve this task kind |
| `FAILED` | the execution failed (a technical failure, not an incorrect inference) |
| `TIMED_OUT` | the deadline passed before the result was ready |
| `CANCELLED` | the request was cancelled, and the state says when |
| `IN_PROGRESS` | the execution model requires it (a long-running task) |

**Accepting a request and completing its cognitive execution are different states.** A
response of `COMPLETED` means the capability ran; it does not mean the answer is true.

The response carries the task and request ids, the correlation, the request's payload hash
(the receiver echoes it so the caller can bind the answer to the request), the responder
identity, the capability and version used, the status, the structured result, the
hypotheses considered, the prediction and the outcome, the supporting observations, the
contradictions and the limitations, the confidence **with an explicit basis**, the evidence
and the provenance, the method-selection rationale, the resource and execution
measurements, a reference to the durable execution trace, and a machine-readable refusal
reason when the status is not `COMPLETED`.

**No fabricated reasoning steps.** The response reports what the production path actually
exposes. Where a chain-B route fabricates a step for its own UI, the bridge does not carry
that fabrication into the epistemic layer.

### 3.3 The rights

Two new rights in `io.matrix.contract.bridge.BridgeCapability`:

- `INFERENCE_REQUEST` — the right to ask a peer to execute a bounded cognitive task;
- `INFERENCE_RESPONSE` — the right to return the result of one.

**The right to receive established knowledge is not the right to execute a task, and the
right to execute a task is not the right to write to Chain A's epistemic store.** A caller
cannot widen its own grant: the grant is held by the receiver and checked on every
invocation.

---

## 4. The execution lifecycle

### 4.1 The states

```
CREATED → DISPATCH_PENDING → ACCEPTED → RUNNING → COMPLETED
```

with the terminal alternatives `REFUSED`, `UNSUPPORTED`, `FAILED`, `TIMED_OUT` and
`CANCELLED`, and with `RECONCILE` for a request found in a non-terminal state after a
restart.

Every transition has an owner and a persistence location:

| the transition | the owner | where it is durable |
|---|---|---|
| `CREATED` → `DISPATCH_PENDING` | Chain A | Chain A's cognitive database (the task and its dispatch intent) |
| `DISPATCH_PENDING` → `ACCEPTED` | Chain B | Chain B's own store (the inbox row) |
| `ACCEPTED` → `RUNNING` | Chain B | Chain B's own store |
| `RUNNING` → `COMPLETED` | Chain B | Chain B's own store (the result and the trace reference) |
| `ACCEPTED`/`RUNNING` → the terminal refusals | Chain B | Chain B's own store |
| `DISPATCH_PENDING` → `COMPLETED` (the response) | Chain A | Chain A's cognitive database (the remote report) |
| any → `RECONCILE` | whoever restarts | the same store that owned the state |

**A request is never "completed" before its result is durably recorded.** A request found
`RUNNING` after a restart is not silently failed and not silently re-run: it is
reconciled — the receiver asks its own store whether the effect was committed, and the
caller asks its own store whether the response arrived.

**The peer reconciles its own rows.** Chain A's reconciliation re-sends the unfinished
dispatches. The peer's does the other half: the rows its previous run left in
`ACCEPTED` or `RUNNING` are closed as a **reasoned failure** when the process starts.
The peer cannot finish them — it keeps the request's digest and not its payload — and
re-running a task whose first effect may have landed would risk a second effect. So the
row says what happened ("the peer restarted while this task was unfinished; the request
was not kept, so the execution could not be finished and its outcome is unknown"), and a
later delivery of that task is answered with the failure rather than with "still
running": a caller must never wait for work that nobody is doing.

### 4.2 The idempotency rules

For a given stable request id:

1. the first valid payload is accepted;
2. a duplicate with the same payload hash creates no second logical effect and returns the
   existing status or the original completed result;
3. the same request id with a **different** payload hash is refused as a conflict;
   a duplicate of a task that is **still running** is answered with the task's status
   (`IN_PROGRESS`) — the caller learns that the peer is working on it instead of
   spending its budget discovering a delay the peer could have named;
4. a retry after restarting either process does not repeat a committed effect;
5. the result and its trace stay retrievable after a restart.

The request id and the payload hash are persisted on both sides. The decision does not
depend on an in-memory map.

### 4.3 The bounded work

The request carries a bounded payload, a bounded execution budget, a deadline and a hop
count. The implementation enforces a maximum request size, a maximum response size, a
maximum in-flight count, a bounded queue, a bounded retry budget and **a single remote
hop**: a capability executed by Chain B does not create further remote tasks.

### 4.3a The episode the delegated task belongs to

A delegated task is part of the episode that delegated it. The correlation therefore
carries that episode's real id — not a placeholder that stands for an episode which does
not exist — and the dispatch row records it, so the kernel's trace and the row name each
other, and the peer's request carries the same episode id. A task arranged outside an
episode (through the HTTP entry point) names **no** episode: an honest gap is better
than a name that points nowhere.

### 4.4 The withdrawal

A cancellation is a crossing of its own: `CognitiveTaskCancel`, the right
`INFERENCE_CANCEL`, a message the peer grants and audits like any other. It is never a
field of the request it withdraws — carried there, it would arrive with the work itself,
too late to prevent anything, and every request would carry the authority to withdraw
itself.

Its **message id is derived from the request id and is not the request id**. The bridge's
idempotency is keyed on the request id, so a withdrawal that reused it would reach the
peer as a second payload under an id it already knows and be refused as a conflict — the
message meant to end the request would be refused as a forgery of it.

The peer's state machine decides what became of the task:

| the withdrawal arrives | the peer does | the record says |
|---|---|---|
| before the task started | the task is never executed | `CANCELLED`, no answer |
| while the task runs | the computation finishes — the brain cannot be interrupted | `CANCELLED`, the answer kept |
| after the task finished | nothing is withdrawn | `COMPLETED`, the effect stands |

A cancellation cannot undo a committed effect, and a row that said otherwise would hide
a fact the caller needs.

---

## 5. The epistemic rule at home

The response is a **secondary report**. Chain A evaluates it and records the evaluation:

- does the response answer the task that was asked?
- is its provenance complete, and is the source inspectable?
- does the cited evidence exist in Chain A's own workspace?
- does the result contradict something Chain A already holds?
- is the confidence's basis `DECLARED`, `MEASURED` or `INDEPENDENT` — and what does that
  distinction permit?

**Circular evidence is detectable.** The report keeps the identity of the knowledge that
chain A itself supplied to Chain B, so a proposition that returns through the other process
is recognised as returning, and is not counted as independent corroboration.

The final answer is Chain A's own: it may use the result with an explicit qualification,
use only the supported part, ask for further evidence within the task's budget, reject the
result, mark the proposition unresolved, or abstain. **It does not promote the whole
response to `SUPPORTED` because the remote task completed.**

---

## 6. The gate

The isolation gate grows, and its negative control must prove that the new checks can go
red. The new checks prove that:

- Chain B can receive a bounded cognitive request and nothing more;
- Chain B reaches no class of Chain A's stores, and Chain A reaches no class of Chain B's;
- the new protocol endpoints are the only allowed crossing;
- the new rights cannot mutate an unrelated domain — a task right cannot write a claim;
- the request path cannot bypass the authentication and the audit;
- the response path cannot bypass Chain A's epistemic evaluation.

---

## 7. What this wave does not claim

- **Not exactly-once.** The guarantee is at-least-once delivery with effectively-once
  logical effects, and it is implemented and tested as such.
- **Not a general remote execution platform.** One task kind, one capability, one hop.
- **Not a semantic match.** Chain A's retrieval limits are unchanged and remain named;
  the distributed path does not make a weakly-supported answer well-supported.
- **Not general intelligence.** A remote capability provider is an engineering fact about
  two processes, not a claim about understanding.
