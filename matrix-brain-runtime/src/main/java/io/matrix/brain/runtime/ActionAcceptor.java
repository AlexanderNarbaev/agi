package io.matrix.brain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TRUE-W11 iteration #5 — Anokhin Acceptor of Action Results.
 *
 * <p>The Acceptor compares the predicted outcome of a cognitive action
 * against the actual outcome. A binary match/mismatch + magnitude
 * is recorded. This is the cybernetics-inspired primitive that lets
 * the mind attribute surprise to a specific stage of the cognitive
 * cycle (Anokhin, P.K. 1974, theory of functional systems).</p>
 */
public final class ActionAcceptor {

    /** Match/mismatch verdict from the Acceptor. */
    public enum Verdict {
        MATCH,           // prediction matched outcome (within tolerance)
        MISMATCH_SMALL,  // mismatch but close (informational, no learning)
        MISMATCH_LARGE   // mismatch with high magnitude (trigger learning)
    }

    private final double largeThreshold;
    private final Map<String, Integer> attribution = new LinkedHashMap<>();
    private int totalPredictions = 0;
    private int totalMatches = 0;
    private int totalLargeMismatches = 0;
    private double cumulativeError = 0.0;

    public ActionAcceptor(double largeThreshold) {
        if (largeThreshold <= 0) throw new IllegalArgumentException("threshold > 0");
        this.largeThreshold = largeThreshold;
    }

    /**
     * Compare predicted vs actual for a given stage.
     * @param stageName stage that produced the prediction (e.g. "hdc", "bir")
     * @param predicted predicted outcome (e.g. cosine similarity)
     * @param actual    actual outcome (e.g. reward from user feedback)
     * @return verdict + magnitude
     */
    public Verdict accept(String stageName, double predicted, double actual) {
        if (stageName == null || stageName.isBlank())
            throw new IllegalArgumentException("stageName required");
        double err = Math.abs(predicted - actual);
        cumulativeError += err * err;
        totalPredictions++;
        attribution.merge(stageName, 1, Integer::sum);

        Verdict v;
        if (err < largeThreshold * 0.1) {
            v = Verdict.MATCH;
            totalMatches++;
        } else if (err < largeThreshold) {
            v = Verdict.MISMATCH_SMALL;
        } else {
            v = Verdict.MISMATCH_LARGE;
            totalLargeMismatches++;
        }
        return v;
    }

    public int totalPredictions() { return totalPredictions; }
    public int totalMatches() { return totalMatches; }
    public int totalLargeMismatches() { return totalLargeMismatches; }
    public double meanSquaredError() {
        return totalPredictions == 0 ? 0 : cumulativeError / totalPredictions;
    }

    /** Stages ordered by attribution count (most-attributed first). */
    public Map<String, Integer> attribution() {
        return attribution;
    }

    /** Reset state. Useful between sessions/cycles. */
    public void reset() {
        attribution.clear();
        totalPredictions = 0;
        totalMatches = 0;
        totalLargeMismatches = 0;
        cumulativeError = 0.0;
    }
}
