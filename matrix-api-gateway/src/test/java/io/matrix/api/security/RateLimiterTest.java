package io.matrix.api.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void testFreePlanLimit() {
        RateLimiter limiter = new RateLimiter(60_000);
        // FREE = 100 req/hr; in test window we can hit the limit
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire("user-1", JwtAuthFilter.Plan.FREE),
                "Request " + i + " should succeed");
        }
        assertFalse(limiter.tryAcquire("user-1", JwtAuthFilter.Plan.FREE),
            "101st request should fail");
    }

    @Test
    void testProPlanHigherLimit() {
        RateLimiter limiter = new RateLimiter(60_000);
        // PRO = 1000 req/hr; should accept at least 200 calls
        for (int i = 0; i < 200; i++) {
            assertTrue(limiter.tryAcquire("user-2", JwtAuthFilter.Plan.PRO));
        }
    }

    @Test
    void testEnterpriseUnlimited() {
        RateLimiter limiter = new RateLimiter(60_000);
        for (int i = 0; i < 10_000; i++) {
            assertTrue(limiter.tryAcquire("user-3", JwtAuthFilter.Plan.ENTERPRISE));
        }
    }

    @Test
    void testPerUserIsolation() {
        RateLimiter limiter = new RateLimiter(60_000);
        // user A exhausts
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("user-A", JwtAuthFilter.Plan.FREE);
        }
        assertFalse(limiter.tryAcquire("user-A", JwtAuthFilter.Plan.FREE));
        // user B unaffected
        assertTrue(limiter.tryAcquire("user-B", JwtAuthFilter.Plan.FREE));
    }

    @Test
    void testUsageMetric() {
        RateLimiter limiter = new RateLimiter(60_000);
        assertEquals(0.0, limiter.usage("user-x", JwtAuthFilter.Plan.FREE));
        limiter.tryAcquire("user-x", JwtAuthFilter.Plan.FREE);
        limiter.tryAcquire("user-x", JwtAuthFilter.Plan.FREE);
        assertEquals(0.02, limiter.usage("user-x", JwtAuthFilter.Plan.FREE), 0.001);
    }

    @Test
    void testReset() {
        RateLimiter limiter = new RateLimiter(60_000);
        limiter.tryAcquire("user-z", JwtAuthFilter.Plan.FREE);
        limiter.reset();
        assertEquals(0.0, limiter.usage("user-z", JwtAuthFilter.Plan.FREE));
    }
}
