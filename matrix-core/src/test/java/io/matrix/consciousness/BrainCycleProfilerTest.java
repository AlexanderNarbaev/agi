package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 190 — BrainCycleProfiler unit tests. */
class BrainCycleProfilerTest {

    @Test
    void profileOneHundredCycles() {
        var svc = new BrainLoopService();
        var prof = BrainCycleProfiler.profile(svc, 100);
        assertThat(prof.cycles()).isEqualTo(100);
        assertThat(prof.perception()).isNotNull();
        assertThat(prof.gate()).isNotNull();
        assertThat(prof.action()).isNotNull();
    }

    @Test
    void phaseStatsRecord() {
        var stats = new BrainCycleProfiler.PhaseStats(
                "X", 100, 200, 500, 800, 1000);
        assertThat(stats.phase()).isEqualTo("X");
        assertThat(stats.minNanos()).isEqualTo(100);
        assertThat(stats.p50Nanos()).isEqualTo(200);
        assertThat(stats.p99Nanos()).isEqualTo(800);
        assertThat(stats.maxNanos()).isEqualTo(1000);
    }

    @Test
    void profileRunRecord() {
        var run = new BrainCycleProfiler.ProfileRun(
                50,
                new BrainCycleProfiler.PhaseStats("p", 1, 2, 3, 4, 5),
                new BrainCycleProfiler.PhaseStats("g", 1, 2, 3, 4, 5),
                new BrainCycleProfiler.PhaseStats("a", 1, 2, 3, 4, 5),
                new BrainCycleProfiler.PhaseStats("t", 1, 2, 3, 4, 5));
        assertThat(run.cycles()).isEqualTo(50);
    }

    @Test
    void formatProducesReadableString() {
        var run = new BrainCycleProfiler.ProfileRun(
                10,
                new BrainCycleProfiler.PhaseStats("p", 100, 200, 500, 800, 1000),
                new BrainCycleProfiler.PhaseStats("g", 100, 200, 500, 800, 1000),
                new BrainCycleProfiler.PhaseStats("a", 100, 200, 500, 800, 1000),
                new BrainCycleProfiler.PhaseStats("t", 100, 200, 500, 800, 1000));
        String text = BrainCycleProfiler.formatRun(run);
        assertThat(text).contains("BrainCycleProfile");
        assertThat(text).contains("cycles=10");
        assertThat(text).contains("perceptionMin");
    }
}
