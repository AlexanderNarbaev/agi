package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 293 — CycleInputBuffer unit tests. */
class CycleInputBufferTest {

    @Test
    void emptyBuffer() {
        var b = new CycleInputBuffer(3);
        assertThat(b.size()).isZero();
        assertThat(b.ready()).isFalse();
    }

    @Test
    void addUntilReady() {
        var b = new CycleInputBuffer(2);
        b.add("a");
        assertThat(b.ready()).isFalse();
        b.add("b");
        assertThat(b.ready()).isTrue();
    }

    @Test
    void drainReturnsBatch() {
        var b = new CycleInputBuffer(2);
        b.add("a");
        b.add("b");
        b.add("c");
        var batch = b.drain();
        assertThat(batch).containsExactly("a", "b");
        assertThat(b.size()).isEqualTo(1);
    }

    @Test
    void drainPartial() {
        var b = new CycleInputBuffer(5);
        b.add("a");
        b.add("b");
        var batch = b.drain();
        assertThat(batch).containsExactly("a", "b");
    }

    @Test
    void clearResets() {
        var b = new CycleInputBuffer(3);
        b.add("a");
        b.clear();
        assertThat(b.size()).isZero();
    }
}
