package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W4 Step 4 — EvalBattery PLANNING_DEPTH category.
 */
class EvalBatteryPlanningDepthTest {

    @Test
    void planning_depth_category_has_at_least_3_probes() {
        long count = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.PLANNING_DEPTH)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    void planning_depth_probes_have_unique_ids() {
        List<String> ids = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.PLANNING_DEPTH)
            .map(EvalBattery.Probe::id)
            .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void planning_depth_probes_require_multi_step_input() {
        // Each PD probe has compound input (multi-op, embedded word, etc.)
        // to ensure the planning path is the right place to solve it.
        for (EvalBattery.Probe p : EvalBattery.standardBattery()) {
            if (p.category() != EvalBattery.Probe.Category.PLANNING_DEPTH) continue;
            // Has at least 2 numeric tokens OR has "twice"/"plus"/"times"/"minus"/"ten"
            String input = p.input().toLowerCase();
            boolean compound = input.matches(".*\\d.*[+\\-*/].*\\d.*")   // 2+ numbers with op
                || input.contains("twice")
                || input.contains("plus")
                || input.contains("times")
                || input.contains("minus")
                || input.contains("ten");
            assertThat(compound).as("PD probe must be compound: " + p.id()).isTrue();
        }
    }
}
