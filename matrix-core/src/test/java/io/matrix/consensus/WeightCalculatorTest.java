package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeightCalculatorTest {

    @Test
    void shouldReturnDefaultWeightForNewNode() {
        double weight = WeightCalculator.defaultWeight();
        assertThat(weight).isEqualTo(0.1);
    }

    @Test
    void shouldComputeFullWeight() {
        double weight = WeightCalculator.compute(1.0, 1.0, 100, 100);

        assertThat(weight).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldComputePartialWeight() {
        double weight = WeightCalculator.compute(0.8, 0.9, 50, 100);

        assertThat(weight).isBetween(0.5, 0.9);
    }

    @Test
    void shouldClampWeightToRange() {
        double weight = WeightCalculator.compute(2.0, 2.0, 200, 100);

        assertThat(weight).isBetween(0.0, 1.0);
    }

    @Test
    void shouldGiveZeroWeightForZeroContributions() {
        double weight = WeightCalculator.compute(0.0, 0.0, 0, 100);

        assertThat(weight).isEqualTo(0.0);
    }

    @Test
    void shouldHandleMaxContributionsZero() {
        double weight = WeightCalculator.compute(0.5, 0.5, 0, 0);

        assertThat(weight).isBetween(0.3, 0.4);
    }
}
