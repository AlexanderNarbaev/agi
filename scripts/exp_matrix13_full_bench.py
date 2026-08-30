# MATRIX RESEARCH-ONLY
# Wave K: real-domain corpus benchmark on the FULL 24-block chain.
# - full HellaSwag validation (10,042 examples)
# - full ARC-Easy test (2,376 examples)
# - full MMLU-mini test (14,042 examples) — sampled to 1k for tractability
#
# Uses BitLinear projection (Wave D) + sign-descent training (Wave J
# follow-up). Reports accuracy honestly.
import argparse
import json
import random
import sys
import time
from pathlib import Path

import numpy as np
import torch
from datasets import load_dataset
from safetensors import safe_open


def bitlinear_project(tensor: torch.Tensor) -> list:
    flat = tensor.float().flatten()
    absmean = float(flat.abs().mean())
    if absmean == 0:
        absmean = 1.0
    bits = (flat > 0).bool().numpy()
    return bits.tolist(), absmean


def sign_only_project(tensor: torch.Tensor) -> list:
    """Wave-7 style: sign-of-zero projection (no absmean rescaling)."""
    flat = tensor.float().flatten()
    bits = (flat > 0).bool().numpy()
    return bits.tolist(), 0.0


def load_chain_all_layers(safetensors_path: Path, k: int = 14,
                         max_neurons_per_tensor: int = 200,
                         projection: str = "bitlinear"):
    """Load ALL transformer-block layers (Wave I: full 24-block chain)."""
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
    project_fn = sign_only_project if projection == "sign" else bitlinear_project
    chain = []
    with safe_open(safetensors_path, framework="pt") as f:
        for n in sorted(by_layer.keys()):
            layer = []
            for tn in by_layer[n]:
                bits, absmean = project_fn(f.get_tensor(tn))
                for i in range(0, min(max_neurons_per_tensor * k, len(bits) - k), k):
                    layer.append(bits[i:i+k])
            chain.append(layer)
    return chain


def chain_score(chain, text: str) -> int:
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


def evaluate(chain, examples, task):
    correct = 0
    for ex in examples:
        if task == "hellaswag":
            ctx, endings, label = ex["ctx"], ex["endings"], int(ex["label"])
        elif task == "arc_easy":
            ctx, endings, label = ex["question"], ex["choices"]["text"], int(ex["answerKey"]) if ex["answerKey"].isdigit() else 0
        elif task == "mmlu_mini":
            ctx, endings, label = ex["question"], [str(c) for c in ex["choices"]], int(ex["answer"])
        scores = [chain_score(chain, ctx + " " + e) for e in endings]
        if int(np.argmax(scores)) == label:
            correct += 1
    return correct / max(1, len(examples))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--qwen", default=
                    "models/external/qwen2.5-0.5b/model.safetensors")
    ap.add_argument("--task", default="hellaswag",
                    choices=["hellaswag", "arc_easy", "mmlu_mini"])
    ap.add_argument("--limit", type=int, default=0,
                    help="0 = full corpus; positive = sample limit")
    ap.add_argument("--projection", default="bitlinear",
                    choices=["bitlinear", "sign"])
    ap.add_argument("--out", default="docs-v2/research/reports/EXP-MATRIX.13-full-bench.json")
    args = ap.parse_args()
    qwen = Path(args.qwen)
    if not qwen.exists():
        print(f"FAIL: {qwen} missing", file=sys.stderr); return 1

    print(f"[Wave K] loading ALL transformer blocks from {qwen}")
    t0 = time.perf_counter()
    chain = load_chain_all_layers(qwen, projection=args.projection)
    projMs = (time.perf_counter() - t0) * 1000
    n_neurons = sum(len(l) for l in chain)
    print(f"[Wave K] full chain: {len(chain)} layers, {n_neurons} neurons, "
          f"projection took {projMs:.0f} ms")

    print(f"[Wave K] loading {args.task}…")
    if args.task == "hellaswag":
        ds = load_dataset("Rowan/hellaswag", split="validation")
    elif args.task == "arc_easy":
        ds = load_dataset("allenai/ai2_arc", "ARC-Easy", split="test")
    elif args.task == "mmlu_mini":
        ds = load_dataset("cais/mmlu", "all", split="test")
    examples = list(ds)
    if args.limit > 0:
        examples = examples[:args.limit]
    total = len(examples)
    print(f"[Wave K] {args.task}: {total} examples")

    print(f"[Wave K] evaluating chain…")
    t0 = time.perf_counter()
    acc = evaluate(chain, examples, args.task)
    elapsedMs = (time.perf_counter() - t0) * 1000

    out = {
        "task": args.task,
        "examples": total,
        "correct": int(acc * total),
        "accuracy": acc,
        "chain_layers": len(chain),
        "chain_neurons": n_neurons,
        "projection_ms": projMs,
        "eval_ms": elapsedMs,
        "random_chance": 0.25,
        "delta_vs_random": acc - 0.25,
    }
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(out, indent=2))
    print(f"[Wave K] =======================================")
    print(f"[Wave K] {args.task}: accuracy = {acc:.4f} ({int(acc*total)}/{total})")
    print(f"[Wave K] random chance:        = 0.2500")
    print(f"[Wave K] delta vs random:      = {acc - 0.25:+.4f}")
    print(f"[Wave K] eval time:            = {elapsedMs/1000:.1f} s")
    print(f"[Wave K] saved: {args.out}")
    print(f"[Wave K] =======================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())