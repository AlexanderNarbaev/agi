package io.matrix.shadow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EcoAuditTest {

    private final EcoAudit auditor = new EcoAudit();

    @Test
    void shouldReturnCleanForLightAction() {
        var result = auditor.evaluate("simple query");

        assertThat(result.acceptable()).isTrue();
        assertThat(result.sustainabilityScore()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectHighEnergyCost() {
        var result = auditor.evaluate("train the model with cauldron over 1000 generations");

        assertThat(result.energyCost()).isGreaterThan(0.3);
    }

    @Test
    void shouldDetectModerateCostForSnapshot() {
        var result = auditor.evaluate("create full snapshot of all clusters");

        assertThat(result.energyCost()).isGreaterThan(0.2);
    }

    @Test
    void shouldReduceCostForOptimization() {
        var result = auditor.evaluate("optimize and compress neural tables");

        assertThat(result.energyCost()).isLessThan(0.3);
    }

    @Test
    void shouldCalculateCarbonEstimate() {
        var result = auditor.evaluate("train cauldron evolution");

        assertThat(result.carbonEstimate()).isGreaterThanOrEqualTo(0);
    }
}
