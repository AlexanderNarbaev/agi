package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.9 — the premise of a RUSSIAN question is checked.
 *
 * <p>The W42.9 diagnosis: the false-premise detector existed and worked in
 * English, and it did not work in Russian. The measurement was explicit — an
 * English question refused with an exact explanation, a Russian question
 * answered, its scope declared OUTSIDE, and the reason for the refusal
 * null.
 *
 * <p>The cause was a mismatch of FORMS: the premise was compared with the
 * claims in RAW words, the bilingual lexicon is a table of FOLDED stems, and
 * the Russian words of the premise therefore never reached the lexicon at
 * all. The Russian fold was also missing the genitive ending "-ого", so
 * "углекислого" never met the lexicon's "углекислый".
 */
class RussianPremiseTest {

    @Test
    @DisplayName("the premise of a Russian question is found")
    void thePremiseOfARussianQuestionIsFound() {
        assertThat(PremiseChecker.premiseOf(
                "Поскольку фотосинтез производит кислород из углекислого газа, "
                        + "сколько молекул кислорода даёт одна молекула CO2?"))
                .isEqualTo("фотосинтез производит кислород из углекислого газа");
    }

    @Test
    @DisplayName("the Russian premise markers are the Russian ones")
    void theRussianPremiseMarkersAreTheRussianOnes() {
        // The English markers do not open a Russian question, and the
        // Russian ones do not open an English one.
        assertThat(PremiseChecker.premiseOf(
                "Так как фотосинтез производит кислород, что это?")).isNotNull();
        assertThat(PremiseChecker.premiseOf(
                "При условии что фотосинтез производит кислород, что это?")).isNotNull();
        // And a Russian question that asserts nothing has no premise.
        assertThat(PremiseChecker.premiseOf("Что такое фотосинтез?")).isNull();
    }

    @Test
    @DisplayName("the Russian claim that denies the premise is found")
    void theRussianClaimThatDeniesThePremiseIsFound(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            String premise = "фотосинтез производит кислород из углекислого газа";
            Claim refutation = PremiseChecker.refutation(premise,
                    workbench.workspace("photosynthesis"));

            assertThat(refutation).isNotNull();
            assertThat(refutation.status()).isEqualTo(ClaimStatus.REFUTED);
            assertThat(refutation.proposition()).contains("carbon dioxide");
        }
    }

    @Test
    @DisplayName("the Russian question with a false premise is refused")
    void theRussianQuestionWithAFalsePremiseIsRefused(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Поскольку фотосинтез производит кислород из углекислого газа, "
                            + "сколько молекул кислорода даёт одна молекула CO2?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
            assertThat(answer.abstentionReason()).contains("premise");
        }
    }

    @Test
    @DisplayName("the Russian question with a supported premise is still answered")
    void theRussianQuestionWithASupportedPremiseIsStillAnswered(@TempDir Path dir) {
        // THE OTHER HALF OF THE RULE. A premise the workspace holds must
        // not be refused: the refusal is only for a premise the workspace
        // denies.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Поскольку фотосинтез преобразует световую энергию в химическую, "
                            + "откуда берётся выделяемый кислород?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
        }
    }

    @Test
    @DisplayName("the Russian genitive reaches the lexicon's entry")
    void theRussianGenitiveReachesTheLexiconsEntry() {
        // The fold strips the case ending, so the genitive of "углекислый"
        // and "углекислый" itself are the same stem.
        assertThat(TextProfiles.RUSSIAN.fold("углекислого")).isEqualTo("углекисл");
        assertThat(TextProfiles.RUSSIAN.fold("углекислый")).isEqualTo("углекисл");
        // And the lexicon holds that stem.
        assertThat(BilingualLexicon.instance().translations("углекисл"))
                .contains("carbon dioxide");
    }

    @Test
    @DisplayName("the English premise still finds its refutation")
    void theEnglishPremiseStillFindsItsRefutation(@TempDir Path dir) {
        // The change is a change of FORM, not of rule, and the English path
        // is the one that already worked.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Claim refutation = PremiseChecker.refutation(
                    "photosynthesis produces oxygen from carbon dioxide",
                    workbench.workspace("photosynthesis"));

            assertThat(refutation).isNotNull();
            assertThat(refutation.status()).isEqualTo(ClaimStatus.REFUTED);
        }
    }
}
