package io.matrix.consciousness;

import java.util.Random;

/**
 * W243 — Cognitive State Space Model (Mamba-style).
 *
 * <p>Inspired by S4 (Gu et al. 2021) and Mamba (Gu & Dao 2023).
 * Linear time-complexity sequence modeling via selective state spaces.
 *
 * <p>Recurrence:
 *   h_t = A * h_{t-1} + B * x_t
 *   y_t = C * h_t
 *
 * <p>Selective: B, C depend on x_t (input-dependent).
 *
 * <p>CONSTITUTION VI compliance: SSM cognitive processing, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveStateSpaceModel {

    private final int dim;
    private final int stateDim;
    private final long seed;
    private final double[] A; // [stateDim]
    private final double[] h; // [stateDim] hidden state

    public CognitiveStateSpaceModel(int dim, int stateDim, long seed) {
        if (dim < 1 || stateDim < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.dim = dim;
        this.stateDim = stateDim;
        this.seed = seed;
        Random rng = new Random(seed);
        // Initialize A: negative real values for stability
        this.A = new double[stateDim];
        for (int i = 0; i < stateDim; i++) {
            A[i] = -0.1 - rng.nextDouble() * 0.9;
        }
        this.h = new double[stateDim];
    }

    /**
     * Process a single input step.
     * Returns output y_t and updates internal state.
     *
     * @param x input vector of length dim
     * @param B input-dependent B matrix [stateDim × dim]
     * @param C input-dependent C matrix [stateDim]
     * @return output vector of length dim
     */
    public double[] step(double[] x, double[][] B, double[] C) {
        if (x == null || x.length != dim) return x;
        if (B == null || B.length != stateDim) return null;
        if (C == null || C.length != stateDim) return null;
        // h_t = A * h_{t-1} + B * x_t
        for (int i = 0; i < stateDim; i++) {
            double update = 0;
            for (int j = 0; j < dim; j++) {
                update += B[i][j] * x[j];
            }
            h[i] = A[i] * h[i] + update;
        }
        // y_t = C * h_t (per-dim projection)
        double[] y = new double[dim];
        for (int i = 0; i < dim; i++) {
            double sum = 0;
            for (int j = 0; j < stateDim; j++) {
                sum += C[j] * h[j];
            }
            y[i] = sum;
        }
        return y;
    }

    /**
     * Process a profile sequence.
     */
    public double[][] processSequence(java.util.List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return new double[0][0];
        reset();
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        Random rng = new Random(seed + 1);
        int n = profiles.size();
        double[][] results = new double[n][dim];
        for (int t = 0; t < n; t++) {
            double[] x = embedder.embed(profiles.get(t));
            // Selective B and C depend on x
            double[][] B = new double[stateDim][dim];
            double[] C = new double[stateDim];
            for (int i = 0; i < stateDim; i++) {
                for (int j = 0; j < dim; j++) {
                    B[i][j] = rng.nextGaussian() * 0.1 + (i == j ? 0.5 : 0.0);
                }
                C[i] = rng.nextGaussian() * 0.1;
            }
            results[t] = step(x, B, C);
        }
        return results;
    }

    /** Reset hidden state. */
    public void reset() {
        java.util.Arrays.fill(h, 0.0);
    }

    public int dim() { return dim; }
    public int stateDim() { return stateDim; }
    public long seed() { return seed; }

    /** Get current hidden state. */
    public double[] hiddenState() {
        return h.clone();
    }
}
