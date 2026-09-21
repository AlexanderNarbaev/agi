package io.matrix.privacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TombstoneServiceTest {

    @Test
    void tombstoneShouldReturnMarker() {
        var svc = new TombstoneService();
        Tombstone t = svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "operator-1");
        assertThat(t.subjectId()).isEqualTo("user-42");
        assertThat(t.resourceType()).isEqualTo("FnlPackage");
        assertThat(t.resourceId()).isEqualTo("fnl-1");
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_GDPR_ERASURE);
        assertThat(t.requesterId()).isEqualTo("operator-1");
        assertThat(t.isGdprErasure()).isTrue();
    }

    @Test
    void duplicateTombstoneShouldBeIdempotent() {
        var svc = new TombstoneService();
        var t1 = svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "operator-1");
        var t2 = svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "operator-2");
        assertThat(t1).isEqualTo(t2);
        assertThat(svc.count()).isEqualTo(1);
    }

    @Test
    void isTombstonedShouldReflectState() {
        var svc = new TombstoneService();
        assertThat(svc.isTombstoned("FnlPackage", "fnl-1")).isFalse();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "operator-1");
        assertThat(svc.isTombstoned("FnlPackage", "fnl-1")).isTrue();
        assertThat(svc.isTombstoned("FnlPackage", "fnl-other")).isFalse();
    }

    @Test
    void bulkTombstoneShouldApplyAtomicStamp() {
        var svc = new TombstoneService();
        var ts = svc.tombstoneAll("user-42", "FnlPackage",
                List.of("fnl-1", "fnl-2", "fnl-3"),
                Tombstone.REASON_GDPR_ERASURE, "operator-1");
        assertThat(ts).hasSize(3);
        assertThat(svc.count()).isEqualTo(3);
        assertThat(ts.stream().map(Tombstone::deletedAt).distinct().count()).isLessThanOrEqualTo(3);
    }

    @Test
    void filterByReasonShouldGroupGdprErasures() {
        var svc = new TombstoneService();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "op");
        svc.tombstoneGdpr("user-43", "FnlPackage", "fnl-2", "op");
        svc.tombstone("user-44", "FnlPackage", "fnl-3",
                Tombstone.REASON_LEGAL_HOLD, "hash", "op");

        assertThat(svc.filterByReason("gdpr.")).hasSize(2);
        assertThat(svc.filterByReason("legal.")).hasSize(1);
    }

    @Test
    void filterBySubjectShouldReturnSingle() {
        var svc = new TombstoneService();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "op");
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-2", "op");
        svc.tombstoneGdpr("user-43", "FnlPackage", "fnl-3", "op");

        assertThat(svc.filterBySubject("user-42")).hasSize(2);
        assertThat(svc.filterBySubject("user-99")).isEmpty();
    }

    @Test
    void summaryShouldIncludeCountsByReasonPrefix() {
        var svc = new TombstoneService();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "op");
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-2", "op");
        svc.tombstone("user-44", "FnlPackage", "fnl-3",
                Tombstone.REASON_OPERATIONAL, "", "op");
        String summary = svc.summary();
        assertThat(summary).contains("gdpr=2").contains("operational=1");
    }

    @Test
    void allShouldReturnImmutableSnapshot() {
        var svc = new TombstoneService();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "op");
        var snap = svc.all();
        assertThat(snap).hasSize(1);
        // mutating the returned list should not throw — it is an unmodifiable copy
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> snap.add(svc.tombstoneGdpr("x", "FnlPackage", "fnl-2", "op")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void customReasonShouldBePreserved() {
        var svc = new TombstoneService();
        var t = svc.tombstone("user-42", "Neuron", "neuron-9",
                "company.policy.specific", "sha256:abc", "compliance-1");
        assertThat(t.reason()).isEqualTo("company.policy.specific");
        assertThat(t.signature()).isEqualTo("sha256:abc");
        assertThat(t.isGdprErasure()).isFalse();
    }

    @Test
    void shouldHandleMultipleResourceTypes() {
        var svc = new TombstoneService();
        svc.tombstoneGdpr("user-42", "FnlPackage", "fnl-1", "op");
        svc.tombstoneGdpr("user-42", "Neuron", "neuron-1", "op");
        svc.tombstoneGdpr("user-42", "Snapshot", "snap-1", "op");
        assertThat(svc.count()).isEqualTo(3);
        assertThat(svc.isTombstoned("FnlPackage", "fnl-1")).isTrue();
        assertThat(svc.isTombstoned("Neuron", "neuron-1")).isTrue();
        assertThat(svc.isTombstoned("Snapshot", "snap-1")).isTrue();
    }
}
