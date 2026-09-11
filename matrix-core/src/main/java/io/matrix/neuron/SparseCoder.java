package io.matrix.neuron;

/**
 * DESIGN-42 — Sparse coding via Matching Pursuit + LASSO.
 * Pure function (CONSTITUTION I).
 *
 * <p>Simple matching-pursuit-based sparse coder. Greedy: at each
 * step, pick the atom with highest correlation, subtract projection,
 * repeat. Then soft-threshold small coefficients.
 */
public final class SparseCoder {

    public static final int DEFAULT_MAX_ATOMS = 10;

    private SparseCoder() {}

    /**
     * Find sparse code a for signal x using dictionary D.
     * Greedy matching pursuit: pick best atom, project, subtract,
     * repeat up to maxAtoms times. Then soft-threshold small values.
     */
    public static double[] encode(double[] x, double[][] dictionary,
                                  double sparsityLambda, int maxAtoms) {
        if (x == null || dictionary == null) {
            throw new IllegalArgumentException("null");
        }
        if (dictionary.length == 0 || x.length == 0) {
            return new double[0];
        }
        if (x.length != dictionary.length) {
            throw new IllegalArgumentException("x/dim mismatch");
        }
        int nAtoms = dictionary[0].length;
        double[] a = new double[nAtoms];
        double[] residual = x.clone();
        for (int iter = 0; iter < Math.min(maxAtoms, nAtoms); iter++) {
            // Find atom with highest correlation
            int bestAtom = -1;
            double bestCorr = 0;
            for (int j = 0; j < nAtoms; j++) {
                double corr = dot(residual, column(dictionary, j));
                if (Math.abs(corr) > Math.abs(bestCorr)) {
                    bestCorr = corr;
                    bestAtom = j;
                }
            }
            if (bestAtom < 0) break;
            // Project residual onto this atom
            double atomNorm = dot(column(dictionary, bestAtom),
                    column(dictionary, bestAtom));
            if (atomNorm < 1e-10) break;
            double coefficient = bestCorr / atomNorm;
            a[bestAtom] += coefficient;
            // Update residual
            for (int i = 0; i < x.length; i++) {
                residual[i] -= coefficient * dictionary[i][bestAtom];
            }
            // Check convergence
            double resNorm = dot(residual, residual);
            if (resNorm < 1e-10) break;
        }
        // Soft-threshold small coefficients
        for (int j = 0; j < nAtoms; j++) {
            if (Math.abs(a[j]) < sparsityLambda) {
                a[j] = 0;
            }
        }
        return a;
    }

    public static double[] encode(double[] x, double[][] dictionary,
                                  double sparsityLambda) {
        return encode(x, dictionary, sparsityLambda, DEFAULT_MAX_ATOMS);
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) sum += a[i] * b[i];
        return sum;
    }

    private static double[] column(double[][] matrix, int col) {
        double[] result = new double[matrix.length];
        for (int i = 0; i < matrix.length; i++) result[i] = matrix[i][col];
        return result;
    }

    /** Compute reconstruction error: ||x - D*a||^2 / ||x||^2. */
    public static double reconstructionError(double[] x, double[][] dictionary,
                                            double[] a) {
        if (x == null || dictionary == null || a == null) {
            return Double.POSITIVE_INFINITY;
        }
        double sumSqError = 0;
        double sumSqX = 0;
        for (int i = 0; i < x.length; i++) {
            double recon = 0;
            for (int j = 0; j < a.length; j++) {
                recon += dictionary[i][j] * a[j];
            }
            double err = x[i] - recon;
            sumSqError += err * err;
            sumSqX += x[i] * x[i];
        }
        return sumSqX == 0 ? 0 : sumSqError / sumSqX;
    }
}
