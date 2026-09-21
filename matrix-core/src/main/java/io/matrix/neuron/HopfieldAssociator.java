package io.matrix.neuron;

/**
 * DESIGN-31 — Hopfield auto-associative memory.
 * Pure function (CONSTITUTION I).
 */
public final class HopfieldAssociator {

    public static final int DEFAULT_MAX_ITER = 20;

    private HopfieldAssociator() {}

    /**
     * Iteratively update state to minimize energy
     * E = -0.5 * Σ w_ij s_i s_j (where s_i ∈ {-1, +1}).
     * Returns the attractor state.
     */
    public static boolean[] associate(boolean[] input, double[][] weights,
                                     int maxIter) {
        if (input == null) throw new IllegalArgumentException("input null");
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("weights empty");
        }
        if (input.length != weights.length) {
            throw new IllegalArgumentException("dim mismatch");
        }
        int n = input.length;
        double[] state = new double[n];
        for (int i = 0; i < n; i++) state[i] = input[i] ? 1.0 : -1.0;
        double[] newState = new double[n];

        for (int iter = 0; iter < maxIter; iter++) {
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) sum += weights[i][j] * state[j];
                newState[i] = sum >= 0 ? 1.0 : -1.0;
            }
            if (converged(state, newState)) {
                state = newState.clone();
                break;
            }
            System.arraycopy(newState, 0, state, 0, n);
        }
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) out[i] = state[i] > 0;
        return out;
    }

    public static boolean[] associate(boolean[] input, double[][] weights) {
        return associate(input, weights, DEFAULT_MAX_ITER);
    }

    /** Learn weights from patterns (outer product rule). */
    public static double[][] learn(boolean[][] patterns) {
        if (patterns == null || patterns.length == 0) {
            throw new IllegalArgumentException("patterns empty");
        }
        int n = patterns[0].length;
        double[][] w = new double[n][n];
        for (boolean[] p : patterns) {
            for (int i = 0; i < n; i++) {
                double si = p[i] ? 1.0 : -1.0;
                for (int j = 0; j < n; j++) {
                    double sj = p[j] ? 1.0 : -1.0;
                    w[i][j] += si * sj;
                }
            }
        }
        for (int i = 0; i < n; i++) w[i][i] = 0;  // no self-connection
        return w;
    }

    private static boolean converged(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
