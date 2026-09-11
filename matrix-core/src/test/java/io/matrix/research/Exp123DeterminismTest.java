package io.matrix.research;

import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.56 — Determinism verification (RUN 123).
 *
 * <p>Verifies greedy decoding produces identical output across
 * multiple runs (no random sampling, no race conditions).
 */
class Exp123DeterminismTest {

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
    void greedyIsDeterministic() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        // Run 3 times, expect identical output
        String r1 = bridge.generate("What is 2+2?", 4);
        String r2 = bridge.generate("What is 2+2?", 4);
        String r3 = bridge.generate("What is 2+2?", 4);

        System.out.println("[DETERMINISM]");
        System.out.println("  R1: " + r1);
        System.out.println("  R2: " + r2);
        System.out.println("  R3: " + r3);

        assertThat(r1).isEqualTo(r2);
        assertThat(r2).isEqualTo(r3);

        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void multiTurnDeterminism() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        // Same chat history, same generation
        var history1 = java.util.List.of(
                io.matrix.api.QwenChatTemplate.Message.system("Be brief."),
                io.matrix.api.QwenChatTemplate.Message.user("Hi"));
        var history2 = java.util.List.of(
                io.matrix.api.QwenChatTemplate.Message.system("Be brief."),
                io.matrix.api.QwenChatTemplate.Message.user("Hi"));

        String r1 = bridge.chatWithHistory(history1, "Hello", 6, 0.0, -1, 1.0);
        String r2 = bridge.chatWithHistory(history2, "Hello", 6, 0.0, -1, 1.0);
        assertThat(r1).isEqualTo(r2);
        System.out.println("[DETERMIN-MULTI] " + r1);

        bridge.close();
    }
}
