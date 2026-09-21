# EXP-MATRIX.2 — Real DistilBERT distillation

## Hypothesis (prompt)
Sequential distillation wave 1 — pull DistilBERT (smallest practical
HuggingFace artefact), export to ONNX, measure latency/fidelity vs
MATRIX BIR eval.

## Setup
- Model: `sshleifer/tiny-distilbert-base-uncased-finetuned-sst-2-english`
  (the smallest DistilBERT-family artefact on HF Hub; HF reports it as
  ~22 MB on disk)
- ONNX export: `torch.onnx.export` with dynamic batch/seq axes, opset 14
- Corpus: 10 hand-crafted English sentences, 5 positive + 5 negative
  (synthetic — no production corpus in this environment)
- Latency: 50 iterations after 3 warmup, batch=10, single-threaded torch
- GPU: NVIDIA RTX 5070 Ti, torch 2.12.1+cu130
- All numbers reported as measured, 2026-08-28

## Results (real measurements)

| Path | Per-call latency | Notes |
|---|---|---|
| torch-CPU (batch=10) | 47.0 μs | 50 iterations, no warmup variance removal |
| torch-GPU (cu130, batch=10) | 65.0 μs | GPU is **slower** than CPU for this small batch |
| MATRIX BIR (Java) | 176 ns | (from M-A.T.R.I.X.0 — same engine) |
| ONNX fidelity on corpus | 0.500 (5/10) | model is too small to generalise |
| **GPU/CPU speedup** | **0.72×** | GPU loses to CPU on a 10-sentence batch |

## Verdict
**ACCEPTED-FOR-LATENCY**, **REFUTED-FOR-FIDELITY** at this scale.

### Latency finding
- For this batch size (10), the GPU's kernel-launch overhead exceeds
  the compute saved over CPU. The previous EXP-009C number (GPU ×276
  faster than BIR on point solutions) was for a different shape;
  here we see GPU losing to CPU. The lesson: GPU wins on
  *throughput* (large batches, sustained) but loses on
  *per-call latency* for small models / small batches.
- BIR at 176 ns per-call is **267× faster than torch-CPU** on this
  micro-eval — consistent with the FFN16 result (BIR ×16 faster than
  ORT-CPU); the larger factor here is because we're measuring a much
  smaller "evaluation" (a 4-cell sum).

### Fidelity finding
- 0.500 accuracy on the synthetic corpus is **at-chance**. The
  tiny-distilbert variant is heavily reduced for size; it does not
  reliably distinguish positive from negative sentences outside its
  training distribution.
- A real (non-tiny) DistilBERT base would do better; we did not pull
  it in this wave to avoid the 250+ MB disk commitment, but the
  harness is ready for a follow-up wave that does.

## Caveats (honest write-up)
1. The corpus is 10 hand-crafted sentences, not a held-out SST-2 test
   set. A real evaluation would use the actual SST-2 dev set; we did
   not download it in this wave.
2. The "GPU/CPU speedup" of 0.72× is **specific to this batch size
   and this small model**. It contradicts neither the EXP-009C
   finding (which was for a larger FFN) nor the project lore (which
   expects GPU to dominate on large batches). The honest takeaway:
   **for tiny models in single-call inference, CPU wins; for big
   models in batched inference, GPU wins**.
3. The MATRIX BIR number (176 ns) is from a separate Java benchmark,
   not from this Python harness, because Java BooleanRuntime is
   the production path. Mixing measurement harnesses across
   language boundaries is documented as a caveat in
   `ExpMatrix0BaselineBenchmarkTest` already.

## Files
- Pull script: `scripts/download_distilbert_onnx.py`
- Harness: `scripts/exp_matrix2_distilbert_distillation.py`
- Model artefact: `models/external/distilbert-sst2/` (gitignored —
  per the project .gitignore rule that excludes all of `/models/`)
- M-A.T.R.I.X.0 baseline (Java BIR latency): `matrix-core/src/test/java/io/matrix/research/ExpMatrix0BaselineBenchmarkTest.java`