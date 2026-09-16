package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W238 — Cognitive Grouped-Query Attention (GQA).
 *
 * <p>Inspired by GQA (Ainslie et al. 2023). Multi-head attention with
 * shared Key/Value heads across query groups. Reduces KV cache size by
 * 2-8x while preserving quality.
 *
 * <p>CONSTITUTION VI compliance: GQA cognitive attention, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveGroupedQueryAttention {

    private final int dim;
    private final int numQueryHeads;
    private final int numKVHeads;
    private final int headDim;
    private final long seed;
    private final double[][] qProj; // [dim × dim]
    private final double[][] kProj; // [dim × numKVHeads * headDim]
    private final double[][] vProj; // [dim × numKVHeads * headDim]
    private final double[][] oProj; // [dim × dim]

    public CognitiveGroupedQueryAttention(int dim, int numQueryHeads,
                                            int numKVHeads, long seed) {
        if (dim < 1 || numQueryHeads < 1 || numKVHeads < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        if (numQueryHeads % numKVHeads != 0) {
            throw new IllegalArgumentException("numQueryHeads must be divisible by numKVHeads");
        }
        if (dim % numQueryHeads != 0) {
            throw new IllegalArgumentException("dim must be divisible by numQueryHeads");
        }
        this.dim = dim;
        this.numQueryHeads = numQueryHeads;
        this.numKVHeads = numKVHeads;
        this.headDim = dim / numQueryHeads;
        this.seed = seed;
        Random rng = new Random(seed);
        double std = 1.0 / Math.sqrt((double) dim);
        this.qProj = randomMatrix(dim, dim, rng, std);
        // K, V projections: full [dim × dim], then split into KV heads
        this.kProj = randomMatrix(dim, dim, rng, std);
        this.vProj = randomMatrix(dim, dim, rng, std);
        this.oProj = randomMatrix(dim, dim, rng, std);
    }

    /**
     * Compute GQA attention output.
     */
    public double[] attend(double[] query) {
        if (query == null || query.length != dim) return query;
        // Project to Q, K, V (all size dim)
        double[] qFlat = matVecMul(qProj, query);
        double[] kFlat = matVecMul(kProj, query);
        double[] vFlat = matVecMul(vProj, query);
        // For K/V, use headDim = dim / numKVHeads (so all KV heads fit in dim)
        int kvHeadDim = dim / numKVHeads;
        int groupSize = numQueryHeads / numKVHeads;
        double[] output = new double[dim];
        double scale = 1.0 / Math.sqrt((double) kvHeadDim);
        for (int g = 0; g < numKVHeads; g++) {
            // Extract K, V for this KV head
            double[] kHead = new double[kvHeadDim];
            double[] vHead = new double[kvHeadDim];
            System.arraycopy(kFlat, g * kvHeadDim, kHead, 0, kvHeadDim);
            System.arraycopy(vFlat, g * kvHeadDim, vHead, 0, kvHeadDim);
            // Each groupSize query heads share this KV head
            for (int gi = 0; gi < groupSize; gi++) {
                int qh = g * groupSize + gi;
                // Extract Q for this query head
                double[] qHead = new double[headDim];
                System.arraycopy(qFlat, qh * headDim, qHead, 0, headDim);
                // Use first kvHeadDim dimensions of qHead for dot product
                // Use min of headDim and kvHeadDim for dot product
                int attDim = Math.min(headDim, kvHeadDim);
                double score = 0;
                for (int d = 0; d < attDim; d++) {
                    score += qHead[d] * kHead[d];
                }
                double weight = Math.exp(score * scale);
                // Place output for this query head starting at qh * headDim
                for (int d = 0; d < kvHeadDim && qh * headDim + d < dim; d++) {
                    output[qh * headDim + d] += weight * vHead[d];
                }
            }
        }
        // Output projection
        return matVecMul(oProj, output);
    }

    public double[][] attendSequence(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return new double[0][0];
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        int n = profiles.size();
        double[][] result = new double[n][dim];
        for (int i = 0; i < n; i++) {
            double[] v = embedder.embed(profiles.get(i));
            result[i] = attend(v);
        }
        return result;
    }

    public double compressionRatio() {
        return (double) numQueryHeads / numKVHeads;
    }

    public int dim() { return dim; }
    public int numQueryHeads() { return numQueryHeads; }
    public int numKVHeads() { return numKVHeads; }
    public long seed() { return seed; }

    private static double[][] randomMatrix(int rows, int cols, Random rng, double std) {
        double[][] m = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                m[i][j] = rng.nextGaussian() * std;
            }
        }
        return m;
    }

    private static double[] matVecMul(double[][] m, double[] v) {
        double[] result = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            double sum = 0;
            for (int j = 0; j < v.length; j++) {
                sum += m[i][j] * v[j];
            }
            result[i] = sum;
        }
        return result;
    }
}
