package io.matrix.events;

import io.matrix.cluster.NeuronId;

import java.util.UUID;

/**
 * Domain event recording a state change in the cluster.
 *
 * <p>Replaces the older {@code NeuronEvent} with a general-purpose event
 * that includes instanceId for multi-instance scenarios.
 *
 * <p>Ref: L6_Memory.md §3.1, §3.2
 */
public record ClusterEvent(
        String eventId,
        ClusterEventType type,
        String instanceId,
        NeuronId neuronId,
        long timestamp,
        String payload
) {
    public static ClusterEvent of(ClusterEventType type, String instanceId,
                                   NeuronId neuronId, String payload) {
        return new ClusterEvent(
                UUID.randomUUID().toString(),
                type,
                instanceId,
                neuronId,
                System.currentTimeMillis(),
                payload);
    }
}
