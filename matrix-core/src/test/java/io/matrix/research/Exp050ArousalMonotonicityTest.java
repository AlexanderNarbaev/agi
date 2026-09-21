package io.matrix.research;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-050 harness (H-050): arousal-update monotonicity on
 * strictly-increasing prediction-error stream.
 *
 * <p>arousal' = clamp(arousal + α·PE, 0, 1)
 */
class Exp050ArousalMonotonicityTest {

    @Test
    void arousalIsMonotonicOnIncreasingPE() {
        double alpha = 0.1;
        double arousal = 0.0;
        Random rng = new Random(0xACE);
        boolean monotone = true;
        double[] pes = new double[100];
        for (int i = 0; i < 100; i++) {
            pes[i] = (i + 1) * 0.01; // strictly increasing 0.01..1.00
        }
        for (double pe : pes) {
            double next = clamp(arousal + alpha * pe, 0.0, 1.0);
            if (next < arousal - 1e-12) monotone = false;
            arousal = next;
        }
        System.out.printf("[EXP-050] arousal after monotone ramp: %.3f (monotone=%s)%n",
                arousal, monotone);
        // sanity check: arousal should saturate near 1.0
        assertThat(arousal).isGreaterThan(0.5);
        assertThat(monotone).isTrue();
    }

    @Test
    void arousalBoundedInUnitInterval() {
        Random rng = new Random(0xACE);
        double arousal = 0.5;
        for (int i = 0; i < 1000; i++) {
            arousal = clamp(arousal + 0.1 * rng.nextDouble(), 0.0, 1.0);
        }
        assertThat(arousal).isBetween(0.0, 1.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}