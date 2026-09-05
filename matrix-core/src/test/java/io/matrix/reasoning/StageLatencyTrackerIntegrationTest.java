package io.matrix.reasoning;

import io.matrix.actions.ActionArena;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.neuron.SchemaDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StageLatencyTracker integration with ConsciousnessLoop (RUN 43).
 */
class StageLatencyTrackerIntegrationTest {

    private ActionArena arena;

    @BeforeEach
    void setUp() {
        arena = ActionArena.defaults();
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    private ConsciousnessLoop newLoop(java.util.function.Supplier<BitSet> perception) {
        BrcChain chain = new BrcChain(
                List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        return new ConsciousnessLoop(chain, arena, cycle, new ConjugateBudgeter(),
                ConsciousnessLoop.uniform(), perception);
    }

    @Test
    void latencyTrackerIsNullByDefault() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        assertThat(loop.getLatencyTracker()).isNull();
        // Tick should still work without a tracker.
        var snap = loop.tick();
        assertThat(snap.tickId()).isGreaterThan(0);
    }

    @Test
    void latencyTrackerRecordsStageMeasurements() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        StageLatencyTracker tracker = new StageLatencyTracker();
        loop.setLatencyTracker(tracker);
        // Run a few ticks.
        for (int i = 0; i < 5; i++) loop.tick();
        // Instrumented stages should have at least 1 measurement.
        // (GATE is not a separate step in this loop, so it's 0.)
        for (StageLatencyTracker.Stage s : new StageLatencyTracker.Stage[]{
                StageLatencyTracker.Stage.PERCEPTION,
                StageLatencyTracker.Stage.ATTENTION,
                StageLatencyTracker.Stage.DELIBERATION,
                StageLatencyTracker.Stage.ACTION}) {
            assertThat(tracker.count(s))
                    .as("stage %s should have measurements", s)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void latencyTrackerSnapshotIsNonEmpty() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        StageLatencyTracker tracker = new StageLatencyTracker();
        loop.setLatencyTracker(tracker);
        for (int i = 0; i < 3; i++) loop.tick();
        var snap = tracker.snapshot();
        assertThat(snap).isNotEmpty();
        // Each stage should have a non-null entry.
        for (StageLatencyTracker.Stage s : StageLatencyTracker.Stage.values()) {
            var entry = snap.get(s.name());
            assertThat(entry)
                    .as("snapshot entry for %s", s)
                    .isNotNull();
        }
    }

    @Test
    void latencyTrackerCanBeDisabled() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        StageLatencyTracker tracker = new StageLatencyTracker();
        loop.setLatencyTracker(tracker);
        for (int i = 0; i < 3; i++) loop.tick();
        long countBefore = tracker.count(StageLatencyTracker.Stage.PERCEPTION);
        // Disable tracking.
        loop.setLatencyTracker(null);
        for (int i = 0; i < 3; i++) loop.tick();
        long countAfter = tracker.count(StageLatencyTracker.Stage.PERCEPTION);
        // Count should not have changed after disabling.
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    void latencyTrackerCapturesProductionLatencies() {
        // Use a slightly more interesting perception supplier.
        ConsciousnessLoop loop = newLoop(() -> {
            Random r = new Random(42);
            BitSet bs = new BitSet(8);
            for (int i = 0; i < 8; i++) {
                if (r.nextBoolean()) bs.set(i);
            }
            return bs;
        });
        StageLatencyTracker tracker = new StageLatencyTracker();
        loop.setLatencyTracker(tracker);
        for (int i = 0; i < 10; i++) loop.tick();
        var snap = tracker.snapshot();
        // All stages within budget for light load.
        for (StageLatencyTracker.Stage s : StageLatencyTracker.Stage.values()) {
            var entry = snap.get(s.name());
            assertThat((Boolean) entry.get("withinBudget"))
                    .as("light-load: %s should be within budget (max=%.3fms, budget=%.3fms)",
                            s, entry.get("maxMs"), entry.get("budgetMs"))
                    .isTrue();
        }
    }
}
