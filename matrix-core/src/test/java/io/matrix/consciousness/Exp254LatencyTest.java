package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 254 — Latency EXP.
 *
 * <p>1000 cycles, measure latency, report stats.
 */
@Tag("exp")
class Exp254LatencyTest {

    @Test
    void thousandCyclesLatency() {
        var svc = new BrainLoopService();
        List<BrainLoopLatency.TimestampedResult> results = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            final int idx = i;
            results.add(BrainLoopLatency.measure(() -> svc.cycle("X-" + idx)));
        }
        var s = BrainLoopLatency.aggregate(results);
        System.out.println("[LATENCY-1000] " + BrainLoopLatency.format(s));
        assertThat(s.cycles()).isEqualTo(1000);
        // Latency is positive for completed cycles
        assertThat(s.avgMicros()).isGreaterThan(0);
    }
}
