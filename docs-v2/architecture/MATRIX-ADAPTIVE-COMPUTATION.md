# MATRIX — Adaptive computation: the algorithm portfolio and the compute controller

**Status:** implemented in W44 · 2026-11-02
**Code:** `matrix-core/src/main/java/io/matrix/cognitive/algorithms/`
**Map:** `docs-v2/engineering/W44-CAPABILITY-MAP.md` §2 (rows 25, and the portfolio section)

---

## 1. The problem this solves

MATRIX already had a large algorithm library — 116 pure-function classes in
`io.matrix.neuron`, plus BIR, Tsetlin, WiSARD, MCTS and HDC — and **not one of them had a
production caller**. What it did not have was any way to CHOOSE between them.

There were three look-alike mechanisms in the repository, and all three fail the requirement:

| Mechanism | Why it does not satisfy the directive |
|---|---|
| `io.matrix.federation.liquid.CognitiveRouter` | the only precondition-driven router, and it is DEAD CODE: its only consumer, `InteractiveChatUI`, has no production caller. Its "preconditions" are cognitive-state scalars (cortisol, dopamine), not properties of the input or the algorithm. It returns a MODE, not a method. |
| `io.matrix.knowledge.AcceptanceModel` | selects Tsetlin against WiSARD by measured accuracy and speed — and never switches: all three of its branches keep the Tsetlin. |
| `io.matrix.brain.runtime.RuleInductionEngine` | does select by held-out fidelity, and it does change behaviour — but it selects between two TRAINERS, on one dataset, and it knows nothing about preconditions. |

And precondition metadata existed in three unrelated shapes — `ActionRegistry`'s string list
(which is really just an input key name), `BrcStepContract`'s functional predicates, and
`KMaxEnforcer`'s hard limits — with none of it attached to an algorithm.

**`Dijkstra` documents "non-negative weights" in its class javadoc and never checks them.**
That single line is the whole argument for this component.

---

## 2. The capability descriptor

Every method offered to the selector declares what it is:

```java
record AlgorithmCapability(
    String id, String version,
    Set<String> problemFamilies,
    Set<String> requiredPreconditions,
    Set<String> supportedInputTypes,
    Set<String> outputTypes,
    ComplexityProfile complexity,
    ResourceProfile resourceRequirements,
    AccuracyProfile accuracyEvidence,
    DeterminismProfile determinism,
    ReliabilityProfile reliability,
    VerificationStrategy verification,
    String fallbackAlgorithmId) {}
```

Three fields carry the weight:

- **`requiredPreconditions`** — the conditions under which the answer is CORRECT. They are
  checked before selection; a violated precondition is a REFUSAL, not a fallback to a wrong
  answer. The method checks them again itself, so a method cannot be run outside its domain by
  a caller that forgot the rule.
- **`accuracyEvidence`** — what was measured, on what, and **with what denominator**. A
  capability with no measurement says so; it is not assumed to be perfect. A score without a
  denominator is not a measurement (CONSTITUTION Article VI).
- **`fallbackAlgorithmId`** — the reference method to use when this method's applicability
  conditions no longer hold. A fast path without a fallback is a trap.

---

## 3. The selection policy

```
admitted  = { m ∈ registry(family) : m.requiredPreconditions ⊆ request.facts
                                   ∧ m.preconditionsHold(payload) }

cost(m)   = measured cost when the ledger has one, otherwise the DECLARED complexity
            (the decision says which of the two it used)

score(m)  = accuracyWeight × accuracy(m) − costWeight × (cost(m) − cheapest) / spread

selected  = the highest-scoring admitted method that fits the budget
```

Four properties this buys:

1. **A method with a violated precondition is never selected.** It appears in the decision
   record with the missing precondition named, so the refusal is auditable.
2. **The cost weight is an INPUT.** The neuromodulation layer supplies it, which is how a
   system under load weighs cost more heavily. It is not a constant inside the selector.
3. **The relative cost is measured, not assumed.** The `CostLedger` accumulates the measured
   nanoseconds per method across cycles; the selector falls back to the declared complexity
   only when nothing has been measured yet, and says so.
4. **The decision is deterministic.** Ties are broken by the method id, so the same request
   always selects the same method.

When nothing is admitted the status is `REFUSED` and the reason names the precondition that
blocked the family. When the winner does not fit the budget the status is `RESOURCE_LIMITED` —
never `COMPLETED`. **An exhausted budget is never reported as a proven result.**

---

## 4. The portfolio

| Method | Family | Preconditions | Fallback |
|---|---|---|---|
| exact integer arithmetic | `arithmetic.exact-integer` | none | — |
| deductive entailment (wraps the EXISTING `io.matrix.knowledge.EntailmentChecker`) | `reasoning.deductive-entailment` | none | — |
| Dijkstra shortest path (wraps `io.matrix.neuron.Dijkstra`) | `graph.shortest-path` | `NON_NEGATIVE_WEIGHTS` | Bellman-Ford |
| Bellman-Ford shortest path (wraps `io.matrix.neuron.BellmanFord`) | `graph.shortest-path` | none | — |
| bounded uniform-cost search | `graph.shortest-path` | none | — |
| collection membership (hash index vs trie) | `collection.membership` | `INDEX_BUILT` | linear scan |
| micro-world reference (exhaustive over the hypothesis space) | `micro-world-outcome` | none | — |
| micro-world settled model (the promoted fast path) | `micro-world-outcome` | `MODEL_SETTLED` | the reference method |

Two of these deserve a note:

- **The Dijkstra wrapper enforces the precondition the library does not.** A negative weight
  produces a refusal, and the fallback is Bellman-Ford. Bellman-Ford's negative-cycle detection
  surfaces as a refusal too — never as a finite shortest path.
- **The bounded search reports `RESOURCE_LIMITED` and a labelled partial result** when it runs
  out of nodes. It never reports a bounded result as proven.

Nothing in the portfolio was written from scratch where an existing implementation could be
wrapped: the entailment checker, Dijkstra, Bellman-Ford, the hash index and the trie are all
existing repository code, used through their existing public signatures.

---

## 5. Learning a fast path from an expensive one

The W44 §22 sequence, as implemented:

1. an unfamiliar problem arrives;
2. the reference method runs and its answer is recorded;
3. a cheaper candidate is proposed;
4. the candidate is compared with the reference on **held-out** cases;
5. the candidate is promoted **only if it agrees with the reference on every case** and the
   case count is above a named minimum;
6. the promoted procedure stores its **applicability domain** and its **reference fallback**;
7. uses and failures are counted;
8. a procedure whose measured agreement falls below the threshold is invalidated, and the
   reference is used again.

Three kinds of reuse are kept apart, because they have different validation requirements:

| Reuse | Valid when | W44's example |
|---|---|---|
| a cached ANSWER | the conditions are equivalent | — (the cases are the record of what was observed) |
| a cached PROCEDURE | the request is inside its verified domain | the settled-model lookup: valid when `MODEL_SETTLED` |
| a learned MODEL | the input is inside its measured distribution | — (W44 does not learn a model; it promotes a procedure) |

---

## 6. The compute budget

`ComputeBudget` is a declaration with four explicit, finite limits: wall clock, CPU,
memory, search nodes. Every limit has a name, and "no limit" is a deliberate value
(`ComputeBudget.unbounded()`) rather than an oversight.

A budget is a DECLARATION, not an enforcement mechanism. A method that exceeds it reports
`RESOURCE_LIMITED` rather than returning a truncated result as if it were complete.

---

## 7. What is NOT in W44

| Not implemented | Why |
|---|---|
| A learned meta-controller over the portfolio | The directive forbids building an elaborate learned policy before a deterministic, measurable baseline exists. W44.2 is that baseline. |
| GPU execution as a selectable backend | `OrtEnvironment.getAvailableProviders()` is never called in the repository and `OnnxRuntimeAdapter.activeProviders()` is hard-coded to `{"CPUExecutionProvider"}`. A capability that advertised GPU would be advertising something no test can falsify. The only honest accelerator today is the JDK Vector API on CPU (`MatrixNativeMath.Backend.CPU_VECTOR`). |
| A compute-provider SPI with capability discovery | The portfolio methods declare their resource needs in the capability descriptor, which is enough for the selection decision. A separate provider SPI would be a second description of the same thing. |
| Energy accounting | There is no wattmeter on this host. The directive asks for energy "where available"; it is not available, so the descriptor does not carry a field that would always be zero. |
