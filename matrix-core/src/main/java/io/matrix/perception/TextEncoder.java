package io.matrix.perception;

/**
 * RUN 150 — TextEncoder (perception boundary).
 *
 * <p>Encodes UTF-8 text into a deterministic boolean vector
 * suitable for BrcChain consumption. The encoding is:
 * <pre>
 *   text → bytes → SHA-256 hash → fixed-size boolean vector
 * </pre>
 *
 * <p>The hash is computed once and cached by content (since text
 * encoding is content-addressable). Each call with the same text
 * returns the same boolean vector (CONSTITUTION I compliance).
 *
 * <p>Output length is configurable (default 256 bits = 32 bytes =
 * one SHA-256 digest expanded to bits).
 */
public final class TextEncoder {

    public static final int DEFAULT_BIT_LENGTH = 256;

    private final int bitLength;

    public TextEncoder() {
        this(DEFAULT_BIT_LENGTH);
    }

    public TextEncoder(int bitLength) {
        if (bitLength <= 0 || bitLength > 4096) {
            throw new IllegalArgumentException("bitLength out of range: " + bitLength);
        }
        this.bitLength = bitLength;
    }

    /**
     * Encode text to a boolean vector.
     *
     * @param text input text (UTF-8)
     * @return boolean vector of {@link #bitLength} bits
     */
    public boolean[] encode(String text) {
        if (text == null) text = "";
        // SHA-256 of UTF-8 bytes, repeated to fill bitLength
        byte[] hash = sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean[] out = new boolean[bitLength];
        for (int i = 0; i < bitLength; i++) {
            int byteIdx = (i / 8) % hash.length;
            int bitIdx = 7 - (i % 8);
            out[i] = ((hash[byteIdx] >> bitIdx) & 1) == 1;
        }
        return out;
    }

    public int bitLength() {
        return bitLength;
    }

    private static byte[] sha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Wrap an int as a bit (0/1). */
    public static boolean[] intsToBooleans(int[] ints) {
        boolean[] out = new boolean[ints.length];
        for (int i = 0; i < ints.length; i++) out[i] = ints[i] != 0;
        return out;
    }
}
