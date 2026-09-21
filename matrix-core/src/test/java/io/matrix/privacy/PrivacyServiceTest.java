package io.matrix.privacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyServiceTest {

    @Test
    void eraseGdprCreatesTombstoneWithCorrectReason() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        var t = svc.eraseGdpr("u-1", "FnlPackage", "fnl-1");
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_GDPR_ERASURE);
        assertThat(t.subjectId()).isEqualTo("u-1");
        assertThat(t.requesterId()).isEqualTo("PrivacyService");
    }

    @Test
    void eraseSubjectRequestUsesCorrectReason() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        var t = svc.eraseSubjectRequest("u", "FnlPackage", "fnl");
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_DATA_SUBJECT_REQUEST);
    }

    @Test
    void legalHoldUsesCorrectReason() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        var t = svc.legalHold("u", "FnlPackage", "fnl");
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_LEGAL_HOLD);
    }

    @Test
    void operationalCleanupUsesCorrectReason() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        var t = svc.operationalCleanup("u", "FnlPackage", "fnl");
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_OPERATIONAL);
    }

    @Test
    void eraseCustomReasonIsAllowed() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        var t = svc.erase("custom.policy", "u", "FnlPackage", "fnl");
        assertThat(t.reason()).isEqualTo("custom.policy");
    }

    @Test
    void multipleErasuresAccumulate() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u1", "X", "1");
        svc.eraseGdpr("u2", "X", "2");
        svc.legalHold("u3", "X", "3");
        assertThat(tombstones.count()).isEqualTo(3);
        assertThat(svc.totalErasures()).isEqualTo(3);
    }

    @Test
    void findReturnsTombstoneByKey() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u", "FnlPackage", "fnl-1");
        assertThat(svc.find("FnlPackage", "fnl-1")).isPresent();
        assertThat(svc.find("FnlPackage", "missing")).isEmpty();
    }

    @Test
    void findByReasonAndSubjectFilterDelegatesCorrectly() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u-1", "X", "1");
        svc.eraseGdpr("u-2", "X", "2");
        svc.legalHold("u-3", "X", "3");
        assertThat(svc.findByReason("gdpr.")).hasSize(2);
        assertThat(svc.findByReason("legal.")).hasSize(1);
        assertThat(svc.findBySubject("u-1")).hasSize(1);
    }

    @Test
    void eraseAndCascadeDelegatesToCascadeService() {
        var tombstones = new TombstoneService();
        var noosphere = new MockNoosphere()
                .addNeuron("a", "n1")
                .addNeuron("a", "n2");
        var cascade = CascadeRegistrar.registerAll(tombstones, new CascadeRegistrar.Resolvers(
                (id, d) -> noosphere.neuronsForAgent(id),
                (id, d) -> noosphere.snapshotsForAgent(id),
                (id, d) -> noosphere.truthTableForNeuron(id),
                (id, d) -> noosphere.knowledgeIndexForPackage(id)));
        var svc = new PrivacyService(tombstones, cascade);

        var tombstones_ = svc.eraseGdprAndCascade("u", "Agent", "a");
        // 1 root + 2 neurons = 3
        assertThat(tombstones_).hasSize(3);
        assertThat(tombstones.count()).isEqualTo(3);
        assertThat(svc.totalErasures()).isEqualTo(1);
        assertThat(svc.totalCascades()).isEqualTo(2);
    }

    @Test
    void exportAuditLogContainsHeaderAndAllTombstones() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u1", "X", "1");
        svc.eraseSubjectRequest("u2", "Y", "2");
        String dump = svc.exportAuditLog();
        assertThat(dump).contains("MATRIX Privacy Audit Log");
        assertThat(dump).contains("Total tombstones: 2");
        assertThat(dump).contains("X/1");
        assertThat(dump).contains("Y/2");
        assertThat(dump).contains("u1").contains("u2");
    }

    @Test
    void exportJsonLinesProducesValidJsonPerLine() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u", "FnlPackage", "fnl-1");
        svc.legalHold("u", "FnlPackage", "fnl-2");
        String jsonl = svc.exportJsonLines();
        String[] lines = jsonl.split("\n");
        assertThat(lines).hasSize(2);
        for (String line : lines) {
            assertThat(line).startsWith("{").endsWith("}");
            assertThat(line).contains("\"subjectId\":\"u\"");
        }
    }

    @Test
    void countersExposed() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        assertThat(svc.totalErasures()).isZero();
        assertThat(svc.totalCascades()).isZero();
        svc.eraseGdpr("u", "X", "1");
        assertThat(svc.totalErasures()).isEqualTo(1);
    }

    @Test
    void tombstonesAccessorReturnsUnderlying() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        assertThat(svc.tombstones()).isSameAs(tombstones);
    }

    @Test
    void countDelegatesToUnderlying() {
        var tombstones = new TombstoneService();
        var svc = new PrivacyService(tombstones);
        svc.eraseGdpr("u", "X", "1");
        svc.eraseGdpr("u", "X", "2");
        assertThat(svc.count()).isEqualTo(2);
    }
}