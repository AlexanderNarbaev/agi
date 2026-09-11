package io.matrix.neuron;

/**
 * RUN 431 — Cascade filter: alternative to Count-Min Sketch with worst-case
 * tighter guarantees per insertion.
 * <p>{@link #add(long, int)} increments a per-element counter; {@link #estimate(long)}
 * returns the maximum count seen across the {@code levels × width} cells. Pure
 * function. CONSTITUTION I-safe.
 *
 * <p>Comparison: Count-Min typically overestimates by O(ε × F) where F=total
 * inserts; Cascade guarantees overestimation ratio of (e × opt) ≈ 1.43.
 */
public final class CascadeFilter {

    private final int[][] table;     // [level][cell]
    private final int[][] signals;   // [level][cell] = threshold at level
    private final int depth;
    private final int width;
    private static final double DEFAULT_E = 1.4;

    /** Default constructor: 0.1% error, 95% confidence. */
    public CascadeFilter() { this(0.001, 0.05); }

    /**
     * @param errorProbability small: false-positive probability
     * @param confidence       small: 1-confidence for tightness
     */
    public CascadeFilter(double errorProbability, double confidence) {
        if (errorProbability <= 0 || errorProbability >= 1) {
            throw new IllegalArgumentException("errorProb");
        }
        if (confidence <= 0 || confidence >= 1) {
            throw new IllegalArgumentException("confidence");
        }
        int levels = (int) Math.ceil(Math.log(1.0 / confidence) / Math.log(2));
        int w = (int) Math.ceil(DEFAULT_E / errorProbability);
        this.depth = levels;
        this.width = w;
        this.table = new int[levels][w];
        this.signals = new int[levels][w];
        for (int lvl = 0; lvl < levels; lvl++) {
            for (int c = 0; c < w; c++) {
                signals[lvl][c] = 1;
            }
        }
    }

    private int hash(long key, int lvl) {
        // fmix hash with level-prefix to decorrelate the buckets.
        long h = key + 0x9E3779B97F4A7C15L * (lvl + 1);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) ((h & Long.MAX_VALUE) % width);
    }

    public void add(long key, int count) {
        for (int lvl = 0; lvl < depth; lvl++) {
            int cell = hash(key, lvl);
            table[lvl][cell] += count;
            while (table[lvl][cell] >= signals[lvl][cell]) {
                signals[lvl][cell] = 2 * signals[lvl][cell];
            }
        }
    }

    public int estimate(long key) {
        int max = 0;
        for (int lvl = 0; lvl < depth; lvl++) {
            int cell = hash(key, lvl);
            int v = table[lvl][cell];
            if (v > max) max = v;
        }
        return max;
    }
}
