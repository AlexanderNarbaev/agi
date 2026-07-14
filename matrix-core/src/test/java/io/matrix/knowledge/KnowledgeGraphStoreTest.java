package io.matrix.knowledge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeGraphStoreTest {

    private KnowledgeGraphStore graph;

    @BeforeEach
    void setup() {
        graph = new KnowledgeGraphStore();
    }

    // ─── 1. Entity CRUD ───

    @Test
    void shouldAddAndRetrieveEntity() {
        var entity = new KnowledgeGraphStore.Entity("n1", "NEURON",
                Map.of("layer", 3, "activation", "relu"));
        graph.addEntity(entity);

        assertThat(graph.entityCount()).isEqualTo(1);
        assertThat(graph.getEntity("n1")).isPresent();
        assertThat(graph.getEntity("n1").get().type()).isEqualTo("NEURON");
        assertThat(graph.getEntity("n1").get().properties())
                .containsEntry("layer", 3)
                .containsEntry("activation", "relu");
    }

    @Test
    void shouldReturnEmptyForMissingEntity() {
        assertThat(graph.getEntity("nonexistent")).isEmpty();
    }

    @Test
    void shouldOverwriteExistingEntity() {
        graph.addEntity(new KnowledgeGraphStore.Entity("n1", "NEURON"));
        graph.addEntity(new KnowledgeGraphStore.Entity("n1", "SYNAPSE"));
        assertThat(graph.entityCount()).isEqualTo(1);
        assertThat(graph.getEntity("n1").get().type()).isEqualTo("SYNAPSE");
    }

    // ─── 2. Relation CRUD ───

    @Test
    void shouldAddRelationBetweenEntities() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "NEURON"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "NEURON"));
        graph.addRelation(new KnowledgeGraphStore.Relation("a", "b", "fires_to", 0.8));

        assertThat(graph.relationCount()).isEqualTo(1);
        assertThat(graph.getRelations("a")).hasSize(1);
        assertThat(graph.getRelations("a").get(0).predicate()).isEqualTo("fires_to");
        assertThat(graph.getRelations("a").get(0).weight()).isEqualTo(0.8);
    }

    @Test
    void shouldRejectRelationWithMissingSource() {
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "NEURON"));
        assertThatThrownBy(() ->
                graph.addRelation(new KnowledgeGraphStore.Relation("a", "b", "fires_to")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a");
    }

    @Test
    void shouldRejectRelationWithMissingTarget() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "NEURON"));
        assertThatThrownBy(() ->
                graph.addRelation(new KnowledgeGraphStore.Relation("a", "b", "fires_to")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("b");
    }

    @Test
    void shouldRejectRelationWithInvalidWeight() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "NEURON"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "NEURON"));
        assertThatThrownBy(() ->
                new KnowledgeGraphStore.Relation("a", "b", "p", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new KnowledgeGraphStore.Relation("a", "b", "p", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── 3. Graph Traversal ───

    @Test
    void shouldTraverseGraphByDepth() {
        buildLinearChain("n1", "n2", "n3", "n4");

        var results = graph.traverse("n1", 3, r -> true);

        assertThat(results).isNotEmpty();
        // From n1 at depth 3, we should see paths: n1→n2, n1→n2→n3, n1→n2→n3→n4
        assertThat(results).anyMatch(r -> r.depth() == 1);
        assertThat(results).anyMatch(r -> r.depth() == 2);
        assertThat(results).anyMatch(r -> r.depth() == 3);
    }

    @Test
    void shouldRespectMaxDepth() {
        buildLinearChain("n1", "n2", "n3", "n4", "n5");

        var results = graph.traverse("n1", 2, r -> true);

        // Max depth 2 means only edges with depth 1 and 2
        assertThat(results).noneMatch(r -> r.depth() > 2);
        assertThat(results).anyMatch(r -> r.depth() == 2);
    }

    @Test
    void shouldFilterEdgesDuringTraversal() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("c", "N"));
        graph.addRelation(new KnowledgeGraphStore.Relation("a", "b", "good", 0.9));
        graph.addRelation(new KnowledgeGraphStore.Relation("b", "c", "bad", 0.1));

        var filteredResults = graph.traverse("a", 5, r -> r.weight() >= 0.5);

        assertThat(filteredResults).hasSize(1);
        assertThat(filteredResults.get(0).edges().get(0).predicate()).isEqualTo("good");
    }

    @Test
    void shouldReturnEmptyForMissingStartEntity() {
        var results = graph.traverse("nonexistent", 5, r -> true);
        assertThat(results).isEmpty();
    }

    // ─── 4. Path Finding ───

    @Test
    void shouldFindPathBetweenEntities() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("c", "N"));
        graph.addRelation(new KnowledgeGraphStore.Relation("a", "b", "to"));
        graph.addRelation(new KnowledgeGraphStore.Relation("b", "c", "to"));

        var path = graph.findPath("a", "c", 5);

        assertThat(path).hasSize(3);
        assertThat(path.get(0).id()).isEqualTo("a");
        assertThat(path.get(1).id()).isEqualTo("b");
        assertThat(path.get(2).id()).isEqualTo("c");
    }

    @Test
    void shouldReturnEmptyWhenNoPathExists() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "N"));
        // No relation between them

        var path = graph.findPath("a", "b", 5);
        assertThat(path).isEmpty();
    }

    @Test
    void shouldReturnSingleEntityForSelfPath() {
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));

        var path = graph.findPath("a", "a", 5);

        assertThat(path).hasSize(1);
        assertThat(path.get(0).id()).isEqualTo("a");
    }

    // ─── 5. Centrality & Metrics ───

    @Test
    void shouldComputeDegreeCentrality() {
        graph.addEntity(new KnowledgeGraphStore.Entity("hub", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "N"));
        graph.addRelation(new KnowledgeGraphStore.Relation("hub", "a", "to"));
        graph.addRelation(new KnowledgeGraphStore.Relation("hub", "b", "to"));

        double hubCentrality = graph.centrality("hub");
        double leafCentrality = graph.centrality("a");

        assertThat(hubCentrality).isGreaterThan(leafCentrality);
        assertThat(hubCentrality).isEqualTo(1.0); // 2 edges / (3-1) = 1.0
    }

    @Test
    void shouldReturnZeroCentralityForMissingEntity() {
        assertThat(graph.centrality("nonexistent")).isEqualTo(0.0);
    }

    @Test
    void shouldFindTopCentralEntities() {
        graph.addEntity(new KnowledgeGraphStore.Entity("hub", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("a", "N"));
        graph.addEntity(new KnowledgeGraphStore.Entity("b", "N"));
        graph.addRelation(new KnowledgeGraphStore.Relation("hub", "a", "to"));
        graph.addRelation(new KnowledgeGraphStore.Relation("hub", "b", "to"));

        var top = graph.topCentral(1);

        assertThat(top).hasSize(1);
        assertThat(top.get(0)).isEqualTo("hub");
    }

    // ─── 6. JSON Roundtrip ───

    @Test
    void shouldRoundtripThroughJson() {
        graph.addEntity(new KnowledgeGraphStore.Entity("n1", "NEURON",
                Map.of("layer", 1)));
        graph.addEntity(new KnowledgeGraphStore.Entity("n2", "NEURON"));
        graph.addRelation(new KnowledgeGraphStore.Relation("n1", "n2", "fires_to", 0.75));

        String json = graph.toJson();
        KnowledgeGraphStore restored = KnowledgeGraphStore.fromJson(json);

        assertThat(restored.entityCount()).isEqualTo(2);
        assertThat(restored.relationCount()).isEqualTo(1);
        assertThat(restored.getEntity("n1")).isPresent();
        assertThat(restored.getEntity("n1").get().properties()).containsEntry("layer", 1);
        assertThat(restored.getRelations("n1")).hasSize(1);
        assertThat(restored.getRelations("n1").get(0).weight()).isEqualTo(0.75);
    }

    // ─── 7. Thread Safety ───

    @Test
    void shouldHandleConcurrentEntityAdds() throws Exception {
        int threadCount = 8;
        int entitiesPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < entitiesPerThread; i++) {
                        String id = "t" + threadId + "_e" + i;
                        graph.addEntity(new KnowledgeGraphStore.Entity(id, "NEURON"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(graph.entityCount()).isEqualTo(threadCount * entitiesPerThread);
    }

    // ─── 8. Empty Graph ───

    @Test
    void shouldHandleEmptyGraph() {
        assertThat(graph.entityCount()).isEqualTo(0);
        assertThat(graph.relationCount()).isEqualTo(0);
        assertThat(graph.topCentral(5)).isEmpty();
        assertThat(graph.toJson()).contains("\"entities\":[]");
    }

    // ─── 9. Large Graph Performance ───

    @Test
    void shouldHandleLargeGraph() {
        int entityCount = 2000;
        int relationCount = 2000;

        for (int i = 0; i < entityCount; i++) {
            graph.addEntity(new KnowledgeGraphStore.Entity("e" + i, "NEURON"));
        }

        // Chain structure: e0 → e1 → e2 → ... → e1999 (1999 relations)
        for (int i = 0; i < entityCount - 1; i++) {
            graph.addRelation(new KnowledgeGraphStore.Relation(
                    "e" + i, "e" + (i + 1), "next", 1.0));
        }

        // Add cross-links to reach exactly 2000 relations: e0 → e2 (1 relation)
        graph.addRelation(new KnowledgeGraphStore.Relation(
                "e0", "e2", "skip_to", 0.5));

        assertThat(graph.entityCount()).isEqualTo(entityCount);
        assertThat(graph.relationCount()).isEqualTo(relationCount);

        // Verify operations still work
        var path = graph.findPath("e0", "e999", entityCount);
        assertThat(path).isNotEmpty();

        var top = graph.topCentral(3);
        assertThat(top).hasSize(3);

        // JSON roundtrip for large graph
        String json = graph.toJson();
        assertThat(json).isNotEmpty();
        KnowledgeGraphStore restored = KnowledgeGraphStore.fromJson(json);
        assertThat(restored.entityCount()).isEqualTo(entityCount);
        assertThat(restored.relationCount()).isEqualTo(relationCount);
    }

    // ─── 10. Builder Pattern ───

    @Test
    void shouldBuildGraphWithBuilder() {
        var g = KnowledgeGraphStore.builder()
                .entity("a", "NEURON")
                .entity("b", "SYNAPSE")
                .entity("c", "NEURON")
                .relation("a", "b", "connects_to", 0.9)
                .relation("b", "c", "triggers", 0.5)
                .build();

        assertThat(g.entityCount()).isEqualTo(3);
        assertThat(g.relationCount()).isEqualTo(2);
        assertThat(g.getRelations("a")).hasSize(1);
        assertThat(g.getRelations("b")).hasSize(1);

        var path = g.findPath("a", "c", 5);
        assertThat(path).hasSize(3);
    }

    // ─── Helpers ───

    private void buildLinearChain(String... ids) {
        for (String id : ids) {
            graph.addEntity(new KnowledgeGraphStore.Entity(id, "NEURON"));
        }
        for (int i = 0; i < ids.length - 1; i++) {
            graph.addRelation(new KnowledgeGraphStore.Relation(
                    ids[i], ids[i + 1], "next", 1.0));
        }
    }
}
