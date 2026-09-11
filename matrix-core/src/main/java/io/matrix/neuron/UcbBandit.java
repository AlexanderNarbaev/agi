package io.matrix.neuron;

import java.util.Arrays;
import java.util.Random;

/**
 * RUN 419 — UCB1 (Upper Confidence Bound) multi-armed bandit.
 * <p>Common-policy band-aid for explore/exploit trade-off in
 * reinforcement-learning exploration phases. Picks the arm with the
 * highest UCB score:
 * <pre>ucb_i = mean_i + sqrt(2 ln N / n_i)</pre>
 * where {@code N} = total pulls so far, {@code n_i} = arm-i pulls so far.
 * <p>Pure function: receives a {@link Random} argument. CONSTITUTION I-safe.
 */
public final class UcbBandit {

    private UcbBandit() {}

    /**
     * Result of one iteration.
     * @param chosen   index of arm chosen by UCB rule
     * @param ucbScores score for every arm (for inspection)
     * @param exploitsChosen true iff the chosen arm is a current best-mean
     */
    public record Step(int chosen, double[] ucbScores, boolean exploitsChosen) {}

    /**
     * @param means     empirical mean reward of each arm (size k, may be 0 for never-tried)
     * @param counts    pull count of each arm (size k)
     * @param totalPulls total pulls sum of counts; computed if {@code <0}
     * @param c         exploration coefficient (sqrt(2 ln N / n_i) factor; default 2.0)
     * @param rng       deterministic RNG to break ties
     */
    public static Step choose(double[] means, long[] counts, long totalPulls, double c, Random rng) {
        int k = means.length;
        if (counts.length != k) throw new IllegalArgumentException("counts.length");
        long total = totalPulls < 0 ? Arrays.stream(counts).sum() : totalPulls;

        // Find any arm that hasn't been tried yet — round-robin try-all.
        for (int i = 0; i < k; i++) {
            if (counts[i] == 0) {
                double[] zeros = new double[k];
                return new Step(i, zeros, false);
            }
        }

        double logTotal = total == 0 ? 0.0 : Math.log(total);
        double[] scores = new double[k];
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean anyBest = false;
        int chosen = 0;
        boolean exploit = false;
        for (int i = 0; i < k; i++) {
            double bonus = Math.sqrt(c * logTotal / counts[i]);
            scores[i] = means[i] + bonus;
            // tie-break deterministically via RNG
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                chosen = i;
                anyBest = true;
                exploit = (bonus < 1e-12); // pure exploitation branch
            } else if (scores[i] == bestScore && anyBest && rng.nextBoolean()) {
                chosen = i;
            }
        }
        return new Step(chosen, scores, exploit);
    }
}
