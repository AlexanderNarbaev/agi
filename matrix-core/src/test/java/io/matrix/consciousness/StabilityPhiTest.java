package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 97 — StabilityPhi: variance/trend over sliding window (Ashby H-085).
 */
class StabilityPhiTest {

    @Test
    void constantSequenceZeroVariance() {
        List<Double> phi = new ArrayList<>();
        for (int i = 0; i < 10; i++) phi.add(0.5);
        assertThat(StabilityPhi.variance(phi, 10)).isEqualTo(0.0);
    }

    @Test
    void oscillatingSequencePositiveVariance() {
        List<Double> phi = new ArrayList<>(Arrays.asList(
                0.5, 0.6, 0.4, 0.7, 0.3, 0.6, 0.4, 0.5, 0.7, 0.3));
        double v = StabilityPhi.variance(phi, 10);
        System.out.printf("W97: oscillating variance=%.4f%n", v);
        assertThat(v).isGreaterThan(0.0);
        assertThat(v).isLessThan(0.1);
    }

    @Test
    void coefficientOfVariation() {
        List<Double> phi = new ArrayList<>(Arrays.asList(1.0, 1.1, 0.9, 1.0, 1.0));
        double cv = StabilityPhi.coefficientOfVariation(phi, 5);
        System.out.printf("W97: CV=%.4f%n", cv);
        assertThat(cv).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void isUltrastableDetection() {
        // Stable: low variance
        List<Double> stable = new ArrayList<>();
        for (int i = 0; i < 20; i++) stable.add(0.5 + 0.001 * i);
        assertThat(StabilityPhi.isUltrastable(stable, 20, 0.01)).isTrue();

        // Unstable: high variance
        List<Double> unstable = new ArrayList<>();
        for (int i = 0; i < 20; i++) unstable.add(0.1 * i + Math.sin(i));
        assertThat(StabilityPhi.isUltrastable(unstable, 20, 0.001)).isFalse();
    }

    @Test
    void trendPositiveWhenIntegrating() {
        List<Double> phi = new ArrayList<>(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5));
        double trend = StabilityPhi.trend(phi, 5);
        System.out.printf("W97: increasing trend=%.4f%n", trend);
        assertThat(trend).isGreaterThan(0.0);
    }

    @Test
    void trendNegativeWhenDisintegrating() {
        List<Double> phi = new ArrayList<>(Arrays.asList(0.5, 0.4, 0.3, 0.2, 0.1));
        double trend = StabilityPhi.trend(phi, 5);
        System.out.printf("W97: decreasing trend=%.4f%n", trend);
        assertThat(trend).isLessThan(0.0);
    }

    @Test
    void trendZeroForConstant() {
        List<Double> phi = new ArrayList<>();
        for (int i = 0; i < 5; i++) phi.add(0.5);
        assertThat(StabilityPhi.trend(phi, 5)).isEqualTo(0.0);
    }

    @Test
    void emptyOrTinyWindowSafe() {
        assertThat(StabilityPhi.variance(new ArrayList<>(), 10)).isEqualTo(0.0);
        assertThat(StabilityPhi.variance(null, 10)).isEqualTo(0.0);
        assertThat(StabilityPhi.variance(
                new ArrayList<>(Arrays.asList(0.1)), 10)).isEqualTo(0.0);
    }
}
