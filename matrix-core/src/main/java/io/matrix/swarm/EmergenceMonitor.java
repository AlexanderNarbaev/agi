package io.matrix.swarm;

import java.util.*;

/**
 * W951 — Emergence Monitor.
 *
 * Detects novel strategies and behaviors not programmed explicitly.
 * Identifies "swarm insights" — emergent solutions to complex tasks.
 */
public final class EmergenceMonitor {

    public record Insight(
            long timestamp,
            String type, // "strategy", "communication", "optimization"
            String description,
            double noveltyScore, // 0-1, how new/surprising
            List<String> participants
    ) {}

    private final List<Insight> insights = new ArrayList<>();
    private final Set<String> knownStrategies = new HashSet<>();

    /**
     * Record an observed behavior and check for emergence.
     */
    public boolean recordBehavior(String behavior, List<String> participants) {
        if (knownStrategies.contains(behavior)) {
            return false; // Not novel
        }

        knownStrategies.add(behavior);
        double novelty = calculateNovelty(behavior, participants);

        if (novelty > 0.6) {
            insights.add(new Insight(
                System.currentTimeMillis(),
                "strategy",
                "New behavior observed: " + behavior,
                novelty,
                participants
            ));
            return true; // Emergent!
        }
        return false;
    }

    /**
     * Calculate novelty score.
     */
    private double calculateNovelty(String behavior, List<String> participants) {
        // More participants = more likely to be emergent
        double participantScore = Math.min(1.0, participants.size() / 10.0);
        // First-occurrence bonus
        double firstOccurrenceBonus = 0.5;
        return Math.min(1.0, participantScore + firstOccurrenceBonus);
    }

    public List<Insight> getInsights() {
        return new ArrayList<>(insights);
    }

    public int getKnownStrategyCount() {
        return knownStrategies.size();
    }
}
