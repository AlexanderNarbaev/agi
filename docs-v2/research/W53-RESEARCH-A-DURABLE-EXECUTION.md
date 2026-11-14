# W53 Research A — Durable, idempotent, crash-consistent execution across two databases

**Status:** `RESEARCH` — a research document: it proposes a design and names its primary sources; it binds nothing.
**Date:** 2026-11-09 · **Author:** research track A, W53 · **Kind:** research document (no code changed)
**Subject:** the correct durable execution protocol for *"Chain A dispatches a bounded cognitive task to Chain B; Chain B executes it; Chain A receives and evaluates the result"*, given two independently owned SQLite databases.

**Scope — what this document does NOT claim.**
It does not claim that MATRIX has general intelligence, human-like understanding, universal learning or reliable autonomous self-improvement, and it makes no claim about the quality of chain B's reasoning. It does not claim that MATRIX already implements the protocol described here — it does not; §3 lists the gaps. It does not claim exactly-once execution, exactly-once delivery, or atomicity across the two databases; §6.6 states which guarantees are and are not claimed. It is a **research** document: it proposes a design and names the primary sources behind it, and it changes no normative document, no ADR and no code. Every statement about the current code is a `file:line` citation into this repository, read rather than inferred.

**Evidence convention.** Claims marked **[EVIDENCE]** rest on a cited primary source or on a cited line of this repository. Claims marked **[HYPOTHESIS]** are this track's reasoning, not a measurement; they are stated so that a later wave can falsify them.

---
## 1. The question

Chain A is the epistemic process (Quarkus, port 9091). Chain B is the brain runtime (JDK `HttpServer`, port 8765). They are two processes, two runtimes, two SQLite files, each with exactly one designated writer. There is no cross-database transaction and no two-phase commit. The bridge is the only governed crossing between them.

The question is narrow and concrete: **when chain A hands a bounded cognitive task to chain B, what is the smallest protocol that (a) never loses the task, (b) never applies the remote effect twice in a way the system can observe, and (c) never tells the caller something it does not know?**

The interesting part is (c). The failure a naive design produces is not a lost task and not a duplicated task; it is a **confident false report** — chain A tells its caller "failed" when chain B in fact completed the work, or "succeeded" when chain B never ran it. Everything below is arranged so that such a report is impossible.

---
## 2. The question restated as five obligations

**No lost work** (a task chain A accepted durably must reach chain B, or chain A must be able to discover that it did not) · **no duplicated observable effect** (the *logical* effect happens once, even if the message is delivered many times) · **a decidable outcome** (for every request id, both sides can answer "what happened to this task?" from durable state alone, after any crash) · **honest uncertainty** (where the outcome cannot be known, the protocol says *unknown* — it does not guess *failed* and it does not guess *succeeded*) · **boundedness** (every queue, every retry sequence, every hop chain has a finite, declared limit).

---
## 3. The facts of MATRIX that constrain the answer

These are read from the repository, not assumed.

| # | the fact | where |
|---|---|---|
| F1 | One cognitive system, two processes; the boundary is no longer a boundary of cognition | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:13-19` |
| F2 | Chain B is the client, chain A the server; chain A keeps the epistemic writer, chain B keeps its own | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:102-108` |
| F3 | Three SQLite files, one writer each: `data/knowledge/workbench.sqlite` (chain A), `data/mind/mind.sqlite` (chain B), `data/bridge/bridge.sqlite` (`BridgeJournal` alone) | `docs-v2/engineering/W51-ARCHITECTURE-MAP.md:215-219`; `matrix-core/src/main/java/io/matrix/bridge/BridgeResource.java:45`; `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:222` |
| F4 | The bridge is the only crossing; gate rule G8 measured **0** chain-A bridge-server classes reachable from chain B | `docs-v2/engineering/W51-ARCHITECTURE-MAP.md:172` |
| F5 | BRIDGE-8 requires the idempotency to be **durable** and to survive the death of either process | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:121` |
| F6, F12 | Chain B's request-id registry for the second direction is **process-local**; a restart between two deliveries of the same request id can apply the effect twice — and the bridge that would hold it is wired to the in-memory implementation with the standard budget | `docs-v2/engineering/W52-STATUS.md:233-237`, restated as limit 4 at `:281`; `matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java:104-110` |
| F7 | The outcome of a task travelling back from chain B as a claim is **designed but not implemented** | `docs-v2/engineering/W52-STATUS.md:243-246` |
| F8, F27 | The protocol is version 2, version 1 is still read, and a change of meaning is a new version, never a silent change. The protocol's own javadoc already anticipates that version 2 "adds the second direction: **a task**" — while the implemented right is `KNOWLEDGE` ("established knowledge") | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeProtocol.java:33`, `:49`, `:11-12`, `:40-43`; `.../contract/bridge/BridgeCapability.java:40-59` |
| F9 | The request id is inside the signed canonical bytes, so it cannot be rewritten in flight | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeEnvelope.java:88-97` |
| F10 | `payloadDigest()` digests **the payload only** — not the protocol, version, sender, kind or instant | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeEnvelope.java:160-162` |
| F11 | The idempotency interface was designed with the durability seam on purpose: the in-memory implementation "does not survive a restart; durability is the server side's business, and this interface is the seam where that implementation will be substituted" | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeIdempotency.java:30-33`; `.../InMemoryBridgeIdempotency.java:16-22` |
| F13, F14 | `BridgeJournal.firstOutcome` is **find-then-act** and the ledger row is written **after** the effect ran; and the durable ledger stores no effect — `find` returns `Outcome.of(decision, digest)`, so a replay answers with the decision and **no outcome** | `matrix-core/src/main/java/io/matrix/bridge/BridgeJournal.java:112-134`, `:103`; `.../contract/bridge/BridgeReceiver.java:231-232` |
| F15, F28 | Two SQLite files open a plain connection with **no** `PRAGMA journal_mode`, `synchronous`, `busy_timeout` or `transaction_mode`: the bridge journal, and chain B's brain memory | `matrix-core/src/main/java/io/matrix/bridge/BridgeJournal.java:63-64`; `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:41`, `:47-52`, `:95-98`; `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentMind.java:57`; `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:222` |
| F16 | The epistemic store, by contrast, sets `journal_mode=WAL`, `synchronous=FULL`, `busy_timeout=10000` and `transaction_mode=IMMEDIATE` | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:180-186`, `:200-204`; `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:436-445` |
| F17 | The cognitive store already implements a **durable, atomic, crash-honest request lifecycle**: `CLAIMED / IN_PROGRESS / COMPLETED / FAILED_NO_EFFECT / INTERRUPTED_NO_EFFECT / INTERRUPTED_PARTIAL / INDETERMINATE / RECOVERY_REFUSED`, with `reExecutable()` and `terminal()` | `matrix-core/src/main/java/io/matrix/cognitive/RequestExecutionState.java:30-75`, `:78-83` |
| F18 | And a **checkpoint ladder** whose first irreversible rung is `OBSERVATION_COMMITTED` | `matrix-core/src/main/java/io/matrix/cognitive/RequestCheckpoint.java:22-61`, `:49` |
| F19 | The atomic claim is one statement: `INSERT … ON CONFLICT(key) DO UPDATE … WHERE fingerprint = excluded.fingerprint AND state IN ('FAILED_NO_EFFECT','INTERRUPTED_NO_EFFECT')` | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java:1616-1642` |
| F20, F21 | The bridge's durable idempotency on chain A rests on **content-addressed effect identity**: the claim id is derived from the request id, the sink checks the store before writing, and the write is `INSERT OR REPLACE INTO claim`, so a repeated application **converges** to one row | `matrix-core/src/main/java/io/matrix/bridge/BridgeService.java:31-36`; `.../bridge/EpistemicBridgeSink.java:88-91`, `:163-169`; `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:2710` |
| F22 | The whole epistemic import is one transaction with commit/rollback | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:2499-2501`, `:2536` |
| F23 | Every refusal in the receiver happens **before** the sink is touched; the effect is the last step | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeReceiver.java:320-329` |
| F24 | The budget is checked before the store, and its counters are **process-local** (reset on restart) | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeBudget.java:172-209`, `:67-70` |
| F25 | The publisher retries under `RetryPolicy.STANDARD` = 3 attempts, with a 5 s timeout | `matrix-core/src/main/java/io/matrix/bridge/KnowledgePublisher.java:59`, `:163-189`; `matrix-core/src/main/java/io/matrix/contract/RetryPolicy.java:52-53` |
| F26 | The SQLite driver is `org.xerial:sqlite-jdbc:3.43.0.0` | `matrix-core/build.gradle:57` |

### 3.1 The four defects this research exposes in the current code

| id | the defect | why it matters | evidence |
|---|---|---|---|
| **D1** | Chain B's registry is process-local | A restart between two deliveries of the same request id can apply the effect twice — the defect W52 named | F6, F12 |
| **D2** | Chain A's durable ledger is not atomic with the effect, and stores no effect | The effect is written, then the ledger row. A crash in between loses the decision. And even without a crash, the replay of a completed request answers with the decision but **no outcome**, so "replay of a completed result" is not implemented | F13, F14 |
| **D3** | The fingerprint binds the payload only | A request id re-used with a different **kind** (a different capability right) or a different **sender** is not a conflict — and the kind is a security-relevant field | F10, F27 |
| **D4** | **Two** of MATRIX's SQLite files lack the PRAGMA discipline the epistemic store has: the bridge journal (`BridgeJournal.java:63-64`) **and** chain B's brain memory (`SqliteMemoryBackend.java:41`, which opens a plain `jdbc:sqlite:` connection) — only `CognitiveStore` and `KnowledgeStore` set `journal_mode=WAL`, `synchronous=FULL`, `busy_timeout` and `transaction_mode=IMMEDIATE` | A second writer gets `SQLITE_BUSY` immediately instead of waiting; with the default rollback journal a power failure can lose a committed ledger row; and the two chain-B files that must share one transaction (§6.2) do not even agree on their journal mode | F15 vs F16; F28 |

---
## 4. The mechanisms

### 4.1 Transactional outbox — commit the intent in the sender's own database

**What it is.** The sender writes the message *into its own database, inside the same transaction as the business change*, and a separate relay reads it out and delivers it. No distributed transaction is involved: the outbox row and the business change commit together, and the delivery is a separate, retryable, at-least-once act.

**Primary source.** Chris Richardson, *"Pattern: Transactional outbox"*, microservices.io (the pattern catalogue by the pattern's author), accessed 2026-11-09 — https://microservices.io/patterns/data/transactional-outbox.html. The academic frame is Hohpe & Woolf, *"Enterprise Integration Patterns: Designing, Building, and Deploying Messaging Solutions"*, Addison-Wesley 2003, ISBN 9780321203146 — https://www.oreilly.com/library/view/enterprise-integration/032120314X/.

**What it guarantees.** *"If the database transaction commits then the messages must be sent. Conversely, if the database rolls back, the messages must not be sent"* [EVIDENCE, source §Forces]. The message is never lost and never sent for a rolled-back change.

**What it does NOT guarantee.** The relay may publish a message more than once: *"The Message relay might publish a message more than once. It might, for example, crash after publishing a message but before recording the fact that it has done so"*, and therefore *"a message consumer must be idempotent, perhaps by tracking the IDs of the messages that it has already processed"* [EVIDENCE, source §Issues]. The outbox gives **at-least-once delivery**, never exactly-once.

**MATRIX adaptation.** Chain A's dispatch row lives in chain A's own database and is committed in the same transaction as whatever chain-A state the task depends on. To touch: `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java` — the table `cognitive_task_dispatch` (§6.2) and the methods `claimDispatch`, `completeDispatch`, `failDispatch`, `expireDispatch`; **new** `matrix-core/.../io/matrix/bridge/DispatchRelay.java` — the relay that polls the pending rows, signs, POSTs and records the attempt, **not** on the request path; and `matrix-core/src/main/java/io/matrix/bridge/KnowledgePublisher.java`, which becomes the relay's client, unchanged in signature.

### 4.2 Idempotent receiver / inbox — the dedup ledger at the receiver

**What it is.** The receiver records the identity of each message it has begun to process, in durable state, so that a repeated delivery is recognised. The canonical statement is Hohpe & Woolf's *"Idempotent Receiver"* pattern (Enterprise Integration Patterns, 2003, ch. 8); the modern HTTP spelling is Richardson's *"Transactional Inbox"* [EVIDENCE, source §Related patterns].

**What it guarantees.** A repeated delivery of a message whose id is already recorded does not apply the effect a second time; the receiver answers from the recorded outcome [EVIDENCE, from the pattern's definition and from MATRIX's own F11 seam].

**What it does NOT guarantee.** Nothing about the *sender's* side, and nothing about the case where the effect and the dedup record cannot be committed together — that case is §4.5, not this one.

**MATRIX adaptation.** The interface already exists and already names the seam: `io.matrix.contract.bridge.BridgeIdempotency` with its single `firstOutcome(requestId, payloadDigest, Supplier<Outcome>)` method, whose own javadoc says *"the check and the act cannot be separated"* [F11]. The work is to supply a **durable** implementation on each side:

- chain A: `io.matrix.bridge.BridgeJournal` already implements it — but it must be made atomic (§6.4 R9 and §6.7 W2; defect D2).
- chain B: **new** `matrix-brain-runtime/.../io/matrix/brain/runtime/bridge/BridgeInbox.java`, implementing `io.matrix.contract.bridge.BridgeIdempotency`, backed by chain B's own SQLite. It must **not** reuse `io.matrix.bridge.BridgeJournal`: gate rule G8 measures *0 chain-A bridge-server classes reachable from chain B* [F4], so a chain-B inbox is a new class in a chain-B package, not a shared one.

### 4.3 At-least-once delivery, effectively-once logical effect

**What it is.** The transport delivers a message at least once and possibly many times; the *effect* is made once by construction — either because the effect is addressed by an identity derived from the request id (content-addressed write-once), or because the effect and its dedup record share one transaction.

**Primary source.** The OASIS AMQP 1.0 specification, Part 3 (Messaging Layer), 29 October 2012 — https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html. Two passages carry the argument: `delivery-count` is *"the number of unsuccessful previous attempts to deliver this message. If this value is non-zero it can be taken as an indication that the delivery might be a duplicate"* [EVIDENCE, §3.2.1]; and the released outcome means *"the message is no longer acquired by the receiver, and has been made available for (re-)delivery to the same or other targets receiving from the node"* [EVIDENCE, §3.4.4]. The re-delivery rule is explicit: *"To preserve 'exactly once' or 'at least once' delivery guarantees, the sender MUST resend the message"* [EVIDENCE, §3.4.6].

The second primary source is the formal statement that agreement across independent processes is a consensus problem: Jim Gray and Leslie Lamport, *"Consensus on Transaction Commit"*, ACM Transactions on Database Systems 31(1):133–160, March 2006 — https://lamport.azurewebsites.net/video/consensus-on-transaction-commit.pdf — whose abstract reads: *"The distributed transaction commit problem requires reaching agreement on whether a transaction is committed or aborted. The classic Two-Phase Commit protocol blocks if the coordinator fails."* [EVIDENCE]. And Fischer, Lynch and Paterson, *"Impossibility of Distributed Consensus with One Faulty Process"*, JACM 32(2):227–246, 1985 — https://www.cs.cornell.edu/courses/cs614/2004sp/papers/FLP85.pdf — *"every protocol for this problem has the possibility of nontermination, even with only one faulty process"* [EVIDENCE, abstract].

**What it guarantees.** The *logical* effect happens once, provided the receiver's dedup record and the effect are committed together **or** the effect is addressed by a request-id-derived identity.

**What it does NOT guarantee.** Exactly-once *execution*, exactly-once *delivery*, or any ordering between two different request ids. Note the direction of the argument: the impossibility result is about *agreement* (both sides agreeing on a value), and a task dispatch needs no agreement — it needs **one-way** delivery plus a receiver that is idempotent. That asymmetry is what makes the design possible without consensus [EVIDENCE + reasoning].

**MATRIX adaptation.** Rule: *every bridged effect must be addressed by an identity derived from the request id, and the store must converge on a repeated write of that identity.* Chain A's first direction already does this (claim id from request id, `INSERT OR REPLACE` → converges) [F20, F21]; the task direction must impose the same discipline on chain B's brain writes.

### 4.4 Idempotency keys: key + fingerprint, conflict, replay

**What it is.** A client-generated key names one logical request; a server-generated fingerprint (a digest of the request payload) tells a *retry* apart from a *conflict*.

**Primary source.** Jayadeba Jena, Sanjay Dalal and Dmitrii Tishkin, *"The Idempotency-Key HTTP Header Field"*, IETF Internet-Draft `draft-ietf-httpapi-idempotency-key-header-08`, 22 October 2026 — https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/. The normative parts:

- *"The idempotency key MUST be unique and MUST NOT be reused with another request with a different request payload."* (§2.2) A fingerprint is *"generated from request payload data by the resource"*, and may be *"Checksum of the entire request payload"*. (§2.4) [EVIDENCE]
- Enforcement: first time → process normally; duplicate (key and fingerprint seen) → *"respond with a response consistent with the request being completed according to the user's intent"*; **concurrent** (retried before the original completed) → *"SHOULD respond with a resource conflict error"*. (§2.6) [EVIDENCE]
- Errors: key reused with a different payload → **HTTP 422 Unprocessable Content**; retry while the original is outstanding → **HTTP 409 Conflict**. (§2.7) Expiry: *"The resource SHOULD define such expiration policy."* (§2.3) [EVIDENCE]

**What it guarantees.** A retry of the same logical request is answered from the recorded outcome; a re-use of a key with a different payload is refused rather than silently applied.

**What it does NOT guarantee.** It does not make the *effect* atomic with the key record — that is §4.5 — and it does not tell the resource what to do when the client dies before receiving the answer.

**MATRIX adaptation.** Three changes to the current code, all local:

1. **Widen the fingerprint.** `BridgeEnvelope.payloadDigest()` [F10] must become a digest over the *logical request*: `protocol`, `version`, `senderId`, `kind`, `payload`. It must **exclude** `sentAt` — transport metadata that legitimately differs between two deliveries of one logical request, so a fingerprint over it would turn every retry into a false conflict — and **include** `kind`, because the kind is the capability right [F27] and a change of kind under one request id is a security-relevant conflict, not a retry.
2. **Persist the effect.** The `bridge_request` ledger must gain the answer so that the replay of a completed request returns the same decision **and** the same outcome [F14]. This is the "replay of a completed result" requirement and it is currently unimplemented on chain A.
3. **Add the 409 case.** A request id that is `CLAIMED` by a live owner is *concurrent*, not *duplicate* [source §2.6]. MATRIX has no refusal reason for it; one is needed (`REQUEST_IN_PROGRESS`). The reasons live in `io.matrix.contract.bridge.BridgeRefusalReason`, a shared contract class, so adding one does not break the isolation gate.

### 4.5 Crash consistency: what must be atomic, and what to do when it cannot be

**What it is.** At every point of the workflow there is a set of facts that must become true together or not at all, and a recovery procedure for the boundary where the process died.

**Primary sources.**

- SQLite's own account of atomic commit: *"Atomic commit means that either all database changes within a single transaction occur or none of them occur… SQLite has the important property that transactions appear to be atomic even if the transaction is interrupted by an operating system crash or power failure."* — SQLite, *"Atomic Commit In SQLite"*, https://www.sqlite.org/atomiccommit.html [EVIDENCE].
- The alternative to a distributed transaction when the participants cannot commit together: Hector Garcia-Molina and Kenneth Salem, *"Sagas"*, SIGMOD 1987 (ACM TODS 12(3):106–120), https://doi.org/10.1145/38713.38742, full text at http://www.cs.cornell.edu/andru/cs711/2002fa/reading/sagas.pdf — *"either all the transactions in a saga are successfully completed or compensating transactions are run to amend a partial execution"* [EVIDENCE, abstract].
- The end-to-end argument for putting the correctness check at the application layer, where the effect actually is: Jerome H. Saltzer, David P. Reed and David D. Clark, *"End-to-End Arguments in System Design"*, ACM TOCS 6(4):277–288, 1984, https://web.mit.edu/saltzer/www/publications/endtoend/endtoend.pdf — its own examples include *"duplicate message suppression"* and *"recovery from system crashes"*, and it concludes that *"Low level mechanisms to support these functions are justified only as performance enhancements"* [EVIDENCE, abstract and §1].

**What it guarantees (per boundary).** The effect and its dedup record, when they are in one transaction, are all-or-nothing under crash.

**What it does NOT guarantee.** Where the effect and the outcome live in two different databases, **no mechanism can make them atomic** — not SQLite, not a protocol. The correct response is not to pretend but to (a) make the effect's identity request-derived so that repetition converges, and (b) make the uncertainty **representable** as a state, which MATRIX already has: `INDETERMINATE` [F17].

**MATRIX adaptation.** Reuse the existing vocabulary instead of inventing a parallel one. MATRIX's cognitive store already models exactly this problem — `RequestExecutionState` with `FAILED_NO_EFFECT` / `INTERRUPTED_NO_EFFECT` (re-executable) versus `INTERRUPTED_PARTIAL` / `INDETERMINATE` (not re-executable) [F17], and `RequestCheckpoint` whose first irreversible rung is `OBSERVATION_COMMITTED` [F18]. The recommendation is to mirror those two enums into the shared contract package as `io.matrix.contract.bridge.BridgeExecutionState` and `io.matrix.contract.bridge.BridgeCheckpoint` (enums only — the contract package must stay free of SQL and of store access, gate rule G7, `W51-ARCHITECTURE-MAP.md:171`), and to have both chains use them.

### 4.6 Correlation, deadlines, cancellation

**What it is.** A correlation id names the conversation; a deadline bounds how long anyone may wait; a cancellation is a *request* to stop, not a guarantee that the work stopped.

**Primary sources.**

- gRPC, *"Deadlines"* — https://grpc.io/docs/guides/deadlines/ — *"A deadline is used to specify a point in time past which a client is unwilling to wait for a response from a server"*; it *"can be converted to a timeout by adding the timeout to the current time when the application starts a call"*; and on propagation gRPC *"converts the deadline to a timeout from which the already elapsed time is already deducted… This shields your system from any clock skew issues"* [EVIDENCE].
- gRPC, *"Cancellation"* — https://grpc.io/docs/guides/cancellation/ — *"When an RPC is cancelled, the server **should** stop any ongoing computation"*, and *"the server handler must periodically check if the RPC it is servicing has been cancelled"*. Note the modality: cancellation is best-effort and cooperative, and the server is the one that decides [EVIDENCE]. Correlation is a first-class wire field in the messaging standard: AMQP 1.0 Part 3 defines `correlation-id` as *"a client-specific id that can be used to mark or identify messages between clients"* [EVIDENCE, §3.2.3].

**What it guarantees.** A deadline bounds the caller's patience; a propagated deadline lets the far side give up too; correlation lets the answer be matched to the request even when several are in flight.

**What it does NOT guarantee.** That a cancelled remote computation stopped. That the remote result did not arrive after the caller gave up. Any system that promises otherwise is lying about the one case that matters.

**MATRIX adaptation.** The envelope must carry, in a **version 3** of the protocol (F8 — a silent change of meaning is forbidden): `deadline_at` (absolute), `deadline_remaining_millis`, `hop` and `criticality`. The receiver clamps: `effective_deadline = min(deadline_at, now + deadline_remaining_millis)` — the gRPC rule, which is clock-skew-safe even though both chains are on one host today.

The four cancellation cases, and what each one means:

| when the cancellation arrives | what the receiver does | what is durable | what the caller is told |
|---|---|---|---|
| **before execution** | the inbox row moves to `FAILED_NO_EFFECT` / `CANCELLED`; nothing is executed | the cancellation, in the inbox row | `CANCELLED` |
| **during a cancellable computation** | cooperative flag; the computation checks it at a checkpoint and stops; the row moves to `CANCELLED` **only if** the effect has not been committed | the cancellation, in the inbox row | `CANCELLED`, or `COMPLETED` if the effect won the race |
| **after the effect committed** | **refused** — the effect is durable and cannot be un-done; only the *wait* can be ended | the effect and its answer | `COMPLETED` (late) |
| **caller timeout, remote may still complete** | nothing local; the remote continues | chain A's row is `DISPATCH_UNKNOWN` | **`UNKNOWN` — never "failed"** |

### 4.7 Bounded work: capacity, retry budget, hop count, backpressure

**What it is.** Every unbounded thing in a distributed system becomes a queue that grows until the process dies. The fix is to make each of them finite and declared.

**Primary sources.** The Google SRE book, *"Handling Overload"* (2018) — https://sre.google/sre-book/handling-overload/ — defines four **criticality** classes (`CRITICAL_PLUS`, `CRITICAL`, `SHEDDABLE_PLUS`, `SHEDDABLE`) and the rule *"When a task is itself overloaded, it will reject requests of lower criticalities sooner"*; it also defines two retry budgets: *"a per-request retry budget of up to three attempts"* and *"a per-client retry budget… A request will only be retried as long as this ratio is below 10%"* [EVIDENCE]. And *"Addressing Cascading Failures"* (2018) — https://sre.google/sre-book/addressing-cascading-failures/ — *"Consider having a server-wide retry budget… avoid amplifying retries by issuing retries at multiple levels: a single request at the highest layer may produce a number of attempts as large as the product of the number of attempts at each layer"* [EVIDENCE] — directly actionable for MATRIX, whose chain A already retries at the publisher level [F25]: a retry inside a retry multiplies.

**MATRIX adaptation.**

- **Queue capacity.** The pending set of `cognitive_task_dispatch` is bounded by `max_pending_dispatch`. When full, the **lowest-criticality, oldest** pending task is shed and its row is closed as `DISPATCH_SHED`. Shedding is recorded, never silent.
- **Retry budget.** Per request: `max_attempts` (default 3, matching `RetryPolicy.STANDARD` [F25]). Per process: a **retry budget** (the SRE server-wide idea) — the relay's total retries per minute are capped; when the cap is hit, the relay stops retrying and the row is closed as `DISPATCH_UNKNOWN` rather than retried forever.
- **Hop count.** `hop` starts at 0 and is incremented on every crossing. Chain B refuses a task whose `hop` is at or above `max_hops` (default 1: chain B never calls back across the bridge for the same task). Without this field there is no structural defence against a bridge loop — the protocol has no such field today [F8, F27].
- **Backpressure.** Chain B applies `BridgeBudget` **before** the task touches the brain [F24] — that part is already right. What is missing is a durable budget: the counters are process-local and reset on restart, so a crash-restart loop re-opens the day's window. The per-day ceiling must be persisted.
- **Criticality.** The dispatch carries a criticality class; the receiver sheds by class, not by arrival order.

### 4.8 What SQLite's own model imposes

**Primary sources (all SQLite, all verified):**

| the constraint | the statement | source |
|---|---|---|
| One writer | *"since there is only one WAL file, there can only be one writer at a time"*; *"SQLite supports multiple simultaneous read transactions… but only one simultaneous write transaction"*. With WAL + `synchronous=FULL` the WAL is synced on every commit; with `synchronous=NORMAL`, *"transactions are no longer durable and might rollback following a power failure"* | https://www.sqlite.org/wal.html §2.2, §2.3; https://www.sqlite.org/lang_transaction.html §2.1 |
| `SQLITE_BUSY` | *"Only one process at a time can hold a RESERVED lock… If the process that wants to write is unable to obtain a RESERVED lock… the write attempt fails and returns SQLITE_BUSY."* | https://www.sqlite.org/lockingv3.html §5.0 |
| The busy timeout | *"This routine sets a busy handler that sleeps for a specified amount of time when a table is locked… After at least 'ms' milliseconds of sleeping, the handler returns 0 which causes sqlite3_step() to return SQLITE_BUSY."* | https://www.sqlite.org/c3ref/busy_timeout.html |
| `IMMEDIATE` transactions | *"IMMEDIATE causes the database connection to start a new write immediately… The BEGIN IMMEDIATE might fail with SQLITE_BUSY if another write transaction is already active on another database connection."* | https://www.sqlite.org/lang_transaction.html §2.2 |
| **No cross-database atomicity** | *"Transactions that involve changes against multiple ATTACHed databases are atomic for each individual database, but are **not atomic across all databases as a set**."* | https://www.sqlite.org/wal.html §1 |
| WAL needs one host | *"All processes using a database must be on the same host computer; WAL does not work over a network filesystem."* | https://www.sqlite.org/wal.html §1 |

**What this settles for MATRIX.**

1. **MATRIX's "no cross-database transaction" is not a policy — it is a property of SQLite.** Even `ATTACH`ing chain B's file to chain A's connection would not give atomicity across the two files [EVIDENCE, wal.html §1]. No SQLite configuration can rescue a cross-database two-phase commit; the design must be crash-consistent rather than atomic.
2. **The single-writer rule is enforced by the engine, not by convention.** Two writers on one file serialise; the loser waits (if `busy_timeout` is set) or gets `SQLITE_BUSY` (if it is not). `BridgeJournal` sets neither [F15], so it is one of the files in MATRIX where a concurrent write fails instantly instead of waiting. `IMMEDIATE` is the right mode and MATRIX already reasons about why [F16]; the same reasoning applies to a new bridge-side store.
4. **A WAL file that is separated from its database "might be lost, or the database file might become corrupted"** [wal.html §4]. The bridge journal must therefore be treated as a first-class durable artefact, with the same backup discipline as the epistemic store.
5. **The WAL-reset bug.** SQLite documents a corruption bug affecting **3.7.0 through 3.51.2**, fixed in 3.51.3 (2026-03-13), which triggers *"when there are two or more database connections open on the same file… and… those two connections attempt to write or checkpoint at the same instant"* [EVIDENCE, https://www.sqlite.org/wal.html §11]. MATRIX bundles SQLite **3.43.0** (via `sqlite-jdbc` 3.43.0.0 [F26]; the release note reads *"upgrade to sqlite 3.43.0"* [EVIDENCE, https://github.com/xerial/sqlite-jdbc/releases/tag/3.43.0.0]) and it opens **two** connections to `data/knowledge/workbench.sqlite` (`CognitiveStore` and `KnowledgeStore`, F16, `W51-ARCHITECTURE-MAP.md:215-216`). **[HYPOTHESIS]** MATRIX is therefore inside the affected version range and the affected usage shape. The bug needs *"an unusual usage pattern"* and the SQLite developers *"were unable to reproduce it organically"*, so this is a **dependency-upgrade risk to record, not an incident**: any durable-execution work should pin a SQLite ≥ 3.51.3, which is a dependency upgrade and therefore an owner decision under `AGENTS.md`.

---
## 5. Rejected or deferred alternatives

| # | the alternative | why it is rejected or deferred |
|---|---|---|
| **A1** | **Two-phase commit between the two databases.** | Rejected. (i) SQLite cannot do it: cross-`ATTACH`ed atomicity does not exist [wal.html §1]. (ii) It would require both databases to be writable by a coordinator process, which violates the one-writer-per-file rule that the W45.1 recovery logic depends on [F17, `CognitiveStore.java:75-92`]. (iii) 2PC *blocks* when the coordinator fails [Gray & Lamport 2006], and consensus-based alternatives do not exist under one fault [FLP 1985]. A blocked coordinator inside a cognitive loop is a system that stops thinking. |
| **A2** | **Keep the in-memory dedup map and add a "restart is rare" assumption.** | Rejected. It is exactly the defect W52 named [F6]. It is also the *only* mechanism that determines whether a duplicated delivery is observable, and the interface was written with the durability seam precisely so that this assumption would not have to be made [F11]. The end-to-end argument says the correctness check belongs at the effect, not at a cheap in-memory layer [Saltzer/Reed/Clark 1984]. |
| **A3** | **Make the remote effect naturally idempotent (content-addressed) and rely on that alone.** | Rejected as a *complete* answer; retained as a necessary second line of defence. Content addressing makes the *state* converge (chain A's first direction already does: `INSERT OR REPLACE INTO claim` [F21]), but it does not make the *answer* recoverable, it does not stop the duplicated CPU work, and it cannot express "the effect happened but the answer was lost" — which is exactly the `INDETERMINATE` state MATRIX already has [F17]. |
| **A4** | **A new bridge direction (`RESULT`) so chain B pushes the answer back asynchronously.** | Deferred. It needs a new capability right and a protocol version 3 [F8], plus a chain-B outbox — real machinery. But the **pull** design (chain A re-dispatches with the same request id and chain B replays its stored answer) achieves the same reconciliation with no new direction, because the answer is already stored in the inbox row. Revisit when a task is legitimately longer than the HTTP timeout, or when chain A cannot re-dispatch because the task specification has changed. |
| **A5** | **Let chain A's HTTP call block until chain B answers; no relay, no outbox.** | Rejected. It makes the caller's latency the durability boundary: a chain-A crash mid-call loses the task unless chain A had already committed the dispatch, and a caller timeout leaves the remote running with no local record [F25 shows a 5 s timeout against an unknown-length task]. The outbox is what makes the task survive chain A's own death. |
| **A6** | **Put chain B's inbox in chain A's `data/bridge/bridge.sqlite` (one shared bridge ledger for both directions).** | Rejected. That file is chain A's; giving chain B a writer on it would create a second writer on a chain-A file and would violate gate rules G2/G8 [F3, F4]. Chain B's inbox belongs to chain B, in chain B's own file. |
| **A7** | **Upgrade the SQLite driver to ≥ 3.51.3 as part of this work.** | Deferred to the owner. It is a dependency upgrade (major-ish), which `AGENTS.md` places behind an explicit RFC. It is recorded as a risk in §4.8 and as an open question in §8. |

---
## 6. The recommendation for MATRIX

### 6.1 The shape of the protocol, in one paragraph

Chain A commits the *intent* to run a task **in chain A's own database**, in one transaction, together with whatever chain-A state the task depends on. A relay inside chain A later picks that row up and POSTs the signed envelope to chain B. Chain B, in **one transaction in chain B's own database**, writes (a) its inbox row for the request id and (b) the effect itself, addressed by an identity derived from the request id, and commits them together. Chain B answers with the stored answer. Chain A, in **one transaction in chain A's own database**, writes the answer, the observation it implies, and the transition of its dispatch row to `COMPLETED`. Every message may be delivered many times; every effect converges to one; every crash leaves a row that says exactly what happened and what may be done next.

### 6.2 The durable state machine

**Chain A — `cognitive_task_dispatch` (in `data/knowledge/workbench.sqlite`, written by `CognitiveStore`, the single writer).**

| state | meaning | who moves it | when |
|---|---|---|---|
| `DISPATCH_PENDING` | committed, not yet sent | chain A's relay | the row is created `PENDING` in the caller's transaction |
| `DISPATCH_IN_FLIGHT` | sent; the relay holds a lease | the relay | before the POST; `lease_until` set |
| `DISPATCH_PENDING` (again) | the lease expired; the POST may or may not have landed | the relay or a sweeper | `lease_until` passed |
| `DISPATCH_COMPLETED` | the answer is committed **and** the observation is committed | chain A's writer | one transaction with the observation |
| `DISPATCH_REFUSED` | chain B refused before any effect | chain A's writer | a refusal arrives (F23 guarantees no effect) |
| `DISPATCH_FAILED_NO_EFFECT` | the dispatch itself failed and nothing happened remotely | chain A's writer | a transport failure with no delivery |
| `DISPATCH_UNKNOWN` | the deadline passed; the remote may still complete | chain A's writer | `deadline_at` passed |
| `DISPATCH_SHED` | dropped by backpressure | chain A's writer | the pending set was full and this was the lowest criticality |
| `DISPATCH_INDETERMINATE` | the remote may have committed the effect and the answer was lost | chain A's writer | a reconciliation attempt found nothing |

**Chain B — `bridge_inbox` (in `data/mind/mind.sqlite`, written by chain B's single writer).**

| state | meaning | who moves it | re-executable? |
|---|---|---|---|
| `CLAIMED` | the request id is claimed; the effect is not committed | chain B's writer | only if the owner is provably dead |
| `EXECUTED` | the effect is committed and the answer is stored | chain B's writer | no — replayable from the stored answer |
| `FAILED_NO_EFFECT` | the task failed before any effect | chain B's writer | **yes** |
| `CANCELLED` | cancelled before the effect | chain B's writer | no — the answer is "cancelled" |
| `INDETERMINATE` | the effect may have happened; the answer was not persisted | chain B's writer | **no** |

These are MATRIX's existing `RequestExecutionState` values re-used (`RequestExecutionState.java:30-75`): `CLAIMED`, `COMPLETED`↔`EXECUTED`, `FAILED_NO_EFFECT`, `INTERRUPTED_NO_EFFECT`↔`CLAIMED` with a dead owner, `INDETERMINATE` [F17]. **The point of re-using them is that MATRIX already has a proven vocabulary for "the effect may have happened and the answer was lost", and a second vocabulary would hide the distinction rather than make it.**

**Schema — chain A, `cognitive_task_dispatch`:**

```sql
CREATE TABLE IF NOT EXISTS cognitive_task_dispatch (
    request_id        TEXT PRIMARY KEY,   -- the idempotency key; the signed id
    fingerprint       TEXT NOT NULL,      -- digest over protocol|version|senderId|kind|payload
    task_spec         TEXT NOT NULL,      -- the canonical task description
    effect_id         TEXT NOT NULL,      -- the identity the remote effect must be written under
    state             TEXT NOT NULL,      -- the states of §6.2
    hop               INTEGER NOT NULL DEFAULT 0,
    criticality       TEXT NOT NULL,      -- CRITICAL_PLUS | CRITICAL | SHEDDABLE_PLUS | SHEDDABLE
    deadline_at       TEXT NOT NULL,      -- absolute instant, ISO-8601
    budget_millis     INTEGER NOT NULL,
    attempts          INTEGER NOT NULL DEFAULT 0,
    lease_until       TEXT,               -- the relay's crash-safe claim
    result_digest     TEXT,               -- digest of the answer
    outcome           TEXT,               -- the durable JSON of the canonical Outcome
    owner_token       TEXT,               -- OwnershipToken of the claiming writer
    owner_pid         INTEGER, failure TEXT, recovery TEXT,
    created_at        TEXT NOT NULL, updated_at TEXT NOT NULL
);
```

**Schema — chain B, `bridge_inbox`:**

```sql
CREATE TABLE IF NOT EXISTS bridge_inbox (
    request_id        TEXT PRIMARY KEY,
    fingerprint       TEXT NOT NULL,
    effect_id         TEXT NOT NULL,
    state             TEXT NOT NULL,      -- CLAIMED | EXECUTED | FAILED_NO_EFFECT | CANCELLED | INDETERMINATE
    answer            TEXT NOT NULL,      -- the durable canonical answer, replayed byte-for-byte
    answer_digest     TEXT NOT NULL,
    attempts          INTEGER NOT NULL DEFAULT 0,
    owner_token       TEXT, owner_pid INTEGER, failure TEXT, recovery TEXT,
    created_at        TEXT NOT NULL, completed_at TEXT
);
```

**Schema — chain A, the existing `bridge_request` ledger, extended (fixes D2):**

```sql
ALTER TABLE bridge_request ADD COLUMN effect_id TEXT;  -- the identity the effect was written under
ALTER TABLE bridge_request ADD COLUMN outcome   TEXT;  -- the durable answer, so a replay returns it
-- the fingerprint column already exists as `payload_digest`; what changes is WHAT it digests (R2).
```

### 6.3 The one connection chain B must share

The chain-B inbox row and the chain-B effect must be committed **in one transaction**, or boundary **B7** exists and the effect can be duplicated [§4.5]. Today chain B's two relevant writers are separate components with separate connections: `io.matrix.memory.SqliteMemoryBackend` (which owns the `memory` table of `data/mind/mind.sqlite`, F28) and the bridge receiver that would own `bridge_inbox`. **Two JDBC connections to one SQLite file do not share a transaction** — SQLite serialises the writers, it does not merge their transactions [wal.html §2.2; lang_transaction.html §2.1].

Three ways out, in order of preference:

1. **One owner.** Make the inbox the *same component* that writes the effect — give the bridge a transaction-scoped write path into the memory backend, so that `INSERT INTO bridge_inbox` and `INSERT OR REPLACE INTO memory` are one `BEGIN IMMEDIATE … COMMIT`. This is the only variant that makes the inbox and the effect atomic by construction.
2. **One connection, two statements.** Two components share one `Connection`, and therefore one transaction; they must then agree on who may use the connection when. This is the pattern chain A already uses with two stores over one file, **but without the shared transaction** — `CognitiveStore` and `KnowledgeStore` open the same file with two connections and rely on SQLite serialising them (`CognitiveStore.java:169-177`). That precedent does not deliver atomicity and must not be copied as if it did.
3. **Re-home the effect.** Put the inbox table *in the same store* as the effect and give the effect's writer the inbox responsibility — for the brain's memory, `bridge_inbox` lives in `data/mind/mind.sqlite` and is written by `SqliteMemoryBackend`'s owner.

**[HYPOTHESIS]** Option 1 is the correct one and it is a small change: `SqliteMemoryBackend` already holds a single `Connection` field and already writes with `INSERT OR REPLACE INTO memory`; giving it a `storeIfAbsent(requestId, effectId, entry)` method that writes the inbox row and the memory row in one transaction is a contained change to one class. It must be verified, not assumed — the class currently uses autocommit and holds no transaction discipline of its own (F28).

### 6.3.1 The same three points applied to the implementation now in the tree

A chain-B task inbox is being written in parallel to this document, and this track read it rather than guessing at it. Three of the findings above apply to it directly and are stated here so that they are not lost:

- **It opens its own connection, with none of the PRAGMA discipline.** `io.matrix.api.bridge.RemoteTaskStore` opens a plain `jdbc:sqlite:` connection in autocommit (`RemoteTaskStore.java:118-133`), so the new chain-B file has the same gap as `BridgeJournal` and `SqliteMemoryBackend` [F15, F28, defect **D4**]. The fix is one line of configuration copied from `CognitiveStore.configure()` (`CognitiveStore.java:180-186`).
- **The effect flag and the effect are not committed together.** The inbox row carries an explicit `effect_committed` flag (`RemoteTaskStore.java:151`) that is written by its own `UPDATE` (`RemoteTaskStore.java:247-248`), separately from the effect itself. That is exactly boundary **B7**: a crash between the effect and the flag leaves the flag false, and the next delivery re-executes. The flag is a *claim* about the effect, not the effect's own transaction — it narrows the window but does not close it. Closing it means the effect and the flag are written by one transaction, which is what option 1 above means.
- **The durable registry is optional at construction.** `io.matrix.api.bridge.TaskBridge` falls back to `new InMemoryBridgeIdempotency()` when no store is supplied (`TaskBridge.java:110-111`), so the W52 defect (**D1**) survives as a configuration-dependent behaviour: a deployment that forgets the inbox file silently gets the non-durable one. The fallback is the right default for a test, but it must not be reachable in a deployment, and the fallback's presence must be visible on the status route the way `GET /v1/bridge/status` already reports `"configured": false` (`BridgeResource.java:115`).

### 6.4 The idempotency rules

- **R1 — the key is the request id, and it is signed.** It is inside `canonicalBytes()` [F9], so it cannot be rewritten in flight.
- **R2 — the fingerprint is over the logical request**: `protocol | version | senderId | kind | payload`. `sentAt` is excluded (it is transport metadata and legitimately differs between deliveries of one logical request). `kind` is included (it is the capability right). This replaces `payloadDigest()` [F10] and closes defect **D3**.
- **R3 — same key, same fingerprint → replay.** Return the recorded decision **and** the recorded outcome; apply no effect. Requires the ledger to persist the outcome [F14] — the fix for **D2**.
- **R4 — same key, different fingerprint → refuse**, with the reason the contract names (`REQUEST_ID_CONFLICT`), audited, and **not** registered as the outcome so the original stays the answer. This is the IETF draft's 422 case [§2.7]. MATRIX already implements the shape [F23 / `BridgeReceiver.java:229-240`].
- **R5 — same key, same fingerprint, but the key is `CLAIMED` by a live owner → refuse as concurrent** (the IETF draft's 409 case, §2.6); MATRIX has no refusal reason for this today.
- **R6 — a refusal implies no effect.** Every refusal in the receiver is taken before the sink is reached [F23], so `DISPATCH_REFUSED` is a truthful statement.
- **R7 — the key is allocated, never reused.** Chain A allocates the request id from the durable, non-resetting `cognitive_sequence` (`CognitiveStore.java:66-69`), and the fingerprint binding prevents reuse for a different task.
- **R8 — the ledger has a retention policy, declared.** The IETF draft requires one [§2.3]. Until the effect's own retention is known, the rule is: *a ledger row lives at least as long as the effect it names.*
- **R9 — the claim is atomic.** Chain A's claim must use the one-statement form MATRIX already has (`INSERT … ON CONFLICT … DO UPDATE … WHERE fingerprint = … AND state IN (…)`, `CognitiveStore.java:1616-1642`), not the current find-then-act (`BridgeJournal.java:112-134`). Chain B's inbox claim must use the same shape.

### 6.5 The crash-boundary table

| boundary | what is durable at that point | what recovery does | what the caller observes |
|---|---|---|---|
| **B1** — chain A committed the dispatch row; nothing sent | the `DISPATCH_PENDING` row, in chain A's DB, one transaction | the relay picks it up after a restart; no effect exists anywhere | "pending" |
| **B2** — the relay sent; chain B has not committed anything | chain A: `DISPATCH_IN_FLIGHT` + `attempts` + `lease_until`; chain B: nothing | the lease expires; the relay re-sends the **same** request id and the **same** fingerprint | "pending" / "in flight" |
| **B3** — chain B committed the effect; the answer did not reach chain A | chain B: the inbox row `EXECUTED` **and** the effect, in one transaction in chain B's DB | chain A re-sends; chain B finds the inbox row and **replays the stored answer**; no second effect | "pending" |
| **B4** — chain A received the answer; chain A died before committing it | chain A: the row is still `DISPATCH_IN_FLIGHT`; the answer exists only in chain B | chain A re-sends; chain B replays; chain A commits the answer **and** the observation in one transaction; the observation's identity is request-derived, so the commit converges [F20, F21] | "pending", then the answer |
| **B5** — chain A committed `DISPATCH_COMPLETED`; chain A's own caller never got the reply | chain A: the row with `result_digest` and `outcome` | the caller's retry, carrying the same request id, is answered from the row — the idempotency rule applied one level up | the original answer |
| **B6** — chain B died during execution, before the effect | chain B: the inbox row `CLAIMED` with a dead owner (the `OwnershipToken` liveness rule MATRIX already uses, `CognitiveStore.java:75-92`) | the row is read as `INTERRUPTED_NO_EFFECT` → **re-executable**; the same request id is re-run | "pending" |
| **B7** — chain B committed the effect but the inbox row was **not** committed with it | the effect exists; the inbox row is absent or `CLAIMED` | **This is the one boundary the design must make impossible**, by putting the inbox row and the effect in one transaction (B3). If it ever happens anyway, the second line of defence is the request-derived effect identity, which makes the repeat converge instead of duplicate [F20, F21] — and the honest state is `INDETERMINATE`, never a silent second effect | "pending" → possibly `UNKNOWN` |
| **B8** — the deadline passed; the remote may still complete | chain A: `DISPATCH_UNKNOWN`; chain B: the effect and the answer may exist | chain A re-dispatches with the same request id (a reconciliation pull, §5/A4) and picks up the answer; it is recorded as a **late** observation | **`UNKNOWN`** — explicitly, never "failed" |
| **B9** — chain B's budget refused the task | chain B: the refusal is registered as the outcome of the request id, so it is sticky | a redelivery replays the refusal; the task is not silently retried into a later window | `REFUSED` with the reason |

### 6.6 The guarantees MATRIX may claim, and the ones it may not

**Claimed.**

1. **No lost task.** A task chain A has accepted durably is either executed by chain B or is provably still pending. [outbox + inbox]
2. **Effectively-once logical effect.** The observable state that a task produces is produced once, because the effect is addressed by a request-derived identity and the inbox row and the effect commit together. [content-addressed effect + atomic inbox]
3. **At-least-once delivery.** Deliveries may repeat; the protocol is designed for that, not against it. [AMQP 1.0 §3.2.1, §3.4.6]
4. **A decidable outcome.** For every request id, both sides can answer "what happened?" from durable state alone, after any crash. [`RequestExecutionState` vocabulary]
5. **Honest uncertainty.** Where the outcome is unknown, the state says `UNKNOWN` / `INDETERMINATE`. [F17]
6. **Refusal implies no effect.** Every refusal precedes the sink. [F23]
7. **Boundedness.** Queue capacity, per-request attempt cap, per-process retry budget, hop cap, and a persisted daily ceiling are all declared. [SRE criticality + retry budget]
8. **Governance preserved.** The bridge remains the only crossing, the right still names the direction, the signature still covers the request id, and the audit still records every call and every refusal. [F4, F9, `W52-STATUS.md:47-48`]

**NOT claimed.**

1. **NOT exactly-once execution, and NOT exactly-once delivery.** A computation may run twice; a message may be delivered many times.
2. **NOT atomicity across the two databases.** No mechanism can provide it here; SQLite itself says so [wal.html §1].
3. **NOT linearizability of the bridge.** The bridge is a governed crossing, not a consensus point. No agreement between the two chains is claimed, and none is required by this design.
4. **NOT that a cancelled remote computation stopped, and NOT that the remote result cannot arrive after the caller gave up.** Cancellation is cooperative and best-effort [gRPC cancellation]; the late arrival is exactly the `DISPATCH_UNKNOWN` case, and it is why the caller is told *unknown*.
7. **NOT that chain B's conclusion is true.** The bridge carries a report; the epistemic store decides what to do with it (`ADR-W51-01-cognitive-runtime-and-bridge.md:125-131`).
8. **NOT that MATRIX has general intelligence, human-like understanding, universal learning or reliable autonomous self-improvement** (`ADR-W51-01-cognitive-runtime-and-bridge.md:173-181`).

### 6.7 The named work this implies (for the coordinator to schedule, not for this track to perform)

| # | the change | the files |
|---|---|---|
| W1 | Widen the fingerprint to `protocol\|version\|senderId\|kind\|payload`; keep `sentAt` out | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeEnvelope.java:160-162` |
| W2 | Persist the outcome and the effect id in the bridge ledger; make the claim atomic; add WAL / `synchronous=FULL` / `busy_timeout` / `transaction_mode=IMMEDIATE` | `matrix-core/src/main/java/io/matrix/bridge/BridgeJournal.java:63-64`, `:77-87`, `:112-134` |
| W3 | Add the durable chain-B inbox; replace `InMemoryBridgeIdempotency` | **new** `matrix-brain-runtime/.../io/matrix/brain/runtime/bridge/BridgeInbox.java`; `matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java:104-110` |
| W4 | Add `cognitive_task_dispatch` and its four methods; add the relay | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java`; **new** `matrix-core/.../io/matrix/bridge/DispatchRelay.java` |
| W5 | Mirror the state and checkpoint enums into the contract package (enums only — G7 purity) | **new** `matrix-core/.../io/matrix/contract/bridge/BridgeExecutionState.java`, `.../BridgeCheckpoint.java` |
| W6 | Add the refusal reason for the concurrent case | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeRefusalReason.java` |
| W7 | Protocol version 3 with `deadline_at`, `deadline_remaining_millis`, `hop`, `criticality`; v1 and v2 stay readable | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeProtocol.java:33`, `:49`; `.../BridgeEnvelope.java:49-57` |
| W8 | Persist the daily budget ceiling | `matrix-core/src/main/java/io/matrix/contract/bridge/BridgeBudget.java:67-70` |
| W9 | Give chain B's memory store the PRAGMA discipline and a transaction-scoped write so the inbox row and the effect commit together (§6.3) | `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:41`, `:47-52` |

---
## 7. Open questions for the coordinator

The named work is in §6.7; the coordinator decides the wave, the ordering and whether the whole of §6.7 is in scope.

1. **Which task shapes are in scope?** The design assumes a task whose effect can be addressed by an identity derived from the request id. If any in-scope task is a genuine side effect on the world (not a store write), "effectively-once" must be weakened for it and the `INDETERMINATE` path becomes the normal path, not the exceptional one. **This is the single most important question in the document.**
2. **The pull or the push, and version 3 now or later?** §5/A4 defers the asynchronous `RESULT` direction: is re-dispatch-as-reconciliation sufficient for the wave's real task shapes, or is a task expected to outlive the HTTP timeout? And W7 changes the protocol — deferring it keeps v2 stable and leaves the deadline and the hop count unimplemented, which leaves the bridge without a loop guard.
4. **Is the bridge journal a separate file, or should it be folded into the epistemic store?** It is currently a third file with its own writer [F3]. Folding it into `data/knowledge/workbench.sqlite` would give it WAL and the busy timeout for free, but would put a second logical component into chain A's epistemic file. Both answers are defensible; the recommendation here is to keep it separate **and** give it the same PRAGMA discipline (W2).
5. **The SQLite version.** §4.8 records that the bundled SQLite 3.43.0 is inside the range of the WAL-reset bug and that the fix is in 3.51.3. Upgrading the driver is a dependency upgrade and therefore an owner decision under `AGENTS.md`. Should this be raised as an RFC?
6. **Does the dispatch row belong in `CognitiveStore` or in a store of its own?** The recommendation puts it in `CognitiveStore`, which already owns the one-writer guard, the durable sequence and the request-lifecycle vocabulary [F17, F19]. The counter-argument: a dispatch row is not a cognitive record, and the cognitive file is already the repository's busiest.
7. **Who owns the relay's liveness?** The relay claims rows with a `lease_until`. Inside chain A a restart loses the lease, which is fine — the row is still `PENDING`. If the relay is ever a separate process, it needs its own claim protocol against the single writer.

---
## 8. The sources

All are primary: official specifications, official documentation of the implementation in use, or original papers. No Wikipedia, no aggregator, no blog post about the subject.

1. SQLite, *"Write-Ahead Logging"*, https://www.sqlite.org/wal.html — §1 (cross-database atomicity; one writer; same-host requirement), §2.2 (concurrency), §2.3 (`synchronous`), §3 (activating WAL), §4 (the WAL file), §11 (the WAL-reset bug).
2. SQLite, *"File Locking And Concurrency In SQLite Version 3"*, https://www.sqlite.org/lockingv3.html — §3.0 (the five locking states), §5.0 (the `RESERVED` lock; `SQLITE_BUSY`).
3. SQLite, *"Transaction"*, https://www.sqlite.org/lang_transaction.html — §2.1 (read versus write transactions), §2.2 (`DEFERRED` / `IMMEDIATE` / `EXCLUSIVE`), §2.3 (implicit versus explicit transactions).
4. SQLite, *"Atomic Commit In SQLite"*, https://www.sqlite.org/atomiccommit.html — §1 (atomic commit survives a crash or a power failure), §4.1 (what a power loss mid-commit leaves behind), §4.2 (hot journals).
5. SQLite, *"Set A Busy Timeout"* (`sqlite3_busy_timeout`), https://www.sqlite.org/c3ref/busy_timeout.html
6. SQLite, *"PRAGMA"* (the `journal_mode`, `synchronous`, `busy_timeout`, `locking_mode` pragmas), https://www.sqlite.org/pragma.html
7. Xerial, `sqlite-jdbc` **3.43.0.0** release notes (*"upgrade to sqlite 3.43.0"*), https://github.com/xerial/sqlite-jdbc/releases/tag/3.43.0.0
8. Xerial, `sqlite-jdbc` `SQLiteConfig.java` at tag 3.43.0.0 (the `transaction_mode`, `busy_timeout` and `journal_mode` pragmas; the driver's default `busy_timeout` of 3000 ms), https://github.com/xerial/sqlite-jdbc/blob/3.43.0.0/src/main/java/org/sqlite/SQLiteConfig.java
9. Jayadeba Jena, Sanjay Dalal, Dmitrii Tishkin, *"The Idempotency-Key HTTP Header Field"*, IETF Internet-Draft `draft-ietf-httpapi-idempotency-key-header-08`, 22 October 2026, https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ — §2.2 (uniqueness), §2.3 (expiry), §2.4 (fingerprint), §2.6 (enforcement; the concurrent case), §2.7 (422 and 409).
10. Roy Fielding, Mark Nottingham, Julian Reschke, *"HTTP Semantics"*, RFC 9110, IETF, June 2022, https://www.rfc-editor.org/rfc/rfc9110.html — §9.2.2 (idempotent methods), §10.2.3 (`Retry-After`), §15.6.1 (retry safety).
11. OASIS, *"Advanced Message Queuing Protocol (AMQP) Version 1.0, Part 3: Messaging Layer"*, OASIS Standard, 29 October 2012, https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html — §3.2.1 (`delivery-count`, `message-id`), §3.2.3 (`correlation-id`), §3.4.4 (the released outcome), §3.4.6 (resuming deliveries; the re-send rule).
12. Jim Gray, Leslie Lamport, *"Consensus on Transaction Commit"*, ACM Transactions on Database Systems 31(1):133–160, March 2006, https://lamport.azurewebsites.net/video/consensus-on-transaction-commit.pdf (DOI https://doi.org/10.1145/1132863.1132867).
13. Hector Garcia-Molina, Kenneth Salem, *"Sagas"*, Proceedings of the 1986 ACM SIGMOD Conference (ACM TODS 12(3):106–120, 1987), https://doi.org/10.1145/38713.38742 — full text at http://www.cs.cornell.edu/andru/cs711/2002fa/reading/sagas.pdf — the abstract (compensating transactions) and §1.
14. Michael J. Fischer, Nancy A. Lynch, Michael S. Paterson, *"Impossibility of Distributed Consensus with One Faulty Process"*, Journal of the ACM 32(2):227–246, April 1985, https://www.cs.cornell.edu/courses/cs614/2004sp/papers/FLP85.pdf
15. Jerome H. Saltzer, David P. Reed, David D. Clark, *"End-to-End Arguments in System Design"*, ACM Transactions on Computer Systems 6(4):277–288, October 1984, https://web.mit.edu/saltzer/www/publications/endtoend/endtoend.pdf — the abstract (duplicate message suppression; recovery from crashes) and §1.
16. Chris Richardson, *"Pattern: Transactional outbox"*, microservices.io, https://microservices.io/patterns/data/transactional-outbox.html — §Context, §Forces, §Solution, §Issues (the relay may publish more than once; the consumer must be idempotent).
17. Gregor Hohpe, Ian Woolf, *"Enterprise Integration Patterns: Designing, Building, and Deploying Messaging Solutions"*, Addison-Wesley, 2003, ISBN 978-0-321-20314-6, https://www.oreilly.com/library/view/enterprise-integration/032120314X/ — the *"Idempotent Receiver"* and the message-memento patterns.
18. gRPC, *"Deadlines"*, https://grpc.io/docs/guides/deadlines/ — deadline semantics, the timeout-to-deadline conversion, deadline propagation and clock-skew handling.
19. gRPC, *"Cancellation"*, https://grpc.io/docs/guides/cancellation/ — cancellation is cooperative and best-effort; the server must poll for it.
20. Bjarne Hoijer, Rob Ewaschuk, Google SRE Book, *"Handling Overload"*, 2018, https://sre.google/sre-book/handling-overload/ — criticality classes; the per-request and per-client retry budgets.
21. Bjarne Hoijer, Rob Ewaschuk, Google SRE Book, *"Addressing Cascading Failures"*, 2018, https://sre.google/sre-book/addressing-cascading-failures/ — randomized exponential backoff; the server-wide retry budget; the multiplication of retries across layers.

