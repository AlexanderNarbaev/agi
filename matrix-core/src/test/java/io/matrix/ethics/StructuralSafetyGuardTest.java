package io.matrix.ethics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuralSafetyGuardTest {

    private StructuralSafetyGuard guard;

    @BeforeEach
    void setup() {
        guard = StructuralSafetyGuard.defaults();
    }

    // ── Removed Tools (Pattern 1) ──

    @Test
    void shouldBlockRemovedTools() {
        var verdict = guard.evaluate("delete_database", Map.of());

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.BLOCKED);
        assertThat(verdict.riskLevel()).isEqualTo(StructuralSafetyGuard.RiskLevel.CRITICAL);
    }

    @Test
    void shouldBlockAllDefaultRemovedTools() {
        for (String tool : Set.of("delete_database", "drop_table", "format_disk",
                "kill_process", "rm_rf")) {
            var verdict = guard.evaluate(tool, Map.of());
            assertThat(verdict.decision())
                    .as("Tool '%s' should be blocked", tool)
                    .isEqualTo(StructuralSafetyGuard.Decision.BLOCKED);
        }
    }

    @Test
    void shouldReportToolNotAvailable() {
        assertThat(guard.isToolAvailable("delete_database")).isFalse();
        assertThat(guard.isToolAvailable("read_data")).isTrue();
    }

    @Test
    void shouldFilterAvailableTools() {
        var requested = Set.of("delete_database", "read_data", "drop_table", "write_data");
        var available = guard.filterAvailableTools(requested);

        assertThat(available).containsExactlyInAnyOrder("read_data", "write_data");
    }

    // ── Gated Operations (Pattern 2) ──

    @Test
    void shouldRequireApprovalForGatedOperations() {
        var verdict = guard.evaluate("deploy_production", Map.of());

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.REQUIRES_APPROVAL);
        assertThat(verdict.gateId()).isPresent();
    }

    @Test
    void shouldGateAllDefaultOperations() {
        for (String op : Set.of("deploy_production", "modify_ethics",
                "access_credentials", "modify_safety_constraints")) {
            var verdict = guard.evaluate(op, Map.of());
            assertThat(verdict.decision())
                    .as("Operation '%s' should require approval", op)
                    .isEqualTo(StructuralSafetyGuard.Decision.REQUIRES_APPROVAL);
        }
    }

    // ── Risk-Based Autonomy (Pattern 3) ──

    @Test
    void shouldApproveLowRiskOperations() {
        var verdict = guard.evaluate("read_data", Map.of());

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.APPROVED);
        assertThat(verdict.riskLevel()).isEqualTo(StructuralSafetyGuard.RiskLevel.LOW);
    }

    @Test
    void shouldApproveMediumRiskInDevelopment() {
        var verdict = guard.evaluate("write_data", Map.of("environment", "development"));

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.APPROVED);
    }

    @Test
    void shouldRequireApprovalForMediumRiskInProduction() {
        var verdict = guard.evaluate("write_data", Map.of("environment", "production"));

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.REQUIRES_APPROVAL);
    }

    @Test
    void shouldRequireApprovalForHighRisk() {
        var verdict = guard.evaluate("delete_data", Map.of());

        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.REQUIRES_APPROVAL);
        assertThat(verdict.riskLevel()).isEqualTo(StructuralSafetyGuard.RiskLevel.HIGH);
    }

    // ── Custom Configuration ──

    @Test
    void shouldBuildCustomGuard() {
        var custom = StructuralSafetyGuard.builder()
                .removeTool("custom_tool")
                .gateOperation("custom_op")
                .riskLevel("custom_risk", StructuralSafetyGuard.RiskLevel.LOW)
                .maxAutonomy(0.5)
                .build();

        assertThat(custom.isToolAvailable("custom_tool")).isFalse();
        assertThat(custom.evaluate("custom_op", Map.of()).decision())
                .isEqualTo(StructuralSafetyGuard.Decision.REQUIRES_APPROVAL);
        assertThat(custom.evaluate("custom_risk", Map.of()).decision())
                .isEqualTo(StructuralSafetyGuard.Decision.APPROVED);
    }

    @Test
    void shouldRejectInvalidAutonomy() {
        assertThatThrownBy(() -> StructuralSafetyGuard.builder().maxAutonomy(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuralSafetyGuard.builder().maxAutonomy(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Edge Cases ──

    @Test
    void shouldHandleUnknownOperations() {
        var verdict = guard.evaluate("unknown_operation", Map.of());

        // Unknown operations default to MEDIUM risk
        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.APPROVED);
        assertThat(verdict.riskLevel()).isEqualTo(StructuralSafetyGuard.RiskLevel.MEDIUM);
    }

    @Test
    void shouldHandleNullContext() {
        assertThatThrownBy(() -> guard.evaluate("read_data", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullOperation() {
        assertThatThrownBy(() -> guard.evaluate(null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldProvideReasonForAllDecisions() {
        var verdicts = java.util.List.of(
                guard.evaluate("delete_database", Map.of()),
                guard.evaluate("deploy_production", Map.of()),
                guard.evaluate("read_data", Map.of()),
                guard.evaluate("write_data", Map.of("environment", "production"))
        );

        for (var verdict : verdicts) {
            assertThat(verdict.reason()).isNotBlank();
        }
    }
}
