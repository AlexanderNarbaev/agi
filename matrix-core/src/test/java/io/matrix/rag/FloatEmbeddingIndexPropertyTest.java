package io.matrix.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for FloatEmbeddingIndex — search correctness, determinism,
 * and embedding vector invariants.
 *
 * <p>Ref: H-007, CONSTITUTION VII.1, SYNC-14
 */
class FloatEmbeddingIndexPropertyTest {

    private static final int DIM = 64;

    // ── Search correctness ──

    @Test
    void shouldReturnTopKMatches() {
        var index = FloatEmbeddingIndex.builder().dimensions(DIM).build();
        for (int i = 0; i < 20; i++) {
            index.add("doc_" + i, FloatEmbeddingIndex.fromValue(i, DIM));
        }

        // Query at value 10 — closest should be doc_10, doc_9, doc_11, doc_8, doc_12
        var query = FloatEmbeddingIndex.fromValue(10, DIM);
        var results = index.search(query, 5);

        assertThat(results).hasSize(5);
        assertThat(results.get(0).id()).isEqualTo("doc_10");  // exact match
        assertThat(results.get(0).score())
                .as("exact match should have highest cosine similarity")
                .isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void shouldReturnEmptyListWhenIndexEmpty() {
        var index = FloatEmbeddingIndex.builder().dimensions(DIM).build();
        var query = FloatEmbeddingIndex.fromValue(42, DIM);
        assertThat(index.search(query, 5)).isEmpty();
    }

    // ── Determinism ──

    @Test
    void shouldProduceSameVectorForSameText() {
        var v1 = EmbeddingVector.fromText("hello world", DIM);
        var v2 = EmbeddingVector.fromText("hello world", DIM);
        assertThat(v1.values()).containsExactly(v2.values());
    }

    @Test
    void shouldProduceSameVectorForSameValue() {
        var v1 = EmbeddingVector.fromValue(42, DIM);
        var v2 = EmbeddingVector.fromValue(42, DIM);
        assertThat(v1.values()).containsExactly(v2.values());
    }

    // ── Vector invariants ──

    @Test
    void cosineShouldBeSymmetric() {
        var a = EmbeddingVector.fromValue(10, DIM);
        var b = EmbeddingVector.fromValue(20, DIM);
        assertThat(a.cosine(b)).isEqualTo(b.cosine(a));
    }

    @Test
    void cosineWithSelfShouldBeOne() {
        var v = EmbeddingVector.fromText("some text for testing", DIM);
        assertThat(v.cosine(v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void hammingShouldBeNonNegative() {
        var a = EmbeddingVector.fromValue(0, DIM);
        var b = EmbeddingVector.fromValue(10, DIM);
        assertThat(a.hamming(b)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hammingWithSelfShouldBeZero() {
        var v = EmbeddingVector.fromText("deterministic test", DIM);
        assertThat(v.hamming(v)).isEqualTo(0);
    }

    @Test
    void normShouldBeCloseToOne() {
        var v = EmbeddingVector.fromValue(99, DIM);
        assertThat(v.norm()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void searchShouldBeDeterministic() {
        var index = FloatEmbeddingIndex.builder().dimensions(DIM).build();
        index.add("a", FloatEmbeddingIndex.fromValue(1, DIM));
        index.add("b", FloatEmbeddingIndex.fromValue(2, DIM));
        var query = FloatEmbeddingIndex.fromValue(1, DIM);

        var r1 = index.search(query, 2);
        var r2 = index.search(query, 2);

        assertThat(r1).hasSize(r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertThat(r1.get(i).id()).isEqualTo(r2.get(i).id());
            assertThat(r1.get(i).score()).isEqualTo(r2.get(i).score());
        }
    }

    // ── EmbeddingVector fromText ──

    @Test
    void differentTextShouldProduceDifferentVectors() {
        var a = EmbeddingVector.fromText("hello world", DIM);
        var b = EmbeddingVector.fromText("goodbye universe", DIM);
        // Extremely unlikely to be identical for different texts
        assertThat(a.cosine(b)).isLessThan(1.0);
    }

    @Test
    void nullTextShouldNotThrow() {
        var v = EmbeddingVector.fromText(null, DIM);
        assertThat(v.dimensions()).isEqualTo(DIM);
    }
}
