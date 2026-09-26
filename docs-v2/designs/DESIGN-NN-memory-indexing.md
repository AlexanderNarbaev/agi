# DESIGN-NN — Memory Indexing via Hierarchical Bloom (META-R R-F math)

> TRUE-W11 research-engine iteration #3.
> Domain: math (efficient set membership for mind memory).

## Goal

Currently the mind's HDC retrieval does O(N) scan over all stored
entries. For mind-state sizes of 10⁶+ entries, this becomes slow.

## Algorithm

A **Hierarchical Bloom filter** (HBF) layers multiple Bloom filters
at different granularities:
- L0 (coarsest): all entries that contain ANY of the top-100 tokens
- L1: all entries that contain ANY of the top-1000 tokens
- L2: all entries that contain the specific 4-gram

A query first checks L0 (fast) and skips entries that L0 rules out.
Then L1 for finer candidates. Then L2 for exact 4-gram match.

## Status (TRUE-W11 iteration 3)

- [x] DESIGN-NN drafted
- [ ] Implementation deferred (the current HDC corpus has < 100 entries)
