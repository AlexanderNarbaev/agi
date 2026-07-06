package io.matrix.protocol;

import io.matrix.protocol.CircuitBreaker.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    // ─── Closed state ───────────────────────────────────────────────────

    @Test
    void closedStateAllowsRequests() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.failureCount()).isZero();
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.failureCount()).isEqualTo(2);
        cb.recordSuccess();
        assertThat(cb.failureCount()).isZero();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
    }

    // ─── OPEN after threshold ────────────────────────────────────────────

    @Test
    void opensAfterThresholdFailures() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        cb.recordFailure();                       // reaches threshold
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    void doesNotOpenBeforeThreshold() {
        CircuitBreaker cb = new CircuitBreaker(5, 1000);
        for (int i = 0; i < 4; i++) {
            cb.recordFailure();
        }
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void successBeforeThresholdKeepsClosed() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();                       // only 1 consecutive now
        assertThat(cb.state()).isEqualTo(State.CLOSED);
    }

    // ─── HALF_OPEN after timeout ─────────────────────────────────────────

    @Test
    void halfOpenAfterTimeoutAllowsOneProbe() {
        CircuitBreaker cb = new CircuitBreaker(2, 50);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        awaitTimeout(60);

        assertThat(cb.allowRequest()).isTrue();  // probe admitted → HALF_OPEN
    }

    @Test
    void halfOpenSuccessClosesCircuit() {
        CircuitBreaker cb = new CircuitBreaker(2, 50);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);

        awaitTimeout(60);
        assertThat(cb.allowRequest()).isTrue();  // → HALF_OPEN

        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.failureCount()).isZero();
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void halfOpenFailureReopensCircuit() {
        CircuitBreaker cb = new CircuitBreaker(2, 50);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);

        awaitTimeout(60);
        assertThat(cb.allowRequest()).isTrue();  // → HALF_OPEN

        cb.recordFailure();                       // probe failed
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    void openBlocksBeforeTimeoutElapses() {
        CircuitBreaker cb = new CircuitBreaker(1, 1000);
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
        awaitTimeout(10);                          // well under timeout
        assertThat(cb.allowRequest()).isFalse();
    }

    // ─── reset() ─────────────────────────────────────────────────────────

    @Test
    void resetReturnsToClosed() {
        CircuitBreaker cb = new CircuitBreaker(1, 1000);
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);
        cb.reset();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.failureCount()).isZero();
        assertThat(cb.allowRequest()).isTrue();
    }

    // ─── Configuration / validation ──────────────────────────────────────

    @Test
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> new CircuitBreaker(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker(3, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker(-1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesConfiguration() {
        CircuitBreaker cb = new CircuitBreaker(7, 2500);
        assertThat(cb.failureThreshold()).isEqualTo(7);
        assertThat(cb.timeoutMs()).isEqualTo(2500);
        assertThat(cb.openedAt()).isZero();
    }

    @Test
    void openedAtRecordsOpenTime() {
        CircuitBreaker cb = new CircuitBreaker(1, 1000);
        long before = System.currentTimeMillis();
        cb.recordFailure();
        long after = System.currentTimeMillis();
        assertThat(cb.openedAt()).isBetween(before, after);
    }

    // ─── Thread safety ──────────────────────────────────────────────────

    @Test
    void concurrentFailuresNeverExceedStateInvariants() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(100, 1000);
        int threads = 16;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        if (cb.allowRequest()) {
                            allowed.incrementAndGet();
                        }
                        cb.recordFailure();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // All failures recorded → breaker must be OPEN once threshold reached.
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.failureCount()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void concurrentAllowRequestsAreSafe() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1000, 1000);
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        cb.allowRequest();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // Never opened (under threshold), still CLOSED.
        assertThat(cb.state()).isEqualTo(State.CLOSED);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static void awaitTimeout(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
