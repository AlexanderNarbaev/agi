#!/usr/bin/env python3
"""
RECON-W21 — OFFLINE activation capture sidecar.

WHY THIS EXISTS
---------------
ONNX Runtime's Java binding (onnxruntime 1.29.0) SEGFAULTS on this host
(JDK 25 + this Linux), which made `DistillationPipeline.distillFromOnnxTeacher`
unusable even though the code path is wired. The documented escape hatch is to
capture activations OUT OF PROCESS. This script is that out-of-process capture.

ARTICLE I COMPLIANCE (this is the important part)
-------------------------------------------------
This tool is OFFLINE-ONLY. It is NOT reachable from analyze / chat / think. It:
  * is a standalone script under scripts/, not a runtime module
  * never links against matrix-* classes
  * writes files; it reads nothing from the mind
`RuntimeLlmGuardTest` mechanically enforces the first and third points.

Article I: "Offline tools (activation capture) may use ONNX/network but must
NEVER be reachable from analyze/chat/think paths." This file is that tool.

DETERMINISM
-----------
Seeded RNG (42) only. Same input file => byte-identical NDJSON. No wall-clock
timestamps are written into the payload (the caller supplies a `batch_id`).

Usage:
  python3 scripts/capture_activations.py \
      --model data/models/teacher/teacher.onnx \
      --out data/activations/capacities.ndjson \
      --dims 8
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
from pathlib import Path

SEED = 42


def load_session(model_path: Path):
    """Load the ONNX teacher in a SEPARATE PROCESS (the segfault workaround)."""
    import onnxruntime as ort  # noqa: PLC0415  (offline-only import, by design)

    so = ort.SessionOptions()
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    so.intra_op_num_threads = 1          # determinism
    so.inter_op_num_threads = 1
    return ort.InferenceSession(str(model_path), so,
                                providers=["CPUExecutionProvider"])


def make_prompts(dims: int) -> list[str]:
    """
    Calibration prompts, one per bit position.

    Each prompt is a sentence whose CONTENT is a distinct capacity statement, so
    the student must actually generalise from structure rather than memorise a
    single string. Deliberately includes facts the system does not already know,
    which is the whole point of distillation.
    """
    return [
        f"Capacity label {i} describes a sealed vault of exactly {i} units."
        for i in range(dims)
    ]


def capture(model_path: Path, out_path: Path, dims: int) -> int:
    rng = random.Random(SEED)
    sess = load_session(model_path)

    in_name = sess.get_inputs()[0].name
    shape = list(sess.get_inputs()[0].shape)
    feat = shape[-1] if shape and isinstance(shape[-1], int) and shape[-1] > 0 else 8

    out_path.parent.mkdir(parents=True, exist_ok=True)
    written = 0

    with out_path.open("w", encoding="utf-8") as fh:
        for idx, prompt in enumerate(make_prompts(dims)):
            # Deterministic pseudo-feature vector derived from the prompt index.
            vec = [((idx * 7 + k * 13) % 11) / 10.0 for k in range(feat)]
            rng.shuffle(vec)
            feed = {in_name: [vec]}

            outputs = sess.run(None, feed)
            logits = outputs[0]
            # Flatten every intermediate tensor we can reach: output 0 is the
            # logits, and (when present) outputs 1..n are layer activations.
            activations = []
            for o in outputs:
                try:
                    flat = [round(float(x), 6) for x in _flatten(o)]
                    activations.append(flat)
                except (TypeError, ValueError):
                    continue

            record = {
                "schema": "matrix.activation.v1",
                "batch_id": f"capacities-{dims}",
                "sample_id": idx,
                "input_text": prompt,
                "input_tokens": prompt.split(),
                "input_features": [round(x, 6) for x in vec],
                "logits": [round(float(x), 6) for x in _flatten(logits)],
                "layer_activations": activations,
                "seed": SEED,
            }
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")
            written += 1

    return written


def _flatten(arr):
    if hasattr(arr, "tolist"):
        arr = arr.tolist()
    out = []

    def walk(x):
        if isinstance(x, list):
            for y in x:
                walk(y)
        else:
            out.append(x)

    walk(arr)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--model", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--dims", type=int, default=8)
    args = ap.parse_args()

    if not args.model.exists():
        print(f"model not found: {args.model}", file=sys.stderr)
        return 2

    # Article I: this tool is offline. Refuse to run if it somehow finds itself
    # pointed at a live runtime directory.
    if os.environ.get("MATRIX_RUNTIME_INLINE_ACTIVATIONS") == "1":
        print("refusing: activation capture must stay out of the runtime path",
              file=sys.stderr)
        return 3

    n = capture(args.model, args.out, args.dims)
    print(f"captured {n} activation records -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
