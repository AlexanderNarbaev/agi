# WAL 27 — W46: BitNet transformer architecture (sub-agent research)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W46: BitNet transformer architecture (sub-agent research)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W46: BitNet transformer architecture (sub-agent research)

**Sub-agent (goal-deep-researcher) provided full transformer architecture:**

Forward pass per decoder layer:
1. input_layernorm (RMSNorm 2560, eps=1e-5)
2. Q/K/V projection via BitLinear (uint8×scale→bf16 matmul)
3. Reshape to (B, n_heads, S, head_dim=128)
4. RoPE on Q,K only (base=500000, interleaved concat)
5. GQA repeat K/V by 4 (n_kv_groups=4)
6. softmax(QK^T/√128 + mask) @ V
7. Reshape → attn_sub_norm (BitNet-specific RMSNorm) → o_proj
8. Residual add
9. post_attention_layernorm (RMSNorm 2560)
10. gate/up projections (BitLinear)
11. hidden = relu2(gate) * up where relu2(x) = max(0,x)²
12. ffn_sub_norm (BitNet-specific RMSNorm 6912)
13. down_proj
14. Residual add

After 30 layers: model.norm → logits = hidden @ embed_tokens.T (tied, plain bf16).

**Critical inference finding:** No SubLN, no absmean at inference. These are
training-time operations. We need a separate inference-time path.

HEAD: 7355de72 → 22 commits ahead of pre-W34.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W28

*Auto-extracted by extract-waves.py*
