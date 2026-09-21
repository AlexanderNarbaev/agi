package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 256 — BrainLoopProfileAggregator unit tests. */
class BrainLoopProfileAggregatorTest {

    @Test
    void freshProfile() {
        var svc = new BrainLoopService();
        var latency = new BrainLoopLatency.LatencyStats(0, 0, 0, 0);
        var p = BrainLoopProfileAggregator.aggregate(svc, latency, 1000L);
        assertThat(p.arousal()).isEqualTo(0.3);  // baseline
        assertThat(p.cycles()).isZero();
        assertThat(p.timestampMillis()).isEqualTo(1000L);
    }

    @Test
    void activeProfile() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 50; i++) svc.cycle("X-" + i);
        var latency = new BrainLoopLatency.LatencyStats(50, 30, 9, 3594);
        var p = BrainLoopProfileAggregator.aggregate(svc, latency, 2000L);
        assertThat(p.cycles()).isEqualTo(50);
        assertThat(p.avgLatencyMicros()).isEqualTo(30);
    }

    @Test
    void formatProducesReadableString() {
        var p = new BrainLoopProfileAggregator.AggregatedProfile(
                0.5, 0.8, 0.1, 30, 50, 1000L);
        String text = BrainLoopProfileAggregator.format(p);
        assertThat(text).contains("Profile");
        assertThat(text).contains("arousal=0.50");
        assertThat(text).contains("health=0.80");
    }

    @Test
    void profileRecord() {
        var p = new BrainLoopProfileAggregator.AggregatedProfile(
                0.5, 0.8, 0.1, 30, 50, 1000L);
        assertThat(p.arousal()).isEqualTo(0.5);
        assertThat(p.health()).isEqualTo(0.8);
        assertThat(p.saturation()).isEqualTo(0.1);
        assertThat(p.avgLatencyMicros()).isEqualTo(30);
        assertThat(p.cycles()).isEqualTo(50);
    }
}
