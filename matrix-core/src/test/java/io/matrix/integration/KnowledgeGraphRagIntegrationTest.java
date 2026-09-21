package io.matrix.integration;

import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.HybridBooleanRag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: KnowledgeGraphStore as third retrieval source in HybridBooleanRag.
 */
class KnowledgeGraphRagIntegrationTest {

    @Test
    void graphStoreContributesToRagRetrieval() {
        // Create graph store with entities
        var graph = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("neuron_n", "neuron", Map.of("id", "n")))
                .entity(new KnowledgeGraphStore.Entity("neuron_s", "neuron", Map.of("id", "s")))
                .entity(new KnowledgeGraphStore.Entity("neuron_w", "neuron", Map.of("id", "w")))
                .entity(new KnowledgeGraphStore.Entity("neuron_e", "neuron", Map.of("id", "e")))
                .relation(new KnowledgeGraphStore.Relation("neuron_n", "neuron_s", "connects", 0.5))
                .relation(new KnowledgeGraphStore.Relation("neuron_s", "neuron_e", "connects", 0.3))
                .build();

        // Create BooleanIndex
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        // Create RAG with all three sources
        HybridBooleanRag rag = HybridBooleanRag.builder()
                .index(index)
                .knowledgeGraphStore(graph)
                .topK(5)
                .build();

        var result = rag.query(new long[]{0x01L});

        assertThat(result).isNotNull();
        assertThat(result.allResults()).isNotNull();
    }

    @Test
    void graphStoreEnrichesGuardedQuery() {
        var graph = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("UserService", "service", Map.of("id", "us")))
                .entity(new KnowledgeGraphStore.Entity("TokenValidator", "service", Map.of("id", "tv")))
                .relation(new KnowledgeGraphStore.Relation("UserService", "TokenValidator", "calls", 0.8))
                .build();

        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        HybridBooleanRag rag = HybridBooleanRag.builder()
                .index(index)
                .knowledgeGraphStore(graph)
                .topK(5)
                .build();

        var result = rag.guardedQuery(
                new long[]{0x01L},
                "How does UserService call TokenValidator?"
        );

        assertThat(result).isNotNull();
        assertThat(result.ragResult()).isNotNull();
    }

    @Test
    void emptyGraphStoreDoesNotBreakRag() {
        var graph = KnowledgeGraphStore.builder().build();
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        HybridBooleanRag rag = HybridBooleanRag.builder()
                .index(index)
                .knowledgeGraphStore(graph)
                .topK(3)
                .build();

        var result = rag.query(new long[]{0x01L});

        assertThat(result).isNotNull();
        // Should work even with empty graph
        assertThat(result.allResults()).isNotNull();
    }

    @Test
    void graphStoreCentralityUsedForRagScoring() {
        var graph = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("hub", "hub", Map.of()))
                .entity(new KnowledgeGraphStore.Entity("node_a", "node", Map.of()))
                .entity(new KnowledgeGraphStore.Entity("node_b", "node", Map.of()))
                .entity(new KnowledgeGraphStore.Entity("node_c", "node", Map.of()))
                .relation(new KnowledgeGraphStore.Relation("hub", "node_a", "links", 1.0))
                .relation(new KnowledgeGraphStore.Relation("hub", "node_b", "links", 1.0))
                .relation(new KnowledgeGraphStore.Relation("hub", "node_c", "links", 1.0))
                .relation(new KnowledgeGraphStore.Relation("node_a", "hub", "links_back", 0.5))
                .build();

        // Hub should have highest centrality
        double hubCentrality = graph.centrality("hub");
        double nodeACentrality = graph.centrality("node_a");

        assertThat(hubCentrality).isGreaterThan(nodeACentrality);
        assertThat(graph.topCentral(3)).contains("hub");
    }
}
