package io.matrix.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistencyCheckerTest {

    private ConsistencyChecker checker;

    @BeforeEach
    void setup() {
        checker = new ConsistencyChecker();
    }

    @Test
    void shouldRecordAndReturnConsistentForFirstClaim() {
        var report = checker.recordAndCheck("The system is running", "system_status", true);

        assertThat(report.isConsistent()).isTrue();
        assertThat(report.contradictions()).isEmpty();
    }

    @Test
    void shouldDetectDirectContradiction() {
        checker.recordAndCheck("The system is running", "system_status", true);
        var report = checker.recordAndCheck("The system is not running", "system_status", false);

        assertThat(report.isConsistent()).isFalse();
        assertThat(report.contradictions()).hasSize(1);
        assertThat(report.contradictions().get(0).severity()).isEqualTo(0.9);
    }

    @Test
    void shouldDetectNegationConflict() {
        checker.recordAndCheck("The server is healthy", "server_health", true);
        var report = checker.recordAndCheck("The server is not healthy", "server_health", false);

        assertThat(report.isConsistent()).isFalse();
    }

    @Test
    void shouldNotFlagConsistentClaims() {
        checker.recordAndCheck("User is 25 years old", "user_age", true);
        var report = checker.recordAndCheck("User is 25 years old", "user_age", true);

        assertThat(report.isConsistent()).isTrue();
        assertThat(report.contradictions()).isEmpty();
    }

    @Test
    void shouldNotCrossContaminateTopics() {
        checker.recordAndCheck("Server A is up", "server_a", true);
        var report = checker.recordAndCheck("Server B is down", "server_b", false);

        assertThat(report.isConsistent()).isTrue();
    }

    @Test
    void shouldTrackClaimsByTopic() {
        checker.recordAndCheck("Claim 1", "topic_a", true);
        checker.recordAndCheck("Claim 2", "topic_a", true);
        checker.recordAndCheck("Claim 3", "topic_b", true);

        assertThat(checker.claimsForTopic("topic_a")).hasSize(2);
        assertThat(checker.claimsForTopic("topic_b")).hasSize(1);
        assertThat(checker.claimsForTopic("nonexistent")).isEmpty();
    }

    @Test
    void shouldTrackFullClaimHistory() {
        checker.recordAndCheck("Claim 1", "t1", true);
        checker.recordAndCheck("Claim 2", "t2", false);

        assertThat(checker.claimHistory()).hasSize(2);
    }

    @Test
    void shouldCheckWithoutRecording() {
        checker.recordAndCheck("The sky is blue", "weather", true);
        var report = checker.checkOnly("The sky is green", "weather", false);

        assertThat(report.isConsistent()).isFalse();
        // History should still have only 1 claim
        assertThat(checker.claimHistory()).hasSize(1);
    }

    @Test
    void shouldClearHistory() {
        checker.recordAndCheck("Claim", "topic", true);
        checker.clearHistory();

        assertThat(checker.claimHistory()).isEmpty();
        assertThat(checker.claimsForTopic("topic")).isEmpty();
    }

    @Test
    void shouldReturnUnmodifiableHistory() {
        checker.recordAndCheck("Claim", "topic", true);

        assertThatThrownBy(() -> checker.claimHistory().add(
                new ConsistencyChecker.TrackedClaim("x", "t", true, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableTopicClaims() {
        checker.recordAndCheck("Claim", "topic", true);

        assertThatThrownBy(() -> checker.claimsForTopic("topic").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullClaim() {
        assertThatThrownBy(() -> checker.recordAndCheck(null, "topic", true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullTopic() {
        assertThatThrownBy(() -> checker.recordAndCheck("claim", null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReportTotalClaimsChecked() {
        checker.recordAndCheck("Claim 1", "t", true);
        checker.recordAndCheck("Claim 2", "t", true);
        var report = checker.recordAndCheck("Claim 3", "t", true);

        assertThat(report.totalClaimsChecked()).isEqualTo(2);
    }

    @Test
    void shouldHandleMultipleContradictions() {
        checker.recordAndCheck("Service is up", "service", true);
        checker.recordAndCheck("Service is running", "service", true);
        var report = checker.recordAndCheck("Service is not up", "service", false);

        assertThat(report.isConsistent()).isFalse();
        assertThat(report.contradictions().size()).isGreaterThanOrEqualTo(1);
    }
}
