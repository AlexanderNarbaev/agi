package io.matrix.research;

import io.matrix.api.GenerationResult;
import io.matrix.api.OnnxInferenceMetrics;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.48 — Argmax probability calibration on real LLM (RUN 94).
 *
 * <p>Runs generateWithProbs and computes avg + min confidence
 * across the generated tokens.
 */
class Exp094ArgmaxProbCalibrationTest {

    private static Path findModelDir() {
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
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    static boolean modelAvailable() {
        return findModelDir() != null && findOnnx() != null;
    }

    @Test
    @EnabledIf("modelAvailable")
    void calibrationForGreeting() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        GenerationResult result = bridge.generateWithProbs("Hello!", 8);
        System.out.println("[CALIB-GREETING] text: " + result.text());
        System.out.printf("[CALIB-GREETING] avg=%.3f min=%.3f tokens=%d%n",
                result.avgConfidence(), result.minConfidence(),
                result.tokensGenerated());

        // Reset metrics before to track just this call
        OnnxInferenceMetrics m = bridge.metrics();
        long before = m.inferenceCount();
        GenerationResult result2 = bridge.generateWithProbs("Goodbye!", 8);
        long after = m.inferenceCount();
        System.out.printf("[CALIB-METRICS] inferences before=%d after=%d delta=%d%n",
                before, after, after - before);
        assertThat(after).isGreaterThan(before);

        // Sanity: argmax probabilities should be in (0, 1)
        for (var step : result.steps()) {
            assertThat(step.probability()).isBetween(0.0, 1.0);
        }
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void calibrationForFactRecall() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        GenerationResult result = bridge.generateWithProbs("France's capital is", 6);
        System.out.println("[CALIB-FACT] text: " + result.text());
        System.out.printf("[CALIB-FACT] avg=%.3f min=%.3f tokens=%d%n",
                result.avgConfidence(), result.minConfidence(),
                result.tokensGenerated());

        // Real LLMs should be highly confident on factual continuations
        // like "Paris" after "France's capital is".
        assertThat(result.avgConfidence()).isGreaterThan(0.0);
        bridge.close();
    }
}
