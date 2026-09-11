package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 126 — ChainBridgeAdapter unit tests. */
class ChainBridgeAdapterTest {

    @Test
    void notReadyWhenBridgeNotLoaded() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none"));
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        assertThat(adapter.isReady()).isFalse();
        assertThat(adapter.generateResponse("hi", 4)).isNull();
    }

    @Test
    void readyWhenBridgeLoaded() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
        };
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        assertThat(adapter.isReady()).isTrue();
    }

    @Test
    void counterStartsAtZero() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "ok";
            }
        };
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        assertThat(adapter.callsForwarded()).isZero();
        assertThat(adapter.tokensForwarded()).isZero();
        adapter.generateResponse("hi", 4);
        assertThat(adapter.callsForwarded()).isEqualTo(1);
        // "ok" = 2 chars / 4 + 1 = 1 token
        assertThat(adapter.tokensForwarded()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void generatesViaStub() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "stub:" + prompt;
            }
        };
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        String reply = adapter.generateResponse("hi", 4);
        assertThat(reply).startsWith("stub:");
    }

    @Test
    void multiTurnUsesChatWithHistory() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String chatWithHistory(List<QwenChatTemplate.Message> h,
                                          String u, int t,
                                          double temp, int k, double p) {
                return "history:" + u;
            }
        };
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        var history = List.of(
                QwenChatTemplate.Message.system("sys"),
                QwenChatTemplate.Message.user("prev"));
        String reply = adapter.generateResponseWithHistory(history, "new", 4);
        assertThat(reply).startsWith("history:");
    }

    @Test
    void generateReturnsNullOnException() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                throw new RuntimeException("boom");
            }
        };
        ChainBridgeAdapter adapter = new ChainBridgeAdapter(stub);
        assertThat(adapter.generateResponse("hi", 4)).isNull();
    }
}
