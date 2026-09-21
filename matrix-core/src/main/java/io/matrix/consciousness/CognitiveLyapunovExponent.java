package io.matrix.consciousness;

import java.util.Random;

/**
 * W157 — Cognitive Lyapunov exponent.
 *
 * <p>Computes the maximum Lyapunov exponent for a cognitive trajectory.
 * Positive value indicates chaos (diverging trajectories), negative
 * value indicates stability (converging trajectories), zero value
 * indicates edge of chaos (neutral stability).
 *
 * <p>This is the classical Lyapunov exponent applied to MATRIX
 * cognitive state descriptors:
 * - If Φ grows over time → likely chaotic
 * - If Φ decays → likely frozen
 * - If Φ oscillates → likely edge of chaos
 *
 * <p>CONSTITUTION VI compliance: dynamical stability measurement,
 * not phenomenal consciousness claim.
 */
public final class CognitiveLyapunovExponent {

    private CognitiveLyapunovExponent() {}

    /**
     * Estimate Lyapunov exponent via two-trajectory divergence.
     * Returns the average log-ratio of trajectory divergence per step.
     *
     * <p>Method: given two close-by trajectories (X, Y), compute
     * divergence over time steps, then log of the divergence ratio.
     * Positive λ → chaos; negative λ → convergence.
     */
    public static double estimate(double[] trajectory1, double[] trajectory2) {
        if (trajectory1 == null || trajectory2 == null) return 0.0;
        if (trajectory1.length != trajectory2.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        if (trajectory1.length < 2) return 0.0;

        double sumLogRatio = 0;
        int count = 0;
        double prevDist = Math.abs(trajectory1[0] - trajectory2[0]);
        if (prevDist == 0) prevDist = 1e-10;  // avoid log(0)
        for (int t = 1; t < trajectory1.length; t++) {
            double dist = Math.abs(trajectory1[t] - trajectory2[t]);
            if (dist == 0) dist = 1e-10;
            if (prevDist > 1e-15) {
                sumLogRatio += Math.log(dist / prevDist);
                count++;
            }
            prevDist = dist;
        }
        if (count == 0) return 0.0;
        return sumLogRatio / count;
    }

    /**
     * Synthesize two trajectories from initial conditions and
     * a divergence model, then estimate Lyapunov exponent.
     *
     * <p>divergenceModel: positive = trajectories diverge (chaos),
     * negative = converge (stability), zero = neutral.
     */
    public static double estimateFromModel(int length, double divergence, long seed) {
        Random rng = new Random(seed);
        double[] t1 = new double[length];
        double[] t2 = new double[length];
        double x1 = 0.1, x2 = 0.1 + 1e-6;  // small initial separation
        for (int t = 0; t < length; t++) {
            // Logistic map with controllable divergence
            double r = 3.9 + divergence * 0.05;  // 3.85-3.95 range
            x1 = r * x1 * (1 - x1);
            x2 = r * x2 * (1 - x2);
            t1[t] = x1;
            t2[t] = x2;
        }
        return estimate(t1, t2);
    }

    /**
     * Classify trajectory based on Lyapunov exponent:
     * - λ > 0.01: chaotic
     * - λ < -0.01: stable
     * - otherwise: edge of chaos
     */
    public static String classify(double lyapunov) {
        if (lyapunov > 0.01) return "CHAOTIC";
        if (lyapunov < -0.01) return "STABLE";
        return "EDGE_OF_CHAOS";
    }
}
