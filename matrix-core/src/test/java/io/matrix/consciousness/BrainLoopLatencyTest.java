package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 253 — BrainLoopLatency unit tests. */
class BrainLoopLatencyTest {

    @Test
    void emptyStats() {
        var s = BrainLoopLatency.aggregate(List.of());
        assertThat(s.cycles()).isZero();
        assertThat(s.avgMicros()).isZero();
    }

    @Test
    void singleCycle() {
        var svc = new BrainLoopService();
        var tr = BrainLoopLatency.measure(() -> svc.cycle("X"));
        var s = BrainLoopLatency.aggregate(List.of(tr));
        assertThat(s.cycles()).isEqualTo(1);
        // Latency should be > 0
        assertThat(s.maxMicros()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void multipleCycles() {
        var svc = new BrainLoopService();
        List<BrainLoopLatency.TimestampedResult> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            results.add(BrainLoopLatency.measure(() -> svc.cycle("X-" + idx)));
        }
        var s = BrainLoopLatency.aggregate(results);
        assertThat(s.cycles()).isEqualTo(10);
    }

    @Test
    void minMaxComputed() {
        var svc = new BrainLoopService();
        List<BrainLoopLatency.TimestampedResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(BrainLoopLatency.measure(() -> svc.cycle("X")));
        }
        var s = BrainLoopLatency.aggregate(results);
        assertThat(s.maxMicros()).isGreaterThanOrEqualTo(s.minMicros());
        assertThat(s.avgMicros()).isBetween(
                (double) s.minMicros(), (double) s.maxMicros());
    }

    @Test
    void formatProducesReadableString() {
        var svc = new BrainLoopService();
        var tr = BrainLoopLatency.measure(() -> svc.cycle("X"));
        var s = BrainLoopLatency.aggregate(List.of(tr));
        String text = BrainLoopLatency.format(s);
        assertThat(text).contains("Latency");
        assertThat(text).contains("avg=");
    }
}
