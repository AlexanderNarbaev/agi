package io.matrix.neuron;

import java.util.Arrays;

/**
 * RUN 439 — BitLinear primitives (DESIGN-54 §3, BitNet b1.58 paper, arxiv 2402.17764).
 *
 * <p>Low-level operations for ternary-weight 8-bit-activation linear layer:
 * <ul>
 *   <li>{@link #quantizeWeightAbsmean} — float weights → ternary
 *       {@code {-1, 0, +1}} using per-tensor absmean scaling
 *       (Ma et al. 2024 §2.1).</li>
 *   <li>{@link #quantizeActivationAbsmax} — float activations → 8-bit
 *       signed ints via per-token absmax.</li>
 *   <li>{@link #matmul} — integer matmul between quantized weight
 *       and activation tensors, output dequantized back to float.</li>
 *   <li>{@link #subln} — Substitute LayerNorm (no learnable params,
 *       per-token RMSNorm).</li>
 *   <li>{@link #forward} — full BitLinear layer: SubLN → matmul
 *       → with absmean-dequantized weights.</li>
 * </ul>
 *
 * <h2>Why absmean, not absmax</h2>
 * BitNet b1.58 uses per-tensor {@code γ = mean(|W|)} as the weight scale,
 * which is more robust to outliers than absmax. This lets activations be
 * quantized independently (absmax per token) and the scales cancel in the
 * matmul product.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure functions. No RNG, no wall-clock. Caller must supply weights and
 * inputs deterministically (e.g. from a fixed-seed training loop or a
 * pretrained model loader).
 *
 * <h2>Numerical guarantees</h2>
 * <ul>
 *   <li>{@code γ > 0} iff at least one weight is nonzero. Zero weights
 *       produce γ = 0; {@link #quantizeWeightAbsmean} returns an all-zero
 *       matrix and {@link #forward} returns zeros for that layer.</li>
 *   <li>8-bit activation range is symmetric {@code [-128, +127]}.</li>
 * </ul>
 */
public final class BitLinear {

    /** Maximum absolute value of an 8-bit signed quantization level. */
    public static final int Q_ABSMAX = 127;

    /** Epsilon for SubLN numerical stability. */
    public static final float SUBLN_EPS = 1e-5f;

    private BitLinear() {}

    /**
     * Quantize a float weight matrix into ternary {@code {-1, 0, +1}} using
     * the BitNet b1.58 absmean quantizer.
     *
     * <p>Formula: {@code γ = mean(|W|)}, then {@code W_int = round(clip(W/γ, -1, +1))}.
     * The {@code γ} is returned so the caller can dequantize outputs.
     *
     * @param weights flat row-major matrix, shape {@code [outFeatures, inFeatures]}
     * @return pair: {@code int[][]} ternary weights (same shape), and float {@code γ} scale
     */
    public static QuantizedWeight quantizeWeightAbsmean(float[][] weights) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("empty weights");
        }
        int out = weights.length;
        int in = weights[0].length;
        if (in == 0) {
            throw new IllegalArgumentException("zero in-features");
        }
        // Compute γ = mean(|W|)
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < out; i++) {
            if (weights[i] == null || weights[i].length != in) {
                throw new IllegalArgumentException("non-rectangular weights");
            }
            for (int j = 0; j < in; j++) {
                sum += Math.abs(weights[i][j]);
                count++;
            }
        }
        float gamma = (float) (sum / count);
        int[][] qw = new int[out][in];
        if (gamma <= 0.0f) {
            return new QuantizedWeight(qw, 0.0f);
        }
        float invGamma = 1.0f / gamma;
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) {
                float v = weights[i][j] * invGamma;
                if (v > 1.0f) v = 1.0f;
                else if (v < -1.0f) v = -1.0f;
                qw[i][j] = Math.round(v);
            }
        }
        return new QuantizedWeight(qw, gamma);
    }

    /**
     * Quantize a float activation vector into 8-bit signed ints using
     * absmax scaling.
     *
     * <p>Formula: {@code α = max(|x|)}, then
     * {@code x_int = round(clip(x * Q_ABSMAX / α, -Q_ABSMAX, +Q_ABSMAX))}.
     * The {@code α} is returned so the caller can dequantize outputs.
     *
     * @param x activation vector (e.g. one token's hidden state)
     */
    public static QuantizedActivation quantizeActivationAbsmax(float[] x) {
        if (x == null || x.length == 0) {
            throw new IllegalArgumentException("empty activation");
        }
        float absmax = 0.0f;
        for (float v : x) {
            float a = Math.abs(v);
            if (a > absmax) absmax = a;
        }
        int[] qx = new int[x.length];
        if (absmax <= 0.0f) {
            return new QuantizedActivation(qx, 0.0f);
        }
        float scale = (float) Q_ABSMAX / absmax;
        for (int i = 0; i < x.length; i++) {
            float v = x[i] * scale;
            if (v > (float) Q_ABSMAX) v = Q_ABSMAX;
            else if (v < -(float) Q_ABSMAX) v = -Q_ABSMAX;
            qx[i] = Math.round(v);
        }
        return new QuantizedActivation(qx, absmax);
    }

    /**
     * Integer matrix-vector product: {@code y[i] = Σⱼ qw[i][j] * qx[j]}.
     *
     * <p>Operates on ternary weights and 8-bit activations. Output is an
     * int (sum-of-products) which the caller scales back to float using
     * {@code γ * α / Q_ABSMAX}.
     *
     * @param qw quantized weight matrix {@code [out, in]}
     * @param qx quantized activation vector {@code [in]}
     */
    public static int[] matmul(int[][] qw, int[] qx) {
        if (qw == null || qw.length == 0) {
            throw new IllegalArgumentException("empty weights");
        }
        if (qx == null || qx.length == 0) {
            throw new IllegalArgumentException("empty activation");
        }
        int out = qw.length;
        int in = qw[0].length;
        if (qx.length != in) {
            throw new IllegalArgumentException("dim mismatch");
        }
        int[] y = new int[out];
        for (int i = 0; i < out; i++) {
            if (qw[i] == null || qw[i].length != in) {
                throw new IllegalArgumentException("non-rectangular weights");
            }
            int sum = 0;
            for (int j = 0; j < in; j++) {
                sum += qw[i][j] * qx[j];
            }
            y[i] = sum;
        }
        return y;
    }

    /**
     * Substitute LayerNorm (SubLN, Ma et al. 2024 §2.2).
     *
     * <p>Per-token RMSNorm without learnable parameters:
     * {@code y = x / sqrt(mean(x²) + eps)}. Returns a new array; input
     * unchanged.
     */
    public static float[] subln(float[] x) {
        if (x == null || x.length == 0) {
            throw new IllegalArgumentException("empty input");
        }
        double sumSq = 0.0;
        for (float v : x) {
            sumSq += (double) v * (double) v;
        }
        double meanSq = sumSq / x.length;
        float rms = (float) Math.sqrt(meanSq + SUBLN_EPS);
        float invRms = 1.0f / rms;
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = x[i] * invRms;
        }
        return y;
    }

    /**
     * Full BitLinear forward pass: SubLN → quantize activations → matmul with
     * quantized weights → dequantize.
     *
     * <p>Formula:
     * <pre>
     *   x_norm = SubLN(x)
     *   x_q = quantizeAbsmax(x_norm)
     *   y_int = matmul(W_q, x_q)
     *   y = y_int * (γ * α) / Q_ABSMAX
     * </pre>
     *
     * @param weights float weights, shape {@code [outFeatures, inFeatures]}
     * @param input   float input vector, length {@code inFeatures}
     * @return float output vector, length {@code outFeatures}
     */
    public static float[] forward(float[][] weights, float[] input) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("empty weights");
        }
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("empty input");
        }
        int in = weights[0].length;
        if (input.length != in) {
            throw new IllegalArgumentException("dim mismatch: input=" + input.length
                    + " inFeatures=" + in);
        }
        float[] normalized = subln(input);
        QuantizedWeight qw = quantizeWeightAbsmean(weights);
        QuantizedActivation qa = quantizeActivationAbsmax(normalized);
        int[] yInt = matmul(qw.values, qa.values);
        float scale = qw.gamma * qa.alpha / Q_ABSMAX;
        float[] y = new float[yInt.length];
        for (int i = 0; i < yInt.length; i++) {
            y[i] = yInt[i] * scale;
        }
        return y;
    }

    /**
     * Result holder for {@link #quantizeWeightAbsmean}. Carries the int
     * ternary matrix and the float scale {@code γ}.
     */
    public static final class QuantizedWeight {
        public final int[][] values;
        public final float gamma;

        public QuantizedWeight(int[][] values, float gamma) {
            this.values = values;
            this.gamma = gamma;
        }

        @Override
        public String toString() {
            return "QuantizedWeight{gamma=" + gamma
                    + ", shape=" + values.length + "x" + values[0].length + "}";
        }
    }

    /**
     * Result holder for {@link #quantizeActivationAbsmax}. Carries the int
     * 8-bit vector and the float absmax scale {@code α}.
     */
    public static final class QuantizedActivation {
        public final int[] values;
        public final float alpha;

        public QuantizedActivation(int[] values, float alpha) {
            this.values = values;
            this.alpha = alpha;
        }

        @Override
        public String toString() {
            return "QuantizedActivation{alpha=" + alpha
                    + ", len=" + values.length + "}";
        }
    }
}
