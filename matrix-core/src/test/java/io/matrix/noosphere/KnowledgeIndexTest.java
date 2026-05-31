package io.matrix.noosphere;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexTest {

    private NoosphereRegistry registry;
    private KnowledgeIndex index;

    @BeforeEach
    void setup() {
        registry = new NoosphereRegistry();
        index = new KnowledgeIndex(registry);

        registry.publish(FnlPackage.builder()
                .name("Edge Detector").type("VISION").authorInstanceId("i1")
                .accuracy(0.95).description("Detects edges in images")
                .tags("edge", "vision", "detection").certified(true).build());

        registry.publish(FnlPackage.builder()
                .name("Text Classifier").type("TEXT").authorInstanceId("i2")
                .accuracy(0.88).description("Classifies text sentiment")
                .tags("text", "sentiment", "nlp").build());

        registry.publish(FnlPackage.builder()
                .name("Motion Tracker").type("VISION").authorInstanceId("i3")
                .accuracy(0.72).description("Tracks moving objects")
                .tags("motion", "vision", "tracking").build());

        index.reindex();
    }

    @Test
    void shouldFindByKeyword() {
        var results = index.search("edge");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).fnl().name()).isEqualTo("Edge Detector");
    }

    @Test
    void shouldRankByRelevance() {
        var results = index.search("vision");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).relevance()).isGreaterThanOrEqualTo(results.get(1).relevance());
    }

    @Test
    void shouldFindTopN() {
        var results = index.findTop("vision", 1);

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldReturnEmptyForUnknownQuery() {
        var results = index.search("nonexistent");

        assertThat(results).isEmpty();
    }

    @Test
    void shouldIndexMultipleKeywords() {
        var results = index.search("text sentiment");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).fnl().name()).isEqualTo("Text Classifier");
    }

    @Test
    void shouldBoostCertifiedResults() {
        var results = index.search("vision");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).fnl().certified()).isTrue();
    }

    @Test
    void shouldTrackIndexedCount() {
        assertThat(index.indexedCount()).isGreaterThan(0);
    }
}
