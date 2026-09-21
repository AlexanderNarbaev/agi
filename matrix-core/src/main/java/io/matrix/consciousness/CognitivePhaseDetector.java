package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W153 — Cognitive phase detector.
 *
 * <p>Detects transitions between cognitive regimes (FROZEN /
 * EDGE_OF_CHAOS / CHAOTIC) in a sequence of CognitiveGenesisProfile
 * instances.
 *
 * <p>Provides:
 * - detectTransitions(): returns a list of (cycle, fromRegime, toRegime)
 * - dominantRegime(): returns the most common regime in the sequence
 * - transitionRate(): fraction of cycles where regime changed
 *
 * <p>CONSTITUTION VI compliance: phase transition detection on
 * measurement substrate, not phenomenal consciousness claim.
 */
public final class CognitivePhaseDetector {

    private CognitivePhaseDetector() {}

    /** A phase transition between two consecutive regimes. */
    public record PhaseTransition(int fromCycle, int toCycle,
                                   String fromRegime, String toRegime) {}

    /**
     * Detect all phase transitions in a sequence of profiles.
     */
    public static List<PhaseTransition> detectTransitions(List<CognitiveGenesisProfile> profiles) {
        List<PhaseTransition> transitions = new ArrayList<>();
        if (profiles == null || profiles.size() < 2) return transitions;
        for (int i = 1; i < profiles.size(); i++) {
            String prev = profiles.get(i - 1).regime();
            String curr = profiles.get(i).regime();
            if (!prev.equals(curr)) {
                transitions.add(new PhaseTransition(i - 1, i, prev, curr));
            }
        }
        return transitions;
    }

    /**
     * Return the most common regime in the profile sequence.
     * Ties broken alphabetically.
     */
    public static String dominantRegime(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return "UNKNOWN";
        int frozen = 0, edge = 0, chaotic = 0;
        for (CognitiveGenesisProfile p : profiles) {
            String r = p.regime();
            if (r.equals("FROZEN")) frozen++;
            else if (r.equals("EDGE_OF_CHAOS")) edge++;
            else if (r.equals("CHAOTIC")) chaotic++;
        }
        int max = Math.max(frozen, Math.max(edge, chaotic));
        if (max == frozen) return "FROZEN";
        if (max == edge) return "EDGE_OF_CHAOS";
        return "CHAOTIC";
    }

    /**
     * Fraction of cycles where regime changed (0..1).
     */
    public static double transitionRate(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.size() < 2) return 0.0;
        return (double) detectTransitions(profiles).size() / (profiles.size() - 1);
    }
}
