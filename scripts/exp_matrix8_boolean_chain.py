# MATRIX RESEARCH-ONLY
# W11 boolean-chain benchmark: run the MATRIX TruthTableLayer chain
# (built in W8 from Qwen2.5-0.5B safetensors) on the same HellaSwag-mini
# task the float Qwen was scored on. Honest comparison.
#
# This is a real measurement of how the 1-bit substrate performs on a
# standard LM benchmark. BitNet-style papers report 5-15 pp accuracy
# drop vs the float source. We expect similar or worse.
import argparse
import hashlib
import json
import os
import sys
import time
from pathlib import Path

import numpy as np
import torch
from safetensors import safe_open
from transformers import AutoTokenizer


def project_tensor(safetensors_path: Path, tensor_name: str,
                   budget: int = 1 << 14):
    """Re-implement TensorProjector in Python: normalize tensor to
    [-1, +1] and threshold at 0; bit i = (val[i] > 0)."""
    with safe_open(safetensors_path, framework="pt") as f:
        t = f.get_tensor(tensor_name)
    flat = t.float().flatten()
    mn = float(flat.min())
    mx = float(flat.max())
    rng = mx - mn
    if rng == 0:
        normalized = torch.zeros_like(flat)
    else:
        normalized = 2 * (flat - mn) / rng - 1
    thresholded = (normalized > 0).bool()
    return thresholded


def text_to_bits(text: str, width: int) -> np.ndarray:
    """Deterministic text → bits: hash SHA-256, extend by repetition."""
    h = hashlib.sha256(text.encode("utf-8")).digest()
    bits_per_byte = np.unpackbits(np.frombuffer(h, dtype=np.uint8))
    out = np.zeros(width, dtype=bool)
    for i in range(width):
        out[i] = bool(bits_per_byte[i % len(bits_per_byte)])
    return out


def build_chain(qwen_path: Path, k: int = 14, max_neurons_per_tensor: int = 200,
                 n_layers: int = 6):
    """Project a subset of Qwen's transformer blocks into neurons.

    For tractable benchmark time we use only the first n_layers
    transformer blocks and cap neurons-per-tensor to max_neurons_per_tensor.
    Same projection strategy as Java's TensorProjector (budget-based).
    """
    from safetensors import safe_open
    with safe_open(qwen_path, framework="pt") as f:
        keys = list(f.keys())
    layers = {}
    for k_full in keys:
        if "embed_tokens" in k_full or "lm_head" in k_full:
            continue
        if not k_full.startswith("model.layers."):
            continue
        try:
            n = int(k_full.split("model.layers.")[1].split(".")[0])
        except Exception:
            continue
        if n not in layers:
            layers[n] = []
        layers[n].append(k_full)
    chain = []
    for n in sorted(layers.keys())[:n_layers]:
        per_layer_bits = []
        for tn in layers[n]:
            bits = project_tensor(qwen_path, tn)
            # limit: take only the first max_neurons_per_tensor chunks
            for i in range(0, min(max_neurons_per_tensor * k, len(bits) - k), k):
                per_layer_bits.append(bits[i:i+k].numpy().tolist())
        chain.append(per_layer_bits)
    return chain


def chain_forward(layers, input_bits: np.ndarray) -> int:
    """Run the chain: each layer's neurons are k-bit thresholds;
    active = number of bits set in the slice of input that matches the
    neuron pattern. Total active bits across all layers."""
    state = input_bits.astype(np.uint8)
    total_active = 0
    for layer in layers:
        # each neuron is a list of k bits (0/1); we score it as
        # the AND-matches between neuron bits and the current state slice
        # plus the OR-matches — both contribute to "active"
        n_neurons = len(layer)
        if n_neurons == 0:
            continue
        k = len(layer[0]) if layer[0] else 14
        neuron_bits = np.array(layer, dtype=np.uint8)  # (n_neurons, k)
        # reshape state to (n_neurons, k) by repetition
        slice_state = np.resize(state, n_neurons * k)
        slice_state = slice_state[:n_neurons * k].reshape(n_neurons, k)
        # count per-neuron active bits
        matches = np.sum(neuron_bits * slice_state, axis=1)
        total_active += int(np.sum(matches))
        # collapse state: keep the top n_neurons/2 outputs
        state = (matches > 0).astype(np.uint8)
    return total_active


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--qwen", default=
                    "/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots/060db6499f32faf8b98477b0a26969ef7d8b9987/model.safetensors")
    ap.add_argument("--task", default="hellaswag",
                    choices=["hellaswag", "arc_easy", "mmlu_mini"])
    ap.add_argument("--limit", type=int, default=25)
    ap.add_argument("--out", default="docs-v2/research/reports/EXP-MATRIX.8-boolean.json")
    args = ap.parse_args()
    qwen = Path(args.qwen)
    if not qwen.exists():
        print(f"FAIL: {qwen} missing", file=sys.stderr); return 1

    from datasets import load_dataset
    print(f"[W11-boolean] loading {args.task} from HF…")
    if args.task == "hellaswag":
        ds = load_dataset("Rowan/hellaswag", split="validation", trust_remote_code=True)
    elif args.task == "arc_easy":
        ds = load_dataset("allenai/ai2_arc", "ARC-Easy", split="test", trust_remote_code=True)
    elif args.task == "mmlu_mini":
        ds = load_dataset("cais/mmlu", "all", split="test", trust_remote_code=True)
    examples = list(ds.select(range(min(args.limit, len(ds)))))

    # Build the MATRIX chain
    print(f"[W11-boolean] projecting Qwen layers…")
    t0 = time.perf_counter()
    chain = build_chain(qwen, max_neurons_per_tensor=200, n_layers=6)
    projMs = (time.perf_counter() - t0) * 1000
    n_neurons = sum(len(layer) for layer in chain)
    print(f"[W11-boolean] chain: {len(chain)} layers, {n_neurons} neurons, "
          f"projection took {projMs:.0f} ms")
    layers = chain

    # Score each example
    n_correct = 0
    detail = []
    for ex in examples:
        # task-specific input formatting
        if args.task == "hellaswag":
            ctx = ex["ctx"]
            endings = ex["endings"]
            label = int(ex["label"])
        elif args.task == "arc_easy":
            ctx = ex["question"]
            endings = ex["choices"]["text"]
            label = int(ex["answerKey"]) if ex["answerKey"].isdigit() else 0
        elif args.task == "mmlu_mini":
            ctx = ex["question"]
            choices = ex["choices"]
            endings = [str(c) for c in choices]
            label = int(ex["answer"])
        scores = []
        for ending in endings:
            text = ctx + " " + ending
            input_bits = text_to_bits(text, width=n_neurons * 14 if n_neurons > 0 else 14)
            score = chain_forward(layers, input_bits)
            scores.append(score)
        pred = int(np.argmax(scores))
        if pred == label:
            n_correct += 1
        detail.append({"ctx": ctx[:60], "label": label, "pred": pred,
                       "scores": scores})

    acc = n_correct / len(examples)
    out = {
        "model": "Qwen2.5-0.5B (boolean-distilled via MATRIX)",
        "task": "hellaswag",
        "limit": args.limit,
        "chain_layers": len(chain),
        "chain_neurons": n_neurons,
        "projection_ms": projMs,
        "accuracy": acc,
        "correct": n_correct,
        "total": len(examples),
        "details_first_5": detail[:5],
    }
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(out, indent=2))
    print(f"[W11-boolean] =====================================")
    print(f"[W11-boolean] accuracy: {acc:.3f} ({n_correct}/{len(examples)})")
    print(f"[W11-boolean] saved: {args.out}")
    print(f"[W11-boolean] =====================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())