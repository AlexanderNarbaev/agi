package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.3 — the conversation remembers, and it is bounded.
 *
 * <p>The mission's §6 asks for context tracking and reference resolution,
 * and names the cases: "Explain this in more detail" and "Let's return to
 * the topic we studied yesterday".
 */
class DialogueContextTest {

    @Test
    @DisplayName("a question that names no subject is referential")
    void aQuestionThatNamesNoSubjectIsReferential() {
        assertThat(DialogueContext.isReferential("Explain this in more detail")).isTrue();
        assertThat(DialogueContext.isReferential("Tell me more about it")).isTrue();
        assertThat(DialogueContext.isReferential("Объясни это подробнее")).isTrue();
    }

    @Test
    @DisplayName("a question that names a subject is never rewritten")
    void aQuestionThatNamesASubjectIsNeverRewritten() {
        assertThat(DialogueContext.isReferential("What is the Calvin cycle?")).isFalse();
        assertThat(DialogueContext.isReferential("What is photosynthesis?")).isFalse();
        assertThat(DialogueContext.isReferential("How does it work?")).isFalse();
    }

    @Test
    @DisplayName("the reference is resolved against the conversation")
    void theReferenceIsResolvedAgainstTheConversation(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

            DialogueContext context = workbench.context("photosynthesis");
            assertThat(context.size()).isEqualTo(1);
            assertThat(context.resolveSubject("Explain this in more detail"))
                    .isEqualTo("Photosynthesis");
            assertThat(context.expand("Explain this in more detail"))
                    .contains("Photosynthesis");
        }
    }

    @Test
    @DisplayName("the answer keeps the question the user wrote, and says what it read it as")
    void theAnswerKeepsTheQuestionTheUserWrote(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
            Answer answer = workbench.ask("photosynthesis", "Explain this in more detail",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.question()).isEqualTo("Explain this in more detail");
            assertThat(answer.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("resolved against the conversation"));
        }
    }

    @Test
    @DisplayName("the conversation is bounded")
    void theConversationIsBounded(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            for (int i = 0; i < DialogueContext.MAXIMUM_TURNS + 20; i++) {
                workbench.ask("photosynthesis", "What is photosynthesis " + i + "?",
                        AbstractionLevel.INTRODUCTORY);
            }

            DialogueContext context = workbench.context("photosynthesis");
            assertThat(context.size()).isEqualTo(DialogueContext.MAXIMUM_TURNS);
            // The oldest turns are the ones that go.
            assertThat(context.turns().get(0).question()).contains("20");
        }
    }

    @Test
    @DisplayName("the context knows which concepts the conversation touched")
    void theContextKnowsWhichConceptsTheConversationTouched(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
            workbench.ask("photosynthesis", "What is the Calvin cycle?", AbstractionLevel.INTRODUCTORY);

            DialogueContext context = workbench.context("photosynthesis");
            assertThat(context.conceptsTouched()).isNotEmpty();
            assertThat(context.conceptsTouched().size()).isGreaterThanOrEqualTo(2);
        }
    }
}
