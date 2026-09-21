package io.matrix.consciousness;

/**
 * W240 — Cognitive Layer Normalization (LayerNorm).
 *
 * <p>Inspired by LayerNorm (Ba et al. 2016). Normalize a vector to
 * zero mean and unit variance, then apply learned scale + bias.
 *
 * <p>Used in every Transformer block.
 *
 * <p>CONSTITUTION VI compliance: layer-normalized cognitive state,
 * not phenomenal consciousness claim.
 */
public final class CognitiveLayerNormalization {

    private CognitiveLayerNormalization() {}

    private static final double EPSILON = 1e-6;

    /**
     * Apply LayerNorm to a vector.
     *
     * @param vector input vector
     * @return normalized vector (same length)
     */
    public static double[] apply(double[] vector) {
        return apply(vector, null, null);
    }

    public static double[] apply(double[] vector, double[] gamma, double[] beta) {
        if (vector == null || vector.length == 0) return vector;
        int n = vector.length;
        // Compute mean
        double mean = 0;
        for (double x : vector) mean += x;
        mean /= n;
        // Compute variance
        double var = 0;
        for (double x : vector) {
            double d = x - mean;
            var += d * d;
        }
        var /= n;
        double std = Math.sqrt(var + EPSILON);
        // Normalize
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double normalized = (vector[i] - mean) / std;
            double g = (gamma != null && gamma.length > i) ? gamma[i] : 1.0;
            double b = (beta != null && beta.length > i) ? beta[i] : 0.0;
            result[i] = normalized * g + b;
        }
        return result;
    }

    /**
     * Compute mean and variance of a vector.
     */
    public static double[] stats(double[] vector) {
        if (vector == null || vector.length == 0) return new double[]{0, 1};
        double mean = 0;
        for (double x : vector) mean += x;
        mean /= vector.length;
        double var = 0;
        for (double x : vector) {
            double d = x - mean;
            var += d * d;
        }
        var /= vector.length;
        return new double[]{mean, var};
    }

    /**
     * RMSNorm (Root Mean Square Layer Normalization) — used in Llama.
     */
    public static double[] rmsNorm(double[] vector, double[] gamma) {
        if (vector == null || vector.length == 0) return vector;
        int n = vector.length;
        double sumSq = 0;
        for (double x : vector) sumSq += x * x;
        double rms = Math.sqrt(sumSq / n + EPSILON);
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double g = (gamma != null && gamma.length > i) ? gamma[i] : 1.0;
            result[i] = (vector[i] / rms) * g;
        }
        return result;
    }
}
