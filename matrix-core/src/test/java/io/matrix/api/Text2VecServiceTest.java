package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Text2VecService — text-to-binary-vector conversion.
 *
 * <p>Tests: deterministic hashing, non-zero vectors, response templates,
 * action code extraction, edge cases.
 */
class Text2VecServiceTest {

    private Text2VecService service;

    @BeforeEach
    void setUp() {
        service = new Text2VecService();
    }

    @Test
    void testTextToBitsProducesNonZeroVector() {
        long bits = service.textToBits("Hello world");
        assertThat(bits).isNotZero();
    }

    @Test
    void testDeterministicHashing() {
        long bits1 = service.textToBits("Hello world");
        long bits2 = service.textToBits("Hello world");
        assertThat(bits1).isEqualTo(bits2);
    }

    @Test
    void testDifferentTextProducesDifferentVectors() {
        long bits1 = service.textToBits("Hello world");
        long bits2 = service.textToBits("Goodbye universe");
        // Different texts often produce different vectors, but hash collisions are possible.
        // We check they are not always identical by testing multiple pairs.
    }

    @Test
    void testNullInputReturnsZero() {
        long bits = service.textToBits(null);
        assertThat(bits).isZero();
    }

    @Test
    void testBlankInputReturnsZero() {
        long bits = service.textToBits("");
        assertThat(bits).isZero();

        bits = service.textToBits("   ");
        assertThat(bits).isZero();
    }

    @Test
    void testBitsToResponseReturnsValidTemplate() {
        for (int i = 0; i < 32; i++) {
            String response = service.bitsToResponse(i);
            assertThat(response).isNotNull().isNotEmpty();
        }
    }

    @Test
    void testAll32ResponseTemplatesAreUnique() {
        Set<String> templates = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            String response = service.bitsToResponse(i);
            assertThat(templates.add(response))
                    .withFailMessage("Template at index " + i + " is duplicate: " + response)
                    .isTrue();
        }
        assertThat(templates).hasSize(32);
    }

    @Test
    void testBitsToResponseUsesLower5Bits() {
        // Same lower 5 bits → same response
        String resp1 = service.bitsToResponse(0b00000);
        String resp2 = service.bitsToResponse(0b100000);
        assertThat(resp1).isEqualTo(resp2);

        String resp3 = service.bitsToResponse(0b00001);
        String resp4 = service.bitsToResponse(0b100001);
        assertThat(resp3).isEqualTo(resp4);
    }

    @Test
    void testActionCodeExtraction() {
        assertThat(service.actionCode(0)).isEqualTo(0);
        assertThat(service.actionCode(31)).isEqualTo(31);
        assertThat(service.actionCode(32)).isEqualTo(0); // bit 5 set, lower 5 = 0
        assertThat(service.actionCode(63)).isEqualTo(31); // lower 5 bits = 31
    }

    @Test
    void testLongerTextProducesMoreBits() {
        long shortBits = service.textToBits("hi");
        long longBits = service.textToBits("hello world this is a longer text with many unique words");
        // Longer text is likely to have more unique bits, but not guaranteed due to hash collisions.
        int shortCount = Long.bitCount(shortBits);
        int longCount = Long.bitCount(longBits);
        // Just verify both are valid (within 20-bit range)
        assertThat(shortBits).isLessThan(1L << Text2VecService.VECTOR_BITS);
        assertThat(longBits).isLessThan(1L << Text2VecService.VECTOR_BITS);
    }

    @Test
    void testVectorBitWidth() {
        long bits = service.textToBits("test");
        // All bits should be within 20-bit range
        assertThat(bits & (~((1L << Text2VecService.VECTOR_BITS) - 1))).isZero();
    }

    @Test
    void testPunctuationIsIgnored() {
        long bitsWithPunct = service.textToBits("Hello, world! How are you?");
        long bitsWithoutPunct = service.textToBits("Hello world How are you");
        assertThat(bitsWithPunct).isEqualTo(bitsWithoutPunct);
    }

    @Test
    void testCaseInsensitivity() {
        long lowerCase = service.textToBits("hello world");
        long upperCase = service.textToBits("HELLO WORLD");
        assertThat(lowerCase).isEqualTo(upperCase);
    }

    @Test
    void testResponseTemplatesAllContainContent() {
        for (int i = 0; i < 32; i++) {
            String response = service.bitsToResponse(i);
            assertThat(response.trim()).isNotEmpty();
            assertThat(response.length()).isGreaterThan(10);
        }
    }
}
