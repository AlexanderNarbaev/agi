# ADR-2026-09-12-001: Adopt HDC × BitLinear Hybrid Brain as Architectural Primitive

**Status:** Accepted
**Date:** 2026-09-12
**Supersedes:** None
**Related:** DESIGN-54..59, MATRIX-CROSS-DISCIPLINARY-RESEARCH.md, AGENTS.md META-R1..R5

## Context

MATRIX has historically relied on **Multiple-Predicate Decision Tables (MPDT)** for discrete decision making (HierarchicalBrain, NeuronLayer, MultiBrainEnsemble). MPDT is fast and deterministic but:

- Cannot perform cross-modal binding
- Cannot handle compositional generalization (108-sentence grammar test failed)
- Cannot implement Pavlovian conditioning without explicit reward signals
- Cannot implement Spelke core knowledge experiments (object permanence requires persistent code)
- Cannot demonstrate NCA-style emergent self-organization

Recent advances (Microsoft BitNet b1.58 arxiv 2402.17764, Mordvintsev NCA 2020, Kanerva HDC 1988, Plate HRR 1995, Anokhin functional systems, Spelke core knowledge) suggest a complementary paradigm is needed for infant-class cognition.

## Decision

We adopt a **hybrid HDC × BitLinear × Boolean brain** as a new architectural primitive, orthogonal to (not replacing) MPDT. The two primitives serve different use cases:

| Workload | Primitive | Reason |
|----------|-----------|--------|
| Discrete binary decisions, fast lookup | MPDT (existing) | 25-neuron HierarchicalBrain, 32-31 actions |
| Associative memory, compositional generalization, Pavlovian conditioning | HDC (new) | XOR binding, graceful degradation, near-orthogonal random codes |
| Linear transformation in HDC codespace | BitLinear (new) | BitNet b1.58 ternary weights, 8-bit activations |
| Self-organization, regeneration | NCA (new) | Mordvintsev lookup-table rules |

## Architecture

```
io.matrix.neuron (extended package):

  HdcEncoding          (1024-bit bipolar ops: random, permute, bundle, hamming, xor)
  HdcBinding           (bind, unbind, sequence, record, ngram, cleanup)
  CodebookMemory       (LRU cleanup memory: Hamming-distance retrieval)
  HebbianUpdater       (discrete Hebbian + exponential decay)
  BitLinear            (BitNet b1.58 absmean + absmax + SubLN)
  HdcBrain             (integration of all primitives)
  HdcConditioning      (Pavlov operant conditioning)
  SpelkeCoreKnowledge  (object permanence, A-not-B, numerosity, agent/object)
  CrossModalPaired     (audio↔visual bind/unbind via XOR)
  NcaBrainSimulator    (4-channel NCA, 3×3 neighborhood)
  HdcAsLlmPreprocessor (text→HDC for memory-augmented LLM)
  LlmOutputDecoder     (HDC→concept extraction)
  SyntheticGrammarExperiment (Pinker-style grammar learning)
  SokolovHabituationExperiment (Sokolov 1963 neuronal model)
```

14 new classes implementing all 7 Capability Levels (L0-L6) per DESIGN-58.

## Consequences

### Positive

- **CPU-only edge-AI positioning**: 37.9M Hamming ops/sec, 21.4M bind ops/sec on commodity hardware
- **24× memory reduction** vs dense embeddings (128 bytes/code vs ~3KB)
- **Capability milestones achievable**: Pavlov, Spelke core knowledge, cross-modal binding, NCA self-organization, Pinker compositional grammar
- **Cross-disciplinary synthesis**: integrates BitNet, Kanerva HDC, Mordvintsev NCA, Hebbian learning, Sokolov, Spelke — validated against 6+ research schools

### Negative

- 14 new classes to maintain (~3200 LOC added)
- New dependencies: 258 new tests (~25% larger test surface)
- Conceptual complexity increased; new docs (DESIGN-54..59) required

### Neutral

- MPDT remains the primary discrete-decision primitive
- Hybrid MPDTxHDC possible but not yet implemented (future work)
- Native-image build still pending OOM workaround (existing constraint)

## Validation

- 258 W31 tests, 0 failures
- Performance benchmarks at sub-millisecond latency for typical workloads
- L0-L6 capability levels all functional in test environment

## Alternatives Considered

1. **Replace MPDT with HDC**: Rejected — MPDT is faster for binary decisions
2. **GPU-required deep learning**: Rejected — violates edge-AI positioning
3. **Pure HDC without BitLinear**: Rejected — loses linear transformation capability
4. **Pure BitLinear without HDC**: Rejected — loses binding/composition capability

## References

- Kanerva P. (1988). *Sparse Distributed Memory*. MIT Press.
- Plate T. (1995). *Holographic Reduced Representations*. IEEE TNN 6(3).
- Ma S. et al. (2024). *The Era of 1-bit LLMs*. arXiv:2402.17764.
- Mordvintsev A. et al. (2020). *Growing Neural Cellular Automata*. Distill.
- Spelke E. & Kinzler K. (2007). *Core Knowledge*. Developmental Science.
- DESIGN-54..59 in `docs-v2/designs/`
- AGENTS.md META-R1..R5
- W31-ARXIV-PAPER-DRAFT.md
