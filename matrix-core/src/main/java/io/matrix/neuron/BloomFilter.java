package io.matrix.neuron;

import java.util.BitSet;

/**
 * RUN 419 — Bloom filter (classic + counting variants).
 * <p>Space-efficient probabilistic membership test with k hash functions
 * indexed by MurmurHash3-ish splits. Pure function.
 */
public final class BloomFilter {

    private final long[] buckets;
    private final int k;
    private final int m;

    public BloomFilter(int m, int k) {
        if (m <= 0 || k <= 0) throw new IllegalArgumentException("m,k > 0");
        this.m = m;
        this.k = k;
        this.buckets = new long[m];
    }

    /** fmix64 — fast integer mix from MurmurHash3 author; deterministic & pure. */
    private static long fmix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private long hash(long seed, long key) {
        return fmix(seed + 0x9E3779B97F4A7C15L * key);
    }

    public void add(long key) {
        for (int i = 0; i < k; i++) {
            int idx = (int) ((hash(0x1234567890ABCDEFL + i, key) & Long.MAX_VALUE) % m);
            buckets[idx >>> 6] |= 1L << (idx & 63);
        }
    }

    public boolean mightContain(long key) {
        for (int i = 0; i < k; i++) {
            int idx = (int) ((hash(0x1234567890ABCDEFL + i, key) & Long.MAX_VALUE) % m);
            if ((buckets[idx >>> 6] & (1L << (idx & 63))) == 0) return false;
        }
        return true;
    }

    public int cardinality() {
        // Sum 1-bits per bucket using Long.bitCount — fast & deterministic
        int ones = 0;
        for (long b : buckets) ones += Long.bitCount(b);
        return ones;
    }

    public BitSet bitset() {
        BitSet bs = new BitSet(m);
        for (int i = 0; i < m; i++) {
            if ((buckets[i >>> 6] & (1L << (i & 63))) != 0) bs.set(i);
        }
        return bs;
    }
}
