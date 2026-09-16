package io.matrix.consciousness;

/**
 * W239 — Cognitive Rotary Position Embedding (RoPE).
 *
 * <p>Inspired by RoPE (Su et al. 2021). Encodes positional information
 * via rotation of vector pairs. Used in Llama, Mistral, Phi-3.
 *
 * <p>For each pair (x_2i, x_2i+1):
 *   x_2i' = x_2i * cos(θ_i) - x_2i+1 * sin(θ_i)
 *   x_2i+1' = x_2i * sin(θ_i) + x_2i+1 * cos(θ_i)
 *
 * <p>θ_i = position / base^(2i/dim)
 *
 * <p>Benefits: extrapolates to longer sequences than training.
 *
 * <p>CONSTITUTION VI compliance: RoPE-style positional encoding, not
 * phenomenal consciousness claim.
 */
public final class CognitiveRotaryEmbedding {

    private CognitiveRotaryEmbedding() {}

    /** Default base for theta computation. */
    public static final double DEFAULT_BASE = 10000.0;

    /**
     * Apply RoPE to a vector at given position.
     */
    public static double[] apply(double[] vector, int position) {
        return apply(vector, position, DEFAULT_BASE);
    }

    public static double[] apply(double[] vector, int position, double base) {
        if (vector == null) return null;
        double[] result = vector.clone();
        int dim = vector.length;
        for (int i = 0; i < dim; i += 2) {
            double theta = position / Math.pow(base, (double) i / dim);
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double x0 = vector[i];
            double x1 = vector[i + 1];
            result[i] = x0 * cos - x1 * sin;
            result[i + 1] = x0 * sin + x1 * cos;
        }
        return result;
    }

    /**
     * Compute frequency for given dimension index.
     */
    public static double frequency(int dimIndex, int totalDim, double base) {
        return 1.0 / Math.pow(base, (double) (2 * dimIndex) / totalDim);
    }

    /**
     * Inverse rotation (for decoding).
     */
    public static double[] inverse(double[] vector, int position) {
        return inverse(vector, position, DEFAULT_BASE);
    }

    public static double[] inverse(double[] vector, int position, double base) {
        if (vector == null) return null;
        double[] result = vector.clone();
        int dim = vector.length;
        for (int i = 0; i < dim; i += 2) {
            double theta = position / Math.pow(base, (double) i / dim);
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double x0 = vector[i];
            double x1 = vector[i + 1];
            result[i] = x0 * cos + x1 * sin;
            result[i + 1] = -x0 * sin + x1 * cos;
        }
        return result;
    }
}
