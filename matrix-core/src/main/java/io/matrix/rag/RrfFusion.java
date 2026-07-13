package io.matrix.rag;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) — merges multiple ranked result lists into one.
 *
 * <p>RRF combines results from different retrieval strategies (dense, sparse,
 * keyword) without requiring manual weight tuning. The formula is:
 * <pre>
 *   score(d) = Σ 1 / (k + rank_i(d))
 * </pre>
 * where k is a constant (typically 60), and rank_i(d) is the rank of document d
 * in the i-th retrieval list.
 *
 * <p>Ref: Research Synthesis 2026-Q3 §1.1 (Hybrid RAG)
 */
public final class RrfFusion {

    /** Default RRF constant. */
    public static final int DEFAULT_K = 60;

    private RrfFusion() {}

    /**
     * Fuses multiple ranked result lists using RRF.
     *
     * @param resultLists ranked lists from different retrieval strategies
     * @param k           RRF constant (typically 60)
     * @return merged list sorted by RRF score (descending)
     */
    public static List<FusedResult> fuse(List<List<SearchHit>> resultLists, int k) {
        Objects.requireNonNull(resultLists, "resultLists");
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");

        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();

        for (List<SearchHit> results : resultLists) {
            for (int rank = 0; rank < results.size(); rank++) {
                SearchHit hit = results.get(rank);
                double rrfScore = 1.0 / (k + rank + 1);
                scoreMap.merge(hit.id(), rrfScore, Double::sum);
                hitMap.putIfAbsent(hit.id(), hit);
            }
        }

        List<FusedResult> fused = new ArrayList<>();
        for (var entry : scoreMap.entrySet()) {
            SearchHit original = hitMap.get(entry.getKey());
            fused.add(new FusedResult(entry.getKey(), entry.getValue(),
                    original != null ? original.source() : "unknown",
                    original != null ? original.metadata() : Map.of()));
        }

        fused.sort(Comparator.comparingDouble(FusedResult::score).reversed());
        return Collections.unmodifiableList(fused);
    }

    /**
     * Fuses with default k=60.
     */
    public static List<FusedResult> fuse(List<List<SearchHit>> resultLists) {
        return fuse(resultLists, DEFAULT_K);
    }

    /**
     * Applies knee-point pruning to a ranked list of results.
     *
     * <p>The knee-point is found by computing the maximum perpendicular distance
     * from the chord connecting the first and last points to the RRF score curve.
     * Results below the knee are pruned.
     *
     * <p>Ref: Habr #1016438 (DRAG with KNEE)
     *
     * @param results    sorted list of fused results
     * @param sensitivity pruning aggressiveness (0.0-1.0, default 0.5)
     * @return pruned list containing only results above the knee-point
     */
    public static List<FusedResult> kneePrune(List<FusedResult> results, double sensitivity) {
        Objects.requireNonNull(results, "results");
        if (results.size() <= 2) return results;
        if (sensitivity < 0 || sensitivity > 1) {
            throw new IllegalArgumentException("sensitivity must be 0-1");
        }

        double[] scores = results.stream()
                .mapToDouble(FusedResult::score)
                .toArray();

        // Find knee-point: max perpendicular distance from chord
        double x1 = 0, y1 = scores[0];
        double x2 = scores.length - 1, y2 = scores[scores.length - 1];

        double maxDist = 0;
        int kneeIndex = scores.length - 1;

        for (int i = 1; i < scores.length - 1; i++) {
            double dist = perpendicularDistance(i, scores[i], x1, y1, x2, y2);
            if (dist > maxDist) {
                maxDist = dist;
                kneeIndex = i;
            }
        }

        // Apply sensitivity: move knee point based on sensitivity
        int adjustedKnee = (int) (kneeIndex * (1.0 - sensitivity * 0.5));
        adjustedKnee = Math.max(1, Math.min(adjustedKnee, scores.length));

        return results.subList(0, adjustedKnee);
    }

    /**
     * Applies knee-prune with default sensitivity (0.5).
     */
    public static List<FusedResult> kneePrune(List<FusedResult> results) {
        return kneePrune(results, 0.5);
    }

    /**
     * Computes perpendicular distance from point (px, py) to line (x1,y1)-(x2,y2).
     */
    private static double perpendicularDistance(double px, double py,
                                                double x1, double y1,
                                                double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double norm = Math.sqrt(dx * dx + dy * dy);
        if (norm == 0) return 0;
        return Math.abs(dy * px - dx * py + x2 * y1 - y2 * x1) / norm;
    }

    /**
     * A search hit from a single retrieval strategy.
     */
    public record SearchHit(
            String id,
            double score,
            String source,
            Map<String, String> metadata
    ) {}

    /**
     * A fused result with combined RRF score.
     */
    public record FusedResult(
            String id,
            double score,
            String source,
            Map<String, String> metadata
    ) {}
}
