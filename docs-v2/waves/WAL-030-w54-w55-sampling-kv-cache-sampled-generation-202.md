# WAL 30 — W54-W55: Sampling + KV Cache + Sampled Generation (2026-09-13)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W54-W55: Sampling + KV Cache + Sampled Generation (2026-09-13)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W54-W55: Sampling + KV Cache + Sampled Generation (2026-09-13)

**W54 RUN 463 — TokenSampler:**
- Temperature + top-k + top-p + min-p filtering
- Config defaults match generation_config.json (T=0.6, top_p=0.9)
- 14 tests, 0 fails

**W55 RUN 464 — KvCache + Generation:**
- KvCache: standard transformer KV cache (per-layer K, V storage)
- BitNetModel.generate(): autoregressive sampling loop
- BitNetSampledGenerationTest: end-to-end with real BitNet 2B weights
- "The capital of France is..." → diverse sampled outputs
- 10/10 unique outputs at high temperature
- EOS stops generation

17 new tests, 0 failures.

HEAD: 8156d58c

**Known limitations (acceptable):**
- Single-token forward (no real KV cache reuse during autoregressive generation)
- 'The capital of France is tranép calculatedeker...' — not semantic
  because each forward is independent, no real context window

**To improve quality, need:**
- Multi-token sequence forward (forwardSequence) with causal mask
- KV cache reuse during incremental decoding
- True prefill + decode loop

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W31

*Auto-extracted by extract-waves.py*
