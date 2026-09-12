package io.matrix.neuron;

/**
 * RUN 459 — BitNet inference forward (no SubLN, bf16 matmul) (DESIGN-54 §11).
 *
 * <p>Plain bf16 matmul path for BitNet b1.58 inference. Unlike
 * {@link BitLinearGpuForward}, this does NOT apply SubLN or absmean
 * because those operations happen during training-time quantization,
 * not at inference.
 *
 * <h2>Usage</h2>
 * <pre>
 *   float[] out = BitNetInferenceForward.forward(unpackedFloatW, x, outDim, inDim);
 * </pre>
 *
 * <h2>Why a separate class?</h2>
 * The {@link BitLinearGpuForward} is for training-time BitLinear layers
 * (SubLN + absmean + int8 matmul + dequant). For real BitNet b1.58-2B-4T
 * inference, weights are already pre-quantized and stored as bf16 after
 * dequantization, so we just need a plain bf16 matmul.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock.
 */
public final class BitNetInferenceForward {

    private BitNetInferenceForward() {}

    /**
     * Forward pass for BitNet b1.58 inference: y = W @ x.
     *
     * @param unpackedWeights FP32 weights from BitNetWeightUnpacker.unpack
     * @param x FP32 input vector [in_features]
     * @param outDim output dimension
     * @param inDim input dimension
     * @return FP32 output [out_dim]
     */
    public static float[] forward(float[] unpackedWeights, float[] x,
                                    int outDim, int inDim) {
        if (unpackedWeights == null || x == null) {
            throw new IllegalArgumentException("null inputs");
        }
        if (unpackedWeights.length != outDim * inDim) {
            throw new IllegalArgumentException("weights length mismatch");
        }
        if (x.length != inDim) {
            throw new IllegalArgumentException("x length mismatch");
        }

        float[] y = new float[outDim];
        for (int i = 0; i < outDim; i++) {
            float sum = 0.0f;
            int rowBase = i * inDim;
            for (int j = 0; j < inDim; j++) {
                sum += unpackedWeights[rowBase + j] * x[j];
            }
            y[i] = sum;
        }
        return y;
    }

    /**
     * Batched forward: y = W @ X for X [batch*seq, inDim].
     * Returns Y [batch*seq, outDim].
     */
    public static float[] forwardBatched(float[] unpackedWeights, float[] x,
                                          int outDim, int inDim, int batchSeq) {
        if (unpackedWeights == null || x == null) {
            throw new IllegalArgumentException("null inputs");
        }
        if (x.length != batchSeq * inDim) {
            throw new IllegalArgumentException("x length mismatch");
        }
        float[] y = new float[batchSeq * outDim];
        for (int b = 0; b < batchSeq; b++) {
            int xBase = b * inDim;
            int yBase = b * outDim;
            for (int i = 0; i < outDim; i++) {
                float sum = 0.0f;
                int rowBase = i * inDim;
                for (int j = 0; j < inDim; j++) {
                    sum += unpackedWeights[rowBase + j] * x[xBase + j];
                }
                y[yBase + i] = sum;
            }
        }
        return y;
    }
}
