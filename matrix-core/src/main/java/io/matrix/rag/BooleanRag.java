package io.matrix.rag;

import io.matrix.noosphere.KnowledgeIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Boolean RAG (Retrieval-Augmented Generation) system.
 *
 * <p>Expands a boolean query vector with Top-K knowledge from a {@link BooleanIndex},
 * producing an expanded vector that concatenates the original query with retrieved
 * knowledge vectors. Optionally integrates with {@link KnowledgeIndex} for hybrid
 * keyword + boolean retrieval.
 *
 * <p>When a {@link QueryExpander} is configured, the system:
 * <ol>
 *   <li>Expands the query into multiple variants</li>
 *   <li>Searches with each variant</li>
 *   <li>Merges results using RRF fusion</li>
 *   <li>Applies knee-point pruning for adaptive context</li>
 * </ol>
 *
 * <p>Thread-safe: delegates all locking to the underlying {@link BooleanIndex}.
 *
 * <p>Ref: Phase 2 — Boolean RAG
 */
public final class BooleanRag {

    private final BooleanIndex index;
    private final KnowledgeIndex knowledgeIndex;
    private final int topK;
    private final QueryExpander queryExpander;
    private final boolean useRrfFusion;

    private BooleanRag(BooleanIndex index, KnowledgeIndex knowledgeIndex, int topK,
                       QueryExpander queryExpander, boolean useRrfFusion) {
        this.index = Objects.requireNonNull(index, "index");
        this.knowledgeIndex = knowledgeIndex;
        this.topK = topK;
        this.queryExpander = queryExpander;
        this.useRrfFusion = useRrfFusion;
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
     * <p>If a {@link QueryExpander} is configured, the query is first expanded
     * into multiple variants, each searched independently, and results are merged
     * using RRF fusion with knee-point pruning.
     *
     * @param query boolean vector (must match index dimensions)
     * @return RAG result with original query, knowledge hits, and expanded vector
     */
    public RagResult query(long[] query) {
        Objects.requireNonNull(query, "query");

        List<BooleanIndex.SearchResult> hits;

        if (queryExpander != null && useRrfFusion) {
            hits = queryWithExpansion(query);
        } else {
            hits = index.search(query, topK);
        }

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
     * Performs query expansion + RRF fusion retrieval.
     */
    private List<BooleanIndex.SearchResult> queryWithExpansion(long[] query) {
        List<long[]> expandedQueries = queryExpander.expand(query);

        List<List<RrfFusion.SearchHit>> allResults = new ArrayList<>();
        for (long[] variant : expandedQueries) {
            var hits = index.search(variant, topK * 2);
            List<RrfFusion.SearchHit> converted = hits.stream()
                    .map(h -> new RrfFusion.SearchHit(
                            h.id(), 1.0 / (1 + h.distance()), "expanded", Map.of()))
                    .toList();
            allResults.add(converted);
        }

        List<RrfFusion.FusedResult> fused = RrfFusion.fuse(allResults);

        // Apply knee-point pruning
        List<RrfFusion.FusedResult> pruned;
        if (fused.size() > 2) {
            pruned = RrfFusion.kneePrune(fused, 0.5);
        } else {
            pruned = fused.stream().limit(topK).toList();
        }

        // Convert back to SearchResult
        return pruned.stream()
                .limit(topK)
                .map(f -> new BooleanIndex.SearchResult(f.id(), 0))
                .toList();
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
        private QueryExpander queryExpander;
        private boolean useRrfFusion = true;

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
         * Sets the query expander for multi-variant retrieval.
         */
        public Builder queryExpander(QueryExpander expander) {
            this.queryExpander = expander;
            return this;
        }

        /**
         * Enables or disables RRF fusion (requires queryExpander).
         */
        public Builder useRrfFusion(boolean use) {
            this.useRrfFusion = use;
            return this;
        }

        /**
         * Builds the {@link BooleanRag}.
         */
        public BooleanRag build() {
            return new BooleanRag(index, knowledgeIndex, topK, queryExpander, useRrfFusion);
        }
    }
}
