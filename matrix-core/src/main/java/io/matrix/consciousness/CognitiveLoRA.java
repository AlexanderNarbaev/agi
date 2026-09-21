package io.matrix.consciousness;

import java.util.Random;

/**
 * W227 — Cognitive LoRA (Low-Rank Adaptation).
 *
 * <p>Inspired by LoRA (Hu et al. 2021). Apply low-rank updates to
 * cognitive profile embeddings without modifying the base projection.
 *
 * <p>Update = A * B where A is [dim x rank] and B is [rank x dim],
 * rank << dim. Total parameters: 2 × dim × rank (vs dim × dim for full).
 *
 * <p>Use cases:
 * - Task-specific cognitive adaptation
 * - Memory-efficient fine-tuning
 * - Multiple "personalities" sharing base weights
 *
 * <p>CONSTITUTION VI compliance: low-rank adapted cognitive state,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveLoRA {

    private final int dim;
    private final int rank;
    private final long seed;
    private final double[][] matrixA;
    private final double[][] matrixB;
    private final double alpha;
    /** Scaling factor = alpha / rank. */
    private double scaling;

    /**
     * Construct LoRA adapter.
     *
     * @param dim embedding dimension
     * @param rank rank of low-rank update
     * @param alpha scaling factor
     * @param seed RNG seed
     */
    public CognitiveLoRA(int dim, int rank, double alpha, long seed) {
        if (dim < 1) throw new IllegalArgumentException("dim must be >= 1");
        if (rank < 1) throw new IllegalArgumentException("rank must be >= 1");
        if (rank > dim) throw new IllegalArgumentException("rank must be <= dim");
        this.dim = dim;
        this.rank = rank;
        this.alpha = alpha;
        this.seed = seed;
        this.scaling = alpha / rank;
        Random rng = new Random(seed);
        // He initialization for A, zeros for B (so initial update is 0)
        double stdA = 1.0 / Math.sqrt((double) rank);
        this.matrixA = new double[dim][rank];
        this.matrixB = new double[rank][dim];
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < rank; j++) {
                matrixA[i][j] = rng.nextGaussian() * stdA;
            }
        }
        // B initialized to zero (standard LoRA)
    }

    /**
     * Apply LoRA adaptation to a vector.
     *
     * <p>Adapted vector = vector + scaling * (A * B) * vector
     *
     * <p>Computationally: h = B * vector (dim-rank product), then
     * out += scaling * (A * h) (rank-rank product).
     */
    public double[] adapt(double[] vector) {
        if (vector == null || vector.length != dim) return vector;
        double[] h = new double[rank];
        for (int r = 0; r < rank; r++) {
            double sum = 0;
            for (int d = 0; d < dim; d++) {
                sum += matrixB[r][d] * vector[d];
            }
            h[r] = sum;
        }
        double[] delta = new double[dim];
        for (int d = 0; d < dim; d++) {
            double sum = 0;
            for (int r = 0; r < rank; r++) {
                sum += matrixA[d][r] * h[r];
            }
            delta[d] = scaling * sum;
        }
        double[] out = new double[dim];
        for (int d = 0; d < dim; d++) {
            out[d] = vector[d] + delta[d];
        }
        return out;
    }

    /**
     * Compute parameter count of LoRA adapter.
     */
    public long parameterCount() {
        return (long) dim * rank + (long) rank * dim;
    }

    /**
     * Compute parameter count if full-rank (dim × dim).
     */
    public long fullRankParameterCount() {
        return (long) dim * dim;
    }

    /**
     * Compute compression ratio vs full-rank.
     */
    public double compressionRatio() {
        return (double) fullRankParameterCount() / parameterCount();
    }

    /** Get dim. */
    public int dim() { return dim; }

    /** Get rank. */
    public int rank() { return rank; }

    /** Get seed. */
    public long seed() { return seed; }
}
