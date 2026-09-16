package io.matrix.cognitive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * W93 — Deterministic bounded ring buffer of cognitive errors (DESIGN-64).
 *
 * <p>Records errors by cycle. Oldest entries are evicted when capacity is reached.
 * The buffer is purely deterministic: same input sequence → same output state.
 *
 * <p>Used by {@link io.matrix.neuron.ConsciousBrain} to track failure modes
 * across an episode. The episode-end {@link #snapshot()} can be hashed to
 * produce a deterministic episode signature.
 */
public final class CognitiveErrorStream implements Iterable<CognitiveError> {

    private final int capacity;
    private final CognitiveError[] ring;
    private int writeIdx = 0;
    private int count = 0;

    public CognitiveErrorStream(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
        this.capacity = capacity;
        this.ring = new CognitiveError[capacity];
    }

    /** Append one error. Evicts oldest if full. */
    public void record(CognitiveError error) {
        if (error == null) throw new IllegalArgumentException("null error");
        ring[writeIdx] = error;
        writeIdx = (writeIdx + 1) % capacity;
        if (count < capacity) count++;
    }

    /** Total errors ever recorded, including evicted ones. */
    public long totalRecorded() {
        // We don't track this in the buffer itself; consumers track externally.
        return -1;  // sentinel
    }

    /** Current size (≤ capacity). */
    public int size() {
        return count;
    }

    /** True if buffer is empty. */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Capacity. */
    public int capacity() {
        return capacity;
    }

    /**
     * Iterate over errors in chronological order (oldest first).
     */
    @Override
    public Iterator<CognitiveError> iterator() {
        List<CognitiveError> ordered = new ArrayList<>(count);
        int start = (count < capacity) ? 0 : writeIdx;
        for (int i = 0; i < count; i++) {
            ordered.add(ring[(start + i) % capacity]);
        }
        return ordered.iterator();
    }

    /**
     * Snapshot the current state as a deterministic hash. Useful as a
     * fingerprint of the brain's failure history.
     */
    public long snapshotHash() {
        long h = 1469598103934665603L;
        for (CognitiveError e : this) {
            h = (h ^ e.deterministicHash()) * 1099511628211L;
        }
        return h;
    }

    /** Count of distinct error kinds currently in the stream. */
    public int distinctKindCount() {
        java.util.Set<CognitiveError.ErrorKind> seen =
                new java.util.HashSet<>();
        for (CognitiveError e : this) seen.add(e.kind());
        return seen.size();
    }

    /** Most recent error (or null if empty). */
    public CognitiveError mostRecent() {
        if (count == 0) return null;
        int lastIdx = (writeIdx - 1 + capacity) % capacity;
        return ring[lastIdx];
    }
}
