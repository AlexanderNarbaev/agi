#!/usr/bin/env python3
"""
RECON-W27 — a SECOND teacher of a different class (boolean-logic), to satisfy the
W21 acceptance criterion ">=2 real teachers (embeddings-class + boolean-logic-class)".

The existing `gen_teacher_onnx.py` produces an EMBEDDINGS-class teacher: an
MLP that maps a feature vector to a scalar score, which is what an
embeddings/distillation teacher looks like at the smallest useful size.

This script produces a BOOLEAN-LOGIC-class teacher: a tiny network whose output
is a thresholded AND/OR/XOR combination of inputs — a different function class,
so distilling from it exercises the pipeline against structure that is not a
smooth scalar map. Using a second, structurally different teacher is the point:
it shows the capture-and-replay route is not tuned to one model shape.

OFFLINE-ONLY (Article I): standalone script, no matrix-* imports, no network.

Usage:
  python3 scripts/gen_teacher_bool.py data/models/teacher/teacher-bool.onnx
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

SEED = 42


def main(out_path: str) -> None:
    rng = np.random.default_rng(SEED)
    bits, hidden = 16, 32

    def mat(r: int, c: int) -> np.ndarray:
        return rng.uniform(-0.4, 0.4, size=(r, c)).astype(np.float32)

    # Class A: an OR-like mixing layer.
    w1 = mat(bits, hidden)
    b1 = np.zeros(hidden, dtype=np.float32)
    # Class B: an AND-like masking layer (sigmoid gate, negative bias).
    w2 = mat(hidden, hidden)
    b2 = np.full(hidden, -0.5, dtype=np.float32)
    # Class C: a near-binary decision head.
    #
    # RECON-W27 FIX: the first version used b3 = 0.0, so the `Greater` node never
    # fired for any calibration input and EVERY activation was 0.0. The capture
    # and replay pipeline then succeeded (8 samples, fidelity 1.0) while learning
    # literally nothing: the distilled hash was byte-identical to the embeddings
    # teacher's. A teacher that never fires is not a second teacher. The negative
    # bias is chosen so roughly half the calibration inputs cross the threshold,
    # giving a genuinely binary, varied activation set.
    w3 = mat(hidden, 1)
    b3 = np.array([-0.05], dtype=np.float32)

    nodes = [
        helper.make_node("Gemm", ["x", "W1", "B1"], ["h_pre"], alpha=1.0),
        helper.make_node("Relu", ["h_pre"], ["h_or"]),
        helper.make_node("Gemm", ["h_or", "W2", "B2"], ["g_pre"], alpha=1.0),
        helper.make_node("Sigmoid", ["g_pre"], ["h_and"]),
        helper.make_node("Mul", ["h_or", "h_and"], ["h_gate"]),
        helper.make_node("Gemm", ["h_gate", "W3", "B3"], ["logit"], alpha=1.0),
        # RECON-W27: emit the REAL-VALUED activation FIRST, then the hard
        # decision. A bool output carries no gradient-free magnitude to distil
        # from, and the capture sidecar now skips non-float tensors entirely.
        helper.make_node("Sigmoid", ["logit"], ["y_soft"]),
        helper.make_node("Greater", ["logit", "zero"], ["y_raw"], name="decide"),
    ]

    inits = [
        numpy_helper.from_array(w1, "W1"),
        numpy_helper.from_array(b1, "B1"),
        numpy_helper.from_array(w2, "W2"),
        numpy_helper.from_array(b2, "B2"),
        numpy_helper.from_array(w3, "W3"),
        numpy_helper.from_array(b3, "B3"),
        numpy_helper.from_array(np.array([0.0], dtype=np.float32), "zero"),
    ]

    graph = helper.make_graph(
        nodes, "matatrix_teacher_bool",
        [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1, bits])],
        [
            helper.make_tensor_value_info("y_soft", TensorProto.FLOAT, [1, 1]),
            helper.make_tensor_value_info("y_raw", TensorProto.BOOL, [1, 1]),
            helper.make_tensor_value_info("logit", TensorProto.FLOAT, [1, 1]),
        ],
        initializer=inits,
    )
    model = helper.make_model(
        graph, producer_name="matrix-teacher-bool",
        opset_imports=[helper.make_opsetid("", 13)],
    )
    model.ir_version = 8

    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, str(out))
    onnx.checker.check_model(model)
    print(f"saved boolean-logic teacher: {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1] if len(sys.argv) > 1
                          else "data/models/teacher/teacher-bool.onnx"))
