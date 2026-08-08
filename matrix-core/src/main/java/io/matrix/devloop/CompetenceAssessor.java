package io.matrix.devloop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Competence assessor: runs scenario battery and produces reports.
 *
 * <p>Per SPEC-000 §3: battery = XOR → GridWorld → craft-graph.
 * Each scenario run produces a CompetenceReport. The assessor tracks
 * aggregate competence and determines MA level readiness.
 */
public final class CompetenceAssessor {

    private final List<CompetenceReport> history = new CopyOnWriteArrayList<>();

    /** Run a single scenario and record the report. */
    public CompetenceReport assess(ScenarioSpec scenario, ScenarioRunner runner) {
        long t0 = System.currentTimeMillis();
        var result = runner.run(scenario);
        long ms = System.currentTimeMillis() - t0;

        double score = computeScore(scenario, result, ms);
        var report = new CompetenceReport(
                scenario.id(), result.success(), result.stepsUsed(), ms, score, result.details());
        history.add(report);
        return report;
    }

    /** Run the standard battery. */
    public List<CompetenceReport> assessBattery(ScenarioRunner runner) {
        return ScenarioSpec.standardBattery().stream()
                .map(s -> assess(s, runner))
                .toList();
    }

    /** Compute aggregate competence from history. */
    public double aggregateCompetence() {
        if (history.isEmpty()) return 0.0;
        return history.stream()
                .mapToDouble(CompetenceReport::competenceScore)
                .average().orElse(0.0);
    }

    /** Check if ready for MA level transition. */
    public boolean readyFor(MaturityLevel target) {
        double threshold = switch (target) {
            case MA_1_LOCAL -> 0.6;
            case MA_2_NETWORK -> 0.75;
            case MA_3_SELF_MODIFY -> 0.85;
            case MA_4_AUTONOMOUS -> 0.95;
            default -> 0.0;
        };
        return aggregateCompetence() >= threshold;
    }

    public List<CompetenceReport> history() { return List.copyOf(history); }

    private double computeScore(ScenarioSpec spec, ScenarioResult result, long ms) {
        if (!result.success()) return 0.0;
        double base = 1.0;
        // Efficiency bonus: fewer steps = higher score
        double efficiency = Math.max(0.0, 1.0 - (double) result.stepsUsed() / spec.maxSteps());
        // Speed bonus: faster = higher score (capped)
        double speed = Math.max(0.0, 1.0 - ms / 10_000.0);
        return base * 0.5 + efficiency * 0.3 + speed * 0.2;
    }

    /** Functional interface for running a scenario. */
    @FunctionalInterface
    public interface ScenarioRunner {
        ScenarioResult run(ScenarioSpec scenario);
    }

    /** Result of a single scenario run. */
    public record ScenarioResult(boolean success, int stepsUsed, String details) {
        public static ScenarioResult success(int steps, String details) {
            return new ScenarioResult(true, steps, details);
        }
        public static ScenarioResult failure(int steps, String reason) {
            return new ScenarioResult(false, steps, reason);
        }
    }
}
