package io.matrix.research;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;
import io.matrix.api.BpeTokenizer;
import io.matrix.api.QwenChatTemplate;
import io.matrix.api.OnnxRuntimeAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.42 — Debug: inspect actual generated token IDs (RUN 78).
 */
class Exp078TokenDebugTest {

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
    void inspectGeneratedTokenIds() throws Exception {
        Path dir = findModelDir();
        BpeTokenizer tokenizer = BpeTokenizer.fromModelDir(dir);
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(findOnnx());
        adapter.setUseGpu(true);
        if (!adapter.load()) return;

        // Encode prompt
        String prompt = QwenChatTemplate.buildUserPrompt("Hello");
        int[] promptIds = tokenizer.encode(prompt);
        System.out.println("[DEBUG] prompt=" + prompt);
        System.out.print("[DEBUG] prompt ids: ");
        for (int id : promptIds) System.out.print(id + " ");
        System.out.println();
        for (int id : promptIds) {
            String tok = tokenizer.reverseToken(id);
            System.out.printf("  id=%d token=%s%n", id, tok == null ? "<null>" : repr(tok));
        }

        // Generate 6 tokens
        StringBuilder fullIds = new StringBuilder();
        for (int step = 0; step < 6; step++) {
            long[] ids = new long[promptIds.length];
            for (int i = 0; i < promptIds.length; i++) ids[i] = promptIds[i];
            long next = adapter.greedyNextToken(ids);
            System.out.printf("[DEBUG] step %d: id=%d token=%s%n",
                    step, next, repr(tokenizer.reverseToken((int) next)));
            fullIds.append(next).append(" ");
            // Append for next iteration (autoregressive)
            int[] newIds = new int[promptIds.length + 1];
            System.arraycopy(promptIds, 0, newIds, 0, promptIds.length);
            newIds[promptIds.length] = (int) next;
            promptIds = newIds;
        }

        adapter.close();
    }

    private static String repr(String s) {
        if (s == null) return "null";
        return "'" + s.replace("\n", "\\n") + "'";
    }
}
