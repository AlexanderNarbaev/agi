package io.matrix.api;

/**
 * RUN 132 — StopWatch utility for measuring elapsed time.
 *
 * <p>Simple wrapper around System.nanoTime() with convenient
 * start/stop/elapsed/reset semantics.
 */
public final class StopWatch {

    private long startNanos = 0;
    private long stopNanos = 0;
    private boolean running = false;

    public StopWatch() {}

    /** Start the watch. */
    public StopWatch start() {
        startNanos = System.nanoTime();
        stopNanos = 0;
        running = true;
        return this;
    }

    /** Stop the watch. */
    public StopWatch stop() {
        if (running) {
            stopNanos = System.nanoTime();
            running = false;
        }
        return this;
    }

    /** Reset to initial state. */
    public StopWatch reset() {
        startNanos = 0;
        stopNanos = 0;
        running = false;
        return this;
    }

    public boolean isRunning() { return running; }

    /** Elapsed time in nanoseconds. */
    public long elapsedNanos() {
        if (running) {
            return System.nanoTime() - startNanos;
        }
        return stopNanos - startNanos;
    }

    /** Elapsed time in milliseconds. */
    public long elapsedMs() {
        return elapsedNanos() / 1_000_000L;
    }

    /** Elapsed time in seconds. */
    public double elapsedSeconds() {
        return elapsedNanos() / 1_000_000_000.0;
    }
}
