package io.matrix.research;

import io.matrix.api.GenerationQuality;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.62 — Generation quality on real GPU (RUN 139).
 */
class Exp139GenerationQualityTest {

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
    void analyzeRealQuality() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(20);
        if (!bridge.load()) return;

        String text = bridge.generate("Tell me about Python", 20);
        System.out.println("[QUALITY] text: " + text);

        GenerationQuality q = new GenerationQuality();
        var metrics = q.evaluate(text);
        System.out.printf("[QUALITY] chars=%d words=%d unique=%d ratio=%.2f lines=%d rep=%b%n",
                metrics.charCount(), metrics.wordCount(),
                metrics.uniqueWords(), metrics.uniqueRatio(),
                metrics.lines(), metrics.hasRepetition());

        assertThat(metrics.charCount()).isGreaterThan(0);
        assertThat(metrics.wordCount()).isGreaterThan(0);

        bridge.close();
    }
}
