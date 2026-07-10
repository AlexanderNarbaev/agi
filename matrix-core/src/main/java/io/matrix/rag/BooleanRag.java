package io.matrix.rag;

import io.matrix.noosphere.KnowledgeIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Boolean RAG (Retrieval-Augmented Generation) system.
 *
 * <p>Expands a boolean query vector with Top-K knowledge from a {@link BooleanIndex},
 * producing an expanded vector that concatenates the original query with retrieved
 * knowledge vectors. Optionally integrates with {@link KnowledgeIndex} for hybrid
 * keyword + boolean retrieval.
 *
 * <p>Thread-safe: delegates all locking to the underlying {@link BooleanIndex}.
 *
 * <p>Ref: Phase 2 — Boolean RAG
 */
public final class BooleanRag {

    private final BooleanIndex index;
    private final KnowledgeIndex knowledgeIndex;
    private final int topK;

    private BooleanRag(BooleanIndex index, KnowledgeIndex knowledgeIndex, int topK) {
        this.index = Objects.requireNonNull(index, "index");
        this.knowledgeIndex = knowledgeIndex;
        this.topK = topK;
    }

    /**
     * Returns a new builder for {@code BooleanRag}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Queries the RAG system, expanding the input vector with Top-K knowledge.
     *
     * <p>The expanded vector is: [query, knowledge_1, knowledge_2, ..., knowledge_K].
     * If fewer than {@code topK} results exist, the expanded vector is shorter.
     * If no knowledge exists, the expanded vector contains only the original query.
     *
     * @param query boolean vector (must match index dimensions)
     * @return RAG result with original query, knowledge hits, and expanded vector
     */
    public RagResult query(long[] query) {
        Objects.requireNonNull(query, "query");

        List<BooleanIndex.SearchResult> hits = index.search(query, topK);

        List<long[]> knowledgeVectors = new ArrayList<>(hits.size());
        for (var hit : hits) {
            long[] vec = index.get(hit.id());
            if (vec != null) {
                knowledgeVectors.add(vec);
            }
        }

        long[][] expanded = new long[1 + knowledgeVectors.size()][];
        expanded[0] = query;
        for (int i = 0; i < knowledgeVectors.size(); i++) {
            expanded[i + 1] = knowledgeVectors.get(i);
        }

        return new RagResult(query, hits, expanded);
    }

    /**
     * Returns the configured Top-K value.
     */
    public int topK() {
        return topK;
    }

    // --- Records ---

    /**
     * Result of a RAG query containing the original query, knowledge hits,
     * and the expanded boolean vector.
     */
    public record RagResult(
            long[] originalQuery,
            List<BooleanIndex.SearchResult> knowledgeHits,
            long[][] expandedVector
    ) {}

    // --- Builder ---

    /**
     * Builder for {@link BooleanRag}.
     */
    public static final class Builder {
        private BooleanIndex index;
        private KnowledgeIndex knowledgeIndex;
        private int topK = 3;

        /**
         * Sets the boolean index to retrieve knowledge from. Required.
         */
        public Builder index(BooleanIndex index) {
            this.index = Objects.requireNonNull(index, "index");
            return this;
        }

        /**
         * Sets an optional {@link KnowledgeIndex} for hybrid retrieval.
         */
        public Builder knowledgeIndex(KnowledgeIndex knowledgeIndex) {
            this.knowledgeIndex = knowledgeIndex;
            return this;
        }

        /**
         * Sets the number of knowledge vectors to retrieve. Must be &gt;= 1.
         */
        public Builder topK(int topK) {
            if (topK < 1) {
                throw new IllegalArgumentException("topK must be >= 1, got: " + topK);
            }
            this.topK = topK;
            return this;
        }

        /**
         * Builds the {@link BooleanRag}.
         */
        public BooleanRag build() {
            return new BooleanRag(index, knowledgeIndex, topK);
        }
    }
}
