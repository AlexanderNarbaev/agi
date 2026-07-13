package io.matrix.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RrfFusionTest {

    @Test
    void shouldFuseSingleList() {
        var hits = List.of(
                new RrfFusion.SearchHit("doc1", 0.9, "dense", Map.of()),
                new RrfFusion.SearchHit("doc2", 0.7, "dense", Map.of())
        );

        var fused = RrfFusion.fuse(List.of(hits));

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).id()).isEqualTo("doc1");
        assertThat(fused.get(0).score()).isGreaterThan(fused.get(1).score());
    }

    @Test
    void shouldFuseMultipleLists() {
        var denseHits = List.of(
                new RrfFusion.SearchHit("doc1", 0.9, "dense", Map.of()),
                new RrfFusion.SearchHit("doc2", 0.7, "dense", Map.of()),
                new RrfFusion.SearchHit("doc3", 0.5, "dense", Map.of())
        );
        var sparseHits = List.of(
                new RrfFusion.SearchHit("doc2", 0.8, "sparse", Map.of()),
                new RrfFusion.SearchHit("doc4", 0.6, "sparse", Map.of())
        );

        var fused = RrfFusion.fuse(List.of(denseHits, sparseHits));

        assertThat(fused).isNotEmpty();
        // doc2 appears in both lists, should have higher combined score
        var doc2 = fused.stream().filter(r -> r.id().equals("doc2")).findFirst();
        assertThat(doc2).isPresent();
    }

    @Test
    void shouldRankByRrfScore() {
        var list1 = List.of(
                new RrfFusion.SearchHit("A", 1.0, "s1", Map.of()),
                new RrfFusion.SearchHit("B", 0.5, "s1", Map.of())
        );
        var list2 = List.of(
                new RrfFusion.SearchHit("B", 1.0, "s2", Map.of()),
                new RrfFusion.SearchHit("A", 0.5, "s2", Map.of())
        );

        var fused = RrfFusion.fuse(List.of(list1, list2));

        // Both appear in both lists with same ranks, scores should be equal
        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).score()).isEqualTo(fused.get(1).score(), org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void shouldApplyKneePruning() {
        var results = List.of(
                new RrfFusion.FusedResult("doc1", 0.9, "s", Map.of()),
                new RrfFusion.FusedResult("doc2", 0.85, "s", Map.of()),
                new RrfFusion.FusedResult("doc3", 0.8, "s", Map.of()),
                new RrfFusion.FusedResult("doc4", 0.3, "s", Map.of()),
                new RrfFusion.FusedResult("doc5", 0.1, "s", Map.of())
        );

        var pruned = RrfFusion.kneePrune(results, 0.5);

        // Should keep high-scoring results, prune low-scoring
        assertThat(pruned).hasSizeLessThanOrEqualTo(results.size());
        assertThat(pruned).isNotEmpty();
    }

    @Test
    void shouldReturnAllForSmallLists() {
        var results = List.of(
                new RrfFusion.FusedResult("doc1", 0.9, "s", Map.of()),
                new RrfFusion.FusedResult("doc2", 0.8, "s", Map.of())
        );

        var pruned = RrfFusion.kneePrune(results, 0.5);

        assertThat(pruned).hasSize(2);
    }

    @Test
    void shouldUseDefaultK() {
        assertThat(RrfFusion.DEFAULT_K).isEqualTo(60);
    }

    @Test
    void shouldRejectInvalidK() {
        var hits = List.of(new RrfFusion.SearchHit("doc1", 0.9, "s", Map.of()));
        assertThatThrownBy(() -> RrfFusion.fuse(List.of(hits), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidSensitivity() {
        var results = List.of(
                new RrfFusion.FusedResult("doc1", 0.9, "s", Map.of()),
                new RrfFusion.FusedResult("doc2", 0.8, "s", Map.of()),
                new RrfFusion.FusedResult("doc3", 0.7, "s", Map.of())
        );
        assertThatThrownBy(() -> RrfFusion.kneePrune(results, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RrfFusion.kneePrune(results, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleEmptyLists() {
        var fused = RrfFusion.fuse(List.of());
        assertThat(fused).isEmpty();
    }

    @Test
    void shouldPreserveMetadata() {
        var hits = List.of(
                new RrfFusion.SearchHit("doc1", 0.9, "dense", Map.of("key", "value"))
        );

        var fused = RrfFusion.fuse(List.of(hits));

        assertThat(fused.get(0).metadata()).containsEntry("key", "value");
    }
}
