package io.matrix.neuron;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 442 — HDC × BitLinear × Hebbian brain integration (DESIGN-54 §5).
 *
 * <p>Minimal integration of the W31 brain primitives into a single class
 * suitable for Capability Level 1 (Pavlov) demos:
 *
 * <ul>
 *   <li>{@link #learn(features, label)} — encode the (features, label)
 *       pair as a HDC record and store it. Optionally strengthen via
 *       Hebbian update.</li>
 *   <li>{@link #forward(features)} — encode features and find the nearest
 *       stored record by Hamming distance. Returns the best-matching label
 *       and confidence.</li>
 *   <li>{@link #forget(label)} — remove a learned association.</li>
 *   <li>{@link #size()} — number of stored associations.</li>
 * </ul>
 *
 * <h2>Architecture (DESIGN-54 §1)</h2>
 * <pre>
 *   features → [BitLinear encoding?] → HDC code → record binding → codebook
 *                                                   ↓
 *                                          Hebbian updater on read
 * </pre>
 *
 * <p>For now, features are assumed to be already in {@code float[]} form
 * and are converted to bipolar codes via a simple sign-threshold quantizer.
 * Future versions will route features through {@link BitLinear} first.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All stored state is deterministic given inputs.
 */
public final class HdcBrain {

    /** Per-record container: stores the HDC code, the label, and its Hebbian state. */
    public static final class Memory {
        public final String label;
        public final long[] code;             // HDC record code
        public final HebbianUpdater.State hebbian; // per-record learning state

        public Memory(String label, long[] code, HebbianUpdater.State hebbian) {
            this.label = label;
            this.code = code;
            this.hebbian = hebbian;
        }
    }

    /** Result of {@link #forward}: best match plus similarity. */
    public static final class Recall {
        public final String label;
        public final double similarity;
        public final int distance;

        public Recall(String label, double similarity, int distance) {
            this.label = label;
            this.similarity = similarity;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return "Recall{label=" + label + ", sim=" + String.format("%.4f", similarity)
                    + ", dist=" + distance + "}";
        }
    }

    private final Random rng;
    private final Map<String, Long> labelSeed; // seed for deterministic label code per label
    private final LinkedHashMap<String, Memory> memories; // LRU access order
    private final int maxCapacity;

    /**
     * Create a brain with bounded capacity. Capacity must be ≥ 1.
     */
    public HdcBrain(int maxCapacity, Random rng) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("capacity must be ≥ 1");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.maxCapacity = maxCapacity;
        this.rng = rng;
        this.memories = new LinkedHashMap<>(16, 0.75f, true);
        this.labelSeed = new LinkedHashMap<>();
    }

    /**
     * Encode a float feature vector as a bipolar HDC code via sign-threshold
     * quantization: bit {@code i} is 1 if {@code features[i] > 0}, else 0.
     */
    public long[] encodeFeatures(float[] features) {
        if (features == null || features.length == 0) {
            throw new IllegalArgumentException("empty features");
        }
        if (features.length != HdcEncoding.DIM) {
            throw new IllegalArgumentException(
                    "features length must be DIM=" + HdcEncoding.DIM
                            + " (got " + features.length + ")");
        }
        long[] code = new long[HdcEncoding.WORDS];
        for (int w = 0; w < HdcEncoding.WORDS; w++) {
            long word = 0L;
            for (int b = 0; b < 64; b++) {
                int pos = (w << 6) + b;
                if (features[pos] > 0.0f) {
                    word |= (1L << b);
                }
            }
            code[w] = word;
        }
        return code;
    }

    /**
     * Get or create the deterministic HDC code for a label.
     */
    public long[] codeForLabel(String label) {
        if (label == null) throw new IllegalArgumentException("null label");
        Long existing = labelSeed.get(label);
        long seed;
        if (existing != null) {
            seed = existing;
        } else {
            seed = rng.nextLong();
            labelSeed.put(label, seed);
        }
        Random labelRng = new Random(seed);
        return HdcEncoding.random(labelRng);
    }

    /**
     * Bind a feature code to a label code into a single record vector.
     * Uses position-based binding (feature at role 0, label at role 1) then bundles.
     */
    public long[] bindRecord(long[] featureCode, long[] labelCode) {
        long[] featPos = HdcBinding.sequence(featureCode, 0);
        long[] labPos = HdcBinding.sequence(labelCode, 1);
        return HdcBinding.bind(featPos, labPos);
    }

    /**
     * Learn a (features, label) association. Stores or strengthens the
     * feature code in the codebook under the given label.
     *
     * <p>The stored "code" is the feature code itself (not a bound record),
     * so forward(features) can directly compare via Hamming distance.
     * If a memory with this label already exists, the new feature code is
     * bundled into the existing one (consolidation), and a Hebbian update
     * is applied to strengthen the (feature, label) association.
     *
     * @return similarity of the new code to the existing record after learn
     */
    public double learn(float[] features, String label) {
        return learn(features, label, 0.5f, 0.01f);
    }

    /**
     * Learn with explicit Hebbian hyperparameters.
     */
    public double learn(float[] features, String label, float eta, float lambda) {
        if (label == null) throw new IllegalArgumentException("null label");
        long[] featureCode = encodeFeatures(features);
        long[] labelCode = codeForLabel(label);

        Memory existing = memories.get(label);
        double sim;
        if (existing == null) {
            // New memory — store feature code directly
            Memory m = new Memory(label, featureCode, HebbianUpdater.empty());
            ensureCapacity();
            memories.put(label, m);
            sim = 1.0; // exact match with itself
        } else {
            // Strengthen: bundle existing with new feature code
            long[] bundled = HdcEncoding.bundle(existing.code, featureCode);
            // Hebbian update on the existing state with (feature, label) pair
            HebbianUpdater.update(existing.hebbian, featureCode, labelCode, eta, lambda);
            Memory updated = new Memory(label, bundled, existing.hebbian);
            memories.put(label, updated);
            sim = HdcEncoding.similarity(existing.code, featureCode);
        }
        return sim;
    }

    /**
     * Forward pass: encode features, find nearest feature code in codebook,
     * return best-matching label.
     */
    public Recall forward(float[] features) {
        if (memories.isEmpty()) return null;
        long[] featureCode = encodeFeatures(features);

        String bestLabel = null;
        long[] bestCode = null;
        int bestDist = Integer.MAX_VALUE;
        for (Memory m : memories.values()) {
            int d = HdcEncoding.hamming(featureCode, m.code);
            if (d < bestDist) {
                bestDist = d;
                bestLabel = m.label;
                bestCode = m.code;
            }
        }
        double sim = 1.0 - 2.0 * bestDist / (double) HdcEncoding.DIM;
        return new Recall(bestLabel, sim, bestDist);
    }

    /**
     * Forward pass with top-K results.
     */
    public List<Recall> forwardTopK(float[] features, int k) {
        if (memories.isEmpty() || k <= 0) return java.util.Collections.emptyList();
        long[] featureCode = encodeFeatures(features);
        java.util.List<Recall> all = new java.util.ArrayList<>(memories.size());
        for (Memory m : memories.values()) {
            int d = HdcEncoding.hamming(featureCode, m.code);
            double sim = 1.0 - 2.0 * d / (double) HdcEncoding.DIM;
            all.add(new Recall(m.label, sim, d));
        }
        all.sort((a, b) -> Integer.compare(a.distance, b.distance));
        if (all.size() > k) return all.subList(0, k);
        return all;
    }

    /**
     * Remove a memory by label. Returns true if removed.
     */
    public boolean forget(String label) {
        return memories.remove(label) != null;
    }

    /**
     * Number of stored memories.
     */
    public int size() {
        return memories.size();
    }

    /**
     * Max capacity.
     */
    public int capacity() {
        return maxCapacity;
    }

    /**
     * All labels in LRU order.
     */
    public java.util.Set<String> labels() {
        return new java.util.LinkedHashSet<>(memories.keySet());
    }

    /**
     * Direct access to memory record for inspection.
     */
    public Memory get(String label) {
        return memories.get(label);
    }

    private void ensureCapacity() {
        while (memories.size() >= maxCapacity) {
            String eldest = memories.keySet().iterator().next();
            memories.remove(eldest);
        }
    }
}
