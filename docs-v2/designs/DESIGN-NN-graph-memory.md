# DESIGN-NN — Graph-Neural Memory Indexing (R-A SOTA ML)

> TRUE-W11 research-engine iteration #11.
> Domain: SOTA ML / graph neural networks.
> Source: Scarselli, F. et al. (2009) "The Graph Neural Network Model"

## Goal

Implement a small **graph-of-memory** structure where:
- Nodes = mind entries (facts, learned concepts)
- Edges = co-occurrence relationships (entries seen together)
- Edge weights = co-occurrence count
- **Spread activation**: query an entry → activation propagates
  along weighted edges to related entries

This gives the mind a way to recall related-but-not-exact memories,
complementing the HDC cosine retrieval.

## Status (TRUE-W11 iteration #11)

- [x] DESIGN-NN drafted
- [x] Prototype: GraphMemoryIndex (node add, edge weight, spread activation)
- [ ] Integration with PersistentHdcStore (deferred)
