package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 121 — RequestCounter unit tests. */
class RequestCounterTest {

    @Test
    void emptyCounter() {
        RequestCounter c = new RequestCounter();
        assertThat(c.totalRequests()).isZero();
        assertThat(c.totalErrors()).isZero();
        assertThat(c.endpoints()).isEmpty();
    }

    @Test
    void recordRequestIncrements() {
        RequestCounter c = new RequestCounter();
        c.recordRequest("/chat");
        c.recordRequest("/chat");
        c.recordRequest("/generate");
        assertThat(c.count("/chat")).isEqualTo(2);
        assertThat(c.count("/generate")).isEqualTo(1);
        assertThat(c.count("/unknown")).isZero();
        assertThat(c.totalRequests()).isEqualTo(3);
    }

    @Test
    void recordErrorIncrements() {
        RequestCounter c = new RequestCounter();
        c.recordRequest("/chat");
        c.recordRequest("/chat");
        c.recordError("/chat");
        assertThat(c.errorCount("/chat")).isEqualTo(1);
        assertThat(c.totalErrors()).isEqualTo(1);
    }

    @Test
    void errorRatePerEndpoint() {
        RequestCounter c = new RequestCounter();
        for (int i = 0; i < 10; i++) c.recordRequest("/chat");
        for (int i = 0; i < 2; i++) c.recordError("/chat");
        assertThat(c.errorRate("/chat")).isCloseTo(0.2,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void totalErrorRate() {
        RequestCounter c = new RequestCounter();
        for (int i = 0; i < 10; i++) c.recordRequest("/a");
        for (int i = 0; i < 5; i++) c.recordRequest("/b");
        for (int i = 0; i < 3; i++) c.recordError("/a");
        // total: 15 req, 3 err = 0.2
        assertThat(c.totalErrorRate()).isCloseTo(0.2,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void resetClears() {
        RequestCounter c = new RequestCounter();
        c.recordRequest("/a");
        c.recordError("/a");
        c.reset();
        assertThat(c.totalRequests()).isZero();
        assertThat(c.totalErrors()).isZero();
    }

    @Test
    void endpointsReturnsAllKeys() {
        RequestCounter c = new RequestCounter();
        c.recordRequest("/a");
        c.recordRequest("/b");
        c.recordRequest("/c");
        assertThat(c.endpoints()).containsExactlyInAnyOrder("/a", "/b", "/c");
    }

    @Test
    void zeroRequestsErrorRate() {
        RequestCounter c = new RequestCounter();
        assertThat(c.errorRate("/unknown")).isZero();
        assertThat(c.totalErrorRate()).isZero();
    }
}
