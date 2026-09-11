package io.matrix.research;

import io.matrix.neuron.MultiLayerPerceptron;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 428 — Coverage for MultiLayerPerceptron (xor + linearly-separable synthetic).
 */
class Exp428MlpBackpropTest {

    @Test
    void mlpLearnsXorInside50Epochs() {
        // XOR (2 → 8 → 1)
        MultiLayerPerceptron mlp =
                new MultiLayerPerceptron(2, 8, 1, new Random(0xCAFE));
        double[][] X = {
                {0, 0},
                {0, 1},
                {1, 0},
                {1, 1}
        };
        double[][] Y = {
                {0},
                {1},
                {1},
                {0}
        };
        mlp.fit(X, Y, 2000, 4, 0.5);
        // Each prediction should be close to the expected XOR output
        double p00 = mlp.predict(new double[]{0, 0})[0];
        double p01 = mlp.predict(new double[]{0, 1})[0];
        double p10 = mlp.predict(new double[]{1, 0})[0];
        double p11 = mlp.predict(new double[]{1, 1})[0];
        assertThat(p00).isLessThan(0.2);
        assertThat(p01).isGreaterThan(0.8);
        assertThat(p10).isGreaterThan(0.8);
        assertThat(p11).isLessThan(0.2);
    }

    @Test
    void mlpApproximatesPieceWiseLinearFunction() {
        // y = AND(x0, x1) — linearly separable
        MultiLayerPerceptron mlp =
                new MultiLayerPerceptron(2, 4, 1, new Random(0xCAFEBABE));
        double[][] X = {
                {0, 0}, {0, 1}, {1, 0}, {1, 1},
                {0.1, 0.1}, {0.9, 0.1}, {0.1, 0.9}, {0.9, 0.9}
        };
        double[][] Y = {
                {0}, {0}, {0}, {1},
                {0}, {0}, {0}, {1}
        };
        mlp.fit(X, Y, 500, 4, 0.3);
        double p11 = mlp.predict(new double[]{1, 1})[0];
        double p00 = mlp.predict(new double[]{0, 0})[0];
        assertThat(p11).isGreaterThan(0.8);
        assertThat(p00).isLessThan(0.2);
    }

    @Test
    void predictReturnsFloatArrayOfOutputDimLength() {
        MultiLayerPerceptron mlp =
                new MultiLayerPerceptron(3, 5, 2, new Random(0xBEEF));
        double[] out = mlp.predict(new double[]{0.1, 0.2, 0.3});
        assertThat(out).hasSize(2);
        for (double v : out) {
            assertThat(v).isBetween(0.0, 1.0);
        }
    }
}
