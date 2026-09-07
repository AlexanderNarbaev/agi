package io.matrix.perception;

import java.util.Random;

/**
 * RUN 206 — BrainVision (image→bits stub).
 *
 * <p>Stub vision subsystem that converts an image-like input
 * (e.g., raw pixels or hash) into a deterministic boolean vector.
 *
 * <p>Production would use a vision encoder; for this scaffold,
 * we hash the input bytes and spread bits.
 */
public final class BrainVision {

    public boolean[] encode(byte[] pixels) {
        if (pixels == null) pixels = new byte[0];
        // SHA-256 hash → 32 bytes → 256 bits
        byte[] hash;
        try {
            hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(pixels);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        boolean[] out = new boolean[256];
        for (int i = 0; i < out.length; i++) {
            int byteIdx = (i / 8) % hash.length;
            int bitIdx = 7 - (i % 8);
            out[i] = ((hash[byteIdx] >> bitIdx) & 1) == 1;
        }
        return out;
    }

    /** For testing: hash-based random simulation. */
    public boolean[] randomEncode(long seed, int bitLength) {
        Random rng = new Random(seed);
        boolean[] out = new boolean[bitLength];
        for (int i = 0; i < bitLength; i++) out[i] = rng.nextBoolean();
        return out;
    }

    public int[] countOnes(boolean[] bits) {
        int ones = 0;
        for (boolean b : bits) if (b) ones++;
        return new int[]{ones, bits.length - ones};
    }
}
