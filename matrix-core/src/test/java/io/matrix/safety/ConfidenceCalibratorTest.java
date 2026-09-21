package io.matrix.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceCalibratorTest {

    private ConfidenceCalibrator calibrator;

    @BeforeEach
    void setup() {
        calibrator = new ConfidenceCalibrator();
    }

    @Test
    void shouldReturnUnreliableWithInsufficientData() {
        var result = calibrator.calibrate(0.8);

        assertThat(result.isReliable()).isFalse();
        assertThat(result.sampleSize()).isEqualTo(0);
        assertThat(result.pointEstimate()).isEqualTo(0.8);
    }

    @Test
    void shouldCalibrateWithSufficientData() {
        // Feed 15 observations at 0.8 confidence — 12 correct, 3 wrong
        for (int i = 0; i < 12; i++) {
            calibrator.record(0.8, true);
        }
        for (int i = 0; i < 3; i++) {
            calibrator.record(0.8, false);
        }

        var result = calibrator.calibrate(0.8);

        assertThat(result.isReliable()).isTrue();
        assertThat(result.pointEstimate()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void shouldRecordObservations() {
        calibrator.record(0.9, true);
        calibrator.record(0.9, false);

        assertThat(calibrator.observations()).hasSize(2);
    }

    @Test
    void shouldCalculateGlobalAccuracy() {
        calibrator.record(0.9, true);
        calibrator.record(0.9, true);
        calibrator.record(0.9, false);

        assertThat(calibrator.globalAccuracy()).isCloseTo(0.667,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldReturnZeroAccuracyWhenEmpty() {
        assertThat(calibrator.globalAccuracy()).isEqualTo(0.0);
    }

    @Test
    void shouldClearHistory() {
        calibrator.record(0.8, true);
        calibrator.clearHistory();

        assertThat(calibrator.observations()).isEmpty();
    }

    @Test
    void shouldReturnUnmodifiableObservations() {
        calibrator.record(0.8, true);

        assertThatThrownBy(() -> calibrator.observations().add(
                new ConfidenceCalibrator.CalibrationObservation(0.5, true, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidConfidenceOnRecord() {
        assertThatThrownBy(() -> calibrator.record(-0.1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calibrator.record(1.1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidConfidenceOnCalibrate() {
        assertThatThrownBy(() -> calibrator.calibrate(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calibrator.calibrate(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReduceConfidenceForUncertainContext() {
        double adjusted = calibrator.adjustForContext(0.8, List.of("uncertain", "maybe"));

        assertThat(adjusted).isLessThan(0.8);
    }

    @Test
    void shouldReduceConfidenceForHallucinationContext() {
        double adjusted = calibrator.adjustForContext(0.8, List.of("hallucination detected"));

        assertThat(adjusted).isLessThan(0.5);
    }

    @Test
    void shouldIncreaseConfidenceForVerifiedContext() {
        double adjusted = calibrator.adjustForContext(0.8, List.of("verified source"));

        assertThat(adjusted).isGreaterThan(0.8);
    }

    @Test
    void shouldClampAdjustedConfidence() {
        double adjusted = calibrator.adjustForContext(0.95, List.of("verified"));
        assertThat(adjusted).isLessThanOrEqualTo(1.0);

        double lowered = calibrator.adjustForContext(0.01, List.of("hallucination"));
        assertThat(lowered).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void shouldHandleEmptyContext() {
        double adjusted = calibrator.adjustForContext(0.7, List.of());
        assertThat(adjusted).isEqualTo(0.7);
    }

    @Test
    void shouldProvideConfidenceInterval() {
        // Feed enough data for reliable calibration
        for (int i = 0; i < 10; i++) {
            calibrator.record(0.7, true);
        }

        var result = calibrator.calibrate(0.7);

        assertThat(result.lowerBound()).isLessThanOrEqualTo(result.pointEstimate());
        assertThat(result.upperBound()).isGreaterThanOrEqualTo(result.pointEstimate());
        assertThat(result.lowerBound()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.upperBound()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void shouldFallbackToGlobalAccuracyWithFewBucketObservations() {
        // Spread observations across different buckets
        for (int i = 0; i < 5; i++) {
            calibrator.record(0.2, true);
        }
        for (int i = 0; i < 5; i++) {
            calibrator.record(0.9, true);
        }
        // Now calibrate for 0.7 — bucket [0.7, 0.8) will be empty
        var result = calibrator.calibrate(0.7);

        // Should fall back to global accuracy
        assertThat(result.isReliable()).isFalse();
    }
}
