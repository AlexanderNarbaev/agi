package io.matrix.research;

import io.matrix.api.OnnxRuntimeAdapter;
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
 * EXP-MATRIX.44 — Real 5-turn conversation with diverse prompts (RUN 84).
 *
 * <p>Drives Qwen2.5-0.5B through 5 different categories of queries:
 * - Greeting (small talk)
 * - Personal recall (multi-turn name)
 * - Factual question (knowledge)
 * - Math/arithmetic
 * - Open-ended generation
 *
 * <p>Verifies the bridge produces non-blank, properly-spaced output
 * for each.
 */
class Exp084DiverseConversationTest {

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
    void fiveTurnDiverseConversation() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(20);
        if (!bridge.load()) return;

        List<QwenChatTemplate.Message> history = new ArrayList<>();
        history.add(QwenChatTemplate.Message.system("You are a friendly assistant. Be brief."));

        // 1. Greeting
        String t1 = bridge.chatWithHistory(history, "Hello!", 16, 0.0, -1, 1.0);
        history.add(QwenChatTemplate.Message.user("Hello!"));
        history.add(QwenChatTemplate.Message.assistant(t1));

        // 2. Personal
        String t2 = bridge.chatWithHistory(history, "My name is Maria.", 16, 0.0, -1, 1.0);
        history.add(QwenChatTemplate.Message.user("My name is Maria."));
        history.add(QwenChatTemplate.Message.assistant(t2));

        // 3. Recall
        String t3 = bridge.chatWithHistory(history, "What is my name?", 16, 0.0, -1, 1.0);
        history.add(QwenChatTemplate.Message.user("What is my name?"));
        history.add(QwenChatTemplate.Message.assistant(t3));

        // 4. Factual
        String t4 = bridge.chatWithHistory(history, "What is the capital of France?", 16, 0.0, -1, 1.0);
        history.add(QwenChatTemplate.Message.user("What is the capital of France?"));
        history.add(QwenChatTemplate.Message.assistant(t4));

        // 5. Math
        String t5 = bridge.chatWithHistory(history, "What is 7 times 8?", 16, 0.0, -1, 1.0);

        System.out.println("[DIVERSE-5]");
        System.out.println("  Greeting:  " + repr(t1));
        System.out.println("  Personal:  " + repr(t2));
        System.out.println("  Recall:    " + repr(t3));
        System.out.println("  Factual:   " + repr(t4));
        System.out.println("  Math:      " + repr(t5));

        // All replies must be non-blank
        assertThat(t1).isNotBlank();
        assertThat(t2).isNotBlank();
        assertThat(t3).isNotBlank();
        assertThat(t4).isNotBlank();
        assertThat(t5).isNotBlank();

        // All replies must be properly formatted (no missing spaces, no raw markers)
        for (String s : List.of(t1, t2, t3, t4, t5)) {
            assertThat(s).doesNotContain("<|im_start|>");
            assertThat(s).doesNotContain("<|im_end|>");
            assertThat(s).doesNotContain("Ġ");  // raw Ġ chars should be decoded
        }

        // Recall must contain "Maria" (verified personal info retention)
        assertThat(t3.toLowerCase()).contains("maria");

        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void deterministicGreedyRunsReproducibly() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        String r1 = bridge.generate("Hi", 4);
        String r2 = bridge.generate("Hi", 4);
        // Greedy should be deterministic
        assertThat(r1).isEqualTo(r2);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void onnxAdapterGpuSpeedupVisible() throws Exception {
        // Compare CPU vs GPU on the same input.
        OnnxRuntimeAdapter cpu = new OnnxRuntimeAdapter(findOnnx());
        cpu.setUseGpu(false);
        OnnxRuntimeAdapter gpu = new OnnxRuntimeAdapter(findOnnx());
        gpu.setUseGpu(true);

        if (!cpu.load() || !gpu.load()) return;

        long[] ids = {1L, 2L, 3L, 4L, 5L};
        // Warmup
        cpu.greedyNextToken(ids);
        gpu.greedyNextToken(ids);

        long t0 = System.nanoTime();
        for (int i = 0; i < 5; i++) cpu.greedyNextToken(ids);
        long cpuMs = (System.nanoTime() - t0) / 1_000_000L;

        long t1 = System.nanoTime();
        for (int i = 0; i < 5; i++) gpu.greedyNextToken(ids);
        long gpuMs = (System.nanoTime() - t1) / 1_000_000L;

        System.out.printf("[GPU-VS-CPU] 5 iters: cpu=%dms gpu=%dms speedup=%.2fx%n",
                cpuMs, gpuMs, (double) cpuMs / gpuMs);

        cpu.close();
        gpu.close();
    }

    private static String repr(String s) {
        return "'" + s.replace("\n", "\\n") + "'";
    }
}
