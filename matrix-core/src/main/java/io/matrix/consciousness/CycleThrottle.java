package io.matrix.consciousness;

/**
 * RUN 269 — CycleThrottle (rate limiting by count per window).
 *
 * <p>Limits the number of cycles within a time window. If the
 * count exceeds the limit, returns false.
 */
public final class CycleThrottle {

    private int count = 0;
    private final int limit;

    public CycleThrottle(int limit) {
        if (limit <= 0) limit = 1;
        this.limit = limit;
    }

    /** Returns true if allowed. */
    public synchronized boolean allow() {
        if (count >= limit) return false;
        count++;
        return true;
    }

    public synchronized void resetWindow() { count = 0; }
    public synchronized int used() { return count; }
    public synchronized int remaining() { return Math.max(0, limit - count); }
}
