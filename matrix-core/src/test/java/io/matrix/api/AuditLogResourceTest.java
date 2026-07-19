package io.matrix.api;

import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.PrivacyService;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogResourceTest {

    private PrivacyService buildPrivacyService() {
        TombstoneService tombstones = new TombstoneService();
        MockNoosphere noosphere = new MockNoosphere();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(tombstones,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> noosphere.neuronsForAgent(id),
                        (id, d) -> noosphere.snapshotsForAgent(id),
                        (id, d) -> noosphere.truthTableForNeuron(id),
                        (id, d) -> noosphere.knowledgeIndexForPackage(id)));
        return new PrivacyService(tombstones, cascade);
    }

    @Test
    void eraseGdprReturnsTombstone() {
        var svc = buildPrivacyService();
        var resource = new AuditLogResource(svc);
        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "FnlPackage";
        req.resourceId = "fnl-1";
        req.reasonCode = "gdpr";
        req.cascade = false;

        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        var t = (Tombstone) resp.getEntity();
        assertThat(t.reason()).isEqualTo(Tombstone.REASON_GDPR_ERASURE);
    }

    @Test
    void eraseWithCascadeReturnsList() {
        var svc = buildPrivacyService();
        var resource = new AuditLogResource(svc);
        // Add a neuron so cascade has something to delete
        svc.tombstones().tombstone("u", "FnlPackage", "fnl-1", "r", "", "manual");
        // For cascade test, set up a separate Noosphere with dependencies
        var noo = new MockNoosphere().addNeuron("a", "n1").addNeuron("a", "n2");
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base, new CascadeRegistrar.Resolvers(
                (id, d) -> noo.neuronsForAgent(id),
                (id, d) -> noo.snapshotsForAgent(id),
                (id, d) -> noo.truthTableForNeuron(id),
                (id, d) -> noo.knowledgeIndexForPackage(id)));
        var ps = new PrivacyService(base, cascade);
        var res = new AuditLogResource(ps);

        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "Agent";
        req.resourceId = "a";
        req.reasonCode = "gdpr";
        req.cascade = true;

        var resp = res.erase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        // Should be a list of 3 (1 root + 2 neurons)
        assertThat(resp.getEntity()).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) resp.getEntity()).hasSize(3);
    }

    @Test
    void eraseValidatesRequiredFields() {
        var resource = new AuditLogResource(buildPrivacyService());
        var resp = resource.erase(null);
        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void eraseRejectsUnknownReasonCode() {
        var resource = new AuditLogResource(buildPrivacyService());
        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "X";
        req.resourceId = "1";
        req.reasonCode = "unknown";
        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void eraseCustomRequiresCustomReason() {
        var resource = new AuditLogResource(buildPrivacyService());
        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "X";
        req.resourceId = "1";
        req.reasonCode = "custom";
        // Missing customReason → 400
        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void eraseCustomWorksWhenCustomReasonProvided() {
        var resource = new AuditLogResource(buildPrivacyService());
        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "X";
        req.resourceId = "1";
        req.reasonCode = "custom";
        req.customReason = "company.policy.specific";
        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        var t = (Tombstone) resp.getEntity();
        assertThat(t.reason()).isEqualTo("company.policy.specific");
    }

    @Test
    void exportAuditReturnsTextByDefault() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u", "X", "1");
        var resource = new AuditLogResource(svc);
        var resp = resource.exportAudit("text");
        assertThat(resp.getStatus()).isEqualTo(200);
        String body = (String) resp.getEntity();
        assertThat(body).contains("MATRIX Privacy Audit Log");
        assertThat(body).contains("X/1");
    }

    @Test
    void exportAuditJsonlWhenRequested() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u", "X", "1");
        svc.legalHold("u", "Y", "2");
        var resource = new AuditLogResource(svc);
        var resp = resource.exportAudit("jsonl");
        assertThat(resp.getStatus()).isEqualTo(200);
        String body = (String) resp.getEntity();
        String[] lines = body.split("\n");
        assertThat(lines).hasSize(2);
    }

    @Test
    void listTombstonesReturnsAll() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u1", "X", "1");
        svc.eraseGdpr("u2", "X", "2");
        var resource = new AuditLogResource(svc);
        var resp = resource.listTombstones(null, null);
        var body = (AuditLogResource.TombstoneListResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void listTombstonesFilteredByReason() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u", "X", "1");
        svc.legalHold("u", "X", "2");
        var resource = new AuditLogResource(svc);
        var resp = resource.listTombstones("gdpr.", null);
        var body = (AuditLogResource.TombstoneListResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    void listTombstonesFilteredBySubject() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u1", "X", "1");
        svc.eraseGdpr("u2", "X", "2");
        var resource = new AuditLogResource(svc);
        var resp = resource.listTombstones(null, "u1");
        var body = (AuditLogResource.TombstoneListResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    void countReturnsTotalAndCounters() {
        var svc = buildPrivacyService();
        svc.eraseGdpr("u", "X", "1");
        svc.eraseGdpr("u", "X", "2");
        svc.eraseGdpr("u", "X", "3");
        var resource = new AuditLogResource(svc);
        var resp = resource.count();
        var body = (AuditLogResource.CountResponse) resp.getEntity();
        assertThat(body.totalTombstones()).isEqualTo(3);
        assertThat(body.totalErasures()).isEqualTo(3);
        assertThat(body.totalCascades()).isZero();
    }

    @Test
    void privacyAccessorExposed() {
        var svc = buildPrivacyService();
        var resource = new AuditLogResource(svc);
        assertThat(resource.privacy()).isSameAs(svc);
    }
}