package io.matrix.neuron;

/**
 * RUN 426 — Ordinary least-squares linear regression (closed form).
 * <p>Solves {@code β = (Xᵀ X)⁻¹ Xᵀ y} for {@code β}. Pure function.
 * Does not handle singular {@code Xᵀ X} — caller pre-validates rank.
 * CONSTITUTION I-safe.
 */
public final class LinearRegression {

    private LinearRegression() {}

    /**
     * Fit slope + intercept on paired data.
     *
     * @param xs features, length n
     * @param ys targets, length n
     */
    public static double[] fitSimple(double[] xs, double[] ys) {
        int n = xs.length;
        if (n != ys.length || n < 2) throw new IllegalArgumentException("n>=2, length match");
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += xs[i];
            sumY += ys[i];
            sumXY += xs[i] * ys[i];
            sumXX += xs[i] * xs[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;
        double cov = sumXY - n * meanX * meanY;
        double var = sumXX - n * meanX * meanX;
        if (Math.abs(var) < 1e-12) throw new IllegalStateException("X variance too small");
        double slope = cov / var;
        double intercept = meanY - slope * meanX;
        return new double[]{slope, intercept};
    }

    /** Multivariate closed-form OLS: betas = (Xᵀ X + λI)⁻¹ Xᵀ y (Ridge regression). */
    public static double[] fitRidge(double[][] xs, double[] y, double lambda) {
        int n = xs.length;
        if (n == 0) throw new IllegalArgumentException("n=0");
        int d = xs[0].length;
        double[][] XtX = new double[d][d];
        double[] Xty = new double[d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                Xty[j] += xs[i][j] * y[i];
                for (int k = 0; k < d; k++) {
                    XtX[j][k] += xs[i][j] * xs[i][k];
                }
            }
        }
        // Add L2
        for (int j = 0; j < d; j++) XtX[j][j] += lambda;
        return solveLinear(XtX, Xty);
    }

    /**
     * Gauss-Jordan solver for A·x = b. Returns x or throws on singular matrix.
     */
    public static double[] solveLinear(double[][] A, double[] b) {
        int n = A.length;
        if (b.length != n) throw new IllegalArgumentException("dim mismatch");
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[pivot][col])) pivot = row;
            }
            if (Math.abs(aug[pivot][col]) < 1e-12) throw new IllegalStateException("Singular");
            double[] tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp;
            double pivotVal = aug[col][col];
            for (int j = col; j <= n; j++) aug[col][j] /= pivotVal;
            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = col; j <= n; j++) aug[row][j] -= factor * aug[col][j];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = aug[i][n];
        return x;
    }
}
