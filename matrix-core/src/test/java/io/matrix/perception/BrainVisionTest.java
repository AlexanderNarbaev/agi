package io.matrix.perception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 206 — BrainVision unit tests. */
class BrainVisionTest {

    @Test
    void encodeProduces256Bits() {
        var vision = new BrainVision();
        boolean[] bits = vision.encode(new byte[]{1, 2, 3});
        assertThat(bits).hasSize(256);
    }

    @Test
    void deterministicForSameInput() {
        var vision = new BrainVision();
        byte[] pixels = {10, 20, 30, 40};
        boolean[] a = vision.encode(pixels);
        boolean[] b = vision.encode(pixels);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentInputDifferentBits() {
        var vision = new BrainVision();
        boolean[] a = vision.encode(new byte[]{1});
        boolean[] b = vision.encode(new byte[]{2});
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void emptyInputProduces256Bits() {
        var vision = new BrainVision();
        boolean[] bits = vision.encode(new byte[0]);
        assertThat(bits).hasSize(256);
    }

    @Test
    void randomEncodeHasCorrectLength() {
        var vision = new BrainVision();
        boolean[] bits = vision.randomEncode(42L, 128);
        assertThat(bits).hasSize(128);
    }

    @Test
    void countOnesWorks() {
        var vision = new BrainVision();
        int[] counts = vision.countOnes(
                new boolean[]{true, false, true, true});
        assertThat(counts[0]).isEqualTo(3);  // ones
        assertThat(counts[1]).isEqualTo(1);  // zeros
    }

    @Test
    void nullInputHandledGracefully() {
        var vision = new BrainVision();
        boolean[] bits = vision.encode(null);
        assertThat(bits).hasSize(256);
    }
}
