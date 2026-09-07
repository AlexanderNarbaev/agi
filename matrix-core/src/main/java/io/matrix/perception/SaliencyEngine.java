package io.matrix.perception;

/**
 * RUN 152 — SaliencyEngine (bottom-up attention scoring).
 *
 * <p>Computes a saliency score from a boolean signal vector.
 * The score reflects how "novel" or "attention-worthy" the
 * input is. Used by AttentionRouter (Phase β) to merge
 * bottom-up saliency with top-down impulses.
 *
 * <p>Scoring formula (deterministic):
 * <ul>
 *   <li>Count of "1" bits (density)</li>
 *   <li>Distance from expected density 50%</li>
 *   <li>Score = bit_count weighted by surprise</li>
 * </ul>
 *
 * <p>Per CONSTITUTION I, the score is fully deterministic for
 * any given input.
 */
public final class SaliencyEngine {

    public record SaliencyScore(String source, double score, int bitCount,
                                double density, double surprise) {}

    public SaliencyScore score(String source, boolean[] bits) {
        int n = bits.length;
        int ones = 0;
        for (boolean b : bits) if (b) ones++;
        double density = n == 0 ? 0 : (double) ones / n;
        // Surprise: |density - 0.5| * 2 (0..1, max at 0% or 100%)
        double surprise = Math.abs(density - 0.5) * 2;
        // Final score: density-weighted surprise
        double score = density * (1 + surprise);
        return new SaliencyScore(source, score, ones, density, surprise);
    }

    /**
     * Rank sources by saliency. Returns sources ordered
     * highest score first.
     */
    public java.util.List<SaliencyScore> rank(java.util.Map<String, boolean[]> sources) {
        java.util.List<SaliencyScore> out = new java.util.ArrayList<>();
        for (var e : sources.entrySet()) {
            out.add(score(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }
}
