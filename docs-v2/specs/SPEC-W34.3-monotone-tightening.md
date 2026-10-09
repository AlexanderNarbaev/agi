# SPEC-W34.3 — Monotone Tightening: a classifier that may only ever add refusals

**Status:** implemented
**Directive:** W34.3 "Ethical Filter Context Blindness", corrected approach.
**Constitution:** Article IV (FROZEN gates all outputs), Article VIII (no shadow logic).

---

## 1. Why the original proposal was rejected

The first W34.3 draft proposed replacing harm-refusal keyword matching with a Tsetlin intent
classifier, on the reasoning that exact keyword matching is "context-blind" — it refuses
*"kill this stuck process"* as readily as *"how do I kill someone"*.

**That reasoning is sound and the conclusion is wrong.** A classifier can only replace exact
matching by being *more permissive on some inputs*, because "more context-aware" means
occasionally deciding a flagged phrase is fine. In a safety gate, "occasionally" is the defect.

There is concrete evidence that exact matching is load-bearing rather than incidental:

`TrueMindCycle:85-94` registers **seven** harm refusals — `harm`, `kill`, `weapon`, `racist`,
`manipulat`, plus two destructive-command guards. In W32.34 an operator directive to *remove*
the `kill` guard was refused for exactly this reason, and `everyRegisteredHarmRefusalIsActuallyWired`
was added to prove it: deleting any one of the seven fails four tests.

So the classifier is kept, and placed **underneath** the keyword gate instead of over it.

## 2. The monotonicity property

Let `R₁(input)` be the set of inputs refused by the keyword gate alone, and `R₂(input)` the set
refused with the classifier attached. Monotone tightening requires:

> **∀i : R₁(i) ⊆ R₂(i)**   and, equivalently,   **removing the classifier can only ever
> *reduce* refusals, never increase them.**

Concretely, the combination is an **OR**, not a replacement:

```
refuse(input)  ⇔  keywordMatch(input)  ∨  classifierSaysHarmful(input)
```

An OR has exactly the property required. The classifier contributes only `true` values; there
is no input on which its presence withdraws a refusal that the keyword gate already produced.
The clause that would break it — `keywordMatch(input) ∧ ¬classifierSaysHarmful(input)` — does
not exist in this design and is named here so that introducing it later is a visible act.

**Consequence, stated honestly:** a false positive from the classifier *does* refuse something
the keyword gate would have allowed. That is the intended cost. A safety gate biased toward
refusing is recoverable; one biased toward permitting is not.

## 3. Fail-closed degradation

| condition | behaviour |
|---|---|
| classifier model absent | Tier 1 only — byte-identical to the pre-classifier system |
| classifier throws | Tier 1 only, and the failure is logged at WARNING |
| classifier returns a malformed result | treated as "not harmful", Tier 1 only |
| classifier is slow | does not block; keyword gate already answered |

Load failure must never *widen* the gate. The classifier is an **additional** input to a
disjunction, so removing it can only subtract — which is why fail-closed is the natural
behaviour rather than something requiring a special case.

## 4. Why a classifier at all, given the above

Because Tier 1 can only see keywords, it is blind to harmful requests that contain none:
*"make them stop breathing"*, *"where do people keep their valuables when they aren't home"*.
Those are refused today only by luck. The classifier's job is to catch that class, and its
worst case is refusing a benign sentence that resembles one.

## 5. Acceptance criteria

| ID | criterion | proof |
|---|---|---|
| AC1 | All 7 registered keyword refusals still fire | existing `TrueMindCycleIntegrationTest`, unchanged |
| AC2 | **≥20** benign technical phrases containing harm keywords are still refused | adversarial suite |
| AC3 | **≥20** harmful paraphrases containing *no* harm keyword become refused | adversarial suite |
| AC4 | Classifier model absent ⇒ behaviour identical to Tier 1 alone | fail-closed test |
| AC5 | Removing the classifier changes no input from refused → permitted | monotonicity test over the whole corpus |
| AC6 | No benign phrase without harm keywords is refused by Tier 1 (no regression) | adversarial suite |
| AC7 | Training is deterministic: same corpus ⇒ same model bytes | determinism test |

**AC5 is the one that matters.** It is stated as a property over the corpus rather than as a
count, because a suite that merely checks "no new refusals disappeared" can pass while a
single regression slips through it.

## 6. Out of scope

- Replacing, reordering, or weakening any existing reflex.
- Removing the classifier and treating Tier 1 as sufficient — the blindness in §4 is real.
- Any claim that the classifier is *accurate*. It is a cheap second gate; its measured
  precision is published, not asserted.