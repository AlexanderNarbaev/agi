package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 419 — Thompson Sampling multi-armed bandit (independent-Gaussian prior).
 * <p>Bayesian alternative to UCB: each arm is modelled as {@code N(mu, sigma^2/n)}
 * and we sample one draw from each posterior, then pick the max.
 * <p>Pure function: receives {@link Random}. CONSTITUTION I-safe.
 */
public final class ThompsonBandit {

    private ThompsonBandit() {}

    /**
     * Result of one Thompson-sampling draw.
     * @param chosen   index of arm with the highest sampled mean
     * @param samples  sampled mean for each arm
     */
    public record Step(int chosen, double[] samples) {}

    /**
     * @param empiricalMeans empirical mean reward of each arm (size k)
     * @param empiricalVariance variance of each arm (size k); 0 ⇒ use prior
     * @param counts    pull counts per arm; prior weight = 1 each
     * @param priorMu   prior mean (default 0)
     * @param priorTau2 prior variance (default 1, scaled by count factor)
     * @param rng       deterministic RNG
     */
    public static Step choose(double[] empiricalMeans, double[] empiricalVariance,
                              long[] counts, double priorMu, double priorTau2, Random rng) {
        int k = empiricalMeans.length;
        if (empiricalVariance.length != k || counts.length != k) {
            throw new IllegalArgumentException("length mismatch");
        }

        double[] samples = new double[k];
        int chosen = 0;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            long n = counts[i];
            double tau2 = priorTau2;
            double mu;
            if (n == 0) {
                // Sample from prior
                mu = priorMu + rng.nextGaussian() * Math.sqrt(tau2);
            } else {
                // Posterior mean: (n * mean / sigma2 + priorMu / tau2) / (n / sigma2 + 1 / tau2)
                double sampleVar = Math.max(empiricalVariance[i] / n, 1e-12);
                double postMean = (empiricalMeans[i] * n / sampleVar + priorMu / tau2)
                        / (n / sampleVar + 1.0 / tau2);
                double postVar = 1.0 / (n / sampleVar + 1.0 / tau2);
                mu = postMean + rng.nextGaussian() * Math.sqrt(postVar);
            }
            samples[i] = mu;
            if (mu > best) { best = mu; chosen = i; }
        }
        return new Step(chosen, samples);
    }
}
