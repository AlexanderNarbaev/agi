package io.matrix.consciousness;

import java.util.List;

/**
 * W98 — CrossLevelPhi: integration between adjacent Bernstein levels (H-086).
 *
 * <p>Bernstein's "levels of construction" hierarchy (DESIGN-58 L0-L7) means
 * that integration at one level cannot be reduced to another. This class
 * measures how well two adjacent levels (L_k and L_{k+1}) coordinate their
 * state representations.
 *
 * <p>For each pair of adjacent levels, we treat each as a separate
 * "sub-system" and measure Φ over their joint state. High cross-level Φ
 * means the lower level's output is informative for the higher level's
 * state — coordination is good. Low cross-level Φ means the levels are
 * running independently (or the higher level is ignoring the lower).
 *
 * <p>CONSTITUTION VI compliance: this is an inter-level measurement
 * substrate, not a phenomenal consciousness claim.
 */
public final class CrossLevelPhi {

    private CrossLevelPhi() {}

    /**
     * Measure cross-level integration between two state sequences from
     * adjacent Bernstein levels.
     *
     * @param lowerLevelStates T × D_lower state matrix from level k
     * @param upperLevelStates T × D_upper state matrix from level k+1
     * @return Φ value ≥ 0 (closed-form linear-Gaussian)
     */
    public static double measure(double[][] lowerLevelStates,
                                   double[][] upperLevelStates) {
        if (lowerLevelStates == null || upperLevelStates == null) {
            throw new IllegalArgumentException("state matrices must be non-null");
        }
        if (lowerLevelStates.length != upperLevelStates.length) {
            throw new IllegalArgumentException("time dimensions must match");
        }
        if (lowerLevelStates.length < 2) {
            throw new IllegalArgumentException("need ≥ 2 timesteps");
        }
        int T = lowerLevelStates.length;
        int Dlower = lowerLevelStates[0].length;
        int Dupper = upperLevelStates[0].length;
        // Concatenate into a single (T × (Dlower+Dupper)) matrix
        int N = Dlower + Dupper;
        double[][] joint = new double[T][N];
        for (int t = 0; t < T; t++) {
            System.arraycopy(lowerLevelStates[t], 0, joint[t], 0, Dlower);
            System.arraycopy(upperLevelStates[t], 0, joint[t], Dlower, Dupper);
        }
        // Compute Φ_linGauss over the joint state
        return IntegrationMetrics.phiLinGaussFromSamples(joint, Math.min(N, 16));
    }

    /**
     * Measure cross-level Φ between HdcBrain recall (lower) and ConsciousBrain
     * CycleReport metric vector (upper). Returns a bounded score.
     *
     * @param hdcSimilarities list of HdcBrain.forward().similarity values
     * @param hdcDistances list of HdcBrain.forward().distance values
     * @param brainMetrics list of CycleReport scalar metrics (e.g., phi, cN)
     * @return integration value ≥ 0
     */
    public static double brainToMetrics(List<Double> hdcSimilarities,
                                          List<Double> hdcDistances,
                                          List<Double> brainMetrics) {
        if (hdcSimilarities == null || hdcDistances == null || brainMetrics == null) {
            throw new IllegalArgumentException("input lists must be non-null");
        }
        int T = Math.min(Math.min(hdcSimilarities.size(), hdcDistances.size()),
                brainMetrics.size());
        if (T < 2) {
            throw new IllegalArgumentException("need ≥ 2 timesteps");
        }
        // Build [hdcSimilarity, hdcDistance, brainMetric] matrix
        double[][] samples = new double[T][3];
        for (int t = 0; t < T; t++) {
            samples[t][0] = hdcSimilarities.get(t);
            samples[t][1] = hdcDistances.get(t);
            samples[t][2] = brainMetrics.get(t);
        }
        return IntegrationMetrics.phiLinGaussFromSamples(samples, 3);
    }
}
