package io.matrix.cluster;

import java.util.UUID;

/**
 * Globally unique neuron identifier.
 */
public record NeuronId(UUID uuid, long generation) {

    public static NeuronId create() {
        return new NeuronId(UUID.randomUUID(), 0L);
    }

    public NeuronId nextGeneration() {
        return new NeuronId(uuid, generation + 1);
    }
}
