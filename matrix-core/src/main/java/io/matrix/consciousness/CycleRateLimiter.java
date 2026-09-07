package io.matrix.consciousness;

/**
 * RUN 302 — CycleRateLimiter (token bucket rate limiter).
 *
 * <p>Implements token bucket algorithm for rate limiting cycles.
 * Tokens refill at a fixed rate; each cycle consumes one token.
 */
public final class CycleRateLimiter {

    private final int maxTokens;
    private final double refillRate; // tokens per second
    private double tokens;
    private long lastRefillNanos;

    public CycleRateLimiter(int maxTokens, double refillRate) {
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
        this.tokens = maxTokens;
        this.lastRefillNanos = System.nanoTime();
    }

    /** Returns true if a token was consumed. */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(maxTokens, tokens + elapsed * refillRate);
        lastRefillNanos = now;
    }

    public synchronized double availableTokens() {
        refill();
        return tokens;
    }
}
