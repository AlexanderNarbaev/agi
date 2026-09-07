package io.matrix.consciousness;

/**
 * RUN 299 — CycleWatchdog (timeout detection).
 *
 * <p>Detects if a cycle has been running too long.
 * Returns true if the cycle should be terminated.
 */
public final class CycleWatchdog {

    private long cycleStartTime = 0;
    private final long timeoutNanos;

    public CycleWatchdog(long timeoutMillis) {
        this.timeoutNanos = timeoutMillis * 1_000_000L;
    }

    public synchronized void startCycle() {
        cycleStartTime = System.nanoTime();
    }

    /** Returns true if cycle has exceeded timeout. */
    public synchronized boolean isExpired() {
        if (cycleStartTime == 0) return false;
        return (System.nanoTime() - cycleStartTime) > timeoutNanos;
    }

    public synchronized void endCycle() {
        cycleStartTime = 0;
    }

    public synchronized long elapsedNanos() {
        if (cycleStartTime == 0) return 0;
        return System.nanoTime() - cycleStartTime;
    }
}
