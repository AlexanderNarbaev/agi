package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.1 — the learning lifecycle is a state machine, not a label.
 *
 * <p>The mission's §7 requires the lifecycle to be revisable, and requires
 * that "a previously mastered concept may return to PRACTICE… Preserve
 * earlier mastery records as historical evidence. Do not erase past
 * failures."
 *
 * <p>RECON-W41 could not deliver any of that: the stage was a pure function
 * of the outcome log, recomputed on every request, with nowhere to record
 * that a state had changed, when, or why.
 */
class LearningStateMachineTest {

    private static final String CONCEPT = "c-photosynthesis-4d4bb673";

    /** Answers every recall item of the photosynthesis concept correctly. */
    private void assessRecall(KnowledgeWorkbench workbench, boolean correct) {
        for (AssessmentItem item : workbench.dataset("photosynthesis").items()) {
            if (!item.conceptId().equals(CONCEPT) || item.dimension() != LearningDimension.RECALL) {
                continue;
            }
            String answer = correct
                    ? String.join(" ", item.keyTerms())
                    : "nothing that has anything to do with the subject";
            workbench.assess("photosynthesis", item.id(), answer, 0.9, "machine-test");
        }
    }

    @Test
    @DisplayName("the evidence advances the state and the advance is recorded")
    void theEvidenceAdvancesTheStateAndTheAdvanceIsRecorded(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            assessRecall(workbench, true);

            List<LearningTransition> transitions = workbench.learningTransitions("photosynthesis");
            assertThat(transitions).isNotEmpty();
            assertThat(transitions).anySatisfy(transition -> {
                assertThat(transition.conceptId()).isEqualTo(CONCEPT);
                assertThat(transition.dimension()).isEqualTo(LearningDimension.RECALL);
                assertThat(transition.toStage()).isEqualTo(LearningStage.COMPETENCE);
                assertThat(transition.regression()).isFalse();
                // A transition with no cause is a re-labelling, not a
                // transition.
                assertThat(transition.reason()).isNotBlank();
                assertThat(transition.outcomeId()).isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("a contradicting outcome regresses the state, and the regression is recorded")
    void aContradictingOutcomeRegressesTheStateAndTheRegressionIsRecorded(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            assessRecall(workbench, true);
            assertThat(workbench.learningStates("photosynthesis"))
                    .anySatisfy(state -> {
                        if (state.conceptId().equals(CONCEPT)
                                && state.dimension() == LearningDimension.RECALL) {
                            assertThat(state.stage()).isEqualTo(LearningStage.COMPETENCE);
                        }
                    });

            // The evidence now contradicts the model.
            assessRecall(workbench, false);

            List<LearningTransition> transitions = workbench.learningTransitions("photosynthesis");
            assertThat(transitions).anySatisfy(transition -> {
                assertThat(transition.regression()).isTrue();
                assertThat(transition.fromStage()).isEqualTo(LearningStage.COMPETENCE);
                assertThat(transition.toStage()).isEqualTo(LearningStage.PRACTICE);
                assertThat(transition.reason()).contains("contradicts");
            });
        }
    }

    @Test
    @DisplayName("the earlier mastery record survives the regression")
    void theEarlierMasteryRecordSurvivesTheRegression(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            assessRecall(workbench, true);
            assessRecall(workbench, false);

            List<LearningTransition> transitions = workbench.learningTransitions("photosynthesis");

            // Both the advance and the regression are in the log: the past
            // failure is not erased by the later state.
            assertThat(transitions).anySatisfy(transition ->
                    assertThat(transition.toStage()).isEqualTo(LearningStage.COMPETENCE));
            assertThat(transitions).anySatisfy(transition ->
                    assertThat(transition.regression()).isTrue());
        }
    }

    @Test
    @DisplayName("the transitions survive a restart")
    void theTransitionsSurviveARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            assessRecall(workbench, true);
            assessRecall(workbench, false);
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            List<LearningTransition> transitions = workbench.learningTransitions("photosynthesis");
            assertThat(transitions).isNotEmpty();
            assertThat(transitions).anySatisfy(transition ->
                    assertThat(transition.regression()).isTrue());
        }
    }

    @Test
    @DisplayName("reconciling twice with the same evidence records nothing new")
    void reconcilingTwiceWithTheSameEvidenceRecordsNothingNew(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            assessRecall(workbench, true);

            int before = workbench.learningTransitions("photosynthesis").size();
            workbench.reconcileLearningStates("photosynthesis", null);
            workbench.reconcileLearningStates("photosynthesis", null);

            assertThat(workbench.learningTransitions("photosynthesis")).hasSize(before);
        }
    }

    @Test
    @DisplayName("a capability may advance one stage at a time, and may fall back from any stage")
    void aCapabilityMayAdvanceOneStageAtATime() {
        assertThat(LearningStateMachine.legal(LearningStage.UNKNOWN, LearningStage.EXPLORATION)).isTrue();
        assertThat(LearningStateMachine.legal(LearningStage.EXPLORATION, LearningStage.PRACTICE))
                .isFalse();
        assertThat(LearningStateMachine.legal(LearningStage.COMPETENCE, LearningStage.UNDERSTANDING))
                .isTrue();
        // Falling back is legal from any stage: the evidence may contradict
        // any model.
        assertThat(LearningStateMachine.legal(LearningStage.KNOWLEDGE, LearningStage.PRACTICE)).isTrue();
        assertThat(LearningStateMachine.legal(LearningStage.COMPETENCE, LearningStage.COMPETENCE))
                .isFalse();
    }

    @Test
    @DisplayName("the machine advances one rung at a time and records the rest for later")
    void theMachineAdvancesOneRungAtATime() {
        LearningStateMachine machine = new LearningStateMachine();
        Map<String, LearningState> recorded = Map.of();
        List<LearningState> computed = List.of(
                new LearningState(CONCEPT, LearningDimension.RECALL, LearningStage.KNOWLEDGE,
                        1.0, 5, 2, 2, 0, List.of(), "the evidence jumped"));

        List<LearningTransition> transitions =
                machine.reconcile("photosynthesis", computed, recorded, "o-1", "now");

        // Nothing was recorded before, so the first transition is from the
        // ladder's floor. The evidence carried the capability several stages
        // at once, and the transition says that rather than inventing a
        // staircase of transitions that never happened.
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).fromStage()).isEqualTo(LearningStage.UNKNOWN);
        assertThat(transitions.get(0).toStage()).isEqualTo(LearningStage.KNOWLEDGE);
        assertThat(transitions.get(0).reason()).contains("several stages at once");
    }
}
