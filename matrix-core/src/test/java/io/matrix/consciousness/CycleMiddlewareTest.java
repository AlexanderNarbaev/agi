package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 287 — CycleMiddleware unit tests. */
class CycleMiddlewareTest {

    @Test
    void emptyMiddlewarePassesThrough() {
        var m = new CycleMiddleware();
        assertThat(m.process("hello")).isEqualTo("hello");
    }

    @Test
    void middlewareTransforms() {
        var m = new CycleMiddleware().add(String::toLowerCase);
        assertThat(m.process("HELLO")).isEqualTo("hello");
    }

    @Test
    void middlewareBlocks() {
        var m = new CycleMiddleware().add(s -> s.startsWith("bad") ? null : s);
        assertThat(m.process("good")).isEqualTo("good");
        assertThat(m.process("bad input")).isNull();
    }

    @Test
    void chainOrder() {
        var m = new CycleMiddleware()
                .add(s -> s + "!")
                .add(String::toUpperCase);
        assertThat(m.process("hello")).isEqualTo("HELLO!");
    }

    @Test
    void standardMiddleware() {
        var m = CycleMiddleware.standard();
        assertThat(m.process("  hello   world  ")).isEqualTo("hello world");
        assertThat(m.process("")).isNull();
        assertThat(m.process(null)).isNull();
    }

    @Test
    void sizeReflectsChain() {
        var m = new CycleMiddleware().add(s -> s).add(s -> s);
        assertThat(m.size()).isEqualTo(2);
    }
}
