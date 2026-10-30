# TLA+ status — what is actually verified

**RECON-W38.4 / RECON-W38.5** · 2026-10-26 → 2026-10-30 · owner directive §14

This document records, per specification, what is *verified* and what is
merely *syntax-checked*. It exists because the previous state of the
repository was misleading in a specific way: the TLA+ CI job ran the parser
only, and the Java-side "formal gate" (`TlaSpecSmokeTest`) is a **text-level**
smoke test that passes on files the TLA+ parser rejects. Nine specifications
had therefore never been parsed by a TLA+ parser at all.

**Current state: all nine specifications parse, all nine are model-checked,
and every configured property holds.** The five specifications that carried a
recorded property failure have been resolved against the Java implementations
they model — see §"The five recorded failures, and how each was resolved".

## How to reproduce

```bash
cd formal
java -cp /tmp/tla/tla2tools.jar tla2sany.SANY  <Spec>.tla    # parse + semantic check
java -cp /tmp/tla/tla2tools.jar tlc2.TLC -config <Spec>.cfg <Spec>.tla
```

Two toolchain facts that are easy to get wrong:

- The main class is **`tlc2.TLC`**. `tlc.TLC` does not exist in the released
  `tla2tools.jar` and fails with `ClassNotFoundException`.
- **SANY exits 0 on semantic errors.** Only parse errors give exit 255. The
  real gate is the absence of an `Unknown operator` / `Parse Error` line. TLC's
  own semantic pass is stricter than SANY's and catches more (for example
  `\supseteq`, which is not a TLA+ operator, was accepted by SANY and rejected
  by TLC).

## Verified — model-checked, all configured properties hold

| Specification | State space (generated / distinct) | Properties evaluated |
|---|---|---|
| `MemoryM4Causal` | 608 001 / 40 000 | `TombstoneIrreversible` (invariant); `MonotonicityExceptTombstone`, `FrozenImmutability`, `EventualConsistency` (properties) |
| `MctsLatsVisit` | 326 593 / 46 656 | `Convergence` (invariant); `VisitsMonotone`, `BestArmIsArgmax`, `TreeAcyclic`, `SearchTerminates` (properties) |
| `Consensus` | 55 057 / 5 476 | `TypeOK` (invariant); `DecisionMonotonic`, `WeightedIdempotent`, `LivenessBounded` (properties) |
| `MPDTNeuron` | 39 568 / 5 184 | `KMaxIsGlobalCeiling` (invariant); `KBounded`, `OutputDeterminedByInputs`, `OutputStableUnderIdempotentInput` (properties) |
| `BotEthicsPipeline` | 14 621 / 7 396 | `TypeOK`, `ApprovalsPlusRejectionsEqualsTicks`, `TombstonesEqualsRejections`, `AuditChainLengthEqualsDecisions`, `AuditLinksAreTyped` (invariants); `BotsMonotonicExpand`, `BotsMonotonic`, `EveryBotEventuallyTicked`, `EventualKillRejection` (properties) |
| `BrcStep` | 5 833 / 729 | `ComposeEndpointPreserved`, `ComposeAssociative`, `ComposeIdentity` |
| `HashChain` | 820 / 820 | `TypeOK`, `HashChainDependency` (invariants); `ChainMonotonic`, `LastHashConsistent`, `TamperDetected`, `RestoreVerifies` (properties) |
| `ConjugateBudgeterDP` | 381 / 91 | all six module properties |
| `FrozenEthicalFNL` | 65 / 16 | all five module properties |

No specification is syntax-checked only, and no property is recorded as
failing.

## The five recorded failures, and how each was resolved

Each was traced to the Java implementation the specification models, and the
model was corrected where it contradicted that implementation. **No property
was weakened, deleted, or replaced with `TRUE` in order to obtain a green
run** — in every case the property now says something stronger or different
from what it used to say, and in one case it was the *code's documentation*
that turned out to be wrong.

### `MPDTNeuron` — hidden state in `Init`

**Was:** `OutputDeterminedByInputs` and `OutputStableUnderIdempotentInput`
violated.

**Cause:** `Init` seeded the memoised output with `FALSE` while seeding the
input with `0`. Whenever a neuron's table contained input 0, the seeded
output contradicted the table — hidden state, which is exactly what the
invariant forbids.

**Evidence:** `io.matrix.neuron.NeuronLayer.evaluate()` computes each
neuron's output **on demand** from its truth table and returns a fresh
`BitSet`. There is no persistent output field in the Java at all, so the Java
has no hidden state to carry.

**Fix:** `Init` now seeds the output from the table
(`outputs = [n \in Neurons |-> (0 \in table[n])]`). The model is **stronger**:
the invariant is now true of every reachable state instead of being
contradicted by the initial state.

### `MemoryM4Causal` — two inversions, and a dead helper

**Was:** `MonotonicityExceptTombstone` and `TombstoneIrreversible` violated.

**Cause 1 (F1):** the property's implication was inverted with respect to its
own comment. As written it said "a key may only APPEAR if no tombstone
covers it" — forbidding *adding* a key, which the `Insert` action does by
construction, so the property was false in the second state of every run.

**Cause 2 (F2):** `Insert`'s guard was `∀t : ⟨k,t⟩ ∈ tombstones[r] ⇒ t > epoch`
— "the insert must be OLDER than every tombstone" — the exact opposite of
F2, which forbids a re-insert at epoch ≤ E. The helper `tombstonedBy` was
**dead code**: referenced by the guard's comment, never called, and defined
with the direction that contradicted F2.

**Evidence:** `io.matrix.noosphere.GrowOnlySet` is a G-Set — "once a neuron
identity is added to the set, it is never removed" — whose only removal path,
`tombstoneAt()`, records a tombstone in the same step. That is F1. The
helper's *name* ("a tombstone covers this epoch") is the right notion; only
its inequality was backwards.

**Fix:** F1 now says what its comment says (a key leaves the state only by
being tombstoned); the helper's direction was corrected to `t ≥ epoch` and it
is now **called** by the `Insert` guard, so the comment, the helper and F2
all agree; the helper was moved above its first use (TLA+ is define-before-use).

### `MctsLatsVisit` — a self-loop, and a search that never runs

**Was:** `TreeAcyclic` and `SearchTerminates` violated.

**Cause 1:** `Expand(parent, child)` had no `child # parent` guard, so
`Expand("n1","n1")` put `"n1"` in `tree["n1"]`.

**Cause 2:** `Spec == Init /\ [][Next]_vars` encoded no fairness, so an
infinite stuttering behaviour was fair and the search could do nothing
forever.

**Evidence:** `io.matrix.federation.liquid.MCTSPlanner.expand()` constructs a
**fresh** `TreeNode` with the expanded node as its parent, so a node is never
its own child; and `backpropagate()` walks `node = node.parent` up the chain,
which requires that chain to terminate. The Java's `plan()` loop always
backpropagates on every iteration, so a search that runs increments a visit
count every time.

**Fix:** `Expand` gained the `child # parent` guard; `Spec` gained
`WF_vars(Next)`.

### `ConjugateBudgeterDP` — a witness that exceeded the envelope, and a false claim

**Was:** `SpentWithinEnvelope` and `LambdaMonotoneTwoStep` violated.

**Cause 1:** the witness reconstruction unioned the "skip" witness with the
"take" witness (`prev[k] \/ take[k]`), so it could select the rows of *both*
solutions — and the union can cost more than the envelope. With
V = (3,5,9) and C = (2,3,4) at envelope 3 it selected rows 1 and 2, costing
5 against an envelope of 3.

**Cause 2:** `LambdaMonotoneTwoStep` asserted that λ(E) = V*(E) − V*(E−1) is
**non-increasing** in the envelope. That is false for a 0/1 knapsack, and the
spec's own row set is a counterexample: V*(7) = 14, V*(8) = 14, V*(9) = 17, so
λ(8) = 0 < λ(9) = 3. A one-unit budget increase can newly enable a whole
row, so the marginal value can rise.

**Evidence:** `io.matrix.budgeter.ConjugateBudgeter.conjugateDp()` tracks the
running budget — `e -= c[i]` when a row is taken — and its "take" witness is
the witness for the *reduced* budget, not a union with the skip witness; it
also takes a row only when taking is at least as good as skipping. The
**same false claim was in the Java's own javadoc** ("lambda monotonicity —
within a single DP, the shadow price is non-increasing in the envelope
(verified by the DP itself)"), and was corrected there too.

**Fix:** `GreedyPick` now follows the Java. `LambdaMonotoneTwoStep` was
replaced by `LambdaBounded`, which asserts what the DP actually guarantees
and what the Java relies on: the shadow price **is** the terminal value slope,
it is non-negative, and it is bounded by the largest single row value. The
false claim was removed from the Java's javadoc, where it had been stated as
a verified invariant.

### `FrozenEthicalFNL` — a model that contradicted its own implementation

**Was:** `DeterministicActivation` violated, and the earlier reading also
reported `NeuronsAreCanonical` and `Liveness`.

**Cause:** the module accumulated one axiom per step into `activated`, while
its three properties asserted things the accumulation cannot satisfy:
`DeterministicActivation` said the fired set never changes;
`NeuronsAreCanonical` said every feature set's fired set equals the full
canonical set (including the ones never evaluated). The three were mutually
inconsistent.

**Evidence:** `io.matrix.ethics.frozen.FrozenAxiomNeuron.activate(BitSet)` is
a **pure function** of (truth table, features) — it recomputes the index and
calls `table.evaluate(idx)`, with no state to accumulate — and
`io.matrix.ethics.frozen.FrozenEthicalFNL.evaluate(BitSet)` calls `activate()`
on **every** neuron against the *same* features and returns the first that
fires. So the fired set is not built up over time; it is computed in one
evaluation and is exactly the set of neurons whose trigger covers that
feature set.

**Fix:** the activation step now evaluates the whole FNL against one feature
set in one step, matching the Java. The three properties were restated to
what the Java guarantees — the fired set is a function of the features alone
(never a partial accumulation); a feature set that has fired anything has
fired exactly the canonical set; and liveness is `<>[]` rather than `[]<>`
(the old shape demanded the biconditional hold from the very first state,
which no evaluation order can deliver). `Spec` gained per-feature-set weak
fairness, so a triggerable feature set is not starved.

## Properties that are checked but prove nothing

These are reported as passing, and they are **vacuous** — the model's shape
means the antecedent is never reachable. A vacuous pass is not a verified
property.

- `BotEthicsPipeline.EventualKillRejection` — its antecedent is
  `[] (lastAction[b] = a /\ Contains("kill", a))`, i.e. the kill action must
  persist in *every* state, which fairness prevents. Separately: the
  specification asserts a kill-trigger rejection policy that **the Java
  implementation does not implement** — `matrix-core/.../BotEthicsPipeline.java`
  has no kill trigger at all. The formal spec is currently *stronger* than the
  code it models.
- `HashChain.TamperDetected` and `HashChain.RestoreVerifies` — the module has
  no action that mutates an existing link and none that sets `verified = FALSE`,
  so the tamper antecedent and the "verification failed" conclusion are
  unreachable.
- `BrcStep` — all three configured invariants are **constant-level**: TLC
  itself warns that each "evaluates to TRUE". They reference only constants
  and stub helpers (`HammingDist == 0`, `SuperStep == s1`) and never touch
  `applied`/`result`/`stepState`. The state machine is not actually
  constrained by them.
- `MctsLatsVisit.Convergence` and `BestArmIsArgmax` — `ArgmaxByReward` is the
  stub `CHOOSE n ∈ Nodes : TRUE` and `Probability(p) == 0`, so neither
  property can fail.

TLC reports the constant-level case explicitly; `BrcStep` is the only
specification that triggers it.

## Defect classes found and repaired

Twenty-one classes, all of them in files that had **never** been parsed. The
list is recorded because "it did not parse" was previously invisible.

**Syntax and well-formedness**

1. `LET` written as `Let`; bindings with `=` instead of `==`.
2. Operators used before they were defined — TLA+ is define-before-use.
3. `\A x \in S, y \in f[x] :` — in a multi-bound quantifier the second bound's
   domain is evaluated **outside** the first binder's scope.
4. `Sum`, `Max`, `Len`, `Seq`, `Records`, `Permutations` used as if they were
   standard TLA+ operators. `Sum` and `Max` exist in no standard module;
   `Len`/`Seq` need `EXTENDS Sequences`; `Records` exists in **no released
   `tla2tools`** at all.
5. `[A]_v` subscript form rejected by SANY when the operand contains `\in`, and
   by TLC when the operand is a state predicate (it must go under
   `PROPERTIES`, not `INVARIANTS`).
6. `[]P` where `P` is an action — TLA+ requires the `[A]_v` form.
7. `[][A]_v` with a nested temporal operator in the operand — a level error.
8. An unbounded set-builder `{x : P}` is not parseable; `{x \in S : P}` is.
9. A quantifier over the infinite set `STRING` inside a temporal formula —
   TLC cannot enumerate it.
10. `SYMMETRY Permutations` — `Permutations(S)` needs an argument.
11. A function-valued constant cannot come from a `.cfg`; it must be
    module-defined from cfg-expressible ingredients.
12. The shipped `Reals` module is a dummy and TLC has no real arithmetic, so a
    ratio must be expressed by cross-multiplication.
13. TLA+ has no ordering on functions, so "monotone" must be element-wise.

**Semantics that contradicted the implementation**

14. `[f EXCEPT ![b] = 0]` does **not** extend a function's domain — every
    `RegisterBot` left the counter functions with their old domain, so
    `TypeOK` was false in every reachable state.
15. `"kill" \in a` — set membership applied to a string. TLC cannot index a
    string at all; `SubSeq` on a string returns a **tuple of characters**, and
    `ToString` of that tuple escapes the characters, so substring tests must
    compare character tuples.
16. `s[1..8]` is not valid substring syntax; `SubSeq(s, 1, 8)` is. Also,
    `"APPROVE:"` has 8 characters and `"REJECT:"` has **7** — a fixed
    8-character window cannot serve both.
17. TLC's `@@` is not the override operator most people assume. `f @@ g`
    keeps **f**'s value wherever f already has the key. In `Consensus` it was
    used to record a vote, so **votes were never recorded at all**: the
    threshold was never reached and the whole consensus model was vacuous.
18. `TypeOK` in `HashChain` declared the link field as `domain` while the
    action built `payload` — and the Java `HashLink` record has no `domain`
    field at all.

**Models that contradicted the implementation they model** (RECON-W38.5)

19. `MPDTNeuron.Init` seeded a memoised output that contradicted the truth
    table; the Java computes the output on demand and has no such field.
20. `MemoryM4Causal`'s F1 was inverted against its own comment, and its
    `Insert` guard was inverted against F2 while the helper encoding the
    right direction sat unused.
21. `MctsLatsVisit.Expand` allowed a node to be its own child, and `Spec`
    encoded no fairness. `ConjugateBudgeterDP.GreedyPick` unioned two
    witnesses and so could exceed the envelope, and its lambda-monotonicity
    property asserted a mathematically false claim — which the Java's javadoc
    also asserted. `FrozenEthicalFNL` accumulated one axiom per step while the
    Java evaluates every neuron against the same features in one call.

## What CI does now

`.github/workflows/tla.yml` (owner directive §14, incident D8 recorded in
`SESSION.md` and `wal/SESSION_WAL.md`):

- runs on `develop` as well as `main`;
- **parses and semantic-checks every specification** (required);
- **model-checks every specification** with TLC (required);
- reports, per specification, the state space and the outcome;
- **fails the job** when a model check fails. The quarantine list is now
  **empty** — every specification's model check passes. The mechanism is kept:
  if a future change makes a model check fail, the specification is named in
  `KNOWN_FAILING` and the job reports it loudly rather than hiding it;
- distinguishes a verified property from a syntax-only check and from a
  recorded failure.
