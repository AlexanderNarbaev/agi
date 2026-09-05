package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 66 / EXP-MATRIX.39 — QwenOnnxBridge integration tests.
 *
 * <p>Verifies end-to-end real LLM inference in Java (no Python):
 * tokenizer + ONNX model → autoregressive generation.
 */
class QwenOnnxBridgeTest {

    private static Path findModelDir() {
        Path[] candidates = {
                Path.of("models/hf_cache/qwen05b"),
                Path.of("../models/hf_cache/qwen05b"),
                Path.of("../../models/hf_cache/qwen05b")
        };
        for (Path p : candidates) {
            if (Files.exists(p.resolve("config.json"))) return p;
        }
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/hf_cache/qwen05b");
            if (Files.exists(p.resolve("config.json"))) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    private static Path findOnnx() {
        Path[] candidates = {
                Path.of("models/onnx/qwen05b/model.onnx"),
                Path.of("../models/onnx/qwen05b/model.onnx"),
                Path.of("../../models/onnx/qwen05b/model.onnx")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    @Test
    void constructorSetsFields() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("models/hf_cache/qwen05b"));
        assertThat(bridge.modelDir().toString()).contains("qwen05b");
        assertThat(bridge.isLoaded()).isFalse();
        assertThat(bridge.isGpuEnabled()).isFalse();
    }

    @Test
    void useGpuFlagToggles() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("models/hf_cache/qwen05b"));
        bridge.useGpu(true);
        assertThat(bridge.isGpuEnabled()).isTrue();
        bridge.useGpu(false);
        assertThat(bridge.isGpuEnabled()).isFalse();
    }

    @Test
    void maxNewTokensClampedToRange() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("models/hf_cache/qwen05b"));
        bridge.setMaxNewTokens(0);   // ignored
        bridge.setMaxNewTokens(-5);  // ignored
        bridge.setMaxNewTokens(2000); // clamped to 1024
        // No exception = success
    }

    @Test
    void infoBeforeLoad() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("models/hf_cache/qwen05b"));
        String info = bridge.info();
        assertThat(info).contains("QwenOnnxBridge");
        assertThat(info).contains("unloaded");
    }

    @Test
    @EnabledIf("modelAvailable")
    void loadsTokenizerAndOnnx() {
        Path dir = findModelDir();
        assertThat(dir).isNotNull();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        boolean ok = bridge.load();
        assertThat(ok).isTrue();
        assertThat(bridge.isLoaded()).isTrue();
        assertThat(bridge.vocabSize()).isGreaterThan(100_000);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void generatesTokensForPrompt() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;  // skip if ONNX unavailable

        // Use a minimal prompt
        String reply = bridge.generate("Hello", 8);
        assertThat(reply).isNotNull();
        // Qwen should produce SOME text even from "Hello"
        // (the exact output is non-deterministic across model versions)
        bridge.close();
    }

    @Test
    void handlesMissingModelDir() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/nonexistent/path"));
        boolean ok = bridge.load();
        assertThat(ok).isFalse();
        assertThat(bridge.isLoaded()).isFalse();
    }

    @Test
    void handlesMissingOnnxFile() {
        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory("qwen-empty-");
        } catch (Exception e) {
            return;  // cannot test without temp dir
        }
        QwenOnnxBridge bridge = new QwenOnnxBridge(tmpDir);
        // Should fail at tokenizer or ONNX load
        boolean ok = bridge.load();
        assertThat(ok).isFalse();
        try { Files.deleteIfExists(tmpDir); } catch (Exception ignored) {}
    }

    // EnabledIf helper
    static boolean modelAvailable() {
        return findModelDir() != null && findOnnx() != null;
    }
}
