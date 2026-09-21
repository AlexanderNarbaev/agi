package io.matrix.consciousness;

/**
 * W151 — Series correlator: time series correlation analysis.
 *
 * <p>Implements standard time series correlation metrics for use in
 * MATRIX cross-disciplinary analysis:
 * - Pearson correlation
 * - Spearman rank correlation
 * - Cross-correlation at lag τ
 * - Autocorrelation function
 *
 * <p>Distinguishes from AnalogicalConsistency which is for trajectory
 * comparison; SeriesCorrelator is for classical time-series analysis
 * of integration metric outputs.
 *
 * <p>CONSTITUTION VI compliance: classical statistical measures,
 * not phenomenal consciousness claims.
 */
public final class SeriesCorrelator {

    private SeriesCorrelator() {}

    /**
     * Compute Pearson correlation between two equal-length series.
     * Returns 0 if either has zero variance.
     */
    public static double pearson(double[] x, double[] y) {
        if (x == null || y == null) return 0.0;
        if (x.length != y.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        if (x.length < 2) return 0.0;
        double mx = mean(x), my = mean(y);
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < x.length; i++) {
            double xi = x[i] - mx;
            double yi = y[i] - my;
            num += xi * yi;
            dx += xi * xi;
            dy += yi * yi;
        }
        if (dx == 0 || dy == 0) return 0.0;
        return num / Math.sqrt(dx * dy);
    }

    /**
     * Compute Spearman rank correlation (Pearson on ranks).
     */
    public static double spearman(double[] x, double[] y) {
        if (x == null || y == null) return 0.0;
        if (x.length != y.length) throw new IllegalArgumentException("Lengths must match");
        if (x.length < 2) return 0.0;
        double[] ranksX = ranks(x);
        double[] ranksY = ranks(y);
        return pearson(ranksX, ranksY);
    }

    /**
     * Cross-correlation at lag τ.
     * Positive τ means x is shifted forward relative to y.
     */
    public static double crossCorrelation(double[] x, double[] y, int tau) {
        if (x == null || y == null) return 0.0;
        if (tau < 0) {
            // Negative τ: reverse the roles
            return crossCorrelation(y, x, -tau);
        }
        if (tau >= x.length || tau >= y.length) return 0.0;
        int n = Math.min(x.length, y.length) - tau;
        if (n < 2) return 0.0;
        double[] xs = new double[n];
        double[] ys = new double[n];
        System.arraycopy(x, 0, xs, 0, n);
        System.arraycopy(y, tau, ys, 0, n);
        return pearson(xs, ys);
    }

    /**
     * Compute autocorrelation function for series x at lags 0..maxLag.
     */
    public static double[] autocorrelation(double[] x, int maxLag) {
        if (x == null || x.length == 0 || maxLag < 0) return new double[0];
        if (maxLag >= x.length) maxLag = x.length - 1;
        double[] result = new double[maxLag + 1];
        for (int tau = 0; tau <= maxLag; tau++) {
            result[tau] = crossCorrelation(x, x, tau);
        }
        return result;
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    private static double[] ranks(double[] a) {
        int n = a.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (i, j) -> Double.compare(a[i], a[j]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && a[idx[j]] == a[idx[i]]) j++;
            double avgRank = (i + j - 1) / 2.0 + 1.0;
            for (int k = i; k < j; k++) r[idx[k]] = avgRank;
            i = j;
        }
        return r;
    }
}
