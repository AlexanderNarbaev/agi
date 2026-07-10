package io.matrix.integration;

import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Integration tests for Boolean RAG system.
 *
 * <p>Tests BooleanIndex with 1000 vectors, Hamming distance search accuracy,
 * Top-K retrieval, and query expansion.
 */
class BooleanRagIntegrationTest {

    @Test
    void indexWith1000Vectors() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        var rng = new Random(42);

        for (int i = 0; i < 1000; i++) {
            long[] vec = new long[]{rng.nextLong()};
            index.add("vec-" + i, vec);
        }

        assertThat(index.size()).isEqualTo(1000);
    }

    @Test
    void hammingDistanceSearchAccuracy() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        // Add known vectors with controlled distances
        long[] base = new long[]{0x000000000000000FL}; // bits 0-3 set
        long[] close = new long[]{0x000000000000001FL}; // bits 0-4 set (distance=1)
        long[] medium = new long[]{0x0000000000000FFL}; // bits 0-7 set (distance=4)
        long[] far = new long[]{0xFFFFFFFFFFFFFF00L}; // bits 8-63 set (distance=60)

        index.add("base", base);
        index.add("close", close);
        index.add("medium", medium);
        index.add("far", far);

        // Search for nearest to base
        List<BooleanIndex.SearchResult> results = index.search(base, 4);
        assertThat(results).hasSize(4);

        // Results should be sorted by distance
        assertThat(results.get(0).id()).isEqualTo("base");
        assertThat(results.get(0).distance()).isEqualTo(0);

        assertThat(results.get(1).id()).isEqualTo("close");
        assertThat(results.get(1).distance()).isEqualTo(1);

        assertThat(results.get(2).id()).isEqualTo("medium");
        assertThat(results.get(2).distance()).isEqualTo(4);

        assertThat(results.get(3).id()).isEqualTo("far");
        assertThat(results.get(3).distance()).isEqualTo(60);
    }

    @Test
    void topKRetrieval() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        var rng = new Random(42);

        long[] query = new long[]{0xAAAAAAAAAAAAAAAAL}; // alternating bits

        // Add 100 random vectors
        for (int i = 0; i < 100; i++) {
            index.add("v-" + i, new long[]{rng.nextLong()});
        }

        // Top-5 search
        List<BooleanIndex.SearchResult> top5 = index.search(query, 5);
        assertThat(top5).hasSize(5);

        // Results should be sorted ascending by distance
        for (int i = 1; i < top5.size(); i++) {
            assertThat(top5.get(i).distance())
                    .isGreaterThanOrEqualTo(top5.get(i - 1).distance());
        }

        // Top-10 search should return 10 results
        List<BooleanIndex.SearchResult> top10 = index.search(query, 10);
        assertThat(top10).hasSize(10);
    }

    @Test
    void topKWithFewerResults() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("a", new long[]{0L});
        index.add("b", new long[]{1L});

        // Request top-10 but only 2 exist
        List<BooleanIndex.SearchResult> results = index.search(new long[]{0L}, 10);
        assertThat(results).hasSize(2);
    }

    @Test
    void queryExpansion() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        var rng = new Random(42);

        // Add vectors to index
        for (int i = 0; i < 50; i++) {
            index.add("knowledge-" + i, new long[]{rng.nextLong()});
        }

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(3)
                .build();

        long[] query = new long[]{0x1234567890ABCDEFL};
        BooleanRag.RagResult result = rag.query(query);

        assertThat(result.originalQuery()).isEqualTo(query);
        assertThat(result.knowledgeHits()).hasSize(3);
        assertThat(result.expandedVector().length).isEqualTo(4); // query + 3 knowledge vectors
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }

    @Test
    void queryExpansionWithEmptyIndex() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(5)
                .build();

        long[] query = new long[]{0xFL};
        BooleanRag.RagResult result = rag.query(query);

        assertThat(result.knowledgeHits()).isEmpty();
        assertThat(result.expandedVector().length).isEqualTo(1); // only query
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }

    @Test
    void indexSerializationRoundtrip() throws Exception {
        BooleanIndex original = BooleanIndex.builder().dimensions(64).build();
        var rng = new Random(42);

        for (int i = 0; i < 100; i++) {
            original.add("item-" + i, new long[]{rng.nextLong()});
        }

        // Serialize
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        original.serialize(bos);
        byte[] data = bos.toByteArray();

        // Deserialize
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
        BooleanIndex restored = BooleanIndex.deserialize(bis);

        assertThat(restored.size()).isEqualTo(100);
        assertThat(restored.dimensions()).isEqualTo(64);

        // Verify search results match
        long[] query = new long[]{0xCAFEBABEDEADBEEFL};
        List<BooleanIndex.SearchResult> origResults = original.search(query, 10);
        List<BooleanIndex.SearchResult> restoredResults = restored.search(query, 10);

        assertThat(origResults).hasSameSizeAs(restoredResults);
        for (int i = 0; i < origResults.size(); i++) {
            assertThat(origResults.get(i).id()).isEqualTo(restoredResults.get(i).id());
            assertThat(origResults.get(i).distance()).isEqualTo(restoredResults.get(i).distance());
        }
    }

    @Test
    void removeReducesSize() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("a", new long[]{1L});
        index.add("b", new long[]{2L});
        index.add("c", new long[]{3L});

        assertThat(index.size()).isEqualTo(3);
        assertThat(index.remove("b")).isTrue();
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.contains("b")).isFalse();
    }
}
