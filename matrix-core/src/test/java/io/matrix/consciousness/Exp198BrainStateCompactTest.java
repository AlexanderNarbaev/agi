package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 198 — BrainStateCompact EXP.
 *
 * <p>Real workload: 100 cycles, capture compact snapshots at
 * intervals. Verify state stable.
 */
@Tag("exp")
class Exp198BrainStateCompactTest {

    @Test
    void hundredCycleSnapshots() {
        var svc = new BrainLoopService();
        byte[] prev = null;
        int transitions = 0;
        for (int i = 0; i < 100; i++) {
            svc.cycle("cycle-" + i);
            if (i % 20 == 0) {
                byte[] snap = BrainStateCompact.pack(svc);
                if (prev != null && !java.util.Arrays.equals(prev, snap)) {
                    transitions++;
                }
                prev = snap;
                var unpacked = BrainStateCompact.unpack(snap);
                assertThat(unpacked.traceCount()).isGreaterThan(0);
            }
        }
        System.out.printf("[COMPACT-EXP] %d snapshots, %d transitions%n",
                100 / 20, transitions);
        // Each snapshot at different cycle should differ
        assertThat(transitions).isGreaterThan(2);
    }
}
