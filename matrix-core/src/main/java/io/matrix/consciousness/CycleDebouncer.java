package io.matrix.consciousness;

/**
 * RUN 268 — CycleDebouncer (prevents cycling same input).
 *
 * <p>If the same input appears N times in a row, debouncer
 * returns false to prevent infinite loops.
 */
public final class CycleDebouncer {

    private String lastInput = "";
    private int repeatCount = 0;
    private final int maxRepeats;

    public CycleDebouncer() { this(3); }
    public CycleDebouncer(int maxRepeats) {
        this.maxRepeats = maxRepeats;
    }

    /** Returns true if input should proceed. */
    public synchronized boolean shouldProcess(String input) {
        if (input == null) return false;
        if (input.equals(lastInput)) {
            repeatCount++;
            return repeatCount < maxRepeats;
        }
        lastInput = input;
        repeatCount = 1;
        return true;
    }

    public synchronized int repeatCount() { return repeatCount; }
    public synchronized void reset() { lastInput = ""; repeatCount = 0; }
}
