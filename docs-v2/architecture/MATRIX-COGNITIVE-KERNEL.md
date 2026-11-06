# MATRIX — The cognitive kernel

**Status:** implemented in W44 · 2026-11-02
**Code:** `matrix-core/src/main/java/io/matrix/cognitive/`
**Decision:** `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md`
**Map:** `docs-v2/engineering/W44-CAPABILITY-MAP.md`

This document describes what the kernel IS. It separates the verified implementation from the
intended architecture and from the deferred capabilities, because a document that mixes the three
is worse than no document.

---

## 1. The one-sentence description

A cognitive kernel is a component that takes a **signal** it does not understand, turns it into a
**percept**, consults a **world model** it does not fully trust, makes a **prediction** it
records before it knows the answer, chooses a **computation** by declared preconditions and
measured cost, **acts** inside a bounded sandbox when the model does not settle the question,
**compares** what it predicted with what it observed, and **records the difference** as an
error with provenance instead of quietly adjusting the model.

That is the whole idea. Everything else in this document is detail.

---

## 2. The cycle

```
signal ──▶ PERCEIVE ──▶ RESTORE ──▶ PREDICT ──▶ SELECT ──▶ COMPUTE ──▶ ACT ──▶ OBSERVE ──▶ LEARN ──▶ REGULATE ──▶ ANSWER
             │             │           │          │          │         │         │          │         │
        format only   from cases   recorded    declared   measured  sandbox  case +   validated  bounded
        (never        (the         with the     precondi-  cost      only      evidence  procedure  influence
        meaning)      epistemic    disagreeing  tions                   recorded          on the
                      core)        hypotheses                                        recorded          NEXT
                                                                                                 decision
```

Each phase appends a step to a `LearningTrace`. The trace is append-only: a later
understanding adds a step, it does not edit one. That is the same rule the epistemic core
follows for revisions, so a reader learns one convention.

### 2.1 PERCEIVE — format, never meaning

`ChannelClassifier` classifies a payload by FORM: text, structured record, binary, tabular,
time series, unknown. It reports what the bytes look like and nothing else.

The directive's rule is the design constraint: recognising a PNG signature is not understanding
an image, and recognising a JSON object is not understanding the event. The classifier therefore
never assigns a meaning to a channel, and it never claims to have understood the content. It
also never invents a file type for bytes it does not recognise.

A malformed payload is REFUSED, not downgraded. A payload that looks structured but does not
parse is usually a producer bug, and a text payload usually means a human — the difference is
worth keeping.

### 2.2 RESTORE — the cases are the memory

The micro-world session is rebuilt from the CASES in the knowledge store. There is no private
copy of what the system saw, so a restart cannot make it forget: the episodic memory IS the
epistemic record.

This is the decision that keeps MATRIX from growing a second memory. The alternative — a
separate episode log — would have been a second source of truth about what happened.

### 2.3 PREDICT — recorded before the answer is known

A `Prediction` carries the subject, the predicted value, the basis and the version of the basis,
the confidence, and the cases that support it. It has NO "confirmed" field: whether a prediction
held is decided by the comparison with an `ErrorSignal`, never by the prediction itself. A
prediction that is never compared stays unresolved, and that is a fact worth recording.

An ambiguous prediction stays ambiguous in the record. When the surviving hypotheses disagree,
the prediction records the hypotheses that disagreed.

### 2.4 SELECT — preconditions first, cost second

The selector admits a method only when every precondition the method DECLARES is established
for the request. A blocked method appears in the decision record with the missing precondition
named. Among the admitted methods the score is

```
score = accuracyWeight × accuracy − costWeight × relativeCost
```

where the relative cost is measured when a measurement exists and falls back to the declared
complexity when it does not — and the decision says which of the two it used. There is no
preference order over method names anywhere in the selector.

### 2.5 ACT — three reasons, three targets

| Reason | What is probed | Why |
|---|---|---|
| **Learning** | the assignment that separates the surviving hypotheses | the model does not settle the case |
| **Confirmation of a new assignment** | the assignment the caller asked about | the model is settled, but nobody has observed this input; answering from the rule alone would be an extrapolation presented as an answer |
| **The regulation asks** | the assignment the model already believes it knows | the uncertainty has risen, and a settled model that has just produced a wrong answer is exactly the model that needs checking |

Nothing outside the sandbox is touched, ever. The only action type in W44 is a probe inside the
micro-world.

### 2.6 OBSERVE — the case, the evidence, the error

The outcome is written as a CASE through the W43 store: a first-class observation with the
conditions it was made under and what was seen. The case id is derived from the conditions AND
the outcome, so a repeated write updates the row rather than doubling the evidence.

Whether the observation CONTRADICTS the model is COMPUTED, not assumed: an observation the
surviving hypotheses explain is an example, an observation none of them explains is a
counterexample. Guessing "the lamp was off, therefore the model is wrong" would make every
dark observation a refutation.

Two kinds of error are recorded, and they are different problems:

| Kind | Condition | Disposition |
|---|---|---|
| A confident wrong answer | the model settled on a value and the outcome says otherwise | `UNRESOLVED` |
| An unexplained outcome | no hypothesis in the model accounts for what was seen | `MISSING_CONDITION` |

Neither rewrites the model. The hypothesis set, the earlier observations and the falsified
prediction all stay readable.

### 2.7 LEARN — a fast path only after it is validated

When the model has settled on exactly one hypothesis, the kernel considers promoting a cheaper
procedure. It is promoted only when it agrees with the reference method on every recorded
held-out case. The promoted procedure carries:

- the **applicability domain** (`MODEL_SETTLED` — the facts under which it is valid);
- the **reference algorithm** it falls back to;
- the **measured** agreement and the number of cases it was measured on.

If the model has no account of its own data, nothing is promoted. A promotion on top of an
unexplained observation would bury the finding.

### 2.8 REGULATE — a bounded influence on the NEXT decision

The drives are updated from what happened, and the resulting bias may change the cost weight
the selector uses and may ask for more evidence. It may not change a fact, a confidence value,
a permission or a gate. See `MATRIX-NEUROMODULATION.md`.

---

## 3. Where it runs

| Surface | Where |
|---|---|
| HTTP | `POST /v1/cognitive/observe`, `GET /v1/cognitive/{summary,predictions,errors,procedures,tasks,claims,evidence,cases}`, `GET /v1/cognitive/trace/{episodeId}` |
| Bean | `io.matrix.cognitive.CognitiveService` (`@ApplicationScoped`) |
| Workbench | the "Cognitive" tab of the static workbench UI — the trace of the last cycle, the predictions, the errors, the promoted procedures, the internal tasks and the cases |
| Storage | the SAME SQLite file as the workbench: `data/knowledge/workbench.sqlite`; the cognitive tables live beside the epistemic ones |

The kernel is a plain object with constructor arguments. It is not a bean, it has no plugin
discovery and no service loader. A kernel with no consumer would be the broad framework the
directive warns against; the consumer here is the HTTP endpoint and the workbench panel, and
both were delivered in the same wave.

---

## 4. Verified behaviour

The acceptance tests are in `matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelTest.java`
and they drive the kernel exactly as the endpoint does — a signal in, a cycle out — and then
check the durable record.

| Scenario | What is asserted |
|---|---|
| A malformed signal | refused; the trace names the refusal; no prediction is recorded |
| A readable assignment | a trace with every phase present; the prediction names its basis |
| An observation | becomes a case with its conditions; the case survives a restart over the same file |
| A second cycle | the session was rebuilt from the recorded cases |
| A settled model | a procedure is promoted, with its measured agreement, its applicability domain and its reference fallback |
| An adversarial observation | an error with provenance; the prediction that was falsified is still readable; a bounded task is generated |
| The regulation | with a neutral regulator no confirmation is requested; with a raised uncertainty one is — and the difference is attributable to that one drive |

---

## 5. Deferred, with the reason

| Deferred | Reason |
|---|---|
| A learned meta-controller over the portfolio | The directive says not to build one before a deterministic, measurable baseline exists. W44.2 is the baseline. |
| A second memory hierarchy | The cases are the episodic memory and the promoted procedures are the procedural memory. The other three classes of the five-class taxonomy are PLANNED, not invented. |
| Merging with the brain runtime | A separate application with its own storage; merging it is a separate decision. |
| Multimodal perception | The channel vocabulary exists (`TEXT`, `STRUCTURED`, `BINARY`, `TABULAR`, `TIME_SERIES`, `UNKNOWN`) and the binary branch recognises signatures without interpreting them. The interpretation of a PNG or an audio stream is not implemented. |
| Cross-modal grounding | The world model is one representation. Two representations of one fact are recognised as a NEED, and the reconciliation is PLANNED. |
