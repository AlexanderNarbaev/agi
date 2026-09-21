package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W244 — Cognitive Linear Attention (Performer/FAVOR+ style).
 *
 * <p>Inspired by Performer (Choromanski et al. 2020). Linear-time
 * attention via random feature maps φ(x). Approximates softmax
 * attention with O(N) complexity.
 *
 * <p>φ(x) = exp(-||x||²/2) * [cos(w_i · x); sin(w_i · x)] for random w_i
 *
 * <p>CONSTITUTION VI compliance: linear attention cognitive state,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveLinearAttention {

    private final int dim;
    private final int numFeatures;
    private final long seed;
    private final double[][] features; // [numFeatures × dim]
    private final double[] featureNorms;

    public CognitiveLinearAttention(int dim, int numFeatures, long seed) {
        if (dim < 1 || numFeatures < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.dim = dim;
        this.numFeatures = numFeatures;
        this.seed = seed;
        Random rng = new Random(seed);
        this.features = new double[numFeatures][dim];
        this.featureNorms = new double[numFeatures];
        for (int f = 0; f < numFeatures; f++) {
            double norm2 = 0;
            for (int d = 0; d < dim; d++) {
                features[f][d] = rng.nextGaussian();
                norm2 += features[f][d] * features[f][d];
            }
            featureNorms[f] = Math.sqrt(norm2);
        }
    }

    /**
     * Compute feature map φ(x).
     */
    public double[] featureMap(double[] x) {
        if (x == null || x.length != dim) return null;
        double[] result = new double[2 * numFeatures];
        double xNorm2 = 0;
        for (double v : x) xNorm2 += v * v;
        double scale = Math.exp(-xNorm2 / 2.0);
        for (int f = 0; f < numFeatures; f++) {
            double dot = 0;
            for (int d = 0; d < dim; d++) {
                dot += features[f][d] * x[d];
            }
            result[2 * f] = scale * Math.cos(dot / featureNorms[f]);
            result[2 * f + 1] = scale * Math.sin(dot / featureNorms[f]);
        }
        return result;
    }

    /**
     * Compute linear attention output: KV · φ(q) where KV is the
     * precomputed sum of φ(k) * v.
     */
    public double[] attend(double[] query, double[] kvs) {
        if (query == null) return null;
        double[] phiQ = featureMap(query);
        if (phiQ == null || kvs == null) return null;
        if (kvs.length != dim * 2 * numFeatures) return null;
        double[] result = new double[dim];
        for (int d = 0; d < dim; d++) {
            double sum = 0;
            for (int f = 0; f < numFeatures; f++) {
                sum += phiQ[2 * f] * kvs[d * 2 * numFeatures + 2 * f];
                sum += phiQ[2 * f + 1] * kvs[d * 2 * numFeatures + 2 * f + 1];
            }
            result[d] = sum;
        }
        return result;
    }

    /**
     * Precompute KV from profile sequence.
     */
    public double[] computeKVs(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return null;
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        double[] kvs = new double[dim * 2 * numFeatures];
        for (CognitiveGenesisProfile p : profiles) {
            double[] v = embedder.embed(p);
            double[] phiV = featureMap(v);
            if (phiV != null) {
                for (int d = 0; d < dim; d++) {
                    for (int f = 0; f < numFeatures; f++) {
                        kvs[d * 2 * numFeatures + 2 * f] += phiV[2 * f];
                        kvs[d * 2 * numFeatures + 2 * f + 1] += phiV[2 * f + 1];
                    }
                }
            }
        }
        return kvs;
    }

    public int dim() { return dim; }
    public int numFeatures() { return numFeatures; }
    public long seed() { return seed; }
}
