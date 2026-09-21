package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 291 — CycleLogger (cycle logging).
 *
 * <p>Logs cycle inputs and outputs for debugging.
 */
public final class CycleLogger {

    public record LogEntry(int cycle, String input, String output,
                           boolean accepted) {}

    private final List<LogEntry> entries = new ArrayList<>();
    private int cycleNumber = 0;

    public synchronized void log(String input, BrainLoopService.CycleResult result) {
        entries.add(new LogEntry(++cycleNumber, input, result.action(),
                result.accepted()));
    }

    public synchronized int size() { return entries.size(); }

    public synchronized List<LogEntry> entries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() { entries.clear(); cycleNumber = 0; }

    /** Get last N entries. */
    public synchronized List<LogEntry> recent(int n) {
        int start = Math.max(0, entries.size() - n);
        return new ArrayList<>(entries.subList(start, entries.size()));
    }
}
