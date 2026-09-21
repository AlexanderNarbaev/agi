package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 116 — TokenUsageTracker unit tests. */
class TokenUsageTrackerTest {

    @Test
    void emptyTracker() {
        TokenUsageTracker t = new TokenUsageTracker();
        assertThat(t.totalInputTokens()).isZero();
        assertThat(t.totalOutputTokens()).isZero();
        assertThat(t.totalTokens()).isZero();
        assertThat(t.totalRequests()).isZero();
        assertThat(t.avgTokensPerRequest()).isZero();
    }

    @Test
    void recordIncrements() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(10, 20);
        t.record(5, 15);
        assertThat(t.totalInputTokens()).isEqualTo(15);
        assertThat(t.totalOutputTokens()).isEqualTo(35);
        assertThat(t.totalTokens()).isEqualTo(50);
        assertThat(t.totalRequests()).isEqualTo(2);
    }

    @Test
    void avgTokensPerRequest() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(10, 20);
        t.record(20, 30);
        // total = 80, requests = 2, avg = 40
        assertThat(t.avgTokensPerRequest()).isEqualTo(40.0);
    }

    @Test
    void resetClears() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(10, 20);
        t.reset();
        assertThat(t.totalInputTokens()).isZero();
        assertThat(t.totalOutputTokens()).isZero();
        assertThat(t.totalRequests()).isZero();
    }

    @Test
    void jsonIncludesAllFields() {
        TokenUsageTracker t = new TokenUsageTracker();
        t.record(5, 10);
        String json = t.toJson();
        assertThat(json).contains("\"totalInputTokens\":5");
        assertThat(json).contains("\"totalOutputTokens\":10");
        assertThat(json).contains("\"totalTokens\":15");
        assertThat(json).contains("\"totalRequests\":1");
        assertThat(json).contains("\"avgTokensPerRequest\":15.0");
    }

    @Test
    void zeroRequestsZeroAvg() {
        TokenUsageTracker t = new TokenUsageTracker();
        assertThat(t.avgTokensPerRequest()).isZero();
    }
}
