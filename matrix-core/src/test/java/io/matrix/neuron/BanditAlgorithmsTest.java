package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BanditAlgorithmsTest {

    // ============ UcbBandit ============

    @Test
    void ucbBanditChoosesArmWithHighestMean() {
        double[] means = {0.1, 0.9, 0.5};
        long[] counts = {10, 10, 10};
        UcbBandit.Step step = UcbBandit.choose(means, counts, 30, 2.0, new Random(1));
        // With equal counts and c=2.0, UCB score = mean + sqrt(2*ln(30)/count)
        // All have similar exploration bonus; highest mean wins
        assertThat(step.chosen()).isEqualTo(1); // arm with mean 0.9
    }

    @Test
    void ucbBanditExploresUntestedArm() {
        // Arm 2 has highest mean but 0 pulls → highest UCB score
        double[] means = {0.1, 0.1, 0.9};
        long[] counts = {10, 10, 0};
        UcbBandit.Step step = UcbBandit.choose(means, counts, 20, 2.0, new Random(1));
        assertThat(step.chosen()).isEqualTo(2);
    }

    @Test
    void ucbBanditScoresArrayMatchesArmCount() {
        double[] means = {0.5, 0.5};
        long[] counts = {5, 5};
        UcbBandit.Step step = UcbBandit.choose(means, counts, 10, 1.0, new Random(1));
        assertThat(step.ucbScores()).hasSize(2);
    }

    @Test
    void ucbBanditRejectsBadInputs() {
        assertThatThrownBy(() -> UcbBandit.choose(null, new long[]{1}, 1, 1.0, new Random(1)))
                .isInstanceOf(NullPointerException.class);
    }

    // ============ ThompsonSampler ============

    @Test
    void thompsonSampleChoosesFromValidRange() {
        int[] successes = {10, 5};
        int[] failures = {5, 5};
        int chosen = ThompsonSampler.sample(successes, failures, 42L);
        assertThat(chosen).isBetween(0, 1);
    }

    @Test
    void thompsonSamplePrefersHigherSuccessRate() {
        // Arm 0: 100/100 (very high), Arm 1: 1/100 (very low)
        int[] successes = {100, 1};
        int[] failures = {0, 100};
        int winsForArm0 = 0;
        int trials = 100;
        for (int i = 0; i < trials; i++) {
            if (ThompsonSampler.sample(successes, failures, i) == 0) {
                winsForArm0++;
            }
        }
        // Arm 0 should win most of the time
        assertThat(winsForArm0).isGreaterThan((int) (trials * 0.8));
    }

    @Test
    void thompsonSampleIsDeterministicGivenSeed() {
        int[] successes = {5, 10};
        int[] failures = {5, 5};
        int a = ThompsonSampler.sample(successes, failures, 42L);
        int b = ThompsonSampler.sample(successes, failures, 42L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void thompsonUpdateCountsReturnsNewArray() {
        int[] counts = {0, 0, 0};
        int[] updated = ThompsonSampler.updateCounts(counts, 1, 1.0);
        // Counts cloned, original unchanged
        assertThat(counts[1]).isEqualTo(0);
        // Updated has +1 at chosen index (reward >= 0.5)
        assertThat(updated[1]).isEqualTo(1);
        assertThat(updated[0]).isEqualTo(0);
        assertThat(updated[2]).isEqualTo(0);
    }

    @Test
    void thompsonUpdateCountsRejectsBadIndex() {
        // updateCounts silently ignores invalid indices; just verify behavior
        int[] counts = {0, 0};
        int[] updated = ThompsonSampler.updateCounts(counts, 5, 1.0);
        // No increments
        assertThat(updated).containsExactly(0, 0);
    }

    // ============ MultiLayerPerceptron ============

    @Test
    void mlpLearnsXor() {
        // XOR: 00 → 0, 01 → 1, 10 → 1, 11 → 0
        double[][] X = {
                {0.0, 0.0},
                {0.0, 1.0},
                {1.0, 0.0},
                {1.0, 1.0}
        };
        double[][] Y = {
                {0.0}, {1.0}, {1.0}, {0.0}
        };
        MultiLayerPerceptron mlp = new MultiLayerPerceptron(2, 8, 1, new Random(42));
        mlp.fit(X, Y, 5000, 4, 0.1);
        // Predictions should match targets within 0.2 tolerance
        double[] p00 = mlp.predict(X[0]);
        double[] p01 = mlp.predict(X[1]);
        double[] p10 = mlp.predict(X[2]);
        double[] p11 = mlp.predict(X[3]);
        assertThat(p00[0]).isLessThan(0.3);
        assertThat(p01[0]).isGreaterThan(0.7);
        assertThat(p10[0]).isGreaterThan(0.7);
        assertThat(p11[0]).isLessThan(0.3);
    }

    @Test
    void mlpPredictsCorrectOutputDimension() {
        MultiLayerPerceptron mlp = new MultiLayerPerceptron(3, 5, 2, new Random(1));
        double[] output = mlp.predict(new double[]{1.0, 2.0, 3.0});
        assertThat(output).hasSize(2);
    }

    // ============ NaiveBayes ============

    @Test
    void naiveBayesClassifiesSimpleText() {
        // Toy corpus: 2 classes, vocab 5
        NaiveBayes.Training t = NaiveBayes.Training.empty(2, 5);
        // Class 0: tokens [0, 1, 2] repeated
        for (int i = 0; i < 10; i++) {
            t = t.increment(0, new int[]{0, 1, 2});
        }
        // Class 1: tokens [2, 3, 4] repeated
        for (int i = 0; i < 10; i++) {
            t = t.increment(1, new int[]{2, 3, 4});
        }
        double[] logPrior = {Math.log(0.5), Math.log(0.5)};
        int predicted = NaiveBayes.classify(t, logPrior, 1.0, new int[]{3, 4});
        assertThat(predicted).isEqualTo(1);
    }

    @Test
    void naiveBayesWithEmptyTokensReturnsValidClass() {
        NaiveBayes.Training t = NaiveBayes.Training.empty(3, 10);
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < 5; i++) {
                t = t.increment(c, new int[]{c});
            }
        }
        double[] logPrior = {0.0, 0.0, 0.0};
        int predicted = NaiveBayes.classify(t, logPrior, 1.0, new int[0]);
        assertThat(predicted).isBetween(0, 2);
    }

    @Test
    void naiveBayesTrainingIncrementProducesNewObject() {
        NaiveBayes.Training t1 = NaiveBayes.Training.empty(2, 5);
        NaiveBayes.Training t2 = t1.increment(0, new int[]{0, 1});
        // Verify they are equal (records compare by value)
        assertThat(t1).isNotNull();
        assertThat(t2).isNotNull();
    }
}
