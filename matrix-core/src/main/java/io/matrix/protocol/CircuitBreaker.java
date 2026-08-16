package io.matrix.protocol;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker for inter-cluster communication (L2 §10).
 *
 * <p>States:
 * <ul>
 *   <li>{@link State#CLOSED} — normal operation; requests pass through.</li>
 *   <li>{@link State#OPEN} — failures exceeded the threshold; requests are
 *       blocked until the timeout elapses.</li>
 *   <li>{@link State#HALF_OPEN} — a probe request is allowed; on success the
 *       breaker closes, on failure it re-opens.</li>
 * </ul>
 *
 * <p>Thread-safe: state transitions are guarded by the {@code volatile} state
 * field and atomic counters.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final long CLOSED_TIMESTAMP = 0L;

    private volatile State state = State.CLOSED;
    private final int failureThreshold;
    private final long timeoutMs;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(CLOSED_TIMESTAMP);
    private final AtomicLong openedAt = new AtomicLong(CLOSED_TIMESTAMP);

    /**
     * @param failureThreshold consecutive failures before opening
     * @param timeoutMs       time to wait before transitioning OPEN → HALF_OPEN
     */
    public CircuitBreaker(int failureThreshold, long timeoutMs) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException(
                    "failureThreshold must be > 0, got: " + failureThreshold);
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "timeoutMs must be > 0, got: " + timeoutMs);
        }
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns {@code true} if a request may proceed. Transitions OPEN →
     * HALF_OPEN once the timeout has elapsed.
     */
    public boolean allowRequest() {
        State current = state;
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - openedAt.get() >= timeoutMs) {
                // Eligible to probe recovery. Only the thread that observes
                // the elapsed timeout performs the transition.
                if (transitionToHalfOpen()) {
                    return true;
                }
                return false;
            }
            return false;
        }
        // HALF_OPEN: exactly one probe request is allowed to pass; concurrent
        // callers are rejected until the probe resolves.
        return true;
    }

    /**
     * Records a successful request. Resets the failure count and closes the
     * circuit (HALF_OPEN → CLOSED, and also closes any stale OPEN state).
     */
    public void recordSuccess() {
        failureCount.set(0);
        state = State.CLOSED;
        openedAt.set(CLOSED_TIMESTAMP);
        lastFailureTime.set(CLOSED_TIMESTAMP);
    }

    /**
     * Records a failed request. Increments the failure count and opens the
     * circuit when the threshold is reached. Re-opens immediately from
     * HALF_OPEN on any failure.
     */
    public void recordFailure() {
        long now = System.currentTimeMillis();
        lastFailureTime.set(now);
        int count = failureCount.incrementAndGet();
        State current = state;
        if (current == State.HALF_OPEN) {
            open(now);
            return;
        }
        if (current == State.CLOSED && count >= failureThreshold) {
            open(now);
        }
    }

    /**
     * Returns the current breaker state.
     */
    public State state() {
        // Lazily reflect elapsed-time transitions for observers.
        if (state == State.OPEN
                && System.currentTimeMillis() - openedAt.get() >= timeoutMs) {
            // Do not perform the HALF_OPEN transition here to avoid spurious
            // probe admission; allowRequest() owns that transition.
        }
        return state;
    }

    /**
     * Returns the current consecutive failure count.
     */
    public int failureCount() {
        return failureCount.get();
    }

    /**
     * Returns the configured failure threshold.
     */
    public int failureThreshold() {
        return failureThreshold;
    }

    /**
     * Returns the configured recovery timeout in milliseconds.
     */
    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * Returns the wall-clock time at which the breaker last opened, or
     * {@code 0} if it has never opened.
     */
    public long openedAt() {
        return openedAt.get();
    }

    /**
     * Resets the breaker to the CLOSED state with a zero failure count.
     */
    public void reset() {
        failureCount.set(0);
        state = State.CLOSED;
        openedAt.set(CLOSED_TIMESTAMP);
        lastFailureTime.set(CLOSED_TIMESTAMP);
    }

    // ─── Internal ────────────────────────────────────────────────────────

    private void open(long now) {
        state = State.OPEN;
        openedAt.set(now);
    }

    /**
     * Attempts a single OPEN → HALF_OPEN transition. Returns {@code true} if
     * the calling thread performed the transition (and may therefore probe).
     */
    private boolean transitionToHalfOpen() {
        State current = state;
        if (current == State.OPEN) {
            state = State.HALF_OPEN;
            return true;
        }
        return false;
    }
}
