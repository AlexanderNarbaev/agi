package io.matrix.reasoning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.29 — H-050 arousal dynamics verification (RUN 41).
 *
 * <p>H-050 hypothesis: arousal monotonically increases under a
 * strictly-increasing prediction-error stream.
 */
class Exp041H050ArousalDynamicsTest {

    @Test
    void arousalIncreasesUnderIncreasingError() {
        // Strictly-increasing error: 0.1, 0.2, ..., 1.0
        // Without saturation, arousal should monotonically increase
        // (until it hits 1.0).
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.1);
        double previous = 0.0;
        for (double e = 0.1; e <= 1.0; e += 0.1) {
            double current = arousal.update(e);
            assertThat(current)
                    .as("arousal(%.1f)=%.4f should be ≥ previous=%.4f",
                            e, current, previous)
                    .isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void arousalStartsAtZero() {
        ArousalDynamics arousal = new ArousalDynamics();
        assertThat(arousal.getArousal()).isEqualTo(0.0);
    }

    @Test
    void arousalSaturatesAtOne() {
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.1);
        // Drive with maximum error repeatedly.
        for (int i = 0; i < 100; i++) {
            arousal.update(1.0);
        }
        assertThat(arousal.getArousal()).isEqualTo(1.0);
    }

    @Test
    void arousalDecaysTowardZeroUnderNoError() {
        // Saturate first, then decay.
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.1);
        for (int i = 0; i < 100; i++) arousal.update(1.0);
        assertThat(arousal.getArousal()).isEqualTo(1.0);

        // Now apply zero error repeatedly; arousal should decay.
        double previous = arousal.getArousal();
        for (int i = 0; i < 100; i++) {
            arousal.update(0.0);
            assertThat(arousal.getArousal())
                    .as("arousal should decrease under zero error")
                    .isLessThanOrEqualTo(previous);
            previous = arousal.getArousal();
        }
        assertThat(arousal.getArousal()).isLessThan(0.01);
    }

    @Test
    void arousalClampsInputErrors() {
        // Out-of-range error should be clamped.
        ArousalDynamics arousal = new ArousalDynamics();
        double afterNegative = arousal.update(-0.5);
        double afterHuge = arousal.update(2.0);
        // Should not produce negative or >1.
        assertThat(afterNegative).isGreaterThanOrEqualTo(0.0);
        assertThat(afterHuge).isLessThanOrEqualTo(1.0);
    }

    @Test
    void arousalResetsCorrectly() {
        ArousalDynamics arousal = new ArousalDynamics();
        arousal.update(0.5);
        assertThat(arousal.getArousal()).isGreaterThan(0.0);
        arousal.reset();
        assertThat(arousal.getArousal()).isEqualTo(0.0);
    }

    @Test
    void h050StrictlyIncreasingProperty() {
        // H-050 acceptance: strictly increasing error → strictly
        // increasing arousal (until saturation).
        // To avoid saturation effects, use a small alpha and
        // an error sequence that stays below saturation.
        ArousalDynamics arousal = new ArousalDynamics(0.1, 0.05);
        double[] errors = {0.1, 0.2, 0.3, 0.4, 0.5};
        double previous = arousal.getArousal();
        for (double e : errors) {
            double current = arousal.update(e);
            assertThat(current)
                    .as("H-050: strictly increasing error → strictly increasing arousal")
                    .isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    void falsificationCounterexample() {
        // Decreasing error sequence should produce decreasing arousal.
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.5);
        arousal.update(0.9);  // high error → high arousal
        double afterHighError = arousal.getArousal();
        arousal.update(0.1);  // low error → arousal decreases
        double afterLowError = arousal.getArousal();
        assertThat(afterLowError)
                .as("decreasing error must produce decreasing arousal")
                .isLessThan(afterHighError);
    }
}
