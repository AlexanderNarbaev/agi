package io.matrix.research;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;
import io.matrix.api.OnnxRuntimeAdapter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.38 — GPU vs CPU ONNX inference benchmark (RUN 65).
 *
 * <p>Measures wall-clock latency for Qwen2.5-0.5B forward pass with
 * CUDA vs CPU execution providers.
 */
class Exp065GpuVsCpuBenchmarkTest {

    private static Path findOnnxModel() {
        Path[] candidates = {
                Path.of("models/onnx/qwen05b/model.onnx"),
                Path.of("../models/onnx/qwen05b/model.onnx"),
                Path.of("../../models/onnx/qwen05b/model.onnx")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    @Test
    void benchmarkGpuVsCpu() throws Exception {
        Path model = findOnnxModel();
        if (model == null) {
            System.out.println("[BENCH] ONNX model not found, skipping");
            return;
        }

        // Warmup: GPU first (it's the slower cold-start)
        runWarmup(model, true);
        runWarmup(model, false);

        // GPU measurements
        List<Long> gpuMs = runBenchmark(model, true, 10);
        // CPU measurements
        List<Long> cpuMs = runBenchmark(model, false, 10);

        long gpuP50 = percentile(gpuMs, 0.5);
        long gpuP99 = percentile(gpuMs, 0.99);
        long cpuP50 = percentile(cpuMs, 0.5);
        long cpuP99 = percentile(cpuMs, 0.99);

        double speedupP50 = (double) cpuP50 / gpuP50;
        double speedupP99 = (double) cpuP99 / gpuP99;

        System.out.printf("[BENCH] GPU p50=%dms p99=%dms%n", gpuP50, gpuP99);
        System.out.printf("[BENCH] CPU p50=%dms p99=%dms%n", cpuP50, cpuP99);
        System.out.printf("[BENCH] speedup p50=%.2fx p99=%.2fx%n", speedupP50, speedupP99);

        // Just verify the benchmark ran successfully.
        assertThat(gpuMs).hasSize(10);
        assertThat(cpuMs).hasSize(10);
        assertThat(gpuP50).isGreaterThan(0);
        assertThat(cpuP50).isGreaterThan(0);
    }

    private void runWarmup(Path model, boolean gpu) throws Exception {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(gpu);
        if (adapter.load()) {
            java.lang.reflect.Field sessionField = OnnxRuntimeAdapter.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            OrtSession session = (OrtSession) sessionField.get(adapter);
            runOnce(session);  // warmup
        }
        adapter.close();
    }

    private List<Long> runBenchmark(Path model, boolean gpu, int iters) throws Exception {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(gpu);
        if (!adapter.load()) {
            System.out.println("[BENCH] load failed (gpu=" + gpu + ")");
            return new ArrayList<>();
        }

        java.lang.reflect.Field sessionField = OnnxRuntimeAdapter.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        OrtSession session = (OrtSession) sessionField.get(adapter);

        List<Long> times = new ArrayList<>();
        for (int i = 0; i < iters; i++) {
            times.add(runOnce(session));
        }
        adapter.close();
        return times;
    }

    private long runOnce(OrtSession session) throws Exception {
        long seqLen = 8;
        long[][] inputIdsData = {{1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L}};
        long[][] attentionMaskData = {{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L}};
        long[][] positionIdsData = {{0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L}};

        try (OnnxTensor inputIds = OnnxTensor.createTensor(
                    ai.onnxruntime.OrtEnvironment.getEnvironment(),
                    java.nio.LongBuffer.wrap(inputIdsData[0]),
                    new long[]{1, seqLen});
             OnnxTensor attentionMask = OnnxTensor.createTensor(
                    ai.onnxruntime.OrtEnvironment.getEnvironment(),
                    java.nio.LongBuffer.wrap(attentionMaskData[0]),
                    new long[]{1, seqLen});
             OnnxTensor positionIds = OnnxTensor.createTensor(
                    ai.onnxruntime.OrtEnvironment.getEnvironment(),
                    java.nio.LongBuffer.wrap(positionIdsData[0]),
                    new long[]{1, seqLen})) {

            long t0 = System.nanoTime();
            try (var results = session.run(java.util.Map.of(
                    "input_ids", inputIds,
                    "attention_mask", attentionMask,
                    "position_ids", positionIds))) {
                results.get(0);
                return (System.nanoTime() - t0) / 1_000_000L;
            }
        }
    }

    private long percentile(List<Long> sortedTimes, double p) {
        if (sortedTimes.isEmpty()) return 0;
        int idx = (int) Math.min(sortedTimes.size() - 1, p * sortedTimes.size());
        java.util.Collections.sort(sortedTimes);
        return sortedTimes.get(idx);
    }
}
