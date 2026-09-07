package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 266 — CycleFeedback unit tests. */
class CycleFeedbackTest {

    @Test
    void emptyFeedback() {
        var f = new CycleFeedback();
        assertThat(f.size()).isZero();
        assertThat(f.movingAverage()).isZero();
    }

    @Test
    void singleRecord() {
        var f = new CycleFeedback();
        f.record(1, 0.5, "good");
        assertThat(f.lastScore()).isEqualTo(0.5);
        assertThat(f.movingAverage()).isEqualTo(0.5);
    }

    @Test
    void movingAverageSmoothed() {
        var f = new CycleFeedback(0.5);
        f.record(1, 1.0, "high");
        f.record(2, 0.0, "low");
        // MA = 0.5 * 0.0 + 0.5 * 1.0 = 0.5
        assertThat(f.movingAverage()).isEqualTo(0.5);
    }

    @Test
    void lastScoreReturnsMostRecent() {
        var f = new CycleFeedback();
        f.record(1, 0.5, "a");
        f.record(2, 0.8, "b");
        assertThat(f.lastScore()).isEqualTo(0.8);
    }

    @Test
    void clearResets() {
        var f = new CycleFeedback();
        f.record(1, 0.5, "a");
        f.clear();
        assertThat(f.size()).isZero();
    }

    @Test
    void defaultAlphaIs01() {
        var f = new CycleFeedback();
        f.record(1, 1.0, "a");
        f.record(2, 0.0, "b");
        // MA = 0.1 * 0.0 + 0.9 * 1.0 = 0.9
        assertThat(f.movingAverage()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(0.01));
    }
}
