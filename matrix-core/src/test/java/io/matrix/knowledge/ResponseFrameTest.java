package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the frame of the answer speaks the language of the
 * question.
 *
 * <p>The owner's decision: translate the frame, not the knowledge. The
 * system's own sentences are said in the user's language; the workspace's
 * own statements stay in the language they were written in, and the answer
 * says so.
 */
class ResponseFrameTest {

    @Test
    @DisplayName("the refusal is in the language of the question")
    void theRefusalIsInTheLanguageOfTheQuestion(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer russian = workbench.ask("photosynthesis",
                    "Какой ионный канал регулирует захват калия в клетке замыкания?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(russian.answered()).isFalse();
            assertThat(russian.abstentionReason()).contains("Ни одно утверждение");
            assertThat(russian.abstentionReason()).doesNotContain("No claim in this workspace");

            Answer english = workbench.ask("photosynthesis",
                    "Which ion channel controls the potassium uptake of a guard cell?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(english.answered()).isFalse();
            assertThat(english.abstentionReason()).contains("No claim in this workspace");
        }
    }

    @Test
    @DisplayName("the language note is in the language of the question")
    void theLanguageNoteIsInTheLanguageOfTheQuestion(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer russian = workbench.ask("photosynthesis", "Что такое фотосинтез?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(russian.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("Вопрос на ru языке, а знание — на en"));
        }
    }

    @Test
    @DisplayName("the knowledge itself is not translated")
    void theKnowledgeItselfIsNotTranslated(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer russian = workbench.ask("photosynthesis", "Что такое фотосинтез?",
                    AbstractionLevel.INTRODUCTORY);

            // The statement is the workspace's own text, in the language the
            // workspace holds it.
            assertThat(russian.statement()).contains("Photosynthesis");
            assertThat(russian.statement()).contains("green plants");
        }
    }

    @Test
    @DisplayName("the frame says what it did not translate")
    void theFrameSaysWhatItDidNotTranslate(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer russian = workbench.ask("photosynthesis", "Что такое фотосинтез?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(russian.limitations()).anySatisfy(limitation -> {
                assertThat(limitation).contains("it does not translate the answer");
            });
        }
    }

    @Test
    @DisplayName("a sentence without a translation stays in English")
    void aSentenceWithoutATranslationStaysInEnglish() {
        // The frames are a closed set the system owns. A sentence that is
        // not one of them is left alone rather than mangled.
        assertThat(ResponseFrame.in("ru", "A sentence the system does not own."))
                .isEqualTo("A sentence the system does not own.");
        assertThat(ResponseFrame.in("ru", "")).isEmpty();
        assertThat(ResponseFrame.in("en", "No claim in this workspace covers enough of the "
                + "question to answer it."))
                .isEqualTo("No claim in this workspace covers enough of the question to answer it.");
    }

    @Test
    @DisplayName("the templates keep the values they were given")
    void theTemplatesKeepTheValuesTheyWereGiven() {
        String english = ResponseFrame.template("en",
                "The question is in {question} and the knowledge is in {knowledge}.",
                java.util.Map.of("question", "ru", "knowledge", "en"));
        assertThat(english).isEqualTo("The question is in ru and the knowledge is in en.");

        String russian = ResponseFrame.template("ru",
                "The question is in {question} and the knowledge is in {knowledge}.",
                java.util.Map.of("question", "ru", "knowledge", "en"));
        assertThat(russian).contains("Вопрос на ru языке, а знание — на en");
        assertThat(russian).doesNotContain("{question}");
    }

    @Test
    @DisplayName("the frame's own set is known")
    void theFramesOwnSetIsKnown() {
        assertThat(ResponseFrame.size()).isGreaterThanOrEqualTo(8);
        assertThat(ResponseFrame.knows("No claim in this workspace covers enough of the question "
                + "to answer it.")).isTrue();
        assertThat(ResponseFrame.knows("Something else entirely.")).isFalse();
    }
}
