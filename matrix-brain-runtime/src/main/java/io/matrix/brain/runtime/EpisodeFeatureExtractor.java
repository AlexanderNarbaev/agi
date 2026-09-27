package io.matrix.brain.runtime;

import java.util.BitSet;

/**
 * RECON-W3 Part B Step 1 — Convert episodic entries into bit-vector features
 * suitable for Tsetlin/MpdtGa induction.
 *
 * <p>Feature vector format: packed {@code long[]} where the j-th bit of the
 * i-th long holds the j-th bit of the encoded entry. Dimensionality defaults
 * to 256 bits (4 × 64-bit longs); tests use higher dimensions for stress.</p>
 *
 * <p><b>Determinism</b>: encoding uses FNV-1a 64-bit hashing (one bit per token
 * via {@link PersistentHdcStore#hashToVector}). Same input text ⇒ same bit-vector.</p>
 */
public final class EpisodeFeatureExtractor {

    public static final int DEFAULT_DIM = 256;

    private final int dim;
    private final int packedLen;   // number of longs = (dim + 63) / 64

    public EpisodeFeatureExtractor() {
        this(DEFAULT_DIM);
    }

    public EpisodeFeatureExtractor(int dim) {
        if (dim <= 0 || dim > 65536) throw new IllegalArgumentException("dim out of range");
        this.dim = dim;
        this.packedLen = (dim + 63) / 64;
    }

    public int dim() { return dim; }
    public int packedLength() { return packedLen; }

    /**
     * Encode an EpisodicLog.Entry into a packed long[] bit-vector.
     * Combines the entry's input + reply text via PersistentHdcStore.hashToVector
     * so the feature reflects the WHOLE interaction (input paired with reply).
     */
    public long[] encode(EpisodicLog.Entry entry) {
        if (entry == null) return new long[packedLen];
        String text = entry.input() + " " + entry.reply();
        BitSet bs = PersistentHdcStore.hashToVector(text, dim);
        return pack(bs, packedLen);
    }

    /** Encode an arbitrary text string (for tests + ad-hoc induction). */
    public long[] encode(String text) {
        BitSet bs = PersistentHdcStore.hashToVector(text, dim);
        return pack(bs, packedLen);
    }

    /** Pack a BitSet into a long[] of fixed length packedLen (LSB-first bit ordering per long). */
    static long[] pack(BitSet bs, int packedLen) {
        long[] out = new long[Math.max(1, packedLen)];
        int setBits = bs.length();
        for (int i = 0; i < setBits; i++) {
            if (bs.get(i)) {
                if ((i >>> 6) < out.length) {
                    out[i >>> 6] |= (1L << i);
                }
            }
        }
        return out;
    }

    /** Hamming distance between two packed vectors (number of differing bits). */
    public static int hammingDistance(long[] a, long[] b) {
        int n = Math.min(a.length, b.length);
        int dist = 0;
        for (int i = 0; i < n; i++) {
            dist += Long.bitCount(a[i] ^ b[i]);
        }
        // Count remaining bits in the longer vector as different
        int aLen = a.length, bLen = b.length;
        for (int i = n; i < Math.max(aLen, bLen); i++) {
            long bits = i < aLen ? a[i] : b[i];
            dist += Long.bitCount(bits);
        }
        return dist;
    }

    /** Cosine similarity (number of set bits in intersection over union). */
    public static double cosine(long[] a, long[] b) {
        int inter = 0, union = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            inter += Long.bitCount(a[i] & b[i]);
            union += Long.bitCount(a[i] | b[i]);
        }
        int aLen = a.length, bLen = b.length;
        for (int i = n; i < aLen; i++) union += Long.bitCount(a[i]);
        for (int i = n; i < bLen; i++) union += Long.bitCount(b[i]);
        return union == 0 ? 0.0 : (double) inter / union;
    }
}
