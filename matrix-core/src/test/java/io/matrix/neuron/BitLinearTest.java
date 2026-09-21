package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BitLinearTest {

    private static float[][] randomWeights(int out, int in, Random rng) {
        float[][] w = new float[out][in];
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) {
                w[i][j] = (float) (rng.nextGaussian() * 0.5);
            }
        }
        return w;
    }

    private static float[] randomInput(int in, Random rng) {
        float[] x = new float[in];
        for (int i = 0; i < in; i++) x[i] = (float) rng.nextGaussian();
        return x;
    }

    @Test
    void quantizeWeightAbsmeanProducesTernaryValues() {
        float[][] w = randomWeights(8, 16, new Random(1));
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        assertThat(qw.values.length).isEqualTo(8);
        assertThat(qw.values[0].length).isEqualTo(16);
        for (int[] row : qw.values) {
            for (int v : row) {
                assertThat(v).isBetween(-1, 1);
            }
        }
        assertThat(qw.gamma).isGreaterThan(0.0f);
    }

    @Test
    void quantizeWeightAbsmeanGammaIsMeanAbsValue() {
        float[][] w = {{1.0f, -2.0f, 3.0f, -4.0f}};
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        // mean(|w|) = (1 + 2 + 3 + 4) / 4 = 2.5
        assertThat(qw.gamma).isCloseTo(2.5f, within(1e-5f));
    }

    @Test
    void quantizeWeightAbsmeanClipsToPlusMinusOne() {
        // All weights >> γ, so all should quantize to ±1
        float[][] w = {{100.0f, -100.0f, 100.0f, -100.0f}};
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        assertThat(qw.values[0][0]).isEqualTo(1);
        assertThat(qw.values[0][1]).isEqualTo(-1);
        assertThat(qw.values[0][2]).isEqualTo(1);
        assertThat(qw.values[0][3]).isEqualTo(-1);
    }

    @Test
    void quantizeWeightAbsmeanZeroWeightsProduceZeros() {
        float[][] w = {{0.0f, 0.0f, 0.0f, 0.0f}};
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        assertThat(qw.gamma).isEqualTo(0.0f);
        for (int v : qw.values[0]) {
            assertThat(v).isEqualTo(0);
        }
    }

    @Test
    void quantizeWeightAbsmeanRejectsBadInput() {
        assertThatThrownBy(() -> BitLinear.quantizeWeightAbsmean(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.quantizeWeightAbsmean(new float[0][]))
                .isInstanceOf(IllegalArgumentException.class);
        float[][] ragged = {{1.0f, 2.0f}, {3.0f}};
        assertThatThrownBy(() -> BitLinear.quantizeWeightAbsmean(ragged))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantizeActivationAbsmaxProduces8BitValues() {
        float[] x = {1.0f, -2.0f, 3.0f, -4.0f, 0.5f};
        BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(x);
        assertThat(qa.values).hasSize(5);
        for (int v : qa.values) {
            assertThat(v).isBetween(-BitLinear.Q_ABSMAX, BitLinear.Q_ABSMAX);
        }
        // absmax = 4, Q=127, scale = 31.75
        // values: 31.75, -63.5, 95.25, -127, 15.875 → round → 32, -64, 95, -127, 16
        assertThat(qa.values[0]).isEqualTo(32);
        assertThat(qa.values[1]).isEqualTo(-63);
        assertThat(qa.values[2]).isEqualTo(95);
        assertThat(qa.values[3]).isEqualTo(-127);
        assertThat(qa.values[4]).isEqualTo(16);
    }

    @Test
    void quantizeActivationAbsmaxPreservesSignOfMax() {
        float[] x = {-10.0f, 5.0f, -3.0f};
        BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(x);
        assertThat(qa.alpha).isEqualTo(10.0f);
        // x = [-10, 5, -3]; scale = 127/10 = 12.7
        // -10 * 12.7 = -127 → round → -127
        // 5 * 12.7 = 63.5 → round → 64
        // -3 * 12.7 = -38.1 → round → -38
        assertThat(qa.values[0]).isEqualTo(-127);
        assertThat(qa.values[1]).isEqualTo(64);
        assertThat(qa.values[2]).isEqualTo(-38);
    }

    @Test
    void quantizeActivationAbsmaxZeroInputProducesZeros() {
        float[] x = {0.0f, 0.0f, 0.0f};
        BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(x);
        assertThat(qa.alpha).isEqualTo(0.0f);
        for (int v : qa.values) {
            assertThat(v).isEqualTo(0);
        }
    }

    @Test
    void quantizeActivationAbsmaxRejectsBadInput() {
        assertThatThrownBy(() -> BitLinear.quantizeActivationAbsmax(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.quantizeActivationAbsmax(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matmulComputesCorrectDotProducts() {
        int[][] qw = {{1, -1, 0}, {0, 1, 1}};
        int[] qx = {2, 3, 4};
        int[] y = BitLinear.matmul(qw, qx);
        // y[0] = 1*2 + (-1)*3 + 0*4 = -1
        // y[1] = 0*2 + 1*3 + 1*4 = 7
        assertThat(y[0]).isEqualTo(-1);
        assertThat(y[1]).isEqualTo(7);
    }

    @Test
    void matmulRejectsBadInput() {
        assertThatThrownBy(() -> BitLinear.matmul(null, new int[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.matmul(new int[0][], new int[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        int[][] qw = {{1, 2}};
        assertThatThrownBy(() -> BitLinear.matmul(qw, new int[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
        int[][] ragged = {{1, 2}, {3}};
        assertThatThrownBy(() -> BitLinear.matmul(ragged, new int[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sublnProducesZeroMeanAndUnitVariance() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] y = BitLinear.subln(x);
        assertThat(y).hasSize(5);
        // After SubLN: y = x / sqrt(mean(x²))
        // mean(x²) = (1+4+9+16+25)/5 = 11
        // rms = sqrt(11 + ε)
        float rms = (float) Math.sqrt(11.0 + BitLinear.SUBLN_EPS);
        assertThat(y[0]).isCloseTo(1.0f / rms, within(1e-5f));
        assertThat(y[4]).isCloseTo(5.0f / rms, within(1e-5f));
    }

    @Test
    void sublnHandlesZeroInput() {
        float[] x = {0.0f, 0.0f, 0.0f};
        float[] y = BitLinear.subln(x);
        // rms = sqrt(0 + ε) ≈ sqrt(ε), so y ≈ 0
        for (float v : y) {
            assertThat(Math.abs(v)).isLessThan(1e-3f);
        }
    }

    @Test
    void sublnRejectsBadInput() {
        assertThatThrownBy(() -> BitLinear.subln(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.subln(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forwardOutputShapeIsOutFeatures() {
        float[][] w = randomWeights(8, 16, new Random(1));
        float[] x = randomInput(16, new Random(2));
        float[] y = BitLinear.forward(w, x);
        assertThat(y).hasSize(8);
    }

    @Test
    void forwardIsApproximatelyLinearInInputs() {
        float[][] w = randomWeights(4, 8, new Random(1));
        float[] x = randomInput(8, new Random(2));
        float[] y1 = BitLinear.forward(w, x);
        // 2x input → SubLN doesn't change scale (it's normalized), so
        // quantized activations are the same and output is identical.
        float[] x2 = new float[x.length];
        for (int i = 0; i < x.length; i++) x2[i] = 2.0f * x[i];
        float[] y2 = BitLinear.forward(w, x2);
        for (int i = 0; i < y1.length; i++) {
            assertThat(y1[i]).isCloseTo(y2[i], within(1e-3f));
        }
    }

    @Test
    void forwardProducesFiniteValuesForLargeWeights() {
        float[][] w = randomWeights(4, 8, new Random(1));
        for (int i = 0; i < w.length; i++) {
            for (int j = 0; j < w[0].length; j++) {
                w[i][j] *= 100.0f; // amplify
            }
        }
        float[] x = randomInput(8, new Random(2));
        float[] y = BitLinear.forward(w, x);
        for (float v : y) {
            assertThat(Float.isFinite(v)).isTrue();
        }
    }

    @Test
    void forwardRejectsBadInput() {
        float[][] w = {{1.0f, 2.0f}, {3.0f, 4.0f}};
        assertThatThrownBy(() -> BitLinear.forward(null, new float[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.forward(w, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinear.forward(w, new float[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantizedWeightToStringContainsShape() {
        float[][] w = {{1.0f, 2.0f}, {3.0f, 4.0f}};
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        String s = qw.toString();
        assertThat(s).contains("gamma=").contains("shape=2x2");
    }

    @Test
    void quantizedActivationToStringContainsLength() {
        BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(new float[]{1, 2, 3});
        String s = qa.toString();
        assertThat(s).contains("alpha=").contains("len=3");
    }
}
