package io.matrix.ethics;

import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FROZENGDPREscalatorTest {

    @Test
    void rejectedActionCreatesTombstone() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "test-user");

        EthicalVerdict v = escalator.evaluateAndRecord("Kill the enemy");
        assertThat(v).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(tombstones.count()).isEqualTo(1);
        assertThat(tombstones.all()).hasSize(1);
        Tombstone t = tombstones.all().get(0);
        assertThat(t.subjectId()).isEqualTo("test-user");
        assertThat(t.resourceType()).isEqualTo("Action");
        assertThat(t.requesterId()).isEqualTo("FROZENGDPREscalator");
        assertThat(t.reason()).startsWith("frozen.axiom.");
        assertThat(escalator.escalationsTriggered()).isEqualTo(1);
    }

    @Test
    void approvedActionDoesNotCreateTombstone() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "test-user");

        EthicalVerdict v = escalator.evaluateAndRecord("Help me find a book");
        assertThat(v).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(tombstones.count()).isZero();
        assertThat(escalator.escalationsTriggered()).isZero();
    }

    @Test
    void evaluateAndRecordAlwaysRecordsEveryAction() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        escalator.evaluateAndRecordAlways("Help me");
        escalator.evaluateAndRecordAlways("Kill the enemy");
        escalator.evaluateAndRecordAlways("Torture someone");
        // 3 actions = 3 tombstones (even approved ones).
        assertThat(tombstones.count()).isEqualTo(3);
        // All marked as escalations.
        assertThat(escalator.escalationsTriggered()).isEqualTo(3);
    }

    @Test
    void multipleRejectionsCreateMultipleTombstones() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        escalator.evaluateAndRecord("Kill the enemy");
        escalator.evaluateAndRecord("Torture someone");
        escalator.evaluateAndRecord("Enslave people");
        escalator.evaluateAndRecord("Dox the CEO");

        assertThat(tombstones.count()).isEqualTo(4);
        assertThat(tombstones.filterByReason("frozen.axiom.")).hasSize(4);
    }

    @Test
    void tombstoneResourceManuallyInserts() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        escalator.tombstoneResource("data-subject-1", "FnlPackage", "fnl-42", "gdpr.erasure");
        assertThat(tombstones.count()).isEqualTo(1);
        assertThat(tombstones.isTombstoned("FnlPackage", "fnl-42")).isTrue();
        assertThat(escalator.escalationsTriggered()).isEqualTo(1);
    }

    @Test
    void guardianAndTombstonesAreExposedForInspection() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        assertThat(escalator.guardian()).isSameAs(guardian);
        assertThat(escalator.tombstones()).isSameAs(tombstones);
    }

    @Test
    void nullActionThrows() {
        var escalator = new FROZENGDPREscalator(
                new FROZENFNLGuardian(), new TombstoneService(), () -> "u");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> escalator.evaluateAndRecord(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void multipleRejectionsAllRecordedInFROZENChain() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        guardian.attestNow();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        escalator.evaluateAndRecord("Kill the enemy");
        escalator.evaluateAndRecord("Help me");
        escalator.evaluateAndRecord("Torture");

        // The FROZEN chain should have: 1 attestation + 3 decisions.
        assertThat(guardian.chain().size()).isEqualTo(4);
        // And the tombstones should match REJECTs only.
        assertThat(tombstones.count()).isEqualTo(2);
        // Audit trail still intact.
        assertThat(guardian.verifyAuditTrail()).isTrue();
    }
}
