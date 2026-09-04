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
        // resize target to match chain input width for stable scoring
        if (target.length < in.length) {
            boolean[] padded = new boolean[in.length];
            System.arraycopy(target, 0, padded, 0, target.length);
            target = padded;
        } else if (target.length > in.length) {
            boolean[] truncated = new boolean[in.length];
            System.arraycopy(target, 0, truncated, 0, in.length);
            target = truncated;
        }

        final boolean[] targetFinal = target;
        final boolean[] inFinal = in;
        EvalFn evalFn = new EvalFn() {
            @Override
            public int exampleCount() { return 1; }
            @Override
            public double evaluate(Map<String, List<TruthTable>> neurons) {
                BooleanChainRunner tmp = new BooleanChainRunner(
                        "training-tmp", "in-memory",
                        snapshotLayers(neurons.get("external-corpus")));
                boolean[] out = tmp.evaluate(inFinal);
                int n = Math.min(out.length, targetFinal.length);
                if (n == 0) return 0.0;

                // Signal: correctly_fired − wrongly_fired, normalized by max
                // possible gain. Range: -1 to +1. The all-zeros output
                // against an all-zeros target now scores 0 (not 1), so
                // sign-descent has real signal to work with.
                int correctlyFired = 0;   // target=1 AND output=1
                int wronglyFired   = 0;   // target=0 AND output=1
                int targetOnCount   = 0;   // target bits that are 1
                for (int i = 0; i < n; i++) {
                    if (targetFinal[i]) targetOnCount++;
                    if (out[i] && targetFinal[i]) correctlyFired++;
                    else if (out[i] && !targetFinal[i]) wronglyFired++;
                }
                // Best case: all "on" bits fire, no "off" bits fire
                // score = (correctlyFired − wronglyFired) / max(1, targetOnCount)
                // normalized so range is roughly [−1, +1]
                int denom = Math.max(1, targetOnCount);
                return ((double) (correctlyFired - wronglyFired)) / denom;
            }
        };

        List<TruthTable> snapshot = snapshotNeurons();
        BitLinearTrainer trainer = new BitLinearTrainer();
        TrainerState state = trainer.train(
                wrapAsMap(snapshot),
                evalFn,
                1,    // one epoch per pair (cheap; users can pass epochs in body)
                0.0,  // no early-stopping tolerance
                null);
        long flipped = state.history().stream()
                .mapToLong(TrainerStats::neuronsFlipped)
                .sum();
        if (flipped > 0) {
            log.debug("trained on '{}' → '{}' — {} neurons flipped", input, expected, flipped);
        }
        return flipped;
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
        try {
            Class<?> c = chainRunner.getClass();
            while (c != null && c != Object.class) {
                try {
                    var f = c.getDeclaredField("layers");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<TruthTableLayer> ls = (List<TruthTableLayer>) f.get(chainRunner);
                    if (ls != null) return ls;
                } catch (NoSuchFieldException ignored) {
                    // try next class
                }
                c = c.getSuperclass();
            }
            log.warn("layers field not found in chainRunner class hierarchy");
            return List.of();
        } catch (Exception e) {
            log.warn("cannot read chain layers: {}", e.getMessage());
            return List.of();
        }
    }

    /** Wrap a flat neuron list as the multi-key map the trainer expects. */
    private Map<String, List<TruthTable>> wrapAsMap(List<TruthTable> neurons) {
        Map<String, List<TruthTable>> out = new LinkedHashMap<>();
        out.put("external-corpus", neurons);
        return out;
    }

    /** Wrap a flat neuron list as a single layer for the temp runner. */
    private List<TruthTableLayer> snapshotLayers(List<TruthTable> neurons) {
        // snapshot neurons are passed by the trainer; build a single layer
        if (neurons.isEmpty()) return List.of();
        int k = 14;  // standard cell size for Qwen projection
        return List.of(new TruthTableLayer(neurons, k));
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