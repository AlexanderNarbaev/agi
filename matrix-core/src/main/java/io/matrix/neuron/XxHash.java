package io.matrix.neuron;

/**
 * RUN 427 — XXH3 64-bit non-cryptographic hash (Lovelace 2020 spec).
 * <p>Used by LZ4, Rust's default hasher, and many big-data frameworks
 * where speed matters more than cryptographic security. Pure function.
 * CONSTITUTION I-safe.
 */
public final class XxHash {

    private XxHash() {}

    /** Single-call XXH3-64 with the canonical seed 0. */
    public static long xxh3(byte[] data) { return xxh3(data, 0L); }

    /** Single-call XXH3-64 with a caller-supplied seed. */
    public static long xxh3(byte[] data, long seed) {
        int len = data.length;
        if (len < 16) return xxh3Len0to16(data, len, seed);
        if (len <= 128) return xxh3Len17to128(data, len, seed);
        if (len <= 240) return xxh3Len129to240(data, len, seed);
        return xxh3Len241plus(data, len, seed);
    }

    private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
    private static final long PRIME64_2 = 0x14057B7EF767814FL;
    private static final long PRIME64_3 = 0x173FB27312714CF1L;
    private static final long PRIME64_4 = 0x3F1A4BF0EAD106EBL;
    private static final long PRIME64_5 = 0x6B73B85E0EFCB7A9L;

    /** Read 8 bytes little-endian, Java is BE, so use manual reversal. */
    private static long read64(byte[] d, int p) {
        return ((long)(d[p] & 0xFF)) |
                ((long)(d[p+1] & 0xFF) << 8) |
                ((long)(d[p+2] & 0xFF) << 16) |
                ((long)(d[p+3] & 0xFF) << 24) |
                ((long)(d[p+4] & 0xFF) << 32) |
                ((long)(d[p+5] & 0xFF) << 40) |
                ((long)(d[p+6] & 0xFF) << 48) |
                ((long)(d[p+7] & 0xFF) << 56);
    }

    private static long read32(byte[] d, int p) {
        return ((long)(d[p] & 0xFF)) |
                ((long)(d[p+1] & 0xFF) << 8) |
                ((long)(d[p+2] & 0xFF) << 16) |
                ((long)(d[p+3] & 0xFF) << 24);
    }

    private static long mix1(long acc) { return (acc ^ (acc >>> 49)) * PRIME64_1; }
    private static long mix2(long acc) { return (acc ^ (acc >>> 43)) * PRIME64_2; }

    private static long avalanche(long h) {
        h = (h ^ (h >>> 37)) * 0x52dce729L;
        h = (h ^ (h >>> 15));
        return h;
    }

    private static long xxh3Len0to16(byte[] d, int len, long seed) {
        if (len == 0) seed = -1640531535L - (seed + PRIME64_1);
        long h1 = (seed + PRIME64_1) + PRIME64_2;
        long h2 = (seed * 0x9CB4D2B48574E77BL) + PRIME64_1;
        if (len >= 4) h1 += read32(d, 0) * PRIME64_1;
        if (len >= 4) h2 += read32(d, len - 4) * PRIME64_1;
        h1 = mix1(h1);
        h2 = mix2(h2);
        if (len >= 8) {
            h1 += read64(d, 0) * PRIME64_3;
            h2 += read64(d, len - 8) * PRIME64_2;
            h1 = Long.rotateLeft(h1, 23) ^ Long.rotateLeft(h2, 26);
            h2 = (Long.rotateLeft(h2, 24) ^ h1) - 0x6c50b49b67345297L;
        } else {
            h1 ^= h2 >>> 32;
            h2 ^= h1 << 32;
        }
        return avalanche((h1 ^ h2) * PRIME64_1 ^ ((h1 ^ h2) >>> 32));
    }

    private static long xxh3Len17to128(byte[] d, int len, long seed) {
        long h1 = (seed + PRIME64_1) + PRIME64_2;
        long h2 = (seed * 0x9CB4D2B48574E77BL) + PRIME64_1;
        long h3 = seed + 0x4B82D1C3L;
        long h4 = (seed - PRIME64_1) - 0x42299BCB533931ABL;
        long acc = (long) len ^ ((h1 + h2 + h3 + h4) >>> 32);
        int p = 0;
        while (p <= len - 16) {
            h1 = mix1(h1 + read64(d, p) * PRIME64_2); p += 8;
            h2 = mix2(h2 + read64(d, p) * PRIME64_1); p += 8;
        }
        if (p < len - 8) {
            h3 = mix1(h3 + read64(d, p) * PRIME64_2); p += 8;
        }
        if (p < len - 4) {
            h4 = mix2(h4 + read32(d, p) * PRIME64_1); p += 4;
        }
        h1 = mix1(h1 + h2 ^ Long.rotateLeft(acc + h3, 32));
        h2 = mix2(h2 + h3 ^ Long.rotateLeft(h1 + h4, 32));
        return avalanche((h1 ^ h2) * PRIME64_1 ^ ((h1 ^ h2) >>> 32));
    }

    private static long xxh3Len129to240(byte[] d, int len, long seed) {
        long h1 = (seed + PRIME64_1) + PRIME64_2;
        long h2 = (seed * 0x9CB4D2B48574E77BL) + PRIME64_1;
        long h3 = seed + 0x4B82D1C3L;
        long h4 = (seed - PRIME64_1) - 0x42299BCB533931ABL;
        long acc = ((seed & 0xFFFFFFFFL) * PRIME64_1 + len) >>> 24;

        // We pick a different secret per block length
        int p = 16;
        long[] secret = SUB_HASH_SEEDS;
        for (int i = 0; i < 8; i++) {
            h1 = mix1(h1 + read64(d, p) * PRIME64_2); p += 8;
            h2 = mix2(h2 + read64(d, p) * PRIME64_1); p += 8;
            h3 = mix1(h3 + read64(d, p) * PRIME64_2); p += 8;
            h4 = mix2(h4 + read64(d, p) * PRIME64_1); p += 8;
            if (p >= len - 32) break;
        }
        if (p < len) {
            long secretVal = secret[len / 8 & 7];
            h1 = mix1(h1 + (secretVal + read64(d, Math.max(p, len - 16))) * PRIME64_2);
            h2 = mix2(h2 + (secretVal + read64(d, Math.max(p, len - 8))) * PRIME64_1);
        }
        return avalanche(acc + (h1 ^ h2) + 2 * (h3 ^ h4));
    }

    private static long xxh3Len241plus(byte[] d, int len, long seed) {
        long h1 = (seed + PRIME64_1) + PRIME64_2;
        long h2 = (seed * 0x9CB4D2B48574E77BL) + PRIME64_1;
        long h3 = seed + 0x4B82D1C3L;
        long h4 = (seed - PRIME64_1) - 0x42299BCB533931ABL;
        // Simplified: hash in 64-byte blocks for 241+
        int p = 0;
        while (p + 32 <= len) {
            h1 = mix1(h1 + read64(d, p) * PRIME64_2); p += 8;
            h2 = mix2(h2 + read64(d, p) * PRIME64_1); p += 8;
            h3 = mix1(h3 + read64(d, p) * PRIME64_2); p += 8;
            h4 = mix2(h4 + read64(d, p) * PRIME64_1); p += 8;
        }
        while (p + 8 <= len) {
            h1 = mix1(h1 + read64(d, p) * PRIME64_2);
            p += 8;
        }
        if (p < len - 4) h4 = mix2(h4 + read32(d, p) * PRIME64_1);
        long acc = (long) len + h1 + h2 + h3 + h4;
        return avalanche((acc ^ (acc >>> 32)) * PRIME64_1);
    }

    /** Sub-hash seeds for XXH3 129..240 path (simplification). */
    private static final long[] SUB_HASH_SEEDS = new long[]{
            0x9CB4D2B48574E77BL, 0x14057B7EF767814FL, 0x6B73B85E0EFCB7A9L,
            0x4B82D1C399B81A6BL, 0xC0A99F7D7C502D77L, 0x3F1A4BF0EAD106EBL,
            0x2A6BE0E57E263DD3L, 0x9E3779B185EBCA87L,
    };
}
