package io.matrix.research;

import io.matrix.api.ChainBridgeAdapter;
import io.matrix.api.QwenChatTemplate;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.58 — ChainBridgeAdapter with real Qwen (RUN 127).
 */
class Exp127ChainBridgeAdapterGpuTest {

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
    void adapterGeneratesRealText() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(12);
        if (!bridge.load()) return;

        ChainBridgeAdapter adapter = new ChainBridgeAdapter(bridge);
        assertThat(adapter.isReady()).isTrue();

        String reply = adapter.generateResponse("What is 2+2?", 12);
        assertThat(reply).isNotNull();
        assertThat(reply).isNotBlank();
        System.out.println("[CHAIN-BRIDGE] " + reply);

        assertThat(adapter.callsForwarded()).isEqualTo(1);
        assertThat(adapter.tokensForwarded()).isGreaterThan(0);

        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void adapterMultiTurn() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        ChainBridgeAdapter adapter = new ChainBridgeAdapter(bridge);
        var history = List.of(
                QwenChatTemplate.Message.system("Be brief."),
                QwenChatTemplate.Message.user("My name is Sam."));
        String reply = adapter.generateResponseWithHistory(
                history, "What's my name?", 8);
        assertThat(reply).isNotNull();
        // The model should remember the name
        assertThat(reply.toLowerCase()).contains("sam");
        System.out.println("[CHAIN-BRIDGE-MULTI] " + reply);

        bridge.close();
    }
}
