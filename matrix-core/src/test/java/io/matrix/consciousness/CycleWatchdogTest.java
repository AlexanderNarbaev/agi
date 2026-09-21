package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 299 — CycleWatchdog unit tests. */
class CycleWatchdogTest {

    @Test
    void notExpiredBeforeStart() {
        var w = new CycleWatchdog(1000);
        assertThat(w.isExpired()).isFalse();
    }

    @Test
    void notExpiredImmediately() {
        var w = new CycleWatchdog(1000);
        w.startCycle();
        assertThat(w.isExpired()).isFalse();
        w.endCycle();
    }

    @Test
    void expiredAfterTimeout() throws Exception {
        var w = new CycleWatchdog(50); // 50ms
        w.startCycle();
        Thread.sleep(100);
        assertThat(w.isExpired()).isTrue();
        w.endCycle();
    }

    @Test
    void elapsedNanosPositive() {
        var w = new CycleWatchdog(1000);
        w.startCycle();
        try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        assertThat(w.elapsedNanos()).isGreaterThan(0);
        w.endCycle();
    }

    @Test
    void endCycleResets() {
        var w = new CycleWatchdog(1000);
        w.startCycle();
        w.endCycle();
        assertThat(w.elapsedNanos()).isZero();
    }
}
