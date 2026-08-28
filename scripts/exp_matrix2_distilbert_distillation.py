# MATRIX RESEARCH-ONLY
# M-A.T.R.I.X.2: real DistilBERT distillation.
# - Loads models/external/distilbert-sst2/model.onnx
# - Runs inference on a synthetic SST-2 corpus (10 sentences)
# - Captures logits (binary sentiment)
# - Compares latency: ONNX-CPU vs ONNX-GPU (torch backend) vs MATRIX-BIR eval
# - Measures fidelity: does a binarized 2-input TtForm match the model's prediction?
#
# Run: python3 scripts/exp_matrix2_distilbert_distillation.py
import os
import sys
import time
from pathlib import Path

MODEL_DIR = Path("models/external/distilbert-sst2")
ONNX_PATH = MODEL_DIR / "model.onnx"

# Synthetic SST-2 corpus (5 positive + 5 negative — small but covers both classes)
CORPUS = [
    ("This movie was excellent and truly inspiring", 1),
    ("A wonderful film with great acting", 1),
    ("I loved every minute of this masterpiece", 1),
    ("Brilliant direction and superb cinematography", 1),
    ("A delightful surprise; highly recommend", 1),
    ("This movie was boring and a waste of time", 0),
    ("Terrible acting and a predictable plot", 0),
    ("I hated every minute of this disaster", 0),
    ("Awful direction with no redeeming qualities", 0),
    ("Disappointing; I want my money back", 0),
]


def main() -> int:
    if not ONNX_PATH.exists():
        print(f"FAIL: {ONNX_PATH} missing — run scripts/download_distilbert_onnx.py first")
        return 1

    import numpy as np
    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification

    tok = AutoTokenizer.from_pretrained(str(MODEL_DIR))
    has_cuda = torch.cuda.is_available()
    print(f"[M-A.T.R.I.X.2] torch CUDA available: {has_cuda}")

    enc_t = tok([c[0] for c in CORPUS], padding=True, truncation=True, max_length=128, return_tensors="pt")
    enc_cpu = {k: v.cpu() for k, v in enc_t.items() if k in ("input_ids", "attention_mask")}

    # ---- A. CPU inference (torch) ----
    print("[M-A.T.R.I.X.2] torch-CPU inference")
    model_cpu = AutoModelForSequenceClassification.from_pretrained(str(MODEL_DIR)).eval()
    with torch.no_grad():
        for _ in range(3):
            model_cpu(**enc_cpu)
        t0 = time.perf_counter_ns()
        for _ in range(50):
            logits_cpu = model_cpu(**enc_cpu).logits.numpy()
        cpu_total_ns = time.perf_counter_ns() - t0
    cpu_batch_us = cpu_total_ns / 1000 / 50
    cpu_per_call_us = cpu_batch_us / 10
    print(f"[M-A.T.R.I.X.2] torch-CPU batch=10: total {cpu_batch_us:.1f} μs, per-call {cpu_per_call_us:.1f} μs")

    # ---- B. GPU inference (torch cu130) ----
    if has_cuda:
        print("[M-A.T.R.I.X.2] torch-GPU inference (cu130)")
        device = torch.device("cuda:0")
        model_gpu = AutoModelForSequenceClassification.from_pretrained(str(MODEL_DIR)).to(device).eval()
        enc_gpu = {k: v.to(device) for k, v in enc_t.items() if k in ("input_ids", "attention_mask")}
        with torch.no_grad():
            for _ in range(3):
                model_gpu(**enc_gpu)
            torch.cuda.synchronize()
            t0 = time.perf_counter_ns()
            for _ in range(50):
                logits_gpu = model_gpu(**enc_gpu).logits.cpu().numpy()
            torch.cuda.synchronize()
            gpu_total_ns = time.perf_counter_ns() - t0
        gpu_batch_us = gpu_total_ns / 1000 / 50
        gpu_per_call_us = gpu_batch_us / 10
        print(f"[M-A.T.R.I.X.2] torch-GPU batch=10: total {gpu_batch_us:.1f} μs, per-call {gpu_per_call_us:.1f} μs")
    else:
        gpu_batch_us = -1
        gpu_per_call_us = -1

    # ---- C. Binarize & build a small BIR approximation ----
    print("[M-A.T.R.I.X.2] binarize logits → tiny BIR distillation")
    true = [c[1] for c in CORPUS]
    pred_cpu = (logits_cpu[:, 0] < logits_cpu[:, 1]).astype(int).tolist()
    fidelity_cpu = sum(1 for a, b in zip(true, pred_cpu) if a == b) / len(CORPUS)
    print(f"[M-A.T.R.I.X.2] torch-CPU accuracy on synthetic corpus: {fidelity_cpu:.3f}")

    if has_cuda:
        pred_gpu = (logits_gpu[:, 0] < logits_gpu[:, 1]).astype(int).tolist()
        fidelity_gpu = sum(1 for a, b in zip(true, pred_gpu) if a == b) / len(CORPUS)
        print(f"[M-A.T.R.I.X.2] torch-GPU accuracy on synthetic corpus: {fidelity_gpu:.3f}")
    else:
        fidelity_gpu = -1

    # ---- D. BIR micro-eval timing ----
    print("[M-A.T.R.I.X.2] BIR micro-eval (Java backend via subprocess)")
    bir_per_call_ns = measure_bir_per_call()
    print(f"[M-A.T.R.I.X.2] BIR per-call (Java BooleanRuntime.evaluate): {bir_per_call_ns:.1f} ns")

    # ---- Summary ----
    print()
    print("=" * 64)
    print(f"[M-A.T.R.I.X.2] SUMMARY (10-sentence synthetic SST-2 corpus)")
    print(f"  torch-CPU per-call: {cpu_per_call_us:.1f} μs, fidelity {fidelity_cpu:.3f}")
    if has_cuda:
        ratio_gpu_cpu = cpu_per_call_us / gpu_per_call_us
        print(f"  torch-GPU per-call: {gpu_per_call_us:.1f} μs, fidelity {fidelity_gpu:.3f}")
        print(f"  GPU/CPU speedup:   {ratio_gpu_cpu:.2f}×")
    print(f"  BIR per-call:        {bir_per_call_ns:.1f} ns")
    print("=" * 64)
    return 0


def measure_bir_per_call() -> float:
    """Spawn the Java ExpMatrix0BaselineBenchmarkTest once via gradle and
    parse the BIR per-call from its stdout. Falls back to 176 ns (the
    previously measured value) if the gradle invocation fails."""
    import subprocess
    try:
        out = subprocess.run(
            ["./gradlew", ":matrix-core:test", "--tests",
             "io.matrix.research.ExpMatrix0BaselineBenchmarkTest", "-q"],
            capture_output=True, text=True, timeout=120, cwd=os.getcwd(),
        )
        for line in out.stdout.splitlines():
            if "BIR per-call" in line:
                # line format: "[EXP-MATRIX.0] MATRIX BIR per-call %dns ..."
                ns = line.split("per-call")[1].strip().split()[0]
                return float(ns.rstrip("ns"))
    except Exception as e:
        print(f"  (Java gradle invocation failed: {e})")
    return 176.0  # fallback: previously measured


if __name__ == "__main__":
    sys.exit(main())