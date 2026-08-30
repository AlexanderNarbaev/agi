package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExpandedTextToBitsService}: deterministic, fixed
 * length, positive-density outputs for non-empty input.
 */
class ExpandedTextToBitsServiceTest {

    @Test
    void producesFixedLengthOutput() {
        boolean[] bits = new ExpandedTextToBitsService().textToBits("hello");
        assertThat(bits).hasSize(ExpandedTextToBitsService.VECTOR_BITS);
    }

    @Test
    void emptyInputIsAllZeros() {
        boolean[] bits = new ExpandedTextToBitsService().textToBits("");
        for (boolean b : bits) assertThat(b).isFalse();
    }

    @Test
    void nullInputIsAllZeros() {
        boolean[] bits = new ExpandedTextToBitsService().textToBits(null);
        for (boolean b : bits) assertThat(b).isFalse();
    }

    @Test
    void nonEmptyInputSetsSomeBits() {
        boolean[] bits = new ExpandedTextToBitsService().textToBits("hello world");
        int set = 0;
        for (boolean b : bits) if (b) set++;
        assertThat(set).as("non-empty input should set at least one bit").isPositive();
    }

    @Test
    void deterministicSameInputSameOutput() {
        ExpandedTextToBitsService s = new ExpandedTextToBitsService();
        boolean[] a = s.textToBits("the quick brown fox");
        boolean[] b = s.textToBits("the quick brown fox");
        for (int i = 0; i < a.length; i++) assertThat(a[i]).isEqualTo(b[i]);
    }

    @Test
    void differentInputsProduceDifferentBits() {
        ExpandedTextToBitsService s = new ExpandedTextToBitsService();
        boolean[] a = s.textToBits("alpha");
        boolean[] b = s.textToBits("omega");
        // at least one differing bit
        boolean differ = false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) { differ = true; break; }
        assertThat(differ).as("different inputs should produce different bits").isTrue();
    }

    @Test
    void longFormAlsoSetsBits() {
        boolean[] bits = new ExpandedTextToBitsService()
                .textToBits("Explain quantum computing in simple terms to a beginner");
        int set = 0;
        for (boolean b : bits) if (b) set++;
        assertThat(set).isPositive();
    }

    @Test
    void longFormMatchesVectorWidth() {
        boolean[] bits = new ExpandedTextToBitsService()
                .textToBits("a much longer paragraph that exceeds typical input lengths and should still produce an output matching the fixed VECTOR_BITS constant without any truncation");
        assertThat(bits).hasSize(896);
    }

    @Test
    void textToBitsLongReturnsConsistentValue() {
        // long form of the API returns a 64-bit summary
        long bits = new ExpandedTextToBitsService().textToBitsLong("hello");
        boolean[] full = new ExpandedTextToBitsService().textToBits("hello");
        // reconstruct what the long should contain
        long expected = 0L;
        for (int i = 0; i < Math.min(64, full.length); i++) {
            if (full[i]) expected |= (1L << i);
        }
        assertThat(bits).isEqualTo(expected);
    }
}