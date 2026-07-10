package io.matrix.rag;

import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanRagTest {

    private BooleanIndex knowledgeStore;
    private BooleanRag rag;

    @BeforeEach
    void setup() {
        knowledgeStore = BooleanIndex.builder().dimensions(64).build();

        // Seed knowledge base
        knowledgeStore.add("concept:neural",   new long[]{0b00001111L});
        knowledgeStore.add("concept:vision",   new long[]{0b11110000L});
        knowledgeStore.add("concept:hybrid",   new long[]{0b00111100L});
        knowledgeStore.add("concept:abstract", new long[]{0b11000011L});

        rag = BooleanRag.builder()
                .index(knowledgeStore)
                .topK(2)
                .build();
    }

    // --- Construction ---

    @Test
    void shouldBuildWithDefaults() {
        var r = BooleanRag.builder().index(knowledgeStore).build();
        assertThat(r.topK()).isEqualTo(3);
    }

    @Test
    void shouldBuildWithCustomTopK() {
        var r = BooleanRag.builder().index(knowledgeStore).topK(5).build();
        assertThat(r.topK()).isEqualTo(5);
    }

    @Test
    void shouldRejectNullIndex() {
        assertThatThrownBy(() -> BooleanRag.builder().index(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectInvalidTopK() {
        assertThatThrownBy(() -> BooleanRag.builder().index(knowledgeStore).topK(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Query Expansion ---

    @Test
    void shouldExpandQueryWithTopKKnowledge() {
        long[] query = {0b00000001L};

        BooleanRag.RagResult result = rag.query(query);

        assertThat(result.originalQuery()).isEqualTo(query);
        assertThat(result.knowledgeHits()).isNotEmpty();
        assertThat(result.knowledgeHits().size()).isLessThanOrEqualTo(2);
    }

    @Test
    void shouldConcatenateQueryAndKnowledgeVectors() {
        long[] query = {0b00000001L};

        BooleanRag.RagResult result = rag.query(query);

        // expanded = query_bits ++ top-K knowledge vectors
        // 64-bit query + 2 * 64-bit knowledge = 192-bit expanded
        assertThat(result.expandedVector()).isNotNull();
        assertThat(result.expandedVector().length).isEqualTo(3); // 1 query + 2 knowledge
    }

    @Test
    void shouldIncludeKnowledgeHitsOrderedByDistance() {
        long[] query = {0b00001111L}; // closest to concept:neural

        BooleanRag.RagResult result = rag.query(query);

        assertThat(result.knowledgeHits()).hasSize(2);
        // First hit should be closest
        assertThat(result.knowledgeHits().get(0).distance())
                .isLessThanOrEqualTo(result.knowledgeHits().get(1).distance());
    }

    @Test
    void shouldReturnOriginalQueryWhenNoKnowledge() {
        var emptyStore = BooleanIndex.builder().dimensions(64).build();
        var emptyRag = BooleanRag.builder().index(emptyStore).topK(3).build();

        long[] query = {0xFFL};
        BooleanRag.RagResult result = emptyRag.query(query);

        assertThat(result.knowledgeHits()).isEmpty();
        assertThat(result.expandedVector().length).isEqualTo(1);
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }

    // --- Integration with KnowledgeIndex ---

    @Test
    void shouldIntegrateWithKnowledgeIndex() {
        NoosphereRegistry registry = new NoosphereRegistry();
        KnowledgeIndex ki = new KnowledgeIndex(registry);

        registry.publish(FnlPackage.builder()
                .name("Neural Net").type("VISION").authorInstanceId("i1")
                .accuracy(0.95).description("Neural network for images")
                .tags("neural", "vision").certified(true).build());

        registry.publish(FnlPackage.builder()
                .name("Text Processor").type("NLP").authorInstanceId("i2")
                .accuracy(0.88).description("Text processing pipeline")
                .tags("text", "nlp").build());

        ki.reindex();

        BooleanIndex store = BooleanIndex.builder().dimensions(64).build();
        store.add("fnl:neural", new long[]{0b11110000L});
        store.add("fnl:text",   new long[]{0b00001111L});

        var integrated = BooleanRag.builder()
                .index(store)
                .knowledgeIndex(ki)
                .topK(1)
                .build();

        long[] query = {0b11111100L};
        BooleanRag.RagResult result = integrated.query(query);

        assertThat(result.knowledgeHits()).isNotEmpty();
    }

    // --- 128-bit Vectors ---

    @Test
    void shouldHandle128BitVectors() {
        var idx128 = BooleanIndex.builder().dimensions(128).build();
        idx128.add("wide1", new long[]{0xAAAAL, 0xBBBBL});
        idx128.add("wide2", new long[]{0xCCCCL, 0xDDDDL});

        var rag128 = BooleanRag.builder().index(idx128).topK(2).build();

        long[] query = {0xAAAAL, 0xBBBBL};
        BooleanRag.RagResult result = rag128.query(query);

        assertThat(result.expandedVector().length).isEqualTo(3);
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }

    // --- Thread Safety ---

    @Test
    void shouldHandleConcurrentQueries() throws Exception {
        int threadCount = 4;
        var latch = new java.util.concurrent.CountDownLatch(threadCount);
        var failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        long[] query = new long[]{(long) (id * 100 + i)};
                        rag.query(query);
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        assertThat(failed.get()).isFalse();
    }

    // --- Edge Cases ---

    @Test
    void shouldHandleSingleElementIndex() {
        var single = BooleanIndex.builder().dimensions(64).build();
        single.add("only", new long[]{0xABCDEL});

        var r = BooleanRag.builder().index(single).topK(5).build();
        long[] query = {0x00L};
        BooleanRag.RagResult result = r.query(query);

        assertThat(result.knowledgeHits()).hasSize(1);
        assertThat(result.expandedVector().length).isEqualTo(2); // 1 query + 1 knowledge
    }

    @Test
    void shouldPreserveQueryInExpandedVector() {
        long[] query = {0xDEADBEEFL};
        BooleanRag.RagResult result = rag.query(query);

        // First element of expanded must be the original query
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }
}
