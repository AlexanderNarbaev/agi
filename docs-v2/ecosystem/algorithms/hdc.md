---
layout: default
title: HDC — Hyperdimensional Computing
parent: Algorithms
nav_order: 2
permalink: /ecosystem/algorithms/hdc/
---

# HDC — Hyperdimensional Computing

## What is HDC?

**HDC** (Hyperdimensional Computing, aka Vector Symbolic Architectures) is a
method of representing and manipulating concepts as **high-dimensional binary
vectors** (typically 10,000 bits). It gives neural-network-like generalization
to purely symbolic systems.

## Mathematical Foundation

A concept is represented as a vector **v ∈ {-1, +1}^D** where D ≈ 10,000.

Three primitive operations:

| Operation | Formula | Purpose |
|-----------|---------|---------|
| **Binding** | c = a ⊗ b = a XOR b | Associate two concepts |
| **Bundling** | c = a ⊕ b = majority(a, b) | Combine multiple concepts |
| **Permutation** | c = ρ(a) | Encode sequence/order |

Similarity is **cosine** (or equivalently Hamming distance for binary vectors):

```
sim(a, b) = (a · b) / (||a|| ||b||)
```

**Key property**: random vectors are approximately orthogonal. With D = 10,000,
the probability that two random vectors have similarity > 0.1 is less than 0.01%.

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/hdc/`](https://github.com/AlexanderNarbaev/agi/tree/develop/matrix-core/src/main/java/io/matrix/hdc)

Key classes:
- `HyperVector` — 10,000-bit binary vector with operations
- `HdcMemory` — append-only store with similarity-based retrieval
- `Codebook` — manages the random basis vectors for known symbols

Performance:
- **Bind**: ~50ns (XOR + bit-count)
- **Bundle**: ~5µs (majority across 100 vectors)
- **Search**: ~100µs for top-K from 1M stored vectors (with locality-sensitive hashing)

## History

HDC was popularized by **Pentti Kanerva** in his 1988 paper
["Sparse Distributed Memory"](https://mitpress.mit.edu/9780262111324/sparse-distributed-memory/).
Key followups:

- **Tony Plate** (1994, 2003) — Holographic Reduced Representations
- **Ross Gayler** (2003) — Vector Symbolic Architectures
- **Dmitri Kanev** et al. (2018) — HDC for few-shot learning

MATRIX uses Kanerva's framework with D = 10,000, binary representation, and
locality-sensitive hashing for fast retrieval.

## Pros

- ✅ Generalizes from few examples (one-shot learning)
- ✅ No neural network training required
- ✅ Pure vector arithmetic, no GPU
- ✅ Excellent for episodic memory
- ✅ Combines well with rules (can store rule preconditions as vectors)

## Cons

- ❌ Capacity limited by D (10,000 bits ≈ 1M-10M distinct concepts)
- ❌ Not as expressive as neural networks for perceptual tasks
- ❌ Hash collisions can cause false positives (mitigated by modulator)
- ❌ Bundle operation loses information (majority is lossy)

## When HDC fires in MATRIX

```
[BIR_RULES_FIRED]
  ↓
[HDC_MEMORY_RETRIEVED]   ← HDC retrieves similar past decisions
   ├─ query vec from input
   ├─ search top-K from 1M+ stored memories
   └─ return similarity-scored results
  ↓
[MCTS_PLAN_SELECTED]
```

Example retrieval:

```java
HyperVector query = encode("capital of France");
List<Hit> hits = memory.topK(query, 5);
// Hits: [(kb-doc-42, sim=0.87), (kb-doc-128, sim=0.72), ...]
```

These hits are recorded in the XAI trace.

## Code example

```java
HyperVector a = HyperVector.random(10_000, seed=42);
HyperVector b = HyperVector.random(10_000, seed=43);
HyperVector c = a.bind(b);  // c = a XOR b

// similarity is high only for related concepts
assert sim(a, a) > 0.99;   // identical
assert sim(a, c) < 0.05;   // orthogonal (bound)
assert sim(a, b) < 0.05;   // orthogonal (random)
```

---

**Last updated:** 2026-09-21 (Wave T-03)
