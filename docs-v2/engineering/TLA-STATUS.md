# TLA+ status — what is actually verified

**RECON-W38.4** · 2026-10-26 · owner directive §14 ("Formal specification quality")

This document records, per specification, what is *verified* and what is merely
*syntax-checked*. It exists because the previous state of the repository was
misleading in a specific way: the TLA+ CI job ran the parser only, and the
Java-side "formal gate" (`TlaSpecSmokeTest`) is a **text-level** smoke test
that passes on files the TLA+ parser rejects. Nine specifications had therefore
never been parsed by a TLA+ parser at all, and no specification had ever been
model-checked.

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

| Specification | State space (generated / distinct) | Properties evaluated | Result |
|---|---|---|---|
| `BotEthicsPipeline` | 14 621 / 7 396 | `TypeOK`, `ApprovalsPlusRejectionsEqualsTicks`, `TombstonesEqualsRejections`, `AuditChainLengthEqualsDecisions`, `AuditLinksAreTyped` (invariants); `BotsMonotonicExpand`, `BotsMonotonic`, `EveryBotEventuallyTicked`, `EventualKillRejection` (properties) | **all hold** |
| `BrcStep` | 5 833 / 729 | `ComposeEndpointPreserved`, `ComposeAssociative`, `ComposeIdentity` | **all hold** |
| `Consensus` | 55 057 / 5 476 | `TypeOK` (invariant); `DecisionMonotonic`, `WeightedIdempotent`, `LivenessBounded` (properties) | **all hold** — and **not vacuous**: both `APPROVED` and `REJECTED` decisions are reachable |
| `HashChain` | 820 / 820 | `TypeOK`, `HashChainDependency` (invariants); `ChainMonotonic`, `LastHashConsistent`, `TamperDetected`, `RestoreVerifies` (properties) | **all hold** |

## Recorded property failures — the check runs and genuinely fails

These are **not** passing checks. TLC runs, explores the state space, and
reports a violated property. Each one is a defect in the specification that
needs an author decision; none was weakened, rewritten to `TRUE`, or removed
from its configuration to obtain a green run.

| Specification | State space | Violated property | Assessment |
|---|---|---|---|
| `ConjugateBudgeterDP` | 381 / 91 | `SpentWithinEnvelope`, `LambdaMonotoneTwoStep` | **Two spec bugs.** (1) `GreedyPick` reconstructs the witness by unioning the "skip" witness with the "take" witness (`prev[k] \/ take[k]`) **without any budget check**, so the reconstructed set can exceed the envelope even though the DP table itself is correct. (2) `LambdaMonotoneTwoStep` is a mathematically false claim — the marginal value of a 0/1 knapsack is *not* non-increasing in the envelope — and the formula also subtracts `DPValue(e0-1)` rather than `DPValue(e1-1)`, so it does not even express the λ its comment describes. |
| `FrozenEthicalFNL` | 65 / 16 | `DeterministicActivation`, `NeuronsAreCanonical`, `Liveness` | **The spec contradicts itself — three ways.** (1) `Activate` accumulates (`activated' = [activated EXCEPT ![features] = activated[features] ∪ {neurons[axiom]}]`), while the same module's FROZEN INVARIANT 2 asserts `activated'[features] = activated[features]` (the fired set never changes). (2) INVARIANT 4 demands that *every* feature set's activation set already equals the full canonical set, but `Next` fires one axiom per step, so it can never establish that in one step. (3) `Liveness` ("eventually some neuron fires") is unprovable because `Spec` has no fairness assumption, so an infinite stuttering behaviour is fair. |
| `MctsLatsVisit` | 156 301 / 32 756 | `TreeAcyclic`, `SearchTerminates` | **Two spec bugs.** (1) `Expand(parent, child)` lacks `child # parent`, so a node may become its own child — the counterexample is `Expand("n1","n1")` giving `tree["n1"] = {"n1"}` — although the property's comment says "no cycles". (2) `SearchTerminates` is unprovable for the same reason as `FrozenEthicalFNL.Liveness`: `Spec` has no `WF_vars(Next)`, so the search can simply never visit anything. |
| `MemoryM4Causal` | 817 265 / 56 252 | `MonotonicityExceptTombstone`, `TombstoneIrreversible` | **Two spec bugs.** (F1) `MonotonicityExceptTombstone` has its implication inverted with respect to its own comment — it forbids *adding* a key, which `Insert` does by construction. (F2) `Insert`'s precondition only blocks epochs ≥ the tombstone epoch, so a tombstoned key can be re-inserted at a lower epoch; the helper `tombstonedBy` (dead code) encodes the opposite direction from what a tombstone-wins CRDT needs. |
| `MPDTNeuron` | 50 912 / 6 724 | `OutputDeterminedByInputs`, `OutputStableUnderIdempotentInput` | **One spec bug with two symptoms.** `Init` hard-codes `outputs = FALSE` while `inputs = 0`, so any `table[n]` containing 0 seeds hidden state. A one-line `Init` fix (`outputs = [n \in Neurons |-> (0 \in table[n])]`) makes all four properties hold — verified on a scratch copy, **not applied**, because it changes what `Init` means. |

## Syntax-checked only — no property was model-checked

| Specification | Reason |
|---|---|
**None.** All nine specifications now parse and all nine have a model
configuration, so every specification is model-checked. Five of the nine
report a property failure; those are in the table above.

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
- `BrcStep` — all three configured invariants are constant-level: they
  reference only constants and stub helpers (`HammingDist == 0`,
  `SuperStep == s1`) and never touch `applied`/`result`/`stepState`. TLC itself
  warns that each "evaluates to TRUE". The state machine is not actually
  constrained by them.

## Defects found and fixed during this wave (syntax and modelling only)

Every one of these was a file that had **never** been parsed. The list is
recorded because "it did not parse" was previously invisible.

1. `LET` written as `Let`; bindings with `=` instead of `==` (`BrcStep`).
2. Operators used before they were defined — TLA+ is define-before-use
   (`BrcStep`, `MctsLatsVisit`, `MemoryM4Causal`).
3. `\A x \in S, y \in f[x] :` — in a multi-bound quantifier the second bound's
   domain is evaluated **outside** the first binder's scope, so `f[x]` is
   ill-formed (`MPDTNeuron`, `BotEthicsPipeline`).
4. `Sum`, `Max`, `Len`, `Seq`, `Records`, `Permutations` used as if they were
   standard TLA+ operators. `Sum` and `Max` exist in no standard module;
   `Len`/`Seq` need `EXTENDS Sequences`; `Records` exists in **no released
   `tla2tools`** at all.
5. `[A]_v` subscript form rejected by SANY when the operand contains `\in` and
   by TLC when the operand is a state predicate (it must go under
   `PROPERTIES`, not `INVARIANTS`).
6. `[f EXCEPT ![b] = 0]` does **not** extend a function's domain — every
   `RegisterBot` left the counter functions with their old domain, so `TypeOK`
   was false in every reachable state (`BotEthicsPipeline`).
7. `"kill" \in a` — set membership applied to a string. TLC cannot index a
   string at all; `SubSeq` on a string returns a **tuple of characters**, and
   `ToString` of that tuple escapes the characters, so substring tests must
   compare character tuples.
8. `s[1..8]` is not valid substring syntax; `SubSeq(s, 1, 8)` is. Also,
   `"APPROVE:"` has 8 characters and `"REJECT:"` has **7** — a fixed 8-character
   window cannot serve both.
9. `[]P` where `P` is an action — TLA+ requires the `[A]_v` form.
10. `[][A]_v` with a nested temporal operator in the operand — a level error.
11. An unbounded set-builder `{x : P}` is not parseable; `{x \in S : P}` is.
12. A quantifier over the infinite set `STRING` inside a temporal formula —
    TLC cannot enumerate it. The bot and action universes are now model
    constants.
13. `SYMMETRY Permutations` — `Permutations(S)` needs an argument; a bare
    reference is an operator, not a set of permutations.
14. `TypeOK` in `HashChain` declared the link field as `domain` while the
    action built `payload` — and the Java `HashLink` record has no `domain`
    field at all.
15. **TLC's `@@` is not the override operator most people assume.**
    `f @@ g` keeps **f**'s value wherever f already has the key
    (`f @@ g == [x \in DOMAIN f \cup DOMAIN g |-> IF x \in DOMAIN f THEN f[x] ELSE g[x]]`).
    In `Consensus` it was used to record a vote, so **votes were never
    recorded at all**: the threshold was never reached and the whole consensus
    model was vacuous (64 states, every property trivially true). Replacing it
    with `EXCEPT` turned it into a 5 476-state model in which decisions are
    actually reached. `@@` *does* extend a function's domain for keys that `f`
    does not have, which is why it is the right operator for registering a new
    bot in `BotEthicsPipeline` — but it is the wrong one for replacing a value.
16. A TLC configuration file cannot express a function, a tuple, or a real —
    only sets of numbers/strings/model values, numbers, strings, and
    `TRUE`/`FALSE`. Every function-valued constant therefore has to be
    module-defined from cfg-expressible ingredients.
17. The shipped `Reals` standard module is a dummy (`a / b == CHOOSE m \in
    Real : m * b = a`), and TLC has no real value type. Comparisons that need
    a ratio must be expressed by cross-multiplication.

## What CI does now

`.github/workflows/tla.yml` (owner directive §14):

- runs on `develop` as well as `main` (previously `main` only, so the working
  branch never triggered it);
- **parses and semantic-checks every specification** (required);
- **model-checks every specification that has a `.cfg`** with TLC (required);
- reports, per specification, the state space and the outcome;
- **fails the job** when a model check fails, except for the five
  specifications listed in `KNOWN_FAILING`, which are reported loudly as
  failures with a pointer to this document — they are not hidden and they are
  not counted as passing;
- reports specifications without a configuration as *syntax-checked only*.
