package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave M tests: SandboxResource bit/text round-trip helpers and the
 * bitsToText edge-case handling. The interactive HTTP surface
 * (CDI-bound resource) is exercised in Quarkus integration tests;
 * here we cover the pure-function helpers via reflection.
 */
class SandboxResourceTest {

    @Test
    void textToBitsIsFixedLength() {
        boolean[] bits = invokeTextToBits("hello", 896);
        assertThat(bits).hasSize(896);
    }

    @Test
    void textToBitsEmptyStringIsAllZeros() {
        boolean[] bits = invokeTextToBits("", 100);
        for (boolean b : bits) assertThat(b).isFalse();
    }

    @Test
    void textToBitsNullIsAllZeros() {
        boolean[] bits = invokeTextToBits(null, 50);
        for (boolean b : bits) assertThat(b).isFalse();
    }

    @Test
    void textToBitsIsDeterministic() {
        // same input → same bits
        boolean[] a = invokeTextToBits("hello world", 256);
        boolean[] b = invokeTextToBits("hello world", 256);
        for (int i = 0; i < a.length; i++) assertThat(a[i]).isEqualTo(b[i]);
    }

    @Test
    void bitsToTextOnEmptyArray() {
        assertThat(invokeBitsToText(new boolean[0]))
                .isEqualTo("(empty)");
    }

    @Test
    void bitsToTextOnAllZerosIsInformative() {
        boolean[] zeros = new boolean[64];
        String s = invokeBitsToText(zeros);
        assertThat(s).contains("[MATRIX");
        assertThat(s).contains("zero-density");
        assertThat(s).contains("64 bits");
    }

    @Test
    void bitsToTextOnMixedShowsDensityAndRange() {
        // set bits at positions 0, 32, 63
        boolean[] mixed = new boolean[64];
        mixed[0] = true;
        mixed[32] = true;
        mixed[63] = true;
        String s = invokeBitsToText(mixed);
        assertThat(s).contains("3 bits set");
        assertThat(s).contains("density");
        assertThat(s).contains("spanning bits 0..63");
    }

    @Test
    void bitsToTextOnSingleBitShowsExactBit() {
        boolean[] single = new boolean[64];
        single[42] = true;
        String s = invokeBitsToText(single);
        assertThat(s).contains("at bit 42");
    }

    private static boolean[] invokeTextToBits(String text, int width) {
        try {
            var m = SandboxResource.class.getDeclaredMethod(
                    "textToBits", String.class, int.class);
            m.setAccessible(true);
            return (boolean[]) m.invoke(null, text, width);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String invokeBitsToText(boolean[] bits) {
        try {
            var m = SandboxResource.class.getDeclaredMethod("bitsToText", boolean[].class);
            m.setAccessible(true);
            return (String) m.invoke(null, (Object) bits);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}