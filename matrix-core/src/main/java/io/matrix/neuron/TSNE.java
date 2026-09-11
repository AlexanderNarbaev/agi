package io.matrix.neuron;

import java.util.Random;

/**
 * DESIGN-53 — t-SNE (van der Maaten 2008).
 * Non-linear dimensionality reduction preserving local neighborhoods.
 * Pure function (CONSTITUTION I) with seeded initialization.
 *
 * <p>Simplified t-SNE: gradient descent on KL divergence between
 * high-D and low-D similarity distributions. Early exaggeration
 * + momentum.
 */
public final class TSNE {

    public record Projection(double[][] y) {}

    private TSNE() {}

    /**
     * Run t-SNE to 2D. Pure function given seed.
     * @param X high-D data, shape [n][d]
     * @param perplexity typical neighbor count
     * @param maxIter iterations of gradient descent
     * @param learningRate gradient step size
     * @param seed RNG seed
     * @return projection to 2D, shape [n][2]
     */
    public static Projection project(double[][] X, double perplexity,
                                      int maxIter, double learningRate,
                                      long seed) {
        if (X == null || X.length == 0) {
            throw new IllegalArgumentException("null/empty");
        }
        int n = X.length;
        int d = X[0].length;
        if (d < 2) throw new IllegalArgumentException("d ≥ 2");
        // 1. Compute high-D affinities P_ij (Gaussian kernel)
        double[][] P = computeHighDimAffinities(X, perplexity);
        // Symmetrize
        double sumP = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                sumP += P[i][j];
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                P[i][j] = Math.max((P[i][j] + P[j][i]) / (2 * sumP), 1e-12);
            }
        }
        // Early exaggeration for first 100 iters
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) P[i][j] *= 4.0;
            }
        }
        // 2. Initialize low-D with small random
        double[][] y = new double[n][2];
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) {
            y[i][0] = rng.nextGaussian() * 0.0001;
            y[i][1] = rng.nextGaussian() * 0.0001;
        }
        double[] dY0 = new double[n], dY1 = new double[n];
        // 3. Gradient descent
        for (int iter = 0; iter < maxIter; iter++) {
            // Low-D affinities Q (Student-t)
            double[][] Q = new double[n][n];
            double sumQ = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dy0 = y[i][0] - y[j][0];
                    double dy1 = y[i][1] - y[j][1];
                    double q = 1.0 / (1.0 + dy0 * dy0 + dy1 * dy1);
                    Q[i][j] = q;
                    Q[j][i] = q;
                    sumQ += 2 * q;
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) Q[i][j] /= Math.max(sumQ, 1e-12);
            }
            // Remove early exaggeration after 100 iters
            if (iter == 100) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        P[i][j] /= 4.0;
                    }
                }
            }
            // Gradient
            double momentum = (iter < 100) ? 0.5 : 0.8;
            for (int i = 0; i < n; i++) {
                double grad0 = 0, grad1 = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    double qij = Math.max(Q[i][j], 1e-12);
                    double pij = Math.max(P[i][j], 1e-12);
                    double mult = (pij - qij) * qij;
                    grad0 += mult * (y[i][0] - y[j][0]);
                    grad1 += mult * (y[i][1] - y[j][1]);
                }
                dY0[i] = momentum * dY0[i] - learningRate * grad0;
                dY1[i] = momentum * dY1[i] - learningRate * grad1;
            }
            for (int i = 0; i < n; i++) {
                y[i][0] += dY0[i];
                y[i][1] += dY1[i];
            }
        }
        return new Projection(y);
    }

    private static double[][] computeHighDimAffinities(double[][] X,
                                                      double perplexity) {
        int n = X.length;
        double[][] P = new double[n][n];
        double targetH = Math.log(perplexity);  // = log(perplexity)
        for (int i = 0; i < n; i++) {
            // Binary search for sigma_i
            double sigma = 1.0;
            double lo = 1e-20, hi = 1e20;
            for (int iter = 0; iter < 50; iter++) {
                double sumP = 0;
                double sumHP = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    double d2 = sqDist(X[i], X[j]);
                    double p = Math.exp(-d2 / (2 * sigma * sigma));
                    sumP += p;
                    sumHP += p * (-d2 / (2 * sigma * sigma));
                }
                double H = Math.log(sumP) + (sumHP / sumP);
                if (Math.abs(H - targetH) < 1e-5) break;
                if (H > targetH) hi = sigma; else lo = sigma;
                sigma = (lo + hi) / 2;
            }
            // Compute row of P
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double d2 = sqDist(X[i], X[j]);
                P[i][j] = Math.exp(-d2 / (2 * sigma * sigma));
            }
            double rowSum = 0;
            for (int j = 0; j < n; j++) rowSum += P[i][j];
            if (rowSum > 0) {
                for (int j = 0; j < n; j++) P[i][j] /= rowSum;
            }
        }
        return P;
    }

    private static double sqDist(double[] a, double[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }
}
