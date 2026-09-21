package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 430 — Token bucket rate limiter (pure-function discrete-time variant).
 * <p>Each call refills {@code refillRate} tokens up to {@code capacity};
 * {@code take(n)} succeeds iff {@code n <= tokens}. Pure function given a
 * deterministic clock via {@code currentMillis}.
 * CONSTITUTION I-safe.
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillRatePerMs;
    private double tokens;
    private long lastRefillMillis;

    /**
     * @param capacity       maximum bucket size
     * @param refillRate     tokens per second
     * @param nowMillis      current time
     */
    public TokenBucket(double capacity, double refillRate, long nowMillis) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity");
        if (refillRate <= 0) throw new IllegalArgumentException("refillRate");
        this.capacity = capacity;
        this.refillRatePerMs = refillRate / 1000.0;
        this.tokens = capacity;  // start full
        this.lastRefillMillis = nowMillis;
    }

    /** Take {@code n} tokens if available; returns true iff request allowed. */
    public synchronized boolean take(double n, long nowMillis) {
        if (nowMillis < lastRefillMillis) nowMillis = lastRefillMillis;  // monotonic
        double delta = (nowMillis - lastRefillMillis) * refillRatePerMs;
        tokens = Math.min(capacity, tokens + delta);
        lastRefillMillis = nowMillis;
        if (tokens >= n) {
            tokens -= n;
            return true;
        }
        return false;
    }

    public double tokens(long nowMillis) {
        double delta = (nowMillis - lastRefillMillis) * refillRatePerMs;
        return Math.min(capacity, tokens + delta);
    }
}
