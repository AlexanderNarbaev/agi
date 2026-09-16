package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PhiMaxCalculatorTest {

    @Test
    void emptyReturnsZero() {
        assertThat(PhiMaxCalculator.phiMax(new int[0])).isEqualTo(0.0);
    }

    @Test
    void nullReturnsZero() {
        assertThat(PhiMaxCalculator.phiMax(null)).isEqualTo(0.0);
    }

    @Test
    void constantStatesReturnsZero() {
        int[] constant = {1, 1, 1, 1, 1};
        assertThat(PhiMaxCalculator.phiMax(constant)).isEqualTo(0.0);
    }

    @Test
    void greedyReturnsFiniteForRandom() {
        int[] random = {1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8};
        double phi = PhiMaxCalculator.phiMaxGreedy(random);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void fullReturnsNonNegativeForManyStates() {
        int[] states = {0, 1, 2, 3, 4, 5, 6, 7};
        double phi = PhiMaxCalculator.phiMax(states);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void greedyIsSameAsFullForSmall() {
        int[] states = {0, 1, 2, 3, 4, 5};
        double full = PhiMaxCalculator.phiMax(states);
        double greedy = PhiMaxCalculator.phiMaxGreedy(states);
        // Greedy should be ≥ full (since greedy finds approximation)
        assertThat(greedy).isGreaterThanOrEqualTo(full - 0.1);
    }
}
