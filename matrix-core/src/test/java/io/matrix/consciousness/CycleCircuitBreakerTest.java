package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 300 — CycleCircuitBreaker unit tests. */
class CycleCircuitBreakerTest {

    @Test
    void initiallyClosed() {
        var cb = new CycleCircuitBreaker(3);
        assertThat(cb.state()).isEqualTo(CycleCircuitBreaker.State.CLOSED);
        assertThat(cb.isAllowed()).isTrue();
    }

    @Test
    void acceptedResetsCounter() {
        var cb = new CycleCircuitBreaker(3);
        cb.record(false);
        cb.record(true);
        assertThat(cb.failureCount()).isZero();
    }

    @Test
    void opensAfterThreshold() {
        var cb = new CycleCircuitBreaker(2);
        cb.record(false);
        cb.record(false);
        assertThat(cb.state()).isEqualTo(CycleCircuitBreaker.State.OPEN);
        assertThat(cb.isAllowed()).isFalse();
    }

    @Test
    void resetClosesCircuit() {
        var cb = new CycleCircuitBreaker(2);
        cb.record(false);
        cb.record(false);
        cb.reset();
        assertThat(cb.state()).isEqualTo(CycleCircuitBreaker.State.CLOSED);
        assertThat(cb.isAllowed()).isTrue();
    }

    @Test
    void failureCountTracks() {
        var cb = new CycleCircuitBreaker(5);
        cb.record(false);
        cb.record(false);
        cb.record(false);
        assertThat(cb.failureCount()).isEqualTo(3);
    }
}
