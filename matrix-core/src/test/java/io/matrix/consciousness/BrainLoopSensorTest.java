package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 250 — BrainLoopSensor unit tests. */
class BrainLoopSensorTest {

    @Test
    void emptySensor() {
        var s = new BrainLoopSensor();
        assertThat(s.size()).isZero();
        assertThat(s.measurements()).isEmpty();
    }

    @Test
    void recordAndRetrieve() {
        var s = new BrainLoopSensor();
        s.record(new BrainLoopSensor.Measurement(10, 256, 0.5, 0.69));
        s.record(new BrainLoopSensor.Measurement(20, 256, 0.6, 0.67));
        assertThat(s.size()).isEqualTo(2);
    }

    @Test
    void avgLength() {
        var s = new BrainLoopSensor();
        s.record(new BrainLoopSensor.Measurement(10, 0, 0, 0));
        s.record(new BrainLoopSensor.Measurement(20, 0, 0, 0));
        assertThat(s.avgLength()).isEqualTo(15.0);
    }

    @Test
    void avgDensity() {
        var s = new BrainLoopSensor();
        s.record(new BrainLoopSensor.Measurement(0, 0, 0.5, 0));
        s.record(new BrainLoopSensor.Measurement(0, 0, 0.7, 0));
        assertThat(s.avgDensity()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void entropyAtMaximum() {
        // density 0.5 → max entropy
        double h = BrainLoopSensor.entropy(0.5);
        assertThat(h).isCloseTo(0.693, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void entropyAtZero() {
        // density 0 or 1 → 0 entropy
        assertThat(BrainLoopSensor.entropy(0.0)).isZero();
        assertThat(BrainLoopSensor.entropy(1.0)).isZero();
    }

    @Test
    void entropyMonotoneOnEachSide() {
        // entropy decreases as density approaches 0 or 1
        double h0_3 = BrainLoopSensor.entropy(0.3);
        double h0_5 = BrainLoopSensor.entropy(0.5);
        double h0_1 = BrainLoopSensor.entropy(0.1);
        assertThat(h0_5).isGreaterThan(h0_3);
        assertThat(h0_3).isGreaterThan(h0_1);
    }
}
