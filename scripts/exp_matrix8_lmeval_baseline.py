# MATRIX RESEARCH-ONLY
# W11 baseline: measure Qwen2.5-0.5B float accuracy on a small
# lm-eval-harness subset. This is the "ground truth" reference for
# the boolean distillation we built in W7-W8.
#
# Run: python3 scripts/exp_matrix8_lmeval_baseline.py
import argparse
import json
import os
import sys
import time
from pathlib import Path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="models/external/distilbert-base-sst2",
                    help="HF model dir (with safetensors)")
    ap.add_argument("--tasks", default="hellaswag,arc_easy")
    ap.add_argument("--limit", type=int, default=20,
                    help="samples per task (keep small for quick runs)")
    ap.add_argument("--out", default="docs-v2/research/reports/EXP-MATRIX.8-baseline.json")
    args = ap.parse_args()

    if not Path(args.model).exists():
        print(f"FAIL: {args.model} missing", file=sys.stderr)
        return 1

    # lm_eval expects model="hf" with pretrained path or HF id
    # use the local path so we don't re-download
    import torch
    from lm_eval import simple_evaluate

    t0 = time.perf_counter()
    print(f"[W11-baseline] running {args.tasks} on {args.model}, limit={args.limit}…")

    # For Qwen2.5-0.5B-Instruct we need to treat it as a causal LM, not
    # classifier. lm_eval's `hf` model_type handles this for causal LMs.
    results = simple_evaluate(
        model="hf",
        model_args=f"pretrained={args.model},dtype=float32",
        tasks=args.tasks.split(","),
        limit=args.limit,
        device="cuda" if torch.cuda.is_available() else "cpu",
    )

    elapsed = time.perf_counter() - t0
    print(f"[W11-baseline] done in {elapsed:.1f}s")

    out = {
        "model": args.model,
        "tasks": args.tasks.split(","),
        "limit": args.limit,
        "elapsed_seconds": elapsed,
        "results": {},
    }
    for task, metrics in results.get("results", {}).items():
        out["results"][task] = dict(metrics)

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(out, indent=2))
    print(f"[W11-baseline] saved: {args.out}")
    print(f"[W11-baseline] =====================================")
    for task, metrics in results.get("results", {}).items():
        for k, v in metrics.items():
            if isinstance(v, float):
                print(f"[W11-baseline] {task}.{k} = {v:.3f}")
    print(f"[W11-baseline] =====================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())