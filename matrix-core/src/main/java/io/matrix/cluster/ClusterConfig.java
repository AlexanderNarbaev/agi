package io.matrix.cluster;

/**
 * Configuration for a neuron cluster.
 */
public record ClusterConfig(
        int maxNeurons,
        int signalBufferCapacity,
        long tickIntervalMs
) {
    public static ClusterConfig defaults() {
        return new ClusterConfig(1000, 10_000, 1);
    }
}
