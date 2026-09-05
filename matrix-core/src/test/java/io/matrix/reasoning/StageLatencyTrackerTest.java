package io.matrix.reasoning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StageLatencyTracker} — RUN 40 H-047 verification.
 */
class StageLatencyTrackerTest {

    private StageLatencyTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new StageLatencyTracker();
    }

    @Test
    void initialStateHasZeroCounts() {
        for (StageLatencyTracker.Stage s : StageLatencyTracker.Stage.values()) {
            assertThat(tracker.count(s)).isZero();
            assertThat(tracker.meanNs(s)).isZero();
        }
    }

    @Test
    void recordTracksCountSumMaxMin() {
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 1_000_000);  // 1ms
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 3_000_000);  // 3ms
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 2_000_000);  // 2ms

        assertThat(tracker.count(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(3);
        assertThat(tracker.sumNs(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(6_000_000);
        assertThat(tracker.maxNs(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(3_000_000);
        assertThat(tracker.minNs(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(1_000_000);
        assertThat(tracker.meanNs(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(2_000_000);
    }

    @Test
    void budgetsAreConfigured() {
        assertThat(tracker.budgetNs(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(5_000_000);
        assertThat(tracker.budgetNs(StageLatencyTracker.Stage.DELIBERATION)).isEqualTo(50_000_000);
        assertThat(tracker.budgetNs(StageLatencyTracker.Stage.ACTION)).isEqualTo(10_000_000);
    }

    @Test
    void withinBudgetReportsCorrectly() {
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 4_000_000);  // 4ms < 5ms budget
        assertThat(tracker.withinBudget(StageLatencyTracker.Stage.PERCEPTION)).isTrue();

        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 10_000_000);  // 10ms > 5ms budget
        assertThat(tracker.withinBudget(StageLatencyTracker.Stage.PERCEPTION)).isFalse();
    }

    @Test
    void recordElapsedUsesNanoTime() throws InterruptedException {
        long start = tracker.startTimer();
        Thread.sleep(1);  // 1ms
        tracker.recordElapsed(StageLatencyTracker.Stage.PERCEPTION, start);
        // Should be at least 1ms = 1_000_000 ns.
        assertThat(tracker.count(StageLatencyTracker.Stage.PERCEPTION)).isEqualTo(1);
        assertThat(tracker.sumNs(StageLatencyTracker.Stage.PERCEPTION)).isGreaterThanOrEqualTo(1_000_000);
    }

    @Test
    void recordRejectsNegative() {
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, -100);
        assertThat(tracker.count(StageLatencyTracker.Stage.PERCEPTION)).isZero();
    }

    @Test
    void recordRejectsNullStage() {
        tracker.record(null, 1_000_000);  // should be no-op
        // All counts should still be zero.
        for (StageLatencyTracker.Stage s : StageLatencyTracker.Stage.values()) {
            assertThat(tracker.count(s)).isZero();
        }
    }

    @Test
    void snapshotContainsAllStages() {
        Map<String, Map<String, Object>> snap = tracker.snapshot();
        assertThat(snap).containsKeys("PERCEPTION", "ATTENTION", "DELIBERATION", "GATE", "ACTION");
    }

    @Test
    void resetClearsAllStats() {
        tracker.record(StageLatencyTracker.Stage.PERCEPTION, 1_000_000);
        tracker.record(StageLatencyTracker.Stage.DELIBERATION, 10_000_000);
        tracker.reset();
        assertThat(tracker.count(StageLatencyTracker.Stage.PERCEPTION)).isZero();
        assertThat(tracker.count(StageLatencyTracker.Stage.DELIBERATION)).isZero();
        assertThat(tracker.maxNs(StageLatencyTracker.Stage.PERCEPTION)).isZero();
    }

    @Test
    void h047BudgetsMetForLightLoad() {
        // Light load: all stages well under budget.
        for (int i = 0; i < 100; i++) {
            tracker.record(StageLatencyTracker.Stage.PERCEPTION, 100_000);    // 0.1ms
            tracker.record(StageLatencyTracker.Stage.ATTENTION, 50_000);     // 0.05ms
            tracker.record(StageLatencyTracker.Stage.DELIBERATION, 500_000); // 0.5ms
            tracker.record(StageLatencyTracker.Stage.GATE, 30_000);         // 0.03ms
            tracker.record(StageLatencyTracker.Stage.ACTION, 80_000);       // 0.08ms
        }
        for (StageLatencyTracker.Stage s : StageLatencyTracker.Stage.values()) {
            assertThat(tracker.withinBudget(s))
                    .as("light load: %s within budget (max=%dns, budget=%dns)",
                            s, tracker.maxNs(s), tracker.budgetNs(s))
                    .isTrue();
        }
    }
}
