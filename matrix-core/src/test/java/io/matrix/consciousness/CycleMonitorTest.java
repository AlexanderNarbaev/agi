package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 297 — CycleMonitor unit tests. */
class CycleMonitorTest {

    @Test
    void emptyMonitor() {
        var m = new CycleMonitor();
        assertThat(m.size()).isZero();
        assertThat(m.last()).isNull();
    }

    @Test
    void recordAddsMetric() {
        var m = new CycleMonitor();
        var svc = new BrainLoopService();
        m.record(svc.cycle("hello"));
        assertThat(m.size()).isEqualTo(1);
    }

    @Test
    void lastReturnsMostRecent() {
        var m = new CycleMonitor();
        var svc = new BrainLoopService();
        m.record(svc.cycle("a"));
        m.record(svc.cycle("b"));
        assertThat(m.last()).isNotNull();
        assertThat(m.last().cycle()).isEqualTo(2);
    }

    @Test
    void clearResets() {
        var m = new CycleMonitor();
        var svc = new BrainLoopService();
        m.record(svc.cycle("x"));
        m.clear();
        assertThat(m.size()).isZero();
    }

    @Test
    void metricsReturnsAll() {
        var m = new CycleMonitor();
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) m.record(svc.cycle("x" + i));
        assertThat(m.metrics()).hasSize(5);
    }
}
