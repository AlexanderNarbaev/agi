package io.matrix.consciousness;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * W97 — StabilityPhi: variance of Φ over sliding window (Ashby H-085).
 *
 * <p>Computes the rolling variance (or coefficient of variation) of an
 * integration-metric signal over a sliding window of recent values. This
 * captures Ashby's notion of ultrastability: when the system is stably
 * regulating its essential variables, Φ variance is low; when entering
 * the disruption phase, Φ variance spikes.
 *
 * <p>Used by ConsciousBrain's loop to detect when the brain is "settling"
 * (low variance) vs "exploring" (high variance). The downstream
 * ErrorDrivenLearner (DESIGN-64 §6) can use this signal to choose between
 * exploitation and exploration phases.
 *
 * <p>CONSTITUTION VI compliance: this is a measurement substrate for
 * stability, not a phenomenal consciousness claim.
 */
public final class StabilityPhi {

    private StabilityPhi() {}

    /**
     * Compute rolling variance of a sequence of Φ values over a fixed-size
     * window. Returns 0 if window has fewer than 2 elements.
     *
     * @param phiValues chronological sequence of integration values
     * @param windowSize number of recent values to consider
     * @return variance (≥ 0); 0 if window too small
     */
    public static double variance(java.util.List<Double> phiValues, int windowSize) {
        if (phiValues == null || phiValues.isEmpty() || windowSize < 2) return 0.0;
        int n = Math.min(phiValues.size(), windowSize);
        double mean = 0;
        int count = 0;
        for (int i = phiValues.size() - n; i < phiValues.size(); i++) {
            Double v = phiValues.get(i);
            if (v != null) { mean += v; count++; }
        }
        if (count == 0) return 0.0;
        mean /= count;
        double var = 0;
        for (int i = phiValues.size() - n; i < phiValues.size(); i++) {
            Double v = phiValues.get(i);
            if (v != null) var += (v - mean) * (v - mean);
        }
        return var / count;
    }

    /**
     * Coefficient of variation (CV = stddev / mean). Useful for measuring
     * relative variability. Returns 0 if mean is 0.
     */
    public static double coefficientOfVariation(java.util.List<Double> phiValues,
                                                   int windowSize) {
        double var = variance(phiValues, windowSize);
        if (var < 1e-12) return 0.0;
        double stddev = Math.sqrt(var);
        double mean = 0;
        int count = 0;
        for (int i = Math.max(0, phiValues.size() - windowSize); i < phiValues.size(); i++) {
            Double v = phiValues.get(i);
            if (v != null) { mean += v; count++; }
        }
        return count == 0 || mean == 0 ? 0.0 : stddev / mean;
    }

    /**
     * Detect ultrastable phase entry: true if recent variance is below
     * the configured threshold for a sustained period. Used as a signal
     * that the brain has converged to a stable configuration.
     *
     * @param phiValues chronological Φ history
     * @param windowSize sliding window
     * @param varianceThreshold variance must be below this to count as "stable"
     * @return true if recent variance < threshold
     */
    public static boolean isUltrastable(java.util.List<Double> phiValues,
                                          int windowSize, double varianceThreshold) {
        if (varianceThreshold < 0) {
            throw new IllegalArgumentException("varianceThreshold must be ≥ 0");
        }
        return variance(phiValues, windowSize) < varianceThreshold;
    }

    /**
     * Compute Ashby-style trend: mean first derivative of Φ over a sliding
     * window. Positive = integrating (state growing); negative = disintegrating.
     */
    public static double trend(java.util.List<Double> phiValues, int windowSize) {
        if (phiValues == null || phiValues.size() < 2 || windowSize < 2) return 0.0;
        int n = Math.min(phiValues.size() - 1, windowSize);
        double sumDerivatives = 0;
        int countDerivatives = 0;
        for (int i = Math.max(0, phiValues.size() - n - 1); i < phiValues.size() - 1; i++) {
            Double v1 = phiValues.get(i);
            Double v2 = phiValues.get(i + 1);
            if (v1 != null && v2 != null) {
                sumDerivatives += (v2 - v1);
                countDerivatives++;
            }
        }
        return countDerivatives == 0 ? 0.0 : sumDerivatives / countDerivatives;
    }
}
