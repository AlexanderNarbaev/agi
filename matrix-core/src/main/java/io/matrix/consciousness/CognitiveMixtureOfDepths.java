package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W250 — Cognitive Mixture-of-Depths (MoD).
 *
 * <p>Inspired by MoD (Raposo et al. 2024). Routes each cognitive
 * profile to a variable number of processing layers, skipping
 * unnecessary computation.
 *
 * <p>Each profile gets a "depth score" determining how many layers
 * to apply. Top-K% get full depth, others get 0 (skip).
 *
 * <p>Benefits: 2-4x compute savings on average.
 *
 * <p>CONSTITUTION VI compliance: depth-routed cognitive processing,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveMixtureOfDepths {

    private CognitiveMixtureOfDepths() {}

    /** Per-profile depth decision. */
    public record DepthDecision(
        CognitiveGenesisProfile profile,
        int depth,
        boolean skip
    ) {}

    /**
     * Compute depth for each profile in sequence.
     *
     * @param profiles list of profiles
     * @param topKPercent percentage (0-100) that gets full depth
     * @param maxDepth max layers to apply
     * @param seed RNG seed
     * @return depth decisions per profile
     */
    public static List<DepthDecision> routeDepth(List<CognitiveGenesisProfile> profiles,
                                                   double topKPercent,
                                                   int maxDepth,
                                                   long seed) {
        if (profiles == null || profiles.isEmpty()) return new java.util.ArrayList<>();
        if (topKPercent < 0 || topKPercent > 100) topKPercent = 50;
        // Compute depth scores for each profile
        Random rng = new Random(seed);
        int n = profiles.size();
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            scores[i] = profiles.get(i).unifiedComplexityScore() + rng.nextGaussian() * 0.01;
        }
        // Determine threshold for top K%
        double[] sorted = scores.clone();
        java.util.Arrays.sort(sorted);
        int thresholdIdx = Math.max(0, (int) (n * (1 - topKPercent / 100.0)));
        double threshold = sorted[thresholdIdx];
        java.util.List<DepthDecision> decisions = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean skip = scores[i] < threshold;
            int depth = skip ? 0 : maxDepth;
            decisions.add(new DepthDecision(profiles.get(i), depth, skip));
        }
        return decisions;
    }

    /**
     * Compute average compute savings.
     */
    public static double computeSavings(List<DepthDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) return 0.0;
        int skipped = 0;
        for (DepthDecision d : decisions) {
            if (d.skip()) skipped++;
        }
        return (double) skipped / decisions.size();
    }
}
