package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 293 — CycleInputBuffer (input buffering).
 *
 * <p>Buffers inputs and releases them in batches.
 */
public final class CycleInputBuffer {

    private final List<String> buffer = new ArrayList<>();
    private final int batchSize;

    public CycleInputBuffer(int batchSize) {
        this.batchSize = batchSize;
    }

    public synchronized void add(String input) {
        buffer.add(input);
    }

    /** Returns true if buffer has enough for a batch. */
    public synchronized boolean ready() {
        return buffer.size() >= batchSize;
    }

    /** Drain one batch (up to batchSize). */
    public synchronized List<String> drain() {
        int count = Math.min(batchSize, buffer.size());
        List<String> batch = new ArrayList<>(buffer.subList(0, count));
        buffer.subList(0, count).clear();
        return batch;
    }

    public synchronized int size() { return buffer.size(); }
    public synchronized void clear() { buffer.clear(); }
}
