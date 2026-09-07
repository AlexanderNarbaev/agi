package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 315 — CycleReadinessChecker unit tests. */
class CycleReadinessCheckerTest {

    @Test
    void freshServiceIsReady() {
        var svc = new BrainLoopService();
        assertThat(CycleReadinessChecker.isReady(svc)).isTrue();
    }

    @Test
    void nullServiceNotReady() {
        assertThat(CycleReadinessChecker.isReady(null)).isFalse();
    }

    @Test
    void reasonForReady() {
        var svc = new BrainLoopService();
        assertThat(CycleReadinessChecker.reason(svc)).isEqualTo("ready");
    }

    @Test
    void reasonForNull() {
        assertThat(CycleReadinessChecker.reason(null)).isEqualTo("service null");
    }
}
