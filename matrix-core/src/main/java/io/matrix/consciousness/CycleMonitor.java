package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 297 — CycleMonitor (cycle monitoring).
 *
 * <p>Monitors cycle execution and records metrics.
 */
public final class CycleMonitor {

    public record Metric(int cycle, double arousal, int focusCount,
                          boolean accepted) {}

    private final List<Metric> metrics = new ArrayList<>();
    private int cycleNumber = 0;

    public synchronized void record(BrainLoopService.CycleResult r) {
        metrics.add(new Metric(++cycleNumber, r.arousal(),
                r.focusCount(), r.accepted()));
    }

    public synchronized int size() { return metrics.size(); }

    public synchronized List<Metric> metrics() {
        return new ArrayList<>(metrics);
    }

    public synchronized Metric last() {
        return metrics.isEmpty() ? null : metrics.get(metrics.size() - 1);
    }

    public synchronized void clear() { metrics.clear(); cycleNumber = 0; }
}
