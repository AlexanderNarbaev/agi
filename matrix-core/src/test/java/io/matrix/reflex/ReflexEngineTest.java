package io.matrix.reflex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 217 — ReflexEngine unit tests. */
class ReflexEngineTest {

    @Test
    void emptyEngineHasNoReflexes() {
        var r = new ReflexEngine();
        assertThat(r.reflexCount()).isZero();
        assertThat(r.tryReflex("anything")).isNull();
    }

    @Test
    void registerAddsReflex() {
        var r = new ReflexEngine();
        r.register("hello", "world");
        assertThat(r.reflexCount()).isEqualTo(1);
    }

    @Test
    void matchingReturnsResponse() {
        var r = new ReflexEngine();
        r.register("hello", "hi there");
        assertThat(r.tryReflex("say hello friend")).isEqualTo("hi there");
    }

    @Test
    void firstMatchingReturnsResponse() {
        var r = new ReflexEngine();
        r.register("hello", "first");
        r.register("hi", "second");
        assertThat(r.tryReflex("hi there")).isEqualTo("second");
    }

    @Test
    void noMatchReturnsNull() {
        var r = new ReflexEngine();
        r.register("hello", "world");
        assertThat(r.tryReflex("goodbye")).isNull();
    }

    @Test
    void nullInputReturnsNull() {
        var r = new ReflexEngine();
        r.register("hello", "world");
        assertThat(r.tryReflex(null)).isNull();
    }

    @Test
    void emptyPatternMatchesEverything() {
        var r = new ReflexEngine();
        r.register("", "default");
        assertThat(r.tryReflex("anything")).isEqualTo("default");
    }
}
