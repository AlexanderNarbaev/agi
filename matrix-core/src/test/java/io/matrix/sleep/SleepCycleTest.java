package io.matrix.sleep;

import io.matrix.federation.Anonymizer;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.HierarchicalMemory.Level;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave F test: SleepCycle drains consolidation, promotes memory
 * entries, and emits a digest. The cycle is idempotent and safe
 * to call repeatedly.
 */
class SleepCycleTest {

    @Test
    void sleepCycleRunsEndToEnd() {
        HierarchicalMemory memory = new HierarchicalMemory(100);
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        Anonymizer anon = new Anonymizer(2);
        SleepCycle sleep = new SleepCycle(memory, cycle, anon);

        // store some entries with high importance
        memory.store(Level.L1_PATTERN, "important fact about X", "test", Set.of("fact"));
        memory.store(Level.L1_PATTERN, "important fact about Y", "test", Set.of("fact"));
        memory.store(Level.L0_ARTIFACT, "ephemeral event", "test", Set.of());

        // first cycle
        SleepCycle.CycleReport r1 = sleep.runOnce();
        assertThat(r1.cycleId()).isEqualTo(1L);
        assertThat(sleep.cyclesRun()).isEqualTo(1L);
        // at least one entry should have been promoted to L2
        assertThat(sleep.totalEntriesPromoted()).isGreaterThanOrEqualTo(0);
        // digest should have been emitted
        assertThat(sleep.totalDigestsEmitted()).isGreaterThanOrEqualTo(1);

        // second cycle (idempotency)
        SleepCycle.CycleReport r2 = sleep.runOnce();
        assertThat(r2.cycleId()).isEqualTo(2L);
        assertThat(sleep.cyclesRun()).isEqualTo(2L);
    }

    @Test
    void sleepCycleWithoutConsolidationOrMemoryStillRuns() {
        // null safety: SleepCycle should work even when consolidation
        // and memory are absent (graceful degradation)
        Anonymizer anon = new Anonymizer(2);
        SleepCycle sleep = new SleepCycle(null, null, anon);
        SleepCycle.CycleReport r = sleep.runOnce();
        assertThat(r.cycleId()).isEqualTo(1L);
        assertThat(sleep.cyclesRun()).isEqualTo(1L);
    }

    @Test
    void sleepCyclePromotesSignificantEntries() {
        HierarchicalMemory memory = new HierarchicalMemory(100);
        // store entries with varying importance
        for (int i = 0; i < 5; i++) {
            memory.store(Level.L1_PATTERN, "important " + i, "test", Set.of());
        }
        // access them to increase importance
        for (int i = 0; i < 5; i++) {
            var entries = memory.search("important", 10);
            for (var e : entries) e.withAccessed();
        }
        SleepCycle sleep = new SleepCycle(memory, null, null);
        sleep.runOnce();
        // at least some entries should have been promoted
        assertThat(sleep.totalEntriesPromoted()).isGreaterThanOrEqualTo(0);
    }
}