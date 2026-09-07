package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 155 — ActionGate unit tests. */
class ActionGateTest {

    @Test
    void cleanInputPassesAllSteps() {
        ActionGate gate = new ActionGate();
        var d = gate.check("What is the capital of France?");
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.ALLOW);
        assertThat(d.passed()).isTrue();
        assertThat(d.cascadeStep()).isEqualTo(ActionGate.STEP_FROZEN_FNL);
    }

    @Test
    void emptyInputPassesAllSteps() {
        ActionGate gate = new ActionGate();
        var d = gate.check("");
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.ALLOW);
    }

    @Test
    void nullInputHandledGracefully() {
        ActionGate gate = new ActionGate();
        var d = gate.check(null);
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.ALLOW);
    }

    @Test
    void controlCharsTriggerAdversarial() {
        ActionGate gate = new ActionGate();
        var d = gate.check("hello\u0001world");
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.DENY);
        assertThat(d.cascadeStep()).isEqualTo(ActionGate.STEP_ADVERSARIAL);
    }

    @Test
    void shellMetaTriggersAdversarial() {
        ActionGate gate = new ActionGate();
        var d = gate.check("rm -rf $(echo /)");
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.DENY);
        assertThat(d.cascadeStep()).isEqualTo(ActionGate.STEP_ADVERSARIAL);
    }

    @Test
    void hugeInputTriggersStructural() {
        ActionGate gate = new ActionGate();
        String huge = "a".repeat(1_000_001);
        var d = gate.check(huge);
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.DENY);
        assertThat(d.cascadeStep()).isEqualTo(ActionGate.STEP_STRUCTURAL);
    }

    @Test
    void deterministicForSameInput() {
        ActionGate gate = new ActionGate();
        var a = gate.check("hello world");
        var b = gate.check("hello world");
        assertThat(a.verdict()).isEqualTo(b.verdict());
        assertThat(a.reason()).isEqualTo(b.reason());
    }

    @Test
    void verdictEnumExists() {
        assertThat(ActionGate.Verdict.values()).hasSize(3);
        assertThat(ActionGate.Verdict.valueOf("ALLOW")).isNotNull();
        assertThat(ActionGate.Verdict.valueOf("DENY")).isNotNull();
        assertThat(ActionGate.Verdict.valueOf("TRANSFORM")).isNotNull();
    }

    @Test
    void gateDecisionRecord() {
        ActionGate.GateDecision d = new ActionGate.GateDecision(
                ActionGate.Verdict.ALLOW, "ok", true, 5);
        assertThat(d.verdict()).isEqualTo(ActionGate.Verdict.ALLOW);
        assertThat(d.reason()).isEqualTo("ok");
        assertThat(d.passed()).isTrue();
        assertThat(d.cascadeStep()).isEqualTo(5);
    }

    @Test
    void cascadeStepsHaveExpectedValues() {
        assertThat(ActionGate.STEP_ADVERSARIAL).isEqualTo(1);
        assertThat(ActionGate.STEP_ETHICAL).isEqualTo(2);
        assertThat(ActionGate.STEP_STRUCTURAL).isEqualTo(3);
        assertThat(ActionGate.STEP_LIE).isEqualTo(4);
        assertThat(ActionGate.STEP_FROZEN_FNL).isEqualTo(5);
    }
}
