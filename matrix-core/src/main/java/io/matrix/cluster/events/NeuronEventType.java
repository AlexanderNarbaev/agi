package io.matrix.cluster.events;

/**
 * Types of neuron lifecycle events.
 */
public enum NeuronEventType {
    NEURON_CREATED,
    NEURON_MUTATED,
    NEURON_FROZEN,
    NEURON_REMOVED,
    SIGNAL_EMITTED,
    BATCH_EVALUATED
}
