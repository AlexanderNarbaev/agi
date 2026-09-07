package io.matrix.perception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 150 — TextEncoder unit tests. */
class TextEncoderTest {

    @Test
    void encodeProducesCorrectLength() {
        TextEncoder enc = new TextEncoder(256);
        boolean[] bits = enc.encode("hello");
        assertThat(bits).hasSize(256);
    }

    @Test
    void encodeIsDeterministic() {
        TextEncoder enc = new TextEncoder();
        boolean[] a = enc.encode("the quick brown fox");
        boolean[] b = enc.encode("the quick brown fox");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentTextProducesDifferentBits() {
        TextEncoder enc = new TextEncoder();
        boolean[] a = enc.encode("cat");
        boolean[] b = enc.encode("dog");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void encoderCaresAboutBitLength() {
        boolean[] shortBits = new TextEncoder(64).encode("x");
        boolean[] longerBits = new TextEncoder(256).encode("x");
        assertThat(shortBits).hasSize(64);
        assertThat(longerBits).hasSize(256);
        // First 64 bits should match (same hash, same prefix)
        for (int i = 0; i < 64; i++) {
            assertThat(shortBits[i]).isEqualTo(longerBits[i]);
        }
    }

    @Test
    void nullTextProducesEmptyBits() {
        TextEncoder enc = new TextEncoder();
        boolean[] bits = enc.encode(null);
        assertThat(bits).hasSize(256);
        // All bits are determined by sha256("")
    }

    @Test
    void emptyStringProducesNonZeroBits() {
        TextEncoder enc = new TextEncoder();
        boolean[] bits = enc.encode("");
        boolean[] expected = enc.encode("");
        assertThat(bits).isEqualTo(expected);
        // At least one bit should be true (sha256 of empty has bits)
        int ones = 0;
        for (boolean b : bits) if (b) ones++;
        assertThat(ones).isGreaterThan(0);
    }

    @Test
    void rejectsZeroBitLength() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TextEncoder(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveBitLength() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TextEncoder(99999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intsToBooleansHelper() {
        boolean[] out = TextEncoder.intsToBooleans(new int[]{0, 1, 0, 1});
        assertThat(out).containsExactly(false, true, false, true);
    }

    @Test
    void bitLengthAccessor() {
        TextEncoder enc = new TextEncoder(128);
        assertThat(enc.bitLength()).isEqualTo(128);
    }
}
