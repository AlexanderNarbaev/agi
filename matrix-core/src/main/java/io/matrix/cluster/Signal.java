package io.matrix.cluster;

/**
 * A signal packet between neurons.
 */
public record Signal(
        NeuronId sourceId,
        NeuronId targetId,
        boolean value,
        long timestamp,
        int priority
) {
    public Signal(NeuronId sourceId, NeuronId targetId, boolean value) {
        this(sourceId, targetId, value, System.currentTimeMillis(), 0);
    }

    public Signal(NeuronId sourceId, NeuronId targetId, boolean value, long timestamp) {
        this(sourceId, targetId, value, timestamp, 0);
    }

    public Signal(NeuronId sourceId, NeuronId targetId, boolean value, int priority) {
        this(sourceId, targetId, value, System.currentTimeMillis(), priority);
    }

    public boolean isHighPriority() {
        return priority > 0;
    }
}
