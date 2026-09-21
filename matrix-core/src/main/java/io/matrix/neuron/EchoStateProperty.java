package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DESIGN-51 — Echo State Property test (Jaeger 2001).
 * Spectral radius ρ(W) < 1 guarantees ESN converges.
 * Pure function (CONSTITUTION I).
 */
public final class EchoStateProperty {

    private EchoStateProperty() {}

    /**
     * Power iteration to compute spectral radius of square matrix W.
     * Returns approximation of ρ(W) (largest absolute eigenvalue).
     */
    public static double spectralRadius(double[][] W, int maxIter,
                                        double tol) {
        if (W == null || W.length == 0) {
            throw new IllegalArgumentException("null/empty");
        }
        int n = W.length;
        // Check square
        for (double[] row : W) {
            if (row.length != n) {
                throw new IllegalArgumentException("non-square");
            }
        }
        // Random initial vector
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = 1.0 / Math.sqrt(n);
        double prevNorm = -1;
        for (int iter = 0; iter < maxIter; iter++) {
            // w = W v
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) {
                    sum += W[i][j] * v[j];
                }
                w[i] = sum;
            }
            // norm(w)
            double norm = 0;
            for (double x : w) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm < 1e-15) return 0;
            // v = w / norm
            for (int i = 0; i < n; i++) v[i] = w[i] / norm;
            if (prevNorm > 0 && Math.abs(norm - prevNorm) < tol) {
                return norm;
            }
            prevNorm = norm;
        }
        return prevNorm;
    }

    /**
     * Assert echo state property: ρ(W) &lt; threshold (default 0.95).
     * Returns true if property holds.
     */
    public static boolean hasEchoStateProperty(double[][] W, double threshold) {
        return spectralRadius(W, 200, 1e-6) < threshold;
    }

    /**
     * Rescale W so that spectral radius equals target.
     * Returns the rescaled matrix (new array).
     */
    public static double[][] rescale(double[][] W, double targetRadius) {
        double current = spectralRadius(W, 200, 1e-6);
        if (current < 1e-15) {
            // Zero matrix — can't rescale meaningfully
            return W;
        }
        double factor = targetRadius / current;
        int n = W.length;
        double[][] scaled = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                scaled[i][j] = W[i][j] * factor;
            }
        }
        return scaled;
    }
}
