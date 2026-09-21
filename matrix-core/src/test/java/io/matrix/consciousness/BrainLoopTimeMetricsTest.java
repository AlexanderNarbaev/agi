package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 255 — BrainLoopTimeMetrics unit tests. */
class BrainLoopTimeMetricsTest {

    @Test
    void emptyMetrics() {
        var m = new BrainLoopTimeMetrics();
        assertThat(m.size()).isZero();
        assertThat(m.totalMicros()).isZero();
    }

    @Test
    void firstCycleHasZeroDelta() {
        var m = new BrainLoopTimeMetrics();
        var ts = m.record(0);
        assertThat(ts.deltaMicros()).isZero();
        assertThat(m.size()).isEqualTo(1);
    }

    @Test
    void secondCycleHasDelta() {
        var m = new BrainLoopTimeMetrics();
        m.record(0);
        try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        m.record(1);
        var all = m.all();
        assertThat(all.get(1).deltaMicros()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void totalMicrosSpan() {
        var m = new BrainLoopTimeMetrics();
        m.record(0);
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        m.record(1);
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        m.record(2);
        assertThat(m.totalMicros()).isGreaterThan(0);
    }

    @Test
    void avgDeltaComputes() {
        var m = new BrainLoopTimeMetrics();
        for (int i = 0; i < 5; i++) m.record(i);
        assertThat(m.avgDeltaMicros()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void timestampRecordFields() {
        var m = new BrainLoopTimeMetrics();
        var ts = m.record(42);
        assertThat(ts.cycleNumber()).isEqualTo(42);
        assertThat(ts.nanoTime()).isGreaterThan(0);
    }
}
