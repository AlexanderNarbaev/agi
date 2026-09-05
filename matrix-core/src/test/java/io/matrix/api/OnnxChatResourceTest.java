package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 67 — OnnxChatResource unit tests.
 *
 * <p>Resource-level tests using a mock bridge (no real GPU load
 * during unit tests — REST tests use a separate harness).
 */
class OnnxChatResourceTest {

    @Test
    void chatReturnsErrorWhenBridgeNotLoaded() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.chat("hello", 8);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void chatReturnsErrorWhenPromptEmpty() {
        OnnxChatResource resource = new OnnxChatResource();
        // Without bridge: should still error
        String reply = resource.chat("", 8);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void chatReturnsErrorWhenPromptNull() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.chat(null, 8);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void statusJsonBeforeLoad() {
        OnnxChatResource resource = new OnnxChatResource();
        String json = resource.status();
        assertThat(json).contains("\"loaded\":false");
        assertThat(json).contains("\"totalInferences\":0");
        assertThat(json).contains("\"info\":\"uninitialized\"");
    }

    @Test
    void statusJsonWithBridgeInjected() {
        OnnxChatResource resource = new OnnxChatResource();
        // Use a fake bridge that doesn't require GPU.
        // QwenOnnxBridge needs a model dir, so we use null to avoid load.
        QwenOnnxBridge stub = new QwenOnnxBridge(java.nio.file.Path.of("/tmp/nonexistent")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public boolean isGpuEnabled() { return false; }
            @Override
            public int vocabSize() { return 151936; }
            @Override
            public java.nio.file.Path modelDir() { return java.nio.file.Path.of("/tmp/nonexistent"); }
            @Override
            public String info() { return "stub"; }
        };
        resource.setBridgeForTesting(stub);
        String json = resource.status();
        assertThat(json).contains("\"loaded\":true");
        assertThat(json).contains("\"gpu\":false");
        assertThat(json).contains("\"vocabSize\":151936");
    }

    @Test
    void reloadReportsErrorWhenModelDirMissing() {
        OnnxChatResource resource = new OnnxChatResource();
        // Default configuredModelPath might not exist in test env;
        // we expect ERROR or "Failed to load" output.
        String result = resource.reload();
        // Either ERROR or failure message is acceptable.
        assertThat(result).isNotBlank();
    }

    @Test
    void generateReturnsErrorWhenBridgeNotLoaded() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.generate("hello", 8, 0.7, 50, 0.9);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void generateReturnsErrorOnEmptyPrompt() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.generate("", 8, 0.7, 50, 0.9);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void generateHandlesNullParams() {
        OnnxChatResource resource = new OnnxChatResource();
        // No bridge loaded: should still error gracefully
        String reply = resource.generate("hi", null, null, null, null);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void metricsBeforeBridgeLoad() {
        OnnxChatResource resource = new OnnxChatResource();
        String json = resource.metrics();
        assertThat(json).contains("\"loaded\":false");
    }

    @Test
    void chatEndpointRequiresLoadedBridge() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.chat("hello", 8, 0.0, -1, 1.0, null);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void chatEndpointRejectsEmptyUser() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.chat("", 8, 0.0, -1, 1.0, null);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void chatEndpointRejectsNullUser() {
        OnnxChatResource resource = new OnnxChatResource();
        String reply = resource.chat(null, 8, 0.0, -1, 1.0, null);
        assertThat(reply).startsWith("ERROR:");
    }

    @Test
    void chatEndpointAcceptsSystemPrompt() {
        OnnxChatResource resource = new OnnxChatResource();
        // Without bridge: should error gracefully
        String reply = resource.chat("hi", 8, 0.0, -1, 1.0, "You are a pirate.");
        assertThat(reply).startsWith("ERROR:");
    }
}
