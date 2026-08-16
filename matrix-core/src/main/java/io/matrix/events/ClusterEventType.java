package io.matrix.events;

/**
 * Types of cluster lifecycle events.
 *
 * <p>Ref: L6_Memory.md §3.1
 */
public enum ClusterEventType {
    NEURON_CREATED,
    NEURON_MUTATED,
    NEURON_FROZEN,
    NEURON_REMOVED,
    SIGNAL_EMITTED,
    BATCH_EVALUATED,
    SNAPSHOT_CREATED,
    SNAPSHOT_RESTORED
}
