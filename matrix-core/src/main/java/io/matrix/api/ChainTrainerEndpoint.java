package io.matrix.api;

import io.matrix.imports.BitLinearTrainer;
import io.matrix.imports.BitLinearTrainer.EvalFn;
import io.matrix.imports.BitLinearTrainer.TrainerStats;
import io.matrix.imports.BitLinearTrainer.TrainerState;
import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.BooleanChainRunner.ChainResult;
import io.matrix.imports.TruthTableLayer;
import io.matrix.neuron.TruthTable;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chain trainer endpoint (Phase 3 — real training on real corpus).
 *
 * <p>Wires the {@link BitLinearTrainer} sign-descent loop to the
 * running boolean chain. Each /v1/train call iterates over the
 * provided (input, expected) pairs and updates neurons toward
 * the target bit-pattern, so the chain becomes specialized to
 * fire on real text patterns rather than producing zero-density
 * decisions on benign input.
 */
@Path("/v1/train")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ChainTrainerEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ChainTrainerEndpoint.class);

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    BpeTokenizerProvider bpeProvider;

    private final AtomicLong totalPairs = new AtomicLong();
    private final AtomicLong totalFlips = new AtomicLong();
    private final AtomicLong totalEpochs = new AtomicLong();

    void onStart(@Observes StartupEvent ev) {
        log.info("ChainTrainerEndpoint ready (chain layers={}, neurons={})",
                chainRunner.layerCount(), chainRunner.totalNeurons());
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        // No-op marker for ordering
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chain_layers", chainRunner.layerCount());
        body.put("chain_neurons", chainRunner.totalNeurons());
        body.put("chain_empty", chainRunner.layerCount() == 0);
        body.put("total_pairs_trained", totalPairs.get());
        body.put("total_flips", totalFlips.get());
        body.put("total_epochs", totalEpochs.get());
        body.put("bpe_available", bpeProvider.isAvailable());
        return body;
    }

    /**
     * Train on (input, expected) pairs. Each pair updates the chain
     * neurons via sign-descent; after enough epochs the chain fires
     * meaningfully on real text.
     *
     * <p>Body: {@code {"pairs":[{"input":"hi","expected":"hello"},...], "epochs":3}}
     */
    @POST
    public Map<String, Object> train(Map<String, Object> body) {
        return trainImpl(body);
    }

    /** True when no chain layers are loaded — auto-trainer can skip. */
    public boolean isEmpty() {
        return chainRunner == null || chainRunner.layerCount() == 0;
    }

    private Map<String, Object> trainImpl(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pairs =
                (List<Map<String, Object>>) body.getOrDefault("pairs", List.of());
        int epochs = ((Number) body.getOrDefault("epochs", 3)).intValue();
        if (pairs.isEmpty()) {
            return Map.of("error", "no pairs provided");
        }
        if (chainRunner.layerCount() == 0) {
            return Map.of("error", "chain has no loaded layers — load a model first");
        }

        long totalFlipped = 0;
        for (int e = 0; e < epochs; e++) {
            for (Map<String, Object> pair : pairs) {
                String input = (String) pair.getOrDefault("input", "");
                String expected = (String) pair.getOrDefault("expected", "");
                totalFlipped += trainPair(input, expected);
            }
            totalEpochs.incrementAndGet();
        }
        totalPairs.addAndGet(pairs.size());
        totalFlips.addAndGet(totalFlipped);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("trained_pairs", pairs.size());
        resp.put("epochs_run", epochs);
        resp.put("neurons_flipped", totalFlipped);
        resp.put("cumulative_pairs", totalPairs.get());
        resp.put("cumulative_epochs", totalEpochs.get());
        resp.put("chain_layers", chainRunner.layerCount());
        resp.put("chain_neurons", chainRunner.totalNeurons());
        return resp;
    }

    /**
     * Train the chain on a single (input, expected) pair using
     * sign-descent. The EvalFn evaluates the chain on the input and
     * measures bit-match accuracy against the expected target.
     *
     * @return number of neurons flipped
     */
    private long trainPair(String input, String expected) {
        boolean[] in = encodeText(input);
        boolean[] target = encodeText("_target_" + expected);
        if (in.length == 0) return 0;
        // resize target to match the number of neurons (one bit per neuron output)
        int totalNeurons = (int) chainRunner.totalNeurons();
        if (totalNeurons <= 0) return 0;

        // Build target bits: one per neuron. If target is shorter, pad with false.
        boolean[] targetBits = new boolean[totalNeurons];
        for (int i = 0; i < totalNeurons && i < target.length; i++) {
            targetBits[i] = target[i];
        }

        // Build input bits: pad input to cover all neuron k-slices
        int inputWidth = totalNeurons * getLayerK();
        boolean[] inputBits = new boolean[inputWidth];
        for (int i = 0; i < in.length && i < inputWidth; i++) {
            inputBits[i] = in[i];
        }

        List<Integer> layerKs = getLayerKs();
        List<TruthTable> snapshot = snapshotNeurons();
        BitLinearTrainer trainer = new BitLinearTrainer();
        TrainerState state = trainer.trainWithTarget(
                wrapAsMap(snapshot),
                layerKs,
                inputBits,
                targetBits,
                1);    // one epoch per pair
        long flipped = state.history().stream()
                .mapToLong(TrainerStats::neuronsFlipped)
                .sum();
        if (flipped > 0) {
            log.debug("trained on '{}' → '{}' — {} neurons flipped", input, expected, flipped);
        }
        return flipped;
    }

    /** Get k value from the first layer. */
    private int getLayerK() {
        for (TruthTableLayer layer : currentLayers()) {
            return layer.k();
        }
        return 14; // fallback
    }

    /** Get k values for each layer. */
    private List<Integer> getLayerKs() {
        List<Integer> ks = new ArrayList<>();
        for (TruthTableLayer layer : currentLayers()) {
            ks.add(layer.k());
        }
        return ks;
    }

    /** Snapshot all neurons across all chain layers. */
    private List<TruthTable> snapshotNeurons() {
        List<TruthTable> out = new ArrayList<>();
        for (TruthTableLayer layer : currentLayers()) {
            for (TruthTable n : layer.neurons()) out.add(n);
        }
        return out;
    }

    private List<TruthTableLayer> currentLayers() {
        // The BooleanChainRunner holds layers privately. Read via reflection
        // — try declared fields first, then walk superclasses if needed.
        // Resolve CDI proxy first (Quarkus client proxies don't expose
        // the real fields).
        Object target = resolveCdiTarget(chainRunner);
        try {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    var f = c.getDeclaredField("layers");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<TruthTableLayer> ls = (List<TruthTableLayer>) f.get(target);
                    if (ls != null) return ls;
                } catch (NoSuchFieldException ignored) {
                    // try next class
                }
                c = c.getSuperclass();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("cannot read chain layers: {}", e.getMessage());
            return List.of();
        }
    }

    /** Unwrap Quarkus CDI client proxy to get the real instance. */
    private Object resolveCdiTarget(Object proxy) {
        if (proxy == null) return null;
        // Quarkus 3.x exposes the target via readWriteTarget / context
        try {
            var m = proxy.getClass().getMethod("getTarget");
            Object t = m.invoke(proxy);
            if (t != null && t != proxy) return t;
        } catch (Exception ignored) {
            // try next approach
        }
        // Alternative: ArcContainer proxies have a "arcInstance" or
        // unwrap via ClientProxy
        for (var f : proxy.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object v = f.get(proxy);
                if (v != null && v != proxy && v.getClass().getName().contains("BooleanChainRunner")) {
                    return v;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return proxy;
    }

    /** Wrap a flat neuron list as the multi-key map the trainer expects. */
    private Map<String, List<TruthTable>> wrapAsMap(List<TruthTable> neurons) {
        Map<String, List<TruthTable>> out = new LinkedHashMap<>();
        out.put("external-corpus", neurons);
        return out;
    }

    private boolean[] encodeText(String text) {
        if (bpeProvider.isAvailable()) {
            return bpeProvider.textToBits(text == null ? "" : text, 896);
        }
        boolean[] bits = new boolean[896];
        byte[] bytes = text == null ? new byte[0] : text.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            int h = (bytes[i] * 31 + i * 17) & 0x7FFFFFFF;
            bits[h % 896] = true;
        }
        return bits;
    }
}