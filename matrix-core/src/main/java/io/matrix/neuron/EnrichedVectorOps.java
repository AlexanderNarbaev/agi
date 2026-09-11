package io.matrix.neuron;

import io.matrix.imports.ChainEnrichedOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase Q.4 — Vector operations on enriched state.
 *
 * <p>Provides cosine similarity, nearest neighbor search, and
 * aggregate statistics over ChainEnrichedOutput vectors.
 *
 * <p>All operations are pure functions (CONSTITUTION I compliant).
 *
 * <p>The "vectors" here are 4D chemical vectors per neuron; we can
 * also operate on per-layer or per-chain aggregates.
 */
public final class EnrichedVectorOps {

    private EnrichedVectorOps() {}

    /** Cosine similarity between two 4D vectors, in [-1, 1]. */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("null vectors");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Euclidean distance between two vectors. */
    public static double euclideanDistance(double[] a, double[] b) {
        if (a == null || b == null) throw new IllegalArgumentException("null");
        if (a.length != b.length) {
            throw new IllegalArgumentException("dim mismatch");
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /** Mean of the chemical vectors of all neurons in an enriched output. */
    public static double[] meanChemicalVector(ChainEnrichedOutput output) {
        double[] mean = new double[EnrichedNeuron.CHEMICAL_DIM];
        long count = 0;
        for (double[][] layer : output.chemicalPerLayer()) {
            for (double[] chem : layer) {
                for (int i = 0; i < chem.length; i++) mean[i] += chem[i];
                count++;
            }
        }
        if (count > 0) {
            for (int i = 0; i < mean.length; i++) mean[i] /= count;
        }
        return mean;
    }

    /**
     * Find the top-K most similar enriched outputs to a query vector,
     * using cosine similarity. Returns indices into the input list,
     * sorted by similarity descending.
     *
     * @param query 4D query vector
     * @param candidates list of candidate 4D vectors
     * @param k number of top results to return
     * @return indices of top-K candidates (descending similarity)
     */
    public static List<Integer> topKByCosine(double[] query, List<double[]> candidates, int k) {
        if (query == null || candidates == null) {
            throw new IllegalArgumentException("null");
        }
        if (k <= 0) return List.of();
        // Score each candidate
        List<int[]> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double sim = cosineSimilarity(query, candidates.get(i));
            // Encode as int bits: upper 32 = sim*1e9 (fixed point), lower 32 = index
            long encoded = ((long) (sim * 1e9)) << 32 | (i & 0xFFFFFFFFL);
            scored.add(new int[]{(int) (encoded >> 32), i});
        }
        // Sort by similarity descending
        scored.sort((a, b) -> Integer.compare(b[0], a[0]));
        List<Integer> result = new ArrayList<>(Math.min(k, candidates.size()));
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            result.add(scored.get(i)[1]);
        }
        return result;
    }

    /**
     * Find the top-K nearest enriched neurons (by chemical vector)
     * to a query neuron.
     */
    public static List<EnrichedNeuron> topKNearestNeurons(
            EnrichedNeuron query, List<EnrichedNeuron> pool, int k) {
        if (query == null || pool == null) throw new IllegalArgumentException("null");
        if (k <= 0) return List.of();

        List<double[]> candidates = new ArrayList<>(pool.size());
        for (EnrichedNeuron n : pool) candidates.add(n.chemicalVector());
        List<Integer> idxs = topKByCosine(query.chemicalVector(), candidates, k);
        List<EnrichedNeuron> result = new ArrayList<>(idxs.size());
        for (int idx : idxs) result.add(pool.get(idx));
        return result;
    }

    /** Cosine similarity matrix between all pairs in a list. */
    public static double[][] pairwiseCosineSimilarity(List<double[]> vectors) {
        if (vectors == null) throw new IllegalArgumentException("null");
        int n = vectors.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            matrix[i][i] = 1.0;
            for (int j = i + 1; j < n; j++) {
                double sim = cosineSimilarity(vectors.get(i), vectors.get(j));
                matrix[i][j] = sim;
                matrix[j][i] = sim;
            }
        }
        return matrix;
    }
}
