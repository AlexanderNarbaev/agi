---
layout: default
title: BIR — Boolean Inference
parent: Algorithms
nav_order: 1
permalink: /ecosystem/algorithms/bir/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# BIR — boolean inference

> **What this page used to describe.** It described BIR as a forward-chaining engine over
> Horn clauses — "similar to Prolog" — with a `RuleBase`, an `AtomStore`, a
> `ForwardChainer` and an `ExplainTracer`. **None of those classes exists in this
> repository.** BIR here is a compiler and a registry of **bit-level functions** in three
> interchangeable forms, and the page below describes that.

## What BIR is

BIR is the logic substrate of MATRIX. An artefact in it is a **boolean function** over a
bounded number of input bits, carried in one of three immutable, interchangeable forms:

| the form | the class | what it is |
|---|---|---|
| **truth table** | `TtForm` | the function, tabulated |
| **clause set** | `ClauseSetForm` | the minimised clause form of the same function |
| **binary decision diagram** | `BddForm` | the same function as a DAG over the input bits |

Every artefact carries a **provenance** and a **fidelity** in `[0, 1]`. A form whose
fidelity is below 1.0 can only be built through the factory that demands a **measured**
fidelity value — a lossy form is never created silently, and the compiler marks the
conversions that are lossy as such.

## The compiler

`io.matrix.bir.BirCompiler` converts between the three forms:

| the conversion | is it exact? |
|---|---|
| TT ⇄ BDD | **exact** |
| TT → CLAUSESET | **exact** — an espresso-type minimisation over the truth table |
| CLAUSESET → TT | only for **k ≤ 20**, where k is the number of input bits |

The truth-table minimisation itself is `io.matrix.compression.TruthTableMinimizer`.

**The bounds.** The constitution (Article II) sets **K_MAX = 20** for the boolean artefacts
— raising it means revisiting every compiler and backend. Separately, `BirLimits` bounds
the literals of a form at `matrix.bir.max-literals`, default **4096**, and a form that
exceeds it cannot be constructed.

## The registry

`io.matrix.bir.BirRegistry` is the catalogue of the artefacts. Each one is registered with

- a unique id,
- a **provenance chain**,
- a **content hash** (SHA3-256) for integrity verification,
- and the **monotonic functionals (Φ)** that make the lineage traceable.

It answers lookups by id, by content hash and by function equivalence; it tracks lineage
across conversions and compositions; it detects duplicates by hash; and it is the audit
trail for the constitution's genesis protocol.

## Evaluation

`io.matrix.bir.BooleanRuntime` evaluates a form. The evaluation is **deterministic**: the
same artefact and the same input bits give the same output bits, every time. There is no
probabilistic sampling anywhere in this path — the stochasticity, where it exists, is in
the cognitive layer, and its seed is a pure function of the state (constitution, Article I).

## Where BIR runs

BIR is stage 6 of the ten-stage cycle of chain B — see
[the mind cycle](/ecosystem/architecture/mindcycle/) — and it backs the `/v1/bir` route of
the gateway and the `io.matrix.bir` package of chain A. It is one of the engines of the
`BirBrainCycle`, and the algorithm portfolio's method selector admits a method only when
every precondition the method declares is present in the request.

## The history, kept as it was

The lineage of the idea is real and worth naming: **Prolog** (Colmerauer, 1972) for logic
programming, **Datalog** (Maier & Warren, 1988) for the database-oriented subset, and
**production systems** (Forgy, 1982) for the Rete pattern-matching optimisation. What this
implementation does *not* do is forward-chain over symbolic atoms — it compiles a boolean
function and evaluates it.

## Pros and cons, as they apply here

| pros | cons |
|---|---|
| fully deterministic; the same input gives the same output | brittle on adversarial input — BIR cannot generalise |
| auditable: the artefact has an id, a provenance chain and a content hash | authoring the artefacts is labour-intensive |
| compact — a compiled form is smaller than a set of neural weights | no uncertainty model; the modulator confidence carries that |
| fast on small and medium artefacts; the BDD form shares structure | it does not read natural language — HDC and the transcoders do that |

## The source

- `matrix-core/src/main/java/io/matrix/bir/` — `Bir`, `BirForm`, `TtForm`, `ClauseSetForm`,
  `BddForm`, `BirCompiler`, `BirRegistry`, `BooleanRuntime`, `BirLimits`, `BirMetrics`.
- The specification: `docs-v2/specifications/SPEC-002-boolean-compute-layer.md`.
- The design: `docs-v2/designs/DESIGN-14-bir-migration.md` and `docs-v2/designs/DESIGN-01-units.md`.
