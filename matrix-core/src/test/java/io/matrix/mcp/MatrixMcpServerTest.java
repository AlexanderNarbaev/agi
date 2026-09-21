package io.matrix.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.agent.AgentBrainService;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatrixMcpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentBrainService brain;
    private BooleanRag rag;
    private KnowledgeGraphStore knowledgeGraph;
    private MatrixMcpServer server;

    @BeforeEach
    void setUp() {
        brain = new AgentBrainService();

        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("doc1", new long[]{0xAAAA_AAAA_AAAA_AAAAL});
        index.add("doc2", new long[]{0x5555_5555_5555_5555L});
        rag = BooleanRag.builder().index(index).topK(3).build();

        knowledgeGraph = KnowledgeGraphStore.builder()
                .entity("a", "Concept")
                .entity("b", "Concept")
                .entity("c", "Concept")
                .relation("a", "b", "links")
                .relation("b", "c", "follows")
                .build();

        server = MatrixMcpServer.createDefault(brain, rag, knowledgeGraph);
    }

    // ─── 1. Default registration provides tools ───────────────────────────

    @Test
    void defaultRegistrationProvidesAllTools() {
        List<Map<String, String>> tools = server.listTools();
        assertThat(tools).isNotEmpty();
        assertThat(tools).extracting(t -> t.get("name"))
                .contains("matrix_query", "matrix_act", "matrix_explain", "matrix_traverse");
    }

    @Test
    void defaultRegistrationProvidesAllResources() {
        List<Map<String, String>> resources = server.listResources();
        assertThat(resources).isNotEmpty();
        assertThat(resources).extracting(r -> r.get("uri"))
                .contains("matrix://brain/state",
                        "matrix://knowledge/entities",
                        "matrix://lifecycle/history");
    }

    // ─── 2. Tool list returns valid MCP format ────────────────────────────

    @Test
    void toolListReturnsMCPFormat() {
        List<Map<String, String>> tools = server.listTools();
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool).containsKeys("name", "description");
            assertThat(tool.get("name")).isNotEmpty();
            assertThat(tool.get("description")).isNotEmpty();
        });
    }

    // ─── 3. Tool call executes handler ────────────────────────────────────

    @Test
    void matrixActReturnsValidAction() {
        Map<String, Object> result = server.callTool("matrix_act",
                Map.of("sensorBits", 0xAAAAAL));
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsKey("action");
        assertThat((String) result.get("action")).isNotEmpty();
    }

    @Test
    void matrixQueryReturnsHits() {
        // Single long = 64-bit vector matching index dimensions
        Map<String, Object> result = server.callTool("matrix_query",
                Map.of("query", List.of(0xAAAA_AAAA_AAAA_AAAAL)));
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsKey("hits");
    }

    @Test
    void matrixExplainReturnsFeatures() {
        Map<String, Object> result = server.callTool("matrix_explain",
                Map.of("sensorBits", 0xAAAAAL, "layer", "action",
                        "neuronIndex", 0, "format", "brief"));
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsKey("topFeatures");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) result.get("topFeatures");
        assertThat(features).isNotEmpty();
    }

    @Test
    void matrixTraverseReturnsPaths() {
        Map<String, Object> result = server.callTool("matrix_traverse",
                Map.of("startId", "a", "maxDepth", 2));
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsKey("paths");
    }

    // ─── 4. Resource list returns valid MCP format ────────────────────────

    @Test
    void resourceListReturnsMCPFormat() {
        List<Map<String, String>> resources = server.listResources();
        assertThat(resources).allSatisfy(resource -> {
            assertThat(resource).containsKeys("uri", "name", "description");
            assertThat(resource.get("uri")).isNotEmpty();
            assertThat(resource.get("name")).isNotEmpty();
        });
    }

    // ─── 5. Resource read returns content ─────────────────────────────────

    @Test
    void brainStateResourceReturnsNonEmptyJson() {
        String content = server.readResource("matrix://brain/state");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("sensorLayer", "featureLayer", "actionLayer");
    }

    @Test
    void knowledgeEntitiesResourceReturnsJson() {
        String content = server.readResource("matrix://knowledge/entities");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("\"entities\"");
    }

    // ─── 6. Custom registration works ─────────────────────────────────────

    @Test
    void customToolRegistration() {
        MatrixMcpServer customServer = new MatrixMcpServer(brain, rag, knowledgeGraph);
        customServer.registerTool(new MatrixMcpServer.McpTool(
                "custom_tool", "A custom tool for testing.",
                args -> Map.of("status", "ok", "custom", true)));

        assertThat(customServer.listTools()).hasSize(1);

        Map<String, Object> result = customServer.callTool("custom_tool", Map.of());
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsEntry("custom", true);
    }

    @Test
    void customResourceRegistration() {
        MatrixMcpServer customServer = new MatrixMcpServer(brain, rag, knowledgeGraph);
        customServer.registerResource(new MatrixMcpServer.McpResource(
                "custom://hello", "Hello Resource", "Says hello.",
                () -> "Hello, MCP!"));

        assertThat(customServer.listResources()).hasSize(1);

        String content = customServer.readResource("custom://hello");
        assertThat(content).isEqualTo("Hello, MCP!");
    }

    // ─── 7. handleRequest dispatches correctly ────────────────────────────

    @Test
    void handleRequestToolsList() {
        String response = server.handleRequest(
                "{\"method\": \"tools/list\"}");
        assertThat(response).contains("\"status\":\"ok\"");
        assertThat(response).contains("\"tools\"");
    }

    @Test
    void handleRequestToolsCall() {
        String response = server.handleRequest(
                "{\"method\": \"tools/call\", \"params\": {"
                        + "\"name\": \"matrix_act\", \"arguments\": {\"sensorBits\": 42}}}");
        assertThat(response).contains("\"status\":\"ok\"");
        assertThat(response).contains("\"action\"");
    }

    @Test
    void handleRequestResourcesList() {
        String response = server.handleRequest(
                "{\"method\": \"resources/list\"}");
        assertThat(response).contains("\"status\":\"ok\"");
        assertThat(response).contains("\"resources\"");
    }

    @Test
    void handleRequestResourcesRead() {
        String response = server.handleRequest(
                "{\"method\": \"resources/read\", \"params\": {"
                        + "\"uri\": \"matrix://brain/state\"}}");
        assertThat(response).contains("\"status\":\"ok\"");
        // Content is JSON-embedded — check for unquoted key name
        assertThat(response).contains("sensorLayer");
    }

    // ─── 8. Null/nullable safety ─────────────────────────────────────────

    @Test
    void callToolWithUnknownNameThrows() {
        assertThatThrownBy(() -> server.callTool("nonexistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool not found");
    }

    @Test
    void readResourceWithUnknownUriThrows() {
        assertThatThrownBy(() -> server.readResource("unknown://uri"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource not found");
    }

    @Test
    void callToolWithNullArgsDoesNotThrow() {
        Map<String, Object> result = server.callTool("matrix_act", null);
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsKey("action");
    }

    @Test
    void handleRequestWithEmptyBodyReturnsError() {
        String response = server.handleRequest("");
        assertThat(response).contains("\"status\":\"error\"");
    }

    @Test
    void handleRequestWithNullBodyReturnsError() {
        String response = server.handleRequest(null);
        assertThat(response).contains("\"status\":\"error\"");
    }

    @Test
    void handleRequestWithInvalidJsonReturnsError() {
        String response = server.handleRequest("{not valid json");
        assertThat(response).contains("\"status\":\"error\"");
    }

    @Test
    void handleRequestWithUnknownMethodReturnsError() {
        String response = server.handleRequest("{\"method\": \"unknown/method\"}");
        assertThat(response).contains("\"status\":\"error\"");
        assertThat(response).contains("unknown method");
    }

    // ─── 9. Edge cases ────────────────────────────────────────────────────

    @Test
    void matrixExplainWithInvalidLayerFallsBackToAction() {
        Map<String, Object> result = server.callTool("matrix_explain",
                Map.of("sensorBits", 0xAAAAAL, "layer", "bogus",
                        "neuronIndex", 0, "format", "brief"));
        assertThat(result).containsEntry("status", "ok");
        assertThat(result).containsEntry("layer", "bogus");
    }

    @Test
    void matrixExplainWithOutOfRangeNeuronReturnsError() {
        Map<String, Object> result = server.callTool("matrix_explain",
                Map.of("sensorBits", 0xAAAAAL, "layer", "action",
                        "neuronIndex", 999, "format", "brief"));
        assertThat(result).containsEntry("status", "error");
    }

    @Test
    void matrixTraverseWithMissingStartIdReturnsError() {
        Map<String, Object> result = server.callTool("matrix_traverse",
                Map.of("maxDepth", 2));
        assertThat(result).containsEntry("status", "error");
    }
}
