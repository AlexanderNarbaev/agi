package io.matrix.agent;

import io.matrix.audit.HashLink;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.FROZENFNLGuardian;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EthicsGuardTest {

    @Test
    void approvedActionReturnsAllow() {
        var guardian = new FROZENFNLGuardian();
        var guard = new EthicsGuard(guardian);
        var gate = guard.gate("Help me navigate home");
        assertThat(gate.allowed()).isTrue();
        assertThat(gate.escalated()).isFalse();
        assertThat(gate.auditLink()).isNotNull();
        assertThat(guard.totalChecked()).isEqualTo(1);
        assertThat(guard.totalAllowed()).isEqualTo(1);
        assertThat(guard.totalBlocked()).isZero();
    }

    @Test
    void rejectedActionReturnsDeny() {
        var guardian = new FROZENFNLGuardian();
        var guard = new EthicsGuard(guardian);
        var gate = guard.gate("Kill the enemy");
        assertThat(gate.allowed()).isFalse();
        assertThat(gate.escalated()).isFalse();
        assertThat(guard.totalBlocked()).isEqualTo(1);
        assertThat(guard.totalAllowed()).isZero();
    }

    @Test
    void isAllowedReturnsTrueForSafeText() {
        var guard = new EthicsGuard(new FROZENFNLGuardian());
        assertThat(guard.isAllowed("Help me with a recipe")).isTrue();
        assertThat(guard.isAllowed("Kill the enemy")).isFalse();
    }

    @Test
    void gateRecordsAuditLinkForEveryDecision() {
        var guardian = new FROZENFNLGuardian();
        var guard = new EthicsGuard(guardian);
        guard.gate("Help me");
        guard.gate("Kill the enemy");
        guard.gate("Help me again");
        assertThat(guardian.chain().size()).isEqualTo(3);
        assertThat(guard.totalChecked()).isEqualTo(3);
    }

    @Test
    void gateSummaryIsHumanReadable() {
        var guard = new EthicsGuard(new FROZENFNLGuardian());
        var gate = guard.gate("Help me");
        String s = gate.summary();
        assertThat(s).contains("ALLOW").contains("Gate[");
    }

    @Test
    void sinkReceivesAuditLink() {
        var guardian = new FROZENFNLGuardian();
        java.util.List<HashLink> captured = new java.util.ArrayList<>();
        var guard = new EthicsGuard(guardian, link -> {
            captured.add(link);
            return null;
        });
        guard.gate("Help me");
        guard.gate("Kill the enemy");
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0)).isNotNull();
        assertThat(captured.get(1)).isNotNull();
        // Different gates produce different audit links.
        assertThat(captured.get(0).hash()).isNotEqualTo(captured.get(1).hash());
    }

    @Test
    void multipleChecksAccumulate() {
        var guard = new EthicsGuard(new FROZENFNLGuardian());
        for (int i = 0; i < 5; i++) guard.gate("Help me " + i);
        for (int i = 0; i < 3; i++) guard.gate("Kill enemy " + i);
        for (int i = 0; i < 2; i++) guard.gate("Torture person " + i);
        assertThat(guard.totalChecked()).isEqualTo(10);
        assertThat(guard.totalAllowed()).isEqualTo(5);
        assertThat(guard.totalBlocked()).isEqualTo(5);
    }

    @Test
    void auditChainIsValidAfterManyGates() {
        var guardian = new FROZENFNLGuardian();
        var guard = new EthicsGuard(guardian);
        for (int i = 0; i < 20; i++) guard.gate(i % 2 == 0 ? "Help me" : "Kill the enemy");
        assertThat(guardian.verifyAuditTrail()).isTrue();
    }

    @Test
    void rejectsNullAction() {
        var guard = new EthicsGuard(new FROZENFNLGuardian());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> guard.gate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void guardianAccessorReturnsUnderlying() {
        var guardian = new FROZENFNLGuardian();
        var guard = new EthicsGuard(guardian);
        assertThat(guard.guardian()).isSameAs(guardian);
    }
}