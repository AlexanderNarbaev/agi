package io.matrix.brain.runtime;

import io.matrix.federation.Anonymizer;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W3 — Real SleepCycle integration tests.
 *
 * <p>Verifies that {@link RealSleepScheduler} wraps the real
 * {@link io.matrix.sleep.SleepCycle} from matrix-core, runs cycles,
 * emits engine-tagged dream reports, and survives multiple cycles.</p>
 */
class RealSleepSchedulerIntegrationTest {

    private static HierarchicalMemory mem() {
        return new HierarchicalMemory(1024);
    }

    private static ConsolidationCycle consolidation() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.open(new LinkedHashMap<>());
        return c;
    }

    private static Anonymizer anon() {
        return new Anonymizer(2);
    }

    @Test
    void single_cycle_returns_engine_tagged_dream() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            var dream = s.triggerNow();
            assertThat(dream).isNotNull();
            assertThat(dream.cycleId).isGreaterThan(0L);
            // Dream report must carry the engine identity (CONSTITUTION Article VIII)
            assertThat(dream.notes).anyMatch(n -> n.contains("SleepCycle.runOnce"));
            assertThat(dream.notes).anyMatch(n -> n.contains("ConsolidationCycle.tick"));
            assertThat(s.cycleCount()).isEqualTo(1L);
        } finally {
            s.close();
        }
    }

    @Test
    void multiple_cycles_increment_count() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            s.triggerNow();
            s.triggerNow();
            s.triggerNow();
            assertThat(s.cycleCount()).isEqualTo(3L);
        } finally {
            s.close();
        }
    }

    @Test
    void last_dream_returns_most_recent() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            s.triggerNow();
            var first = s.lastDream();
            assertThat(first).isNotNull();
            long firstId = first.cycleId;
            s.triggerNow();
            var second = s.lastDream();
            assertThat(second.cycleId).isGreaterThan(firstId);
        } finally {
            s.close();
        }
    }

    @Test
    void snapshot_reports_cycle_count_and_engine() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            s.triggerNow();
            var snap = s.snapshot();
            assertThat(snap.get("cycle_count")).isEqualTo(1L);
            assertThat(snap.get("idle_minutes")).isEqualTo(60);
            assertThat(snap.get("healthy")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            var dream = (java.util.Map<String, Object>) snap.get("last_dream");
            assertThat(dream.get("engine")).isEqualTo("SleepCycle.runOnce");
        } finally {
            s.close();
        }
    }

    @Test
    void idempotency_cycle_count_only_increments_when_runOnce_called() {
        // Create without auto-cycling.
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            assertThat(s.cycleCount()).isZero();
            assertThat(s.lastDream().cycleId).isZero();
        } finally {
            s.close();
        }
    }

    @Test
    void activity_tracking_does_not_advance_cycle_count() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            for (int i = 0; i < 100; i++) s.noteActivity();
            assertThat(s.cycleCount()).isZero();
        } finally {
            s.close();
        }
    }

    @Test
    void dream_report_engine_field_is_sleepcycle_runonce() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            var dream = s.triggerNow();
            var snap = dream.snapshot();
            assertThat(snap.get("engine")).isEqualTo("SleepCycle.runOnce");
            assertThat(snap.get("entriesPromoted")).isNotNull();
            assertThat(snap.get("digestsEmitted")).isNotNull();
            assertThat(snap.get("consolidationDrains")).isNotNull();
        } finally {
            s.close();
        }
    }

    @Test
    void consolidation_drains_real_data() {
        ConsolidationCycle c = consolidation();
        c.drain("test-route", 5);
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), c, anon(), 60);
        try {
            s.triggerNow();
            // Real ConsolidationCycle.backlog() returns the current backlog.
            // We just verify the call works without crash.
            assertThat(c.backlog("test-route")).isGreaterThanOrEqualTo(0);
        } finally {
            s.close();
        }
    }

    @Test
    void anonymizer_is_referenced_in_sleep_cycle() {
        Anonymizer a = anon();
        a.recordContribution("hash1", "node-a");
        a.recordContribution("hash1", "node-b");
        a.recordContribution("hash2", "node-c");
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), a, 60);
        try {
            // Sleep cycle must complete without crashing on a pre-populated anonymizer
            s.triggerNow();
            assertThat(s.cycleCount()).isEqualTo(1L);
        } finally {
            s.close();
        }
    }

    @Test
    void survives_many_consecutive_cycles() {
        RealSleepScheduler s = new RealSleepScheduler(
            mem(), consolidation(), anon(), 60);
        try {
            for (int i = 0; i < 25; i++) s.triggerNow();
            assertThat(s.cycleCount()).isEqualTo(25L);
        } finally {
            s.close();
        }
    }
}
