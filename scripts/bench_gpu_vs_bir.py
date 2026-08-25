# MATRIX RESEARCH-ONLY
# EXP-009C: GPU-нога честного сравнения. Та же функция учителя (FFN 16→64→GELU→64→1),
# веса берутся ИЗ teacher.onnx (инициализаторы) — apples-to-apples с CPU/ORT/BIR линиями.
# Замеры: батч N=2000 (200 повторов, cuda.synchronize) и per-call (1000 одиночных с sync).
# Запуск: python3 scripts/bench_gpu_vs_bir.py models/teacher/teacher_ffn16.onnx
import sys
import time

import numpy as np
import onnx
import torch
from onnx import numpy_helper


def load_weights(path):
    m = onnx.load(path)
    w = {i.name: numpy_helper.to_array(i) for i in m.graph.initializer}
    return w["W1"], w["B1"], w["W2"], w["B2"]


def main(path):
    W1, B1, W2, B2 = load_weights(path)
    dev = torch.device("cuda")
    t_w1 = torch.tensor(W1, device=dev)
    t_b1 = torch.tensor(B1, device=dev)
    t_w2 = torch.tensor(W2, device=dev)
    t_b2 = torch.tensor(B2, device=dev)

    rng = np.random.default_rng(42)
    x = torch.tensor(
        rng.integers(0, 2, size=(2000, W1.shape[0])).astype(np.float32), device=dev)

    def ffn(batch):
        h = torch.nn.functional.gelu(batch @ t_w1 + t_b1)
        return h @ t_w2 + t_b2

    # Warmup
    for _ in range(50):
        ffn(x)
    torch.cuda.synchronize()

    reps = 200
    t0 = time.perf_counter()
    for _ in range(reps):
        ffn(x)
    torch.cuda.synchronize()
    gpu_batch_ms = (time.perf_counter() - t0) / reps * 1000

    single = x[:1]
    for _ in range(20):
        ffn(single)
    torch.cuda.synchronize()
    calls = 1000
    t0 = time.perf_counter()
    for _ in range(calls):
        ffn(single)
    torch.cuda.synchronize()
    gpu_single_us = (time.perf_counter() - t0) / calls * 1e6

    print(f"EXP009C device={torch.cuda.get_device_name(0)} "
          f"gpuBatchMs={gpu_batch_ms:.4f} gpuSingleUs={gpu_single_us:.2f} "
          f"reps={reps} calls={calls}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "models/teacher/teacher_ffn16.onnx")
