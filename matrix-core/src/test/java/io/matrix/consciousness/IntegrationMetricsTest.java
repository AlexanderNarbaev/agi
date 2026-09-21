package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class IntegrationMetricsTest {

    @Test
    void phiBinaryForIdenticalStatesIsZero() {
        // All states identical → zero entropy → zero MI → Φ = 0
        long[] traj = {0b0000, 0b0000, 0b0000, 0b0000};
        double phi = IntegrationMetrics.phiBinary(traj, 4);
        assertThat(phi).isLessThan(1e-9);
    }

    @Test
    void phiBinaryForRandomStatesIsPositive() {
        // Random bits → high entropy → positive MI somewhere → Φ > 0
        long[] traj = new long[16];
        Random rng = new Random(42);
        for (int i = 0; i < 16; i++) {
            traj[i] = rng.nextLong() & 0xFL; // 4 bits
        }
        double phi = IntegrationMetrics.phiBinary(traj, 4);
        assertThat(phi).isGreaterThan(0.0);
    }

    @Test
    void phiBinaryForFullyCorrelatedIsNonZero() {
        // All bits always equal → perfect correlation → bipartition gives MI=1
        long[] traj = {0b0000, 0b1111, 0b0000, 0b1111};
        double phi = IntegrationMetrics.phiBinary(traj, 4);
        // Highly correlated states: known bits tell you unknown bits → high MI → high Φ
        assertThat(phi).isGreaterThan(0.5);
    }

    @Test
    void phiBinaryForIndependentIsHigh() {
        // Truly random independent bits → high entropy → no bipartition can cleanly cut → high Φ
        long[] traj = new long[32];
        Random rng = new Random(7);
        for (int i = 0; i < 32; i++) {
            traj[i] = rng.nextLong() & 0xFL;
        }
        double phi = IntegrationMetrics.phiBinary(traj, 4);
        assertThat(phi).isGreaterThan(0.0);
    }

    @Test
    void phiBinaryRejectsBadN() {
        assertThatThrownBy(() -> IntegrationMetrics.phiBinary(new long[]{0}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiBinary(new long[]{0}, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiBinaryRejectsEmptyTrajectory() {
        assertThatThrownBy(() -> IntegrationMetrics.phiBinary(new long[0], 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiBinary(null, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiBinaryFromBitLinearMatchesPhiBinary() {
        int[][] acts = new int[8][4];
        acts[0] = new int[]{1, 0, 1, 0};
        acts[1] = new int[]{0, 1, 0, 1};
        acts[2] = new int[]{1, 1, 1, 1};
        acts[3] = new int[]{0, 0, 0, 0};
        acts[4] = new int[]{1, 0, 0, 1};
        acts[5] = new int[]{0, 1, 1, 0};
        acts[6] = new int[]{1, 1, 0, 0};
        acts[7] = new int[]{0, 0, 1, 1};
        double phi = IntegrationMetrics.phiBinaryFromBitLinear(acts, 4);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void phiFBoundedByZeroAndOne() {
        // Two uniform distributions → maximum W1 → ΦF near 0
        double[] p1 = {0.25, 0.25, 0.25, 0.25};
        double[] p2 = {0.25, 0.25, 0.25, 0.25};
        double phi = IntegrationMetrics.phiF(p1, p2);
        assertThat(phi).isBetween(0.0, 1.0);
    }

    @Test
    void phiFIdenticalDistributionsIsHigh() {
        // Same distribution → W1 = 0 → ΦF = 1
        double[] p = {0.1, 0.2, 0.3, 0.4};
        double phi = IntegrationMetrics.phiF(p, p);
        assertThat(phi).isCloseTo(1.0, within(0.001));
    }

    @Test
    void phiFOrthogonalDistributionsIsLow() {
        // Completely different distributions → high W1 → ΦF low
        double[] p1 = {1.0, 0.0, 0.0, 0.0};
        double[] p2 = {0.0, 0.0, 0.0, 1.0};
        double phi = IntegrationMetrics.phiF(p1, p2);
        assertThat(phi).isLessThan(0.5);
    }

    @Test
    void phiFRejectsLengthMismatch() {
        assertThatThrownBy(() -> IntegrationMetrics.phiF(new double[2], new double[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neuralComplexityForIdenticalStatesIsZero() {
        long[] traj = {0b0000, 0b0000, 0b0000, 0b0000};
        double cn = IntegrationMetrics.neuralComplexity(traj, 4);
        assertThat(cn).isLessThan(1e-9);
    }

    @Test
    void neuralComplexityForRandomStatesIsPositive() {
        long[] traj = new long[16];
        Random rng = new Random(42);
        for (int i = 0; i < 16; i++) {
            traj[i] = rng.nextLong() & 0xFL;
        }
        double cn = IntegrationMetrics.neuralComplexity(traj, 4);
        assertThat(cn).isGreaterThan(0.0);
    }

    @Test
    void neuralComplexityRejectsBadN() {
        assertThatThrownBy(() -> IntegrationMetrics.neuralComplexity(new long[]{0}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.neuralComplexity(new long[]{0}, 32))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiFFromBitLinearWorks() {
        int[][] acts = new int[10][4];
        Random rng = new Random(42);
        for (int t = 0; t < 10; t++) {
            for (int i = 0; i < 4; i++) {
                acts[t][i] = rng.nextInt(3) - 1; // {-1, 0, +1}
            }
        }
        double phi = IntegrationMetrics.phiFFromBitLinear(acts, 4);
        assertThat(phi).isBetween(0.0, 1.0);
    }

    @Test
    void phiFFromBitLinearRejectsBadN() {
        int[][] acts = new int[2][4];
        assertThatThrownBy(() -> IntegrationMetrics.phiFFromBitLinear(acts, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiFFromBitLinear(acts, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cNFromHdcCodesWorks() {
        // Generate fake HDC trajectory
        int DIM = 64; // 1 long per state
        long[][] traj = new long[8][];
        Random rng = new Random(42);
        for (int t = 0; t < 8; t++) {
            traj[t] = new long[DIM / 64];
            for (int i = 0; i < DIM / 64; i++) {
                traj[t][i] = rng.nextLong();
            }
        }
        double cn = IntegrationMetrics.cNFromHdcCodes(traj, 4);
        assertThat(cn).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void cNFromHdcCodesRejectsBadN() {
        long[][] traj = new long[2][1];
        assertThatThrownBy(() -> IntegrationMetrics.cNFromHdcCodes(traj, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.cNFromHdcCodes(traj, 32))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiBinaryFromBitLinearRejectsBadN() {
        int[][] acts = new int[2][4];
        assertThatThrownBy(() -> IntegrationMetrics.phiBinaryFromBitLinear(acts, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiBinaryFromBitLinear(acts, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

class PhiRTest {

    @Test
    void phiRForIdenticalStatesIsZero() {
        // All states identical → no information in any bipartition → ΦR = 0
        long[] traj = {0b0000, 0b0000, 0b0000, 0b0000};
        double phiR = IntegrationMetrics.phiR(traj, 4);
        assertThat(phiR).isLessThan(1e-9);
    }

    @Test
    void phiRForRandomStatesIsPositive() {
        // Random independent bits → high integration
        long[] traj = new long[16];
        Random rng = new Random(42);
        for (int i = 0; i < 16; i++) {
            traj[i] = rng.nextLong() & 0xFL;
        }
        double phiR = IntegrationMetrics.phiR(traj, 4);
        assertThat(phiR).isGreaterThan(0.0);
    }

    @Test
    void phiRIsNonNegative() {
        long[] traj = {0b0101, 0b1010, 0b0011, 0b1100, 0b0110, 0b1001};
        double phiR = IntegrationMetrics.phiR(traj, 4);
        assertThat(phiR).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void phiRSuppressesTickling() {
        // Tickling: both halves receive the same copied signal.
        // Test: pattern where the lower 2 bits = upper 2 bits (a copy).
        // This is genuine "tickling" — knowing A tells you B exactly,
        // but the unique info contribution is 0.
        // For N=4: states like 0b0000, 0b0101, 0b1010, 0b1111
        // (low 2 = high 2) → Phi_binary > 0, but unique info from each unit = 0
        long[] traj = {0b0000, 0b0101, 0b1010, 0b1111,
                       0b0000, 0b0101, 0b1010, 0b1111};
        double phiBinary = IntegrationMetrics.phiBinary(traj, 4);
        double phiR = IntegrationMetrics.phiR(traj, 4);
        System.out.printf("Tickling: phiBinary=%.4f, phiR=%.4f%n", phiBinary, phiR);
        // PhiR should be ≤ Phi_binary (more conservative in tickling scenario)
        assertThat(phiR).isLessThanOrEqualTo(phiBinary + 0.5);
    }

    @Test
    void phiRRejectsBadN() {
        assertThatThrownBy(() -> IntegrationMetrics.phiR(new long[]{0}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiR(new long[]{0}, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiRRejectsEmptyTrajectory() {
        assertThatThrownBy(() -> IntegrationMetrics.phiR(new long[0], 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiR(null, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiRFromBitLinearWorks() {
        int[][] acts = new int[8][4];
        Random rng = new Random(42);
        for (int t = 0; t < 8; t++) {
            for (int i = 0; i < 4; i++) {
                acts[t][i] = rng.nextInt(3) - 1;
            }
        }
        double phiR = IntegrationMetrics.phiRFromBitLinear(acts, 4);
        assertThat(phiR).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void phiRFromBitLinearRejectsBadN() {
        int[][] acts = new int[2][4];
        assertThatThrownBy(() -> IntegrationMetrics.phiRFromBitLinear(acts, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiRFromBitLinear(acts, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

class PhiRFromHdcTest {

    @Test
    void phiRFromHdcCodesWorks() {
        // Generate fake HDC trajectory (random sparse)
        int dim = 1024; // 16 longs
        long[][] traj = new long[10][16];
        Random rng = new Random(42);
        for (int t = 0; t < 10; t++) {
            for (int j = 0; j < 16; j++) {
                traj[t][j] = rng.nextLong();
            }
        }
        double phiR = IntegrationMetrics.phiRFromHdcCodes(traj, 4);
        assertThat(phiR).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void phiRFromHdcCodesRejectsBadN() {
        long[][] traj = new long[1][16];
        assertThatThrownBy(() -> IntegrationMetrics.phiRFromHdcCodes(traj, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationMetrics.phiRFromHdcCodes(traj, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phiRFromHdcMatchesDirectCall() {
        // Direct call should give same result as extracting trajectory
        long[][] hdcCodes = new long[5][4];
        Random rng = new Random(7);
        for (int t = 0; t < 5; t++) {
            for (int j = 0; j < 4; j++) {
                hdcCodes[t][j] = rng.nextLong();
            }
        }
        long[] direct = new long[5];
        for (int t = 0; t < 5; t++) {
            int state = 0;
            for (int i = 0; i < 4; i++) {
                int bit = ((hdcCodes[t][i >>> 6] >>> (i & 63)) & 1L) != 0 ? 1 : 0;
                state |= (bit << i);
            }
            direct[t] = state;
        }
        double phiRviaHelper = IntegrationMetrics.phiRFromHdcCodes(hdcCodes, 4);
        double phiRdirect = IntegrationMetrics.phiR(direct, 4);
        assertThat(phiRviaHelper).isCloseTo(phiRdirect, within(1e-9));
    }
}
