package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 303 — CycleAdmission unit tests. */
class CycleAdmissionTest {

    @Test
    void admitsUnderLimits() {
        var a = new CycleAdmission(0.8, 10);
        assertThat(a.admit(0.5)).isTrue();
    }

    @Test
    void rejectsHighArousal() {
        var a = new CycleAdmission(0.8, 10);
        assertThat(a.admit(0.9)).isFalse();
    }

    @Test
    void rejectsOverWindowLimit() {
        var a = new CycleAdmission(1.0, 2);
        a.admit(0.5);
        a.admit(0.5);
        assertThat(a.admit(0.5)).isFalse();
    }

    @Test
    void resetWindowAllowsAgain() {
        var a = new CycleAdmission(1.0, 1);
        a.admit(0.5);
        a.resetWindow();
        assertThat(a.admit(0.5)).isTrue();
    }

    @Test
    void windowCyclesTracks() {
        var a = new CycleAdmission(1.0, 10);
        a.admit(0.5);
        a.admit(0.5);
        assertThat(a.windowCycles()).isEqualTo(2);
    }
}
