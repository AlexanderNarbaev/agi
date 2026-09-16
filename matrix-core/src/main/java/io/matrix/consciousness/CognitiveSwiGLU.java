package io.matrix.consciousness;

import java.util.Random;

/**
 * W241 — Cognitive SwiGLU (gated MLP).
 *
 * <p>Inspired by SwiGLU (Shazeer 2020). Gated feed-forward network
 * used in Llama, PaLM, Mistral. Replaces standard FFN in Transformer
 * blocks.
 *
 * <p>SwiGLU(x) = (Swish(xW1) ⊙ xW2) W3
 * where Swish(x) = x * sigmoid(x)
 *
 * <p>Benefits: better quality at same param count vs ReLU FFN.
 *
 * <p>CONSTITUTION VI compliance: gated cognitive MLP, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveSwiGLU {

    private final int dim;
    private final int hiddenDim;
    private final long seed;
    private final double[][] w1; // [dim × hiddenDim]
    private final double[][] w2; // [dim × hiddenDim]
    private final double[][] w3; // [hiddenDim × dim]

    public CognitiveSwiGLU(int dim, int hiddenDim, long seed) {
        if (dim < 1 || hiddenDim < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.dim = dim;
        this.hiddenDim = hiddenDim;
        this.seed = seed;
        Random rng = new Random(seed);
        double std = 1.0 / Math.sqrt((double) dim);
        this.w1 = randomMatrix(dim, hiddenDim, rng, std);
        this.w2 = randomMatrix(dim, hiddenDim, rng, std);
        this.w3 = randomMatrix(hiddenDim, dim, rng, std);
    }

    /**
     * Apply SwiGLU to a vector.
     */
    public double[] apply(double[] input) {
        if (input == null || input.length != dim) return input;
        // gate = Swish(input @ w1)
        double[] gate = matVecMul(w1, input);
        for (int i = 0; i < hiddenDim; i++) {
            double x = gate[i];
            gate[i] = x * sigmoid(x);
        }
        // value = input @ w2
        double[] value = matVecMul(w2, input);
        // elementwise multiply
        for (int i = 0; i < hiddenDim; i++) {
            gate[i] *= value[i];
        }
        // output = gate @ w3
        return matVecMul(w3, gate);
    }

    /**
     * Apply SwiGLU to a profile (via embedded vector).
     */
    public double[] applyToProfile(CognitiveGenesisProfile profile) {
        if (profile == null) return null;
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        double[] vec = embedder.embed(profile);
        return apply(vec);
    }

    public int dim() { return dim; }
    public int hiddenDim() { return hiddenDim; }
    public long seed() { return seed; }

    /** Parameter count. */
    public long parameterCount() {
        return (long) dim * hiddenDim * 3;
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

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
