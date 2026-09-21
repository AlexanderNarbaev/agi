package io.matrix.consciousness;

import java.util.List;

/**
 * W155 — Cognitive entropy meter.
 *
 * <p>Computes various entropy measures over a sequence of cognitive
 * state descriptors. Useful for tracking the "disorder" or
 * "information content" of cognitive dynamics over time.
 *
 * <p>Provides:
 * - regimeEntropy: Shannon entropy of regime labels
 * - profileEntropy: Shannon entropy of unified complexity score distribution
 * - regimeChangeRate: rate of regime transitions
 *
 * <p>CONSTITUTION VI compliance: entropy measurement of cognitive
 * state descriptors, not phenomenal consciousness claim.
 */
public final class CognitiveEntropyMeter {

    private CognitiveEntropyMeter() {}

    /**
     * Compute Shannon entropy of regime labels in profile sequence.
     * Returns log(3) ≈ 1.099 when regimes are uniformly distributed.
     */
    public static double regimeEntropy(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return 0.0;
        int frozen = 0, edge = 0, chaotic = 0;
        for (CognitiveGenesisProfile p : profiles) {
            String r = p.regime();
            if (r.equals("FROZEN")) frozen++;
            else if (r.equals("EDGE_OF_CHAOS")) edge++;
            else if (r.equals("CHAOTIC")) chaotic++;
        }
        int total = frozen + edge + chaotic;
        if (total == 0) return 0.0;
        double h = 0;
        if (frozen > 0) h -= ((double) frozen / total) * Math.log((double) frozen / total);
        if (edge > 0) h -= ((double) edge / total) * Math.log((double) edge / total);
        if (chaotic > 0) h -= ((double) chaotic / total) * Math.log((double) chaotic / total);
        return h;
    }

    /**
     * Compute entropy of unified complexity scores (binned into 10 bins).
     */
    public static double profileEntropy(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return 0.0;
        int[] bins = new int[10];
        for (CognitiveGenesisProfile p : profiles) {
            double score = p.unifiedComplexityScore();
            int bin = (int) (score * 10);
            if (bin >= 10) bin = 9;
            if (bin < 0) bin = 0;
            bins[bin]++;
        }
        int total = profiles.size();
        double h = 0;
        for (int b : bins) {
            if (b > 0) {
                double p = (double) b / total;
                h -= p * Math.log(p);
            }
        }
        return h;
    }

    /**
     * Compute max-entropy reference (log of number of categories).
     * Returns log(3) ≈ 1.099 for 3 regimes.
     */
    public static double maxEntropy() {
        return Math.log(3);
    }

    /**
     * Compute normalized entropy in [0, 1].
     */
    public static double normalizedRegimeEntropy(List<CognitiveGenesisProfile> profiles) {
        double h = regimeEntropy(profiles);
        double max = maxEntropy();
        if (max == 0) return 0.0;
        return h / max;
    }
}
