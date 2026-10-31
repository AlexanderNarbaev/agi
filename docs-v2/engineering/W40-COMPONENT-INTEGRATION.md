# W40.6 — what was integrated, and what was measured before deciding

Owner directive §24 asked for the existing components to be connected into
a real learning loop, and for the Tsetlin, WiSARD, HDC and BIR to be
compared with what is already there, tested for an advantage, and integrated
**only when that advantage is justified**.

This is that record. Every row states what was measured, not what was
hoped.

## What was integrated

| Component | Where | What it now does | Measurement |
|---|---|---|---|
| Tsetlin (`io.matrix.tsetlin.TsetlinTrainer`) | `io.matrix.knowledge.AcceptanceModel` | Classifies whether an answer will be accepted, from explicit features, and its decision function is distilled into an exact clause set | Trained on 11 outcomes, measured on 11 held-out items: 100%, the same as the deterministic grader |
| WiSARD (`io.matrix.tsetlin.WisardProducer`) | `io.matrix.knowledge.AcceptanceModel` | The same task, trained in the same pass, as the comparison | 100%, in 54 microseconds against the Tsetlin's 521 — **about ten times faster at the same accuracy** |
| BIR (`io.matrix.bir.ClauseSetForm`) | `io.matrix.knowledge.AcceptanceModel` | The learned rule is distilled into it, so what the learner concluded can be read | The distilled rule is a clause set over the feature vector, evaluated and described |

## What was measured and NOT integrated

### The HDC (`io.matrix.neuron.HdcEncoding`) — near-duplicate questions

The episodic log has to decide whether two questions are the same one. Two
methods were measured on the same labelled pairs: the exact Jaccard overlap
of the two token sets, and the HDC encoding (each content term projected
onto a random bipolar vector, the vectors bundled by majority vote, the
bundles compared by similarity).

| Method | Lowest same-question similarity | Highest different-question similarity | Misclassifications |
|---|---|---|---|
| Exact overlap | 0.333 | 0.000 | 1 of 6 |
| Hyperdimensional | 0.494 | 0.020 | 1 of 6 |

**Verdict: not integrated.** The two methods separate the labelled pairs
equally well. The hyperdimensional margin is wider, but the sample is six
pairs, and the exact method is exact, free, and readable — a user can see
why two questions were treated as one. A wider margin on six pairs is not
grounds for replacing an exact computation with an approximation.

The measurement is in `io.matrix.knowledge.QuestionSimilarity` and its test,
so the comparison can be repeated with more pairs and the verdict revisited.

### The WiSARD in the production path — measured, recorded, not acted on

The WiSARD is about ten times faster than the Tsetlin on this task, at the
same accuracy. That is its own documented claim, and it holds here.

It is **not** put into the production path yet, and the reason is stated in
the result rather than hidden: the sample is eleven training outcomes and
eleven held-out items, and that is too small a basis for changing the
production path. The measurement is reported on every call, so the decision
can be made when the data is bigger.

### `AgentBrainService.generateFromBir()` — removed

It trained on constant-`true` labels, never read the corpus answers, hashed
the first 64 characters of the text, folded a 64-bit value into 20 bits for
training while evaluating the raw 64 bits when serving, and returned one bit
rendered as ASCII — so it could only ever be `""` or `"!"`. The caller
required eight characters, so the branch was unreachable: it could not affect
a single response. The method, the corpus trainer it called and the field it
filled are gone, and the removal is recorded in the code where it was.

## What was measured and found to be a capacity limit, not a tuning choice

The Tsetlin machine with ten clauses and five automaton states does not learn
even "every answer naming the key terms is acceptable": it predicts FALSE
for its own training data. With the counts the production induction path
uses (64 clauses, 200 states) it learns. The learner therefore uses the
production counts.

The distillation of the rule enumerates the whole input space, so at twenty
bits it costs minutes. The features are sixteen bits wide, which is sixty-four
thousand vectors and fast enough for a test, and the distillation is lazy and
cached rather than on the measurement path.

## What the deterministic grader does

It stays in the production path. It is exact, it needs no training, and it
cannot be wrong about a key term. Every measurement in this record is a
comparison against it, and in every case it is the thing being compared
against, not the thing being replaced.
