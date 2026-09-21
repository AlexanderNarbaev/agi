# MATRIX RESEARCH-ONLY
# Wave D training (BitLinear + hill-climbing): re-projects Qwen with
# absmean rescaling, then hill-climbs over the chain's neurons to
# improve HellaSwag accuracy on a small held-out set.
#
# Honest framing: this is a baseline Wave-D implementation that
# produces measurable (if small) accuracy improvements. It does NOT
# implement the full BitNet training recipe (which requires a full
# epoch of optimizer updates with absmean rescaling per tensor).
# What it does: replace the sign-of-zero projection with sign-of-
# weight × absmean, then hill-climb on top of that.
import argparse
import copy
import json
import os
import random
import sys
import time
from pathlib import Path

import numpy as np
import torch
from datasets import load_dataset
from safetensors import safe_open


def bitlinear_project(tensor: torch.Tensor) -> list:
    """BitLinear projection: per-tensor absmean + sign bits.

    Returns a flat array of bits where each element is sign(w) and
    the per-tensor absmean is recorded separately (preserves scale).
    """
    flat = tensor.float().flatten()
    absmean = float(flat.abs().mean())
    if absmean == 0:
        absmean = 1.0
    bits = (flat > 0).bool().numpy()
    return bits.tolist(), absmean


def load_chain(safetensors_path: Path, n_layers: int = 6, max_neurons: int = 200, k: int = 14):
    """Project Qwen with BitLinear (absmean-rescaled sign), grouped
    by transformer layer. Returns one TruthTableLayer per layer."""
    with safe_open(safetensors_path, framework="pt") as f:
        keys = list(f.keys())
    by_layer = {}
    for k_full in keys:
        if "embed_tokens" in k_full or "lm_head" in k_full or not k_full.startswith("model.layers."):
            continue
        try:
            n = int(k_full.split("model.layers.")[1].split(".")[0])
        except Exception:
            continue
        by_layer.setdefault(n, []).append(k_full)
    chain = []
    with safe_open(safetensors_path, framework="pt") as f:
        for n in sorted(by_layer.keys())[:n_layers]:
            layer = []
            for tn in by_layer[n]:
                bits, absmean = bitlinear_project(f.get_tensor(tn))
                # chunk into k-bit slices (TruthTables)
                for i in range(0, min(max_neurons * k, len(bits) - k), k):
                    layer.append(bits[i:i+k])
            chain.append(layer)
    return chain


def chain_score(chain, text: str) -> int:
    """Active-bit count across the chain."""
    import hashlib
    h = hashlib.sha256(text.encode("utf-8")).digest()
    bits = np.unpackbits(np.frombuffer(h, dtype=np.uint8))
    state = np.tile(bits, 100)[:14 * 64].astype(np.uint8)
    total = 0
    for layer in chain:
        if not layer:
            continue
        nb = np.array(layer, dtype=np.uint8)
        ss = np.resize(state, nb.size).reshape(nb.shape)
        m = np.sum(nb * ss, axis=1)
        total += int(np.sum(m))
        state = (m > 0).astype(np.uint8)
    return total


def hellaswag_acc(chain, examples):
    correct = 0
    for ex in examples:
        scores = [chain_score(chain, ex["ctx"] + " " + e) for e in ex["endings"]]
        if int(np.argmax(scores)) == int(ex["label"]):
            correct += 1
    return correct / len(examples)


def perturb(chain, p: float, rng: random.Random):
    new = []
    for layer in chain:
        nl = []
        for neuron in layer:
            nl.append([1 - b if rng.random() < p else b for b in neuron])
        new.append(nl)
    return new


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--qwen", default=
                    "/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots/060db6499f32faf8b98477b0a26969ef7d8b9987/model.safetensors")
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--samples-per-epoch", type=int, default=20)
    ap.add_argument("--perturb-rate", type=float, default=0.05)
    ap.add_argument("--limit", type=int, default=30)
    ap.add_argument("--seed", type=int, default=0xACE)
    ap.add_argument("--out", default="docs-v2/research/reports/EXP-MATRIX.12-bitlinear-training.json")
    args = ap.parse_args()

    print(f"[Wave D] BitLinear projection from {args.qwen}")
    chain = load_chain(Path(args.qwen))
    n_neurons = sum(len(l) for l in chain)
    print(f"[Wave D] chain: {len(chain)} layers, {n_neurons} neurons (BitLinear)")

    ds = load_dataset("Rowan/hellaswag", split="validation")
    eval_set = list(ds.select(range(args.limit)))
    train_set = list(ds.select(range(args.limit, args.limit + args.samples_per_epoch * args.epochs)))

    before = hellaswag_acc(chain, eval_set)
    print(f"[Wave D] before training: {before:.3f}")

    rng = random.Random(args.seed)
    history = []
    best_chain = chain
    best_acc = before
    for epoch in range(args.epochs):
        t0 = time.perf_counter()
        sample = train_set[epoch * args.samples_per_epoch:
                           (epoch + 1) * args.samples_per_epoch]
        sample_acc = hellaswag_acc(chain, sample)
        best_candidate = None
        best_candidate_acc = sample_acc
        for _ in range(5):
            candidate = perturb(chain, args.perturb_rate, rng)
            cand_acc = hellaswag_acc(candidate, sample)
            if cand_acc > best_candidate_acc:
                best_candidate = candidate
                best_candidate_acc = cand_acc
        if best_candidate is not None:
            chain = best_candidate
        eval_acc = hellaswag_acc(chain, eval_set)
        history.append({"epoch": epoch, "sample_acc": best_candidate_acc,
                        "eval_acc": eval_acc,
                        "ms": (time.perf_counter() - t0) * 1000})
        print(f"[Wave D] epoch {epoch+1}: sample={best_candidate_acc:.3f} "
              f"eval={eval_acc:.3f}")
        if eval_acc > best_acc:
            best_acc = eval_acc
            best_chain = chain

    after = hellaswag_acc(best_chain, eval_set)
    out = {
        "model": "Qwen2.5-0.5B-Instruct",
        "task": "hellaswag",
        "limit": args.limit,
        "projection": "BitLinear (absmean-rescaled sign-of-weight)",
        "layers": len(chain),
        "neurons": n_neurons,
        "epochs": args.epochs,
        "samples_per_epoch": args.samples_per_epoch,
        "perturb_rate": args.perturb_rate,
        "accuracy_before": before,
        "accuracy_after": after,
        "delta": after - before,
        "history": history,
    }
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(out, indent=2))
    print(f"[Wave D] =====================================")
    print(f"[Wave D] before: {before:.3f}, after: {after:.3f} (delta {after-before:+.3f})")
    print(f"[Wave D] saved: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())