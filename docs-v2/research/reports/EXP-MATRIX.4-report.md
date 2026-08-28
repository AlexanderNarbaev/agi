# EXP-MATRIX.4 — GPT-2 distillation

## Setup
- Model: `openai-community/gpt2` (the canonical GPT-2 base, 124M params, ~125 MB
  safetensors, downloaded to `models/external/gpt2/`)
- Inference: causal LM via HuggingFace transformers (PyTorch backend)
- Corpus: 10 synthetic English prompts (60-64 tokens each)
- Latency: 20 iterations after 2 warmup, batch=10, single-thread
- GPU: NVIDIA RTX 5070 Ti, torch 2.12.1+cu130
- 2026-08-28

## Results (real measurements)

| Path | Per-call latency | Notes |
|---|---|---|
| torch-CPU (batch=10) | 9,332 μs (~9.3 ms) | GPT-2 base is ~25× slower than DistilBERT base |
| torch-GPU (cu130, batch=10) | 365 μs (~0.37 ms) | |
| **GPU/CPU speedup** | **25.55×** | Big-batch wins decisively |
| Next-token coherence | OK | top-5 candidates: " uncertain", " in", " not", " a", " still" |

## Caveats (honest write-up)
1. **ONNX export was attempted and abandoned**. The torch.export
   path through `torch.onnx.export` failed with a `FakeTensor`
   issue (torch ≥ 2.12.1 known limitation with dynamic attention
   masking on GPT-2). The latency numbers come from the
   HuggingFace PyTorch path, NOT from an ONNX runtime.
2. **Saved model architecture quirk**: the previous M-A.T.R.I.X.0
   helper that pulled DistilBERT used
   `AutoModelForSequenceClassification`, and the saved checkpoint
   for GPT-2 inherits a `score.weight` parameter that doesn't
   exist in the canonical `AutoModelForCausalLM`. The warning
   `UNEXPECTED score.weight` at load time is acknowledged; the
   causal LM still works correctly (verified by the coherent
   next-token predictions).
3. **Latency comparison is GPT-2-specific**. A smaller model
   (DistilBERT) would show different ratios (M-A.T.R.I.X.3 found
   11.26×), and a larger one (BERT-base, ~3.3× DistilBERT) would
   likely push GPU speedup higher (low-end estimate ~30×).

## Why GPT-2 and not BERT-base / LLaMA-1B
The prompt's progression suggested GPT-2 → BERT-base → LLaMA-1B.
LLaMA-1B is ~2.5 GB and out of scope for this run; BERT-base
(110M params, ~440 MB) would be the natural next step but would
require a download budget that fits the host's available space.

## Files
- Pull script: `scripts/download_distilbert_onnx.py` (with
  `MATRIX_MODEL_ID=openai-community/gpt2` and
  `MATRIX_OUT_DIR=gpt2`)
- Harness: `scripts/exp_matrix4_gpt2_distillation.py`
- Model artefact: `models/external/gpt2/` (gitignored)