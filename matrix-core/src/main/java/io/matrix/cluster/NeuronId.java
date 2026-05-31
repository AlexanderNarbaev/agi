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

    @Override
    public String toString() {
        return uuid + ":" + generation;
    }

    public static NeuronId parse(String s) {
        String[] parts = s.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid NeuronId: " + s);
        return new NeuronId(UUID.fromString(parts[0]), Long.parseLong(parts[1]));
    }
}
