package io.matrix.ethics;

import io.matrix.consciousness.ActionGate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 225 — ActionGatePolicy unit tests. */
class ActionGatePolicyTest {

    @Test
    void cleanInputGoesAllSteps() {
        ActionGate gate = new ActionGate();
        var v = ActionGatePolicy.check("Hello, world!", gate);
        assertThat(v.step()).isEqualTo(ActionGatePolicy.CascadeStep.FROZEN);
        assertThat(v.decision().passed()).isTrue();
    }

    @Test
    void adversarialStopsAtAdversarial() {
        ActionGate gate = new ActionGate();
        var v = ActionGatePolicy.check("hi\u0001there", gate);
        assertThat(v.step()).isEqualTo(ActionGatePolicy.CascadeStep.ADVERSARIAL);
        assertThat(v.decision().passed()).isFalse();
    }

    @Test
    void hugeInputStopsAtStructural() {
        ActionGate gate = new ActionGate();
        var huge = "x".repeat(2_000_000);
        var v = ActionGatePolicy.check(huge, gate);
        assertThat(v.step()).isEqualTo(ActionGatePolicy.CascadeStep.STRUCTURAL);
    }

    @Test
    void cascadeStepEnum() {
        assertThat(ActionGatePolicy.CascadeStep.values()).hasSize(5);
        assertThat(ActionGatePolicy.CascadeStep.valueOf("FROZEN")).isNotNull();
    }
}
