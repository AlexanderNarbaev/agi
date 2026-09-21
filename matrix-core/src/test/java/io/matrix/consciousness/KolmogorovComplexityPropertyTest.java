package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W129 — Kolmogorov complexity property-based tests.
 *
 * <p>Property-based verification of W104 Kolmogorov complexity estimator.
 * Uses jqwik @Property annotations which generate Quarkus XML reports.
 */
class KolmogorovComplexityPropertyTest {

    @Property(tries = 100)
    void propertyEmptyIsZero(@ForAll("anySeed") int seed) {
        long[] empty = new long[0];
        assertThat(KolmogorovComplexity.estimate(empty)).isEqualTo(0.0);
    }

    @Property(tries = 100)
    void propertyNullIsZero() {
        assertThat(KolmogorovComplexity.estimate((long[]) null)).isEqualTo(0.0);
    }

    @Property(tries = 100)
    void propertySingleStateIs64(@ForAll("anyLong") long value) {
        long[] single = {value};
        assertThat(KolmogorovComplexity.estimate(single)).isEqualTo(64.0);
    }

    @Property(tries = 50)
    void propertyConstantTrajectoryLowK(@ForAll("trajectoryLength") int length) {
        long[] constant = new long[length];
        double k = KolmogorovComplexity.estimate(constant);
        // Constant → 1 unique symbol → small K (model overhead only)
        assertThat(k).isLessThan(20.0);
    }

    @Property(tries = 50)
    void propertyBinaryConstantLowK(@ForAll("booleanValue") boolean value) {
        boolean[] seq = new boolean[10];
        for (int i = 0; i < 10; i++) seq[i] = value;
        double k = KolmogorovComplexity.estimateBinary(seq);
        assertThat(k).isLessThan(2.0);
    }

    @Property(tries = 50)
    void propertyBinaryEmptyIsZero() {
        boolean[] empty = new boolean[0];
        assertThat(KolmogorovComplexity.estimateBinary(empty)).isEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyBinaryRandomHighK(@ForAll("anySeed") int seed) {
        Random rng = new Random(seed);
        boolean[] seq = new boolean[100];
        for (int i = 0; i < 100; i++) seq[i] = rng.nextBoolean();
        double k = KolmogorovComplexity.estimateBinary(seq);
        // Random binary sequence: K near 100 bits
        assertThat(k).isGreaterThan(20.0);
    }

    @Property(tries = 50)
    void propertyStructuredLowerThanRandom(@ForAll("anySeed") int seed) {
        // Structured: alternating 0,1,0,1,...
        long[] structured = new long[32];
        for (int i = 0; i < 32; i++) structured[i] = i & 1;
        // Random: full Long range
        Random rng = new Random(seed);
        long[] random = new long[32];
        for (int i = 0; i < 32; i++) random[i] = rng.nextLong();
        double kStruct = KolmogorovComplexity.estimate(structured);
        double kRand = KolmogorovComplexity.estimate(random);
        assertThat(kStruct).isLessThan(kRand);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Long> anyLong() {
        return Arbitraries.longs().between(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);
    }

    @Provide
    Arbitrary<Integer> trajectoryLength() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Boolean> booleanValue() {
        return Arbitraries.of(true, false);
    }
}
