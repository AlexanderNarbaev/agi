package io.matrix.neuron;

import io.matrix.imports.BitLinearGpu;

/**
 * RUN 455 — GPU-accelerated BitLinear forward (DESIGN-54 §3.3).
 *
 * <p>Wraps {@link BitLinear#forward} with automatic GPU dispatch: for
 * matrices above a size threshold, routes the int8 matmul through
 * {@link BitLinearGpu#matmulI8} (CUDA) instead of {@link BitLinear#matmul}
 * (Java). Falls back to pure Java on GPU absence or below-threshold sizes.
 *
 * <h2>Why auto-dispatch?</h2>
 * <ul>
 *   <li>GPU kernel launch overhead: ~50-100 μs</li>
 *   <li>CPU int8 dot product: ~50 ns per element</li>
 *   <li>GPU compute kicks in above ~1024 elements (when launch overhead
 *       amortizes over compute time)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   float[] output = BitLinearGpuForward.forward(weights, input);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure wrapper. Caller supplies all inputs.
 */
public final class BitLinearGpuForward {

    /**
     * Threshold (out_features × in_features) above which GPU dispatch kicks in.
     * Below this size, CPU is faster due to GPU kernel launch overhead.
     */
    public static final int GPU_DISPATCH_THRESHOLD = 4096;

    private BitLinearGpuForward() {}

    /**
     * Forward pass with GPU auto-dispatch.
     *
     * @param weights float weight matrix [out × in]
     * @param input   float input vector [in]
     * @return float output vector [out]
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
        int out = weights.length;
        int matrixSize = out * in;

        // Standard preprocessing (CPU)
        float[] normalized = BitLinear.subln(input);
        BitLinear.QuantizedWeight qw = BitLinear.quantizeWeightAbsmean(weights);
        BitLinear.QuantizedActivation qa = BitLinear.quantizeActivationAbsmax(normalized);

        int[] yInt;
        if (matrixSize >= GPU_DISPATCH_THRESHOLD
                && BitLinearGpu.isNativeAvailable()
                && BitLinearGpu.deviceCount() > 0) {
            // GPU path: pack ternary weights into int8 ({-1,0,+1} → {-1,0,1}),
            // convert int[] activations to byte[].
            byte[] weightBytes = packTernaryToInt8(qw.values);
            byte[] activationBytes = new byte[qa.values.length];
            for (int i = 0; i < qa.values.length; i++) {
                activationBytes[i] = (byte) qa.values[i];
            }
            int[] gpuResult = BitLinearGpu.matmulI8(weightBytes, activationBytes, 0);
            yInt = (gpuResult != null) ? gpuResult : BitLinear.matmul(qw.values, qa.values);
        } else {
            // CPU path
            yInt = BitLinear.matmul(qw.values, qa.values);
        }

        float scale = qw.gamma * qa.alpha / BitLinear.Q_ABSMAX;
        float[] y = new float[yInt.length];
        for (int i = 0; i < yInt.length; i++) {
            y[i] = yInt[i] * scale;
        }
        return y;
    }

    /**
     * Pack a 2D ternary int matrix (values in {-1, 0, +1}) into a flat
     * byte array (row-major). Suitable for {@link BitLinearGpu#matmulI8}.
     */
    private static byte[] packTernaryToInt8(int[][] values) {
        byte[] bytes = new byte[values.length * values[0].length];
        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values[i].length; j++) {
                bytes[i * values[i].length + j] = (byte) values[i][j];
            }
        }
        return bytes;
    }
}
