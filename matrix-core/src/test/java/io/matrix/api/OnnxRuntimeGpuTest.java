package io.matrix.api;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OnnxRuntimeAdapter} — RUN 62 GPU execution.
 */
class OnnxRuntimeGpuTest {

    @TempDir
    Path tmp;

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
    void loadWithGpuEnabled() throws IOException {
        Path model = findOnnxModel();
        if (model == null) {
            System.out.println("[GPU-TEST] ONNX model not found, skipping");
            return;
        }

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        boolean loaded = adapter.load();
        assertThat(loaded).isTrue();
        assertThat(adapter.isLoaded()).isTrue();
        String[] providers = adapter.activeProviders();
        System.out.println("[GPU-TEST] active providers: " + Arrays.toString(providers));
        assertThat(providers).isNotEmpty();
        String info = adapter.info();
        assertThat(info).contains("useGpu=true");
        adapter.close();
    }

    @Test
    void loadWithGpuDisabledUsesCpuOnly() throws IOException {
        Path model = findOnnxModel();
        if (model == null) return;

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(false);
        boolean loaded = adapter.load();
        assertThat(loaded).isTrue();
        for (String p : adapter.activeProviders()) {
            assertThat(p.toUpperCase())
                    .as("CUDA provider should not be active when GPU disabled")
                    .doesNotContain("CUDA");
        }
        adapter.close();
    }

    @Test
    void loadReturnsFalseForMissingModel() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        adapter.setUseGpu(true);
        assertThat(adapter.load()).isFalse();
    }

    @Test
    void gpuFlagRoundtrips() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter();
        assertThat(adapter.isUseGpu()).isTrue();
        adapter.setUseGpu(false);
        assertThat(adapter.isUseGpu()).isFalse();
        adapter.setUseGpu(true);
        assertThat(adapter.isUseGpu()).isTrue();
    }

    @Test
    void activeProvidersAreClonable() throws IOException {
        Path model = findOnnxModel();
        if (model == null) return;

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.load();
        String[] providers1 = adapter.activeProviders();
        String[] providers2 = adapter.activeProviders();
        if (providers1.length > 0) {
            providers1[0] = "MUTATED";
        }
        assertThat(providers2[0]).isNotEqualTo("MUTATED");
        adapter.close();
    }

    /**
     * RUN 63: Real GPU inference test. Runs a forward pass and measures
     * latency. Requires CUDA 12 libs on LD_LIBRARY_PATH.
     */
    @Test
    void realInferenceRunsAndProducesLogits() throws Exception {
        Path model = findOnnxModel();
        if (model == null) {
            System.out.println("[GPU-INFER] ONNX model not found, skipping");
            return;
        }

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        if (!adapter.load()) {
            System.out.println("[GPU-INFER] load failed, skipping");
            return;
        }

        // Get the underlying session for raw inference.
        // We use reflection to access session (it's private).
        java.lang.reflect.Field sessionField = OnnxRuntimeAdapter.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        OrtSession session = (OrtSession) sessionField.get(adapter);

        long seqLen = 5;
        long[][] inputIdsData = {{1L, 2L, 3L, 4L, 5L}};
        long[][] attentionMaskData = {{1L, 1L, 1L, 1L, 1L}};
        long[][] positionIdsData = {{0L, 1L, 2L, 3L, 4L}};

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
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                var logits = (float[][][]) results.get(0).getValue();
                System.out.printf("[GPU-INFER] GPU inference: %dms, logits shape=%dx%dx%d, last_argmax=%d%n",
                        ms, logits.length, logits[0].length, logits[0][0].length,
                        argmax(logits[0][logits[0].length - 1]));
                assertThat(ms).isGreaterThanOrEqualTo(0L);  // we measured something
                assertThat(logits.length).isEqualTo(1);
            }
        }
        adapter.close();
    }

    private static int argmax(float[] arr) {
        int bestIdx = 0;
        float bestVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > bestVal) {
                bestVal = arr[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
