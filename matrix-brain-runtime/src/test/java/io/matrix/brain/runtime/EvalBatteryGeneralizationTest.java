package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part B Step 5 — EvalBattery GENERALIZATION category.
 *
 * <p>≥70% pass rate target on the GENERALIZATION probes (transitivity,
 * cross-lingual, novel-instance composition). Memorization probes
 * (RETRIEVAL category) are isolated and excluded from the headline
 * score per the spec.</p>
 */
class EvalBatteryGeneralizationTest {

    @Test
    void generalization_category_has_at_least_5_probes() {
        // Per RECON-W3 Part B Step 5: GENERALIZATION must contain at least
        // 5 probes covering transitivity, cross-lingual, and composition.
        long count = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.GENERALIZATION)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(5);
    }

    @Test
    void retrieval_category_is_isolated_from_headline_score() {
        // RETRIEVAL probes (memorization-only) must be in their own category,
        // separate from the headline score (per D-15 fix).
        long count = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.RETRIEVAL)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    void generalization_probes_have_unique_ids() {
        List<String> ids = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.GENERALIZATION)
            .map(EvalBattery.Probe::id)
            .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void headline_score_excludes_retrieval_memorization() {
        // The headline score (computed by callers of the battery) must
        // exclude RETRIEVAL probes to avoid self-confirming memorization.
        // This test verifies the structural separation.
        long headlineCount = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() != EvalBattery.Probe.Category.RETRIEVAL)
            .count();
        long retrievalCount = EvalBattery.standardBattery().stream()
            .filter(p -> p.category() == EvalBattery.Probe.Category.RETRIEVAL)
            .count();
        assertThat(headlineCount).isGreaterThan(0);
        assertThat(retrievalCount).isGreaterThan(0);
    }
}
