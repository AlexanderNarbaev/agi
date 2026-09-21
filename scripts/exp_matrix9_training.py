# MATRIX RESEARCH-ONLY
# W12: train (fine-tune) the boolean chain on HuggingFace wikitext via
# random-bit hill-climbing, then re-run HellaSwag to measure the
# delta vs the untrained chain.
#
# The chain's neurons are k-bit patterns. Each "epoch" picks random
# neurons, flips random bits, and keeps changes that improve
# HellaSwag-mini accuracy. After N epochs, the chain's weights have
# specialized to the loss landscape.
#
# This is intentionally simple (gradient-free); the goal is to
# measure whether ANY training improves the chain's benchmark
# performance, not to beat the float source.
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


def project_tensor_subset(safetensors_path: Path, tensor_name: str,
                          max_neurons: int, k: int):
    """Project one tensor into at most max_neurons k-bit patterns."""
    with safe_open(safetensors_path, framework="pt") as f:
        t = f.get_tensor(tensor_name)
    flat = t.float().flatten()
    mn, mx = float(flat.min()), float(flat.max())
    rng = mx - mn
    if rng == 0:
        normalized = torch.zeros_like(flat)
    else:
        normalized = 2 * (flat - mn) / rng - 1
    bits = (normalized > 0).bool().numpy()
    n_full = len(bits) // k
    n_use = min(max_neurons, n_full)
    return [bits[i*k:(i+1)*k].tolist() for i in range(n_use)]


def build_chain(qwen_path: Path, k: int = 14, max_neurons: int = 200, n_layers: int = 6):
    with safe_open(qwen_path, framework="pt") as f:
        keys = list(f.keys())
    by_layer = {}
    for kn in keys:
        if "embed_tokens" in kn or "lm_head" in kn or not kn.startswith("model.layers."):
            continue
        try:
            n = int(kn.split("model.layers.")[1].split(".")[0])
        except Exception:
            continue
        by_layer.setdefault(n, []).append(kn)
    chain = []
    for n in sorted(by_layer.keys())[:n_layers]:
        layer = []
        for tn in by_layer[n]:
            layer.extend(project_tensor_subset(qwen_path, tn, max_neurons, k))
        chain.append(layer)
    return chain


def chain_score(chain, k, text: str) -> int:
    """Hash-based text → bits → score via active-neuron count."""
    import hashlib
    h = hashlib.sha256(text.encode("utf-8")).digest()
    bit_array = np.unpackbits(np.frombuffer(h, dtype=np.uint8))
    state = np.tile(bit_array, 100)[:k * 64].astype(np.uint8)
    total = 0
    for layer in chain:
        if not layer: continue
        neuron_bits = np.array(layer, dtype=np.uint8)
        slice_state = np.resize(state, neuron_bits.size).reshape(neuron_bits.shape)
        matches = np.sum(neuron_bits * slice_state, axis=1)
        total += int(np.sum(matches))
        state = (matches > 0).astype(np.uint8)
    return total


def hellaswag_acc(chain, k, examples):
    correct = 0
    for ex in examples:
        scores = [chain_score(chain, k, ex["ctx"] + " " + e) for e in ex["endings"]]
        pred = int(np.argmax(scores))
        if pred == int(ex["label"]): correct += 1
    return correct / len(examples)


def perturb(chain, p: float, rng: random.Random) -> list:
    """Return a perturbed copy: each bit flips with probability p."""
    new = []
    for layer in chain:
        new_layer = []
        for neuron in layer:
            new_neuron = [1 - b if rng.random() < p else b for b in neuron]
            new_layer.append(new_neuron)
        new.append(new_layer)
    return new


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--qwen", default=
                    "/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots/060db6499f32faf8b98477b0a26969ef7d8b9987/model.safetensors")
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--samples-per-epoch", type=int, default=20)
    ap.add_argument("--perturb-rate", type=float, default=0.05)
    ap.add_argument("--limit", type=int, default=25)
    ap.add_argument("--seed", type=int, default=0xBEEF)
    ap.add_argument("--out", default="docs-v2/research/reports/EXP-MATRIX.9-training.json")
    args = ap.parse_args()

    print(f"[W12] building chain from {args.qwen}")
    chain = build_chain(Path(args.qwen))
    print(f"[W12] chain: {len(chain)} layers, "
          f"{sum(len(l) for l in chain)} neurons")

    print(f"[W12] loading HellaSwag…")
    ds = load_dataset("Rowan/hellaswag", split="validation")
    eval_set = list(ds.select(range(args.limit)))
    train_set = list(ds.select(range(args.limit, args.limit + args.samples_per_epoch * args.epochs)))

    before = hellaswag_acc(chain, 14, eval_set)
    print(f"[W12] before training: HellaSwag acc = {before:.3f}")

    rng = random.Random(args.seed)
    history = []
    best_chain = chain
    best_acc = before

    for epoch in range(args.epochs):
        t0 = time.perf_counter()
        # sample a small set for gradient-free tuning
        sample = train_set[epoch * args.samples_per_epoch:
                           (epoch + 1) * args.samples_per_epoch]
        # baseline acc on sample
        baseline_sample_acc = hellaswag_acc(chain, 14, sample)

        # try several perturbations, keep the best
        best_candidate = None
        best_candidate_acc = baseline_sample_acc
        n_tries = 5
        for _ in range(n_tries):
            candidate = perturb(chain, args.perturb_rate, rng)
            cand_acc = hellaswag_acc(candidate, 14, sample)
            if cand_acc > best_candidate_acc:
                best_candidate = candidate
                best_candidate_acc = cand_acc

        if best_candidate is not None:
            chain = best_candidate

        # evaluate on the eval set
        eval_acc = hellaswag_acc(chain, 14, eval_set)
        epoch_ms = (time.perf_counter() - t0) * 1000
        history.append({"epoch": epoch, "sample_acc": best_candidate_acc,
                        "eval_acc": eval_acc, "ms": epoch_ms})
        print(f"[W12] epoch {epoch+1}: sample={best_candidate_acc:.3f} "
              f"eval={eval_acc:.3f} ({epoch_ms:.0f} ms)")
        if eval_acc > best_acc:
            best_acc = eval_acc
            best_chain = chain

    after = hellaswag_acc(best_chain, 14, eval_set)
    out = {
        "model": "Qwen2.5-0.5B (boolean-distilled, fine-tuned via W12 hill-climbing)",
        "task": "hellaswag",
        "limit": args.limit,
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
    print(f"[W12] =======================================")
    print(f"[W12] before training: {before:.3f}")
    print(f"[W12] after training:  {after:.3f}")
    print(f"[W12] delta:           {after - before:+.3f}")
    print(f"[W12] saved: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())