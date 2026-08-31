package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.BooleanChainProducer;
import io.matrix.model.ModelRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Interactive sandbox (Wave M): lets the user chat with MATRIX
 * through the boolean chain, inspect chain state, and ask for
 * explanations of decisions. This is the human-facing API for
 * testing the system.
 */
@Path("/v1/sandbox")
@Produces(MediaType.APPLICATION_JSON)
public class SandboxResource {

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    ModelRegistry modelRegistry;

    @Inject
    ExpandedTextToBitsService textEncoder;

    @Inject
    BpeTokenizerProvider bpeProvider;

    private final AtomicLong conversationCount = new AtomicLong();
    private final List<Map<String, Object>> recentConversations =
            java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile Map<String, Object> lastDecision = Map.of();

    /** Boot trace: confirms the chain loaded on startup. */
    void onStart(@Observes StartupEvent ev) {
        // log via System.out for ops visibility
        System.out.printf("[sandbox] chain ready: model=%s layers=%d neurons=%d%n",
                chainRunner.modelName(), chainRunner.layerCount(),
                chainRunner.totalNeurons());
    }

    /**
     * POST /v1/sandbox/chat — chat with MATRIX through the boolean chain.
     * Body: {"input": "...", "model": "M.A.T.R.I.X."}
     */
    @POST
    @Path("/chat")
    public Map<String, Object> chat(Map<String, Object> body) {
        conversationCount.incrementAndGet();
        String input = (String) body.getOrDefault("input", "");
        // prefer real BPE tokenization when the Qwen tokenizer files are
        // available; fall back to the position-aware hash encoder
        boolean[] bits;
        String encodingMethod;
        if (bpeProvider.isAvailable()) {
            bits = bpeProvider.textToBits(input, 896);
            encodingMethod = "bpe-qwen";
        } else {
            bits = textEncoder.textToBits(input);
            encodingMethod = "hash-fallback";
        }
        boolean[] decision;
        long t0 = System.nanoTime();
        if (chainRunner.layerCount() > 0) {
            decision = chainRunner.evaluate(bits);
        } else {
            // chain not loaded — fall back to deterministic bits
            decision = bits;
        }
        long forwardMs = (System.nanoTime() - t0) / 1_000_000;

        // produce a response text from the decision bits (templated)
        String response = bitsToText(decision);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("input", input);
        record.put("response", response);
        record.put("forward_ms", forwardMs);
        record.put("decision_bits_set", countSet(decision));
        record.put("decision_bits_total", decision.length);
        record.put("encoding", encodingMethod);
        record.put("chain_used", chainRunner.layerCount() > 0);
        record.put("conversation_id", conversationCount.get());
        recentConversations.add(record);
        if (recentConversations.size() > 100) recentConversations.remove(0);
        lastDecision = record;
        return record;
    }

    /**
     * GET /v1/sandbox/explain — explain the last decision (which
     * neurons fired, which layer produced the decision, etc.).
     */
    @GET
    @Path("/explain")
    public Map<String, Object> explain() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("last_decision", lastDecision);
        body.put("chain_layers", chainRunner.layerCount());
        body.put("chain_neurons", chainRunner.totalNeurons());
        body.put("chain_evals", chainRunner.totalEvalCount());
        body.put("avg_eval_micros", chainRunner.avgEvalMicros());
        // heuristic explanation: count bits set vs total bits
        if (lastDecision.containsKey("decision_bits_set")
                && lastDecision.containsKey("decision_bits_total")) {
            int set = (int) lastDecision.get("decision_bits_set");
            int total = (int) lastDecision.get("decision_bits_total");
            double density = total > 0 ? (double) set / total : 0.0;
            body.put("decision_density", density);
            String interpretation = density > 0.5
                    ? "decision is dense — chain strongly activated"
                    : density > 0.1
                    ? "decision is moderate — chain partially activated"
                    : "decision is sparse — chain weakly activated";
            body.put("interpretation", interpretation);
        }
        return body;
    }

    /**
     * GET /v1/sandbox/inspect — full chain status and recent conversations.
     */
    @GET
    @Path("/inspect")
    public Map<String, Object> inspect() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chain_model", chainRunner.modelName());
        body.put("chain_source", chainRunner.sourcePath());
        body.put("chain_layers", chainRunner.layerCount());
        body.put("chain_neurons", chainRunner.totalNeurons());
        body.put("chain_evals", chainRunner.totalEvalCount());
        body.put("chain_avg_eval_micros", chainRunner.avgEvalMicros());
        body.put("loaded_models", modelRegistry.names());
        body.put("recent_conversations", recentConversations);
        body.put("total_conversations", conversationCount.get());
        return body;
    }

    /**
     * GET /v1/sandbox/topology — visualize the chain as a flat list
     * of layer summaries (widths, neuron counts). Useful for plotting.
     */
    @GET
    @Path("/topology")
    public Map<String, Object> topology() {
        List<Map<String, Object>> layers = new ArrayList<>();
        // best-effort: we don't have per-layer introspection on the
        // BooleanChainRunner; report aggregate stats instead.
        for (int i = 0; i < chainRunner.layerCount(); i++) {
            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("index", i);
            layer.put("kind", "TruthTableLayer");
            layers.add(layer);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("layers", layers);
        body.put("total", layers.size());
        return body;
    }

    private static boolean[] textToBits(String text, int width) {
        boolean[] bits = new boolean[width];
        if (text == null || text.isEmpty()) return bits;
        byte[] bytes = text.getBytes();
        for (int i = 0; i < width; i++) {
            int b = bytes[i % bytes.length];
            bits[i] = ((b >>> ((i % 8))) & 1) == 1;
        }
        return bits;
    }

    private static String bitsToText(boolean[] bits) {
        // deterministic mapping: produce a sentence-like output from
        // bit density, even when the chain output is sparse (zeros)
        if (bits == null || bits.length == 0) return "(empty)";
        int total = bits.length;
        int set = 0;
        int firstSet = -1;
        int lastSet = -1;
        for (int i = 0; i < total; i++) {
            if (bits[i]) {
                set++;
                if (firstSet < 0) firstSet = i;
                lastSet = i;
            }
        }
        if (set == 0) {
            return "[MATRIX thinks...] " +
                    "(zero-density decision across " + total + " bits)";
        }
        // density + range + count → templated response
        double density = (double) set / total;
        String range = (firstSet == lastSet)
                ? "at bit " + firstSet
                : "spanning bits " + firstSet + ".." + lastSet;
        return String.format(
                "[MATRIX answered] %d bits set out of %d (%.1f%% density) %s",
                set, total, density * 100, range);
    }

    private static int countSet(boolean[] bits) {
        int c = 0;
        for (boolean b : bits) if (b) c++;
        return c;
    }
}