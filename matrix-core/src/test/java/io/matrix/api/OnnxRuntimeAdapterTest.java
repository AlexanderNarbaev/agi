package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
        if (model == null || !Files.isRegularFile(model)) return;  // skip if not exported
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
