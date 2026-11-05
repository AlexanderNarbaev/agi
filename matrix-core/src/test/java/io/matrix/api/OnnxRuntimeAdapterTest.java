package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link OnnxRuntimeAdapter} — RUN 59 ONNX Runtime integration.
 */
class OnnxRuntimeAdapterTest {

    @TempDir
    Path tmp;

    @Test
    void missingModelReportsUnavailable() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        assertThat(adapter.isAvailable()).isFalse();
        assertThat(adapter.isLoaded()).isFalse();
    }

    @Test
    void emptyFileReportsUnavailable() throws IOException {
        // Empty file is not a valid ONNX model.
        Path p = tmp.resolve("empty.onnx");
        Files.writeString(p, "");
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(p);
        // Files.exists but content is invalid → loading would fail.
        // isAvailable returns true if file exists.
        assertThat(adapter.isAvailable()).isTrue();
        assertThat(adapter.isLoaded()).isFalse();
    }

    @Test
    void inferenceCountStartsAtZero() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        assertThat(adapter.inferenceCount()).isZero();
    }

    @Test
    void modelPathAccessor() {
        Path p = tmp.resolve("test.onnx");
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(p);
        assertThat(adapter.modelPath()).isEqualTo(p);
    }

    @Test
    void infoUnloaded() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        String info = adapter.info();
        assertThat(info).contains("unloaded");
        assertThat(info).contains("missing.onnx");
    }

    @Test
    void defaultModelPathMatchesExportedOnnx() {
        // After RUN 59 ONNX export, this path should be valid.
        assertThat(OnnxRuntimeAdapter.DEFAULT_MODEL.toString())
                .isEqualTo("models/onnx/qwen05b/model.onnx");
    }

    @Test
    void loadOnRealExportedModel() throws IOException {
        // Run against the actual exported ONNX model if present.
        // Walk up directory tree to find the model.
        Path model = findOnnxModel();
        assumeTrue(model != null && Files.isRegularFile(model),
                "the ONNX model models/onnx/qwen05b/model.onnx is not present on this "
                + "machine, so the adapter cannot be loaded against it");
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        boolean loaded = adapter.load();
        assertThat(loaded)
                .as("loading exported ONNX model at %s", model)
                .isTrue();
        assertThat(adapter.isLoaded()).isTrue();
        String info = adapter.info();
        assertThat(info).contains("loaded");
        assertThat(info).contains("inputs=");
        adapter.close();
    }

    /**
     * OD-028: the diagnostic reports what the runtime reported, not a constant.
     *
     * <p>Before OD-028 the adapter reported a hardcoded {@code {"CPUExecutionProvider"}}
     * whatever happened, so this assertion would have passed against a lie. Now the
     * diagnostic carries a state, the evidence it rests on, and the providers the
     * session runs with — and the two agree with each other.
     */
    @Test
    void theDiagnosticReportsTheRuntimeAndNotAConstant() throws IOException {
        Path model = findOnnxModel();
        assumeTrue(model != null && Files.isRegularFile(model),
                "the ONNX model models/onnx/qwen05b/model.onnx is not present on this "
                + "machine, so the diagnostic cannot be exercised against it");
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        assertThat(adapter.load()).isTrue();

        OnnxRuntimeAdapter.GpuDiagnostic diagnostic = adapter.gpuDiagnostic();
        System.out.println("[ADAPTER-TEST] diagnostic: " + diagnostic);

        assertThat(diagnostic.state()).isNotNull();
        assertThat(diagnostic.evidence()).isNotBlank();
        assertThat(diagnostic.activeProviders()).isNotEmpty();
        assertThat(diagnostic.availableProviders()).isNotEmpty();
        assertThat(diagnostic.gpuRequested()).isTrue();

        // The providers and the state must tell the same story.
        boolean cudaReported = Arrays.stream(diagnostic.activeProviders())
                .anyMatch(provider -> provider.toUpperCase().contains("CUDA"));
        if (cudaReported) {
            assertThat(diagnostic.state())
                    .as("a session running on CUDA cannot be in a fallback state")
                    .isNotEqualTo(OnnxRuntimeAdapter.GpuState.CPU_FALLBACK_USED);
        }
        if (diagnostic.fallbackUsed()) {
            assertThat(diagnostic.activeProviders())
                    .as("a session that fell back to the CPU runs on the CPU")
                    .containsExactly("CPUExecutionProvider");
        }
        adapter.close();
    }

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
    void loadReturnsFalseForMissingModel() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        boolean loaded = adapter.load();
        assertThat(loaded).isFalse();
    }
}
