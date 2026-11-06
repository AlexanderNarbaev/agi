# W45.0 — DECISION LOG

**Status:** the decisions the W45.0 wave took, with the alternatives that were considered and
the evidence each was decided on.
**Prepared:** 2026-11-03.

Eleven decisions. Each one names the alternatives it rejected, because a decision without
the rejected alternatives is a description, not a decision.

---

## D1 — the disposition is a new concept, not an overload of `Case.Origin`

**Decision.** A new enum, `io.matrix.cognitive.microworld.ObservationDisposition`, with four
states, persisted in a new table `observation_disposition` (schema v17).

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| overload `Case.Origin` | provenance and epistemic applicability are different questions. A system-observed case can be eligible, unresolved or rejected; a reported case can be eligible too. One field cannot answer two questions, and the second would never be answerable. The W45 directive forbids the overload explicitly. |
| infer the disposition from the evidence relation at read time | the relation says what the evidence is ABOUT, not what may be believed because of it. The W44 kernel wrote `CONTRADICTS` only when no hypothesis explained the observation, so the relation cannot distinguish "contradicted a prediction" from "unexplained". The inference is lossy and the loss falls on the unsafe side only sometimes. |
| keep the decision in memory only | the whole defect is that the decision did not survive a restart. |
| a column on `epistemic_case` | the disposition carries the adjudication — why it was held, who resolved it, when, which model revision took it in — and the case table has no room for that. A separate table also keeps the migration additive: no existing query changes shape. |

**Evidence.** `Case.Origin` has five values, all about provenance
(`OBSERVED`, `REPORTED`, `DERIVED`, `ADVERSARIAL`, …). The evidence relation has seven, all
about the relation of a source to a claim.

**Trade-off accepted.** Two tables now have to be read to understand an observation, and the
join is by `case_id`.

---

## D2 — the conservative default for an unclassifiable record is `UNRESOLVED`

**Decision.** A record whose disposition cannot be established is held, never eligible. The
v16 migration backfills from the evidence relation and holds anything it cannot read.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| default to `ELIGIBLE` | the W45 directive forbids it explicitly, and it is the unsafe direction: a contradiction that cannot be classified would become model data. |
| delete the unclassifiable records | "No original case, source, evidence, or prediction is silently deleted" — and a record that cannot be classified is not a record that may be thrown away. |
| leave them unclassified and let the caller decide | the caller is the restoration path, and a caller that has to decide would decide wrongly under pressure. The store decides, once, and the decision is recorded. |

**Evidence.** The v16 → v17 migration's backfill rule, and the test "a v16 database is
migrated to v17 and its dispositions are backfilled" — including the case with **no evidence
link**, which is held.

**Trade-off accepted.** A workspace whose cases predate W45 and which has known conflicts
loses the "apply it" behaviour of the W44 path. That is the intended direction, and the
adjudication path is one call.

---

## D3 — the contradiction rule is "against a BELIEF", not "against any prediction"

**Decision.** The session holds an observation only when the prediction it contradicts was
**unanimous**. An observation that differs from an unsettled prediction is ordinary learning
and is applied.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| keep the W44 rule (hold whenever the observation differs from the prediction's value) | it is a **learning deadlock**. The kernel's natural cycle predicts an assignment with a tie-break value and then probes an assignment the tie-break got wrong; the observation is held, the model never settles, every later prediction stays unsettled, and nothing ever becomes a belief again. The W44 wave never saw this because the restoration path applied the held observations anyway — the defect D1/D4 fixed. |
| hold only when the prediction was unanimous AND the model was settled | "settled" (one survivor) is stronger than "unanimous" (the survivors agree on this assignment). With two survivors that agree on the assignment, the model does hold a position about it, and an observation that disagrees is a real conflict. The W44 benchmark's adversarial case (§4.4) is exactly that case, and it must stay held. |
| never hold anything | a contradiction would rewrite the model, which is the failure mode §14 forbids. |

**Evidence.** The diagnostic that found it: with the defect fixed and the W44 rule kept, the
kernel's model stalled at **2 survivors instead of 1**, the RESTORE trace read "3 of them
constrain the model and 1 are held as unresolved conflicts", and the lying-environment test
stopped producing an error — because the prediction it contradicted was a tie, not a belief.

**Trade-off accepted.** The W44 rule was a wider net, and it caught more observations as
conflicts. The wider net also stopped the model learning at all. The narrower net keeps the
one case the W44 benchmark documents.

**Consequence for the W44 documents.** `W44-BENCHMARK.md` §4.4 describes the adversarial
case as "predict `false`, observe `true`" without saying which assignment; the assignment it
used in the test was one where the two survivors DISAGREE. Under the new rule that case is
learning, not a conflict; the test now uses a settled assignment, where the case holds
exactly as the benchmark describes. **The benchmark's numbers are unaffected** — its
survivor table (§4.1) was produced by feeding observations directly to a session, with no
prediction in between.

---

## D4 — the reference method reads the model-eligible observations

**Decision.** `MicroWorldReferenceMethod.execute` reads `session.appliedObservations()`.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| keep reading `session.observations()` | the reference would be a second implementation over a DIFFERENT dataset. Its agreement with the fast path would prove nothing about the model in production, and its disagreement would be a false alarm. The W45 directive requires "the reference method must be a genuine reference for the active model, not a second implementation with a different input dataset". |
| give the reference the raw cases and let it decide | that is the same defect with more steps: the reference would have to re-implement the inclusion policy, and the policy is exactly what was wrong. |

**Evidence.** The test "the reference method reads the model-eligible observations, not every
observation": under the W44 rule the reference refused with "no hypothesis fits the recorded
observations" after a restart, because the held contradiction made the truth table
inconsistent.

**Trade-off accepted.** The reference no longer sees the held observations, so it cannot be
used to audit them. The audit of held observations is the disposition table's job.

---

## D5 — no procedure is promoted without INDEPENDENT evidence, and the fast path is relabelled

**Decision.** The promotion check's candidate validation set is the recorded observations the
model was **not** built from. In the lamp world that set is always empty, so **nothing is
promoted**. The fast path is used as a **derived shortcut** of the reference over the same
model-eligible data, and nothing calls it validated.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| keep promoting on the model's own data | that is the defect: an in-sample consistency check reported as a held-out validation. |
| split the four truth-table rows into two halves and call them independent | the micro-world's input domain is four assignments; a truth table over four rows is determined only when all four have been observed, so a model that has SETTLED has seen the whole domain. Splitting the same four rows is a split of the same data, and the domain cannot support the claim. The W45 directive says so in as many words. |
| validate on a different rule family | the promoted procedure is "the settled-model lookup" for a SPECIFIC rule; validating it on another rule's data would not be evidence about it either. It would be evidence about the METHOD, which is a different claim — and the method's correctness is an algebraic identity over the same data, not a statistical matter. |
| keep promoting, and rename the field | a rename does not make the evidence independent. The record would still claim a validation it does not have. |

**Evidence.** The domain argument is a proof, not a measurement: `HypothesisSpace` enumerates
16 Boolean functions of 2 variables; the model settles when exactly one survives; surviving
requires all four rows to be consistent; therefore a settled model has observed all four
rows; therefore the set of observations it was not built from is empty. The trace now says it
in words: *"no procedure was promoted: the validation set holds 0 observation(s) the model
was not built from, and 2 independent observations are required; the fast path is used as an
in-sample derived shortcut of the reference, not as a validated procedure"*.

**Trade-off accepted.** The W44 fast path is no longer a *promoted procedure*. Its
availability now rests on the model's being settled — which is a property of the durable
data — rather than on a stored validation record. The W44 wave's §22 sequence ("validate on
held-out cases, promote only if it preserves quality, store the applicability domain,
monitor it, invalidate after drift") is therefore **half-implemented**: the promotion step
cannot fire in this domain, and the monitoring and invalidation steps have nothing to
monitor. **The capability delta records this as a change, not as a capability.**

---

## D6 — the epistemic write is one transaction

**Decision.** `KnowledgeStore.recordObservationAtomically(...)` writes the source, the
evidence, the case, the evidence-to-case link and the disposition in one transaction.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| keep the three separate writes and add a compensating delete on failure | the failure is a process death, not an exception: there is no code left to compensate, and the half-written state is already on disk. |
| write the case FIRST and the evidence later | the same problem with the other order: a reader that sees a case without evidence cannot tell "not yet written" from "never written". |
| one transaction across the cognitive store AND the epistemic store | two different databases with two different owners. The directive says the two stores may remain separate; a distributed transaction would be a new architecture for one write. |

**Evidence.** The test "the case, its source, its evidence, the link and the disposition are
all written" checks all five artefacts, and the concurrency test "the epistemic records of
an observation are linked, never half-linked" checks the links under eight concurrent
writers.

**Trade-off accepted.** The `KnowledgeStore`'s public `recordCase` and `linkEvidenceToCase`
remain for the importers that write them separately; the kernel's path uses the atomic one.

---

## D7 — the cycle budget is a deadline on a monotonic clock

**Decision.** One `CycleDeadline` at cycle entry, checked at ten phase boundaries, with the
timeout producing an explicit `RESOURCE_LIMITED` outcome and a persisted trace.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| check the elapsed time after the whole cycle | that is not enforcement; it reports a fact after the work is done. |
| kill the worker thread when the budget expires | interrupting an arbitrary Java thread is unsafe, and the operations that matter here are not interruptible anyway. The directive forbids it. |
| run the cycle in a bounded executor with a future timeout | a timeout would leave the worker running, and the episode's state would be whatever the worker happened to have done. The outcome would be untruthful. |
| use the wall clock | the wall clock can move. A deadline measured against it can lengthen. |
| check the budget inside every algorithm | each algorithm would need its own deadline plumbing, and the micro-world's two algorithms are bounded by construction (16 hypotheses × the number of observations). The check belongs where the phases are. |

**Evidence.** `CognitiveCycleBudgetTest` — five tests, including one that makes the budget
expire exactly after a probe, so the test can assert both that the side effect that had
already happened is not rolled back and that the notes say so.

**Trade-off accepted.** There is no hard wall-clock bound on a single operation. The design
document classifies every operation that cannot be interrupted. The 250 ms figure remains a
**configured convention**, not a calibrated one: the wave did not measure the cycle's
distribution, and the number is not a promise.

---

## D8 — the drive state is durable, and the version is allocated inside the transaction

**Decision.** The regulatory state is persisted in the cognitive store (schema v2 → v3), with
the provenance of every change in the same transaction, and restored **once, when the
application starts**.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| treat the drive state as process-local and document it | ADR-W44-01 lists the drives among the cognitive state the store persists. The audit found the document and the implementation disagreed. The directive asks for the contract to be made explicit — and when the document says "persisted", the honest options are to persist it or to change the document with the owner's approval. |
| restore the state at the start of every cycle | it would overwrite a regulator that a caller had deliberately seeded — a test that builds a "loaded" regulator would silently lose it. The application restores once, at start; within a process the in-memory state is authoritative. |
| allocate the version in the caller | two concurrent writers would read the same version and both claim it. The version is allocated inside the transaction. |

**Evidence.** `CognitiveRegulatoryPersistenceTest` — five tests, including "the regulatory
state is persisted and restored after a real restart" (the files are closed and opened
again) and "a repeated event is refused: a drive must not move twice for one event".

**Trade-off accepted.** The regulatory state is written on every cycle that produces an
event, which is one small transaction per cycle. The `CostLedger` is deliberately **not**
persisted: a timing measurement without the environment it was measured in is not
interpretable, so the ledger stays process-local and the documents say so.

---

## D9 — one cognitive cycle runs at a time per kernel

**Decision.** `CognitiveKernel.process` is `synchronized`.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| synchronise only the store's methods | the store's writes do not overlap, but the regulatory update is a read-modify-write of a state that lives in the kernel: two cycles would each read the same drives and the second write would discard the first one's contribution. |
| a lock around the regulator only | the episodes, the events and the epistemic writes would still interleave, and the interleaving of an episode's events with another's is exactly what the event position index forbids. |
| connection-per-operation | it fixes the JDBC sharing, not the lost update. |
| an actor or a message bus | the directive forbids a generic framework for a local synchronisation problem, and there is no throughput problem to solve: this is a single-user local application. |

**Evidence.** `CognitiveConcurrencyTest` — five tests: eight concurrent observations produce
eight independent episodes; reads during writes never fail; the sequence never repeats; the
regulatory version advances once per write; the epistemic records are never half-linked.

**Trade-off accepted.** **The system does not scale.** A slow cycle holds the others back.
That is stated in the design document rather than discovered later.

---

## D10 — the W44 tests that encoded the defects were updated, not deleted

**Decision.** Four W44 tests were changed, and the change is recorded here.

| test | what it asserted | what it asserts now | why |
|---|---|---|---|
| `CognitiveKernelTest.theFastPathIsNotUsedBeforeItIsValidated` | no procedure before the model settles | unchanged | — |
| `CognitiveKernelTest.onceTheModelSettlesTheFastPathIsSelected` | a procedure IS promoted after the model settles | **no procedure is promoted**, and the refusal says why | D5 |
| `CognitiveKernelTest.anObservationTheModelCannotExplainProducesAnError` | an error is recorded | unchanged — it passes again | the failure was D3 |
| `MicroWorldSessionTest` TEST-LEARN-004 | an adversarial observation against an **unsettled** prediction is held | the same, against a **settled** prediction | D3 |
| `CognitiveKernelTest.observe(...)` helper | the signal id is the payload's hash | a unique id per request | the W44 helper encoded Priority B's defect |

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| delete the tests | they are the evidence that the properties hold. Deleting the assertion of a property that no longer holds would leave nothing behind. |
| keep the tests and make the code satisfy them | that would reinstate the defects: an in-sample "validation", and a hash-derived episode identity. |

**Trade-off accepted.** The W44 suite's pass count changed, and the changed assertions are
visible in the diff. The W45 test report lists every changed test.

---

## D11 — the W44 archaeology artefacts were NOT regenerated

**Decision.** The W45 wave did not re-run the four audit scripts over the changed sources,
did not regenerate `W44-CLASS-INVENTORY.csv` or any other W44 artefact, and did not modify
any W44 document.

**Alternatives.**

| alternative | why it was rejected |
|---|---|
| regenerate the inventory so the CSV matches the new code | the W45 directive says: "Preserve all RECON-W44.0 deliverables and all four audit scripts. Do not overwrite, rename, regenerate in place, delete, stage, or move them." The CSV is a **historical measurement** of the tree as it was at the archaeology. |
| edit the W44 documents where the W45 findings contradict them | the same rule: "Never modify existing W44 archaeology documents to make them agree with new findings." The corrections are recorded in the W45 documents instead. |

**Evidence.** `git status` shows the W44 artefacts as untracked and unmodified; the
corrections are in `W45-CAPABILITY-DELTA.md` §2 with their reproduction commands.

**Trade-off accepted.** The W44 CSV describes a tree that no longer exists exactly. A reader
must read the two waves together: the W44 CSV is the measurement, the W45 delta is the
correction.
