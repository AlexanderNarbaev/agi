package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W332 — Cognitive Multi-Resolution Attention (MRA).
 *
 * <p>Inspired by Multi-Resolution Attention (2024). Attention at
 * multiple scales simultaneously (like wavelet decomposition).
 *
 * <p>For cognitive profiles: attend at different time scales
 * (fast vs slow dynamics).
 *
 * <p>CONSTITUTION VI compliance: multi-resolution cognitive substrate,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveMultiResolutionAttention {

    private CognitiveMultiResolutionAttention() {}

    private int numResolutions;
    private long seed;
    public CognitiveMultiResolutionAttention(int dim, int numResolutions, long seed) {
        if (dim < 1 || numResolutions < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.numResolutions = numResolutions;
        this.seed = seed;
    }

    /**
     * Multi-resolution attention: combine attention at different scales.
     *
     * @param query query vector
     * @param keys list of key vectors
     * @param values list of value vectors (same length as keys)
     * @return aggregated output
     */
    public double[] attendMultiResolution(double[] query, List<double[]> keys, List<double[]> values) {
        if (query == null || keys == null || values == null ||
                keys.size() != values.size() || keys.isEmpty()) {
            return query;
        }
        double[] output = new double[query.length];
        int n = keys.size();
        // For each resolution, compute attention and combine
        for (int r = 0; r < numResolutions; r++) {
            double scale = Math.pow(2.0, r); // Coarser at higher r
            double[] scores = new double[n];
            for (int i = 0; i < n; i++) {
                double[] kProj = project(keys.get(i), r);
                double score = 0;
                int minLen = Math.min(query.length, kProj.length);
                for (int d = 0; d < minLen; d++) {
                    score += query[d] * kProj[d];
                }
                scores[i] = score / Math.sqrt(scale);
            }
            // Softmax
            double max = scores[0];
            for (double s : scores) if (s > max) max = s;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                scores[i] = Math.exp(scores[i] - max);
                sum += scores[i];
            }
            for (int i = 0; i < n; i++) scores[i] /= sum;
            // Weighted aggregation
            for (int i = 0; i < n; i++) {
                double[] v = values.get(i);
                int minLen = Math.min(output.length, v.length);
                for (int d = 0; d < minLen; d++) {
                    output[d] += scores[i] * v[d];
                }
            }
        }
        return output;
    }

    /**
     * Project vector to resolution space (downsampled).
     */
    private double[] project(double[] vector, int resolution) {
        if (vector == null) return null;
        // Downsample by stride 2^resolution
        int stride = 1 << resolution;
        int newLen = (vector.length + stride - 1) / stride;
        double[] result = new double[newLen];
        for (int i = 0; i < newLen; i++) {
            int src = i * stride;
            if (src < vector.length) {
                result[i] = vector[src];
            }
        }
        return result;
    }

    public int numResolutions() { return numResolutions; }
    public long seed() { return seed; }
}
