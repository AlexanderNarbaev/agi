package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.6 — the error-driven integration happens only after the error
 * is verified.
 *
 * <p>The mission's §9: "error-driven learning integration only after
 * verifying the math, 8 required error fields", and "do not
 * indiscriminately weaken neural associations after every unexpected
 * event".
 */
class ErrorIntegratorTest {

    @Test
    @DisplayName("a single wrong answer is not integrated")
    void aSingleWrongAnswerIsNotIntegrated(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);

            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");

            ErrorIntegrator.Result result = ErrorIntegrator.integrate(workbench, "photosynthesis");

            assertThat(result.candidates()).isGreaterThan(0);
            assertThat(result.verified()).isEqualTo(0);
            assertThat(result.recorded()).isEqualTo(0);
            assertThat(result.unverified()).contains(item.id());
            assertThat(result.changedAnything()).isFalse();
        }
    }

    @Test
    @DisplayName("the same error in two sessions is integrated")
    void theSameErrorInTwoSessionsIsIntegrated(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);

            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-two");

            ErrorIntegrator.Result result = ErrorIntegrator.integrate(workbench, "photosynthesis");

            assertThat(result.verified()).isEqualTo(1);
            assertThat(result.recorded()).isEqualTo(1);
            LearningError error = result.errors().get(0);
            assertThat(error.itemId()).isEqualTo(item.id());
            assertThat(error.conceptId()).isEqualTo(item.conceptId());
            assertThat(error.dimension()).isEqualTo(item.dimension());
            assertThat(error.verified()).isTrue();
            // The eight fields are all there.
            assertThat(error.id()).isNotBlank();
            assertThat(error.workspaceId()).isNotBlank();
            assertThat(error.learnerAnswer()).isNotBlank();
            assertThat(error.expectedAnswer()).isNotBlank();
        }
    }

    @Test
    @DisplayName("an error answered rightly afterwards is not integrated")
    void anErrorAnsweredRightlyAfterwardsIsNotIntegrated(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);

            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-two");
            workbench.assess("photosynthesis", item.id(), String.join(" ", item.keyTerms()), 0.9,
                    "session-three");

            ErrorIntegrator.Result result = ErrorIntegrator.integrate(workbench, "photosynthesis");

            // The learner has it right now: the error is not the current
            // state of the learner, and integrating it would train the
            // system on something the learner has already overcome.
            assertThat(result.verified()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("the verified error's concept is what the selector prefers")
    void theVerifiedErrorsConceptIsWhatTheSelectorPrefers(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);

            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-two");
            ErrorIntegrator.integrate(workbench, "photosynthesis");

            Set<String> concepts = ErrorIntegrator.conceptsWithVerifiedErrors(workbench,
                    "photosynthesis");
            assertThat(concepts).contains(item.conceptId());

            ExerciseSelector.Selection selection = workbench.nextExercise("photosynthesis");
            assertThat(selection).isNotNull();
            assertThat(selection.item().conceptId()).isEqualTo(item.conceptId());
            assertThat(selection.reason()).contains("verified");
        }
    }

    @Test
    @DisplayName("the integration is a phase of the consolidation")
    void theIntegrationIsAPhaseOfTheConsolidation(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-two");

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", System.currentTimeMillis(),
                    new KnowledgeConsolidator.Budget(10, 0));

            assertThat(report.integration()).isNotNull();
            assertThat(report.integration().recorded()).isEqualTo(1);
            assertThat(report.invariants()).anySatisfy(invariant ->
                    assertThat(invariant).contains("VERIFIED"));
        }
    }

    @Test
    @DisplayName("the integrated error survives a restart")
    void theIntegratedErrorSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-one");
            workbench.assess("photosynthesis", item.id(), "wrong", 0.5, "session-two");
            ErrorIntegrator.integrate(workbench, "photosynthesis");
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            List<LearningError> errors = workbench.store().learningErrors("photosynthesis");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).verified()).isTrue();
            assertThat(ErrorIntegrator.conceptsWithVerifiedErrors(workbench, "photosynthesis"))
                    .hasSize(1);
        }
    }
}
