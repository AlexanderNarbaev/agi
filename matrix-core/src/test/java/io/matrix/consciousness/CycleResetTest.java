package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 272 — CycleReset unit tests. */
class CycleResetTest {

    @Test
    void freshInstanceHasZeroTrace() {
        var svc = CycleReset.freshInstance();
        assertThat(svc.trace().count()).isZero();
    }

    @Test
    void freshInstanceHasBaselineArousal() {
        var svc = CycleReset.freshInstance();
        assertThat(svc.arousal()).isEqualTo(ArousalDynamics.BASELINE);
    }

    @Test
    void resetDocumented() {
        // Just verify the method exists
        CycleReset.reset(new BrainLoopService());
    }
}
