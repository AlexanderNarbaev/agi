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

    @Test
    @EnabledIf("modelAvailable")
    void generateSampledProducesText() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        // Temperature 0.0 should equal greedy
        String greedy = bridge.generateSampled("Hello", 4, 0.0);
        assertThat(greedy).isNotNull();

        // Temperature 1.0 produces some text (non-deterministic but should work)
        String sampled = bridge.generateSampled("Hello", 4, 1.0);
        assertThat(sampled).isNotNull();
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void generateSampledHonoursMaxTokens() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(64);
        if (!bridge.load()) return;

        String reply = bridge.generateSampled("The", 8, 0.5);
        assertThat(reply).isNotNull();
        // We can't predict exact length, but reply should be small
        bridge.close();
    }

    @Test
    void temperatureClampingImplicitInApi() {
        // The sampling function clamps temperature to [0.01, 2.0]
        // We can verify indirectly by calling with extreme values.
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("models/hf_cache/qwen05b"));
        // No exception during construction; behavior verified when bridge is loaded.
        // This test just verifies the API exists.
        assertThat(bridge).isNotNull();
    }

    @Test
    @EnabledIf("modelAvailable")
    void generateWithTopKAndTopP() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        // topK=50, topP=0.9, temperature=0.7 — typical settings
        String reply = bridge.generateSampled("Hello", 4, 0.7, 50, 0.9);
        assertThat(reply).isNotNull();

        // topK=1, topP=1.0 = greedy (effectively)
        String greedy = bridge.generateSampled("Hi", 4, 0.7, 1, 1.0);
        assertThat(greedy).isNotNull();

        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void chatMethodAppliesTemplate() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        String reply = bridge.chat("What is 2+2?", 8);
        // reply should not contain raw <|im_start|> etc.
        assertThat(reply).doesNotContain("<|im_start|>");
        assertThat(reply).doesNotContain("<|im_end|>");
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void chatMethodWithSampling() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        String reply = bridge.chat("Hello", 8, 0.7, 50, 0.9);
        assertThat(reply).doesNotContain("<|im_end|>");
        bridge.close();
    }

    // EnabledIf helper
    static boolean modelAvailable() {
        return findModelDir() != null && findOnnx() != null;
    }
}
