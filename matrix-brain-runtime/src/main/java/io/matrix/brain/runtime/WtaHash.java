package io.matrix.brain.runtime;

import java.util.Random;

/**
 * TRUE-W13 — Sparse-HDC via winner-take-all (WTA) hashing.
 *
 * <p>Each token activates exactly {@code K} bits out of {@code D} via
 * 32-bit MurmurHash-derived scores: the {@code K} highest-scoring bits
 * win. This produces dense-but-sparse codes suitable for bit-cosine
 * similarity while using only {@code K} bits of storage per token.</p>
 *
 * <p>Design rationale (per docs-v2/designs/DESIGN-NN-SPARSE-HDC.md):</p>
 * <ul>
 *   <li>Standard dense 10000-bit codes use ~1.25 KB per token.</li>
 *   <li>Sparse K-of-D codes use {@code K*D/8} bytes per token.</li>
 *   <li>For K=64, D=10000: 8 KB per token — but cosine comparison is 50x
 *       faster because XOR/AND/OR operate on the K set bits.</li>
 *   <li>For {@code K=64, D=1024}: 8 KB per token, similar cosine fidelity,
 *       even faster comparisons (cache-friendly).</li>
 * </ul>
 *
 * <p>CONSTITUTION Article III — deterministic; seeded RNG.</p>
 */
public final class WtaHash {

    private final int dimension;
    private final int k;
    private final Random rng;

    public WtaHash(int dimension, int k) {
        if (dimension <= 0) throw new IllegalArgumentException("dimension must be > 0");
        if (k <= 0 || k > dimension) throw new IllegalArgumentException(
            "k in 1.." + dimension);
        this.dimension = dimension;
        this.k = k;
        this.rng = new Random(42L);
    }

    public WtaHash(int dimension, int k, long seed) {
        if (dimension <= 0) throw new IllegalArgumentException("dimension must be > 0");
        if (k <= 0 || k > dimension) throw new IllegalArgumentException(
            "k in 1.." + dimension);
        this.dimension = dimension;
        this.k = k;
        this.rng = new Random(seed);
    }

    public int dimension() { return dimension; }
    public int k() { return k; }

    /**
     * Hash a single token into a sparse bit-vector representation
     * (returns a {@code long[]} array of exactly {@code k} set bit indices).
     */
    public int[] hashToken(String token) {
        // Score each bit position by FNV-1a hash mod a large prime;
        // pick the top-k by score.
        double[] scores = new double[dimension];
        int[] indices = new int[dimension];
        for (int i = 0; i < dimension; i++) {
            scores[i] = score(token, i);
            indices[i] = i;
        }
        // Partial sort: take top-k by score. Use a small selection algorithm.
        int[] topK = selectTopK(scores, indices, k);
        return topK;
    }

    /** Hash a phrase (multiple tokens) by element-wise OR-binding of token codes. */
    public int[] hashPhrase(String[] tokens) {
        int[] combined = new int[k];
        // Use a tally array to combine hits across tokens
        int[] counts = new int[dimension];
        for (String tok : tokens) {
            for (int idx : hashToken(tok)) counts[idx]++;
        }
        // Pick the k indices with highest counts
        double[] scores = new double[dimension];
        int[] indices = new int[dimension];
        for (int i = 0; i < dimension; i++) {
            scores[i] = counts[i];
            indices[i] = i;
        }
        return selectTopK(scores, indices, k);
    }

    /** FNV-1a-derived score for (token, bit-index). */
    private double score(String token, int idx) {
        long h = 0xcbf29ce484222325L;
        String combined = token + "#" + idx;
        for (int i = 0; i < combined.length(); i++) {
            h ^= combined.charAt(i);
            h *= 0x100000001b3L;
        }
        // Map to a deterministic [0, 1) score
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x20000000000000L;
    }

    /**
     * Quickselect-based top-k selection. O(D) average. Mutates {@code scores}/
     * {@code indices} arrays.
     */
    private static int[] selectTopK(double[] scores, int[] indices, int k) {
        // We only need top-k; do a partial quickselect.
        int n = scores.length;
        if (k >= n) {
            int[] all = new int[n];
            System.arraycopy(indices, 0, all, 0, n);
            return all;
        }
        quickselect(scores, indices, k);
        // After quickselect with kth, the first k are the top-k (unsorted)
        int[] result = new int[k];
        System.arraycopy(indices, 0, result, 0, k);
        return result;
    }

    private static void quickselect(double[] a, int[] idx, int k) {
        int left = 0, right = a.length - 1;
        while (left < right) {
            int pivotIdx = partition(a, idx, left, right);
            if (k == pivotIdx) return;
            if (k < pivotIdx) right = pivotIdx - 1;
            else left = pivotIdx + 1;
        }
    }

    private static int partition(double[] a, int[] idx, int left, int right) {
        double pivot = a[right];
        int i = left;
        for (int j = left; j < right; j++) {
            if (a[j] >= pivot) {
                double tmp = a[i]; a[i] = a[j]; a[j] = tmp;
                int ti = idx[i]; idx[i] = idx[j]; idx[j] = ti;
                i++;
            }
        }
        double tmp = a[i]; a[i] = a[right]; a[right] = tmp;
        int ti = idx[i]; idx[i] = idx[right]; idx[right] = ti;
        return i;
    }
}
