package io.matrix.research;

import io.matrix.neuron.KalmanStateEstimator;
import io.matrix.neuron.ThompsonSampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 371 — DESIGN-34/35 implementations (Thompson sampling + Kalman filter).
 */
class Exp371ThompsonKalmanTest {

    @Test
    void thompsonSamplingPicksBestArm() {
        // Arm 0: 90% success rate, Arm 1: 10% success rate
        int[] successes = {90, 10};
        int[] failures = {10, 90};
        // Sample many times; arm 0 should win much more often
        int arm0Wins = 0;
        for (long seed = 0; seed < 50; seed++) {
            int chosen = ThompsonSampler.sample(successes, failures, seed);
            if (chosen == 0) arm0Wins++;
        }
        // Arm 0 should win > 80% of the time
        assertThat(arm0Wins).isGreaterThan(40);
    }

    @Test
    void thompsonDeterministicWithSeed() {
        int[] successes = {5, 5, 5};
        int[] failures = {5, 5, 5};
        // Same seed → same arm
        int arm1 = ThompsonSampler.sample(successes, failures, 0xCAFE);
        int arm2 = ThompsonSampler.sample(successes, failures, 0xCAFE);
        assertThat(arm1).isEqualTo(arm2);
    }

    @Test
    void thompsonEqualArms() {
        // All arms identical → uniform distribution
        int[] successes = {5, 5, 5};
        int[] failures = {5, 5, 5};
        int[] counts = new int[3];
        for (long seed = 0; seed < 90; seed++) {
            int chosen = ThompsonSampler.sample(successes, failures, seed);
            counts[chosen]++;
        }
        // Each arm should be ~30 (with some variance)
        for (int c : counts) {
            assertThat(c).isBetween(15, 50);
        }
    }

    @Test
    void thompsonUpdateCounts() {
        int[] counts = {0, 0, 0};
        int[] updated = ThompsonSampler.updateCounts(counts, 1, 0.8);
        assertThat(updated[1]).isEqualTo(1);
        assertThat(updated[0]).isZero();
    }

    @Test
    void kalmanPredictIncreasesVariance() {
        var prev = new KalmanStateEstimator.Estimate(0.5, 0.01);
        var predicted = KalmanStateEstimator.predict(prev, 0.1);
        assertThat(predicted.value()).isEqualTo(0.5);
        assertThat(predicted.variance()).isGreaterThan(prev.variance());
    }

    @Test
    void kalmanUpdateReducesVariance() {
        var prev = new KalmanStateEstimator.Estimate(0.5, 0.5);
        var updated = KalmanStateEstimator.update(prev, 0.7, 0.1);
        assertThat(updated.variance()).isLessThan(prev.variance());
        // Value moved toward observation
        assertThat(updated.value()).isBetween(0.5, 0.7);
    }

    @Test
    void kalmanFilterConvergesToObservation() {
        // Track a constant value 0.7 with noisy observations
        var estimate = new KalmanStateEstimator.Estimate(0.0, 1.0);
        double trueValue = 0.7;
        for (int step = 0; step < 50; step++) {
            double obs = trueValue + (Math.random() - 0.5) * 0.2;
            estimate = KalmanStateEstimator.filterStep(estimate, obs, 0.001, 0.05);
        }
        // After 50 steps, estimate should be close to 0.7
        assertThat(Math.abs(estimate.value() - trueValue)).isLessThan(0.1);
        // Variance should have decreased significantly
        assertThat(estimate.variance()).isLessThan(0.05);
    }
}
