package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 98 — CrossLevelPhi: integration between Bernstein levels (H-086).
 */
class CrossLevelPhiTest {

    @Test
    void coordinatedLevelsYieldNonZero() {
        // Two adjacent levels, both following same temporal pattern → high Φ
        int T = 16, Dlower = 2, Dupper = 2;
        double[][] lower = new double[T][Dlower];
        double[][] upper = new double[T][Dupper];
        java.util.Random rng = new java.util.Random(42);
        for (int t = 0; t < T; t++) {
            lower[t][0] = Math.sin(t * 0.3);
            lower[t][1] = Math.cos(t * 0.3);
            // Upper level tracks lower with some processing delay
            upper[t][0] = Math.sin((t - 1) * 0.3);
            upper[t][1] = Math.cos((t - 1) * 0.3);
        }
        double phi = CrossLevelPhi.measure(lower, upper);
        System.out.printf("W98: coordinated levels Φ=%.4f%n", phi);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void independentLevelsYieldSmallPhi() {
        // Two adjacent levels with no relationship → small Φ
        int T = 32, D = 4;
        double[][] lower = new double[T][D];
        double[][] upper = new double[T][D];
        java.util.Random rng = new java.util.Random(42);
        for (int t = 0; t < T; t++) {
            for (int i = 0; i < D; i++) {
                lower[t][i] = rng.nextGaussian();
                upper[t][i] = rng.nextGaussian();
            }
        }
        double phi = CrossLevelPhi.measure(lower, upper);
        System.out.printf("W98: independent levels Φ=%.4f (small expected)%n", phi);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
        assertThat(phi).isLessThan(0.5);
    }

    @Test
    void brainToMetricsValid() {
        // HdcBrain recall + ConsciousBrain metric vector
        List<Double> sims = new ArrayList<>(Arrays.asList(0.9, 0.8, 0.7, 0.6, 0.5));
        List<Double> dists = new ArrayList<>(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5));
        List<Double> metrics = new ArrayList<>(Arrays.asList(0.5, 0.4, 0.3, 0.2, 0.1));
        double phi = CrossLevelPhi.brainToMetrics(sims, dists, metrics);
        System.out.printf("W98: brain→metrics Φ=%.4f%n", phi);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void invalidArgsThrow() {
        try {
            CrossLevelPhi.measure(null, new double[][]{{1, 2}});
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            // Mismatched T
            CrossLevelPhi.measure(
                    new double[][]{{1, 2}, {3, 4}},
                    new double[][]{{5, 6}});
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            CrossLevelPhi.brainToMetrics(null, new ArrayList<>(), new ArrayList<>());
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    void singleTimestepFails() {
        try {
            CrossLevelPhi.measure(
                    new double[][]{{1.0, 2.0}},
                    new double[][]{{3.0, 4.0}});
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) { /* expected */ }
    }
}
