package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 457 — BitNet RMSNorm (DESIGN-54 §11, BitNet inference primitives).
 *
 * <p>Pure-Java RMSNorm matching the PyTorch reference:
 * <pre>
 *   x_fp32 = hidden_states.to(float32)
 *   variance = x_fp32.pow(2).mean(-1, keepdim=True)
 *   normalized = x_fp32 * rsqrt(variance + eps)
 *   out = weight * normalized.to(input_dtype)
 * </pre>
 *
 * <p>eps default 1e-5 per config.json (overrides class default 1e-6).
 *
 * <h2>Usage</h2>
 * <pre>
 *   float[] weight = ...; // [hidden_size]
 *   BitNetRmsNorm norm = new BitNetRmsNorm(weight, 1e-5f);
 *   float[] normalized = norm.forward(hidden_states, batch, seq, hidden);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock. Deterministic given inputs.
 */
public final class BitNetRmsNorm {

    private final float[] weight;
    private final float eps;

    /**
     * Create RMSNorm with weight (bf16 stored as float) and epsilon.
     */
    public BitNetRmsNorm(float[] weight, float eps) {
        if (weight == null) throw new IllegalArgumentException("null weight");
        if (eps <= 0) throw new IllegalArgumentException("eps must be positive");
        this.weight = weight.clone();
        this.eps = eps;
    }

    /**
     * Forward pass: normalize hidden_states in-place (returns new array).
     *
     * @param hidden_states input [batch, seq, hidden_size] row-major
     * @param batch batch size
     * @param seq sequence length
     * @param hidden hidden size (must match weight.length)
     * @return normalized hidden states [batch, seq, hidden_size]
     */
    /**
     * Forward for a sequence [seq_len, hidden_size] (variable seq_len per row).
     */
    public float[][] forwardSeq(float[][] hiddenStates) {
        if (hiddenStates == null || hiddenStates.length == 0) {
            throw new IllegalArgumentException("empty hiddenStates");
        }
        int hidden = weight.length;
        float[][] out = new float[hiddenStates.length][];
        float invHidden = 1.0f / hidden;
        for (int s = 0; s < hiddenStates.length; s++) {
            float[] in = hiddenStates[s];
            if (in.length != hidden) {
                throw new IllegalArgumentException("seq " + s + " hidden=" + in.length
                        + " != weight.length " + hidden);
            }
            double sumSq = 0.0;
            for (int d = 0; d < hidden; d++) {
                sumSq += (double) in[d] * in[d];
            }
            double meanSq = sumSq * invHidden;
            float rms = (float) (1.0 / Math.sqrt(meanSq + eps));
            float[] outS = new float[hidden];
            for (int d = 0; d < hidden; d++) {
                outS[d] = in[d] * rms * weight[d];
            }
            out[s] = outS;
        }
        return out;
    }

    public float[] forward(float[] hidden_states, int batch, int seq, int hidden) {
        if (hidden_states == null) throw new IllegalArgumentException("null hidden_states");
        if (hidden != weight.length) {
            throw new IllegalArgumentException("hidden=" + hidden + " but weight.length="
                    + weight.length);
        }
        if (hidden_states.length != batch * seq * hidden) {
            throw new IllegalArgumentException("hidden_states length "
                    + hidden_states.length + " != batch*seq*hidden " + (batch * seq * hidden));
        }

        float[] out = new float[batch * seq * hidden];
        float invHidden = 1.0f / hidden;
        for (int b = 0; b < batch; b++) {
            for (int s = 0; s < seq; s++) {
                int base = (b * seq + s) * hidden;
                // Compute variance (mean of x^2)
                double sumSq = 0.0;
                for (int d = 0; d < hidden; d++) {
                    float v = hidden_states[base + d];
                    sumSq += (double) v * v;
                }
                double meanSq = sumSq * invHidden;
                float rms = (float) (1.0 / Math.sqrt(meanSq + eps));
                // Normalize and apply weight
                for (int d = 0; d < hidden; d++) {
                    out[base + d] = hidden_states[base + d] * rms * weight[d];
                }
            }
        }
        return out;
    }

    /**
     * Get the weight vector (length = hidden_size).
     */
    public float[] weight() {
        return weight.clone();
    }
}
