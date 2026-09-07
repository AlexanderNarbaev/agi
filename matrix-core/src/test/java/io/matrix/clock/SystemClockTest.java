package io.matrix.clock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 222 — SystemClock unit tests. */
class SystemClockTest {

    @Test
    void realClockReturnsNonZero() {
        assertThat(SystemClock.REAL.currentTimeMillis()).isGreaterThan(0L);
        assertThat(SystemClock.REAL.nanoTime()).isGreaterThan(0L);
    }

    @Test
    void fixedClockReturnsSameValue() {
        SystemClock.FixedClock c = new SystemClock.FixedClock(1000L);
        assertThat(c.currentTimeMillis()).isEqualTo(1000L);
        assertThat(c.nanoTime()).isEqualTo(1000L * 1_000_000L);
    }

    @Test
    void fixedClockAdvances() {
        SystemClock.FixedClock c = new SystemClock.FixedClock(1000L);
        c.advance(500L);
        assertThat(c.currentTimeMillis()).isEqualTo(1500L);
        c.advance(250L);
        assertThat(c.currentTimeMillis()).isEqualTo(1750L);
    }

    @Test
    void currentClockSetting() {
        SystemClock.FixedClock original = new SystemClock.FixedClock(12345L);
        SystemClock.setCurrent(original);
        try {
            assertThat(SystemClock.now()).isEqualTo(12345L);
            assertThat(SystemClock.nano()).isEqualTo(12345L * 1_000_000L);
            original.advance(100L);
            assertThat(SystemClock.now()).isEqualTo(12445L);
        } finally {
            SystemClock.setCurrent(SystemClock.REAL);
        }
    }
}
