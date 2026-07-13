package io.matrix.rag;

import io.matrix.noosphere.KnowledgeIndex;

import java.util.*;

/**
 * Hybrid Boolean RAG — enhanced retrieval with RRF fusion and adaptive context.
 *
 * <p>Extends the basic Boolean RAG with:
 * <ul>
 *   <li><b>Hybrid search:</b> combines dense (boolean vector) + sparse (keyword) retrieval</li>
 *   <li><b>RRF fusion:</b> merges results from multiple strategies without weight tuning</li>
 *   <li><b>Adaptive context:</b> knee-point pruning replaces static top-K</li>
 *   <li><b>Two-level filtering:</b> strong vs borderline matches</li>
 *   <li><b>Structure-aware chunking:</b> breadcrumb injection for context</li>
 * </ul>
 *
 * <p>Ref: Research Synthesis 2026-Q3 §1.1, §1.2, §1.3
 */
public final class HybridBooleanRag {

    private final BooleanIndex index;
    private final KnowledgeIndex knowledgeIndex;
    private final int topK;
    private final boolean adaptiveContext;
    private final double kneeSensitivity;
    private final double strongThreshold;
    private final double borderlineThreshold;

    private HybridBooleanRag(Builder builder) {
        this.index = Objects.requireNonNull(builder.index, "index");
        this.knowledgeIndex = builder.knowledgeIndex;
        this.topK = builder.topK;
        this.adaptiveContext = builder.adaptiveContext;
        this.kneeSensitivity = builder.kneeSensitivity;
        this.strongThreshold = builder.strongThreshold;
        this.borderlineThreshold = builder.borderlineThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Queries the RAG system with hybrid retrieval and adaptive context.
     *
     * @param query boolean vector query
     * @return hybrid RAG result with fused and pruned results
     */
    public HybridRagResult query(long[] query) {
        Objects.requireNonNull(query, "query");

        // Step 1: Dense retrieval (boolean vector similarity)
        List<BooleanIndex.SearchResult> denseHits = index.search(query, topK * 2);
        List<RrfFusion.SearchHit> denseConverted = denseHits.stream()
                .map(h -> new RrfFusion.SearchHit(
                        h.id(), 1.0 / (1 + h.distance()), "dense", Map.of()))
                .toList();

        // Step 2: Sparse retrieval (keyword-based, if KnowledgeIndex available)
        List<RrfFusion.SearchHit> sparseConverted = Collections.emptyList();
        if (knowledgeIndex != null) {
            sparseConverted = sparseSearch(query, topK * 2);
        }

        // Step 3: RRF Fusion
        List<List<RrfFusion.SearchHit>> allResults = new ArrayList<>();
        allResults.add(denseConverted);
        if (!sparseConverted.isEmpty()) {
            allResults.add(sparseConverted);
        }
        List<RrfFusion.FusedResult> fused = RrfFusion.fuse(allResults);

        // Step 4: Adaptive context management (knee-point pruning)
        List<RrfFusion.FusedResult> pruned;
        if (adaptiveContext && fused.size() > 2) {
            pruned = RrfFusion.kneePrune(fused, kneeSensitivity);
        } else {
            pruned = fused.stream().limit(topK).toList();
        }

        // Step 5: Two-level filtering
        List<RrfFusion.FusedResult> strong = new ArrayList<>();
        List<RrfFusion.FusedResult> borderline = new ArrayList<>();
        for (var result : pruned) {
            if (result.score() >= strongThreshold) {
                strong.add(result);
            } else if (result.score() >= borderlineThreshold) {
                borderline.add(result);
            }
        }

        // Step 6: Build expanded vector
        List<long[]> knowledgeVectors = new ArrayList<>();
        for (var hit : strong) {
            long[] vec = index.get(hit.id());
            if (vec != null) {
                knowledgeVectors.add(vec);
            }
        }
        for (var hit : borderline) {
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

        return new HybridRagResult(
                query, pruned, strong, borderline, expanded,
                strong.isEmpty()
        );
    }

    /**
     * Performs sparse (keyword-based) search via KnowledgeIndex.
     */
    private List<RrfFusion.SearchHit> sparseSearch(long[] query, int limit) {
        if (knowledgeIndex == null) return Collections.emptyList();

        // Convert boolean vector to keyword query
        String keywordQuery = queryToKeywordString(query);
        if (keywordQuery.isBlank()) return Collections.emptyList();

        try {
            var results = knowledgeIndex.findTop(keywordQuery, limit);
            return results.stream()
                    .map(r -> new RrfFusion.SearchHit(
                            r.entryId().toString(), r.relevance(), "sparse",
                            Map.of("fnl", r.fnl().name())))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Extracts keywords from boolean vector (bit positions as semantic markers).
     */
    private String queryToKeywordString(long[] query) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < query.length; i++) {
            long bits = query[i];
            for (int bit = 0; bit < 64; bit++) {
                if ((bits & (1L << bit)) != 0) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append("bit_").append(i * 64 + bit);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Result of a hybrid RAG query.
     */
    public record HybridRagResult(
            /** Original query vector. */
            long[] originalQuery,
            /** All pruned results (after knee-point pruning). */
            List<RrfFusion.FusedResult> allResults,
            /** Strong matches (score >= strongThreshold). */
            List<RrfFusion.FusedResult> strongMatches,
            /** Borderline matches (score between borderline and strong thresholds). */
            List<RrfFusion.FusedResult> borderlineMatches,
            /** Expanded boolean vector (query + knowledge). */
            long[][] expandedVector,
            /** Whether the system should refuse to answer (no strong matches). */
            boolean shouldRefuse
    ) {
        /**
         * Returns true if there are sufficient strong matches for generation.
         */
        public boolean hasSufficientContext() {
            return !strongMatches.isEmpty();
        }

        /**
         * Returns the total number of knowledge vectors retrieved.
         */
        public int totalRetrieved() {
            return strongMatches.size() + borderlineMatches.size();
        }
    }

    /**
     * Builder for {@link HybridBooleanRag}.
     */
    public static final class Builder {
        private BooleanIndex index;
        private KnowledgeIndex knowledgeIndex;
        private int topK = 5;
        private boolean adaptiveContext = true;
        private double kneeSensitivity = 0.5;
        private double strongThreshold = 0.015;
        private double borderlineThreshold = 0.010;

        public Builder index(BooleanIndex index) {
            this.index = Objects.requireNonNull(index);
            return this;
        }

        public Builder knowledgeIndex(KnowledgeIndex knowledgeIndex) {
            this.knowledgeIndex = knowledgeIndex;
            return this;
        }

        public Builder topK(int topK) {
            if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
            this.topK = topK;
            return this;
        }

        public Builder adaptiveContext(boolean adaptive) {
            this.adaptiveContext = adaptive;
            return this;
        }

        public Builder kneeSensitivity(double sensitivity) {
            if (sensitivity < 0 || sensitivity > 1) {
                throw new IllegalArgumentException("sensitivity must be 0-1");
            }
            this.kneeSensitivity = sensitivity;
            return this;
        }

        public Builder strongThreshold(double threshold) {
            this.strongThreshold = threshold;
            return this;
        }

        public Builder borderlineThreshold(double threshold) {
            this.borderlineThreshold = threshold;
            return this;
        }

        public HybridBooleanRag build() {
            return new HybridBooleanRag(this);
        }
    }
}
