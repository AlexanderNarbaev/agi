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
