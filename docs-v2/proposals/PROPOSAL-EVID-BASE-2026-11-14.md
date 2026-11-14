# PROPOSAL — EVID-BASE: the next cycle measures what MATRIX claims and recovers what it loses

**Status:** `PROPOSAL` — a proposal, not a wave; the owner names it and cuts the branch ·
**Date:** 2026-11-14 · **Cut from:** `develop` after the W53 release
**Subject:** four experiments, each of which ends in a number instead of an intention
**Companions:** `docs-v2/engineering/ADR-REP-SUBSTRATE-01-representation-substrate.md` ·
`docs-v2/engineering/W53-STATUS.md` · `docs-v2/engineering/W53-EPISTEMIC-COMPARISON.md` ·
`docs-v2/governance/ROADMAP.md` §4.14

---

## 1. Why this cycle, and why it is not another infrastructure phase

The W53 release is integrated and REP-SUBSTRATE has decided what each workload deserves.
What is left is the owner's third property — **a system that claims something must be able
to prove it** — and two of MATRIX's own claims cannot be proved today:

- the peer reports a **fidelity of 1.0000** that was measured on a training window of
  **one class** and three distinct texts, so the number describes a constant, not a learner;
- **11 of the 29 epistemic cases still fail**, every one of them because the right claim is
  **not retrieved** (`KnowledgeRetriever.java:386`) — a user-visible wrong answer, not a
  laboratory artefact.

Both are measurable today. Both are measurements the system cannot currently make about
itself. That is what this cycle is for.

**What this cycle is not:** it does not add an abstraction layer, it does not widen the
Tsetlin machine, it does not bring C/JNI/FFM/GPU back into the runtime, and it does not
reopen the architecture decision the ADR has already taken.

## 2. The four experiments

### E1 — the rule induction's evidence base — **ADOPT**

**The problem.** Chain B's sleep cycle trains its rule induction on at most 20 episodes
(Article II's `K_MAX`), and on the running gateway's own log that window holds **one class**
and three distinct texts. The reported fidelity is therefore uninformative, and the fold's
240-dimensional kernel cannot be judged: the data never asks the question.

**The benefit.** The peer's "it learned a rule" becomes a claim a user can check, and the
next wave's architecture decision rests on a measurement instead of on a shape.

**The baseline.** The measured numbers of 2026-11-13: 20 episodes, **16 of the 256 feature
bits vary**, all 20 labels positive, fidelity 1.0000, the informative projection 1.0000 as
well, and the original features separating a two-class sample exactly as the folded ones do.

**The competing hypotheses.** (a) the *label rule* (`confidence >= 0.7 && accepted`) is
degenerate — the log holds one refusal in 108 episodes; (b) the *window* is too small and
too homogeneous — 20 episodes of three texts; (c) the *feature* carries too little — 6.5 set
bits of 256, one bit per token.

**The design.** Build the labelled episode set with both classes from the real log plus a
controlled set, measure the separability of the original and the folded representation (the
fold's cost in accuracy points, not its kernel dimension alone), and report the learner's
held-out accuracy on the 80/20 split the engine itself uses.

**The acceptance criteria.** (i) a dataset with **two classes and at least thirty distinct
episodes**, committed with its provenance; (ii) the fidelity reported **with its
distribution** and the fold's cost in accuracy points; (iii) the report states plainly
whether the fold is the bottleneck — and the answer may be "no, the data is".

**The cost.** One research week, no serving change. **The risks.** The controlled set is
synthetic and must be labelled as such. **The recommendation: ADOPT** — the measurement is
the deliverable, and it decides whether the owner's A/B/C question for the fold is worth
answering at all.

### E2 — the retrieval's relevance floor — **ADOPT**

**The problem.** Eleven of the twenty-nine epistemic cases fail because the gold proposition
is not cited: the retriever's relevance floor and its status filter exclude knowledge that
the answer needs. The system's answer is wrong for a reason that is entirely inside the
retriever.

**The benefit.** The answers stop being wrong in the way they are wrong now, and the
retriever's parameters stop being folklore.

**The baseline.** `python3 scripts/w51-epistemic-eval.py` — **18/29**, reproduced on
2026-11-14, the same runner and the same expectations since W52; the eleven failures are
A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02, F-02, R-04, R-05.

**The competing hypotheses.** (a) the floor is too high (recall is the problem); (b) the
status filter excludes claims that should be usable (availability is the problem); (c) the
ranking ignores the question's dimension.

**The design.** For each failing case, measure whether the gold claim is in the candidate
set **before** the ranking (recall@K) and at which rank (MRR); then sweep the floor and the
filter and publish the accuracy/latency curve, using the JMH harness REP-SUBSTRATE left
behind.

**The acceptance criteria.** (i) the eleven failures attributed to a measured cause, not to
a guess — each case says recall or filter, with the number; (ii) a sweep whose chosen point
is the **knee** of the measured curve, with the latency at that point; (iii) the baseline
runner re-run and its number quoted before and after.

**The cost.** One research week; the retriever's parameters are touched, not its design.
**The risks.** A sweep can be fitted to the twenty-nine cases — the held-out set must be
declared in advance. **The recommendation: ADOPT**, because the defect is user-visible and
the measurement is cheap.

### E3 — the interrupted execution — **OPEN — the owner's**

**The problem.** The peer's inbox keeps the request's **digest and not its payload**, so an
execution interrupted by a crash is lost, and the request is answered with a reasoned
failure. The peer's own unfinished rows are reconciled at startup — the work is *closed*
honestly, not *recovered*.

**The benefit.** A distributed task that dies on the peer comes back: the effect is applied
once, and the answer is the real one.

**The baseline.** The crash-boundary test (a request accepted but never finished is visible
after a restart) and the honest limitation in the wave's report.

**The competing hypotheses.** (a) the digest is enough to re-drive the task — it is not, the
payload is gone; (b) the payload is stored at rest — a change to the durable contract, with
a storage and a privacy decision; (c) the loss is accepted and reported.

**The design.** Only after the owner picks: measure the recovery rate, the duplicate-execution
risk (the peer's own three idempotency layers are the control), and the storage cost.

**The acceptance criteria.** To be set with the decision; the criterion is that the crash
test's outcome becomes "the task's result is recovered" rather than "the task is closed as
failed".

**The cost.** A change to the peer's durable row and to the bridge's storage semantics.
**The risks.** A re-executed task may apply its effect twice. **The recommendation: OPEN** —
the durable contract and what may be stored are the owner's, and no option may be chosen
without it.

### E4 — the neuron's compute economy — **DEFER until the end-to-end effect is measured**

**The problem.** The neuron layer costs **146.5 ns per neuron** — a BitSet slice, an array
copy and a BIR call — while the boundary work costs 1.8 ns, and the BIR scalar evaluation
costs 0.643 ns with a reused buffer against 2.255 ns with the runtime's own allocation
(**3.51×**).

**The benefit.** Only if it reaches the user: a faster cognitive cycle.

**The baseline.** The REP-SUBSTRATE arms: `neuronLayerEvaluate` 4 688 ns for 32 neurons,
`birScalarRuntimeAllocates` 2.255 ns against `birScalarReusedBuffer` 0.643 ns.

**The competing hypotheses.** (a) a boundary buffer API removes the slice and the copy;
(b) the form cache's global monitor is the real cost; (c) it does not matter at the system's
real throughput.

**The design.** Implement the API behind a flag, re-measure the same arms in the same
harness, and then measure **the whole cognitive cycle's latency** — a microbenchmark is not
a product claim.

**The acceptance criteria.** A measured end-to-end effect on the cycle, or a published DEFER
with the numbers. **The cost.** A day of work for the experiment, more for the change.
**The risks.** Optimising a path the profile never named. **The recommendation: DEFER** —
the ADR already adopted the API; what is missing is the end-to-end measurement that would
justify landing it.

## 3. The two owner decisions this cycle needs

1. **The fold's replacement** (ADR §4.4, options A/B/C) — E1 measures what it costs; the
   decision stays with the owner, and E1 may well show that it is not the next thing to fix.
2. **The peer's durable contract** (E3) — whether the payload may be stored at rest.

## 4. The standing rules of the cycle

- A number without its method and its environment is not evidence; the artefact is committed
  with the block that made it reproducible.
- A decision without a measurement is an opinion; the table carries the measurement.
- No representation is the substrate of the system because it is newer, smaller or more
  suggestive; the workload decides, and the workload is measured.
- The three properties, in the owner's order: **informational integrity**, **verifiability**,
  **computational economy**.
