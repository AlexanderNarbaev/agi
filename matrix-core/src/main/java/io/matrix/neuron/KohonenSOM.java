package io.matrix.neuron;

import java.util.Random;

/**
 * DESIGN-33 — Kohonen Self-Organizing Map.
 * Topological feature map with winner-take-all + lateral neighborhood.
 * Pure functions (CONSTITUTION I with seeded RNG).
 */
public final class KohonenSOM {

    public static final double DEFAULT_LEARNING_RATE = 0.1;
    public static final double DEFAULT_RADIUS = 1.0;

    private KohonenSOM() {}

    /**
     * Find best-matching unit (BMU) — index of neuron with smallest
     * Euclidean distance to input.
     */
    public static int findBMU(double[][] som, double[] input) {
        if (som == null || input == null) {
            throw new IllegalArgumentException("null");
        }
        int bestIdx = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < som.length; i++) {
            double d = euclideanDistance(som[i], input);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Single training step: find BMU, update BMU + neighbors
     * (Gaussian neighborhood with given radius).
     */
    public static void trainStep(double[][] som, double[] input,
                                 double learningRate, double radius,
                                 int somWidth) {
        if (som == null || input == null) {
            throw new IllegalArgumentException("null");
        }
        int bmu = findBMU(som, input);
        int bmuX = bmu % somWidth;
        int bmuY = bmu / somWidth;
        for (int i = 0; i < som.length; i++) {
            int x = i % somWidth;
            int y = i / somWidth;
            double dx = x - bmuX, dy = y - bmuY;
            double d2 = dx * dx + dy * dy;
            double neighborhood = Math.exp(-d2 / (2 * radius * radius));
            double delta = learningRate * neighborhood;
            for (int k = 0; k < som[i].length; k++) {
                som[i][k] += delta * (input[k] - som[i][k]);
            }
        }
    }

    /** Train for N epochs. Mutates som in place. */
    public static double[][] train(double[][] som, double[][] inputs,
                                  int epochs, double initialLearningRate,
                                  int somWidth, long seed) {
        Random rng = new Random(seed);
        for (int epoch = 0; epoch < epochs; epoch++) {
            double lr = initialLearningRate * (1.0 - (double) epoch / epochs);
            double radius = Math.max(0.5, DEFAULT_RADIUS
                    * (1.0 - (double) epoch / epochs));
            for (double[] input : inputs) {
                trainStep(som, input, lr, radius, somWidth);
            }
            // Deterministic (no randomness in step itself); rng unused but
            // available for future stochastic variants
            rng.nextBoolean();
        }
        return som;
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
