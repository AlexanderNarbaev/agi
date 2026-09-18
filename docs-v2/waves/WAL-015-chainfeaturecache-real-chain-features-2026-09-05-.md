# WAL 15 — ChainFeatureCache + real chain features (2026-09-05 13:05)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** ChainFeatureCache + real chain features (2026-09-05 13:05)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— ChainFeatureCache + real chain features (2026-09-05 13:05)

- New io.matrix.api.ChainFeatureCache: SHA-256 keyed cache for question → boolean[]
  chain output. Disk-backed at data/chain_feature_cache.bin (~24 MB).
- LmHeadTrainer.trainOne uses featureCache.getOrCompute(question) instead of FNV-1a
  hash fingerprint. Real chain output is corpus-aligned.
- 10/10 ChainFeatureCacheTest pass; 7/7 LmHeadTest still pass.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W16

*Auto-extracted by extract-waves.py*
