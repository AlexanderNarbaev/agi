package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 278 — CycleSplitter unit tests. */
class CycleSplitterTest {

    @Test
    void emptyInput() {
        var s = CycleSplitter.split("");
        assertThat(s.chunks()).isEmpty();
    }

    @Test
    void nullInput() {
        var s = CycleSplitter.split(null);
        assertThat(s.chunks()).isEmpty();
    }

    @Test
    void shortInputSingleChunk() {
        var s = CycleSplitter.split("hello", 100);
        assertThat(s.chunks()).containsExactly("hello");
    }

    @Test
    void longInputSplit() {
        var s = CycleSplitter.split("abcdefghijklmnop", 5);
        assertThat(s.chunks()).containsExactly("abcde", "fghij", "klmno", "p");
        assertThat(s.originalLength()).isEqualTo(16);
    }

    @Test
    void exactSizeOneChunk() {
        var s = CycleSplitter.split("abcde", 5);
        assertThat(s.chunks()).containsExactly("abcde");
    }
}
