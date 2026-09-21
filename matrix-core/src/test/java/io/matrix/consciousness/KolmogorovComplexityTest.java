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

    // ─── Property-based tests (jqwik) for Quarkus XML verification ───

    @net.jqwik.api.Property
    void propertyKolmogorovEmptyIsZero(@net.jqwik.api.ForAll("intSeeds") int seed) {
        // Any number of empty trajectories → K=0
        long[] empty = new long[0];
        assertThat(KolmogorovComplexity.estimate(empty)).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimate((long[]) null)).isEqualTo(0.0);
    }

    @net.jqwik.api.Property
    void propertyKolmogorovSingleIs64(@net.jqwik.api.ForAll("longValues") long value) {
        long[] single = {value};
        assertThat(KolmogorovComplexity.estimate(single)).isEqualTo(64.0);
    }

    @net.jqwik.api.Property
    void propertyKolmogorovConstantIsLow(@net.jqwik.api.ForAll("trajectoryLengths") int length) {
        long[] constant = new long[length];
        double k = KolmogorovComplexity.estimate(constant);
        // All zeros → 1 unique symbol → K should be small (just model overhead)
        assertThat(k).isLessThan(20.0);
    }

    @net.jqwik.api.Property
    void propertyKolmogorovBinaryConstantIsLow(@net.jqwik.api.ForAll("bools") boolean value) {
        boolean[] seq = new boolean[10];
        for (int i = 0; i < 10; i++) seq[i] = value;
        double k = KolmogorovComplexity.estimateBinary(seq);
        assertThat(k).isLessThan(2.0);
    }

    @net.jqwik.api.Property
    void propertyKolmogorovRandomBinaryIsHigher(@net.jqwik.api.ForAll("intSeeds") int seed) {
        Random rng = new Random(seed);
        boolean[] seq = new boolean[100];
        for (int i = 0; i < 100; i++) seq[i] = rng.nextBoolean();
        double k = KolmogorovComplexity.estimateBinary(seq);
        // Random binary sequence has K near its length (100 bits)
        assertThat(k).isGreaterThan(20.0);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Integer> intSeeds() {
        return net.jqwik.api.Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Long> longValues() {
        return net.jqwik.api.Arbitraries.longs().between(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Integer> trajectoryLengths() {
        return net.jqwik.api.Arbitraries.integers().between(1, 32);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Boolean> bools() {
        return net.jqwik.api.Arbitraries.of(true, false);
    }
}
