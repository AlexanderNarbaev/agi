package io.matrix.lifecycle;

import java.util.HashMap;
import java.util.Map;

/**
 * ConsolidationCycle — the deterministic «sleep» window of DESIGN-07 §4:
 * background route backlogs are drained in bounded batches while the cycle
 * is open; closing produces a drain summary. No wall-clock, no randomness —
 * batch sizes are supplied by the caller.
 */
public final class ConsolidationCycle {

    /** Aggregate result of one sleep window. */
    public record DrainSummary(int routesDrained, long itemsMigrated) {}

    private final Map<String, Integer> backlogs = new HashMap<>();
    private boolean open;
    private long migratedTotal;

    /** Opens the cycle; initial backlogs are registered here. */
    public void open(Map<String, Integer> initialBacklogs) {
        if (open) {
            throw new IllegalStateException("cycle_already_open");
        }
        // Validate everything before mutating state (no dirty open on failure).
        for (var entry : initialBacklogs.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "invalid backlog for " + entry.getKey() + ": " + entry.getValue());
            }
        }
        open = true;
        migratedTotal = 0;
        backlogs.clear();
        backlogs.putAll(initialBacklogs);
    }

    /**
     * Drains up to {@code batchSize} items from the route.
     *
     * @return number of items actually drained
     */
    public int drain(String route, int batchSize) {
        if (!open) {
            throw new IllegalStateException("cycle_closed");
        }
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize must be ≥ 0");
        }
        int pending = backlogs.getOrDefault(route, 0);
        int drained = Math.min(pending, batchSize);
        backlogs.put(route, pending - drained);
        migratedTotal += drained;
        return drained;
    }

    /** Closes the window and reports what was accomplished. */
    public DrainSummary close() {
        if (!open) {
            throw new IllegalStateException("cycle_closed");
        }
        open = false;
        int routesDrained = (int) backlogs.values().stream().filter(v -> v == 0).count();
        return new DrainSummary(routesDrained, migratedTotal);
    }

    /** Remaining backlog of a route (0 for unknown routes). */
    public int backlog(String route) {
        return backlogs.getOrDefault(route, 0);
    }
}
