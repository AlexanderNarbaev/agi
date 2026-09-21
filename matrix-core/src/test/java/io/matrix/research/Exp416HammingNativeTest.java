package io.matrix.research;

import io.matrix.imports.HammingNative;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 416 — C extension via Project Panama FFM for Hamming distance.
 */
class Exp416HammingNativeTest {

    @Test
    void hammingIdenticalNumbersIsZero() {
        assertThat(HammingNative.hamming(0L, 0L)).isEqualTo(0);
        assertThat(HammingNative.hamming(0xDEADBEEFL, 0xDEADBEEFL)).isEqualTo(0);
        assertThat(HammingNative.hamming(-1L, -1L)).isEqualTo(0);
    }

    @Test
    void hammingSingleBitDifference() {
        assertThat(HammingNative.hamming(0L, 1L)).isEqualTo(1);
        assertThat(HammingNative.hamming(0L, 2L)).isEqualTo(1);
        assertThat(HammingNative.hamming(0L, 0x80000000L)).isEqualTo(1);
    }

    @Test
    void hammingMatchesBitCount() {
        // Java fallback should match native; native should match Long.bitCount
        Random rng = new Random(0xCAFE);
        for (int trial = 0; trial < 100; trial++) {
            long a = rng.nextLong();
            long b = rng.nextLong();
            int expected = Long.bitCount(a ^ b);
            int actual = HammingNative.hamming(a, b);
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    void hammingLargeRandom() {
        Random rng = new Random(0xBEEFL);
        long a = rng.nextLong();
        long b = rng.nextLong();
        int result = HammingNative.hamming(a, b);
        // Hamming distance is in [0, 64]
        assertThat(result).isBetween(0, 64);
    }
}
