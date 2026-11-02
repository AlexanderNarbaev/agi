package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the application of a stated rule to a new instance.
 *
 * <p>The mission's §7 names the transfer category: a principle that has
 * been grasped must be applied to an instance it was not stated in. The
 * workspace can state modus ponens, and it could not apply it.
 */
class RuleApplicationTest {

    @Test
    @DisplayName("a question that states a conditional and asks what follows is applied")
    void aQuestionThatStatesAConditionalAndAsksWhatFollowsIsApplied(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            Answer answer = workbench.ask("logic",
                    "A rule says: if it rains then the ground is wet. It rains. What must be true?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            // The answer applies the rule to the instance the question
            // stated - and the instance is the question's own text.
            assertThat(answer.statement()).contains("wet");
            // And it is GROUNDED: the workspace holds the rule, and the
            // answer says which claim states it.
            assertThat(answer.statement()).contains("if p implies q");
            assertThat(answer.limitations()).anyMatch(limitation ->
                    limitation.startsWith("The rule applied is stated by the workspace"));
        }
    }

    @Test
    @DisplayName("a second instance of the same rule is applied as well")
    void aSecondInstanceOfTheSameRuleIsAppliedAsWell(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            // The mechanism is not tied to the benchmark's instance.
            Answer answer = workbench.ask("logic",
                    "A rule says: if the switch is on then the lamp is lit. The switch is on. "
                            + "What must be true?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            assertThat(answer.statement()).contains("lit");
        }
    }

    @Test
    @DisplayName("a question that does not ask for an inference is not an application")
    void aQuestionThatDoesNotAskForAnInferenceIsNotAnApplication(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            // "What is modus ponens?" is a knowledge question, not a
            // request to apply anything, and it must not be answered with
            // an instance.
            Answer answer = workbench.ask("logic", "What is modus ponens?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            assertThat(answer.statement()).doesNotContain("Applied here");
        }
    }

    @Test
    @DisplayName("a workspace that holds no rule is not licensed to apply one")
    void aWorkspaceThatHoldsNoRuleIsNotLicensedToApplyOne(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The photosynthesis package holds no rule of inference, so the
            // application is not licensed, and the answer does not invent
            // one.
            Answer answer = workbench.ask("photosynthesis",
                    "A rule says: if it rains then the ground is wet. It rains. What must be true?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.statement()).doesNotContain("Applied here");
        }
    }
}
