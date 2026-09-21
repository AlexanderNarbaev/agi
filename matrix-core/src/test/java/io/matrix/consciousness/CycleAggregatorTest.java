package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 238 — CycleAggregator unit tests. */
class CycleAggregatorTest {

    @Test
    void emptyAggregator() {
        var svc = new BrainLoopService();
        var w = CycleAggregator.aggregate(svc, 10);
        assertThat(w.cycles()).isZero();
    }

    @Test
    void aggregatorCountsCycles() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) svc.cycle("X-" + i);
        var w = CycleAggregator.aggregate(svc, 10);
        assertThat(w.cycles()).isEqualTo(5);
    }

    @Test
    void windowCappedAtSize() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 20; i++) svc.cycle("X-" + i);
        var w = CycleAggregator.aggregate(svc, 5);
        assertThat(w.cycles()).isEqualTo(5);
    }

    @Test
    void arousalReflectsCurrentState() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 30; i++) svc.cycle("X-" + i);
        var w = CycleAggregator.aggregate(svc, 100);
        assertThat(w.avgArousal()).isEqualTo(svc.arousal());
    }

    @Test
    void formatProducesReadableString() {
        var svc = new BrainLoopService();
        svc.cycle("X");
        var w = CycleAggregator.aggregate(svc, 10);
        String text = CycleAggregator.format(w);
        assertThat(text).contains("Window");
        assertThat(text).contains("cycles=");
    }

    @Test
    void windowStatsRecord() {
        var w = new CycleAggregator.WindowStats(10, 10, 0, 1.0, 0.5);
        assertThat(w.cycles()).isEqualTo(10);
        assertThat(w.acceptedRatio()).isEqualTo(1.0);
    }
}
