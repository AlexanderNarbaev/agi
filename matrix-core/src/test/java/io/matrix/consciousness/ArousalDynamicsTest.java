package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 157 — ArousalDynamics unit tests. */
class ArousalDynamicsTest {

    @Test
    void defaultStartsAtBaseline() {
        ArousalDynamics dyn = new ArousalDynamics();
        assertThat(dyn.current()).isEqualTo(ArousalDynamics.BASELINE);
    }

    @Test
    void highErrorRaisesArousal() {
        ArousalDynamics dyn = new ArousalDynamics();
        double before = dyn.current();
        dyn.update(1.0);
        assertThat(dyn.current()).isGreaterThan(before);
    }

    @Test
    void zeroErrorNoChange() {
        ArousalDynamics dyn = new ArousalDynamics();
        double before = dyn.current();
        dyn.update(0.0);
        // With zero error, only decay applies; rises 0
        // decay = (before - baseline) * decayRate = 0 (since at baseline)
        assertThat(dyn.current()).isEqualTo(before);
    }

    @Test
    void arousalClampedToOne() {
        ArousalDynamics dyn = new ArousalDynamics();
        for (int i = 0; i < 100; i++) dyn.update(10.0);
        assertThat(dyn.current()).isEqualTo(1.0);
    }

    @Test
    void arousalNeverNegative() {
        ArousalDynamics dyn = new ArousalDynamics(0.5, 0.4);
        dyn.update(1.0);
        // Wait many idle cycles — should not go negative
        for (int i = 0; i < 1000; i++) dyn.idle();
        assertThat(dyn.current()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void idleReturnsArousal() {
        ArousalDynamics dyn = new ArousalDynamics();
        dyn.update(0.5);
        double a = dyn.idle();
        assertThat(a).isEqualTo(dyn.current());
    }

    @Test
    void decayTendsTowardBaseline() {
        ArousalDynamics dyn = new ArousalDynamics();
        dyn.update(1.0);
        double high = dyn.current();
        for (int i = 0; i < 50; i++) dyn.idle();
        double decayed = dyn.current();
        assertThat(decayed).isLessThan(high);
    }

    @Test
    void resetReturnsToBaseline() {
        ArousalDynamics dyn = new ArousalDynamics();
        dyn.update(1.0);
        dyn.reset();
        assertThat(dyn.current()).isEqualTo(ArousalDynamics.BASELINE);
    }

    @Test
    void updateAndIdleCombined() {
        ArousalDynamics dyn = new ArousalDynamics();
        double orig = dyn.current();
        dyn.update(0.5);
        double afterRise = dyn.current();
        dyn.idle();
        // After idle, arousal moves toward baseline
        assertThat(dyn.current()).isLessThanOrEqualTo(afterRise);
        // Should be ≥ baseline if before was baseline
        assertThat(dyn.current()).isGreaterThanOrEqualTo(orig - 0.1);
    }
}
