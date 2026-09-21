# MATRIX RESEARCH-ONLY
# M-A.T.R.I.X.4: GPT-2 distillation (Python-only).
# - Loads the HuggingFace GPT-2 (text generation, causal LM)
# - Runs inference on a synthetic corpus (10 prompts)
# - Compares latency: torch-CPU vs torch-GPU (cu130)
# - Captures logits; not classified (causal LM), but documents per-call latency
#
# GPT-2 (~125 MB safetensors) skipped the ONNX export path due to
# torch.export FakeTensor issues with dynamic attention; we use the
# HuggingFace model directly via torch (cu130 GPU path is fully wired).
#
# Run: python3 scripts/exp_matrix4_gpt2_distillation.py
import os
import sys
import time
from pathlib import Path

MODEL_DIR = Path(os.environ.get("MATRIX_MODEL_DIR",
                                 "models/external/gpt2"))

# Synthetic prompts for GPT-2 generation
PROMPTS = [
    "The future of artificial intelligence is",
    "In the year 2050, humans and machines will",
    "The most important principle in software engineering is",
    "When designing distributed systems, we must always consider",
    "The difference between a junior and senior engineer is",
    "Climate change requires immediate action from",
    "The history of mathematics begins with",
    "Quantum computing will eventually enable",
    "Modern web applications should prioritize",
    "Open source software has transformed the industry by",
]


def main() -> int:
    if not MODEL_DIR.exists():
        print(f"FAIL: {MODEL_DIR} missing — set MATRIX_MODEL_DIR or download gpt2 first")
        return 1

    import numpy as np
    import torch
    from transformers import AutoTokenizer, AutoModelForCausalLM

    tok = AutoTokenizer.from_pretrained(str(MODEL_DIR))
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    has_cuda = torch.cuda.is_available()
    print(f"[M-A.T.R.I.X.4] torch CUDA available: {has_cuda}")

    enc_t = tok(PROMPTS, padding=True, truncation=True, max_length=64, return_tensors="pt")
    enc_cpu = {k: v for k, v in enc_t.items()}

    # ---- A. CPU inference ----
    print("[M-A.T.R.I.X.4] torch-CPU inference (GPT-2)")
    model_cpu = AutoModelForCausalLM.from_pretrained(str(MODEL_DIR)).eval()
    with torch.no_grad():
        for _ in range(2):
            model_cpu(**enc_cpu)
        t0 = time.perf_counter_ns()
        for _ in range(20):
            out = model_cpu(**enc_cpu).logits
        cpu_total_ns = time.perf_counter_ns() - t0
    cpu_batch_us = cpu_total_ns / 1000 / 20
    cpu_per_call_us = cpu_batch_us / len(PROMPTS)
    print(f"[M-A.T.R.I.X.4] torch-CPU batch={len(PROMPTS)}: total {cpu_batch_us:.1f} μs, per-call {cpu_per_call_us:.1f} μs")

    # ---- B. GPU inference ----
    if has_cuda:
        print("[M-A.T.R.I.X.4] torch-GPU inference (GPT-2 cu130)")
        device = torch.device("cuda:0")
        model_gpu = AutoModelForCausalLM.from_pretrained(str(MODEL_DIR)).to(device).eval()
        enc_gpu = {k: v.to(device) for k, v in enc_t.items()}
        with torch.no_grad():
            for _ in range(2):
                model_gpu(**enc_gpu)
            torch.cuda.synchronize()
            t0 = time.perf_counter_ns()
            for _ in range(20):
                out = model_gpu(**enc_gpu).logits
            torch.cuda.synchronize()
            gpu_total_ns = time.perf_counter_ns() - t0
        gpu_batch_us = gpu_total_ns / 1000 / 20
        gpu_per_call_us = gpu_batch_us / len(PROMPTS)
        print(f"[M-A.T.R.I.X.4] torch-GPU batch={len(PROMPTS)}: total {gpu_batch_us:.1f} μs, per-call {gpu_per_call_us:.1f} μs")

        ratio = cpu_per_call_us / gpu_per_call_us
        print(f"[M-A.T.R.I.X.4] GPU/CPU speedup: {ratio:.2f}×")
    else:
        gpu_per_call_us = -1.0
        ratio = 0.0

    # ---- C. Capture logits for distillation record ----
    print("[M-A.T.R.I.X.4] capturing logits on a single prompt")
    single = tok("The future of AI is", return_tensors="pt")
    with torch.no_grad():
        out = model_cpu(**single).logits[0]
    last_token_logits = out[-1].numpy()
    top5 = last_token_logits.argsort()[-5:][::-1]
    decoded_top5 = [tok.decode([t]) for t in top5]
    print(f"[M-A.T.R.I.X.4] next-token top-5 candidates: {decoded_top5}")

    print()
    print("=" * 64)
    print(f"[M-A.T.R.I.X.4] SUMMARY (GPT-2 batch={len(PROMPTS)} synthetic prompts)")
    print(f"  torch-CPU per-call: {cpu_per_call_us:.1f} μs")
    if has_cuda:
        print(f"  torch-GPU per-call: {gpu_per_call_us:.1f} μs ({ratio:.2f}× vs CPU)")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    sys.exit(main())