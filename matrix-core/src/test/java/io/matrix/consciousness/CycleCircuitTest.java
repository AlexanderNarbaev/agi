package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 306 — CycleCircuit unit tests. */
class CycleCircuitTest {

    @Test
    void roundRobinCycles() {
        var c = new CycleCircuit(List.of("a", "b", "c"));
        assertThat(c.next()).isEqualTo("a");
        assertThat(c.next()).isEqualTo("b");
        assertThat(c.next()).isEqualTo("c");
        assertThat(c.next()).isEqualTo("a"); // wraps
    }

    @Test
    void emptyCircuitReturnsNull() {
        var c = new CycleCircuit(List.of());
        assertThat(c.next()).isNull();
    }

    @Test
    void sizeAndIndex() {
        var c = new CycleCircuit(List.of("x", "y"));
        assertThat(c.size()).isEqualTo(2);
        assertThat(c.currentIndex()).isZero();
        c.next();
        assertThat(c.currentIndex()).isEqualTo(1);
    }
}
