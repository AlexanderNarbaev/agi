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

    /**
     * Apply RoPE to a vector at a given position.
     *
     * <p>RECON-W32.31. The loop stepped {@code i += 2} and then read
     * {@code vector[i + 1]}, so an ODD-length vector read one element past its end:
     * {@code Index 3 out of bounds for length 3}, reproduced for dim 3 and dim 5. The
     * audit described this as "indexes past the position table"; there is no position
     * table in this method, and the real cause is a pair-wise rotation applied to a
     * vector that has an unpaired trailing component.</p>
     *
     * <p><b>Why the trailing element is left alone rather than "handled".</b> RoPE rotates
     * PAIRS of adjacent components by a position-dependent angle. An unpaired component
     * has no partner to rotate against, and there is no honest half-rotation to invent —
     * any value chosen here would be arbitrary and would silently differ from what a
     * caller rotating the full vector expects. So it passes through unchanged, and the
     * behaviour is documented rather than left to be discovered.</p>
     *
     * <p>Real models use an even head dimension, which is why this survived; the
     * property test generated odd dimensions and found it.</p>
     *
     * @param vector components to rotate; an odd length rotates every complete pair and
     *               leaves the final unpaired component unchanged
     * @return a new array; null if {@code vector} is null
     */
    public static double[] apply(double[] vector, int position, double base) {
        if (vector == null) return null;
        double[] result = vector.clone();
        int dim = vector.length;
        // i + 1 < dim, not i < dim: with an odd dim the last i has no partner and
        // reading vector[i + 1] is the out-of-bounds access this guards.
        for (int i = 0; i + 1 < dim; i += 2) {
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
        // RECON-W32.31: this method carried the IDENTICAL out-of-bounds bug that apply()
        // had, and fixing only apply() left propertyRoPEInverseUndoes still throwing
        // "Index 3 out of bounds for length 3". A round-trip property exercises BOTH
        // halves, so it is the test that would have caught a partial fix — which is
        // exactly what happened, and the only reason it was noticed in this session.
        for (int i = 0; i + 1 < dim; i += 2) {
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
