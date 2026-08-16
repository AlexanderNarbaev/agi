package io.matrix.events;

import java.util.List;

/**
 * Append-only event journal for Event Sourcing.
 *
 * <p>Events are immutable, ordered, and replayable. Used by
 * {@code NeuronClusterActor} to record all state changes.
 *
 * <p>Ref: L6_Memory.md §3
 */
public interface EventJournal {

    /**
     * Appends an event to the journal.
     *
     * @return the index of the appended event (0-based)
     */
    long append(ClusterEvent event);

    /**
     * Returns all events from the given index (inclusive).
     */
    List<ClusterEvent> replayFrom(long fromIndex);

    /**
     * Returns all events in the journal.
     */
    List<ClusterEvent> replayAll();

    /**
     * Returns the number of events in the journal.
     */
    long size();
}
