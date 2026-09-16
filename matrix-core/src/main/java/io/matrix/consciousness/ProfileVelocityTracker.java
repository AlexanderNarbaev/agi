package io.matrix.consciousness;

import java.util.List;

/**
 * W168 — Profile velocity tracker.
 *
 * <p>Measures how fast a cognitive profile is changing across
 * consecutive cycles. Useful for detecting:
 * - Stationary periods (low velocity)
 * - Rapid transitions (high velocity)
 * - Regime shifts (velocity spike)
 *
 * <p>Provides:
 * - velocity(profiles): per-cycle magnitude of profile change
 * - maxVelocity(profiles): peak velocity
 * - isRapidlyChanging(profiles, threshold): boolean check
 *
 * <p>CONSTITUTION VI compliance: rate of change of cognitive
 * state descriptors, not phenomenal consciousness claim.
 */
public final class ProfileVelocityTracker {

    private ProfileVelocityTracker() {}

    /**
     * Compute per-cycle velocity (Euclidean distance between
     * consecutive profiles).
     */
    public static double[] velocity(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.size() < 2) return new double[0];
        double[] v = new double[profiles.size() - 1];
        for (int i = 1; i < profiles.size(); i++) {
            v[i - 1] = euclideanDistance(profiles.get(i - 1), profiles.get(i));
        }
        return v;
    }

    /**
     * Return maximum velocity across profile sequence.
     */
    public static double maxVelocity(List<CognitiveGenesisProfile> profiles) {
        double[] v = velocity(profiles);
        if (v.length == 0) return 0.0;
        double max = v[0];
        for (int i = 1; i < v.length; i++) if (v[i] > max) max = v[i];
        return max;
    }

    /**
     * Return mean velocity.
     */
    public static double meanVelocity(List<CognitiveGenesisProfile> profiles) {
        double[] v = velocity(profiles);
        if (v.length == 0) return 0.0;
        double sum = 0;
        for (double vi : v) sum += vi;
        return sum / v.length;
    }

    /**
     * Check if profiles are rapidly changing (max velocity > threshold).
     */
    public static boolean isRapidlyChanging(List<CognitiveGenesisProfile> profiles, double threshold) {
        return maxVelocity(profiles) > threshold;
    }

    /**
     * Compute Euclidean distance between two profile fields (excluding
     * Kolmogorov K and L-system ratio which can be unbounded).
     */
    private static double euclideanDistance(CognitiveGenesisProfile a, CognitiveGenesisProfile b) {
        double sum = 0;
        sum += sq(a.phiBinary(), b.phiBinary());
        sum += sq(a.phiF(), b.phiF());
        sum += sq(a.phiR(), b.phiR());
        sum += sq(a.phiLinGauss(), b.phiLinGauss());
        sum += sq(a.interAgentPhi(), b.interAgentPhi());
        sum += sq(a.stabilityPhi(), b.stabilityPhi());
        sum += sq(a.crossLevelPhi(), b.crossLevelPhi());
        sum += sq(a.analogicalSimilarity(), b.analogicalSimilarity());
        sum += sq(a.conceptualExclusion(), b.conceptualExclusion());
        // memristorConductance is bounded
        sum += sq(a.memristorConductance(), b.memristorConductance());
        // NK edge K (small int)
        double nkDiff = a.nkEdgeOfChaosK() - b.nkEdgeOfChaosK();
        sum += nkDiff * nkDiff / 64.0;  // normalize
        return Math.sqrt(sum);
    }

    private static double sq(double a, double b) {
        double d = a - b;
        return d * d;
    }
}
