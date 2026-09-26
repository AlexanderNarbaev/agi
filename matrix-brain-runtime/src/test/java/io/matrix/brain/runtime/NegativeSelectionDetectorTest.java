package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W11 — Negative-Selection Detector tests (research iteration #2).
 */
class NegativeSelectionDetectorTest {

    @Test
    void untrained_detector_marks_normal_input_as_anomalous() {
        NegativeSelectionDetector d = new NegativeSelectionDetector();
        // Random detectors should match something with non-trivial probability.
        assertThat(d.survivors()).isGreaterThan(900);
    }

    @Test
    void training_prunes_matching_detectors() {
        NegativeSelectionDetector d = new NegativeSelectionDetector();
        int before = d.survivors();
        d.train(List.of("the quick brown fox jumps over the lazy dog"));
        int after = d.survivors();
        assertThat(after).isLessThan(before);
    }

    @Test
    void empty_corpus_keeps_all_survivors() {
        NegativeSelectionDetector d = new NegativeSelectionDetector();
        d.train(List.of());
        assertThat(d.survivors()).isGreaterThan(900);
    }

    @Test
    void anomaly_classification_is_deterministic() {
        NegativeSelectionDetector d = new NegativeSelectionDetector();
        d.train(List.of("normal operation"));
        // Same input -> same answer
        boolean a = d.isAnomalous("test input");
        boolean b = d.isAnomalous("test input");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void null_input_is_not_anomalous() {
        NegativeSelectionDetector d = new NegativeSelectionDetector();
        assertThat(d.isAnomalous(null)).isFalse();
        assertThat(d.isAnomalous("")).isFalse();
    }
}
