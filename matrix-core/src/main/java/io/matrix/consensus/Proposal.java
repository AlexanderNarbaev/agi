package io.matrix.consensus;

import java.util.UUID;

/**
 * A mutation or change proposal submitted for consensus.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3
 */
public record Proposal(
        UUID id,
        ConsensusLevel level,
        String proposerId,
        String action,
        String payload,
        long timestamp
) {
    public static Proposal create(ConsensusLevel level, String proposerId,
                                   String action, String payload) {
        return new Proposal(
                UUID.randomUUID(),
                level,
                proposerId,
                action,
                payload,
                System.currentTimeMillis());
    }
}
