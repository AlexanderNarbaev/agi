package io.matrix.research;

import io.matrix.neuron.InfoBottleneck;
import io.matrix.neuron.PredictiveCoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 384 — DESIGN-41/43 implementations (Info Bottleneck, Predictive Coding).
 */
class Exp384InfoBottleneckPredictiveTest {

    @Test
    void infoBottleneckSelectsRelevantNeurons() {
        // Create activations where neuron 0 perfectly correlates with target
        int n = 50;
        int[][] activations = new int[n][5];
        int[] targets = new int[n];
        java.util.Random rng = new java.util.Random(0xCAFE);
        for (int i = 0; i < n; i++) {
            targets[i] = rng.nextInt(2);
            for (int j = 0; j < 5; j++) {
                // Neuron 0 = target exactly; others random
                activations[i][j] = (j == 0) ? targets[i] : rng.nextInt(2);
            }
        }
        int[] selected = InfoBottleneck.selectNeurons(activations, targets, 3);
        assertThat(selected).isNotEmpty();
        // Neuron 0 should be first (highest correlation)
        assertThat(selected[0]).isEqualTo(0);
    }

    @Test
    void infoBottleneckEmptyK() {
        int[][] activations = {{0, 1}, {1, 0}};
        int[] targets = {0, 1};
        int[] selected = InfoBottleneck.selectNeurons(activations, targets, 0);
        assertThat(selected).isEmpty();
    }

    @Test
    void predictiveCodingErrorIsDifference() {
        double[] obs = {1.0, 0.0, 1.0};
        double[] pred = {0.5, 0.5, 0.5};
        var err = PredictiveCoder.computeError(obs, pred);
        assertThat(err.magnitude()).isCloseTo(0.866,
                org.assertj.core.data.Offset.offset(0.01));
        assertThat(err.corrected()).isEqualTo(new double[]{0.5, -0.5, 0.5});
    }

    @Test
    void predictiveCodingZeroErrorForExactPrediction() {
        double[] obs = {0.5, 0.5, 0.5};
        double[] pred = {0.5, 0.5, 0.5};
        var err = PredictiveCoder.computeError(obs, pred);
        assertThat(err.magnitude()).isEqualTo(0.0);
    }

    @Test
    void predictiveCodingUpdateReducesError() {
        double[] pred = {0.0, 0.0};
        double[] error = {1.0, 0.5};
        double[] updated = PredictiveCoder.update(pred, error, 0.5);
        assertThat(updated[0]).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(updated[1]).isCloseTo(0.25,
                org.assertj.core.data.Offset.offset(1e-9));
    }
}
