package io.matrix.api;

import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeResourceTest {

    @Test
    void cascadeEraseCreatesMultipleTombstones() {
        var noo = new MockNoosphere()
                .addNeuron("agent-1", "n1")
                .addNeuron("agent-1", "n2")
                .setTruthTable("n1", "tt-1")
                .addSnapshot("agent-1", "snap-1");
        var base = new TombstoneService();
        var resource = new CascadeResource(base, noo);

        var req = new CascadeResource.CascadeRequest();
        req.subjectId = "user-1";
        req.sourceType = "Agent";
        req.sourceId = "agent-1";
        req.reason = "gdpr.erasure";
        req.requesterId = "op-1";

        var resp = resource.cascadeErase(req);
        assertThat(resp.getStatus()).isEqualTo(200);
        var body = (CascadeResource.CascadeResponse) resp.getEntity();
        // 1 root + 2 neurons + 1 snapshot + 1 truth table = 5
        assertThat(body.totalTombstoned()).isEqualTo(5);
        assertThat(body.sourceType()).isEqualTo("Agent");
        assertThat(body.sourceId()).isEqualTo("agent-1");
        assertThat(body.cascadeCount()).isEqualTo(4);
    }

    @Test
    void cascadeEraseValidatesRequiredFields() {
        var resource = new CascadeResource(new TombstoneService(), new MockNoosphere());
        var req = new CascadeResource.CascadeRequest();
        req.subjectId = "u";
        // Missing sourceType / sourceId / reason → 400
        var resp = resource.cascadeErase(req);
        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void noosphereStatsReturnsEntityCounts() {
        var noo = new MockNoosphere()
                .addNeuron("a", "n1")
                .addNeuron("a", "n2")
                .addSnapshot("a", "s1")
                .addKnowledgeIndex("p1", "ki-1");
        var resource = new CascadeResource(new TombstoneService(), noo);

        var resp = resource.noosphereStats();
        assertThat(resp.getStatus()).isEqualTo(200);
        var body = (CascadeResource.NoosphereStatsResponse) resp.getEntity();
        assertThat(body.totalEntities()).isEqualTo(4);
        assertThat(body.countByType().get("neurons")).isEqualTo(2);
        assertThat(body.countByType().get("snapshots")).isEqualTo(1);
        assertThat(body.countByType().get("knowledgeIndex")).isEqualTo(1);
    }

    @Test
    void dependentsForAgentReturnsNeuronsAndSnapshots() {
        var noo = new MockNoosphere()
                .addNeuron("a", "n1")
                .addNeuron("a", "n2")
                .addSnapshot("a", "s1");
        var resource = new CascadeResource(new TombstoneService(), noo);
        var resp = resource.dependents("Agent", "a");
        var body = (CascadeResource.DependentsResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.dependents()).containsExactlyInAnyOrder("n1", "n2", "s1");
    }

    @Test
    void dependentsForNeuronReturnsTruthTable() {
        var noo = new MockNoosphere().setTruthTable("n1", "tt-1");
        var resource = new CascadeResource(new TombstoneService(), noo);
        var resp = resource.dependents("Neuron", "n1");
        var body = (CascadeResource.DependentsResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.dependents()).containsExactly("tt-1");
    }

    @Test
    void dependentsForFnlPackageReturnsKnowledgeIndex() {
        var noo = new MockNoosphere()
                .addKnowledgeIndex("p1", "ki-1")
                .addKnowledgeIndex("p1", "ki-2");
        var resource = new CascadeResource(new TombstoneService(), noo);
        var resp = resource.dependents("FnlPackage", "p1");
        var body = (CascadeResource.DependentsResponse) resp.getEntity();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void dependentsForUnknownTypeReturnsEmpty() {
        var resource = new CascadeResource(new TombstoneService(), new MockNoosphere());
        var resp = resource.dependents("UnknownType", "x");
        var body = (CascadeResource.DependentsResponse) resp.getEntity();
        assertThat(body.size()).isZero();
    }

    @Test
    void fullLifecycle() {
        var noo = new MockNoosphere()
                .addNeuron("agent-X", "n1")
                .addNeuron("agent-X", "n2");
        var base = new TombstoneService();
        var resource = new CascadeResource(base, noo);

        // 1) populate noosphere
        // 2) cascade
        var req = new CascadeResource.CascadeRequest();
        req.subjectId = "u";
        req.sourceType = "Agent";
        req.sourceId = "agent-X";
        req.reason = "gdpr.erasure";
        resource.cascadeErase(req);

        // 3) check stats
        var stats = (CascadeResource.NoosphereStatsResponse) resource.noosphereStats().getEntity();
        // Noosphere is unchanged (we don't actually delete from the mock).
        assertThat(stats.totalEntities()).isEqualTo(2);
        // But tombstones were created.
        assertThat(base.isTombstoned("Agent", "agent-X")).isTrue();
        assertThat(base.isTombstoned("Neuron", "n1")).isTrue();
        assertThat(base.isTombstoned("Neuron", "n2")).isTrue();
    }
}