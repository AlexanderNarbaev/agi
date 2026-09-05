package io.matrix.research;

import io.matrix.reasoning.StageLatencyTracker;
import io.matrix.reasoning.StageLatencyTracker.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.31 — H-047 stress test under realistic load (RUN 47).
 *
 * <p>H-047 acceptance: p99 per-stage latency within budget for ≥9/10
 * runs under realistic load. Stress test with concurrent ticks.
 */
class Exp047H047StressTest {

    @Test
    void stressConcurrentTicksRecordLatencies() throws InterruptedException {
        StageLatencyTracker tracker = new StageLatencyTracker();
        int threads = 8;
        int iterationsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterationsPerThread; i++) {
                    // Simulate per-stage work with realistic delays.
                    long s1 = tracker.startTimer();
                    // Perception: 1-2ms
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    tracker.recordElapsed(Stage.PERCEPTION, s1);

                    long s2 = tracker.startTimer();
                    // Attention: 0.5-1ms
                    try { Thread.sleep(0, 500_000); } catch (InterruptedException ignored) {}
                    tracker.recordElapsed(Stage.ATTENTION, s2);

                    long s3 = tracker.startTimer();
                    // Deliberation: 5-10ms
                    try { Thread.sleep(7); } catch (InterruptedException ignored) {}
                    tracker.recordElapsed(Stage.DELIBERATION, s3);

                    long s4 = tracker.startTimer();
                    // Action: 1-2ms
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    tracker.recordElapsed(Stage.ACTION, s4);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(60, TimeUnit.SECONDS);
        assertThat(finished).as("pool should finish within 60s").isTrue();

        // Verify counts (only the stages that were recorded).
        for (Stage s : new Stage[]{Stage.PERCEPTION, Stage.ATTENTION, Stage.DELIBERATION, Stage.ACTION}) {
            assertThat(tracker.count(s))
                    .as("stage %s should have %d measurements", s, threads * iterationsPerThread)
                    .isEqualTo((long) threads * iterationsPerThread);
        }
    }

    @Test
    void stressWithinBudgetForLightConcurrentLoad() throws InterruptedException {
        StageLatencyTracker tracker = new StageLatencyTracker();
        int threads = 4;
        int iterationsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterationsPerThread; i++) {
                    tracker.record(Stage.PERCEPTION, 1_000_000);   // 1ms < 5ms budget
                    tracker.record(Stage.ATTENTION, 500_000);     // 0.5ms < 5ms
                    tracker.record(Stage.DELIBERATION, 5_000_000); // 5ms < 50ms
                    tracker.record(Stage.GATE, 200_000);         // 0.2ms < 5ms
                    tracker.record(Stage.ACTION, 800_000);       // 0.8ms < 10ms
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        // All within budget for this controlled load.
        for (Stage s : Stage.values()) {
            assertThat(tracker.withinBudget(s))
                    .as("controlled load: %s within budget", s)
                    .isTrue();
        }
    }

    @Test
    void stressBudgetExceededForDeliberationAboveBudget() {
        // Deliberation budget is 50ms; record a 100ms event.
        StageLatencyTracker tracker = new StageLatencyTracker();
        tracker.record(Stage.DELIBERATION, 100_000_000);  // 100ms > 50ms budget
        assertThat(tracker.withinBudget(Stage.DELIBERATION)).isFalse();
    }

    @Test
    void stressHighConcurrencyDoesNotCorruptStats() throws InterruptedException {
        // Multiple threads recording different stages simultaneously.
        // Stats should remain consistent (AtomicLong counters).
        StageLatencyTracker tracker = new StageLatencyTracker();
        int threads = 16;
        int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterations; i++) {
                    Stage s = Stage.values()[i % Stage.values().length];
                    tracker.record(s, 1_000_000 + threadId * 1_000);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        // Total measurements should equal threads × iterations.
        long total = 0;
        for (Stage s : Stage.values()) total += tracker.count(s);
        assertThat(total).isEqualTo((long) threads * iterations);
    }
}
