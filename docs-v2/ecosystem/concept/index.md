---
layout: default
title: What is MATRIX?
nav_order: 1
parent: Ecosystem
permalink: /ecosystem/concept/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# What is MATRIX?

MATRIX is a **deterministic neuro-symbolic core**. Knowledge is represented as truth
tables and their compilable forms, the learning path is separated from the runtime, and a
runtime decision is a boolean chain. The system's own description is in
[`docs-v2/governance/PROJECT-STATE.md`](../../governance/PROJECT-STATE.md), and the
capability-by-capability record is in
[`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](../../architecture/CAPABILITY-REGISTRY.yml).

> **What this page used to call it.** An earlier version of this page described MATRIX as a
> "hybrid neuro-symbolic civilization" and compared it with an LLM in a table of absolute
> claims — "no hallucination", "fully explainable", "free local". None of those are
> measurable, the constitution forbids them in writing (Article VI), and the metaphor
> obscured the one fact that matters: **a large part of this repository is implemented and
> is not served**. The analogy is gone; the measurements are here instead.

## The three engines

### BIR — boolean inference

A BIR artefact is a **bit-level function** with a bounded number of input and output bits
(`matrix.bir.max-literals`, default 4096) and a **provenance**, compiled into one of three
immutable forms: a **truth table** (`TtForm`), a **clause set** (`ClauseSetForm`) or a
**binary decision diagram** (`BddForm`). Every form carries a **fidelity** in `[0, 1]`, and
a form that is lossy can only be constructed through the factory that demands a *measured*
fidelity — a lossy form is never created silently.

Deterministic, auditable, and fast. The rules live in the knowledge base as text and are
compiled into forms; the runtime executes only compiled artefacts.

### HDC — hyperdimensional computing

Patterns as binary vectors of **1024 bits** (`HdcEncoding.DIM` — 128 bytes per vector,
Hamming distance over packed `long` words). Encoding is binding by XOR and majority;
retrieval is similarity. It generalises from few examples, and it needs no neural network:
it is arithmetic over binary vectors.

### MCTS — Monte Carlo tree search

Planning as a search over a tree, in `io.matrix.mcts`.

Alongside the three engines, an **algorithm portfolio** admits a method only when every
precondition the method declares is present in the request, and ranks the admitted methods
by measured data — eight methods over four families, `SERVED` with a named serving path
(registry row `W44-ALGO-PORTFOLIO`).

## What the difference from an LLM is, precisely

**The LLM is not on the decision path.** The gate scans the runtime modules of both chains
for legacy LLM imports and fails the build on a hit; the reproduced value is **0**, and it
runs as a blocking CI job (registry row `C-01b`, verification `MEASURED`).

What follows, and what does not:

| follows | does not follow |
|---|---|
| a decision can be read back as a boolean chain, auditable through `x-matrix-trace` and a hash chain (constitution, Article VIII) | that the answer is **correct** — a trace explains a decision, it does not certify it |
| the same state and the same seed give the same answer | that the system is free of errors; a wrong rule produces a reproducible wrong answer |
| the numerical substrate is a pure function: no randomness, no wall-clock read | that every component is deterministic — the cognitive layer may use seeded randomness where the seed is a pure function of the state (Article I) |
| it runs on a CPU, and the GPU is an **optional** accelerator (OD-047) | that it is cheaper than any alternative; that comparison was never measured, and the published "33,000×" was refuted (registry row `C-02`) |
| it can be run on your own machine | that there is a hosted service to point it at — there is none |

## When to use it, and when not to

**Use it when** the decision has to be explainable and reproducible: a regulated domain, a
scientific pipeline, a CI gate, a system that must run on your own hardware, or a workload
where the rules are known and can be written down.

**Do not use it for** free-form generation, open-web retrieval, or a prototype with no
explainability requirement — an LLM is the better tool there, and the constitution's ban is
on the *decision path*, not on the world.

## The honest limits

| | |
|---|---|
| a large part of the research subsystems is **implemented and unserved** | the registry states each capability on four axes; `SERVED` requires a named serving path |
| the **gateway serves a stub by default** | `MATRIX_MODE=production` is what starts the real brain |
| the **native-image build is blocked** | the GraalVM toolchain is absent on the development host |
| the **GPU is optional**, and on a single-point call it is about 4× slower | the measured 12.06× is on the serving path, over three runs |
| the **corpus properties skip on CI** | the corpus is the owner's and is not in git. A skip is not a pass |

## Next

- [Quickstart →](/ecosystem/quickstart/)
- [What the system is →](/story/overview.html)
- [The runtime topology →](../../architecture/RUNTIME-TOPOLOGY.html)
