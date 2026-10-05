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
     *
     * <p>RECON-W32.30. Returns {@link Double#NaN} when the two vectors cannot be
     * compared, and 0.0 only for a genuine zero norm. It used to return <b>0.0 for
     * both</b>, and 0.0 is the mathematically correct cosine of two IDENTICAL vectors —
     * so the function answered "these are unrelated" about two vectors it had never
     * looked at. A caller comparing a 4-dim against a 32-dim vector was told a fact, and
     * the fact was false.</p>
     *
     * <p>NaN rather than an exception: this is a metric, and a metric that throws cannot
     * be used in an aggregate. NaN is loud exactly where it should be — every
     * comparison, every range assertion, every mean — and silent nowhere.</p>
     *
     * @return the cosine in [-1, 1], 0.0 for a zero-norm vector, or NaN if the vectors
     *         are null or of different lengths
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return Double.NaN;
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
     *
     * <p>RECON-W32.30. This one was the dangerous case. An L2 distance of
     * <b>0.0 means IDENTICAL</b>, so returning 0.0 for vectors of different lengths
     * asserted that two things it could not compare were the same thing. This is the
     * worst shape of a wrong answer in this codebase: it is not a weak signal, it is a
     * confident false one, and it survives every range check downstream because 0.0 is
     * in range for a distance.</p>
     *
     * <p>Now {@link Double#NaN}, which is outside every distance range and therefore
     * fails loudly rather than quietly. The property test that caught this generates
     * each vector with an INDEPENDENT random length, which is why it fired on nearly
     * every try; the generator was also wrong, and both are fixed — see the test.</p>
     *
     * @return the distance, or NaN if the vectors are null or of different lengths
     */
    public static double l2Distance(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return Double.NaN;
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
