package io.matrix.consciousness;

/**
 * W192 — Entropy decomposition.
 *
 * <p>Decompose Shannon entropy of a probability distribution into
 * components (entropy of marginals vs joint):
 * - Total entropy H(X)
 * - Marginal entropy H(X_i)
 * - Mutual information I(X_i; X_j)
 * - Conditional entropy H(X_i | X_j)
 *
 * <p>Used for understanding information structure in cognitive
 * profile sequences.
 *
 * <p>CONSTITUTION VI compliance: entropy decomposition of
 * measurement substrate, not phenomenal consciousness claim.
 */
public final class EntropyDecomposition {

    private EntropyDecomposition() {}

    /** Shannon entropy of a probability distribution (log base 2). */
    public static double entropy(double[] p) {
        if (p == null || p.length == 0) return 0.0;
        double sum = 0;
        for (double pi : p) sum += pi;
        if (sum == 0) return 0.0;
        double h = 0;
        for (double pi : p) {
            if (pi > 0) {
                double normalized = pi / sum;
                h -= normalized * Math.log(normalized);
            }
        }
        return h / Math.log(2);
    }

    /** Joint entropy of 2D distribution. */
    public static double jointEntropy(double[][] p) {
        if (p == null || p.length == 0 || p[0].length == 0) return 0.0;
        double sum = 0;
        for (double[] row : p) for (double v : row) sum += v;
        if (sum == 0) return 0.0;
        double h = 0;
        for (double[] row : p) {
            for (double v : row) {
                if (v > 0) {
                    double normalized = v / sum;
                    h -= normalized * Math.log(normalized);
                }
            }
        }
        return h / Math.log(2);
    }

    /**
     * Mutual information between two discrete distributions.
     * I(X; Y) = H(X) + H(Y) - H(X, Y).
     */
    public static double mutualInformation(double[] p, double[] q, double[][] joint) {
        double hX = entropy(p);
        double hY = entropy(q);
        double hXY = jointEntropy(joint);
        return hX + hY - hXY;
    }

    /**
     * Conditional entropy H(X | Y).
     */
    public static double conditionalEntropy(double[] p, double[][] joint) {
        double hXY = jointEntropy(joint);
        double hY = entropy(marginalY(joint));
        return hXY - hY;
    }

    /** Marginal distribution over rows (sum over columns). */
    public static double[] marginalX(double[][] joint) {
        if (joint == null || joint.length == 0) return new double[0];
        double[] marg = new double[joint.length];
        for (int i = 0; i < joint.length; i++) {
            for (int j = 0; j < joint[i].length; j++) {
                marg[i] += joint[i][j];
            }
        }
        return marg;
    }

    /** Marginal distribution over columns (sum over rows). */
    public static double[] marginalY(double[][] joint) {
        if (joint == null || joint.length == 0 || joint[0].length == 0) return new double[0];
        int n = joint[0].length;
        double[] marg = new double[n];
        for (int i = 0; i < joint.length; i++) {
            for (int j = 0; j < joint[i].length; j++) {
                marg[j] += joint[i][j];
            }
        }
        return marg;
    }

    /**
     * Decompose joint entropy into components:
     * - H(X, Y) = H(X) + H(Y | X)
     * - H(X, Y) = H(Y) + H(X | Y)
     * - H(X, Y) = H(X) + H(Y) - I(X; Y)
     */
    public static EntropyComponents decompose(double[] p, double[] q, double[][] joint) {
        double hX = entropy(p);
        double hY = entropy(q);
        double hXY = jointEntropy(joint);
        double mi = hX + hY - hXY;
        double hXgivenY = hXY - hY;
        double hYgivenX = hXY - hX;
        return new EntropyComponents(hX, hY, hXY, mi, hXgivenY, hYgivenX);
    }

    public record EntropyComponents(
            double hX, double hY, double hXY,
            double mutualInformation,
            double hXgivenY, double hYgivenX) {}
}
