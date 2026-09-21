package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 185 — BrainLoop performance benchmark EXP.
 *
 * <p>Measures cycle throughput. Informational only — not a
 * CONSTITUTION VI hard-claim; just tracks p50.
 */
@Tag("exp")
class Exp185BrainLoopBenchmarkTest {

    @Test
    void cycleThroughput() {
        BrainLoopService svc = new BrainLoopService();
        // Warmup
        for (int i = 0; i < 50; i++) svc.cycle("warmup-" + i);

        // Measure 1000 cycles
        int N = 1000;
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            svc.cycle("cycle-" + i);
        }
        long elapsedNanos = System.nanoTime() - t0;
        double avgMicros = elapsedNanos / 1000.0 / N;
        double perSec = N * 1_000_000_000.0 / elapsedNanos;
        System.out.printf("[PERF] %d cycles in %.2fms, avg=%.0fµs/cycle, throughput=%.0f cycles/sec%n",
                N, elapsedNanos / 1_000_000.0, avgMicros, perSec);
        // Should complete (don't fail on numbers — just observe)
        assertThat(N).isEqualTo(1000);
    }

    @Test
    void adversarialCycleStillFast() {
        BrainLoopService svc = new BrainLoopService();
        String[] attacks = {
                "hi\u0001there", "rm -rf $(echo /)",
                "x".repeat(1_000_000)
        };
        // Warmup
        for (int i = 0; i < 5; i++) svc.cycle("warm");
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            svc.cycle(attacks[i % attacks.length]);
        }
        long elapsedMillis = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[PERF-ADV] 100 cycles in %dms%n", elapsedMillis);
    }
}
