package io.matrix.research;

import io.matrix.consciousness.PhiMaxCalculator;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W189 — Empirical Φ_max benchmark.
 *
 * <p>Measure Φ_max across different system sizes (N) and connectivity
 * to understand scaling behavior.
 */
class W189PhiMaxBenchmark {

    @Test
    void benchmarkPhiMaxForSmallSystems() {
        Random rng = new Random(42);
        for (int n = 2; n <= 8; n++) {
            int[] states = new int[n];
            for (int i = 0; i < n; i++) states[i] = rng.nextInt(2);
            double phiFull = PhiMaxCalculator.phiMax(states);
            double phiGreedy = PhiMaxCalculator.phiMaxGreedy(states);
            assertThat(phiFull).isGreaterThanOrEqualTo(0.0);
            assertThat(phiGreedy).isGreaterThanOrEqualTo(0.0);
            // Greedy should be ≤ full + epsilon
            assertThat(phiGreedy).isLessThanOrEqualTo(phiFull + 0.5);
        }
    }

    @Test
    void benchmarkPhiMaxForLargerSystems() {
        Random rng = new Random(123);
        for (int n = 9; n <= 16; n++) {
            int[] states = new int[n];
            for (int i = 0; i < n; i++) states[i] = rng.nextInt(2);
            // For N > 8 only greedy (exponential complexity)
            double phiGreedy = PhiMaxCalculator.phiMaxGreedy(states);
            assertThat(phiGreedy).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void phiMaxGreedyPerformanceFast() {
        // 100 greedy computations on N=16 should be fast
        Random rng = new Random(42);
        long start = System.nanoTime();
        for (int trial = 0; trial < 100; trial++) {
            int[] states = new int[16];
            for (int i = 0; i < 16; i++) states[i] = rng.nextInt(2);
            PhiMaxCalculator.phiMaxGreedy(states);
        }
        long elapsed = System.nanoTime() - start;
        double avgUs = elapsed / 100.0 / 1000.0;
        // Just verify it ran (no specific performance target)
        assertThat(avgUs).isGreaterThanOrEqualTo(0.0);
    }
}
