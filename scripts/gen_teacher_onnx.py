# MATRIX RESEARCH-ONLY
# Генерирует крошечного ONNX-учителя FFN 16→64→GELU→64→1 для EXP-009B
# (дистилляция активаций в BIR). Запуск вручную:
#   python3 scripts/gen_teacher_onnx.py [выходной_путь]
# Детерминированно: seed 42.
import sys
import random

import onnx
from onnx import TensorProto, helper, numpy_helper


def main(out_path: str) -> None:
    rng = random.Random(42)
    bits, hidden = 16, 64

    def mat(rows, cols):
        return [[rng.uniform(-0.5, 0.5) for _ in range(cols)] for _ in range(rows)]

    import numpy as np
    w1 = np.array(mat(bits, hidden), dtype=np.float32)   # [K,N] для Gemm x@W
    b1 = np.array([rng.uniform(-0.1, 0.1) for _ in range(hidden)], dtype=np.float32)
    w2 = np.array([[rng.uniform(-0.5, 0.5)] for _ in range(hidden)], dtype=np.float32)  # [hidden,1]
    b2 = np.array([0.0], dtype=np.float32)

    inits = [
        numpy_helper.from_array(np.array([0.5], dtype=np.float32), name="half"),
        numpy_helper.from_array(np.array([1.0], dtype=np.float32), name="one"),
        numpy_helper.from_array(np.array([2**0.5], dtype=np.float32), name="sqrt2"),
        numpy_helper.from_array(w1, name="W1"),
        numpy_helper.from_array(b1, name="B1"),
        numpy_helper.from_array(w2, name="W2"),
        numpy_helper.from_array(b2, name="B2"),
    ]

    # GELU = 0.5*x*(1+erf(x/sqrt(2))) — композиция стандартных опов.
    nodes = [
        helper.make_node("Gemm", ["x", "W1", "B1"], ["h_pre"], alpha=1.0),
        helper.make_node("Div", ["h_pre", "sqrt2"], ["h_half"]),
        helper.make_node("Erf", ["h_half"], ["h_erf"]),
        helper.make_node("Add", ["h_erf", "one"], ["h_p1"]),
        helper.make_node("Mul", ["h_pre", "h_p1"], ["h_x2"]),
        helper.make_node("Mul", ["h_x2", "half"], ["h"]),
        helper.make_node("Gemm", ["h", "W2", "B2"], ["y"], alpha=1.0),
    ]

    graph = helper.make_graph(
        nodes, "teacher_ffn16",
        [helper.make_tensor_value_info("x", TensorProto.FLOAT, ["batch", bits])],
        [helper.make_tensor_value_info("y", TensorProto.FLOAT, ["batch", 1])],
        inits,
    )
    model = helper.make_model(
        graph, producer_name="matrix-exp009b",
        opset_imports=[helper.make_opsetid("", 18)],
    )
    onnx.checker.check_model(model)
    onnx.save(model, out_path)
    print("saved:", out_path)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "models/teacher/teacher_ffn16.onnx")
