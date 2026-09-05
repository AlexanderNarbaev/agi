package io.matrix.api;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;

/**
 * RUN 67 — /v1/onnx/chat endpoint exposing QwenOnnxBridge.
 *
 * <p>Lightweight REST interface that lets external clients query
 * the actual Qwen2.5-0.5B model running in Java on GPU.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /v1/onnx/chat?prompt=...&max_tokens=...}</li>
 *   <li>{@code GET /v1/onnx/status}</li>
 *   <li>{@code POST /v1/onnx/reload}</li>
 * </ul>
 */
@Path("/v1/onnx")
@ApplicationScoped
public class OnnxChatResource {

    private static final Logger log = LoggerFactory.getLogger(OnnxChatResource.class);

    @ConfigProperty(name = "matrix.qwen.model-path",
            defaultValue = "models/hf_cache/qwen05b")
    String configuredModelPath;

    @ConfigProperty(name = "matrix.onnx.use-gpu", defaultValue = "true")
    boolean defaultUseGpu;

    @ConfigProperty(name = "matrix.onnx.auto-load", defaultValue = "true")
    boolean autoLoad;

    private volatile QwenOnnxBridge bridge;
    private volatile long lastLoadMs = 0;
    private volatile long totalInferences = 0;
    private final long startTimeMs = System.currentTimeMillis();
    private final TokenUsageTracker tracker = new TokenUsageTracker();

    /** Load bridge on startup if configured. */
    void onStart(@Observes StartupEvent ev) {
        if (!autoLoad) {
            log.info("OnnxChatResource: auto-load disabled");
            return;
        }
        reloadBridge();
    }

    @POST
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("prompt") String prompt,
                       @QueryParam("max_tokens") Integer maxTokens) {
        if (bridge == null || !bridge.isLoaded()) {
            return "ERROR: ONNX bridge not loaded. Call /v1/onnx/reload first.";
        }
        if (prompt == null || prompt.isBlank()) {
            return "ERROR: empty prompt";
        }
        int tokens = maxTokens == null ? 64 : Math.max(1, Math.min(256, maxTokens));
        String reply = bridge.generate(prompt, tokens);
        totalInferences++;
        return reply == null ? "" : reply;
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public String status() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"loaded\":").append(bridge != null && bridge.isLoaded());
        sb.append(",\"gpu\":").append(bridge != null && bridge.isGpuEnabled());
        sb.append(",\"totalInferences\":").append(totalInferences);
        sb.append(",\"lastLoadMs\":").append(lastLoadMs);
        if (bridge != null) {
            sb.append(",\"vocabSize\":").append(bridge.vocabSize());
            sb.append(",\"modelDir\":\"").append(bridge.modelDir()).append("\"");
        }
        sb.append(",\"info\":\"").append(
                bridge == null ? "uninitialized" : bridge.info().replace("\"", "'")
        ).append("\"");
        sb.append("}");
        return sb.toString();
    }

    @POST
    @Path("/reload")
    @Produces(MediaType.TEXT_PLAIN)
    public String reload() {
        return reloadBridge();
    }

    @POST
    @Path("/stream")
    @Produces(MediaType.TEXT_PLAIN)
    public String stream(@QueryParam("prompt") String prompt,
                         @QueryParam("max_tokens") Integer maxTokens) {
        if (bridge == null || !bridge.isLoaded()) {
            return "ERROR: ONNX bridge not loaded";
        }
        if (prompt == null || prompt.isBlank()) {
            return "ERROR: empty prompt";
        }
        int tokens = maxTokens == null ? 32 : Math.max(1, Math.min(256, maxTokens));
        StringBuilder sb = new StringBuilder();
        for (TokenEvent e : bridge.streamGenerate(prompt, tokens)) {
            if (e.isContent()) {
                sb.append(e.text());
            }
            if (e.isEos()) {
                sb.append("\n[EOS]");
                break;
            }
        }
        totalInferences++;
        return sb.toString();
    }

    @POST
    @Path("/compare")
    @Produces(MediaType.TEXT_PLAIN)
    public String compare(@QueryParam("prompt") String prompt,
                          @QueryParam("max_tokens") Integer maxTokens) {
        if (bridge == null || !bridge.isLoaded()) {
            return "{\"error\":\"ONNX bridge not loaded\"}";
        }
        if (prompt == null || prompt.isBlank()) {
            return "{\"error\":\"empty prompt\"}";
        }
        int tokens = maxTokens == null ? 32 : Math.max(1, Math.min(128, maxTokens));
        long t0 = System.nanoTime();
        String onnxReply = bridge.generate(prompt, tokens);
        long onnxMs = (System.nanoTime() - t0) / 1_000_000L;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"prompt\":\"").append(jsonEscape(prompt)).append("\",");
        sb.append("\"onnx\":{");
        sb.append("\"reply\":\"").append(jsonEscape(onnxReply)).append("\",");
        sb.append("\"latencyMs\":").append(onnxMs).append(",");
        sb.append("\"gpu\":").append(bridge.isGpuEnabled());
        sb.append("},");
        sb.append("\"info\":\"").append(bridge.info().replace("\"", "'")).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Visible-for-testing wrapper around jsonEscape. */
    static String jsonEscapePublic(String s) {
        return jsonEscape(s);
    }

    /** Visible-for-testing accessor for the token usage tracker. */
    public TokenUsageTracker getTracker() {
        return tracker;
    }

    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public String metrics() {
        if (bridge == null) {
            return "{\"loaded\":false}";
        }
        return "{\"loaded\":" + bridge.isLoaded()
                + ",\"inference\":" + bridge.metrics().toJson() + "}";
    }

    @POST
    @Path("/generate")
    @Produces(MediaType.TEXT_PLAIN)
    public String generate(@QueryParam("prompt") String prompt,
                            @QueryParam("max_tokens") Integer maxTokens,
                            @QueryParam("temperature") Double temperature,
                            @QueryParam("top_k") Integer topK,
                            @QueryParam("top_p") Double topP) {
        if (bridge == null || !bridge.isLoaded()) {
            return "ERROR: ONNX bridge not loaded";
        }
        if (prompt == null || prompt.isBlank()) {
            return "ERROR: empty prompt";
        }
        int tokens = maxTokens == null ? 32 : Math.max(1, Math.min(256, maxTokens));
        double temp = temperature == null ? 0.0 : temperature;
        int k = topK == null ? -1 : Math.max(-1, topK);
        double p = topP == null ? 1.0 : Math.max(0.0, Math.min(1.0, topP));
        String reply = bridge.generateSampled(prompt, tokens, temp, k, p);
        totalInferences++;
        return reply == null ? "" : reply;
    }

    @POST
    @Path("/embed")
    @Produces(MediaType.TEXT_PLAIN)
    public String embed(@QueryParam("text") String text) {
        if (bridge == null || !bridge.isLoaded()) {
            return "ERROR: ONNX bridge not loaded";
        }
        if (text == null || text.isBlank()) {
            return "ERROR: empty text";
        }
        try {
            TextEmbedder embedder = new TextEmbedder(bridge);
            float[] vec = embedder.embed(text);
            // Return as comma-separated
            StringBuilder sb = new StringBuilder();
            sb.append(vec.length).append(":");
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(vec[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @GET
    @Path("/usage")
    @Produces(MediaType.APPLICATION_JSON)
    public String usage() {
        TokenUsageTracker t = tracker;
        if (t == null) {
            return "{\"totalTokens\":0}";
        }
        return t.toJson();
    }

    @GET
    @Path("/route")
    @Produces(MediaType.APPLICATION_JSON)
    public String route(@QueryParam("prompt") String prompt) {
        if (prompt == null) {
            return "{\"error\":\"empty prompt\"}";
        }
        AdaptiveModelRouter router = AdaptiveModelRouter.defaultRouter();
        int tokenCount = prompt.length() / 4;  // rough estimate
        return "{"
                + "\"promptLength\":" + prompt.length()
                + ",\"estimatedTokens\":" + tokenCount
                + ",\"tier\":\"" + router.tierFor(tokenCount) + "\""
                + ",\"model\":\"" + router.modelFor(tokenCount) + "\""
                + "}";
    }

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    public String version() {
        return "{"
                + "\"name\":\"matrix-onnx\","
                + "\"version\":\"1.0.0\","
                + "\"build\":\"" + (System.getenv("MATRIX_BUILD") != null
                        ? System.getenv("MATRIX_BUILD") : "dev") + "\","
                + "\"runtime\":\"java-" + System.getProperty("java.version") + "\","
                + "\"features\":["
                + "\"chat\",\"generate\",\"stream\",\"embed\","
                + "\"compare\",\"metrics\",\"health\",\"registry\","
                + "\"cache\",\"batch\",\"calibration\""
                + "]"
                + "}";
    }

    @GET
    @Path("/registry")
    @Produces(MediaType.APPLICATION_JSON)
    public String registry() {
        // For now, expose the bridge's model info as a single registration
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"registered\":[");
        if (bridge != null) {
            sb.append("{");
            sb.append("\"id\":\"qwen:0.5b:bf16\",");
            sb.append("\"name\":\"Qwen2.5-0.5B-Instruct\",");
            sb.append("\"size\":\"0.5b\",");
            sb.append("\"precision\":\"bf16\",");
            sb.append("\"loaded\":").append(bridge.isLoaded());
            sb.append(",\"gpu\":").append(bridge.isGpuEnabled());
            sb.append("}");
        }
        sb.append("],");
        sb.append("\"totalRegistered\":").append(bridge != null ? 1 : 0);
        sb.append(",\"totalLoaded\":").append(bridge != null && bridge.isLoaded() ? 1 : 0);
        sb.append("}");
        return sb.toString();
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public String health() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":");
        if (bridge == null) {
            sb.append("\"uninitialized\"");
        } else if (bridge.isLoaded()) {
            sb.append("\"healthy\"");
        } else {
            sb.append("\"not_loaded\"");
        }
        sb.append(",\"gpu\":").append(bridge != null && bridge.isGpuEnabled());
        sb.append(",\"inferences\":").append(totalInferences);
        sb.append(",\"uptimeMs\":").append(
                System.currentTimeMillis() - startTimeMs);
        sb.append("}");
        return sb.toString();
    }

    @POST
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("user") String user,
                       @QueryParam("max_tokens") Integer maxTokens,
                       @QueryParam("temperature") Double temperature,
                       @QueryParam("top_k") Integer topK,
                       @QueryParam("top_p") Double topP,
                       @QueryParam("system") String systemPrompt) {
        if (bridge == null || !bridge.isLoaded()) {
            return "ERROR: ONNX bridge not loaded";
        }
        if (user == null || user.isBlank()) {
            return "ERROR: empty user message";
        }
        int tokens = maxTokens == null ? 64 : Math.max(1, Math.min(256, maxTokens));
        double temp = temperature == null ? 0.0 : temperature;
        int k = topK == null ? -1 : Math.max(-1, topK);
        double p = topP == null ? 1.0 : Math.max(0.0, Math.min(1.0, topP));

        java.util.List<QwenChatTemplate.Message> history = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(QwenChatTemplate.Message.system(systemPrompt));
        }
        String reply = bridge.chatWithHistory(history, user, tokens, temp, k, p);
        totalInferences++;
        return reply == null ? "" : reply;
    }

    private synchronized String reloadBridge() {
        long t0 = System.nanoTime();
        if (bridge != null) {
            try { bridge.close(); } catch (Exception ignored) {}
        }
        String path = configuredModelPath == null
                ? "models/hf_cache/qwen05b" : configuredModelPath;
        java.nio.file.Path dir = java.nio.file.Path.of(path);
        if (!Files.isDirectory(dir)) {
            log.warn("OnnxChatResource: model dir not found: {}", dir);
            return "ERROR: model dir not found: " + dir;
        }
        bridge = new QwenOnnxBridge(dir);
        bridge.useGpu(defaultUseGpu);
        boolean ok = bridge.load();
        lastLoadMs = (System.nanoTime() - t0) / 1_000_000L;
        return ok
                ? "Loaded in " + lastLoadMs + "ms (gpu=" + defaultUseGpu + ")"
                : "Failed to load from " + dir;
    }

    /** Test-only hook to inject a bridge (skipping CDI). */
    void setBridgeForTesting(QwenOnnxBridge b) {
        this.bridge = b;
    }

    QwenOnnxBridge bridge() {
        return bridge;
    }
}
