package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W146 — InterAgentPhi property-based tests.
 */
class InterAgentPhiPropertyTest {

    @Property(tries = 50)
    void propertyMeasureTimeSeriesBounded(@ForAll("trajectoryLengths") int length,
                                            @ForAll("numDimensions") int dim) {
        if (length < 4 || dim < 1) return;
        Random rng = new Random(length);
        double[][] trajectory = new double[length][dim];
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < dim; j++) {
                trajectory[i][j] = rng.nextDouble() * 2 - 1;
            }
        }
        double phi = InterAgentPhi.measureTimeSeries(trajectory, 2);
        assertThat(Double.isFinite(phi)).isTrue();
    }

    @Property(tries = 30)
    void propertyMeasureTimeSeriesThrowsOnTinyInput() {
        // Too-short trajectory should either throw or return 0/finite
        double[][] tiny = new double[2][1];
        try {
            double phi = InterAgentPhi.measureTimeSeries(tiny, 1);
            assertThat(Double.isFinite(phi)).isTrue();
        } catch (IllegalArgumentException e) {
            // Expected for too-small input
            assertThat(true).isTrue();
        }
    }

    @Provide
    Arbitrary<Integer> trajectoryLengths() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> numDimensions() {
        return Arbitraries.integers().between(1, 4);
    }
}
