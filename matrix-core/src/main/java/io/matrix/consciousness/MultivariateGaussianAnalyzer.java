package io.matrix.consciousness;

/**
 * W164 — Multivariate Gaussian analyzer.
 *
 * <p>Computes Gaussian-fit statistics over a multivariate trajectory:
 * - Mean vector
 * - Covariance matrix
 * - Correlation matrix (normalized covariance)
 * - Mahalanobis distance (how far a point is from the mean)
 * - Chi-squared statistic (whether point is in tail)
 *
 * <p>Used for analyzing how multiple Φ measures co-vary across
 * brain cycles. Identifies which measurements are correlated vs
 * independent.
 *
 * <p>CONSTITUTION VI compliance: statistical analysis of
 * measurement substrate, not phenomenal consciousness claim.
 */
public final class MultivariateGaussianAnalyzer {

    private MultivariateGaussianAnalyzer() {}

    /**
     * Compute mean vector across columns.
     */
    public static double[] mean(double[][] samples) {
        if (samples == null || samples.length == 0) return new double[0];
        int n = samples.length;
        int d = samples[0].length;
        double[] m = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += samples[i][j];
            m[j] = sum / n;
        }
        return m;
    }

    /**
     * Compute covariance matrix (d × d).
     * Population covariance (divide by N).
     */
    public static double[][] covariance(double[][] samples) {
        if (samples == null || samples.length < 2) return new double[0][];
        int n = samples.length;
        int d = samples[0].length;
        double[] m = mean(samples);
        double[][] cov = new double[d][d];
        for (int j = 0; j < d; j++) {
            for (int k = j; k < d; k++) {
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    sum += (samples[i][j] - m[j]) * (samples[i][k] - m[k]);
                }
                cov[j][k] = sum / n;
                cov[k][j] = cov[j][k];
            }
        }
        return cov;
    }

    /**
     * Compute correlation matrix (normalized covariance).
     */
    public static double[][] correlation(double[][] samples) {
        double[][] cov = covariance(samples);
        int d = cov.length;
        if (d == 0) return new double[0][];
        double[][] corr = new double[d][d];
        for (int j = 0; j < d; j++) {
            for (int k = 0; k < d; k++) {
                double stdJ = Math.sqrt(Math.max(0, cov[j][j]));
                double stdK = Math.sqrt(Math.max(0, cov[k][k]));
                if (stdJ == 0 || stdK == 0) {
                    corr[j][k] = j == k ? 1.0 : 0.0;
                } else {
                    corr[j][k] = cov[j][k] / (stdJ * stdK);
                }
            }
        }
        return corr;
    }

    /**
     * Compute Mahalanobis distance from mean to a query point.
     * d_M = sqrt((x - μ)^T Σ^{-1} (x - μ))
     */
    public static double mahalanobisDistance(double[] query, double[] mu, double[][] sigmaInv) {
        if (query == null || mu == null || sigmaInv == null) return 0.0;
        if (query.length != mu.length || query.length != sigmaInv.length) return 0.0;
        int d = query.length;
        double[] diff = new double[d];
        for (int i = 0; i < d; i++) diff[i] = query[i] - mu[i];
        // diff^T Σ^{-1}
        double[] tmp = new double[d];
        for (int i = 0; i < d; i++) {
            double sum = 0;
            for (int j = 0; j < d; j++) sum += diff[j] * sigmaInv[j][i];
            tmp[i] = sum;
        }
        // (diff^T Σ^{-1}) . diff
        double dSq = 0;
        for (int i = 0; i < d; i++) dSq += tmp[i] * diff[i];
        return Math.sqrt(Math.max(0, dSq));
    }

    /**
     * Compute log-determinant of a covariance matrix.
     * Used for Gaussian log-likelihood and Φ_linGauss.
     */
    public static double logDeterminant(double[][] cov) {
        if (cov == null || cov.length == 0) return 0.0;
        // Cholesky decomposition
        int n = cov.length;
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = cov[i][j];
                for (int k = 0; k < j; k++) sum -= l[i][k] * l[j][k];
                if (i == j) {
                    if (sum <= 0) return Double.NEGATIVE_INFINITY;
                    l[i][j] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        double logDet = 0;
        for (int i = 0; i < n; i++) logDet += Math.log(l[i][i]);
        return 2 * logDet;  // log(det(L)^2) = 2 * log(det(L))
    }
}
