package io.matrix.perception;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 212 — SaliencyRanker (diverse top-K).
 *
 * <p>Ranks saliency scores and returns top-K while penalizing
 * similar sources. Useful for diverse attention selection.
 */
public final class SaliencyRanker {

    /**
     * Rank sources by score and return top-K (default k=5).
     * Diversity: if two sources produce identical scores, the
     * earlier in iteration order wins (stable).
     */
    public static List<SaliencyEngine.SaliencyScore> rankTopK(
            List<SaliencyEngine.SaliencyScore> sources, int k) {
        List<SaliencyEngine.SaliencyScore> sorted = new ArrayList<>(sources);
        sorted.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (sorted.size() <= k) return sorted;
        return new ArrayList<>(sorted.subList(0, k));
    }

    /** Returns true if the source diversity cap is satisfied. */
    public static boolean isDiverse(double[] scores, double threshold) {
        if (scores.length < 2) return true;
        double min = scores[0], max = scores[0];
        for (double s : scores) {
            if (s < min) min = s;
            if (s > max) max = s;
        }
        return (max - min) >= threshold;
    }

    /** Quantize a score to a discrete bucket. */
    public static int bucket(double score) {
        if (score < 0.2) return 0;
        if (score < 0.4) return 1;
        if (score < 0.6) return 2;
        if (score < 0.8) return 3;
        return 4;
    }
}
