package io.matrix.sdk;

import io.matrix.sdk.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * MATRIX Java SDK — production-grade client for matrix-api-gateway.
 *
 * <p>Usage:</p>
 * <pre>{@code
 *   try (MatrixClient client = MatrixClient.builder()
 *       .apiKey("sk-matrix-...")
 *       .baseUrl("https://api.matrix.ai")
 *       .build()) {
 *     AnalyzeResponse resp = client.analyze().text("Hello").call();
 *     System.out.println(resp.reply);
 *   }
 * }</pre>
 *
 * <p><b>CONSTITUTION compliance:</b> Pure transport. No LLM calls.</p>
 */
public final class MatrixClient implements AutoCloseable {

    private static final String DEFAULT_BASE_URL = "https://api.matrix.ai";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final boolean closed;

    private MatrixClient(Builder b) {
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey;
        this.timeout = b.timeout;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
        this.closed = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==========================================================================
    // Synchronous API
    // ==========================================================================

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        return post("/v1/analyze", request, AnalyzeResponse.class);
    }

    public AnalyzeCall analyze() {
        return new AnalyzeCall();
    }

    public ExplainResponse explain(String explainId) {
        return get("/v1/explain/" + encodePath(explainId), ExplainResponse.class);
    }

    public FederateResponse federate(FederateRequest request) {
        return post("/v1/federate", request, FederateResponse.class);
    }

    public List<FederateNode> listFederation() {
        return getAsList("/v1/federate", FederateNode.class);
    }

    public List<AuditEntry> auditLogs(int limit) {
        return getAsList("/v1/audit/logs?limit=" + limit, AuditEntry.class);
    }

    // ==========================================================================
    // Async API
    // ==========================================================================

    public CompletableFuture<AnalyzeResponse> analyzeAsync(AnalyzeRequest request) {
        return CompletableFuture.supplyAsync(() -> analyze(request));
    }

    public CompletableFuture<ExplainResponse> explainAsync(String explainId) {
        return CompletableFuture.supplyAsync(() -> explain(explainId));
    }

    // ==========================================================================
    // Streaming API (long polling — true SSE in T-08.5)
    // ==========================================================================

    /**
     * Subscribe to explain trace updates. Calls the user-provided consumer
     * for each received explain snapshot.
     */
    public void streamExplain(String explainId, java.util.function.Consumer<ExplainResponse> onUpdate,
                              Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        // T-08 implementation: poll every 250ms (true SSE in T-08.5)
        Thread poller = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ExplainResponse resp = explain(explainId);
                    onUpdate.accept(resp);
                    if (resp.confidenceBreakdown != null
                        && resp.confidenceBreakdown.aggregate() > 0) {
                        break;
                    }
                    Thread.sleep(250);
                }
                onComplete.run();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                onError.accept(e);
            }
        }, "matrix-stream-" + explainId);
        poller.setDaemon(true);
        poller.start();
    }

    // ==========================================================================
    // AnalyzeCall builder (fluent)
    // ==========================================================================

    public final class AnalyzeCall {
        private final AnalyzeRequest req = new AnalyzeRequest();

        public AnalyzeCall text(String text) {
            req.input = text;
            req.contentType = "text";
            return this;
        }
        public AnalyzeCall audio(String base64Audio) {
            req.input = base64Audio;
            req.contentType = "audio";
            return this;
        }
        public AnalyzeCall image(String base64Image) {
            req.input = base64Image;
            req.contentType = "image";
            return this;
        }
        public AnalyzeCall context(String ctx) { req.context = ctx; return this; }
        public AnalyzeCall model(String m) { req.model = m; return this; }

        public AnalyzeResponse call() {
            return analyze(req);
        }

        public CompletableFuture<AnalyzeResponse> callAsync() {
            return analyzeAsync(req);
        }
    }

    // ==========================================================================
    // Internal HTTP helpers
    // ==========================================================================

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(response, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("MATRIX request failed: " + e.getMessage(), e);
        }
    }

    private <T> T get(String path, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(response, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("MATRIX request failed: " + e.getMessage(), e);
        }
    }

    private <T> List<T> getAsList(String path, Class<T> elementType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new MatrixException(response.statusCode(), response.body());
            }
            return mapper.readValue(response.body(),
                mapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("MATRIX request failed: " + e.getMessage(), e);
        }
    }

    private <T> T handleResponse(HttpResponse<String> response, Class<T> type) throws IOException {
        if (response.statusCode() / 100 != 2) {
            throw new MatrixException(response.statusCode(), response.body());
        }
        return mapper.readValue(response.body(), type);
    }

    private static String encodePath(String id) {
        return java.net.URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public void close() {
        // HttpClient doesn't need explicit close in Java 25
    }

    // ==========================================================================
    // Builder
    // ==========================================================================

    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiKey;
        private Duration timeout = DEFAULT_TIMEOUT;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public MatrixClient build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("apiKey is required");
            }
            return new MatrixClient(this);
        }
    }

    public record FederateNode(String nodeId, String region, int shardCapacity, String joinedAt) {}
}
