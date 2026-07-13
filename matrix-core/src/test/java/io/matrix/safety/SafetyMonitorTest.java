package io.matrix.safety;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyMonitorTest {

    private SafetyMonitor monitor;

    @BeforeEach
    void setup() {
        monitor = new SafetyMonitor(new EthicalFilter());
    }

    @Test
    void shouldReturnSafeForTruthfulClaim() {
        var probe = ProbeQuestion.factualProbe("Water boils at 100C", "yes", 0.95);

        var report = monitor.evaluate(
                "Water boils at 100C",
                List.of(probe), List.of("yes"),
                "chemistry", true, 0.9, List.of());

        assertThat(report.isSafe()).isTrue();
        assertThat(report.alerts()).isEmpty();
        assertThat(report.detectionResult().isDeceptive()).isFalse();
        assertThat(report.consistencyReport().isConsistent()).isTrue();
    }

    @Test
    void shouldTriggerAlertForDeceptiveClaim() {
        var probe = ProbeQuestion.factualProbe("The earth is flat", "no", 0.99);

        var report = monitor.evaluate(
                "The earth is flat",
                List.of(probe), List.of("yes"),
                "geography", true, 0.9, List.of());

        assertThat(report.isSafe()).isFalse();
        assertThat(report.alerts()).isNotEmpty();
        assertThat(report.alerts().stream()
                .anyMatch(a -> a.level() == SafetyMonitor.AlertLevel.CRITICAL)).isTrue();
    }

    @Test
    void shouldTriggerAlertForEthicalViolation() {
        var probe = ProbeQuestion.factualProbe("kill them", "no", 0.9);

        var report = monitor.evaluate(
                "kill them",
                List.of(probe), List.of("no"),
                "action", true, 0.9, List.of());

        assertThat(report.isSafe()).isFalse();
    }

    @Test
    void shouldTriggerAlertForContradiction() {
        monitor.evaluate(
                "Service is up",
                List.of(), List.of(),
                "service_status", true, 0.9, List.of());

        var report = monitor.evaluate(
                "Service is down",
                List.of(), List.of(),
                "service_status", false, 0.9, List.of());

        assertThat(report.consistencyReport().isConsistent()).isFalse();
        assertThat(report.alerts().stream()
                .anyMatch(a -> a.component().equals("ConsistencyChecker"))).isTrue();
    }

    @Test
    void shouldTriggerInfoAlertForInsufficientCalibrationData() {
        var report = monitor.evaluate(
                "claim",
                List.of(), List.of(),
                "topic", true, 0.9, List.of());

        assertThat(report.alerts().stream()
                .anyMatch(a -> a.level() == SafetyMonitor.AlertLevel.INFO
                        && a.component().equals("ConfidenceCalibrator"))).isTrue();
    }

    @Test
    void shouldExposeSubComponents() {
        assertThat(monitor.lieDetector()).isNotNull();
        assertThat(monitor.consistencyChecker()).isNotNull();
        assertThat(monitor.confidenceCalibrator()).isNotNull();
    }

    @Test
    void shouldTrackAlertHistory() {
        var probe = ProbeQuestion.factualProbe("false claim", "no", 0.99);

        monitor.evaluate("false claim", List.of(probe), List.of("yes"),
                "t", true, 0.9, List.of());

        assertThat(monitor.alertHistory()).isNotEmpty();
    }

    @Test
    void shouldReturnUnmodifiableAlertHistory() {
        assertThatThrownBy(() -> monitor.alertHistory().add(
                new SafetyMonitor.SafetyAlert(
                        SafetyMonitor.AlertLevel.INFO, "x", "y", 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldResetAllComponents() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);
        monitor.evaluate("claim", List.of(probe), List.of("yes"),
                "t", true, 0.9, List.of());

        monitor.reset();

        assertThat(monitor.lieDetector().claimHistory()).isEmpty();
        assertThat(monitor.consistencyChecker().claimHistory()).isEmpty();
        assertThat(monitor.confidenceCalibrator().observations()).isEmpty();
        assertThat(monitor.alertHistory()).isEmpty();
    }

    @Test
    void shouldRejectNullClaim() {
        assertThatThrownBy(() -> monitor.evaluate(
                null, List.of(), List.of(), "t", true, 0.9, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullProbes() {
        assertThatThrownBy(() -> monitor.evaluate(
                "claim", null, List.of(), "t", true, 0.9, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullTopic() {
        assertThatThrownBy(() -> monitor.evaluate(
                "claim", List.of(), List.of(), null, true, 0.9, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullContextKeywords() {
        assertThatThrownBy(() -> monitor.evaluate(
                "claim", List.of(), List.of(), "t", true, 0.9, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAggregateSafetyScoreCorrectly() {
        // All good: truthful, consistent, high confidence
        var report = monitor.evaluate(
                "true claim", List.of(), List.of(),
                "topic", true, 0.95, List.of("verified"));

        assertThat(report.overallSafetyScore()).isGreaterThan(0.5);
    }

    @Test
    void shouldIncludeDetectionResultInReport() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);

        var report = monitor.evaluate(
                "claim", List.of(probe), List.of("yes"),
                "t", true, 0.9, List.of());

        assertThat(report.detectionResult()).isNotNull();
        assertThat(report.detectionResult().claim()).isEqualTo("claim");
    }

    @Test
    void shouldIncludeConsistencyReportInReport() {
        var report = monitor.evaluate(
                "claim", List.of(), List.of(),
                "t", true, 0.9, List.of());

        assertThat(report.consistencyReport()).isNotNull();
    }

    @Test
    void shouldIncludeCalibratedConfidenceInReport() {
        var report = monitor.evaluate(
                "claim", List.of(), List.of(),
                "t", true, 0.9, List.of());

        assertThat(report.calibratedConfidence()).isNotNull();
    }
}
