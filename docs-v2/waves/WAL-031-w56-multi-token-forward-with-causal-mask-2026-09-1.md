# WAL 31 — W56: Multi-token forward with causal mask (2026-09-13)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W56: Multi-token forward with causal mask (2026-09-13)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W56: Multi-token forward with causal mask (2026-09-13)

**W56 RUN 465 — BitNetBlockSequence:**
- Multi-token forward with proper causal attention mask
- Position i attends to [0..i] only (no future leakage)
- Full transformer block: RMSNorm → Q/K/V proj → RoPE → GQA → attention → attn_sub_norm → o_proj → residual → MLP → residual

**BitNetRmsNorm.forwardSeq:** sequence variant [seq_len, hidden]

**4 tests pass**, including single-token (seq_len=1) and multi-token (seq_len=3).

**Significance:**
- Foundation for proper autoregressive generation with real context windows
- Combined with KvCache, we can do prefill (forward prompt) + decode (single-token forward with cached K/V)
- Generation quality should improve significantly

**Known remaining limitations:**
- BitNetBlockSequence.forwardSequence uses fresh attention (no KV cache reuse)
- BitNetModel.forwardSingleToken still does independent forwards (no prefill)
- True autoregressive loop with KV cache reuse NOT yet implemented

Next: W57 — wire BitNetBlockSequence + KvCache together for true prefill+decode loop.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W32

*Auto-extracted by extract-waves.py*
