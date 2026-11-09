# ADR-W51-01 — The cognitive runtime, the canonical contracts, and the one crossing

**Status:** `ACCEPTED` · **Date:** 2026-11-08 · **Wave:** W51
**Supersedes:** nothing. **Amends:** `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` §2 and §6 —
the isolation decision is kept, its "no bridge" clause is superseded by the bridge contract
that the same document already specified.
**Machine-readable declaration:** `io.matrix.contract.CognitiveContract`, `io.matrix.api.CanonicalEntryPoint`.

---

## 1. The decision

**MATRIX's subsystems are one cognitive system that runs in two processes.**

The two serving applications stay separate processes, keep their own runtimes, and keep their
own databases with their own single writer. What changes is that the boundary between them
stops being a boundary of **cognition**: chain B's observations can now enter chain A's
epistemic model, and they do so through one explicit, versioned, authenticated, audited,
bounded component — the **bridge**.

The bridge is not a new idea in this wave. `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` §6
already wrote down the contract a future bridge must satisfy (BRIDGE-1 … BRIDGE-10) and BRIDGE-10
already required the isolation gate to be extended to the bridge before it is merged. W51
implements that contract rather than inventing a new one.

---

## 2. What is preserved, and what is not

| the W45.2 decision | W51 |
|---|---|
| The two chains are separate processes | **preserved** — no process is merged into another |
| Each chain owns its runtime and its files | **preserved** — chain B keeps `data/mind/mind.sqlite` |
| Each SQLite file keeps its designated writer | **preserved** — the bridge writes only through chain A's store, and only as an observation |
| The snapshot, recovery and fencing guarantees | **preserved** — the bridge does not touch the writer lock, the ownership token or the fence points |
| "No bridge" | **superseded** — the bridge of §4 exists, and it is the ONLY crossing |
| "No implicit write path" | **preserved** — every write crosses the bridge explicitly, with a request id, a signature and an audit record |
| "No automatic trust" | **preserved** — the trust of a call is established per call by a signature; the chain B side holds no ambient authority |
| "No new network surface" | **amended** — the bridge adds no port and no protocol of its own; it reuses chain A's existing HTTP port with a new path prefix |

**What the bridge may do.** BRIDGE-5 limits it to three things: it may add an observation, a
candidate, or an evidence link. It may not alter a claim, a confidence, a goal, a safety
policy, a disposition or an adjudication. Everything the bridge delivers is held as an
**unverified observation** — the disposition the epistemic store gives to something nobody
has checked.

**What the bridge may not do.** It cannot write to the store by another route, it cannot
widen its own grant, and it cannot survive the death of the process that owns the database
without the audit recording that it did.

---

## 3. The canonical contracts

**`io.matrix.contract` is the canonical model.** It holds the domain contracts — Entity,
Relation, Property, WorldState, StateTransition, Event, Observation, Evidence, Belief,
Hypothesis, Prediction, Experiment, Action, Outcome, Goal, Task, Skill, Procedure, Algorithm,
Plugin, Evaluation, Reflection — and the wire envelopes — CognitiveCommand, CognitiveEvent,
CognitiveResponse, Correlation, RetryPolicy, Confidence, Provenance, ValidityInterval,
BeliefStatus, Capability.

**The rules the contracts encode**, each of them a thing the repository did not previously
state anywhere:

1. **A graph association is not proof of truth.** `Relation` carries a `BeliefStatus` and a
   `Confidence` of its own, and it keeps association strength, observation frequency, causal
   relevance and evidential support in four separate fields. None of them may be used as
   another.
2. **A candidate belief does not become an answer because of a score.** `Belief.established()`
   consults the status and nothing else. A belief with a confidence of `0.99` and no supporting
   evidence is still `CANDIDATE`.
3. **A confidence carries its basis.** `Confidence.Basis` distinguishes `DECLARED`, `MEASURED`
   and `UNESTABLISHED`. An imported claim that no procedure examined carries
   `UNESTABLISHED`, and the number `0.0` no longer has to stand for "no confidence" and
   "unknown" at the same time.
4. **A prediction is settled before it is compared.** `Prediction.settledAt` is set before the
   action, and only a settled prediction can be compared with an outcome.
5. **A reflection produces decisions, not prose.** `Reflection.decisions` is a list of
   machine-readable decisions with a kind, a subject, a rationale and parameters.
6. **A plugin cannot widen its own authority.** `Plugin.declaredCapabilities()` is what the
   plugin asks for and `Plugin.grantedCapabilities()` is what the runtime gave it; they are
   separate fields and the runtime fills the second one.

**What the contracts are NOT.** They are not a second implementation of the subsystems' own
records. `io.matrix.knowledge.Claim` remains the workbench's claim, `io.matrix.cognitive.Prediction`
remains the kernel's prediction, and `io.matrix.cognitive.microworld.Observation` remains the
micro-world's observation. The canonical records are the **interchange** form: what crosses a
process boundary, what a plugin is told about, and what the runtime reasons over. The
subsystem records are adapted into them.

**Where the package sits, and why it matters.** `io.matrix.contract` is a package that neither
chain owns: chain A depends on it and chain B depends on it, and it contains no class that can
open a database, serve a route, or reach a store. That is what lets the isolation gate keep
its rules G1–G6 exactly as they were and add one more.

---

## 4. The bridge

### 4.1 The direction

**Chain B is the client; chain A is the server.** This is the direction BRIDGE-3 specifies:
"The chain B side is the client and holds no ambient trust: the trust of a call is established
per call, not by the fact that the process happens to run on the same host."

Chain B sends; chain A verifies and records. Chain A therefore keeps the single writer of the
epistemic database, and chain B keeps the single writer of its own mind database. Neither
process writes the other's files, and neither process holds a handle to the other's store.

### 4.2 The requirements and where each is satisfied

| requirement | what it demands | where it is satisfied |
|---|---|---|
| **BRIDGE-1** explicit | a named, opt-in component with one owner; no implicit write path | `io.matrix.contract.bridge.BridgeProtocol` names the contract; `io.matrix.bridge.BridgeService` is the single owner of the write path; the gate knows it by name |
| **BRIDGE-2** versioned | a version in every message; a reader refuses an unknown version | `BridgeProtocol` carries the version; `CognitiveContract.compatible` decides; a refusal names the version |
| **BRIDGE-3** authenticated | every call is authenticated and the chain A side verifies it | `HmacBridgeSigner` signs the canonical bytes; the comparison is timing-safe; there is NO default secret — without one the bridge refuses everything |
| **BRIDGE-4** provenance-carrying | the source, the instant, the sender, the episode and the raw observation travel with the message; a message without provenance is refused | `Provenance` carries the source, the origin and the span with a declared unit; `BridgePayload` refuses a payload without provenance |
| **BRIDGE-5** safety-bounded | the bridge may add an observation, a candidate or an evidence link, and nothing else | `BridgeCapability` names the three kinds; anything else is refused before the store is touched |
| **BRIDGE-6** fail-closed | every failure is a refusal and an audit record; the default is "no" | `BridgeReceiver` turns every failure into a `BridgeDecision` of `REFUSED` with a reason; there is no degraded mode |
| **BRIDGE-7** audited | every call and every refusal is written to the append-only audit chain with its request id, its decision and the reason | `BridgeAuditRecord` is append-only; the refusal is recorded as carefully as the acceptance |
| **BRIDGE-8** replayable | idempotent per request id; the effect is recorded once; the idempotency is durable | `BridgeIdempotency` maps a request id to its first outcome; the server's ledger is durable |
| **BRIDGE-9** bounded | a rate, a size, a batch size and a per-day ceiling, enforced before the store is touched | `BridgeBudget` checks every limit before the write |
| **BRIDGE-10** gated | the isolation gate is extended to the bridge; the bridge is the only crossing; the negative control is re-run against the tree that contains the bridge | the gate's rule **G7** and the negative control's third case |

### 4.3 Why the bridge carries an observation and not a conclusion

Chain B's brain produces conclusions. A conclusion that arrives from another process is not
knowledge; it is a report. The bridge therefore delivers it as an **observation with
provenance**, and the epistemic store holds it in the disposition it gives an unverified
observation. Nothing in the bridge can raise a claim's status. That is the whole of the
safety story, and it is why the bridge needs no write authority beyond "add one observation".

---

## 5. The shared runtime

**`io.matrix.runtime.CognitiveRuntime` is the composition layer.** It declares the parts of
the system, what each can do, what each needs and how each is doing; it routes a request for
a capability to the component that offers it; and it refuses a component whose dependencies
are down **by name**.

It is deliberately not a service locator and not an event bus: it holds declarations, not
references, so a component can be started, stopped or replaced without the runtime knowing
anything about it. There is no global bus in MATRIX after this wave, and there was none
before it.

**The rule the runtime enforces.** A component whose dependency is not `UP` is reported as
`DEGRADED` and is not handed out. This is what makes "what happens when they fail" part of
the runtime's knowledge rather than of each caller's discretion.

---

## 6. The gate

The isolation gate of §4.3 of `W45.2-CHAIN-BOUNDARY.md` keeps its rules **G1**–**G6**
unchanged, and gains:

- **G7** — the classes of `io.matrix.contract.**` that chain B reaches cannot open a database,
  cannot serve a route and cannot reach a store. The contract package is shared substrate, and
  the gate says so mechanically rather than by convention.
- **G8** — the bridge is the only class allowed to reach both chains; any other crossing is a
  failure of the gate.
- **G9** — the bridge's own properties are checked: it authenticates, it versions, it bounds,
  it audits, and it writes only observations.

The negative control is re-run against the tree that contains the bridge, so that "the bridge
is the only crossing" is a tested statement rather than a promise.

---

## 7. What this wave does not claim

- It does not claim that MATRIX has general intelligence, human-like understanding, universal
  learning, or reliable autonomous self-improvement.
- It does not claim that the bridge makes the two chains one process. They remain two, with
  two databases and two writers.
- It does not claim that chain B's conclusions are correct. The bridge delivers reports, and
  the epistemic model decides what to do with them.
- It does not claim that a plugin is isolated by a classloader. It is not: the isolation the
  wave ships is a closed interpreter whose vocabulary contains no I/O operation at all, and
  that is a weaker capability than a WASM runtime and a stronger boundary than a namespace.
