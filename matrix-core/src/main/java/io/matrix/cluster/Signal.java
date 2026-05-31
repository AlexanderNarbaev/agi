package io.matrix.cluster;

/**
 * A signal packet between neurons.
 */
public record Signal(
        NeuronId sourceId,
        NeuronId targetId,
        boolean value,
        long timestamp
) {
    public Signal(NeuronId sourceId, NeuronId targetId, boolean value) {
        this(sourceId, targetId, value, System.currentTimeMillis());
    }
}
