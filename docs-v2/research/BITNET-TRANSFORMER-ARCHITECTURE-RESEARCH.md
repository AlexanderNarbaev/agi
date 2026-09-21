# BitNet b1.58 Transformer Block Architecture Deep-Research Report

**Date:** 2026-09-12
**Sub-agent:** goal-deep-researcher
**Confidence:** HIGH (verified against official PyTorch source)
**Source:** huggingface/transformers/.../modeling_bitnet.py (HF canonical, co-authored by BitNet team)

## 1. Residual Stream: Standard Llama-Style Pre-Norm

Two RMSNorms per decoder block, both applied **before** their sub-block:

```python
# Block 1: Self-Attention
residual = hidden_states
hidden_states = input_layernorm(hidden_states)         # RMSNorm(2560), eps=1e-5
hidden_states, _ = self_attn(hidden_states)
hidden_states = residual + hidden_states                # residual add AFTER

# Block 2: MLP
residual = hidden_states
hidden_states = post_attention_layernorm(hidden_states) # RMSNorm(2560), eps=1e-5
hidden_states = mlp(hidden_states)
hidden_states = residual + hidden_states                # residual add AFTER
```

No post-norm anywhere.

## 2. BitNetAttention.forward — EXACT order

```python
# 1. Q/K/V projections
q = q_proj(hidden_states).view(B, S, n_heads, head_dim).transpose(1, 2)  # (B, 20, S, 128)
k = k_proj(hidden_states).view(B, S, n_kv_heads, head_dim).transpose(1, 2)  # (B, 5, S, 128)
v = v_proj(hidden_states).view(B, S, n_kv_heads, head_dim).transpose(1, 2)

# 2. RoPE on Q and K only
q, k = apply_rotary_pos_emb(q, k, cos, sin)

# 3. KV cache update (if any)

# 4. Attention: repeat_kv(K/V, 4) → GQA, then softmax(QK^T/√128 + mask)V
k_full = repeat_kv(k, 4)  # (B, 20, S, 128)
v_full = repeat_kv(v, 4)  # (B, 20, S, 128)
attn = softmax(Q @ K_full.T / sqrt(128) + causal_mask) @ V_full

# 5. Reshape → attn_sub_norm → o_proj
attn = attn.transpose(1, 2).contiguous().view(B, S, 2560)
attn = self.attn_sub_norm(attn)   # ← BitNet-specific RMSNorm
attn = self.o_proj(attn)
```

**BitNet-specific**: `attn_sub_norm` is RMSNorm inserted between attention output reshape and `o_proj`. This is the unique BitNet signature.

## 3. BitNetMLP.forward — EXACT order

```python
gate = self.gate_proj(x)      # (B, S, 6912)
up   = self.up_proj(x)        # (B, S, 6912)
hidden = self.act_fn(gate) * up   # relu2(gate) * up, element-wise
hidden = self.ffn_sub_norm(hidden) # ← BitNet-specific RMSNorm
hidden = self.down_proj(hidden)
```

SwiGLU-shaped with relu² activation (not silu). `ffn_sub_norm` is BitNet's signature.

## 4. relu2 Activation

`relu2(x) = (max(0, x))²`

NOT `x * 2`. NOT `max(0, 2x)`. Preserves sign (clamped at 0), then squares.

## 5. RoPE Computation

```python
head_dim = 128  # hidden_size // num_attention_heads
inv_freq = 1.0 / (rope_theta ** (arange(0, head_dim, 2) / head_dim))  # length 64
freqs = inv_freq @ position_ids.T  # (B, S, head_dim/2)
emb = cat((freqs, freqs), dim=-1)  # duplicate for half-split
cos, sin = emb.cos(), emb.sin()

# Apply rotation
def rotate_half(x):
    return cat((-x[..., head_dim//2:], x[..., :head_dim//2]), dim=-1)

q_embed = q * cos + rotate_half(q) * sin
k_embed = k * cos + rotate_half(k) * sin
```

`rope_theta = 500000.0`. Vanilla RoPE (Su et al. 2021), interleaved concat form.

## 6. BitNetRMSNorm

```python
def forward(self, hidden_states):
    input_dtype = hidden_states.dtype
    hidden_states = hidden_states.to(float32)  # upcast for variance
    variance = hidden_states.pow(2).mean(-1, keepdim=True)
    hidden_states = hidden_states * rsqrt(variance + self.variance_epsilon)
    return self.weight * hidden_states.to(input_dtype)  # back to bf16
```

RMSNorm in fp32 for stability, then back. eps=1e-5 (from config).

## 7. lm_head Tied to Embeddings

`tie_word_embeddings: true` → `lm_head.weight = embed_tokens.weight` (same buffer).

**lm_head is NOT BitLinear** — it's plain bf16 matmul `logits = hidden @ embed_tokens.T`.

## 8. CRITICAL: No SubLN at Inference

The `quantization_mode: "offline"` in config means weights are **pre-quantized** (1.58-bit) at training time. At inference:
- **No SubLN** between hidden_states and q_proj
- **No absmean** on activations (hidden_states stay bf16)
- For each BitLinear: unpack uint8 → ternary, multiply by bf16 scale → bf16 matmul

This means our `BitLinearGpuForward` (which does SubLN + absmean + int-matmul) is for **training**. For inference we need a separate path.

## 9. GQA Details

- num_attention_heads = 20
- num_key_value_heads = 5
- num_key_value_groups = 20/5 = 4

KV cache: `[B, 5, 128, max_seq_len=4096]` bf16 per layer. Total: 30 × 10.5 MB = ~315 MB for batch=1.

## 10. Key Numbers for b1.58-2B-4T

| Param | Value |
|-------|-------|
| hidden_size | 2560 |
| num_hidden_layers | 30 |
| num_attention_heads | 20 |
| num_key_value_heads | 5 |
| head_dim | 128 (= 2560/20) |
| intermediate_size | 6912 |
| vocab_size | 128256 |
| max_position_embeddings | 4096 |
| rope_theta | 500000.0 |
| rms_norm_eps | 1e-5 |
| hidden_act | relu2 |
| tie_word_embeddings | true |
| bos_token_id | 128000 |
| eos_token_id | 128001 |

## 11. Per-Layer Parameter Names

Per layer idx 0..29:
- `model.layers.{i}.input_layernorm.weight` (bf16, [2560])
- `model.layers.{i}.post_attention_layernorm.weight` (bf16, [2560])
- `model.layers.{i}.self_attn.{q,k,v,o}_proj.{weight, weight_scale}`
- `model.layers.{i}.mlp.{gate,up,down}_proj.{weight, weight_scale}`
- `model.layers.{i}.self_attn.attn_sub_norm.weight` (bf16, [2560]) — **BitNet-specific**
- `model.layers.{i}.mlp.ffn_sub_norm.weight` (bf16, [6912]) — **BitNet-specific**

Plus:
- `model.embed_tokens.weight` (bf16, [128256, 2560]) — also lm_head
- `model.norm.weight` (bf16, [2560])

## 12. Block Diagram

```
Input x ∈ (B, S, 2560) bf16
│
├─ input_layernorm[i]── RMSNorm(2560, eps=1e-5)
│
├─ q_proj ─→ (B, S, 2560)     [terrain × scale]
├─ k_proj ─→ (B, S, 640)
├─ v_proj ─→ (B, S, 640)
│
├─ reshape to (B, n_heads, S, head_dim=128)
├─ RoPE(Q, K) base=500000, dim/2=64
├─ repeat_kv(K,V, 4) for GQA
├─ softmax(QK^T/√128 + mask) @ V
├─ reshape → (B, S, 2560)
│
├─ ★ attn_sub_norm[i]── RMSNorm(2560)        ← BitNet-specific
│
├─ o_proj ─→ (B, S, 2560)
│
└─⊕ residual add
│
├─ post_attention_layernorm[i]── RMSNorm(2560)
│
├─ gate_proj ─→ (B, S, 6912)
├─ up_proj ─→ (B, S, 6912)
├─ hidden = relu2(gate) * up
│
├─ ★ ffn_sub_norm[i]── RMSNorm(6912)        ← BitNet-specific
│
├─ down_proj ─→ (B, S, 2560)
│
└─⊕ residual add

After 30 layers:
├─ model.norm ─→ RMSNorm(2560)
└─ logits = hidden @ embed_tokens.T  [tied, plain bf16, no BitLinear]
```

## 13. References

1. `huggingface/transformers/.../modeling_bitnet.py` — canonical reference
2. `huggingface.co/microsoft/bitnet-b1.58-2B-4T/config.json` — config values
3. `huggingface/transformers/.../activations.py` — relu2 definition
4. Ma et al. 2024, arXiv:2402.17764 — the BitNet b1.58 paper
5. microsoft/BitNet/gpu/README.md — W2A8 kernel confirmation
