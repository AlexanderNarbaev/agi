package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W188 — PhiMaxCalculator property-based tests.
 */
class PhiMaxCalculatorPropertyTest {

    @Property(tries = 50)
    void propertyPhiMaxNonNegative(@ForAll("stateArrays") int[] states) {
        double phi = PhiMaxCalculator.phiMax(states);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyPhiMaxGreedyNonNegative(@ForAll("stateArrays") int[] states) {
        double phi = PhiMaxCalculator.phiMaxGreedy(states);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyPhiMaxConstantIsZero(@ForAll("lengths") int length,
                                       @ForAll("values") int value) {
        if (length < 1) return;
        int[] states = new int[length];
        for (int i = 0; i < length; i++) states[i] = value;
        assertThat(PhiMaxCalculator.phiMax(states)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyPhiMaxGreedyAtLeastFullMinusEpsilon(@ForAll("smallArrays") int[] states) {
        double full = PhiMaxCalculator.phiMax(states);
        double greedy = PhiMaxCalculator.phiMaxGreedy(states);
        // Greedy should find at least as low MI as full
        assertThat(greedy).isLessThanOrEqualTo(full + 0.01);
    }

    @Property(tries = 30)
    void propertyPhiMaxEmptyReturnsZero() {
        assertThat(PhiMaxCalculator.phiMax(new int[0])).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyPhiMaxGreedyEmptyReturnsZero() {
        assertThat(PhiMaxCalculator.phiMaxGreedy(new int[0])).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyPhiMaxRandomHasFiniteValue(@ForAll("anySeed") int seed,
                                             @ForAll("lengths") int length) {
        if (length < 2) return;
        Random rng = new Random(seed);
        int[] states = new int[length];
        for (int i = 0; i < length; i++) states[i] = rng.nextInt(4);
        double phi = PhiMaxCalculator.phiMax(states);
        assertThat(Double.isFinite(phi)).isTrue();
    }

    @Provide
    Arbitrary<int[]> stateArrays() {
        return Arbitraries.integers().between(2, 8).flatMap(length ->
            Arbitraries.integers().between(0, 7).array(int[].class).ofSize(length));
    }

    @Provide
    Arbitrary<int[]> smallArrays() {
        return Arbitraries.integers().between(2, 6).flatMap(length ->
            Arbitraries.integers().between(0, 3).array(int[].class).ofSize(length));
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> values() {
        return Arbitraries.integers().between(0, 7);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
