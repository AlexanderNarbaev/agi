package io.matrix.federated;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Protocol for federated learning rounds.
 */
@ApplicationScoped
public class FederatedProtocol {

    private int rounds = 10;
    private int minParticipants = 2;

    /**
     * Run federated training round.
     */
    public FederatedRound runRound(List<LocalUpdate> updates) {
        if (updates.size() < minParticipants) {
            throw new IllegalStateException(
                    "Not enough participants: " + updates.size() + " < " + minParticipants);
        }

        // Aggregate updates
        boolean[] aggregated = aggregate(updates);
        
        return new FederatedRound(
                System.currentTimeMillis(),
                updates.size(),
                aggregated,
                computeAverageLoss(updates)
        );
    }

    /**
     * Aggregate local updates using XOR.
     */
    private boolean[] aggregate(List<LocalUpdate> updates) {
        if (updates.isEmpty()) return new boolean[0];
        
        int maxLen = updates.stream()
                .mapToInt(u -> u.update().length)
                .max().orElse(0);
        
        boolean[] result = new boolean[maxLen];
        for (LocalUpdate update : updates) {
            for (int i = 0; i < update.update().length; i++) {
                result[i] ^= update.update()[i];
            }
        }
        return result;
    }

    private double computeAverageLoss(List<LocalUpdate> updates) {
        return updates.stream()
                .mapToDouble(LocalUpdate::loss)
                .average()
                .orElse(0.0);
    }

    public int getRounds() { return rounds; }
    public int getMinParticipants() { return minParticipants; }

    /**
     * Record of a federated training round.
     */
    public record FederatedRound(
            long timestamp,
            int participantCount,
            boolean[] aggregatedModel,
            double averageLoss
    ) {}
}
