package io.matrix.consciousness;

/**
 * W176 — Profile distance metric.
 *
 * <p>Compute distance between two CognitiveGenesisProfile instances
 * using different metrics:
 * - L1 (Manhattan) — sum of absolute differences
 * - L2 (Euclidean) — root sum of squared differences
 * - Cosine — 1 - cosine similarity
 * - Custom: weighted per-field importance
 *
 * <p>Used to compare cognitive states across time, agents, or
 * experimental conditions.
 *
 * <p>CONSTITUTION VI compliance: distance between cognitive state
 * descriptors, not phenomenal consciousness claim.
 */
public final class ProfileDistance {

    private ProfileDistance() {}

    /**
     * L1 (Manhattan) distance between two profiles.
     * Normalized to [0, 1] by dividing by 13 (number of fields).
     */
    public static double l1Distance(CognitiveGenesisProfile a, CognitiveGenesisProfile b) {
        if (a == null || b == null) return 0.0;
        double sum = 0;
        sum += Math.abs(a.phiBinary() - b.phiBinary());
        sum += Math.abs(a.phiF() - b.phiF());
        sum += Math.abs(a.phiR() - b.phiR());
        sum += Math.abs(a.phiLinGauss() - b.phiLinGauss());
        sum += Math.abs(a.interAgentPhi() - b.interAgentPhi());
        sum += Math.abs(a.stabilityPhi() - b.stabilityPhi());
        sum += Math.abs(a.crossLevelPhi() - b.crossLevelPhi());
        sum += Math.abs(a.kolmogorovK() / 100.0 - b.kolmogorovK() / 100.0);
        sum += Math.abs(a.analogicalSimilarity() - b.analogicalSimilarity());
        sum += Math.abs(a.conceptualExclusion() - b.conceptualExclusion());
        sum += Math.abs(a.nkEdgeOfChaosK() / 8.0 - b.nkEdgeOfChaosK() / 8.0);
        sum += Math.abs(a.memristorConductance() - b.memristorConductance());
        sum += Math.abs(a.lSystemComplexityRatio() / 5.0 - b.lSystemComplexityRatio() / 5.0);
        return sum / 13.0;
    }

    /**
     * L2 (Euclidean) distance between two profiles.
     */
    public static double l2Distance(CognitiveGenesisProfile a, CognitiveGenesisProfile b) {
        if (a == null || b == null) return 0.0;
        double sum = 0;
        sum += sqDiff(a.phiBinary(), b.phiBinary());
        sum += sqDiff(a.phiF(), b.phiF());
        sum += sqDiff(a.phiR(), b.phiR());
        sum += sqDiff(a.phiLinGauss(), b.phiLinGauss());
        sum += sqDiff(a.interAgentPhi(), b.interAgentPhi());
        sum += sqDiff(a.stabilityPhi(), b.stabilityPhi());
        sum += sqDiff(a.crossLevelPhi(), b.crossLevelPhi());
        sum += sqDiff(a.kolmogorovK() / 100.0, b.kolmogorovK() / 100.0);
        sum += sqDiff(a.analogicalSimilarity(), b.analogicalSimilarity());
        sum += sqDiff(a.conceptualExclusion(), b.conceptualExclusion());
        sum += sqDiff(a.nkEdgeOfChaosK() / 8.0, b.nkEdgeOfChaosK() / 8.0);
        sum += sqDiff(a.memristorConductance(), b.memristorConductance());
        sum += sqDiff(a.lSystemComplexityRatio() / 5.0, b.lSystemComplexityRatio() / 5.0);
        return Math.sqrt(sum / 13.0);
    }

    /**
     * Cosine distance: 1 - cosine similarity.
     * Returns [0, 2], typically [0, 1].
     */
    public static double cosineDistance(CognitiveGenesisProfile a, CognitiveGenesisProfile b) {
        if (a == null || b == null) return 0.0;
        double[] va = toVector(a);
        double[] vb = toVector(b);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < va.length; i++) {
            dot += va[i] * vb[i];
            na += va[i] * va[i];
            nb += vb[i] * vb[i];
        }
        if (na == 0 || nb == 0) return 1.0;
        return 1.0 - dot / Math.sqrt(na * nb);
    }

    /**
     * Composite distance: average of L1, L2, cosine.
     */
    public static double compositeDistance(CognitiveGenesisProfile a, CognitiveGenesisProfile b) {
        return (l1Distance(a, b) + l2Distance(a, b) + cosineDistance(a, b)) / 3.0;
    }

    private static double sqDiff(double x, double y) {
        double d = x - y;
        return d * d;
    }

    private static double[] toVector(CognitiveGenesisProfile p) {
        return new double[]{
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK() / 100.0,
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK() / 8.0, p.memristorConductance(),
            p.lSystemComplexityRatio() / 5.0
        };
    }
}
