package io.matrix.reasoning;

import io.matrix.ethics.FreezeRecoveryManager;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FreezeRecoveryManager integration with BrainLoopService (RUN 49).
 */
class BrainLoopServiceFreezeIntegrationTest {

    @Test
    void tickWithoutFreezeManagerRunsNormally() {
        BrainLoopService svc = new BrainLoopService();
        var trace = svc.tick(new BitSet());
        assertThat(trace.tickId()).isGreaterThan(0);
        assertThat(trace.phasePath()).isNotEqualTo("frozen");
    }

    @Test
    void tickIsBlockedDuringFreeze() {
        BrainLoopService svc = new BrainLoopService();
        FreezeRecoveryManager freeze = new FreezeRecoveryManager(10_000L);  // 10s freeze
        svc.setFreezeManager(freeze);

        // Trigger violation.
        freeze.reportViolation("test", "violation", 0);
        // tick should return frozen trace.
        var trace = svc.tick(new BitSet());
        assertThat(trace.phasePath()).isEqualTo("frozen");
        assertThat(trace.tickId()).isEqualTo(-1L);
        // totalTicks should NOT increment because no real tick happened.
        assertThat(svc.totalTicks()).isZero();
    }

    @Test
    void tickResumesAfterCooldown() throws InterruptedException {
        BrainLoopService svc = new BrainLoopService();
        FreezeRecoveryManager freeze = new FreezeRecoveryManager(50L);
        svc.setFreezeManager(freeze);

        freeze.reportViolation("test", "v", 0);
        // During cooldown: frozen
        var t1 = svc.tick(new BitSet());
        assertThat(t1.phasePath()).isEqualTo("frozen");

        Thread.sleep(100);
        // After cooldown: tick resumes
        var t2 = svc.tick(new BitSet());
        assertThat(t2.phasePath()).isNotEqualTo("frozen");
        assertThat(t2.tickId()).isGreaterThan(0);
    }

    @Test
    void reportViolationForwardsToManager() {
        BrainLoopService svc = new BrainLoopService();
        FreezeRecoveryManager freeze = new FreezeRecoveryManager();
        svc.setFreezeManager(freeze);
        boolean result = svc.reportViolation("src", "reason");
        assertThat(result).isTrue();
        assertThat(freeze.violationCount()).isEqualTo(1);
    }

    @Test
    void reportViolationWithoutManagerReturnsFalse() {
        BrainLoopService svc = new BrainLoopService();
        // No freeze manager set.
        boolean result = svc.reportViolation("src", "reason");
        assertThat(result).isFalse();
    }

    @Test
    void getFreezeManagerReturnsCurrentManager() {
        BrainLoopService svc = new BrainLoopService();
        assertThat(svc.getFreezeManager()).isNull();
        FreezeRecoveryManager freeze = new FreezeRecoveryManager();
        svc.setFreezeManager(freeze);
        assertThat(svc.getFreezeManager()).isSameAs(freeze);
    }
}
