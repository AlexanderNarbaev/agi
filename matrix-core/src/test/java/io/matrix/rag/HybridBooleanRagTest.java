package io.matrix.rag;

import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridBooleanRagTest {

    private BooleanIndex denseIndex;
    private KnowledgeIndex knowledgeIndex;
    private HybridBooleanRag rag;

    @BeforeEach
    void setup() {
        denseIndex = BooleanIndex.builder().dimensions(64).build();
        denseIndex.add("concept:neural",   new long[]{0b00001111L});
        denseIndex.add("concept:vision",   new long[]{0b11110000L});
        denseIndex.add("concept:hybrid",   new long[]{0b00111100L});
        denseIndex.add("concept:abstract", new long[]{0b11000011L});

        knowledgeIndex = null;

        rag = HybridBooleanRag.builder()
                .index(denseIndex)
                .topK(3)
                .build();
    }

    // ── Construction ──

    @Test
    void shouldBuildWithDefaults() {
        var r = HybridBooleanRag.builder().index(denseIndex).build();
        assertThat(r).isNotNull();
    }

    @Test
    void shouldBuildWithKnowledgeIndex() {
        NoosphereRegistry registry = new NoosphereRegistry();
        KnowledgeIndex ki = new KnowledgeIndex(registry);
        ki.reindex();

        var r = HybridBooleanRag.builder()
                .index(denseIndex)
                .knowledgeIndex(ki)
                .topK(3)
                .build();
        assertThat(r).isNotNull();
    }

    // ── Query ──

    @Test
    void shouldReturnHybridResult() {
        long[] query = {0b00001111L};

        HybridBooleanRag.HybridRagResult result = rag.query(query);

        assertThat(result.originalQuery()).isEqualTo(query);
        assertThat(result.allResults()).isNotEmpty();
        assertThat(result.expandedVector()).isNotNull();
    }

    @Test
    void shouldExpandVectorWithKnowledge() {
        long[] query = {0b00001111L};

        HybridBooleanRag.HybridRagResult result = rag.query(query);

        assertThat(result.expandedVector().length).isGreaterThan(1);
        assertThat(result.expandedVector()[0]).isEqualTo(query);
    }

    @Test
    void shouldClassifyStrongAndBorderlineMatches() {
        long[] query = {0b00001111L};

        HybridBooleanRag.HybridRagResult result = rag.query(query);

        assertThat(result.strongMatches()).isNotNull();
        assertThat(result.borderlineMatches()).isNotNull();
    }

    @Test
    void shouldNotRefuseWhenStrongMatchesExist() {
        long[] query = {0b00001111L};

        HybridBooleanRag.HybridRagResult result = rag.query(query);

        // With exact match (concept:neural), should have sufficient context
        assertThat(result.hasSufficientContext()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNoKnowledge() {
        var emptyStore = BooleanIndex.builder().dimensions(64).build();
        var emptyRag = HybridBooleanRag.builder().index(emptyStore).topK(3).build();

        long[] query = {0xFFL};
        HybridBooleanRag.HybridRagResult result = emptyRag.query(query);

        assertThat(result.allResults()).isEmpty();
        assertThat(result.expandedVector().length).isEqualTo(1);
        assertThat(result.shouldRefuse()).isTrue();
    }

    // ── Adaptive Context ──

    @Test
    void shouldUseAdaptiveContextByDefault() {
        var r = HybridBooleanRag.builder().index(denseIndex).build();
        assertThat(r).isNotNull();
    }

    @Test
    void shouldSupportCustomKneeSensitivity() {
        var r = HybridBooleanRag.builder()
                .index(denseIndex)
                .adaptiveContext(true)
                .kneeSensitivity(0.7)
                .build();

        long[] query = {0b00001111L};
        HybridBooleanRag.HybridRagResult result = r.query(query);

        assertThat(result).isNotNull();
        assertThat(result.allResults()).isNotEmpty();
    }

    @Test
    void shouldSupportCustomThresholds() {
        var r = HybridBooleanRag.builder()
                .index(denseIndex)
                .strongThreshold(0.5)
                .borderlineThreshold(0.4)
                .build();

        long[] query = {0b00001111L};
        HybridBooleanRag.HybridRagResult result = r.query(query);

        assertThat(result).isNotNull();
    }

    // ── Edge Cases ──

    @Test
    void shouldHandleSingleElementIndex() {
        var single = BooleanIndex.builder().dimensions(64).build();
        single.add("only", new long[]{0xABCDEL});

        var r = HybridBooleanRag.builder().index(single).topK(5).build();
        long[] query = {0x00L};
        HybridBooleanRag.HybridRagResult result = r.query(query);

        assertThat(result.allResults()).hasSize(1);
    }

    @Test
    void shouldHandle128BitVectors() {
        var idx128 = BooleanIndex.builder().dimensions(128).build();
        idx128.add("wide1", new long[]{0xAAAAL, 0xBBBBL});
        idx128.add("wide2", new long[]{0xCCCCL, 0xDDDDL});

        var rag128 = HybridBooleanRag.builder().index(idx128).topK(2).build();
        long[] query = {0xAAAAL, 0xBBBBL};

        HybridBooleanRag.HybridRagResult result = rag128.query(query);

        assertThat(result.expandedVector().length).isGreaterThan(1);
    }

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
}
