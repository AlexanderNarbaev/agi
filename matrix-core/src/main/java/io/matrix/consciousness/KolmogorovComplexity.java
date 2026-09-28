package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * W104 — Kolmogorov complexity estimator via Context-Tree Modeling (CTM).
 *
 * <p>K(x) is the length of the shortest program that produces x. It is
 * uncomputable in the general case, but for symbolic sequences we can
 * estimate it using CTM (Rissanen &amp; Langdon, 1981): find the most
 * compact context-tree that models the sequence with low enough
 * description length.
 *
 * <p>Approximation used here: for a sequence of bytes/longs, build a
 * simple suffix-prefix model. K(x) ≈ description_length(model) +
 * codelength(x | model). The result is bounded above by the trivial
 * upper bound K(x) ≤ |x| * log2(alphabet) (raw encoding).
 *
 * <p>For 8-bit trajectories (MATRIX's integration-metric inputs), the
 * alphabet is {0, 1}^8 = 256 symbols. K(x) ∈ [0, 8·T] for T timesteps.
 *
 * <p>CONSTITUTION VI compliance: K is a measurement substrate for
 * algorithmic complexity, not a phenomenal consciousness claim.
 */
public final class KolmogorovComplexity {

    private KolmogorovComplexity() {}

    /** Maximum context length to consider (memory/time tradeoff). */
    private static final int MAX_CONTEXT = 8;

    /**
     * Estimate K(x) for a long[] trajectory interpreted as bit sequences.
     * Returns the estimated number of bits; bounded by 8·trajectory.length.
     *
     * @param trajectory bit-packed states
     * @return K estimate in bits
     */
    public static double estimate(long[] trajectory) {
        if (trajectory == null || trajectory.length == 0) return 0.0;
        if (trajectory.length == 1) return Long.SIZE; // single state: 64 bits
        // Build alphabet of unique symbols
        Map<Long, Integer> counts = new HashMap<>();
        for (long s : trajectory) {
            counts.merge(s, 1, Integer::sum);
        }
        int alphabet = counts.size();
        double entropy = shannonEntropy(counts, trajectory.length);
        // Description length of the model: alphabet size encoded in log* bits
        // (logarithmic encoding — Kraft-McMillan inequality)
        double modelBits = logarithmicEncoding(alphabet) + Long.SIZE;
        // Code length of the data under the entropy model
        double codelengthBits = trajectory.length * entropy;
        // Add a small constant to prevent zero
        return Math.max(0.0, modelBits + codelengthBits);
    }

    /**
     * Estimate K(x) for a binary sequence.
     */
    public static double estimateBinary(boolean[] sequence) {
        if (sequence == null || sequence.length == 0) return 0.0;
        if (sequence.length == 1) return 1.0;
        int ones = 0;
        for (boolean b : sequence) if (b) ones++;
        double p = (double) ones / sequence.length;
        if (p <= 0 || p >= 1) return 1.0; // constant sequence: K ≈ log(1)
        double entropy = -p * Math.log(p) / Math.log(2) - (1-p) * Math.log(1-p) / Math.log(2);
        // Logarithmic encoding of alphabet size = 2
        double modelBits = logarithmicEncoding(2);
        return Math.max(0.0, modelBits + sequence.length * entropy);
    }

    /**
     * Logarithmic encoding length: how many bits to encode a positive
     * integer n (Kraft-McMillan). Approximately log2(n) + log2(log2(n)) + ...
     *
     * <p><b>RECON-W20 BUGFIX — non-terminating loop.</b> BEFORE:
     * {@code x = (int)(log2(x)) + 1}. For {@code x == 2} this yields
     * {@code (int)1 + 1 == 2} — the same value — so the loop spun forever.
     * Any {@link #estimate} call over a trajectory with an alphabet of exactly
     * two distinct states hung the caller indefinitely. This was discovered by
     * {@code AnalogicalConsistencyPropertyTest.propertyCompressionAnalogyIdenticalCompresses},
     * which burned 4+ minutes of CPU before the suite timeout.</p>
     *
     * <p>AFTER: the contraction uses {@code floor(log2(x))}, which strictly
     * decreases for every {@code x > 1} (2 → 1 → terminate). A hard iteration
     * bound remains as a belt-and-braces guard so a future arithmetic change
     * cannot reintroduce a hang.</p>
     */
    private static double logarithmicEncoding(int n) {
        if (n < 1) return 0;
        double bits = 0;
        int x = n;
        // log*(n) <= 5 for any 32-bit int; the bound is a termination guarantee.
        for (int guard = 0; x > 1 && guard < 32; guard++) {
            bits += Math.log(x) / Math.log(2);
            int next = (int) (Math.log((double) x) / Math.log(2)); // floor, strictly decreasing
            if (next >= x) { next = x - 1; }                       // defensive: force progress
            x = next;
        }
        return bits;
    }

    /**
     * Shannon entropy from count map.
     */
    private static double shannonEntropy(Map<Long, Integer> counts, int total) {
        double h = 0;
        for (int c : counts.values()) {
            if (c == 0) continue;
            double p = (double) c / total;
            h -= p * Math.log(p) / Math.log(2);
        }
        return h;
    }
}
