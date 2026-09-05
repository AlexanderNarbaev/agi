package io.matrix.research;

import io.matrix.api.BpeTokenizer;
import io.matrix.api.QwenOnnxBridge;
import io.matrix.api.TokenAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.61 — Token analysis on real GPU generation (RUN 137).
 */
class Exp137TokenAnalysisTest {

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
    void analyzeGeneratedTokens() throws Exception {
        Path dir = findModelDir();
        BpeTokenizer tokenizer = BpeTokenizer.fromModelDir(dir);
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        String text = bridge.generate("Hello, how are you?", 16);
        int[] ids = tokenizer.encode(text);
        System.out.println("[TOK-ANALYSIS] text: " + text);
        System.out.println("[TOK-ANALYSIS] tokens: " + ids.length);

        TokenAnalyzer analyzer = new TokenAnalyzer();
        var result = analyzer.analyze(ids, tokenizer);
        System.out.printf("[TOK-ANALYSIS] total=%d special=%.2f punct=%.2f%n",
                result.totalTokens(), result.specialRatio(),
                result.punctuationRatio());

        assertThat(result.totalTokens()).isEqualTo(ids.length);
        assertThat(result.specialRatio()).isGreaterThanOrEqualTo(0);

        bridge.close();
    }
}
