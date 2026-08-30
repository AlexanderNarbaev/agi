package io.matrix.api;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * BPE-aware text-to-binary-vector converter (Wave improvement).
 *
 * <p>Replaces the 20-bit word-hash encoding in {@link Text2VecService}
 * with a position-aware 896-bit encoding matching Qwen2.5-0.5B's
 * hidden dimension. Each word contributes bits at multiple positions
 * based on its hash and length, so the 24-layer boolean chain has
 * meaningful per-position input rather than a collapsed 20-bit vector.
 *
 * <p>This is NOT full BPE (would need Qwen's tokenizer.json + merges.txt
 * which is ~6 MB); it's a position-aware hash that gives the chain
 * enough input bits to fire meaningfully across all 24 layers.
 */
@ApplicationScoped
public class ExpandedTextToBitsService {

    /** Qwen2.5-0.5B hidden dim: 896. */
    public static final int VECTOR_BITS = 896;

    /** Map a UTF-8 string to 896 boolean bits. Deterministic. */
    public boolean[] textToBits(String text) {
        boolean[] bits = new boolean[VECTOR_BITS];
        if (text == null || text.isEmpty()) return bits;
        byte[] bytes = text.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int pos = 0; pos < bytes.length; pos++) {
            // three independent bit positions per byte — gives the
            // chain enough variance to fire different neurons across
            // 24 layers without collapsing to zero
            int b = bytes[pos] & 0xFF;
            bits[hash(pos, b, 0)] = true;
            bits[hash(pos, b, 13)] = true;
            bits[hash(pos, b, 27)] = true;
        }
        return bits;
    }

    /** Convert boolean[] back to a long for legacy long-based APIs. */
    public long textToBitsLong(String text) {
        boolean[] bits = textToBits(text);
        long out = 0L;
        for (int i = 0; i < Math.min(64, bits.length); i++) {
            if (bits[i]) out |= (1L << i);
        }
        return out;
    }

    private static int hash(int pos, int byteVal, int salt) {
        long h = ((long) pos * 2654435761L) ^ ((long) byteVal * 40503L) ^ ((long) salt * 16777619L);
        h = (h >>> 16) ^ h;
        // (int) cast of a long may produce Integer.MIN_VALUE; bitwise-and with mask
        int idx = (int) (h & 0x7FFFFFFF);
        return idx % VECTOR_BITS;
    }
}