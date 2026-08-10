package io.matrix.devloop;

/**
 * Competence report for a single scenario run (SPEC-000).
 *
 * <p>Tracks: success/failure, steps taken, wall time, competence score.
 * Competence score = weighted combination of accuracy, efficiency, and
 * robustness (noise tolerance).
 */
public record CompetenceReport(
        String scenarioId,
        boolean success,
        int stepsUsed,
        long wallTimeMs,
        double competenceScore,
        String details) {

    public static CompetenceReport success(String scenarioId, int steps, long ms, double score) {
        return new CompetenceReport(scenarioId, true, steps, ms, score, "success");
    }

    public static CompetenceReport failure(String scenarioId, int steps, long ms, String reason) {
        return new CompetenceReport(scenarioId, false, steps, ms, 0.0, reason);
    }
}
