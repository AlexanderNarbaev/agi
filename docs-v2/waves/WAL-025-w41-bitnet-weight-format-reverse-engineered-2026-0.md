# WAL 25 — W41: BitNet weight format reverse-engineered (2026-09-12)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W41: BitNet weight format reverse-engineered (2026-09-12)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W41: BitNet weight format reverse-engineered (2026-09-12)

**Sub-agent deep-research (goal-deep-researcher) found:**

BitNet b1.58 2B weight format (from microsoft/BitNet/utils/convert-hf-to-gguf-bitnet.py):
- Each uint8 byte holds 4 ternary values, 2-bit each
- Bit positions in byte: [1:0]→v[0], [3:2]→v[1], [5:4]→v[2], [7:6]→v[3] (LSB first)
- Encoding: 0b00=-1, 0b01=0, 0b10=+1, 0b11=unused
- Packed row i → dequantized rows [4i, 4i+1, 4i+2, 4i+3]
- weight_scale: bf16 [1] per-tensor scalar (mean(|W_orig|))
- Memory-efficient: keep packed, multiply scale at epilogue

Empirically verified: distribution ~30%/-1, 40%/0, 30%/+1, mean(|W_dequant|) ≈ 0.946 matches theory.

Other tensors (bf16): embed_tokens (tied with lm_head), layer norms, sub_norms.
No biases. 30 layers × 7 weight tensors = 210 packed weights + 210 scales.

Source: docs-v2/research/BITNET-WEIGHT-FORMAT-RESEARCH.md (to be written)

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W26

*Auto-extracted by extract-waves.py*
