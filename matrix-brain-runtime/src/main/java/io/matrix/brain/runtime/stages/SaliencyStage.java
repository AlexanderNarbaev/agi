package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.List;

/**
 * MIND-W1 — Stage 3: Salience.
 *
 * <p>Scores how much attention the input deserves. Inputs with very short
 * length or only punctuation get low salience; questions and arithmetic get
 * high salience.</p>
 */
public final class SaliencyStage {

    public record SalienceScore(double score, String tier) {
        public static SalienceScore of(double score) {
            String tier = score >= 0.75 ? "HIGH"
                : score >= 0.45 ? "MEDIUM"
                : "LOW";
            return new SalienceScore(score, tier);
        }
    }

    public SalienceScore score(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        double score = 0.50; // baseline
        String lower = input.toLowerCase().trim();

        // Question pattern -> higher salience
        if (lower.endsWith("?") || lower.startsWith("what") || lower.startsWith("how")
            || lower.startsWith("why") || lower.startsWith("when")
            || lower.startsWith("who") || lower.startsWith("where")) {
            score += 0.25;
        }
        // Arithmetic -> highest
        if (lower.matches(".*\\d+\\s*[+\\-*/]\\s*\\d+.*")) {
            score += 0.30;
        }
        // Analogy -> high
        if (lower.contains("::") || lower.contains(" is to ")) {
            score += 0.20;
        }
        // Very short -> low
        if (input.trim().length() < 8) {
            score -= 0.30;
        }
        // clamp to [0, 1]
        score = Math.max(0.0, Math.min(1.0, score));

        SalienceScore s = SalienceScore.of(score);
        BrcStep step = BrcStep.of("SALIENCE", true, score,
            List.of("tier=" + s.tier()));
        trace.add(step);
        return s;
    }
}
