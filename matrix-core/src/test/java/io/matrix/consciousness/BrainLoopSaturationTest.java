package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 226 — BrainLoopSaturation unit tests. */
class BrainLoopSaturationTest {

    @Test
    void freshServiceHasZeroSaturation() {
        var svc = new BrainLoopService();
        var r = BrainLoopSaturation.compute(svc, 100);
        assertThat(r.cycles()).isZero();
        assertThat(r.saturationRatio()).isZero();
        assertThat(r.overloaded()).isFalse();
    }

    @Test
    void cyclesFillCapacity() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 50; i++) svc.cycle("Q-" + i);
        var r = BrainLoopSaturation.compute(svc, 100);
        assertThat(r.cycles()).isEqualTo(50);
        assertThat(r.saturationRatio()).isEqualTo(0.5);
    }

    @Test
    void overCapacityTriggersFlag() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 200; i++) svc.cycle("Q-" + i);
        var r = BrainLoopSaturation.compute(svc, 100);
        assertThat(r.overloaded()).isTrue();
        assertThat(r.saturationRatio()).isGreaterThan(1.0);
    }

    @Test
    void zeroCapacitySafe() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 10; i++) svc.cycle("Q-" + i);
        var r = BrainLoopSaturation.compute(svc, 0);
        assertThat(r.saturationRatio()).isZero();
        assertThat(r.overloaded()).isFalse();
    }

    @Test
    void formatProducesReadableString() {
        var svc = new BrainLoopService();
        svc.cycle("X");
        var r = BrainLoopSaturation.compute(svc, 100);
        String text = BrainLoopSaturation.format(r);
        assertThat(text).contains("Saturation");
        assertThat(text).contains("capacity=");
    }

    @Test
    void saturationReportRecord() {
        var r = new BrainLoopSaturation.SaturationReport(50, 100, 0.5, false);
        assertThat(r.cycles()).isEqualTo(50);
        assertThat(r.capacity()).isEqualTo(100);
        assertThat(r.saturationRatio()).isEqualTo(0.5);
        assertThat(r.overloaded()).isFalse();
    }
}
