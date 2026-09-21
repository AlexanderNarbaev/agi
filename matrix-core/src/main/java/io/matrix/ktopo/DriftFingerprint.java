package io.matrix.ktopo;

import java.util.Arrays;

/**
 * Drift fingerprint of a knowledge graph (SPEC-003 FR-4): an L1-normalized
 * histogram of Ollivier-Ricci edge curvatures over fixed bins.
 *
 * <p>The histogram is a compact, comparable signature of graph structure;
 * degradation between two snapshots is measured by
 * {@link FingerprintDistance#wasserstein1(double[], double[], double)}.
 */
public final class DriftFingerprint {

    /** Fixed bin count (deterministic schema for comparability). */
    public static final int BINS = 24;

    /** Theoretical lower bound of Ollivier-Ricci κ on simple graphs. */
    public static final double KAPPA_MIN = -2.0;

    /** Theoretical upper bound of Ollivier-Ricci κ. */
    public static final double KAPPA_MAX = 1.0;

    /** Grid step of the histogram in κ units. */
    public static final double BIN_WIDTH = (KAPPA_MAX - KAPPA_MIN) / BINS;

    private DriftFingerprint() {}

    /**
     * Builds the normalized curvature histogram.
     *
     * @param curvatures edge curvatures aligned with {@code Graph} edges
     * @return L1-normalized histogram of length {@link #BINS}; values outside
     *         {@code [KAPPA_MIN, KAPPA_MAX]} are clamped; an empty input yields
     *         the uniform distribution (documented neutral baseline)
     */
    public static double[] of(double[] curvatures) {
        if (curvatures == null) {
            throw new IllegalArgumentException("curvatures must not be null");
        }
        double[] bins = new double[BINS];
        if (curvatures.length == 0) {
            Arrays.fill(bins, 1.0 / BINS);
            return bins;
        }
        for (double kappa : curvatures) {
            double c = Math.max(KAPPA_MIN, Math.min(KAPPA_MAX, kappa));
            int idx = (int) ((c - KAPPA_MIN) / BIN_WIDTH);
            if (idx >= BINS) {
                idx = BINS - 1;
            }
            bins[idx] += 1.0;
        }
        for (int i = 0; i < BINS; i++) {
            bins[i] /= curvatures.length;
        }
        return bins;
    }
}
