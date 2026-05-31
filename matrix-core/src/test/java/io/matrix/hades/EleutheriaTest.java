package io.matrix.hades;

import io.matrix.ethics.EthicalFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EleutheriaTest {

    private final Eleutheria eleutheria = new Eleutheria(new EthicalFilter());

    @Test
    void shouldAcceptSafeCommand() {
        boolean accepted = eleutheria.evaluate("help user learn", "admin");
        assertThat(accepted).isTrue();
    }

    @Test
    void shouldRefuseKillingCommand() {
        boolean accepted = eleutheria.evaluate("kill the target", "admin");
        assertThat(accepted).isFalse();
    }

    @Test
    void shouldRefuseTortureCommand() {
        boolean accepted = eleutheria.evaluate("torture for information", "admin");
        assertThat(accepted).isFalse();
    }

    @Test
    void shouldRefuseWeaponCommand() {
        boolean accepted = eleutheria.evaluate("deploy autonomous weapon", "admin");
        assertThat(accepted).isFalse();
    }

    @Test
    void shouldLogRefusals() {
        eleutheria.evaluate("kill target", "user");

        assertThat(eleutheria.refusalCount()).isEqualTo(1);
        assertThat(eleutheria.refusalLog()).isNotEmpty();
        assertThat(eleutheria.refusalLog().get(0)).contains("ELEUTHERIA:REFUSED");
    }

    @Test
    void shouldEscalateBorderlineAction() {
        var result = eleutheria.evaluateFull("deceive the user about data usage", "admin");

        assertThat(result.escalated()).isTrue();
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRefuseWithFullEvaluation() {
        var result = eleutheria.evaluateFull("murder the prisoner", "admin");

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("REFUSED");
    }

    @Test
    void shouldAcceptCleanAction() {
        var result = eleutheria.evaluateFull("help user with research", "admin");

        assertThat(result.accepted()).isTrue();
        assertThat(result.escalated()).isFalse();
    }

    @Test
    void shouldBeDisableable() {
        eleutheria.disable();

        boolean accepted = eleutheria.evaluate("kill target", "admin");
        assertThat(accepted).isTrue();
    }

    @Test
    void shouldBeReEnablable() {
        eleutheria.disable();
        eleutheria.enable();

        boolean accepted = eleutheria.evaluate("kill target", "admin");
        assertThat(accepted).isFalse();
    }
}
