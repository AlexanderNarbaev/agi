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


def make_prompts(dims: int, domain: str = "capacity") -> list[str]:
    """
    Calibration prompts, one per bit position.

    Each prompt is a sentence whose CONTENT is a distinct capacity statement, so
    the student must actually generalise from structure rather than memorise a
    single string. Deliberately includes facts the system does not already know,
    which is the whole point of distillation.
    """
    # RECON-W27: the domain matters. The distilled clause structure is a function
    # of the INPUT BIT VECTORS, which ActivationRecord.toBitVector derives from the
    # prompt TOKENS. Two different teacher models fed the same prompts therefore
    # produce byte-identical artifacts, because the teacher's activation VALUES do
    # not shape the clause masks. Distinct domains make the comparison meaningful.
    if domain == "boolean":
        return [
            f"Gate rule {i}: the switch must stay closed while the lamp is dark."
            for i in range(dims)
        ]
    return [
        f"Capacity label {i} describes a sealed vault of exactly {i} units."
        for i in range(dims)
    ]


def capture(model_path: Path, out_path: Path, dims: int, domain: str = "capacity") -> int:
    rng = random.Random(SEED)
    sess = load_session(model_path)

    in_name = sess.get_inputs()[0].name
    shape = list(sess.get_inputs()[0].shape)
    feat = shape[-1] if shape and isinstance(shape[-1], int) and shape[-1] > 0 else 8

    out_path.parent.mkdir(parents=True, exist_ok=True)
    written = 0

    with out_path.open("w", encoding="utf-8") as fh:
        for idx, prompt in enumerate(make_prompts(dims, domain)):
            # Deterministic pseudo-feature vector derived from the prompt index.
            vec = [((idx * 7 + k * 13) % 11) / 10.0 for k in range(feat)]
            rng.shuffle(vec)
            feed = {in_name: [vec]}

            outputs = sess.run(None, feed)
            # RECON-W27 FIX: prefer REAL-VALUED tensors. A boolean-logic teacher
            # may expose a BOOL output first, and a bool carries no distillation
            # signal — capturing it produced eight records of all-zero
            # "activations" that replayed successfully while learning nothing
            # (the distilled hash came out byte-identical to another teacher's).
            # Bool/int tensors are kept out of the activation list entirely.
            numeric = [o for o in outputs if _is_float(o)]
            numeric = numeric or list(outputs)
            logits = numeric[0]
            activations = []
            for o in numeric:
                try:
                    flat = [round(float(x), 6) for x in _flatten(o)]
                    activations.append(flat)
                except (TypeError, ValueError):
                    continue

            record = {
                "schema": "matrix.activation.v1",
                # RECON-W27: the batch id must identify the MODEL, not just the
                # dimension count. Two different teacher classes captured at the
                # same width previously shared a batch id, so their provenance in
                # the registry was indistinguishable.
                "batch_id": f"{model_path.stem}-{domain}-{dims}",
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


def _is_float(arr) -> bool:
    """True when the tensor holds real numbers (not bool/int labels)."""
    if arr is None:
        return False
    dtype = getattr(arr, "dtype", None)
    if dtype is None:
        return False
    return str(dtype) in ("float32", "float64", "float16")


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
    ap.add_argument("--domain", default="capacity",
                    choices=["capacity", "boolean"],
                    help="prompt family; different domains yield different "
                         "input bit vectors and therefore different artifacts")
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

    n = capture(args.model, args.out, args.dims, args.domain)
    print(f"captured {n} activation records -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
