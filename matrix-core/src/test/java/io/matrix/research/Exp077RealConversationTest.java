package io.matrix.research;

import io.matrix.api.QwenChatTemplate;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.41 — Real multi-turn conversation with Qwen2.5-0.5B
 * on GPU (RUN 77).
 *
 * <p>Drives a 3-turn conversation: introduce name → ask name → ask age.
 * Verifies the bridge formats prompts, runs autoregressive generation,
 * and returns non-empty replies.
 */
class Exp077RealConversationTest {

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
    void threeTurnConversation() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        List<QwenChatTemplate.Message> history = new ArrayList<>();
        history.add(QwenChatTemplate.Message.system(
                "You are a friendly assistant. Be concise."));

        // Turn 1: introduce name
        String turn1 = bridge.chatWithHistory(history,
                "My name is Alex.", 12, 0.0, -1, 1.0);
        assertThat(turn1).isNotBlank();
        assertThat(turn1).doesNotContain("<|im_start|>");
        history.add(QwenChatTemplate.Message.user("My name is Alex."));
        history.add(QwenChatTemplate.Message.assistant(turn1));

        // Turn 2: ask for name back
        String turn2 = bridge.chatWithHistory(history,
                "What's my name?", 12, 0.0, -1, 1.0);
        assertThat(turn2).isNotBlank();
        history.add(QwenChatTemplate.Message.user("What's my name?"));
        history.add(QwenChatTemplate.Message.assistant(turn2));

        // Turn 3: ask about colors
        String turn3 = bridge.chatWithHistory(history,
                "What color is the sky?", 16, 0.0, -1, 1.0);
        assertThat(turn3).isNotBlank();

        System.out.println("[CONV-3TURN]");
        System.out.println("  T1: " + turn1);
        System.out.println("  T2: " + turn2);
        System.out.println("  T3: " + turn3);

        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void batchGenerationSpeed() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        long t0 = System.nanoTime();
        int turns = 5;
        for (int i = 0; i < turns; i++) {
            String reply = bridge.chat("Hi " + i, 8);
            assertThat(reply).isNotNull();
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf("[BATCH] %d turns in %dms (avg %.0fms/turn)%n",
                turns, elapsedMs, (double) elapsedMs / turns);
        bridge.close();
    }
}
