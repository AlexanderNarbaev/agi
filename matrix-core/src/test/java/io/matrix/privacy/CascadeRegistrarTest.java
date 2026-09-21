package io.matrix.privacy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeRegistrarTest {

    @Test
    void registerAllAddsFourRules() {
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base, CascadeRegistrar.noOpResolvers());
        assertThat(cascade.ruleCount()).isEqualTo(4);
    }

    @Test
    void agentCascadesToNeuronAndSnapshot() {
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(id + "-n1", id + "-n2"),  // 2 neurons
                        (id, d) -> List.of("snap-of-" + id),         // 1 snapshot
                        (id, d) -> List.of(),
                        (id, d) -> List.of()));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-A", "gdpr.erasure", "op");
        // 1 root + 2 neurons + 1 snapshot = 4
        assertThat(tombstones).hasSize(4);
        assertThat(base.isTombstoned("Agent", "agent-A")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-A-n1")).isTrue();
        assertThat(base.isTombstoned("Neuron", "agent-A-n2")).isTrue();
        assertThat(base.isTombstoned("Snapshot", "snap-of-agent-A")).isTrue();
    }

    @Test
    void neuronCascadesToTruthTable() {
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of("tt-of-" + id),         // 1 truth-table
                        (id, d) -> List.of()));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Neuron", "neuron-7", "r", "op");
        assertThat(tombstones).hasSize(2);  // root + 1 truth-table
        assertThat(base.isTombstoned("Neuron", "neuron-7")).isTrue();
        assertThat(base.isTombstoned("TruthTable", "tt-of-neuron-7")).isTrue();
    }

    @Test
    void fnlPackageCascadesToKnowledgeIndex() {
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of(),
                        (id, d) -> List.of("ki-" + id, "ki-" + id + "-2")));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "FnlPackage", "fnl-1", "r", "op");
        assertThat(tombstones).hasSize(3);  // root + 2 knowledge-index entries
    }

    @Test
    void noOpResolversCascadeWithoutDependents() {
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base, CascadeRegistrar.noOpResolvers());
        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-x", "r", "op");
        assertThat(tombstones).hasSize(1);  // just root
    }

    @Test
    void realResolversCanLookUpMaps() {
        var base = new TombstoneService();
        // Simulate a real resolver that uses a Map for lookups.
        Map<String, List<String>> neuronMap = Map.of(
                "agent-1", List.of("n-a", "n-b", "n-c"),
                "agent-2", List.of());
        Map<String, List<String>> snapshotMap = Map.of(
                "agent-1", List.of("snap-1", "snap-2"));
        var cascade = CascadeRegistrar.registerAll(base,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> neuronMap.getOrDefault(id, List.of()),
                        (id, d) -> snapshotMap.getOrDefault(id, List.of()),
                        (id, d) -> List.of("tt-" + id),
                        (id, d) -> List.of()));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-1", "r", "op");
        // 1 root + 3 neurons + 2 snapshots + (each neuron → truth table) = 1+3+2+3 = 9
        assertThat(tombstones).hasSize(9);
    }

    @Test
    void standardSourceTypesContainsExpectedNames() {
        assertThat(CascadeRegistrar.STANDARD_SOURCE_TYPES).contains("Agent", "Neuron", "FnlPackage", "Snapshot");
    }

    @Test
    void nullResolversAreReplacedWithNoOps() {
        // Resolvers constructor replaces nulls with no-op defaults.
        var resolvers = new CascadeRegistrar.Resolvers(null, null, null, null);
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base, resolvers);
        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "a", "r", "op");
        assertThat(tombstones).hasSize(1);
    }
}