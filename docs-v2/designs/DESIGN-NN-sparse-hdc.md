# DESIGN-NN — Sparse-HDC winner-take-all hashing

> Per META-R doctrine F (math of creativity). TRUE-W13 research-engine
> iteration #1.

## Goal

Reduce storage cost of HDC vectors by ~5–10x with negligible fidelity loss.

## Algorithm

**WtaHash**: each token activates exactly `K` bits out of `D` via
FNV-1a-derived scores. The `K` highest-scoring bits win.

```
For each token t, each bit index i in [0, D):
    score(t, i) = FNV1a(t + "#" + i) >> 11 / 2^53  // uniform [0, 1)
Select top-K indices by score.
```

For a phrase: combine via element-wise score-sum across tokens; select top-K.

## Trade-offs

| Variant | D | K | Storage | Cosine fidelity |
|---------|---|---|---------|-----------------|
| Dense (current) | 10000 | ~64 | 1250 B/vector | 1.000 |
| Sparse-WTA | 1024 | 32 | 128 B/vector | 0.93 (expected) |
| Sparse-WTA extreme | 256 | 16 | 64 B/vector | 0.85 (expected) |

## Status (TRUE-W13)

- [x] `WtaHash` class implemented + tested (`WtaHashTest`)
- [x] EvalBattery (34 probes across 6 categories)
- [x] BenchmarkRunner (CSV output to `data/mind/benchmarks/true-w13-eval.csv`)
- [x] Baseline benchmark: **97.1% pass rate at 0.9 ms mean latency** (34 probes, all categories ≥ 83%)
- [ ] Wire into `PersistentHdcStore` as alternate backend (deferred — current mind uses FNV-1a on 256-bit dense, already fast enough)
- [ ] Cross-language WTA evaluation (RU/EN mixed phrases)

## Recommendation

**PROMOTE WtaHash** as an OPTIONAL backend for the HDC layer; default to
dense FNV-1a (current behavior) unless caller requests sparse mode.
The 10x storage win matters only for mind-store with > 1M vectors; at
the current scale (~6 entries), dense is faster and simpler.

**Defer integration** until TRUE-W14 (federation) introduces mind-shard
where storage density matters.
