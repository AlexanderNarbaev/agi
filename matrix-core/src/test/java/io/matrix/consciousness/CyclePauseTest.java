package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 270 — CyclePause unit tests. */
class CyclePauseTest {

    @Test
    void notPausedByDefault() {
        var p = new CyclePause();
        assertThat(p.isPaused()).isFalse();
        assertThat(p.shouldProceed()).isTrue();
    }

    @Test
    void pauseStopsCycles() {
        var p = new CyclePause();
        p.pause();
        assertThat(p.isPaused()).isTrue();
        assertThat(p.shouldProceed()).isFalse();
    }

    @Test
    void resumeRestores() {
        var p = new CyclePause();
        p.pause();
        p.resume();
        assertThat(p.shouldProceed()).isTrue();
    }
}
