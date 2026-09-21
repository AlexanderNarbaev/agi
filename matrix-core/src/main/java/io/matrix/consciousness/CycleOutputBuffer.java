package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 294 — CycleOutputBuffer (output buffering).
 *
 * <p>Buffers cycle results and releases them in batches.
 */
public final class CycleOutputBuffer {

    private final List<BrainLoopService.CycleResult> buffer = new ArrayList<>();
    private final int maxBuffer;

    public CycleOutputBuffer(int maxBuffer) {
        this.maxBuffer = maxBuffer;
    }

    public synchronized void add(BrainLoopService.CycleResult result) {
        if (buffer.size() >= maxBuffer) {
            buffer.remove(0); // FIFO eviction
        }
        buffer.add(result);
    }

    public synchronized List<BrainLoopService.CycleResult> drain() {
        List<BrainLoopService.CycleResult> out = new ArrayList<>(buffer);
        buffer.clear();
        return out;
    }

    public synchronized List<BrainLoopService.CycleResult> snapshot() {
        return new ArrayList<>(buffer);
    }

    public synchronized int size() { return buffer.size(); }
    public synchronized void clear() { buffer.clear(); }
}
