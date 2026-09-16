package io.matrix.consciousness;

import java.util.Random;

/**
 * W203 — Cognitive Embedding.
 *
 * <p>Distributed vector representation of cognitive profiles, inspired by
 * Word2Vec (Mikolov 2013) and the geometric view of meaning in high-dimensional
 * space. Similar cognitive states are mapped to nearby vectors.
 *
 * <p>Each profile is encoded as a fixed-dimension vector (default 64).
 * Distance metrics: cosine (semantic similarity) and L2 (geometric proximity).
 *
 * <p>Inspired by: Mikolov et al. 2013 "Distributed Representations of Words
 * and Phrases and their Compositionality"; Vaswani et al. 2017 "Attention Is
 * All You Need" (vector space view).
 *
 * <p>Use cases:
 * - Find nearest cognitive states in profile history
 * - Cluster similar cognitive regimes
 * - Visualize cognitive trajectory in vector space
 *
 * <p>CONSTITUTION VI compliance: geometric representation of cognitive
 * state, not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random instances are seeded.
 */
public final class CognitiveEmbedding {

    /** Default embedding dimension. */
    public static final int DEFAULT_DIM = 64;

    /** Random seed for deterministic projection matrix. */
    private final long seed;
    private final int dimension;
    /** Fixed projection matrix [13 fields × dim]. */
    private final double[][] projection;
    /** Per-field mean (centers the projection). */
    private final double[] fieldMeans;
    /** Per-field scale (normalizes the projection). */
    private final double[] fieldScales;

    /**
     * Construct embedding with default 64-dim vector space.
     */
    public CognitiveEmbedding() {
        this(DEFAULT_DIM, 0xC09F1107L);
    }

    /**
     * Construct embedding with custom dimension and seed.
     *
     * @param dimension embedding dimensionality (positive)
     * @param seed RNG seed (CONSTITUTION I v3 compliance)
     */
    public CognitiveEmbedding(int dimension, long seed) {
        if (dimension < 1) {
            throw new IllegalArgumentException("dimension must be >= 1");
        }
        this.dimension = dimension;
        this.seed = seed;
        this.projection = new double[13][dimension];
        this.fieldMeans = new double[13];
        this.fieldScales = new double[13];
        Random rng = new Random(seed);
        for (int f = 0; f < 13; f++) {
            // Normal initialization (He-style scaling)
            double scale = 1.0 / Math.sqrt((double) dimension);
            for (int d = 0; d < dimension; d++) {
                projection[f][d] = rng.nextGaussian() * scale;
            }
            fieldMeans[f] = 0.5;
            fieldScales[f] = 1.0;
        }
    }

    /**
     * Embed a profile into the vector space.
     *
     * @param profile cognitive profile
     * @return dense vector of length {@link #dimension()}
     */
    public double[] embed(CognitiveGenesisProfile profile) {
        if (profile == null) return new double[dimension];
        double[] vector = new double[dimension];
        double[] fields = extractFields(profile);
        for (int f = 0; f < 13; f++) {
            double normalized = (fields[f] - fieldMeans[f]) / fieldScales[f];
            for (int d = 0; d < dimension; d++) {
                vector[d] += normalized * projection[f][d];
            }
        }
        return vector;
    }

    /**
     * Compute cosine similarity between two vectors.
     * Returns 0.0 if either vector has zero norm.
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Compute L2 (Euclidean) distance between two vectors.
     */
    public static double l2Distance(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Find the k nearest neighbors of a profile in a sequence.
     * Returns indices sorted by ascending L2 distance.
     */
    public int[] nearestNeighbors(CognitiveGenesisProfile query,
                                    java.util.List<CognitiveGenesisProfile> profiles,
                                    int k) {
        if (profiles == null || profiles.isEmpty() || k <= 0) return new int[0];
        double[] qVec = embed(query);
        int n = profiles.size();
        double[] dists = new double[n];
        for (int i = 0; i < n; i++) {
            double[] pVec = embed(profiles.get(i));
            dists[i] = l2Distance(qVec, pVec);
        }
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(dists[a], dists[b]));
        int resultLen = Math.min(k, n);
        int[] result = new int[resultLen];
        for (int i = 0; i < resultLen; i++) result[i] = indices[i];
        return result;
    }

    /** Embedding dimension. */
    public int dimension() { return dimension; }

    /** Seed used for projection. */
    public long seed() { return seed; }

    /** Extract the 13 cognitive fields as double array. */
    private static double[] extractFields(CognitiveGenesisProfile p) {
        return new double[] {
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            Math.min(1.0, p.kolmogorovK() / 100.0),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK() / 8.0, p.memristorConductance(),
            Math.min(1.0, p.lSystemComplexityRatio() / 5.0)
        };
    }
}
