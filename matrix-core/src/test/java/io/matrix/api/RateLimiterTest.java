package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 99 — RateLimiter unit tests. */
class RateLimiterTest {

    @Test
    void initialBurstAllowed() {
        RateLimiter lim = new RateLimiter(5, 1.0);
        for (int i = 0; i < 5; i++) {
            assertThat(lim.tryAcquire("k")).isTrue();
        }
        // 6th request should be rejected
        assertThat(lim.tryAcquire("k")).isFalse();
    }

    @Test
    void tokensRefillOverTime() throws Exception {
        RateLimiter lim = new RateLimiter(2, 100.0);  // 100/sec refill
        assertThat(lim.tryAcquire("k")).isTrue();
        assertThat(lim.tryAcquire("k")).isTrue();
        assertThat(lim.tryAcquire("k")).isFalse();
        // After 50ms, ~5 tokens refilled
        Thread.sleep(50);
        assertThat(lim.tryAcquire("k")).isTrue();
    }

    @Test
    void differentKeysTrackedSeparately() {
        RateLimiter lim = new RateLimiter(2, 0.1);
        assertThat(lim.tryAcquire("user-1")).isTrue();
        assertThat(lim.tryAcquire("user-1")).isTrue();
        assertThat(lim.tryAcquire("user-1")).isFalse();
        // user-2 still has full bucket
        assertThat(lim.tryAcquire("user-2")).isTrue();
        assertThat(lim.tryAcquire("user-2")).isTrue();
    }

    @Test
    void countersTrackRequestsAndRejections() {
        RateLimiter lim = new RateLimiter(2, 0.1);
        for (int i = 0; i < 5; i++) lim.tryAcquire("k");
        assertThat(lim.totalRequests()).isEqualTo(5);
        assertThat(lim.totalRejections()).isEqualTo(3);  // 2 allowed, 3 rejected
        assertThat(lim.rejectionRate()).isCloseTo(0.6,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void resetClearsBucket() {
        RateLimiter lim = new RateLimiter(1, 0.1);
        assertThat(lim.tryAcquire("k")).isTrue();
        assertThat(lim.tryAcquire("k")).isFalse();
        lim.reset("k");
        assertThat(lim.tryAcquire("k")).isTrue();
    }

    @Test
    void resetAllClearsAllBuckets() {
        RateLimiter lim = new RateLimiter(1, 0.1);
        lim.tryAcquire("a");
        lim.tryAcquire("b");
        assertThat(lim.activeBuckets()).isEqualTo(2);
        lim.resetAll();
        assertThat(lim.activeBuckets()).isZero();
    }

    @Test
    void invalidCapacityRejected() {
        assertThatThrownBy(() -> new RateLimiter(0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(-5, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidRefillRejected() {
        assertThatThrownBy(() -> new RateLimiter(1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessorsReturnCorrectValues() {
        RateLimiter lim = new RateLimiter(10, 5.0);
        assertThat(lim.capacity()).isEqualTo(10);
        assertThat(lim.refillPerSecond()).isEqualTo(5.0);
    }

    @Test
    void tryAcquireWithWaitWaitsForToken() throws Exception {
        RateLimiter lim = new RateLimiter(1, 50.0);  // 50/sec
        assertThat(lim.tryAcquire("k")).isTrue();
        // Second request waits up to 200ms
        long t0 = System.nanoTime();
        boolean ok = lim.tryAcquire("k", 200_000_000L);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertThat(ok).isTrue();
        // Should have waited at least ~20ms (1 token / 50 per sec)
        assertThat(elapsedMs).isLessThan(500);
    }
}
