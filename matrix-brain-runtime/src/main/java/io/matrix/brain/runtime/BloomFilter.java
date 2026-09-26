package io.matrix.brain.runtime;

/**
 * TRUE-W11 — Bloom filter stub (research iteration #3).
 *
 * <p>Simple Bloom filter with k hash functions (k = 3) over an m-bit
 * array. Membership queries are O(k). False positives possible; false
 * negatives impossible.</p>
 *
 * <p>Used here for indexing the mind's HDC store to skip entries that
 * definitely don't match a query (negative-screening).</p>
 */
public final class BloomFilter {

    private final long[] bits;
    private final int numBits;
    private final int numHashes;

    public BloomFilter(int expectedItems, double falsePositiveRate) {
        if (expectedItems <= 0) throw new IllegalArgumentException("expectedItems > 0");
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1)
            throw new IllegalArgumentException("falsePositiveRate in (0, 1)");

        // Standard Bloom filter sizing
        double ln2 = Math.log(2);
        this.numBits = Math.max(1, (int) Math.ceil(
            -expectedItems * Math.log(falsePositiveRate) / (ln2 * ln2)));
        this.numHashes = Math.max(1, (int) Math.round(
            (numBits / expectedItems) * ln2));
        this.bits = new long[(numBits + 63) / 64];
    }

    public void add(String item) {
        if (item == null) return;
        for (int i = 0; i < numHashes; i++) {
            int bit = hash(item, i);
            bits[bit >>> 6] |= (1L << (bit & 63));
        }
    }

    public boolean mightContain(String item) {
        if (item == null) return false;
        for (int i = 0; i < numHashes; i++) {
            int bit = hash(item, i);
            if ((bits[bit >>> 6] & (1L << (bit & 63))) == 0) return false;
        }
        return true;
    }

    public int numBits() { return numBits; }
    public int numHashes() { return numHashes; }

    /** FNV-1a with seed mixing for k different hash positions. */
    private int hash(String item, int i) {
        long h = 0xcbf29ce484222325L ^ ((long) i * 0x9e3779b97f4a7c15L);
        for (int j = 0; j < item.length(); j++) {
            h ^= item.charAt(j);
            h *= 0x100000001b3L;
        }
        return (int) ((h >>> 11) % numBits);
    }
}
