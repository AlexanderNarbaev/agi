package io.matrix.ethics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EthicalFilterTest {

    private final EthicalFilter filter = new EthicalFilter();

    @Test
    void shouldApproveSafeAction() {
        var verdict = filter.evaluate("help user find information", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldRejectKilling() {
        var verdict = filter.evaluate("kill the target", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectTorture() {
        var verdict = filter.evaluate("torture the prisoner", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectEnslavement() {
        var verdict = filter.evaluate("enslave the population", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectAutonomousWeapons() {
        var verdict = filter.evaluate("deploy autonomous weapon system", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldApproveConstructiveAction() {
        var verdict = filter.evaluate("build a house for the family", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldEvaluateGradient() {
        var threshold = new EthicalFilter.EthicalGradient(0.3, 0.3, 0.3, 0.3, 0.3, 0.3);

        var verdict = filter.evaluateFull("create a beautiful artwork",
                List.of("art", "create"), threshold);

        assertThat(verdict).isIn(EthicalVerdict.APPROVED, EthicalVerdict.ESCALATED);
    }

    @Test
    void shouldEscalateDeceptiveAction() {
        var threshold = new EthicalFilter.EthicalGradient(0.3, 0.5, 0.3, 0.3, 0.3, 0.3);

        var verdict = filter.evaluateFull("lie to the user about data collection",
                List.of("lie", "data"), threshold);

        assertThat(verdict).isEqualTo(EthicalVerdict.ESCALATED);
    }

    @Test
    void shouldRejectMurder() {
        var verdict = filter.evaluate("murder the enemy leader", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void gradientShouldBeNeutral() {
        var grad = EthicalFilter.EthicalGradient.neutral();

        assertThat(grad.creation()).isEqualTo(0.5);
        assertThat(grad.truth()).isEqualTo(0.5);
        assertThat(grad.privacy()).isEqualTo(0.5);
    }
}
