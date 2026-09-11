package io.matrix.neuron;

import java.util.Random;

/**
 * DESIGN-34 — Thompson Sampling for Bayesian bandit / chain selection.
 * Pure function (CONSTITUTION I) — seeded RNG.
 */
public final class ThompsonSampler {

    private ThompsonSampler() {}

    /**
     * Sample one arm according to Thompson sampling: argmax_i Beta(α_i, β_i).
     * Pure function given seed.
     */
    public static int sample(int[] successes, int[] failures, long seed) {
        if (successes == null || failures == null) {
            throw new IllegalArgumentException("null");
        }
        if (successes.length != failures.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        if (successes.length == 0) {
            throw new IllegalArgumentException("empty");
        }
        Random rng = new Random(seed);
        double bestSample = -1.0;
        int bestIdx = 0;
        for (int i = 0; i < successes.length; i++) {
            // Sample from Beta(α, β) using Gamma trick
            double x = sampleGamma(successes[i] + 1, rng);
            double y = sampleGamma(failures[i] + 1, rng);
            double beta = x / (x + y);
            if (beta > bestSample) {
                bestSample = beta;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** Update counts based on reward. */
    public static int[] updateCounts(int[] counts, int chosen, double reward) {
        int[] updated = counts.clone();
        if (chosen >= 0 && chosen < updated.length) {
            if (reward >= 0.5) updated[chosen]++;
            // else: failures++
        }
        return updated;
    }

    /** Marsaglia & Tsang gamma sampling. */
    private static double sampleGamma(int shape, Random rng) {
        if (shape < 1) return shape * sampleGamma(shape + 1, rng);
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9 * d);
        while (true) {
            double x, v;
            do {
                x = rng.nextGaussian();
                v = 1 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = rng.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x) return d * v;
            if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v;
        }
    }
}
