# W45.0 — INTEGRITY DESIGN

**Status:** implemented. **Scope:** the cognitive kernel in `io.matrix.cognitive`, the
epistemic store it writes to, and the micro-world session it restores.
**Prepared:** 2026-11-03.

This document states the six contracts the W45 wave established. Each one answers a
question the RECON-W44.0 archaeology left open, and each one names the mechanism, the file
that carries it, and the test that proves it.

---

## 1. The durable observation disposition

### The defect

The W44 wave recorded that an observation CONTRADICTED the model — as an evidence relation —
but the micro-world's restoration path never read it back. A session rebuilt from the
durable cases had no prediction history to compare against, so every observation was applied
to the model, including the ones the live session had deliberately kept out of it. **The
contradiction survived the restart as a record and was lost as a decision.**

The evidence relation was never the right place for the decision, and it is not used as one
now. It answers "what is this evidence about"; the disposition answers "what may be believed
because of it".

### The mechanism

**`io.matrix.cognitive.microworld.ObservationDisposition`** — four states, and the question
each one answers:

| state | meaning | constrains the model? |
|---|---|---|
| `ELIGIBLE` | ordinary model data; the model was built from it | **yes** |
| `UNRESOLVED` | held: a recorded conflict whose cause is not established | **no** |
| `ACCEPTED` | a review adjudicated the conflict and accepted the observation | **yes** |
| `REJECTED` | a review adjudicated the conflict and rejected the observation | **no** |

`ObservationDisposition.constrainsModel()` is the **one** rule. The codec that restores a
session, the session itself, the reference method and the promotion check all ask that
question, and they all get the same answer.

**The persisted form** — a new table in the epistemic store, schema **v16 → v17**:

```sql
CREATE TABLE observation_disposition (
    case_id                   TEXT PRIMARY KEY,
    workspace_id              TEXT NOT NULL,
    disposition               TEXT NOT NULL,
    reason                    TEXT,
    contradicted_prediction_id TEXT,
    resolved_by               TEXT,
    resolved_at               TEXT,
    model_revision_id         TEXT,
    recorded_at               TEXT NOT NULL)
```

It is a separate table and not a column on `epistemic_case`, because it answers a different
question from the case's `origin` — provenance versus epistemic applicability — and because
it carries the adjudication (why it was held, who resolved it, when, which model revision
took it in), which the case table has no room for.

### The conservative default

A record whose disposition cannot be established is **`UNRESOLVED`**, never `ELIGIBLE`. An
observation that cannot be classified must not silently become confirmed model data.

The v16 → v17 migration backfills from what the database DOES hold — the linked evidence's
relation:

| the record holds | the backfilled disposition |
|---|---|
| evidence with relation `CONTRADICTS` | `UNRESOLVED` — it was a recorded conflict |
| evidence with relation `SUPPORTS` / `EXEMPLIFIES` / `QUALIFIES` | `ELIGIBLE` — ordinary model data |
| any other relation | `UNRESOLVED` — the relation does not establish eligibility |
| **no evidence link at all** | `UNRESOLVED` — it cannot be classified |

**The one limitation, stated plainly.** A v16 database cannot distinguish "an observation
that contradicted a prediction" from "an observation a hypothesis explained", because the W44
kernel wrote the relation `CONTRADICTS` only when NO hypothesis explained the observation.
A contradiction whose cause was a wrong prediction — and which some hypothesis did explain —
is therefore backfilled as `ELIGIBLE`, and the restored session will apply it. **The new
write path is exact; the legacy path is best-effort and conservative in the direction that
holds rather than trusts.** A workspace whose cases predate W45 and which has known
conflicts should be adjudicated once, explicitly.

### Provenance

Nothing is deleted. The original case, its source, its evidence and the evidence-to-case
link all stay exactly as they were; the disposition is an ADDITIONAL record. The adjudication
path is `KnowledgeStore.adjudicateObservation(...)`, which rewrites the disposition row and
nothing else.

**Test:** `CognitiveContradictionDurabilityTest` — "a review can adjudicate a held
observation without deleting anything", "a v16 database is migrated to v17 and its
dispositions are backfilled".

---

## 2. Restoration semantics

### The rule

**A restored session honours the durable disposition; it does not re-decide.** The W44
session re-decided from a prediction history it did not have, and that is the whole defect.

```
read cases ──► read the disposition of each case ──► rebuild the session
                                                        │
                        ELIGIBLE / ACCEPTED ─────────────┤──► applied to the model
                        UNRESOLVED / REJECTED ───────────┘──► held, visible, outside the model
```

The restoration is therefore **order-independent**: the same set of cases always rebuilds the
same model, whatever order the rows come back in.

### What is held, and what a hold means

A held observation is:

- **recorded** — it is in `observations()`, in the durable case table, and in the trace;
- **visible** — `quarantinedObservations()` names it, and the RESTORE step of every cycle
  says how many observations are held;
- **outside the model** — it never enters `appliedObservations()`, so it never constrains a
  hypothesis;
- **never deleted** — a later review can accept or reject it, and the acceptance is a
  versioned change to the disposition, not a rewrite of history.

### The second half of the rule: what counts as a contradiction

**A contradiction is a disagreement with a BELIEF.** The session holds an observation only
when the prediction it contradicts was **unanimous** — when the model actually held a
position. A prediction the session marked as unsettled is an admission of ignorance: the
record says the value "is explicitly not a decision", so an observation that differs from it
is ordinary learning.

This is a change to the W44 rule, and it is a necessary one. The W44 rule held the
observation in both cases, and that only LOOKED harmless because the restoration path
applied the held observations anyway — the defect this wave fixes. With the defect fixed,
the W44 rule is a **learning deadlock**: in the kernel's natural cycle the observation that
discriminates two hypotheses routinely differs from the tie-break value of a prediction the
model never made, so the observation is held, the model never settles, every later
prediction stays unsettled, and nothing ever becomes a belief again. The W44 benchmark's own
adversarial case is unaffected: it predicts a **settled** assignment (the two survivors agree
there), and it is still held.

**Test:** `MicroWorldSessionTest` — "TEST-LEARN-004" (an adversarial observation against a
settled prediction creates a prediction error and the model is not rewritten) and
"TEST-LEARN-005" (an observation against an UNSETTLED prediction is learning, not a
conflict).

---

## 3. Reference-data semantics

### The rule

**The reference method and the fast path must read the same data.** Before W45 the
reference method read `session.observations()` — every recorded observation, including the
ones the model was built without — while the fast path read the model. Two implementations
over two datasets: their agreement proved nothing about the model in production, and their
disagreement was a false alarm.

Now:

- the **reference method** reads `session.appliedObservations()` — the model-eligible data;
- the **fast path** reads the single surviving hypothesis — the same data;
- the **promotion check** computes its candidate validation set from the same rule.

**The reference is now a genuine reference for the active model.** When a contradiction is
held, the reference does not refuse with "no hypothesis fits the recorded observations" — it
answers from the model that is actually in production.

**Test:** `CognitiveContradictionDurabilityTest` — "the reference method reads the
model-eligible observations, not every observation".

---

## 4. Identity and idempotency

### Four identities, four sources

| identity | what it is | where it comes from | durable in |
|---|---|---|---|
| **request id** | the caller's logical request | the caller's idempotency key | `cognitive_idempotency.key` |
| **signal id** | the received signal | `EpisodeIdAllocator.nextSignalId()`, from an injectable `IdSource` | — (unique per request) |
| **episode id** | one execution of the cognitive cycle | the kernel: `ep-<signalId>` | `cognitive_episode.id` |
| **event id** | one immutable event inside an episode | the kernel: `<episodeId>-evt-<sequence>` | `cognitive_event.id` |
| **arrival number** | the order of the arrivals | `cognitive_sequence`, incremented inside a transaction | `cognitive_sequence.value` |

**`String.hashCode()` is not an identity and is not used as one.** The W44 service derived
the signal id from the payload's hash, so two requests with the same payload shared one
episode id, and distinct payloads could collide. The arrival number lives in the database,
so a restart cannot reset it and two concurrent callers cannot be handed the same number.

### The write policy

No write in the cognitive store uses `INSERT OR REPLACE` any more.

| record | policy |
|---|---|
| `cognitive_event` | **immutable, insert-only.** Same id and same content → idempotent no-op. Same id or same `(episode_id, sequence)` position with different content → `CognitiveConflictException`. A unique index on the position makes the collision impossible at the database level. |
| `cognitive_prediction`, `cognitive_error`, `cognitive_task` | **immutable, insert-only**, same rule. |
| `cognitive_episode` | **mutable in one direction.** `startEpisode` is an INSERT (the summary and `started_at` are never rewritten); `closeEpisode` is an explicit `UPDATE ... WHERE closed_at IS NULL`. Opening an episode twice with a different account is a conflict; closing it twice with different instants is a conflict. |
| `cognitive_procedure` | **a version.** The identity is the pair `(id, version)`; a new version is a new row and the predecessor survives. The definition is compared (name, family, applicability, required facts, reference, agreement, validation kind); the counters and the promotion instant are measurements and are not part of it. |
| `cognitive_regulatory_state` | **a version.** The version is allocated inside the transaction, so two concurrent writers cannot claim the same one. |

### The idempotency policy

`POST /v1/cognitive/observe` accepts an optional `idempotencyKey`.

| the caller sends | what happens |
|---|---|
| **no key** | a fresh episode every time, even for a byte-identical payload |
| **a key** | the first delivery claims it (an atomic INSERT of an `IN_PROGRESS` row — the only atomic step, so two concurrent deliveries of one key cannot both work), runs the cycle, and stores the outcome under the key |
| **the same key, the same payload** | the stored outcome is replayed. **No new episode, no new events, no new prediction. No work at all.** |
| **the same key, a different payload** | **409 Conflict.** The first outcome is untouched. The payload is bound to the key by a SHA-256 digest, not by `hashCode`. |
| **a delivery whose work FAILED** | the claim is released. A failed request is not a completed one, and the caller may retry it. |

The mapping lives in `cognitive_idempotency`, so it survives a restart: a retry after a
process death is still answered with the original outcome.

**Test:** `CognitiveServiceIdentityTest`, `IdempotencyRegistryTest`,
`EpisodeIdAllocatorTest`, `CognitiveStoreIdentityTest`, `CognitiveStoreMigrationTest`,
`CognitiveResourceTest`.

---

## 5. Transaction boundaries

### The epistemic write is now one transaction

The W44 path wrote an observation in **three separately committed operations**: the source
and the evidence through `applyChanges`, then the case, then the link from the evidence to
the case. A process that died between them left a half-written observation — evidence with
no case, a case with no evidence, a link to nothing. None of those states can be read.

Now `KnowledgeStore.recordObservationAtomically(...)` writes **the source, the evidence, the
case, the evidence-to-case link and the disposition in one transaction**. Either all of it is
on disk or none of it is.

### The cognitive write is one transaction too

The regulatory state and the provenance of the change are written together
(`CognitiveStore.recordRegulatoryState`). A state without the events that produced it is a
number nobody can explain; events without the state are a history of changes that did not
happen.

**The replay rule.** A regulatory event id that already exists means the same event is being
applied twice — after a crash, or by a caller that retried. The transaction is then
**refused** and nothing is written: **a drive must not move twice for one event.**

### What remains two stores

The cognitive store and the epistemic store remain separate — they answer different
questions and have different owners. A cycle that writes to both is not distributed
transactionally, and it is not pretending to be: the cycle's own record says what it did,
and the trace is append-only.

**Test:** `CognitiveContradictionDurabilityTest` — "the case, its source, its evidence, the
link and the disposition are all written"; `CognitiveRegulatoryPersistenceTest` — "a repeated
event is refused".

---

## 6. The deadline contract

### The rule

**One monotonic deadline, taken at cycle entry, checked at the phase boundaries.**

`CognitiveKernel.DEFAULT_CYCLE_BUDGET_MILLIS` is now a bound rather than a description.
`CycleDeadline` holds it and measures it against an injected **monotonic** clock
(`System::nanoTime` in production; a deterministic supplier in the tests).

**Why a monotonic clock is allowed here.** The project rule forbids a wall-clock read in a
runtime DECISION path, because a decision that depends on WHEN it was taken depends on the
clock rather than on what is true. A deadline is not such a decision: "has the work already
taken too long?" has the same answer whatever the wall clock says, and a system that cannot
stop is a system one slow input can hold hostage. The deadline is a resource boundary and it
never reaches a decision about what is true.

### Where it is checked

| phase boundary | what it covers |
|---|---|
| `PERCEIVE` | perception and input validation |
| `RESTORE` | restoring the model from the cases |
| `PREDICT` | the prediction (and the probed prediction) |
| `SELECT` | algorithm selection |
| `COMPUTE` | the computation |
| `PROPOSE` | experiment selection |
| `PROBE` | the environment probe |
| `OBSERVE` | observation processing and the epistemic write |
| `LEARN` | the promotion check |
| `REGULATE` | the regulatory update and its persistence |
| `ANSWER` | the answer and the trace persistence |

### What it does NOT promise

**There is no hard wall-clock bound on any single operation.** The operations that cannot be
interrupted are classified as such:

| operation | interruptible? | bound |
|---|---|---|
| the environment probe | **no** | one probe of the micro-world; the environment is a two-variable truth table |
| the reference enumeration | **no** | 16 hypotheses × the number of model-eligible observations |
| the epistemic write | **no** | one SQLite transaction |
| the algorithm selection | **no** | a bounded number of candidates |

None of them is killed, and no thread is interrupted unsafely. The deadline is checked
**around** them. The honest statement is therefore: *the cycle stops starting new work after
the budget is exhausted, and a single operation that was already running runs to
completion.*

### What a timeout produces

A timeout is an **explicit, inspectable terminal outcome**, not an abandoned episode:

- the status is `RESOURCE_LIMITED`;
- the trace carries a `TIMEOUT` step that names the phase, the elapsed time and the budget;
- the episode is **closed**;
- the notes record **whether an experiment was executed, whether an observation was
  committed, whether the procedure state changed, and that the side effects that had already
  happened are NOT rolled back**.

**Already-observed world events are never rolled back.** A cycle that pretended its earlier
work had not happened would be a cycle lying about the world.

**Test:** `CognitiveCycleBudgetTest` — five tests, including "work that had already happened
is not rolled back when the budget expires" and "the timeout is persisted: the episode ends
and the trace survives".

---

## 7. The concurrency guarantees

### The contract

**One cognitive cycle runs at a time per kernel.** Every write to the cognitive store is
serialised on the store's own monitor.

### Why it is this and not something else

The kernel holds mutable state — the regulator's drives — and the store holds **one** JDBC
connection. Two cycles at once would interleave their regulatory updates: each would read the
same drive state, each would evolve it, and the second write would silently discard the
first one's contribution. Synchronising the store's methods prevents two WRITES from
overlapping, but a write that is correct on its own is still a **lost update** when the state
it was derived from has already moved.

The smallest mechanism that makes the contract correct is therefore: **the cycle does not
overlap with itself**. There is no actor, no message bus, no lock hierarchy, and no new
framework.

### What is guaranteed

| property | guarantee |
|---|---|
| concurrent `/observe` requests | independent episodes, independent predictions, independent traces |
| concurrent reads during writes | the reads never fail and never see a half-written episode |
| episode sequence allocation | no number is handed out twice, across a restart |
| event ordering | `(episode_id, sequence)` is unique; the position index makes it so |
| cross-episode state | no episode's trace contains another's events |
| regulatory updates | the version advances once per write; no update is lost |
| the JDBC connection | every use is inside a synchronised store method |
| epistemic records | no half-linked source/evidence/case structure survives a concurrent write |

### What is NOT guaranteed

- **The system does not scale.** A slow cycle holds the others back. That is the price of the
  guarantee, and it is the right price for a single-user local application.
- **The kernel is the boundary, not the store.** Two `CognitiveKernel` instances over one
  database file would each serialise their own cycles, and the store's version check would
  then refuse the second writer's regulatory update rather than lose it. The application
  creates one kernel per service, so this does not arise today.

**Test:** `CognitiveConcurrencyTest` — five tests, including "concurrent observations produce
independent episodes, predictions and traces" and "the regulatory version advances once per
write, with no lost update".
