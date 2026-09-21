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

    public static ClusterConfig forSize(int neuronCount) {
        return new ClusterConfig(neuronCount, neuronCount * 10, 1);
    }
}
