package io.matrix.consciousness;

/**
 * RUN 300 — CycleCircuitBreaker (failure circuit breaker).
 *
 * <p>If N consecutive cycles fail, the circuit opens and
 * blocks further cycles until manually reset.
 */
public final class CycleCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private int failureCount = 0;
    private final int failureThreshold;
    private State state = State.CLOSED;

    public CycleCircuitBreaker(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    /** Record a cycle result. Returns true if circuit allows. */
    public synchronized boolean record(boolean accepted) {
        if (accepted) {
            failureCount = 0;
            state = State.CLOSED;
            return true;
        }
        failureCount++;
        if (failureCount >= failureThreshold) {
            state = State.OPEN;
            return false;
        }
        return true;
    }

    public synchronized boolean isAllowed() {
        return state != State.OPEN;
    }

    public synchronized void reset() {
        failureCount = 0;
        state = State.CLOSED;
    }

    public synchronized State state() { return state; }
    public synchronized int failureCount() { return failureCount; }
}
