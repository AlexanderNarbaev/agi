package io.matrix.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Query expansion for boolean vector RAG.
 *
 * <p>Generates multiple query variants from a single boolean vector query to
 * improve recall by exploring different semantic aspects of the query space.
 * Each variant captures a different perspective:
 * <ul>
 *   <li><b>Bit-flip:</b> flips random subsets of bits to explore nearby semantic regions</li>
 *   <li><b>Rotate:</b> cyclically shifts bits to capture rotational invariance</li>
 *   <li><b>Complement:</b> inverts bits to find semantically opposite concepts</li>
 *   <li><b>Mask:</b> zeroes out random bit subsets for partial matching</li>
 *   <li><b>Noise:</b> adds gaussian-like bit noise for robustness</li>
 * </ul>
 *
 * <p>Thread-safe: immutable configuration, no shared mutable state.
 *
 * <p>Ref: Phase 2 — Boolean RAG, Query Expansion
 */
public final class QueryExpander {

    private final int numVariants;
    private final double flipProbability;
    private final boolean enableRotate;
    private final boolean enableComplement;
    private final boolean enableFlip;
    private final boolean enableMask;
    private final boolean enableNoise;
    private final long seed;

    private QueryExpander(Builder builder) {
        this.numVariants = builder.numVariants;
        this.flipProbability = builder.flipProbability;
        this.enableRotate = builder.enableRotate;
        this.enableComplement = builder.enableComplement;
        this.enableFlip = builder.enableFlip;
        this.enableMask = builder.enableMask;
        this.enableNoise = builder.enableNoise;
        this.seed = builder.seed;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Expands a boolean vector query into multiple variants.
     *
     * <p>The original query is always included as the first element.
     * Additional variants are generated based on configured strategies.
     *
     * @param query original boolean vector query (non-null, must match expected dimensions)
     * @return list of query variants including the original (size = 1 + numVariants)
     */
    public List<long[]> expand(long[] query) {
        Objects.requireNonNull(query, "query");
        if (query.length == 0) {
            throw new IllegalArgumentException("Query must not be empty");
        }

        List<long[]> variants = new ArrayList<>(numVariants + 1);
        variants.add(query.clone()); // original always first

        SplittableRandom rng = new SplittableRandom(seed);

        // Generate variants using each enabled strategy
        int variantsPerStrategy = Math.max(1, numVariants / countEnabledStrategies());
        int remaining = numVariants;

        if (enableRotate) {
            int count = Math.min(variantsPerStrategy, remaining);
            for (int i = 0; i < count; i++) {
                variants.add(rotate(query, i + 1));
            }
            remaining -= count;
        }

        if (enableComplement && remaining > 0) {
            variants.add(complement(query));
            remaining--;
        }

        if (enableFlip && remaining > 0) {
            int count = Math.min(variantsPerStrategy, remaining);
            for (int i = 0; i < count; i++) {
                variants.add(flipBits(query, flipProbability, rng));
            }
            remaining -= count;
        }

        if (enableMask && remaining > 0) {
            int count = Math.min(variantsPerStrategy, remaining);
            for (int i = 0; i < count; i++) {
                variants.add(maskBits(query, rng));
            }
            remaining -= count;
        }

        if (enableNoise && remaining > 0) {
            for (int i = 0; i < remaining; i++) {
                variants.add(addNoise(query, rng));
            }
        }

        return Collections.unmodifiableList(variants);
    }

    /**
     * Rotates bits cyclically by the specified shift amount.
     *
     * <p>For 64-bit vectors, rotates within each long. For multi-long vectors,
     * rotates across the entire bit array.
     */
    static long[] rotate(long[] query, int shift) {
        long[] result = new long[query.length];
        int totalBits = query.length * 64;
        int effectiveShift = shift % totalBits;
        if (effectiveShift < 0) effectiveShift += totalBits;

        for (int i = 0; i < totalBits; i++) {
            int srcIdx = i;
            int dstIdx = (i + effectiveShift) % totalBits;

            int srcLong = srcIdx / 64;
            int srcBit = srcIdx % 64;
            int dstLong = dstIdx / 64;
            int dstBit = dstIdx % 64;

            if ((query[srcLong] & (1L << srcBit)) != 0) {
                result[dstLong] |= (1L << dstBit);
            }
        }
        return result;
    }

    /**
     * Computes the bitwise complement (NOT) of the query.
     */
    static long[] complement(long[] query) {
        long[] result = new long[query.length];
        for (int i = 0; i < query.length; i++) {
            result[i] = ~query[i];
        }
        return result;
    }

    /**
     * Flips each bit with the given probability.
     */
    static long[] flipBits(long[] query, double probability, SplittableRandom rng) {
        long[] result = query.clone();
        for (int i = 0; i < result.length; i++) {
            long mask = 0;
            for (int bit = 0; bit < 64; bit++) {
                if (rng.nextDouble() < probability) {
                    mask |= (1L << bit);
                }
            }
            result[i] ^= mask;
        }
        return result;
    }

    /**
     * Masks out (zeros) a random subset of bits (30-50% masked).
     */
    static long[] maskBits(long[] query, SplittableRandom rng) {
        long[] result = query.clone();
        double maskRatio = 0.3 + rng.nextDouble() * 0.2; // 30-50%
        for (int i = 0; i < result.length; i++) {
            long mask = 0;
            for (int bit = 0; bit < 64; bit++) {
                if (rng.nextDouble() >= maskRatio) {
                    mask |= (1L << bit);
                }
            }
            result[i] &= mask;
        }
        return result;
    }

    /**
     * Adds bit noise — flips a small percentage of bits (5-15%).
     */
    static long[] addNoise(long[] query, SplittableRandom rng) {
        long[] result = query.clone();
        double noiseRate = 0.05 + rng.nextDouble() * 0.10; // 5-15%
        for (int i = 0; i < result.length; i++) {
            long mask = 0;
            for (int bit = 0; bit < 64; bit++) {
                if (rng.nextDouble() < noiseRate) {
                    mask |= (1L << bit);
                }
            }
            result[i] ^= mask;
        }
        return result;
    }

    private int countEnabledStrategies() {
        int count = 0;
        if (enableRotate) count++;
        if (enableComplement) count++;
        if (enableFlip) count++;
        if (enableMask) count++;
        if (enableNoise) count++;
        return Math.max(1, count);
    }

    // --- Getters ---

    public int numVariants() { return numVariants; }
    public double flipProbability() { return flipProbability; }

    // --- Builder ---

    public static final class Builder {
        private int numVariants = 4;
        private double flipProbability = 0.1;
        private boolean enableRotate = true;
        private boolean enableComplement = true;
        private boolean enableFlip = true;
        private boolean enableMask = true;
        private boolean enableNoise = true;
        private long seed = 42L;

        public Builder numVariants(int numVariants) {
            if (numVariants < 1) throw new IllegalArgumentException("numVariants must be >= 1");
            this.numVariants = numVariants;
            return this;
        }

        public Builder flipProbability(double p) {
            if (p < 0 || p > 1) throw new IllegalArgumentException("flipProbability must be 0-1");
            this.flipProbability = p;
            return this;
        }

        public Builder enableRotate(boolean enable) { this.enableRotate = enable; return this; }
        public Builder enableComplement(boolean enable) { this.enableComplement = enable; return this; }
        public Builder enableFlip(boolean enable) { this.enableFlip = enable; return this; }
        public Builder enableMask(boolean enable) { this.enableMask = enable; return this; }
        public Builder enableNoise(boolean enable) { this.enableNoise = enable; return this; }

        public Builder seed(long seed) { this.seed = seed; return this; }

        public QueryExpander build() {
            return new QueryExpander(this);
        }
    }
}
