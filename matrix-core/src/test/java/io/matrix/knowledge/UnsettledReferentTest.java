package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — a question whose referent the conversation has not settled
 * gets a question back, not a guess.
 *
 * <p>The mission's §6: an ambiguous request should trigger a
 * clarification, not an answer. "How does it work?" refers to something
 * and names no subject, and the demand analysis saw only a knowledge
 * question - so the workspace refused it with the generic "no claim
 * covers enough of the question" and never asked.
 */
class UnsettledReferentTest {

    @Test
    @DisplayName("a question with an unsettled referent asks which one is meant")
    void aQuestionWithAnUnsettledReferentAsksWhichOneIsMeant(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis", "How does it work?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
            assertThat(answer.abstentionReason()).contains("the conversation has not settled");
            assertThat(answer.abstentionReason()).contains("What does it refer to?");
        }
    }

    @Test
    @DisplayName("the conversation settles the referent, and the system stops asking")
    void theConversationSettlesTheReferentAndTheSystemStopsAsking(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);

            // Now the conversation HAS a subject, so the referent is
            // settled and the system no longer asks what the question is
            // about. It still refuses - the workspace holds no claim that
            // covers "work" - but the refusal is the coverage refusal and
            // not a question back.
            //
            // A second try with the subject named was tried and reverted:
            // it answered "Who invented propositional logic?" and the
            // abstention quality fell from 0.900 to 0.700. A question that
            // the workspace cannot answer must not become answerable by
            // the conversation's subject.
            Answer answer = workbench.ask("photosynthesis", "How does it work?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
            assertThat(answer.abstentionReason()).doesNotContain("has not settled");
        }
    }

    @Test
    @DisplayName("a question that names a subject is answered, not asked about")
    void aQuestionThatNamesASubjectIsAnsweredNotAskedAbout(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Какие газообразные вещества участвуют в фотосинтезе?",
                    AbstractionLevel.INTRODUCTORY);

            // The Russian reference words are matched as WORDS. The first
            // version matched them as substrings, and the Russian "те"
            // matched inside another word - so this question was read as
            // referring to something, and the workspace asked what it
            // referred to instead of answering. The accuracy fell from
            // 0.917 to 0.875.
            assertThat(answer.answered()).isTrue();
            assertThat(answer.abstentionReason()).isNull();
        }
    }

    @Test
    @DisplayName("the reference words are words, not substrings")
    void theReferenceWordsAreWordsNotSubstrings() {
        // "те" is a reference word in Russian, and it appears inside
        // "участвуют" - which is not a reference.
        assertThat(DialogueContext.hasReference("Какие вещества участвуют в фотосинтезе?"))
                .isFalse();
        assertThat(DialogueContext.hasReference("Объясни это подробнее")).isTrue();
        assertThat(DialogueContext.hasReference("Explain this in more detail")).isTrue();
        assertThat(DialogueContext.hasReference("What is photosynthesis?")).isFalse();
    }
}
