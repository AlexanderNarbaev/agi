package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RUN 451 — MPDT × HDC bridge (DESIGN-54 §9, integration primitive).
 *
 * <p>Combines MATRIX's discrete MPDT (Multiple-Predicate Decision Table)
 * brain layer with HDC associative memory, enabling:
 * <ul>
 *   <li>Discrete MPDT decisions → HDC codebook lookup for memory augmentation</li>
 *   <li>HDC episodic memory → MPDT feature extraction for fast lookup</li>
 *   <li>Round-trip: MPDT decides action, HDC remembers context, MPDT retrieves
 *       similar context on similar sensor input</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 *   sensors (long)
 *      │
 *      ▼
 *   MPDT (HierarchicalBrain.decide) → action (int)
 *      │
 *      ▼
 *   HdcBrain.learn(sensors, label=action)  ←  episodic memory
 *      │
 *      ▼
 *   on next similar sensor: HdcBrain.forward(sensors') → action'
 * </pre>
 *
 * <h2>Use case</h2>
 * <p>Edge-AI devices with limited CPU: use MPDT for fast discrete decisions
 * but augment with HDC memory to learn from experience. The hybrid
 * achieves both fast inference (MPDT) and memory-augmented behavior (HDC).
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class MpdtHdcBridge {

    private final HierarchicalBrain mpdt;
    private final HdcBrain hdc;
    private final Random rng;

    /**
     * Create a new MPDT × HDC bridge.
     */
    public MpdtHdcBridge(HierarchicalBrain mpdt, HdcBrain hdc, Random rng) {
        if (mpdt == null) throw new IllegalArgumentException("null mpdt");
        if (hdc == null) throw new IllegalArgumentException("null hdc");
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.mpdt = mpdt;
        this.hdc = hdc;
        this.rng = rng;
    }

    /**
     * Hybrid decide: use MPDT for fast action, then store (sensor, action)
     * in HDC memory. Returns the MPDT action.
     */
    public HybridDecision decideAndRemember(long sensors) {
        int action = mpdt.decide(sensors);
        // Encode sensors as 1024-bit code (sign-threshold)
        float[] features = encodeSensors(sensors);
        hdc.learn(features, actionLabel(action), 0.5f, 0.01f);
        return new HybridDecision(sensors, action, null);
    }

    /**
     * Hybrid decide with memory retrieval: first query HDC for similar
     * past sensor→action pair; if similarity > threshold, return that
     * action; otherwise fall back to MPDT.
     */
    public HybridDecision decideWithMemory(long sensors, double threshold) {
        // Try HDC retrieval first
        float[] features = encodeSensors(sensors);
        HdcBrain.Recall hit = hdc.forward(features);
        if (hit != null && hit.similarity > threshold) {
            // Extract action from label
            int action = parseActionLabel(hit.label);
            if (action >= 0) {
                return new HybridDecision(sensors, action, hit);
            }
        }
        // Fall back to MPDT
        return decideAndRemember(sensors);
    }

    /**
     * Encode a long sensor value as 1024-element float feature vector
     * suitable for {@link HdcBrain#encodeFeatures}.
     */
    public float[] encodeSensors(long sensors) {
        float[] features = new float[HdcEncoding.DIM];
        // Spread sensor bits across the feature vector
        for (int i = 0; i < 64; i++) {
            float value = ((sensors >>> i) & 1L) != 0L ? 1.0f : -1.0f;
            // Each sensor bit drives 16 features
            for (int j = 0; j < 16; j++) {
                features[i * 16 + j] = value;
            }
        }
        return features;
    }

    /**
     * Convert MPDT action int to HDC label string.
     */
    private static String actionLabel(int action) {
        return "act-" + action;
    }

    /**
     * Parse HDC label back to MPDT action int.
     */
    private static int parseActionLabel(String label) {
        if (label == null || !label.startsWith("act-")) return -1;
        try {
            return Integer.parseInt(label.substring(4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Hybrid decision result: action + optional HDC memory hit.
     */
    public static final class HybridDecision {
        public final long sensors;
        public final int action;
        /** HDC memory hit if retrieved from memory, null if MPDT only. */
        public final HdcBrain.Recall memoryHit;

        public HybridDecision(long sensors, int action, HdcBrain.Recall memoryHit) {
            this.sensors = sensors;
            this.action = action;
            this.memoryHit = memoryHit;
        }

        public boolean fromMemory() {
            return memoryHit != null;
        }

        @Override
        public String toString() {
            return "HybridDecision{action=" + action
                    + (memoryHit != null ? ", fromMemory" : ", fromMpdt") + "}";
        }
    }

    /**
     * Run a benchmark: train for N rounds then test MPDT vs hybrid on
     * a sequence of sensor inputs.
     */
    public BridgeBenchmarkResult benchmark(int trainRounds, int testRounds, double threshold) {
        if (trainRounds < 0) throw new IllegalArgumentException("trainRounds must be ≥ 0");
        if (testRounds < 1) throw new IllegalArgumentException("testRounds must be ≥ 1");

        // Train: store (sensor, action) pairs
        for (int t = 0; t < trainRounds; t++) {
            long sensors = rng.nextLong();
            decideAndRemember(sensors);
        }

        // Test: compare MPDT vs hybrid
        int mpdtCorrect = 0;
        int hybridCorrect = 0;
        int hybridFromMemory = 0;
        for (int t = 0; t < testRounds; t++) {
            long sensors = rng.nextLong();
            int expected = mpdt.decide(sensors);
            int mpdtAction = expected; // MPDT always returns the same action for same input
            HybridDecision hybrid = decideWithMemory(sensors, threshold);
            if (mpdtAction == expected) mpdtCorrect++;
            if (hybrid.action == expected) hybridCorrect++;
            if (hybrid.fromMemory()) hybridFromMemory++;
        }

        return new BridgeBenchmarkResult(
                mpdtCorrect, hybridCorrect, testRounds, hybridFromMemory);
    }

    /**
     * Benchmark result.
     */
    public static final class BridgeBenchmarkResult {
        public final int mpdtCorrect;
        public final int hybridCorrect;
        public final int total;
        public final int hybridFromMemoryCount;

        public BridgeBenchmarkResult(int mpdtCorrect, int hybridCorrect,
                                      int total, int hybridFromMemoryCount) {
            this.mpdtCorrect = mpdtCorrect;
            this.hybridCorrect = hybridCorrect;
            this.total = total;
            this.hybridFromMemoryCount = hybridFromMemoryCount;
        }

        public double mpdtAccuracy() {
            return total > 0 ? (double) mpdtCorrect / total : 0.0;
        }

        public double hybridAccuracy() {
            return total > 0 ? (double) hybridCorrect / total : 0.0;
        }

        public double memoryHitRate() {
            return total > 0 ? (double) hybridFromMemoryCount / total : 0.0;
        }

        @Override
        public String toString() {
            return "BridgeBenchmark{mpdt=" + String.format("%.1f%%", mpdtAccuracy() * 100)
                    + ", hybrid=" + String.format("%.1f%%", hybridAccuracy() * 100)
                    + ", memHits=" + hybridFromMemoryCount + "/" + total + "}";
        }
    }
}
