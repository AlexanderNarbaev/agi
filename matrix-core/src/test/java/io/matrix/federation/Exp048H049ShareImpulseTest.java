package io.matrix.federation;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.32 — H-049 share-impulse verification (RUN 48).
 */
class Exp048H049ShareImpulseTest {

    @Test
    void shareImpulseFiresWhenUtilityExceedsThreshold() {
        ShareImpulseFirer firer = new ShareImpulseFirer(0.5);
        boolean fired = firer.observe(0.7, true, true);
        assertThat(fired).isTrue();
        assertThat(firer.firedCount()).isEqualTo(1);
    }

    @Test
    void shareImpulseDoesNotFireWhenUtilityBelowThreshold() {
        ShareImpulseFirer firer = new ShareImpulseFirer(0.5);
        boolean fired = firer.observe(0.3, true, false);
        assertThat(fired).isFalse();
        assertThat(firer.firedCount()).isZero();
    }

    @Test
    void shareImpulseDoesNotFireWhenNotAccepted() {
        ShareImpulseFirer firer = new ShareImpulseFirer(0.5);
        boolean fired = firer.observe(0.7, false, false);
        assertThat(fired).isFalse();
    }

    @Test
    void precisionAndRecallOnBalancedStream() {
        ShareImpulseFirer firer = new ShareImpulseFirer(0.5);
        // Feed 100 events: utility ∈ {0.3, 0.7}, accept ∈ {true, false}
        Random rng = new Random(42);
        int truePositives = 0, falsePositives = 0, falseNegatives = 0;
        for (int i = 0; i < 100; i++) {
            double utility = rng.nextBoolean() ? 0.3 : 0.7;
            boolean accepted = rng.nextBoolean();
            boolean groundTruth = utility > 0.5 && accepted;
            boolean fired = firer.observe(utility, accepted, groundTruth);
            if (fired && groundTruth) truePositives++;
            if (fired && !groundTruth) falsePositives++;
            if (!fired && groundTruth) falseNegatives++;
        }
        double precision = (double) truePositives / Math.max(1, truePositives + falsePositives);
        double recall = (double) truePositives / Math.max(1, truePositives + falseNegatives);
        System.out.printf("[EXP-MATRIX.32] H-049 precision=%.3f recall=%.3f%n", precision, recall);

        // H-049 acceptance: precision ≥ 0.8.
        assertThat(precision)
                .as("H-049 precision ≥ 0.8 (got %.3f)", precision)
                .isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void thresholdControlsFiringRate() {
        ShareImpulseFirer low = new ShareImpulseFirer(0.1);
        ShareImpulseFirer high = new ShareImpulseFirer(0.9);
        // Feed same stream to both.
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            double utility = rng.nextDouble();
            boolean accepted = rng.nextBoolean();
            low.observe(utility, accepted, false);
            high.observe(utility, accepted, false);
        }
        // Lower threshold should fire more.
        assertThat(low.firedCount()).isGreaterThan(high.firedCount());
    }

    @Test
    void counterConsistencyUnderConcurrentAccess() throws InterruptedException {
        ShareImpulseFirer firer = new ShareImpulseFirer(0.5);
        int threads = 8;
        int iterations = 100;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < iterations; i++) {
                    firer.observe(0.7, true, true);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        // Total fired = threads × iterations (every event fires).
        assertThat(firer.firedCount()).isEqualTo((long) threads * iterations);
        assertThat(firer.totalAccepted()).isEqualTo((long) threads * iterations);
    }
}
