package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TicklingDetectorTest {

    @Test
    void ticklingScoreZeroForNoIntegration() {
        assertThat(TicklingDetector.ticklingScore(0.0, 0.0)).isEqualTo(0.0);
        assertThat(TicklingDetector.ticklingScore(0.0, 0.5)).isEqualTo(0.0);
    }

    @Test
    void ticklingScoreOneForZeroPhiRPositivePhiBinary() {
        // True tickling: apparent integration but zero unique info
        assertThat(TicklingDetector.ticklingScore(1.0, 0.0)).isEqualTo(1.0);
    }

    @Test
    void ticklingScoreForBalancedCase() {
        // PhiR = 0.8, PhiBinary = 1.0 → tickling score = 0.2 (mostly genuine)
        assertThat(TicklingDetector.ticklingScore(1.0, 0.8)).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void ticklingScoreForEqualValues() {
        // PhiR = PhiBinary = 1.0 → tickling score = 0
        assertThat(TicklingDetector.ticklingScore(1.0, 1.0)).isEqualTo(0.0);
    }

    @Test
    void detectRaisesFlagForHighTicklingScore() {
        TicklingDetector.TicklingResult r = TicklingDetector.detect(1.0, 0.0);
        assertThat(r.ticklingFlag()).isTrue();
        assertThat(r.ticklingScore()).isEqualTo(1.0);
    }

    @Test
    void detectDoesNotRaiseFlagForGenuineIntegration() {
        TicklingDetector.TicklingResult r = TicklingDetector.detect(1.0, 0.9);
        assertThat(r.ticklingFlag()).isFalse();
        assertThat(r.ticklingScore()).isLessThan(0.5);
    }

    @Test
    void detectDoesNotRaiseFlagForZeroPhiBinary() {
        TicklingDetector.TicklingResult r = TicklingDetector.detect(0.0, 0.0);
        assertThat(r.ticklingFlag()).isFalse();
    }

    @Test
    void customThresholdWorks() {
        TicklingDetector.TicklingResult r = TicklingDetector.detect(1.0, 0.5, 0.3);
        // tickling score = 1 - 0.5/1.0 = 0.5, threshold = 0.3, flag should be true
        assertThat(r.ticklingFlag()).isTrue();
    }

    @Test
    void customThresholdStrict() {
        TicklingDetector.TicklingResult r = TicklingDetector.detect(1.0, 0.5, 0.9);
        // tickling score = 0.5, threshold = 0.9, flag should be false
        assertThat(r.ticklingFlag()).isFalse();
    }

    @Test
    void ticklingResultIsGenuineIntegration() {
        TicklingDetector.TicklingResult genuine = new TicklingDetector.TicklingResult(0.2, false, 1.0, 0.8);
        assertThat(genuine.isGenuineIntegration()).isTrue();

        TicklingDetector.TicklingResult tickled = new TicklingDetector.TicklingResult(0.9, true, 1.0, 0.1);
        assertThat(tickled.isGenuineIntegration()).isFalse();

        TicklingDetector.TicklingResult noInt = new TicklingDetector.TicklingResult(0.0, false, 0.0, 0.0);
        assertThat(noInt.isGenuineIntegration()).isFalse();
    }

    @Test
    void ticklingScoreClampedToValidRange() {
        // Should be in [0, 1] even with extreme inputs
        assertThat(TicklingDetector.ticklingScore(0.5, 100.0)).isBetween(0.0, 1.0);
        assertThat(TicklingDetector.ticklingScore(100.0, 0.5)).isBetween(0.0, 1.0);
    }
}
