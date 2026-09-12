package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RUN 453 — Multi-layer BitLinear pipeline (DESIGN-54 §3.2).
 *
 * <p>Chains multiple {@link BitLinear} layers with SubLN between them,
 * simulating a small transformer block (FFN + residual).
 *
 * <h2>Architecture</h2>
 * <pre>
 *   input ─┐
 *          ▼
 *        BitLinear1 ── SubLN ── BitLinear2 ── SubLN ── BitLinear3 ── output
 *          ▲                                                   │
 *          └───────────── residual add ─────────────────────┘
 * </pre>
 *
 * <p>This is the minimal building block of a BitNet-style neural network:
 * linear projection → normalization → linear projection → ... with residual
 * connections. With N=3 layers and SubLN between them, this approximates
 * one transformer FFN block.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class BitLinearPipeline {

    /** Per-layer weights. */
    public final List<float[][]> weights;
    /** Output dimensions per layer. */
    public final List<Integer> outputDims;
    /** Random source for initialization. */
    private final Random rng;

    /**
     * Build a pipeline with N layers of given dimensions.
     *
     * @param layerDims output dimension per layer (≥2 elements: input dim, hidden1, hidden2, ..., output dim)
     * @param rng RNG source for weight initialization
     */
    public BitLinearPipeline(int[] layerDims, Random rng) {
        if (layerDims == null || layerDims.length < 2) {
            throw new IllegalArgumentException("layerDims must have ≥ 2 elements");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.rng = rng;
        this.outputDims = new ArrayList<>();
        this.weights = new ArrayList<>();
        for (int i = 0; i < layerDims.length - 1; i++) {
            int outDim = layerDims[i + 1];
            int inDim = layerDims[i];
            float[][] w = new float[outDim][inDim];
            for (int r = 0; r < outDim; r++) {
                for (int c = 0; c < inDim; c++) {
                    // He initialization
                    w[r][c] = (float) (rng.nextGaussian() * Math.sqrt(2.0 / inDim));
                }
            }
            weights.add(w);
            outputDims.add(outDim);
        }
    }

    /**
     * Forward pass through the pipeline.
     */
    public float[] forward(float[] input) {
        if (input == null) throw new IllegalArgumentException("null input");
        float[] residual = input.clone();
        float[] current = input.clone();
        for (int i = 0; i < weights.size(); i++) {
            float[][] w = weights.get(i);
            float[] projected = BitLinear.forward(w, current);
            float[] normalized = BitLinear.subln(projected);
            // Residual addition: dimensions must match
            if (normalized.length == residual.length) {
                for (int k = 0; k < normalized.length; k++) {
                    normalized[k] += residual[k];
                }
                residual = normalized.clone();
            }
            current = normalized;
        }
        return current;
    }

    /**
     * Forward pass with intermediate layer outputs (for inspection).
     */
    public List<float[]> forwardWithIntermediates(float[] input) {
        if (input == null) throw new IllegalArgumentException("null input");
        List<float[]> intermediates = new ArrayList<>();
        float[] residual = input.clone();
        float[] current = input.clone();
        intermediates.add(current.clone());
        for (int i = 0; i < weights.size(); i++) {
            float[][] w = weights.get(i);
            float[] projected = BitLinear.forward(w, current);
            float[] normalized = BitLinear.subln(projected);
            if (normalized.length == residual.length) {
                for (int k = 0; k < normalized.length; k++) {
                    normalized[k] += residual[k];
                }
                residual = normalized.clone();
            }
            current = normalized;
            intermediates.add(current.clone());
        }
        return intermediates;
    }

    /**
     * Get total parameter count across all layers.
     */
    public int parameterCount() {
        int total = 0;
        for (float[][] w : weights) total += w.length * w[0].length;
        return total;
    }

    /**
     * Get input dimension (first layer input).
     */
    public int inputDim() {
        return weights.get(0)[0].length;
    }

    /**
     * Get output dimension (last layer output).
     */
    public int outputDim() {
        return weights.get(weights.size() - 1).length;
    }

    /**
     * Number of layers.
     */
    public int layerCount() {
        return weights.size();
    }
}
