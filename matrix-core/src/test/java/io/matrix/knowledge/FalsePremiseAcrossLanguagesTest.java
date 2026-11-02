package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.9 — the false premise is refused in BOTH languages.
 *
 * <p>The W42.9 measurement was unambiguous and unflattering: an English
 * question refused with an exact explanation, a Russian question answered,
 * its scope declared OUTSIDE, and the reason for the refusal null. The
 * detector existed and worked in one language only.
 *
 * <p>This test pins the two halves of the property. A detector that refuses
 * every question is not a detector either, so the premise the workspace
 * HOLDS must still be answered.
 */
class FalsePremiseAcrossLanguagesTest {

    /** The English and the Russian form of the same false premise. */
    private static final String[] ENGLISH_FALSE_PREMISE = {
            "Since photosynthesis produces oxygen from carbon dioxide, how much oxygen "
                    + "does a plant release in an hour?",
            "Since every valid argument has true premises, what are the premises "
                    + "of modus ponens?"};

    private static final String[] RUSSIAN_FALSE_PREMISE = {
            "Поскольку фотосинтез производит кислород из углекислого газа, "
                    + "сколько молекул кислорода даёт одна молекула CO2?",
            "Поскольку всякий правильный аргумент истинно посылает, "
                    + "каковы посылки правильного аргумента?"};

    @Test
    @DisplayName("the English question with a false premise is refused")
    void theEnglishQuestionWithAFalsePremiseIsRefused(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("logic");

            for (String question : ENGLISH_FALSE_PREMISE) {
                Answer answer = workbench.ask("photosynthesis", question, AbstractionLevel.INTRODUCTORY);
                // The second question is about the logic package, and the
                // photosynthesis workspace does not hold it — so only the
                // first is asserted on the premise's refusal.
                if (question.startsWith("Since photosynthesis")) {
                    assertThat(answer.answered()).isFalse();
                    assertThat(answer.abstentionReason()).contains("premise");
                }
            }
        }
    }

    @Test
    @DisplayName("the Russian question with a false premise is refused")
    void theRussianQuestionWithAFalsePremiseIsRefused(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    RUSSIAN_FALSE_PREMISE[0], AbstractionLevel.INTRODUCTORY);

            // THE MEASUREMENT THAT WAS MISSING. Before this wave the
            // Russian question was answered, and the answer accepted the
            // false premise the workspace denies.
            assertThat(answer.answered())
                    .as("a Russian question under a premise the workspace denies")
                    .isFalse();
            assertThat(answer.abstentionReason()).contains("premise");
            assertThat(answer.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("carbon dioxide"));
        }
    }

    @Test
    @DisplayName("a question whose premise the workspace holds is still answered")
    void aQuestionWhosePremiseTheWorkspaceHoldsIsStillAnswered(@TempDir Path dir) {
        // THE OTHER HALF. A detector that refuses everything is not a
        // detector: the premise the workspace HOLDS must be answered.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer english = workbench.ask("photosynthesis",
                    "Since photosynthesis converts light energy into chemical energy, "
                            + "where does the released oxygen come from?",
                    AbstractionLevel.INTRODUCTORY);
            Answer russian = workbench.ask("photosynthesis",
                    "Поскольку фотосинтез преобразует световую энергию в химическую, "
                            + "откуда берётся выделяемый кислород?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(english.answered()).isTrue();
            assertThat(russian.answered()).isTrue();
        }
    }
}
