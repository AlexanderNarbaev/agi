package io.matrix.api;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MatrixResource input sanitization.
 *
 * Verifies that the sanitize() method correctly rejects malicious inputs.
 */
class MatrixResourceSanitizeTest {

    @Test
    void shouldAcceptValidString() {
        String result = MatrixResource.sanitize("hello world", "field");
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void shouldTrimWhitespace() {
        String result = MatrixResource.sanitize("  hello  ", "field");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void shouldAcceptNull() {
        assertThat(MatrixResource.sanitize(null, "field")).isNull();
    }

    @Test
    void shouldAcceptEmptyString() {
        String result = MatrixResource.sanitize("", "field");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectOverlongString() {
        String longString = "x".repeat(4097);
        assertThatThrownBy(() -> MatrixResource.sanitize(longString, "field"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void shouldAcceptMaxLengthString() {
        String maxLength = "x".repeat(4096);
        String result = MatrixResource.sanitize(maxLength, "field");
        assertThat(result).hasSize(4096);
    }

    @Test
    void shouldRejectNullBytes() {
        assertThatThrownBy(() -> MatrixResource.sanitize("hello\0world", "field"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid control characters");
    }

    @Test
    void shouldRejectControlCharacters() {
        assertThatThrownBy(() -> MatrixResource.sanitize("hello\u0001world", "field"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid control characters");
    }

    @Test
    void shouldRejectVerticalTab() {
        assertThatThrownBy(() -> MatrixResource.sanitize("hello\u000Bworld", "field"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid control characters");
    }

    @Test
    void shouldAcceptNewlines() {
        String result = MatrixResource.sanitize("hello\nworld", "field");
        assertThat(result).isEqualTo("hello\nworld");
    }

    @Test
    void shouldAcceptTabs() {
        String result = MatrixResource.sanitize("hello\tworld", "field");
        assertThat(result).isEqualTo("hello\tworld");
    }

    @Test
    void shouldAcceptCarriageReturn() {
        String result = MatrixResource.sanitize("hello\rworld", "field");
        assertThat(result).isEqualTo("hello\rworld");
    }

    @Test
    void shouldAcceptSpecialCharacters() {
        String result = MatrixResource.sanitize("test@email.com:8080/path?q=1&r=2#frag", "field");
        assertThat(result).isEqualTo("test@email.com:8080/path?q=1&r=2#frag");
    }

    @Test
    void shouldIncludeFieldNameInErrorMessage() {
        assertThatThrownBy(() -> MatrixResource.sanitize("x".repeat(4097), "myField"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("myField");
    }
}
