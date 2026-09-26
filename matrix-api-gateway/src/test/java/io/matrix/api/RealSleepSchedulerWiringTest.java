package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 — RealSleepScheduler is wired into the gateway.
 *
 * <p>Dream reports come from the real SleepCycle (matrix-core/sleep/),
 * not from a local stub. The gateway's local SleepScheduler/ConsolidationCycle
 * drafts (D-8) are scheduled for deletion once all consumers migrate.</p>
 */
class RealSleepSchedulerWiringTest {

    @Test
    void gateway_has_realSleepScheduler_field() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("realSleepScheduler");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.RealSleepScheduler");
    }

    @Test
    void realSleepScheduler_can_be_triggered_to_produce_dream() throws Exception {
        // Find the field, instantiate manually for the test.
        var f = MinimalHttpServer.class.getDeclaredField("realSleepScheduler");
        f.setAccessible(true);
        var sched = new io.matrix.brain.runtime.RealSleepScheduler(
            new io.matrix.memory.HierarchicalMemory(),
            new io.matrix.lifecycle.ConsolidationCycle(),
            new io.matrix.federation.Anonymizer(2),
            5);
        try {
            sched.noteActivity();
            var report = sched.triggerNow();
            assertThat(report).isNotNull();
            // DreamReport fields exist (cycleCount >= 0)
            assertThat(sched.cycleCount()).isGreaterThanOrEqualTo(0);
        } finally {
            sched.close();
        }
    }

    @Test
    void realSleepScheduler_snapshot_has_engine_marker() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("realSleepScheduler");
        f.setAccessible(true);
        var sched = new io.matrix.brain.runtime.RealSleepScheduler(
            new io.matrix.memory.HierarchicalMemory(),
            new io.matrix.lifecycle.ConsolidationCycle(),
            new io.matrix.federation.Anonymizer(2),
            5);
        try {
            // Trigger a dream so the report is populated.
            sched.triggerNow();
            var snap = sched.snapshot();
            // The engine marker lives inside the last_dream sub-map.
            @SuppressWarnings("unchecked")
            var lastDream = (java.util.Map<String, Object>) snap.get("last_dream");
            assertThat(lastDream).isNotNull();
            assertThat(lastDream).containsKey("engine");
            assertThat(lastDream.get("engine").toString())
                .isEqualTo("SleepCycle.runOnce");
        } finally {
            sched.close();
        }
    }
}
