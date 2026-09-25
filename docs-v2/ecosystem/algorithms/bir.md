---
layout: default
title: BIR — Boolean Inference Rules
parent: Algorithms
nav_order: 1
permalink: /ecosystem/algorithms/bir/
---

# BIR — Boolean Inference Rules

## What is BIR?

**BIR** (Boolean Inference Rules) is the logic engine that fires first in
every MATRIX inference cycle. It encodes domain knowledge as if-then rules
over symbolic atoms, similar to Prolog but optimized for batch evaluation.

## Mathematical Foundation

A BIR rule is a Horn clause:

```
R1: head ← body1 ∧ body2 ∧ ... ∧ bodyn
```

Example:

```
R42: capital(X, "Paris") ← country(X, "France") ∧ capital_is(X, "Paris")
```

**Resolution** is forward-chaining: starting from the input atoms, repeatedly
fire rules whose bodies are satisfied until either:
- A rule fires whose head matches the goal (success)
- No more rules can fire (failure)
- A modulator vetoes the conclusion

Resolution is **deterministic** given the rule order and the input. There is
no probabilistic sampling — every run with the same input produces the same
output.

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java`](https://github.com/AlexanderNarbaev/agi/blob/develop/matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java)

Key components:

- `RuleBase` — indexed collection of rules
- `AtomStore` — deduplicated atom storage
- `ForwardChainer` — fixed-point iteration engine
- `ExplainTracer` — records which rules fired for XAI

Performance:
- **1,000 rules, 10,000 atoms**: ~3ms per cycle
- **10,000 rules, 100,000 atoms**: ~50ms per cycle

## History

BIR is rooted in:
- **Prolog** (Colmerauer, 1972) — the original logic programming language
- **Datalog** (Maier & Warren, 1988) — database-oriented subset
- **Production systems** (Forgy, 1982, Rete algorithm) — pattern matching optimization

MATRIX uses a custom Rete-like forward chainer optimized for the
explainability requirement (every rule fire must be traceable).

## Pros

- ✅ Fully deterministic
- ✅ Auditable (every rule fire is logged)
- ✅ Compact representation (rules are smaller than neural weights)
- ✅ Fast on small/medium rule bases
- ✅ Modulator-friendly (each step is a clean yes/no)

## Cons

- ❌ Brittle on adversarial input (BIR cannot generalize)
- ❌ Rule authoring is labor-intensive
- ❌ Doesn't handle uncertainty well (modulator confidence fills this gap)
- ❌ Doesn't handle natural language well (handled by HDC + ONNX)

## When BIR fires in MATRIX

```
POST /v1/analyze  { input: "What is the capital of France?" }
  ↓
[INPUT_NORMALIZED]
  ↓
[BIR_RULES_FIRED]   ← BIR runs here (typically 3-50ms)
   ├─ rule-42 fires: question(capital_query, France)
   ├─ rule-87 fires: country(France) → capital(Paris)
   └─ 0..N more rules
  ↓
[HDC_MEMORY_RETRIEVED]   ← Next stage
```

The number of rules that fire is logged in the XAI trace.

## Code example

```java
RuleBase rules = new RuleBase();
rules.add(new Rule("capital-of-france",
    List.of(atom("country", "France")),
    atom("capital", "Paris")));

AtomStore atoms = new AtomStore();
atoms.add(atom("country", "France"));

ForwardChainer chainer = new ForwardChainer(rules, atoms);
List<RuleFire> fires = chainer.run();  // [rule-42 fires]
```

---

**Last updated:** 2026-09-21 (Wave T-03)
