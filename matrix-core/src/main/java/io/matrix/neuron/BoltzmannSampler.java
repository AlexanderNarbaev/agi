package io.matrix.neuron;

import java.util.Random;

/**
 * DESIGN-32 — Boltzmann Machine (stochastic Hopfield with temperature).
 * Seeded RNG for CONSTITUTION I compliance.
 */
public final class BoltzmannSampler {

    public static final double DEFAULT_TEMP = 1.0;
    public static final int DEFAULT_BURN_IN = 100;

    private BoltzmannSampler() {}

    /**
     * Run a single Gibbs sampling step: P(s_i=1) = sigmoid(Σ w_ij s_j / T).
     * Returns new state with one bit updated.
     */
    public static boolean[] gibbsStep(boolean[] state, double[][] weights,
                                     double temperature, Random rng) {
        if (state == null || weights == null) {
            throw new IllegalArgumentException("null");
        }
        if (temperature <= 0) {
            throw new IllegalArgumentException("T > 0");
        }
        int n = state.length;
        boolean[] newState = state.clone();
        int i = rng.nextInt(n);
        double sum = 0;
        for (int j = 0; j < n; j++) {
            sum += weights[i][j] * (state[j] ? 1.0 : 0.0);
        }
        double prob = 1.0 / (1.0 + Math.exp(-sum / temperature));
        newState[i] = rng.nextDouble() < prob;
        return newState;
    }

    /** Run burn-in + collect final state. */
    public static boolean[] sample(boolean[] init, double[][] weights,
                                  double temperature, long seed,
                                  int burnIn, int totalIters) {
        Random rng = new Random(seed);
        boolean[] state = init.clone();
        for (int i = 0; i < burnIn; i++) {
            state = gibbsStep(state, weights, temperature, rng);
        }
        for (int i = 0; i < totalIters - burnIn; i++) {
            state = gibbsStep(state, weights, temperature, rng);
        }
        return state;
    }

    public static boolean[] sample(boolean[] init, double[][] weights, long seed) {
        return sample(init, weights, DEFAULT_TEMP, seed,
                DEFAULT_BURN_IN, DEFAULT_BURN_IN * 2);
    }
}
