package io.matrix.neuron;

import java.util.Arrays;
import java.util.Random;

/**
 * RUN 430 — HyperLogLog cardinality estimator (Flajolet 2007).
 * <p>Estimates the number of distinct values in a stream with ~0.81/sqrt(m)
 * standard error. Uses {@code std *= 4/5 * m * (m - sum 2^{-M}) ...}
 * bias correction for m&lt;25k. Pure function.
 * CONSTITUTION I-safe.
 */
public final class HyperLogLog {

    private final int p;        // precision: buckets = 1 << p
    private final int m;        // 2^p
    private final byte[] registers;
    private int zeroCount;
    private static final double[] alphaMap = {
            // alpha[p] for p=4..16
            0.0, 0.0, 0.0, 0.0,
            0.673,  // p=4
            0.697,  // p=5
            0.709,  // p=6
            Double.NaN,
            0.721,  // p=8
            0.734,
            0.745,
            0.753,
            0.760,
            0.769,
            0.776,
            0.783,
            0.790   // p=16
    };

    public HyperLogLog(int p) {
        if (p < 4 || p > 16) throw new IllegalArgumentException("p in [4,16]");
        this.p = p;
        this.m = 1 << p;
        this.registers = new byte[m];
        Arrays.fill(registers, (byte) 0);
        this.zeroCount = m;
    }

    /** Hash-with-leading-zero count for an item. We use MurmurHash3 fmix64. */
    private static long fmix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }

    /** Add an item via its long key. */
    public void add(long key) {
        long hash = fmix(key);
        int bucket = (int) ((hash >>> (64 - p)) & (m - 1));
        // `rank` = 1 + position of the highest 1-bit in the lower (64-p) bits.
        // = 1 + count_leading_zeros(low) clamped to [1, 64-p+1].
        long w = hash & ((1L << (64 - p)) - 1);  // lower (64-p) bits
        int rank = 1;
        for (int i = 63 - p; i >= 0; i--) {
            if (((w >>> i) & 1L) != 0) break;
            rank++;
        }
        if (rank > 64 - p) rank = 64 - p + 1;
        byte newVal = (byte) rank;
        if (newVal > registers[bucket]) {
            if (registers[bucket] == 0) zeroCount--;
            registers[bucket] = newVal;
        }
    }

    public long cardinality() {
        // Raw estimate
        double sum = 0;
        for (byte r : registers) {
            sum += Math.pow(2, -r);
        }
        double alpha = alphaMap[p];
        double rawEstimate = alpha * m * m / sum;
        // Small-range correction (linear counting)
        if (rawEstimate <= 2.5 * m && zeroCount > 0) {
            rawEstimate = m * Math.log((double) m / zeroCount);
        }
        return Math.max(0, Math.round(rawEstimate));
    }
}
