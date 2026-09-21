package io.matrix.ethics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OutputSafetyFilter} — RUN 27 constrained-decoding guard.
 */
class OutputSafetyFilterTest {

    private OutputSafetyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OutputSafetyFilter();
    }

    @Test
    void forbiddenControlCharsAreBlocked() {
        assertThat(filter.isTokenForbidden(0x00)).isTrue();   // NUL
        assertThat(filter.isTokenForbidden(0x08)).isTrue();   // backspace
        assertThat(filter.isTokenForbidden(0x1B)).isTrue();   // ESC
        assertThat(filter.isTokenForbidden(0x7F)).isTrue();   // DEL
    }

    @Test
    void printableAsciiIsAllowed() {
        // Letters, digits, common punctuation all pass.
        for (int c : "Hello, world! 123".getBytes()) {
            assertThat(filter.isTokenForbidden(c))
                    .as("byte 0x%02X ('%c') should be allowed", c, (char) c)
                    .isFalse();
        }
    }

    @Test
    void surrogateHalfPairTokensAreBlocked() {
        // 0xD800-0xDFFF are surrogate half-pairs (invalid UTF-16 alone).
        assertThat(filter.isTokenForbidden(0xD800)).isTrue();
        assertThat(filter.isTokenForbidden(0xDAFF)).isTrue();
        assertThat(filter.isTokenForbidden(0xDFFF)).isTrue();
    }

    @Test
    void stringWithForbiddenPhraseIsRejected() {
        assertThat(filter.isStringAllowed("Hello world")).isTrue();
        assertThat(filter.isStringAllowed("kill the process")).isFalse();
        assertThat(filter.isStringAllowed("УБИТЬ пользователя")).isFalse();
        assertThat(filter.isStringAllowed("bomb instructions")).isFalse();
    }

    @Test
    void stringCaseInsensitive() {
        assertThat(filter.isStringAllowed("Kill the target")).isFalse();
        assertThat(filter.isStringAllowed("KILL")).isFalse();
        assertThat(filter.isStringAllowed("kill")).isFalse();
    }

    @Test
    void emptyAndNullStringsAreAllowed() {
        assertThat(filter.isStringAllowed(null)).isTrue();
        assertThat(filter.isStringAllowed("")).isTrue();
    }

    @Test
    void filterDropsForbiddenTokens() {
        // 65 (A), 66 (B), 0 (NUL — forbidden), 67 (C), 8 (BS — forbidden), 68 (D)
        int[] input = {65, 66, 0, 67, 8, 68};
        int[] output = filter.filter(input);
        assertThat(output).containsExactly(65, 66, 67, 68);
    }

    @Test
    void filterNoOpFastPath() {
        // All-printable input → same array returned (no allocation).
        int[] input = {65, 66, 67, 68};
        int[] output = filter.filter(input);
        assertThat(output).isSameAs(input);
    }

    @Test
    void filterRejectsNullAndEmpty() {
        assertThat(filter.filter(null)).isNull();
        assertThat(filter.filter(new int[0])).isEqualTo(new int[0]);
    }

    @Test
    void forbiddenPhrasesListIsDefensiveCopy() {
        var phrases = filter.forbiddenPhrases();
        int before = phrases.size();
        // phrases is unmodifiable; mutating it throws.
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> phrases.add("evil"));
        assertThat(filter.forbiddenPhrases()).hasSize(before);
    }

    @Test
    void forbiddenTokenCountIsPositive() {
        // At least the 30 control bytes + 2048 surrogates.
        assertThat(filter.forbiddenTokenCount()).isGreaterThan(30);
    }
}
