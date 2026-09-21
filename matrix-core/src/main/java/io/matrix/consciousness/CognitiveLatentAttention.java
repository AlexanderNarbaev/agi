package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W319 — Cognitive Multi-head Latent Attention (MLA).
 *
 * <p>Inspired by Multi-head Latent Attention (DeepSeek-V2/V3, 2024-2025).
 * Compresses KV cache by 5-10x via low-rank latent vectors.
 *
 * <p>Standard MHA: K, V each take O(numHeads × headDim) per token
 * MLA: Compresses K, V into a single latent vector c of dim d_c
 *
 * <p>CONSTITUTION VI compliance: latent attention cognitive substrate,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveLatentAttention {

    private CognitiveLatentAttention() {}

    private int dim;
    private int numHeads;
    private int headDim;
    private int latentDim;
    private long seed;
    private double[][] downProjK;  // [dim × latentDim]
    private double[][] downProjV;  // [dim × latentDim]
    private double[][] upProjK;   // [latentDim × dim]
    private double[][] upProjV;   // [latentDim × dim]

    public CognitiveLatentAttention(int dim, int numHeads, int latentDim, long seed) {
        if (dim < 1 || numHeads < 1 || latentDim < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        if (dim % numHeads != 0) {
            throw new IllegalArgumentException("dim must be divisible by numHeads");
        }
        this.dim = dim;
        this.numHeads = numHeads;
        this.headDim = dim / numHeads;
        this.latentDim = latentDim;
        this.seed = seed;
        Random rng = new Random(seed);
        double std = 1.0 / Math.sqrt((double) dim);
        this.downProjK = randomMatrix(dim, latentDim, rng, std);
        this.downProjV = randomMatrix(dim, latentDim, rng, std);
        this.upProjK = randomMatrix(latentDim, dim, rng, std);
        this.upProjV = randomMatrix(latentDim, dim, rng, std);
    }

    /**
     * Compress a key/value vector to latent space.
     */
    public double[] compress(double[] vector, boolean isKey) {
        if (vector == null || vector.length != dim) return null;
        double[][] proj = isKey ? downProjK : downProjV;
        double[] result = new double[latentDim];
        for (int j = 0; j < latentDim; j++) {
            double sum = 0;
            for (int i = 0; i < dim; i++) {
                sum += vector[i] * proj[i][j];
            }
            result[j] = sum;
        }
        return result;
    }

    /**
     * Decompress latent back to K/V space.
     */
    public double[] decompress(double[] latent, boolean isKey) {
        if (latent == null || latent.length != latentDim) return null;
        double[][] proj = isKey ? upProjK : upProjV;
        double[] result = new double[dim];
        for (int i = 0; i < dim; i++) {
            double sum = 0;
            for (int j = 0; j < latentDim; j++) {
                sum += latent[j] * proj[j][i];
            }
            result[i] = sum;
        }
        return result;
    }

    /**
     * Compute KV cache size for a sequence.
     * Standard MHA: 2 × numHeads × headDim × seqLen
     * MLA: 1 × latentDim × seqLen
     */
    public double compressionRatio() {
        return (2.0 * numHeads * headDim) / latentDim;
    }

    public int dim() { return dim; }
    public int numHeads() { return numHeads; }
    public int headDim() { return headDim; }
    public int latentDim() { return latentDim; }
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
}
