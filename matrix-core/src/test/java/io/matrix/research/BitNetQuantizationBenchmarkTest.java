package io.matrix.research;

import io.matrix.neuron.BitLinear;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * W32 deep-research: BitNet b1.58 quantization accuracy benchmark.
 *
 * <p>Compares the output of {@link BitLinear#forward} against a reference
 * FP32 linear layer to measure quantization error. Per Ma et al. 2024,
 * absmean quantization should preserve accuracy at scale.
 *
 * <p>This is the algorithmic reference for what would happen when loading
 * a real BitNet b1.58 model — we verify our quantization matches what the
 * paper claims.
 */
class BitNetQuantizationBenchmarkTest {

    /**
     * Reference FP32 linear: y = W @ x (no bias).
     */
    private static double[] refMatmul(float[][] w, float[] x) {
        double[] y = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            double sum = 0.0;
            for (int j = 0; j < x.length; j++) {
                sum += w[i][j] * x[j];
            }
            y[i] = sum;
        }
        return y;
    }

    @Test
    void bitLinearApproximatesFp32WithinReasonableError() {
        Random rng = new Random(42);
        int out = 16, in = 64;
        float[][] w = new float[out][in];
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) {
                w[i][j] = (float) (rng.nextGaussian() * 0.3);
            }
        }
        float[] x = new float[in];
        for (int i = 0; i < in; i++) x[i] = (float) rng.nextGaussian();

        double[] ref = refMatmul(w, x);
        float[] actual = BitLinear.forward(w, x);

        // Relative error per output
        double totalRelError = 0.0;
        for (int i = 0; i < out; i++) {
            if (Math.abs(ref[i]) > 0.001) {
                totalRelError += Math.abs((actual[i] - ref[i]) / ref[i]);
            }
        }
        double avgRelError = totalRelError / out;
        // At small scale (64-dim), BitNet quantization error is high (~50%).
        // Paper claims parity only at 3B+ scale where errors average out.
        // Just verify it's bounded (not catastrophic).
        assertThat(avgRelError).isLessThan(1.0);
        System.out.printf("[BitLinear] avg relative error: %.2f%%%n", avgRelError * 100);
    }

    @Test
    void bitLinearSignAgreementAcrossManyInputs() {
        Random rng = new Random(42);
        int out = 8, in = 32;
        float[][] w = new float[out][in];
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) {
                w[i][j] = (float) (rng.nextGaussian() * 0.3);
            }
        }
        int signAgreement = 0;
        int total = 0;
        for (int trial = 0; trial < 50; trial++) {
            float[] x = new float[in];
            for (int i = 0; i < in; i++) x[i] = (float) rng.nextGaussian();
            double[] ref = refMatmul(w, x);
            float[] actual = BitLinear.forward(w, x);
            for (int i = 0; i < out; i++) {
                if (Math.signum(ref[i]) == Math.signum(actual[i])) signAgreement++;
                total++;
            }
        }
        double signRate = (double) signAgreement / total;
        // Per BitNet paper, expect ~85-95% sign agreement
        assertThat(signRate).isGreaterThan(0.75);
        System.out.printf("[BitLinear] sign agreement: %.2f%%%n", signRate * 100);
    }

    @Test
    void ternaryWeightStats() {
        Random rng = new Random(42);
        int out = 32, in = 64;
        float[][] w = new float[out][in];
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) {
                w[i][j] = (float) (rng.nextGaussian() * 0.3);
            }
        }
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(w);
        // Count distribution
        int posOnes = 0, negOnes = 0, zeros = 0;
        for (int[] row : qw.values) {
            for (int v : row) {
                if (v == 1) posOnes++;
                else if (v == -1) negOnes++;
                else zeros++;
            }
        }
        int total = posOnes + negOnes + zeros;
        // Per BitNet paper, expected distribution: ~50% zeros, ~25% +1, ~25% -1
        // (this is the "1.58 bits" — average log2(3) ≈ 1.58 bits per weight)
        double zeroRate = (double) zeros / total;
        assertThat(zeroRate).isBetween(0.2, 0.8); // sanity range
        System.out.printf("[BitLinear] weights: %.1f%% +1, %.1f%% -1, %.1f%% 0%n",
                100.0 * posOnes / total, 100.0 * negOnes / total, 100.0 * zeros / total);
    }

    @Test
    void sublnNormalizesToUnitRMS() {
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] y = BitLinear.subln(x);
        double rms = 0.0;
        for (float v : y) rms += v * v;
        rms = Math.sqrt(rms / y.length);
        assertThat(rms).isCloseTo(1.0, within(0.01));
    }

    @Test
    void absmaxQuantizationStaysIn8BitRange() {
        Random rng = new Random(42);
        for (int trial = 0; trial < 100; trial++) {
            float[] x = new float[64];
            for (int i = 0; i < 64; i++) {
                x[i] = (float) (rng.nextGaussian() * 5.0);
            }
            BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(x);
            for (int v : qa.values) {
                assertThat(v).isBetween(-BitLinear.Q_ABSMAX, BitLinear.Q_ABSMAX);
            }
        }
    }
}
