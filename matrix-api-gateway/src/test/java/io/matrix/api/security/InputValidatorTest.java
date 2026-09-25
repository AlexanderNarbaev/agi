package io.matrix.api.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    @Test
    void testValidText() {
        assertEquals("hello world", InputValidator.sanitizeText("hello world"));
    }

    @Test
    void testNullText() {
        assertThrows(SecurityException.class, () -> InputValidator.sanitizeText(null));
    }

    @Test
    void testTooLongText() {
        String huge = "a".repeat(InputValidator.MAX_INPUT_BYTES + 1);
        assertThrows(SecurityException.class, () -> InputValidator.sanitizeText(huge));
    }

    @Test
    void testNullByteInjection() {
        assertThrows(SecurityException.class,
            () -> InputValidator.sanitizeText("hello\u0000world"));
    }

    @Test
    void testControlCharInjection() {
        assertThrows(SecurityException.class,
            () -> InputValidator.sanitizeText("hello\u0007world"));
    }

    @Test
    void testValidId() {
        assertEquals("expl_abc123def", InputValidator.sanitizeId("expl_abc123def"));
    }

    @Test
    void testShortId() {
        assertThrows(SecurityException.class, () -> InputValidator.sanitizeId("short"));
    }

    @Test
    void testLongId() {
        assertThrows(SecurityException.class,
            () -> InputValidator.sanitizeId("a".repeat(65)));
    }

    @Test
    void testIdWithForbiddenChars() {
        assertThrows(SecurityException.class,
            () -> InputValidator.sanitizeId("expl_abc/def"));
    }

    @Test
    void testContentTypeDefaultsToText() {
        assertEquals("text", InputValidator.sanitizeContentType(null));
        assertEquals("text", InputValidator.sanitizeContentType(""));
    }

    @Test
    void testValidContentTypes() {
        assertEquals("text", InputValidator.sanitizeContentType("TEXT"));
        assertEquals("audio", InputValidator.sanitizeContentType("audio"));
        assertEquals("image", InputValidator.sanitizeContentType("Image"));
    }

    @Test
    void testInvalidContentType() {
        assertThrows(SecurityException.class,
            () -> InputValidator.sanitizeContentType("video"));
    }
}
