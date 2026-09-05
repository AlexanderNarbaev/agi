package io.matrix.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 99 — Token-bucket rate limiter.
 *
 * <p>For controlling API request rates per-user or per-IP. Implements
 * a simple token bucket: tokens refill at a steady rate, requests
 * consume tokens.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code capacity}: maximum bucket size (burst limit)</li>
 *   <li>{@code refillPerSecond}: tokens added per second</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * RateLimiter limiter = new RateLimiter(10, 1.0);  // 10 burst, 1 req/s
 * if (limiter.tryAcquire("user-1")) {
 *     // allow request
 * } else {
 *     // reject with 429
 * }
 * }</pre>
 */
public final class RateLimiter {

    private final long capacity;
    private final double refillPerSecondNanos;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalRejections = new AtomicLong();

    public RateLimiter(long capacity, double refillPerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (refillPerSecond <= 0) throw new IllegalArgumentException("refill must be > 0");
        this.capacity = capacity;
        this.refillPerSecondNanos = refillPerSecond / 1_000_000_000.0;
    }

    /** Try to acquire a token for the given key. */
    public boolean tryAcquire(String key) {
        totalRequests.incrementAndGet();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        synchronized (bucket) {
            long now = System.nanoTime();
            long elapsed = now - bucket.lastRefillNanos;
            double refilled = elapsed * refillPerSecondNanos;
            bucket.tokens = Math.min(capacity, bucket.tokens + refilled);
            bucket.lastRefillNanos = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            totalRejections.incrementAndGet();
            return false;
        }
    }

    /** Wait up to maxWaitNanos for a token. Returns true if acquired. */
    public boolean tryAcquire(String key, long maxWaitNanos) {
        long deadline = System.nanoTime() + maxWaitNanos;
        while (System.nanoTime() < deadline) {
            if (tryAcquire(key)) return true;
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Reset the bucket for a key. */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** Reset all buckets. */
    public void resetAll() {
        buckets.clear();
    }

    public long totalRequests() { return totalRequests.get(); }
    public long totalRejections() { return totalRejections.get(); }
    public double rejectionRate() {
        long t = totalRequests.get();
        return t == 0 ? 0.0 : (double) totalRejections.get() / t;
    }
    public int activeBuckets() { return buckets.size(); }
    public long capacity() { return capacity; }
    public double refillPerSecond() { return refillPerSecondNanos * 1_000_000_000.0; }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        Bucket(long capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }
    }
}
