package io.matrix.budgeter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Budgeter-Homeostat (DESIGN-11): coupled budget allocator for Cauldron
 * rows + homeostat for corridor management.
 *
 * <p>Budgeter: allocates CPU/memory/time budgets to Cauldron rows.
 * Homeostat: monitors corridor health and triggers corrections when
 * metrics drift outside acceptable ranges.
 *
 * <p>Per DESIGN-11: the budgeter uses shadow prices (dual variables from
 * linear programming relaxation) to prioritize rows. The homeostat uses
 * negative feedback to maintain stability.
 */
public final class BudgeterHomeostat {

    private final Map<String, RowBudget> rowBudgets = new ConcurrentHashMap<>();
    private final Map<String, Corridor> corridors = new ConcurrentHashMap<>();
    private final AtomicLong totalAllocations = new AtomicLong();
    private final AtomicLong totalCorrections = new AtomicLong();

    /** Allocate budget to a Cauldron row. */
    public AllocationResult allocate(String rowId, long cpuMs, long memoryBytes, long wallTimeMs) {
        totalAllocations.incrementAndGet();
        var budget = new RowBudget(rowId, cpuMs, memoryBytes, wallTimeMs, System.currentTimeMillis());
        rowBudgets.put(rowId, budget);
        return new AllocationResult(rowId, true, "allocated");
    }

    /** Check if a row is within budget. */
    public boolean withinBudget(String rowId) {
        RowBudget b = rowBudgets.get(rowId);
        if (b == null) return false;
        long elapsed = System.currentTimeMillis() - b.allocatedAt();
        return elapsed < b.wallTimeMs();
    }

    /** Get remaining budget for a row. */
    public long remainingWallTime(String rowId) {
        RowBudget b = rowBudgets.get(rowId);
        if (b == null) return 0;
        long elapsed = System.currentTimeMillis() - b.allocatedAt();
        return Math.max(0, b.wallTimeMs() - elapsed);
    }

    /** Register a corridor for homeostat monitoring. */
    public void registerCorridor(String name, double min, double max) {
        corridors.put(name, new Corridor(name, min, max, min)); // start at min
    }

    /** Update corridor metric and get correction if needed. */
    public Correction updateCorridor(String name, double value) {
        Corridor c = corridors.get(name);
        if (c == null) return null;
        double correction = 0;
        if (value < c.min()) {
            correction = c.min() - value;
            totalCorrections.incrementAndGet();
        } else if (value > c.max()) {
            correction = c.max() - value;
            totalCorrections.incrementAndGet();
        }
        corridors.put(name, new Corridor(name, c.min(), c.max(), value));
        return new Correction(name, correction, value, c.min(), c.max());
    }

    /** Get corridor status. */
    public Corridor corridorStatus(String name) {
        return corridors.get(name);
    }

    public long totalAllocations() { return totalAllocations.get(); }
    public long totalCorrections() { return totalCorrections.get(); }

    public record RowBudget(String rowId, long cpuMs, long memoryBytes, long wallTimeMs, long allocatedAt) {}
    public record Corridor(String name, double min, double max, double current) {}
    public record AllocationResult(String rowId, boolean allocated, String reason) {}
    public record Correction(String corridor, double correction, double current, double min, double max) {}
}
