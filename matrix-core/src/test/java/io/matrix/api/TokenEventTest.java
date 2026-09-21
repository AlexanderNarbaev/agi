package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 90 — TokenEvent + streamGenerate tests. */
class TokenEventTest {

    @Test
    void contentToken() {
        TokenEvent e = new TokenEvent(1234, " hello", false, 0);
        assertThat(e.tokenId()).isEqualTo(1234);
        assertThat(e.text()).isEqualTo(" hello");
        assertThat(e.isEos()).isFalse();
        assertThat(e.stepIndex()).isZero();
        assertThat(e.isContent()).isTrue();
    }

    @Test
    void eosMarker() {
        TokenEvent e = TokenEvent.eos(5);
        assertThat(e.isEos()).isTrue();
        assertThat(e.tokenId()).isEqualTo(-1);
        assertThat(e.isContent()).isFalse();
    }

    @Test
    void errorMarker() {
        TokenEvent e = TokenEvent.error("boom");
        assertThat(e.text()).startsWith("ERROR:");
        assertThat(e.isContent()).isFalse();
    }

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

    static boolean modelAvailable() {
        return findModelDir() != null;
    }

    @Test
    @EnabledIf("modelAvailable")
    void streamGenerateProducesEvents() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        Iterable<TokenEvent> events = bridge.streamGenerate("Hi", 4);
        int count = 0;
        boolean sawEos = false;
        StringBuilder text = new StringBuilder();
        for (TokenEvent e : events) {
            if (e.isContent()) {
                text.append(e.text());
                count++;
            }
            if (e.isEos()) sawEos = true;
        }
        // Either we got content, or we hit EOS before generating any
        assertThat(count + (sawEos ? 1 : 0)).isGreaterThan(0);
        // The decoded text should be properly formatted (no raw Ġ)
        assertThat(text.toString()).doesNotContain("Ġ");
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void streamGenerateRespectsMaxTokens() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(4);
        if (!bridge.load()) return;

        Iterable<TokenEvent> events = bridge.streamGenerate("Hello", 3);
        int count = 0;
        for (TokenEvent e : events) {
            if (e.isContent()) count++;
        }
        // Should not exceed maxTokens
        assertThat(count).isLessThanOrEqualTo(3);
        bridge.close();
    }
}
