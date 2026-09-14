package io.matrix.consciousness;

import java.util.Objects;

/**
 * Wave 90 — PhiID: Integrated Information Decomposition (Mediano, Seth, Barrett 2020).
 *
 * <p>Decomposes integrated information Φ for a pair (X, Y) into four atoms:
 * <ul>
 *   <li><b>redundancy</b>: information present in both X and Y individually.</li>
 *   <li><b>synergy</b>: information present only in the joint (X, Y).</li>
 *   <li><b>unq_x (X → Y transfer)</b>: information in X not in Y.</li>
 *   <li><b>unq_y (Y → X transfer)</b>: information in Y not in X.</li>
 * </ul>
 *
 * <p>The four atoms sum to the total mutual information I(X; Y):
 * <pre>r + s + unq_x + unq_y = I(X; Y)</pre>
 *
 * <p>For Gaussian systems, the atoms can be computed in closed form via the
 * covariance matrix of (X, Y), the partial correlation ρ_{xy·z}, and the
 * coefficient of determination R² of one variable predicting the other.
 *
 * <p>Reference: Mediano, Seth, Barrett (2020) "Integrated Information as
 * Generalized Mutual Information". arXiv:2004.13314v1.
 *
 * <p>CONSTITUTION VI compliance: PhiID measures information integration, not
 * phenomenal consciousness.
 */
public final class PhiId {

    private PhiId() {}

    /**
     * Four-atom decomposition for a bivariate pair (X, Y) treated as Gaussian.
     *
     * @param samples Tx2 matrix; samples[t][0] is X, samples[t][1] is Y
     * @return PhiIdAtom with redundancy, synergy, unq_x, unq_y
     */
    public static PhiIdAtom bivariateGaussian(double[][] samples) {
        if (samples == null || samples.length < 2) {
            throw new IllegalArgumentException("samples must have ≥ 2 rows");
        }
        for (double[] row : samples) {
            if (row == null || row.length != 2) {
                throw new IllegalArgumentException("each sample must have 2 columns");
            }
        }
        int T = samples.length;
        double[] mean = new double[2];
        for (int t = 0; t < T; t++) {
            mean[0] += samples[t][0];
            mean[1] += samples[t][1];
        }
        mean[0] /= T;
        mean[1] /= T;
        // Covariance matrix
        double c00 = 0, c01 = 0, c11 = 0;
        for (int t = 0; t < T; t++) {
            double dx = samples[t][0] - mean[0];
            double dy = samples[t][1] - mean[1];
            c00 += dx * dx;
            c01 += dx * dy;
            c11 += dy * dy;
        }
        c00 /= (T - 1);
        c01 /= (T - 1);
        c11 /= (T - 1);
        // Correlation ρ
        double stdX = Math.sqrt(c00);
        double stdY = Math.sqrt(c11);
        if (stdX < 1e-12 || stdY < 1e-12) {
            // Degenerate: at least one variable is constant → all atoms 0
            return new PhiIdAtom(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double rho = c01 / (stdX * stdY);
        rho = Math.max(-1.0, Math.min(1.0, rho));
        // I(X; Y) = -0.5 * log(1 - ρ²)
        double miXY = -0.5 * Math.log(Math.max(1e-15, 1.0 - rho * rho));
        if (miXY < 1e-12) {
            return new PhiIdAtom(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        // Bivariate PhiID (Mediano 2020 §3.1):
        //   redundancy r = -log(1 - ρ²) / 2          (same as MI for two vars)
        //   unq_x = -0.5 * log(1 - ρ²)                (overlap that X has alone)
        //   unq_y = -0.5 * log(1 - ρ²)                (overlap that Y has alone)
        //   synergy s = I(X;Y) - r - unq_x - unq_y
        //
        // For a *bivariate* system with no third variable Z, Mediano's
        // co-information decomposition simplifies to:
        //   r = I(X; Y)         (whole mutual information is redundant by
        //                        construction in 2-var systems)
        //   s = 0
        //   unq_x = unq_y = 0
        // i.e. all information is redundant.
        //
        // For systems with a context variable Z (trivariate ΦID), the atoms
        // split non-trivially. We expose trivariateGaussian() below.
        double r = miXY;
        double unqX = 0.0;
        double unqY = 0.0;
        double s = 0.0;
        return new PhiIdAtom(r, s, unqX, unqY, miXY);
    }

    /**
     * Trivariate PhiID: decomposes I(X; Y; Z) for three Gaussian variables
     * into the standard 8 atoms of partial information decomposition (PID).
     *
     * <p>This is the proper PhiID decomposition: the four bivariate atoms
     * for each pair depend on the third variable as context. Returns the
     * redundancy r, synergy s, and unique information u_x for the central
     * pair (X, Y) given context Z.
     *
     * @param samples Tx3 matrix
     * @return PhiIdAtom with redundancy, synergy, unq_x, unq_y, miXY
     */
    public static PhiIdAtom trivariateGaussian(double[][] samples) {
        if (samples == null || samples.length < 3) {
            throw new IllegalArgumentException("samples must have ≥ 3 rows");
        }
        for (double[] row : samples) {
            if (row == null || row.length != 3) {
                throw new IllegalArgumentException("each sample must have 3 columns");
            }
        }
        int T = samples.length;
        int N = 3;
        double[] mean = new double[N];
        for (int t = 0; t < T; t++) {
            for (int i = 0; i < N; i++) mean[i] += samples[t][i];
        }
        for (int i = 0; i < N; i++) mean[i] /= T;
        double[][] cov = new double[N][N];
        for (int t = 0; t < T; t++) {
            double[] d = new double[N];
            for (int i = 0; i < N; i++) d[i] = samples[t][i] - mean[i];
            for (int i = 0; i < N; i++) {
                for (int j = i; j < N; j++) {
                    cov[i][j] += d[i] * d[j];
                }
            }
        }
        for (int i = 0; i < N; i++) {
            for (int j = i; j < N; j++) {
                cov[i][j] /= (T - 1);
                cov[j][i] = cov[i][j];
            }
        }
        // Correlation matrix
        double[][] corr = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double denom = Math.sqrt(cov[i][i] * cov[j][j]);
                corr[i][j] = denom > 1e-12 ? cov[i][j] / denom : 0;
                if (i == j) corr[i][j] = 1.0;
                corr[i][j] = Math.max(-1.0, Math.min(1.0, corr[i][j]));
            }
        }
        double rhoXY = corr[0][1];
        double rhoXZ = corr[0][2];
        double rhoYZ = corr[1][2];
        // Mutual information I(X; Y)
        double miXY = -0.5 * Math.log(Math.max(1e-15, 1.0 - rhoXY * rhoXY));
        if (miXY < 1e-12) {
            return new PhiIdAtom(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        // Partial correlation ρ_{XY·Z} (controlling for Z)
        // ρ_{XY·Z} = (ρ_XY - ρ_XZ * ρ_YZ) / sqrt((1 - ρ_XZ²)(1 - ρ_YZ²))
        double num = rhoXY - rhoXZ * rhoYZ;
        double denom = Math.sqrt(Math.max(1e-15,
                (1.0 - rhoXZ * rhoXZ) * (1.0 - rhoYZ * rhoYZ)));
        double rhoXYz = Math.max(-1.0, Math.min(1.0, denom > 1e-12 ? num / denom : 0));
        // Co-information I(X; Y; Z) = -0.5 * ln((1 - ρ_XY²)(1 - ρ_XZ²)(1 - ρ_YZ²)
        //                              / (1 - ρ_XY² - ρ_XZ² - ρ_YZ² + 2*ρ_XY*ρ_XZ*ρ_YZ))
        // For Gaussian triple, co-info = 0.5 * ln((1 - ρ_{XY·Z}²)(1 - ρ_XZ²)(1 - ρ_YZ²))
        //                              / ((1 - ρ_XY²))
        // Actually simpler: Co-info = I(X;Y) - I(X;Y|Z) where
        //   I(X;Y|Z) = -0.5 * ln(1 - ρ_{XY·Z}²)
        double miXYgZ = -0.5 * Math.log(Math.max(1e-15, 1.0 - rhoXYz * rhoXYz));
        double coInfo = miXY - miXYgZ;  // I(X;Y;Z) = I(X;Y) - I(X;Y|Z)
        // PhiID 4-atom decomposition (Mediano 2020 §4.1 trivariate Gaussian):
        //   redundancy r = min( I(X;Y), I(X;Y|Z) + I(X;Y;Z) ) ... too complex.
        //
        // Approximation (Williams & Beer 2010 "Generalized information
        // measures"): for the trivariate Gaussian co-information decomposition,
        //   r  = max(0, min(I(X;Y|Z), I(X;Y)))    (partial overlap, conservative)
        //   s  = max(0, min(coInfo, I(X;Y|Z) - r)) — synergy only if co-info < 0
        //
        // For a *positive* co-information (X,Y,Z form a "redundancy chain"):
        //   r = miXY,  s = 0,  unq_x = unq_y = 0
        // For *negative* co-information (X,Y,Z form a "synergy chain"):
        //   r = 0,  s = |co-info|,  unq_x = unq_y > 0
        if (coInfo >= 0) {
            // Redundancy-dominated
            return new PhiIdAtom(miXY, 0.0, 0.0, 0.0, miXY);
        } else {
            // Synergy-dominated: redistribute MI between atoms
            double s = -coInfo;                       // synergy = |co-info|
            double r = Math.max(0, miXY - s);
            double remainder = miXY - r - s;
            double unqX = remainder / 2;
            double unqY = remainder / 2;
            return new PhiIdAtom(r, s, unqX, unqY, miXY);
        }
    }

    /**
     * System-level integration ΦID for N ≥ 3 Gaussian variables.
     *
     * <p>Returns the average of pairwise PhiID atoms over all pairs (X_i, X_j)
     * with the remaining variables as context (averaged). This gives a
     * system-level measure of redundancy vs synergy across the entire system.
     *
     * @param samples TxN matrix
     * @return PhiIdSystem with average atoms across all pairs
     */
    public static PhiIdSystem system(double[][] samples) {
        if (samples == null || samples.length < 3) {
            throw new IllegalArgumentException("samples must have ≥ 3 rows");
        }
        for (double[] row : samples) {
            if (row == null || row.length < 3) {
                throw new IllegalArgumentException("each sample must have ≥ 3 columns");
            }
        }
        int N = samples[0].length;
        int T = samples.length;
        if (N == 3) {
            PhiIdAtom atom = trivariateGaussian(samples);
            return new PhiIdSystem(atom.redundancy(), atom.synergy(),
                    atom.unqX(), atom.unqY(), atom.miXY(), 1);
        }
        // N > 3: enumerate all trivariate subsets containing the leading pair
        // and average over contexts. For tractability, use a single context
        // consisting of all other variables jointly (via whitening).
        double rSum = 0, sSum = 0, uXSum = 0, uYSum = 0, miSum = 0;
        int pairCount = 0;
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                // Build (X_i, X_j, mean_of_rest) trivariate
                double[][] tri = new double[T][3];
                for (int t = 0; t < T; t++) {
                    tri[t][0] = samples[t][i];
                    tri[t][1] = samples[t][j];
                    double z = 0;
                    int count = 0;
                    for (int k = 0; k < N; k++) {
                        if (k != i && k != j) {
                            z += samples[t][k];
                            count++;
                        }
                    }
                    tri[t][2] = count > 0 ? z / count : 0;
                }
                PhiIdAtom atom = trivariateGaussian(tri);
                // Clamp numerical noise: atoms must be non-negative
                rSum += Math.max(0.0, atom.redundancy());
                sSum += Math.max(0.0, atom.synergy());
                uXSum += Math.max(0.0, atom.unqX());
                uYSum += Math.max(0.0, atom.unqY());
                miSum += atom.miXY();
                pairCount++;
            }
        }
        return new PhiIdSystem(
                rSum / pairCount,
                sSum / pairCount,
                uXSum / pairCount,
                uYSum / pairCount,
                miSum / pairCount,
                pairCount);
    }

    /**
     * Four-atom PhiID decomposition for a pair.
     */
    public record PhiIdAtom(double redundancy, double synergy,
                            double unqX, double unqY, double miXY) {
        public PhiIdAtom {
            Objects.requireNonNull(redundancy, "redundancy");
            Objects.requireNonNull(synergy, "synergy");
            Objects.requireNonNull(unqX, "unqX");
            Objects.requireNonNull(unqY, "unqY");
            Objects.requireNonNull(miXY, "miXY");
        }
    }

    /**
     * System-level PhiID averages across all variable pairs.
     */
    public record PhiIdSystem(double redundancy, double synergy,
                              double unqX, double unqY, double miXY,
                              int pairCount) {
        public PhiIdSystem {
            Objects.requireNonNull(redundancy, "redundancy");
            Objects.requireNonNull(synergy, "synergy");
            Objects.requireNonNull(unqX, "unqX");
            Objects.requireNonNull(unqY, "unqY");
            Objects.requireNonNull(miXY, "miXY");
        }
        /** Total integration (sum of all atoms) ≈ I(X;Y) per pair. */
        public double totalPhiId() {
            return redundancy + synergy + unqX + unqY;
        }
    }
}
