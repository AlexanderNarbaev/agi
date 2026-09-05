package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 100 — BackoffPolicy unit tests. */
class BackoffPolicyTest {

    @Test
    void firstAttemptIsInitial() {
        BackoffPolicy p = new BackoffPolicy(100, 5000, 2.0, false);
        assertThat(p.delayMs(0)).isEqualTo(100);
    }

    @Test
    void exponentialGrowth() {
        BackoffPolicy p = new BackoffPolicy(100, 5000, 2.0, false);
        assertThat(p.delayMs(0)).isEqualTo(100);
        assertThat(p.delayMs(1)).isEqualTo(200);
        assertThat(p.delayMs(2)).isEqualTo(400);
        assertThat(p.delayMs(3)).isEqualTo(800);
    }

    @Test
    void cappedAtMax() {
        BackoffPolicy p = new BackoffPolicy(100, 1000, 2.0, false);
        assertThat(p.delayMs(10)).isEqualTo(1000);  // would be 102400
    }

    @Test
    void jitterAddsVariance() {
        BackoffPolicy p = new BackoffPolicy(100, 5000, 2.0, true);
        // Sample 10 attempts and verify they're not all equal
        long first = p.delayMs(0);
        boolean varied = false;
        for (int i = 0; i < 10; i++) {
            if (p.delayMs(0) != first) {
                varied = true;
                break;
            }
        }
        assertThat(varied).isTrue();
    }

    @Test
    void jitterBounded() {
        BackoffPolicy p = new BackoffPolicy(100, 5000, 2.0, true);
        // With ±25% jitter, delay should be in [75, 125] for attempt 0
        for (int i = 0; i < 20; i++) {
            long d = p.delayMs(0);
            assertThat(d).isBetween(75L, 125L);
        }
    }

    @Test
    void invalidInitialRejected() {
        assertThatThrownBy(() -> new BackoffPolicy(0, 100, 2.0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidMaxRejected() {
        assertThatThrownBy(() -> new BackoffPolicy(100, 50, 2.0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidMultiplierRejected() {
        assertThatThrownBy(() -> new BackoffPolicy(100, 1000, 0.5, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessors() {
        BackoffPolicy p = new BackoffPolicy(100, 5000, 2.5, true);
        assertThat(p.initialDelayMs()).isEqualTo(100);
        assertThat(p.maxDelayMs()).isEqualTo(5000);
        assertThat(p.multiplier()).isEqualTo(2.5);
        assertThat(p.jitter()).isTrue();
    }

    @Test
    void multiplierOneIsConstant() {
        BackoffPolicy p = new BackoffPolicy(100, 1000, 1.0, false);
        assertThat(p.delayMs(0)).isEqualTo(100);
        assertThat(p.delayMs(5)).isEqualTo(100);
        assertThat(p.delayMs(10)).isEqualTo(100);
    }
}
