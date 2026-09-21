package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 279 — CycleAssembler unit tests. */
class CycleAssemblerTest {

    @Test
    void emptyChunks() {
        assertThat(CycleAssembler.assemble(List.of())).isEmpty();
        assertThat(CycleAssembler.assemble(null)).isEmpty();
    }

    @Test
    void singleChunk() {
        assertThat(CycleAssembler.assemble(List.of("hello"))).isEqualTo("hello");
    }

    @Test
    void multipleChunksNoSep() {
        assertThat(CycleAssembler.assemble(List.of("a", "b", "c"))).isEqualTo("abc");
    }

    @Test
    void withSeparator() {
        assertThat(CycleAssembler.assemble(List.of("a", "b", "c"), "-")).isEqualTo("a-b-c");
    }
}
