package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 296 — CycleScheduler unit tests. */
class CycleSchedulerTest {

    @Test
    void emptySchedule() {
        var s = new CycleScheduler();
        assertThat(s.size()).isZero();
        assertThat(s.next()).isNull();
    }

    @Test
    void scheduleAndNext() {
        var s = new CycleScheduler();
        s.schedule("a", 5);
        s.schedule("b", 10);
        var next = s.next();
        assertThat(next.input()).isEqualTo("b"); // higher priority first
    }

    @Test
    void priorityOrder() {
        var s = new CycleScheduler();
        s.schedule("low", 1);
        s.schedule("high", 10);
        s.schedule("mid", 5);
        assertThat(s.next().input()).isEqualTo("high");
        assertThat(s.next().input()).isEqualTo("mid");
        assertThat(s.next().input()).isEqualTo("low");
    }

    @Test
    void fifoTieBreak() {
        var s = new CycleScheduler();
        s.schedule("first", 5);
        s.schedule("second", 5);
        assertThat(s.next().input()).isEqualTo("first");
        assertThat(s.next().input()).isEqualTo("second");
    }

    @Test
    void pendingReturnsAll() {
        var s = new CycleScheduler();
        s.schedule("a", 1);
        s.schedule("b", 2);
        assertThat(s.pending()).hasSize(2);
    }

    @Test
    void clearResets() {
        var s = new CycleScheduler();
        s.schedule("a", 1);
        s.clear();
        assertThat(s.size()).isZero();
    }
}
