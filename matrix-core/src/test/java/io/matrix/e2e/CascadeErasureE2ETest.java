package io.matrix.e2e;

import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration test for the GDPR cascade erasure pipeline.
 *
 * <p>Tests that when a resource is tombstoned, all dependent resources
 * declared by the standard {@link CascadeRegistrar} rules are also
 * tombstoned — preventing GDPR Art. 17 leakage in derived artefacts.
 *
 * <p>Ref: docs/specs/WAVE_22_E2E_INTEGRATION.md, Wave 24.
 */
class CascadeErasureE2ETest {

    @Test
    void agentErasureCascadesToNeuronsAndSnapshots() {
        TombstoneService base = new TombstoneService();
        // Production-grade resolvers: 2 neurons per agent, 1 snapshot.
        Map<String, List<String>> neuronMap = Map.of(
                "agent-A", List.of("agent-A-n1", "agent-A-n2"),
                "agent-B", List.of("agent-B-n1"));
        Map<String, List<String>> snapshotMap = Map.of(
                "agent-A", List.of("snap-1", "snap-2"));
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> neuronMap.getOrDefault(id, List.of()),
                        (id, d) -> snapshotMap.getOrDefault(id, List.of()),
                        (id, d) -> List.of("tt-of-" + id),
                        (id, d) -> List.of()));

        // Erase agent-A. Should cascade to:
        // 1 root + 2 neurons + 2 snapshots + (each neuron → truth table) = 1+2+2+2 = 7
        List<Tombstone> tombstones = cascade.tombstoneAndCascade(
                "data-subject-1", "Agent", "agent-A", "gdpr.erasure", "op-1");

        assertThat(tombstones).hasSize(7);
        // Original is tombstoned
        assertThat(base.isTombstoned("Agent", "agent-A")).isTrue();
        // All neurons tombstoned
        assertThat(base.isTombstoned("Neuron", "agent-A-n1")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-A-n2")).isTrue();
        // All snapshots tombstoned
        assertThat(base.isTombstoned("Snapshot", "snap-1")).isTrue();
        assertThat(base.isTombstoned("Snapshot", "snap-2")).isTrue();
        // All truth tables (from neurons) tombstoned
        assertThat(base.isTombstoned("TruthTable", "tt-of-agent-A-n1")).isTrue();
        assertThat(base.isTombstoned("TruthTable", "tt-of-agent-A-n2")).isTrue();
    }

    @Test
    void fnlPackageErasureCascadesToKnowledgeIndex() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of("ki-" + id, "ki-" + id + "-v2")));

        List<Tombstone> tombstones = cascade.tombstoneAndCascade(
                "data-subject-2", "FnlPackage", "fnl-42", "gdpr.subject_request", "op-2");

        assertThat(tombstones).hasSize(3);  // root + 2 knowledge-index entries
        assertThat(base.isTombstoned("FnlPackage", "fnl-42")).isTrue();
        assertThat(base.isTombstoned("KnowledgeIndex", "ki-fnl-42")).isTrue();
        assertThat(base.isTombstoned("KnowledgeIndex", "ki-fnl-42-v2")).isTrue();
    }

    @Test
    void unknownResourceTypeCascadesNothing() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                CascadeRegistrar.noOpResolvers());
        // Type "UnknownType" has no cascade rules.
        List<Tombstone> tombstones = cascade.tombstoneAndCascade(
                "u", "UnknownType", "x", "r", "op");
        assertThat(tombstones).hasSize(1);
    }

    @Test
    void cascadeIdempotencySecondErasureIsNoOp() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(id + "-n"),
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of()));

        cascade.tombstoneAndCascade("u", "Agent", "a", "r", "op");
        assertThat(base.count()).isEqualTo(2);  // agent + 1 neuron

        // Second call is idempotent — same resources already tombstoned.
        List<Tombstone> secondCall = cascade.tombstoneAndCascade(
                "u", "Agent", "a", "r", "op");
        assertThat(secondCall).hasSize(1);  // Only the root (1) was actually created.
        assertThat(base.count()).isEqualTo(2);  // Total unchanged.
    }

    @Test
    void maxDepthLimitsCascadeChain() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = new CascadeTombstoneService(base, 1);

        // A → B → C (would normally cascade through 2 levels, but max depth is 1)
        cascade.register(new CascadeTombstoneService.CascadeRule("A", "B",
                (id, d) -> List.of(id + "-b"), "from-a"));
        cascade.register(new CascadeTombstoneService.CascadeRule("B", "C",
                (id, d) -> List.of(id + "-c"), "from-b"));

        var tombstones = cascade.tombstoneAndCascade("u", "A", "a1", "r", "op");
        // A → B (depth 0→1), but C is skipped (depth >= 1)
        assertThat(tombstones).hasSize(2);
        assertThat(cascade.cascadeSkipped()).isEqualTo(1);
    }

    @Test
    void multipleSubjectsCannotEraseEachOthersData() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                CascadeRegistrar.noOpResolvers());

        // Two different subjects erase the same package — only the first
        // creates a tombstone, the second is a no-op (idempotent).
        cascade.tombstoneAndCascade("subject-1", "FnlPackage", "shared", "r", "op");
        cascade.tombstoneAndCascade("subject-2", "FnlPackage", "shared", "r", "op");
        assertThat(base.count()).isEqualTo(1);
    }

    @Test
    void cascadeReasonTraceabilityIsIntact() {
        TombstoneService base = new TombstoneService();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(id + "-n"),
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of()));

        cascade.tombstoneAndCascade("u", "Agent", "agent-X", "gdpr.erasure", "op");

        // The cascaded Neuron tombstone's reason references the parent.
        var n = base.find("Neuron", "agent-X-n");
        assertThat(n).isNotNull();
        assertThat(n.reason()).contains("Agent.agent-X");
    }
}