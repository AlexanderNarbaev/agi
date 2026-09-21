package io.matrix.consciousness;

/**
 * RUN 240 — CycleBudget (per-window cycle budget).
 *
 * <p>Limits cycles within a window of time or count. Useful
 * for preventing runaway brain activity.
 */
public final class CycleBudget {

    public record BudgetState(int allowed, int used, int remaining) {}

    private final int maxCycles;
    private int used = 0;

    public CycleBudget(int maxCycles) {
        if (maxCycles <= 0) maxCycles = 1;
        this.maxCycles = maxCycles;
    }

    public synchronized boolean tryConsume() {
        if (used >= maxCycles) return false;
        used++;
        return true;
    }

    public synchronized int remaining() {
        return Math.max(0, maxCycles - used);
    }

    public synchronized int used() { return used; }

    public synchronized int max() { return maxCycles; }

    public synchronized void reset() { used = 0; }

    public synchronized BudgetState snapshot() {
        return new BudgetState(maxCycles, used, remaining());
    }
}
