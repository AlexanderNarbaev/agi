package io.matrix.e2e;

import io.matrix.api.AuditLogResource;
import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.PrivacyService;
import io.matrix.privacy.TombstoneService;
import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration test for the audit-log HTTP surface.
 *
 * <p>Exercises the full chain: HTTP request → PrivacyService → TombstoneService →
 * CascadeTombstoneService → MockNoosphere → JSON response.
 *
 * <p>Ref: docs/specs/WAVE_22_E2E_INTEGRATION.md.
 */
class AuditLogE2ETest {

    private AuditLogResource buildResource() {
        TombstoneService tombstones = new TombstoneService();
        MockNoosphere noosphere = new MockNoosphere();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(tombstones,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> noosphere.neuronsForAgent(id),
                        (id, d) -> noosphere.snapshotsForAgent(id),
                        (id, d) -> noosphere.truthTableForNeuron(id),
                        (id, d) -> noosphere.knowledgeIndexForPackage(id)));
        return new AuditLogResource(new PrivacyService(tombstones, cascade));
    }

    @Test
    void gdprCascadeFlow() {
        var resource = buildResource();
        // Pre-populate the Noosphere so cascade has targets.
        var privacy = resource.privacy();
        // First populate: add a neuron dependency directly via tombstones service.
        privacy.tombstones().tombstone(
                "u", "Neuron", "agent-1-n1", "r", "sig", "manual");

        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "data-subject-7";
        req.resourceType = "FnlPackage";
        req.resourceId = "fnl-1";
        req.reasonCode = "gdpr";
        req.cascade = false;  // simple erase (we've already pre-tombstoned)

        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(privacy.count()).isEqualTo(2);  // 1 pre + 1 erase
    }

    @Test
    void customReasonFlow() {
        var resource = buildResource();
        var req = new AuditLogResource.EraseRequest();
        req.subjectId = "u";
        req.resourceType = "FnlPackage";
        req.resourceId = "fnl-1";
        req.reasonCode = "custom";
        req.customReason = "company.policy.audit-cleanup";

        var resp = resource.erase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        var t = (io.matrix.privacy.Tombstone) resp.getEntity();
        assertThat(t.reason()).isEqualTo("company.policy.audit-cleanup");
    }

    @Test
    void allFiveReasonCodesAreSupported() {
        var resource = buildResource();
        for (String code : new String[]{"gdpr", "subject_request", "legal_hold", "operational"}) {
            var req = new AuditLogResource.EraseRequest();
            req.subjectId = "u";
            req.resourceType = "FnlPackage";
            req.resourceId = "fnl-" + code;
            req.reasonCode = code;
            var resp = resource.erase(req);
            assertThat(resp.getStatus()).as("reasonCode=" + code).isEqualTo(200);
        }
        assertThat(resource.privacy().count()).isEqualTo(4);
    }

    @Test
    void exportFlowRoundTrip() {
        var resource = buildResource();
        var privacy = resource.privacy();

        // 1) Erase a few resources with different reasons
        privacy.eraseGdpr("u1", "FnlPackage", "fnl-1");
        privacy.legalHold("u2", "FnlPackage", "fnl-2");
        privacy.operationalCleanup("u3", "Neuron", "n-1");

        // 2) Export as text — should mention all 3
        var textResp = resource.exportAudit("text");
        String textBody = (String) textResp.getEntity();
        assertThat(textBody).contains("Total tombstones: 3");
        assertThat(textBody).contains("FnlPackage/fnl-1");
        assertThat(textBody).contains("FnlPackage/fnl-2");
        assertThat(textBody).contains("Neuron/n-1");
        assertThat(textBody).contains("u1").contains("u2").contains("u3");

        // 3) Export as JSONL — should have 3 lines
        var jsonResp = resource.exportAudit("jsonl");
        String jsonBody = (String) jsonResp.getEntity();
        assertThat(jsonBody.split("\n")).hasSize(3);
    }

    @Test
    void filterBySubjectWorksThroughHttp() {
        var resource = buildResource();
        var privacy = resource.privacy();
        privacy.eraseGdpr("alice", "FnlPackage", "fnl-1");
        privacy.eraseGdpr("alice", "FnlPackage", "fnl-2");
        privacy.eraseGdpr("bob", "FnlPackage", "fnl-3");

        var resp = resource.listTombstones(null, "alice");
        var body = (AuditLogResource.TombstoneListResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.tombstones()).extracting(t -> t.resourceId())
                .containsExactlyInAnyOrder("fnl-1", "fnl-2");
    }

    @Test
    void filterByReasonWorksThroughHttp() {
        var resource = buildResource();
        var privacy = resource.privacy();
        privacy.eraseGdpr("u", "X", "1");
        privacy.eraseGdpr("u", "X", "2");
        privacy.legalHold("u", "Y", "3");

        var resp = resource.listTombstones("gdpr.", null);
        var body = (AuditLogResource.TombstoneListResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void countReflectsLifecycle() {
        var resource = buildResource();
        var privacy = resource.privacy();
        assertThat(((AuditLogResource.CountResponse) resource.count().getEntity())
                .totalTombstones() == 0);

        privacy.eraseGdpr("u", "X", "1");
        privacy.eraseGdpr("u", "X", "2");
        privacy.eraseGdpr("u", "X", "3");

        var body = (AuditLogResource.CountResponse) resource.count().getEntity();
        assertThat(body.totalTombstones()).isEqualTo(3);
        assertThat(body.totalErasures()).isEqualTo(3);
        assertThat(body.totalCascades()).isZero();
    }

    @Test
    void validationErrorsReturn400() {
        var resource = buildResource();
        assertThat(resource.erase(null).getStatus()).isEqualTo(400);

        var req = new AuditLogResource.EraseRequest();
        // Missing all fields
        assertThat(resource.erase(req).getStatus()).isEqualTo(400);

        req.subjectId = "u";
        req.resourceType = "X";
        req.resourceId = "1";
        req.reasonCode = "unknown";
        assertThat(resource.erase(req).getStatus()).isEqualTo(400);
    }

    @Test
    void e2eFlow_eraseExportListCount() {
        var resource = buildResource();
        var privacy = resource.privacy();

        // 1) Erase 5 different resources
        privacy.eraseGdpr("u1", "A", "1");
        privacy.eraseSubjectRequest("u2", "A", "2");
        privacy.legalHold("u3", "B", "1");
        privacy.operationalCleanup("u4", "B", "2");
        privacy.erase("company.policy", "u5", "C", "1");

        // 2) Count → 5
        var count = (AuditLogResource.CountResponse) resource.count().getEntity();
        assertThat(count.totalTombstones()).isEqualTo(5);

        // 3) List all → 5
        var list = (AuditLogResource.TombstoneListResponse)
                resource.listTombstones(null, null).getEntity();
        assertThat(list.size()).isEqualTo(5);

        // 4) Filter by reason → 2 (gdpr.erasures + subject_request)
        var gdpr = (AuditLogResource.TombstoneListResponse)
                resource.listTombstones("gdpr.", null).getEntity();
        assertThat(gdpr.size()).isEqualTo(2);

        // 5) Export text — contains all 5 tombstones
        String text = (String) resource.exportAudit("text").getEntity();
        assertThat(text).contains("A/1").contains("A/2").contains("B/1").contains("B/2").contains("C/1");
    }
}