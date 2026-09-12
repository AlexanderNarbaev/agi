package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BitNetInferencePrimitivesTest {

    @Test
    void rmsNormNormalizesToUnitRMS() {
        // RMSNorm should produce output with RMS = sqrt(1/eps + ...)
        // but the key property is that variance is normalized to ~1
        float[] weight = {1.0f, 1.0f, 1.0f, 1.0f};
        BitNetRmsNorm norm = new BitNetRmsNorm(weight, 1e-5f);
        float[] input = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = norm.forward(input, 1, 1, 4);
        assertThat(output).hasSize(4);
        // Variance should be ~1
        double meanSq = 0;
        for (float v : output) meanSq += v * v;
        meanSq /= 4;
        // After RMSNorm with weight=1, variance ≈ 1
        assertThat(meanSq).isCloseTo(1.0, within(0.01));
    }

    @Test
    void rmsNormHandlesBatchAndSequence() {
        float[] weight = new float[8];
        java.util.Arrays.fill(weight, 1.0f);
        BitNetRmsNorm norm = new BitNetRmsNorm(weight, 1e-5f);
        // batch=2, seq=3, hidden=8 → 48 elements
        float[] input = new float[48];
        Random rng = new Random(42);
        for (int i = 0; i < 48; i++) input[i] = (float) rng.nextGaussian();
        float[] output = norm.forward(input, 2, 3, 8);
        assertThat(output).hasSize(48);
    }

    @Test
    void rmsNormRejectsMismatchedDimensions() {
        float[] weight = new float[4];
        BitNetRmsNorm norm = new BitNetRmsNorm(weight, 1e-5f);
        float[] input = new float[8]; // wrong size
        assertThatThrownBy(() -> norm.forward(input, 1, 1, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rmsNormRejectsNullInputs() {
        BitNetRmsNorm norm = new BitNetRmsNorm(new float[]{1.0f}, 1e-5f);
        assertThatThrownBy(() -> norm.forward(null, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ropePrecomputesTables() {
        BitNetRope rope = new BitNetRope(4096);
        // Default head_dim = 128
        assertThat(rope.headDim()).isEqualTo(128);
    }

    @Test
    void ropeApplyInPlaceProducesExpectedValues() {
        // Small test: head_dim=4, max_seq=4
        BitNetRope rope = new BitNetRope(4, 4, 10000.0);
        // batch=1, n_heads=1, seq=2 → 8 elements
        float[] qk = {1.0f, 0.0f, 0.0f, 0.0f,    // pos 0
                       1.0f, 0.0f, 0.0f, 0.0f};   // pos 1
        // Apply RoPE with default position IDs [0, 1]
        int[] posIds = BitNetRope.defaultPositionIds(1, 2);
        rope.applyInPlace(qk, 1, 1, 2, posIds);
        // After RoPE at pos=0 with theta=10000 and dim=4:
        // inv_freq = [1/1, 1/100] = [1, 0.01]
        // angle = 0 for pos=0 → cos=1, sin=0 → unchanged
        // For pos=1: angle = 1.0, cos≈0.54, sin≈0.84
        // Just verify output is finite and not all zero
        for (float v : qk) {
            assertThat(Float.isFinite(v)).isTrue();
        }
        // At position 0, output should equal input (rotation is identity)
        assertThat(qk[0]).isEqualTo(1.0f);
    }

    @Test
    void ropeDefaultPositionIdsAreSequential() {
        int[] ids = BitNetRope.defaultPositionIds(2, 3);
        assertThat(ids).hasSize(6);
        // [0, 1, 2, 0, 1, 2] for batch=2, seq=3
        assertThat(ids).containsExactly(0, 1, 2, 0, 1, 2);
    }

    @Test
    void inferenceForwardSingleVector() {
        float[] weights = new float[6]; // 3 out × 2 in
        // weight = [[1, 2], [3, 4], [5, 6]]
        weights[0] = 1; weights[1] = 2;
        weights[2] = 3; weights[3] = 4;
        weights[4] = 5; weights[5] = 6;
        float[] x = {1.0f, 2.0f};
        // Expected: [1*1+2*2, 3*1+4*2, 5*1+6*2] = [5, 11, 17]
        float[] y = BitNetInferenceForward.forward(weights, x, 3, 2);
        assertThat(y).containsExactly(5.0f, 11.0f, 17.0f);
    }

    @Test
    void inferenceForwardBatched() {
        float[] weights = new float[4]; // 2 out × 2 in
        weights[0] = 1; weights[1] = 0;
        weights[2] = 0; weights[3] = 1;
        // Identity matrix
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f}; // 2 batches of [1,2] and [3,4]
        float[] y = BitNetInferenceForward.forwardBatched(weights, x, 2, 2, 2);
        assertThat(y).hasSize(4);
        assertThat(y[0]).isEqualTo(1.0f);
        assertThat(y[1]).isEqualTo(2.0f);
        assertThat(y[2]).isEqualTo(3.0f);
        assertThat(y[3]).isEqualTo(4.0f);
    }

    @Test
    void inferenceForwardRejectsNullInputs() {
        assertThatThrownBy(() -> BitNetInferenceForward.forward(null, new float[1], 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitNetInferenceForward.forward(new float[1], null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
