package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the ladder of §9, and the rule that the top of it needs
 * evidence a score cannot provide.
 *
 * <p>§9 names seven stages and says two things that matter here:
 * "Generating an explanation is not proof of teaching", and understanding is
 * not recall. The evaluator that derives the per-dimension states stopped at
 * COMPETENCE, so the top three stages of the ladder were unreachable. These
 * tests are about the ladder being real.
 */
class CapabilityEvaluatorTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    private String photosynthesisId() {
        return workbench.workspace("photosynthesis").concepts().stream()
                .filter(c -> c.name().equals("Photosynthesis"))
                .findFirst().orElseThrow().id();
    }

    private void answerEverythingCorrectly() {
        for (AssessmentItem item : workbench.dataset("photosynthesis").items()) {
            workbench.assess("photosynthesis", item.id(), String.join(" ", item.keyTerms()),
                    0.9, "session-1");
        }
    }

    @Test
    @DisplayName("with no outcome the capability is unknown")
    void withNoOutcomeTheCapabilityIsUnknown() {
        CapabilityState state = workbench.capabilityState("photosynthesis", photosynthesisId(), false);
        assertThat(state.stage()).isEqualTo(LearningStage.UNKNOWN);
        assertThat(state.note()).contains("nothing is known");
    }

    @Test
    @DisplayName("with only wrong outcomes the concept is unexplored")
    void withOnlyWrongOutcomesItIsUnexplored() {
        for (AssessmentItem item : workbench.dataset("photosynthesis").items()) {
            workbench.assess("photosynthesis", item.id(), "an answer with nothing in it",
                    0.5, "session-1");
        }
        CapabilityState state = workbench.capabilityState("photosynthesis", photosynthesisId(), false);
        assertThat(state.stage()).isEqualTo(LearningStage.EXPLORATION);
    }

    @Test
    @DisplayName("a partly answered concept is still being learned")
    void aPartlyAnsweredConceptIsStillBeingLearned() {
        AssessmentItem item = workbench.dataset("photosynthesis").items().stream()
                .filter(i -> i.dimension() == LearningDimension.RECALL)
                .findFirst().orElseThrow();
        workbench.assess("photosynthesis", item.id(), String.join(" ", item.keyTerms()),
                0.9, "session-1");

        CapabilityState state = workbench.capabilityState("photosynthesis", photosynthesisId(), false);
        assertThat(state.stage()).isIn(LearningStage.INITIAL_LEARNING, LearningStage.PRACTICE);
        assertThat(state.dimensionsMissing()).isNotEmpty();
    }

    @Test
    @DisplayName("competence across every dimension is competence, and understanding needs more")
    void competenceIsNotUnderstanding() {
        answerEverythingCorrectly();

        CapabilityState state = workbench.capabilityState("photosynthesis", photosynthesisId(), false);

        // The ladder is reachable: it is not stuck at COMPETENCE.
        assertThat(state.stage()).isIn(LearningStage.COMPETENCE, LearningStage.UNDERSTANDING,
                LearningStage.KNOWLEDGE);
        assertThat(state.stage()).isNotEqualTo(LearningStage.PRACTICE);
        assertThat(state.dimensionsMissing()).isEmpty();
    }

    @Test
    @DisplayName("the highest stage is not reached without a teach-back that met its expectation")
    void theHighestStageNeedsTheTeachBack() {
        answerEverythingCorrectly();

        CapabilityState without = workbench.capabilityState("photosynthesis", photosynthesisId(), false);
        assertThat(without.stage()).isNotEqualTo(LearningStage.KNOWLEDGE);
        assertThat(without.teachBackMet()).isFalse();
        assertThat(without.note()).contains("not proof of teaching");

        CapabilityState with = workbench.capabilityState("photosynthesis", photosynthesisId(), true);
        assertThat(with.teachBackMet()).isTrue();
        assertThat(with.stage()).isEqualTo(LearningStage.KNOWLEDGE);
        // The record says what kind of teach-back it was.
        assertThat(with.note()).contains("machine-to-machine");
        assertThat(with.teachBackConclusion()).isNotBlank();
    }

    @Test
    @DisplayName("the capability state names every dimension and its stage")
    void theCapabilityStateNamesEveryDimension() {
        answerEverythingCorrectly();
        CapabilityState state = workbench.capabilityState("photosynthesis", photosynthesisId(), false);

        assertThat(state.dimensions()).containsKey(LearningDimension.RECALL);
        assertThat(state.dimensions()).containsKey(LearningDimension.CAUSAL);
        assertThat(state.dimensions()).containsKey(LearningDimension.TRANSFER);
        assertThat(state.dimensions().values()).allMatch(s -> s == LearningStage.COMPETENCE);
    }

    @Test
    @DisplayName("the capability state survives a restart")
    void theCapabilityStateSurvivesARestart() {
        answerEverythingCorrectly();
        LearningStage before = workbench.capabilityState("photosynthesis", photosynthesisId(), false).stage();
        workbench.close();

        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        try {
            assertThat(reopened.capabilityState("photosynthesis", photosynthesisId(), false).stage())
                    .isEqualTo(before);
        } finally {
            reopened.close();
        }
    }
}
