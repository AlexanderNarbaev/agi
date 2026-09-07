package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 269 — CycleThrottle unit tests. */
class CycleThrottleTest {

    @Test
    void allowUpToLimit() {
        var t = new CycleThrottle(3);
        assertThat(t.allow()).isTrue();
        assertThat(t.allow()).isTrue();
        assertThat(t.allow()).isTrue();
    }

    @Test
    void blockAfterLimit() {
        var t = new CycleThrottle(2);
        t.allow();
        t.allow();
        assertThat(t.allow()).isFalse();
    }

    @Test
    void resetWindowAllowsAgain() {
        var t = new CycleThrottle(1);
        t.allow();
        t.resetWindow();
        assertThat(t.allow()).isTrue();
    }

    @Test
    void remainingReflectsUsage() {
        var t = new CycleThrottle(5);
        t.allow();
        t.allow();
        assertThat(t.remaining()).isEqualTo(3);
        assertThat(t.used()).isEqualTo(2);
    }
}
