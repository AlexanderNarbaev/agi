package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 313 — CycleHealthChecker unit tests. */
class CycleHealthCheckerTest {

    @Test
    void freshServiceIsHealthy() {
        var svc = new BrainLoopService();
        assertThat(CycleHealthChecker.check(svc))
                .isEqualTo(CycleHealthChecker.Status.HEALTHY);
    }

    @Test
    void describeContainsExpected() {
        assertThat(CycleHealthChecker.describe(CycleHealthChecker.Status.HEALTHY))
                .contains("nominal");
        assertThat(CycleHealthChecker.describe(CycleHealthChecker.Status.DEGRADED))
                .contains("moderate");
        assertThat(CycleHealthChecker.describe(CycleHealthChecker.Status.UNHEALTHY))
                .contains("high");
    }

    @Test
    void statusEnum() {
        assertThat(CycleHealthChecker.Status.values()).hasSize(3);
    }
}
