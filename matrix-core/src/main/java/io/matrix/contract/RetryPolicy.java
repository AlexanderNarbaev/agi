package io.matrix.contract;

/**
 * W51 — when a command may be sent again, and how long to wait.
 *
 * <p><b>What this replaces.</b> There was no retry policy, which meant the only safe behaviour
 * for a caller that lost a connection was to give up — and the only unsafe behaviour, the one
 * a caller chose when it wanted the work done, was to send again blindly. Both are wrong for an
 * idempotent command with an idempotency key: the first loses work, the second performs it
 * twice.</p>
 *
 * <p><b>Not every failure is retried.</b> A refused contract version is not retried: the
 * receiver understood the message and said no, and sending it again says the same thing. A
 * capability denial is not retried: the answer will not change. A timeout IS retried, because
 * the receiver may have completed the work and lost the reply — which is precisely why the
 * command carries an idempotency key.</p>
 *
 * <p><b>The backoff is bounded.</b> An unbounded exponential backoff in a cognitive loop is a
 * way to make a system stop thinking without saying so.</p>
 *
 * @param maxAttempts        the total number of attempts, including the first
 * @param initialBackoffMillis the wait before the second attempt
 * @param backoffMultiplier  the factor the wait grows by
 * @param maxBackoffMillis   the ceiling on the wait
 */
public record RetryPolicy(
        int maxAttempts,
        long initialBackoffMillis,
        double backoffMultiplier,
        long maxBackoffMillis) {

    /** The factor an exponential backoff grows by. CONVENTION: two. */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    /** A policy that sends a command once and never again. */
    public static final RetryPolicy NONE = new RetryPolicy(1, 0L, DEFAULT_MULTIPLIER, 0L);

    /** A policy that sends a command up to three times with a growing wait. */
    public static final RetryPolicy STANDARD =
            new RetryPolicy(3, 50L, DEFAULT_MULTIPLIER, 2_000L);

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("max attempts must be at least one");
        }
        if (initialBackoffMillis < 0L) {
            throw new IllegalArgumentException("initial backoff must not be negative");
        }
        if (backoffMultiplier < 1.0 || Double.isNaN(backoffMultiplier)) {
            throw new IllegalArgumentException("backoff multiplier must be at least one");
        }
        if (maxBackoffMillis < initialBackoffMillis) {
            throw new IllegalArgumentException("max backoff must not be below the initial backoff");
        }
    }

    /**
     * The wait before the given attempt.
     *
     * @param attempt the one-based number of the attempt about to be made
     * @return the wait in milliseconds
     */
    public long backoffMillis(int attempt) {
        if (attempt <= 1) {
            return 0L;
        }
        double scaled = initialBackoffMillis * Math.pow(backoffMultiplier, attempt - 2.0);
        long capped = scaled >= maxBackoffMillis ? maxBackoffMillis : (long) scaled;
        return Math.min(capped, maxBackoffMillis);
    }

    /**
     * Whether another attempt is permitted.
     *
     * @param attemptsSoFar the number of attempts already made
     * @return true when one more attempt is allowed
     */
    public boolean mayRetry(int attemptsSoFar) {
        return attemptsSoFar < maxAttempts;
    }

    @Override
    public String toString() {
        return "RetryPolicy[attempts=" + maxAttempts + ", backoff=" + initialBackoffMillis
                + "ms x" + backoffMultiplier + ", cap=" + maxBackoffMillis + "ms]";
    }
}
