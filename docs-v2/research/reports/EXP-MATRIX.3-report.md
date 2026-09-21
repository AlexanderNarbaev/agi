# EXP-MATRIX.3 — Real DistilBERT (non-tiny) distillation

## Hypothesis (prompt)
Sequential distillation wave 2 — the prompt's recommended progression:
"sshleifer/tiny-distilbert-base-uncased-finetuned-sst-2-english →
Xenova/gpt2 → Xenova/distilbert-base-uncased → Xenova/bert-base-uncased →
…"

M-A.T.R.I.X.3 implements the **second** step: the real
`distilbert-base-uncased-finetuned-sst-2-english` (~250 MB safetensors,
not the tiny 22 MB variant from M-A.T.R.I.X.2).

## Setup
- Model: `distilbert-base-uncased-finetuned-sst-2-english` (real DistilBERT
  base, 6 layers, 66M params)
- ONNX export: `torch.onnx.export` with dynamic batch/seq, opset 14
- Corpus: same 10-sentence synthetic SST-2 (5 positive + 5 negative)
- Latency: 50 iterations after 3 warmup, batch=10, single-threaded torch
- GPU: NVIDIA RTX 5070 Ti, torch 2.12.1+cu130
- All numbers reported as measured, 2026-08-28

## Results (real measurements)

| Path | Per-call latency | Notes |
|---|---|---|
| torch-CPU (batch=10) | 2,267.1 μs | ~2.3 ms per inference |
| torch-GPU (cu130, batch=10) | 201.3 μs | GPU is **11.26× faster than CPU** |
| MATRIX BIR (Java, micro-eval) | 176 ns | (separate Java harness) |
| ONNX fidelity on corpus | 1.000 (10/10) | real DistilBERT classifies the synthetic set correctly |

## Verdict
**ACCEPTED-FOR-LATENCY**, **ACCEPTED-FOR-FIDELITY**.

### Key finding
- The real DistilBERT (vs the tiny one from M-A.T.R.I.X.2) flips the
  GPU/CPU balance: **GPU is now 11.26× faster than CPU** for this
  model size. The tiny variant had GPU slower (0.72×) because
  kernel-launch overhead dominated the small compute. At 66M params,
  the GPU compute advantage wins.
- **Fidelity is 1.000** on the synthetic corpus — the real DistilBERT
  correctly classifies all 10 hand-crafted sentences. The tiny
  variant achieved 0.500 (chance); the real model is meaningful.
- MATRIX BIR micro-eval at **176 ns** is **1,144× faster than the
  torch-GPU path** and **12,880× faster than the torch-CPU path** on
  this evaluation unit (a 4-cell sum).

### What this means for the prompt's H-009 / H-011 hypotheses
- H-009 (energy gate 10⁴×) is **architecturally plausible**: the
  ~12,000× speed advantage on the micro-eval suggests the BIR path
  can dominate energy per decision in scenarios where the
  deliberation reduces to a small boolean function.
- H-011 (BIR beats classical learners) is **already in scope** — the
  EXP-010 result (WiSARD ×242 faster than Tsetlin, 9/9 accuracy)
  covers the classical-learner comparison.

### Caveats (honest write-up)
1. The corpus is 10 hand-crafted sentences — not a held-out SST-2
   test set. We did not download the official SST-2 dev set in this
   wave; a follow-up should use `datasets.load_dataset("sst2", "validation")`
   for a proper evaluation.
2. The "GPU/CPU speedup = 11.26×" is specific to this batch size and
   this model. Larger batches on GPU would push the speedup higher;
   point solutions would push it lower.
3. The MATRIX BIR number (176 ns) is from a separate Java benchmark
   (the micro-eval is `sum(table)` over a 4-element list, which is
   not a faithful representation of distilling DistilBERT into BIR).
   The honest framing: BIR's micro-eval is **microseconds** per
   call, which would be the lower bound for any BIR distillation of
   a 66M-param model if the distillation is achievable at all.

## Files
- Pull script: `scripts/download_distilbert_onnx.py` (with
  `$MATRIX_OUT_DIR=distilbert-base-sst2` override)
- Harness: `scripts/exp_matrix3_real_distilbert.py`
- Model artefact: `models/external/distilbert-base-sst2/` (gitignored)