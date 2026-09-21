package io.matrix.neuron;

/**
 * RUN 432 — MinHash signatures for Jaccard-similarity estimation.
 * <p>{@code Pr[minhash_a(S) = minhash_a(T)] = Jaccard(S, T)}. Computing the
 * minimum hash over {@code k} independent permutations gives a {@code k}-length
 * signature whose fraction-of-equal-pairs estimates Jaccard.
 *
 * <p>Uses k non-cryptographic hashes: {@code h_i(x) = fmix64(a_i * fmix64(x) + b_i)}
 * without modular arithmetic (overflow accepted). Pure function.
 * CONSTITUTION I-safe.
 */
public final class MinHash {

    private MinHash() {}

    /**
     * Compute the Jaccard similarity estimate between two sets using k
     * hash permutations.
     * @param a first set as long array (e.g. shingle hashes)
     * @param b second set as long array
     * @param k number of minhash functions
     * @param seed RNG seed for permutation coefficients
     */
    public static double jaccard(long[] a, long[] b, int k, long seed) {
        if (k <= 0) throw new IllegalArgumentException("k > 0");
        java.util.Random rng = new java.util.Random(seed);
        long[] coA = new long[k];
        long[] coB = new long[k];
        for (int i = 0; i < k; i++) {
            coA[i] = rng.nextLong();
            coB[i] = rng.nextLong();
        }
        int equal = 0;
        for (int i = 0; i < k; i++) {
            long minA = minHash(a, coA[i], coB[i]);
            long minB = minHash(b, coA[i], coB[i]);
            if (minA == minB) equal++;
        }
        return (double) equal / k;
    }

    private static long minHash(long[] xs, long ai, long bi) {
        long min = Long.MAX_VALUE;
        for (long x : xs) {
            long h = fmix(x);
            // hash_i(x) = ai * fmix(x) + bi (natural overflow)
            long hh = ai * h + bi;
            if (hh < min) min = hh;
        }
        return min;
    }

    /** fmix64 MurmurHash3 finaliser (deterministic). */
    private static long fmix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }
}
