# WAL 32 — W58: True autoregressive with KV cache reuse (2026-09-13)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W58: True autoregressive with KV cache reuse (2026-09-13)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W58: True autoregressive with KV cache reuse (2026-09-13)

**Wave 58 RUN 466-467 — BitNetAutoregressive + BitNetModel.generateWithKvCache:**

BitNetAutoregressive.decodeStep: single-token forward that writes K/V
to shared cache and attends to all cached positions with causal mask.

KvCache refactored to support multi-head:
- perPos() = numKvHeads * headDim
- Storage sized correctly for GQA (5 KV heads × 128 head_dim = 640/pos)
- Bounds checking on layerIdx

BitNetModel.generateWithKvCache:
- Prefill via BitNetBlockSequence.forwardSequence (real context window)
- Each generated token uses decodeStep with KV cache reuse
- Fix: layerIdx=0 passed to decodeStep (each cache is per-layer)

REAL BitNet 2B results:
- 'The capital of France is' → 5 tokens in 8165 ms
- 'Hello' → 5 tokens in 4705 ms

**Project total: 817 tests, 0 failures** (was 738 before this wave).

HEAD: e0283d21 in origin/main.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W33

*Auto-extracted by extract-waves.py*
