package io.matrix.ethics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
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

    // ── Security: Immutability & structural bypass tests ──

    @Test
    void removedToolsSetShouldBeImmutable() {
        Set<String> removed = guard.removedTools();
        assertThatThrownBy(() -> removed.add("new_tool"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> removed.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void gatedOperationsSetShouldBeImmutable() {
        Set<String> gated = guard.gatedOperations();
        assertThatThrownBy(() -> gated.add("new_op"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> gated.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotAllowReAddingRemovedToolsViaReturnedSet() {
        Set<String> removed = guard.removedTools();
        int originalSize = removed.size();
        assertThatThrownBy(() -> removed.add("delete_database"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(guard.removedTools()).hasSize(originalSize);
    }

    @Test
    void removedToolsShouldStayBlockedRegardlessOfContext() {
        // Even with "safe" context, removed tools remain blocked
        for (String tool : Set.of("delete_database", "drop_table", "format_disk")) {
            var verdict = guard.evaluate(tool, Map.of("environment", "development", "approved", "true"));
            assertThat(verdict.decision())
                    .as("Removed tool '%s' must stay BLOCKED regardless of context", tool)
                    .isEqualTo(StructuralSafetyGuard.Decision.BLOCKED);
        }
    }

    @Test
    void shouldBlockCriticalRiskOperations() {
        var custom = StructuralSafetyGuard.builder()
                .riskLevel("nuke", StructuralSafetyGuard.RiskLevel.CRITICAL)
                .build();

        var verdict = custom.evaluate("nuke", Map.of());
        assertThat(verdict.decision()).isEqualTo(StructuralSafetyGuard.Decision.BLOCKED);
    }

    @Test
    void classShouldBeFinal() {
        assertThat(Modifier.isFinal(StructuralSafetyGuard.class.getModifiers())).isTrue();
    }

    @Test
    void removedToolsFieldShouldBeFinal() throws Exception {
        Field field = StructuralSafetyGuard.class.getDeclaredField("removedTools");
        assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
    }

    @Test
    void defaultRemovedToolsShouldContainExpectedDangerousTools() {
        Set<String> expected = Set.of("delete_database", "drop_table", "format_disk",
                "kill_process", "rm_rf");
        assertThat(guard.removedTools()).containsExactlyInAnyOrderElementsOf(expected);
    }

    // ── GAP-015: Deterministic Gate IDs (no UUID.randomUUID) ──

    @Test
    void gateIdsShouldFollowDeterministicFormat() {
        var verdict = guard.evaluate("deploy_production", Map.of());

        assertThat(verdict.gateId()).isPresent();
        String id = verdict.gateId().get();
        // Format: gate-<operation>-<7-digit counter>-<8-hex context hash>
        assertThat(id).matches("^gate-deploy_production-\\d{7}-[0-9a-f]{8}$");
    }

    @Test
    void gateIdsShouldBeUniquePerCall() {
        var v1 = guard.evaluate("deploy_production", Map.of());
        var v2 = guard.evaluate("deploy_production", Map.of());

        assertThat(v1.gateId()).isPresent();
        assertThat(v2.gateId()).isPresent();
        assertThat(v1.gateId().get()).isNotEqualTo(v2.gateId().get());
    }

    @Test
    void gateIdsShouldIncludeContextHash() {
        // Both ops are HIGH risk → both require approval → both produce gate IDs.
        var ctxA = guard.evaluate("delete_data", Map.of("environment", "production"));
        var ctxB = guard.evaluate("delete_data", Map.of("environment", "development"));

        assertThat(ctxA.gateId()).isPresent();
        assertThat(ctxB.gateId()).isPresent();
        // Same operation/different context → context-hash tail differs.
        String suffixA = ctxA.gateId().get().substring(ctxA.gateId().get().lastIndexOf('-') + 1);
        String suffixB = ctxB.gateId().get().substring(ctxB.gateId().get().lastIndexOf('-') + 1);
        assertThat(suffixA).isNotEqualTo(suffixB);
    }

    @Test
    void gateIdsShouldBeStableForIdenticalContext() {
        // Determinism check: identical inputs must produce identical tail hashes.
        // (Counter differs, but the context hash segment is stable.)
        var v1 = guard.evaluate("write_data", Map.of("environment", "production"));
        var v2 = guard.evaluate("write_data", Map.of("environment", "production"));

        String suffix1 = v1.gateId().get().substring(v1.gateId().get().lastIndexOf('-') + 1);
        String suffix2 = v2.gateId().get().substring(v2.gateId().get().lastIndexOf('-') + 1);
        assertThat(suffix1).isEqualTo(suffix2);
    }

    @Test
    void gateIdsShouldNotContainUuidDashes() {
        // UUIDs look like xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx — gate IDs must NOT match.
        var verdict = guard.evaluate("deploy_production", Map.of());
        String id = verdict.gateId().get();
        // Strip our known fixed prefixes — no remainder should look like a UUID.
        String remainder = id.replaceAll("^gate-deploy_production-\\d{7}-", "");
        assertThat(remainder).matches("^[0-9a-f]{8}$"); // 8 hex chars, not a 32-char UUID
    }
}
