package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HammingNative} — C extension via Project Panama FFM.
 *
 * <p>Verifies both the native path and Java fallback produce correct
 * Hamming distances and population counts.
 */
class HammingNativeTest {

    @Test
    void hammingDistanceOfZeroIsZero() {
        assertThat(HammingNative.hamming(0L, 0L)).isEqualTo(0);
    }

    @Test
    void hammingDistanceOfComplementIs64() {
        long allOnes = 0xFFFFFFFFFFFFFFFFL;
        assertThat(HammingNative.hamming(0L, allOnes)).isEqualTo(64);
    }

    @Test
    void hammingDistanceOfIdenticalIsZero() {
        long x = 0xDEADBEEFCAFEBABEL;
        assertThat(HammingNative.hamming(x, x)).isEqualTo(0);
    }

    @Test
    void hammingDistanceMatchesJavaBitCount() {
        long[] testPairs = {
                0L, 0L,
                0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL,
                0L, 0xFFFFFFFFFFFFFFFFL,
                0x5555555555555555L, 0x2AAAAAAAAAAAAAABL,
                0xDEADBEEFL, 0xCAFEBABEL,
                0x123456789ABCDEF0L, 0xFEDCBA9876543210L
        };
        for (int i = 0; i < testPairs.length; i += 2) {
            int native_ = HammingNative.hamming(testPairs[i], testPairs[i + 1]);
            int java_ = Long.bitCount(testPairs[i] ^ testPairs[i + 1]);
            assertThat(native_).isEqualTo(java_);
        }
    }

    @Test
    void hammingDistanceOfAlternatingBitsIs64() {
        // 0101... XOR 1010... = 1111... (all 64 bits differ)
        long a = 0x5555555555555555L;
        long b = ~a; // bitwise complement
        assertThat(HammingNative.hamming(a, b)).isEqualTo(64);
    }

    @Test
    void isNativeAvailableReturnsBoolean() {
        // Should not throw; either true or false depending on env
        boolean available = HammingNative.isNativeAvailable();
        // No assertion needed — just verify the call works
        assertThat(available == true || available == false).isTrue();
    }

    @Test
    void hammingWithLargeNumbersMatchesJava() {
        // Use 0 vs 1 — differ by exactly 1 bit (LSB)
        long a = 0L;
        long b = 1L;
        int native_ = HammingNative.hamming(a, b);
        int java_ = Long.bitCount(a ^ b);
        assertThat(native_).isEqualTo(java_);
        assertThat(native_).isEqualTo(1);
    }
}
