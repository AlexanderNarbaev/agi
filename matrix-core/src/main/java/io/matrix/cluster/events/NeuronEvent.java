package io.matrix.cluster.events;

import io.matrix.cluster.NeuronId;

/**
 * Event recorded in the cluster event log.
 */
public record NeuronEvent(
        String eventId,
        NeuronEventType type,
        NeuronId neuronId,
        long timestamp,
        String payload
) {
    public static NeuronEvent of(NeuronEventType type, NeuronId neuronId, String payload) {
        return new NeuronEvent(
                java.util.UUID.randomUUID().toString(),
                type,
                neuronId,
                System.currentTimeMillis(),
                payload);
    }
}
