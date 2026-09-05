package io.matrix.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 121 — Per-endpoint request counter.
 *
 * <p>Tracks how many times each REST endpoint is hit, with a
 * thread-safe counter map.
 */
public final class RequestCounter {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errors = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();

    public void recordRequest(String endpoint) {
        counts.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void recordError(String endpoint) {
        errors.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        totalErrors.incrementAndGet();
    }

    public long count(String endpoint) {
        AtomicLong c = counts.get(endpoint);
        return c == null ? 0 : c.get();
    }

    public long errorCount(String endpoint) {
        AtomicLong c = errors.get(endpoint);
        return c == null ? 0 : c.get();
    }

    public long totalRequests() { return totalRequests.get(); }
    public long totalErrors() { return totalErrors.get(); }

    public double errorRate(String endpoint) {
        long req = count(endpoint);
        if (req == 0) return 0.0;
        return (double) errorCount(endpoint) / req;
    }

    public double totalErrorRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : (double) totalErrors.get() / total;
    }

    public void reset() {
        counts.clear();
        errors.clear();
        totalRequests.set(0);
        totalErrors.set(0);
    }

    public java.util.Set<String> endpoints() {
        return java.util.Set.copyOf(counts.keySet());
    }
}
