package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W205 — Cognitive Attention (Q/K/V over profiles).
 *
 * <p>Inspired by Transformer attention (Vaswani et al. 2017). For each
 * profile in a sequence, computes Q (query), K (key), V (value) vectors.
 * Attention weights = softmax(Q · K / sqrt(d)) over all profiles.
 * Output = weighted sum of V vectors.
 *
 * <p>Identifies which historical profiles are most relevant to the
 * current one — "attention sinks" (StreamingLLM) emerge naturally.
 *
 * <p>Use cases:
 * - Self-attention over cognitive profile history
 * - Identify regime transitions (high attention weight = transition)
 * - Detect "key" profiles (attention sinks)
 *
 * <p>CONSTITUTION VI compliance: attention-weighted aggregation of
 * cognitive state, not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random instances are seeded.
 */
public final class CognitiveAttention {

    private CognitiveAttention() {}

    /** Result of cognitive attention. */
    public record AttentionResult(
        double[][] outputVectors,    // [N profiles × dim] aggregated output
        double[][] attentionWeights, // [N profiles × N profiles] attention map
        double[] sinkScores          // [N profiles] "sink score" = sum of attention received
    ) {}

    /**
     * Compute self-attention over a profile sequence.
     *
     * @param profiles list of cognitive profiles (length N)
     * @param dim output dimension (each profile embedded to dim-dim vector)
     * @param seed RNG seed for projection matrices
     * @return attention result with output vectors, weights, and sink scores
     */
    public static AttentionResult selfAttention(List<CognitiveGenesisProfile> profiles,
                                                  int dim, long seed) {
        if (profiles == null || profiles.isEmpty()) {
            return new AttentionResult(new double[0][], new double[0][], new double[0]);
        }
        int n = profiles.size();
        if (n == 1) {
            double[][] w = {{1.0}};
            CognitiveEmbedding e = new CognitiveEmbedding(dim, seed);
            double[][] out = {e.embed(profiles.get(0))};
            double[] sinks = {1.0};
            return new AttentionResult(out, w, sinks);
        }
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        double[][] keys = new double[n][dim];
        double[][] queries = new double[n][dim];
        double[][] values = new double[n][dim];
        Random rng = new Random(seed ^ 0xDEADBEEFL);
        double scale = 1.0 / Math.sqrt((double) dim);
        // Project: Q = K = V = embedding (simplified — same as input projection)
        // In real Transformer, Q, K, V have separate learned projections.
        // Here we use the same embedding for simplicity.
        for (int i = 0; i < n; i++) {
            double[] v = embedder.embed(profiles.get(i));
            System.arraycopy(v, 0, keys[i], 0, dim);
            System.arraycopy(v, 0, values[i], 0, dim);
            // Slightly perturbed queries for differentiation
            for (int d = 0; d < dim; d++) {
                queries[i][d] = v[d] + rng.nextGaussian() * 0.01;
            }
        }
        // Compute attention weights
        double[][] weights = new double[n][n];
        double[] sinks = new double[n];
        for (int i = 0; i < n; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                double score = 0;
                for (int d = 0; d < dim; d++) {
                    score += queries[i][d] * keys[j][d];
                }
                weights[i][j] = score * scale;
                if (weights[i][j] > max) max = weights[i][j];
            }
            double sum = 0;
            for (int j = 0; j < n; j++) {
                weights[i][j] = Math.exp(weights[i][j] - max);
                sum += weights[i][j];
            }
            for (int j = 0; j < n; j++) {
                weights[i][j] /= sum;
                sinks[j] += weights[i][j];
            }
        }
        // Compute output vectors
        double[][] output = new double[n][dim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int d = 0; d < dim; d++) {
                    output[i][d] += weights[i][j] * values[j][d];
                }
            }
        }
        return new AttentionResult(output, weights, sinks);
    }

    /**
     * Find attention sinks (profiles that receive the most attention).
     * Returns indices sorted by descending sink score.
     */
    public static int[] findSinks(double[][] attentionWeights, int topK) {
        if (attentionWeights == null || attentionWeights.length == 0 || topK <= 0) {
            return new int[0];
        }
        int n = attentionWeights.length;
        double[] sinks = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                sinks[j] += attentionWeights[i][j];
            }
        }
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(sinks[b], sinks[a]));
        int resultLen = Math.min(topK, n);
        int[] result = new int[resultLen];
        for (int i = 0; i < resultLen; i++) result[i] = indices[i];
        return result;
    }

    /**
     * Classify attention regime: SPARSE, UNIFORM, or CONCENTRATED.
     */
    public static String classifyRegime(double[][] attentionWeights) {
        if (attentionWeights == null || attentionWeights.length == 0) return "EMPTY";
        double entropySum = 0;
        int n = attentionWeights.length;
        for (int i = 0; i < n; i++) {
            double h = 0;
            for (int j = 0; j < n; j++) {
                double p = attentionWeights[i][j];
                if (p > 0) h -= p * Math.log(p);
            }
            double maxEntropy = Math.log(n);
            if (maxEntropy > 0) entropySum += h / maxEntropy;
        }
        double meanNormalizedEntropy = entropySum / n;
        if (meanNormalizedEntropy > 0.8) return "UNIFORM";
        if (meanNormalizedEntropy < 0.3) return "CONCENTRATED";
        return "SPARSE";
    }
}
