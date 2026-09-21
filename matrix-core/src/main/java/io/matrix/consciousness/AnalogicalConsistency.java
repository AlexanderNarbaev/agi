package io.matrix.consciousness;

import java.util.Arrays;

/**
 * W105 — Analogical consistency measurement (Nyaya Upamana).
 *
 * <p>Upamana (उपमान, "comparison" / "analogy") is one of the four
 * pramanas (valid means of knowledge) in classical Nyaya school
 * (Aksapada Gautama, ~2nd century BCE). It is the knowledge of a
 * thing through its similarity to something already known.
 *
 * <p>Nyaya Sutra (1.1.6): "Upamana is the knowledge of a thing from
 * its similarity to another thing well known."
 *
 * <p>In the MATRIX cognitive architecture, analogical consistency
 * measures the structural similarity between two integration-metric
 * trajectories. High analogy should correspond to low cognitive
 * error rates (H-089 hypothesis).
 *
 * <p>CONSTITUTION VI compliance: A measure of structural similarity,
 * not a phenomenal consciousness claim.
 */
public final class AnalogicalConsistency {

    private AnalogicalConsistency() {}

    /**
     * Compute the analogical similarity between two equal-length trajectories.
     * Returns a value in [0, 1]: 1 = identical, 0 = completely different.
     *
     * <p>Method: normalized Hamming similarity of bit representation.
     * For each timestep, XOR the two long states, count differing bits,
     * and average across timesteps.
     */
    public static double bitSimilarity(long[] a, long[] b) {
        if (a == null || b == null) return 0.0;
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                "Trajectory lengths must match: " + a.length + " vs " + b.length);
        }
        if (a.length == 0) return 1.0;
        long totalDiffs = 0;
        for (int i = 0; i < a.length; i++) {
            totalDiffs += Long.bitCount(a[i] ^ b[i]);
        }
        // 64 bits per long; similarity = 1 - (diff / total)
        double maxDiff = 64.0 * a.length;
        return 1.0 - (totalDiffs / maxDiff);
    }

    /**
     * Compute structural similarity between two trajectories of equal length.
     * Compares the rank-order of values rather than the raw values — this
     * captures "the same structure" even when magnitudes differ.
     *
     * <p>Method: Spearman rank correlation, normalized to [0, 1].
     * 1.0 = identical rank structure, 0.0 = inverse structure, 0.5 = no relation.
     */
    public static double structuralSimilarity(long[] a, long[] b) {
        if (a == null || b == null) return 0.0;
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                "Trajectory lengths must match: " + a.length + " vs " + b.length);
        }
        if (a.length < 2) return 1.0;
        double[] ranksA = ranks(a);
        double[] ranksB = ranks(b);
        double rho = spearman(ranksA, ranksB);
        // Map [-1, 1] to [0, 1]: -1 → 0, 0 → 0.5, 1 → 1
        return 0.5 * (rho + 1.0);
    }

    /**
     * Compression-ratio analogy: when two trajectories share structure,
     * concatenating them compresses better than two independent streams.
     * Returns ratio of joint code-length to separate code-lengths.
     *
     * <p>1.0 = no compression gain; &lt; 1.0 = structure shared; &gt; 1.0 = anti-structured.
     */
    public static double compressionAnalogy(long[] a, long[] b) {
        if (a == null || b == null) return 1.0;
        if (a.length == 0 || b.length == 0) return 1.0;
        long[] joined = new long[a.length + b.length];
        System.arraycopy(a, 0, joined, 0, a.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        double kA = KolmogorovComplexity.estimate(a);
        double kB = KolmogorovComplexity.estimate(b);
        double kJoined = KolmogorovComplexity.estimate(joined);
        double sum = kA + kB;
        if (sum == 0) return 1.0;
        return kJoined / sum;
    }

    /** Compute ranks with average-tie handling. */
    private static double[] ranks(long[] a) {
        int n = a.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (i, j) -> Long.compare(a[i], a[j]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && a[idx[j]] == a[idx[i]]) j++;
            double avgRank = (i + j - 1) / 2.0 + 1.0; // 1-indexed average rank
            for (int k = i; k < j; k++) r[idx[k]] = avgRank;
            i = j;
        }
        return r;
    }

    private static double spearman(double[] x, double[] y) {
        int n = x.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < n; i++) {
            double xi = x[i] - mx;
            double yi = y[i] - my;
            num += xi * yi;
            dx += xi * xi;
            dy += yi * yi;
        }
        if (dx == 0 || dy == 0) return 1.0;
        return num / Math.sqrt(dx * dy);
    }
}
