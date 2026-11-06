package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.5 — the retention is measured against a baseline.
 *
 * <p>The mission's §14 asks that "the delayed retention is measured and
 * improved over a no-consolidation baseline". The experiment compares two
 * selection policies under one scripted learner, so the difference is the
 * policy and not the learner.
 */
class RetentionExperimentTest {

    @Test
    @DisplayName("the experiment compares the two policies and reports the difference")
    void theExperimentComparesTheTwoPoliciesAndReportsTheDifference(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            RetentionExperiment.Report report = RetentionExperiment.run(
                    () -> KnowledgeWorkbench.at(dir.resolve("arm.sqlite").toString()),
                    "photosynthesis", 3);

            assertThat(report.baseline()).isNotNull();
            assertThat(report.consolidation()).isNotNull();
            assertThat(report.baseline().policy())
                    .isEqualTo(RetentionExperiment.Policy.BASELINE_ORDER);
            assertThat(report.consolidation().policy())
                    .isEqualTo(RetentionExperiment.Policy.CONSOLIDATION_SELECTED);
            assertThat(report.itemsInTheDataset()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("the report says what was measured, and what the numbers are not")
    void theReportSaysWhatWasMeasuredAndWhatTheNumbersAreNot(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            RetentionExperiment.Report report = RetentionExperiment.run(
                    () -> KnowledgeWorkbench.at(dir.resolve("arm.sqlite").toString()),
                    "photosynthesis", 3);

            assertThat(report.whatWasMeasured()).contains("selection policy");
            assertThat(report.limits()).isNotEmpty();
            // The limit about the scripted learner is stated, because a
            // number produced by a scripted rule must never be read as a
            // statement about a person.
            assertThat(report.limits()).anySatisfy(limit ->
                    assertThat(limit).contains("not of a person"));
        }
    }

    @Test
    @DisplayName("the consolidation arm reaches a higher retention than the baseline")
    void theConsolidationArmReachesAHigherRetentionThanTheBaseline(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            RetentionExperiment.Report report = RetentionExperiment.run(
                    () -> KnowledgeWorkbench.at(dir.resolve("arm-" + System.nanoTime() + ".sqlite")
                            .toString()),
                    "photosynthesis", 20);

            // MEASURED: the error-driven selection gathers MORE delayed
            // evidence than the plain order, because it revisits the items
            // the learner got wrong - and the retention's criteria are
            // therefore better met.
            assertThat(report.consolidation().retentionEvidence())
                    .isGreaterThan(report.baseline().retentionEvidence());
            assertThat(report.consolidation().retentionStage())
                    .isNotEqualTo(report.baseline().retentionStage());
            assertThat(report.consolidation().retentionStage())
                    .isEqualTo(LearningStage.PRACTICE.name());
        }
    }

    @Test
    @DisplayName("the run is deterministic: the same numbers every time")
    void theRunIsDeterministicTheSameNumbersEveryTime(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            RetentionExperiment.ArmResult first = RetentionExperiment.runArm(workbench,
                    "photosynthesis", RetentionExperiment.Policy.CONSOLIDATION_SELECTED, 3);

            try (KnowledgeWorkbench other = KnowledgeWorkbench.at(dir.resolve("other.sqlite").toString())) {
                other.loadBundledPackage("photosynthesis");
                RetentionExperiment.ArmResult second = RetentionExperiment.runArm(other,
                        "photosynthesis", RetentionExperiment.Policy.CONSOLIDATION_SELECTED, 3);

                assertThat(second.sessions()).isEqualTo(first.sessions());
                assertThat(second.itemsAsked()).isEqualTo(first.itemsAsked());
                assertThat(second.meanScore()).isEqualTo(first.meanScore());
            }
        }
    }

    @Test
    @DisplayName("the delayed retention is measured when the arm ran two sessions")
    void theDelayedRetentionIsMeasuredWhenTheArmRanTwoSessions(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            RetentionExperiment.ArmResult arm = RetentionExperiment.runArm(workbench,
                    "photosynthesis", RetentionExperiment.Policy.CONSOLIDATION_SELECTED, 3);

            assertThat(arm.sessions()).isGreaterThanOrEqualTo(2);
            assertThat(arm.retentionMeasured()).isTrue();
            assertThat(arm.retentionStage()).isNotEqualTo(LearningStage.UNKNOWN.name());
        }
    }
}
