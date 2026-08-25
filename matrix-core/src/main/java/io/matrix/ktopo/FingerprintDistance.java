package io.matrix.ktopo;

/**
 * Exact Wasserstein-1 distance between drift fingerprints (SPEC-003 FR-4).
 *
 * <p>For distributions on the real line there is a closed-form solution:
 * {@code W1(μ, ν) = ∫ |F_μ(t) − F_ν(t)| dt} where {@code F} are the CDFs
 * (see Vallender, 1974). On a discrete uniform grid of bins this reduces
 * to the sum of absolute CDF differences multiplied by the bin width.
 */
public final class FingerprintDistance {

    private FingerprintDistance() {}

    /**
     * Exact W1 between two {@link DriftFingerprint} histograms.
     *
     * @param a first fingerprint (length {@code DriftFingerprint.BINS})
     * @param b second fingerprint (same length)
     * @return non-negative distance; 0 for identical inputs
     */
    public static double distance(double[] a, double[] b) {
        return wasserstein1(a, b, DriftFingerprint.BIN_WIDTH);
    }

    /**
     * Exact W1 between two equal-length histograms on a uniform grid.
     *
     * <p>Closed form for 1D distributions:
     * {@code Σ_i |cumA_i − cumB_i| · binWidth}.
     *
     * @param a        first histogram (L1-normalized expected)
     * @param b        second histogram (same length)
     * @param binWidth grid step in value units
     * @return non-negative distance
     */
    public static double wasserstein1(double[] a, double[] b, double binWidth) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("histograms must be non-null and equal length");
        }
        if (binWidth <= 0) {
            throw new IllegalArgumentException("binWidth must be positive");
        }
        double cumA = 0.0;
        double cumB = 0.0;
        double total = 0.0;
        for (int i = 0; i < a.length; i++) {
            cumA += a[i];
            cumB += b[i];
            total += Math.abs(cumA - cumB);
        }
        return total * binWidth;
    }
}
