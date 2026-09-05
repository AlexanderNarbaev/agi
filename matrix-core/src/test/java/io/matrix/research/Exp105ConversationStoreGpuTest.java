package io.matrix.research;

import io.matrix.api.ConversationStore;
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
 * EXP-MATRIX.51 — Real conversation using ConversationStore + GPU (RUN 105).
 */
class Exp105ConversationStoreGpuTest {

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
    void conversationStoreRetainsContext() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(12);
        if (!bridge.load()) return;

        ConversationStore store = new ConversationStore(20, 60_000);
        String user = "alice";

        // Turn 1: introduce self
        store.append(user, ConversationStore.Role.USER, "Hi, I'm Alex.");
        String t1 = callBridgeWithHistory(bridge, store, user);
        store.append(user, ConversationStore.Role.ASSISTANT, t1);

        // Turn 2: ask name back
        store.append(user, ConversationStore.Role.USER, "What's my name?");
        String t2 = callBridgeWithHistory(bridge, store, user);
        store.append(user, ConversationStore.Role.ASSISTANT, t2);

        // Verify context is preserved
        List<ConversationStore.Message> history = store.get(user);
        assertThat(history).hasSize(4);  // user, assistant, user, assistant

        // Recall should mention the name
        assertThat(t2.toLowerCase()).contains("alex");

        System.out.println("[CONV-STORE]");
        for (var m : history) {
            System.out.println("  " + m.role() + ": " + m.content());
        }

        bridge.close();
    }

    private static String callBridgeWithHistory(QwenOnnxBridge bridge,
                                                ConversationStore store, String user) {
        // Build messages from store
        List<QwenChatTemplate.Message> messages = new ArrayList<>();
        messages.add(QwenChatTemplate.Message.system(
                "You are a friendly assistant. Be brief."));
        for (var m : store.get(user)) {
            switch (m.role()) {
                case USER -> messages.add(QwenChatTemplate.Message.user(m.content()));
                case ASSISTANT -> messages.add(QwenChatTemplate.Message.assistant(m.content()));
                case SYSTEM -> messages.add(QwenChatTemplate.Message.system(m.content()));
            }
        }
        // Last message should be the user; remove and ask
        if (!messages.isEmpty()
                && messages.get(messages.size() - 1).role() == QwenChatTemplate.Role.USER) {
            String userMsg = messages.remove(messages.size() - 1).content();
            return bridge.chatWithHistory(messages, userMsg, 12, 0.0, -1, 1.0);
        }
        return "";
    }
}
