package io.matrix.api;

/**
 * RUN 100 — Exponential backoff utility.
 *
 * <p>Computes delay between retries using exponential growth with
 * optional jitter.
 *
 * <p>Standard pattern:
 * <pre>{@code
 * BackoffPolicy policy = new BackoffPolicy(100, 5000, 2.0, true);
 * for (int attempt = 0; attempt < maxAttempts; attempt++) {
 *     try {
 *         return operation();
 *     } catch (Exception e) {
 *         long delayMs = policy.delayMs(attempt);
 *         Thread.sleep(delayMs);
 *     }
 * }
 * }</pre>
 */
public final class BackoffPolicy {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final boolean jitter;

    public BackoffPolicy(long initialDelayMs, long maxDelayMs,
                         double multiplier, boolean jitter) {
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("initial must be > 0");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("max must be >= initial");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.jitter = jitter;
    }

    /**
     * Compute delay for the given attempt number (0-based).
     *
     * <p>{@code attempt 0} → initial delay (or with jitter).
     * {@code attempt N} → initial * multiplier^N, capped at max.
     */
    public long delayMs(int attempt) {
        double delay = initialDelayMs * Math.pow(multiplier, attempt);
        delay = Math.min(delay, maxDelayMs);
        if (jitter) {
            // Add ±25% jitter
            double jitterFactor = 0.75 + Math.random() * 0.5;
            delay = delay * jitterFactor;
        }
        return (long) delay;
    }

    public long initialDelayMs() { return initialDelayMs; }
    public long maxDelayMs() { return maxDelayMs; }
    public double multiplier() { return multiplier; }
    public boolean jitter() { return jitter; }
}
