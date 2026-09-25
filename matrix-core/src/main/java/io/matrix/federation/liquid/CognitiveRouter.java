package io.matrix.federation.liquid;

import java.util.*;

/**
 * W577 — Dual-Mode Cognitive Router.
 *
 * Decides BIR vs. HDC vs. MCTS based on modulator state + query complexity.
 *
 * Mode A: Deep Understanding (Symbolic/Causal)
 * - Uses BIR for logic/ethics
 * - Uses Causal Graphs for "Why?" questions
 * - Uses MCTS for planning
 * - Trigger: Low confidence, high novelty, ethical dilemma
 *
 * Mode B: High Efficiency (Intuitive/Associative)
 * - Uses HDC for pattern matching
 * - Uses Tsetlin for rapid classification
 * - Trigger: Routine tasks, high confidence, real-time constraints
 *
 * Switching: Controlled by Biochemical Modulators
 * - High Cortisol → Safe/BIR mode
 * - High Dopamine → Explore/MCTS mode
 */
public final class CognitiveRouter {

    public enum CognitiveMode {
        DEEP_UNDERSTANDING,  // BIR + Causal + MCTS
        HIGH_EFFICIENCY,     // HDC + Tsetlin
        HYBRID               // Both modes in parallel
    }

    public record RoutingDecision(
            CognitiveMode mode,
            String reason,
            double confidence,
            Map<String, Double> modulatorSnapshot
    ) {}

    private final Map<String, KineticModulator> modulators;
    private final double noveltyThreshold;
    private final double confidenceThreshold;

    public CognitiveRouter(Map<String, KineticModulator> modulators,
                           double noveltyThreshold, double confidenceThreshold) {
        this.modulators = modulators != null ? modulators : new HashMap<>();
        this.noveltyThreshold = noveltyThreshold;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Route a query to the appropriate cognitive mode.
     *
     * @param query           the input query
     * @param noveltyScore    novelty score [0, 1]
     * @param currentConfidence current confidence [0, 1]
     * @param isEthicalDilemma whether this is an ethical question
     * @return routing decision
     */
    public RoutingDecision route(String query, double noveltyScore,
                                  double currentConfidence, boolean isEthicalDilemma) {
        Map<String, Double> snapshot = new HashMap<>();
        modulators.forEach((k, v) -> snapshot.put(k, v.getCurrentLevel()));

        double cortisol = getModulatorLevel("CORTISOL");
        double dopamine = getModulatorLevel("DOPAMINE");
        double norepinephrine = getModulatorLevel("NOREPINEPHRINE");

        // Rule 1: Ethical dilemmas always go to Deep mode (BIR)
        if (isEthicalDilemma) {
            return new RoutingDecision(CognitiveMode.DEEP_UNDERSTANDING,
                    "Ethical dilemma → BIR mode", 1.0, snapshot);
        }

        // Rule 2: High cortisol → Safe/BIR mode
        if (cortisol > 0.7) {
            return new RoutingDecision(CognitiveMode.DEEP_UNDERSTANDING,
                    String.format("High cortisol (%.2f) → safe mode", cortisol), 0.9, snapshot);
        }

        // Rule 3: Low confidence → Deep mode
        if (currentConfidence < confidenceThreshold) {
            return new RoutingDecision(CognitiveMode.DEEP_UNDERSTANDING,
                    String.format("Low confidence (%.2f) → deep mode", currentConfidence), 0.8, snapshot);
        }

        // Rule 4: High novelty → Deep mode
        if (noveltyScore > noveltyThreshold) {
            return new RoutingDecision(CognitiveMode.DEEP_UNDERSTANDING,
                    String.format("High novelty (%.2f) → deep mode", noveltyScore), 0.8, snapshot);
        }

        // Rule 5: High dopamine + norepinephrine → Explore/MCTS
        if (dopamine > 0.6 && norepinephrine > 0.5) {
            return new RoutingDecision(CognitiveMode.DEEP_UNDERSTANDING,
                    String.format("High dopamine (%.2f) + norepinephrine (%.2f) → explore mode",
                            dopamine, norepinephrine), 0.7, snapshot);
        }

        // Rule 6: Routine task → High Efficiency mode
        return new RoutingDecision(CognitiveMode.HIGH_EFFICIENCY,
                "Routine task → efficiency mode", 0.9, snapshot);
    }

    /**
     * Route with auto-detected parameters.
     */
    public RoutingDecision routeAuto(String query) {
        // Simple novelty detection based on query length and uniqueness
        double novelty = Math.min(1.0, query.length() / 100.0);

        // Check for ethical keywords
        boolean isEthical = query.toLowerCase().contains("ethics") ||
                query.toLowerCase().contains("moral") ||
                query.toLowerCase().contains("should i") ||
                query.toLowerCase().contains("is it right");

        return route(query, novelty, 0.5, isEthical);
    }

    private double getModulatorLevel(String id) {
        KineticModulator m = modulators.get(id);
        return m != null ? m.getCurrentLevel() : 0.5;
    }
}
