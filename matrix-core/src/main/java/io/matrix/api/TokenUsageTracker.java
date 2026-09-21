package io.matrix.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 116 — Token usage tracker.
 *
 * <p>Counts tokens consumed across all generation requests for
 * cost tracking, rate limiting per user, or SLA monitoring.
 */
public final class TokenUsageTracker {

    private final AtomicLong totalInputTokens = new AtomicLong();
    private final AtomicLong totalOutputTokens = new AtomicLong();
    private final AtomicLong totalRequests = new AtomicLong();

    /** Record a generation's token usage. */
    public void record(long inputTokens, long outputTokens) {
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        totalRequests.incrementAndGet();
    }

    public long totalInputTokens() { return totalInputTokens.get(); }
    public long totalOutputTokens() { return totalOutputTokens.get(); }
    public long totalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }
    public long totalRequests() { return totalRequests.get(); }

    /** Average tokens per request. */
    public double avgTokensPerRequest() {
        long n = totalRequests.get();
        return n == 0 ? 0.0 : (double) totalTokens() / n;
    }

    /** Reset all counters. */
    public void reset() {
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        totalRequests.set(0);
    }

    public String toJson() {
        return "{"
                + "\"totalInputTokens\":" + totalInputTokens.get()
                + ",\"totalOutputTokens\":" + totalOutputTokens.get()
                + ",\"totalTokens\":" + totalTokens()
                + ",\"totalRequests\":" + totalRequests.get()
                + ",\"avgTokensPerRequest\":" + avgTokensPerRequest()
                + "}";
    }
}
