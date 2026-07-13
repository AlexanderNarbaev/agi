package io.matrix.rag;

import java.util.*;

/**
 * Benchmark comparing retrieval quality and latency across RAG approaches.
 *
 * <p>Compares three strategies:
 * <ul>
 *   <li><b>Basic:</b> single-query boolean vector search</li>
 *   <li><b>Hybrid:</b> dense + sparse with RRF fusion and knee-point pruning</li>
 *   <li><b>Expanded:</b> query expansion with multiple variants + RRF fusion</li>
 * </ul>
 *
 * <p>Measures:
 * <ul>
 *   <li>Retrieval quality: number of unique documents retrieved, overlap</li>
 *   <li>Latency: average query time in microseconds</li>
 *   <li>Scalability: performance at 100, 1000, 10000 vectors</li>
 * </ul>
 *
 * <p>Not thread-safe: designed for single-threaded benchmark execution.
 */
public final class BooleanRagBenchmark {

    private final int dimensions;
    private final int topK;
    private final int warmupIterations;
    private final int measureIterations;

    private BooleanRagBenchmark(Builder builder) {
        this.dimensions = builder.dimensions;
        this.topK = builder.topK;
        this.warmupIterations = builder.warmupIterations;
        this.measureIterations = builder.measureIterations;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the full benchmark suite.
     *
     * @param sizes array of index sizes to test (e.g., [100, 1000, 10000])
     * @return benchmark results for each size
     */
    public List<SizeResult> run(int[] sizes) {
        Objects.requireNonNull(sizes, "sizes");
        List<SizeResult> results = new ArrayList<>(sizes.length);

        for (int size : sizes) {
            results.add(benchmarkSize(size));
        }

        return results;
    }

    /**
     * Benchmarks a single index size.
     */
    private SizeResult benchmarkSize(int size) {
        // Build index with random vectors
        BooleanIndex index = buildRandomIndex(size);
        SplittableRandom rng = new SplittableRandom(42);

        // Generate test queries
        int numQueries = Math.min(100, size / 10 + 1);
        List<long[]> queries = new ArrayList<>(numQueries);
        for (int i = 0; i < numQueries; i++) {
            queries.add(randomVector(rng));
        }

        // --- Basic RAG ---
        BooleanRag basicRag = BooleanRag.builder()
                .index(index)
                .topK(topK)
                .build();

        double basicLatency = measureLatency(queries, q -> basicRag.query(q).expandedVector());
        Set<String> basicUniqueDocs = countUniqueDocs(queries, q -> basicRag.query(q).knowledgeHits()
                .stream().map(BooleanIndex.SearchResult::id).toList());

        // --- Hybrid RAG ---
        HybridBooleanRag hybridRag = HybridBooleanRag.builder()
                .index(index)
                .topK(topK)
                .adaptiveContext(true)
                .build();

        double hybridLatency = measureLatency(queries, q -> hybridRag.query(q).expandedVector());
        Set<String> hybridUniqueDocs = countUniqueDocsHybrid(queries, q -> hybridRag.query(q).allResults()
                .stream().map(RrfFusion.FusedResult::id).toList());

        // --- Expanded RAG ---
        QueryExpander expander = QueryExpander.builder()
                .numVariants(4)
                .build();

        double expandedLatency = measureExpandedLatency(queries, index, expander);
        Set<String> expandedUniqueDocs = countExpandedUniqueDocs(queries, index, expander);

        return new SizeResult(
                size,
                numQueries,
                basicLatency, hybridLatency, expandedLatency,
                basicUniqueDocs.size(), hybridUniqueDocs.size(), expandedUniqueDocs.size()
        );
    }

    private BooleanIndex buildRandomIndex(int size) {
        BooleanIndex index = BooleanIndex.builder().dimensions(dimensions).build();
        SplittableRandom rng = new SplittableRandom(123);
        for (int i = 0; i < size; i++) {
            index.add("doc_" + i, randomVector(rng));
        }
        return index;
    }

    private long[] randomVector(SplittableRandom rng) {
        long[] vec = new long[dimensions / 64];
        for (int i = 0; i < vec.length; i++) {
            vec[i] = rng.nextLong();
        }
        return vec;
    }

    @FunctionalInterface
    private interface QueryFn<T> {
        T apply(long[] query);
    }

    private double measureLatency(List<long[]> queries, QueryFn<?> fn) {
        // Warmup
        for (int i = 0; i < warmupIterations; i++) {
            fn.apply(queries.get(i % queries.size()));
        }

        // Measure
        long totalNanos = 0;
        for (int i = 0; i < measureIterations; i++) {
            long start = System.nanoTime();
            fn.apply(queries.get(i % queries.size()));
            totalNanos += System.nanoTime() - start;
        }

        return (double) totalNanos / measureIterations / 1000.0; // microseconds
    }

    private double measureExpandedLatency(List<long[]> queries, BooleanIndex index, QueryExpander expander) {
        // Warmup
        for (int i = 0; i < warmupIterations; i++) {
            long[] q = queries.get(i % queries.size());
            List<long[]> expanded = expander.expand(q);
            List<List<RrfFusion.SearchHit>> allResults = new ArrayList<>();
            for (long[] variant : expanded) {
                var hits = index.search(variant, topK * 2);
                allResults.add(hits.stream()
                        .map(h -> new RrfFusion.SearchHit(h.id(), 1.0 / (1 + h.distance()), "expanded", Map.of()))
                        .toList());
            }
            RrfFusion.fuse(allResults);
        }

        // Measure
        long totalNanos = 0;
        for (int i = 0; i < measureIterations; i++) {
            long[] q = queries.get(i % queries.size());
            long start = System.nanoTime();
            List<long[]> expanded = expander.expand(q);
            List<List<RrfFusion.SearchHit>> allResults = new ArrayList<>();
            for (long[] variant : expanded) {
                var hits = index.search(variant, topK * 2);
                allResults.add(hits.stream()
                        .map(h -> new RrfFusion.SearchHit(h.id(), 1.0 / (1 + h.distance()), "expanded", Map.of()))
                        .toList());
            }
            RrfFusion.fuse(allResults);
            totalNanos += System.nanoTime() - start;
        }

        return (double) totalNanos / measureIterations / 1000.0;
    }

    private Set<String> countUniqueDocs(List<long[]> queries, QueryFn<List<String>> fn) {
        Set<String> allDocs = new HashSet<>();
        for (long[] q : queries) {
            allDocs.addAll(fn.apply(q));
        }
        return allDocs;
    }

    private Set<String> countUniqueDocsHybrid(List<long[]> queries, QueryFn<List<String>> fn) {
        Set<String> allDocs = new HashSet<>();
        for (long[] q : queries) {
            allDocs.addAll(fn.apply(q));
        }
        return allDocs;
    }

    private Set<String> countExpandedUniqueDocs(List<long[]> queries, BooleanIndex index, QueryExpander expander) {
        Set<String> allDocs = new HashSet<>();
        for (long[] q : queries) {
            List<long[]> expanded = expander.expand(q);
            for (long[] variant : expanded) {
                var hits = index.search(variant, topK);
                for (var hit : hits) {
                    allDocs.add(hit.id());
                }
            }
        }
        return allDocs;
    }

    /**
     * Results for a single index size.
     */
    public record SizeResult(
            int indexSize,
            int numQueries,
            double basicLatencyUs,
            double hybridLatencyUs,
            double expandedLatencyUs,
            int basicUniqueDocs,
            int hybridUniqueDocs,
            int expandedUniqueDocs
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Size=%d | Queries=%d | Latency(us): basic=%.1f, hybrid=%.1f, expanded=%.1f | UniqueDocs: basic=%d, hybrid=%d, expanded=%d",
                    indexSize, numQueries,
                    basicLatencyUs, hybridLatencyUs, expandedLatencyUs,
                    basicUniqueDocs, hybridUniqueDocs, expandedUniqueDocs
            );
        }
    }

    // --- Builder ---

    public static final class Builder {
        private int dimensions = 64;
        private int topK = 5;
        private int warmupIterations = 10;
        private int measureIterations = 50;

        public Builder dimensions(int dimensions) {
            if (dimensions <= 0 || dimensions % 64 != 0) {
                throw new IllegalArgumentException("Dimensions must be positive multiple of 64");
            }
            this.dimensions = dimensions;
            return this;
        }

        public Builder topK(int topK) {
            if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
            this.topK = topK;
            return this;
        }

        public Builder warmupIterations(int n) {
            this.warmupIterations = n;
            return this;
        }

        public Builder measureIterations(int n) {
            this.measureIterations = n;
            return this;
        }

        public BooleanRagBenchmark build() {
            return new BooleanRagBenchmark(this);
        }
    }
}
