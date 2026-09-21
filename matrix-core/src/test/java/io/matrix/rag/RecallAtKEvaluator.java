package io.matrix.rag;

import java.util.*;

/**
 * Recall@K evaluator for boolean RAG retrieval quality.
 *
 * <p>Computes Recall@K = |retrieved_ids ∩ relevant_ids| / |relevant_ids|
 * for a given query against a golden truth set. Used as a building block
 * for H-007 verification: "Knowledge Stack достигает Recall@5 ≥0.85
 * на golden-наборе владельца после устранения пропуска эмбеддинга
 * (нет-LLM конфиг)".
 *
 * <p>Thread-safe: immutable, accepts external data in method calls.
 *
 * <p>Ref: ROADMAP.md G3, METRICS.md (§Recall@k)
 */
public final class RecallAtKEvaluator {

    private final int k;

    private RecallAtKEvaluator(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1, got: " + k);
        this.k = k;
    }

    public static RecallAtKEvaluator forK(int k) {
        return new RecallAtKEvaluator(k);
    }

    /**
     * Evaluates a single query result against golden relevant IDs.
     *
     * @param retrievedIds ordered list of retrieved document IDs (top-K)
     * @param relevantIds  set of relevant document IDs from golden truth
     * @return recall value in [0.0, 1.0]
     */
    public double evaluate(List<String> retrievedIds, Set<String> relevantIds) {
        Objects.requireNonNull(retrievedIds, "retrievedIds");
        Objects.requireNonNull(relevantIds, "relevantIds");

        if (relevantIds.isEmpty()) {
            return 1.0; // vacuously true
        }

        long found = retrievedIds.stream()
                .limit(k)
                .filter(relevantIds::contains)
                .count();

        return (double) found / relevantIds.size();
    }

    /**
     * Evaluates multiple queries and returns aggregate statistics.
     *
     * @param results list of per-query evaluation pairs (retrieved IDs, relevant IDs)
     * @return aggregate statistics including mean recall, pass rate, and per-query details
     */
    public AggregateResult evaluateAll(List<QueryResult> results) {
        Objects.requireNonNull(results, "results");

        if (results.isEmpty()) {
            return new AggregateResult(0.0, 0, 0, 0, List.of());
        }

        List<PerQueryRecall> perQuery = new ArrayList<>();
        double sum = 0.0;
        int passCount = 0;

        for (int i = 0; i < results.size(); i++) {
            QueryResult qr = results.get(i);
            double recall = evaluate(qr.retrievedIds(), qr.relevantIds());
            boolean pass = recall >= 0.85;
            if (pass) passCount++;
            sum += recall;
            perQuery.add(new PerQueryRecall(i, recall, pass, qr.retrievedIds().size()));
        }

        double mean = sum / results.size();
        return new AggregateResult(mean, passCount, results.size() - passCount,
                results.size(), perQuery);
    }

    /**
     * A single query evaluation input: retrieved IDs + golden relevant IDs.
     */
    public record QueryResult(
            List<String> retrievedIds,
            Set<String> relevantIds
    ) {
        public QueryResult {
            Objects.requireNonNull(retrievedIds, "retrievedIds");
            Objects.requireNonNull(relevantIds, "relevantIds");
        }
    }

    /**
     * Per-query recall detail.
     */
    public record PerQueryRecall(
            int queryIndex,
            double recall,
            boolean pass,
            int retrievedCount
    ) {}

    /**
     * Aggregate recall statistics across multiple queries.
     */
    public record AggregateResult(
            double meanRecall,
            int passCount,
            int failCount,
            int totalQueries,
            List<PerQueryRecall> perQueryDetails
    ) {
        /** Fraction of queries with Recall@K ≥ 0.85. */
        public double passRate() {
            return totalQueries == 0 ? 0.0 : (double) passCount / totalQueries;
        }

        /** Whether the aggregate meets the H-007 threshold (pass rate ≥ 85%). */
        public boolean meetsThreshold() {
            return passRate() >= 0.85;
        }

        /** Multi-line human-readable summary. */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Recall@K Aggregate: mean=%.4f, pass=%d/%d (%.1f%%), fail=%d\n",
                    meanRecall, passCount, totalQueries, passRate() * 100.0, failCount));
            sb.append("Per-query:\n");
            for (var pq : perQueryDetails) {
                sb.append(String.format("  q%03d: recall=%.4f %s [retrieved=%d]\n",
                        pq.queryIndex(), pq.recall(),
                        pq.pass() ? "PASS" : "FAIL",
                        pq.retrievedCount()));
            }
            sb.append(String.format("=> %s threshold (pass rate %.1f%% >= 85%%)\n",
                    meetsThreshold() ? "MEETS" : "BELOW",
                    passRate() * 100.0));
            return sb.toString();
        }
    }
}
