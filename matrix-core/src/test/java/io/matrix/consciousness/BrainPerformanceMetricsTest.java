package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 189 — BrainPerformanceMetrics unit tests. */
class BrainPerformanceMetricsTest {

    @Test
    void snapshotOfEmptyService() {
        var svc = new BrainLoopService();
        var snap = BrainPerformanceMetrics.snapshot(svc);
        assertThat(snap.totalCycles()).isZero();
        assertThat(snap.acceptedRatio()).isZero();
    }

    @Test
    void snapshotAfterActivity() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 20; i++) svc.cycle("Q-" + i);
        var snap = BrainPerformanceMetrics.snapshot(svc);
        assertThat(snap.totalCycles()).isGreaterThan(0);
    }

    @Test
    void formatProducesReadableString() {
        var svc = new BrainLoopService();
        svc.cycle("test");
        var snap = BrainPerformanceMetrics.snapshot(svc);
        String text = BrainPerformanceMetrics.format(snap);
        assertThat(text).contains("BrainPerf");
        assertThat(text).contains("cycles=");
        assertThat(text).contains("ratio=");
    }

    @Test
    void snapshotRecord() {
        var snap = new BrainPerformanceMetrics.Snapshot(
                10, 8, 2, 0.8, 5.0, 50);
        assertThat(snap.totalCycles()).isEqualTo(10);
        assertThat(snap.acceptedCycles()).isEqualTo(8);
        assertThat(snap.deniedCycles()).isEqualTo(2);
        assertThat(snap.acceptedRatio()).isEqualTo(0.8);
        assertThat(snap.longestTraceChain()).isEqualTo(50);
    }
}
