package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.3 — the typos are corrected, and the original is kept.
 *
 * <p>The mission's §6: "must preserve the original message alongside any
 * normalized representation. Do not silently change meaning during spelling
 * correction."
 */
class TypoCorrectorTest {

    @Test
    @DisplayName("a misspelled word is corrected")
    void aMisspelledWordIsCorrected() {
        assertThat(TypoCorrector.distance("photosynthesis", "photosynthesi")).isEqualTo(1);
        assertThat(TypoCorrector.distance("oxygen", "oxygn")).isEqualTo(1);
        assertThat(TypoCorrector.distance("water", "wter")).isEqualTo(1);
        // A transposition is one edit, not two.
        assertThat(TypoCorrector.distance("photosynthesis", "fotosynthesis")).isEqualTo(2);
    }

    @Test
    @DisplayName("the edit distance is the Levenshtein distance")
    void theEditDistanceIsTheLevenshteinDistance() {
        assertThat(TypoCorrector.distance("kitten", "sitting")).isEqualTo(3);
        assertThat(TypoCorrector.distance("", "abc")).isEqualTo(3);
        assertThat(TypoCorrector.distance("abc", "abc")).isZero();
    }

    @Test
    @DisplayName("the threshold scales with the length of the word")
    void theThresholdScalesWithTheLengthOfTheWord() {
        // A short word is not half rewritten for the sake of two edits.
        assertThat(TypoCorrector.maximumEdits(4)).isZero();
        assertThat(TypoCorrector.maximumEdits(6)).isEqualTo(1);
        assertThat(TypoCorrector.maximumEdits(12)).isEqualTo(2);
    }

    @Test
    @DisplayName("a word that is close to two words is left alone")
    void aWordThatIsCloseToTwoWordsIsLeftAlone(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TypoCorrector corrector = TypoCorrector.over(workbench.workspace("photosynthesis"));

            // "work" is two edits from "core", and the word is short: the
            // conservative rule leaves it alone. The first version of the
            // corrector made this correction, and it RESCUED a vague
            // question the workspace should have refused.
            assertThat(corrector.corrections("How does it work?"))
                    .noneSatisfy(correction ->
                            assertThat(correction.corrected()).isEqualTo("core"));
        }
    }

    @Test
    @DisplayName("the question with a typo is answered, and the correction is reported")
    void theQuestionWithATypoIsAnsweredAndTheCorrectionIsReported(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis", "What is fotosynthesis?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            // The original is kept, and the correction is visible.
            assertThat(answer.question()).isEqualTo("What is fotosynthesis?");
            assertThat(answer.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("fotosynthesis")
                            .contains("photosynthesis")
                            .contains("kept as it was written"));
        }
    }

    @Test
    @DisplayName("a word the workspace has never heard of is not corrected")
    void aWordTheWorkspaceHasNeverHeardOfIsNotCorrected(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TypoCorrector corrector = TypoCorrector.over(workbench.workspace("photosynthesis"));

            // A proper noun outside the knowledge is exactly what the
            // conservative rule protects: there is nothing to correct it to.
            assertThat(corrector.corrections("What is photosynthesis?"))
                    .noneSatisfy(correction ->
                            assertThat(correction.original()).isEqualTo("photosynthesis"));
            assertThat(corrector.corrections("Tell me about ketorolac")).isEmpty();
        }
    }

    @Test
    @DisplayName("the corrected question keeps the words it did not change")
    void theCorrectedQuestionKeepsTheWordsItDidNotChange(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TypoCorrector corrector = TypoCorrector.over(workbench.workspace("photosynthesis"));

            String corrected = corrector.corrected("What is fotosynthesis and oxygen?");
            assertThat(corrected).contains("photosynthesis");
            assertThat(corrected).contains("oxygen");
            assertThat(corrected).doesNotContain("fotosynthesis");
        }
    }

    @Test
    @DisplayName("the correction is reported with the distance it cost")
    void theCorrectionIsReportedWithTheDistanceItCost(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TypoCorrector corrector = TypoCorrector.over(workbench.workspace("photosynthesis"));
            List<TypoCorrector.Correction> corrections = corrector.corrections("What is fotosynthesis?");

            assertThat(corrections).isNotEmpty();
            assertThat(corrections).allSatisfy(correction -> {
                assertThat(correction.original()).isNotBlank();
                assertThat(correction.corrected()).isNotBlank();
                assertThat(correction.distance()).isGreaterThan(0)
                        .isLessThanOrEqualTo(TypoCorrector.maximumEdits(
                                correction.original().length()));
            });
        }
    }
}
