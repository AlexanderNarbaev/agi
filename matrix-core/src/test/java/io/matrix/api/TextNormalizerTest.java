package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 140 — TextNormalizer unit tests. */
class TextNormalizerTest {

    @Test
    void normalizeBasic() {
        assertThat(TextNormalizer.normalize("  hello  ")).isEqualTo("hello");
        assertThat(TextNormalizer.normalize("hello   world")).isEqualTo("hello world");
    }

    @Test
    void normalizeNewlines() {
        assertThat(TextNormalizer.normalize("line1\n\nline2")).isEqualTo("line1 line2");
        assertThat(TextNormalizer.normalize("a\t\tb")).isEqualTo("a b");
    }

    @Test
    void normalizeStripsControlChars() {
        assertThat(TextNormalizer.normalize("hello\u0001world")).isEqualTo("helloworld");
    }

    @Test
    void normalizeNullReturnsEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void trimSimple() {
        assertThat(TextNormalizer.trim("  hello  ")).isEqualTo("hello");
        assertThat(TextNormalizer.trim(null)).isEmpty();
    }

    @Test
    void collapseSpaces() {
        assertThat(TextNormalizer.collapseSpaces("a   b   c")).isEqualTo("a b c");
        assertThat(TextNormalizer.collapseSpaces("\n\nhi\n\n")).isEqualTo("hi");
    }

    @Test
    void stripControl() {
        assertThat(TextNormalizer.stripControl("a\u0000b\u0001c")).isEqualTo("abc");
        assertThat(TextNormalizer.stripControl(null)).isEmpty();
    }

    @Test
    void normalizePreservesLetters() {
        assertThat(TextNormalizer.normalize("Python is great")).isEqualTo("Python is great");
    }
}
