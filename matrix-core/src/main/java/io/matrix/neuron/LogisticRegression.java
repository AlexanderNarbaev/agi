package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 426 — Logistic regression (binary, batch gradient descent).
 * <p>Sigmoid + cross-entropy loss; iterates weight updates for {@code maxIters}
 * epochs. Caller-supplied {@link Random} for shuffle order. CONSTITUTION I-safe.
 */
public final class LogisticRegression {

    private LogisticRegression() {}

    public static double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }

    /**
     * Fit a classifier on binary labels.
     *
     * @param xs        n×d feature matrix
     * @param y         binary labels 0/1
     * @param learningRate gradient step size
     * @param maxIters     epochs
     * @param lambda       L2 regularisation
     * @param rng          optional shuffle
     */
    public static double[] fit(double[][] xs, int[] y, double learningRate,
                               int maxIters, double lambda, Random rng) {
        int n = xs.length;
        int d = xs[0].length;
        double[] w = new double[d];
        double[] g = new double[d];
        // Initialise order
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int epoch = 0; epoch < maxIters; epoch++) {
            // Shuffle
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = order[i]; order[i] = order[j]; order[j] = t;
            }
            // Reset gradient
            java.util.Arrays.fill(g, 0.0);
            for (int idx = 0; idx < n; idx++) {
                int i = order[idx];
                double z = 0;
                for (int j = 0; j < d; j++) z += w[j] * xs[i][j];
                double pred = sigmoid(z);
                double diff = pred - y[i];
                for (int j = 0; j < d; j++) g[j] += diff * xs[i][j];
            }
            // Update with L2
            for (int j = 0; j < d; j++) {
                w[j] -= learningRate * (g[j] / n + lambda * w[j]);
            }
        }
        return w;
    }

    /** Predict probability for a single feature vector. */
    public static double predictProbability(double[] w, double[] x) {
        double z = 0;
        for (int i = 0; i < w.length; i++) z += w[i] * x[i];
        return sigmoid(z);
    }
}
