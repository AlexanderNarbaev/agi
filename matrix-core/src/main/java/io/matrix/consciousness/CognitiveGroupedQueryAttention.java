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
 * <p>Architecture:
 * - H_q query heads (full)
 * - H_kv key/value heads (shared, H_kv < H_q)
 * - Group size G = H_q / H_kv
 *
 * <p>Benefits: smaller memory footprint, faster inference.
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
    private final long seed;
    private final double[][] qProj; // [dim × dim]
    private final double[][] kProj; // [dim × numKVHeads × headDim]
    private final double[][] vProj; // [dim × numKVHeads × headDim]
    private final double[][] oProj; // [dim × dim]

    /**
     * @param dim model dimension
     * @param numQueryHeads number of query heads
     * @param numKVHeads number of KV heads (must divide numQueryHeads)
     * @param seed RNG seed
     */
    public CognitiveGroupedQueryAttention(int dim, int numQueryHeads,
                                            int numKVHeads, long seed) {
        if (dim < 1 || numQueryHeads < 1 || numKVHeads < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        if (numQueryHeads % numKVHeads != 0) {
            throw new IllegalArgumentException("numQueryHeads must be divisible by numKVHeads");
        }
        this.dim = dim;
        this.numQueryHeads = numQueryHeads;
        this.numKVHeads = numKVHeads;
        this.seed = seed;
        Random rng = new Random(seed);
        int headDim = dim / numQueryHeads;
        double std = 1.0 / Math.sqrt((double) dim);
        this.qProj = randomMatrix(dim, dim, rng, std);
        this.kProj = randomMatrix(dim, numKVHeads * headDim, rng, std);
        this.vProj = randomMatrix(dim, numKVHeads * headDim, rng, std);
        this.oProj = randomMatrix(dim, dim, rng, std);
    }

    /**
     * Compute GQA attention output.
     *
     * @param query vector of length dim
     * @return attended vector of length dim
     */
    public double[] attend(double[] query) {
        if (query == null || query.length != dim) return query;
        int headDim = dim / numQueryHeads;
        // Project to Q, K, V
        double[][] qHeads = projectHeads(query, qProj, numQueryHeads, headDim);
        double[][] kHeads = projectHeads(query, kProj, numKVHeads, headDim);
        double[][] vHeads = projectHeads(query, vProj, numKVHeads, headDim);
        // Group queries share KV
        int groupSize = numQueryHeads / numKVHeads;
        double[] output = new double[dim];
        double scale = 1.0 / Math.sqrt((double) headDim);
        for (int g = 0; g < numKVHeads; g++) {
            double[] kHead = kHeads[g];
            double[] vHead = vHeads[g];
            for (int gi = 0; gi < groupSize; gi++) {
                int qh = g * groupSize + gi;
                double[] qHead = qHeads[qh];
                double score = 0;
                for (int d = 0; d < headDim; d++) {
                    score += qHead[d] * kHead[d];
                }
                double weight = Math.exp(score * scale);
                for (int d = 0; d < headDim; d++) {
                    output[qh * headDim + d] += weight * vHead[d];
                }
            }
        }
        // Output projection
        return matVecMul(oProj, output);
    }

    /**
     * Compute GQA over a profile sequence.
     */
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

    /** Compression ratio vs MHA. */
    public double compressionRatio() {
        // Standard MHA: dim × numQueryHeads for K + V = 2 × dim²
        // GQA: 2 × dim × numKVHeads × headDim = 2 × dim × numKVHeads × dim/numQueryHeads
        //       = 2 × dim² × numKVHeads / numQueryHeads
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

    private static double[][] projectHeads(double[] input, double[][] proj,
                                              int numHeads, int headDim) {
        // project: [input] * [proj] -> [numHeads × headDim]
        double[][] heads = new double[numHeads][headDim];
        double[] full = matVecMul(proj, input);
        for (int h = 0; h < numHeads; h++) {
            for (int d = 0; d < headDim; d++) {
                heads[h][d] = full[h * headDim + d];
            }
        }
        return heads;
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
