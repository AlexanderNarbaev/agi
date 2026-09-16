package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 104 — Kolmogorov complexity estimator (CTM).
 */
class KolmogorovComplexityTest {

    @Test
    void emptyReturnsZero() {
        assertThat(KolmogorovComplexity.estimate(new long[0])).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimate((long[]) null)).isEqualTo(0.0);
    }

    @Test
    void singleStateReturnsOneLong() {
        long[] traj = {42L};
        double k = KolmogorovComplexity.estimate(traj);
        assertThat(k).isEqualTo(64.0); // single 64-bit state
    }

    @Test
    void constantTrajectoryLowComplexity() {
        // All same symbol → low entropy → low K
        long[] traj = new long[20];
        for (int i = 0; i < 20; i++) traj[i] = 42L;
        double k = KolmogorovComplexity.estimate(traj);
        System.out.printf("W104: constant traj K=%.4f%n", k);
        // model + 0 codelength = log encoding of alphabet=1
        assertThat(k).isGreaterThan(0.0);
        assertThat(k).isLessThan(20.0);  // much less than 8*20=160 raw bits
    }

    @Test
    void randomTrajectoryHighComplexity() {
        Random rng = new Random(42);
        long[] traj = new long[64];
        for (int i = 0; i < traj.length; i++) traj[i] = rng.nextLong();
        double k = KolmogorovComplexity.estimate(traj);
        System.out.printf("W104: random traj K=%.4f%n", k);
        assertThat(k).isGreaterThan(200.0);  // near maximum (64*8=512 bits)
    }

    @Test
    void structuredTrajectoryLowerThanRandom() {
        // 32 timesteps where bit-0 = time parity
        long[] structured = new long[32];
        for (int t = 0; t < 32; t++) structured[t] = (long) (t & 1);
        double kStruct = KolmogorovComplexity.estimate(structured);
        // 32 random bits
        Random rng = new Random(42);
        long[] random = new long[32];
        for (int i = 0; i < 32; i++) random[i] = rng.nextLong();
        double kRand = KolmogorovComplexity.estimate(random);
        System.out.printf("W104: structured K=%.4f, random K=%.4f%n", kStruct, kRand);
        assertThat(kStruct).isLessThan(kRand);
    }

    @Test
    void binaryConstantLowComplexity() {
        boolean[] seq = new boolean[100];
        // all false
        double k = KolmogorovComplexity.estimateBinary(seq);
        assertThat(k).isLessThan(2.0);  // very low
    }

    @Test
    void binaryRandomHigherComplexity() {
        Random rng = new Random(42);
        boolean[] seq = new boolean[100];
        for (int i = 0; i < 100; i++) seq[i] = rng.nextBoolean();
        double k = KolmogorovComplexity.estimateBinary(seq);
        assertThat(k).isGreaterThan(20.0);  // random binary ≈ 100 bits
    }
}
