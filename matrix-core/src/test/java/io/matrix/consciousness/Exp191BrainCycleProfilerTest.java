package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 191 — Profiler EXP.
 *
 * <p>Real profiling: 1000 cycles, capture p50/p95/p99 cycle time.
 */
@Tag("exp")
class Exp191BrainCycleProfilerTest {

    @Test
    void profileThousandCycles() {
        var svc = new BrainLoopService();
        var run = BrainCycleProfiler.profile(svc, 1000);
        System.out.println("[PROFILE-1000] " + BrainCycleProfiler.formatRun(run));
        // Sanity check on bucket min/max
        assertThat(run.perception().minNanos()).isGreaterThan(0);
        assertThat(run.perception().maxNanos()).isGreaterThan(0);
        assertThat(run.perception().maxNanos())
                .isGreaterThanOrEqualTo(run.perception().minNanos());
        assertThat(run.cycles()).isEqualTo(1000);
    }
}
