package io.matrix.advanced;

import java.util.*;

/**
 * W831 — Sparse HDC for Memory Compression.
 *
 * Block-sparse hyperdimensional vectors with 90% zeros
 * for 10x memory compression while preserving accuracy.
 */
public final class SparseHDC {

    private final int dimension;
    private final double sparsity; // fraction of zeros (0.9 = 90% zeros)
    private final Random rng;
    private final Map<String, SparseVector> vectors = new HashMap<>();

    public SparseHDC(int dimension, double sparsity, long seed) {
        if (sparsity < 0 || sparsity > 0.99) {
            throw new IllegalArgumentException("Sparsity must be in [0, 0.99]");
        }
        this.dimension = dimension;
        this.sparsity = sparsity;
        this.rng = new Random(seed);
    }

    /**
     * A sparse vector storing only non-zero indices.
     */
    public record SparseVector(int dimension, int[] nonzeroIndices) {
        public int nonZeroCount() { return nonzeroIndices.length; }
        public double actualSparsity() {
            return 1.0 - ((double) nonzeroIndices.length / dimension);
        }
    }

    /**
     * Generate a sparse random vector with given sparsity.
     */
    public SparseVector generateSparse(String id) {
        int nonZeroCount = (int) (dimension * (1.0 - sparsity));
        Set<Integer> indices = new HashSet<>();
        while (indices.size() < nonZeroCount) {
            indices.add(rng.nextInt(dimension));
        }
        SparseVector v = new SparseVector(dimension, indices.stream().mapToInt(Integer::intValue).toArray());
        vectors.put(id, v);
        return v;
    }

    /**
     * Binding (XOR) of two sparse vectors.
     * Result is also sparse.
     */
    public SparseVector bind(SparseVector a, SparseVector b) {
        Set<Integer> symmetricDiff = new HashSet<>();
        for (int i : a.nonzeroIndices()) symmetricDiff.add(i);
        for (int i : b.nonzeroIndices()) {
            if (symmetricDiff.contains(i)) symmetricDiff.remove(i);
            else symmetricDiff.add(i);
        }
        int[] result = symmetricDiff.stream().mapToInt(Integer::intValue).toArray();
        java.util.Arrays.sort(result);
        return new SparseVector(dimension, result);
    }

    /**
     * Similarity between two sparse vectors.
     */
    public double similarity(SparseVector a, SparseVector b) {
        Set<Integer> setA = new HashSet<>();
        for (int i : a.nonzeroIndices()) setA.add(i);
        int intersect = 0;
        for (int i : b.nonzeroIndices()) {
            if (setA.contains(i)) intersect++;
        }
        return (2.0 * intersect) / (a.nonZeroCount() + b.nonZeroCount());
    }

    public int getDimension() { return dimension; }
    public double getSparsity() { return sparsity; }
    public int getStoredVectorCount() { return vectors.size(); }
}
