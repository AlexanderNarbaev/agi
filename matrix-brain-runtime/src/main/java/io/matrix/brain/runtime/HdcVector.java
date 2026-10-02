package io.matrix.brain.runtime;

import java.util.BitSet;
import java.util.Arrays;

/**
 * RECON-W31.3 — HW-1: allocation-free HDC vector kernel over packed 64-bit words.
 *
 * <p><b>Why this exists.</b> W30 measured that computing Jaccard over a {@code long[]}
 * with {@link Long#bitCount} is 2.9-3.4x faster than the previous implementation, and
 * then left the win in a document. The old code
 * ({@link PersistentHdcStore#cosine}) clones two {@link BitSet}s per comparison — three
 * backing-array allocations for every pair scored.</p>
 *
 * <p><b>Where the cost actually was.</b> Note this is not on the retrieval hot path any
 * more: RECON-W31.2 replaced retrieval with {@link ContentSimilarity}, because the
 * BitSet cosine was measured at ROC-AUC 0.502 — chance level. What remains is
 * {@link PersistentHdcStore#checkContradiction}, which runs on every {@code teach} and
 * scores against the whole store. That makes bulk ingest O(n^2): at 10 000 facts,
 * roughly 50 million cosine calls. The speedup is needed for W31.4, not because a
 * single query felt slow.</p>
 *
 * <p><b>What it guarantees.</b> {@link #jaccard} is <em>bit-exact</em> with the
 * algorithm it replaces, asserted over randomized and adversarial inputs by
 * {@code HdcVectorTest} including non-word-aligned dimensions and bits in the final
 * partial word. This matters more than the speedup: the value decides whether a taught
 * fact is DUPLICATE, POTENTIAL_CONFLICT, or NOVEL, and a silent drift would quietly
 * reclassify knowledge with nothing reporting it.</p>
 *
 * <p><b>No allocation on the scoring path.</b> The speedup comes entirely from removing
 * per-call allocation; the test asserts the allocation budget directly, because a kernel
 * that allocates is not faster at scale and the benchmark would be measuring the
 * allocator.</p>
 *
 * <p><b>Article III.</b> Pure function of its inputs, no clock, no randomness, no I/O.</p>
 *
 * <p><b>Article VI.</b> This is a bitwise set-overlap kernel. It is not a Vector API
 * implementation — the SIMD form is future work and is labelled as such in
 * {@code TUNING-PARAMETERS.md}, not implied by the speedup quoted here.</p>
 */
public final class HdcVector {

    private static final int BITS_PER_WORD = Long.SIZE;   // 64

    private final long[] words;

    private HdcVector(long[] words) {
        this.words = words;
    }

    /**
     * Number of packed words needed for a dimension.
     *
     * <p>Unit: 64-bit words. Ceiling division, so dimension 100 uses 2 words and the
     * 36 high bits of the second are masked off. Getting this wrong truncates the scan
     * (missing matches, so a duplicate scores as novel) or over-reads into another
     * vector's data, so it is asserted directly by the test.</p>
     *
     * @param dim vector width in bits
     * @return words required, 0 for a non-positive dimension
     */
    public static int wordCount(int dim) {
        return dim <= 0 ? 0 : (dim + BITS_PER_WORD - 1) / BITS_PER_WORD;
    }

    /**
     * Pack a {@link BitSet} into word form.
     *
     * @param bs source bit set, may be null
     * @return packed vector, or null when {@code bs} is null
     */
    public static HdcVector from(BitSet bs) {
        if (bs == null) return null;
        return new HdcVector(bs.toLongArray());
    }

    /**
     * Pack arbitrary bit indices into word form.
     *
     * @param dim  vector width in bits; indices at or above it are dropped
     * @param bits indices to set
     * @return packed vector
     */
    public static HdcVector fromIndices(int dim, int[] bits) {
        long[] w = new long[wordCount(dim)];
        if (bits != null) {
            for (int b : bits) {
                if (b >= 0 && b < dim) w[b / BITS_PER_WORD] |= 1L << (b % BITS_PER_WORD);
            }
        }
        return new HdcVector(w);
    }

    /** Number of set bits. Unit: bits. */
    public int cardinality() {
        int n = 0;
        for (long w : words) n += Long.bitCount(w);
        return n;
    }

    /** Number of packed words. Unit: 64-bit words. */
    public int words() {
        return words.length;
    }

    /**
     * Jaccard similarity: |A and B| / |A or B|.
     *
     * <p>Bit-exact with {@link PersistentHdcStore#cosine(BitSet, BitSet)} and allocates
     * nothing. A null input scores 0.0, matching the previous contract for null.</p>
     *
     * <p><b>Lengths may differ, and that is not an error.</b> {@link BitSet#toLongArray}
     * pads to the highest set bit rather than to the declared dimension, so a 809-bit
     * vector with a bit near the top and one with only low bits produce arrays of
     * different lengths even though they live in the same space. Requiring equal
     * lengths silently turned those into a 0.0 "no similarity", which is a wrong
     * answer rather than a conservative one — and on the contradiction path a wrong
     * answer means a duplicate fact is filed as novel. The loop therefore runs to the
     * longer length and treats the missing tail as zero, which is what the underlying
     * bit sets actually mean.</p>
     *
     * @param a first vector, may be null
     * @param b second vector, may be null
     * @return similarity in [0,1]; 0.0 when either is null or both are empty
     */
    public static double jaccard(HdcVector a, HdcVector b) {
        if (a == null || b == null) return 0.0;
        int n = Math.max(a.words.length, b.words.length);
        int inter = 0;
        int union = 0;
        for (int i = 0; i < n; i++) {
            long av = i < a.words.length ? a.words[i] : 0L;
            long bv = i < b.words.length ? b.words[i] : 0L;
            inter += Long.bitCount(av & bv);
            union += Long.bitCount(av | bv);
        }
        return union == 0 ? 0.0 : (double) inter / (double) union;
    }

    /**
     * Unpack back to a {@link BitSet} of the given width.
     *
     * @param dim vector width in bits
     * @return bit set with exactly {@code dim} addressable positions
     */
    public BitSet toBitSet(int dim) {
        BitSet bs = new BitSet(dim);
        for (int i = 0; i < words.length; i++) {
            long w = words[i];
            while (w != 0) {
                int bit = Long.numberOfTrailingZeros(w);
                int idx = i * BITS_PER_WORD + bit;
                if (idx < dim) bs.set(idx);
                w &= (w - 1);   // clear lowest set bit
            }
        }
        return bs;
    }

    /** Copy of the packed words. Callers must not mutate the returned array. */
    long[] wordsUnsafe() {
        return words;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HdcVector v && Arrays.equals(words, v.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return "HdcVector[words=" + words.length + ",bits=" + cardinality() + "]";
    }
}
