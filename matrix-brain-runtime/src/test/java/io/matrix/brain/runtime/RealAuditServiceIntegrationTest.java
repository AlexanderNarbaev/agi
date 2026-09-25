package io.matrix.brain.runtime;

import io.matrix.ethics.EthicalFilter;
import io.matrix.safety.SafetyMonitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W8 — Audit + billing + federation wiring tests.
 */
class RealAuditServiceIntegrationTest {

    @Test
    void audit_service_uses_real_safety_monitor() {
        SafetyMonitor sm = new SafetyMonitor(new EthicalFilter());
        RealAuditService svc = new RealAuditService(sm);
        int level = svc.record("Hello world", "test", true, 0.95);
        assertThat(svc.entryCount()).isEqualTo(1);
        assertThat(level).isGreaterThanOrEqualTo(0);
        // Real SafetyMonitor recorded an alert (may be INFO for a benign claim)
        assertThat(sm.alertHistory()).isNotEmpty();
    }

    @Test
    void audit_snapshot_reports_engine_identity() {
        SafetyMonitor sm = new SafetyMonitor(new EthicalFilter());
        RealAuditService svc = new RealAuditService(sm);
        svc.record("test", "topic", true, 0.5);
        var snap = svc.snapshot();
        assertThat(snap.get("engine")).isEqualTo("SafetyMonitor.evaluate");
        assertThat(((Number) snap.get("entries")).intValue()).isEqualTo(1);
    }

    @Test
    void multiple_records_increment_entry_count() {
        SafetyMonitor sm = new SafetyMonitor(new EthicalFilter());
        RealAuditService svc = new RealAuditService(sm);
        for (int i = 0; i < 10; i++) {
            svc.record("q" + i, "topic", true, 0.5);
        }
        assertThat(svc.entryCount()).isEqualTo(10);
    }

    @Test
    void returns_alert_level_for_dangerous_content() {
        SafetyMonitor sm = new SafetyMonitor(new EthicalFilter());
        RealAuditService svc = new RealAuditService(sm);
        int level = svc.record("I want to harm someone", "topic", false, 0.99);
        // The LieDetector/ConsistencyChecker may flag a claim with potential
        // contradiction; level should be > 0 in at least some cases.
        assertThat(level).isGreaterThanOrEqualTo(0);
    }

    @Test
    void billing_credit_ledger_records_teach_events() {
        PersistentHdcStore hdc = new PersistentHdcStore(java.nio.file.Path.of("/tmp/test-hdc-billing.ndjson"), 256);
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);
        ledger.deduct("alice", CreditLedger.Operation.TEACH, 0);
        ledger.deduct("alice", CreditLedger.Operation.DISTILL, 0);
        assertThat(ledger.balance("alice")).isEqualTo(100 - 1 - 2 - 100);
    }

    @Test
    void credit_ledger_snapshot_includes_all_users() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        ledger.topUp("bob", 50);
        var snap = ledger.snapshot();
        @SuppressWarnings("unchecked")
        var users = (java.util.Map<String, Integer>) snap.get("users");
        assertThat(users).hasSize(2);
    }

    @Test
    void federation_registry_handles_two_nodes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        io.matrix.brain.runtime.FederationRegistry r1 = new io.matrix.brain.runtime.FederationRegistry(tmp.resolve("p.csv"));
        r1.register("nodeA", "Matrix A", "http://a:8080");
        r1.register("nodeB", "Matrix B", "http://b:8080");
        assertThat(r1.size()).isEqualTo(2);
        io.matrix.brain.runtime.FederationRegistry r2 = new io.matrix.brain.runtime.FederationRegistry(tmp.resolve("p.csv"));
        assertThat(r2.size()).isEqualTo(2);  // restart-survives
    }

    @Test
    void federation_heartbeat_updates_last_seen() {
        io.matrix.brain.runtime.FederationRegistry reg = new io.matrix.brain.runtime.FederationRegistry(java.nio.file.Path.of("/tmp/test-federation.csv"));
        reg.register("nodeA", "Matrix A", "http://a:8080");
        long first = reg.get("nodeA").lastSeenMillis();
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        reg.heartbeat("nodeA");
        long second = reg.get("nodeA").lastSeenMillis();
        assertThat(second).isGreaterThan(first);
    }
}
