package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * W159 — Inferential distance: complexity of inferring one
 * distribution from another.
 *
 * <p>Provides several measures of inferential distance between two
 * categorical distributions:
 * - KL divergence (forward / asymmetric)
 * - Jensen-Shannon divergence (symmetric, bounded)
 * - Hellinger distance (bounded, related to L2 norm)
 * - Total variation distance (L1, bounded)
 *
 * <p>Used to measure how different two cognitive distributions are,
 * which is a measure of "processing difficulty".
 *
 * <p>CONSTITUTION VI compliance: measure of inferential complexity,
 * not phenomenal consciousness claim.
 */
public final class InferentialDistance {

    private InferentialDistance() {}

    /**
     * KL divergence: D_KL(P || Q).
     * Returns +infinity if any P[i] > 0 and Q[i] == 0.
     */
    public static double klDivergence(double[] p, double[] q) {
        if (p == null || q == null) return 0.0;
        if (p.length != q.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        double kl = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0) {
                if (q[i] == 0) return Double.POSITIVE_INFINITY;
                kl += p[i] * Math.log(p[i] / q[i]);
            }
        }
        return kl / Math.log(2);  // log base 2
    }

    /**
     * Jensen-Shannon divergence (symmetric, bounded [0, 1]).
     * JS(P || Q) = 0.5 * KL(P || M) + 0.5 * KL(Q || M), where M = (P+Q)/2.
     */
    public static double jsDivergence(double[] p, double[] q) {
        if (p == null || q == null) return 0.0;
        if (p.length != q.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        double[] m = new double[p.length];
        for (int i = 0; i < p.length; i++) m[i] = (p[i] + q[i]) / 2;
        return 0.5 * klDivergence(p, m) + 0.5 * klDivergence(q, m);
    }

    /**
     * Hellinger distance: H(P, Q) = sqrt(0.5 * sum((sqrt(P_i) - sqrt(Q_i))^2))
     * Bounded in [0, 1].
     */
    public static double hellingerDistance(double[] p, double[] q) {
        if (p == null || q == null) return 0.0;
        if (p.length != q.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        double sum = 0;
        for (int i = 0; i < p.length; i++) {
            double diff = Math.sqrt(Math.max(0, p[i])) - Math.sqrt(Math.max(0, q[i]));
            sum += diff * diff;
        }
        return Math.sqrt(0.5 * sum);
    }

    /**
     * Total variation distance: 0.5 * sum(|P_i - Q_i|).
     * Bounded in [0, 1].
     */
    public static double totalVariationDistance(double[] p, double[] q) {
        if (p == null || q == null) return 0.0;
        if (p.length != q.length) {
            throw new IllegalArgumentException("Lengths must match");
        }
        double sum = 0;
        for (int i = 0; i < p.length; i++) {
            sum += Math.abs(p[i] - q[i]);
        }
        return 0.5 * sum;
    }

    /**
     * Composite distance: average of all 4 measures (after normalization).
     * Returns [0, 1].
     */
    public static double compositeDistance(double[] p, double[] q) {
        double kl = klDivergence(p, q);
        if (Double.isInfinite(kl)) return 1.0;
        // Normalize KL by log2(N) which is max possible for uniform distributions
        double maxKL = Math.log(p.length) / Math.log(2);
        double normKL = maxKL > 0 ? Math.min(1.0, kl / maxKL) : 0.0;
        double js = jsDivergence(p, q);  // already in [0, 1] (log2 base)
        // JS in log2 base max is log2(2) = 1
        double hellinger = hellingerDistance(p, q);
        double tv = totalVariationDistance(p, q);
        return (normKL + js + hellinger + tv) / 4.0;
    }
}
