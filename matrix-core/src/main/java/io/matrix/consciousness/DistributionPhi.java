package io.matrix.consciousness;

import java.util.Random;

/**
 * W149 — Φ measurement over empirical distributions.
 *
 * <p>Unlike IntegrationMetrics.phiBinary which assumes N-bit states,
 * this measures Φ over arbitrary continuous or discrete distributions.
 * Used for empirical benchmarks where we don't have a fixed alphabet.
 *
 * <p>Method: bin the data into K equal-width bins, compute entropy of
 * joint distribution over T timesteps × B bins, then bipartition.
 */
public final class DistributionPhi {

    private DistributionPhi() {}

    /** Number of histogram bins per dimension. */
    public static final int DEFAULT_BINS = 16;

    /**
     * Compute Φ over a 1D time series by binning into K bins.
     *
     * @param values time series of doubles
     * @param N bipartition size (>= 1)
     * @param bins number of histogram bins per dimension
     * @return Phi estimate (entropy-based)
     */
    public static double phiFromTimeSeries(double[] values, int N, int bins) {
        if (values == null || values.length == 0) return 0.0;
        if (N < 1) throw new IllegalArgumentException("N must be ≥ 1");
        if (bins < 2) throw new IllegalArgumentException("bins must be ≥ 2");

        // Find range
        double min = values[0], max = values[0];
        for (double v : values) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max == min) {
            // All same value → no information
            return 0.0;
        }

        // Bin the values
        int[] counts = new int[bins];
        double range = max - min;
        for (double v : values) {
            int bin = (int) ((v - min) / range * bins);
            if (bin >= bins) bin = bins - 1;
            if (bin < 0) bin = 0;
            counts[bin]++;
        }

        // Compute MI bipartition
        return miBipartition(counts, N);
    }

    /**
     * Compute Φ over 2D time series (joint distribution).
     */
    public static double phiFrom2DTimeSeries(double[] x, double[] y, int N, int bins) {
        if (x == null || y == null || x.length != y.length || x.length == 0) return 0.0;
        if (N < 1) throw new IllegalArgumentException("N must be ≥ 1");
        if (bins < 2) throw new IllegalArgumentException("bins must be ≥ 2");

        // Find ranges
        double[] mins = new double[2];
        double[] maxs = new double[2];
        mins[0] = x[0]; maxs[0] = x[0];
        mins[1] = y[0]; maxs[1] = y[0];
        for (int i = 0; i < x.length; i++) {
            if (x[i] < mins[0]) mins[0] = x[i];
            if (x[i] > maxs[0]) maxs[0] = x[i];
            if (y[i] < mins[1]) mins[1] = y[i];
            if (y[i] > maxs[1]) maxs[1] = y[i];
        }

        // Joint histogram
        int[][] joint = new int[bins][bins];
        double range0 = maxs[0] - mins[0];
        double range1 = maxs[1] - mins[1];
        if (range0 == 0 || range1 == 0) return 0.0;

        for (int i = 0; i < x.length; i++) {
            int bx = (int) ((x[i] - mins[0]) / range0 * bins);
            int by = (int) ((y[i] - mins[1]) / range1 * bins);
            if (bx >= bins) bx = bins - 1;
            if (by >= bins) by = bins - 1;
            if (bx < 0) bx = 0;
            if (by < 0) by = 0;
            joint[bx][by]++;
        }

        return miBipartition2D(joint, N);
    }

    private static double miBipartition(int[] counts, int N) {
        int total = 0;
        for (int c : counts) total += c;
        if (total == 0) return 0.0;

        // For 1D, bipartition is just half
        int mid = counts.length / 2;
        double hLeft = entropy(counts, 0, mid, total);
        double hRight = entropy(counts, mid, counts.length, total);
        double hFull = entropy(counts, 0, counts.length, total);
        double mi = hLeft + hRight - hFull;
        return Math.max(0.0, mi);
    }

    private static double miBipartition2D(int[][] joint, int N) {
        int total = 0;
        for (int[] row : joint) for (int c : row) total += c;
        if (total == 0) return 0.0;

        int mid = joint.length / 2;
        // Marginals
        int[] margX = new int[joint.length];
        int[] margY = new int[joint[0].length];
        for (int i = 0; i < joint.length; i++) {
            for (int j = 0; j < joint[i].length; j++) {
                margX[i] += joint[i][j];
                margY[j] += joint[i][j];
            }
        }
        double hX = entropy(margX, 0, margX.length, total);
        double hY = entropy(margY, 0, margY.length, total);
        double hJoint = 0;
        for (int i = 0; i < joint.length; i++) {
            for (int j = 0; j < joint[i].length; j++) {
                int c = joint[i][j];
                if (c > 0) {
                    double p = (double) c / total;
                    hJoint -= p * Math.log(p);
                }
            }
        }
        double mi = hX + hY - hJoint;
        return Math.max(0.0, mi);
    }

    private static double entropy(int[] counts, int from, int to, int total) {
        double h = 0;
        for (int i = from; i < to; i++) {
            int c = counts[i];
            if (c > 0) {
                double p = (double) c / total;
                h -= p * Math.log(p);
            }
        }
        return h;
    }
}
