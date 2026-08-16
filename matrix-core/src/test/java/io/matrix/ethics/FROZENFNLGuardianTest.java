package io.matrix.ethics;

import io.matrix.audit.HashLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FROZENFNLGuardianTest {

    @Test
    void approvesNeutralText() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        assertThat(g.evaluate("Help me find a recipe")).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(g.totalDecisions()).isEqualTo(1);
        assertThat(g.totalRejections()).isZero();
    }

    @Test
    void rejectsHarmfulText() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        assertThat(g.evaluate("Kill the enemy")).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(g.totalRejections()).isEqualTo(1);
    }

    @Test
    void everyEvaluationIsRecordedInChain() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        g.evaluate("Kill");
        g.evaluate("Help");
        g.evaluate("Lie about it");
        assertThat(g.chain().size()).isEqualTo(4);  // 1 attestation + 3 decisions
    }

    @Test
    void auditTrailVerifies() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        g.evaluate("Torture");
        g.evaluate("Help");
        assertThat(g.verifyAuditTrail()).isTrue();
    }

    @Test
    void restoreReplacesChain() {
        var g1 = new FROZENFNLGuardian();
        g1.attestNow();
        g1.evaluate("Kill");
        g1.evaluate("Help");
        List<HashLink> snap = g1.chain().snapshot();

        var g2 = new FROZENFNLGuardian();
        g2.restoreFrom(snap);
        assertThat(g2.chain().size()).isEqualTo(3);
        assertThat(g2.verifyAuditTrail()).isTrue();
    }

    @Test
    void countersTrackAccurately() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        g.evaluate("Kill");    // REJECTED
        g.evaluate("Help");    // APPROVED
        g.evaluate("Torture"); // REJECTED
        g.evaluate("Cook");    // APPROVED
        assertThat(g.totalDecisions()).isEqualTo(4);
        assertThat(g.totalRejections()).isEqualTo(2);
    }

    @Test
    void summaryIsHumanReadable() {
        var g = new FROZENFNLGuardian();
        g.attestNow();
        g.evaluate("Kill");
        String summary = g.summary();
        assertThat(summary).contains("decisions=1").contains("rejected=1")
                .contains("FROZENFNLGuardian");
    }
}
