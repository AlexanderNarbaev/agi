---
layout: default
title: What is MATRIX?
nav_order: 1
parent: Ecosystem
permalink: /ecosystem/concept/
---

# What is MATRIX?

## The Civilization Analogy

MATRIX is a **Hybrid Neuro-Symbolic Civilization**. Each user request spawns
a coordinated sequence of cognitive processes — perception, memory retrieval,
logical inference, planning, action — much like a civilization of specialized
agents.

But unlike a black-box LLM, every step is **auditable**:
1. **BIR** fires explicit rules
2. **HDC** retrieves memories as vectors
3. **MCTS** searches the decision tree
4. **Modulators** (Ethics, Safety, Consistency) gate every output
5. **XAI** traces record each step

## Why Neuro-Symbolic?

| Pure LLM | Pure Symbolic | **MATRIX (Hybrid)** |
|----------|---------------|-------------------|
| ❌ Hallucinates | ❌ Brittle | ✅ Grounded in rules |
| ❌ Cannot explain | ✅ Fully explainable | ✅ Both |
| ❌ $0.01/query cloud cost | ✅ Free local | ✅ Cheap (BIR is O(1)) |
| ❌ Stochastic | ✅ Deterministic | ✅ Seeded Random |
| ❌ Privacy risk | ✅ On-prem | ✅ On-prem |
| ✅ Strong language | ❌ Weak language | ✅ Distilled ONNX for language |
| ❌ No factual memory | ❌ No generalization | ✅ HDC memory |

## The Three Engines

### BIR — Boolean Inference Rules

Logic that runs **first**, before any neural computation. Encodes domain
knowledge as if-then rules over symbolic atoms. Fully deterministic. Auditable.

```java
rule: IF temperature(sensor-1) > 90 THEN alarm()
```

### HDC — Hyperdimensional Computing

Memory as 10,000-bit vectors. Encoding is binding (XOR + majority), retrieval
is similarity (cosine). Generalizes from few examples. **No neural network
required** — pure vector arithmetic on binary vectors.

```java
vec(cat) XOR vec(sits_on) XOR vec(mat) = memory_record
```

### MCTS — Monte Carlo Tree Search

Planning as game-tree search. Rollouts are simulated, best move wins. Used
in AlphaZero. Here it plans **action sequences** over the rule base.

## The Four FROZEN Modulators

These are **immutable** (CONSTITUTION Article IV):
- **ETHICAL_FILTER** — refuses outputs violating ethics
- **SAFETY_MONITOR** — refuses outputs that could cause harm
- **CONSISTENCY_CHECKER** — refuses outputs contradicting prior state
- **LIE_DETECTOR** — refuses outputs that misrepresent the source

All four must agree before any reply is accepted. **No developer can
bypass them.** This is enforced at the matrix-core layer, not the API
gateway.

## How a Query Flows

```
Input
  ↓
[Sensory] transcoders (FFT for audio, Sobel for vision)
  ↓
[BIR] rule firing
  ↓
[HDC] memory retrieval
  ↓
[MCTS] plan search
  ↓
[MODULATORS] 4-way veto
  ↓
Output + Explain trace
```

Every step is recorded. Every step is reproducible. **Every step is auditable.**

## What's Different from an LLM?

The single most important difference: **the LLM is not in the request path.**

A user query never reaches an LLM. It reaches a hybrid reasoning engine that
combines:
- A small **distilled** ONNX model for perception (text, audio, image)
- Pure symbolic algorithms for logic and memory
- Deterministic planning

This means:
- ✅ No hallucination (BIR enforces facts)
- ✅ Explainable (every step is traceable)
- ✅ Reproducible (seeded Random)
- ✅ Cheap to run (no GPU cluster needed for inference)
- ✅ On-prem (your data never leaves your server)

## When to Use MATRIX

✅ **Use MATRIX when:**
- You need explainability (regulated industries: finance, healthcare, legal)
- You need reproducibility (CI/CD, scientific computing)
- You need to run on-prem (data sovereignty)
- You need to integrate symbolic knowledge (rules, ontologies)
- You're building compliance-critical applications

❌ **Don't use MATRIX when:**
- You need free-form creative writing (use an LLM)
- You need real-time web search (use a search API)
- You're prototyping fast with no explainability requirements

---

**Next:** [Quickstart →](/ecosystem/quickstart/)

**Last updated:** 2026-09-21 (Wave T-03)
