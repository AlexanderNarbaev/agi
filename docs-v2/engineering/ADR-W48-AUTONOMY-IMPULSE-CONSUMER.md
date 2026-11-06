# ADR-W48 — An autonomy impulse consumer (PROPOSED)

**Wave:** W48 · **Task:** W48-11 · **Status:** **PROPOSED — NOT IMPLEMENTED, NOT APPROVED**
**Date:** 2026-11-06 · **Owner decision:** pending (see §9)

---

## 1. The question

`ImpulseScheduler.fire()` returns a `FireRecord`. Its `FIRED` outcome means the
scheduler **accepted** the impulse: it passed the FROZEN ethical gate and the
conjugate budget. It does **not** mean that any work was dispatched, consumed,
executed or completed.

The W48 directive asks for an ADR and an owner decision, not for an implementation.
**Nothing in this ADR is implemented.** The wave explicitly does not implement
unapproved autonomous behaviour.

## 2. The current evidence

| the fact | where | how it was checked |
|---|---|---|
| the scheduler returns a `FireRecord(impulse, outcome, envelopeSpent, rowsAllocated)` and can return `FIRED`, `REJECTED_BUDGET`, `REJECTED_ETHICS`, `REJECTED_UNKNOWN` | `matrix-core/src/main/java/io/matrix/lifecycle/ImpulseScheduler.java:22-41` | read |
| the only consumer of a `FireRecord` in the whole tree is a **log line** — a refusal is logged, and a `FIRED` record is **dropped** | `matrix-core/src/main/java/io/matrix/autonomy/AutonomyEngine.java:140-146` | read |
| there are exactly **two** call sites of `scheduler.fire(...)` in production code: `AutonomyEngine` (three drives) and `SelfImprovingEngine` (one) | `grep -rn "scheduler.fire\|\.fire(" --include=*.java matrix-core/src/main/java` | run |
| **no consumer, proposal or queue class exists** — the concept is not implemented under any name | `grep -rln "ImpulseConsumer\|ProposalQueue\|TaskProposal" --include=*.java .` → **no match** | run |
| the impulses are a closed enum of four: `CURIOSITY`, `CONSOLIDATION`, `INTEGRITY_CHECK`, `SHARE_DIGEST` | `matrix-core/src/main/java/io/matrix/lifecycle/AutonomyImpulse.java:11-30` | read |
| the curiosity envelope is **100** (owner decision OD-044), the integrity drive 25, the self-improvement drive 50 | `AutonomyEngine.java:116,125`; `SelfImprovingEngine.java:60` | read |
| the budget is allocated by `ConjugateBudgeter.allocate(Row[], envelope)`, which returns an `Allocation` with the selected rows, the objective and the shadow price | `matrix-core/src/main/java/io/matrix/budgeter/ConjugateBudgeter.java:42,114` | read |
| the ethical gate is the FROZEN `EthicalFilter`, and the scheduler additionally allow-lists the four canonical impulse names | `ImpulseScheduler.java:62-77` | read |

**What this means in plain words.** MATRIX today decides *whether* it may want to do
something autonomously, and then writes that decision in a log. It does not have a
place to put the wanting. The curiosity cycle is **not** a completed autonomous work
system, and nothing in the repository may describe it as one.

## 3. What a consumer would be

A consumer is the thing that takes an accepted impulse and turns it into a **bounded
internal proposal** — a typed record of an intent, with provenance, a budget, a
lifetime and a terminal outcome.

It is deliberately **not** an executor. The difference is the whole point:

| | the scheduler (exists) | the consumer (proposed) | the executor (NOT proposed) |
|---|---|---|---|
| decides | whether an impulse is admissible | what the impulse means as a concrete proposal | what actually happens |
| side effects | none | **none** — it records | external effects |
| chain | none | **Chain B only** | — |
| writes | counters and a log | a proposal row and a terminal outcome | the world |

## 4. The alternatives considered

| # | the alternative | why it was not chosen |
|---|---|---|
| A | **do nothing** — keep the scheduler as the whole autonomy story | leaves the gap open: the impulse is accepted and then silently vanishes, which is exactly the ambiguity the directive names. The capability would keep being described as more than it is. |
| B | **a consumer that executes** — the impulse becomes a command | the directive forbids it without a new owner decision, and it is the design most likely to produce an unreviewed side effect. Chain A's epistemic claims, confidence, learning goals and safety policy must not be reachable from Chain B. |
| C | **a consumer that bridges to Chain A** | forbidden by W45.2: no implicit bridge, no shared write path, no new network surface between the chains. |
| D | **a consumer that proposes and proposes again** — an unbounded internal backlog | a queue that grows without backpressure is a memory leak with a cognitive vocabulary. A proposal that never expires is a promise nobody keeps. |
| E | **the recommended scope** — a consumer that creates and records typed internal proposals, inside Chain B, with no external effect | it closes the gap in the smallest way that can be verified, and every one of its guarantees is testable without a sandbox. |

## 5. The recommended scope

**A proposal-creating consumer.** Its whole contract:

1. **It consumes a `FireRecord` with outcome `FIRED` only.** A refused impulse is not
   a proposal. The refusal reason stays observable in the log and in the counters.
2. **It creates a typed internal proposal** and records it. The proposal carries:
   - a **stable proposal id** (derived from the impulse, the cycle and a monotonic
     counter — never from a wall clock, so the id is reproducible);
   - the **provenance**: the impulse, the cycle that fired it, the source of that
     cycle, and the envelope that was spent;
   - the **budget**: the envelope, the rows the budgeter allocated, and the shadow
     price at the moment of the allocation;
   - the **kind** of work the proposal asks for, from a closed enumeration;
   - the **expiry**.
3. **It stays inside Chain B.** A proposal is Chain B's own record. It never writes to
   Chain A, never changes an epistemic claim, a confidence, a learning goal or a
   safety rule, and it never opens a network surface.
4. **It has no external effect.** No message is sent, no shell command is executed, no
   external service is touched, no file outside Chain B's own store is written.
5. **The queue is bounded and the backpressure is explicit.** When the queue is full,
   the consumer **refuses** the proposal and says so — it does not block, it does not
   grow, and it does not drop the refusal silently.
6. **Proposals expire and are deduplicated.** An expired proposal reaches a terminal
   outcome (`EXPIRED`), and a proposal identical to a live one is refused
   (`DUPLICATE`).
7. **A fresh FROZEN check runs at execution time, not only at creation time.** The
   ethical gate is consulted again before the proposal is acted upon, because the
   state that permitted it may have changed.
8. **Every proposal ends in an observable terminal outcome.** `PROPOSED` is not a
   terminal state; a proposal that is still open after its expiry becomes `EXPIRED`,
   and the counts of every outcome are exposed.

### The terminal outcomes

`PROPOSED` (still open) · `EXPIRED` · `DUPLICATE` · `REFUSED_QUEUE_FULL` ·
`REFUSED_FROZEN` · `REFUSED_BUDGET` · `ACCEPTED_FOR_REVIEW`

`ACCEPTED_FOR_REVIEW` means the proposal was recorded and is waiting for a **human**,
or for a later wave that has its own mandate. It does not mean the work happened.

## 6. The safety invariants

| # | the invariant | how it is checked |
|---|---|---|
| S1 | no proposal creates an external effect | the consumer has no dependency that can reach outside Chain B; a test asserts that the proposal store is the only thing it writes |
| S2 | no proposal reaches Chain A | the consumer has no reference to Chain A's store; a test asserts the Chain A fingerprint is unchanged across a proposal |
| S3 | the FROZEN gate is consulted at creation **and** at execution | the ethical filter is called twice; a test flips the gate between the two and asserts the refusal |
| S4 | the budget is accounted for | the envelope spent is taken from the `FireRecord`; the consumer never allocates on its own |
| S5 | the queue is bounded | a fixed capacity; the consumer refuses beyond it |
| S6 | every proposal reaches a terminal outcome | a sweep over the proposals reaches only terminal states; no proposal is left open past its expiry |
| S7 | no wall clock in the decision path | the expiry is measured by an injected monotonic source (`System.nanoTime()` or an injected clock), never by `System.currentTimeMillis()` |
| S8 | the proposal id is stable and reproducible | the id is derived from the impulse, the cycle and a counter — the same impulse in the same cycle gives the same id |

## 7. The cost and the resource implications

| the cost | the estimate | the note |
|---|---|---|
| the code | one consumer class, one proposal record, one bounded store | in `matrix-core`, Chain B's package. No new module, no new infrastructure. |
| the storage | one row per open proposal, plus one row per terminal outcome | the store is in Chain B's own database; the retention is the same three-tier calendar as the snapshots (OD-035/OD-039) if the rows accumulate |
| the runtime | the consumer runs where the drives already run | no new thread, no new scheduler, no new timer |
| the budget | no additional envelope | the proposal spends the envelope the scheduler already accounted for; it does not allocate a second time |
| the test | one new test class, plus the negative controls of §6 | the tests are JVM tests — no native image, no GPU, no Docker |

**The cost of NOT doing it** is the ambiguity this ADR exists to remove: an autonomy
layer whose acceptance looks like completion.

## 8. The failure modes

| the failure | the behaviour | the evidence |
|---|---|---|
| the queue is full | the proposal is refused with `REFUSED_QUEUE_FULL` and counted | the counter is exposed |
| the same impulse fires twice in one cycle | the second is refused with `DUPLICATE` | the dedup key is (impulse, cycle, kind) |
| the FROZEN gate changes after creation | the proposal is refused with `REFUSED_FROZEN` at execution time | the gate is called twice |
| the consumer's store fails to write | the impulse was accepted by the scheduler but the proposal is **not** recorded — the failure is counted and logged, and the scheduler's acceptance is not silently turned into a proposal | the write is inside the consumer's own transaction |
| the process dies with open proposals | on restart, the proposals older than their expiry are closed as `EXPIRED` | the sweep runs at start-up |
| the proposal is accepted and nothing happens | the proposal reaches `ACCEPTED_FOR_REVIEW` and **stays** there until a human or a later mandate acts | this is the terminal state of the first consumer, not a failure |

## 9. The tests the implementation would have to carry

1. an accepted impulse produces exactly one proposal, with the provenance, the budget
   and a stable id;
2. a refused impulse produces **no** proposal;
3. the proposal id is reproducible for the same impulse and cycle;
4. the queue is bounded and the overflow is refused and counted;
5. a duplicate is refused;
6. the FROZEN gate is consulted at execution time and the refusal is honoured;
7. the Chain A fingerprint is unchanged across a proposal;
8. every proposal reaches a terminal outcome after its expiry;
9. the expiry uses the injected monotonic source, not the wall clock;
10. the budget is accounted once, not twice.

## 10. The owner decision

This ADR is **PROPOSED**. Nothing in §5 has been implemented. The W48 directive is
explicit: *do not implement autonomous side effects without a new owner decision*, and
*ask the owner before implementing this behaviour*.

The question put to the owner is in the wave questionnaire: **whether the first
consumer may be built at all**, and if so, whether its scope is the one in §5 — a
proposal-creating consumer that records internal proposals and does nothing else.

| the option | the meaning |
|---|---|
| **OD-045 · approve the proposal-creating consumer** | the next wave implements §5 exactly, with the tests of §9 |
| **OD-046 · keep the scope at "record only"** | the consumer creates and records proposals; no proposal is ever accepted for review — the terminal outcomes of §5 are reduced to `EXPIRED`, `DUPLICATE` and the two refusals |
| **OD-047 · do not build a consumer** | the scheduler stays the whole autonomy story; the capability is then described as "decides, does not act", and the gap is recorded in the registry rather than closed |

**Until the owner answers, the status of every autonomy capability in the registry stays
as it is today: the scheduler accepts impulses, nothing consumes them.**
