package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production observability endpoint (RUN 24).
 *
 * <p>Aggregates counters and telemetry from across the matrix services
 * into a single JSON response. Designed for monitoring scrapers
 * (Prometheus, Datadog, etc.).
 *
 * <p>Includes:
 * <ul>
 *   <li>Chain runner stats (eval count, avg eval microseconds)</li>
 *   <li>LM head stats (positive/negative updates, query count, vocab coverage)</li>
 *   <li>Feedback trainer stats (pos/neg/skipped updates)</li>
 *   <li>Feature cache stats (size, hit/miss ratio)</li>
 *   <li>Chat request count (in-process counter, since process start)</li>
 * </ul>
 *
 * <p>All counters are monotonic from process start; uptime is reported
 * alongside so monitoring tools can compute rates.
 *
 * <p>No external dependencies, no PII, no tenant-aware — this endpoint
 * is safe to expose for monitoring. Tenant-aware metrics belong in
 * /v1/admin/metrics (not implemented yet).
 */
@Path("/v1/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {

    /** Process-start timestamp for uptime calculation. */
    public static final long START_TIME_MS = System.currentTimeMillis();

    /** Chat request counter (incremented by OpenAIChatResource on each call). */
    private static final AtomicLong CHAT_REQUEST_COUNT = new AtomicLong();
    private static final AtomicLong CHAT_ERROR_COUNT = new AtomicLong();

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    LmHeadTrainer lmHeadTrainer;

    @Inject
    LmHeadFeedbackTrainer feedbackTrainer;

    @Inject
    ChainFeatureCache featureCache;

    @Inject
    OnnxRuntimeAdapter onnxAdapter;

    @Inject
    QwenModelAdapter qwenAdapter;

    /**
     * Increment the chat request counter (called from OpenAIChatResource).
     * Package-private static so any resource in the same package can call.
     */
    public static void recordChatRequest() {
        CHAT_REQUEST_COUNT.incrementAndGet();
    }

    public static void recordChatError() {
        CHAT_ERROR_COUNT.incrementAndGet();
    }

    public static long chatRequestCount() {
        return CHAT_REQUEST_COUNT.get();
    }

    public static long chatErrorCount() {
        return CHAT_ERROR_COUNT.get();
    }

    @GET
    public Map<String, Object> metrics() {
        long uptimeMs = System.currentTimeMillis() - START_TIME_MS;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uptimeMs", uptimeMs);
        body.put("uptimeSec", uptimeMs / 1000.0);

        // Chain runner
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("model", chainRunner.modelName());
        chain.put("layers", chainRunner.layerCount());
        chain.put("totalNeurons", chainRunner.totalNeurons());
        chain.put("totalEvals", chainRunner.totalEvalCount());
        chain.put("avgEvalMicros", chainRunner.avgEvalMicros());
        // RUN 45: per-layer stats — count neurons per layer.
        Map<Integer, Integer> neuronsPerLayer = new LinkedHashMap<>();
        try {
            var layers = chainRunner.layers();
            if (layers != null) {
                for (int i = 0; i < layers.size(); i++) {
                    var layer = layers.get(i);
                    if (layer != null) neuronsPerLayer.put(i, layer.neurons().size());
                }
            }
        } catch (Exception ignored) {}
        chain.put("neuronsPerLayer", neuronsPerLayer);
        body.put("chain", chain);

        // LM head
        Map<String, Object> lmHead = new LinkedHashMap<>();
        LmHead head = lmHeadTrainer != null ? lmHeadTrainer.lmHead() : null;
        if (head != null) {
            lmHead.put("vocabCoverage", head.vocabularyCoverage());
            lmHead.put("updateCount", head.updateCount());
            lmHead.put("positiveUpdates", head.positiveUpdateCount());
            lmHead.put("negativeUpdates", head.negativeUpdateCount());
            lmHead.put("queryCount", head.queryCount());
            lmHead.put("temperature", head.temperature());
        }
        lmHead.put("isTrained", lmHeadTrainer != null && lmHeadTrainer.isTrained());
        lmHead.put("trainedPairs", lmHeadTrainer != null ? lmHeadTrainer.trainedPairs() : 0);
        lmHead.put("trainedEpochs", lmHeadTrainer != null ? lmHeadTrainer.trainedEpochs() : 0);
        lmHead.put("lastTrainedAt", lmHeadTrainer != null ? lmHeadTrainer.lastTrainedAt() : "never");
        body.put("lmHead", lmHead);

        // Feedback trainer
        Map<String, Object> feedback = new LinkedHashMap<>();
        if (feedbackTrainer != null) {
            feedback.put("positiveUpdates", feedbackTrainer.positiveUpdates());
            feedback.put("negativeUpdates", feedbackTrainer.negativeUpdates());
            feedback.put("skippedUpdates", feedbackTrainer.skippedUpdates());
        }
        body.put("feedbackTrainer", feedback);

        // Feature cache
        Map<String, Object> cache = new LinkedHashMap<>();
        if (featureCache != null) {
            long hits = featureCache.hits();
            long misses = featureCache.misses();
            cache.put("size", featureCache.size());
            cache.put("hits", hits);
            cache.put("misses", misses);
            long total = hits + misses;
            cache.put("hitRate", total > 0 ? (double) hits / total : 0.0);
        }
        body.put("chainFeatureCache", cache);

        // Chat
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("requests", chatRequestCount());
        chat.put("errors", chatErrorCount());
        chat.put("errorRate", chatRequestCount() > 0
                ? (double) chatErrorCount() / chatRequestCount()
                : 0.0);
        body.put("chat", chat);

        // RUN 59: ONNX runtime + Qwen model metadata.
        Map<String, Object> onnx = new LinkedHashMap<>();
        if (onnxAdapter != null) {
            onnx.put("available", onnxAdapter.isAvailable());
            onnx.put("loaded", onnxAdapter.isLoaded());
            onnx.put("info", onnxAdapter.info());
            onnx.put("inferences", onnxAdapter.inferenceCount());
        }
        body.put("onnx", onnx);

        Map<String, Object> qwen = new LinkedHashMap<>();
        if (qwenAdapter != null) {
            qwen.put("summary", qwenAdapter.summary());
        }
        body.put("qwen", qwen);

        return body;
    }
}
