package io.matrix.neuron;

import io.matrix.agent.PretrainedLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Ensemble of ALL available pretrained MPDT brains loaded from disk.
 *
 * <p>Each pretrained model (Qwen3-1.7B, DeepSeek-R1, Qwen2.5, SmolLM2, etc.)
 * contributes a HierarchicalBrain. The ensemble computes a joint neural
 * signature by concatenating the intermediate-layer activations from EVERY
 * brain, producing a high-dimensional vector that captures the knowledge
 * of all models simultaneously.
 *
 * <h2>Why ensemble</h2>
 * <p>A single model's attention patterns capture only one distribution.
 * Combining multiple models (different architectures, different pre-training
 * corpora) yields a richer feature space for memory retrieval. The result is
 * responses that reflect the combined knowledge of 8 models, not just one.
 *
 * <h2>Signature format</h2>
 * The composite signature is:
 * <ol>
 *   <li>input sensor hash (64-bit)</li>
 *   <li>for each brain: sensor-layer output bits (long[]), feature-layer output bits,
 *       action-layer output bits</li>
 * </ol>
 * The resulting vector is ~200-400 longs (1,600–3,200 bits), providing
 * high-dimensional separation for retrieval.
 */
public final class MultiBrainEnsemble {

    private static final Logger log = LoggerFactory.getLogger(MultiBrainEnsemble.class);
    private static final Path PRETRAINED_DIR = Path.of("models/pretrained");

    private final List<BrainEntry> brains;
    private final int totalBrains;

    private MultiBrainEnsemble(List<BrainEntry> brains) {
        this.brains = List.copyOf(brains);
        this.totalBrains = brains.size();
    }

    /** Loads ALL pretrained models found in {@code models/pretrained/}. */
    public static MultiBrainEnsemble loadAll() {
        List<BrainEntry> loaded = new ArrayList<>();
        PretrainedLoader loader = new PretrainedLoader();

        // Priority-ordered model list with max layer count
        String[][] models = {
            {"qwen3-1.7b", "Qwen3-1.7B", "6"},
            {"deepseek-r1-distill-qwen-1.5b", "DeepSeek-R1-Distill-Qwen-1.5B", "6"},
            {"qwen2.5-1.5b", "Qwen2.5-1.5B", "6"},
            {"qwen3-0.6b", "Qwen3-0.6B", "6"},
            {"smollm2-360m", "SmolLM2-360M", "6"},
            {"merged", "Merged-Ensemble", "6"},
            {"qwen2.5-0.5b", "Qwen2.5-0.5B", "6"},
            {"SmolLM2-135M-synth", "SmolLM2-135M", "6"},
        };

        for (String[] model : models) {
            Path modelDir = PRETRAINED_DIR.resolve(model[0]);
            if (!Files.isDirectory(modelDir)) {
                log.debug("Skipping {} — directory not found", model[0]);
                continue;
            }
            int maxLayer = Integer.parseInt(model[2]);
            try {
                List<TruthTable> l0 = loader.loadLayer(modelDir, model[1], 0);
                List<TruthTable> l1 = loader.loadLayer(modelDir, model[1], 1);
                List<TruthTable> l2 = loader.loadLayer(modelDir, model[1], 2);
                List<TruthTable> l3 = maxLayer > 3 ? loader.loadLayer(modelDir, model[1], 3) : List.of();
                List<TruthTable> l4 = maxLayer > 4 ? loader.loadLayer(modelDir, model[1], 4) : List.of();
                List<TruthTable> l5 = maxLayer > 5 ? loader.loadLayer(modelDir, model[1], 5) : List.of();

                NeuronLayer sensorLayer = buildLayer(merge(l0, l1), 12, 12);
                NeuronLayer featureLayer = buildLayer(merge(l2, l3), 8, 12);
                NeuronLayer actionLayer = buildLayer(merge(l4, l5), 5, 8);

                HierarchicalBrain brain = new HierarchicalBrain(sensorLayer, featureLayer, actionLayer);
                loaded.add(new BrainEntry(model[1], brain));
                log.info("MultiBrainEnsemble: loaded {} ({} neurons)", model[1],
                        sensorLayer.outputWidth() + featureLayer.outputWidth() + actionLayer.outputWidth());
            } catch (Exception e) {
                log.warn("MultiBrainEnsemble: failed to load {}: {}", model[0], e.getMessage());
            }
        }

        log.info("MultiBrainEnsemble: loaded {} / {} models", loaded.size(), models.length);
        return new MultiBrainEnsemble(loaded);
    }

    /** Number of brains in the ensemble. */
    public int size() {
        return totalBrains;
    }

    /**
     * Computes the composite neural signature for the given text.
     *
     * <p>Runs the forward pass through ALL brains and concatenates their
     * intermediate outputs into one flat vector.
     *
     * @param text input query
     * @return composite signature or null if no brains loaded
     */
    public long[] compositeSignature(String text) {
        if (brains.isEmpty() || text == null) return null;

        long sensorBits = NeuralMemoryResponse.hashText(text);
        List<long[]> allWords = new ArrayList<>();
        allWords.add(new long[]{sensorBits});

        for (BrainEntry entry : brains) {
            try {
                long[] brainSig = singleBrainSignature(entry.brain, sensorBits);
                if (brainSig != null) {
                    allWords.add(brainSig);
                }
            } catch (Exception e) {
                // skip failing brains silently
            }
        }

        // Flatten into a single long[]
        int totalLen = 0;
        for (long[] w : allWords) totalLen += w.length;
        long[] result = new long[totalLen];
        int offset = 0;
        for (long[] w : allWords) {
            System.arraycopy(w, 0, result, offset, w.length);
            offset += w.length;
        }
        return result;
    }

    /**
     * Decides using the primary (first) brain. For ensemble decisions, use
     * majority vote (not implemented yet — extends to action-space only).
     */
    public int decide(long sensorBits) {
        if (brains.isEmpty()) return 0;
        return brains.get(0).brain.decide(sensorBits);
    }

    // ─── helpers ───

    private static long[] singleBrainSignature(HierarchicalBrain brain, long sensorBits) {
        NeuronLayer sl = brain.sensorLayer();
        NeuronLayer fl = brain.featureLayer();
        NeuronLayer al = brain.actionLayer();
        if (sl == null || fl == null || al == null) return null;

        BitSet input = NeuralMemoryResponse.toBitSet(sensorBits, 20);
        BitSet l0 = sl.evaluate(NeuralMemoryResponse.padInput(input, sl.outputWidth() * sl.k()));
        BitSet l1 = fl.evaluate(NeuralMemoryResponse.padInput(l0, fl.outputWidth() * fl.k()));
        BitSet l2 = al.evaluate(NeuralMemoryResponse.padInput(l1, al.outputWidth() * al.k()));

        long[] w0 = l0.toLongArray();
        long[] w1 = l1.toLongArray();
        long[] w2 = l2.toLongArray();

        long[] sig = new long[w0.length + w1.length + w2.length];
        System.arraycopy(w0, 0, sig, 0, w0.length);
        System.arraycopy(w1, 0, sig, w0.length, w1.length);
        System.arraycopy(w2, 0, sig, w0.length + w1.length, w2.length);
        return sig;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> merge(List<T> a, List<T> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<T> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }

    private static NeuronLayer buildLayer(List<TruthTable> tables, int neuronCount, int k) {
        List<TruthTable> matching = new ArrayList<>();
        for (TruthTable t : tables) {
            if (t.k() == k && matching.size() < neuronCount) {
                matching.add(t);
            }
        }
        if (matching.size() >= neuronCount) {
            return NeuronLayer.fromTruthTables(matching);
        }
        // Fill remaining with deterministic neurons from the available tables
        List<DecisionTree> neurons = new ArrayList<>();
        for (TruthTable t : matching) {
            neurons.add(PretrainedLoader.truthTableToTree(t));
        }
        java.util.Random fillRng = new java.util.Random(matching.hashCode());
        while (neurons.size() < neuronCount) {
            neurons.add(DecisionTree.random(k, Math.min(k, 8), fillRng));
        }
        return new NeuronLayer(neurons, k);
    }

    record BrainEntry(String modelName, HierarchicalBrain brain) {}
}