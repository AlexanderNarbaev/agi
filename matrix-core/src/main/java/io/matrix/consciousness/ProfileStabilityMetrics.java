package io.matrix.consciousness;

import java.util.List;

/**
 * W170 — Profile stability metrics.
 *
 * <p>Compute various stability measures for a sequence of cognitive
 * profile descriptors:
 * - stationary: low variance in profile fields
 * - oscillatory: alternating high/low values
 * - drifting: trend in profile fields
 * - chaotic: high velocity with no clear pattern
 *
 * <p>Combines ProfileVelocityTracker, CognitivePhaseDetector, and
 * CognitiveEntropyMeter to give a holistic stability assessment.
 *
 * <p>CONSTITUTION VI compliance: stability classification,
 * not phenomenal consciousness claim.
 */
public final class ProfileStabilityMetrics {

    private ProfileStabilityMetrics() {}

    /**
     * Classify profile sequence stability:
     * - STATIONARY: low velocity, no regime changes
     * - OSCILLATORY: regimes alternate, moderate velocity
     * - DRIFTING: directional change in unified score
     * - CHAOTIC: high velocity, no clear pattern
     */
    public static String classify(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.size() < 3) return "INSUFFICIENT_DATA";

        double meanV = ProfileVelocityTracker.meanVelocity(profiles);
        double transitionRate = CognitivePhaseDetector.transitionRate(profiles);
        double entropy = CognitiveEntropyMeter.normalizedRegimeEntropy(profiles);

        if (meanV < 0.05 && transitionRate < 0.1) {
            return "STATIONARY";
        }
        if (transitionRate > 0.5 && entropy > 0.7) {
            return "CHAOTIC";
        }
        if (transitionRate > 0.2 && entropy > 0.4) {
            return "OSCILLATORY";
        }
        // Check for drift (linear trend in unified score)
        if (hasDrift(profiles)) {
            return "DRIFTING";
        }
        return "STATIONARY";
    }

    /**
     * Check if profiles show a directional trend (linear regression slope).
     */
    private static boolean hasDrift(List<CognitiveGenesisProfile> profiles) {
        if (profiles.size() < 5) return false;
        double[] y = new double[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            y[i] = profiles.get(i).unifiedComplexityScore();
        }
        // Simple slope estimation: (last - first) / (n - 1)
        double slope = (y[y.length - 1] - y[0]) / (y.length - 1);
        // Significant if |slope| > 0.01
        return Math.abs(slope) > 0.01;
    }

    /**
     * Return stability score in [0, 1]: 1 = perfectly stable, 0 = chaotic.
     * Computed as: 1 - min(meanVelocity / 1.0, 1.0).
     */
    public static double stabilityScore(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.size() < 2) return 1.0;
        double meanV = ProfileVelocityTracker.meanVelocity(profiles);
        return Math.max(0.0, 1.0 - Math.min(1.0, meanV));
    }
}
