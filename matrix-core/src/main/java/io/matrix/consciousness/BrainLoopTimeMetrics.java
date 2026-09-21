package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 255 — BrainLoopTimeMetrics (cycle timestamps).
 *
 * <p>Tracks per-cycle timestamps using System.nanoTime (allowed
 * for instrumentation per DecisionPathAuditor exemption).
 * Provides inter-cycle deltas.
 */
public final class BrainLoopTimeMetrics {

    public record CycleTimestamp(int cycleNumber, long nanoTime,
                                 long deltaMicros) {}

    private final List<CycleTimestamp> timestamps = new ArrayList<>();
    private long lastNanos = 0;

    public synchronized CycleTimestamp record(int cycleNumber) {
        long now = System.nanoTime();
        long deltaMicros = lastNanos == 0 ? 0
                : (now - lastNanos) / 1000L;
        CycleTimestamp ts = new CycleTimestamp(cycleNumber, now, deltaMicros);
        timestamps.add(ts);
        lastNanos = now;
        return ts;
    }

    public synchronized int size() { return timestamps.size(); }

    public synchronized List<CycleTimestamp> all() {
        return new ArrayList<>(timestamps);
    }

    public synchronized long totalMicros() {
        if (timestamps.size() < 2) return 0;
        long first = timestamps.get(0).nanoTime();
        long last = timestamps.get(timestamps.size() - 1).nanoTime();
        return (last - first) / 1000L;
    }

    public synchronized double avgDeltaMicros() {
        if (timestamps.isEmpty()) return 0;
        long total = 0;
        int count = 0;
        for (var t : timestamps) {
            total += t.deltaMicros();
            count++;
        }
        return count == 0 ? 0 : (double) total / count;
    }
}
