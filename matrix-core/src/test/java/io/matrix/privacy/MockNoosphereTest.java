package io.matrix.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockNoosphereTest {

    @Test
    void emptyNoosphereHasNoEntities() {
        var noo = new MockNoosphere();
        assertThat(noo.neuronsForAgent("any")).isEmpty();
        assertThat(noo.snapshotsForAgent("any")).isEmpty();
        assertThat(noo.truthTableForNeuron("any")).isEmpty();
        assertThat(noo.knowledgeIndexForPackage("any")).isEmpty();
        assertThat(noo.totalEntities()).isZero();
    }

    @Test
    void canAddAndQueryNeurons() {
        var noo = new MockNoosphere()
                .addNeuron("agent-A", "n1")
                .addNeuron("agent-A", "n2")
                .addNeuron("agent-B", "n3");
        assertThat(noo.neuronsForAgent("agent-A")).containsExactly("n1", "n2");
        assertThat(noo.neuronsForAgent("agent-B")).containsExactly("n3");
        assertThat(noo.neuronsForAgent("agent-C")).isEmpty();
        assertThat(noo.totalEntities()).isEqualTo(3);
    }

    @Test
    void canAddAndQueryTruthTables() {
        var noo = new MockNoosphere()
                .setTruthTable("neuron-1", "tt-1")
                .setTruthTable("neuron-2", "tt-2");
        assertThat(noo.truthTableForNeuron("neuron-1")).containsExactly("tt-1");
        assertThat(noo.truthTableForNeuron("neuron-2")).containsExactly("tt-2");
    }

    @Test
    void canAddAndQueryKnowledgeIndex() {
        var noo = new MockNoosphere()
                .addKnowledgeIndex("fnl-1", "ki-1")
                .addKnowledgeIndex("fnl-1", "ki-2")
                .addKnowledgeIndex("fnl-2", "ki-3");
        assertThat(noo.knowledgeIndexForPackage("fnl-1")).containsExactly("ki-1", "ki-2");
        assertThat(noo.knowledgeIndexForPackage("fnl-2")).containsExactly("ki-3");
    }

    @Test
    void returnedListsAreImmutable() {
        var noo = new MockNoosphere().addNeuron("a", "n1");
        var list = noo.neuronsForAgent("a");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> list.add("n2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void countByTypeReflectsAllEntities() {
        var noo = new MockNoosphere()
                .addNeuron("a", "n1")
                .addNeuron("a", "n2")
                .addSnapshot("a", "s1")
                .setTruthTable("n1", "tt-1")
                .addKnowledgeIndex("p1", "ki-1")
                .addKnowledgeIndex("p1", "ki-2");
        var counts = noo.countByType();
        assertThat(counts.get("neurons")).isEqualTo(2);
        assertThat(counts.get("snapshots")).isEqualTo(1);
        assertThat(counts.get("truthTables")).isEqualTo(1);
        assertThat(counts.get("knowledgeIndex")).isEqualTo(2);
    }

    @Test
    void realCascadeWithMockNoosphere() {
        // End-to-end: build a Noosphere, register cascades, erase an agent.
        var noo = new MockNoosphere()
                .addNeuron("agent-1", "agent-1-n1")
                .addNeuron("agent-1", "agent-1-n2")
                .setTruthTable("agent-1-n1", "tt-of-agent-1-n1")
                .addSnapshot("agent-1", "snap-of-agent-1");
        var base = new TombstoneService();
        var cascade = CascadeRegistrar.registerAll(base, new CascadeRegistrar.Resolvers(
                (id, d) -> noo.neuronsForAgent(id),
                (id, d) -> noo.snapshotsForAgent(id),
                (id, d) -> noo.truthTableForNeuron(id),
                (id, d) -> noo.knowledgeIndexForPackage(id)));

        var tombstones = cascade.tombstoneAndCascade(
                "u", "Agent", "agent-1", "gdpr.erasure", "op");
        // 1 root + 2 neurons + 1 snapshot + (n1 → truth table) = 5
        assertThat(tombstones).hasSize(5);
        assertThat(base.count()).isEqualTo(5);
        // Truth table is tombstoned.
        assertThat(base.isTombstoned("TruthTable", "tt-of-agent-1-n1")).isTrue();
    }
}