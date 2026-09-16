package io.matrix.consciousness;

/**
 * W198 — Cognitive triplet analysis.
 *
 * <p>Compute three-way interactions between cognitive fields:
 * - Interaction information I(X;Y;Z) = I(X;Y) - I(X;Y|Z)
 * - Positive I(X;Y;Z): synergy (3-way information > sum of 2-way)
 * - Negative I(X;Y;Z): redundancy (overlapping information)
 *
 * <p>Used to identify synergistic vs redundant information
 * structures in cognitive profile fields.
 *
 * <p>CONSTITUTION VI compliance: three-way information analysis,
 * not phenomenal consciousness claim.
 */
public final class CognitiveTriplet {

    private CognitiveTriplet() {}

    /**
     * Compute interaction information (co-information) for three
     * discrete distributions.
     *
     * <p>I(X;Y;Z) = I(X;Y) - I(X;Y|Z) = I(X;Y) + I(X;Z) - I(X;Y,Z)
     *
     * <p>Returns:
     * - Positive: synergy
     * - Zero: independence
     * - Negative: redundancy
     */
    public static double interactionInformation(double[][][] jointXYZ) {
        if (jointXYZ == null || jointXYZ.length == 0) return 0.0;
        int nx = jointXYZ.length;
        int ny = jointXYZ[0].length;
        if (ny == 0) return 0.0;
        int nz = jointXYZ[0][0].length;
        if (nz == 0) return 0.0;

        // Compute marginals
        double[] margX = new double[nx];
        double[] margY = new double[ny];
        double[] margZ = new double[nz];
        double[][] jointXY = new double[nx][ny];
        double[][] jointXZ = new double[nx][nz];
        double[][] jointYZ = new double[ny][nz];
        double total = 0;
        for (int x = 0; x < nx; x++) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    double p = jointXYZ[x][y][z];
                    margX[x] += p;
                    margY[y] += p;
                    margZ[z] += p;
                    jointXY[x][y] += p;
                    jointXZ[x][z] += p;
                    jointYZ[y][z] += p;
                    total += p;
                }
            }
        }
        if (total == 0) return 0.0;

        double hX = entropy(margX, total);
        double hY = entropy(margY, total);
        double hZ = entropy(margZ, total);
        double hXY = entropy2D(jointXY, total);
        double hXZ = entropy2D(jointXZ, total);
        double hYZ = entropy2D(jointYZ, total);
        double hXYZ = entropy3D(jointXYZ, total);

        // I(X;Y;Z) = -H(X,Y,Z) + H(X,Y) + H(X,Z) + H(Y,Z) - H(X) - H(Y) - H(Z)
        return -hXYZ + hXY + hXZ + hYZ - hX - hY - hZ;
    }

    /**
     * Compute synergy vs redundancy from interaction information.
     * Returns "SYNERGY", "REDUNDANCY", or "INDEPENDENT".
     */
    public static String classifyInteraction(double[][][] jointXYZ, double threshold) {
        double ii = interactionInformation(jointXYZ);
        if (ii > threshold) return "SYNERGY";
        if (ii < -threshold) return "REDUNDANCY";
        return "INDEPENDENT";
    }

    private static double entropy(double[] p, double total) {
        double h = 0;
        for (double v : p) {
            if (v > 0) {
                double pn = v / total;
                h -= pn * Math.log(pn);
            }
        }
        return h / Math.log(2);
    }

    private static double entropy2D(double[][] p, double total) {
        double h = 0;
        for (double[] row : p) {
            for (double v : row) {
                if (v > 0) {
                    double pn = v / total;
                    h -= pn * Math.log(pn);
                }
            }
        }
        return h / Math.log(2);
    }

    private static double entropy3D(double[][][] p, double total) {
        double h = 0;
        for (double[][] xy : p) {
            for (double[] z : xy) {
                for (double v : z) {
                    if (v > 0) {
                        double pn = v / total;
                        h -= pn * Math.log(pn);
                    }
                }
            }
        }
        return h / Math.log(2);
    }
}
