package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 268 — CycleDebouncer unit tests. */
class CycleDebouncerTest {

    @Test
    void differentInputsAlwaysProceed() {
        var d = new CycleDebouncer();
        assertThat(d.shouldProcess("a")).isTrue();
        assertThat(d.shouldProcess("b")).isTrue();
        assertThat(d.shouldProcess("c")).isTrue();
    }

    @Test
    void repeatsAllowedUpToMax() {
        var d = new CycleDebouncer(3);
        assertThat(d.shouldProcess("x")).isTrue();
        assertThat(d.shouldProcess("x")).isTrue();
        assertThat(d.shouldProcess("x")).isFalse();  // 3rd repeat blocked
    }

    @Test
    void differentInputResetsCounter() {
        var d = new CycleDebouncer(2);
        d.shouldProcess("x");
        d.shouldProcess("x");  // 2 repeats
        d.shouldProcess("y");  // different → reset
        assertThat(d.shouldProcess("x")).isTrue();  // allowed again
    }

    @Test
    void nullInputBlocked() {
        var d = new CycleDebouncer();
        assertThat(d.shouldProcess(null)).isFalse();
    }

    @Test
    void resetClearsState() {
        var d = new CycleDebouncer(1);
        d.shouldProcess("x");
        d.shouldProcess("x");  // blocked
        d.reset();
        assertThat(d.shouldProcess("x")).isTrue();  // reset allows it
    }
}
