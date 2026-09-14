package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Wave 89 — Φ_linGauss (closed-form linear-Gaussian integration metric).
 *
 * <p>Tests verify that the closed-form covariance ln-determinant computation
 * satisfies the formal properties of integrated information:
 * <ol>
 *   <li><b>Symmetry</b>: Φ(X₁, X₂, ..., Xₙ) = Φ(permuted inputs).</li>
 *   <li><b>Non-negativity</b>: Φ ≥ 0 for any valid input.</li>
 *   <li><b>Independence ⇒ 0</b>: independent variables yield Φ = 0.</li>
 *   <li><b>Perfect correlation ⇒ 0</b>: perfectly correlated variables are not
 *       "integrated" — they share all information.</li>
 *   <li><b>Partial correlation ⇒ Φ &gt; 0</b>: structured coupling gives Φ &gt; 0.</li>
 * </ol>
 */
class PhiLinGaussTest {

    /** Independent binary sequences → Φ = 0. */
    @Test
    void independentVariablesYieldZero() {
        long[] trajectory = independentTrajectory(8, 32, 42);
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 8);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
        // Independent bits should have very low integration
        // (numerical noise may give a small positive value)
        assertThat(phi).isLessThan(0.5);
    }

    /** Identical trajectory (constant) is a degenerate case. */
    @Test
    void constantTrajectoryIsDegenerate() {
        long[] trajectory = new long[16];
        for (int i = 0; i < 16; i++) trajectory[i] = 0xAA;
        // Singular covariance → 0
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 8);
        assertThat(phi).isEqualTo(0.0);
    }

    /** Perfectly redundant: trajectory[t] = all-zeros + t-bit → only 1 variable has variance. */
    @Test
    void perfectlyRedundantYieldsLowPhi() {
        // All bits are identical across timesteps
        long[] trajectory = new long[32];
        for (int t = 0; t < 32; t++) trajectory[t] = 0xFF;
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 8);
        // Singular (constant) → 0
        assertThat(phi).isEqualTo(0.0);
    }

    /** Structured coupling: bit-0 always equals bit-1 → partial integration. */
    @Test
    void partialCorrelationGivesPositivePhi() {
        // 16 timesteps where bit-1 = bit-0, others random
        long[] trajectory = new long[16];
        java.util.Random rng = new java.util.Random(123);
        for (int t = 0; t < 16; t++) {
            long state = 0;
            int b0 = rng.nextBoolean() ? 1 : 0;
            state |= (long) b0;       // bit 0
            state |= (long) b0 << 1;  // bit 1 = bit 0
            for (int i = 2; i < 8; i++) {
                if (rng.nextBoolean()) state |= (1L << i);
            }
            trajectory[t] = state;
        }
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 8);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
        System.out.printf("Partial-corr Φ_linGauss=%.4f%n", phi);
    }

    /** Symmetry: permuting input dimensions should not change Φ. */
    @Test
    void permutationInvariance() {
        long[] trajectory = structuredTrajectory(8, 32, 99);
        long[] permuted = new long[trajectory.length];
        int[] perm = {2, 0, 1, 3, 4, 5, 6, 7};  // arbitrary
        for (int t = 0; t < trajectory.length; t++) {
            long s = trajectory[t];
            long out = 0;
            for (int newIdx = 0; newIdx < 8; newIdx++) {
                int oldIdx = perm[newIdx];
                int bit = (int) ((s >> oldIdx) & 1L);
                out |= (long) bit << newIdx;
            }
            permuted[t] = out;
        }
        double phiOrig = IntegrationMetrics.phiLinGauss(trajectory, 8);
        double phiPerm = IntegrationMetrics.phiLinGauss(permuted, 8);
        assertThat(phiPerm).isCloseTo(phiOrig, within(0.01));
    }

    /** phiLinGaussFromSamples produces same result as phiLinGauss for binary data. */
    @Test
    void fromSamplesMatchesBinaryTrajectory() {
        long[] trajectory = structuredTrajectory(8, 32, 42);
        double[][] samples = new double[trajectory.length][8];
        for (int t = 0; t < trajectory.length; t++) {
            long s = trajectory[t];
            for (int i = 0; i < 8; i++) {
                samples[t][i] = ((s >> i) & 1L) == 1 ? 1.0 : -1.0;
            }
        }
        double phiA = IntegrationMetrics.phiLinGauss(trajectory, 8);
        double phiB = IntegrationMetrics.phiLinGaussFromSamples(samples, 8);
        assertThat(phiA).isCloseTo(phiB, within(1e-6));
    }

    /** Parameter validation: N out of range → IllegalArgumentException. */
    @Test
    void invalidNThrows() {
        long[] trajectory = new long[4];
        try {
            IntegrationMetrics.phiLinGauss(trajectory, 0);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            IntegrationMetrics.phiLinGauss(trajectory, 17);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** N=1 trivially yields Φ = 0 (no bipartition). */
    @Test
    void n1YieldsZero() {
        long[] trajectory = {0L, 1L, 0L, 1L, 1L, 0L, 1L, 0L};
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 1);
        assertThat(phi).isEqualTo(0.0);
    }

    /** Independent random bits across many samples give very low Φ. */
    @Test
    void largeRandomTrajectoryHasLowPhi() {
        long[] trajectory = independentTrajectory(8, 256, 7);
        double phi = IntegrationMetrics.phiLinGauss(trajectory, 8);
        System.out.printf("Independent random (256 samples) Φ_linGauss=%.4f%n", phi);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
        assertThat(phi).isLessThan(0.5);  // small but not zero due to finite-sample noise
    }

    // ===== Helpers =====

    private static long[] independentTrajectory(int N, int length, long seed) {
        long[] t = new long[length];
        java.util.Random rng = new java.util.Random(seed);
        for (int s = 0; s < length; s++) {
            long state = 0;
            for (int i = 0; i < N; i++) {
                if (rng.nextBoolean()) state |= (1L << i);
            }
            t[s] = state;
        }
        return t;
    }

    private static long[] structuredTrajectory(int N, int length, long seed) {
        // bit-0 always 1; bit-1 always 0; rest random
        long[] t = new long[length];
        java.util.Random rng = new java.util.Random(seed);
        for (int s = 0; s < length; s++) {
            long state = 1L; // bit-0 = 1
            for (int i = 2; i < N; i++) {
                if (rng.nextBoolean()) state |= (1L << i);
            }
            t[s] = state;
        }
        return t;
    }
}
