package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 277 — CycleCombiner unit tests. */
class CycleCombinerTest {

    @Test
    void emptyInputs() {
        var r = CycleCombiner.combine(List.of());
        assertThat(r.combined()).isEmpty();
        assertThat(r.originals()).isEmpty();
    }

    @Test
    void singleInput() {
        var r = CycleCombiner.combine(List.of("hello"));
        assertThat(r.combined()).isEqualTo("hello");
    }

    @Test
    void multipleInputsSpace() {
        var r = CycleCombiner.combine(List.of("hello", "world"));
        assertThat(r.combined()).isEqualTo("hello world");
    }

    @Test
    void customSeparator() {
        var r = CycleCombiner.combine(List.of("a", "b", "c"), "-");
        assertThat(r.combined()).isEqualTo("a-b-c");
    }

    @Test
    void originalsPreserved() {
        var r = CycleCombiner.combine(List.of("a", "b"));
        assertThat(r.originals()).containsExactly("a", "b");
    }
}
