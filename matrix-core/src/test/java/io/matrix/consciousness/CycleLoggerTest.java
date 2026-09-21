package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 291 — CycleLogger unit tests. */
class CycleLoggerTest {

    @Test
    void emptyLogger() {
        var l = new CycleLogger();
        assertThat(l.size()).isZero();
    }

    @Test
    void logAddsEntry() {
        var l = new CycleLogger();
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        l.log("hello", r);
        assertThat(l.size()).isEqualTo(1);
    }

    @Test
    void recentReturnsLastN() {
        var l = new CycleLogger();
        var svc = new BrainLoopService();
        for (int i = 0; i < 10; i++) l.log("i" + i, svc.cycle("i" + i));
        var recent = l.recent(3);
        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).cycle()).isEqualTo(8);
    }

    @Test
    void clearResets() {
        var l = new CycleLogger();
        var svc = new BrainLoopService();
        l.log("x", svc.cycle("x"));
        l.clear();
        assertThat(l.size()).isZero();
    }

    @Test
    void logEntryFields() {
        var l = new CycleLogger();
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        l.log("hello", r);
        var e = l.entries().get(0);
        assertThat(e.cycle()).isEqualTo(1);
        assertThat(e.input()).isEqualTo("hello");
        assertThat(e.accepted()).isEqualTo(r.accepted());
    }
}
