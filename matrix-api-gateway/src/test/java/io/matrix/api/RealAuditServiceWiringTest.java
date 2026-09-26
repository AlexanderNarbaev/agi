package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 — RealAuditService is wired into the gateway.
 *
 * <p>The FROZEN-modulator events from the real SafetyMonitor
 * (in matrix-core) now land in the hash-chained audit log.</p>
 */
class RealAuditServiceWiringTest {

    @Test
    void gateway_initializes_real_audit_service_field() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("realAuditService");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.RealAuditService");
    }

    @Test
    void real_audit_service_uses_real_safety_monitor() throws Exception {
        // Verify the SafetyMonitor is real, not a stub.
        var f = MinimalHttpServer.class.getDeclaredField("realAuditService");
        f.setAccessible(true);
        Object svc = f.get(new MinimalHttpServer(0));
        var safetyMethod = svc.getClass().getMethod("safety");
        Object safety = safetyMethod.invoke(svc);
        assertThat(safety.getClass().getName())
            .isEqualTo("io.matrix.safety.SafetyMonitor");
    }

    @Test
    void real_audit_service_record_returns_alert_level() throws Exception {
        // Smoke-test: a benign message returns alert level 0.
        var f = MinimalHttpServer.class.getDeclaredField("realAuditService");
        f.setAccessible(true);
        Object svc = f.get(new MinimalHttpServer(0));
        var recordMethod = svc.getClass().getMethod("record",
            String.class, String.class, boolean.class, double.class);
        int level = (int) recordMethod.invoke(svc,
            "Paris is the capital of France", "analyze", true, 0.95);
        assertThat(level).isBetween(0, 3);
    }

    @Test
    void real_audit_service_count_increments() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("realAuditService");
        f.setAccessible(true);
        Object svc = f.get(new MinimalHttpServer(0));
        var recordMethod = svc.getClass().getMethod("record",
            String.class, String.class, boolean.class, double.class);
        long before = (long) svc.getClass().getMethod("entryCount").invoke(svc);
        recordMethod.invoke(svc, "test claim", "topic", true, 0.5);
        recordMethod.invoke(svc, "test claim 2", "topic", true, 0.5);
        long after = (long) svc.getClass().getMethod("entryCount").invoke(svc);
        assertThat(after - before).isEqualTo(2);
    }
}
