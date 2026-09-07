package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 281 — CycleTransformer unit tests. */
class CycleTransformerTest {

    @Test
    void identityTransform() {
        var t = CycleTransformer.identity();
        assertThat(t.apply("hello")).isEqualTo("hello");
    }

    @Test
    void lowercaseTransform() {
        var t = CycleTransformer.lowercase();
        assertThat(t.apply("Hello WORLD")).isEqualTo("hello world");
    }

    @Test
    void stripTransform() {
        var t = CycleTransformer.strip();
        assertThat(t.apply("  hello  ")).isEqualTo("hello");
    }

    @Test
    void maxLengthTransform() {
        var t = CycleTransformer.maxLength(5);
        assertThat(t.apply("hello world")).isEqualTo("hello");
        assertThat(t.apply("hi")).isEqualTo("hi");
    }

    @Test
    void nullInputReturnsNull() {
        var t = CycleTransformer.identity();
        assertThat(t.apply(null)).isNull();
    }
}
