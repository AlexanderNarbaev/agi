package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 283 — CycleNormalizer unit tests. */
class CycleNormalizerTest {

    @Test
    void nullReturnsEmpty() {
        assertThat(CycleNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void trimsWhitespace() {
        assertThat(CycleNormalizer.normalize("  hello  ")).isEqualTo("hello");
    }

    @Test
    void collapsesSpaces() {
        assertThat(CycleNormalizer.normalize("hello   world")).isEqualTo("hello world");
    }

    @Test
    void removesControlChars() {
        assertThat(CycleNormalizer.normalize("hello\u0001world")).isEqualTo("helloworld");
    }

    @Test
    void normalizeLowerLowercases() {
        assertThat(CycleNormalizer.normalizeLower("Hello WORLD")).isEqualTo("hello world");
    }

    @Test
    void normalizeTruncateShortens() {
        assertThat(CycleNormalizer.normalizeTruncate("hello world", 5)).isEqualTo("hello");
        assertThat(CycleNormalizer.normalizeTruncate("hi", 5)).isEqualTo("hi");
    }
}
