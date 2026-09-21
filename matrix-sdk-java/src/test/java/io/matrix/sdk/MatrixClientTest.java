package io.matrix.sdk;

import io.matrix.sdk.dto.AnalyzeRequest;
import io.matrix.sdk.dto.AnalyzeResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatrixClientTest {

    @Test
    void testBuilderRequiresApiKey() {
        assertThrows(IllegalStateException.class,
            () -> MatrixClient.builder().build());
    }

    @Test
    void testBuilderWithApiKey() {
        try (MatrixClient client = MatrixClient.builder()
                .apiKey("test-key")
                .build()) {
            assertNotNull(client);
        }
    }

    @Test
    void testBuilderCustomBaseUrl() {
        try (MatrixClient client = MatrixClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:8080")
                .build()) {
            assertNotNull(client);
        }
    }

    @Test
    void testAnalyzeRequestTextFactory() {
        var req = AnalyzeRequest.text("Hello MATRIX");
        assertEquals("Hello MATRIX", req.input);
        assertEquals("text", req.contentType);
    }

    @Test
    void testAnalyzeRequestAudioFactory() {
        var req = AnalyzeRequest.audio("base64audiodata");
        assertEquals("audio", req.contentType);
    }

    @Test
    void testAnalyzeRequestImageFactory() {
        var req = AnalyzeRequest.image("base64imagedata");
        assertEquals("image", req.contentType);
    }

    @Test
    void testAnalyzeResponseAcceptsPayload() {
        AnalyzeResponse resp = new AnalyzeResponse();
        resp.reply = "Hello";
        resp.confidence = 0.95;
        resp.accepted = true;
        resp.explainId = "expl_abc";

        assertEquals("Hello", resp.reply);
        assertEquals(0.95, resp.confidence);
        assertTrue(resp.accepted);
        assertEquals("expl_abc", resp.explainId);
    }

    @Test
    void testMatrixException() {
        MatrixException ex = new MatrixException(401, "Unauthorized");
        assertEquals(401, ex.statusCode);
        assertEquals("Unauthorized", ex.errorBody);
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void testAnalyzeCallFluentApi() {
        // Just verify the fluent builder compiles and chains
        try (MatrixClient client = MatrixClient.builder()
                .apiKey("test-key")
                .build()) {
            var call = client.analyze()
                .text("test")
                .context("ctx")
                .model("default");
            assertNotNull(call);
        }
    }

    @Test
    void testClientIsAutoCloseable() {
        MatrixClient client = MatrixClient.builder().apiKey("test").build();
        assertDoesNotThrow(client::close);
    }

    @Test
    void testFederateNodeRecord() {
        MatrixClient.FederateNode node = new MatrixClient.FederateNode(
            "node_1", "us-east", 100, "2026-09-21");
        assertEquals("node_1", node.nodeId());
        assertEquals("us-east", node.region());
        assertEquals(100, node.shardCapacity());
    }
}
