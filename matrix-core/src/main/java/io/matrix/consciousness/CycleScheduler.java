package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 296 — CycleScheduler (cycle scheduling).
 *
 * <p>Schedules cycles to run at specific intervals.
 */
public final class CycleScheduler {

    public record ScheduledCycle(int cycleNumber, String input, int priority) {}

    private final List<ScheduledCycle> schedule = new ArrayList<>();
    private int nextCycle = 0;

    public synchronized void schedule(String input, int priority) {
        schedule.add(new ScheduledCycle(++nextCycle, input, priority));
    }

    public synchronized List<ScheduledCycle> pending() {
        return new ArrayList<>(schedule);
    }

    public synchronized ScheduledCycle next() {
        if (schedule.isEmpty()) return null;
        // Sort by priority (descending), then by cycle number
        schedule.sort((a, b) -> {
            int cmp = Integer.compare(b.priority(), a.priority());
            return cmp != 0 ? cmp : Integer.compare(a.cycleNumber(), b.cycleNumber());
        });
        return schedule.remove(0);
    }

    public synchronized int size() { return schedule.size(); }
    public synchronized void clear() { schedule.clear(); }
}
