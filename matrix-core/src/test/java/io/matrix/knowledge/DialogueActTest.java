package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.3 — the class of an utterance is recognised.
 *
 * <p>The mission's §5 names the classes a dialogue must distinguish:
 * "question, correction, observation, hypothesis, teach-request, example,
 * failed-explanation, experiment-result".
 */
class DialogueActTest {

    @Test
    @DisplayName("a correction is recognised")
    void aCorrectionIsRecognised() {
        assertThat(DialogueAct.classify("No, that's wrong."))
                .isEqualTo(DialogueAct.Act.CORRECTION);
        assertThat(DialogueAct.classify("you are wrong about that"))
                .isEqualTo(DialogueAct.Act.CORRECTION);
        assertThat(DialogueAct.classify("Это неверно."))
                .isEqualTo(DialogueAct.Act.CORRECTION);
        assertThat(DialogueAct.isCorrection("No, that's wrong.")).isTrue();
    }

    @Test
    @DisplayName("the other classes are recognised")
    void theOtherClassesAreRecognised() {
        assertThat(DialogueAct.classify("What is photosynthesis?"))
                .isEqualTo(DialogueAct.Act.QUESTION);
        assertThat(DialogueAct.classify("I don't understand the Calvin cycle"))
                .isEqualTo(DialogueAct.Act.FAILED_EXPLANATION);
        assertThat(DialogueAct.classify("Maybe the rate depends on temperature"))
                .isEqualTo(DialogueAct.Act.HYPOTHESIS);
        assertThat(DialogueAct.classify("Teach me about glycolysis"))
                .isEqualTo(DialogueAct.Act.TEACH_REQUEST);
        assertThat(DialogueAct.classify("For example, in a plant leaf"))
                .isEqualTo(DialogueAct.Act.EXAMPLE);
        assertThat(DialogueAct.classify("I tried it and the result was 2 ATP"))
                .isEqualTo(DialogueAct.Act.EXPERIMENT_RESULT);
        assertThat(DialogueAct.classify("I see that the rate rises with light"))
                .isEqualTo(DialogueAct.Act.OBSERVATION);
    }

    @Test
    @DisplayName("a correction names the claims that are open to it, and overwrites nothing")
    void aCorrectionNamesTheClaimsThatAreOpenToIt(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer first = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(first.answered()).isTrue();
            int claimsBefore = workbench.workspace("photosynthesis").claims().size();

            Answer correction = workbench.ask("photosynthesis", "No, that's wrong.",
                    AbstractionLevel.INTRODUCTORY);

            // The claims are named, and the gap says what a correction needs.
            assertThat(correction.gaps()).anySatisfy(gap -> {
                assertThat(gap).contains("claim(s) of the previous answer");
                assertThat(gap).contains("evidence");
                assertThat(gap).contains("nothing is overwritten");
            });
            // And nothing was overwritten.
            assertThat(workbench.workspace("photosynthesis").claims()).hasSize(claimsBefore);
        }
    }

    @Test
    @DisplayName("the turn of a conversation carries its class")
    void theTurnOfAConversationCarriesItsClass(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
            workbench.ask("photosynthesis", "Maybe the rate depends on light",
                    AbstractionLevel.INTRODUCTORY);

            DialogueContext context = workbench.context("photosynthesis");
            assertThat(context.turns()).hasSize(2);
            assertThat(context.turns().get(0).act()).isEqualTo(DialogueAct.Act.QUESTION);
            assertThat(context.turns().get(1).act()).isEqualTo(DialogueAct.Act.HYPOTHESIS);
        }
    }
}
