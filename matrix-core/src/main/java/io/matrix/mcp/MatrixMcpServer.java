package io.matrix.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.agent.AgentBrainService;
import io.matrix.explain.BooleanExplainability;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.HierarchicalBrain;
import io.matrix.rag.BooleanRag;
import io.matrix.simulation.AgentBrain;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MCP (Model Context Protocol) server for MATRIX.
 * Exposes MATRIX capabilities as MCP tools/resources for external AI agents.
 *
 * <p>Tools exposed:
 * <ul>
 *   <li>{@code matrix_query} — query the Boolean RAG knowledge base</li>
 *   <li>{@code matrix_act} — trigger agent action through AgentBrainService</li>
 *   <li>{@code matrix_explain} — get SHAP-style explanation for a decision</li>
 *   <li>{@code matrix_traverse} — traverse the knowledge graph</li>
 * </ul>
 *
 * <p>Resources exposed:
 * <ul>
 *   <li>{@code matrix://brain/state} — current brain configuration</li>
 *   <li>{@code matrix://knowledge/entities} — all known entities</li>
 *   <li>{@code matrix://lifecycle/history} — agent lifecycle timeline</li>
 * </ul>
 *
 * <p>This is a protocol stub for external MCP integration — lightweight,
 * no full MCP transport. Uses Jackson for JSON serialization.
 */
public final class MatrixMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    /**
     * An MCP tool definition: name, description, and handler function.
     * The handler receives a map of arguments and returns a result map.
     */
    public record McpTool(
            String name,
            String description,
            Function<Map<String, Object>, Map<String, Object>> handler) {
        public McpTool {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(handler, "handler");
        }
    }

    /**
     * An MCP resource definition: URI, name, description, and content supplier.
     */
    public record McpResource(
            String uri,
            String name,
            String description,
            Supplier<String> contentSupplier) {
        public McpResource {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(contentSupplier, "contentSupplier");
        }
    }

    private final AgentBrainService brain;
    private final BooleanRag rag;
    private final KnowledgeGraphStore knowledgeGraph;
    private final List<McpTool> tools;
    private final List<McpResource> resources;

    /**
     * Creates a new MCP server with the required MATRIX dependencies.
     * Tools and resources must be registered separately.
     */
    public MatrixMcpServer(AgentBrainService brain, BooleanRag rag,
                           KnowledgeGraphStore knowledgeGraph) {
        this.brain = Objects.requireNonNull(brain, "brain");
        this.rag = Objects.requireNonNull(rag, "rag");
        this.knowledgeGraph = Objects.requireNonNull(knowledgeGraph, "knowledgeGraph");
        this.tools = new ArrayList<>();
        this.resources = new ArrayList<>();
    }

    /**
     * Registers a tool on this server. Returns {@code this} for fluent chaining.
     */
    public MatrixMcpServer registerTool(McpTool tool) {
        tools.add(Objects.requireNonNull(tool, "tool"));
        return this;
    }

    /**
     * Registers a resource on this server. Returns {@code this} for fluent chaining.
     */
    public MatrixMcpServer registerResource(McpResource resource) {
        resources.add(Objects.requireNonNull(resource, "resource"));
        return this;
    }

    // ─── MCP tools/list ───────────────────────────────────────────────────

    /**
     * Lists all registered tools in MCP-compatible format.
     *
     * @return list of tool descriptors with keys: {@code name}, {@code description}
     */
    public List<Map<String, String>> listTools() {
        return tools.stream()
                .map(t -> Map.of("name", t.name(), "description", t.description()))
                .toList();
    }

    // ─── MCP tools/call ───────────────────────────────────────────────────

    /**
     * Calls a registered tool by name with the given arguments.
     *
     * @param toolName the tool name (e.g. {@code "matrix_query"})
     * @param args     tool-specific arguments as key-value pairs
     * @return result map with at least a {@code "status"} key
     * @throws IllegalArgumentException if the tool is not found
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> args) {
        McpTool tool = findTool(toolName);
        try {
            Map<String, Object> result = tool.handler().apply(args != null ? args : Map.of());
            if (result == null) {
                return Map.of("status", "error", "error", "handler returned null");
            }
            return result;
        } catch (Exception e) {
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    // ─── MCP resources/list ───────────────────────────────────────────────

    /**
     * Lists all registered resources in MCP-compatible format.
     *
     * @return list of resource descriptors with keys: {@code uri}, {@code name}, {@code description}
     */
    public List<Map<String, String>> listResources() {
        return resources.stream()
                .map(r -> Map.of(
                        "uri", r.uri(),
                        "name", r.name(),
                        "description", r.description()))
                .toList();
    }

    // ─── MCP resources/read ───────────────────────────────────────────────

    /**
     * Reads the content of a registered resource by its URI.
     *
     * @param uri the resource URI (e.g. {@code "matrix://brain/state"})
     * @return the resource content as a string
     * @throws IllegalArgumentException if the resource is not found
     */
    public String readResource(String uri) {
        McpResource resource = findResource(uri);
        return resource.contentSupplier().get();
    }

    // ─── MCP JSON dispatcher ──────────────────────────────────────────────

    /**
     * Handles a raw JSON MCP request and returns a JSON response.
     *
     * <p>Expected request format:
     * <pre>{@code
     * {
     *   "method": "tools/list" | "tools/call" | "resources/list" | "resources/read",
     *   "params": { ... }
     * }
     * }</pre>
     *
     * <p>For {@code tools/call}, params must include {@code "name"} and {@code "arguments"}.
     * For {@code resources/read}, params must include {@code "uri"}.
     *
     * @param jsonRequest raw JSON request string
     * @return JSON response string
     */
    public String handleRequest(String jsonRequest) {
        if (jsonRequest == null || jsonRequest.isBlank()) {
            return errorResponse("empty request");
        }

        Map<String, Object> request;
        try {
            request = MAPPER.readValue(jsonRequest, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return errorResponse("invalid JSON: " + e.getMessage());
        }

        String method = (String) request.get("method");
        if (method == null) {
            return errorResponse("missing 'method' field");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        try {
            return switch (method) {
                case "tools/list" -> successResponse(Map.of("tools", listTools()));
                case "tools/call" -> handleToolCall(params);
                case "resources/list" -> successResponse(Map.of("resources", listResources()));
                case "resources/read" -> handleResourceRead(params);
                default -> errorResponse("unknown method: " + method);
            };
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    // ─── Factory ──────────────────────────────────────────────────────────

    /**
     * Creates a {@link MatrixMcpServer} pre-registered with all default MATRIX tools
     * and resources.
     *
     * @param brain          agent brain service
     * @param rag            Boolean RAG system
     * @param knowledgeGraph knowledge graph store
     * @return fully configured MCP server
     */
    public static MatrixMcpServer createDefault(
            AgentBrainService brain, BooleanRag rag, KnowledgeGraphStore knowledgeGraph) {
        MatrixMcpServer server = new MatrixMcpServer(brain, rag, knowledgeGraph);

        // ── Tools ────────────────────────────────────────────────────────

        server.registerTool(new McpTool(
                "matrix_query",
                "Query the Boolean RAG knowledge base with a boolean vector. "
                        + "Args: { 'query': [1, 0, 1, 0, ...] } — long array of bits.",
                args -> {
                    long[] query = parseLongArray(args.get("query"));
                    var result = rag.query(query);
                    return Map.of(
                            "status", "ok",
                            "hits", result.knowledgeHits().stream()
                                    .map(h -> Map.of("id", h.id(), "distance", h.distance()))
                                    .toList(),
                            "expandedVectorRows", result.expandedVector().length);
                }));

        server.registerTool(new McpTool(
                "matrix_act",
                "Trigger agent action through AgentBrainService. "
                        + "Args: { 'sensorBits': 12345 } — long integer sensor input.",
                args -> {
                    long sensorBits = parseLong(args.get("sensorBits"));
                    String action = brain.act(sensorBits);
                    return Map.of("status", "ok", "action", action);
                }));

        server.registerTool(new McpTool(
                "matrix_explain",
                "Get SHAP-style explanation for a decision tree. "
                        + "Args: { 'sensorBits': 12345, 'layer': 'action'|'feature'|'sensor', "
                        + "'neuronIndex': 0, 'format': 'default'|'brief'|'single' }. "
                        + "Format defaults to 'brief'.",
                args -> {
                    long sensorBits = parseLong(args.get("sensorBits"));
                    String layer = (String) args.getOrDefault("layer", "action");
                    int neuronIndex = parseInt(args.get("neuronIndex"), 0);
                    String format = (String) args.getOrDefault("format", "brief");

                    HierarchicalBrain hb = brain.brain();
                    List<DecisionTree> neurons = switch (layer) {
                        case "sensor" -> hb.sensorLayer().neurons();
                        case "feature" -> hb.featureLayer().neurons();
                        default -> hb.actionLayer().neurons();
                    };
                    if (neuronIndex < 0 || neuronIndex >= neurons.size()) {
                        return Map.of("status", "error",
                                "error", "neuronIndex out of range [0, " + (neurons.size() - 1) + "]");
                    }

                    DecisionTree tree = neurons.get(neuronIndex);
                    BitSet input = toBitSet(sensorBits);
                    var importances = BooleanExplainability.explain(tree, input);
                    String explanation = BooleanExplainability.toExplanation(importances, format);
                    var topFeatures = BooleanExplainability.topFeatures(importances, 5).stream()
                            .map(fi -> Map.of(
                                    "bitIndex", fi.bitIndex(),
                                    "inputValue", fi.inputValue(),
                                    "shapValue", fi.shapValue(),
                                    "explanation", fi.explanation()))
                            .toList();

                    return Map.of(
                            "status", "ok",
                            "layer", layer,
                            "neuronIndex", neuronIndex,
                            "topFeatures", topFeatures,
                            "fullExplanation", explanation);
                }));

        server.registerTool(new McpTool(
                "matrix_traverse",
                "Traverse the knowledge graph from a starting entity. "
                        + "Args: { 'startId': 'entity1', 'maxDepth': 2 }.",
                args -> {
                    String startId = (String) args.get("startId");
                    int maxDepth = parseInt(args.get("maxDepth"), 3);
                    if (startId == null) {
                        return Map.of("status", "error", "error", "missing 'startId'");
                    }
                    var results = knowledgeGraph.traverse(startId, maxDepth, r -> true);
                    var paths = results.stream()
                            .map(r -> Map.of(
                                    "path", r.path().stream().map(
                                            KnowledgeGraphStore.Entity::id).toList(),
                                    "edges", r.edges().stream()
                                            .map(e -> Map.of("from", e.fromId(), "to", e.toId(),
                                                    "predicate", e.predicate(), "weight", e.weight()))
                                            .toList(),
                                    "totalWeight", r.totalWeight(),
                                    "depth", r.depth()))
                            .toList();
                    return Map.of("status", "ok", "paths", paths, "count", paths.size());
                }));

        // ── Resources ────────────────────────────────────────────────────

        server.registerResource(new McpResource(
                "matrix://brain/state",
                "Brain Configuration",
                "Current hierarchical brain layer configuration (neuron counts, layer sizes).",
                () -> {
                    HierarchicalBrain hb = brain.brain();
                    var sensorNeurons = hb.sensorLayer().neurons();
                    var featureNeurons = hb.featureLayer().neurons();
                    var actionNeurons = hb.actionLayer().neurons();
                    Map<String, Object> state = new HashMap<>();
                    state.put("sensorLayer", Map.of(
                            "neuronCount", sensorNeurons.size(),
                            "k", hb.sensorLayer().k()));
                    state.put("featureLayer", Map.of(
                            "neuronCount", featureNeurons.size(),
                            "k", hb.featureLayer().k()));
                    state.put("actionLayer", Map.of(
                            "neuronCount", actionNeurons.size(),
                            "k", hb.actionLayer().k()));
                    try {
                        return MAPPER.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(state);
                    } catch (JsonProcessingException e) {
                        return "{\"error\":\"" + e.getMessage() + "\"}";
                    }
                }));

        server.registerResource(new McpResource(
                "matrix://knowledge/entities",
                "Knowledge Entities",
                "JSON dump of all entities in the knowledge graph.",
                () -> {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        var entities = new ArrayList<Map<String, Object>>();
                        for (int i = 0; i < Math.min(knowledgeGraph.entityCount(), 100); i++) {
                            // Iterate through all entities via toJson
                        }
                        return knowledgeGraph.toJson();
                    } catch (Exception e) {
                        return "{\"error\":\"" + e.getMessage() + "\"}";
                    }
                }));

        server.registerResource(new McpResource(
                "matrix://lifecycle/history",
                "Lifecycle History",
                "JSON array of lifecycle state snapshots.",
                () -> {
                    // Return a stub — lifecycle manager is not wired in createDefault
                    return "{\"status\":\"lifecycle manager not attached\",\"history\":[]}";
                }));

        return server;
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    private McpTool findTool(String name) {
        return tools.stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tool not found: " + name));
    }

    private McpResource findResource(String uri) {
        return resources.stream()
                .filter(r -> r.uri().equals(uri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resource not found: " + uri));
    }

    private String handleToolCall(Map<String, Object> params) {
        if (params == null || !params.containsKey("name")) {
            return errorResponse("missing tool 'name' in params");
        }
        String name = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault(
                "arguments", Map.of());
        return successResponse(callTool(name, arguments));
    }

    private String handleResourceRead(Map<String, Object> params) {
        if (params == null || !params.containsKey("uri")) {
            return errorResponse("missing 'uri' in params");
        }
        String uri = (String) params.get("uri");
        return successResponse(Map.of("content", readResource(uri)));
    }

    private static String successResponse(Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("result", result);
        try {
            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"error\":\"serialization failed\"}";
        }
    }

    private static String errorResponse(String message) {
        try {
            return MAPPER.writeValueAsString(
                    Map.of("status", "error", "error", message));
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"error\":\"serialization failed\"}";
        }
    }

    // ─── Argument parsing ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static long[] parseLongArray(Object value) {
        if (value instanceof List<?> list) {
            long[] arr = new long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                arr[i] = item instanceof Number n ? n.longValue() : 0L;
            }
            return arr;
        }
        if (value instanceof long[] arr) {
            return arr;
        }
        return new long[0];
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static BitSet toBitSet(long bits) {
        BitSet bs = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) bs.set(i);
        }
        return bs;
    }
}
