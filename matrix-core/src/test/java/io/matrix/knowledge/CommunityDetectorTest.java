package io.matrix.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CommunityDetector}.
 */
class CommunityDetectorTest {

    @Test
    void emptyGraphShouldReturnNoCommunities() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).isEmpty();
        assertThat(result.nodeToCommunity()).isEmpty();
    }

    @Test
    void singleNodeShouldFormSingleCommunity() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        graph.addEntity(new KnowledgeGraphStore.Entity("A", "concept", Map.of()));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).hasSize(1);
        assertThat(result.communities().get(0).members()).containsExactly("A");
    }

    @Test
    void twoConnectedNodesShouldBeSameCommunity() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        graph.addEntity(new KnowledgeGraphStore.Entity("A", "concept", Map.of()));
        graph.addEntity(new KnowledgeGraphStore.Entity("B", "concept", Map.of()));
        graph.addRelation(new KnowledgeGraphStore.Relation("A", "B", "links_to", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).hasSize(1);
        assertThat(result.nodeToCommunity().get("A"))
                .isEqualTo(result.nodeToCommunity().get("B"));
    }

    @Test
    void disconnectedNodesShouldBeSeparateCommunities() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        graph.addEntity(new KnowledgeGraphStore.Entity("A", "concept", Map.of()));
        graph.addEntity(new KnowledgeGraphStore.Entity("B", "concept", Map.of()));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).hasSize(2);
    }

    @Test
    void clusterShouldHaveInternalAndExternalEdges() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        // Triangle: A-B, B-C, C-A (single community)
        addEntities(graph, "A", "B", "C");
        graph.addRelation(new KnowledgeGraphStore.Relation("A", "B", "links", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("B", "C", "links", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "A", "links", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).hasSize(1);
        CommunityDetector.Community c = result.communities().get(0);
        assertThat(c.internalEdges()).isEqualTo(3);
        assertThat(c.externalEdges()).isEqualTo(0);
        assertThat(c.modularity(3)).isPositive();
    }

    @Test
    void twoClustersShouldBeDetected() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        // Cluster 1: A-B, B-C (A-B-C)
        addEntities(graph, "A", "B", "C", "X", "Y", "Z");
        graph.addRelation(new KnowledgeGraphStore.Relation("A", "B", "c1", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("B", "C", "c1", 1.0));
        // Cluster 2: X-Y, Y-Z (X-Y-Z)
        graph.addRelation(new KnowledgeGraphStore.Relation("X", "Y", "c2", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("Y", "Z", "c2", 1.0));
        // Bridge: C-X (weak connection between clusters)
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "X", "bridge", 0.1));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph, 20);

        // Should find at least 2 communities
        assertThat(result.communities().size()).isGreaterThanOrEqualTo(2);
        assertThat(result.modularity()).isPositive();
    }

    @Test
    void starTopologyShouldFormOneCommunity() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        addEntities(graph, "C", "P1", "P2", "P3", "P4");
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "P1", "r", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "P2", "r", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "P3", "r", 1.0));
        graph.addRelation(new KnowledgeGraphStore.Relation("C", "P4", "r", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.communities()).hasSize(1);
    }

    @Test
    void communityShouldHaveCorrectSize() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        addEntities(graph, "N1", "N2");
        graph.addRelation(new KnowledgeGraphStore.Relation("N1", "N2", "r", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);
        assertThat(result.communities().get(0).size()).isEqualTo(2);
    }

    @Test
    void nodeToCommunityMappingShouldBeComplete() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        addEntities(graph, "A", "B", "C");
        graph.addRelation(new KnowledgeGraphStore.Relation("A", "B", "r", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.nodeToCommunity()).containsKeys("A", "B", "C");
    }

    @Test
    void modularityShouldBeBetweenMinusOneAndOne() {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        addEntities(graph, "X", "Y");
        graph.addRelation(new KnowledgeGraphStore.Relation("X", "Y", "r", 1.0));

        CommunityDetector.DetectionResult result = CommunityDetector.detect(graph);

        assertThat(result.modularity()).isBetween(-1.0, 1.0);
    }

    private static void addEntities(KnowledgeGraphStore graph, String... ids) {
        for (String id : ids) {
            graph.addEntity(new KnowledgeGraphStore.Entity(id, "test", Map.of()));
        }
    }
}
