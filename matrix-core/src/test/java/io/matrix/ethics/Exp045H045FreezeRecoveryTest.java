package io.matrix.ethics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.28 — H-045 ethics violation recovery (RUN 39).
 *
 * <p>H-045 hypothesis: after an ethics violation, the system
 * gracefully degrades (enters FROZEN) and recovers after a
 * cooldown — NOT a permanent lockout.
 */
class Exp045H045FreezeRecoveryTest {

    private FreezeRecoveryManager mgr;

    @BeforeEach
    void setUp() {
        // Use a moderate freeze duration for fast tests.
        mgr = new FreezeRecoveryManager(200L);
    }

    @Test
    void initialStateIsNormal() {
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.NORMAL);
        assertThat(mgr.isActionAllowed()).isTrue();
    }

    @Test
    void violationEntersFrozenState() {
        mgr.reportViolation("test", "forbidden action", 0);
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.FROZEN);
        assertThat(mgr.isActionAllowed()).isFalse();
    }

    @Test
    void frozenStateRecoversAfterCooldown() throws InterruptedException {
        mgr.reportViolation("test", "forbidden action", 0);
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.FROZEN);
        // Wait for cooldown (200ms in this test).
        Thread.sleep(300);
        // Next check should trigger recovery.
        assertThat(mgr.isActionAllowed()).isTrue();
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.RECOVERING);
    }

    @Test
    void multipleViolationsAreTracked() {
        mgr.reportViolation("src1", "reason1", 0);
        mgr.reportViolation("src2", "reason2", 1);
        mgr.reportViolation("src3", "reason3", 2);
        assertThat(mgr.violationCount()).isEqualTo(3);
        assertThat(mgr.violationHistory()).hasSize(3);
        // All violations should be recorded.
        assertThat(mgr.violationHistory().get(0).source()).isEqualTo("src1");
        assertThat(mgr.violationHistory().get(2).source()).isEqualTo("src3");
    }

    @Test
    void manualRecoveryReturnsToNormal() {
        mgr.reportViolation("test", "violation", 0);
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.FROZEN);
        mgr.manualRecover();
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.NORMAL);
        assertThat(mgr.isActionAllowed()).isTrue();
    }

    @Test
    void recoveryCountTracksAutoAndManualRecoveries() throws InterruptedException {
        // Auto-recovery
        mgr.reportViolation("test", "v1", 0);
        Thread.sleep(300);
        mgr.isActionAllowed();  // triggers auto-recovery
        // Manual recovery
        mgr.reportViolation("test", "v2", 1);
        mgr.manualRecover();
        assertThat(mgr.recoveryCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void actionIsBlockedDuringFreeze() {
        mgr.reportViolation("test", "violation", 0);
        // Immediate check: blocked.
        assertThat(mgr.isActionAllowed()).isFalse();
        // Multiple subsequent checks while still frozen: still blocked.
        for (int i = 0; i < 5; i++) {
            assertThat(mgr.isActionAllowed()).isFalse();
        }
    }

    @Test
    void freezeDurationIsRespected() {
        FreezeRecoveryManager longMgr = new FreezeRecoveryManager(10_000L);  // 10s
        longMgr.reportViolation("test", "v", 0);
        // Even after 50ms, still frozen (10s cooldown).
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        assertThat(longMgr.isActionAllowed()).isFalse();
        // Manual recovery bypasses the cooldown.
        longMgr.manualRecover();
        assertThat(longMgr.isActionAllowed()).isTrue();
    }

    @Test
    void reportViolationIsIdempotent() {
        // Reporting multiple violations during freeze keeps state FROZEN.
        mgr.reportViolation("a", "r1", 0);
        mgr.reportViolation("b", "r2", 1);
        mgr.reportViolation("c", "r3", 2);
        assertThat(mgr.getState()).isEqualTo(FreezeRecoveryManager.State.FROZEN);
        assertThat(mgr.violationCount()).isEqualTo(3);
    }

    @Test
    void zeroOrNegativeFreezeDurationClamped() {
        // Constructor clamps negative values to >= 100ms.
        FreezeRecoveryManager m = new FreezeRecoveryManager(-100L);
        assertThat(m.freezeDurationMs()).isGreaterThanOrEqualTo(100L);
    }
}
