package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W262 — Cognitive Reflexion (verbal self-reflection).
 *
 * <p>Inspired by Reflexion (Shinn et al. 2023). Verbal self-reflection
 * for cognitive improvement.
 *
 * <p>Process:
 * 1. Try cognitive task
 * 2. Evaluate outcome
 * 3. Generate verbal reflection
 * 4. Update internal state
 * 5. Retry with improved strategy
 *
 * <p>CONSTITUTION VI compliance: self-reflective cognitive processing,
 * not phenomenal consciousness claim.
 */
public final class CognitiveReflexion {

    private CognitiveReflexion() {}

    /** A single reflection entry. */
    public record Reflection(
        int iteration,
        CognitiveGenesisProfile stateBefore,
        CognitiveGenesisProfile stateAfter,
        String reflection,
        double successScore
    ) {}

    /** Reflexion result. */
    public record ReflexionResult(
        List<Reflection> reflections,
        CognitiveGenesisProfile finalProfile,
        double finalScore
    ) {}

    /** Functional interface for reflection generation. */
    @FunctionalInterface
    public interface ReflectionGenerator {
        String reflect(CognitiveGenesisProfile state, double score, int iteration);
    }

    /**
     * Run reflexion loop.
     */
    public static ReflexionResult reflect(CognitiveGenesisProfile initial,
                                            int maxIterations,
                                            ReflectionGenerator generator) {
        if (initial == null || maxIterations < 1) {
            return new ReflexionResult(new ArrayList<>(), initial, 0.0);
        }
        List<Reflection> reflections = new ArrayList<>();
        CognitiveGenesisProfile current = initial;
        double score = computeScore(current);
        for (int i = 0; i < maxIterations; i++) {
            CognitiveGenesisProfile before = current;
            String text = (generator != null) ?
                generator.reflect(before, score, i) :
                "default reflection " + i + " score=" + score;
            current = applyReflection(before, text);
            double newScore = computeScore(current);
            reflections.add(new Reflection(i, before, current, text, newScore));
            score = newScore;
        }
        return new ReflexionResult(reflections, current, score);
    }

    private static double computeScore(CognitiveGenesisProfile p) {
        // Simple score: weighted sum of phi and kolmogorovK
        return p.phiBinary() * 0.5 + Math.min(1.0, p.kolmogorovK() / 100.0) * 0.5;
    }

    private static CognitiveGenesisProfile applyReflection(CognitiveGenesisProfile p, String text) {
        // Apply reflection: small adjustments based on text
        double boost = Math.min(0.05, text.length() / 1000.0);
        double newPhi = Math.max(0.0, Math.min(1.0, p.phiBinary() + boost));
        return new CognitiveGenesisProfile(
            newPhi, p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK() + 1.0, // tiny boost
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK(), p.memristorConductance(), p.lSystemComplexityRatio()
        );
    }
}
