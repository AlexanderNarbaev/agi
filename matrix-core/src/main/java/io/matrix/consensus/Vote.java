package io.matrix.consensus;

import java.util.UUID;

/**
 * A signed vote on a proposal.
 *
 * <p>Weight is computed by {@link WeightCalculator} based on the voter's
 * accuracy, uptime, and contribution history.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3
 */
public record Vote(
        UUID id,
        UUID proposalId,
        String voterId,
        boolean approve,
        double weight,
        long timestamp
) {
    public static Vote approve(UUID proposalId, String voterId, double weight) {
        return new Vote(UUID.randomUUID(), proposalId, voterId, true,
                weight, System.currentTimeMillis());
    }

    public static Vote reject(UUID proposalId, String voterId, double weight) {
        return new Vote(UUID.randomUUID(), proposalId, voterId, false,
                weight, System.currentTimeMillis());
    }
}
