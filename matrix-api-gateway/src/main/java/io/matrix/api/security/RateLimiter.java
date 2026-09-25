package io.matrix.api.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WAVE T-02 — Token-bucket rate limiter (per user, sliding window).
 *
 * <p>Simple in-memory implementation suitable for single-node deployments.
 * T-02.5 will replace with Redis-backed token bucket for horizontal scaling.</p>
 *
 * <p><b>Limits by plan:</b></p>
 * <ul>
 *   <li>FREE: 100 req/hr</li>
 *   <li>PRO: 1,000 req/hr</li>
 *   <li>ENTERPRISE: unlimited</li>
 * </ul>
 */
public final class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final long windowMs;

    public RateLimiter() {
        this(60L * 60L * 1000L);  // 1-hour window
    }

    /** Constructor with custom window — used in tests. */
    public RateLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    public boolean tryAcquire(String userId, JwtAuthFilter.Plan plan) {
        if (plan == JwtAuthFilter.Plan.ENTERPRISE) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(userId, (k, existing) -> {
            if (existing == null || now - existing.startMs >= windowMs) {
                return new Window(now, 0);
            }
            return existing;
        });
        if (w.count >= plan.requestsPerHour) {
            return false;
        }
        // Re-create with incremented count (Window is a record → immutable)
        windows.put(userId, new Window(w.startMs, w.count + 1));
        return true;
    }

    /** Returns current usage 0..1, used for {@code X-RateLimit-Used} headers. */
    public double usage(String userId, JwtAuthFilter.Plan plan) {
        Window w = windows.get(userId);
        if (w == null) return 0.0;
        if (plan.requestsPerHour == Integer.MAX_VALUE) return 0.0;
        return (double) w.count / plan.requestsPerHour;
    }

    /** Test helper: clear all counters. */
    public void reset() {
        windows.clear();
    }

    private record Window(long startMs, int count) {}
}
