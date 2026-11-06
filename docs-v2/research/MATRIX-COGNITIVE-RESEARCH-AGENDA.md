# MATRIX — Cognitive research agenda

**Status:** wave W44 · 2026-11-02
**Scope:** what MATRIX must LEARN before it can claim the next capability level.

This agenda is written against the verified state of the repository, not against an aspiration.
Every "known" item below was verified in W44 by reading the caller graph; every "unknown" is
unknown because nobody measured it.

---

## 1. What W44 established, and what it therefore makes possible

W44 built the first MATRIX component in which the whole loop is present and reachable:

```
perceive → predict → choose a computation → act in a sandbox → compare → record the error →
revise the model → promote a validated cheaper procedure → regulate the next decision
```

The consequences for research are specific:

1. **There is now a place to put an experiment.** Before W44 a hypothesis about MATRIX's
   cognition had no instrument: the only measurable artefacts were retrieval accuracy and
   benchmark scores over a fixed corpus. The kernel produces a TRACE — a sequence of typed
   decisions with the evidence each one used — and the trace is the raw material for every
   question below.
2. **There is now a place to put a counterexample.** The micro-world has a hidden rule and a
   fault injector. An algorithm, a heuristic or a regulation can be attacked and the attack is
   recorded as a case and as an error.
3. **There is now a place to put a comparison.** The portfolio's selector makes a decision from
   declared preconditions and measured costs, and the decision record says which of the two it
   used. A better policy can be written against the same interface and measured against the
   same baseline.

---

## 2. The research questions, ordered by what they block

### Q1 — Does the selector approximate the best available policy? **(blocks: a learned controller)**

**Why it matters.** The W44 selector is a deterministic baseline: accuracy minus a weighted
relative cost, with preconditions as a hard gate. It is explicitly NOT a learned policy, and the
directive forbids building one before the baseline is measurable. To justify a learned policy
later, MATRIX must be able to say how far the baseline is from the best achievable choice on
the same instances.

**What to measure.** For a corpus of labelled instances, the oracle choice is the admissible
method with the highest measured quality per unit cost (or the exact answer, where an exact
answer is computable). Report: the selected-versus-oracle agreement, the quality loss, the
latency reduction, the fallback rate and the invalid-precondition rate — separately for each
task family, never as one aggregate number.

**What would falsify the baseline.** A corpus where the baseline's choice is worse than the
second-best admissible method by more than the cost it saves. That would mean the score is
mis-specified, not that a learned policy is needed.

### Q2 — When does a promoted procedure stop being valid? **(blocks: safe reuse)**

**Why it matters.** W44 promotes a procedure only after it agrees with the reference on every
held-out case, stores its applicability domain and names its reference fallback. The open
question is the one the directive calls model drift: how does the system notice that the world
moved on?

**What to measure.** Introduce a controlled distribution shift in the micro-world (the rule
changes at a known step). Measure: the time from the shift to the first failed use, the time
from the shift to the invalidation, the number of wrong answers served between those two
moments, and whether the reference fallback was actually taken. The expected result is that a
procedure with a NARROW domain fails fast and one with a BROAD domain fails slowly but serves
more wrong answers first — and that trade-off is the thing to measure, not the procedure.

**What would falsify the design.** A case where the procedure keeps being used after the drift
because its applicability facts still hold. That would mean the applicability domain is
expressed in the wrong vocabulary, and the fix is in the vocabulary rather than in the
validator.

### Q3 — What does a prediction error actually tell the system? **(blocks: error-driven revision)**

**Why it matters.** W44 records two kinds of error: a confident wrong answer (`UNRESOLVED`) and
an unexplained outcome (`MISSING_CONDITION`). It does NOT rewrite the model. The research
question is when rewriting is justified, and how a revision can be shown to be an improvement
rather than an accommodation of the latest observation.

**What to measure.** For each error, compare three responses: keep the model unchanged, revise
the scope, and revise the rule. Score each response on held-out instances of the SAME family.
Report the accuracy delta and the number of counterexamples each response would have absorbed.

**What would falsify the design.** If "keep the model unchanged" wins on held-out data in a
regime where the error rate is high, the system's refusal to rewrite is costing accuracy. That
is a real finding and it would change the default.

### Q4 — How much of the regulation's effect is real? **(blocks: calibrated drives)**

**Why it matters.** W44 demonstrates that a drive changes downstream BEHAVIOUR (the regulation
asks for a confirmation the neutral state does not). It does not claim that any constant is
calibrated. The constants are conventions.

**What to measure.** A factorial experiment over the drive space: vary one drive at a time
across its range, hold the model and the data fixed, and measure the downstream effect (the
cost weight the selector used, whether a confirmation was requested, whether a task was
admitted). Report the effect curve, not a single number. Then ask whether the curve is monotone
— a non-monotone drive is a drive with a bug in its semantics.

**What would falsify the design.** A drive whose effect is measurable but whose SIGN flips
inside its range (the load drive making the system CHEAPER below the threshold and DEARER
above it would be fine; a flip in the wrong direction would not).

### Q5 — What is the cost of the safety invariants? **(blocks: the boundary's price)**

**Why it matters.** Every guard in MATRIX costs something: a refusal the user did not need, a
verification the system did not need, a budget spent proving a fact that was already known.
An unmeasured guard is an unpriced guard, and an unpriced guard gets cut the first time
someone is short of budget.

**What to measure.** For each guard in the kernel: the latency it adds, the fraction of
requests it refuses, and the fraction of refusals that were CORRECT refusals (a refusal that
was wrong is a cost the system paid for nothing). Report the ratio of correct refusals to
wrong refusals per guard.

### Q6 — Can a hypothesis space be too wide? **(blocks: the micro-world's realism)**

**Why it matters.** The micro-world starts from ALL 16 Boolean functions of two variables,
because a staged hypothesis space would hide the ambiguity that the directive wants the system
to face. That is honest but it is also a small space: 16 candidates is not a realistic prior.
The research question is whether the system's behaviour depends on the WIDTH of the space, or
only on its consistency with the evidence.

**What to measure.** Repeat the whole learning scenario with hypothesis spaces of 4, 16, 256
and 4096 candidates, and compare: the number of cycles to settle, the number of surviving
hypotheses at each step, the information gain of the chosen experiment, and the cost of the
reference method. If the cycles-to-settle grows with the width, the reference method will not
scale, and that is the finding that justifies a different reference.

### Q7 — Does the trace carry enough to audit a decision? **(blocks: trust)**

**Why it matters.** The kernel's trace is the audit artefact. The W43 baseline found a
constructor guard that was never read and a quality gate whose counter was never read — two
cases where a check existed and did nothing. A trace that is complete but not checkable is the
same failure.

**What to measure.** Take the traces of a hundred cycles and ask, for each, whether an
independent reader can reconstruct: what was perceived, what the model believed, why that
computation was chosen, what was observed, and what changed. Report the fraction that can be
reconstructed WITHOUT reading the source.

---

## 3. The hypotheses that are not yet testable

These are named so that a future wave does not quietly assume them.

| Hypothesis | Why it is not yet testable |
|---|---|
| "The kernel generalises to a new domain" | The only domain is the micro-world. A second domain is required before the word means anything. |
| "The regulatory layer improves the answers" | It changes the DECISIONS, and the decisions are measured. Whether the answers are better is a separate question that needs a ground truth the micro-world does not have. |
| "The portfolio is representative" | It has five families and one domain. The library holds 116 algorithms; the portfolio holds a measured handful. |
| "The promoted procedure is faster in production" | It is measured against the reference method inside the kernel. Whether that difference survives a real workload is not measured. |

---

## 4. The order of work

The questions are ordered by dependency, not by interest:

1. **Q1** — it produces the baseline every later comparison needs.
2. **Q6** — it decides whether the reference method survives contact with a larger space, which
   decides whether Q2 is even measurable.
3. **Q2 and Q3** — together they are the drift-and-revision pair, and they need the same
   instrument: a trace and a counterexample.
4. **Q4 and Q5** — they price the parts of the system that are otherwise free.
5. **Q7** — last, because the trace should not be redesigned before the questions that use it
   are settled.

---

## 5. What must not happen

- A benchmark must not be published as one aggregate number when the results differ materially
  by task family or language. The W43 documents already carry a lesson about this: two
  "accuracy" figures from two instruments with overlapping content were compared as if they
  were a pair.
- A test set must not be used for learning or for threshold tuning. The micro-world's
  validation cases and its held-out cases are separate by construction, and the procedure
  promotion reads only the validation ones.
- A negative result must be published as a negative result. The W44 wave found four dead
  subsystems and one hard-coded GPU provider list; those are findings, not defects of the wave.
