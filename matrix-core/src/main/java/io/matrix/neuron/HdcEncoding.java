package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 437 — Hyperdimensional Computing encoding primitives (DESIGN-54).
 *
 * <p>Foundational HDC operations on bipolar vectors ({@code +1, -1}) of fixed
 * dimension {@link #DIM}. Backed by packed {@code long[]} for SIMD-friendly
 * Hamming-distance via {@link io.matrix.imports.HammingNative} when available.
 *
 * <h2>Why bipolar</h2>
 * <ul>
 *   <li>Elementwise XOR doubles as bipolar multiply ({@code a*b = 1-(a^b)}).
 *       Binding two codes yields a near-orthogonal composite that preserves
 *       component identity (Kanerva 1988, Plate HRR 1995).</li>
 *   <li>Majority-vote bundle over many codes is well-defined and converges
 *       to a meaningful centroid (Kanerva, Section 5.3).</li>
 *   <li>Permutation (circular shift) is the role-filler operator and trivially
 *       invertible.</li>
 * </ul>
 *
 * <h2>CONSTITUTION I compliance</h2>
 * All randomness is supplied by a caller-provided {@link Random}; no
 * {@code Math.random()} or {@code System.currentTimeMillis()} in runtime paths.
 * All methods are pure functions of their arguments.
 *
 * <h2>Practical sizes</h2>
 * <ul>
 *   <li>{@link #DIM}=1024 (16 longs) — small, edge-friendly, capacity ~10^308.</li>
 *   <li>{@link #DIM}=10000 (157 longs) — Kanerva textbook, higher capacity
 *       but 10× the memory.</li>
 * </ul>
 * DIM=1024 chosen for first version: fits in 128 bytes per vector, Hamming
 * distance is a single XOR-popcount, bundle is 16 longs of majority vote.
 */
public final class HdcEncoding {

    /** Default vector dimension. Must be a multiple of 64. */
    public static final int DIM = 1024;

    /** Number of {@code long} words needed to pack DIM bits. */
    public static final int WORDS = DIM / Long.SIZE; // 16

    /** Hard limit for unused-warning elimination. */
    private static final long[] EMPTY_LONG = new long[0];

    private HdcEncoding() {}

    /**
     * Allocate a fresh zero vector.
     */
    public static long[] zero() {
        return new long[WORDS];
    }

    /**
     * Allocate a random bipolar vector. Each bit is +1 with probability 0.5.
     */
    public static long[] random(Random rng) {
        if (rng == null) throw new IllegalArgumentException("rng is null");
        long[] v = new long[WORDS];
        for (int i = 0; i < WORDS; i++) {
            v[i] = rng.nextLong();
        }
        return v;
    }

    /**
     * Allocate a sparse bipolar vector with {@code ones} bits set to +1
     * and the rest -1. Useful for high-level symbol coding where each symbol
     * activates a small distinctive subset (Kanerva "thin code").
     *
     * @param dim   number of bits, must equal {@link #DIM}
     * @param ones  number of +1 bits (the rest are -1)
     * @param rng   RNG source
     */
    public static long[] sparseRandom(int dim, int ones, Random rng) {
        if (dim != DIM) {
            throw new IllegalArgumentException("only DIM=" + DIM + " supported");
        }
        if (ones < 0 || ones > DIM) {
            throw new IllegalArgumentException("ones out of range");
        }
        if (rng == null) throw new IllegalArgumentException("rng is null");

        long[] v = new long[WORDS];
        for (int i = 0; i < WORDS; i++) {
            v[i] = -1L; // all bits = 1
        }
        // Sample `ones` distinct positions
        int[] pos = new int[ones];
        for (int k = 0; k < ones; k++) {
            int p;
            // Rejection sampling to avoid duplicates
            outer:
            while (true) {
                p = rng.nextInt(DIM);
                for (int j = 0; j < k; j++) {
                    if (pos[j] == p) continue outer;
                }
                break;
            }
            pos[k] = p;
            int wordIdx = p >>> 6;
            int bitIdx = p & 63;
            v[wordIdx] &= ~(1L << bitIdx); // clear bit -> 0 = -1 bipolar
        }
        return v;
    }

    /**
     * Circular shift (Kanerva permutation). Positive {@code k} rotates right.
     * Negative {@code k} rotates left. Bit positions wrap around.
     *
     * <p>Implemented by indexing each output bit to its source position
     * {@code (p + k) mod DIM}. This is bit-accurate for any shift amount.
     */
    public static long[] permute(long[] v, int k) {
        if (v == null || v.length != WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        // Normalize k to [0, DIM)
        k = ((k % DIM) + DIM) % DIM;
        if (k == 0) {
            long[] cp = new long[WORDS];
            System.arraycopy(v, 0, cp, 0, WORDS);
            return cp;
        }

        long[] out = new long[WORDS];
        // output[p_out] = input[(p_out + k) mod DIM]
        for (int w = 0; w < WORDS; w++) {
            long word = 0L;
            for (int b = 0; b < 64; b++) {
                int pOut = (w << 6) | b;
                int pIn = pOut + k;
                if (pIn >= DIM) pIn -= DIM;
                int wIn = pIn >>> 6;
                int bIn = pIn & 63;
                if (((v[wIn] >>> bIn) & 1L) != 0L) {
                    word |= (1L << b);
                }
            }
            out[w] = word;
        }
        return out;
    }

    /**
     * Hamming distance between two bipolar vectors (counts positions where
     * bits differ). Uses native lib when available per-word.
     */
    public static int hamming(long[] a, long[] b) {
        if (a == null || b == null || a.length != WORDS || b.length != WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        int d = 0;
        for (int i = 0; i < WORDS; i++) {
            // Use native per-word Hamming when available; fallback to bitCount.
            try {
                d += io.matrix.imports.HammingNative.hamming(a[i], b[i]);
            } catch (Throwable t) {
                d += Long.bitCount(a[i] ^ b[i]);
            }
        }
        return d;
    }

    /**
     * Bipolar similarity: (#same bits - #diff bits) / DIM.
     * Range: [-1.0, +1.0]. +1 = identical, -1 = complement, 0 = orthogonal.
     */
    public static double similarity(long[] a, long[] b) {
        int dist = hamming(a, b);
        return 1.0 - 2.0 * dist / (double) DIM;
    }

    /**
     * Majority-vote bundle of an array of bipolar vectors. Each output bit
     * is +1 iff the majority of input bits at that position are 1 (which
     * represents +1 in our bipolar convention).
     *
     * <p>Mathematically: for each bit position i, sum the input bits as
     * {0, 1} values; if sum &gt; n/2, output 1, else 0.
     *
     * @throws IllegalArgumentException if {@code vectors} is empty or any
     *         vector has wrong length
     */
    public static long[] bundle(long[]... vectors) {
        if (vectors == null || vectors.length == 0) {
            throw new IllegalArgumentException("no vectors");
        }
        int n = vectors.length;
        long[] out = new long[WORDS];
        int[] counts = new int[DIM];

        // Sum each input bit into counts[]
        for (long[] v : vectors) {
            if (v == null || v.length != WORDS) {
                throw new IllegalArgumentException("vector wrong length");
            }
            for (int w = 0; w < WORDS; w++) {
                long bits = v[w];
                for (int b = 0; b < 64; b++) {
                    if (((bits >>> b) & 1L) != 0L) {
                        counts[(w << 6) + b]++;
                    }
                }
            }
        }
        // Majority vote
        for (int i = 0; i < DIM; i++) {
            if (counts[i] * 2 > n) {
                int w = i >>> 6;
                int b = i & 63;
                out[w] |= (1L << b);
            }
        }
        return out;
    }

    /**
     * Bitwise XOR of two bipolar vectors. In bipolar terms this is also the
     * multiplication (binding) operator (see class javadoc).
     */
    public static long[] xor(long[] a, long[] b) {
        if (a == null || b == null || a.length != WORDS || b.length != WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        long[] out = new long[WORDS];
        for (int i = 0; i < WORDS; i++) {
            out[i] = a[i] ^ b[i];
        }
        return out;
    }

    /**
     * Popcount of a single long word. Convenience for callers building custom
     * metrics.
     */
    public static int popcount(long x) {
        return Long.bitCount(x);
    }

    /**
     * Convert a bipolar vector to a String of '+' and '-' for debugging.
     * Result has {@link #DIM} characters.
     */
    public static String toSymbols(long[] v) {
        if (v == null || v.length != WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        char[] s = new char[DIM];
        for (int w = 0; w < WORDS; w++) {
            for (int b = 0; b < 64; b++) {
                s[(w << 6) + b] = ((v[w] >>> b) & 1L) != 0L ? '+' : '-';
            }
        }
        return new String(s);
    }
}
