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

    @Inject
    QaCorpusIndex qaIndex;

    private final AtomicLong totalPairs = new AtomicLong();
    private final AtomicLong totalFlips = new AtomicLong();
    private final AtomicLong totalEpochs = new AtomicLong();
    private final List<String> trainingErrors = new ArrayList<>();

    void onStart(@Observes StartupEvent ev) {
        log.info("ChainTrainerEndpoint ready (chain layers={}, neurons={}, qa-corpus={})",
                chainRunner.layerCount(), chainRunner.totalNeurons(),
                qaIndex != null ? qaIndex.size() : 0);
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
        boolean useCorpus = Boolean.TRUE.equals(body.get("use_corpus"))
                || Boolean.TRUE.equals(body.get("useCorpus"));
        int corpusLimit = ((Number) body.getOrDefault("corpus_limit", 100)).intValue();
        if (pairs.isEmpty() && useCorpus && qaIndex != null && qaIndex.size() > 0) {
            // Build pairs from the QA corpus automatically.
            var hits = qaIndex.state().entries;
            int limit = Math.min(corpusLimit, hits.size());
            pairs = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                var e = hits.get(i);
                pairs.add(Map.of("input", e.question(), "expected", e.answer()));
            }
            log.info("Train: pulling {} pairs from QA corpus", pairs.size());
        }
        if (pairs.isEmpty()) {
            return Map.of("error", "no pairs provided and qa-corpus is empty");
        }
        if (chainRunner.layerCount() == 0) {
            return Map.of("error", "chain has no loaded layers — load a model first");
        }
        if (currentLayers().isEmpty()) {
            // CDI reflection couldn't resolve the runner's layers field — return useful info.
            return Map.of(
                    "error", "cannot read chain layers (CDI proxy unwrap failed)",
                    "chain_layers", chainRunner.layerCount(),
                    "chain_neurons", chainRunner.totalNeurons(),
                    "hint", "check that BooleanChainRunner.layers field is accessible"
            );
        }

        long totalFlipped = 0;
        int successPairs = 0;
        int erroredPairs = 0;
        for (int e = 0; e < epochs; e++) {
            for (Map<String, Object> pair : pairs) {
                String input = (String) pair.getOrDefault("input", "");
                String expected = (String) pair.getOrDefault("expected", "");
                try {
                    long flipped = trainPair(input, expected);
                    if (flipped > 0) successPairs++;
                    totalFlipped += flipped;
                } catch (Throwable t) {
                    erroredPairs++;
                    if (trainingErrors.size() < 5) {
                        trainingErrors.add("pair " + truncate(input, 30) + " → " + t.getMessage());
                    }
                }
            }
            totalEpochs.incrementAndGet();
        }
        totalPairs.addAndGet(pairs.size());
        totalFlips.addAndGet(totalFlipped);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("trained_pairs", pairs.size());
        resp.put("epochs_run", epochs);
        resp.put("neurons_flipped", totalFlipped);
        resp.put("successful_pairs", successPairs);
        resp.put("errored_pairs", erroredPairs);
        resp.put("first_errors", trainingErrors);
        resp.put("cumulative_pairs", totalPairs.get());
        resp.put("cumulative_epochs", totalEpochs.get());
        resp.put("cumulative_flips", totalFlips.get());
        resp.put("chain_layers", chainRunner.layerCount());
        resp.put("chain_neurons", chainRunner.totalNeurons());
        return resp;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * Train the chain on a single (input, expected) pair using
     * sign-descent. The EvalFn evaluates the chain on the input and
     * measures bit-match accuracy against the expected target.
     *
     * <p>This method actually mutates the chain's neurons via
     * {@link TruthTableLayer#replaceNeuron(int, TruthTable)} so
     * subsequent /v1/generate and /v1/chat calls see the trained
     * weights. Training is persistent within the JVM until restart.
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
        Map<String, List<TruthTable>> before = wrapAsMap(snapshot);
        BitLinearTrainer trainer = new BitLinearTrainer();
        TrainerState state = trainer.trainWithTarget(
                before,
                layerKs,
                inputBits,
                targetBits,
                1);    // one epoch per pair
        long flipped = state.history().stream()
                .mapToLong(TrainerStats::neuronsFlipped)
                .sum();

        // WRITE-BACK: copy the trained neurons back into the actual chain layers
        // so future evaluations see the updated weights.
        if (flipped > 0) {
            try {
                Map<String, List<TruthTable>> after = state.trainedNeurons();
                // Traverse layers and write each trained neuron.
                // Directly use the trained map's neurons (not findTrained) so we
                // don't accidentally write back the ORIGINAL neuron instead of
                // the trained one.
                int neuronIdx = 0;
                int written = 0;
                int actuallyChanged = 0;
                for (TruthTableLayer layer : currentLayers()) {
                    int n = layer.neuronCount();
                    for (int i = 0; i < n && neuronIdx < snapshot.size(); i++) {
                        TruthTable trained = lookupTrained(after, neuronIdx);
                        if (trained == null) continue;
                        TruthTable prev = layer.replaceNeuron(i, trained);
                        if (prev != trained) written++;
                        // Verify the trained neuron is actually different
                        if (prev.table().cardinality() != trained.table().cardinality()
                                || !prev.table().equals(trained.table())) {
                            actuallyChanged++;
                        }
                        neuronIdx++;
                    }
                }
                log.info("trained on pair → {} neurons flipped, {} written, {} actually changed",
                        flipped, written, actuallyChanged);
            } catch (Throwable t) {
                log.warn("write-back failed: {}", t.getMessage());
            }
        }

        return flipped;
    }

    /** Look up a single trained neuron by index in the trained map. */
    private TruthTable lookupTrained(Map<String, List<TruthTable>> trained, int idx) {
        if (trained == null) return null;
        int total = 0;
        for (List<TruthTable> list : trained.values()) {
            if (idx < total + list.size()) {
                return list.get(idx - total);
            }
            total += list.size();
        }
        return null;
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
        // BooleanChainRunner exposes `layers()` as a public method.
        // No reflection needed — the accessor was added in commit 66657a94.
        List<TruthTableLayer> ls = chainRunner.layers();
        return ls == null ? List.of() : ls;
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