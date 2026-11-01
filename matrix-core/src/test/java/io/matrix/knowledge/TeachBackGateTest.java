package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.1 — the capability's top stage is gated on the teach-back that
 * can fail.
 *
 * <p>The legacy containment measurement scores whether the lesson contained
 * the held-out items' key terms. The lesson and the items are written from
 * the same account, so the measurement cannot fail: the lesson contains the
 * terms by construction. A capability stage gated on it is a stage that can
 * never record a regression.
 */
class TeachBackGateTest {

    private String photosynthesisId(KnowledgeWorkbench workbench) {
        return workbench.workspace("photosynthesis").concepts().stream()
                .filter(concept -> concept.name().equals("Photosynthesis"))
                .map(Concept::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the photosynthesis concept is missing"));
    }

    @Test
    @DisplayName("without a teach-back the top stage is not reached")
    void withoutATeachBackTheTopStageIsNotReached(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            CapabilityState state = workbench.capabilityState("photosynthesis",
                    photosynthesisId(workbench), false);

            assertThat(state.teachBackMet()).isFalse();
            assertThat(state.teachBackConclusion()).isNull();
            // With no outcomes at all the note is about the outcomes, not
            // about the teach-back: the capability simply has no evidence.
            assertThat(state.note()).contains("No outcome has been recorded");
        }
    }

    @Test
    @DisplayName("with a teach-back the conclusion is the rebuilt experiment's")
    void withATeachBackTheConclusionIsTheRebuiltExperiments(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            String conceptId = photosynthesisId(workbench);
            TeachBackExperiment.Result experiment = workbench.teachBackExperiment("photosynthesis",
                    conceptId, CapabilityEvaluator.DEFAULT_EXPECTED_GAIN);

            CapabilityState state = workbench.capabilityState("photosynthesis", conceptId, true);

            // The conclusion is the REBUILT experiment's, not the legacy
            // containment measurement's.
            assertThat(state.teachBackConclusion()).isEqualTo(experiment.conclusion());
            // The conclusion is the REBUILT experiment's: it reports the
            // independent learner's post-lesson accuracy, and it names the
            // legacy containment measurement as the thing it compares with.
            assertThat(state.teachBackConclusion()).contains("post-lesson items");
            assertThat(state.teachBackConclusion()).contains("legacy containment measurement");
            assertThat(state.teachBackMet()).isEqualTo(experiment.metExpectation());
        }
    }

    @Test
    @DisplayName("the rebuilt experiment measures four phases and can fail")
    void theRebuiltExperimentMeasuresFourPhasesAndCanFail(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TeachBackExperiment.Result experiment = workbench.teachBackExperiment("photosynthesis",
                    photosynthesisId(workbench), CapabilityEvaluator.DEFAULT_EXPECTED_GAIN);

            assertThat(experiment.phases()).extracting(TeachBackExperiment.Phase::name)
                    .contains("pre", "post", "delayed", "transfer");
            // The delayed phase is the one that fails: it is the phase that
            // asks whether the learning survived.
            TeachBackExperiment.Phase delayed = experiment.phases().stream()
                    .filter(phase -> phase.name().equals("delayed"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the delayed phase is missing"));
            assertThat(delayed.accuracy()).isLessThan(1.0);
            // And the legacy containment measurement scores higher than the
            // independent learner - which is the whole point.
            assertThat(experiment.legacyAccuracy()).isGreaterThanOrEqualTo(
                    experiment.structuredAccuracy());
        }
    }
}
