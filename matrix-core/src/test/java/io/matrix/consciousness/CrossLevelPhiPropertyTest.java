package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W145 — CrossLevelPhi property-based tests.
 */
class CrossLevelPhiPropertyTest {

    @Property(tries = 30)
    void propertyMeasureIsFinite(@ForAll("stateMatrices") double[][] lower,
                                  @ForAll("stateMatrices") double[][] upper) {
        if (lower.length < 4 || upper.length < 4) return;
        if (lower.length != upper.length) return;
        double phi = CrossLevelPhi.measure(lower, upper);
        assertThat(Double.isFinite(phi)).isTrue();
    }

    @Property(tries = 30)
    void propertyMeasureHandlesRandomInputs(@ForAll("anySeed") int seed,
                                            @ForAll("matrixSizes") int size) {
        if (size < 4 || size > 32) return;
        Random rng = new Random(seed);
        double[][] lower = new double[size][2];
        double[][] upper = new double[size][2];
        for (int i = 0; i < size; i++) {
            lower[i][0] = rng.nextDouble();
            lower[i][1] = rng.nextDouble();
            upper[i][0] = rng.nextDouble();
            upper[i][1] = rng.nextDouble();
        }
        double phi = CrossLevelPhi.measure(lower, upper);
        assertThat(Double.isFinite(phi)).isTrue();
    }

    @Property(tries = 20)
    void propertyMeasureThrowsOnLengthMismatch() {
        double[][] a = new double[4][2];
        double[][] b = new double[5][2];
        // This should throw
        try {
            CrossLevelPhi.measure(a, b);
            assertThat(false).as("Expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(true).isTrue();
        }
    }

    @Provide
    Arbitrary<double[][]> stateMatrices() {
        return Arbitraries.integers().between(4, 16).flatMap(rows ->
            Arbitraries.doubles().between(0.0, 1.0).array(double[].class)
                .ofSize(2).array(double[][].class).ofSize(rows));
    }

    @Provide
    Arbitrary<Integer> matrixSizes() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
